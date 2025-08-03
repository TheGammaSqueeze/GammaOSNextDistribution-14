#!/bin/bash -eux
#
# Script to run some local APEX tests while APEX support is WIP and not easily testable on CI

set -o pipefail

source $(cd $(dirname $BASH_SOURCE) &> /dev/null && pwd)/../../make/shell_utils.sh
require_top


# Generate BUILD files into out/soong/bp2build
"${TOP}/build/soong/soong_ui.bash" --make-mode BP2BUILD_VERBOSE=1 bp2build --skip-soong-tests

BUILD_FLAGS_LIST=(
  --color=no
  --curses=no
  --show_progress_rate_limit=5
  --config=bp2build
)
BUILD_FLAGS="${BUILD_FLAGS_LIST[@]}"

TEST_FLAGS_LIST=(
  --keep_going
  --test_output=errors
)
TEST_FLAGS="${TEST_FLAGS_LIST[@]}"

BUILD_TARGETS_LIST=(
  //build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal
  //system/timezone/apex:com.android.tzdata
)
BUILD_TARGETS="${BUILD_TARGETS_LIST[@]}"

echo "Building APEXes with Bazel..."
${TOP}/build/bazel/bin/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_x86 -k ${BUILD_TARGETS}
${TOP}/build/bazel/bin/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_x86_64 -k ${BUILD_TARGETS}
${TOP}/build/bazel/bin/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_arm -k ${BUILD_TARGETS}
${TOP}/build/bazel/bin/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_arm64 -k ${BUILD_TARGETS}

set +x
echo
echo "All tests passed, you are awesome!"
