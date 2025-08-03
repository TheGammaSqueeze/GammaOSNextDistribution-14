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

set -e

REPO_ROOT="$1"

FILES_TO_CHECK=()
for i in "${@:2}"; do
  if [[ $i == *_failures.txt ]]; then
    FILES_TO_CHECK+=($i)
  fi
done

# if no libcore_*_failures.txt files were changed
if [ ${#FILES_TO_CHECK[@]} -eq 0 ]; then
  exit 0
fi

TMP_DIR=`mktemp -d`
# check if tmp dir was created
if [[ ! "$TMP_DIR" || ! -d "$TMP_DIR" ]]; then
  echo "Could not create temp dir"
  exit 1
fi

function cleanup {
  rm -rf "$TMP_DIR"
}

# register the cleanup function to be called on the EXIT signal
trap cleanup EXIT

GSON_JAR="${REPO_ROOT}/external/caliper/lib/gson-2.2.2.jar"

javac --class-path "$GSON_JAR" "${REPO_ROOT}/art/tools/PresubmitJsonLinter.java" -d "$TMP_DIR"
java --class-path "$TMP_DIR:$GSON_JAR" PresubmitJsonLinter "${FILES_TO_CHECK[@]}"
