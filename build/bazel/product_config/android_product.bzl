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

load("@soong_injection//product_config_platforms:product_labels.bzl", _product_labels = "product_labels")
load("//build/bazel/platforms/arch/variants:constants.bzl", _arch_constants = "constants")
load(
    "//build/bazel/toolchains/clang/host/linux-x86:cc_toolchain_constants.bzl",
    "arch_to_variants",
    "variant_constraints",
    "variant_name",
)
load("@env//:env.bzl", "env")

# This dict denotes the suffixes for host platforms (keys) and the constraints
# associated with them (values). Used in transitions and tests, in addition to
# here.
host_platforms = {
    "linux_x86": [
        "@//build/bazel_common_rules/platforms/arch:x86",
        "@//build/bazel_common_rules/platforms/os:linux",
    ],
    "linux_x86_64": [
        "@//build/bazel_common_rules/platforms/arch:x86_64",
        "@//build/bazel_common_rules/platforms/os:linux",
    ],
    "linux_musl_x86": [
        "@//build/bazel_common_rules/platforms/arch:x86",
        "@//build/bazel_common_rules/platforms/os:linux_musl",
    ],
    "linux_musl_x86_64": [
        "@//build/bazel_common_rules/platforms/arch:x86_64",
        "@//build/bazel_common_rules/platforms/os:linux_musl",
    ],
    # linux_bionic is the OS for the Linux kernel plus the Bionic libc runtime,
    # but without the rest of Android.
    "linux_bionic_arm64": [
        "@//build/bazel_common_rules/platforms/arch:arm64",
        "@//build/bazel_common_rules/platforms/os:linux_bionic",
    ],
    "linux_bionic_x86_64": [
        "@//build/bazel_common_rules/platforms/arch:x86_64",
        "@//build/bazel_common_rules/platforms/os:linux_bionic",
    ],
    "darwin_arm64": [
        "@//build/bazel_common_rules/platforms/arch:arm64",
        "@//build/bazel_common_rules/platforms/os:darwin",
    ],
    "darwin_x86_64": [
        "@//build/bazel_common_rules/platforms/arch:x86_64",
        "@//build/bazel_common_rules/platforms/os:darwin",
    ],
    "windows_x86": [
        "@//build/bazel_common_rules/platforms/arch:x86",
        "@//build/bazel_common_rules/platforms/os:windows",
    ],
    "windows_x86_64": [
        "@//build/bazel_common_rules/platforms/arch:x86_64",
        "@//build/bazel_common_rules/platforms/os:windows",
    ],
}

def _is_variant_default(arch, variant):
    return variant == None or variant in (arch, "generic")

def _soong_arch_config_to_struct(soong_arch_config):
    return struct(
        arch = soong_arch_config["arch"],
        arch_variant = soong_arch_config["arch_variant"],
        cpu_variant = soong_arch_config["cpu_variant"],
    )

def _determine_target_arches_from_config(config):
    arches = []

    # ndk_abis and aml_abis explicitly get handled first as they override any setting
    # for DeviceArch, DeviceSecondaryArch in Soong:
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/android/config.go;l=455-468;drc=b45a2ea782074944f79fc388df20b06e01f265f7
    if config.get("Ndk_abis"):
        for arch_config in _arch_constants.ndk_arches:
            arches.append(_soong_arch_config_to_struct(arch_config))
        return arches
    elif config.get("Aml_abis"):
        for arch_config in _arch_constants.aml_arches:
            arches.append(_soong_arch_config_to_struct(arch_config))
        return arches

    arch = config.get("DeviceArch")
    arch_variant = config.get("DeviceArchVariant")
    cpu_variant = config.get("DeviceCpuVariant")

    if _is_variant_default(arch, arch_variant):
        arch_variant = ""
    if _is_variant_default(arch, cpu_variant):
        cpu_variant = ""

    if not arch:
        # TODO(b/258839711): determine how to better id whether a config is actually host only or we're just missing the target config
        if "DeviceArch" in config:
            fail("No architecture was specified in the product config, expected one of Ndk_abis, Aml_abis, or DeviceArch to be set:\n%s" % config)
        else:
            return arches

    arches.append(struct(
        arch = arch,
        arch_variant = arch_variant,
        cpu_variant = cpu_variant,
    ))

    arch = config.get("DeviceSecondaryArch")
    arch_variant = config.get("DeviceSecondaryArchVariant")
    cpu_variant = config.get("DeviceSecondaryCpuVariant")

    if _is_variant_default(arch, arch_variant):
        arch_variant = ""
    if _is_variant_default(arch, cpu_variant):
        cpu_variant = ""

    if arch:
        arches.append(struct(
            arch = arch,
            arch_variant = arch_variant,
            cpu_variant = cpu_variant,
        ))
    return arches

def _define_platform_for_arch(name, common_constraints, arch, secondary_arch = None):
    if secondary_arch == None:
        # When there is no secondary arch, we'll pretend it exists but is the same as the primary arch
        secondary_arch = arch
    native.platform(
        name = name,
        constraint_values = common_constraints + [
            "@//build/bazel_common_rules/platforms/arch:" + arch.arch,
            "@//build/bazel_common_rules/platforms/arch:secondary_" + secondary_arch.arch,
            "@//build/bazel_common_rules/platforms/os:android",
        ] + ["@" + v for v in variant_constraints(
            arch,
            _arch_constants.AndroidArchToVariantToFeatures[arch.arch],
        )],
    )

