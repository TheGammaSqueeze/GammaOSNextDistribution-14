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

def apex_compression_test(name, apex, compressed):
    """This verifies APEX or compressed APEX file:
        1) has the correct file extension name
        2) contains the required files specified by the APEX file format
    """

    native.sh_library(
        name = name + "_wrapper_sh_lib",
        data = [apex],
    )

    args = ["$(location " + apex + ")"]
    if compressed:
        args.append("compressed")

    native.sh_test(
        name = name,
        srcs = ["apex_compression_test.sh"],
        deps = ["@bazel_tools//tools/bash/runfiles"],
        data = [
            ":" + name + "_wrapper_sh_lib",
            "@bazel_tools//tools/zip:zipper",
            apex,
        ],
        args = args,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
    )
