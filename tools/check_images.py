#!/usr/bin/env python3
"""Checks the size limits of the README images (no third-party modules needed)."""
import glob, os, struct, sys

def dims(path):
    with open(path, "rb") as fh:
        head = fh.read(24)
    assert head[:8] == b"\x89PNG\r\n\x1a\n", path + " is not a PNG"
    return struct.unpack(">II", head[16:24])

rules = [(p, (1080, 2400), 400) for p in sorted(glob.glob("docs/screenshots/*.png"))]
rules += [("docs/hero.png", (1600, 1000), 1200), ("docs/social-preview.png", (1280, 640), 1000)]
bad = 0
for path, want, max_kb in rules:
    w, h = dims(path)
    kb = os.path.getsize(path) / 1024
    ok = (w, h) == want and kb <= max_kb
    bad += not ok
    print(f"{'OK ' if ok else 'BAD'} {path}: {w}x{h}, {kb:.0f} KB (want {want[0]}x{want[1]}, <= {max_kb} KB)")
if not glob.glob("docs/screenshots/*.png"):
    print("BAD no screenshots"); bad += 1
sys.exit(1 if bad else 0)
