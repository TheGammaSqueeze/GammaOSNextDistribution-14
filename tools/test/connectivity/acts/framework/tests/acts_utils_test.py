#!/usr/bin/env python3
#
#   Copyright 2016 - The Android Open Source Project
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

import logging
import subprocess
import time
import unittest

import mock

from acts import utils
from acts import signals
from acts.controllers.adb_lib.error import AdbError
from acts.controllers.android_device import AndroidDevice
from acts.controllers.fuchsia_device import FuchsiaDevice
from acts.controllers.fuchsia_lib.sl4f import SL4F
from acts.controllers.fuchsia_lib.ssh import SSHConfig, SSHProvider, SSHResult
from acts.controllers.utils_lib.ssh.connection import SshConnection
from acts.libs.proc import job

PROVISIONED_STATE_GOOD = 1

MOCK_ENO1_IP_ADDRESSES = """100.127.110.79
2401:fa00:480:7a00:8d4f:85ff:cc5c:787e
2401:fa00:480:7a00:459:b993:fcbf:1419
fe80::c66d:3c75:2cec:1d72"""

MOCK_WLAN1_IP_ADDRESSES = ""

FUCHSIA_INTERFACES = {
    'id':
    '1',
    'result': [
        {
            'id': 1,
            'name': 'lo',
            'ipv4_addresses': [
                [127, 0, 0, 1],
            ],
            'ipv6_addresses': [
                [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1],
            ],
            'online': True,
            'mac': [0, 0, 0, 0, 0, 0],
        },
        {
            'id':
            2,
            'name':
            'eno1',
            'ipv4_addresses': [
                [100, 127, 110, 79],
            ],
            'ipv6_addresses': [
                [
                    254, 128, 0, 0, 0, 0, 0, 0, 198, 109, 60, 117, 44, 236, 29,
                    114
                ],
                [
                    36, 1, 250, 0, 4, 128, 122, 0, 141, 79, 133, 255, 204, 92,
                    120, 126
                ],
                [
                    36, 1, 250, 0, 4, 128, 122, 0, 4, 89, 185, 147, 252, 191,
                    20, 25
                ],
            ],
            'online':
            True,
            'mac': [0, 224, 76, 5, 76, 229],
        },
        {
            'id':
            3,
            'name':
            'wlanxc0',
            'ipv4_addresses': [],
            'ipv6_addresses': [
                [
                    254, 128, 0, 0, 0, 0, 0, 0, 96, 255, 93, 96, 52, 253, 253,
                    243
                ],
                [
                    254, 128, 0, 0, 0, 0, 0, 0, 70, 7, 11, 255, 254, 118, 126,
                    192
                ],
            ],
            'online':
            False,
            'mac': [68, 7, 11, 118, 126, 192],
        },
    ],
    'error':
    None,
}

CORRECT_FULL_IP_LIST = {
    'ipv4_private': [],
    'ipv4_public': ['100.127.110.79'],
    'ipv6_link_local': ['fe80::c66d:3c75:2cec:1d72'],
    'ipv6_private_local': [],
    'ipv6_public': [
        '2401:fa00:480:7a00:8d4f:85ff:cc5c:787e',
        '2401:fa00:480:7a00:459:b993:fcbf:1419'
    ]
}

CORRECT_EMPTY_IP_LIST = {
    'ipv4_private': [],
    'ipv4_public': [],
    'ipv6_link_local': [],
    'ipv6_private_local': [],
    'ipv6_public': []
}


