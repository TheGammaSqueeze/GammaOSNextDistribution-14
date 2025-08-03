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
"""
This module provides a function for validating starlark data against a schema.
See validate() for more information.
"""

_schema_schema = {
    "type": "dict",
    "optional_keys": {
        "or": {
            "type": "list",
            "length": ">=2",
        },
        "noneable": {"type": "bool"},
        "type": {
            "type": "string",
            "choices": [
                "NoneType",
                "bool",
                "int",
                "float",
                "string",
                "bytes",
                "list",
                "tuple",
                "dict",
                "struct",
            ],
        },
        "choices": {
            "type": "list",
            "of": {
                "or": [
                    {"type": "string"},
                    {"type": "int"},
                    {"type": "float"},
                ],
            },
        },
        "value": {
            "or": [
                {"type": "string"},
                {"type": "int"},
                {"type": "float"},
            ],
        },
        "of": {},  # to be filled in later
        "unique": {"type": "bool"},
        "length": {"or": [
            {"type": "string"},
            {"type": "int"},
        ]},
        "required_keys": {
            "type": "dict",
            "values": {},  # to be filled in later
        },
        "optional_keys": {
            "type": "dict",
            "values": {},  # to be filled in later
        },
        "keys": {},  # to be filled in later
        "values": {},  # to be filled in later
        "required_fields": {
            "type": "dict",
            "keys": {"type": "string"},
            "values": {},  # to be filled in later
        },
        "optional_fields": {
            "type": "dict",
            "keys": {"type": "string"},
            "values": {},  # to be filled in later
        },
    },
}

_schema_schema["optional_keys"]["of"] = _schema_schema
_schema_schema["optional_keys"]["required_keys"]["values"] = _schema_schema
_schema_schema["optional_keys"]["optional_keys"]["values"] = _schema_schema
_schema_schema["optional_keys"]["keys"] = _schema_schema
_schema_schema["optional_keys"]["values"] = _schema_schema
_schema_schema["optional_keys"]["required_fields"]["values"] = _schema_schema
_schema_schema["optional_keys"]["optional_fields"]["values"] = _schema_schema

def _check_len(obj, length):
    if type(length) == "int":
        return len(obj) == length
    if length.startswith("<="):
        return len(obj) <= int(length[2:])
    if length.startswith(">="):
        return len(obj) >= int(length[2:])
    ln = int(length[1:])
    if length[0] == "=":
        return len(obj) == ln
    if length[0] == "<":
        return len(obj) < ln
    if length[0] == ">":
        return len(obj) > ln
    fail("Unexpected length format")

