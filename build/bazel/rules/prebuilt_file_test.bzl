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
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo", "prebuilt_file")

def _prebuilt_file_with_filename_from_src_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)

    actual_prebuilt_file_info = target[PrebuiltFileInfo]

    # We can't stub a source file object for testing so we scope it out
    actual_prebuilt_file_info_without_src = PrebuiltFileInfo(
        dir = actual_prebuilt_file_info.dir,
        filename = actual_prebuilt_file_info.filename,
        installable = actual_prebuilt_file_info.installable,
    )
    expected_prebuilt_file_info_without_src = PrebuiltFileInfo(
        dir = "etc/policy",
        filename = "file.policy",
        installable = True,
    )

    # Check PrebuiltFileInfo provider, excluding src
    asserts.equals(
        env,
        actual_prebuilt_file_info_without_src,
        expected_prebuilt_file_info_without_src,
        "PrebuiltFileInfo needs to match with expected result",
    )

    # Check PrebuiltFileInfo src separately
    asserts.equals(
        env,
        actual_prebuilt_file_info.src.path,
        target.label.package + "/dir/file.policy",
        "PrebuiltFileInfo src needs to match with what is given to prebuilt_file rule",
    )

    return analysistest.end(env)

prebuilt_file_with_filename_from_src_test = analysistest.make(
    _prebuilt_file_with_filename_from_src_test_impl,
)

def _prebuilt_file_with_filename_from_src_test():
    name = "prebuilt_file_with_filename_from_src"
    test_name = name + "_test"
    prebuilt_file(
        name = name,
        dir = "etc/policy",
        filename_from_src = True,
        src = "dir/file.policy",
        tags = ["manual"],
    )
    prebuilt_file_with_filename_from_src_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def prebuilt_file_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _prebuilt_file_with_filename_from_src_test(),
        ],
    )
