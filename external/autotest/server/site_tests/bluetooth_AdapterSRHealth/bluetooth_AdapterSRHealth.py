# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
""" Server-side bluetooth adapter tests that involve suspend/resume with peers

paired and/or connected.

Single btpeer tests:
  - Reconnect on resume test
    - Classic HID
    - LE HID
    - A2DP
  - Wake from suspend test
    - Classic HID
    - LE HID
    - A2DP shouldn't wake from suspend
  - Suspend while discovering (discovering should pause and unpause)
  - Suspend while advertising (advertising should pause and unpause)

Multiple btpeer tests:
  - Reconnect on resume test
    - One classic HID, One LE HID
    - Two classic HID
    - Two LE HID
  - Wake from suspend test
    - Two classic HID
    - Two classic LE
"""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import time

from autotest_lib.client.cros.bluetooth.bluetooth_audio_test_data import A2DP
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        TABLET_MODELS, SUSPEND_POWER_DOWN_CHIPSETS,
        SUSPEND_RESET_IF_NO_PEER_CHIPSETS, SUSPEND_POWER_DOWN_MODELS)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_audio_tests import (
        BluetoothAdapterAudioTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        BluetoothAdapterQuickTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_quick_tests import (
        PROFILE_CONNECT_WAIT, SUSPEND_SEC, EXPECT_NO_WAKE_SUSPEND_SEC)
from six.moves import range

test_wrapper = BluetoothAdapterQuickTests.quick_test_test_decorator
batch_wrapper = BluetoothAdapterQuickTests.quick_test_batch_decorator

STRESS_ITERATIONS = 50


class bluetooth_AdapterSRHealth(BluetoothAdapterQuickTests,
                                BluetoothAdapterAudioTests):
    """Server side bluetooth adapter suspend resume test with peer."""

    def _test_keyboard_with_string(self, device):
        return (self.test_hid_device_created(device.address)
                and self.test_keyboard_input_from_trace(device, "simple_text"))

    def _test_mouse(self, device):
        return (self.test_hid_device_created(device.address)
                and self.test_mouse_left_click(device)
                and self.test_mouse_move_in_xy(device, -60, 100)
                and self.test_mouse_scroll_down(device, 70)
                and self.test_mouse_click_and_drag(device, 90, 30))

    # ---------------------------------------------------------------
    # Reconnect after suspend tests
    # ---------------------------------------------------------------

    def run_reconnect_device(self,
                             devtuples,
                             iterations=1,
                             auto_reconnect=False):
        """ Reconnects a device after suspend/resume.

        @param devtuples: array of tuples consisting of the following
                            * device_type: MOUSE, BLE_MOUSE, etc.
                            * device: meta object for peer device
                            * device_test: Optional; test function to run w/
                                           device (for example, mouse click)
        @params iterations: number of suspend/resume + reconnect iterations
        @params auto_reconnect: Expect host to automatically reconnect to peer
        """
        boot_id = self.host.get_boot_id()

        try:
            # Set up the device; any failures should assert
            for _, device, device_test in devtuples:
                self.assert_discover_and_pair(device)
                self.assert_on_fail(
                        self.test_device_set_discoverable(device, False))
                self.assert_on_fail(
                        self.test_connection_by_adapter(device.address))

                # Profile connection may not have completed yet and this will
                # race with a subsequent disconnection (due to suspend). Use the
                # device test to force profile connect or wait if no test was
                # given.
                if device_test is not None:
                    self.assert_on_fail(device_test(device))
                else:
                    time.sleep(PROFILE_CONNECT_WAIT)

            for it in range(iterations):
                logging.info('Running iteration {}/{} of suspend reconnection'.
                             format(it + 1, iterations))

                # Start the suspend process
                suspend = self.suspend_async(suspend_time=SUSPEND_SEC)
                start_time = self.bluetooth_facade.get_device_utc_time()

                # Trigger suspend, wait for regular resume, verify we can reconnect
                # and run device specific test
                self.test_suspend_and_wait_for_sleep(suspend,
                                                     sleep_timeout=SUSPEND_SEC)
                self.test_wait_for_resume(boot_id,
                                          suspend,
                                          resume_timeout=SUSPEND_SEC,
                                          test_start_time=start_time)

                # Only reconnect if we don't expect automatic reconnect.
                # Let the devices initiate connections before the DUT initiates
                # auto reconnections.
                # Complete reconnecting all peers before running device tests.
                # Otherwise, we may have a race between auto reconnection
                # from the dut and peer initiated connection. See b/177870286
                if not auto_reconnect:
                    for device_type, device, _ in devtuples:
                        if 'BLE' in device_type:
                            # LE can't reconnect without
                            # advertising/discoverable
                            self.test_device_set_discoverable(device, True)
                            # Make sure we're actually connected
                            self.test_device_is_connected(device.address)
                        else:
                            # Classic requires peer to initiate a connection to
                            # wake up the dut
                            self.test_connection_by_device(device)

                for _, device, device_test in devtuples:
                    if device_test is not None:
                        device_test(device)

        finally:
            for _, device, __ in devtuples:
                self.test_remove_pairing(device.address)

    @test_wrapper('Reconnect Classic HID', devices={'MOUSE': 1})
    def sr_reconnect_classic_hid(self):
        """ Reconnects a classic HID device after suspend/resume. """
        device_type = 'MOUSE'
        device = self.devices[device_type][0]
        self.run_reconnect_device([(device_type, device,
                                    self._test_mouse)])

    @test_wrapper('Reconnect LE HID', devices={'BLE_MOUSE': 1})
    def sr_reconnect_le_hid(self):
        """ Reconnects a LE HID device after suspend/resume. """
        device_type = 'BLE_MOUSE'
        device = self.devices[device_type][0]
        self.run_reconnect_device([(device_type, device,
                                    self._test_mouse)])

    # TODO(b/163143005) - Hana can't handle two concurrent HID connections
    @test_wrapper('Reconnect Multiple Classic HID',
                  devices={
                          'MOUSE': 1,
                          'KEYBOARD': 1
                  },
                  skip_models=['hana'])
    def sr_reconnect_multiple_classic_hid(self):
        """ Reconnects multiple classic HID devices after suspend/resume. """
        devices = [('MOUSE', self.devices['MOUSE'][0],
                    self._test_mouse),
                   ('KEYBOARD', self.devices['KEYBOARD'][0],
                    self._test_keyboard_with_string)]
        self.run_reconnect_device(devices)

    @test_wrapper('Reconnect Multiple LE HID',
                  devices={
                          'BLE_MOUSE': 1,
                          'BLE_KEYBOARD': 1
                  })
    def sr_reconnect_multiple_le_hid(self):
        """ Reconnects multiple LE HID devices after suspend/resume. """
        devices = [('BLE_MOUSE', self.devices['BLE_MOUSE'][0],
                    self._test_mouse),
                   ('BLE_KEYBOARD', self.devices['BLE_KEYBOARD'][0],
                    self._test_keyboard_with_string)]
        self.run_reconnect_device(devices)

    @test_wrapper('Reconnect one of each classic+LE HID',
                  devices={
                          'BLE_MOUSE': 1,
                          'KEYBOARD': 1
                  })
    def sr_reconnect_multiple_classic_le_hid(self):
        """ Reconnects one of each classic and LE HID devices after
            suspend/resume.
        """
        devices = [('BLE_MOUSE', self.devices['BLE_MOUSE'][0],
                    self._test_mouse),
                   ('KEYBOARD', self.devices['KEYBOARD'][0],
                    self._test_keyboard_with_string)]
        self.run_reconnect_device(devices)

    @test_wrapper('Reconnect Classic HID Stress Test', devices={'MOUSE': 1})
    def sr_reconnect_classic_hid_stress(self):
        """ Reconnects a classic HID device after suspend/resume. """
        device_type = 'MOUSE'
        device = self.devices[device_type][0]
        self.run_reconnect_device(
                [(device_type, device, self._test_mouse)],
                iterations=STRESS_ITERATIONS)

    @test_wrapper('Reconnect LE HID Stress Test', devices={'BLE_MOUSE': 1})
    def sr_reconnect_le_hid_stress(self):
        """ Reconnects a LE HID device after suspend/resume. """
        device_type = 'BLE_MOUSE'
        device = self.devices[device_type][0]
        self.run_reconnect_device(
                [(device_type, device, self._test_mouse)],
                iterations=STRESS_ITERATIONS)

    @test_wrapper('Reconnect A2DP',
                  devices={'BLUETOOTH_AUDIO': 1},
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_reconnect_a2dp(self):
        """ Reconnects an A2DP device after suspend/resume. """
        device_type = 'BLUETOOTH_AUDIO'
        device = self.devices[device_type][0]
        self.initialize_bluetooth_audio(device, A2DP)
        self.run_reconnect_device(
                [(device_type, device, self.test_device_a2dp_connected)],
                auto_reconnect=True)


    # TODO(b/151332866) - Bob can't wake from suspend due to wrong power/wakeup
    # TODO(b/150897528) - Dru is powered down during suspend, won't wake up
    @test_wrapper('Peer wakeup Classic HID',
                  devices={'MOUSE': 1},
                  skip_models=TABLET_MODELS + SUSPEND_POWER_DOWN_MODELS +
                  ['bob'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_classic_hid(self):
        """ Use classic HID device to wake from suspend. """
        device = self.devices['MOUSE'][0]
        self.run_peer_wakeup_device('MOUSE',
                                    device,
                                    device_test=self._test_mouse)

    # TODO(b/151332866) - Bob can't wake from suspend due to wrong power/wakeup
    # TODO(b/150897528) - Dru is powered down during suspend, won't wake up
    @test_wrapper('Peer wakeup LE HID',
                  devices={'BLE_MOUSE': 1},
                  skip_models=TABLET_MODELS + SUSPEND_POWER_DOWN_MODELS +
                  ['bob'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_le_hid(self):
        """ Use LE HID device to wake from suspend. """
        device = self.devices['BLE_MOUSE'][0]
        self.run_peer_wakeup_device('BLE_MOUSE',
                                    device,
                                    device_test=self._test_mouse)


    # TODO(b/151332866) - Bob can't wake from suspend due to wrong power/wakeup
    # TODO(b/150897528) - Dru is powered down during suspend, won't wake up
    @test_wrapper('Peer wakeup LE HID with reconnect LE HID',
                  devices={
                          'BLE_MOUSE': 1,
                          'BLE_KEYBOARD': 1
                  },
                  skip_models=TABLET_MODELS + SUSPEND_POWER_DOWN_MODELS +
                  ['bob'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_le_hid_reconnect_le_hid(self):
        """ Use LE HID device to wake from suspend. And reconnects a secondary
            LE HID device afterwards
        """
        device = self.devices['BLE_MOUSE'][0]
        device_reconnect = self.devices['BLE_KEYBOARD'][0]

        self.assert_discover_and_pair(device_reconnect)
        self.test_device_set_discoverable(device_reconnect, False)
        self.test_connection_by_adapter(device_reconnect.address)
        self._test_keyboard_with_string(device_reconnect)

        self.run_peer_wakeup_device('BLE_MOUSE',
                                    device,
                                    device_test=self._test_mouse,
                                    keep_paired=True)

        self.test_device_set_discoverable(device_reconnect, True)
        self.test_device_is_connected(device_reconnect.address)
        self._test_keyboard_with_string(device_reconnect)


    # TODO(b/151332866) - Bob can't wake from suspend due to wrong power/wakeup
    # TODO(b/150897528) - Dru is powered down during suspend, won't wake up
    @test_wrapper('Peer wakeup Classic HID',
                  devices={'MOUSE': 1},
                  skip_models=TABLET_MODELS + SUSPEND_POWER_DOWN_MODELS +
                  ['bob'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_classic_hid_stress(self):
        """ Use classic HID device to wake from suspend. """
        device = self.devices['MOUSE'][0]
        self.run_peer_wakeup_device('MOUSE',
                                    device,
                                    device_test=self._test_mouse,
                                    iterations=STRESS_ITERATIONS)

    # TODO(b/151332866) - Bob can't wake from suspend due to wrong power/wakeup
    # TODO(b/150897528) - Dru is powered down during suspend, won't wake up
    @test_wrapper('Peer wakeup LE HID',
                  devices={'BLE_MOUSE': 1},
                  skip_models=TABLET_MODELS + SUSPEND_POWER_DOWN_MODELS +
                  ['bob'],
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_le_hid_stress(self):
        """ Use LE HID device to wake from suspend. """
        device = self.devices['BLE_MOUSE'][0]
        self.run_peer_wakeup_device('BLE_MOUSE',
                                    device,
                                    device_test=self._test_mouse,
                                    iterations=STRESS_ITERATIONS)

    @test_wrapper('Peer wakeup with A2DP should fail',
                  devices={'BLUETOOTH_AUDIO': 1},
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS)
    def sr_peer_wake_a2dp_should_fail(self):
        """ Use A2DP device to wake from suspend and fail. """
        device_type = 'BLUETOOTH_AUDIO'
        device = self.devices[device_type][0]
        self.initialize_bluetooth_audio(device, A2DP)
        self.run_peer_wakeup_device(
                device_type,
                device,
                device_test=self.test_device_a2dp_connected,
                should_wake=False)

    # ---------------------------------------------------------------
    # Suspend while discovering and advertising
    # ---------------------------------------------------------------

    # TODO(b/150897528) - Scarlet Dru loses firmware around suspend
    @test_wrapper('Suspend while discovering',
                  devices={'BLE_MOUSE': 1},
                  skip_models=SUSPEND_POWER_DOWN_MODELS,
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS +
                  SUSPEND_RESET_IF_NO_PEER_CHIPSETS)
    def sr_while_discovering(self):
        """ Suspend while discovering. """
        device = self.devices['BLE_MOUSE'][0]
        boot_id = self.host.get_boot_id()

        self.test_device_set_discoverable(device, True)

        # Test discovery without setting discovery filter
        # ----------------------------------------------------------------------
        suspend = self.suspend_async(suspend_time=EXPECT_NO_WAKE_SUSPEND_SEC)
        start_time = self.bluetooth_facade.get_device_utc_time()

        # We don't pair to the peer device because we don't want it in the
        # allowlist. However, we want an advertising peer in this test
        # responding to the discovery requests.
        self.test_start_discovery()
        self.test_suspend_and_wait_for_sleep(suspend,
                                             sleep_timeout=SUSPEND_SEC)

        # If discovery events wake us early, we will raise and suspend.exitcode
        # will be non-zero
        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=EXPECT_NO_WAKE_SUSPEND_SEC,
                                  test_start_time=start_time)

        # Discovering should restore after suspend
        self.test_is_discovering()
        self.test_stop_discovery()

        # Test discovery with discovery filter set
        # ----------------------------------------------------------------------
        suspend = self.suspend_async(suspend_time=EXPECT_NO_WAKE_SUSPEND_SEC)
        start_time = self.bluetooth_facade.get_device_utc_time()

        self.test_set_discovery_filter({'Transport': 'auto'})
        self.test_start_discovery()
        self.test_suspend_and_wait_for_sleep(suspend,
                                             sleep_timeout=SUSPEND_SEC)

        # If discovery events wake us early, we will raise and suspend.exitcode
        # will be non-zero
        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=EXPECT_NO_WAKE_SUSPEND_SEC,
                                  test_start_time=start_time)

        # Discovering should restore after suspend
        self.test_is_discovering()
        self.test_stop_discovery()

    # TODO(b/150897528) - Scarlet Dru loses firmware around suspend
    @test_wrapper('Suspend while advertising',
                  devices={'MOUSE': 1},
                  skip_models=SUSPEND_POWER_DOWN_MODELS,
                  skip_chipsets=SUSPEND_POWER_DOWN_CHIPSETS +
                  SUSPEND_RESET_IF_NO_PEER_CHIPSETS)
    def sr_while_advertising(self):
        """ Suspend while advertising. """
        device = self.devices['MOUSE'][0]
        boot_id = self.host.get_boot_id()
        suspend = self.suspend_async(suspend_time=EXPECT_NO_WAKE_SUSPEND_SEC)
        start_time = self.bluetooth_facade.get_device_utc_time()

        self.test_discoverable()
        self.test_suspend_and_wait_for_sleep(suspend,
                                             sleep_timeout=SUSPEND_SEC)

        # Peer device should not be able to discover us in suspend
        self.test_discover_by_device_fails(device)

        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=EXPECT_NO_WAKE_SUSPEND_SEC,
                                  test_start_time=start_time)

        # Test that we are properly discoverable again
        self.test_is_discoverable()
        self.test_discover_by_device(device)

        self.test_nondiscoverable()

    # ---------------------------------------------------------------
    # Health checks
    # ---------------------------------------------------------------

    @test_wrapper('Suspend while powered off', devices={'MOUSE': 1})
    def sr_while_powered_off(self):
        """ Suspend while adapter is powered off. """
        device = self.devices['MOUSE'][0]
        boot_id = self.host.get_boot_id()
        suspend = self.suspend_async(suspend_time=SUSPEND_SEC)
        start_time = self.bluetooth_facade.get_device_utc_time()

        # Pair device so we have something to do in suspend
        self.assert_discover_and_pair(device)

        # Trigger power down and quickly suspend
        self.test_power_off_adapter()
        self.test_suspend_and_wait_for_sleep(suspend,
                                             sleep_timeout=SUSPEND_SEC)
        # Suspend and resume should succeed
        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=SUSPEND_SEC,
                                  test_start_time=start_time)

        # We should be able to power it back on
        self.test_power_on_adapter()

        # Test that we can reconnect to the device after powering back on
        self.test_connection_by_device(device)

    @batch_wrapper('SR with Peer Health')
    def sr_health_batch_run(self, num_iterations=1, test_name=None):
        """ Batch of suspend/resume peer health tests. """
        self.sr_reconnect_classic_hid()
        self.sr_reconnect_le_hid()
        self.sr_peer_wake_classic_hid()
        self.sr_peer_wake_le_hid()
        self.sr_while_discovering()
        self.sr_while_advertising()
        self.sr_reconnect_multiple_classic_hid()
        self.sr_reconnect_multiple_le_hid()
        self.sr_reconnect_multiple_classic_le_hid()

    def run_once(self,
                 host,
                 num_iterations=1,
                 args_dict=None,
                 test_name=None,
                 flag='Quick Health'):
        """Running Bluetooth adapter suspend resume with peer autotest.

        @param host: the DUT, usually a chromebook
        @param num_iterations: the number of times to execute the test
        @param test_name: the test to run or None for all tests
        @param flag: run tests with this flag (default: Quick Health)

        """

        # Initialize and run the test batch or the requested specific test
        self.quick_test_init(host,
                             use_btpeer=True,
                             flag=flag,
                             args_dict=args_dict)
        self.sr_health_batch_run(num_iterations, test_name)
        self.quick_test_cleanup()
