# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
""" Bluetooth test that tests the Fast Pair scenarios."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from base64 import b64decode
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        UNSUPPORTED_BT_HW_FILTERING_CHIPSETS)
from autotest_lib.server import autotest

imported_password_util = True

try:
    # Importing this private util fails on public boards (e.g amd64-generic)
    from autotest_lib.client.common_lib.cros import password_util
except ImportError:
    imported_password_util = False
    logging.error('Failed to import password_util from autotest-private')

test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator


class bluetooth_FastPair(BluetoothAdapterQuickTests):
    """Fast Pair tests"""

    UI_TEST = 'bluetooth_FastPairUI'

    KEY_PEM_ARG_KEY = 'fast_pair_antispoofing_key_pem'
    ACCOUNT_KEY_ARG_KEY = 'fast_pair_account_key'
    USERNAME_ARG_KEY = 'fast_pair_username'
    PASSWORD_ARG_KEY = 'fast_pair_password'

    _key_pem = None
    _account_key = None
    _username = None
    _password = None

    def run_ui_test(self):
        """Runs the UI client test, which clicks through the Fast Pair UI"""
        client_at = autotest.Autotest(self.host)
        client_at.run_test(self.UI_TEST,
                           username=self._username,
                           password=self._password)
        client_at._check_client_test_result(self.host, self.UI_TEST)

    @test_wrapper('Fast Pair Initial Pairing',
                  devices={'BLE_FAST_PAIR': 1},
                  skip_chipsets=UNSUPPORTED_BT_HW_FILTERING_CHIPSETS)
    def fast_pair_initial_pairing_test(self):
        """Test the Fast Pair initial pairing scenario"""
        try:
            # Setup the Fast Pair device.
            device = self.devices['BLE_FAST_PAIR'][0]
            device.SetAntispoofingKeyPem(self._key_pem)

            # Toggling discoverable here ensures the device starts
            # advertising during this test.
            device.SetDiscoverable(False)
            device.SetDiscoverable(True)

            # Run UI test, which clicks through the pairing UI flow.
            self.run_ui_test()
            # Verify device is paired.
            return self.bluetooth_facade.device_is_paired(device.address)
        except Exception as e:
            logging.error('exception in fast_pair_initial_pairing_test %s',
                          str(e))
            return False

    @test_wrapper('Fast Pair Subsequent Pairing',
                  devices={'BLE_FAST_PAIR': 1},
                  skip_chipsets=UNSUPPORTED_BT_HW_FILTERING_CHIPSETS)
    def fast_pair_subsequent_pairing_test(self):
        """Test the Fast Pair subsequent pairing scenario"""
        try:
            # Setup the Fast Pair device.
            device = self.devices['BLE_FAST_PAIR'][0]
            device.SetAntispoofingKeyPem(None)
            device.AddAccountKey(self._account_key)

            # Toggling discoverable here ensures the device starts
            # advertising during this test.
            device.SetDiscoverable(False)
            device.SetDiscoverable(True)

            # Run UI test, which clicks through the pairing UI flow.
            self.run_ui_test()

            # Verify device is paired.
            return self.bluetooth_facade.device_is_paired(device.address)
        except Exception as e:
            logging.error('exception in fast_pair_subsequent_pairing_test %s',
                          str(e))
            return False

    def set_key_pem(self, args_dict):
        if imported_password_util:
            self._key_pem = b64decode(
                    password_util.get_fast_pair_anti_spoofing_key())

        elif args_dict is not None and self.KEY_PEM_ARG_KEY in args_dict:
            self._key_pem = b64decode(args_dict[self.KEY_PEM_ARG_KEY])

        if self._key_pem is None:
            raise error.TestError('Valid %s arg is missing' %
                                  self.KEY_PEM_ARG_KEY)

    def set_account_key(self, args_dict):
        if imported_password_util:
            self._account_key = b64decode(
                    password_util.get_fast_pair_account_key())

        elif args_dict is not None and self.ACCOUNT_KEY_ARG_KEY in args_dict:
            self._account_key = b64decode(args_dict[self.ACCOUNT_KEY_ARG_KEY])

        if self._account_key is None:
            raise error.TestError('Valid %s arg is missing' %
                                  self.ACCOUNT_KEY_ARG_KEY)

    def set_username(self, args_dict):
        if imported_password_util:
            self._username = (
                    password_util.get_fast_pair_user_credentials().username)

        elif args_dict is not None and self.USERNAME_ARG_KEY in args_dict:
            self._username = args_dict[self.USERNAME_ARG_KEY]

        if self._username is None:
            raise error.TestError('Valid %s arg is missing' %
                                  self.USERNAME_ARG_KEY)

    def set_password(self, args_dict):
        if imported_password_util:
            self._password = (
                    password_util.get_fast_pair_user_credentials().password)

        elif args_dict is not None and self.PASSWORD_ARG_KEY in args_dict:
            self._password = args_dict[self.PASSWORD_ARG_KEY]

        if self._password is None:
            raise error.TestError('Valid %s arg is missing' %
                                  self.PASSWORD_ARG_KEY)

    @batch_wrapper('Fast Pair')
    def fast_pair_batch_run(self, num_iterations=1, test_name=None):
        """ Batch of Fair Pair tests """
        self.fast_pair_initial_pairing_test()
        self.fast_pair_subsequent_pairing_test()

    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Running Fast Pair tests.

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of times to execute the test
        @param test_name: the test to run or None for all tests
        @param flag: run tests with this flag (default: Quick Health)

        """

        # First set required args
        self.set_key_pem(args_dict)
        self.set_account_key(args_dict)
        self.set_username(args_dict)
        self.set_password(args_dict)

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.fast_pair_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
