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
This file contains platforms for defining rust toolchains for device builds
"""

load("//build/bazel/toolchains/rust:flags.bzl", "flags")

platforms = [
    ("aarch64-linux-android", "android", "arm64", flags.device_arm64_rustc_flags),
    ("armv7-linux-androideabi", "android", "arm", flags.device_arm_rustc_flags),
    ("x86_64-linux-android", "android", "x86_64", flags.device_x86_64_rustc_flags),
    ("i686-linux-android", "android", "x86", flags.device_x86_rustc_flags),
]
