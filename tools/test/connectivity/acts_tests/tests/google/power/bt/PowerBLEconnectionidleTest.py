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
from acts import utils
import acts_contrib.test_utils.bt.BleEnum as bleenum
import acts_contrib.test_utils.bt.bt_power_test_utils as btputils
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import acts_contrib.test_utils.power.PowerBTBaseTest as PBtBT
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test

BLE_LOCATION_SCAN_ENABLE = 'settings put secure location_mode 3'
EXTRA_CON_TIME = 3
SCAN_TAIL = 5


class PowerBLEconnectionidleTest(PBtBT.PowerBTBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['BLE_Connection_Priority']
        self.unpack_userparams(req_params)
        for con_priority in self.BLE_Connection_Priority:
            self.generate_test_case_ble_idle_connection(con_priority)

    def setup_class(self):
        super().setup_class()
        self.client_ad = self.android_devices[0]
        utils.set_location_service(self.client_ad, True)
        self.server_ad = self.android_devices[1]
        self.Connection_duration = self.mon_info.duration + self.mon_offset + SCAN_TAIL + EXTRA_CON_TIME
        setup_multiple_devices_for_bt_test(self.android_devices)

    def setup_test(self):
        super().setup_test()
        time.sleep(5)
        self.client_ad.adb.shell(BLE_LOCATION_SCAN_ENABLE)
        btutils.enable_bluetooth(self.client_ad.droid, self.client_ad.ed)
        if not self.server_ad.droid.bluetoothSetLocalName('advertiser'):
            self.log.error("Failed rename the device")
            return False
        self.log.info("Renamed the BT device")
        self.client_ad.droid.goToSleepNow()

    def teardown_test(self):
        self.client_ad.droid.bluetoothFactoryReset()
        btutils.disable_bluetooth(self.client_ad.droid)
        self.server_ad.droid.bluetoothFactoryReset()
        btutils.disable_bluetooth(self.server_ad.droid)

    def generate_test_case_ble_idle_connection(self, con_priority):
        def test_case_fn():
            self.measure_ble_connection_idle_power(con_priority)

        test_case_name = ('test_BLE_{}_IDLE_CONNECTION'.format(
            bleenum.BLEConnectionPriority(con_priority).name))
        setattr(self, test_case_name, test_case_fn)

    def measure_ble_connection_idle_power(self, con_priority):
        btputils.establish_ble_connection(self.client_ad, self.server_ad,
                                          con_priority)
        time.sleep(EXTRA_CON_TIME)
        self.measure_power_and_validate()
