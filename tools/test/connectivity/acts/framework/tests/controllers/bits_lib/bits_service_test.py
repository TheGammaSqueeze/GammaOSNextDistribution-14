#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import unittest

from acts.controllers.bits_lib import bits_service
from acts.controllers.bits_lib import bits_service_config
import mock

SERVICE_CONFIG = bits_service_config.BitsServiceConfig(
    {'Monsoon': {'serial_num': 538141, 'monsoon_voltage': 4.2}},
    hvpm_monsoon_bin='hvpm.par')


@mock.patch('acts.controllers.bits_lib.bits_service.atexit')
@mock.patch('builtins.open')
class BitsServiceTest(unittest.TestCase):
    def test_output_log_opens_on_creation(self, mock_open, *_):
        bits_service.BitsService(SERVICE_CONFIG, 'binary', 'log_path')

        mock_open.assert_called_with('log_path', 'w')

    @mock.patch.object(bits_service.BitsService, '_write_extra_debug_logs')
    @mock.patch('acts.libs.proc.job.run')
    def test_output_log_gets_closed_on_cleanup(self, _, __, mock_open, *___):
        mock_log = mock.Mock()
        mock_open.return_value = mock_log
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')
        service._cleanup()

        mock_log.close.assert_called_with()

    @mock.patch('acts.libs.proc.job.run')
    def test_monsoons_usb_gets_connected_on_cleanup(self, mock_run, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')

        service._cleanup()

        mock_run.assert_called()
        self.assertIn('--usbpassthrough', mock_run.call_args[0][0])
        self.assertIn('on', mock_run.call_args[0][0])

    def test_service_can_not_be_started_twice(self, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')
        service.service_state = bits_service.BitsServiceStates.STARTED
        with self.assertRaises(bits_service.BitsServiceError):
            service.start()

    def test_service_can_not_be_stoped_twice(self, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')
        service.service_state = bits_service.BitsServiceStates.STOPPED
        with self.assertRaises(bits_service.BitsServiceError):
            service.stop()

    def test_stopped_service_can_not_be_started(self, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')
        service.service_state = bits_service.BitsServiceStates.STOPPED
        with self.assertRaises(bits_service.BitsServiceError):
            service.start()

    def test_service_output_changes_service_reported_state(self, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')
        self.assertEqual(bits_service.BitsServiceStates.NOT_STARTED,
                         service.service_state)

        service.port = '1234'
        service._output_callback('Started server!')

        self.assertEqual(bits_service.BitsServiceStates.STARTED,
                         service.service_state)

    def test_service_output_defines_port(self, *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')

        service._output_callback('Server listening on ...:6174.')

        self.assertIsNotNone(service.port)

    @mock.patch('acts.context.get_current_context')
    @mock.patch('acts.libs.proc.process.Process')
    def test_top_level_call_is_timeout_if_timeout_is_defined(self, mock_process,
                                                             *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path',
                                           timeout=42)

        def side_effect(*_, **__):
            service.service_state = bits_service.BitsServiceStates.STARTED
            return mock.Mock()

        mock_process.side_effect = side_effect

        service.start()

        args, kwargs = mock_process.call_args
        self.assertEqual('timeout', args[0][0])
        self.assertEqual('--signal=SIGTERM', args[0][1])
        self.assertEqual('--kill-after=60', args[0][2])
        self.assertEqual('42', args[0][3])
        self.assertEqual('binary', args[0][4])

    @mock.patch.object(bits_service.BitsService, '_write_extra_debug_logs')
    @mock.patch('acts.context.get_current_context')
    @mock.patch('acts.libs.proc.process.Process')
    def test_top_level_call_is_binary_if_timeout_is_not_defined(self,
                                                                mock_process,
                                                                *_):
        service = bits_service.BitsService(SERVICE_CONFIG, 'binary',
                                           'log_path')

        def side_effect(*_, **__):
            service.service_state = bits_service.BitsServiceStates.STARTED
            return mock.Mock()

        mock_process.side_effect = side_effect

        service.start()

        args, kwargs = mock_process.call_args
        self.assertEqual('binary', args[0][0])


if __name__ == '__main__':
    unittest.main()
