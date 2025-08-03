# Copyright (C) 2021 The Android Open Source Project
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

# Rules for declaring Android licenses used by a package.
# See: go/license-checking-v2

load("@rules_license//rules:license.bzl", "license")

_special_licenses = {
    "legacy_by_exception_only": 0,
    "legacy_not_a_contribution": 0,
    "legacy_not_allowed": 0,
    "legacy_notice": 0,
    "legacy_permissive": 0,
    "legacy_proprietary": 0,
    "legacy_reciprocal": 0,
    "legacy_restricted": 0,
    "legacy_unencumbered": 0,
    "legacy_unknown": 0,
}
_spdx_license_prefix = "SPDX-license-identifier-"
_spdx_package = "//build/soong/licenses:"

def _remap_license_kind(license_kind):
    # In bazel license_kind is a label.
    # First, map legacy license kinds.
    if license_kind in _special_licenses:
        return _spdx_package + license_kind

    # Map SPDX licenses to the ones defined in build/soong/licenses.
    if license_kind.startswith(_spdx_license_prefix):
        return _spdx_package + license_kind

    # Last resort.
    return license_kind

# buildifier: disable=function-docstring-args
def android_license(
        name,
        license_text = "__NO_LICENSE__",  # needed as `license` expects it
        visibility = ["//visibility:public"],
        license_kinds = [],
        copyright_notice = None,
        package_name = None,
        tags = []):
    """Wrapper for license rule.

    Args:
      name: str target name.
      license_text: str Filename of the license file
      visibility: list(label) visibility spec
      license_kinds: list(text) list of license_kind targets.
      copyright_notice: str Copyright notice associated with this package.
      package_name : str A human readable name identifying this package. This
                     may be used to produce an index of OSS packages used by
                     an application.
      tags: list(str) tags applied to the rule
    """

    license(
        name = name,
        license_kinds = [_remap_license_kind(x) for x in license_kinds],
        license_text = license_text,
        copyright_notice = copyright_notice,
        package_name = package_name,
        visibility = visibility,
        tags = tags,
    )
