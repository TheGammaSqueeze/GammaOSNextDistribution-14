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

def _cc_prebuilt_library_static_impl(ctx):
    lib = ctx.file.static_library
    files = ctx.attr.static_library.files if lib != None else None
    cc_toolchain = find_cpp_toolchain(ctx)
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
    )
    cc_info, _ = create_cc_prebuilt_library_info(
        ctx,
        cc_common.create_library_to_link(
            actions = ctx.actions,
            static_library = lib,
            alwayslink = ctx.attr.alwayslink,
            feature_configuration = feature_configuration,
            cc_toolchain = cc_toolchain,
        ) if lib != None else None,
    )
    return [DefaultInfo(files = files), cc_info]

cc_prebuilt_library_static = rule(
    implementation = _cc_prebuilt_library_static_impl,
    attrs = dict(
        static_library = attr.label(
            providers = [CcInfo],
            allow_single_file = True,
        ),
        alwayslink = attr.bool(default = False),
        export_includes = attr.string_list(),
        export_system_includes = attr.string_list(),
    ),
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
    provides = [CcInfo],
)
