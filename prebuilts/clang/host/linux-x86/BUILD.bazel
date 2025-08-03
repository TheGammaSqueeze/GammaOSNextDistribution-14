# NOTE: THIS FILE IS EXPERIMENTAL FOR THE BAZEL MIGRATION AND NOT USED FOR
# YOUR BUILDS CURRENTLY.
#
# It is not yet the source of truth for your build. If you're looking to modify
# the build file, modify the Android.bp file instead. Do *not* modify this file
# unless you have coordinated with the team managing the Soong to Bazel
# migration.

"""
Toolchain config
"""

load("@bazel_skylib//rules:common_settings.bzl", "string_flag")
load("@env//:env.bzl", "env")
load(
    "//build/bazel/toolchains/clang/host/linux-x86:cc_toolchain_config.bzl",
    "CLANG_TOOLS",
    "android_cc_toolchain",
    "clang_tool_output_group",
    "clang_version_info",
    "expand_feature_flags",
    "toolchain_definition",
)
load(
    "//build/bazel/toolchains/clang/host/linux-x86:cc_toolchain_constants.bzl",
    "arch_to_variants",
    "arches",
    "device_compatibility_flags_non_darwin",
    "device_compatibility_flags_non_windows",
    "generated_config_constants",
    "libclang_rt_prebuilt_map",
    "libclang_ubsan_minimal_rt_prebuilt_map",
    "variant_name",
    "x86_64_host_toolchains",
    "x86_64_musl_host_toolchains",
    "x86_host_toolchains",
    "x86_musl_host_toolchains",
    _bionic_crt = "bionic_crt",
    _musl_crt = "musl_crt",
)
load("//build/bazel/platforms/arch/variants:constants.bzl", _arch_constants = "constants")

filegroup(name = "empty")

# Different clang versions are configured here.
clang_version_info(
    name = "clang",
    clang_files = glob(["**/*"]),
    clang_short_version = ":clang_short_version",
    clang_version = ":clang_version",
)

# x86_64 toolchain definitions
[
    android_cc_toolchain(
        name = "cc_toolchain_x86_64" + variant_name(variant),
        clang_version = ":clang",
        compiler_flags = generated_config_constants.X86_64ToolchainCflags +
                         generated_config_constants.X86_64ArchVariantCflags[variant.arch_variant] +
                         expand_feature_flags(
                             variant.arch_variant,
                             _arch_constants.AndroidArchToVariantToFeatures[arches.X86_64],
                             generated_config_constants.X86_64ArchFeatureCflags,
                         ) + generated_config_constants.X86_64Cflags,
        crt = _bionic_crt,
        libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_x86_64"],
        libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_x86_64"],
        linker_flags = generated_config_constants.X86_64ToolchainLdflags + generated_config_constants.X86_64Lldflags,
        target_arch = arches.X86_64,
        target_os = "android",
        toolchain_identifier = "x86_64-toolchain",
    )
    for variant in arch_to_variants[arches.X86_64]
]

# x86 toolchain definitions.
[
    android_cc_toolchain(
        name = "cc_toolchain_x86" + variant_name(variant),
        clang_version = ":clang",
        compiler_flags = generated_config_constants.X86ToolchainCflags +
                         generated_config_constants.X86ArchVariantCflags[variant.arch_variant] +
                         expand_feature_flags(
                             variant.arch_variant,
                             _arch_constants.AndroidArchToVariantToFeatures[arches.X86],
                             generated_config_constants.X86ArchFeatureCflags,
                         ) + generated_config_constants.X86Cflags,
        crt = _bionic_crt,
        libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_x86"],
        libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_x86"],
        linker_flags = generated_config_constants.X86ToolchainLdflags + generated_config_constants.X86Lldflags,
        target_arch = arches.X86,
        target_os = "android",
        toolchain_identifier = "x86-toolchain",
    )
    for variant in arch_to_variants[arches.X86]
]

# arm64 toolchain definitions.
[
    android_cc_toolchain(
        name = "cc_toolchain_arm64" + variant_name(variant),
        clang_version = ":clang",
        compiler_flags = generated_config_constants.Arm64Cflags +
                         generated_config_constants.Arm64ArchVariantCflags[variant.arch_variant] +
                         generated_config_constants.Arm64CpuVariantCflags.get(
                             variant.cpu_variant,
                             [],
                         ),
        crt = _bionic_crt,
        libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_arm64"],
        libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_arm64"],
        linker_flags = generated_config_constants.Arm64CpuVariantLdflags.get(
            variant.cpu_variant,
            [],
        ) + generated_config_constants.Arm64Lldflags,
        target_arch = arches.Arm64,
        target_os = "android",
        toolchain_identifier = "arm64-toolchain",
    )
    for variant in arch_to_variants[arches.Arm64]
]

