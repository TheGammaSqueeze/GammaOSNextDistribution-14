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

load("//build/bazel/tests/products:product_labels.bzl", _products_for_testing = "products")
load("//build/bazel/rules/common/api_constants.bzl", _api_levels_released_versions = "api_levels_released_versions")
load("//build/bazel/rules/env_variables.bzl", _CAPTURED_ENV_VARS = "CAPTURED_ENV_VARS")

api_levels_released_versions = _api_levels_released_versions
captured_env_vars = _CAPTURED_ENV_VARS
products_for_testing = _products_for_testing
additional_module_names_to_packages = {
    "apex_certificate_label_with_overrides": "//build/bazel/rules/apex",
    "apex_certificate_label_with_overrides_another_cert": "//build/bazel/rules/apex",
}
