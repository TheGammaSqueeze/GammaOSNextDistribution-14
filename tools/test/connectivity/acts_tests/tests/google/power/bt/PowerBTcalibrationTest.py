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

import csv
import os
import time
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import acts_contrib.test_utils.power.PowerBTBaseTest as PBtBT

EXTRA_PLAY_TIME = 30
GET_PROPERTY_HARDWARE_PLATFORM = 'getprop ro.boot.hardware.platform'


class PowerBTcalibrationTest(PBtBT.PowerBTBaseTest):
    def setup_test(self):

        super().setup_test()
        self.attenuator = self.attenuators[0]
        btutils.enable_bqr(self.dut)
        time.sleep(2)
        btutils.disable_bluetooth(self.dut.droid)
        time.sleep(2)
        btutils.enable_bluetooth(self.dut.droid, self.dut.ed)
        btutils.connect_phone_to_headset(self.dut, self.bt_device, 60)
        vol = self.dut.droid.getMaxMediaVolume() * self.volume
        self.dut.droid.setMediaVolume(int(vol))

        self.cal_data_path = os.path.join(self.log_path, 'Calibration')
        self.log_file = os.path.join(self.cal_data_path, 'Cal_data.csv')
        os.makedirs(os.path.dirname(self.log_file), exist_ok=True)

    def test_calibrate(self):
        """Run calibration to get attenuation value at each power level

        """

        self.cal_matrix = []
        self.media.play()
        time.sleep(EXTRA_PLAY_TIME)

        # Loop through attenuation in 1 dB step
        self.log.info('Starting Calibration Process')
        for i in range(int(self.attenuator.get_max_atten())):
            try:
                self.attenuator.set_atten(i)
                bt_metrics_dict = btutils.get_bt_metric(self.dut)
                pwl = bt_metrics_dict['pwlv'][self.dut.serial]
                rssi = bt_metrics_dict['rssi'][self.dut.serial]
                bftx = bt_metrics_dict['bftx'][self.dut.serial]
                self.log.info(
                    'Reach PW {}, RSSI {}, BFTX {} at attenuation {} dB'.format(
                        pwl, rssi, bftx, i))
            except Exception as e:
                self.log.warning('Get Exception {} at attenuation {} dB'.format(
                    str(e), i))
                continue
            self.cal_matrix.append([i, pwl, rssi, bftx])

        # Write cal results to csv
        with open(self.log_file, 'w', newline='') as f:
            writer = csv.writer(f)
            writer.writerows(self.cal_matrix)

    def teardown_test(self):
        """
        Clean up in teardown_test : Disable BQR
        """
        btutils.disable_bqr(self.dut)
