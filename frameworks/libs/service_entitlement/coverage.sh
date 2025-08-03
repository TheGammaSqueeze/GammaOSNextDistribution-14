#!/usr/bin/env bash

##### App specific parameters #####

PACKAGE_NAME='com.android.libraries.entitlement'
MODULE_NAME='service-entitlement'
MODULE_PATH='frameworks/libs/service_entitlement'

TEST_PACKAGE='com.android.libraries.entitlement.tests'
TEST_MODULE_NAME='service-entitlement-tests'
TEST_MODULE_PATH='frameworks/libs/service_entitlement/tests'
TEST_MODULE_INSTALL_PATH="testcases/$TEST_MODULE_NAME/arm64/$TEST_MODULE_NAME.apk"
TEST_RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"

##### End app specific parameters #####

if [[ $# != 0 && ! ($# == 1 && ($1 == "html" || $1 == "xml" || $1 == "csv")) ]]; then
  echo "$0: usage: coverage.sh [REPORT_TYPE]"
  echo "REPORT_TYPE [html | xml | csv] : the type of the report (default is html)"
  exit 1
fi

REPORT_TYPE=${1:-html}

if [ -z $ANDROID_BUILD_TOP ]; then
  echo "You need to source and lunch before you can use this script"
  exit 1
fi

REPORTER_JAR="$ANDROID_BUILD_TOP/out/soong/host/linux-x86/framework/jacoco-cli.jar"

OUTPUT_DIR="$ANDROID_BUILD_TOP/out/coverage/$MODULE_NAME"

echo "Running tests and generating coverage report"
echo "Output dir: $OUTPUT_DIR"
echo "Report type: $REPORT_TYPE"

# location on the device to store coverage results, need to be accessible by the app
REMOTE_COVERAGE_OUTPUT_FILE="/data/user/0/$TEST_PACKAGE/files/coverage.ec"

COVERAGE_OUTPUT_FILE="$ANDROID_BUILD_TOP/out/$PACKAGE_NAME.ec"
OUT_COMMON="$ANDROID_BUILD_TOP/out/target/common"
COVERAGE_CLASS_FILE="$OUT/obj/JAVA_LIBRARIES/${MODULE_NAME}_intermediates/javalib.jar"

source $ANDROID_BUILD_TOP/build/envsetup.sh

set -e # fail early

echo ""
echo "BUILDING PACKAGE $PACKAGE_NAME"
echo "============================================"
(cd "$ANDROID_BUILD_TOP/$MODULE_PATH" && EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true mma -j32)
echo "============================================"

echo ""
echo "BUILDING TEST PACKAGE $TEST_MODULE_NAME"
echo "============================================"
(cd "$ANDROID_BUILD_TOP/$TEST_MODULE_PATH" && EMMA_INSTRUMENT=true EMMA_INSTRUMENT_STATIC=true mma -j32)
echo "============================================"

#set -x # print commands

adb root
adb wait-for-device

adb shell rm -f "$REMOTE_COVERAGE_OUTPUT_FILE"

adb install -r -g "$OUT/$TEST_MODULE_INSTALL_PATH"

echo ""
echo "RUNNING TESTS $TEST_RUNNER"
echo "============================================"
adb shell am instrument -e coverage true -w $TEST_RUNNER
echo "============================================"

mkdir -p "$OUTPUT_DIR"

adb pull "$REMOTE_COVERAGE_OUTPUT_FILE" "$COVERAGE_OUTPUT_FILE"

java -jar "$REPORTER_JAR" \
  report "$COVERAGE_OUTPUT_FILE" \
  --$REPORT_TYPE "$OUTPUT_DIR" \
  --classfiles "$COVERAGE_CLASS_FILE" \
  --sourcefiles "$ANDROID_BUILD_TOP/$MODULE_PATH/java"

#set +x

# Echo the file as URI to quickly open the result using ctrl-click in terminal
if [[ REPORT_TYPE == html ]] ; then
  echo "COVERAGE RESULTS IN:"
  echo "file://$OUTPUT_DIR/index.html"
fi
