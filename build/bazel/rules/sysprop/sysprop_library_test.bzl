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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":sysprop_library.bzl", "SyspropGenInfo", "sysprop_library")

def _provides_src_files_test_impl(ctx):
    env = analysistest.begin(ctx)

    target_under_test = analysistest.target_under_test(env)
    asserts.equals(
        env,
        ["foo.sysprop", "bar.sysprop"],
        [src.label.name for src in target_under_test[SyspropGenInfo].srcs],
    )

    return analysistest.end(env)

provides_src_files_test = analysistest.make(
    _provides_src_files_test_impl,
)

def _test_provides_src_files():
    name = "provides_src_files"
    test_name = name + "_test"
    sysprop_library(
        name = name,
        srcs = ["foo.sysprop", "bar.sysprop"],
        tags = ["manual"],
    )
    provides_src_files_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def sysprop_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_provides_src_files(),
        ],
    )
