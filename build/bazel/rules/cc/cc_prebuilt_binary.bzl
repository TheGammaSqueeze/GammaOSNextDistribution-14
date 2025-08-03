"""Copyright (C) 2022 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load(":stripped_cc_common.bzl", "common_strip_attrs", "stripped_impl")

def is_target_host(ctx):
    return not platforms.is_target_android(ctx.attr._platform_utils)

def _cc_prebuilt_binary_impl(ctx):
    # If the target is host, Soong just manually does a symlink
    if is_target_host(ctx):
        exec = ctx.actions.declare_file(ctx.attr.name)
        ctx.actions.symlink(
            output = exec,
            target_file = ctx.files.src[0],
        )
    else:
        exec = stripped_impl(ctx, ctx.file.src)
    return [
        DefaultInfo(
            files = depset([exec]),
            executable = exec,
        ),
    ]

cc_prebuilt_binary = rule(
    implementation = _cc_prebuilt_binary_impl,
    attrs = dict(
        common_strip_attrs,  # HACK: inlining common_strip_attrs
        src = attr.label(
            mandatory = True,
            allow_single_file = True,
        ),
        _platform_utils = attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    ),
    executable = True,
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)
