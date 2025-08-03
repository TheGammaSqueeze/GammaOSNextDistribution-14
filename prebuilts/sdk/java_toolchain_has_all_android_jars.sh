#!/usr/bin/env bash

# Copyright (C) 2023 The Android Open Source Project
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

# Tests that the java toolchain depends on all prebuilt android.jar.

source "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"

function check_toolchain_refers_to_all_prebuilt_files() {
  actual_query_name=$1
  file=$2
  readonly package_path="__main__/prebuilts/sdk"

  actual_query=$(cat $(rlocation $package_path/$actual_query_name)|sed 's%//\|:%/%g')
  expected_query=$(ls $(rlocation $package_path)/**/*/${file}|sed s%$(rlocation "__main__")%%;)

  if [ "$expected_query" != "$actual_query" ]; then
    toolchain_type=$(echo ${actual_query_name}|sed s%_toolchain.*%%)
    echo "device ${toolchain_type} toolchain does not refer to all ${file} under prebuilts/sdk." &&
      echo "The toolchain depends on:" &&
      echo $actual_query &&
      echo "The directory structure has:" &&
      echo $expected_query &&
      exit 1
  fi;
}

check_toolchain_refers_to_all_prebuilt_files "java_toolchain_android_jar_deps" "android.jar"
check_toolchain_refers_to_all_prebuilt_files "android_sdk_toolchain_android_jar_deps" "android.jar"
check_toolchain_refers_to_all_prebuilt_files "java_toolchain_core_jar_deps" "core-for-system-modules.jar"
check_toolchain_refers_to_all_prebuilt_files "android_sdk_toolchain_core_jar_deps" "core-for-system-modules.jar"
check_toolchain_refers_to_all_prebuilt_files "android_sdk_toolchain_framework_aidl_deps" "framework.aidl"
