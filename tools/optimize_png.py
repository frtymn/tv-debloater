#!/usr/bin/env python3
"""Lossy-then-lossless PNG optimization: pngquant (if it shrinks the file) and then oxipng or optipng."""
import shutil, subprocess, sys

for f in sys.argv[1:]:
    if shutil.which("pngquant"):
        # Exit code 98/99 = quality or size target not met; the original is kept then.
        subprocess.run(["pngquant", "--quality=70-95", "--speed=1", "--strip", "--skip-if-larger", "--force", "--output", f, f])
    if shutil.which("oxipng"):
        subprocess.run(["oxipng", "-o", "4", "--strip", "safe", "-q", f], check=True)
    elif shutil.which("optipng"):
        subprocess.run(["optipng", "-o3", "-quiet", f], check=True)
    print("optimized", f)
