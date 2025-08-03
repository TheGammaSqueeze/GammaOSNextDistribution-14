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
Utilities for getting Bazel generated paths from tests
"""

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest")
load("@rules_testing//lib:util.bzl", "TestingAspectInfo")

def get_package_dir_based_path(env, path):
    """
    Returns the given path prefixed with the full package directory path
    """

    return paths.join(env.ctx.label.package, path)

def get_output_and_package_dir_based_path(env, path):
    """
    Returns the given path prefixed with the full output and package directories
    """

    return paths.join(analysistest.target_bin_dir_path(env), analysistest.target_under_test(env).label.package, path)

def get_output_and_package_dir_based_path_rt(target, path):
    """
    Same as get_output_and_package_dir_based_path but for use with rules_testing
    """

    return paths.join(
        target[TestingAspectInfo].bin_path,
        target.label.package,
        path,
    )
