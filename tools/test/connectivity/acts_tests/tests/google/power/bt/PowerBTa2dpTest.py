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
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import acts_contrib.test_utils.power.PowerBTBaseTest as PBtBT
from acts import asserts
from acts_contrib.test_utils.bt import BtEnum
from acts.libs.proc import job

DEFAULT_ADB_TIMEOUT = 60
EXTRA_PLAY_TIME = 10
GET_PROPERTY_HARDWARE_PLATFORM = 'getprop ro.boot.hardware.platform'
PL_MAP = {
    '10': 'EPA_BF',
    '9': 'EPA_DIV',
    '8': 'IPA_BF',
    '7': 'IPA_DIV',
}

class PowerBTa2dpTest(PBtBT.PowerBTBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['codecs', 'tx_power_levels', 'atten_pl_settings']
        self.unpack_userparams(req_params)
        # Loop all codecs and tx power levels
        for codec_config in self.codecs:
            for tpl in self.tx_power_levels:
                # As a temporary fix, directly set the test with tpl 10
                if tpl != 10:
                    self.generate_test_case(codec_config, tpl)

    def setup_test(self):
        super().setup_test()
        btutils.connect_phone_to_headset(self.dut, self.bt_device, 60)
        vol = self.dut.droid.getMaxMediaVolume() * self.volume
        self.dut.droid.setMediaVolume(0)
        time.sleep(1)
        self.dut.droid.setMediaVolume(int(vol))

    def generate_test_case(self, codec_config, tpl):
        def test_case_fn():
            self.measure_a2dp_power(codec_config, tpl)

        power_level = 'PL{}'.format(tpl)

        # If the device is P21 and later, generate tests with different name.
        platform = self._get_hardware_platform_at_init_stage()
        self.log.info('Hardware Platform is: {}'.format(platform))
        if platform.startswith('gs'):
            power_level = PL_MAP[str(tpl)]
            self.log.info('The device is P21 or later, use name {}'.format(
                power_level))

        test_case_name = ('test_BTa2dp_{}_codec_at_{}'.format(
            codec_config['codec_type'], power_level))
        setattr(self, test_case_name, test_case_fn)

    def _get_hardware_platform_at_init_stage(self):

        # At __init__ stage the android devices are not registered. Thus, run
        # adb command with device sn directly.
        sn = self.controller_configs['AndroidDevice'][0]
        cmd = 'adb -s {} shell {}'.format(sn, GET_PROPERTY_HARDWARE_PLATFORM)
        result = job.run(cmd, ignore_status=True, timeout=DEFAULT_ADB_TIMEOUT)
        ret, out, err = result.exit_status, result.stdout, result.stderr
        self.log.info('get platform ret: {}, out: {}, err: {}'.format(
            ret, out, err))
        return out

    def measure_a2dp_power(self, codec_config, tpl):

        current_codec = self.dut.droid.bluetoothA2dpGetCurrentCodecConfig()
        current_codec_type = BtEnum.BluetoothA2dpCodecType(
            current_codec['codecType']).name
        if current_codec_type != codec_config['codec_type']:
            codec_set = btutils.set_bluetooth_codec(self.dut, **codec_config)
            asserts.assert_true(codec_set, 'Codec configuration failed.')
        else:
            self.log.info('Current Codec is {}, no need to change'.format(
                current_codec_type))
        # Start music playing
        self.media.play()
        time.sleep(EXTRA_PLAY_TIME)

        # Set attenuation so BT tx at desired power level
        self.log.info('Current Attenuation {} dB'.format(
            self.attenuator.get_atten()))
        tpl = 'PL' + str(tpl)
        PBtBT.ramp_attenuation(self.attenuator, self.atten_pl_settings[tpl][0],
                               attenuation_step_max=1, time_wait_in_between=1)
        self.log.info('Setting Attenuator to {} dB'.format(
            self.atten_pl_settings[tpl][0]))

        self.log.info('Running A2DP with codec {} at {}'.format(
            codec_config['codec_type'], tpl))
        self.dut.droid.goToSleepNow()
        self.measure_power_and_validate()

    def test_BTa2dp_AAC_codec_at_EPA_BF(self):
        self.measure_a2dp_power(self.codecs[0], 10)

    def test_BTa2dp_SBC_codec_at_EPA_BF(self):
        self.measure_a2dp_power(self.codecs[1], 10)