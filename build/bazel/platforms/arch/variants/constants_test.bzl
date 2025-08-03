# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":constants.bzl", "power_set")

def _power_set_test(ctx):
    env = unittest.begin(ctx)

    actual = power_set(ctx.attr.items, include_empty = ctx.attr.include_empty)
    expected = json.decode(ctx.attr.expected_value_json)

    asserts.equals(env, expected, actual, "expected power_set({items}) to be {expected}, got {actual}".format(
        items = ctx.attr.items,
        expected = expected,
        actual = actual,
    ))

    return unittest.end(env)

power_set_test = unittest.make(
    _power_set_test,
    attrs = {
        "items": attr.string_list(doc = "Input to the power set function"),
        "include_empty": attr.bool(doc = "The include_empty argument to the power set function", default = True),
        "expected_value_json": attr.string(doc = "Expected output as a json-encoded string because attributes can't be a list of lists of strings"),
    },
)

def _power_set_tests():
    power_set_test(
        name = "power_set_test_0",
        items = ["a", "b", "c"],
        include_empty = True,
        expected_value_json = json.encode([[], ["a"], ["b"], ["a", "b"], ["c"], ["a", "c"], ["b", "c"], ["a", "b", "c"]]),
    )
    power_set_test(
        name = "power_set_test_1",
        items = ["a", "b", "c"],
        include_empty = False,
        expected_value_json = json.encode([["a"], ["b"], ["a", "b"], ["c"], ["a", "c"], ["b", "c"], ["a", "b", "c"]]),
    )
    power_set_test(
        name = "power_set_test_2",
        items = [],
        include_empty = True,
        expected_value_json = json.encode([[]]),
    )
    power_set_test(
        name = "power_set_test_3",
        items = [],
        include_empty = False,
        expected_value_json = json.encode([]),
    )
    power_set_test(
        name = "power_set_test_4",
        items = ["a"],
        include_empty = True,
        expected_value_json = json.encode([[], ["a"]]),
    )
    power_set_test(
        name = "power_set_test_5",
        items = ["a"],
        include_empty = False,
        expected_value_json = json.encode([["a"]]),
    )

    return ["power_set_test_" + str(i) for i in range(6)]

def power_set_test_suite(name):
    native.test_suite(
        name = name,
        tests = _power_set_tests(),
    )
