#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/upgrade
cp app/build/outputs/apk/debug/app-debug.apk app/build/upgrade/before.apk
adb install -r app/build/upgrade/before.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class dev.pdfcrate.app.UpgradeContinuityTest -e stage seed dev.pdfcrate.app.test/androidx.test.runner.AndroidJUnitRunner > app/build/upgrade/seed.txt
if ! rg -q 'OK \(1 test\)' app/build/upgrade/seed.txt; then
    cat app/build/upgrade/seed.txt
    exit 1
fi
gradle --no-daemon -PpdfcrateVersionCode=4 :app:assembleDebug :app:assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -r -e class dev.pdfcrate.app.UpgradeContinuityTest -e stage verify dev.pdfcrate.app.test/androidx.test.runner.AndroidJUnitRunner > app/build/upgrade/verify.txt
if ! rg -q 'OK \(1 test\)' app/build/upgrade/verify.txt; then
    cat app/build/upgrade/verify.txt
    exit 1
fi
cp app/build/outputs/apk/release/app-release-unsigned.apk app/build/upgrade/pdfcrate-next-unsigned.apk
rm app/build/upgrade/before.apk
