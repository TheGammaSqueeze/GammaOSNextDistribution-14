#!/usr/bin/env python3

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

import unittest
from unittest import mock

from mobly.tools import device_flags
from protos import aconfig_pb2


class DeviceFlagsTest(unittest.TestCase):
    """Unit tests for DeviceFlags."""

    def setUp(self) -> None:
        super().setUp()
        self.ad = mock.MagicMock()
        self.device_flags = device_flags.DeviceFlags(self.ad)
        self.device_flags._aconfig_flags = {}

    def test_get_value_aconfig_flag_missing_use_device_config(self) -> None:
        self.ad.adb.shell.return_value = b'foo'

        value = self.device_flags.get_value('sample', 'flag')

        self.assertEqual(value, 'foo')

    def test_get_value_aconfig_flag_read_write_use_device_config(self) -> None:
        sample_flag = aconfig_pb2.parsed_flag()
        sample_flag.state = aconfig_pb2.flag_state.ENABLED
        sample_flag.permission = aconfig_pb2.flag_permission.READ_WRITE
        self.device_flags._aconfig_flags['sample/flag'] = sample_flag
        self.ad.adb.shell.return_value = b'false'

        value = self.device_flags.get_value('sample', 'flag')

        self.assertEqual(value, 'false')

    def test_get_value_aconfig_flag_read_only_use_aconfig(self) -> None:
        sample_flag = aconfig_pb2.parsed_flag()
        sample_flag.state = aconfig_pb2.flag_state.ENABLED
        sample_flag.permission = aconfig_pb2.flag_permission.READ_ONLY
        self.device_flags._aconfig_flags['sample/flag'] = sample_flag
        self.ad.adb.shell.return_value = b'false'

        value = self.device_flags.get_value('sample', 'flag')

        self.assertEqual(value, 'true')

    def test_get_value_device_config_null_use_aconfig(self) -> None:
        sample_flag = aconfig_pb2.parsed_flag()
        sample_flag.state = aconfig_pb2.flag_state.ENABLED
        sample_flag.permission = aconfig_pb2.flag_permission.READ_WRITE
        self.device_flags._aconfig_flags['sample/flag'] = sample_flag
        self.ad.adb.shell.return_value = b'null'

        value = self.device_flags.get_value('sample', 'flag')

        self.assertEqual(value, 'true')

    def test_get_bool_with_valid_bool_value_true(self) -> None:
        self.ad.adb.shell.return_value = b'true'

        value = self.device_flags.get_bool('sample', 'flag')

        self.assertTrue(value)

    def test_get_bool_with_valid_bool_value_false(self) -> None:
        self.ad.adb.shell.return_value = b'false'

        value = self.device_flags.get_bool('sample', 'flag')

        self.assertFalse(value)

    def test_get_bool_with_invalid_bool_value(self) -> None:
        self.ad.adb.shell.return_value = b'foo'

        with self.assertRaisesRegex(ValueError, 'not a boolean'):
            self.device_flags.get_bool('sample', 'flag')

    def test_set_value_runs_correct_command(self) -> None:
        self.device_flags.set_value('sample', 'flag', 'value')

        self.ad.adb.shell.assert_called_with('device_config put sample flag value')

    def test_enable_runs_correct_command(self) -> None:
        self.ad.adb.shell.return_value = b'true'

        self.device_flags.enable('sample', 'flag')

        self.ad.adb.shell.assert_called_with('device_config put sample flag true')

    def test_disable_runs_correct_command(self) -> None:
        self.ad.adb.shell.return_value = b'true'

        self.device_flags.disable('sample', 'flag')

        self.ad.adb.shell.assert_called_with('device_config put sample flag false')

    def test_disable_fails_with_non_boolean_original_value(self) -> None:
        self.ad.adb.shell.return_value = b'foo'

        with self.assertRaisesRegex(ValueError, 'not a boolean'):
            self.device_flags.disable('sample', 'flag')


if __name__ == '__main__':
    unittest.main()
