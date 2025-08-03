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

load("@soong_injection//api_levels:platform_versions.bzl", "platform_versions")
load("//build/bazel/rules/common:api.bzl", "api", "api_from_product")
load(":manifest_fixer_internal.bzl", _internal = "manifest_fixer_internal")

# TODO(b/300428335): access these variables in a transition friendly way.
_PLATFORM_SDK_VERSION = platform_versions.platform_sdk_version
_PLATFORM_SDK_CODENAME = platform_versions.platform_sdk_codename
_PLATFORM_VERSION_ACTIVE_CODENAMES = platform_versions.platform_version_active_codenames

# Starlark implementation of TargetSdkVersionForManifestFixer from build/soong/java/android_manifest.go
def _target_sdk_version_for_manifest_fixer(
        target_sdk_version,
        platform_sdk_final,
        has_unbundled_build_apps):
    platform_sdk_variables = struct(
        platform_sdk_final = platform_sdk_final,
        platform_sdk_version = _PLATFORM_SDK_VERSION,
        platform_sdk_codename = _PLATFORM_SDK_CODENAME,
        platform_version_active_codenames = _PLATFORM_VERSION_ACTIVE_CODENAMES,
    )
    return _internal.target_sdk_version_for_manifest_fixer(
        target_sdk_version = target_sdk_version,
        has_unbundled_build_apps = has_unbundled_build_apps,
        platform_sdk_variables = platform_sdk_variables,
    )

# TODO: b/301430823 - Only pass ctx.actions to limit the scope of what this function can access.
def _fix(
        ctx,
        manifest_fixer,
        in_manifest,
        out_manifest,
        mnemonic = "FixAndroidManifest",
        test_only = None,
        min_sdk_version = None,
        target_sdk_version = None):
    args = ctx.actions.args()
    if test_only:
        args.add("--test-only")
    if min_sdk_version:
        args.add("--minSdkVersion", min_sdk_version)
    if target_sdk_version:
        args.add("--targetSdkVersion", target_sdk_version)
    if min_sdk_version or target_sdk_version:
        args.add("--raise-min-sdk-version")
    args.add(in_manifest)
    args.add(out_manifest)
    ctx.actions.run(
        inputs = [in_manifest],
        outputs = [out_manifest],
        executable = manifest_fixer,
        arguments = [args],
        mnemonic = mnemonic,
    )

manifest_fixer = struct(
    fix = _fix,
    target_sdk_version_for_manifest_fixer = _target_sdk_version_for_manifest_fixer,
)
