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

load("//build/bazel/rules/common:api.bzl", "api")

def maybe_override_min_sdk_version(min_sdk_version, override_min_sdk_version):
    """
    Override a min_sdk_version with another if higher.

    Normalizes string codenames to API ints for direct comparisons.

    Args:
      min_sdk_version: The min_sdk_version to potentially be overridden, as a string.
        Can be "current", or a number.
      override_min_sdk_version: The version to potentially override min_sdk_version with, as a string.
        Can be a number, of a known api level codename.
    Returns:
      Either min_sdk_version or override_min_sdk_version, converted to a string representation of a number.
    """
    if min_sdk_version == "current":
        min_sdk_version = "10000"
    if not str(min_sdk_version).isdigit():
        fail("%s must only contain digits." % min_sdk_version)

    min_api_level = int(min_sdk_version)

    if str(override_min_sdk_version).isdigit():
        override_api_level = int(override_min_sdk_version)
    else:
        override_api_level = api.api_levels.get(override_min_sdk_version, -1)

    # Only override version numbers upwards.
    if override_api_level > min_api_level:
        min_api_level = override_api_level

    return str(min_api_level)