class ByPassSetupWizardTests(unittest.TestCase):
    """This test class for unit testing acts.utils.bypass_setup_wizard."""

    def test_start_standing_subproc(self):
        with self.assertRaisesRegex(utils.ActsUtilsError,
                                    'Process .* has terminated'):
            utils.start_standing_subprocess('sleep 0', check_health_delay=0.1)

    def test_stop_standing_subproc(self):
        p = utils.start_standing_subprocess('sleep 0')
        time.sleep(0.1)
        with self.assertRaisesRegex(utils.ActsUtilsError,
                                    'Process .* has terminated'):
            utils.stop_standing_subprocess(p)

    @mock.patch('time.sleep')
    def test_bypass_setup_wizard_no_complications(self, _):
        ad = mock.Mock()
        ad.adb.shell.side_effect = [
            # Return value for SetupWizardExitActivity
            BypassSetupWizardReturn.NO_COMPLICATIONS,
            # Return value for device_provisioned
            PROVISIONED_STATE_GOOD,
        ]
        ad.adb.return_state = BypassSetupWizardReturn.NO_COMPLICATIONS
        self.assertTrue(utils.bypass_setup_wizard(ad))
        self.assertFalse(
            ad.adb.root_adb.called,
            'The root command should not be called if there are no '
            'complications.')

    @mock.patch('time.sleep')
    def test_bypass_setup_wizard_unrecognized_error(self, _):
        ad = mock.Mock()
        ad.adb.shell.side_effect = [
            # Return value for SetupWizardExitActivity
            BypassSetupWizardReturn.UNRECOGNIZED_ERR,
            # Return value for device_provisioned
            PROVISIONED_STATE_GOOD,
        ]
        with self.assertRaises(AdbError):
            utils.bypass_setup_wizard(ad)
        self.assertFalse(
            ad.adb.root_adb.called,
            'The root command should not be called if we do not have a '
            'codepath for recovering from the failure.')

    @mock.patch('time.sleep')
    def test_bypass_setup_wizard_need_root_access(self, _):
        ad = mock.Mock()
        ad.adb.shell.side_effect = [
            # Return value for SetupWizardExitActivity
            BypassSetupWizardReturn.ROOT_ADB_NO_COMP,
            # Return value for rooting the device
            BypassSetupWizardReturn.NO_COMPLICATIONS,
            # Return value for device_provisioned
            PROVISIONED_STATE_GOOD
        ]

        utils.bypass_setup_wizard(ad)

        self.assertTrue(
            ad.adb.root_adb_called,
            'The command required root access, but the device was never '
            'rooted.')

    @mock.patch('time.sleep')
    def test_bypass_setup_wizard_need_root_already_skipped(self, _):
        ad = mock.Mock()
        ad.adb.shell.side_effect = [
            # Return value for SetupWizardExitActivity
            BypassSetupWizardReturn.ROOT_ADB_SKIPPED,
            # Return value for SetupWizardExitActivity after root
            BypassSetupWizardReturn.ALREADY_BYPASSED,
            # Return value for device_provisioned
            PROVISIONED_STATE_GOOD
        ]
        self.assertTrue(utils.bypass_setup_wizard(ad))
        self.assertTrue(ad.adb.root_adb_called)

    @mock.patch('time.sleep')
    def test_bypass_setup_wizard_root_access_still_fails(self, _):
        ad = mock.Mock()
        ad.adb.shell.side_effect = [
            # Return value for SetupWizardExitActivity
            BypassSetupWizardReturn.ROOT_ADB_FAILS,
            # Return value for SetupWizardExitActivity after root
            BypassSetupWizardReturn.UNRECOGNIZED_ERR,
            # Return value for device_provisioned
            PROVISIONED_STATE_GOOD
        ]

        with self.assertRaises(AdbError):
            utils.bypass_setup_wizard(ad)
        self.assertTrue(ad.adb.root_adb_called)


