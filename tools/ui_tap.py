#!/usr/bin/env python3
"""Tap the first UI element whose text (or content-desc with 'desc') matches argv[1]."""
import re, subprocess, sys
want = sys.argv[1]
attr = "content-desc" if len(sys.argv) > 2 and sys.argv[2] == "desc" else "text"
subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=True, capture_output=True)
xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], check=True, capture_output=True, text=True).stdout
for node in re.findall(r"<node [^>]*>", xml):
    m = re.search(attr + r'="([^"]*)"', node)
    if m and m.group(1) == want:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x = (int(b.group(1)) + int(b.group(3))) // 2
        y = (int(b.group(2)) + int(b.group(4))) // 2
        subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], check=True)
        print("tapped", want, x, y)
        sys.exit(0)
print("not found:", want)
sys.exit(1)
