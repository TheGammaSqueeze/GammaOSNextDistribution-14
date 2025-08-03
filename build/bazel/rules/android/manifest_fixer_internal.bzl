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
load("//build/bazel/rules/common:api.bzl", "api", "api_from_product")

visibility("private")

# Starlark implementation of shouldReturnFinalOrFutureInt from build/soong/java/android_manifest.go
# TODO: b/300916781 - In Soong this also returns true when the module is an MTS test.
def _should_return_future_int(
        target_sdk_version,
        platform_sdk_variables,
        has_unbundled_build_apps):
    if platform_sdk_variables.platform_sdk_final:
        return False
    return api_from_product(platform_sdk_variables).is_preview(target_sdk_version) and has_unbundled_build_apps

# Starlark implementation of TargetSdkVersionForManifestFixer from build/soong/java/android_manifest.go
def _target_sdk_version_for_manifest_fixer(
        target_sdk_version,
        platform_sdk_variables,
        has_unbundled_build_apps):
    if _should_return_future_int(
        target_sdk_version = target_sdk_version,
        platform_sdk_variables = platform_sdk_variables,
        has_unbundled_build_apps = has_unbundled_build_apps,
    ):
        return str(api.FUTURE_API_LEVEL)
    return api_from_product(platform_sdk_variables).effective_version_string(target_sdk_version)

manifest_fixer_internal = struct(
    target_sdk_version_for_manifest_fixer = _target_sdk_version_for_manifest_fixer,
)
