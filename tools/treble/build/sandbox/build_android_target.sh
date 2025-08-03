#!/bin/bash

# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

readonly ANDROID_TARGET=$1
readonly BUILD_DIR=$2
shift
shift
readonly BUILD_COMMAND="$@"

if [[ -z "${ANDROID_TARGET}" ]]; then
  echo "error: Android target not set"
  exit 1
fi

if [[ -z "${BUILD_DIR}" ]]; then
  echo "error: Build directory not set"
  exit 1
fi

if [[ -z "${BUILD_COMMAND}" ]]; then
  echo "error: Build command not set"
  exit 1
fi

# If there is an error, exit right away instead of continuing. For example,
# lunch could fail. If so, there is no point in continuing the build.

set -e

BUILD_COMMAND_ARRAY=($BUILD_COMMAND)
for i in ${BUILD_COMMAND_ARRAY[@]};
do
  if [[ $i =~ ^[A-Z_][A-Z0-9_]*= ]];
  then
    echo "build_android_target.sh: export $i";
    export $i;
  fi;
done;

echo "build_android_target.sh: source build/envsetup.sh"
source build/envsetup.sh
echo "build_android_target.sh: lunch $ANDROID_TARGET"
lunch "$ANDROID_TARGET"
echo "build_android_target.sh: cd $BUILD_DIR"
cd "$BUILD_DIR"

# However, the build command itself cannot use set -e. I haven't figured this
# out yet, but something in the build command causes early exit for some
# targets.

set +e

echo "build_android_target.sh: $BUILD_COMMAND"
eval $BUILD_COMMAND
BUILD_COMMAND_EXIT_VALUE=$?

# Collect RBE metrics if enabled
if [[ -n "${USE_RBE}" && -n "${RBE_DIR}" ]]; then
  echo "build_android_target.sh: $RBE_DIR/dumpstats"
  $RBE_DIR/dumpstats
fi

exit $BUILD_COMMAND_EXIT_VALUE
