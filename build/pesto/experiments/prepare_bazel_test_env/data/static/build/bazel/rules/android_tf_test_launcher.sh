#!/bin/bash
#
# Launches an Android instrumentation test with Tradefed.
# The script expects the following:
#   1. adb is present on the path.
#   2. LD_LIBRARY_PATH is set for any libraries that need to be referenced.
#   3. script_help.sh is included in the runfiles.
#   4. TF_JAR_DIR and TF_PATH are set corresponding to the location of the
#      Tradefed JARs and any associated libraries.
#   5. The Tradefed launch script, test_module (i.e. hello_world_tests),
#      and test_path (i.e. platform_testing/tests/example/native) are provided
#      as the first and second positional arguments respectively.

TRADEFED_LAUNCHER=$1
shift
TEST_MODULE=$1
shift
TEST_PATH=$1
shift

if [ "$1" == "--host" ]; then
  HOST_ARGS=(-n --prioritize-host-config --skip-host-arch-check)
  shift
fi

exec $TRADEFED_LAUNCHER template/atest_local_min \
    --template:map test=atest \
    --tests-dir "$TEST_PATH" \
    --logcat-on-failure \
    --no-enable-granular-attempts \
    --no-early-device-release \
    --include-filter "$TEST_MODULE" \
    --skip-loading-config-jar \
    --log-level-display VERBOSE \
    --log-level VERBOSE \
    "${HOST_ARGS[@]}" \
    "$@"