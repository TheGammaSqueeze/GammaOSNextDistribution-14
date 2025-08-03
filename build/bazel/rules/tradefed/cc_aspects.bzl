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

load("//build/bazel/rules/cc:cc_library_shared.bzl", "CcSharedLibraryOutputInfo")

CcTestSharedLibsInfo = provider(
    "Shared lib dependencies needed to run the cc_test targets",
    fields = {
        "shared_libs": "Shared libs that this cc test needs.",
    },
)

def _collect_cc_libs_aspect_impl(target, ctx):
    shared_libs = []
    transitive_deps = []

    rules_propagate_src = [
        "_bssl_hash_injection",
        "stripped_shared_library",
        "versioned_shared_library",
        "stripped_test",
        "versioned_binary",
    ]

    if ctx.rule.kind == "_cc_library_shared_proxy":
        shared_libs.append(target[CcSharedLibraryOutputInfo].output_file)
        if hasattr(ctx.rule.attr, "shared"):
            transitive_deps.append(ctx.rule.attr.shared[0])
    elif ctx.rule.kind in ["cc_shared_library", "cc_test"]:
        # Propagate along the dynamic_deps edges for binaries and shared libs
        if hasattr(ctx.rule.attr, "dynamic_deps"):
            for dep in ctx.rule.attr.dynamic_deps:
                transitive_deps.append(dep)
        if ctx.rule.kind == "cc_test" and hasattr(ctx.rule.attr, "deps"):
            for dep in ctx.rule.attr.deps:
                transitive_deps.append(dep)
    elif ctx.rule.kind == "_cc_library_combiner" and hasattr(ctx.rule.attr, "androidmk_dynamic_deps"):
        for dep in ctx.rule.attr.androidmk_dynamic_deps:
            transitive_deps.append(dep)
    elif ctx.rule.kind in rules_propagate_src and hasattr(ctx.rule.attr, "src"):
        if ctx.rule.kind == "stripped_test":
            transitive_deps.append(ctx.rule.attr.src[0])
        else:
            transitive_deps.append(ctx.rule.attr.src)

    if ctx.rule.kind in ["stripped_test", "_cc_library_shared_proxy"] and hasattr(ctx.rule.attr, "runtime_deps"):
        for dep in ctx.rule.attr.runtime_deps:
            for output_file in dep[DefaultInfo].files.to_list():
                if output_file.extension == "so":
                    shared_libs.append(output_file)
            transitive_deps.append(dep)

    return [
        CcTestSharedLibsInfo(
            shared_libs = depset(
                shared_libs,
                transitive = [info[CcTestSharedLibsInfo].shared_libs for info in transitive_deps],
            ),
        ),
    ]

# The list of attributes in a cc dep graph where this aspect will traverse on.
CC_ATTR_ASPECTS = [
    "dynamic_deps",
    "deps",
    "shared",
    "src",
    "runtime_deps",
    "static_deps",
    "whole_archive_deps",
    "androidmk_dynamic_deps",
]

collect_cc_libs_aspect = aspect(
    implementation = _collect_cc_libs_aspect_impl,
    provides = [CcTestSharedLibsInfo],
    attr_aspects = CC_ATTR_ASPECTS,
)
