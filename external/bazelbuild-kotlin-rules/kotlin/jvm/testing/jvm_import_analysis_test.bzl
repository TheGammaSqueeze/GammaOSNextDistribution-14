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

"""kt_jvm_import_analysis_test"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

visibility(RULES_KOTLIN)

kt_jvm_import_analysis_test = analysistest.make(
    impl = lambda ctx: _kt_jvm_import_analysis_test_impl(ctx),
    attrs = dict(),
)

def _kt_jvm_import_analysis_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.true(
        env,
        JavaInfo in ctx.attr.target_under_test,
        "kt_jvm_import did not produce JavaInfo provider.",
    )
    asserts.true(
        env,
        ProguardSpecProvider in ctx.attr.target_under_test,
        "kt_jvm_import did not produce ProguardSpecProvider provider.",
    )
    return analysistest.end(env)
