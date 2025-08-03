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

load("@bazel_skylib//lib:selects.bzl", "selects")
load(":config_setting_boolean_algebra_test.bzl", "config_setting_boolean_algebra_test_suite")
load(":schema_validation_test.bzl", "schema_validation_test_suite")

config_setting_boolean_algebra_test_suite(
    name = "config_setting_boolean_algebra_test_suite",
)

schema_validation_test_suite(name = "schema_validation_test_suite")

# Stamp is a random flag that can only be 1 of 2 values, so it can be used to make
# the always_on/off_config_settings. This concept is copied from skylib, but skylib
# only actually made these always on/off config settings as part of the
# config_setting_group macro.
config_setting(
    name = "stamp_binary_on_check",
    values = {"stamp": "1"},
    # For some reason bazel requires this to be visible to where
    # always_on_config_setting is used.
    visibility = ["//visibility:public"],
)

config_setting(
    name = "stamp_binary_off_check",
    values = {"stamp": "0"},
    # For some reason bazel requires this to be visible to where
    # always_on_config_setting is used.
    visibility = ["//visibility:public"],
)

selects.config_setting_group(
    name = "always_on_config_setting",
    match_any = [
        ":stamp_binary_off_check",
        ":stamp_binary_on_check",
    ],
    visibility = ["//visibility:public"],
)

selects.config_setting_group(
    name = "always_off_config_setting",
    match_all = [
        ":stamp_binary_off_check",
        ":stamp_binary_on_check",
    ],
    visibility = ["//visibility:public"],
)

filegroup(name = "empty_filegroup")

sh_binary(
    name = "fail",
    srcs = ["fail.sh"],
)
