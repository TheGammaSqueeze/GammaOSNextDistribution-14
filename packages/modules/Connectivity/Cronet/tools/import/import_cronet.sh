#!/bin/bash

# Copyright 2023 Google Inc. All rights reserved.
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

# Script to invoke copybara locally to import Cronet into Android.
# Inputs:
#  Environment:
#   ANDROID_BUILD_TOP: path the root of the current Android directory.
#  Arguments:
#   -l rev: The last revision that was imported.
#  Optional Arguments:
#   -n rev: The new revision to import.
#   -f: Force copybara to ignore a failure to find the last imported revision.

set -e -x

OPTSTRING=fl:n:

usage() {
    cat <<EOF
Usage: import_cronet.sh -n new-rev [-l last-rev] [-f]
EOF
    exit 1
}

COPYBARA_FOLDER_ORIGIN="/tmp/copybara-origin"

#######################################
# Create local upstream-import branch in external/cronet.
# Globals:
#   ANDROID_BUILD_TOP
# Arguments:
#   none
#######################################
setup_upstream_import_branch() {
    local git_dir="${ANDROID_BUILD_TOP}/external/cronet"

    (cd "${git_dir}" && git fetch aosp upstream-import:upstream-import)
}

#######################################
# Setup folder.origin for copybara inside /tmp
# Globals:
#   COPYBARA_FOLDER_ORIGIN
# Arguments:
#   new_rev, string
#######################################
setup_folder_origin() (
    local _new_rev=$1
    mkdir -p "${COPYBARA_FOLDER_ORIGIN}"
    cd "${COPYBARA_FOLDER_ORIGIN}"

    if [ -d src ]; then
        (cd src && git fetch --tags && git checkout "${_new_rev}")
    else
        # For this to work _new_rev must be a branch or a tag.
        git clone --depth=1 --branch "${_new_rev}" https://chromium.googlesource.com/chromium/src.git
    fi


    cat <<EOF >.gclient
solutions = [
  {
    "name": "src",
    "url": "https://chromium.googlesource.com/chromium/src.git",
    "managed": False,
    "custom_deps": {},
    "custom_vars": {},
  },
]
target_os = ["android"]
EOF
    cd src
    # Set appropriate gclient flags to speed up syncing.
    gclient sync \
        --no-history \
        --shallow \
        --delete_unversioned_trees
)

#######################################
# Runs the copybara import of Chromium
# Globals:
#   ANDROID_BUILD_TOP
#   COPYBARA_FOLDER_ORIGIN
# Arguments:
#   last_rev, string or empty
#   force, string or empty
#######################################
do_run_copybara() {
    local _last_rev=$1
    local _force=$2

    local -a flags
    flags+=(--git-destination-url="file://${ANDROID_BUILD_TOP}/external/cronet")
    flags+=(--repo-timeout 3m)

    # buildtools/third_party/libc++ contains an invalid symlink
    flags+=(--folder-origin-ignore-invalid-symlinks)
    flags+=(--git-no-verify)

    if [ ! -z "${_force}" ]; then
        flags+=(--force)
    fi

    if [ ! -z "${_last_rev}" ]; then
        flags+=(--last-rev "${_last_rev}")
    fi

    /google/bin/releases/copybara/public/copybara/copybara \
        "${flags[@]}" \
        "${ANDROID_BUILD_TOP}/packages/modules/Connectivity/Cronet/tools/import/copy.bara.sky" \
        import_cronet "${COPYBARA_FOLDER_ORIGIN}/src"
}

while getopts $OPTSTRING opt; do
    case "${opt}" in
        f) force=true ;;
        l) last_rev="${OPTARG}" ;;
        n) new_rev="${OPTARG}" ;;
        ?) usage ;;
        *) echo "'${opt}' '${OPTARG}'"
    esac
done

if [ -z "${new_rev}" ]; then
    echo "-n argument required"
    usage
fi

setup_upstream_import_branch
setup_folder_origin "${new_rev}"
do_run_copybara "${last_rev}" "${force}"

