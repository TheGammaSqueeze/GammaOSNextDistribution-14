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

"""android_library rule."""

load("@rules_android//rules:providers.bzl", "StarlarkAndroidResourcesInfo")
load(
    "//build/bazel/rules/android/android_library_aosp_internal:rule.bzl",
    "android_library_aosp_internal_macro",
)
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")
load("//build/bazel/rules/kotlin:kt_jvm_library.bzl", "make_kt_compiler_opt")

# TODO(b/277801336): document these attributes.
def android_library(
        name,
        sdk_version = None,
        errorprone_force_enable = None,
        javacopts = [],
        java_version = None,
        tags = [],
        target_compatible_with = [],
        visibility = None,
        kotlincflags = None,
        **attrs):
    """ android_library macro wrapper that handles custom attrs needed in AOSP

    Args:
      name: the wrapper rule name.
      sdk_version: string representing which sdk_version to build against. See
      //build/bazel/rules/common/sdk_version.bzl for formatting and semantics.
      errorprone_force_enable: set this to true to always run Error Prone
      on this target (overriding the value of environment variable
      RUN_ERROR_PRONE). Error Prone can be force disabled for an individual
      module by adding the "-XepDisableAllChecks" flag to javacopts
      java_version: string representing which version of java the java code in this rule should be
      built with.
      tags, target_compatible_with and visibility have Bazel's traditional semantics.
      **attrs: Rule attributes
    """
    lib_name = name + "_private"
    custom_kotlincopts = make_kt_compiler_opt(name, kotlincflags)

    opts = javacopts
    if errorprone_force_enable == None:
        # TODO (b/227504307) temporarily disable errorprone until environment variable is handled
        opts = opts + ["-XepDisableAllChecks"]

    android_library_aosp_internal_macro(
        name = lib_name,
        javacopts = opts,
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        visibility = ["//visibility:private"],
        custom_kotlincopts = custom_kotlincopts,
        **attrs
    )

    android_library_sdk_transition(
        aar = name + ".aar",
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
def _android_library_sdk_transition_impl(ctx):
    ctx.actions.symlink(
        output = ctx.outputs.aar,
        target_file = ctx.attr.exports[0][AndroidIdeInfo].aar,
    )

    providers = []
    if AndroidLibraryAarInfo in ctx.attr.exports[0]:
        providers.append(ctx.attr.exports[0][AndroidLibraryAarInfo])
    return struct(
        android = ctx.attr.exports[0].android,
        java = ctx.attr.exports[0].java,
        providers = providers + [
            ctx.attr.exports[0][StarlarkAndroidResourcesInfo],
            ctx.attr.exports[0][AndroidLibraryResourceClassJarProvider],
            ctx.attr.exports[0][AndroidIdlInfo],
            ctx.attr.exports[0][BaselineProfileProvider],
            ctx.attr.exports[0][DataBindingV2Info],
            ctx.attr.exports[0][JavaInfo],
            ctx.attr.exports[0][ProguardSpecProvider],
            ctx.attr.exports[0][AndroidProguardInfo],
            ctx.attr.exports[0][AndroidNativeLibsInfo],
            ctx.attr.exports[0][AndroidCcLinkParamsInfo],
            ctx.attr.exports[0][AndroidIdeInfo],
            ctx.attr.exports[0][InstrumentedFilesInfo],
            ctx.attr.exports[0][Actions],
            ctx.attr.exports[0][OutputGroupInfo],
            ctx.attr.exports[0][DefaultInfo],
        ],
    )

android_library_sdk_transition = rule(
    implementation = _android_library_sdk_transition_impl,
    attrs = sdk_transition_attrs | {"aar": attr.output()},
    provides = [
        AndroidCcLinkParamsInfo,
        AndroidIdeInfo,
        AndroidIdlInfo,
        AndroidLibraryResourceClassJarProvider,
        AndroidNativeLibsInfo,
        JavaInfo,
    ],
)
