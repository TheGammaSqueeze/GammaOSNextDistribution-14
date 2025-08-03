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
load("@bazel_skylib//rules:diff_test.bzl", "diff_test")

def apex_package_name_test(name, apex, expected_package_name):
    """Compare the actual package name of an apex using aapt2."""
    native.genrule(
        name = name + "_actual_package_name",
        tools = ["//frameworks/base/tools/aapt2"],
        srcs = [apex],
        outs = [name + "_actual_package_name.txt"],
        cmd = "$(location //frameworks/base/tools/aapt2) dump packagename $< > $@",
        tags = ["manual"],
    )

    native.genrule(
        name = name + "_expected_package_name",
        outs = [name + "expected_package_name.txt"],
        cmd = "echo " + expected_package_name + " > $@",
        tags = ["manual"],
    )

    diff_test(
        name = name,
        file1 = name + "_actual_package_name",
        file2 = name + "_expected_package_name",
    )
