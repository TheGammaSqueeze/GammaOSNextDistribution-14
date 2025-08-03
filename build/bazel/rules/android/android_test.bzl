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

"""android_test macro for building and running Android device tests with Bazel."""

load("//build/bazel/rules/android:android_binary.bzl", "android_binary")
load(
    "//build/bazel/rules/tradefed:tradefed.bzl",
    "FILTER_GENERATOR_SUFFIX",
    "LANGUAGE_ANDROID",
    "TEST_DEP_SUFFIX",
    "java_test_filter_generator",
    "tradefed_test_suite",
)

def android_test(
        name,
        srcs,
        tags = [],
        optimize = False,
        visibility = ["//visibility:private"],
        **kwargs):
    """android_test macro for building and running Android device tests with Bazel.

    Args:
      name: The name of this target.
      srcs: The list of source files that are processed to create the target.
      tags: Tags for the test binary target and test suite target.
      optimize: Boolean, whether optimize the build process.
        android_test disables optimize by default.
      visibility: Bazel visibility declarations for this target.
      **kwargs: map, additional args to pass to android_binary.
    """
    test_dep_name = name + TEST_DEP_SUFFIX
    android_binary(
        name = test_dep_name,
        srcs = srcs,
        optimize = optimize,
        tags = tags,
        visibility = ["//visibility:private"],
        testonly = True,
        **kwargs
    )

    test_filter_generator_name = name + FILTER_GENERATOR_SUFFIX
    java_test_filter_generator(
        name = test_filter_generator_name,
        srcs = srcs,
        module_name = name,
    )

    tradefed_test_suite(
        name = name,
        test_dep = test_dep_name,
        test_config = None,
        template_test_config = None,
        template_configs = None,
        template_install_base = None,
        device_driven_test_config = "//build/make/core:instrumentation_test_config_template.xml",
        runs_on = ["device"],
        test_filter_generator = test_filter_generator_name,
        tags = tags,
        visibility = visibility,
        test_language = LANGUAGE_ANDROID,
    )
