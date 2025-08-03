#!/usr/bin/env bash

# Copyright (C) 2020 The Android Open Source Project
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

set -ex

# TODO(b/156942077): when this test was converted to atest, we lost the ability
# to see test results on stdout, and we can't print out the gtest results on
# stderr because it breaks python test parsing. So, this file is added to help
# run integration tests.

$ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode \
    MODULES-IN-system-tools-aidl

adb root
adb wait-for-device
adb sync data
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_service/*/aidl_test_java_service.jar \
    /data/framework/aidl_test_java_service.jar
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_client/*/aidl_test_java_client.jar \
    /data/framework/aidl_test_java_client.jar
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_service_sdk29/*/aidl_test_java_service_sdk29.jar \
    /data/framework/aidl_test_java_service_sdk29.jar
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_client_sdk29/*/aidl_test_java_client_sdk29.jar \
    /data/framework/aidl_test_java_client_sdk29.jar
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_service_sdk1/*/aidl_test_java_service_sdk1.jar \
    /data/framework/aidl_test_java_service_sdk1.jar
adb push \
    ${ANDROID_PRODUCT_OUT}/testcases/aidl_test_java_client_sdk1/*/aidl_test_java_client_sdk1.jar \
    /data/framework/aidl_test_java_client_sdk1.jar

${ANDROID_BUILD_TOP}/system/tools/aidl/tests/aidl_integration_test.py
