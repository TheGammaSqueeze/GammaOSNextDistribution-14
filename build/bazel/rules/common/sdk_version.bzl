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

load("//build/bazel/rules/common:api.bzl", "api")

# Only scopes that are available in prebuilts (and "none") are listed
# here for now, but the list should eventually match Soong's SdkKind
# enum.
_KIND_PUBLIC = "public"
_KIND_SYSTEM = "system"
_KIND_TEST = "test"
_KIND_SYSTEM_SERVER = "system_server"
_KIND_MODULE = "module"
_KIND_CORE = "core"
_KIND_NONE = "none"
_KIND_CORE_PLATFORM = "core_platform"
_ALL_KINDS = [
    _KIND_PUBLIC,
    _KIND_SYSTEM,
    _KIND_TEST,
    _KIND_SYSTEM_SERVER,
    _KIND_MODULE,
    _KIND_CORE,
    _KIND_NONE,
    # _KIND_CORE_PLATFORM, # TODO: Add when core_plaform is supported in b builds
]

# Starlark implementation of SdkSpecFrom at https://cs.android.com/android/platform/build/soong/+/master:android/sdk_version.go;l=248-299;drc=69f4218c4feaeca953237cd9e76a9a8cc423d3e3.
def _sdk_spec_from(sdk_version):
    """Parses an sdk_version string into kind and api_level.

    Args:
        sdk_version: a string to specify which SDK version to depend on.
            - The empty string maps to the full set of private APIs and is currently unsupported.
            - "core_platform" maps to the module scope of the core system modules.
            - "none" maps to no SDK (used for bootstrapping the core).
            - Otherwise, the format is "{kind}_{api_level}", where kind must be one of the strings
              in ALL_KINDS, and api_level is either an integer, and android codename, or "current".
              The default kind is "public", and can be omitted by simply providing "{api_level}".

    Returns:
        A struct with a kind attribute set to one of the string in ALL_KINDS, and an api_level
        attribute as returned by api.bzl's parse_api_level_from_version.
    """
    if not sdk_version:
        fail("Only prebuilt SDK versions are available, sdk_version must be specified and non-empty.")
    if sdk_version == "core_platform":
        fail("Only prebuilt SDK versions are available, sdk_version core_platform is not yet supported.")
    if sdk_version == "none":
        return struct(kind = _KIND_NONE, api_level = api.NONE_API_LEVEL, _api_level_string = "(no version)")
    if type(sdk_version) != type(""):
        fail("sdk_version must be a string")
    sep_index = sdk_version.rfind("_")
    api_level_string = sdk_version if sep_index < 0 else sdk_version[sep_index + 1:]
    api_level = api.parse_api_level_from_version(api_level_string)
    kind = _KIND_PUBLIC if sep_index == -1 else sdk_version[:sep_index]
    if kind not in _ALL_KINDS:
        fail("kind %s parsed from sdk_version %s must be one of %s" % (
            kind,
            sdk_version,
            ",".join(_ALL_KINDS),
        ))
    return struct(kind = kind, api_level = api_level, _api_level_string = api_level_string)

def _api_level_string_with_fallback(api_level_string, sdk_version):
    return api_level_string if api_level_string else _sdk_spec_from(sdk_version)._api_level_string

sdk_version = struct(
    KIND_PUBLIC = _KIND_PUBLIC,
    KIND_SYSTEM = _KIND_SYSTEM,
    KIND_TEST = _KIND_TEST,
    KIND_SYSTEM_SERVER = _KIND_SYSTEM_SERVER,
    KIND_MODULE = _KIND_MODULE,
    KIND_CORE = _KIND_CORE,
    KIND_NONE = _KIND_NONE,
    KIND_CORE_PLATFORM = _KIND_CORE_PLATFORM,
    ALL_KINDS = _ALL_KINDS,
    api_level_string_with_fallback = _api_level_string_with_fallback,
    sdk_spec_from = _sdk_spec_from,
)
