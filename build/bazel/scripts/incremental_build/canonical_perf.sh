#!/bin/bash
#
# Copyright (C) 2022 The Android Open Source Project
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
# Gather and print top-line performance metrics for the android build
#
readonly TOP="$(realpath "$(dirname "$0")/../../../..")"

usage() {
  cat <<EOF
usage: $0 [-l LOG_DIR] [BUILD_TYPES]
  -l    LOG_DIR should be outside of source tree, including not in out/,
        because the whole tree will be cleaned during testing.
example:
 $0 soong prod
EOF
  exit 1
}

declare -a build_types
while getopts "l:" opt; do
  case "$opt" in
  l) log_dir=$OPTARG ;;
  ?) usage ;;
  esac
done
shift $((OPTIND - 1))
readonly -a build_types=("$@")

log_dir=${log_dir:-"$TOP/../timing-$(date +%b%d-%H%M)"}
log_dir=$(realpath "$log_dir")

function build() {
  date
  set -x
  if ! "$TOP/build/bazel/scripts/incremental_build/incremental_build.sh" \
    --ignore-repo-diff --log-dir "$log_dir" \
    ${build_types:+--build-types "${build_types[@]}"} \
    "$@"; then
    echo "See logs for errors"
    exit 1
  fi
  set +x
}
build --cujs clean 'no change' 'create bionic/unreferenced.txt' 'modify Android.bp' -- droid
build --cujs 'modify bionic/.*/stdio.cpp' --append-csv libc
build --cujs 'modify .*/adb/daemon/main.cpp' --append-csv adbd
build --cujs 'modify frameworks/.*/View.java' --append-csv framework
build --cujs 'modify frameworks/.*/Settings.java' --append-csv framework-minus-apex
