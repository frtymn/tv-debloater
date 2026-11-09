#!/usr/bin/env python3
"""Package database tool. db/packages.json on main is the single validated source.

  python3 tools/db_build.py check            validate + guard cross-check + SAFE rule
  python3 tools/db_build.py check --apk FILE also check that the APK bundles exactly db/packages.json
  python3 tools/db_build.py publish --out DIR [--previous-manifest FILE]
                                             bump dbVersion, write DIR/db/packages.json and DIR/db/manifest.json

Checks (CI only, the app has its own strict validator):
  * JSON Schema (db/packages.schema.json) when the jsonschema module is installed, plus built-in checks
    with the same limits as the app (DbValidator.kt);
  * every package matched by the hard-coded guard (ProtectedGuard.kt) must be PROTECTED;
  * every SAFE entry needs >= 2 sources, or 1 source plus a verifiedOn record.
"""
import argparse, datetime, hashlib, json, os, re, sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SOURCE = os.path.join(ROOT, "db", "packages.json")
SCHEMA = os.path.join(ROOT, "db", "packages.schema.json")
GUARD = os.path.join(ROOT, "app", "src", "main", "java", "com", "frtymn", "tvdebloater", "core", "ProtectedGuard.kt")

SUPPORTED_SCHEMA = 2
MAX_BYTES = 1_048_576
MAX_ENTRIES = 2000
LIMITS = dict(label=80, descriptionEn=600, descriptionTr=600, notes=600)
MAX_SOURCES, MAX_URL, MAX_DEVICES, MAX_SHORT, MAX_VERIFIED = 10, 300, 20, 64, 10
PKG = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*$")
LEVELS = ("SAFE", "CAUTION", "PROTECTED")


# ---------------------------------------------------------------- guard (mirrors ProtectedGuard.isProtected)

def _block(src, name):
    m = re.search(r"val %s\b[^=]*=\s*(?:setOf|listOf)\((.*?)\n\s*\)" % name, src, re.S)
    if not m:
        raise SystemExit(f"cannot find {name} in ProtectedGuard.kt")
    body = re.sub(r"//[^\n]*", "", m.group(1))
    return re.findall(r'"([^"]+)"', body)


def load_guard(path=GUARD):
    src = open(path, encoding="utf-8").read()
    return set(_block(src, "EXACT")), _block(src, "PREFIXES"), _block(src, "KEYWORDS")


def is_protected(name, guard):
    exact, prefixes, keywords = guard
    p = name.strip().lower()
    if not p or p in exact:
        return True
    if any(p == x or p.startswith(x + ".") or p.startswith(x) for x in prefixes):
        return True
    return any(k in p for k in keywords)


# ---------------------------------------------------------------- validation

def validate(db, guard, errors):
    """Appends problems to errors. Same limits as the app's DbValidator."""
    schema = db.get("schemaVersion", db.get("version", 1))
    if not isinstance(schema, int) or not 1 <= schema <= SUPPORTED_SCHEMA:
        errors.append(f"schemaVersion {schema!r} is not supported")
    if not isinstance(db.get("dbVersion", 0), int) or db.get("dbVersion", 0) < 0:
        errors.append("dbVersion must be an integer >= 0")
    for k in ("publishedAt", "minAppVersion"):
        if len(str(db.get(k, ""))) > MAX_SHORT:
            errors.append(f"{k} too long")
    devices = db.get("devices", [])
    if not isinstance(devices, list) or len(devices) > MAX_DEVICES or any(not isinstance(d, str) or not d.strip() or len(d) > MAX_SHORT for d in devices):
        errors.append("bad top-level devices")
    pkgs = db.get("packages")
    if not isinstance(pkgs, list) or not pkgs:
        errors.append("packages must be a non-empty list")
        return
    if len(pkgs) > MAX_ENTRIES:
        errors.append(f"too many entries ({len(pkgs)})")
    seen = set()
    for i, e in enumerate(pkgs):
        name = e.get("packageName") if isinstance(e, dict) else None
        if not isinstance(name, str) or not PKG.match(name) or len(name) > 255:
            errors.append(f"entry {i}: bad packageName {name!r}")
            continue
        if name in seen:
            errors.append(f"duplicate package {name}")
        seen.add(name)
        level = e.get("level")
        if level not in LEVELS:
            errors.append(f"{name}: bad level {level!r}")
        for k, n in LIMITS.items():
            if len(str(e.get(k, ""))) > n:
                errors.append(f"{name}: {k} longer than {n}")
        src = e.get("sources", [])
        if not isinstance(src, list) or len(src) > MAX_SOURCES or any(not isinstance(u, str) or not u.startswith("https://") or len(u) > MAX_URL for u in src):
            errors.append(f"{name}: bad sources")
            src = []
        if not src:
            errors.append(f"{name}: needs at least one source")
        dev = e.get("devices", [])
        if not isinstance(dev, list) or len(dev) > MAX_DEVICES or any(not isinstance(d, str) or not d.strip() or len(d) > MAX_SHORT for d in dev):
            errors.append(f"{name}: bad devices")
        ver = e.get("verifiedOn", [])
        if not isinstance(ver, list) or len(ver) > MAX_VERIFIED or any(
            not isinstance(v, dict) or not v.get("model") or any(len(str(v.get(k, ""))) > MAX_SHORT for k in ("model", "android", "date")) for v in ver
        ):
            errors.append(f"{name}: bad verifiedOn")
            ver = []
        if is_protected(name, guard) and level != "PROTECTED":
            errors.append(f"{name}: protected in code (ProtectedGuard) but level is {level}")
        if level == "SAFE" and not (len(src) >= 2 or (len(src) == 1 and ver)):
            errors.append(f"{name}: SAFE needs >= 2 sources, or 1 source plus a verifiedOn record")
        if e.get("aggressive") and level != "CAUTION":
            errors.append(f"{name}: aggressive is only allowed on CAUTION entries")


