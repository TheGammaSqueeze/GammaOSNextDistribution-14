#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import unittest
import gdb
import json


def remove_gdb_output_prefix(str):
    """ gdb output contains a "$%d =" prefix, the function remove the prefix
    """
    idx = str.find("=")
    return str[idx + 1:]


def check_type_and_ctype(input_json, input_type, ctype):
    assert(input_json["type"] == input_type)
    assert(input_json["ctype"] == ctype)


def check_basic_value(input_json, input_type, ctype, value):
    check_type_and_ctype(input_json, input_type, ctype)
    assert(input_json["value"] == value)


def gdb_extract_variable_to_json(variable_name):
    """ extract a variable from gdb

    Args:
        variable_name:
            a string, the name of the variable to extract
    Returns:
        A json Object in Python
    """
    variable = gdb.execute("p {}".format(variable_name), to_string=True)
    variable = remove_gdb_output_prefix(variable)
    variable_json = json.loads(variable)
    return variable_json


def json_equal_except_address(testcase, json0, json1):
    """ check if all content of two json are same except the address fields

    The function will check the two json objects recursively to set address fields to None,
    then convert two json objects into strings with sorted keys, and check if the two strings
    are same.
    """
    def replace_address_by_None(input_json):
        if isinstance(input_json, dict):
            for (k, v) in input_json.items():
                if k == "address":
                    input_json[k] = None
                else:
                    replace_address_by_None(v)
        elif isinstance(input_json, list):
            for item in input_json:
                replace_address_by_None(item)

    replace_address_by_None(json0)
    replace_address_by_None(json1)
    json0 = json.dumps(json0, sort_keys=True)
    json1 = json.dumps(json1, sort_keys=True)
    testcase.assertEqual(json0, json1)


def test_json_variable(testcase, testname, line, variable_name_list):
    for variable_name in variable_name_list:
        variable_json = gdb_extract_variable_to_json(variable_name)
        with open("test/{}_{}_{}.json".format(testname, str(line), variable_name), "r") as f:
            expect_variable = json.load(f)
        json_equal_except_address(testcase, variable_json, expect_variable)


