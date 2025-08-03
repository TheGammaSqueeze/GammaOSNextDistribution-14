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

load("@bazel_skylib//lib:paths.bzl", "paths")

HidlPackageRoot = provider(fields = [
    "root",
    "root_path",
    "root_interface_file",
])

def _hidl_package_rule_impl(ctx):
    path = ctx.attr.path
    if ctx.attr.path == ".":
        path = paths.dirname(ctx.build_file_path)
    current = ctx.file.current
    currents = []
    if current:
        currents.append(current)
    return [
        DefaultInfo(
            files = depset(direct = currents),
            runfiles = ctx.runfiles(files = currents),
        ),
        HidlPackageRoot(
            root = ctx.attr.name,
            root_path = path,
            root_interface_file = current,
        ),
    ]

hidl_package_root = rule(
    implementation = _hidl_package_rule_impl,
    attrs = {
        "path": attr.string(default = "."),
        "current": attr.label(allow_single_file = ["current.txt"]),
    },
)
