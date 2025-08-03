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

"""Android Lint toolchain for Kotlin."""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//bazel:stubs.bzl", "lint_actions")

visibility(RULES_KOTLIN)

_ATTRS = dict(
    _android_lint_baseline_file = attr.label(
        allow_single_file = True,
        cfg = "exec",
    ),
)

def _set_baselines():
    return {
        # `$foo` is used to set `_foo`
        "$android_lint_baseline_file": lint_actions.get_android_lint_baseline_file(native.package_name()),
    }

androidlint_toolchains = struct(
    attrs = _ATTRS,
    get_baseline = lambda ctx: getattr(ctx.file, "_android_lint_baseline_file", None),
    set_baselines = _set_baselines,
)
