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
EXTRA_ADV_TIME = 3
ADV_TAIL = 5


class PowerBLEadvertiseTest(PBtBT.PowerBTBaseTest):

    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['adv_modes', 'adv_power_levels']
        self.unpack_userparams(req_params)
        # Loop all advertise modes and power levels
        for adv_mode in self.adv_modes:
            for adv_power_level in self.adv_power_levels:
                # As a temporary fix, set high power tests directly
                if adv_power_level != 3:
                    self.generate_test_case(adv_mode, adv_power_level)

    def setup_class(self):

        super().setup_class()
        self.dut.adb.shell(BLE_LOCATION_SCAN_ENABLE)
        # Make sure during power measurement, advertisement is always on
        self.adv_duration = self.mon_info.duration + self.mon_offset + ADV_TAIL + EXTRA_ADV_TIME

    def generate_test_case(self, adv_mode, adv_power_level):
        def test_case_fn():

            self.measure_ble_advertise_power(adv_mode, adv_power_level)

        adv_mode_str = bleenum.AdvertiseSettingsAdvertiseMode(adv_mode).name
        adv_txpl_str = bleenum.AdvertiseSettingsAdvertiseTxPower(
            adv_power_level).name.strip('ADVERTISE').strip('_')
        test_case_name = ('test_BLE_{}_{}'.format(adv_mode_str, adv_txpl_str))
        setattr(self, test_case_name, test_case_fn)

    def measure_ble_advertise_power(self, adv_mode, adv_power_level):

        btputils.start_apk_ble_adv(self.dut, adv_mode, adv_power_level,
                                   self.adv_duration)
        time.sleep(EXTRA_ADV_TIME)
        self.measure_power_and_validate()

    def test_BLE_ADVERTISE_MODE_LOW_POWER_TX_POWER_HIGH(self):
        self.measure_ble_advertise_power(0, 3)

    def test_BLE_ADVERTISE_MODE_BALANCED_TX_POWER_HIGH(self):
        self.measure_ble_advertise_power(1, 3)

    def test_BLE_ADVERTISE_MODE_LOW_LATENCY_TX_POWER_HIGH(self):
        self.measure_ble_advertise_power(2, 3)
