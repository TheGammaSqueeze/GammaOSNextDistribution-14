# Copyright (C) 2023 The Android Open Source Project
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

# An API level, can be a finalized (numbered) API, a preview (codenamed) API, or
# the future API level (10000). Can be parsed from a string with
# parse_api_level_with_version.

load("@bazel_skylib//lib:dicts.bzl", "dicts")
load("@soong_injection//api_levels:platform_versions.bzl", "platform_versions")
load(":api_constants.bzl", "api_levels_released_versions")

_NONE_API_LEVEL_INT = -1
_PREVIEW_API_LEVEL_BASE = 9000  # Base constant for preview API levels.
_FUTURE_API_LEVEL_INT = 10000  # API Level associated with an arbitrary future release

# Dict of unfinalized codenames to a placeholder preview API int.
def _preview_codenames_to_ints(platform_sdk_variables):
    return {
        codename: _PREVIEW_API_LEVEL_BASE + i
        for i, codename in enumerate(platform_sdk_variables.platform_version_active_codenames)
    }

# Returns true if a string or int version is in preview (not finalized).
def _is_preview(version, platform_sdk_variables):
    preview_codenames_to_ints = _preview_codenames_to_ints(platform_sdk_variables)
    if type(version) == "string" and version.isdigit():
        # normalize int types internally
        version = int(version)

    # Future / current / none is considered as a preview.
    if version in ("current", "(no version)", _FUTURE_API_LEVEL_INT, _NONE_API_LEVEL_INT):
        return True

    # api can be either the codename or the int level (9000+)
    return version in preview_codenames_to_ints or version in preview_codenames_to_ints.values()

# Return 10000 for unfinalized versions, otherwise return unchanged.
def _final_or_future(version, platform_sdk_variables):
    if _is_preview(version = version, platform_sdk_variables = platform_sdk_variables):
        return _FUTURE_API_LEVEL_INT
    else:
        return version

def _api_levels_with_previews(platform_sdk_variables):
    return dicts.add(
        api_levels_released_versions,
        _preview_codenames_to_ints(platform_sdk_variables),
    )

# @unused
def _api_levels_with_final_codenames(platform_sdk_variables):
    if platform_sdk_variables.platform_sdk_final and platform_sdk_variables.platform_sdk_version:
        return api_levels_released_versions
    return dicts.add(
        api_levels_released_versions,
        {"current": _final_or_future(
            version = platform_sdk_variables.platform_sdk_version,
            platform_sdk_variables = platform_sdk_variables,
        )},
    )

# parse_api_level_from_version is a Starlark implementation of ApiLevelFromUser
# at https://cs.android.com/android/platform/superproject/+/master:build/soong/android/api_levels.go;l=221-250;drc=5095a6c4b484f34d5c4f55a855d6174e00fb7f5e
def _parse_api_level_from_version(version, platform_sdk_variables):
    """converts the given string `version` to an api level

    Args:
        version: must be non-empty. Inputs that are not "current", known
        previews, finalized codenames, or convertible to an integer will return
        an error.

    Returns: The api level as an int.
    """
    if version == "":
        fail("API level string must be non-empty")

    if version == "current":
        return _FUTURE_API_LEVEL_INT

    if _is_preview(version = version, platform_sdk_variables = platform_sdk_variables):
        return _preview_codenames_to_ints(platform_sdk_variables).get(version) or int(version)

    # Not preview nor current.
    #
    # If the level is the codename of an API level that has been finalized, this
    # function returns the API level number associated with that API level. If
    # the input is *not* a finalized codename, the input is returned unmodified.
    canonical_level = api_levels_released_versions.get(version)
    if not canonical_level:
        if not version.isdigit():
            fail("version %s could not be parsed as integer and is not a recognized codename" % version)
        return int(version)
    return canonical_level

def _default_app_target_sdk_string(platform_sdk_variables):
    if platform_sdk_variables.platform_sdk_final:
        return str(platform_sdk_variables.platform_sdk_version)

    if not platform_sdk_variables.platform_sdk_codename:
        # soong returns NoneApiLevel here value: "(no version)", number: -1, isPreview: true
        #
        # fail fast instead of returning an arbitrary value.
        fail("Platform_sdk_codename must be set.")

    if platform_sdk_variables.platform_sdk_codename == "REL":
        fail("Platform_sdk_codename should not be REL when Platform_sdk_final is false")

    return platform_sdk_variables.platform_sdk_codename

# Starlark implementation of DefaultAppTargetSDK from build/soong/android/config.go
# https://cs.android.com/android/platform/superproject/+/master:build/soong/android/config.go;l=875-889;drc=b0dc477ef740ec959548fe5517bd92ac4ea0325c
# check what you want returned for codename == "" case before using
def _default_app_target_sdk(platform_sdk_variables):
    """default_app_target_sdk returns the API level that platform apps are targeting.
       This converts a codename to the exact ApiLevel it represents.
    """
    return _parse_api_level_from_version(
        version = _default_app_target_sdk_string(platform_sdk_variables),
        platform_sdk_variables = platform_sdk_variables,
    )

# Starlark implementation of EffectiveVersionString from build/soong/android/api_levels.go
# EffectiveVersionString converts an api level string into the concrete version string that the module
# should use. For modules targeting an unreleased SDK (meaning it does not yet have a number)
# it returns the codename (P, Q, R, etc.)
def _effective_version_string(
        version,
        platform_sdk_variables):
    if not _is_preview(version, platform_sdk_variables):
        return version
    default_app_target_sdk_string = _default_app_target_sdk_string(platform_sdk_variables)
    if not _is_preview(default_app_target_sdk_string, platform_sdk_variables):
        return default_app_target_sdk_string
    if version in platform_sdk_variables.platform_version_active_codenames:
        return version
    return default_app_target_sdk_string

def api_from_product(platform_sdk_variables):
    """Provides api level-related utility functions from platform variables.

    Args:
        platform_sdk_variables: a struct that must provides the 4
          product variables: platform_sdk_final (boolean),
          platform_sdk_version (int), platform_sdk_codename (string),
          platform_version_active_codenames (string list)

    Returns: A struct containing utility functions and constants
        around api levels, e.g. for parsing them from user input and for
        overriding them based on defaults and the input product variables.
    """
    return struct(
        NONE_API_LEVEL = _NONE_API_LEVEL_INT,
        FUTURE_API_LEVEL = _FUTURE_API_LEVEL_INT,
        is_preview = lambda version: _is_preview(
            version = version,
            platform_sdk_variables = platform_sdk_variables,
        ),
        final_or_future = lambda version: _final_or_future(
            version = version,
            platform_sdk_variables = platform_sdk_variables,
        ),
        default_app_target_sdk_string = lambda: _default_app_target_sdk_string(platform_sdk_variables),
        default_app_target_sdk = lambda: _default_app_target_sdk(platform_sdk_variables),
        parse_api_level_from_version = lambda version: _parse_api_level_from_version(
            version = version,
            platform_sdk_variables = platform_sdk_variables,
        ),
        api_levels = _api_levels_with_previews(platform_sdk_variables),
        effective_version_string = lambda version: _effective_version_string(
            version = version,
            platform_sdk_variables = platform_sdk_variables,
        ),
    )

# TODO(b/300428335): access these variables in a transition friendly way.
api = api_from_product(platform_versions)
