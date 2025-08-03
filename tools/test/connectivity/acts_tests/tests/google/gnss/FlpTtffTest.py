#!/usr/bin/env python3.5
#
#   Copyright 2019 - The Android Open Source Project
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

from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts.utils import get_current_epoch_time
from acts_contrib.test_utils.wifi.wifi_test_utils import wifi_toggle_state
from acts_contrib.test_utils.tel.tel_logging_utils import start_qxdm_logger
from acts_contrib.test_utils.tel.tel_logging_utils import stop_qxdm_logger
from acts_contrib.test_utils.tel.tel_logging_utils import start_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import stop_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.gnss.gnss_test_utils import get_baseband_and_gms_version
from acts_contrib.test_utils.gnss.gnss_test_utils import _init_device
from acts_contrib.test_utils.gnss.gnss_test_utils import clear_logd_gnss_qxdm_log
from acts_contrib.test_utils.gnss.gnss_test_utils import set_mobile_data
from acts_contrib.test_utils.gnss.gnss_test_utils import get_gnss_qxdm_log
from acts_contrib.test_utils.gnss.gnss_test_utils import set_wifi_and_bt_scanning
from acts_contrib.test_utils.gnss.gnss_test_utils import process_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import start_ttff_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import process_ttff_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import check_ttff_data
from acts_contrib.test_utils.gnss.gnss_test_utils import set_attenuator_gnss_signal
from acts_contrib.test_utils.gnss.gnss_test_utils import connect_to_wifi_network
from acts_contrib.test_utils.gnss.gnss_test_utils import gnss_tracking_via_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import parse_gtw_gpstool_log
from acts_contrib.test_utils.gnss.gnss_test_utils import log_current_epoch_time
from acts_contrib.test_utils.gnss.testtracker_util import log_testtracker_uuid


