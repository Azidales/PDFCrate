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
if [ -d app/build/outputs/androidTest-results/connected ]; then
    cp -R app/build/outputs/androidTest-results/connected "app/build/device-results/api-$1/results"
fi
if [ -d app/build/outputs/connected_android_test_additional_output ]; then
    cp -R app/build/outputs/connected_android_test_additional_output "app/build/device-results/api-$1/additional"
fi
if [ "$pdfcrate_test_status" -eq 0 ] && [ "$1" = "36" ]; then
    bash scripts/test-update.sh
    pdfcrate_test_status=$?
fi
exit "$pdfcrate_test_status"
