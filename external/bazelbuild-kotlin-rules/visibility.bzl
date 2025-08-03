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

"""Bzl visibility lists for rules_kotlin"""

RULES_KOTLIN = [
    "//...",
]

visibility(RULES_KOTLIN)

TOOLS_KOTLIN = [
]

# bzl files in these packages have access to internal parts of rules_kotlin, so think carefully
# before expanding the list.
RULES_DEFS_THAT_COMPILE_KOTLIN = RULES_KOTLIN + [
    "public",
]
