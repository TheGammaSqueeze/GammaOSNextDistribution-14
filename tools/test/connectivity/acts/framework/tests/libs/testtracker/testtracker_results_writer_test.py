#!/usr/bin/env python3
#
# Copyright 2020 - The Android Open Source Project
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

import unittest

import mock
from acts.libs.testtracker.protos.gen.testtracker_result_pb2 import Result
from acts.libs.testtracker.testtracker_results_writer import KEY_DETAILS
from acts.libs.testtracker.testtracker_results_writer import KEY_EFFORT_NAME
from acts.libs.testtracker.testtracker_results_writer import KEY_PROJECT_ID
from acts.libs.testtracker.testtracker_results_writer import \
    KEY_TESTTRACKER_UUID
from acts.libs.testtracker.testtracker_results_writer import KEY_USER
from acts.libs.testtracker.testtracker_results_writer import KEY_UUID
from acts.libs.testtracker.testtracker_results_writer import \
    TestTrackerError
from acts.libs.testtracker.testtracker_results_writer import \
    TestTrackerResultsWriter
from acts.records import TestResultRecord
from acts.signals import TestSignal

MOCK_PROPERTIES = {
    KEY_USER: 'sample_user',
    KEY_PROJECT_ID: 12345,
    KEY_EFFORT_NAME: 'sample_effort'
}
MOCK_SIGNAL = TestSignal(
    'Sample error message', {KEY_TESTTRACKER_UUID: '12345abcde'})
MOCK_LOG_PATH = 'sample_logs'


class TestTrackerResultsWriterTest(unittest.TestCase):
    """Unit tests for TestTrackerResultsWriter"""

    def setUp(self):
        self.mock_proto = mock.MagicMock()
        self.mock_prop = mock.MagicMock()
        self.mock_proto.property.add.return_value = self.mock_prop
        self.mock_record = TestResultRecord('sample_test')
        self.mock_record.begin_time = 0
        self.mock_record.test_pass(MOCK_SIGNAL)

    def test_validate_properties_raises_on_missing_required_props(self):
        """Test that _validate_properties raises an exception if not all of the
        required properties are specified.
        """
        invalid_props = {'property1': 'value1'}
        with self.assertRaisesRegex(TestTrackerError, 'required properties'):
            _ = TestTrackerResultsWriter(MOCK_LOG_PATH, invalid_props)

    def test_add_property_with_bool_value(self):
        """Test that adding a bool Property to a Result correctly sets the
        Property value.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        writer._add_property(self.mock_proto, 'bool_prop', False)
        self.assertEqual(self.mock_prop.bool_value, False)

    def test_add_property_with_int_value(self):
        """Test that adding a int Property to a Result correctly sets the
        Property value.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        writer._add_property(self.mock_proto, 'int_prop', 5)
        self.assertEqual(self.mock_prop.int_value, 5)

    def test_add_property_with_float_value(self):
        """Test that adding a float Property to a Result correctly sets the
        Property value.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        writer._add_property(self.mock_proto, 'float_prop', 7.4)
        self.assertEqual(self.mock_prop.double_value, 7.4)

    def test_add_property_with_str_value(self):
        """Test that adding a str Property to a Result correctly sets the
        Property value.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        writer._add_property(self.mock_proto, 'str_prop', 'ok')
        self.assertEqual(self.mock_prop.string_value, 'ok')

    def test_create_result_proto_sets_correct_uuid(self):
        """Test that _create_result_proto sets the correct uuid from the test
        result record.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.uuid, '12345abcde')
        for prop in result.property:
            if prop.name == KEY_UUID:
                self.assertEqual(prop.string_value, '12345abcde')
                return
        self.fail('Property "%s" missing from Result proto.' % KEY_UUID)

    def test_create_result_proto_sets_status_to_passed(self):
        """Test that _create_result_proto correctly sets status for a test with
        result=PASS.
        """
        self.mock_record.test_pass(MOCK_SIGNAL)
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.status, Result.PASSED)

    def test_create_result_proto_sets_status_to_failed(self):
        """Test that _create_result_proto correctly sets status for a test with
        result=FAIL.
        """
        self.mock_record.test_fail(MOCK_SIGNAL)
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.status, Result.FAILED)

    def test_create_result_proto_sets_status_to_skipped(self):
        """Test that _create_result_proto correctly sets status for a test with
        result=SKIP.
        """
        self.mock_record.test_skip(MOCK_SIGNAL)
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.status, Result.SKIPPED)

    def test_create_result_proto_sets_status_to_error(self):
        """Test that _create_result_proto correctly sets status for a test with
        result=ERROR.
        """
        self.mock_record.test_error(MOCK_SIGNAL)
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.status, Result.ERROR)

    def test_create_result_proto_sets_correct_timestamp(self):
        """Test that _create_result_proto sets the correct timestamp format
        given begin_time (in ms).
        """
        self.mock_record.begin_time = 1579230033639
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        self.assertEqual(result.timestamp, '2020-01-17T03:00:33.639Z')

    def test_create_result_proto_sets_correct_details(self):
        """Test that _create_result_proto correctly sets the details Property
        from the test signal.
        """
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        result = writer._create_result_proto(self.mock_record)
        for prop in result.property:
            if prop.name == KEY_DETAILS:
                self.assertEqual(prop.string_value, 'Sample error message')
                return
        self.fail('Property "%s" missing from Result proto.' % KEY_DETAILS)

    def test_create_result_proto_sets_additional_properties(self):
        """Test that _create_result_proto correctly sets extra properties."""
        writer = TestTrackerResultsWriter(
            MOCK_LOG_PATH, dict(**MOCK_PROPERTIES, extra_prop='foo'))
        result = writer._create_result_proto(self.mock_record)
        for prop in result.property:
            if prop.name == 'extra_prop':
                self.assertEqual(prop.string_value, 'foo')
                return
        self.fail('Property "extra_prop" missing from Result proto.')

    @mock.patch('os.makedirs')
    def test_create_results_dir(self, _):
        """Test that _create_results_dir generates the correct path."""
        writer = TestTrackerResultsWriter(MOCK_LOG_PATH, MOCK_PROPERTIES)
        self.assertEqual(
            writer._create_results_dir('23456bcdef'),
            'sample_logs/test_tracker_results/test_effort_name=sample_effort/'
            'test_case_uuid=23456bcdef')


if __name__ == '__main__':
    unittest.main()
