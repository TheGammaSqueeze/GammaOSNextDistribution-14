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

"""
platform_utils.bzl defines a platform_utils rule, and several
utility functions that accept an instance of that rule and return
information about the target platform. One instance of the platform_utils
rule is defined in //build/bazel/platforms:platform_utils. All rules
that need it can depend on that target, and then call the util
functions by doing something like `is_target_linux(ctx.attr._platform_utils)`.
This works because child targets inherit their parent's configuration.
"""

_name_to_constraint = {
    "_x86_constraint": "//build/bazel_common_rules/platforms/arch:x86",
    "_x86_64_constraint": "//build/bazel_common_rules/platforms/arch:x86_64",
    "_arm_constraint": "//build/bazel_common_rules/platforms/arch:arm",
    "_arm64_constraint": "//build/bazel_common_rules/platforms/arch:arm64",
    "_secondary_x86_constraint": "//build/bazel_common_rules/platforms/arch:secondary_x86",
    "_secondary_x86_64_constraint": "//build/bazel_common_rules/platforms/arch:secondary_x86_64",
    "_secondary_arm_constraint": "//build/bazel_common_rules/platforms/arch:secondary_arm",
    "_secondary_arm64_constraint": "//build/bazel_common_rules/platforms/arch:secondary_arm64",
    "_android_constraint": "//build/bazel_common_rules/platforms/os:android",
    "_linux_constraint": "//build/bazel_common_rules/platforms/os:linux",
    "_linux_musl_constraint": "//build/bazel_common_rules/platforms/os:linux_musl",
    "_linux_bionic_constraint": "//build/bazel_common_rules/platforms/os:linux_bionic",
    "_darwin_constraint": "//build/bazel_common_rules/platforms/os:darwin",
}

_AndroidPlatformUtilsInfo = provider(
    "_AndroidPlatformUtilsInfo exports metadata about what platform the code is being run on.",
    fields = {
        "target" + name: "Whether the target platform has the constraint %s" % constraint
        for name, constraint in _name_to_constraint.items()
    },
)

def _platform_utils_impl(ctx):
    return [
        _AndroidPlatformUtilsInfo(**{
            "target" + name: ctx.target_platform_has_constraint(getattr(ctx.attr, name)[platform_common.ConstraintValueInfo])
            for name in _name_to_constraint
        }),
    ]

platform_utils = rule(
    implementation = _platform_utils_impl,
    attrs = {
        name: attr.label(
            default = Label(constraint),
            doc = "An internal reference to the constraint so it can be used in the rule implementation.",
        )
        for name, constraint in _name_to_constraint.items()
    },
)

def _get_platform_info(utils):
    if _AndroidPlatformUtilsInfo not in utils:
        fail("Provided object was not an instance of platform_utils. " +
             "You should depend on //build/bazel/platforms:platform_utils and then pass " +
             "ctx.attr._platform_utils to this function.")
    return utils[_AndroidPlatformUtilsInfo]

def _is_target_linux(utils):
    """Returns if the target platform is linux with any variation of libc."""
    platforminfo = _get_platform_info(utils)
    return (platforminfo.target_linux_constraint or
            platforminfo.target_linux_musl_constraint or
            platforminfo.target_linux_bionic_constraint)

def _is_target_android(utils):
    """Returns if the target platform is android."""
    return _get_platform_info(utils).target_android_constraint

def _is_target_darwin(utils):
    """Returns if the target platform is darwin."""
    return _get_platform_info(utils).target_darwin_constraint

def _is_target_linux_or_android(utils):
    """Returns if the target platform is linux with any variation of libc, or android."""
    return _is_target_linux(utils) or _is_target_android(utils)

def _is_target_bionic(utils):
    """Returns if the target platform uses the Bionic libc"""
    return _is_target_linux_bionic(utils) or _is_target_android(utils)

def _is_target_linux_bionic(utils):
    """Returns if the target platform runs (non-Android) Linux and uses the Bionic libc"""
    return _get_platform_info(utils).target_linux_bionic_constraint

def _get_target_bitness(utils):
    """Returns 32 or 64 depending on the bitness of the target platform."""
    platforminfo = _get_platform_info(utils)

    if platforminfo.target_x86_constraint or platforminfo.target_arm_constraint:
        return 32
    elif platforminfo.target_x86_64_constraint or platforminfo.target_arm64_constraint:
        return 64
    fail("Unable to determine target bitness")

def _get_target_arch(utils):
    """Returns 'x86', 'x86_64', 'arm', or 'arm64' depending on the target platform."""
    platforminfo = _get_platform_info(utils)

    if platforminfo.target_x86_constraint:
        return "x86"
    elif platforminfo.target_x86_64_constraint:
        return "x86_64"
    elif platforminfo.target_arm_constraint:
        return "arm"
    elif platforminfo.target_arm64_constraint:
        return "arm64"

    fail("Unable to determine target arch")

def _get_target_secondary_arch(utils):
    """
    Returns 'x86', 'x86_64', 'arm', 'arm64', or '' depending on the target platform.

    If the secondary arch is the same as the primary arch, an empty string will be returned.
    This is supposed to indicate that no secondary arch exists. The main motivation for this
    behavior is in soong.variables, DeviceSecondaryArch and related variables are empty
    strings when they don't exist, so a lot of code revolves around that. However in bazel
    a constraint setting must always have a value, and a "none" value would probably
    introduce more problems, so instead the secondary arch copies the primary arch if it
    doesn't exist.
    """
    platforminfo = _get_platform_info(utils)

    result = ""
    if platforminfo.target_secondary_x86_constraint:
        result = "x86"
    elif platforminfo.target_secondary_x86_64_constraint:
        result = "x86_64"
    elif platforminfo.target_secondary_arm_constraint:
        result = "arm"
    elif platforminfo.target_secondary_arm64_constraint:
        result = "arm64"
    else:
        fail("Unable to determine target secondary arch")

    if _get_target_arch(utils) == result:
        return ""
    return result

platforms = struct(
    is_target_linux = _is_target_linux,
    is_target_android = _is_target_android,
    is_target_bionic = _is_target_bionic,
    is_target_darwin = _is_target_darwin,
    is_target_linux_or_android = _is_target_linux_or_android,
    get_target_bitness = _get_target_bitness,
    get_target_arch = _get_target_arch,
    get_target_secondary_arch = _get_target_secondary_arch,
)
