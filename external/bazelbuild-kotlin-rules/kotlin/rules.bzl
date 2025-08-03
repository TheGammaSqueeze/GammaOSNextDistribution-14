# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""Kotlin rules."""

# go/keep-sorted start
load("//kotlin:compiler_opt.bzl", _kt_compiler_opt = "kt_compiler_opt")
load("//kotlin:compiler_plugin_export.bzl", _kt_compiler_plugin_export = "kt_compiler_plugin_export")
load("//kotlin:jvm_import.bzl", _kt_jvm_import = "kt_jvm_import")
load("//kotlin:jvm_library.bzl", _kt_jvm_library = "kt_jvm_library")
load("//kotlin:jvm_test.bzl", _kt_jvm_test = "kt_jvm_test")
# go/keep-sorted end

# go/keep-sorted start
kt_compiler_opt = _kt_compiler_opt
kt_compiler_plugin_export = _kt_compiler_plugin_export
kt_jvm_import = _kt_jvm_import
kt_jvm_library = _kt_jvm_library
kt_jvm_test = _kt_jvm_test
# go/keep-sorted end
