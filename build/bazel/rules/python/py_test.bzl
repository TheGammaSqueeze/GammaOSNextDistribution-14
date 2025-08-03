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

load("@rules_python//python:defs.bzl", "py_binary")
load("//build/bazel/rules/tradefed:tradefed.bzl", "TEST_DEP_SUFFIX", "tradefed_test_suite")

def py_test(
        name = "",
        deps = [],
        srcs = [],
        main = None,
        tags = [],
        test_config = None,
        template_test_config = None,
        template_configs = [],
        template_install_base = None,
        visibility = None,
        target_compatible_with = [],
        **kwargs):
    test_dep_name = name + TEST_DEP_SUFFIX

    py_binary(
        name = test_dep_name,
        deps = deps,
        srcs = srcs,
        main = main or "%s.py" % name,
        tags = tags + ["manual"],
        visibility = visibility,
        target_compatible_with = target_compatible_with,
        **kwargs
    )

    tradefed_test_suite(
        name = name,
        test_dep = test_dep_name,
        test_config = test_config,
        template_test_config = template_test_config,
        template_configs = template_configs,
        template_install_base = template_install_base,
        deviceless_test_config = "//build/make/core:python_binary_host_test_config_template.xml",
        tags = tags,
        visibility = visibility,
    )
