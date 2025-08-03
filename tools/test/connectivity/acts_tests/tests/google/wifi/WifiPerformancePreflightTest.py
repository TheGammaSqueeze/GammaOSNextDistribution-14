#!/usr/bin/env python3.4
#
#   Copyright 2020 - The Android Open Source Project
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

from acts import base_test
from acts.metrics.loggers.blackbox import BlackboxMappedMetricLogger
from acts_contrib.test_utils.wifi import wifi_performance_test_utils as wputils
from acts_contrib.test_utils.wifi import wifi_retail_ap as retail_ap
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils


class WifiPerformancePreflightTest(base_test.BaseTestClass):
    """Class for Wifi performance preflight tests.

    This class implements WiFi performance tests to perform before any other
    test suite. Currently, the preflight checklist checks the wifi firmware and
    config files, i.e., bdf files for any changes by retrieving their version
    number and checksum.
    """
    def __init__(self, controllers):
        base_test.BaseTestClass.__init__(self, controllers)
        self.testcase_metric_logger = (
            BlackboxMappedMetricLogger.for_test_case())

    def setup_class(self):
        self.dut = self.android_devices[-1]
        # Initialize AP to ensure that tests can be run in later suites
        req_params = ['RetailAccessPoints']
        opt_params = ['bdf', 'firmware']
        self.unpack_userparams(req_params, opt_params)
        self.access_point = retail_ap.create(self.RetailAccessPoints)[0]
        # Load BDF and firmware if needed
        if hasattr(self, 'bdf'):
            self.log.info('Pushing WiFi BDF to DUT.')
            wputils.push_config(self.dut, self.bdf[0])
        if hasattr(self, 'firmware'):
            self.log.info('Pushing WiFi firmware to DUT.')
            wputils.push_firmware(self.dut, self.firmware)

        for ad in self.android_devices:
            wutils.wifi_toggle_state(ad, True)
            ad.droid.wifiEnableVerboseLogging(1)
            try:
                ad.adb.shell("wpa_cli -i wlan0 -p -g@android:wpa_wlan0 IFNAME="
                             "wlan0 log_level EXCESSIVE")
            except:
                self.log.warning('Could not set log level.')

    def test_wifi_sw_signature(self):
        sw_signature = wputils.get_sw_signature(self.dut)
        self.testcase_metric_logger.add_metric(
            'config_signature', sw_signature['config_signature'])
        self.testcase_metric_logger.add_metric('fw_signature',
                                               sw_signature['fw_signature'])
        self.testcase_metric_logger.add_metric('serial_hash',
                                               sw_signature['serial_hash'])

    def teardown_class(self):
        # Teardown AP and release its lockfile
        self.access_point.teardown()
