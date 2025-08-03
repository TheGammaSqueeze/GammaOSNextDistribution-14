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
#
#!/bin/bash
# This script builds two Bazel classes which output the default checks in
# the Errorprone versions in Soong and Bazel.
# It then runs a python script that generates a file of flags that can be
# applied to Bazel that will ensure Bazel has the same default bugprone patterns
# as Soong's version of Errorprone.
#
#
# Usage: errorProneCompatibilityFlags.sh  <path to repository root>


BASEDIR=$1
TEMP_SOONG_DIR=$(mktemp -d)
TEMP_BAZEL_DIR=$(mktemp -d)
SOONGFILE=$(mktemp)
BAZELFILE=$(mktemp)

cd "${BASEDIR}" || { echo "Error: directory not found ${BASEDIR}"; exit 1; }

build/bazel/bin/b build //build/bazel/rules/java/errorprone:PrintSoongClasses --noshow_loading_progress --noshow_progress
"${BASEDIR}/bazel-bin/build/bazel/rules/java/errorprone/PrintSoongClasses" > $SOONGFILE

build/bazel/bin/b build //build/bazel/rules/java/errorprone:PrintBazelClasses --noshow_loading_progress --noshow_progress
"${BASEDIR}/bazel-bin/build/bazel/rules/java/errorprone/PrintBazelClasses" > $BAZELFILE

prebuilts/build-tools/path/linux-x86/python3 "${BASEDIR}/build/bazel/rules/java/errorprone/generateErrorProneCompatibilityFlags.py" --soong_file=$SOONGFILE --bazel_file=$BAZELFILE


