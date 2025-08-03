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

#TODO(b/285574832) re-enable Java backend
readonly expected_query="\
//build/bazel/rules/aidl/testing:aidl_interface_test-V1
//build/bazel/rules/aidl/testing:aidl_interface_test-V2
//build/bazel/rules/aidl/testing:aidl_interface_test-latest"

readonly query_paths=(
  "__main__/build/bazel/rules/aidl/testing/aidl_library_V1_produced_by_default_query"
  "__main__/build/bazel/rules/aidl/testing/aidl_library_V2_produced_by_default_query"
  "__main__/build/bazel/rules/aidl/testing/aidl_library_latest_produced_by_default_query"
  #TODO(b/285574832) re-enable Java backend
)
actual_query=""
for runfile in ${query_paths[@]}; do
    this_query="$(cat $(rlocation $runfile))"
    if [ "$actual_query" = "" ]; then
        actual_query=$this_query
    else
        actual_query="\
${actual_query}
${this_query}"
    fi
done

if [ "$expected_query" != "$actual_query" ]; then
    echo "not all interface macro targets were created" &&
    echo "expected query result:" &&
    echo "$expected_query" &&
    echo "actual query result:" &&
    echo "$actual_query" &&
    exit 1
fi
