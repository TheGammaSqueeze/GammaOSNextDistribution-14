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

"""kt_traverse_exports visitor for identifying forbidden deps of Kotlin rules.

Currently this system recognizes:
  - nano protos
  - targets in forbidden packages
  - targets exporting other forbidden targets
"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//bazel:stubs.bzl", "is_exempt_dep", "is_forbidden_dep")

visibility(RULES_KOTLIN)

def _error(target, msg):
    return (str(target.label), msg)

def _check_forbidden(target, ctx_rule):
    if is_exempt_dep(target):
        return []

    if is_forbidden_dep(target):
        return [_error(target, "Forbidden package")]

    # Identify nano protos using tag (b/122083175)
    for tag in ctx_rule.attr.tags:
        if "nano_proto_library" == tag:
            return [_error(target, "nano_proto_library")]

    return []

def _if_not_checked(target):
    return [] if is_exempt_dep(target) else [_error(target, "Not checked")]

def _validate_deps(error_set):
    if not error_set:
        return

    error_lines = [
        "  " + name + " : " + msg
        for (name, msg) in error_set.to_list()
    ]
    fail("Forbidden deps, see go/kotlin/build-rules#restrictions:\n" + "\n".join(error_lines))

kt_forbidden_deps_visitor = struct(
    name = "forbidden_deps",
    visit_target = _check_forbidden,
    filter_edge = None,
    process_unvisited_target = _if_not_checked,
    finish_expansion = _validate_deps,
)
