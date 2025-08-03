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

load("//build/bazel/product_config:android_product.bzl", "host_platforms")
load(":cc_library_common.bzl", "path_in_list")

CFI_FEATURE = "android_cfi"
CFI_ASSEMBLY_FEATURE = "android_cfi_assembly_support"
DISABLE_CFI_FEATURE = "-" + CFI_FEATURE

# This propagates CFI enablement and disablement down the dependency graph
def apply_cfi_deps(
        features,
        old_cli_features,
        path,
        cfi_include_paths,
        cfi_exclude_paths,
        enable_cfi,
        platform):
    new_cli_features = list(old_cli_features)
    disabled_by_product_vars = (
        not enable_cfi or
        path_in_list(path, cfi_exclude_paths)
    )
    enabled_by_product_vars = (
        path_in_list(path, cfi_include_paths) and _os_is_android(platform[0].name)
    )

    # Counterintuitive though it may be, we propagate not only the enablement
    # of CFI down the dependency graph, but also its disablement
    if (
        CFI_FEATURE in features or
        enabled_by_product_vars
    ) and not disabled_by_product_vars:
        if CFI_FEATURE not in new_cli_features:
            new_cli_features.append(CFI_FEATURE)
        if DISABLE_CFI_FEATURE in new_cli_features:
            new_cli_features.remove(DISABLE_CFI_FEATURE)
    else:
        if DISABLE_CFI_FEATURE not in new_cli_features:
            new_cli_features.append(DISABLE_CFI_FEATURE)
        if CFI_FEATURE in new_cli_features:
            new_cli_features.remove(CFI_FEATURE)

    return new_cli_features

# Since CFI is only propagated down static deps, we use this transition to
# remove it from shared deps that it's added to. It is also used to prevent
# stub libraries from having two configurations for the same dependency.
def apply_drop_cfi(old_cli_features):
    new_cli_features = list(old_cli_features)
    if CFI_FEATURE in old_cli_features:
        new_cli_features.remove(CFI_FEATURE)
    if DISABLE_CFI_FEATURE not in old_cli_features:
        new_cli_features.append(DISABLE_CFI_FEATURE)
    return new_cli_features

def _os_is_android(platform):
    if type(platform) != "string":
        fail("platform argument should be a string!")
    for os_suffix in host_platforms:
        if platform.endswith(os_suffix):
            return False
    return True
