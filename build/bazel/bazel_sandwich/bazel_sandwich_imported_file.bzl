# Copyright (C) 2023 The Android Open Source Project
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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")

def _impl(ctx):
    target = ctx.attr.target
    device_name = ctx.attr._device_name[BuildSettingInfo].value
    target = target.replace("$(DeviceName)", device_name)

    if paths.normalize(target) != target:
        fail("file path must be normalized: " + target)
    if target.startswith("/") or target.startswith("../"):
        fail("file path must not start with / or ../: " + target)

    data = {
        "target": target,
        "depend_on_target": ctx.attr.depend_on_target,
    }
    if ctx.attr.implicit_deps:
        data["implicit_deps"] = [d.replace("$(DeviceName)", device_name) for d in ctx.attr.implicit_deps]

    device_name = ctx.attr._device_name[BuildSettingInfo].value

    out = ctx.actions.declare_symlink(ctx.label.name)
    ctx.actions.symlink(
        output = out,
        # the bazel_sandwich: prefix signals to the mixed build handler to treat this specially
        target_path = "bazel_sandwich:" + json.encode(data),
    )
    return [
        DefaultInfo(files = depset([out])),
    ]

_bazel_sandwich_imported_file = rule(
    implementation = _impl,
    attrs = {
        "target": attr.string(
            mandatory = True,
            doc = "The target of the symlink. It's a path relative to the root of the output dir " +
                  " to import. In this attribute, $(DeviceName) will be replaced with the device name " +
                  "product variable.",
        ),
        "depend_on_target": attr.bool(
            default = True,
            doc = "Whether or not a dependency should be added from the symlink to the file it's " +
                  "targeting. In most cases you want this to be true.",
        ),
        "implicit_deps": attr.string_list(
            doc = "Paths to other make-generated files to use as implicit deps. This is useful " +
                  "when you want to import a folder to bazel, you can add an implicit dep on a " +
                  "stamp file that depends on all contents of the foler. In this attribute, " +
                  "$(DeviceName) will be replaced with the device name product variable.",
        ),
        "_device_name": attr.label(
            default = "//build/bazel/product_config:device_name",
        ),
    },
)

def bazel_sandwich_imported_file(target_compatible_with = [], **kwargs):
    _bazel_sandwich_imported_file(
        target_compatible_with = ["//build/bazel/platforms:mixed_builds"] + target_compatible_with,
        **kwargs
    )
