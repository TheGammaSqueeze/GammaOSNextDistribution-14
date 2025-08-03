#!/bin/bash -e
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

#
# The script to run locally to re-generate global allowed list of dependencies
# for updatable modules.

if [ ! -e "build/envsetup.sh" ]; then
  echo "ERROR: $0 must be run from the top of the tree"
  exit 1
fi

source build/envsetup.sh > /dev/null || exit 1

readonly OUT_DIR=$(get_build_var OUT_DIR)

readonly ALLOWED_DEPS_FILE="packages/modules/common/build/allowed_deps.txt"
readonly NEW_ALLOWED_DEPS_FILE="${OUT_DIR}/soong/apex/depsinfo/new-allowed-deps.txt"

# If the script is run after droidcore failure, ${NEW_ALLOWED_DEPS_FILE}
# should already be built. If running the script manually, make sure it exists.
m "${NEW_ALLOWED_DEPS_FILE}" -j

cat > "${ALLOWED_DEPS_FILE}" << EndOfFileComment
# A list of allowed dependencies for all updatable modules.
#
# The list tracks all direct and transitive dependencies that end up within any
# of the updatable binaries; specifically excluding external dependencies
# required to compile those binaries. This prevents potential regressions in
# case a new dependency is not aware of the different functional and
# non-functional requirements being part of an updatable module, for example
# setting correct min_sdk_version.
#
# To update the list, run:
# repo-root$ packages/modules/common/build/update-apex-allowed-deps.sh
#
# See go/apex-allowed-deps-error for more details.
# TODO(b/157465465): introduce automated quality signals and remove this list.
EndOfFileComment

cat "${NEW_ALLOWED_DEPS_FILE}" >> "${ALLOWED_DEPS_FILE}"
