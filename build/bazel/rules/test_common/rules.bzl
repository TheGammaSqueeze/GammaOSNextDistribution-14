# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")

def _rule_failure_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.failure_message)
    return analysistest.end(env)

expect_failure_test = analysistest.make(
    impl = _rule_failure_impl,
    expect_failure = True,
    attrs = {
        "failure_message": attr.string(),
    },
    doc = "This test checks that a rule fails with the expected failure_message",
)

def _target_under_test_exist_impl(ctx):
    env = analysistest.begin(ctx)
    return analysistest.end(env)

target_under_test_exist_test = analysistest.make(
    impl = _target_under_test_exist_impl,
    doc = "This test checks that the target under test exists without failure",
)
