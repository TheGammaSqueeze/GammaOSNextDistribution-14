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

load("@rules_android//rules:common.bzl", "common")
load("@rules_android//rules:migration_tag_DONOTUSE.bzl", "add_migration_tag")
load(
    "//build/bazel/rules/android/android_binary_aosp_internal:rule.bzl",
    "android_binary_aosp_internal_macro",
)
load("//build/bazel/rules/java:sdk_transition.bzl", "sdk_transition_attrs")
load(":debug_signing_key.bzl", "debug_signing_key")

# TODO(b/277801336): document these attributes.
def _android_binary_helper(**attrs):
    """ Duplicates the logic in top-level android_binary macro in
        rules_android/rules/android_binary.bzl but uses
        android_binary_aosp_internal_macro instead of android_binary_internal_macro.

        https://docs.bazel.build/versions/master/be/android.html#android_binary

        Args:
          **attrs: Rule attributes
    """
    android_binary_aosp_internal_name = ":" + attrs["name"] + common.PACKAGED_RESOURCES_SUFFIX
    android_binary_aosp_internal_macro(
        **dict(
            attrs,
            name = android_binary_aosp_internal_name[1:],
            visibility = ["//visibility:private"],
        )
    )

    # The following attributes are unknown the native android_binary rule and must be removed
    # prior to instantiating it.
    attrs.pop("$enable_manifest_merging", None)
    attrs["proguard_specs"] = []
    attrs.pop("sdk_version")
    if "updatable" in attrs:
        attrs.pop("updatable")

    native.android_binary(
        application_resources = android_binary_aosp_internal_name,
        **add_migration_tag(attrs)
    )

def android_binary(
        name,
        certificate = None,
        certificate_name = None,
        sdk_version = None,
        errorprone_force_enable = None,
        javacopts = [],
        java_version = None,
        optimize = True,
        tags = [],
        target_compatible_with = [],
        testonly = False,
        visibility = None,
        **kwargs):
    """ android_binary macro wrapper that handles custom attrs needed in AOSP
       Bazel macro to find and create a keystore to use for debug_signing_keys
       with @rules_android android_binary.

    This module emulates the Soong behavior which allows a developer to specify
    a specific module name for the android_app_certificate or the name of a
    .pem/.pk8 certificate/key pair in a directory specified by the
    DefaultAppCertificate product variable. In either case, we convert the specified
    .pem/.pk8 certificate/key pair to a JKS .keystore file before passing it to the
    android_binary rule.

    Arguments:
        certificate: Bazel target
        certificate_name: string, name of private key file in default certificate directory
        errorprone_force_enable: set this to true to always run Error Prone
        on this target (overriding the value of environment variable
        RUN_ERROR_PRONE). Error Prone can be force disabled for an individual
        module by adding the "-XepDisableAllChecks" flag to javacopts
        **kwargs: map, additional args to pass to android_binary

    """

    opts = javacopts
    if errorprone_force_enable == None:
        # TODO (b/227504307) temporarily disable errorprone until environment variable is handled
        opts = opts + ["-XepDisableAllChecks"]

    debug_signing_keys = kwargs.pop("debug_signing_keys", [])
    debug_signing_keys.extend(debug_signing_key(name, certificate, certificate_name))

    if optimize:
        kwargs["proguard_specs"] = [
            "//build/make/core:global_proguard_flags",
        ] + kwargs.get("proguard_specs", [])

    bin_name = name + "_private"
    _android_binary_helper(
        name = bin_name,
        debug_signing_keys = debug_signing_keys,
        javacopts = opts,
        target_compatible_with = target_compatible_with,
        tags = tags + ["manual"],
        testonly = testonly,
        visibility = ["//visibility:private"],
        sdk_version = sdk_version,
        **kwargs
    )

    android_binary_sdk_transition(
        name = name,
        sdk_version = sdk_version,
        java_version = java_version,
        exports = bin_name,
        tags = tags,
        target_compatible_with = target_compatible_with,
        testonly = testonly,
        visibility = visibility,
    )

# The list of providers to forward was determined using cquery on one
# of the example targets listed under EXAMPLE_WRAPPER_TARGETS at
# //build/bazel/ci/target_lists.sh. It may not be exhaustive. A unit
# test ensures that the wrapper's providers and the wrapped rule's do
# match.
def _android_binary_sdk_transition_impl(ctx):
    return struct(
        android = ctx.attr.exports[0].android,
        providers = [
            ctx.attr.exports[0][AndroidIdlInfo],
            ctx.attr.exports[0][InstrumentedFilesInfo],
            ctx.attr.exports[0][DataBindingV2Info],
            ctx.attr.exports[0][JavaInfo],
            ctx.attr.exports[0][AndroidIdeInfo],
            ctx.attr.exports[0][ApkInfo],
            ctx.attr.exports[0][AndroidPreDexJarInfo],
            ctx.attr.exports[0][AndroidFeatureFlagSet],
            ctx.attr.exports[0][OutputGroupInfo],
            ctx.attr.exports[0][DefaultInfo],
        ],
    )

android_binary_sdk_transition = rule(
    implementation = _android_binary_sdk_transition_impl,
    attrs = sdk_transition_attrs,
)
