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

"""Combined aspect for all rules_kotlin behaviours that need to traverse exports."""

load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")
load("//kotlin/jvm/traverse_exports:traverse_exports.bzl", _kt_traverse_exports = "kt_traverse_exports")

visibility(RULES_DEFS_THAT_COMPILE_KOTLIN)

kt_traverse_exports = _kt_traverse_exports
