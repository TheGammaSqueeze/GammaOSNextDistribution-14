# Copyright (C) 2021 The Android Open Source Project
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

# Constants for cc_* rules.
# To use, load the constants struct:
#
#   load("//build/bazel/rules:cc_constants.bzl", "constants")
# Supported hdr extensions in Soong. Keep this consistent with hdrExts in build/soong/cc/snapshot_utils.go
_HDR_EXTS = ["h", "hh", "hpp", "hxx", "h++", "inl", "inc", "ipp", "h.generic"]
_C_SRC_EXTS = ["c"]
_CPP_SRC_EXTS = ["cc", "cpp"]
_AS_SRC_EXTS = ["s", "S"]
_SRC_EXTS = _C_SRC_EXTS + _CPP_SRC_EXTS + _AS_SRC_EXTS
_ALL_EXTS = _SRC_EXTS + _HDR_EXTS
_HDR_EXTS_WITH_DOT = ["." + ext for ext in _HDR_EXTS]
_SRC_EXTS_WITH_DOT = ["." + ext for ext in _SRC_EXTS]
_ALL_EXTS_WITH_DOT = ["." + ext for ext in _ALL_EXTS]

constants = struct(
    hdr_exts = _HDR_EXTS,
    c_src_exts = _C_SRC_EXTS,
    cpp_src_exts = _CPP_SRC_EXTS,
    as_src_exts = _AS_SRC_EXTS,
    src_exts = _SRC_EXTS,
    all_exts = _ALL_EXTS,
    hdr_dot_exts = _HDR_EXTS_WITH_DOT,
    src_dot_exts = _SRC_EXTS_WITH_DOT,
    all_dot_exts = _ALL_EXTS_WITH_DOT,
)

# Constants for use in cc transitions
_FEATURES_ATTR_KEY = "features"
_CLI_FEATURES_KEY = "//command_line_option:features"
_CLI_PLATFORMS_KEY = "//command_line_option:platforms"
_CFI_INCLUDE_PATHS_KEY = "@//build/bazel/product_config:cfi_include_paths"
_CFI_EXCLUDE_PATHS_KEY = "@//build/bazel/product_config:cfi_exclude_paths"
_ENABLE_CFI_KEY = "@//build/bazel/product_config:enable_cfi"
_CFI_ASSEMBLY_KEY = "@//build/bazel/rules/cc:cfi_assembly"
_MEMTAG_HEAP_ASYNC_INCLUDE_PATHS_KEY = "@//build/bazel/product_config:memtag_heap_async_include_paths"
_MEMTAG_HEAP_SYNC_INCLUDE_PATHS_KEY = "@//build/bazel/product_config:memtag_heap_sync_include_paths"
_MEMTAG_HEAP_EXCLUDE_PATHS_KEY = "@//build/bazel/product_config:memtag_heap_exclude_paths"

# TODO: b/294868620 - This can be removed when completing the bug
_SANITIZERS_ENABLED_KEY = "@//build/bazel/rules/cc:sanitizers_enabled_setting"

transition_constants = struct(
    features_attr_key = _FEATURES_ATTR_KEY,
    cli_features_key = _CLI_FEATURES_KEY,
    cfi_include_paths_key = _CFI_INCLUDE_PATHS_KEY,
    cfi_exclude_paths_key = _CFI_EXCLUDE_PATHS_KEY,
    enable_cfi_key = _ENABLE_CFI_KEY,
    cli_platforms_key = _CLI_PLATFORMS_KEY,
    cfi_assembly_key = _CFI_ASSEMBLY_KEY,
    sanitizers_enabled_key = _SANITIZERS_ENABLED_KEY,
    memtag_heap_async_include_paths_key = _MEMTAG_HEAP_ASYNC_INCLUDE_PATHS_KEY,
    memtag_heap_sync_include_paths_key = _MEMTAG_HEAP_SYNC_INCLUDE_PATHS_KEY,
    memtag_heap_exclude_paths_key = _MEMTAG_HEAP_EXCLUDE_PATHS_KEY,
)
