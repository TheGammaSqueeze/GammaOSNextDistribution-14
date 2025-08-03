"""
Copyright (C) 2023 The Android Open Source Project

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

load("@rules_kotlin//kotlin:compiler_opt.bzl", "kt_compiler_opt")
load("@rules_kotlin//kotlin:rules.bzl", _kt_jvm_library = "kt_jvm_library")
load("//build/bazel/rules/java:java_resources.bzl", "java_resources")
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")

def make_kt_compiler_opt(
        name,
        kotlincflags = None):
    custom_kotlincopts = None
    if kotlincflags != None:
        ktcopts_name = name + "_kotlincopts"
        kt_compiler_opt(
            name = ktcopts_name,
            opts = kotlincflags,
        )
        custom_kotlincopts = [":" + ktcopts_name]

    return custom_kotlincopts

# TODO(b/277801336): document these attributes.
def kt_jvm_library(
        name,
        deps = None,
        resources = None,
        resource_strip_prefix = None,
        kotlincflags = None,
        java_version = None,
        sdk_version = None,
        javacopts = [],
        errorprone_force_enable = None,
        tags = [],
        target_compatible_with = [],
        visibility = None,
        **kwargs):
    """Bazel macro wrapping for kt_jvm_library

        Attributes:
            errorprone_force_enable: set this to true to always run Error Prone
            on this target (overriding the value of environment variable
            RUN_ERROR_PRONE). Error Prone can be force disabled for an individual
            module by adding the "-XepDisableAllChecks" flag to javacopts
        """
    if resource_strip_prefix != None:
        kt_res_jar_name = name + "__kt_res_jar"

        java_resources(
            name = kt_res_jar_name,
            resources = resources,
            resource_strip_prefix = resource_strip_prefix,
        )

        deps = deps + [":" + kt_res_jar_name]

    custom_kotlincopts = make_kt_compiler_opt(name, kotlincflags)

    opts = javacopts
    if errorprone_force_enable == None:
        # TODO (b/227504307) temporarily disable errorprone until environment variable is handled
        opts = opts + ["-XepDisableAllChecks"]

    lib_name = name + "_private"
    _kt_jvm_library(
        name = lib_name,
        deps = deps,
        custom_kotlincopts = custom_kotlincopts,
        javacopts = opts,
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        visibility = ["//visibility:private"],
        **kwargs
    )

    kt_jvm_library_sdk_transition(
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
def _kt_jvm_library_sdk_transition_impl(ctx):
    return [
        ctx.attr.exports[0][JavaInfo],
        ctx.attr.exports[0][InstrumentedFilesInfo],
        ctx.attr.exports[0][ProguardSpecProvider],
        ctx.attr.exports[0][OutputGroupInfo],
        ctx.attr.exports[0][DefaultInfo],
    ]

kt_jvm_library_sdk_transition = rule(
    implementation = _kt_jvm_library_sdk_transition_impl,
    attrs = sdk_transition_attrs,
    provides = [JavaInfo],
)
