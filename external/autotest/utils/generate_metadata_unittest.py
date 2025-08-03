#!/usr/bin/python3

# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import six
import unittest
import tempfile
import shutil

os.environ["PY_VERSION"] = '3'

import common

# These tests are strictly not supported in python2.
if six.PY2:
    exit(0)

from autotest_lib.client.common_lib import control_data
from autotest_lib.utils import generate_metadata

CONTROL_DATA1 = """
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

AUTHOR = 'an author with email@google.com'
NAME = 'fake_test1'
PURPOSE = 'A fake test.'
ATTRIBUTES = 'suite:fake_suite1, suite:fake_suite2'
TIME = 'SHORT'
TEST_CATEGORY = 'Functional'
TEST_CLASS = 'audio'
TEST_TYPE = 'client'
DEPENDENCIES = 'fakedep1'

DOC = '''
a doc
'''

job.run_test('fake_test1')

"""

CONTROL_DATA2 = """
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

AUTHOR = 'an author with email@google.com'
NAME = 'fake_test2'
PURPOSE = 'A fake test.'
ATTRIBUTES = 'suite:fake_suite1, suite:fake_suite2'
TIME = 'SHORT'
TEST_CATEGORY = 'Functional'
TEST_CLASS = 'audio'
TEST_TYPE = 'client'
DEPENDENCIES = 'fakedep2'

DOC = '''
a doc
'''

job.run_test('fake_test2')

"""


class Namespace:
    """Stub for mocking args."""

    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)


class MetadataTest(unittest.TestCase):
    """Test generate_metadata."""

    def setUp(self):
        """Build up a tmp directory to host control files."""
        self.tmp_dir = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.tmp_dir, 'server/site_tests'))
        os.makedirs(os.path.join(self.tmp_dir, 'client/site_tests'))
        self.path1 = os.path.join(self.tmp_dir, 'server/site_tests',
                                  'control.1')
        self.path2 = os.path.join(self.tmp_dir, 'client/site_tests',
                                  'control.2')
        self.test1 = control_data.parse_control_string(CONTROL_DATA1,
                                                       raise_warnings=True,
                                                       path=self.path1)
        self.test2 = control_data.parse_control_string(CONTROL_DATA2,
                                                       raise_warnings=True,
                                                       path=self.path2)

    def tearDown(self):
        """Delete the tmp directory."""
        shutil.rmtree(self.tmp_dir)

    def test_args(self):
        """Test CLI."""
        parsed = generate_metadata.parse_local_arguments(
                ['-autotest_path', '/tauto/path', '-output_file', 'testout'])
        self.assertEqual(parsed.autotest_path, '/tauto/path')
        self.assertEqual(parsed.output_file, 'testout')

    def test_all_control_files(self):
        """Test all_control_files finds all ctrl files in the expected dirs."""
        with open(self.path1, 'w') as wf:
            wf.write(CONTROL_DATA1)
        with open(self.path2, 'w') as wf:
            wf.write(CONTROL_DATA2)

        files = generate_metadata.all_control_files(
                Namespace(autotest_path=self.tmp_dir))

        # Verify the files are found.
        self.assertEqual(set(files), set([self.path1, self.path2]))

    def test_serialization(self):
        """Test a single control file gets properly serialized."""
        meta_data = generate_metadata.serialized_test_case_metadata(self.test1)
        self.assertEqual(meta_data.test_case.id.value, 'tauto.fake_test1')
        self.assertEqual(meta_data.test_case.name, 'fake_test1')
        # verify tags
        expected_tags = set([
                'fakedep1', 'test_class:audio', 'suite:fake_suite1',
                'suite:fake_suite2'
        ])
        actual_tags = set([item.value for item in meta_data.test_case.tags])
        self.assertEqual(expected_tags, actual_tags)
        # verify harness. This is a bit of a hack but works and keeps import
        # hacking down.
        self.assertIn('tauto', str(meta_data.test_case_exec.test_harness))
        # verify owners
        expected_owners = set(
                [item.email for item in meta_data.test_case_info.owners])
        self.assertEqual(expected_owners,
                         set(['an author with email@google.com']))

    def test_serialized_test_case_metadata_list(self):
        """Test all control file get properly serialized."""
        serialized_list = generate_metadata.serialized_test_case_metadata_list(
                [
                        generate_metadata.serialized_test_case_metadata(
                                self.test1),
                        generate_metadata.serialized_test_case_metadata(
                                self.test2)
                ])
        names = set([item.test_case.name for item in serialized_list.values])
        self.assertEqual(set(['fake_test1', 'fake_test2']), names)


if __name__ == '__main__':
    if six.PY2:
        print('cannot run in py2')
        exit(0)
    else:
        unittest.main()
