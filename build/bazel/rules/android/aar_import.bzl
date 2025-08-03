# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Macro wrapping the aar_import for bp2build. """

load("//build/bazel/rules/android/aar_import_aosp_internal:rule.bzl", _aar_import = "aar_import")
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")
load("@rules_android//rules:providers.bzl", "StarlarkAndroidResourcesInfo")

# TODO(b/277801336): document these attributes.
def aar_import(
        name = "",
        aar = [],
        sdk_version = None,
        deps = [],
        tags = [],
        target_compatible_with = [],
        visibility = None,
        **kwargs):
    lib_name = name + "_private"
    _aar_import(
        name = lib_name,
        aar = aar,
        deps = deps,
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        visibility = ["//visibility:private"],
        **kwargs
    )

    aar_import_sdk_transition(
        name = name,
        sdk_version = sdk_version,
        java_version = None,
        exports = lib_name,
        tags = tags,
        target_compatible_with = target_compatible_with,
        visibility = visibility,
    )

# The list of providers to forward was determined using cquery on one
# of the example targets listed under EXAMPLE_WRAPPER_TARGETS at
# //build/bazel/ci/target_lists.sh. It may not be exhaustive. A unit
# test ensures that the wrapper's providers and the wrapped rule's do
# match.
def _aar_import_sdk_transition_impl(ctx):
    return [
        ctx.attr.exports[0][AndroidLibraryResourceClassJarProvider],
        ctx.attr.exports[0][JavaInfo],
        ctx.attr.exports[0][AndroidNativeLibsInfo],
        ctx.attr.exports[0][ProguardSpecProvider],
        ctx.attr.exports[0][AndroidIdeInfo],
        ctx.attr.exports[0][DefaultInfo],
        ctx.attr.exports[0][StarlarkAndroidResourcesInfo],
    ]

aar_import_sdk_transition = rule(
    implementation = _aar_import_sdk_transition_impl,
    attrs = sdk_transition_attrs,
    provides = [
        AndroidIdeInfo,
        AndroidLibraryResourceClassJarProvider,
        AndroidNativeLibsInfo,
        JavaInfo,
    ],
)
