#!/usr/bin/env python3
"""Self-test for tools/db_build.py (run in CI before the real check)."""
import copy, hashlib, json, os, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import db_build as d  # noqa: E402

guard = d.load_guard()
assert "com.android.systemui" in guard[0] and guard[1] and guard[2], "guard lists not found in ProtectedGuard.kt"
assert d.is_protected("com.google.android.tvlauncher", guard)
assert not d.is_protected("com.dazn", guard)

S1, S2 = "https://example.org/a", "https://example.org/b"
base = {"schemaVersion": 2, "dbVersion": 1, "devices": ["MiTV-AESP0"], "packages": [
    {"packageName": "com.dazn", "level": "SAFE", "sources": [S1, S2]},
]}


def errors(db):
    e = []
    d.validate(db, guard, e)
    return e


assert errors(base) == [], errors(base)

bad = copy.deepcopy(base); bad["packages"].append({"packageName": "com.android.systemui", "level": "CAUTION", "sources": [S1]})
assert any("protected in code" in e for e in errors(bad)), "guard conflict not caught"

bad = copy.deepcopy(base); bad["packages"][0]["sources"] = [S1]
assert any("SAFE needs" in e for e in errors(bad)), "SAFE rule not enforced"
bad["packages"][0]["verifiedOn"] = [{"model": "MiTV-AESP0", "android": "9", "date": "2026-11-09"}]
assert errors(bad) == [], "1 source + verifiedOn must be allowed"

bad = copy.deepcopy(base); bad["packages"].append(copy.deepcopy(base["packages"][0]))
assert any("duplicate" in e for e in errors(bad))
bad = copy.deepcopy(base); bad["packages"][0]["level"] = "MAYBE"
assert any("bad level" in e for e in errors(bad))
bad = copy.deepcopy(base); bad["packages"][0]["packageName"] = "com.dazn; reboot"
assert any("bad packageName" in e for e in errors(bad))

# publish: bump, manifest size and sha256 match the written file.
with tempfile.TemporaryDirectory() as t:
    prev = os.path.join(t, "prev.json")
    json.dump({"dbVersion": 4}, open(prev, "w"))
    subprocess.run([sys.executable, os.path.join(d.ROOT, "tools", "db_build.py"), "publish", "--out", t, "--previous-manifest", prev], check=True, capture_output=True)
    m = json.load(open(os.path.join(t, "db", "manifest.json")))
    data = open(os.path.join(t, "db", "packages.json"), "rb").read()
    assert m["dbVersion"] == 5 and json.loads(data)["dbVersion"] == 5, m
    assert m["size"] == len(data) and m["sha256"] == hashlib.sha256(data).hexdigest()
    assert m["file"] == "packages.json" and sum(m["counts"].values()) == len(json.loads(data)["packages"])

print("db_build self-test OK")
