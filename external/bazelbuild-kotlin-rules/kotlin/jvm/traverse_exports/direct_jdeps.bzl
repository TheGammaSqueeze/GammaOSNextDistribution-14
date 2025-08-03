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

"""kt_traverse_exports visitor for exposing jdeps files from direct deps."""

load("//:visibility.bzl", "RULES_KOTLIN")

visibility(RULES_KOTLIN)

def _get_jdeps(target, _ctx_rule):
    return [
        out.compile_jdeps
        for out in target[JavaInfo].java_outputs
        if out.compile_jdeps
    ]

kt_direct_jdeps_visitor = struct(
    name = "direct_jdeps",
    visit_target = _get_jdeps,
    filter_edge = None,
    process_unvisited_target = None,
    finish_expansion = None,
)
