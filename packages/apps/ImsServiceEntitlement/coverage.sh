#!/usr/bin/env bash

#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##### App specific parameters #####

PACKAGE_NAME='com.android.imsserviceentitlement'
MODULE_NAME='ImsServiceEntitlement'
MODULE_PATH='packages/apps/ImsServiceEntitlement'

TEST_PACKAGE='com.android.imsserviceentitlement.tests'
TEST_MODULE_NAME='ImsServiceEntitlementUnitTests'
TEST_MODULE_PATH='packages/apps/ImsServiceEntitlement/tests/unittests'
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
REMOTE_COVERAGE_OUTPUT_FILE="/data/data/$PACKAGE_NAME/files/coverage.ec"

COVERAGE_OUTPUT_FILE="$ANDROID_BUILD_TOP/out/$PACKAGE_NAME.ec"
OUT_COMMON="$ANDROID_BUILD_TOP/out/target/common"
COVERAGE_CLASS_FILE="$ANDROID_BUILD_TOP/out/soong/.intermediates/$MODULE_PATH/ImsServiceEntitlementLib/android_common/javac/ImsServiceEntitlementLib.jar"

source $ANDROID_BUILD_TOP/build/envsetup.sh

set -e # fail early

echo ""
echo "BUILDING TEST PACKAGE $TEST_PACKAGE_NAME"
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
adb shell am instrument -e coverage true -e coverageFile "$REMOTE_COVERAGE_OUTPUT_FILE" -w "$TEST_RUNNER"
echo "============================================"

mkdir -p "$OUTPUT_DIR"

adb pull "$REMOTE_COVERAGE_OUTPUT_FILE" "$COVERAGE_OUTPUT_FILE"

java -jar "$REPORTER_JAR" \
  report "$COVERAGE_OUTPUT_FILE" \
  --$REPORT_TYPE "$OUTPUT_DIR" \
  --classfiles "$COVERAGE_CLASS_FILE" \
  --sourcefiles "$ANDROID_BUILD_TOP/$MODULE_PATH/src"

#set +x

# Echo the file as URI to quickly open the result using ctrl-click in terminal
if [[ REPORT_TYPE == html ]] ; then
  echo "COVERAGE RESULTS IN:"
  echo "file://$OUTPUT_DIR/index.html"
fi
