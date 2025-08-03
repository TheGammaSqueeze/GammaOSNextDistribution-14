# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Server side Bluetooth range vs rate tests."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging

import common
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        BluetoothAdapterTests)
from six.moves import range


class BluetoothAdapterRvRTests(BluetoothAdapterTests):
    """Server side Bluetooth adapter audio test class."""

    def check_rssi_vs_attenuation(self, device, bt_attenuator):
        """
        @param device: Object representing the peer device
        @param bt_attenuator: Object representing the controllable variable attenuator

        @returns: Dict containing attenuation:rssi values. Empty on failure

        This function keeps measuring the rssi while increasing the attenuation.
        At some point the device discovery will fail, which is expected. So this
        failure is ignored and self.fails cleared.

        This should not be run in a batch
        """
        try:
            fixed_attenuation = bt_attenuator.get_minimal_total_attenuation()
            logging.debug('Fixed attentuation is %s', fixed_attenuation)
            final_attenuation = 100  # Maximum attenuation
            freq = 2427  # Frequency used to calculate total attenuation
            rssi_dict = {}
            for attn in range(fixed_attenuation, final_attenuation):
                logging.debug('Setting attenuation to %s', attn)
                bt_attenuator.set_total_attenuation(attn, freq)
                device.SetDiscoverable(True)
                try:
                    rssi = self.get_device_sample_rssi(device,
                                                       use_cached_value=False)
                except error.TestFail as e:
                    # test_discover_device might fail if RSSI is too low
                    logging.debug(
                            'get_device_sample rssi failed with %s.'
                            'This is expected if RSSI is too low', str(e))
                    self.fails = []
                    break
                logging.info('Total attenuation is %s RSSI is %s', attn, rssi)
                rssi_dict[attn] = rssi
            return rssi_dict
        except Exception as e:
            logging.error('Exception in check_rssi_vs_attenuation %s', str(e))
            return {}
        finally:
            bt_attenuator.set_variable_attenuation(0)
