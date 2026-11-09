#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (see .github/workflows/e2e.yml).
set -euo pipefail
mkdir -p out
adb wait-for-device
until adb shell service list | grep -q 'package'; do sleep 2; done
adb shell getprop ro.build.version.release
adb shell getprop ro.adb.secure || true
echo "ANDROID_USER_HOME=${ANDROID_USER_HOME:-} HOME=$HOME"; ls -la "$HOME/.android" || true

# 1) Real ADB end-to-end test from the JVM through the app's own AdbManager.
E2E_ADB=1 ./gradlew --no-daemon testDebugUnitTest --tests '*EmulatorE2ETest*' -i | grep -E 'E2E|PASSED|FAILED|SKIPPED|BUILD' || true
test -f app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml
if grep -q '<failure\|<skipped' app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml; then
  cat app/build/test-results/testDebugUnitTest/TEST-com.frtymn.tvdebloater.e2e.EmulatorE2ETest.xml
  exit 1
fi

# 2) UI flow test: install and drive the real app against the emulator's own adbd.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
python3 tools/ui_flow.py
adb logcat -d > out/logcat.txt
if grep -E "FATAL EXCEPTION" out/logcat.txt; then
  echo "App crashed"; exit 1
fi
echo "UI flow OK"