def schema_check(db, errors):
    try:
        import jsonschema  # type: ignore
    except ImportError:
        print("note: jsonschema not installed; built-in checks only")
        return
    schema = json.load(open(SCHEMA, encoding="utf-8"))
    v = jsonschema.Draft202012Validator(schema)
    for e in sorted(v.iter_errors(db), key=lambda x: list(x.path)):
        errors.append("schema: " + "/".join(map(str, e.path)) + ": " + e.message)


def canonical(db):
    return (json.dumps(db, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_source():
    raw = open(SOURCE, "rb").read()
    if len(raw) > MAX_BYTES:
        raise SystemExit(f"{SOURCE} is larger than {MAX_BYTES} bytes")
    return json.loads(raw.decode("utf-8"))


def counts(db):
    return {lv: sum(1 for p in db["packages"] if p["level"] == lv) for lv in LEVELS}


def full_check(db):
    errors = []
    schema_check(db, errors)
    validate(db, load_guard(), errors)
    return errors


# ---------------------------------------------------------------- commands

def cmd_check(a):
    db = load_source()
    errors = full_check(db)
    note = ""
    if a.apk:
        import zipfile
        try:
            bundled = zipfile.ZipFile(a.apk).read("assets/packages.json")
        except KeyError:
            bundled = None
        if bundled != open(SOURCE, "rb").read():
            errors.append(f"{a.apk}: assets/packages.json is not identical to db/packages.json (bundled copy drifted)")
        note = "; the APK bundles the same bytes"
    for e in errors:
        print("ERROR:", e)
    if errors:
        sys.exit(1)
    c = counts(db)
    print(f"OK: db/packages.json dbVersion {db.get('dbVersion')} - {len(db['packages'])} entries {c}{note}")


def cmd_publish(a):
    db = load_source()
    errors = full_check(db)
    if errors:
        for e in errors:
            print("ERROR:", e)
        sys.exit(1)
    prev = None
    if a.previous_manifest and os.path.exists(a.previous_manifest):
        prev = json.load(open(a.previous_manifest, encoding="utf-8"))
    src_version = int(db.get("dbVersion", 0))
    if prev is None:
        new_version = max(src_version, 1)  # the first published database
    else:
        new_version = max(int(prev["dbVersion"]), src_version) + 1
    if prev is not None and a.previous_db and os.path.exists(a.previous_db):
        old = json.load(open(a.previous_db, encoding="utf-8"))
        if old.get("packages") == db["packages"] and old.get("devices") == db.get("devices") and not a.force:
            print("No content change since the published dbVersion", prev["dbVersion"], "- nothing to publish.")
            return
    db["schemaVersion"] = SUPPORTED_SCHEMA
    db["dbVersion"] = new_version
    if prev is not None or not db.get("publishedAt"):
        db["publishedAt"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    data = canonical(db)
    out = os.path.join(a.out, "db")
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "packages.json"), "wb").write(data)
    manifest = {
        "schemaVersion": SUPPORTED_SCHEMA,
        "dbVersion": new_version,
        "publishedAt": db["publishedAt"],
        "minAppVersion": db.get("minAppVersion", ""),
        "file": "packages.json",
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "counts": counts(db),
    }
    open(os.path.join(out, "manifest.json"), "w", encoding="utf-8").write(json.dumps(manifest, indent=2) + "\n")
    if a.update_source:
        open(SOURCE, "wb").write(data)
    print(f"published dbVersion {new_version}: {len(data)} bytes, sha256 {manifest['sha256']}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check")
    c.add_argument("--apk", help="APK whose assets/packages.json must equal db/packages.json")
    c.set_defaults(f=cmd_check)
    p = sub.add_parser("publish")
    p.add_argument("--out", required=True)
    p.add_argument("--previous-manifest")
    p.add_argument("--previous-db")
    p.add_argument("--force", action="store_true", help="publish even when the content did not change")
    p.add_argument("--update-source", action="store_true", help="also write the bumped version back to db/packages.json")
    p.set_defaults(f=cmd_publish)
    a = ap.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()