class TestGdbJsonPrinter(unittest.TestCase):

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.maxDiff = None

    def test_basic_types(self):
        gdb.execute("file test_examples/basic_types")
        gdb.execute("b 42")
        gdb.execute("r")

        variable_json = gdb_extract_variable_to_json("a")
        check_basic_value(variable_json, "int", "uint8_t", "1")

        variable_json = gdb_extract_variable_to_json("a")
        check_basic_value(variable_json, "int", "uint8_t", "1")

        variable_json = gdb_extract_variable_to_json("b")
        check_basic_value(variable_json, "int", "int8_t", "-2")

        variable_json = gdb_extract_variable_to_json("c")
        check_basic_value(variable_json, "int", "uint16_t", "3")

        variable_json = gdb_extract_variable_to_json("d")
        check_basic_value(variable_json, "int", "int16_t", "-4")

        variable_json = gdb_extract_variable_to_json("e")
        check_basic_value(variable_json, "int", "uint32_t", "5")

        variable_json = gdb_extract_variable_to_json("f")
        check_basic_value(variable_json, "int", "int32_t", "-6")

        variable_json = gdb_extract_variable_to_json("g")
        check_basic_value(variable_json, "int", "uint64_t", "7")

        variable_json = gdb_extract_variable_to_json("h")
        check_basic_value(variable_json, "int", "int64_t", "-8")

        variable_json = gdb_extract_variable_to_json("i")
        check_basic_value(variable_json, "float", "float", "9.0")

        variable_json = gdb_extract_variable_to_json("j")
        check_basic_value(variable_json, "float", "double", "-10.0")

        variable_json = gdb_extract_variable_to_json("k")
        check_type_and_ctype(variable_json, "pointer", "const char *")
        check_basic_value(
            variable_json["reference"], "int", "const char", "72")

        variable_json = gdb_extract_variable_to_json("l")
        check_type_and_ctype(variable_json, "pointer", "void *")
        assert(variable_json["reference"] == "cannot extract void ptr")

        variable_json = gdb_extract_variable_to_json("m")
        check_type_and_ctype(variable_json, "pointer", "char *")
        assert(variable_json["reference"] == "nullptr")

        variable_json = gdb_extract_variable_to_json("o")
        check_basic_value(variable_json, "enum", "Animal", "0")

        gdb.execute("b 61")
        gdb.execute("c")

        variable_json = gdb_extract_variable_to_json("a")
        check_basic_value(variable_json, "int", "const uint8_t", "1")

        variable_json = gdb_extract_variable_to_json("a")
        check_basic_value(variable_json, "int", "const uint8_t", "1")

        variable_json = gdb_extract_variable_to_json("b")
        check_basic_value(variable_json, "int", "const int8_t", "-2")

        variable_json = gdb_extract_variable_to_json("c")
        check_basic_value(variable_json, "int", "const uint16_t", "3")

        variable_json = gdb_extract_variable_to_json("d")
        check_basic_value(variable_json, "int", "const int16_t", "-4")

        variable_json = gdb_extract_variable_to_json("e")
        check_basic_value(variable_json, "int", "const uint32_t", "5")

        variable_json = gdb_extract_variable_to_json("f")
        check_basic_value(variable_json, "int", "const int32_t", "-6")

        variable_json = gdb_extract_variable_to_json("g")
        check_basic_value(variable_json, "int", "const uint64_t", "7")

        variable_json = gdb_extract_variable_to_json("h")
        check_basic_value(variable_json, "int", "const int64_t", "-8")

        variable_json = gdb_extract_variable_to_json("i")
        check_basic_value(variable_json, "float", "const float", "9.0")

        variable_json = gdb_extract_variable_to_json("j")
        check_basic_value(variable_json, "float", "const double", "-10.0")

        variable_json = gdb_extract_variable_to_json("k")
        check_type_and_ctype(variable_json, "pointer", "const char *")
        check_basic_value(
            variable_json["reference"], "int", "const char", "72")

        variable_json = gdb_extract_variable_to_json("l")
        check_type_and_ctype(variable_json, "pointer", "const void *")
        assert(variable_json["reference"] == "cannot extract void ptr")

        variable_json = gdb_extract_variable_to_json("m")
        check_type_and_ctype(variable_json, "pointer", "const char *")
        assert(variable_json["reference"] == "nullptr")

        variable_json = gdb_extract_variable_to_json("o")
        check_basic_value(variable_json, "enum", "const Animal", "1")

    def test_objects(self):
        gdb.execute("file test_examples/objects")
        gdb.execute("b 37")
        gdb.execute("r")
        test_json_variable(self, "objects", 37, ["bar", "foo"])

    def test_objects1(self):
        gdb.execute("file test_examples/objects1")
        gdb.execute("b 41")
        gdb.execute("r")
        test_json_variable(self, "objects1", 41, ["bar", "foo"])

    def test_objects2(self):
        gdb.execute("file test_examples/objects2")
        gdb.execute("b 85")
        gdb.execute("r")
        test_json_variable(
            self,
            "objects2",
            85,
            ["a", "b", "c", "d", "e", "f", "w", "x", "y", "z"])

    def test_array(self):
        gdb.execute("file test_examples/array")
        gdb.execute("b 42")
        gdb.execute("r")
        test_json_variable(self, "array", 42, ["a", "b", "bar", "foo"])

    def test_reference(self):
        gdb.execute("file test_examples/reference")
        gdb.execute("b 41")
        gdb.execute("r")
        test_json_variable(
            self,
            "reference",
            41,
            ["bar", "foo", "bar_ref", "foo_ref", "bar_ptr", "foo_ptr"])


unittest.main()
