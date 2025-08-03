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
import time
import os
import logging
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import acts_contrib.test_utils.coex.audio_test_utils as atu
from acts import asserts
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
from acts.signals import TestFailure

INIT_ATTEN = 0
WAIT_TIME = 2


class BtA2dpDynamicChannelTest(A2dpBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['codecs', 'rssi_profile_params']
        # 'rssi_profile_params' is a dict containing,a list of upper_bound,
        # lower_bound attenuation values, Dwell time for RSSI and the test duration
        # ex:- "rssi_profile_params": {
        #   "upper_bound": [15, 25],
        #    "RSSI_Dwell_time": [1, 1],
        #    "lower_bound": [35, 45],
        #    "test_duration": 30}
        # 'codecs' is a list containing all codecs required in the tests
        self.unpack_userparams(req_params)
        self.upper_bound = self.rssi_profile_params['upper_bound']
        self.lower_bound = self.rssi_profile_params['lower_bound']
        self.dwell_time = self.rssi_profile_params['RSSI_Dwell_time']
        for upper_bound, lower_bound, dwell_time in zip(
                self.upper_bound, self.lower_bound, self.dwell_time):
            for codec_config in self.codecs:
                self.generate_test_case(codec_config, upper_bound, lower_bound,
                                        dwell_time)

    def setup_class(self):
        super().setup_class()
        # Enable BQR on all android devices
        btutils.enable_bqr(self.android_devices)
        self.log_path = os.path.join(logging.log_path, 'results')

    def teardown_class(self):
        super().teardown_class()

    def generate_test_case(self, codec_config, upper_bound, lower_bound,
                           dwell_time):
        def test_case_fn():
            self.check_audio_quality_dynamic_rssi(upper_bound, lower_bound,
                                                  dwell_time)

        test_case_name = 'test_bt_a2dp_Dynamic_channel_between_attenuation_{}dB_and_{}dB' \
                         '_codec_{}'.format(upper_bound, lower_bound, codec_config['codec_type'])
        setattr(self, test_case_name, test_case_fn)

    def check_audio_quality_dynamic_rssi(self, upper_bound, lower_bound,
                                         dwell_time):
        tag = 'Dynamic_RSSI'
        self.media.play()
        proc = self.audio_device.start()
        self.inject_rssi_profile(upper_bound, lower_bound, dwell_time)
        proc.kill()
        time.sleep(WAIT_TIME)
        proc.kill()
        audio_captured = self.audio_device.stop()
        self.media.stop()
        self.log.info('Audio play and record stopped')
        asserts.assert_true(audio_captured, 'Audio not recorded')
        audio_result = atu.AudioCaptureResult(audio_captured,
                                              self.audio_params)
        thdn = audio_result.THDN(**self.audio_params['thdn_params'])
        self.log.info('THDN is {}'.format(thdn[0]))
        # Reading DUT RSSI to check the RSSI fluctuation from
        # upper and lower bound attenuation values
        self.attenuator.set_atten(upper_bound)
        [
            rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
            txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
        ], [rssi_slave] = self._get_bt_link_metrics(tag)
        rssi_l1 = rssi_master.get(self.dut.serial, -127)
        pwlv_l1 = pwl_master.get(self.dut.serial, -127)
        self.attenuator.set_atten(lower_bound)
        [
            rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
            txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
        ], [rssi_slave] = self._get_bt_link_metrics(tag)
        rssi_l2 = rssi_master.get(self.dut.serial, -127)
        pwlv_l2 = pwl_master.get(self.dut.serial, -127)
        self.log.info(
            "DUT RSSI is fluctuating between {} and {} dBm with {}sec interval"
            .format(rssi_l1, rssi_l2, dwell_time))
        if thdn[0] > self.audio_params['thdn_threshold'] or thdn[0] == 0:
            raise TestFailure('Observed audio glitches!')

    def inject_rssi_profile(self, upper_bound, lower_bound, dwell_time):
        end_time = time.time() + self.rssi_profile_params['test_duration']
        self.log.info("Testing dynamic channel RSSI")
        while time.time() < end_time:
            self.attenuator.set_atten(upper_bound)
            time.sleep(dwell_time)
            self.attenuator.set_atten(lower_bound)
            time.sleep(dwell_time)