def _define_platform_for_arch_with_secondary(name, common_constraints, arch, secondary_arch = None):
    if secondary_arch != None:
        _define_platform_for_arch(name, common_constraints, arch, secondary_arch)
        _define_platform_for_arch(name + "_secondary", common_constraints, secondary_arch)
    else:
        _define_platform_for_arch(name, common_constraints, arch)
        native.alias(
            name = name + "_secondary",
            actual = ":" + name,
        )

def _verify_product_is_registered(name):
    """
    Verifies that this android_product() is listed in _product_labels.

    _product_labels is used to build a platform_mappings file entry from each product to its
    build settings. This is because we store most product configuration in build settings, and
    currently the only way to set build settings based on a certain platform is with a
    platform_mappings file.
    """
    my_label = native.repository_name() + "//" + native.package_name() + ":" + name
    for label in _product_labels:
        if my_label == label:
            return
    fail("All android_product() instances must have an entry in the platform_mappings file " +
         "generated by bp2build. By default the products generated from legacy android product " +
         "configurations and products listed in //build/bazel/tests/products:product_labels.bzl " +
         "are included.")

def android_product(*, name, soong_variables, extra_constraints = []):
    """
    android_product integrates product variables into Bazel platforms.

    This uses soong.variables to create constraints and platforms used by the
    bazel build. The soong.variables file itself contains a post-processed list of
    variables derived from Make variables, through soong_config.mk, generated
    during the product config step.

    Some constraints used here are handcrafted in
    //build/bazel_common_rules/platforms/{arch,os}. The rest are dynamically generated.

    If you're looking for what --config=android, --config=linux_x86_64 or most
    select statements in the BUILD files (ultimately) refer to, they're all
    created here.
    """
    _verify_product_is_registered(name)

    arch_configs = _determine_target_arches_from_config(soong_variables)

    common_constraints = extra_constraints

    # TODO(b/258802089): figure out how to deal with multiple arches for target
    if len(arch_configs) > 0:
        arch = arch_configs[0]
        secondary_arch = None
        if len(arch_configs) > 1:
            secondary_arch = arch_configs[1]

        _define_platform_for_arch_with_secondary(name, common_constraints, arch, secondary_arch)

        # These variants are mostly for mixed builds, which may request a
        # module with a certain arch
        for arch, variants in arch_to_variants.items():
            for variant in variants:
                native.platform(
                    name = name + "_android_" + arch + variant_name(variant),
                    constraint_values = common_constraints + [
                        "@//build/bazel_common_rules/platforms/arch:" + arch,
                        "@//build/bazel_common_rules/platforms/arch:secondary_" + arch,
                        "@//build/bazel_common_rules/platforms/os:android",
                    ] + ["@" + v for v in variant_constraints(
                        variant,
                        _arch_constants.AndroidArchToVariantToFeatures[arch],
                    )],
                )

        arch_transitions = [
            struct(
                name = "arm",
                arch = struct(
                    arch = "arm",
                    arch_variant = "armv7-a-neon",
                    cpu_variant = "",
                ),
                secondary_arch = None,
            ),
            struct(
                name = "arm64",
                arch = struct(
                    arch = "arm64",
                    arch_variant = "armv8-a",
                    cpu_variant = "",
                ),
                secondary_arch = struct(
                    arch = "arm",
                    arch_variant = "armv7-a-neon",
                    cpu_variant = "",
                ),
            ),
            struct(
                name = "arm64only",
                arch = struct(
                    arch = "arm64",
                    arch_variant = "armv8-a",
                    cpu_variant = "",
                ),
                secondary_arch = None,
            ),
            struct(
                name = "x86",
                arch = struct(
                    arch = "x86",
                    arch_variant = "",
                    cpu_variant = "",
                ),
                secondary_arch = None,
            ),
            struct(
                name = "x86_64",
                arch = struct(
                    arch = "x86_64",
                    arch_variant = "",
                    cpu_variant = "",
                ),
                secondary_arch = struct(
                    arch = "x86",
                    arch_variant = "",
                    cpu_variant = "",
                ),
            ),
            struct(
                name = "x86_64only",
                arch = struct(
                    arch = "x86_64",
                    arch_variant = "",
                    cpu_variant = "",
                ),
                secondary_arch = None,
            ),
        ]

        # TODO(b/249685973): Remove this, this is currently just for aabs
        # to build each architecture
        for arch in arch_transitions:
            _define_platform_for_arch_with_secondary(name + "__internal_" + arch.name, common_constraints, arch.arch, arch.secondary_arch)

    # Now define the host platforms. We need a host platform per product because
    # the host platforms still use the product variables.
    # TODO(b/262753134): Investigate making the host platforms product-independant
    for suffix, constraints in host_platforms.items():
        # Add RBE properties if the host platform support it.
        exec_properties = {}
        if "linux" in suffix and env.get("DEVICE_TEST_RBE_DOCKER_IMAGE_LINK"):
            exec_properties = {
                "container-image": env.get("DEVICE_TEST_RBE_DOCKER_IMAGE_LINK").replace("_atChar_", "@").replace("_colonChar_", ":"),
                "dockerNetwork": "standard",
                "dockerPrivileged": "true",
                "dockerRunAsRoot": "true",
                "OSFamily": "Linux",
            }
        native.platform(
            name = name + "_" + suffix,
            constraint_values = common_constraints + constraints,
            exec_properties = exec_properties,
        )
