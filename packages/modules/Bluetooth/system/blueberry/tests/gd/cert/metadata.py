#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import functools
import inspect

from mobly import asserts

from blueberry.tests.gd.cert.test_decorators import test_info


def _fail_decorator(msg):

    def fail_decorator(func):

        @functools.wraps(func)
        def fail(*args, **kwargs):
            asserts.fail(msg)

        return fail

    return fail_decorator


def metadata(_do_not_use=None, pts_test_id=None, pts_test_name=None):
    """
    Record a piece of test metadata in the Extra section of the test Record in
    the test summary file. The metadata will come with a timestamp, but there
    is no guarantee on the order of when the metadata will be written

    Note:
    - Metadata is recorded per test case as key-value pairs.
    - Metadata is only guaranteed to be written when the test result is PASS,
      FAIL or SKIPPED. When there are test infrastructural errors, metadata
      might not be written successfully
    :param _do_not_use: a positional argument with default value. This argument
                        is to ensure that @metadata(key=value) is used in a
                        functional form instead of @metadata or @metadata(a)
    :param pts_test_id: A fully qualified PTS test ID such as
                        L2CAP/COS/IEX/BV-01-C
    :param pts_test_name: A human readable test name such as
                          "Request Connection" for the above example
    :return: decorated test case function object
    """
    if _do_not_use is not None:

        def fail(*args, **kwargs):
            asserts.fail("@metadata must be used in functional form such " "as @metadta(key=value)")

        return fail

    # Create a dictionary of optional parameters
    values = locals()
    args = {arg: values[arg] for arg in inspect.getfullargspec(metadata).args}
    del args["_do_not_use"]

    # Check if at least one optional parameter is valid
    if not any(args.values()):
        return _fail_decorator("at least one optional argument should be valid")

    # Validate pts_test_id and pts_test_name
    if any((pts_test_id, pts_test_name)) and \
            not all((pts_test_id, pts_test_name)):
        return _fail_decorator("pts_test_id and pts_test_name must both " "be valid if one of them is valid")

    return test_info(**args)
