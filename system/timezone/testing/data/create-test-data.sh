#!/usr/bin/env bash
#
# Copyright (C) 2017 The Android Open Source Project
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
# Updates the test data files.
TIMEZONE_DIR=${ANDROID_BUILD_TOP}/system/timezone
TZVERSION_TOOLS_DIR=${TIMEZONE_DIR}/input_tools/version
REFERENCE_DATA_FILES=${TIMEZONE_DIR}/output_data

# Fail on error
set -e

# Test 1: A set of data newer than the system-image data from ${TIMEZONE_DIR}
IANA_VERSION=2030a
TEST_DIR=test1

# Create fake data input files.
./transform-data-files.sh ${REFERENCE_DATA_FILES} ${IANA_VERSION} ./${TEST_DIR}/output_data

# Create the tz version file.
mkdir -p ${TEST_DIR}/output_data/version
${TZVERSION_TOOLS_DIR}/create-tz_version.py \
    -iana_version ${IANA_VERSION} \
    -revision 1 \
    -output_version_file ${TEST_DIR}/output_data/version/tz_version

# Test 2 was about out-dated APK installation. Knowledge about these tests might be hardcoded
# somewhere, so test3 is left as test3, not test2. Renaming might break something w/o obvious
# benefits.

# Test 3: A corrupted set of data like test 1, but with a truncated ICU
# overlay file. This test data set exists because it is (currently) a good way
# to trigger a boot loop which enables easy watchdog and recovery testing.
IANA_VERSION=2030a
TEST_DIR=test3

# Create fake data input files.
./transform-data-files.sh ${REFERENCE_DATA_FILES} ${IANA_VERSION} ./${TEST_DIR}/output_data

# Corrupt icu_tzdata.dat by truncating it
truncate --size 27766 ${TEST_DIR}/output_data/icu_overlay/icu_tzdata.dat

# Create tz version file.
mkdir -p ${TEST_DIR}/output_data/version
${TZVERSION_TOOLS_DIR}/create-tz_version.py \
    -iana_version ${IANA_VERSION} \
    -revision 1 \
    -output_version_file ${TEST_DIR}/output_data/version/tz_version
