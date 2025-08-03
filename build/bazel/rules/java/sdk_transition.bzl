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

"""
Contains logic for a transition that is applied to java-based rules which
takes the sdk_version and java_version attributes and populates build settings
based on their values.
"""

load("//build/bazel/rules/common:api.bzl", "api")
load("//build/bazel/rules/common:sdk_version.bzl", "sdk_version")
load("//build/bazel/rules/java:versions.bzl", "java_versions")

_DEFAULT_API_DOMAIN = "system"  # i.e. the platform variant

def _validate_attrs(attr):
    if hasattr(attr, "sdk_version") and hasattr(attr, "_sdk_version"):
        fail("don't have both _sdk_version and sdk_version in attrs, it's confusing.")
    if not hasattr(attr, "sdk_version") and not hasattr(attr, "_sdk_version"):
        fail("must have one of _sdk_version or sdk_version attr.")

def _sdk_transition_impl(settings, attr):
    _validate_attrs(attr)
    sdk_version_attr = (
        attr.sdk_version if hasattr(attr, "sdk_version") else attr._sdk_version
    )
    java_version = attr.java_version if hasattr(attr, "java_version") else None
    host_platform = settings["//command_line_option:host_platform"]
    default_java_version = str(java_versions.get_version())

    # TODO: this condition should really be "platform is not a device".
    # More details on why we're treating java version for non-device platforms differently at the
    # definition of the //build/bazel/rules/java:host_version build setting.
    if all([host_platform == platform for platform in settings["//command_line_option:platforms"]]):
        return {
            "//build/bazel/rules/java:version": default_java_version,
            "//build/bazel/rules/java:host_version": str(
                java_versions.get_version(java_version),
            ),
            "//build/bazel/rules/java/sdk:kind": sdk_version.KIND_NONE,
            "//build/bazel/rules/java/sdk:api_level": api.NONE_API_LEVEL,
            "//build/bazel/rules/apex:api_domain": _DEFAULT_API_DOMAIN,
        }
    sdk_spec = sdk_version.sdk_spec_from(sdk_version_attr)
    final_java_version = str(java_versions.get_version(
        java_version,
        sdk_spec.api_level,
    ))

    ret = {
        "//build/bazel/rules/java:host_version": default_java_version,
        "//build/bazel/rules/java:version": final_java_version,
        "//build/bazel/rules/java/sdk:kind": sdk_spec.kind,
        "//build/bazel/rules/java/sdk:api_level": sdk_spec.api_level,
        "//build/bazel/rules/apex:api_domain": _DEFAULT_API_DOMAIN,
    }

    # uses_sdk returns true if the app sets an sdk_version _except_ `core_platform`
    # https://cs.android.com/android/_/android/platform/build/soong/+/main:java/app.go;l=253;bpv=1;bpt=0;drc=e12c083198403ec694af6c625aed11327eb2bf7f
    uses_sdk = (sdk_spec != None) and (sdk_spec.kind != sdk_version.KIND_CORE_PLATFORM)

    if uses_sdk:
        # If the app is using an SDK, build it in the "unbundled_app" api domain build setting
        # This ensures that its jni deps are building against the NDK
        # TODO - b/299360988 - Handle jni_uses_sdk_apis, jni_uses_platform_apis
        ret["//build/bazel/rules/apex:api_domain"] = "unbundled_app"

    return ret

sdk_transition = transition(
    implementation = _sdk_transition_impl,
    inputs = [
        "//command_line_option:host_platform",
        "//command_line_option:platforms",
    ],
    outputs = [
        "//build/bazel/rules/java:version",
        "//build/bazel/rules/java:host_version",
        "//build/bazel/rules/java/sdk:kind",
        "//build/bazel/rules/java/sdk:api_level",
        "//build/bazel/rules/apex:api_domain",
    ],
)

sdk_transition_attrs = {
    # This attribute must have a specific name to let the DexArchiveAspect propagate
    # through it.
    "exports": attr.label(
        cfg = sdk_transition,
    ),
    "java_version": attr.string(),
    "sdk_version": attr.string(),
    "_allowlist_function_transition": attr.label(
        default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
    ),
}
