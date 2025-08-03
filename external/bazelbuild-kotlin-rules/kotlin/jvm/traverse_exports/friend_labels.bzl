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

"""kt_friend_labels_visitor"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin/common:is_eligible_friend.bzl", "is_eligible_friend")

visibility(RULES_KOTLIN)

def _get_output_labels(target, _):
    return [target.label]

kt_friend_labels_visitor = struct(
    name = "friend_labels",
    visit_target = _get_output_labels,
    filter_edge = is_eligible_friend,
    finish_expansion = None,
    process_unvisited_target = None,
)
