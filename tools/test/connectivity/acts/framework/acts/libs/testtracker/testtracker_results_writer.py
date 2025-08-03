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

import datetime
import os

from acts.libs.proto.proto_utils import parse_proto_to_ascii
from acts.libs.testtracker.protos.gen.testtracker_result_pb2 import Result
from acts.records import TestResultEnums
from acts.records import TestResultRecord

from acts import signals

KEY_DETAILS = 'details'
KEY_EFFORT_NAME = 'effort_name'
KEY_PROJECT_ID = 'project_id'
KEY_TESTTRACKER_UUID = 'test_tracker_uuid'
KEY_USER = 'user'
KEY_UUID = 'uuid'

TESTTRACKER_PATH = 'test_tracker_results/test_effort_name=%s/test_case_uuid=%s'
RESULT_FILE_NAME = 'result.pb.txt'

_TEST_RESULT_TO_STATUS_MAP = {
    TestResultEnums.TEST_RESULT_PASS: Result.PASSED,
    TestResultEnums.TEST_RESULT_FAIL: Result.FAILED,
    TestResultEnums.TEST_RESULT_SKIP: Result.SKIPPED,
    TestResultEnums.TEST_RESULT_ERROR: Result.ERROR
}


class TestTrackerError(Exception):
    """Exception class for errors raised within TestTrackerResultsWriter"""


class TestTrackerResultsWriter(object):
    """Takes a test record, converts it to a TestTracker result proto, and
    writes it to the log directory. In automation, these protos will
    automatically be read from Sponge and uploaded to TestTracker.
    """

    def __init__(self, log_path, properties):
        """Creates a TestTrackerResultsWriter

        Args:
            log_path: Base log path to store TestTracker results. Must be within
                the ACTS directory.
            properties: dict representing key-value pairs to be uploaded as
                TestTracker properties.
        """
        self._log_path = log_path
        self._properties = properties
        self._validate_properties()

    def write_results(self, record):
        """Create a Result proto from test record, then write it to a file.

        Args:
            record: An acts.records.TestResultRecord object
        """
        proto = self._create_result_proto(record)
        proto_dir = self._create_results_dir(proto.uuid)
        with open(os.path.join(proto_dir, RESULT_FILE_NAME), mode='w') as f:
            f.write(parse_proto_to_ascii(proto))

    def write_results_from_test_signal(self, signal, begin_time=None):
        """Create a Result proto from a test signal, then write it to a file.

        Args:
            signal: An acts.signals.TestSignal object
            begin_time: Optional. Sets the begin_time of the test record.
        """
        record = TestResultRecord('')
        record.begin_time = begin_time
        if not record.begin_time:
            record.test_begin()
        if isinstance(signal, signals.TestPass):
            record.test_pass(signal)
        elif isinstance(signal, signals.TestFail):
            record.test_fail(signal)
        elif isinstance(signal, signals.TestSkip):
            record.test_skip(signal)
        else:
            record.test_error(signal)
        self.write_results(record)

    def _validate_properties(self):
        """Checks that the required properties are set

        Raises:
            TestTrackerError if one or more required properties is absent
        """
        required_props = [KEY_USER, KEY_PROJECT_ID, KEY_EFFORT_NAME]
        missing_props = [
            p for p in required_props if p not in self._properties
        ]
        if missing_props:
            raise TestTrackerError(
                'Missing the following required properties for TestTracker: %s'
                % missing_props)

    @staticmethod
    def _add_property(result_proto, name, value):
        """Adds a Property to a given Result proto

        Args:
            result_proto: Result proto to modify
            name: Property name
            value: Property value
        """
        new_prop = result_proto.property.add()
        new_prop.name = name
        if isinstance(value, bool):
            new_prop.bool_value = value
        elif isinstance(value, int):
            new_prop.int_value = value
        elif isinstance(value, float):
            new_prop.double_value = value
        else:
            new_prop.string_value = str(value)

    def _create_result_proto(self, record):
        """Create a Result proto object from test record. Fills in uuid, status,
        and properties with info gathered from the test record.

        Args:
            record: An acts.records.TestResultRecord object

        Returns: Result proto, or None if record is invalid
        """
        uuid = record.extras[KEY_TESTTRACKER_UUID]
        result = Result()
        result.uuid = uuid
        result.status = _TEST_RESULT_TO_STATUS_MAP[record.result]
        result.timestamp = (datetime.datetime.fromtimestamp(
            record.begin_time / 1000,
            datetime.timezone.utc).isoformat(timespec='milliseconds').replace(
                '+00:00', 'Z'))

        self._add_property(result, KEY_UUID, uuid)
        if record.details:
            self._add_property(result, KEY_DETAILS, record.details)

        for key, value in self._properties.items():
            self._add_property(result, key, value)

        return result

    def _create_results_dir(self, uuid):
        """Creates the TestTracker directory given the test uuid

        Args:
            uuid: The TestTracker uuid of the test

        Returns: Path to the created directory.
        """
        dir_path = os.path.join(
            self._log_path,
            TESTTRACKER_PATH % (self._properties[KEY_EFFORT_NAME], uuid))
        os.makedirs(dir_path, exist_ok=True)
        return dir_path
