#!/usr/bin/env python3
"""Repository consistency checks, run in CI (build.yml, job "repo-checks") and before every release.

Fails when:
- README.md or README.tr.md references a local image or file that does not exist,
  or the two READMEs have a different section structure or a different number of images;
- string keys differ between values/ and values-tr/, format arguments differ, a string is unused,
  or a string still contains TODO/FIXME/placeholder text;
- a manifest permission is not explained in both READMEs;
- packages.json is not valid JSON, has duplicates, or has an entry without sources;
- on a tag build (refs/tags/vX.Y.Z): VERSION_NAME is not X.Y.Z, or docs/release-notes/vX.Y.Z.md
  is missing or is not the newest release-notes file.
Image sizes are checked by tools/check_images.py, which this script also runs.
No third-party modules needed.
"""
import glob, json, os, re, subprocess, sys
import xml.etree.ElementTree as ET

errors = []
def err(msg): errors.append(msg); print("BAD", msg)
def ok(msg): print("OK ", msg)

# 1) READMEs: local links/images exist, same structure in both languages.
def readme_info(path):
    text = open(path, encoding="utf-8").read()
    no_code = re.sub(r"```.*?```", "", text, flags=re.S)
    refs = re.findall(r'(?:src|href)="([^"]+)"', no_code) + re.findall(r"\]\(([^)\s]+)\)", no_code)
    imgs = re.findall(r'<img[^>]+src="([^"]+)"', no_code) + re.findall(r"!\[[^\]]*\]\(([^)\s]+)\)", no_code)
    heads = [len(m) for m in re.findall(r"^(#{1,6}) ", no_code, flags=re.M)]
    return refs, imgs, heads

info = {}
for readme in ("README.md", "README.tr.md"):
    refs, imgs, heads = readme_info(readme)
    info[readme] = (imgs, heads)
    for r in refs:
        if re.match(r"^(https?:|mailto:|#)", r):
            continue
        target = r.split("#")[0].split("?")[0]
        if not os.path.exists(target):
            err(f"{readme}: '{r}' does not exist")
    ok(f"{readme}: {len(refs)} links, {len(imgs)} images checked")
(en_imgs, en_heads), (tr_imgs, tr_heads) = info["README.md"], info["README.tr.md"]
if en_heads != tr_heads:
    err(f"README.md and README.tr.md have different heading structures: {en_heads} vs {tr_heads}")
local = lambda l: [i for i in l if not i.startswith("http")]
if len(local(en_imgs)) != len(local(tr_imgs)):
    err(f"README.md has {len(local(en_imgs))} local images, README.tr.md has {len(local(tr_imgs))}")
else:
    ok("READMEs have the same structure and number of images")

# 2) Strings.
def strings(path):
    out = {}
    for e in ET.parse(path).getroot():
        if e.get("translatable") == "false":
            continue
        out[e.get("name")] = "".join(e.itertext())
    return out
en = strings("app/src/main/res/values/strings.xml")
tr = strings("app/src/main/res/values-tr/strings.xml")
for k in sorted(set(en) ^ set(tr)):
    err(f"string '{k}' exists only in {'values' if k in en else 'values-tr'}")
fmt = lambda s: sorted(re.findall(r"%\d+\$[sd]|%[sd]", s))
for k in sorted(set(en) & set(tr)):
    if fmt(en[k]) != fmt(tr[k]):
        err(f"string '{k}': format arguments differ ({fmt(en[k])} vs {fmt(tr[k])})")
for k, v in list(en.items()) + list(tr.items()):
    if re.search(r"\b(TODO|FIXME|TBD|lorem ipsum)\b", v, re.I):
        err(f"string '{k}' contains placeholder text")
code = ""
for f in glob.glob("app/src/**/*.kt", recursive=True) + glob.glob("app/src/main/**/*.xml", recursive=True):
    if not f.endswith("strings.xml"):
        code += open(f, encoding="utf-8").read()
all_en = {e.get("name") for e in ET.parse("app/src/main/res/values/strings.xml").getroot()}
for k in sorted(all_en):
    if not re.search(r"R\.(string|plurals|array)\.%s\b|@string/%s\b" % (k, k), code):
        err(f"string '{k}' is not used")
ok(f"strings: {len(en)} keys in both languages")

# 3) Manifest permissions are explained in both READMEs.
manifest = open("app/src/main/AndroidManifest.xml", encoding="utf-8").read()
perms = re.findall(r'uses-permission[^>]*android:name="android\.permission\.([A-Z_]+)"', manifest)
for readme in ("README.md", "README.tr.md"):
    text = open(readme, encoding="utf-8").read()
    for p in perms:
        if f"`{p}`" not in text:
            err(f"{readme}: permission {p} is not explained")
ok(f"manifest permissions: {', '.join(perms)}")

# 4) Package database.
try:
    db = json.load(open("app/src/main/assets/packages.json", encoding="utf-8"))
    names = [p["packageName"] for p in db["packages"]]
    if len(names) != len(set(names)):
        err("packages.json has duplicate package names")
    for p in db["packages"]:
        if p.get("level") not in ("SAFE", "CAUTION", "PROTECTED"):
            err(f"packages.json: {p['packageName']} has level {p.get('level')}")
        if not p.get("sources"):
            err(f"packages.json: {p['packageName']} has no sources")
    ok(f"packages.json: {len(names)} entries (the guard overlap is tested in ProtectedGuardTest)")
except Exception as e:  # noqa: BLE001
    err(f"packages.json is not valid: {e}")

# 5) Versions on tag builds.
def semver(path):
    m = re.search(r"v(\d+)\.(\d+)\.(\d+)\.md$", path)
    return tuple(map(int, m.groups())) if m else (-1, -1, -1)
notes = sorted(glob.glob("docs/release-notes/v*.md"), key=semver)
if not notes:
    err("no files in docs/release-notes/")
ref = os.environ.get("GITHUB_REF", "")
if ref.startswith("refs/tags/v"):
    tag = ref[len("refs/tags/"):]
    version = os.environ.get("VERSION_NAME", "")
    if version != tag[1:]:
        err(f"tag {tag} but VERSION_NAME is '{version}'")
    if not os.path.exists(f"docs/release-notes/{tag}.md"):
        err(f"docs/release-notes/{tag}.md is missing")
    elif notes and os.path.basename(notes[-1]) != f"{tag}.md":
        err(f"tag {tag} but the newest release notes file is {os.path.basename(notes[-1])}")
    else:
        ok(f"tag {tag}, VERSION_NAME {version}, release notes {tag}.md")
else:
    ok(f"not a tag build; newest release notes: {os.path.basename(notes[-1]) if notes else '-'}")

# 6) Image sizes.
if subprocess.run([sys.executable, "tools/check_images.py"]).returncode != 0:
    err("tools/check_images.py failed")

print(f"\n{len(errors)} problem(s)")
sys.exit(1 if errors else 0)
