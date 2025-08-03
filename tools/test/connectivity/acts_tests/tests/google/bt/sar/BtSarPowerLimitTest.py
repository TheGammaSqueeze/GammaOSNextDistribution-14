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
import acts_contrib.test_utils.bt.bt_test_utils as bt_utils
from acts_contrib.test_utils.bt.BtSarBaseTest import BtSarBaseTest
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation
import acts_contrib.test_utils.wifi.wifi_performance_test_utils as wifi_utils

SLEEP_DURATION = 2

class BtSarPowerLimitTest(BtSarBaseTest):
    """Class to define BT SAR power cap tests.

    This class defines tests that iterate over and force different
    states in the BT SAR table and calculates the TX power at the
    antenna port.
    """
    def setup_test(self):
        super().setup_test()

        # To prevent default file from being overwritten
        self.dut.adb.shell('cp {} {}'.format(self.power_file_paths[0],
                                             self.power_file_paths[1]))

        self.sar_file_path = self.power_file_paths[1]
        self.sar_file_name = os.path.basename(self.power_file_paths[1])
        self.bt_sar_df = self.read_sar_table(self.dut)

        # BokehFigure object
        self.plot = wifi_utils.BokehFigure(title='{}'.format(
            self.current_test_name),
                                           x_label='Scenarios',
                                           primary_y_label='TX power(dBm)')

    def teardown_test(self):
        # Deleting the table
        self.dut.adb.shell('rm {}'.format(self.power_file_paths[1]))
        super().teardown_test()

    def test_bt_sar_table(self):
        sar_df = self.sweep_table()
        sar_df = self.process_table(sar_df)
        self.process_results(sar_df)

    def test_bt_sar_custom_table(self):

        self.push_table(self.dut, self.custom_sar_path)
        self.attenuator.set_atten(0)

        # Connect master and slave
        self.bt_device.reset()
        self.dut.droid.bluetoothFactoryReset()
        bt_utils.connect_phone_to_headset(self.dut, self.bt_device, 60)
        time.sleep(SLEEP_DURATION)
        ramp_attenuation(self.attenuator, self.pl10_atten)
        time.sleep(SLEEP_DURATION)
        sar_df = self.sweep_table()
        sar_df = self.process_table(sar_df)
        self.process_results(sar_df)
