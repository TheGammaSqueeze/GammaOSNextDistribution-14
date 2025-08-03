#!/bin/bash

function run() {
    local FAILED_TESTS=()

    # the linter test requires having the source tree available in order
    # to run, so it isn't using TEST_MAPPING/tradefed/etc
    local RUN_TIME_TESTS=(\
        hidl-lint_test \
    )

    $ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode -j \
        ${RUN_TIME_TESTS[*]} || return

    local BITNESS=("nativetest" "nativetest64")

    for bits in ${BITNESS[@]}; do
        for test in ${RUN_TIME_TESTS[@]}; do
            echo $bits $test
            $ANDROID_BUILD_TOP/out/host/linux-x86/$bits/$test/$test ||
                FAILED_TESTS+=("$bits:$test")
        done
    done

    echo
    echo ===== ALL HOST TESTS SUMMARY =====
    echo
    if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
        for failed in ${FAILED_TESTS[@]}; do
            echo "FAILED TEST: $failed"
        done
    else
        echo "SUCCESS"
    fi
}

run
