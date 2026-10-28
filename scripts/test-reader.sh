#!/usr/bin/env bash
# Preserve the test result even if screenshots cannot be collected.
set -u
gradle --no-daemon :app:connectedDebugAndroidTest
pdfcrate_test_status=$?
mkdir -p "app/build/screenshots/api-$1" "app/build/device-results/api-$1"
adb pull /sdcard/Android/data/dev.pdfcrate.app/files/screenshots/. "app/build/screenshots/api-$1/" || true
if [ -d app/build/reports/androidTests/connected ]; then
    cp -R app/build/reports/androidTests/connected "app/build/device-results/api-$1/"
fi
exit "$pdfcrate_test_status"
