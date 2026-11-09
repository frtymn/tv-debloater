#!/usr/bin/env python3
"""Drives the real app UI on the CI emulator. The app connects to the emulator's own adbd
through the host (10.0.2.2:5555), so every screen runs against a real Android 9 package manager."""
import re, subprocess, sys, time

SHOTS = "out"
n = [0]

def adb(*a, check=True):
    return subprocess.run(["adb", *a], check=check, capture_output=True, text=True).stdout

def shot(name):
    n[0] += 1
    path = f"{SHOTS}/{n[0]:02d}-{name}.png"
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout)
    print("shot", path)

def nodes():
    for _ in range(3):
        r = subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, text=True)
        if "dumped" in (r.stdout + r.stderr).lower():
            break
        time.sleep(1)
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    out = []
    for node in re.findall(r"<node [^>]*>", xml):
        t = re.search(r' text="([^"]*)"', node); d = re.search(r'content-desc="([^"]*)"', node)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            out.append(((t.group(1) if t else ""), (d.group(1) if d else ""), tuple(map(int, b.groups()))))
    return out

def size():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return int(m.group(1)), int(m.group(2))

def find(text, exact=True, desc=False):
    for t, d, b in nodes():
        v = d if desc else t
        if (v == text) if exact else (text in v):
            return b
    return None

def swipe_up():
    w, h = size()
    adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.75)), str(w // 2), str(int(h * 0.30)), "300")
    time.sleep(1)

def tap(text, exact=True, desc=False, scroll=6, required=True):
    for i in range(scroll + 1):
        b = find(text, exact, desc)
        if b:
            x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            adb("shell", "input", "tap", str(x), str(y))
            print("tap", text, x, y)
            time.sleep(1.5)
            return True
        if i < scroll:
            swipe_up()
    if required:
        shot("missing-" + re.sub(r"\W+", "_", text)[:20])
        print("NOT FOUND:", text, [t for t, _, _ in nodes() if t][:40])
        sys.exit(1)
    return False

def wait_for(text, timeout=40, exact=False):
    end = time.time() + timeout
    while time.time() < end:
        if find(text, exact):
            return True
        time.sleep(2)
    shot("timeout-" + re.sub(r"\W+", "_", text)[:20])
    print("TIMEOUT waiting for:", text, [t for t, _, _ in nodes() if t][:40])
    sys.exit(1)

def disabled_pkgs():
    return set(l.replace("package:", "").strip() for l in adb("shell", "pm", "list", "packages", "-d").splitlines() if l.strip())

# --- start fresh
adb("shell", "pm", "clear", "com.frtymn.tvdebloater")
adb("shell", "am", "start", "-W", "-n", "com.frtymn.tvdebloater/.MainActivity")
time.sleep(5)
shot("onboarding")
tap("I did this, continue")
wait_for("Connect to TV")
shot("connect")

# --- error path: closed port gives a friendly message and Retry
tap("TV IP address", scroll=0)
adb("shell", "input", "text", "10.0.2.2")
tap("5555", scroll=0)
for _ in range(6): adb("shell", "input", "keyevent", "67")
adb("shell", "input", "text", "5599")
adb("shell", "input", "keyevent", "4")  # hide keyboard
time.sleep(1)
tap("Connect")
wait_for("Retry")
shot("error-refused")

# --- real connection
tap("5599", scroll=2)
for _ in range(6): adb("shell", "input", "keyevent", "67")
adb("shell", "input", "text", "5555")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap("Connect")
wait_for("System apps", timeout=60)
wait_for("Protected (", timeout=60)
time.sleep(2)
shot("packages")

# --- light profile + dry run: nothing must change
before = disabled_pkgs()
tap("Light profile")
time.sleep(1)
shot("light-selected")
tap("Disable", scroll=0)
wait_for("These exact commands")
shot("confirm-dry")
tap("Show commands", scroll=0)
wait_for("Dry run:", timeout=30)
shot("results-dry")
assert disabled_pkgs() == before, "dry run changed the device"
tap("Done")

# --- turn dry run off in Settings
tap("Menu", desc=True, scroll=0)
tap("Settings", scroll=0)
tap("Dry run", scroll=0)
shot("settings-dry-off")
adb("shell", "input", "keyevent", "4")
time.sleep(2)

# --- real disable of the SAFE preselection
tap("Light profile")
tap("Disable", scroll=0)
wait_for("These exact commands")
shot("confirm-real")
tap("Run", scroll=0)
wait_for("succeeded", timeout=90)
time.sleep(1)
shot("results-real")
after = disabled_pkgs()
changed = after - before
print("newly disabled:", sorted(changed))
assert changed, "nothing was disabled"
tap("Done")

# --- history + undo batch
tap("Menu", desc=True, scroll=0)
tap("History", scroll=0)
wait_for("Undo batch")
shot("history")
tap("Undo batch")
wait_for("These exact commands")
shot("confirm-undo")
tap("Run", scroll=0)
wait_for("succeeded", timeout=90)
shot("results-undo")
restored = disabled_pkgs()
assert not (restored & changed), f"undo did not restore {sorted(restored & changed)}"
print("undo restored all:", sorted(changed))
tap("Done")

# --- panic: nothing left to restore
tap("Menu", desc=True, scroll=0)
tap("Restore all", scroll=0)
time.sleep(4)
shot("panic-nothing")
print("UI FLOW OK")