# arm32 toolchain definitions.
[
    android_cc_toolchain(
        name = "cc_toolchain_arm" + variant_name(variant),
        clang_version = ":clang",
        compiler_flags = generated_config_constants.ArmCflags +
                         generated_config_constants.ArmToolchainCflags +
                         generated_config_constants.ArmArchVariantCflags[variant.arch_variant] +
                         generated_config_constants.ArmCpuVariantCflags.get(
                             variant.cpu_variant,
                             [],
                         ),
        crt = _bionic_crt,
        libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_arm"],
        libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:android_arm"],
        # do not pass "ld"-only flags as Bazel is only using lld. Ensure that all flags are lld-compatible.
        linker_flags = generated_config_constants.ArmLldflags,
        target_arch = arches.Arm,
        target_os = "android",
        toolchain_identifier = "arm-toolchain",
    )
    for variant in arch_to_variants[arches.Arm]
]

# Toolchain to compile for the linux host.
# TODO(b/186628704): automatically generate from Soong.
android_cc_toolchain(
    name = "cc_toolchain_x86_64_linux_host",
    clang_version = ":clang",
    compiler_flags = generated_config_constants.LinuxCflags +
                     generated_config_constants.LinuxGlibcCflags +
                     generated_config_constants.LinuxX8664Cflags +
    # Added by stl.go for non-bionic toolchains.
    [
        "-nostdinc++",
    ],
    crt = False,
    gcc_toolchain = generated_config_constants.LinuxGccRoot,
    libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_glibc_x86_64"],
    libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_glibc_x86_64"],
    linker_flags = generated_config_constants.LinuxGlibcLdflags +
                   generated_config_constants.LinuxLdflags +
                   generated_config_constants.LinuxX8664Ldflags +
                   device_compatibility_flags_non_windows +
                   device_compatibility_flags_non_darwin,
    rtti_toggle = False,
    target_arch = "x86_64",
    target_flags = ["--target=x86_64-linux-gnu"],
    target_os = "linux_glibc",
    toolchain_identifier = "x86_64-toolchain",
)

# Toolchain to compile for the linux x86 target.
android_cc_toolchain(
    name = "cc_toolchain_x86_linux_host",
    clang_version = ":clang",
    compiler_flags = generated_config_constants.LinuxCflags +
                     generated_config_constants.LinuxGlibcCflags +
                     generated_config_constants.LinuxX86Cflags +
    # Added by stl.go for non-bionic toolchains.
    [
        "-nostdinc++",
    ],
    crt = False,
    gcc_toolchain = generated_config_constants.LinuxGccRoot,
    libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_glibc_x86"],
    libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_glibc_x86"],
    linker_flags = generated_config_constants.LinuxGlibcLdflags +
                   generated_config_constants.LinuxLdflags +
                   generated_config_constants.LinuxX86Ldflags +
                   device_compatibility_flags_non_windows +
                   device_compatibility_flags_non_darwin,
    rtti_toggle = False,
    target_arch = "x86",
    target_flags = ["--target=i686-linux-gnu"],
    target_os = "linux_glibc",
    toolchain_identifier = "x86-toolchain",
)

# Toolchain to compile for the linux host with musl libc.
android_cc_toolchain(
    name = "cc_toolchain_x86_64_linux_musl_host",
    clang_version = ":clang",
    compiler_flags = generated_config_constants.LinuxCflags +
                     generated_config_constants.LinuxMuslCflags +
                     generated_config_constants.LinuxX8664Cflags +
    # Added by stl.go for non-bionic toolchains.
    [
        "-nostdinc++",
    ],
    crt = _musl_crt,
    gcc_toolchain = generated_config_constants.LinuxGccRoot,
    libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_musl_x86_64"],
    libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_musl_x86_64"],
    linker_flags = generated_config_constants.LinuxMuslLdflags +
                   generated_config_constants.LinuxLdflags +
                   generated_config_constants.LinuxX8664Ldflags,
    rtti_toggle = False,
    target_arch = "x86_64",
    target_flags = ["--target=x86_64-linux-musl"],
    target_os = "linux_musl",
    toolchain_identifier = "x86_64-toolchain",
)

