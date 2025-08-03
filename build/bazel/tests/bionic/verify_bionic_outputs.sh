#!/bin/bash
#
# Copyright 2021 Google Inc. All rights reserved.
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

set -euo pipefail

source "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"

NM="$1"
READELF="$2"

# This should be abstracted to a unit-test library when it has more uses.
function assert_contains_regex() {
    local needle="$1"
    local haystack="$2"
    local message="${3:-Expected regexp "$needle" not found in\n"$haystack"}"
    echo "${haystack}" | grep "${needle}" && return 0

    echo -e "$message"
    exit 1
}

# Test that a library is a static library.
function test_is_static_library() {
    local filepath="$(readlink -f $1)"; shift
    local metadata="$($READELF -h ${filepath})"
    assert_contains_regex "Type:.*REL (Relocatable file)" "${metadata}"
}

# Test that a library is a shared library.
function test_is_shared_library() {
    local filepath="$(readlink -f $1)"; shift
    local metadata="$($READELF -h ${filepath})"
    assert_contains_regex "Type:.*DYN (Shared object file)" "${metadata}"
}

# Test that the shared library contains a symbol
function test_shared_library_symbols() {
    local filepath="$(readlink -f $1)"; shift
    local symbols=("$@"); shift
    local nm_output="$($NM -D "${filepath}")"
    for symbol in "${symbols[@]}"; do
        assert_contains_regex "${symbol}" "${nm_output}"
    done
}

# Test file contents of //bionic/linker:ld-android
function test_ld-android() {
    local shared_library="$(rlocation __main__/bionic/linker/ld-android/ld-android.so)"
    local static_library="$(rlocation __main__/bionic/linker/libld-android_bp2build_cc_library_static.a)"

    test_is_shared_library "${shared_library}"
    test_is_static_library "${static_library}"

    symbols=(
        __loader_add_thread_local_dtor
        __loader_android_create_namespace
        __loader_android_dlopen_ext
        __loader_android_dlwarning
        __loader_android_get_application_target_sdk_version
        __loader_android_get_exported_namespace
        __loader_android_get_LD_LIBRARY_PATH
        __loader_android_init_anonymous_namespace
        __loader_android_link_namespaces
        __loader_android_link_namespaces_all_libs
        __loader_android_set_application_target_sdk_version
        __loader_android_update_LD_LIBRARY_PATH
        __loader_cfi_fail
        __loader_dladdr
        __loader_dlclose
        __loader_dlerror
        __loader_dl_iterate_phdr
        __loader_dlopen
        __loader_dlsym
        __loader_dlvsym
        __loader_remove_thread_local_dtor
        __loader_shared_globals
        _db_dlactivity
    )

    test_shared_library_symbols "${shared_library}" "${symbols[@]}"
}

function test_libdl_android() {
    local shared_library="$(rlocation __main__/bionic/libdl/libdl_android/libdl_android.so)"
    local static_library="$(rlocation __main__/bionic/libdl/liblibdl_android_bp2build_cc_library_static.a)"

    test_is_shared_library "${shared_library}"
    test_is_static_library "${static_library}"

    symbols=(
        android_create_namespace
        android_dlwarning
        android_get_exported_namespace
        android_get_LD_LIBRARY_PATH
        android_init_anonymous_namespace
        android_link_namespaces
        android_set_application_target_sdk_version
        android_update_LD_LIBRARY_PATH
    )

    test_shared_library_symbols "${shared_library}" "${symbols[@]}"
}

function test_libc() {
    local shared_library="$(rlocation __main__/bionic/libc/libc/libc.so)"
    local static_library="$(rlocation __main__/bionic/libc/liblibc_bp2build_cc_library_static.a)"

    test_is_shared_library "${shared_library}"
    test_is_static_library "${static_library}"

    symbols=(
        __libc_get_static_tls_bounds
        __libc_register_thread_exit_callback
        __libc_iterate_dynamic_tls
        __libc_register_dynamic_tls_listeners
        android_reset_stack_guards
        ffsl
        ffsll
        pidfd_getfd
        pidfd_open
        pidfd_send_signal
        process_madvise
        _Unwind_Backtrace  # apex llndk
        _Unwind_DeleteException  # apex llndk
        _Unwind_Find_FDE  # apex llndk
        _Unwind_FindEnclosingFunction  # apex llndk
        _Unwind_GetCFA  # apex llndk
        _Unwind_GetDataRelBase  # apex llndk
        _Unwind_GetGR  # apex llndk
        _Unwind_GetIP  # apex llndk
        _Unwind_GetIPInfo  # apex llndk
        _Unwind_GetLanguageSpecificData  # apex llndk
        _Unwind_GetRegionStart  # apex llndk
        _Unwind_GetTextRelBase  # apex llndk
        _Unwind_RaiseException  # apex llndk
        _Unwind_Resume  # apex llndk
        _Unwind_Resume_or_Rethrow  # apex llndk
        _Unwind_SetGR  # apex llndk
        _Unwind_SetIP  # apex llndk
    )

    test_shared_library_symbols "${shared_library}" "${symbols[@]}"
}

test_ld-android
test_libdl_android
test_libc