class BypassSetupWizardReturn:
    # No complications. Bypass works the first time without issues.
    NO_COMPLICATIONS = (
        'Starting: Intent { cmp=com.google.android.setupwizard/'
        '.SetupWizardExitActivity }')

    # Fail with doesn't need to be skipped/was skipped already.
    ALREADY_BYPASSED = AdbError('', 'ADB_CMD_OUTPUT:0', 'Error type 3\n'
                                'Error: Activity class', 1)
    # Fail with different error.
    UNRECOGNIZED_ERR = AdbError('', 'ADB_CMD_OUTPUT:0', 'Error type 4\n'
                                'Error: Activity class', 0)
    # Fail, get root access, then no complications arise.
    ROOT_ADB_NO_COMP = AdbError(
        '', 'ADB_CMD_OUTPUT:255', 'Security exception: Permission Denial: '
        'starting Intent { flg=0x10000000 '
        'cmp=com.google.android.setupwizard/'
        '.SetupWizardExitActivity } from null '
        '(pid=5045, uid=2000) not exported from uid '
        '10000', 0)
    # Even with root access, the bypass setup wizard doesn't need to be skipped.
    ROOT_ADB_SKIPPED = AdbError(
        '', 'ADB_CMD_OUTPUT:255', 'Security exception: Permission Denial: '
        'starting Intent { flg=0x10000000 '
        'cmp=com.google.android.setupwizard/'
        '.SetupWizardExitActivity } from null '
        '(pid=5045, uid=2000) not exported from '
        'uid 10000', 0)
    # Even with root access, the bypass setup wizard fails
    ROOT_ADB_FAILS = AdbError(
        '', 'ADB_CMD_OUTPUT:255',
        'Security exception: Permission Denial: starting Intent { '
        'flg=0x10000000 cmp=com.google.android.setupwizard/'
        '.SetupWizardExitActivity } from null (pid=5045, uid=2000) not '
        'exported from uid 10000', 0)


class ConcurrentActionsTest(unittest.TestCase):
    """Tests acts.utils.run_concurrent_actions and related functions."""

    @staticmethod
    def function_returns_passed_in_arg(arg):
        return arg

    @staticmethod
    def function_raises_passed_in_exception_type(exception_type):
        raise exception_type

    def test_run_concurrent_actions_no_raise_returns_proper_return_values(
            self):
        """Tests run_concurrent_actions_no_raise returns in the correct order.

        Each function passed into run_concurrent_actions_no_raise returns the
        values returned from each individual callable in the order passed in.
        """
        ret_values = utils.run_concurrent_actions_no_raise(
            lambda: self.function_returns_passed_in_arg(
                'ARG1'), lambda: self.function_returns_passed_in_arg('ARG2'),
            lambda: self.function_returns_passed_in_arg('ARG3'))

        self.assertEqual(len(ret_values), 3)
        self.assertEqual(ret_values[0], 'ARG1')
        self.assertEqual(ret_values[1], 'ARG2')
        self.assertEqual(ret_values[2], 'ARG3')

    def test_run_concurrent_actions_no_raise_returns_raised_exceptions(self):
        """Tests run_concurrent_actions_no_raise returns raised exceptions.

        Instead of allowing raised exceptions to be raised in the main thread,
        this function should capture the exception and return them in the slot
        the return value should have been returned in.
        """
        ret_values = utils.run_concurrent_actions_no_raise(
            lambda: self.function_raises_passed_in_exception_type(IndexError),
            lambda: self.function_raises_passed_in_exception_type(KeyError))

        self.assertEqual(len(ret_values), 2)
        self.assertEqual(ret_values[0].__class__, IndexError)
        self.assertEqual(ret_values[1].__class__, KeyError)

    def test_run_concurrent_actions_returns_proper_return_values(self):
        """Tests run_concurrent_actions returns in the correct order.

        Each function passed into run_concurrent_actions returns the values
        returned from each individual callable in the order passed in.
        """

        ret_values = utils.run_concurrent_actions(
            lambda: self.function_returns_passed_in_arg(
                'ARG1'), lambda: self.function_returns_passed_in_arg('ARG2'),
            lambda: self.function_returns_passed_in_arg('ARG3'))

        self.assertEqual(len(ret_values), 3)
        self.assertEqual(ret_values[0], 'ARG1')
        self.assertEqual(ret_values[1], 'ARG2')
        self.assertEqual(ret_values[2], 'ARG3')

    def test_run_concurrent_actions_raises_exceptions(self):
        """Tests run_concurrent_actions raises exceptions from given actions."""
        with self.assertRaises(KeyError):
            utils.run_concurrent_actions(
                lambda: self.function_returns_passed_in_arg('ARG1'), lambda:
                self.function_raises_passed_in_exception_type(KeyError))

    def test_test_concurrent_actions_raises_non_test_failure(self):
        """Tests test_concurrent_actions raises the given exception."""
        with self.assertRaises(KeyError):
            utils.test_concurrent_actions(
                lambda: self.function_raises_passed_in_exception_type(KeyError
                                                                      ),
                failure_exceptions=signals.TestFailure)

    def test_test_concurrent_actions_raises_test_failure(self):
        """Tests test_concurrent_actions raises the given exception."""
        with self.assertRaises(signals.TestFailure):
            utils.test_concurrent_actions(
                lambda: self.function_raises_passed_in_exception_type(KeyError
                                                                      ),
                failure_exceptions=KeyError)


