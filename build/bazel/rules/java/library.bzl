# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Macro wrapping the java_library for bp2build. """

load(
    "@rules_java//java:defs.bzl",
    _java_library = "java_library",
)
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")

_sharded_java_library = experimental_java_library_export_do_not_use.sharded_java_library(
    default_shard_size = 0,
)

# TODO(b/277801336): document these attributes.
def java_library(
        name = "",
        srcs = [],
        deps = [],
        javacopts = [],
        sdk_version = None,
        java_version = None,
        errorprone_force_enable = None,
        tags = [],
        target_compatible_with = [],
        visibility = None,
        javac_shard_size = 0,
        **kwargs):
    """ java_library macro wrapper that handles custom attrs needed in AOSP

    Args:
        errorprone_force_enable: set this to true to always run Error Prone
            on this target (overriding the value of environment variable
            RUN_ERROR_PRONE). Error Prone can be force disabled for an individual
            module by adding the "-XepDisableAllChecks" flag to javacopts
    """
    lib_name = name + "_private"

    opts = javacopts
    if errorprone_force_enable == None:
        # TODO (b/227504307) temporarily disable errorprone until environment variable is handled
        opts = opts + ["-XepDisableAllChecks"]

    args = {
        "name": lib_name,
        "srcs": srcs,
        "deps": deps,
        "javacopts": opts,
        "tags": tags + ["manual"],
        "target_compatible_with": target_compatible_with,
        "visibility": ["//visibility:private"],
    }
    args.update(kwargs)
    if javac_shard_size > 0:
        args["experimental_javac_shard_size"] = javac_shard_size
        _sharded_java_library(**args)
    else:
        _java_library(**args)

    java_library_sdk_transition(
        name = name,
        sdk_version = sdk_version,
        java_version = java_version,
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
def _java_library_sdk_transition_impl(ctx):
    return [
        ctx.attr.exports[0][JavaInfo],
        ctx.attr.exports[0][InstrumentedFilesInfo],
        ctx.attr.exports[0][ProguardSpecProvider],
        ctx.attr.exports[0][OutputGroupInfo],
        ctx.attr.exports[0][DefaultInfo],
    ]

java_library_sdk_transition = rule(
    implementation = _java_library_sdk_transition_impl,
    attrs = sdk_transition_attrs,
    provides = [JavaInfo],
)
