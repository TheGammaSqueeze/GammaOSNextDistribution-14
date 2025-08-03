#!/usr/bin/env bash

# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script is dedicated to build the atest integration test in build server.
# To run the test locally, it's recommended to invoke the test via
# `atest atest_integration_tests` or `python atest_integration_tests.py`.
# For usage examples please run `python atest_integration_tests.py --help`.

set -eo pipefail
set -x

# Legacy support for the deprecated argument --artifacts_dir and directory name
for ((i=1; i<=$#; i++)); do
  arg="${@:$i:1}"
  case "$arg" in
    --artifacts_dir)
      export SNAPSHOT_STORAGE_TAR_PATH="${@:$i+1:1}"/atest_integration_tests.tar
      i=$((i+1))
      ;;
    *)
      filtered_args+=("$arg")
      ;;
  esac
done

if [ -n "${DIST_DIR}" ] ; then
  export SNAPSHOT_STORAGE_TAR_PATH=${DIST_DIR}/atest_integration_tests.tar
fi

function get_build_var()
{
  (${PWD}/build/soong/soong_ui.bash --dumpvar-mode --abs $1)
}

if [ ! -n "${ANDROID_BUILD_TOP}" ] ; then
  export ANDROID_BUILD_TOP=${PWD}
fi

# Uncomment the following if verifying locally without running envsetup
# if [ ! -n "${TARGET_PRODUCT}" ] || [ ! -n "${TARGET_BUILD_VARIANT}" ] ; then
#   export \
#     TARGET_PRODUCT=aosp_x86_64 \
#     TARGET_BUILD_VARIANT=userdebug \
#     TARGET_RELEASE="trunk_staging"
# fi

# ANDROID_BUILD_TOP is deprecated, so don't use it throughout the script.
# But if someone sets it, we'll respect it.
cd ${ANDROID_BUILD_TOP:-.}

if [ ! -n "${ANDROID_PRODUCT_OUT}" ] ; then
  export ANDROID_PRODUCT_OUT=$(get_build_var PRODUCT_OUT)
fi

if [ ! -n "${OUT}" ] ; then
  export OUT=$ANDROID_PRODUCT_OUT
fi

if [ ! -n "${ANDROID_HOST_OUT}" ] ; then
  export ANDROID_HOST_OUT=$(get_build_var HOST_OUT)
fi

if [ ! -n "${ANDROID_TARGET_OUT_TESTCASES}" ] ; then
  export ANDROID_TARGET_OUT_TESTCASES=$(get_build_var TARGET_OUT_TESTCASES)
fi

if [ ! -n "${HOST_OUT_TESTCASES}" ] ; then
  export HOST_OUT_TESTCASES=$(get_build_var HOST_OUT_TESTCASES)
  export ANDROID_HOST_OUT_TESTCASES=$HOST_OUT_TESTCASES
fi

if [ ! -n "${ANDROID_JAVA_HOME}" ] ; then
  export ANDROID_JAVA_HOME=$(get_build_var ANDROID_JAVA_HOME)
  export JAVA_HOME=$(get_build_var JAVA_HOME)
fi

export REMOTE_AVD=true

# Use the versioned Python binaries in prebuilts/ for a reproducible
# build with minimal reliance on host tools. Add build/bazel/bin to PATH since
# atest needs 'b'
export PATH=${PWD}/prebuilts/build-tools/path/linux-x86:${PWD}/build/bazel/bin:${PWD}/out/host/linux-x86/bin/:${PATH}

# Use the versioned Java binaries in prebuilds/ for a reproducible
# build with minimal reliance on host tools.
export PATH=${ANDROID_JAVA_HOME}/bin:${PATH}

python3 tools/asuite/atest/integration_tests/atest_integration_tests.py "${filtered_args[@]}" --build --tar_snapshot