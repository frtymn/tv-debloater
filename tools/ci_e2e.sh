#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (see .github/workflows/e2e.yml).
set -euo pipefail
mkdir -p out
adb wait-for-device
until adb shell service list | grep -q 'package'; do sleep 2; done
adb shell getprop ro.build.version.release
adb shell getprop ro.adb.secure || true

# 1) Real ADB end-to-end test from the JVM through the app's own AdbManager.
E2E_ADB=1 ./gradlew --no-daemon testDebugUnitTest --tests '*EmulatorE2ETest*' -i | grep -E 'E2E|PASSED|FAILED|SKIPPED|BUILD' || true
test -f app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml
if grep -q '<failure\|<skipped' app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml; then
  cat app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml
  exit 1
fi

# 2) UI smoke test: install, launch, screenshot, make sure it does not crash.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n com.frtymn.tvdebloater/.MainActivity
sleep 6
adb exec-out screencap -p > out/01-onboarding.png
python3 tools/ui_tap.py "I did this, continue" || python3 tools/ui_tap.py "Yaptım, devam et" || true
sleep 3
adb exec-out screencap -p > out/02-connect.png
python3 tools/ui_tap.py "Settings" desc || true
sleep 3
adb exec-out screencap -p > out/03-settings.png
adb logcat -d > out/logcat.txt
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: com.frtymn.tvdebloater" out/logcat.txt; then
  echo "App crashed"; exit 1
fi
adb shell pidof com.frtymn.tvdebloater
echo "UI smoke OK"
