#!/usr/bin/env python3
# Copyright 2018 The Android Open Source Project
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

"""Validates TEST_MAPPING files in Android source code.

The goal of this script is to validate the format of TEST_MAPPING files:
1. It must be a valid json file.
2. Each test group must have a list of test that containing name and options.
3. Each import must have only one key `path` and one value for the path to
   import TEST_MAPPING files.
"""

import argparse
import json
import os
import re
import sys
from typing import Any, Dict

_path = os.path.realpath(__file__ + '/../..')
if sys.path[0] != _path:
    sys.path.insert(0, _path)
del _path

# We have to import our local modules after the sys.path tweak.  We can't use
# relative imports because this is an executable program, not a module.
# pylint: disable=wrong-import-position
import rh.git

_IMPORTS = 'imports'
_NAME = 'name'
_OPTIONS = 'options'
_PATH = 'path'
_HOST = 'host'
_PREFERRED_TARGETS = 'preferred_targets'
_FILE_PATTERNS = 'file_patterns'
_INVALID_IMPORT_CONFIG = 'Invalid import config in TEST_MAPPING file'
_INVALID_TEST_CONFIG = 'Invalid test config in TEST_MAPPING file'
_TEST_MAPPING_URL = (
    'https://source.android.com/compatibility/tests/development/'
    'test-mapping')

# Pattern used to identify line-level '//'-format comment in TEST_MAPPING file.
_COMMENTS_RE = re.compile(r'^\s*//')


class Error(Exception):
    """Base exception for all custom exceptions in this module."""


class InvalidTestMappingError(Error):
    """Exception to raise when detecting an invalid TEST_MAPPING file."""


def _filter_comments(json_data: str) -> str:
    """Removes '//'-format comments in TEST_MAPPING file to valid format.

    Args:
        json_data: TEST_MAPPING file content (as a string).

    Returns:
        Valid json string without comments.
    """
    return ''.join(
        '\n' if _COMMENTS_RE.match(x) else x for x in json_data.splitlines())


def _validate_import(entry: Dict[str, Any], test_mapping_file: str):
    """Validates an import setting.

    Args:
        entry: A dictionary of an import setting.
        test_mapping_file: Path to the TEST_MAPPING file to be validated.

    Raises:
        InvalidTestMappingError: if the import setting is invalid.
    """
    if len(entry) != 1:
        raise InvalidTestMappingError(
            f'{_INVALID_IMPORT_CONFIG} {test_mapping_file}. Each import can '
            f'only have one `path` setting. Failed entry: {entry}')
    if _PATH not in entry:
        raise InvalidTestMappingError(
            f'{_INVALID_IMPORT_CONFIG} {test_mapping_file}. Import can '
            f'only have one `path` setting. Failed entry: {entry}')


def _validate_test(test: Dict[str, Any], test_mapping_file: str) -> bool:
    """Returns whether a test declaration is valid.

    Args:
        test: A dictionary of a test declaration.
        test_mapping_file: Path to the TEST_MAPPING file to be validated.

    Raises:
        InvalidTestMappingError: if the a test declaration is invalid.
    """
    if _NAME not in test:
        raise InvalidTestMappingError(

            f'{_INVALID_TEST_CONFIG} {test_mapping_file}. Test config must '
            f'have a `name` setting. Failed test config: {test}')

    if not isinstance(test.get(_HOST, False), bool):
        raise InvalidTestMappingError(
            f'{_INVALID_TEST_CONFIG} {test_mapping_file}. `host` setting in '
            f'test config can only have boolean value of `true` or `false`. '
            f'Failed test config: {test}')

    for key in (_PREFERRED_TARGETS, _FILE_PATTERNS):
        value = test.get(key, [])
        if (not isinstance(value, list) or
            any(not isinstance(t, str) for t in value)):
            raise InvalidTestMappingError(
                f'{_INVALID_TEST_CONFIG} {test_mapping_file}. `{key}` setting '
                f'in test config can only be a list of strings. '
                f'Failed test config: {test}')

    for option in test.get(_OPTIONS, []):
        if not isinstance(option, dict):
            raise InvalidTestMappingError(
                f'{_INVALID_TEST_CONFIG} {test_mapping_file}. Option setting '
                f'in test config can only be a dictionary of key-val setting. '
                f'Failed entry: {option}')
        if len(option) != 1:
            raise InvalidTestMappingError(
                f'{_INVALID_TEST_CONFIG} {test_mapping_file}. Each option '
                f'setting can only have one key-val setting. '
                f'Failed entry: {option}')


def process_file(test_mapping_file: str):
    """Validates a TEST_MAPPING file content."""
    try:
        test_mapping_data = json.loads(_filter_comments(test_mapping_file))
    except ValueError as exception:
        # The file is not a valid JSON file.
        print(
            f'Invalid JSON data in TEST_MAPPING file '
            f'Failed to parse JSON data: {test_mapping_file}, '
            f'error: {exception}',
            file=sys.stderr)
        raise

    for group, value in test_mapping_data.items():
        if group == _IMPORTS:
            # Validate imports.
            for test in value:
                _validate_import(test, test_mapping_file)
        else:
            # Validate tests.
            for test in value:
                _validate_test(test, test_mapping_file)


def get_parser():
    """Returns a command line parser."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', type=str,
                        help='Specify the commit to validate.')
    parser.add_argument('project_dir')
    parser.add_argument('files', nargs='+')
    return parser


def main(argv):
    """Main function."""
    parser = get_parser()
    opts = parser.parse_args(argv)
    try:
        for filename in opts.files:
            if opts.commit:
                json_data = rh.git.get_file_content(opts.commit, filename)
            else:
                with open(os.path.join(opts.project_dir, filename),
                          encoding='utf-8') as file:
                    json_data = file.read()
            process_file(json_data)
    except:
        print(f'Visit {_TEST_MAPPING_URL} for details about the format of '
              'TEST_MAPPING file.', file=sys.stderr)
        raise


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
