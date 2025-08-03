#!/bin/bash

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

BAZEL=build/bazel/bin/bazel

function main() {
  if [ ! -e "build/make/core/Makefile" ]; then
    echo "$0 must be run from the top of the Android source tree."
    exit 1
  fi
  "build/soong/soong_ui.bash" --build-mode --all-modules --dir="$(pwd)" bp2build
  ${BAZEL} build //build/bazel/vendor/google:mainline_modules --config=bp2build
}

main
