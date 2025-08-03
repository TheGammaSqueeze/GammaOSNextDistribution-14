# Copyright (C) 2023 The Android Open Source Project
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
load("//build/bazel/rules/test_common:rules.bzl", "expect_failure_test")
load(":droiddoc_exported_dir.bzl", "DroiddocExportedDirInfo", "droiddoc_exported_dir")

def _droiddoc_exported_dir_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[DroiddocExportedDirInfo]
    asserts.equals(env, expected = [], actual = analysistest.target_actions(env), msg = "no actions expected")
    asserts.equals(env, expected = ctx.attr.expected_dir, actual = info.dir)
    asserts.equals(env, expected = ctx.attr.expected_srcs, actual = [s.short_path for s in info.srcs])

    return analysistest.end(env)

droiddoc_exported_dir_test = analysistest.make(
    _droiddoc_exported_dir_test_impl,
    attrs = {
        "expected_dir": attr.string(),
        "expected_srcs": attr.string_list(),
    },
)

def _test_name(base_name):
    return base_name + "_test"

def _test_droiddoc_exported_dir_success(name, srcs, dir, expected_srcs, expected_dir):
    droiddoc_exported_dir(
        name = name,
        dir = dir,
        srcs = srcs,
        tags = ["manual"],
    )
    droiddoc_exported_dir_test(
        name = _test_name(name),
        target_under_test = name,
        expected_dir = expected_dir,
        expected_srcs = expected_srcs,
    )
    return _test_name(name)

def _test_droiddoc_exported_dir_failure(name, dir, srcs, expected_msg):
    droiddoc_exported_dir(
        name = name,
        dir = dir,
        srcs = srcs,
        tags = ["manual"],
    )
    expect_failure_test(
        name = _test_name(name),
        target_under_test = name,
        failure_message = expected_msg,
    )
    return _test_name(name)

def droiddoc_exported_dir_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_droiddoc_exported_dir_success(
                name = "without_dir",
                dir = None,
                srcs = [":src1.txt", "//build/bazel/rules/droiddoc:src2.txt"],
                expected_dir = "build/bazel/rules/droiddoc",
                expected_srcs = ["build/bazel/rules/droiddoc/src1.txt", "build/bazel/rules/droiddoc/src2.txt"],
            ),
            _test_droiddoc_exported_dir_success(
                name = "with_dir",
                dir = "dir",
                srcs = [":dir/src.txt", ":dir/dir2/src.txt"],
                expected_dir = "build/bazel/rules/droiddoc/dir",
                expected_srcs = ["build/bazel/rules/droiddoc/dir/src.txt", "build/bazel/rules/droiddoc/dir/dir2/src.txt"],
            ),
            _test_droiddoc_exported_dir_success(
                name = "subpackage",
                dir = None,
                srcs = ["//build/bazel/rules/droiddoc/bogus:bogus.txt"],
                expected_dir = "build/bazel/rules/droiddoc",
                expected_srcs = ["build/bazel/rules/droiddoc/bogus/bogus.txt"],
            ),
            _test_droiddoc_exported_dir_failure(
                name = "wrong_dir",
                dir = "some_dir",
                srcs = [":src.txt"],
                expected_msg = "File [build/bazel/rules/droiddoc/src.txt] is not under [build/bazel/rules/droiddoc/some_dir]",
            ),
            _test_droiddoc_exported_dir_failure(
                name = "wrong_package",
                dir = None,
                srcs = ["//build/bazel_common_rules/test_mappings:test_mappings.sh"],
                expected_msg = "File [build/bazel_common_rules/test_mappings/test_mappings.sh] is not under [build/bazel/rules/droiddoc]",
            ),
            _test_droiddoc_exported_dir_failure(
                name = "wrong_workspace",
                dir = None,
                srcs = ["@rules_proto//proto:defs.bzl"],
                expected_msg = "File [../rules_proto/proto/defs.bzl] is under a different workspace [rules_proto]",
            ),
        ],
    )
