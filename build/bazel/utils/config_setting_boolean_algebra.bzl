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

load("@bazel_skylib//lib:selects.bzl", "selects")

def _not(name, config_setting):
    native.alias(
        name = name,
        actual = select({
            config_setting: "//build/bazel/utils:always_off_config_setting",
            "//conditions:default": "//build/bazel/utils:always_on_config_setting",
        }),
    )

def _or(name, match_any):
    if not match_any:
        native.alias(
            name = name,
            actual = "//build/bazel/utils:always_off_config_setting",
        )
    else:
        selects.config_setting_group(
            name = name,
            match_any = match_any,
        )

def _and(name, match_all):
    if not match_all:
        native.alias(
            name = name,
            actual = "//build/bazel/utils:always_on_config_setting",
        )
    else:
        selects.config_setting_group(
            name = name,
            match_all = match_all,
        )

def config_setting_boolean_algebra(*, name, expr):
    """
    Computes the given boolean expression of config settings.

    The format of the expr argument is a dictionary with a single key/value pair.
    The key can be AND, OR, or NOT. The value or AND/OR keys must be a list
    of strings or more expression dictionaries, where the strings are labels of config settings.
    The value of NOT keys must be a string or an expression dictionary.

    The result will be a new config setting which is the evaluation of the expression.

    A bunch of internal config settings will also be created, but they should be treated
    as an implementation detail and not relied on. They could change in future updates to
    this method.

    Example:
    config_setting_boolean_algebra(
        name = "my_config_setting",
        expr = {"AND": [
            ":config_setting_1",
            {"NOT": ":config_setting_2"},
            {"OR": [
                ":config_setting_3",
                {"NOT": "config_setting_4"},
            ]}
        ]}
    )
    """

    # The implementation of this function is modeled after a recursive function,
    # but due to the special nature of the problem it's simplified quite a bit from
    # a full recursion-to-iteration algorithm. (no need for return values, no need to return
    # to prior stack frames once we start executing a new one)
    stack = [struct(
        expr = expr,
        name = name,
    )]

    # Starlark doesn't support infinite loops, so just make a large loop
    for _ in range(1000):
        if not stack:
            break

        frame = stack.pop()
        name = frame.name
        expr = frame.expr
        expr_type = type(expr)
        if expr_type == "string":
            native.alias(
                name = name,
                actual = expr,
            )
            continue
        elif expr_type == "dict":
            if len(expr) != 1:
                fail("Dictionaries should have exactly 1 key/value")
            op, operands = expr.items()[0]
            if op == "NOT":
                # traditionally this would come after the recursive call, but because it's a rule
                # definition it doesn't matter if name + ".0" exists yet or not. Having the
                # recursive call come last makes the non-recursive version easier to implement
                _not(name, name + ".0")
                stack.append(struct(
                    name = name + ".0",
                    expr = operands,
                ))
                continue

            if type(operands) != "list":
                fail("Operand to AND/OR must be a list, got %s" % type(operands))

            operand_names = [name + "." + str(i) for i, elem in enumerate(operands)]

            if op == "AND":
                _and(name, operand_names)
            elif op == "OR":
                _or(name, operand_names)
            else:
                fail("Operator must be AND, OR, or NOT, got %s" % op)

            for elem_name, elem in zip(operand_names, operands):
                # because we don't need a return value from these recursive calls,
                # we can queue them all up at once without returning to the current stack frame.
                stack.append(struct(
                    name = elem_name,
                    expr = elem,
                ))
        else:
            fail("Expression must be string or dict, got %s" % expr_type)
    if stack:
        fail("Recursion took too many iterations!")