# Toolchain to compile for the linux x86 host with musl libc.
android_cc_toolchain(
    name = "cc_toolchain_x86_linux_musl_host",
    clang_version = ":clang",
    compiler_flags = generated_config_constants.LinuxCflags +
                     generated_config_constants.LinuxMuslCflags +
                     generated_config_constants.LinuxX86Cflags +
    # Added by stl.go for non-bionic toolchains.
    [
        "-nostdinc++",
    ],
    crt = _musl_crt,
    gcc_toolchain = generated_config_constants.LinuxGccRoot,
    libclang_rt_builtin = libclang_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_musl_x86"],
    libclang_rt_ubsan_minimal = libclang_ubsan_minimal_rt_prebuilt_map["//build/bazel_common_rules/platforms/os_arch:linux_musl_x86"],
    linker_flags = generated_config_constants.LinuxMuslLdflags +
                   generated_config_constants.LinuxLdflags +
                   generated_config_constants.LinuxX86Ldflags,
    rtti_toggle = False,
    target_arch = "x86",
    target_flags = ["--target=i686-linux-musl"],
    target_os = "linux_musl",
    toolchain_identifier = "x86-toolchain",
)

toolchain_type(name = "nocrt_toolchain")

# Device toolchains
[
    [
        [
            toolchain_definition(arch, variant, nocrt)
            for nocrt in [
                True,
                False,
            ]
        ]
        for variant in variants
    ]
    for arch, variants in arch_to_variants.items()
]

# Toolchains for linux host (x86_64) archs
[
    toolchain(
        name = "%s_def" % toolchain_name,
        exec_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_glibc",
        ],
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_glibc",
        ],
        toolchain = toolchain_name,
        toolchain_type = toolchain_type,
    )
    for (toolchain_name, toolchain_type) in x86_64_host_toolchains
]

# Toolchains for linux target (non-host) x86 arch
[
    toolchain(
        name = "%s_def" % toolchain_name,
        exec_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_glibc",
        ],
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86",
            "//build/bazel_common_rules/platforms/os:linux_glibc",
        ],
        toolchain = toolchain_name,
        toolchain_type = toolchain_type,
    )
    for (toolchain_name, toolchain_type) in x86_host_toolchains
]

# Toolchains for linux musl host (x86_64) archs
[
    toolchain(
        name = "%s_def" % toolchain_name,
        exec_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_musl",
        ],
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_musl",
        ],
        toolchain = toolchain_name,
        toolchain_type = toolchain_type,
    )
    for (toolchain_name, toolchain_type) in x86_64_musl_host_toolchains
]

# Toolchains for linux musl target (non-host) x86 arch
[
    toolchain(
        name = "%s_def" % toolchain_name,
        exec_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86_64",
            "//build/bazel_common_rules/platforms/os:linux_musl",
        ],
        target_compatible_with = [
            "//build/bazel_common_rules/platforms/arch:x86",
            "//build/bazel_common_rules/platforms/os:linux_musl",
        ],
        toolchain = toolchain_name,
        toolchain_type = toolchain_type,
    )
    for (toolchain_name, toolchain_type) in x86_musl_host_toolchains
]

[
    filegroup(
        name = "libclang_rt_%s_%s_%s" % (prefix, os, arch),
        srcs = [":clang"],
        output_group = "libclang_rt_%s_%s_%s" % (prefix, os, arch),
    )
    for os, arches in {
        "android": [
            "arm",
            "arm64",
            "x86",
            "x86_64",
        ],
        "linux": [
            "bionic_x86_64",
            "glibc_x86",
            "glibc_x86_64",
            "musl_x86",
            "musl_x86_64",
        ],
    }.items()
    for arch in arches
    for prefix in [
        "builtins",
        "ubsan_minimal",
    ]
]

cc_import(
    name = "libclang_rt",
    static_library = select(libclang_rt_prebuilt_map),
)

[
    filegroup(
        name = tool,
        srcs = [":clang"],
        output_group = clang_tool_output_group(tool),
        visibility = ["//visibility:public"],
    )
    for tool in CLANG_TOOLS
]

# Test tools used by Bazel tests.
filegroup(
    name = "test_tools",
    srcs = [":clang"],
    output_group = "clang_test_tools",
    visibility = ["//build/bazel/tests:__subpackages__"],
)

string_flag(
    name = "clang_version",
    build_setting_default = env.get(
        "LLVM_PREBUILTS_VERSION",
        generated_config_constants.CLANG_DEFAULT_VERSION,
    ),
)

string_flag(
    name = "clang_short_version",
    build_setting_default = env.get(
        "LLVM_RELEASE_VERSION",
        generated_config_constants.CLANG_DEFAULT_SHORT_VERSION,
    ),
)
