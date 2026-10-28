#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/upgrade
cp app/build/outputs/apk/debug/app-debug.apk app/build/upgrade/before.apk
adb install -r app/build/upgrade/before.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class dev.pdfcrate.app.UpgradeContinuityTest -e stage seed dev.pdfcrate.app.test/androidx.test.runner.AndroidJUnitRunner > app/build/upgrade/seed.txt
if ! grep -Fq 'OK (1 test)' app/build/upgrade/seed.txt; then
    cat app/build/upgrade/seed.txt
    exit 1
fi
pdfcrate_previous_code=$("$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging app/build/upgrade/before.apk | python3 -c 'import shlex,sys; print(next(t.split("=",1)[1] for t in shlex.split(sys.stdin.readline()) if t.startswith("versionCode=")))')
pdfcrate_next_code=$((pdfcrate_previous_code + 1))
gradle --no-daemon -PpdfcrateVersionCode="$pdfcrate_next_code" :app:assembleDebug :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -r -e class dev.pdfcrate.app.UpgradeContinuityTest -e stage verify dev.pdfcrate.app.test/androidx.test.runner.AndroidJUnitRunner > app/build/upgrade/verify.txt
if ! grep -Fq 'OK (1 test)' app/build/upgrade/verify.txt; then
    cat app/build/upgrade/verify.txt
    exit 1
fi
cp app/build/outputs/apk/release/app-release-unsigned.apk app/build/upgrade/pdfcrate-next-unsigned.apk
rm app/build/upgrade/before.apk
