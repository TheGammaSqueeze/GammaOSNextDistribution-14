#!/bin/bash
#
# Copyright (C) 2022 The Android Open Source Project
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

# Declare a map of relative paths from the libcore directory to checkstyle
# configuration files to apply.
declare -A PATH_TO_CONFIG=(
    [benchmarks]=tools/checkstyle/not-gpl.xml
    [dalvik]=tools/checkstyle/aosp-copyright.xml
    [dom]=tools/checkstyle/w3c-copyright.xml
    [harmony-tests]=tools/checkstyle/not-gpl.xml
    [json]=tools/checkstyle/aosp-copyright.xml
    [jsr166-tests]=tools/checkstyle/jsr166-public-domain.xml
    [libart]=tools/checkstyle/aosp-copyright.xml
    [luni/annotations]=tools/checkstyle/not-gpl.xml
    [luni/src/main/java]=tools/checkstyle/not-gpl.xml
    [luni/src/module]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/androidsdk34]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/androidsdkcurrent]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/annotations/src/libcore/tests]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/dex_src/libcore]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/etc]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/filesystems]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/crossvmtest]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/android]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/build]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/dalvik/system]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/highmemorytest]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/java]=tools/checkstyle/not-gpl.xml
    [luni/src/test/java/libcore/javax/crypto]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/javax/net]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/javax/security]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/javax/sql]=tools/checkstyle/asf-copyright.xml
    [luni/src/test/java/libcore/javax/xml]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/jdk]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/libcore]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/sun]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/libcore/xml]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/org/apache/harmony]=tools/checkstyle/not-gpl.xml
    [luni/src/test/java/tests/com/android/org]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/java/lang]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/java/nio]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/java/security]=tools/checkstyle/asf-copyright.xml
    [luni/src/test/java/tests/java/sql]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/javax/crypto]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/org/w3c]=tools/checkstyle/w3c-copyright.xml
    [luni/src/test/java/tests/security]=tools/checkstyle/not-gpl.xml
    [luni/src/test/java/tests/support]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java/tests/targets]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java17language]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java11language]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java9compatibility]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/java9language]=tools/checkstyle/aosp-copyright.xml
    [luni/src/test/parameter_metadata]=tools/checkstyle/aosp-copyright.xml
    [metrictests]=tools/checkstyle/aosp-copyright.xml
    [ojluni/annotations]=tools/checkstyle/ojluni-src-main-header.xml
    [ojluni/src/lambda]=tools/checkstyle/ojluni-src-main-header.xml
    [ojluni/src/main]=tools/checkstyle/ojluni-src-main-header.xml
    [ojluni/src/test]=tools/checkstyle/ojluni-src-test-header.xml
    [ojluni/src/tools]=tools/checkstyle/ojluni-src-main-header.xml
    [support/src/test/java/libcore]=tools/checkstyle/aosp-copyright.xml
    [support/src/test/java/org]=tools/checkstyle/asf-copyright.xml
    [support/src/test/java/tests]=tools/checkstyle/not-gpl.xml
    [test-rules]=tools/checkstyle/aosp-copyright.xml
    [tools]=tools/checkstyle/aosp-copyright.xml
    [xml]=tools/checkstyle/not-gpl.xml
)

function fatal_error() {
    echo -e "${@}" >&2
    exit 1
}

function warn() {
    echo -e "${@}" >&2
}

# Function to expand a user provided directory into directories that appear in the
# configuration map.
# Usage: checkstyle_of_paths <path1> [... <pathN>]
function expand_directories() {
    local requested_path name
    for requested_path in "${@}" ; do
        if [ -d "${requested_path}" ]; then
            requested_path=${requested_path%/}
            for prefix in "${!PATH_TO_CONFIG[@]}"; do
                if [[ "${prefix}/" = "${requested_path}"/* ]]; then
                    echo -n " ${prefix}"
                fi
            done
        else
            echo -n " ${requested_path}"
        fi
    done
}

# Function to apply path specific checkstyle configurations to a list of specified paths.
# Usage: checkstyle_of_paths <path1> [... <pathN>]
function checkstyle_of_paths() {
    local matching_files prefix requested_path requested_paths
    declare -A visited

    requested_paths=$(expand_directories "${@}")
    for prefix in "${!PATH_TO_CONFIG[@]}"; do
        matching_files=()
        for requested_path in ${requested_paths} ; do
            if [[ "${requested_path}" = "${prefix}" || "${requested_path}" = "${prefix}"/* ]] ; then
                matching_files+=("${requested_path}")
                visited[${requested_path}]="yes"
            fi
        done

        if [ "${matching_files[*]}" = "" ]; then
            continue
        fi

        echo "Applying ${PATH_TO_CONFIG[${prefix}]} to ${matching_files[@]}"
        ${CHECKSTYLE} --config_xml "${PATH_TO_CONFIG[${prefix}]}" --file "${matching_files[@]}" \
            || exit 1
    done

    for requested_path in "${@}" ; do
        if [ -z "${visited[${requested_path}]}" ] ; then
            warn "WARNING: No checkstyle configuration covers ${requested_path}"
        fi
    done
}

# Function to check that the paths (keys) in PATH_TO_CONFIG exist.
function check_directories_with_configs_exist() {
    local prefix prefix_no_slash

    for prefix in "${!PATH_TO_CONFIG[@]}"; do
        prefix_no_slash=${prefix%/}
        if [ "${prefix}" != "${prefix_no_slash}" ]; then
            fatal_error "Directory name should not end with '/': ${prefix}"
        fi
        if [ ! -d "${prefix}" ] ; then
            fatal_error "Bad prefix path. Directory does not exist: ${prefix}"
        fi
    done
}

# Function to check that all Java files have an associated checkstyle configuration.
function check_files_have_associated_configs() {
    local java_file prefix has_match

    for java_file in $(find . -type f -name '*.java' | sed -e 's@^[.]/@@'); do
        has_match=""
        for prefix in "${!PATH_TO_CONFIG[@]}"; do
            if [[ "${java_file}" = "${prefix}"/* ]]; then
                has_match="y"
                break
            fi
        done

        if [ -z "${has_match}" ]; then
            fatal_error "${java_file} has no checkstyle configuration."
        fi
    done
}

# Main function that applies checkstyle to the files provided as arguments or to the all
# the paths in libcore that have checkstyle configurations (if no arguments are provided).
# Usage: main [<path1> ... <pathN>]
function main() {
    if [ -n "$REPO_ROOT" ] ; then
        ROOT=${REPO_ROOT}
    elif [ -n "$ANDROID_BUILD_TOP" ]; then
        ROOT=${ANDROID_BUILD_TOP}
    else
        fatal_error "This script requires REPO_ROOT or ANDROID_BUILD_TOP to be defined." \
                    "\nRun \`lunch\` and try again."
    fi

    CHECKSTYLE=${ROOT}/prebuilts/checkstyle/checkstyle.py
    if [ ! -x ${CHECKSTYLE} ]; then
        fatal_error "Checkstyle is not present or is not executable: ${CHECKSTYLE}"
    fi

    cd "${ROOT}/libcore"

    check_directories_with_configs_exist
    check_files_have_associated_configs

    if [ $# = 0 ] ; then
        checkstyle_of_paths "${!PATH_TO_CONFIG[@]}"
    else
        checkstyle_of_paths "${@}"
    fi
}

main "${@}"
