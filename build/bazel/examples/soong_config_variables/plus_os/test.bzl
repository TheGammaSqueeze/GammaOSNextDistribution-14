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

load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_absent_for_mnemonic_aosp_arm64_host_test",
    "action_flags_absent_for_mnemonic_aosp_arm64_test",
    "action_flags_present_only_for_mnemonic_aosp_arm64_host_test",
    "action_flags_present_only_for_mnemonic_aosp_arm64_test",
)

def _test_device_present():
    test_name = "test_device_present"

    action_flags_present_only_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = ":build.bazel.examples.soong_config_variables.plus_os__internal_root_cpp",
        mnemonics = ["CppCompile"],
        expected_flags = [
            "-DDEFAULT",
            "-DDEFAULT_PLUS_ANDROID",
            "-DBOOL_VAR_DEFAULT",
            "-DBOOL_VAR_DEFAULT_PLUS_ANDROID",
        ],
    )

    return test_name

def _test_device_absent():
    test_name = "test_device_absent"

    action_flags_absent_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = ":build.bazel.examples.soong_config_variables.plus_os__internal_root_cpp",
        mnemonics = ["CppCompile"],
        expected_absent_flags = [
            "-DDEFAULT_PLUS_HOST",
            "-DBOOL_VAR_DEFAULT_PLUS_HOST",
        ],
    )

    return test_name

def _test_host_present():
    test_name = "test_host_present"

    action_flags_present_only_for_mnemonic_aosp_arm64_host_test(
        name = test_name,
        target_under_test = ":build.bazel.examples.soong_config_variables.plus_os__internal_root_cpp",
        mnemonics = ["CppCompile"],
        expected_flags = [
            "-DDEFAULT",
            "-DDEFAULT_PLUS_HOST",
            "-DBOOL_VAR_DEFAULT",
            "-DBOOL_VAR_DEFAULT_PLUS_HOST",
        ],
    )

    return test_name

def _test_host_absent():
    test_name = "test_host_absent"

    action_flags_absent_for_mnemonic_aosp_arm64_host_test(
        name = test_name,
        target_under_test = ":build.bazel.examples.soong_config_variables.plus_os__internal_root_cpp",
        mnemonics = ["CppCompile"],
        expected_absent_flags = [
            "-DDEFAULT_PLUS_ANDROID",
            "-DBOOL_VAR_DEFAULT_PLUS_ANDROID",
        ],
    )

    return test_name

def soong_config_variables_plus_os_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_device_present(),
            _test_device_absent(),
            _test_host_present(),
            _test_host_absent(),
        ],
    )
