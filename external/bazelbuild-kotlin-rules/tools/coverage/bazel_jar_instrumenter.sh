#!/bin/bash
# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

#
set -eu

readonly current_dir="$0.runfiles"
readonly in="$1"
readonly out="$2"
shift 2

readonly tmp_dir="$(mktemp -d)"
trap "rm -rf ${tmp_dir}" EXIT

readonly jacoco="$(ls ${current_dir}/rules_kotlin/bazel/jacoco_cli)"
readonly jar="$(which jar)"

# Unzip input Jar and run JaCoCo over it
mkdir "${tmp_dir}/classes"
mkdir "${tmp_dir}/instrumented"
unzip -qq -d "${tmp_dir}/classes" "${in}"
"${jacoco}" instrument "${tmp_dir}/classes" --dest "${tmp_dir}/instrumented" >/dev/null

# Rename input .class files to .class.uninstrumented
find "${tmp_dir}/classes" -name '*.class' -exec mv {} {}.uninstrumented \;

# Zip all the files together
"${jar}" cf "${out}" -C "${tmp_dir}/instrumented" .
"${jar}" uf "${out}" -C "${tmp_dir}/classes" .
"${jar}" uf "${out}" "$@"
