# Copyright (C) 2024 The Android Open Source Project
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
"""Helper functions.

Facilitates the implementation of a new profile proxy or a PTS MMI.
"""

import functools
import textwrap
import unittest
import re

DOCSTRING_WIDTH = 80 - 8  # 80 cols - 8 indentation spaces


def assert_description(f):
    """Decorator which verifies the description of a PTS MMI implementation.

    Asserts that the docstring of a function implementing a PTS MMI is the same
    as the corresponding official MMI description.

    Args:
        f: function implementing a PTS MMI.

    Raises:
        AssertionError: the docstring of the function does not match the MMI
            description.
    """

    @functools.wraps(f)
    def wrapper(*args, **kwargs):
        description = textwrap.fill(kwargs['description'], DOCSTRING_WIDTH, replace_whitespace=False)
        description = ('\n'.join(map(lambda line: line.rstrip(), description.split('\n')))).strip()
        docstring = textwrap.dedent(f.__doc__ or '').strip()

        if docstring != description:
            print(f'Expected description of {f.__name__}:')
            print(description)

            # Generate AssertionError.
            test = unittest.TestCase()
            test.maxDiff = None
            test.assertMultiLineEqual(docstring, description, f'description does not match with function docstring of'
                                      f' {f.__name__}')

        return f(*args, **kwargs)

    return wrapper


def match_description(f):
    """Extracts parameters from PTS MMI descriptions.

    Similar to assert_description, but treats the description as an (indented)
    regex that can be used to extract named capture groups from the PTS command.

    Args:
        f: function implementing a PTS MMI.

    Raises:
        AssertionError: the docstring of the function does not match the MMI
            description.
    """

    def normalize(desc):
        return re.sub('\s+', ' ', desc).strip()

    docstring = normalize(textwrap.dedent(f.__doc__))
    regex = re.compile(docstring)

    @functools.wraps(f)
    def wrapper(*args, **kwargs):
        description = normalize(kwargs['description'])
        match = regex.fullmatch(description)

        assert match is not None, f'description does not match with function docstring of {f.__name__}:\n{repr(description)}\n!=\n{repr(docstring)}'

        return f(*args, **kwargs, **match.groupdict())

    return wrapper


def format_function(mmi_name, mmi_description):
    """Returns the base format of a function implementing a PTS MMI."""
    wrapped_description = textwrap.fill(mmi_description, DOCSTRING_WIDTH, replace_whitespace=False)
    wrapped_description = '\n'.join(map(lambda line: line.rstrip(), wrapped_description.split('\n')))
    return (f'@assert_description\n'
            f'def {mmi_name}(self, **kwargs):\n'
            f'    """\n'
            f'{textwrap.indent(wrapped_description, "    ")}\n'
            f'    """\n'
            f'\n'
            f'    return "OK"\n')


def format_proxy(profile, mmi_name, mmi_description):
    """Returns the base format of a profile proxy including a given MMI."""
    wrapped_function = textwrap.indent(format_function(mmi_name, mmi_description), '    ')
    return (f'from mmi2grpc._helpers import assert_description\n'
            f'from mmi2grpc._proxy import ProfileProxy\n'
            f'\n'
            f'from pandora_experimental.{profile.lower()}_grpc import {profile}\n'
            f'\n'
            f'\n'
            f'class {profile}Proxy(ProfileProxy):\n'
            f'\n'
            f'    def __init__(self, channel):\n'
            f'        super().__init__(channel)\n'
            f'        self.{profile.lower()} = {profile}(channel)\n'
            f'\n'
            f'{wrapped_function}')
