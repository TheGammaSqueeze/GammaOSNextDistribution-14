#!/usr/bin/env bash

# Copyright (C) 2022 The Android Open Source Project
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

# A simple script for running the Atest workspace generation script in a
# contained environment.

function check_env_var()
{
  if [ ! -n "${!1}" ] ; then
    echo "Necessary environment variable ${1} missing, did you forget to lunch?"
    exit 1
  fi
}

# Save the location of this script for later.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

# Check for necessary environment variables.
check_env_var "ANDROID_BUILD_TOP"
check_env_var "TARGET_PRODUCT"
check_env_var "TARGET_BUILD_VARIANT"

OUT_DIR=$(mktemp -d)
trap "rm -rf $OUT_DIR" EXIT

# The dist directory is not usually present on clean local machines so create it
# here.
mkdir $OUT_DIR/dist

${ANDROID_BUILD_TOP}/prebuilts/build-tools/linux-x86/bin/nsjail \
  -H android-build \
  -E TARGET_PRODUCT=${TARGET_PRODUCT} \
  -E DIST_DIR=${OUT_DIR}/dist \
  -E TARGET_BUILD_VARIANT=${TARGET_BUILD_VARIANT} \
  -E OUT_DIR=${OUT_DIR} \
  -E ANDROID_BUILD_TOP=${ANDROID_BUILD_TOP} \
  -E HOME=${HOME} \
  -u nobody \
  -g $(id -g) \
  -R / \
  -B /tmp \
  -B $OUT_DIR \
  -B $PWD \
  --disable_clone_newcgroup \
  --cwd $ANDROID_BUILD_TOP \
  -t 0 \
  --proc_rw \
  --rlimit_as soft \
  --rlimit_core soft \
  --rlimit_cpu soft \
  --rlimit_fsize soft \
  --rlimit_nofile soft \
  -q \
  -- \
  ${SCRIPT_DIR}/gen_workspace_archive.sh
