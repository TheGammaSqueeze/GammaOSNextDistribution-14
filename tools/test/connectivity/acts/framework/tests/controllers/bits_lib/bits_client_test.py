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

from datetime import datetime
import unittest

from acts.libs.proc import job
from acts.controllers.bits_lib import bits_client
from acts.controllers.bits_lib import bits_service_config
import mock

CONTROLLER_CONFIG_WITH_MONSOON = {
    'Monsoon': {'serial_num': 1234, 'monsoon_voltage': 4.2}
}

MONSOONED_CONFIG = bits_service_config.BitsServiceConfig(
    CONTROLLER_CONFIG_WITH_MONSOON, lvpm_monsoon_bin='lvpm.par')

CONTROLLER_CONFIG_WITHOUT_MONSOON = {}

NON_MONSOONED_CONFIG = bits_service_config.BitsServiceConfig(
    CONTROLLER_CONFIG_WITHOUT_MONSOON)

KIBBLES_CONFIG = bits_service_config.BitsServiceConfig(
    {
        'Kibbles': [{
            'board':     'board',
            'connector': 'connector',
            'serial':    'serial',
        }],
    },
    kibble_bin='bin',
    kibble_board_file='file.board',
    virtual_metrics_file='file.vm')


class BitsClientTest(unittest.TestCase):

    def setUp(self):
        super().setUp()
        self.mock_service = mock.Mock()
        self.mock_service.port = '42'

    @mock.patch('acts.libs.proc.job.run')
    def test_execute_generic_command(self, mock_run):
        mock_service = mock.Mock()
        mock_service.port = '1337'
        client = bits_client.BitsClient('bits.par', mock_service,
                                        service_config=KIBBLES_CONFIG)

        client.run_cmd('-i', '-am', '-not', '-a', '-teapot', timeout=12345)

        expected_final_command = ['bits.par',
                                  '--port',
                                  '1337',
                                  '-i',
                                  '-am',
                                  '-not',
                                  '-a',
                                  '-teapot']
        mock_run.assert_called_with(expected_final_command, timeout=12345)

    @mock.patch('acts.libs.proc.job.run')
    def test_start_collection__without_monsoon__does_not_disconnect_monsoon(
        self,
        mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=NON_MONSOONED_CONFIG)

        client.start_collection('collection')

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        non_expected_call = list(
            filter(lambda call: 'usb_disconnect' in call.args[0],
                   args_list))
        self.assertEqual(len(non_expected_call), 0,
                         'did not expect call with usb_disconnect')

    @mock.patch('acts.libs.proc.job.run')
    def test_start_collection__frecuency_arg_gets_populated(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.start_collection('collection', default_sampling_rate=12345)

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_calls = list(
            filter(lambda call: '--time' in call.args[0], args_list))
        self.assertEqual(len(expected_calls), 1, 'expected 1 calls with --time')
        self.assertIn('--default_sampling_rate', expected_calls[0][0][0])
        self.assertIn('12345', expected_calls[0][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_start_collection__sampling_rate_defaults_to_1000(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.start_collection('collection')

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_calls = list(
            filter(lambda call: '--time' in call.args[0], args_list))
        self.assertEqual(len(expected_calls), 1, 'expected 1 calls with --time')
        self.assertIn('--default_sampling_rate', expected_calls[0][0][0])
        self.assertIn('1000', expected_calls[0][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_stop_collection__usb_not_automanaged__does_not_connect_monsoon(
        self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.stop_collection('collection')

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        non_expected_call = list(
            filter(lambda call: 'usb_connect' in call.args[0], args_list))
        self.assertEqual(len(non_expected_call), 0,
                         'did not expect call with usb_connect')

    @mock.patch('acts.libs.proc.job.run')
    def test_export_ignores_dataseries_gaps(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.export('collection', '/path/a.7z.bits')

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_call = list(
            filter(
                lambda call: '--ignore_gaps' in call.args[0] and '--export' in
                             call.args[0], args_list))
        self.assertEqual(len(expected_call), 1,
                         'expected a call with --ignore_gaps and --export')
        self.assertIn('--ignore_gaps', expected_call[0].args[0])

    def test_export_path_must_end_in_bits_file_extension(self):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        self.assertRaisesRegex(
            bits_client.BitsClientError,
            r'collections can only be exported to files ending in .7z.bits',
            client.export, 'collection', '/path/')

    @mock.patch('acts.libs.proc.job.run')
    def test_export_as_csv(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)
        output_file = '/path/to/csv'
        collection = 'collection'

        client.export_as_csv([':mW', ':mV'], collection, output_file)

        mock_run.assert_called()
        cmd = mock_run.call_args_list[0].args[0]
        self.assertIn(collection, cmd)
        self.assertIn(output_file, cmd)
        self.assertIn(':mW,:mV', cmd)
        self.assertNotIn('--vm_file', cmd)
        self.assertNotIn('default', cmd)

    @mock.patch('acts.libs.proc.job.run')
    def test_export_as_csv_with_virtual_metrics_file(self, mock_run):
        output_file = '/path/to/csv'
        collection = 'collection'
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=KIBBLES_CONFIG)

        client.export_as_csv([':mW', ':mV'], collection, output_file)

        mock_run.assert_called()
        cmd = mock_run.call_args_list[0].args[0]
        self.assertIn(collection, cmd)
        self.assertIn(':mW,:mV', cmd)
        self.assertIn('--vm_file', cmd)
        self.assertIn('default', cmd)

    @mock.patch('acts.libs.proc.job.run')
    def test_add_markers(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.add_markers('collection', [(1, 'ein'),
                                          (2, 'zwei'),
                                          (3, 'drei')])

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_calls = list(
            filter(lambda call: '--log' in call.args[0], args_list))
        self.assertEqual(len(expected_calls), 3, 'expected 3 calls with --log')
        self.assertIn('--log_ts', expected_calls[0][0][0])
        self.assertIn('1', expected_calls[0][0][0])
        self.assertIn('ein', expected_calls[0][0][0])

        self.assertIn('--log_ts', expected_calls[1][0][0])
        self.assertIn('2', expected_calls[1][0][0])
        self.assertIn('zwei', expected_calls[1][0][0])

        self.assertIn('--log_ts', expected_calls[2][0][0])
        self.assertIn('3', expected_calls[2][0][0])
        self.assertIn('drei', expected_calls[2][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_add_markers_with_datetimes(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.add_markers('collection',
                           [(datetime.utcfromtimestamp(1), 'ein'),
                            (2e9, 'zwei'),
                            (datetime.utcfromtimestamp(3), 'drei')])

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_calls = list(
            filter(lambda call: '--log' in call.args[0], args_list))
        self.assertEqual(len(expected_calls), 3, 'expected 3 calls with --log')
        self.assertIn('--log_ts', expected_calls[0][0][0])
        self.assertIn(str(int(1e9)), expected_calls[0][0][0])
        self.assertIn('ein', expected_calls[0][0][0])

        self.assertIn('--log_ts', expected_calls[1][0][0])
        self.assertIn(str(int(2e9)), expected_calls[1][0][0])
        self.assertIn('zwei', expected_calls[1][0][0])

        self.assertIn('--log_ts', expected_calls[2][0][0])
        self.assertIn(str(int(3e9)), expected_calls[2][0][0])
        self.assertIn('drei', expected_calls[2][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_get_metrics(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.get_metrics('collection', 8888, 9999)

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_call = list(
            filter(lambda call: '--aggregates_yaml_path' in call.args[0],
                   args_list))
        self.assertEqual(len(expected_call), 1,
                         'expected a call with --aggregates_yaml_path')
        self.assertIn('8888', expected_call[0][0][0])
        self.assertIn('--ignore_gaps', expected_call[0][0][0])
        self.assertIn('--abs_stop_time', expected_call[0][0][0])
        self.assertIn('9999', expected_call[0][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_get_metrics_with_datetime_markers(self, mock_run):
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=MONSOONED_CONFIG)

        client.get_metrics('collection',
                           datetime.utcfromtimestamp(1),
                           datetime.utcfromtimestamp(2))

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_call = list(
            filter(lambda call: '--aggregates_yaml_path' in call.args[0],
                   args_list))
        self.assertEqual(len(expected_call), 1,
                         'expected a call with --aggregates_yaml_path')
        self.assertIn(str(int(1e9)), expected_call[0][0][0])
        self.assertIn('--ignore_gaps', expected_call[0][0][0])
        self.assertIn('--abs_stop_time', expected_call[0][0][0])
        self.assertIn(str(int(2e9)), expected_call[0][0][0])

    @mock.patch('acts.libs.proc.job.run')
    def test_get_metrics_with_virtual_metrics_file(self, mock_run):
        service_config = mock.Mock()
        service_config.has_virtual_metrics_file = True
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=service_config)

        client.get_metrics(8888, 9999)

        mock_run.assert_called()
        args_list = mock_run.call_args_list
        expected_call = list(
            filter(lambda call: '--aggregates_yaml_path' in call.args[0],
                   args_list))
        self.assertEqual(len(expected_call), 1,
                         'expected a call with --aggregates_yaml_path')
        self.assertIn('--vm_file', expected_call[0][0][0])
        self.assertIn('default', expected_call[0][0][0])

    @mock.patch('acts.libs.proc.job.run',
                return_value=job.Result(stdout=bytes('device', 'utf-8')))
    def test_list_devices(self, mock_run):
        service_config = mock.Mock()
        client = bits_client.BitsClient('bits.par', self.mock_service,
                                        service_config=service_config)

        result = client.list_devices()

        mock_run.assert_called()
        cmd = mock_run.call_args_list[0].args[0]
        self.assertIn('--list', cmd)
        self.assertIn('devices', cmd)
        self.assertEqual(result, 'device')


if __name__ == '__main__':
    unittest.main()
