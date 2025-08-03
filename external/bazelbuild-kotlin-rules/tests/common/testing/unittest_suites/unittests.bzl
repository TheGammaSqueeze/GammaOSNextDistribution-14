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

visibility(RULES_KOTLIN)

unittests = kt_unittest_suites.create()

def _add_private_test_case(_ctx, _env):
    under_test = kt_unittest_suites.create()

    def _some_test():
        pass

    under_test.expect_finish(_some_test)

unittests.expect_finish(_add_private_test_case)

def _add_private_fail_case(_ctx, _env):
    under_test = kt_unittest_suites.create()

    def _some_test():
        pass

    under_test.expect_fail(_some_test, "")

unittests.expect_finish(_add_private_fail_case)

def _add_public_test_case(_ctx):
    under_test = kt_unittest_suites.create()

    def some_test():
        pass

    under_test.expect_finish(some_test)

unittests.expect_fail(_add_public_test_case, "private")

def _add_public_fail_case(_ctx):
    under_test = kt_unittest_suites.create()

    def some_test():
        pass

    under_test.expect_fail(some_test, "")

unittests.expect_fail(_add_public_fail_case, "private")

def _add_duplicate_test_case(_ctx):
    under_test = kt_unittest_suites.create()

    def _some_test():
        pass

    under_test.expect_finish(_some_test)
    under_test.expect_finish(_some_test)

unittests.expect_fail(_add_duplicate_test_case, "Existing")

def _add_duplicate_fail_case(_ctx):
    under_test = kt_unittest_suites.create()

    def _some_test():
        pass

    under_test.expect_fail(_some_test, "")
    under_test.expect_fail(_some_test, "")

unittests.expect_fail(_add_duplicate_fail_case, "Existing")

def _add_duplicate_test_fail_case(_ctx):
    under_test = kt_unittest_suites.create()

    def _some_test():
        pass

    under_test.expect_finish(_some_test)
    under_test.expect_fail(_some_test, "")

unittests.expect_fail(_add_duplicate_test_fail_case, "Existing")

_test, _fail = unittests.close()  # @unused
