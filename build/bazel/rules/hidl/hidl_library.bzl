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

load("//build/bazel/rules/hidl:hidl_package_root.bzl", "HidlPackageRoot")

HidlInfo = provider(fields = [
    "srcs",
    "transitive_srcs",
    "transitive_roots",
    "transitive_root_interface_files",
    "fq_name",
])

def _hidl_library_rule_impl(ctx):
    transitive_srcs = []
    transitive_root_interface_files = []
    transitive_roots = []

    for dep in ctx.attr.deps:
        transitive_srcs.append(dep[HidlInfo].transitive_srcs)
        transitive_root_interface_files.append(dep[HidlInfo].transitive_root_interface_files)
        transitive_roots.append(dep[HidlInfo].transitive_roots)

    root = ctx.attr.root[HidlPackageRoot]
    root_interface_files = []
    if root.root_interface_file:
        root_interface_files.append(root.root_interface_file)
    return [
        DefaultInfo(files = depset(ctx.files.srcs)),
        HidlInfo(
            srcs = depset(ctx.files.srcs),
            transitive_srcs = depset(
                direct = ctx.files.srcs,
                transitive = transitive_srcs,
            ),
            # These transitive roots will be used as -r arguments later when calling
            # hidl-gen, for example, -r android.hardware:hardware/interfaces
            transitive_roots = depset(
                direct = [root.root + ":" + root.root_path],
                transitive = transitive_roots,
            ),
            transitive_root_interface_files = depset(
                direct = root_interface_files,
                transitive = transitive_root_interface_files,
            ),
            fq_name = ctx.attr.fq_name,
        ),
    ]

hidl_library = rule(
    implementation = _hidl_library_rule_impl,
    attrs = {
        "srcs": attr.label_list(
            allow_files = [".hal"],
        ),
        "deps": attr.label_list(
            providers = [HidlInfo],
            doc = "hidl_interface targets that this one depends on",
        ),
        "fq_name": attr.string(),
        "root": attr.label(),
    },
    provides = [HidlInfo],
)
