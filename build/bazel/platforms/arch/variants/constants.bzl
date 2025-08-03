"""Constants for arch/cpu variants/features."""

# Copyright (C) 2022 The Android Open Source Project
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

load(
    "@soong_injection//product_config:arch_configuration.bzl",
    _aml_arches = "aml_arches",
    _android_arch_feature_for_arch_variant = "android_arch_feature_for_arch_variants",
    _arch_to_cpu_variants = "arch_to_cpu_variants",
    _arch_to_features = "arch_to_features",
    _arch_to_variants = "arch_to_variants",
    _ndk_arches = "ndk_arches",
)

def _flatten_string_list_dict_to_set(string_list_dict):
    ret = {}
    for l in string_list_dict.values():
        for i in l:
            ret[i] = True
    return ret

_arch_variants = _flatten_string_list_dict_to_set(_arch_to_variants)
_cpu_variants = _flatten_string_list_dict_to_set(_arch_to_cpu_variants)
_arch_features = _flatten_string_list_dict_to_set(_arch_to_features)

constants = struct(
    AvailableArchVariants = _arch_variants,
    AvailableCpuVariants = _cpu_variants,
    AvailableArchFeatures = _arch_features,
    ArchToVariants = _arch_to_variants,
    CpuToVariants = _arch_to_cpu_variants,
    ArchToFeatures = _arch_to_features,
    AndroidArchToVariantToFeatures = _android_arch_feature_for_arch_variant,
    aml_arches = _aml_arches,
    ndk_arches = _ndk_arches,
)

def power_set(items, *, include_empty = True):
    """Calculates the power set of the given items."""

    def _exp(x, y):
        result = 1
        for _ in range(y):
            result *= x
        return result

    power_set = []
    n = len(items)
    for i in range(0 if include_empty else 1, _exp(2, n)):
        combination = []
        for j in range(n):
            if (i >> j) % 2 == 1:
                combination.append(items[j])
        power_set.append(combination)
    return power_set

arch_variant_to_constraints = {
    "arm": "//build/bazel_common_rules/platforms/arch:arm",
    "arm64": "//build/bazel_common_rules/platforms/arch:arm64",
    "x86": "//build/bazel_common_rules/platforms/arch:x86",
    "x86_64": "//build/bazel_common_rules/platforms/arch:x86_64",
    "riscv64": "//build/bazel_common_rules/platforms/arch:riscv64",
    "android": "//build/bazel_common_rules/platforms/os:android",
    "darwin": "//build/bazel_common_rules/platforms/os:darwin",
    "linux": "//build/bazel_common_rules/platforms/os:linux",
    "linux_bionic": "//build/bazel_common_rules/platforms/os:linux_bionic",
    "linux_glibc": "//build/bazel_common_rules/platforms/os:linux_glibc",
    "linux_musl": "//build/bazel_common_rules/platforms/os:linux_musl",
    "windows": "//build/bazel_common_rules/platforms/os:windows",
}
