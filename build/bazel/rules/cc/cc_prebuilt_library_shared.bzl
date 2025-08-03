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

load("@bazel_tools//tools/cpp:toolchain_utils.bzl", "find_cpp_toolchain")
load(":cc_library_common.bzl", "create_cc_prebuilt_library_info")
load(":stripped_cc_common.bzl", "common_strip_attrs", "stripped_impl")

def _cc_prebuilt_library_shared_impl(ctx):
    lib = ctx.file.shared_library
    files = []
    if lib:
        lib = stripped_impl(ctx, ctx.file.shared_library, suffix = ".so", subdir = ctx.attr.name)
        files.append(lib)

    cc_toolchain = find_cpp_toolchain(ctx)
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
    )
    cc_info, linker_input = create_cc_prebuilt_library_info(
        ctx,
        cc_common.create_library_to_link(
            actions = ctx.actions,
            dynamic_library = lib,
            feature_configuration = feature_configuration,
            cc_toolchain = cc_toolchain,
        ) if lib != None else None,
    )

    return [
        DefaultInfo(
            files = depset(direct = files),
            runfiles = ctx.runfiles(files),
        ),
        cc_info,
        CcSharedLibraryInfo(
            dynamic_deps = depset(),
            exports = [],
            link_once_static_libs = [],
            linker_input = linker_input,
        ),
        OutputGroupInfo(
            # TODO(b/279433767): remove once cc_library_shared is stable
            rule_impl_debug_files = [],
        ),
    ]

cc_prebuilt_library_shared = rule(
    implementation = _cc_prebuilt_library_shared_impl,
    attrs = dict(
        common_strip_attrs,
        shared_library = attr.label(
            providers = [CcInfo],
            allow_single_file = True,
        ),
        export_includes = attr.string_list(),
        export_system_includes = attr.string_list(),
    ),
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
    provides = [CcInfo, CcSharedLibraryInfo],
)
