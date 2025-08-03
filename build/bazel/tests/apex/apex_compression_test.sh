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
set -xeuo pipefail

readonly arg_apex_filepath=$1
arg_compressed=false
[ $# -eq 2 ] && [ "$2" = "compressed" ] && arg_compressed=true

readonly ZIPPER=$(rlocation bazel_tools/tools/zip/zipper/zipper)
readonly -a APEX_FILES=(
  "apex_manifest.pb"
  "AndroidManifest.xml"
  "apex_payload.img"
  "apex_pubkey"
  "assets/NOTICE.html.gz"
  "META-INF/CERT\.SF"
  "META-INF/CERT\.RSA"
  "META-INF/MANIFEST\.MF"
)
readonly -a CAPEX_FILES=(
  "apex_manifest.pb"
  "AndroidManifest.xml"
  "original_apex"
  "apex_pubkey"
  "META-INF/CERT\.SF"
  "META-INF/CERT\.RSA"
  "META-INF/MANIFEST\.MF"
)

# Check if apex file contains specified files
function apex_contains_files() {
  local apex_filepath=$1
  shift
  local expected_files=("$@")
  local apex_entries=$($ZIPPER v "$apex_filepath")
  for file in "${expected_files[@]}"; do
    if ! echo -e "$apex_entries" | grep "$file"; then
      echo "Failed to find file $file in $apex_filepath"
      exit 1
    fi
  done
}

# Test compressed apex file required files.
function test_capex_contains_required_files() {
  if [ "${arg_apex_filepath: -6}" != ".capex" ]; then
    echo "@arg_apex_filepath does not have .capex as extension."
    exit 1
  fi
  apex_contains_files "$arg_apex_filepath" "${CAPEX_FILES[@]}"

  # Check files in original_apex extracted from the compressed apex file
  local apex_file_dir=$(dirname "$arg_apex_filepath")
  local extracted_capex=$(mktemp -d -p "$apex_file_dir")
  $ZIPPER x "$arg_apex_filepath" -d "$extracted_capex"
  apex_contains_files "$extracted_capex/original_apex" "${APEX_FILES[@]}"
  rm -rf "${extracted_capex}"
}

# Test apex file contains required files
function test_apex_contains_required_files() {
  if [ "${arg_apex_filepath: -5}" != ".apex" ]; then
    echo "@arg_apex_filepath does not have .apex as extension."
    exit 1
  fi
  apex_contains_files "$arg_apex_filepath" "${APEX_FILES[@]}"
}

if [ $arg_compressed == true ]; then
  test_capex_contains_required_files
else
  test_apex_contains_required_files
fi

echo "Passed all test cases."