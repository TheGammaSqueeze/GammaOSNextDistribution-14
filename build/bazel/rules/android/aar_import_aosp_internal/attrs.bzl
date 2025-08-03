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

load(
    "@rules_android//rules:attrs.bzl",
    _attrs = "attrs",
)
load(
    "@rules_android//rules/aar_import:attrs.bzl",
    _BASE_ATTRS = "ATTRS",
)

ATTRS = _attrs.replace(
    _BASE_ATTRS,
    exports = attr.label_list(
        allow_files = False,
        allow_rules = [
            "aar_import",
            "java_import",
            "kt_jvm_import",
            "aar_import_sdk_transition",
            "java_import_sdk_transition",
            "kt_jvm_import_sdk_transition",
        ],
        doc = "The closure of all rules reached via `exports` attributes are considered " +
              "direct dependencies of any rule that directly depends on the target with " +
              "`exports`. The `exports` are not direct deps of the rule they belong to.",
    ),
)
