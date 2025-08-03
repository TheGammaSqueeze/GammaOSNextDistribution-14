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
load("@bazel_skylib//lib:unittest.bzl", "analysistest")
load(":config_setting_boolean_algebra.bzl", "config_setting_boolean_algebra")

_always_on_config_setting = "//build/bazel/utils:always_on_config_setting"
_always_off_config_setting = "//build/bazel/utils:always_off_config_setting"

def _fail_with_message_test(ctx):
    env = analysistest.begin(ctx)
    if ctx.attr.message:
        analysistest.fail(env, ctx.attr.message)
    return analysistest.end(env)

fail_with_message_test = analysistest.make(
    _fail_with_message_test,
    attrs = {
        "message": attr.string(),
    },
)

def config_setting_test(*, name, config_setting, expected_value):
    if type(expected_value) != "bool":
        fail("Type of expected_value must be a bool")
    if expected_value:
        message = select({
            config_setting: "",
            "//conditions:default": "Expected %s to be on but was off" % config_setting,
        })
    else:
        message = select({
            config_setting: "Expected %s to be off but was on" % config_setting,
            "//conditions:default": "",
        })

    fail_with_message_test(
        name = name,
        # target_under_test is required but unused
        target_under_test = _always_on_config_setting,
        message = message,
    )

def _test_always_on():
    test_name = "test_always_on"
    config_setting_test(
        name = test_name,
        config_setting = _always_on_config_setting,
        expected_value = True,
    )
    return test_name

def _test_always_off():
    test_name = "test_always_off"
    config_setting_test(
        name = test_name,
        config_setting = _always_off_config_setting,
        expected_value = False,
    )
    return test_name

def _test_config_setting_boolean_algebra():
    on = _always_on_config_setting
    off = _always_off_config_setting
    tests = [
        struct(
            name = "not_on",
            expected = False,
            expr = {"NOT": on},
        ),
        struct(
            name = "not_off",
            expected = True,
            expr = {"NOT": off},
        ),
        struct(
            name = "and_on_on",
            expected = True,
            expr = {"AND": [on, on]},
        ),
        struct(
            name = "and_on_off",
            expected = False,
            expr = {"AND": [on, off]},
        ),
        struct(
            name = "and_off_off",
            expected = False,
            expr = {"AND": [off, off]},
        ),
        struct(
            name = "and_empty",
            expected = True,
            expr = {"AND": []},
        ),
        struct(
            name = "or_on_on",
            expected = True,
            expr = {"OR": [on, on]},
        ),
        struct(
            name = "or_on_off",
            expected = True,
            expr = {"OR": [on, off]},
        ),
        struct(
            name = "or_off_off",
            expected = False,
            expr = {"OR": [off, off]},
        ),
        struct(
            name = "or_empty",
            expected = False,
            expr = {"OR": []},
        ),
        struct(
            name = "complicated_1",
            expected = True,
            expr = {"AND": [
                on,
                {"NOT": off},
                {"OR": [on, on, off]},
            ]},
        ),
        struct(
            name = "complicated_2",
            expected = False,
            expr = {"NOT": {"AND": [
                on,
                {"NOT": off},
                {"OR": [on, on, off]},
            ]}},
        ),
    ]

    test_name = "test_config_setting_boolean_algebra"

    for test in tests:
        config_setting_boolean_algebra(
            name = test_name + "_config_setting_" + test.name,
            expr = test.expr,
        )

        config_setting_test(
            name = test_name + "_" + test.name,
            config_setting = ":" + test_name + "_config_setting_" + test.name,
            expected_value = test.expected,
        )

    native.test_suite(
        name = test_name,
        tests = [
            test_name + "_" + test.name
            for test in tests
        ],
    )
    return test_name

def config_setting_boolean_algebra_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_always_on(),
            _test_always_off(),
            _test_config_setting_boolean_algebra(),
        ],
    )
