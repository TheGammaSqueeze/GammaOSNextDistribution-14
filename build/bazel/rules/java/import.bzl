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

"""Macro wrapping the java_import for bp2build. """

load("@rules_java//java:defs.bzl", _java_import = "java_import")
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")

# TODO(b/277801336): document these attributes.
def java_import(
        name = "",
        jars = [],
        deps = [],
        tags = [],
        target_compatible_with = [],
        visibility = None,
        **kwargs):
    lib_name = name + "_private"
    _java_import(
        name = lib_name,
        jars = jars,
        deps = deps,
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        visibility = ["//visibility:private"],
        **kwargs
    )

    java_import_sdk_transition(
        name = name,
        sdk_version = "none",
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
def _java_import_sdk_transition_impl(ctx):
    return [
        ctx.attr.exports[0][JavaInfo],
        ctx.attr.exports[0][ProguardSpecProvider],
        ctx.attr.exports[0][OutputGroupInfo],
        ctx.attr.exports[0][DefaultInfo],
    ]

java_import_sdk_transition = rule(
    implementation = _java_import_sdk_transition_impl,
    attrs = sdk_transition_attrs,
    provides = [JavaInfo],
)
