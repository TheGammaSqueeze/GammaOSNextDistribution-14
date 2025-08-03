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

"""A framework for writing tests of Starlark code with minimal overhead.

Test cases are written as Starlark functions that will eventually be executed as
individual test targets. Two types of tests are supported: those including their
own assertions, and those expected to call 'fail()'.

Basic usage looks like:
```
# unittests.bzl
load("//kotlin/common/testing:unittest_suites.bzl", "kt_unittest_suites")
load("@bazel_skylib//lib:unittest.bzl", "asserts")

unittests = kt_unittest_suite.create() # Create a new suite in this file.

def _some_test_case(ctx, env):
    # Test logic here
    asserts.true(env, 1 == 1)
    return [] # Return any declared files
    
unittests.expect_finish(_some_test_case) # Include the test case in the suite

def _some_fail_case(ctx):
    # No assertions are allowed in fail cases
    _some_logic_that_should_call_fail(ctx)
    
unittests.expect_fail(_some_fail_case, "fail message substring") # Expect this case to call fail

# Generate a pair of rules that will be used for test targets
_test, _fail = unittests.close()  # @unused
```

```
// BUILD
load(":unittests.bzl", "unittests")

# Render each test case as a target in this package
unittests.render(
    name = "unittests"
)
```
"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("@bazel_skylib//lib:unittest.bzl", "unittest")
load(":testing_rules.bzl", "kt_testing_rules")

visibility(RULES_KOTLIN)

def _create():
    """Create a new test suite.

    Returns:
        [kt_unittest_suite] An object representing the suite under construction
    """

    test_cases = dict()
    rule_holder = []  # Use a list rather than separate vars becase captured vars are final

    def expect_fail(test_case, msg_contains):
        """Add a test case to the suite which is expected to call fail.

        Args:
            test_case: [function(ctx)]
            msg_contains: [string] A substring expected in the failure message
        """

        if rule_holder:
            fail("Test suite is closed")

        test_case_name = _fn_name(test_case)
        if not test_case_name.startswith("_"):
            fail("Test cases must be private '%s'" % test_case_name)
        if test_case_name in test_cases:
            fail("Existing test case named '%s'" % test_case_name)

        test_cases[test_case_name] = struct(
            impl = test_case,
            msg_contains = msg_contains,
        )

    def expect_finish(test_case):
        """Add a test case to the suite.

        Args:
            test_case: [function(ctx,unittest.env):None|list[File]]
        """

        expect_fail(test_case, None)

    def close():
        """Close the suite from expect_finishing new tests.

        The return value must be assigned to '_test, _fail' with an '# @unused' suppression.

        Returns:
            [(rule, rule)]
        """

        if rule_holder:
            fail("Test suite is closed")

        def test_impl(ctx):
            env = unittest.begin(ctx)

            output_files = test_cases[ctx.attr.case_name].impl(ctx, env) or []
            if output_files:
                ctx.actions.run_shell(
                    outputs = output_files,
                    command = "exit 1",
                )

            return unittest.end(env) + [OutputGroupInfo(_file_sink = depset(output_files))]

        test_rule = unittest.make(
            impl = test_impl,
            attrs = dict(case_name = attr.string()),
        )
        rule_holder.append(test_rule)

        def fail_impl(ctx):
            test_cases[ctx.attr.case_name].impl(ctx)
            return []

        fail_rule = rule(
            implementation = fail_impl,
            attrs = dict(case_name = attr.string()),
        )
        rule_holder.append(fail_rule)

        # Rules must be assigned to top-level Starlark vars before being called
        return test_rule, fail_rule

    def render(name, tags = [], **kwargs):
        """Render the test suite into targets.

        Args:
            name: [string]
            tags: [list[string]]
            **kwargs: Generic rule kwargs
        """

        if not rule_holder:
            fail("Test suite is not closed")
        test_rule = rule_holder[0]
        fail_rule = rule_holder[1]

        test_targets = []
        for test_case_name, test_case_data in test_cases.items():
            target_name = test_case_name.removeprefix("_") + "_test"
            test_targets.append(target_name)

            if test_case_data.msg_contains == None:
                test_rule(
                    name = target_name,
                    tags = tags,
                    case_name = test_case_name,
                    **kwargs
                )
            else:
                fail_rule(
                    name = test_case_name,
                    tags = tags + kt_testing_rules.ONLY_FOR_ANALYSIS_TAGS,
                    case_name = test_case_name,
                    **kwargs
                )
                kt_testing_rules.assert_failure_test(
                    name = target_name,
                    target_under_test = test_case_name,
                    msg_contains = test_case_data.msg_contains,
                )

        native.test_suite(
            name = name,
            tests = test_targets,
            **kwargs
        )

    return struct(
        expect_finish = expect_finish,
        expect_fail = expect_fail,
        close = close,
        render = render,
    )

def _fn_name(rule_or_fn):
    parts = str(rule_or_fn).removeprefix("<").removesuffix(">").split(" ")
    return parts[0] if (len(parts) == 1) else parts[1]

kt_unittest_suites = struct(
    create = _create,
)
