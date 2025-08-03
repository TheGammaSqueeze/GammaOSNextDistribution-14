# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@soong_injection//java_toolchain:constants.bzl", "constants")
load(":errorprone_flags.bzl", "errorprone_soong_bazel_diffs")

def _process_exported_list(lst):
    # empty lists get exported from Soong to Bazel as an empty string ""
    # this is a work around to prevent type errors
    if type(lst) == type("str"):
        return []
    return lst

def _soong_default_errorprone_checks():
    res = []
    res += _process_exported_list(constants.ErrorProneChecksError)
    res += _process_exported_list(constants.ErrorProneChecksWarning)
    res += _process_exported_list(constants.ErrorProneChecksDefaultDisabled)
    res += _process_exported_list(constants.ErrorProneChecksOff)
    return res

errorprone_global_flags = errorprone_soong_bazel_diffs + _soong_default_errorprone_checks()
