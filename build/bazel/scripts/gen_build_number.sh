#!/bin/bash -e
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script used as --workspace_status_command.
# Must execute at the root of workspace.
# https://docs.bazel.build/versions/main/command-line-reference.html#flag--workspace_status_command

# Common script utilities
source $(cd $(dirname $BASH_SOURCE) &> /dev/null && pwd)/../../make/shell_utils.sh
require_top


if [[ ! -f "WORKSPACE" ]]; then
    echo "ERROR: gen_build_number.sh must be executed at the root of Bazel workspace." >&2
    exit 1
fi


# TODO(b/228463719): figure out how to get the path properly.
BUILD_NUMBER_FILE=$(getoutdir)/soong/build_number.txt
if [[ -f ${BUILD_NUMBER_FILE} ]]; then
    BUILD_NUMBER=$(cat ${BUILD_NUMBER_FILE})
else
    BUILD_NUMBER=eng.${USER:0:6}.$(date '+%Y%m%d.%H%M%S')
fi

echo "BUILD_NUMBER ${BUILD_NUMBER}"
