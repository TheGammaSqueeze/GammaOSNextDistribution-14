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

"""unittests"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("//kotlin/common/testing:unittest_suites.bzl", "kt_unittest_suites")
load("//kotlin/jvm/util:file_factory.bzl", "FileFactory")
load("@bazel_skylib//lib:unittest.bzl", "asserts")

visibility(RULES_KOTLIN)

unittests = kt_unittest_suites.create()

def _base_from_file(ctx, env):
    base_file = ctx.actions.declare_file("file/base.txt")
    factory = FileFactory(ctx, base_file)

    _assert_path_equals(ctx, env, "/file/base", factory.base_as_path)

    return [base_file]

unittests.expect_finish(_base_from_file)

def _declare(ctx, env):
    factory = FileFactory(ctx, "string/base")

    _assert_path_equals(ctx, env, "/string/base", factory.base_as_path)

    a_file = factory.declare_file("a.txt")
    _assert_path_equals(ctx, env, "/string/basea.txt", a_file.path)

    b_dir = factory.declare_directory("b_dir")
    _assert_path_equals(ctx, env, "/string/baseb_dir", b_dir.path)

    return [a_file, b_dir]

unittests.expect_finish(_declare)

def _derive(ctx, env):
    factory = FileFactory(ctx, "")

    # Once
    factory_once = factory.derive("once")
    _assert_path_equals(ctx, env, "/once", factory_once.base_as_path)

    # Twice
    factory_twice = factory_once.derive("/twice")
    _assert_path_equals(ctx, env, "/once/twice", factory_twice.base_as_path)

unittests.expect_finish(_derive)

def _base_file_without_extension(ctx):
    base_file = ctx.actions.declare_file(ctx.label.name + "/BUILD")
    FileFactory(ctx, base_file)

unittests.expect_fail(_base_file_without_extension, "file must have an extension")

def _base_file_from_different_pkg(ctx):
    mock_file = struct(owner = struct(package = ctx.label.package + "/sub"), extension = "txt")
    FileFactory(ctx, mock_file)

unittests.expect_fail(_base_file_from_different_pkg, "file must be from ctx package")

def _assert_path_equals(ctx, env, expected, actual):
    pkg_path = ctx.bin_dir.path + "/" + ctx.label.package

    asserts.equals(
        env,
        pkg_path + expected,
        actual,
    )

_test, _fail = unittests.close()  # @unused
