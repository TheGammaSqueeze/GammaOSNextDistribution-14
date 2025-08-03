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

load("@bazel_skylib//rules:diff_test.bzl", "diff_test")

def partition_diff_test(
        *,
        name,
        partition1,
        partition2):
    """A test that compares the contents of two paritions."""

    native.genrule(
        name = name + "_1_genrule",
        tools = [
            "//build/bazel/rules/partitions/diff:partition_inspector",
            "//external/e2fsprogs/debugfs:debugfs",
        ],
        srcs = [partition1],
        outs = [name + "_1.txt"],
        cmd = "$(location //build/bazel/rules/partitions/diff:partition_inspector) --debugfs-path=$(location //external/e2fsprogs/debugfs:debugfs) $< > $@",
    )

    native.genrule(
        name = name + "_2_genrule",
        tools = [
            "//build/bazel/rules/partitions/diff:partition_inspector",
            "//external/e2fsprogs/debugfs:debugfs",
        ],
        srcs = [partition2],
        outs = [name + "_2.txt"],
        cmd = "$(location //build/bazel/rules/partitions/diff:partition_inspector) --debugfs-path=$(location //external/e2fsprogs/debugfs:debugfs) $< > $@",
    )

    diff_test(
        name = name,
        file1 = name + "_1.txt",
        file2 = name + "_2.txt",
    )
