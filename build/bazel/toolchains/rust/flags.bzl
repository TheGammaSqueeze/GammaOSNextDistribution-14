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

"""
This file contains flags to be set on the toolchains
"""

load("@soong_injection//rust_toolchain:constants.bzl", "constants")

# rust flags that are applied to both host and device build
_global_rustc_flags = list(constants.GLOBAL_RUSTC_FLAGS)

# https://github.com/bazelbuild/rules_rust/blob/aa4b3a862a8200c9422b7f7e050b12c7ef2c2a61/rust/private/rustc.bzl#L959
# rules_rust already set `--color=always`
_global_rustc_flags.remove("--color=always")

# TODO: b/301466790 - Set linting flags to rustc before checking in BUILD files
_global_rustc_flags.append("--cap-lints=allow")

# host rust flags
_linux_host_rustc_flags = ["-Clink-args={}".format(" ".join(constants.LINUX_HOST_GLOBAL_LINK_FLAGS))]

# device rust flags
_device_global_rustc_flags = list(constants.DEVICE_GLOBAL_RUSTC_FLAGS)

_device_global_rustc_flags.extend(
    ["-Clink-args={}".format(" ".join(constants.DEVICE_GLOBAL_LINK_FLAGS))],
)

flags = struct(
    global_rustc_flags = _global_rustc_flags,
    # host rust flags
    linux_host_rustc_flags = _linux_host_rustc_flags,
    # device rust flags
    device_global_rustc_flags = _device_global_rustc_flags,
    device_arm64_rustc_flags = constants.DEVICE_ARM64_RUSTC_FLAGS,
    device_arm_rustc_flags = constants.DEVICE_ARM_RUSTC_FLAGS,
    device_x86_64_rustc_flags = constants.DEVICE_X86_64_RUSTC_FLAGS,
    device_x86_rustc_flags = constants.DEVICE_X86_RUSTC_FLAGS,
)
