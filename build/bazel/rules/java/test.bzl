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

"""java_test macro for building and running Java tests with Bazel."""

load("@rules_java//java:defs.bzl", "java_binary")
load(
    "//build/bazel/rules/tradefed:tradefed.bzl",
    "FILTER_GENERATOR_SUFFIX",
    "LANGUAGE_JAVA",
    "TEST_DEP_SUFFIX",
    "java_test_filter_generator",
    "tradefed_test_suite",
)

HOST_TEST_TEMPLATE = "//build/make/core:java_host_unit_test_config_template.xml"

def java_test(
        name = "",
        srcs = [],
        deps = [],
        tags = [],
        visibility = None,
        target_compatible_with = [],
        **kwargs):
    """java_test macro for building and running Java tests with Bazel.

    Args:
      name: The name of this target.
      srcs: The list of source files that are processed to create the target.
      deps: The list of other libraries to be linked in to the target.
      tags: Tags for the test binary target and test suite target.
      visibility: Bazel visibility declarations for this target.
      target_compatible_with: A list of constraint_values that must be present
        in the target platform for this target to be considered compatible.
      **kwargs: map, additional args to pass to android_binary.
    """
    test_dep_name = name + TEST_DEP_SUFFIX
    java_binary_name = name + "_jb"

    # tradefed_test_suite uses the _deploy.jar from this java_binary to execute tests.
    java_binary(
        name = java_binary_name,
        srcs = srcs,
        deps = deps,
        create_executable = False,
        tags = tags + ["manual"],
        visibility = visibility,
        target_compatible_with = target_compatible_with,
        **kwargs
    )

    native.filegroup(
        name = test_dep_name,
        srcs = [java_binary_name + "_deploy.jar"],
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
        test_filter_generator = test_filter_generator_name,
        tags = tags,
        test_language = LANGUAGE_JAVA,
        visibility = visibility,
        deviceless_test_config = HOST_TEST_TEMPLATE,
    )
