#!/bin/bash -eu

set -o pipefail

# Copyright (C) 2023 The Android Open Source Project
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


# This test suite contains a number of end to end tests verifying Bazel's integration
# with Soong in Android builds.

TOP="$(readlink -f "$(dirname "$0")"/../../..)"
"$TOP/build/bazel/ci/determinism_test.sh"
"$TOP/build/bazel/ci/mixed_mode_toggle.sh"
"$TOP/build/bazel/ci/ensure_allowlist_integrity.sh"
