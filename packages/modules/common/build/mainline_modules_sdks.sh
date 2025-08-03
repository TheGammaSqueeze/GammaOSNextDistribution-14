#!/bin/bash -ex
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

function init() {
  declare -ga ARGV
  while (($# > 0)); do
    case $1 in
    --py3script)
      declare -gr py3script="$2"
      shift 2
      ;;
    *)
      ARGV+=("$1")
      shift 1
      ;;
    esac
  done
  readonly ARGV

  if [ -z "${py3script}" ]; then
    declare -gr py3script="packages/modules/common/build/mainline_modules_sdks.py"
  fi
}

function main() {
  if [ ! -e "build/make/core/Makefile" ]; then
    echo "$0 must be run from the top of the tree"
    exit 1
  fi

  # Assign to a variable and eval that, since bash ignores any error status from
  # the command substitution if it's directly on the eval line.
  vars="$(TARGET_PRODUCT='' build/soong/soong_ui.bash --dumpvars-mode \
    --vars="BUILD_NUMBER DIST_DIR OUT_DIR")"
  eval "${vars}"

  # Building with --soong-only and module products requires build_number.txt for
  # some targets.
  # Command to populate {OUT_DIR}/soong/build_number.txt.
  build/soong/soong_ui.bash --make-mode nothing

  # Delegate the SDK generation to the python script. Use the python version
  # provided by the build to ensure consistency across build environments.
  export DIST_DIR OUT_DIR

  # Make sure that the ANDROID_BUILD_TOP (which is the same as the current
  # directory as checked above) is exported to Python.
  export ANDROID_BUILD_TOP="${PWD}"

  # The path to this tool is the .sh script that lives alongside the .py script.
  TOOL_PATH="${py3script%.py}.sh"
  prebuilts/build-tools/linux-x86/bin/py3-cmd -u "${py3script}" \
      --tool-path "${TOOL_PATH}" \
      "$@"
}

init "$@"
# The wacky ${foo[@]+"${foo[@]}"}, makes bash correctly pass nothing when an
# array is empty (necessary prior to bash 4.4).
main ${ARGV[@]+"${ARGV[@]}"}
