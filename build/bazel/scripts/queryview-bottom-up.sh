#!/usr/bin/bash
# Copyright 2020 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# queryview-bottom-up.sh: A query script to strategize the bottom-up migration
# approach of any target in the queryview workspace.
#
# Usage: ./queryview-bottom-up.sh <label of target in queryview>
#
# You can generate the queryview workspace using "m queryview". Note that the
# queryview targets represent the fully configured build graph of Soong, so the
# results may differ based on the product configuration and environment which
# the queryview workspace was created in.
#
# This script obtains the transitive closure of a target, sorts its dependencies
# based on minrank (shortest path from root to dep), and gets their module type.
# This can help to figure out the exact module types we need to work on
# converting first,
#
# This script produces the following (a sample):
#
# 0  //bionic/libc:libc--android_arm_armv7-a-neon_shared                                                                         cc_library
# 1  //bionic/libc:bionic_libc_license                                                                                           license
# 1  //bionic/libc:libc--android_arm_armv7-a-neon_shared_29                                                                      cc_library
# 1  //bionic/libc:libc--android_arm_armv7-a-neon_shared_30                                                                      cc_library
# 1  //bionic/libc:libc--android_arm_armv7-a-neon_shared_current                                                                 cc_library
# 1  //bionic/libc:libc--android_arm_armv7-a-neon_static                                                                         cc_library
# 1  //bionic/libc:libc.arm.map                                                                                                  genrule
# 1  //bionic/libc:libc_common_shared--android_arm_armv7-a-neon_static                                                           cc_library_static
# 1  //bionic/libc:libc_defaults                                                                                                 cc_defaults
# 1  //bionic/libc:libc_headers--android_arm_armv7-a-neon                                                                        cc_library_headers
# 1  //bionic/libc:libc_init_dynamic--android_arm_armv7-a-neon_static                                                            cc_library_static
# 1  //bionic/libc:libc_native_allocator_defaults                                                                                cc_defaults
# 1  //bionic/libc:libc_sources_shared_arm                                                                                       filegroup
# 1  //bionic/libc:libc_sources_shared                                                                                           filegroup
# 1  //bionic/libc:libc_sources_static                                                                                           filegroup
# 1  //bionic/libdl:libdl_android--android_arm_armv7-a-neon_static                                                               cc_library
# 1  //bionic/libdl:libdl--android_arm_armv7-a-neon_shared                                                                       cc_library
# 1  //bionic/linker:ld-android--android_arm_armv7-a-neon_shared                                                                 cc_library
# 1  //external/gwp_asan:gwp_asan--android_arm_armv7-a-neon_static                                                               cc_library_static
# 1  //external/gwp_asan:gwp_asan_headers--android_arm_armv7-a-neon                                                              cc_library_headers
# 1  //external/scudo:libscudo--android_arm_armv7-a-neon_static                                                                  cc_library_static
# 1  //prebuilts/clang/host/linux-x86:libclang_rt.builtins-arm-android-exported--android_arm_armv7-a-neon_static                 libclang_rt_prebuilt_library_static
# 1  //prebuilts/clang/host/linux-x86:libunwind-exported--android_arm_armv7-a-neon_static                                        llvm_prebuilt_library_static
# 1  //prebuilts/runtime/mainline/runtime/sdk:prebuilt_libc--android_arm_armv7-a-neon_shared                                     cc_prebuilt_library_shared
# 2  //bionic/libc:bionic_libc_platform_headers--android_arm_armv7-a-neon                                                        cc_library_headers
# 2  //bionic/libc:crtbegin_so--android_arm_armv7-a-neon                                                                         cc_object
# 2  //bionic/libc:crtend_so--android_arm_armv7-a-neon                                                                           cc_object
# 2  //bionic/libc:libc_common--android_arm_armv7-a-neon_static                                                                  cc_library_static
# 2  //bionic/libc:libc_common_static--android_arm_armv7-a-neon_static                                                           cc_library_static
# 2  //bionic/libc:libc_dynamic_dispatch--android_arm_armv7-a-neon_static                                                        cc_library_static
# ...

set -euo pipefail

# Convenience function to use the checked-in bazel binary
function bazel() {
  # We're in <root>/build/bazel/scripts
  AOSP_ROOT="$(dirname $0)/../../.."
  "${AOSP_ROOT}/build/bazel/bin/bazel" "$@"
}

T=${1:-//bionic/libc:libc--android_arm_armv7-a-neon_shared}
COMMON_BAZEL_OPTS="--noshow_loading_progress --color=no --curses=no"

declare -a label_array
declare -a minrank_array
declare -a label_kind_array

# Obtain the sorted list of labels in the transitive dependency closure of T.
label_array=$(bazel query --config=queryview "deps($T)" --output=label $COMMON_BAZEL_OPTS 2>/dev/null | sort)

# Obtain the sorted list of ranks based on the label as sort key. The rank value is the minimum distance between the root node to the dep.
minrank_array=$(bazel query --config=queryview "deps($T)" --output=minrank $COMMON_BAZEL_OPTS 2>/dev/null | awk -e '{print $2 " " $1}' | sort | awk -e '{print $2}')

# Obtain the sorted list of module types (label kinds) based on the label as sort key.
label_kind_array=$(bazel query --config=queryview "deps($T)" --output=label_kind $COMMON_BAZEL_OPTS 2>/dev/null | awk -e '{print $3 " " $1}' | sort | awk -e '{print $2}')

# Zip the lists together, tabularize it, and sort the list based on the rank
# value of the dep. A rank value of 0 represents the root node.
paste <(echo "${minrank_array[*]}") <(echo "${label_array[*]}")  <(echo "${label_kind_array[*]}") | column -t | sort -n
