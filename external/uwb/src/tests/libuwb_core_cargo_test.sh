#!/usr/bin/env sh

# Copyright 2022 The Android Open Source Project
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

# The directory of this script.
ROOT_DIR="$(dirname "$(realpath "$0")")"
# The temporary artifacts directory.
TEMP_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "${TEMP_DIR}"
}

# Unpack the artifacts zip.
if ! unzip "${ROOT_DIR}/uwb_core_artifacts.zip" -d ${TEMP_DIR}; then
  echo "Failed to unzip the uwb_core_artifacts.zip"
  cleanup
  exit 1
fi

# Install the cargo inside TEMP_DIR.
export RUSTUP_HOME="${TEMP_DIR}/.rustup"
export CARGO_HOME="${TEMP_DIR}/.cargo"
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
    sh -s -- -y --no-modify-path

# Build the uwb_core source code.
cd "${TEMP_DIR}"
"${CARGO_HOME}/bin/cargo" test -vv
return_code=$?

cleanup
exit "${return_code}"
