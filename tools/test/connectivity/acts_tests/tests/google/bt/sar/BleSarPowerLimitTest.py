#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import os
import time
from acts import utils

import acts_contrib.test_utils.bt.bt_test_utils as bt_utils
import acts_contrib.test_utils.wifi.wifi_performance_test_utils as wifi_utils
from acts_contrib.test_utils.bt.ble_performance_test_utils import ble_gatt_disconnection
from acts_contrib.test_utils.bt.ble_performance_test_utils import ble_coc_connection
from acts_contrib.test_utils.bt.bt_constants import l2cap_max_inactivity_delay_after_disconnect
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test
from acts_contrib.test_utils.bt.BtSarBaseTest import BtSarBaseTest

FIXED_ATTENUATION = 36


class BleSarPowerLimitTest(BtSarBaseTest):
    """Class to define BLE SAR power cap tests.

    This class defines tests that iterate over and force different
    states in the BLE SAR table and calculates the TX power at the
    antenna port.
    """
    def setup_class(self):
        super().setup_class()
        self.bt_device = self.android_devices[1]
        return setup_multiple_devices_for_bt_test(self.android_devices)

    def setup_test(self):

        #self.dut.droid.bluetoothFactoryReset()
        #bt_utils.enable_bluetooth(self.dut.droid, self.bt_device.ed)

        #Reset SAR test result to 0 before every test
        self.sar_test_result.metric_value = 0

        # To prevent default file from being overwritten
        self.dut.adb.shell('cp {} {}'.format(self.power_file_paths[0],
                                             self.power_file_paths[1]))

        self.sar_file_path = self.power_file_paths[1]
        self.sar_file_name = os.path.basename(self.power_file_paths[1])
        self.bt_sar_df = self.read_sar_table(self.dut)

        utils.set_location_service(self.bt_device, True)
        utils.set_location_service(self.dut, True)

        self.attenuator.set_atten(FIXED_ATTENUATION)
        self.log.info('Attenuation set to {} dB'.format(FIXED_ATTENUATION))

        # BokehFigure object
        self.plot = wifi_utils.BokehFigure(title='{}'.format(
            self.current_test_name),
                                           x_label='Scenarios',
                                           primary_y_label='TX power(dBm)')

    def teardown_test(self):
        # Deleting the table
        self.dut.adb.shell('rm {}'.format(self.power_file_paths[1]))
        self.attenuator.set_atten(self.atten_min)
        self.log.info('Attenuation set to {} dB'.format(self.atten_min))
        ble_gatt_disconnection(self.bt_device, self.bluetooth_gatt,
                               self.gatt_callback)
        self.bt_device.droid.bluetoothSocketConnStop()
        self.dut.droid.bluetoothSocketConnStop()
        time.sleep(l2cap_max_inactivity_delay_after_disconnect)
        self.dut.droid.bluetoothFactoryReset()
        bt_utils.disable_bluetooth(self.dut.droid)

    def teardown_class(self):
        bt_utils.disable_bluetooth(self.dut.droid)

    def test_ble_sar_table(self):
        """ Test for BLE SAR default table

        BLE SAR table Test iterates over BT SAR default table and forces signal states, by
        measuring ble RSSI and power level for each state and processes the results of
        sweep_table and computes BLE TX power and parses the processed table with
        computed BLE TX power values to return pass or fail
        """
        # Establish CoC BLE connection
        self.status, self.gatt_callback, self.gatt_server, self.bluetooth_gatt, \
        self.client_conn_id = ble_coc_connection(self.bt_device, self.dut)
        sar_df = self.sweep_table(self.dut,
                                  self.bt_device,
                                  self.client_conn_id,
                                  self.gatt_server,
                                  self.gatt_callback,
                                  isBLE=True)
        sar_df = self.process_table(sar_df)
        self.process_results(sar_df, type='BLE')

    def test_ble_sar_custom_table(self):
        """ Test for BLE SAR custom table

        BLE SAR custom table Test Iterates over BT SAR custom table and forces signal states, by
        measuring ble RSSI and power level for each state and processes the results of
        sweep_table and computes BLE TX power and parses the processed table with
        computed BLE TX power values to return pass or fail
        """

        self.push_table(self.dut, self.custom_sar_path)
        setup_multiple_devices_for_bt_test(self.android_devices)

        # Establish CoC BLE connection
        self.status, self.gatt_callback, self.gatt_server, self.bluetooth_gatt, \
        self.client_conn_id = ble_coc_connection(self.bt_device, self.dut)
        sar_df = self.sweep_table(self.dut,
                                  self.bt_device,
                                  self.client_conn_id,
                                  self.gatt_server,
                                  self.gatt_callback,
                                  isBLE=True)
        sar_df = self.process_table(sar_df)
        self.process_results(sar_df, type='BLE')
