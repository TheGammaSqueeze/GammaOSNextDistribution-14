#!/bin/bash
#
# Copyright 2021 Google Inc. All rights reserved.
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

set -euo pipefail

source "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"

# Smoke test to check that the stripped libc.so is smaller than the unstripped one.
function test_libc_stripping_basic() {
    local readonly base="__main__/bionic/libc"
    local readonly stripped_path="${base}/libc/libc.so"
    local readonly unstripped_path="${base}/liblibc_unstripped.so"
    local stripped="$(rlocation $stripped_path)"
    local unstripped="$(rlocation $unstripped_path)"

    if [ ! -e "$stripped" ]; then
      >&2 echo "Missing stripped file; expected '$stripped_path'; got '$stripped'"
      exit 2
    fi
    if [ ! -e "$unstripped" ]; then
      >&2 echo "Missing unstripped file; expected '$unstripped_path'; got '$unstripped'"
      exit 2
    fi

    local stripped_size=$(stat -L -c %s "${stripped}")
    local unstripped_size=$(stat -L -c %s "${unstripped}")

    # Check that the unstripped size is not greater or equal to the stripped size.
    if [ "${stripped_size}" -ge "${unstripped_size}"  ]; then
        echo "Expected the size of stripped libc.so to be strictly smaller than the unstripped one."
        exit 1
    fi
}

test_libc_stripping_basic
