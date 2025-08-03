# Copyright (C) 2023 The Android Open Source Project
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
"""Tests for the validate() function."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest")
load(":schema_validation.scl", "validate")

def _string_comparison_test_impl(ctx):
    env = analysistest.begin(ctx)
    if ctx.attr.actual != ctx.attr.expected:
        analysistest.fail(env, "expected '%s' but got '%s'" % (ctx.attr.expected, ctx.attr.actual))
    return analysistest.end(env)

_string_comparison_raw_test = analysistest.make(
    _string_comparison_test_impl,
    attrs = {
        "actual": attr.string(),
        "expected": attr.string(),
    },
)

def _string_comparison_test(*, name, actual, expected):
    _string_comparison_raw_test(
        name = name,
        actual = actual,
        expected = expected,
        # target_under_test is required but unused
        target_under_test = "//build/bazel/utils:always_on_config_setting",
    )

def _test_string_success():
    test_name = "test_string_success"
    data = "hello, world"
    schema = {"type": "string"}
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _choices_success():
    test_name = "choices_success"
    data = "bar"
    schema = {
        "type": "string",
        "choices": [
            "foo",
            "bar",
            "baz",
        ],
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _choices_failure():
    test_name = "choices_failure"
    data = "qux"
    schema = {
        "type": "string",
        "choices": [
            "foo",
            "bar",
            "baz",
        ],
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = 'Expected one of ["foo", "bar", "baz"], got qux',
        actual = message,
    )
    return test_name

def _value_success():
    test_name = "value_success"
    data = "bar"
    schema = {
        "type": "string",
        "value": "bar",
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _value_failure():
    test_name = "value_failure"
    data = "qux"
    schema = {
        "type": "string",
        "value": "bar",
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected bar, got qux",
        actual = message,
    )
    return test_name

def _length_success():
    test_name = "length_success"
    data = {
        "a": "foo",
        "b": "foo",
        "c": "foo",
        "d": "foo",
        "e": "foo",
        "f": "foo",
    }
    schema = {
        "type": "dict",
        "required_keys": {
            "a": {
                "type": "string",
                "length": 3,
            },
            "b": {
                "type": "string",
                "length": "<4",
            },
            "c": {
                "type": "string",
                "length": "<=4",
            },
            "d": {
                "type": "string",
                "length": ">2",
            },
            "e": {
                "type": "string",
                "length": ">=2",
            },
            "f": {
                "type": "string",
                "length": "=3",
            },
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _length_failure_1():
    test_name = "length_failure_1"
    data = "qux"
    schema = {
        "type": "string",
        "length": 4,
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected length 4, got 3",
        actual = message,
    )
    return test_name

def _length_failure_2():
    test_name = "length_failure_2"
    data = "qux"
    schema = {
        "type": "string",
        "length": ">3",
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected length >3, got 3",
        actual = message,
    )
    return test_name

def _test_type_failure():
    test_name = "test_type_failure"
    data = 5
    schema = {"type": "string"}
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected string, got int",
        actual = message,
    )
    return test_name

def _test_or_success():
    test_name = "test_or_success"
    data = "hello, world"
    schema = {"or": [
        {"type": "int"},
        {"type": "string"},
    ]}
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _test_or_failure():
    test_name = "test_or_failure"
    data = 3.5
    schema = {"or": [
        {"type": "int"},
        {"type": "string"},
    ]}
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "did not match any schemas in 'or' list, errors:\n  Expected int, got float\n  Expected string, got float",
        actual = message,
    )
    return test_name

def _list_of_strings_success():
    test_name = "list_of_strings_success"
    data = ["a", "b"]
    schema = {
        "type": "list",
        "of": {"type": "string"},
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _list_of_strings_failure():
    test_name = "list_of_strings_failure"
    data = ["a", 5, "b"]
    schema = {
        "type": "list",
        "of": {"type": "string"},
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected string, got int",
        actual = message,
    )
    return test_name

def _tuple_of_strings_success():
    test_name = "tuple_of_strings_success"
    data = ("a", "b")
    schema = {
        "type": "tuple",
        "of": {"type": "string"},
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _tuple_of_strings_failure():
    test_name = "tuple_of_strings_failure"
    data = ("a", 5, "b")
    schema = {
        "type": "tuple",
        "of": {"type": "string"},
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected string, got int",
        actual = message,
    )
    return test_name

def _unique_list_of_strings_success():
    test_name = "unique_list_of_strings_success"
    data = ["a", "b"]
    schema = {
        "type": "list",
        "of": {"type": "string"},
        "unique": True,
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _unique_list_of_strings_failure():
    test_name = "unique_list_of_strings_failure"
    data = ["a", "b", "a"]
    schema = {
        "type": "list",
        "of": {"type": "string"},
        "unique": True,
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "Expected all elements to be unique, but saw 'a' twice",
        actual = message,
    )
    return test_name

def _dict_success():
    test_name = "dict_success"
    data = {
        "foo": 5,
        "bar": "baz",
        "qux": 3.5,
    }
    schema = {
        "type": "dict",
        "required_keys": {
            "foo": {"type": "int"},
            "bar": {"type": "string"},
        },
        "optional_keys": {
            "qux": {"type": "float"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _dict_missing_required_key():
    test_name = "dict_missing_required_key"
    data = {
        "foo": 5,
    }
    schema = {
        "type": "dict",
        "required_keys": {
            "foo": {"type": "int"},
            "bar": {"type": "string"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "required key 'bar' not found",
        actual = message,
    )
    return test_name

def _dict_extra_keys():
    test_name = "dict_extra_keys"
    data = {
        "foo": 5,
        "bar": "hello",
        "baz": 3.5,
    }
    schema = {
        "type": "dict",
        "required_keys": {
            "foo": {"type": "int"},
        },
        "optional_keys": {
            "bar": {"type": "string"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = 'keys ["baz"] not allowed, valid keys: ["foo", "bar"]',
        actual = message,
    )
    return test_name

def _dict_generic_keys_success():
    test_name = "dict_generic_keys_success"
    data = {
        "foo": 5,
        "bar": "hello",
    }
    schema = {
        "type": "dict",
        "keys": {"type": "string"},
        "values": {
            "or": [
                {"type": "string"},
                {"type": "int"},
            ],
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _dict_generic_keys_failure():
    test_name = "dict_generic_keys_failure"
    data = {
        "foo": 5,
        "bar": "hello",
        "baz": 3.5,
    }
    schema = {
        "type": "dict",
        "keys": {"type": "string"},
        "values": {
            "or": [
                {"type": "string"},
                {"type": "int"},
            ],
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "did not match any schemas in 'or' list, errors:\n  Expected string, got float\n  Expected int, got float",
        actual = message,
    )
    return test_name

def _struct_success():
    test_name = "struct_success"
    data = struct(
        foo = 5,
        bar = "baz",
        qux = 3.5,
    )
    schema = {
        "type": "struct",
        "required_fields": {
            "foo": {"type": "int"},
            "bar": {"type": "string"},
        },
        "optional_fields": {
            "qux": {"type": "float"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "",
        actual = message,
    )
    return test_name

def _struct_missing_required_field():
    test_name = "struct_missing_required_field"
    data = struct(
        foo = 5,
    )
    schema = {
        "type": "struct",
        "required_fields": {
            "foo": {"type": "int"},
            "bar": {"type": "string"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = "required field 'bar' not found",
        actual = message,
    )
    return test_name

def _struct_extra_fields():
    test_name = "struct_extra_fields"
    data = struct(
        foo = 5,
        bar = "baz",
        baz = 3.5,
    )
    schema = {
        "type": "struct",
        "required_fields": {
            "foo": {"type": "int"},
        },
        "optional_fields": {
            "bar": {"type": "string"},
        },
    }
    message = validate(data, schema, fail_on_error = False)
    _string_comparison_test(
        name = test_name,
        expected = 'fields ["baz"] not allowed, valid keys: ["foo", "bar"]',
        actual = message,
    )
    return test_name

def schema_validation_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_string_success(),
            _choices_success(),
            _choices_failure(),
            _value_success(),
            _value_failure(),
            _length_success(),
            _length_failure_1(),
            _length_failure_2(),
            _test_type_failure(),
            _test_or_success(),
            _test_or_failure(),
            _list_of_strings_success(),
            _list_of_strings_failure(),
            _tuple_of_strings_success(),
            _tuple_of_strings_failure(),
            _unique_list_of_strings_success(),
            _unique_list_of_strings_failure(),
            _dict_success(),
            _dict_missing_required_key(),
            _dict_extra_keys(),
            _dict_generic_keys_success(),
            _dict_generic_keys_failure(),
            _struct_success(),
            _struct_missing_required_field(),
            _struct_extra_fields(),
        ],
    )
