#!/usr/bin/env bash

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Tests that generated targets have correct srcs attribute.

. "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"

readonly expected_query_v1="\
//build/bazel/rules/aidl/testing:Test.aidl
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/1/.hash
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/1/android/net/Test.aidl
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/1/android/net/Test2.aidl
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/1/android/net/Test3.aidl
//system/tools/aidl/build:message_check_equality.txt"
readonly expected_query_v2="\
//build/bazel/rules/aidl/testing:Test.aidl
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/2/.hash
//build/bazel/rules/aidl/testing:aidl_api/aidl_interface_test/2/Test2Only.aidl
//system/tools/aidl/build:message_check_equality.txt"

readonly query_path_v1="__main__/build/bazel/rules/aidl/testing/generated_target_V1_has_correct_srcs_query"
readonly query_path_v2="__main__/build/bazel/rules/aidl/testing/generated_target_V2_has_correct_srcs_query"
readonly actual_query_v1=$(cat "$(rlocation $query_path_v1)")
readonly actual_query_v2=$(cat "$(rlocation $query_path_v2)")

if [ "$expected_query_v1" != "$actual_query_v1" ]; then
    echo "aidl_interface generated target V1 has incorrect srcs." &&
    echo "expected:" &&
    echo "$expected_query_v1" &&
    echo "actual:" &&
    echo "$actual_query_v1" &&
    exit 1
fi

if [ "$expected_query_v2" != "$actual_query_v2" ]; then
    echo "aidl_interface generated target V2 has incorrect srcs." &&
    echo "expected:" &&
    echo "$expected_query_v2" &&
    echo "actual:" &&
    echo "$actual_query_v2" &&
    exit 1
fi
