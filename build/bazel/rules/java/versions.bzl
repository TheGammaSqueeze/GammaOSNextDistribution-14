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
"""Constants and utility functions relating to Java versions and how they map to SDK versions.
"""

load("//build/bazel/rules/common:api.bzl", "api")

# The default java version used absent any java_version or sdk_version specification.
_DEFAULT_VERSION = 17

# All available java versions
_ALL_VERSIONS = [
    7,
    8,
    9,
    11,
    17,
]

_VERSION_TO_CONFIG_SETTING = {
    java_version: "config_setting_java_%s" % java_version
    for java_version in _ALL_VERSIONS
}

def _compatible_versions_for_api_level(api_level):
    """Returns all possible java versions that can be used at the given api level."""
    if api_level in (api.FUTURE_API_LEVEL, api.NONE_API_LEVEL):
        return _ALL_VERSIONS
    if api_level <= 23:
        return [7]
    if api_level <= 29:
        return [
            7,
            8,
        ]
    if api_level <= 31:
        return [
            7,
            8,
            9,
        ]
    if api_level <= 33:
        return [
            7,
            8,
            9,
            11,
        ]
    return _ALL_VERSIONS

def _supports_pre_java_9(api_level):
    return any([
        version < 9
        for version in _compatible_versions_for_api_level(api_level)
    ])

def _supports_post_java_9(api_level):
    return any([
        version >= 9
        for version in _compatible_versions_for_api_level(api_level)
    ])

_NORMALIZED_VERSIONS = {
    "1.7": 7,
    "7": 7,
    "1.8": 8,
    "8": 8,
    "1.9": 9,
    "9": 9,
    "11": 11,
    "17": 17,
}

def _default_version(api_level):
    """Returns the default java version for the input api level."""
    return max(_compatible_versions_for_api_level(api_level))

def _get_version(java_version = None, api_level = None):
    """Returns the java version to use for a given target based on the java_version set by this target and the api_level_string extracted from sdk_version."""
    if java_version:
        return _NORMALIZED_VERSIONS[java_version]
    elif api_level:
        return _default_version(api_level)
    return _DEFAULT_VERSION

def _kt_jvm_version_from_normalized_java_version(java_version):
    if java_version == 7:
        return "1.6"
    elif java_version == 8:
        return "1.8"
    else:
        return str(java_version)

java_versions = struct(
    ALL_VERSIONS = _ALL_VERSIONS,
    VERSION_TO_CONFIG_SETTING = _VERSION_TO_CONFIG_SETTING,
    compatible_versions_for_api_level = _compatible_versions_for_api_level,
    get_version = _get_version,
    kt_jvm_version_from_normalized_java_version = _kt_jvm_version_from_normalized_java_version,
    supports_pre_java_9 = _supports_pre_java_9,
    supports_post_java_9 = _supports_post_java_9,
)