def _validate_impl(obj, schema):
    stack = []

    def newStackFrame(obj, schema):
        stack.append({
            "obj": obj,
            "schema": schema,
            "state": "start",
        })

    newStackFrame(obj, schema)
    ret = ""

    # Because bazel doesn't allow infinite loops/recursion, just make a loop
    # with an arbitrarily large number of iterations.
    for _ in range(100000):
        if not stack:
            break
        frame = stack[-1]
        obj = frame["obj"]
        schema = frame["schema"]
        state = frame["state"]

        if state == "start":
            if len(schema) == 0:
                ret = ""
                stack.pop()
                continue
            if "or" in schema:
                if len(schema) != 1:
                    fail("an 'or' schema must not be accompanied by any other keys")
                frame["i"] = 0
                frame["state"] = "or_loop"
                frame["failures"] = []
                newStackFrame(obj, schema["or"][0])
                continue
            if "type" not in schema:
                fail("a non-empty/non-or schema must have a 'type' key: " + str(schema))
            if schema.get("noneable", False):
                if obj == None:
                    ret = ""
                    stack.pop()
                    continue
            ty = schema["type"]
            if type(obj) != ty:
                ret = "Expected %s, got %s" % (ty, type(obj))
                stack.pop()
                continue
            if "length" in schema:
                if ty not in ["string", "bytes", "list", "tuple"]:
                    fail("'len' is only valid for string, bytes, lists, or tuples, got: " + ty)
                if not _check_len(obj, schema["length"]):
                    ret = "Expected length %s, got %d" % (schema["length"], len(obj))
                    stack.pop()
                    continue
            if "choices" in schema:
                if ty not in ["string", "int", "float"]:
                    fail("'choices' is only valid for string, int, or float, got: " + ty)
                if obj not in schema["choices"]:
                    ret = "Expected one of %s, got %s" % (schema["choices"], obj)
                    stack.pop()
                    continue
            if "value" in schema:
                if ty not in ["string", "int", "float"]:
                    fail("'value' is only valid for string, int, or float, got: " + ty)
                if obj != schema["value"]:
                    ret = "Expected %s, got %s" % (schema["value"], obj)
                    stack.pop()
                    continue
            if schema.get("unique", False):
                if ty != "list" and ty != "tuple":
                    fail("'unique' is only valid for lists or tuples, got: " + ty)
                sorted_list = sorted(obj)
                done = False
                for i in range(len(sorted_list) - 1):
                    if type(sorted_list[i]) not in ["string", "int", "float", "bool", "NoneType", "bytes"]:
                        ret = "'unique' only works on lists/tuples of scalar types, got: " + type(sorted_list[i])
                        stack.pop()
                        done = True
                        break
                    if sorted_list[i] == sorted_list[i + 1]:
                        ret = "Expected all elements to be unique, but saw '%s' twice" % str(sorted_list[i])
                        stack.pop()
                        done = True
                        break
                if done:
                    continue
            if "of" in schema:
                if ty != "list" and ty != "tuple":
                    fail("'of' is only valid for lists or tuples, got: " + ty)
                if obj:
                    frame["i"] = 0
                    frame["state"] = "of_loop"
                    newStackFrame(obj[0], schema["of"])
                    continue
            if ty == "dict":
                if "required_fields" in schema or "optional_fields" in schema:
                    fail("a dict schema can't contain required_fields/optional_fields")
                schema_names_keys = bool(schema.get("required_keys", {})) or bool(schema.get("optional_keys", {}))
                schema_enforces_generic_keys = bool(schema.get("keys", {})) or bool(schema.get("values", {}))
                if schema_names_keys and schema_enforces_generic_keys:
                    fail("Only required_keys/optional_keys or keys/values may be used, but not both")
                if schema_names_keys:
                    all_keys = {}
                    done = False
                    for key, subSchema in schema.get("required_keys", {}).items():
                        if key not in obj:
                            ret = "required key '" + key + "' not found"
                            stack.pop()
                            done = True
                            break
                        all_keys[key] = subSchema
                    if done:
                        continue
                    for key, subSchema in schema.get("optional_keys", {}).items():
                        if key in all_keys:
                            fail("A key cannot be both required and optional: " + key)
                        if key in obj:
                            all_keys[key] = subSchema
                    extra_keys = [
                        key
                        for key in obj.keys()
                        if key not in all_keys
                    ]
                    if extra_keys:
                        ret = "keys " + str(extra_keys) + " not allowed, valid keys: " + str(all_keys.keys())
                        stack.pop()
                        continue
                    if all_keys:
                        frame["all_keys"] = all_keys.items()
                        frame["i"] = 0
                        frame["state"] = "dict_individual_keys_loop"
                        k, v = frame["all_keys"][0]
                        newStackFrame(obj[k], v)
                        continue
                elif schema_enforces_generic_keys:
                    frame["items"] = obj.items()
                    if frame["items"]:
                        frame["i"] = 0
                        frame["state"] = "dict_generic_keys_loop"
                        frame["checking_key"] = True
                        continue
            if ty == "struct":
                if "required_keys" in schema or "optional_keys" in schema or "keys" in schema or "values" in schema:
                    fail("a struct schema can't contain required_keys/optional_keys/keys/values")
                all_fields = {}
                original_fields = {f: True for f in dir(obj)}
                done = False
                for field, subSchema in schema.get("required_fields", {}).items():
                    if field not in original_fields:
                        ret = "required field '" + field + "' not found"
                        stack.pop()
                        done = True
                        break
                    all_fields[field] = subSchema
                if done:
                    continue
                for field, subSchema in schema.get("optional_fields", {}).items():
                    if field in all_fields:
                        fail("A field cannot be both required and optional: " + key)
                    if field in original_fields:
                        all_fields[field] = subSchema
                for field in all_fields:
                    if field == "to_json" or field == "to_proto":
                        fail("don't use deprecated fields to_json or to_proto")
                extra_fields = [
                    field
                    for field in original_fields.keys()
                    if field not in all_fields and field != "to_json" and field != "to_proto"
                ]
                if extra_fields:
                    ret = "fields " + str(extra_fields) + " not allowed, valid keys: " + str(all_fields.keys())
                    stack.pop()
                    continue
                if all_fields:
                    frame["all_fields"] = all_fields.items()
                    frame["i"] = 0
                    frame["state"] = "struct_individual_fields_loop"
                    k, v = frame["all_fields"][0]
                    newStackFrame(getattr(obj, k), v)
                    continue
        elif state == "or_loop":
            if ret != "":
                frame["failures"].append("  " + ret)
                frame["i"] += 1
                if frame["i"] >= len(schema["or"]):
                    ret = "did not match any schemas in 'or' list, errors:\n" + "\n".join(frame["failures"])
                    stack.pop()
                    continue
                else:
                    newStackFrame(obj, schema["or"][frame["i"]])
                    continue
        elif state == "of_loop":
            frame["i"] += 1
            if ret != "" or frame["i"] >= len(obj):
                stack.pop()
                continue
            newStackFrame(obj[frame["i"]], schema["of"])
            continue
        elif state == "dict_individual_keys_loop":
            frame["i"] += 1
            if ret != "" or frame["i"] >= len(frame["all_keys"]):
                stack.pop()
                continue
            k, v = frame["all_keys"][frame["i"]]
            newStackFrame(obj[k], v)
            continue
        elif state == "dict_generic_keys_loop":
            if ret != "" or frame["i"] >= len(frame["items"]):
                stack.pop()
                continue
            k, v = frame["items"][frame["i"]]
            if frame["checking_key"]:
                frame["checking_key"] = False
                newStackFrame(k, schema.get("keys", {}))
                continue
            else:
                frame["checking_key"] = True
                frame["i"] += 1
                newStackFrame(v, schema.get("values", {}))
                continue
        elif state == "struct_individual_fields_loop":
            frame["i"] += 1
            if ret != "" or frame["i"] >= len(frame["all_fields"]):
                stack.pop()
                continue
            k, v = frame["all_fields"][frame["i"]]
            newStackFrame(getattr(obj, k), v)
            continue

        # by default return success
        ret = ""
        stack.pop()
    if stack:
        fail("Schema validation took too many iterations")
    return ret

