#!/usr/bin/env python3.4
#
#   Copyright 2017 - Google
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

import os

from acts import asserts
from acts import utils
from acts.base_test import BaseTestClass
from acts.keys import Config
from acts_contrib.test_utils.net import net_test_utils as nutils
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.rtt import rtt_const as rconsts
from acts_contrib.test_utils.wifi.rtt import rtt_test_utils as rutils


class RttBaseTest(BaseTestClass):

    def setup_class(self):
        opt_param = ["ranging_role_concurrency_flexible_models"]
        self.unpack_userparams(opt_param_names=opt_param)

    def setup_test(self):
        required_params = ("lci_reference", "lcr_reference",
                           "rtt_reference_distance_mm",
                           "stress_test_min_iteration_count",
                           "stress_test_target_run_time_sec")
        self.unpack_userparams(required_params)

        # can be moved to JSON config file
        self.rtt_reference_distance_margin_mm = 2000
        self.rtt_max_failure_rate_two_sided_rtt_percentage = 20
        self.rtt_max_failure_rate_one_sided_rtt_percentage = 50
        self.rtt_max_margin_exceeded_rate_two_sided_rtt_percentage = 10
        self.rtt_max_margin_exceeded_rate_one_sided_rtt_percentage = 50
        self.rtt_min_expected_rssi_dbm = -100

        wutils.start_all_wlan_logs(self.android_devices)
        self.tcpdump_proc = []
        if hasattr(self, "android_devices"):
            for ad in self.android_devices:
                proc = nutils.start_tcpdump(ad, self.test_name)
                self.tcpdump_proc.append((ad, proc))

        for ad in self.android_devices:
            utils.set_location_service(ad, True)
            ad.droid.wifiEnableVerboseLogging(1)
            asserts.skip_if(
                not ad.droid.doesDeviceSupportWifiRttFeature(),
                "Device under test does not support Wi-Fi RTT - skipping test")
            wutils.wifi_toggle_state(ad, True)
            rtt_avail = ad.droid.wifiIsRttAvailable()
            if not rtt_avail:
                self.log.info('RTT not available. Waiting ...')
                rutils.wait_for_event(ad, rconsts.BROADCAST_WIFI_RTT_AVAILABLE)
            ad.ed.clear_all_events()
            rutils.config_privilege_override(ad, False)
            wutils.set_wifi_country_code(ad, wutils.WifiEnums.CountryCode.US)
            ad.rtt_capabilities = rutils.get_rtt_capabilities(ad)

    def teardown_test(self):
        wutils.stop_all_wlan_logs(self.android_devices)
        for proc in self.tcpdump_proc:
            nutils.stop_tcpdump(
                    proc[0], proc[1], self.test_name, pull_dump=False)
        self.tcpdump_proc = []
        for ad in self.android_devices:
            if not ad.droid.doesDeviceSupportWifiRttFeature():
                return

            # clean-up queue from the System Service UID
            ad.droid.wifiRttCancelRanging([1000])

    def on_fail(self, test_name, begin_time):
        for ad in self.android_devices:
            ad.take_bug_report(test_name, begin_time)
            ad.cat_adb_log(test_name, begin_time)
            wutils.get_ssrdumps(ad)
        wutils.stop_all_wlan_logs(self.android_devices)
        for ad in self.android_devices:
            wutils.get_wlan_logs(ad)
        for proc in self.tcpdump_proc:
            nutils.stop_tcpdump(proc[0], proc[1], self.test_name)
        self.tcpdump_proc = []
