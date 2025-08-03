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

"""
rust_ffi_static macro wraps the include paths to cc headers with the generated
rust staticlib
"""

load("@rules_rust//rust:defs.bzl", "rust_static_library")
load("//build/bazel/rules/cc:cc_prebuilt_library_static.bzl", "cc_prebuilt_library_static")

def rust_ffi_static(
        name,
        srcs,
        crate_name,
        deps,
        edition,
        export_includes,
        compile_data = None,
        crate_features = [],
        # TODO: b/305997810 - Support crate_root attribute
        proc_macro_deps = [],
        rustc_flags = [],
        target_compatible_with = []):
    rust_static_library(
        name = name + "_rust_staticlib",
        srcs = srcs,
        compile_data = compile_data,
        crate_features = crate_features,
        crate_name = crate_name,
        deps = deps,
        edition = edition,
        proc_macro_deps = proc_macro_deps,
        rustc_flags = rustc_flags,
        target_compatible_with = target_compatible_with,
    )

    # TODO: b/305274034 - remove cc_prebuilt_library_static if we can
    # add includes to rust_ffi_static
    cc_prebuilt_library_static(
        name = name,
        export_includes = export_includes,
        static_library = name + "_rust_staticlib",
        target_compatible_with = target_compatible_with,
    )