def validate(obj, schema, *, validate_schema = True, fail_on_error = True):
    """Validates the given starlark object against a schema.

    A schema is a dictionary that describes the format of obj. Currently,
    recursive objects cannot be validated because there's no cycle detection.

    An empty dictionary describes "any object".

    A dictionary with an "or" key must not have any other keys, and its
    value is a list of other schema objects. If any of those schema objects
    match, the "or" schema is considered a success.

    Any schemas that are not empty or "or" schemas must have a "type" key.
    This type must match the result of type(obj).

    The "noneable" key can be set to true to act as an alias for:
    `{"or": [{"type": "NoneType"}, ...the rest of the schema...]}`

    The "value" key contains a value that must match the object exactly.
    Only applies to strings, ints, and floats.

    The "choices" key is a list of values that the object could match.
    If the object is equal to any one of them then validation succeeds.

    The "length" key applies to strings, bytes, lists, or tuples.
    Its value can either be an integer length that the object must have,
    or a string in that starts with <, >, <=, >=, or =, followed by a number.

    The "of" key is a schema to match against the elements of a list/tuple.

    Dictionaries and structs have "required_keys"/"required_fields" and
    "optional_keys"/"optional_fields". (keys for dictionaries, fields for
    structs). The value of each of these fields is a dictionary mapping from
    the key/field value to a schema object to validate the value of the
    key/field. Any keys/fields that are not listed in the schema will cause
    the validation to fail. Any keys/fields in the required_ schemas must
    be present in the input object.

    Dictionaries have two additional fields over structs, "keys" and "values".
    These fields cannot be mixed with required_keys/optional_keys. They provide
    a single schema object each to apply to all the keys/values in the dictionary.

    Args:
        obj: The object to be validated against the schema
        schema: The schema. (See above)
        validate_schema: Also check that the schema itself is valid. This
            can be disabled for performance. However, some of the checks
            about the schema are hardcoded and cannot be disabled.
        fail_on_error: If this function should fail() when the object doesn't
            conform to the schema. Note that if the schema itself is invalid,
            validate() fails regardless of the value of this argument.
    Returns:
        If fail_on_error is True, validate() doesn't return anything.
        If fail_on_error is False, validate() returns a string that describes
        the reason why the object doesn't match the schema, or an empty string
        if it does match.
    """
    if validate_schema:
        schema_validation_results = _validate_impl(schema, _schema_schema)
        if schema_validation_results:
            fail("Schema is invalid: " + schema_validation_results)
    result = _validate_impl(obj, schema)
    if not fail_on_error:
        return result
    if result:
        fail(result)
    return None
