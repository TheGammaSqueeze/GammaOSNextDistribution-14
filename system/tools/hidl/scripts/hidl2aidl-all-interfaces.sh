#!/usr/bin/env bash

# Runs all released packages through the hidl2aidl tool and reports any failures
# Requires that hidl2aidl is built.
# 'm hidl2aidl'


function hidl2aidl-all-interfaces-main() {
    local ANY_FAIL=0
    local TEST_DIR='/tmp/hidl2aidl_test'
    set -e
    mkdir "$TEST_DIR"
    source "${ANDROID_BUILD_TOP}/system/tools/hidl/scripts/hal-queries.sh"

    for i in $(aosp-released-packages);
    do
        hidl2aidl -o "$TEST_DIR" -f "$i" || \
            { echo "FAIL: $i"; ANY_FAIL=1; }
    done

    rm -rf "$TEST_DIR"

    [ $ANY_FAIL -eq 0 ] && echo 'All passed!'
}

hidl2aidl-all-interfaces-main
