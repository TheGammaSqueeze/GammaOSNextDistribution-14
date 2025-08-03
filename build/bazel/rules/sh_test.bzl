# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""sh_test macro for building shell tests with Bazel."""

load(
    "//build/bazel/rules/tradefed:tradefed.bzl",
    "LANGUAGE_SHELL",
    "TEST_DEP_SUFFIX",
    "tradefed_test_suite",
)

def sh_test(
        name,
        srcs,
        data = [],
        data_bins = [],
        tags = [],
        test_config = None,
        template_test_config = None,
        template_install_base = None,
        template_configs = [],
        visibility = None,
        runs_on = [],
        suffix = "",
        **kwargs):
    "Bazel macro to correspond with the sh_test Soong module."

    test_dep_name = name + TEST_DEP_SUFFIX
    native.sh_test(
        name = test_dep_name,
        srcs = srcs,
        data = data,
        tags = tags + ["manual"],
        **kwargs
    )

    tradefed_test_suite(
        name = name,
        test_dep = test_dep_name,
        # TODO(b/296964806): Handle auto_generate_test_config in tradefed Bazel rules.
        data_bins = data_bins,
        test_config = test_config,
        template_configs = template_configs,
        template_test_config = template_test_config,
        template_install_base = template_install_base,
        deviceless_test_config = "//build/make/core:shell_test_config_template.xml",
        device_driven_test_config = "//build/make/core:shell_test_config_template.xml",
        host_driven_device_test_config = "//build/make/core:shell_test_config_template.xml",
        runs_on = runs_on,
        tags = tags,
        suffix = suffix,
        test_language = LANGUAGE_SHELL,
        visibility = visibility,
    )
