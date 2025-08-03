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
load("//build/bazel/rules:linker_config.bzl", "linker_config")
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo")

SRC = "foo.json"
OUT_EXP = "foo.pb"

def _test_linker_config_actions_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.equals(env, 1, len(actions), "expected  1 action got {}".format(actions))

    in_file = actions[0].inputs.to_list()[0]
    out_files = actions[0].outputs.to_list()
    asserts.equals(env, 1, len(out_files), "expected 1 out file  got {}".format(out_files))

    asserts.equals(
        env,
        SRC,
        in_file.basename,
        "expected source file {} got {}".format(SRC, in_file.basename),
    )
    asserts.equals(
        env,
        OUT_EXP,
        out_files[0].basename,
        "expected out file {} got {}".format(OUT_EXP, out_files[0].basename),
    )

    # gets build target we are testing for
    target_under_test = analysistest.target_under_test(env)
    prebuilt_file_info = target_under_test[PrebuiltFileInfo]
    asserts.equals(
        env,
        "linker.config.pb",
        prebuilt_file_info.filename,
        "expected PrebuiltFileInfo filename to be {} but  got {}".format("linkerconfig.pb", prebuilt_file_info.filename),
    )
    asserts.equals(
        env,
        "etc",
        prebuilt_file_info.dir,
        "expected PrebuiltFileInfo dir to be {} but  got {}".format("etc", prebuilt_file_info.dir),
    )
    asserts.equals(
        env,
        out_files[0],
        prebuilt_file_info.src,
        "expected PrebuiltFileInfo src to be {} but got {}".format(out_files[0], prebuilt_file_info.src),
    )

    return analysistest.end(env)

linker_config_actions_test = analysistest.make(_test_linker_config_actions_impl)

def _test_linker_config_actions():
    name = "linker_config_actions"
    test_name = name + "_test"

    linker_config(
        name = name,
        src = SRC,
        tags = ["manual"],
    )

    linker_config_actions_test(
        name = test_name,
        target_under_test = name,
    )
    return test_name

def _test_linker_config_commands_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    in_files = actions[0].inputs.to_list()
    asserts.true(env, len(in_files) > 0, "expected at least 1 input file got {}".format(in_files))

    args = actions[0].argv
    asserts.equals(env, 6, len(args), "expected 4 args got {}".format(args))
    asserts.equals(env, "proto", args[1])
    asserts.equals(env, "-s", args[2])
    asserts.equals(env, "-o", args[4])

    return analysistest.end(env)

linker_config_commands_test = analysistest.make(_test_linker_config_commands_impl)

def _test_linker_config_commands():
    name = "linker_config_commands"
    test_name = name + "_test"
    linker_config(
        name = name,
        src = SRC,
        tags = ["manual"],
    )

    linker_config_commands_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def linker_config_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_linker_config_actions(),
            _test_linker_config_commands(),
        ],
    )
