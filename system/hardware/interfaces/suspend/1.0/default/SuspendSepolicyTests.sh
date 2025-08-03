#!/bin/bash

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

# Check that sysfs wakeup nodes are correctly labeled by the
# device's sepolicy.

wakeup_attr="u:object_r:sysfs_wakeup:s0"
wakeup_paths=()
unlabeled_paths=()

get_wakeup_paths() {
    adb shell 'paths=()
        wakeup_dir=/sys/class/wakeup
        cd $wakeup_dir
        for file in $wakeup_dir/*; do
            paths+=( $(realpath $file) )
        done
        echo "${paths[@]}"'
}

has_wakeup_attr() { #path
    adb shell ls -dZ "$1" | grep -q "$wakeup_attr"
    return $?
}

check_wakeup_dup() { # wakeup_path
    ret=1
    for path in "${unlabeled_paths[@]}"; do
        if [ "$path" == "$1" ]; then
            ret=0
            break
        fi
    done
    return $ret
}

get_unlabeled_wakeup_paths() {
    for path in ${wakeup_paths[@]}; do
        wakeup_path="$path"
        has_wakeup_attr "$wakeup_path" && continue

        # If there exists a common wakeup parent directory, label that instead
        # of each wakeupN directory
        dir_path="$(dirname $path)"
        [ "$(basename $dir_path)" == "wakeup" ] && wakeup_path="$dir_path"

        check_wakeup_dup $wakeup_path || unlabeled_paths+=( $wakeup_path )
    done
}

print_missing_labels() {
    nr_unlabeled="${#unlabeled_paths[@]}"

    [ "$nr_unlabeled" == "0" ] && return 1

    echo ""
    echo "Unlabeled wakeup nodes found, your device is likely missing"
    echo "device/oem specific selinux genfscon rules for suspend."
    echo ""
    echo "Please review and add the following generated rules to the"
    echo "device specific genfs_contexts:"
    echo ""

    for path in ${unlabeled_paths[@]}; do
        echo "genfscon sysfs ${path#/sys/} $wakeup_attr"
    done

    echo ""

    return 0
}

fail() { #msg
    echo $1
    exit 1
}

pass() { #msg
    echo $1
    exit 0
}

# requirement added in T (33)
vendor_api_level="$(adb shell getprop ro.vendor.api_level 33)"
if [ "$vendor_api_level" -lt 33 ]; then
    pass "Test skipped: vendor_api_level ($vendor_api_level) < min_api_level (33)"
fi

# Test unlabeled sysfs_wakeup nodes
wakeup_paths+=( $(get_wakeup_paths) )
get_unlabeled_wakeup_paths
print_missing_labels && fail "Missing sysfs_wakeup labels" \
    || pass "No missing sysfs_wakeup labels"
