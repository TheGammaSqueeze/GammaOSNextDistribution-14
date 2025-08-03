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

"""Constants and utility functions relating to prebuilt SDKs.
"""

load("//build/bazel/rules/common:api.bzl", "api")
load("//build/bazel/rules/common:sdk_version.bzl", "sdk_version")

# The highest numbered directory under prebuilts/sdk that provides an android.jar
_MAX_API_LEVEL = 34

# All api levels that have a prebuilt SDK.
_API_LEVELS = list(range(4, _MAX_API_LEVEL + 1)) + [api.FUTURE_API_LEVEL]

def _available_kinds_for_api_level(api_level):
    """Return the available SDK kinds (or scopes) under the given api level directory."""
    if api_level not in _API_LEVELS:
        fail("api_level %s is not one of %s" % (api_level, _API_LEVELS.join(",")))
    if api_level == api.FUTURE_API_LEVEL:
        return [
            sdk_version.KIND_PUBLIC,
            sdk_version.KIND_SYSTEM,
            sdk_version.KIND_TEST,
            sdk_version.KIND_SYSTEM_SERVER,
            sdk_version.KIND_MODULE,
            sdk_version.KIND_CORE,
        ]
    if api_level <= 20:
        return [sdk_version.KIND_PUBLIC]
    if api_level <= 28:
        return [
            sdk_version.KIND_PUBLIC,
            sdk_version.KIND_SYSTEM,
        ]
    if api_level == 29:
        return [
            sdk_version.KIND_PUBLIC,
            sdk_version.KIND_SYSTEM,
            sdk_version.KIND_TEST,
        ]
    return [
        sdk_version.KIND_PUBLIC,
        sdk_version.KIND_SYSTEM,
        sdk_version.KIND_TEST,
        sdk_version.KIND_SYSTEM_SERVER,
        sdk_version.KIND_MODULE,
    ]

# core-for-system-modules public starts at v30, module starts at v33.
def _available_core_kinds_for_api_level(api_level):
    """Return all core module kinds available for a given api level."""
    if api_level not in _API_LEVELS:
        fail("api_level %s is not one of %s" % (api_level, _API_LEVELS.join(",")))
    if api_level == api.FUTURE_API_LEVEL:
        return [
            sdk_version.KIND_PUBLIC,
            sdk_version.KIND_MODULE,
        ]
    if api_level <= 29:
        return []
    if api_level <= 32:
        return [sdk_version.KIND_PUBLIC]
    return [
        sdk_version.KIND_PUBLIC,
        sdk_version.KIND_MODULE,
    ]

def _to_core_kind(kind, api_level):
    """Returns the core kind corresponding to the input kind and api level."""
    if api_level not in _API_LEVELS:
        fail("api_level %s is not one of %s" % (api_level, _API_LEVELS.join(",")))
    if api_level == api.FUTURE_API_LEVEL:
        return (
            sdk_version.KIND_MODULE if kind in (
                sdk_version.KIND_MODULE,
                sdk_version.KIND_SYSTEM_SERVER,
            ) else sdk_version.KIND_PUBLIC
        )
    if api_level <= 29:
        return None
    if api_level <= 32:
        return sdk_version.KIND_PUBLIC
    return (
        sdk_version.KIND_MODULE if kind in (
            sdk_version.KIND_MODULE,
            sdk_version.KIND_SYSTEM_SERVER,
        ) else sdk_version.KIND_PUBLIC
    )

def _to_aidl_kind(kind, api_level):
    """Returns the best available framework.aidl prebuilt kind for the given kind X api level."""
    if api_level not in _API_LEVELS:
        fail("api_level %s is not one of %s" % (api_level, _API_LEVELS.join(",")))
    if api_level == api.FUTURE_API_LEVEL:
        return sdk_version.KIND_PUBLIC

    # Only 23 and 28 provide framework.aidl for system.
    if api_level in (23, 28) and kind == sdk_version.KIND_SYSTEM:
        return sdk_version.KIND_SYSTEM
    return sdk_version.KIND_PUBLIC

def _to_kind_dir(kind):
    """Maps kind as specified in sdk_version with the corresponding directory under prebuilt/sdk"""
    if kind == sdk_version.KIND_MODULE:
        return "module-lib"
    if kind == sdk_version.KIND_SYSTEM_SERVER:
        return "system-server"
    return kind

prebuilt_sdk_utils = struct(
    MAX_API_LEVEL = _MAX_API_LEVEL,
    API_LEVELS = _API_LEVELS,
    available_kinds_for_api_level = _available_kinds_for_api_level,
    available_core_kinds_for_api_level = _available_core_kinds_for_api_level,
    to_core_kind = _to_core_kind,
    to_aidl_kind = _to_aidl_kind,
    to_kind_dir = _to_kind_dir,
)
