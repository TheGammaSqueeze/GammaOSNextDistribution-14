#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import acts_contrib.test_utils.bt.bt_test_utils as btutils
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
from acts_contrib.test_utils.bt.bt_constants import ble_scan_settings_modes
from acts_contrib.test_utils.bt.bt_test_utils import generate_ble_scan_objects
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test
from acts_contrib.test_utils.bt.bt_constants import scan_result

INIT_ATTEN = 0


class BtA2dpRangeWithBleScanTest(A2dpBaseTest):
    """User can generate test case with below format.
    test_bt_a2dp_range_codec_"Codec"_with_BLE_scan_"Scan Mode"

    Below are the list of test cases:
        test_bt_a2dp_range_codec_AAC_with_BLE_scan_balanced
        test_bt_a2dp_range_codec_AAC_with_BLE_scan_low_latency
        test_bt_a2dp_range_codec_AAC_with_BLE_scan_low_power
        test_bt_a2dp_range_codec_AAC_with_BLE_scan_opportunistic
        test_bt_a2dp_range_codec_SBC_with_BLE_scan_balanced
        test_bt_a2dp_range_codec_SBC_with_BLE_scan_low_latency
        test_bt_a2dp_range_codec_SBC_with_BLE_scan_low_power
        test_bt_a2dp_range_codec_SBC_with_BLE_scan_opportunistic
    """

    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['attenuation_vector', 'codecs']
        opt_params = ['gain_mismatch', 'dual_chain']
        #'attenuation_vector' is a dict containing: start, stop and step of attenuation changes
        #'codecs' is a list containing all codecs required in the tests
        #'gain_mismatch' is an offset value between the BT two chains
        #'dual_chain' set to 1 enable sweeping attenuation for BT two chains
        self.unpack_userparams(req_params)
        self.unpack_userparams(opt_params, dual_chian=None, gain_mismatch=None)

    def setup_generated_tests(self):
        for codec_config in self.codecs:
            for scan_mode in ble_scan_settings_modes.items():
                arg_set = [(codec_config, scan_mode)]
                self.generate_tests(
                    test_logic=self.BtA2dp_with_ble_scan_test_logic,
                    name_func=self.create_test_name,
                    arg_sets=arg_set)

    def setup_class(self):
        super().setup_class()
        #Enable BQR on all android devices
        btutils.enable_bqr(self.android_devices)
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            self.atten_c0 = self.attenuators[0]
            self.atten_c1 = self.attenuators[1]
            self.atten_c0.set_atten(INIT_ATTEN)
            self.atten_c1.set_atten(INIT_ATTEN)
        return setup_multiple_devices_for_bt_test(self.android_devices)

    def teardown_class(self):
        super().teardown_class()
        if hasattr(self, 'atten_c0') and hasattr(self, 'atten_c1'):
            self.atten_c0.set_atten(INIT_ATTEN)
            self.atten_c1.set_atten(INIT_ATTEN)

    def BtA2dp_with_ble_scan_test_logic(self, codec_config, scan_mode):
        scan_callback = self.start_ble_scan(scan_mode[1])
        self.run_a2dp_to_max_range(codec_config)
        self.dut.droid.bleStopBleScan(scan_callback)
        self.log.info("BLE Scan stopped successfully")

    def create_test_name(self, codec_config, scan_mode):
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            test_case_name = 'test_dual_bt_a2dp_range_codec_{}_gainmismatch_{}dB_with_BLE_scan_{}'.format(
                codec_config['codec_type'], self.gain_mismatch, scan_mode[0])
        else:
            test_case_name = 'test_bt_a2dp_range_codec_{}_with_BLE_scan_{}'.format(
                codec_config['codec_type'], scan_mode[0])
        return test_case_name

    def start_ble_scan(self, scan_mode):
        """ This function will start Ble Scan with different scan mode.

        Args:
            Scan_mode: Ble scan setting modes

        returns:
        Scan_callback: Ble scan callback
        """

        self.dut.droid.bleSetScanSettingsScanMode(scan_mode)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(
            self.dut.droid)
        self.dut.droid.bleStartBleScan(filter_list, scan_settings,
                                       scan_callback)
        self.log.info("BLE Scanning started successfully")
        return scan_callback

