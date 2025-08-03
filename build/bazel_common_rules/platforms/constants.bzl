"""Constants related to Bazel platforms."""

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
