#!/usr/bin/env python3.4
#
#   Copyright 2018 - The Android Open Source Project
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

import time
import acts_contrib.test_utils.bt.BleEnum as bleenum
import acts_contrib.test_utils.bt.bt_power_test_utils as btputils
import acts_contrib.test_utils.power.PowerBTBaseTest as PBtBT

BLE_LOCATION_SCAN_ENABLE = 'settings put secure location_mode 3'
EXTRA_SCAN_TIME = 3
SCAN_TAIL = 5


class PowerBLEscanTest(PBtBT.PowerBTBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['scan_modes']
        self.unpack_userparams(req_params)

    def setup_class(self):

        super().setup_class()
        self.dut.adb.shell(BLE_LOCATION_SCAN_ENABLE)
        # Make sure during power measurement, scan is always on
        self.scan_duration = self.mon_info.duration + self.mon_offset + SCAN_TAIL + EXTRA_SCAN_TIME

    def generate_test_case_no_devices_around(self, scan_mode):
        def test_case_fn():

            self.measure_ble_scan_power(scan_mode)

        test_case_name = ('test_BLE_{}_no_advertisers'.format(
            bleenum.ScanSettingsScanMode(scan_mode).name))
        setattr(self, test_case_name, test_case_fn)

    def measure_ble_scan_power(self, scan_mode):

        btputils.start_apk_ble_scan(self.dut, scan_mode, self.scan_duration)
        time.sleep(EXTRA_SCAN_TIME)
        self.measure_power_and_validate()

    def test_BLE_SCAN_MODE_LOW_POWER_no_advertisers(self):
        self.measure_ble_scan_power(0)

    def test_BLE_SCAN_MODE_BALANCED_no_advertisers(self):
        self.measure_ble_scan_power(1)

    def test_BLE_SCAN_MODE_LOW_LATENCY_no_advertisers(self):
        self.measure_ble_scan_power(2)