class SuppressLogOutputTest(unittest.TestCase):
    """Tests SuppressLogOutput"""

    def test_suppress_log_output(self):
        """Tests that the SuppressLogOutput context manager removes handlers
        of the specified levels upon entry and re-adds handlers upon exit.
        """
        handlers = [
            logging.NullHandler(level=lvl)
            for lvl in (logging.DEBUG, logging.INFO, logging.ERROR)
        ]
        log = logging.getLogger('test_log')
        for handler in handlers:
            log.addHandler(handler)
        with utils.SuppressLogOutput(log, [logging.INFO, logging.ERROR]):
            self.assertTrue(
                any(handler.level == logging.DEBUG
                    for handler in log.handlers))
            self.assertFalse(
                any(handler.level in (logging.INFO, logging.ERROR)
                    for handler in log.handlers))
        self.assertCountEqual(handlers, log.handlers)


class IpAddressUtilTest(unittest.TestCase):
    def test_positive_ipv4_normal_address(self):
        ip_address = "192.168.1.123"
        self.assertTrue(utils.is_valid_ipv4_address(ip_address))

    def test_positive_ipv4_any_address(self):
        ip_address = "0.0.0.0"
        self.assertTrue(utils.is_valid_ipv4_address(ip_address))

    def test_positive_ipv4_broadcast(self):
        ip_address = "255.255.255.0"
        self.assertTrue(utils.is_valid_ipv4_address(ip_address))

    def test_negative_ipv4_with_ipv6_address(self):
        ip_address = "fe80::f693:9fff:fef4:1ac"
        self.assertFalse(utils.is_valid_ipv4_address(ip_address))

    def test_negative_ipv4_with_invalid_string(self):
        ip_address = "fdsafdsafdsafdsf"
        self.assertFalse(utils.is_valid_ipv4_address(ip_address))

    def test_negative_ipv4_with_invalid_number(self):
        ip_address = "192.168.500.123"
        self.assertFalse(utils.is_valid_ipv4_address(ip_address))

    def test_positive_ipv6(self):
        ip_address = 'fe80::f693:9fff:fef4:1ac'
        self.assertTrue(utils.is_valid_ipv6_address(ip_address))

    def test_positive_ipv6_link_local(self):
        ip_address = 'fe80::'
        self.assertTrue(utils.is_valid_ipv6_address(ip_address))

    def test_negative_ipv6_with_ipv4_address(self):
        ip_address = '192.168.1.123'
        self.assertFalse(utils.is_valid_ipv6_address(ip_address))

    def test_negative_ipv6_invalid_characters(self):
        ip_address = 'fe80:jkyr:f693:9fff:fef4:1ac'
        self.assertFalse(utils.is_valid_ipv6_address(ip_address))

    def test_negative_ipv6_invalid_string(self):
        ip_address = 'fdsafdsafdsafdsf'
        self.assertFalse(utils.is_valid_ipv6_address(ip_address))

    @mock.patch('acts.libs.proc.job.run')
    def test_local_get_interface_ip_addresses_full(self, job_mock):
        job_mock.side_effect = [
            job.Result(stdout=bytes(MOCK_ENO1_IP_ADDRESSES, 'utf-8'),
                       encoding='utf-8'),
        ]
        self.assertEqual(utils.get_interface_ip_addresses(job, 'eno1'),
                         CORRECT_FULL_IP_LIST)

    @mock.patch('acts.libs.proc.job.run')
    def test_local_get_interface_ip_addresses_empty(self, job_mock):
        job_mock.side_effect = [
            job.Result(stdout=bytes(MOCK_WLAN1_IP_ADDRESSES, 'utf-8'),
                       encoding='utf-8'),
        ]
        self.assertEqual(utils.get_interface_ip_addresses(job, 'wlan1'),
                         CORRECT_EMPTY_IP_LIST)

    @mock.patch('acts.controllers.utils_lib.ssh.connection.SshConnection.run')
    def test_ssh_get_interface_ip_addresses_full(self, ssh_mock):
        ssh_mock.side_effect = [
            job.Result(stdout=bytes(MOCK_ENO1_IP_ADDRESSES, 'utf-8'),
                       encoding='utf-8'),
        ]
        self.assertEqual(
            utils.get_interface_ip_addresses(SshConnection('mock_settings'),
                                             'eno1'), CORRECT_FULL_IP_LIST)

    @mock.patch('acts.controllers.utils_lib.ssh.connection.SshConnection.run')
    def test_ssh_get_interface_ip_addresses_empty(self, ssh_mock):
        ssh_mock.side_effect = [
            job.Result(stdout=bytes(MOCK_WLAN1_IP_ADDRESSES, 'utf-8'),
                       encoding='utf-8'),
        ]
        self.assertEqual(
            utils.get_interface_ip_addresses(SshConnection('mock_settings'),
                                             'wlan1'), CORRECT_EMPTY_IP_LIST)

    @mock.patch('acts.controllers.adb.AdbProxy')
    @mock.patch.object(AndroidDevice, 'is_bootloader', return_value=True)
    def test_android_get_interface_ip_addresses_full(self, is_bootloader,
                                                     adb_mock):
        adb_mock().shell.side_effect = [
            MOCK_ENO1_IP_ADDRESSES,
        ]
        self.assertEqual(
            utils.get_interface_ip_addresses(AndroidDevice(), 'eno1'),
            CORRECT_FULL_IP_LIST)

    @mock.patch('acts.controllers.adb.AdbProxy')
    @mock.patch.object(AndroidDevice, 'is_bootloader', return_value=True)
    def test_android_get_interface_ip_addresses_empty(self, is_bootloader,
                                                      adb_mock):
        adb_mock().shell.side_effect = [
            MOCK_WLAN1_IP_ADDRESSES,
        ]
        self.assertEqual(
            utils.get_interface_ip_addresses(AndroidDevice(), 'wlan1'),
            CORRECT_EMPTY_IP_LIST)

    @mock.patch('acts.controllers.fuchsia_device.FuchsiaDevice.sl4f',
                new_callable=mock.PropertyMock)
    @mock.patch('acts.controllers.fuchsia_device.FuchsiaDevice.ffx',
                new_callable=mock.PropertyMock)
    @mock.patch('acts.controllers.fuchsia_lib.utils_lib.wait_for_port')
    @mock.patch('acts.controllers.fuchsia_lib.ssh.SSHProvider.run')
    @mock.patch(
        'acts.controllers.fuchsia_lib.sl4f.SL4F._verify_sl4f_connection')
    @mock.patch('acts.controllers.fuchsia_device.'
                'FuchsiaDevice._set_control_path_config')
    @mock.patch('acts.controllers.'
                'fuchsia_lib.netstack.netstack_lib.'
                'FuchsiaNetstackLib.netstackListInterfaces')
    def test_fuchsia_get_interface_ip_addresses_full(
            self, list_interfaces_mock, control_path_mock,
            verify_sl4f_conn_mock, ssh_run_mock, wait_for_port_mock,
            ffx_mock, sl4f_mock):
        # Configure the log path which is required by ACTS logger.
        logging.log_path = '/tmp/unit_test_garbage'

        ssh = SSHProvider(SSHConfig('192.168.1.1', 22, '/dev/null'))
        ssh_run_mock.return_value = SSHResult(
            subprocess.CompletedProcess([], 0, stdout=b'', stderr=b''))

        # Don't try to wait for the SL4F server to start; it's not being used.
        wait_for_port_mock.return_value = None

        sl4f_mock.return_value = SL4F(ssh, 'http://192.168.1.1:80')
        verify_sl4f_conn_mock.return_value = None

        list_interfaces_mock.return_value = FUCHSIA_INTERFACES
        self.assertEqual(
            utils.get_interface_ip_addresses(
                FuchsiaDevice({'ip': '192.168.1.1'}), 'eno1'),
            CORRECT_FULL_IP_LIST)

    @mock.patch('acts.controllers.fuchsia_device.FuchsiaDevice.sl4f',
                new_callable=mock.PropertyMock)
    @mock.patch('acts.controllers.fuchsia_device.FuchsiaDevice.ffx',
                new_callable=mock.PropertyMock)
    @mock.patch('acts.controllers.fuchsia_lib.utils_lib.wait_for_port')
    @mock.patch('acts.controllers.fuchsia_lib.ssh.SSHProvider.run')
    @mock.patch(
        'acts.controllers.fuchsia_lib.sl4f.SL4F._verify_sl4f_connection')
    @mock.patch('acts.controllers.fuchsia_device.'
                'FuchsiaDevice._set_control_path_config')
    @mock.patch('acts.controllers.'
                'fuchsia_lib.netstack.netstack_lib.'
                'FuchsiaNetstackLib.netstackListInterfaces')
    def test_fuchsia_get_interface_ip_addresses_empty(
            self, list_interfaces_mock, control_path_mock,
            verify_sl4f_conn_mock, ssh_run_mock, wait_for_port_mock,
            ffx_mock, sl4f_mock):
        # Configure the log path which is required by ACTS logger.
        logging.log_path = '/tmp/unit_test_garbage'

        ssh = SSHProvider(SSHConfig('192.168.1.1', 22, '/dev/null'))
        ssh_run_mock.return_value = SSHResult(
            subprocess.CompletedProcess([], 0, stdout=b'', stderr=b''))

        # Don't try to wait for the SL4F server to start; it's not being used.
        wait_for_port_mock.return_value = None

        sl4f_mock.return_value = SL4F(ssh, 'http://192.168.1.1:80')
        verify_sl4f_conn_mock.return_value = None

        list_interfaces_mock.return_value = FUCHSIA_INTERFACES
        self.assertEqual(
            utils.get_interface_ip_addresses(
                FuchsiaDevice({'ip': '192.168.1.1'}), 'wlan1'),
            CORRECT_EMPTY_IP_LIST)


class GetDeviceTest(unittest.TestCase):
    class TestDevice:
        def __init__(self, id, device_type=None) -> None:
            self.id = id
            if device_type:
                self.device_type = device_type

    def test_get_device_none(self):
        devices = []
        self.assertRaises(ValueError, utils.get_device, devices, 'DUT')

    def test_get_device_default_one(self):
        devices = [self.TestDevice(0)]
        self.assertEqual(utils.get_device(devices, 'DUT').id, 0)

    def test_get_device_default_many(self):
        devices = [self.TestDevice(0), self.TestDevice(1)]
        self.assertEqual(utils.get_device(devices, 'DUT').id, 0)

    def test_get_device_specified_one(self):
        devices = [self.TestDevice(0), self.TestDevice(1, 'DUT')]
        self.assertEqual(utils.get_device(devices, 'DUT').id, 1)

    def test_get_device_specified_many(self):
        devices = [self.TestDevice(0, 'DUT'), self.TestDevice(1, 'DUT')]
        self.assertRaises(ValueError, utils.get_device, devices, 'DUT')


if __name__ == '__main__':
    unittest.main()
