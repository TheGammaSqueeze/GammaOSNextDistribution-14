#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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

from acts import signals
from acts import utils
from acts_contrib.test_utils.power.PowerBaseTest import PowerBaseTest
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils

DEFAULT_WAIT_TIME = 60
DPO_NV_VALUE = '15DC'
MDS_TEST_PACKAGE = 'com.google.mdstest'
MDS_RUNNER = 'com.google.mdstest.instrument.ModemConfigInstrumentation'


class PowerGTWGnssBaseTest(PowerBaseTest):
    """Power GTW Gnss Base test"""

    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = [
            'wifi_network', 'test_location', 'qdsp6m_path',
            'calibrate_target', 'interval', 'meas_interval'
        ]
        self.unpack_userparams(req_param_names=req_params)
        self.set_xtra_data()

    def setup_test(self):
        gutils.log_current_epoch_time(self.ad, "test_start_time")
        super().setup_test()
        # Enable DPO
        self.enable_DPO(True)
        # Enable GNSS setting for GNSS standalone mode
        self.ad.adb.shell('settings put secure location_mode 3')
        # Recover attenuation value to strong CN
        self.set_attenuation(self.atten_level['strong_signal'])

    def teardown_test(self):
        begin_time = utils.get_current_epoch_time()
        self.ad.take_bug_report(self.test_name, begin_time)
        gutils.get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
        gutils.log_current_epoch_time(self.ad, "test_end_time")

    def set_xtra_data(self):
        gutils.disable_xtra_throttle(self.ad)
        self.turn_on_wifi_connection()
        gutils.enable_gnss_verbose_logging(self.ad)
        gutils.start_gnss_by_gtw_gpstool(self.ad, True, 'gnss')
        time.sleep(30)
        gutils.start_gnss_by_gtw_gpstool(self.ad, False, 'gnss')

    def turn_on_wifi_connection(self):
        """Turn on wifi connection."""
        wutils.wifi_toggle_state(self.ad, True)
        gutils.connect_to_wifi_network(self.ad, self.wifi_network)

    def set_cell_only(self):
        """Turn off wifi connection, enable cell service."""
        wutils.wifi_toggle_state(self.ad, False)
        utils.force_airplane_mode(self.ad, False)

    def baseline_test(self):
        """Baseline power measurement"""
        self.ad.droid.goToSleepNow()
        self.collect_power_data()
        self.ad.log.info('TestResult AVG_Current %.2f' % self.avg_current)

    def start_gnss_tracking_with_power_data(self,
                                            mode='default',
                                            is_signal=True,
                                            freq=0,
                                            lowpower=False,
                                            meas=False):
        """Start GNSS tracking and collect power metrics.

        Args:
            is_signal: default True, False for no Gnss signal test.
            freq: an integer to set location update frequency.
            lowpower: a boolean to set GNSS Low Power Mode.
            meas: a boolean to set GNSS Measurement registeration.
        """
        c_power, c_tracking, c_acquisition = self.request_power_stat()
        self.ad.adb.shell('settings put secure location_mode 3')
        gutils.start_gnss_by_gtw_gpstool(self.ad, True, 'gnss', True, freq,
                                         lowpower, meas)
        self.ad.droid.goToSleepNow()

        self.ad.log.info('Collect SV data for %d seconds.' % DEFAULT_WAIT_TIME)
        time.sleep(DEFAULT_WAIT_TIME)

        samples = self.collect_power_data()
        self.ad.log.info('TestResult AVG_Current %.2f' % self.avg_current)
        self.calibrate_avg_current(samples)
        self.ad.send_keycode('WAKEUP')

        gutils.start_gnss_by_gtw_gpstool(self.ad, False, 'gnss')
        n_power, n_tracking, n_acquisition = self.request_power_stat()
        self.ad.log.info("TestResult Total_power: %.2f" %(n_power - c_power))
        self.ad.log.info("TestResult Tracking: %.2f" %(n_tracking - c_tracking))
        self.ad.log.info("TestResult Acquisition: %.2f" %(n_acquisition - c_acquisition))
        gutils.parse_gtw_gpstool_log(self.ad, self.test_location, type='gnss')

    def calibrate_avg_current(self, samples):
        """Calibrate average current by filtering AP wake up current with target
           value.

        Args:
            samples: a list of tuples where the first element is a timestamp
            and the second element is a current sample.
        """
        calibrate_results = [
            sample[1] * 1000 for sample in samples
            if sample[1] * 1000 < self.calibrate_target
        ]
        avg_current = sum(calibrate_results) / len(calibrate_results)
        self.ad.log.info('TestResult Calibrate_AVG_Current %.2f' % avg_current)

    def enable_DPO(self, enable):
        """Enable or disable the DPO option.

        Args:
            enable: True or False to enable DPO.
        """
        self.ad.log.info('Change DPO to new state: %s.' % enable)
        val = '02' if enable else '00'
        options = {'request': 'writeNV', 'item': DPO_NV_VALUE, 'data': val}
        instrument_cmd = gutils.build_instrumentation_call(
            MDS_TEST_PACKAGE, MDS_RUNNER, options=options)
        result = self.ad.adb.shell(instrument_cmd)
        if 'SUCCESS' not in result:
            self.ad.log.info(result)
            raise signals.TestFailure('DPO is not able to Turn: %s' % enable)
        self.dut_rockbottom()

    def request_power_stat(self):
        """Request the power state via command.
        Returns:
            total_power, tracking, acquisition power consumption.
            If the device does not support, return 0, 0, 0
        """
        self.ad.adb.shell('cmd location providers send-extra-command gps request_power_stats')
        time.sleep(1)
        res = self.ad.adb.shell('dumpsys location | grep -A 10 -i \'power stats\'')
        if res:
            for line in res.split("\n"):
                if "total power" in line:
                    total_power = line.split(" ")[-1].split("mJ")[0]
                if "single-band tracking" in line:
                    single_tracking = line.split(" ")[-1].split("mJ")[0]
                    self.ad.log.info(single_tracking)
                if "multi-band tracking" in line:
                    multi_tracking = line.split(" ")[-1].split("mJ")[0]
                if "single-band acquisition" in line:
                    single_acquisition = line.split(" ")[-1].split("mJ")[0]
                if "multi-band acquisition" in line:
                    multi_acquisition = line.split(" ")[-1].split("mJ")[0]
            tracking = float(single_tracking) + float(multi_tracking)
            acquisition = float(single_acquisition) + float(multi_acquisition)
            self.ad.log.info("total power: %.2f" %float(total_power))
            self.ad.log.info("tracking: %.2f" %tracking)
            self.ad.log.info("acquisition: %.2f" %acquisition)
            return float(total_power), tracking, acquisition
        return 0, 0, 0