class FlpTtffTest(BaseTestClass):
    """ FLP TTFF Tests"""
    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = ["pixel_lab_network", "standalone_cs_criteria",
                      "qdsp6m_path", "flp_ttff_max_threshold",
                      "pixel_lab_location", "default_gnss_signal_attenuation",
                      "weak_gnss_signal_attenuation", "ttff_test_cycle",
                      "collect_logs"]
        self.unpack_userparams(req_param_names=req_params)
        self.ssid_map = {}
        for network in self.pixel_lab_network:
            SSID = network['SSID']
            self.ssid_map[SSID] = network
        if int(self.ad.adb.shell("settings get global airplane_mode_on")) != 0:
            self.ad.log.info("Force airplane mode off")
            force_airplane_mode(self.ad, False)
        _init_device(self.ad)

    def setup_test(self):
        log_current_epoch_time(self.ad, "test_start_time")
        log_testtracker_uuid(self.ad, self.current_test_name)
        get_baseband_and_gms_version(self.ad)
        if self.collect_logs:
            clear_logd_gnss_qxdm_log(self.ad)
            set_attenuator_gnss_signal(self.ad, self.attenuators,
                                       self.default_gnss_signal_attenuation)
        if not verify_internet_connection(self.ad.log, self.ad, retries=3,
                                          expected_state=True):
            raise signals.TestFailure("Fail to connect to LTE network.")

    def teardown_test(self):
        if self.collect_logs:
            stop_qxdm_logger(self.ad)
            stop_adb_tcpdump(self.ad)
            set_attenuator_gnss_signal(self.ad, self.attenuators,
                                       self.default_gnss_signal_attenuation)
        if int(self.ad.adb.shell("settings get global mobile_data")) != 1:
            set_mobile_data(self.ad, True)
        if int(self.ad.adb.shell(
            "settings get global wifi_scan_always_enabled")) != 1:
            set_wifi_and_bt_scanning(self.ad, True)
        if self.ad.droid.wifiCheckState():
            wifi_toggle_state(self.ad, False)
        log_current_epoch_time(self.ad, "test_end_time")

    def on_pass(self, test_name, begin_time):
        if self.collect_logs:
            self.ad.take_bug_report(test_name, begin_time)
            get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
            get_tcpdump_log(self.ad, test_name, begin_time)

    def on_fail(self, test_name, begin_time):
        if self.collect_logs:
            self.ad.take_bug_report(test_name, begin_time)
            get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
            get_tcpdump_log(self.ad, test_name, begin_time)

    """ Helper Functions """

    def flp_ttff_hs_and_cs(self, criteria, location):
        flp_results = []
        ttff = {"hs": "Hot Start", "cs": "Cold Start"}
        for mode in ttff.keys():
            begin_time = get_current_epoch_time()
            process_gnss_by_gtw_gpstool(
                self.ad, self.standalone_cs_criteria, api_type="flp")
            start_ttff_by_gtw_gpstool(
                self.ad, ttff_mode=mode, iteration=self.ttff_test_cycle)
            ttff_data = process_ttff_by_gtw_gpstool(
                self.ad, begin_time, location, api_type="flp")
            result = check_ttff_data(self.ad, ttff_data, ttff[mode], criteria)
            flp_results.append(result)
        asserts.assert_true(
            all(flp_results), "FLP TTFF fails to reach designated criteria")

    def start_qxdm_and_tcpdump_log(self):
        """Start QXDM and adb tcpdump if collect_logs is True."""
        if self.collect_logs:
            start_qxdm_logger(self.ad, get_current_epoch_time())
            start_adb_tcpdump(self.ad)

    """ Test Cases """

    def test_flp_one_hour_tracking(self):
        """Verify FLP tracking performance of position error.

        Steps:
            1. Launch GTW_GPSTool.
            2. FLP tracking for 60 minutes.

        Expected Results:
            DUT could finish 60 minutes test and output track data.
        """
        self.start_qxdm_and_tcpdump_log()
        gnss_tracking_via_gtw_gpstool(self.ad, self.standalone_cs_criteria,
                                      api_type="flp", testtime=60)
        parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="flp")

    def test_flp_ttff_strong_signal_wifiscan_on_wifi_connect(self):
        """Verify FLP TTFF Hot Start and Cold Start under strong GNSS signals
        with WiFi scanning on and connected.

        Steps:
            1. Enable WiFi scanning in location setting.
            2. Connect to WiFi AP.
            3. TTFF Hot Start for 10 iteration.
            4. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, True)
        wifi_toggle_state(self.ad, True)
        connect_to_wifi_network(
            self.ad, self.ssid_map[self.pixel_lab_network[0]["SSID"]])
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)

    def test_flp_ttff_strong_signal_wifiscan_on_wifi_not_connect(self):
        """Verify FLP TTFF Hot Start and Cold Start under strong GNSS signals
        with WiFi scanning on and not connected.

        Steps:
            1. Enable WiFi scanning in location setting.
            2. WiFi is not connected.
            3. TTFF Hot Start for 10 iteration.
            4. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, True)
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)

    def test_flp_ttff_strong_signal_wifiscan_off(self):
        """Verify FLP TTFF Hot Start and Cold Start with WiFi scanning OFF
           under strong GNSS signals.

        Steps:
            1. Disable WiFi scanning in location setting.
            2. TTFF Hot Start for 10 iteration.
            3. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, False)
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)

    def test_flp_ttff_weak_signal_wifiscan_on_wifi_connect(self):
        """Verify FLP TTFF Hot Start and Cold Start under Weak GNSS signals
        with WiFi scanning on and connected

        Steps:
            1. Set attenuation value to weak GNSS signal.
            2. Enable WiFi scanning in location setting.
            3. Connect to WiFi AP.
            4. TTFF Hot Start for 10 iteration.
            5. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        set_attenuator_gnss_signal(self.ad, self.attenuators,
                                   self.weak_gnss_signal_attenuation)
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, True)
        wifi_toggle_state(self.ad, True)
        connect_to_wifi_network(
            self.ad, self.ssid_map[self.pixel_lab_network[0]["SSID"]])
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)

    def test_flp_ttff_weak_signal_wifiscan_on_wifi_not_connect(self):
        """Verify FLP TTFF Hot Start and Cold Start under Weak GNSS signals
        with WiFi scanning on and not connected.

        Steps:
            1. Set attenuation value to weak GNSS signal.
            2. Enable WiFi scanning in location setting.
            3. WiFi is not connected.
            4. TTFF Hot Start for 10 iteration.
            5. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        set_attenuator_gnss_signal(self.ad, self.attenuators,
                                   self.weak_gnss_signal_attenuation)
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, True)
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)

    def test_flp_ttff_weak_signal_wifiscan_off(self):
        """Verify FLP TTFF Hot Start and Cold Start with WiFi scanning OFF
           under weak GNSS signals.

        Steps:
            1. Set attenuation value to weak GNSS signal.
            2. Disable WiFi scanning in location setting.
            3. TTFF Hot Start for 10 iteration.
            4. TTFF Cold Start for 10 iteration.

        Expected Results:
            Both FLP TTFF Hot Start and Cold Start results should be within
            flp_ttff_max_threshold.
        """
        set_attenuator_gnss_signal(self.ad, self.attenuators,
                                   self.weak_gnss_signal_attenuation)
        self.start_qxdm_and_tcpdump_log()
        set_wifi_and_bt_scanning(self.ad, False)
        self.flp_ttff_hs_and_cs(self.flp_ttff_max_threshold,
                                self.pixel_lab_location)
