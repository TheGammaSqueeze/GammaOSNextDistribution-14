#!/usr/bin/env bash

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

ATEST_SRC="${ANDROID_BUILD_TOP}/tools/asuite/atest/atest.py"
WORKSPACE_ROOT="${ANDROID_BUILD_TOP}/out/atest_bazel_workspace/"
BAZEL_BINARY="${ANDROID_BUILD_TOP}/prebuilts/bazel/linux-x86_64/bazel"

function create_bazel_workspace(){
    source "${ANDROID_BUILD_TOP}/build/envsetup.sh"
    cd ${ANDROID_BUILD_TOP}
    python ${ATEST_SRC} --bazel-mode atest_unittests --build
}

function bazel_query(){
    cd ${WORKSPACE_ROOT}
    echo "${BAZEL_BINARY} query ${1}"
    ${BAZEL_BINARY} query ${1}
}

function bazel_test(){
    cd ${WORKSPACE_ROOT}
    echo "${BAZEL_BINARY} test ${1}"
    ${BAZEL_BINARY} test ${1}
}

create_bazel_workspace
bazel_query "deps(//tools/asuite/atest:atest_unittests_host)"
bazel_test //tools/asuite/atest:atest_unittests_host
