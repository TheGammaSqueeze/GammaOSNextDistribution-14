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
from acts_contrib.test_utils.bt.bt_constants import adv_succ
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
from acts_contrib.test_utils.bt.bt_constants import bt_default_timeout
from acts_contrib.test_utils.bt.bt_constants import ble_advertise_settings_modes
from acts_contrib.test_utils.bt.bt_constants import ble_advertise_settings_tx_powers
from acts_contrib.test_utils.bt.bt_test_utils import BtTestUtilsError
from queue import Empty
from acts_contrib.test_utils.bt.bt_test_utils import generate_ble_advertise_objects
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test

INIT_ATTEN = 0


class BtA2dpRangeWithBleAdvTest(A2dpBaseTest):
    """User can generate test case with below format.
      test_bt_a2dp_range_codec_"Codec"_adv_mode_"Adv Mode"_adv_tx_power_"Adv Tx Power"

      Below are the list of test cases:
          test_bt_a2dp_range_codec_AAC_adv_mode_low_power_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_AAC_adv_mode_low_power_adv_tx_power_low
          test_bt_a2dp_range_codec_AAC_adv_mode_low_power_adv_tx_power_medium
          test_bt_a2dp_range_codec_AAC_adv_mode_low_power_adv_tx_power_high
          test_bt_a2dp_range_codec_AAC_adv_mode_balanced_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_AAC_adv_mode_balanced_adv_tx_power_low
          test_bt_a2dp_range_codec_AAC_adv_mode_balanced_adv_tx_power_medium
          test_bt_a2dp_range_codec_AAC_adv_mode_balanced_adv_tx_power_high
          test_bt_a2dp_range_codec_AAC_adv_mode_low_latency_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_AAC_adv_mode_low_latency_adv_tx_power_low
          test_bt_a2dp_range_codec_AAC_adv_mode_low_latency_adv_tx_power_medium
          test_bt_a2dp_range_codec_AAC_adv_mode_low_latency_adv_tx_power_high
          test_bt_a2dp_range_codec_SBC_adv_mode_low_power_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_SBC_adv_mode_low_power_adv_tx_power_low
          test_bt_a2dp_range_codec_SBC_adv_mode_low_power_adv_tx_power_medium
          test_bt_a2dp_range_codec_SBC_adv_mode_low_power_adv_tx_power_high
          test_bt_a2dp_range_codec_SBC_adv_mode_balanced_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_SBC_adv_mode_balanced_adv_tx_power_low
          test_bt_a2dp_range_codec_SBC_adv_mode_balanced_adv_tx_power_medium
          test_bt_a2dp_range_codec_SBC_adv_mode_balanced_adv_tx_power_high
          test_bt_a2dp_range_codec_SBC_adv_mode_low_latency_adv_tx_power_ultra_low
          test_bt_a2dp_range_codec_SBC_adv_mode_low_latency_adv_tx_power_low
          test_bt_a2dp_range_codec_SBC_adv_mode_low_latency_adv_tx_power_medium
          test_bt_a2dp_range_codec_SBC_adv_mode_low_latency_adv_tx_power_high
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
            for adv_mode in ble_advertise_settings_modes.items():
                for adv_power_level in ble_advertise_settings_tx_powers.items():
                    arg_set = [(codec_config, adv_mode, adv_power_level)]
                    self.generate_tests(
                        test_logic=self.BtA2dp_with_ble_adv_test_logic,
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

    def BtA2dp_with_ble_adv_test_logic(self, codec_config, adv_mode,
                                       adv_power_level):
        adv_callback = self.start_ble_adv(adv_mode[1], adv_power_level[1])
        self.run_a2dp_to_max_range(codec_config)
        self.dut.droid.bleStopBleAdvertising(adv_callback)
        self.log.info("Advertisement stopped Successfully")

    def create_test_name(self, codec_config, adv_mode, adv_power_level):
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            test_case_name = 'test_dual_bt_a2dp_range_codec_{}_gainmismatch_{}dB_adv_mode_{}_adv_tx_power_{}'.format(
                codec_config['codec_type'], self.gain_mismatch, adv_mode[0],
                adv_power_level[0])
        else:
            test_case_name = 'test_bt_a2dp_range_codec_{}_adv_mode_{}_adv_tx_power_{}'.format(
                codec_config['codec_type'], adv_mode[0], adv_power_level[0])
        return test_case_name

    def start_ble_adv(self, adv_mode, adv_power_level):
        """Function to start an LE advertisement
        Steps:
        1. Create a advertise data object
        2. Create a advertise settings object.
        3. Create a advertise callback object.
        4. Start an LE advertising using the objects created in steps 1-3.
        5. Find the onSuccess advertisement event.

        Expected Result:
        Advertisement is successfully advertising.

        Returns:
          Returns advertise call back"""

        self.dut.droid.bleSetAdvertiseDataIncludeDeviceName(True)
        self.dut.droid.bleSetAdvertiseSettingsAdvertiseMode(adv_mode)
        self.dut.droid.bleSetAdvertiseSettingsIsConnectable(True)
        self.dut.droid.bleSetAdvertiseSettingsTxPowerLevel(adv_power_level)
        advertise_callback, advertise_data, advertise_settings = (
            generate_ble_advertise_objects(self.dut.droid))
        self.dut.droid.bleStartBleAdvertising(advertise_callback,
                                              advertise_data,
                                              advertise_settings)
        try:
            self.dut.ed.pop_event(adv_succ.format(advertise_callback),
                                  bt_default_timeout)
            self.log.info("Advertisement started successfully")
        except Empty as err:
            raise BtTestUtilsError(
                "Advertiser did not start successfully {}".format(err))
        return advertise_callback

