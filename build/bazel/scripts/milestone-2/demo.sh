#!/bin/bash
#
# Copyright 2018 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ---
# Milestone 2 demo script to convert Android.bp modules in //bionic/libc to
# buildable Bazel BUILD targets. Run "./demo help" for more info.
# ---

set -euo pipefail

function help() {
  cat <<EOF
A demo script for the Android.bp to BUILD file converter.

Usage:

  ./demo.sh generate -- runs the bp2build converter to generate BUILD files from Android.bp files.
  ./demo.sh query -- runs the bazel query command for all targets in //bionic/libc, recursively.
  ./demo.sh build -- runs the bazel build command for all targets in //bionic/libc, recursively.
  ./demo.sh full -- runs the generate, query and build steps in sequence.
  ./demo.sh help -- prints this message.

EOF
}

# We're in <root>/build/bazel/scripts/milestone-2
AOSP_ROOT="$(dirname $0)/../../../.."

RED="\031[0;32m"
GREEN="\033[0;32m"
RESET="\033[0m"

function error() {
  local message=$1; shift;
  echo -e "${RED}ERROR[Milestone 2 Demo]: $message${RESET}"
}

function log() {
  local message=$1; shift;
  echo -e "${GREEN}INFO[Milestone 2 Demo]: $message${RESET}"
}

# Ensure that this script uses the checked-in Bazel binary.
function bazel() {
  "${AOSP_ROOT}/build/bazel/bin/bazel" "$@"
}

# Run the bp2build converter to generate BUILD files into out/soong/bp2build.
function generate() {
  log "Running the bp2build converter.."
  "${AOSP_ROOT}/build/soong/soong_ui.bash" --make-mode --skip-soong-tests bp2build
  log "Successfully generated BUILD files in out/soong/bp2build."
}

# Run bazel query for the generated targets in the //bionic/libc package.
function query-bionic-package() {
  log "Running bazel query //bionic/..."
  bazel query //bionic/...
}

# Use bazel to build the generated targets in the //bionic/libc package.
function build-bionic-package() {
  log "Running bazel build //bionic/..."
  bazel build --platforms //build/bazel/platforms:android_x86 //bionic/...
  bazel build --platforms //build/bazel/platforms:android_x86_64 //bionic/...
  bazel build --platforms //build/bazel/platforms:android_arm //bionic/...
  bazel build --platforms //build/bazel/platforms:android_arm64 //bionic/...
}

function run() {
  action=${1:-full}

  case $action in
    "help")
      help
      ;;
    "generate")
      generate
      ;;
    "query")
      query-bionic-package
      ;;
    "build")
      build-bionic-package
      ;;
    "full")
      generate
      query-bionic-package
      build-bionic-package
      ;;
    *)
      error "Unknown action: $action"
      help
      exit 1
  esac

  log "($action) done."
}

run $@
