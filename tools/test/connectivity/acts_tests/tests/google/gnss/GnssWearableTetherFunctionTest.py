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
import statistics

from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.gnss.testtracker_util import log_testtracker_uuid
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils
from acts_contrib.test_utils.tel import tel_logging_utils as tutils
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts.utils import get_current_epoch_time
from acts_contrib.test_utils.gnss.gnss_test_utils import delete_lto_file, pair_to_wearable
from acts_contrib.test_utils.gnss.gnss_test_utils import process_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import start_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import check_tracking_file
from uiautomator import Device


class GnssWearableTetherFunctionTest(BaseTestClass):
    """ GNSS Wearable Tether Function Tests"""
    def setup_class(self):
        super().setup_class()
        self.watch = self.android_devices[0]
        self.phone = self.android_devices[1]
        self.phone.uia = Device(self.phone.serial)
        req_params = ["pixel_lab_network", "standalone_cs_criteria",
                      "flp_ttff_max_threshold", "pixel_lab_location",
                      "flp_ttff_cycle", "default_gnss_signal_attenuation",
                      "flp_waiting_time", "tracking_test_time",
                      "far_start_criteria", "ttff_test_cycle"]
        self.unpack_userparams(req_param_names=req_params)
        # create hashmap for SSID
        self.ssid_map = {}
        for network in self.pixel_lab_network:
            SSID = network["SSID"]
            self.ssid_map[SSID] = network
        self.ttff_mode = {"cs": "Cold Start",
                          "ws": "Warm Start",
                          "hs": "Hot Start"}
        gutils._init_device(self.watch)
        pair_to_wearable(self.watch, self.phone)

    def teardown_class(self):
        super().teardown_class()
        gutils.reboot(self.phone)

    def setup_test(self):
        gutils.log_current_epoch_time(self.watch, "test_start_time")
        log_testtracker_uuid(self.watch, self.current_test_name)
        gutils.get_baseband_and_gms_version(self.watch)
        gutils.clear_logd_gnss_qxdm_log(self.watch)
        gutils.clear_logd_gnss_qxdm_log(self.phone)
        gutils.set_attenuator_gnss_signal(self.watch, self.attenuators,
                                          self.default_gnss_signal_attenuation)
        if not verify_internet_connection(self.watch.log, self.watch, retries=5,
                                          expected_state=True):
            time.sleep(60)
            if not verify_internet_connection(self.watch.log, self.watch, retries=3,
                                              expected_state=True):
                raise signals.TestFailure("Fail to connect to LTE network.")

    def teardown_test(self):
        gutils.stop_pixel_logger(self.watch)
        tutils.stop_adb_tcpdump(self.watch)
        gutils.set_attenuator_gnss_signal(self.watch, self.attenuators,
                                       self.default_gnss_signal_attenuation)
        if self.watch.droid.connectivityCheckAirplaneMode():
            self.watch.log.info("Force airplane mode off")
            toggle_airplane_mode(self.watch.log, self.watch, new_state=False)
        gutils.log_current_epoch_time(self.watch, "test_end_time")

    def on_fail(self, test_name, begin_time):
        self.watch.take_bug_report(test_name, begin_time)
        gutils.get_gnss_qxdm_log(self.watch)
        tutils.get_tcpdump_log(self.watch, test_name, begin_time)

    def start_qxdm_and_tcpdump_log(self):
        """Start QXDM and adb tcpdump if collect_logs is True."""
        gutils.start_pixel_logger(self.watch)
        tutils.start_adb_tcpdump(self.watch)

    def flp_ttff(self, mode, criteria, location):
        self.start_qxdm_and_tcpdump_log()
        start_gnss_by_gtw_gpstool(self.phone, True, api_type="FLP")
        time.sleep(self.flp_waiting_time)
        self.watch.unlock_screen(password=None)
        begin_time = get_current_epoch_time()
        process_gnss_by_gtw_gpstool(
            self.watch, self.standalone_cs_criteria, api_type="flp")
        gutils.start_ttff_by_gtw_gpstool(
            self.watch, mode, iteration=self.flp_ttff_cycle)
        results = gutils.process_ttff_by_gtw_gpstool(
            self.watch, begin_time, location, api_type="flp")
        gutils.check_ttff_data(self.watch, results, mode, criteria)
        self.check_location_from_phone()
        start_gnss_by_gtw_gpstool(self.phone, False, api_type="FLP")

    def check_location_from_phone(self):
        watch_file = check_tracking_file(self.watch)
        phone_file = check_tracking_file(self.phone)
        return gutils.compare_watch_phone_location(self, watch_file, phone_file)

    def run_ttff_via_gtw_gpstool(self, mode, criteria):
        """Run GNSS TTFF test with selected mode and parse the results.

        Args:
            mode: "cs", "ws" or "hs"
            criteria: Criteria for the TTFF.
        """
        begin_time = get_current_epoch_time()
        gutils.process_gnss_by_gtw_gpstool(self.watch, self.standalone_cs_criteria, clear_data=False)
        gutils.start_ttff_by_gtw_gpstool(self.watch, mode, self.ttff_test_cycle)
        ttff_data = gutils.process_ttff_by_gtw_gpstool(
            self.watch, begin_time, self.pixel_lab_location)
        result = gutils.check_ttff_data(
            self.watch, ttff_data, self.ttff_mode.get(mode), criteria)
        asserts.assert_true(
            result, "TTFF %s fails to reach designated criteria of %d "
                    "seconds." % (self.ttff_mode.get(mode), criteria))

    """ Test Cases """
    def test_flp_ttff_cs(self):
        """Verify FLP TTFF Cold Start while tether with phone.

        Steps:
            1. Pair with phone via Bluetooth.
            2. FLP TTFF Cold Start for 10 iteration.
            3. Check location source is from Phone.

        Expected Results:
            1. FLP TTFF Cold Start results should be within
            flp_ttff_max_threshold.
            2. Watch uses phone's FLP location.
        """
        self.flp_ttff("cs", self.flp_ttff_max_threshold, self.pixel_lab_location)

    def test_flp_ttff_ws(self):
        """Verify FLP TTFF Warm Start while tether with phone.

        Steps:
            1. Pair with phone via Bluetooth.
            2. FLP TTFF Warm Start for 10 iteration.
            3. Check location source is from Phone.

        Expected Results:
            1. FLP TTFF Warm Start results should be within
            flp_ttff_max_threshold.
            2. Watch uses phone's FLP location.
        """
        self.flp_ttff("ws", self.flp_ttff_max_threshold, self.pixel_lab_location)

    def test_flp_ttff_hs(self):
        """Verify FLP TTFF Hot Start while tether with phone.

        Steps:
            1. Pair with phone via Bluetooth.
            2. FLP TTFF Hot Start for 10 iteration.
            3. Check location source is from Phone.

        Expected Results:
            1. FLP TTFF Hot Start results should be within
            flp_ttff_max_threshold.
            2. Watch uses phone's FLP location.
        """
        self.flp_ttff("hs", self.flp_ttff_max_threshold, self.pixel_lab_location)

    def test_tracking_during_bt_disconnect_resume(self):
        """Verify tracking is correct during Bluetooth disconnect and resume.

        Steps:
            1. Make sure watch Bluetooth is on and in paired status.
            2. Do 1 min tracking.
            3. After 1 min tracking, check location source is using phone's FLP.
            4. Turn off watch Bluetooth, and do 1 min tracking.
            5. After 1 min tracking, check tracking results.
            6. Repeat Step 1 to Step 5 for 5 times.

        Expected Results:
            1. Watch uses phone's FLP location in Bluetooth connect state.
            2. Tracking results should be within pixel_lab_location criteria.
        """
        self.start_qxdm_and_tcpdump_log()
        for i in range(1, 4):
            if not self.watch.droid.bluetoothCheckState():
                self.watch.droid.bluetoothToggleState(True)
                self.watch.log.info("Turn Bluetooth on")
                self.watch.log.info("Wait 40s for Bluetooth auto re-connect")
                time.sleep(40)
            if not gutils.is_bluetooth_connected(self.watch, self.phone):
                raise signals.TestFailure("Fail to connect to device via Bluetooth.")
            start_gnss_by_gtw_gpstool(self.phone, True, api_type="FLP")
            time.sleep(self.flp_waiting_time)
            start_gnss_by_gtw_gpstool(self.watch, True, api_type="FLP")
            time.sleep(self.flp_waiting_time)
            self.watch.log.info("Wait 1 min for tracking")
            time.sleep(self.tracking_test_time)
            if not self.check_location_from_phone():
                raise signals.TestFailure("Watch is not using phone location")
            self.watch.droid.bluetoothToggleState(False)
            self.watch.log.info("Turn off Watch Bluetooth")
            self.watch.log.info("Wait 1 min for tracking")
            time.sleep(self.tracking_test_time)
            if self.check_location_from_phone():
                raise signals.TestError("Watch should not use phone location")
            gutils.parse_gtw_gpstool_log(self.watch, self.pixel_lab_location, api_type="FLP")
            start_gnss_by_gtw_gpstool(self.phone, False, api_type="FLP")

    def test_oobe_first_fix(self):
        """Verify first fix after OOBE pairing within the criteria

        Steps:
            1. Pair watch to phone during OOBE.
            2. Ensure LTO file download in watch.
            3. Ensure UTC time inject in watch.
            4. Enable AirPlane mode to untether to phone.
            5. Open GPSTool to get first fix in LTO and UTC time injected.
            6. Repeat Step1 ~ Step5 for 5 times.

        Expected Results:
            1. First fix should be within far_start_threshold.
        """
        oobe_results_all = []
        for i in range(1,4):
            self.watch.log.info("First fix after OOBE pairing - attempts %s" % i)
            pair_to_wearable(self.watch, self.phone)
            self.start_qxdm_and_tcpdump_log()
            begin_time = get_current_epoch_time()
            gutils.check_xtra_download(self.watch, begin_time)
            gutils.check_inject_time(self.watch)
            self.watch.log.info("Turn airplane mode on")
            # self.watch.droid.connectivityToggleAirplaneMode(True)
            toggle_airplane_mode(self.watch.log, self.watch, new_state=True)
            oobe_results = gutils.process_gnss(
                self.watch, self.far_start_criteria, clear_data=False)
            oobe_results_all.append(oobe_results)
        self.watch.log.info(f"TestResult Max_OOBE_First_Fix {max(oobe_results_all)}")
        self.watch.log.info(f"TestResult Avg_OOBE_First_Fix {statistics.mean(oobe_results_all)}")
        # self.watch.droid.connectivityToggleAirplaneMode(False)
        toggle_airplane_mode(self.watch.log, self.watch, new_state=False)
        self.watch.log.info("Turn airplane mode off")

    def test_oobe_first_fix_with_network_connection(self):
        """Verify first fix after OOBE pairing within the criteria

        Steps:
            1. Pair watch to phone during OOBE.
            2. Ensure LTO file download in watch.
            3. Ensure UTC time inject in watch.
            4. Turn off Bluetooth to untether to phone.
            5. Open GPSTool to get first fix in LTO and UTC time injected.
            6. Repeat Step1 ~ Step5 for 5 times.

        Expected Results:
            1. First fix should be within far_start_threshold.
        """
        oobe_results_all = []
        for i in range(1,4):
            self.watch.log.info("First fix after OOBE pairing - attempts %s" % i)
            pair_to_wearable(self.watch, self.phone)
            self.start_qxdm_and_tcpdump_log()
            begin_time = get_current_epoch_time()
            gutils.check_xtra_download(self.watch, begin_time)
            gutils.check_inject_time(self.watch)
            self.watch.log.info("Turn off Bluetooth to disconnect to phone")
            self.watch.droid.bluetoothToggleState(False)
            oobe_results = gutils.process_gnss(
                self.watch, self.far_start_criteria, clear_data=False)
            oobe_results_all.append(oobe_results)
        self.watch.log.info(f"TestResult Max_OOBE_First_Fix {max(oobe_results_all)}")
        self.watch.log.info(f"TestResult Avg_OOBE_First_Fix {statistics.mean(oobe_results_all)}")

    def test_far_start_ttff(self):
        """Verify Far Start (Warm Start v4) TTFF within the criteria

        Steps:
            1. Pair watch to phone during OOBE.
            2. Ensure LTO file download in watch.
            3. Ensure UTC time inject in watch.
            4. Enable AirPlane mode to untether to phone.
            5. TTFF Warm Start for 10 iteration.

        Expected Results:
            1. TTFF should be within far_start_threshold.
        """
        pair_to_wearable(self.watch, self.phone)
        self.start_qxdm_and_tcpdump_log()
        begin_time = get_current_epoch_time()
        gutils.check_xtra_download(self.watch, begin_time)
        gutils.check_inject_time(self.watch)
        self.watch.log.info("Turn airplane mode on")
        toggle_airplane_mode(self.watch.log, self.watch, new_state=True)
        self.run_ttff_via_gtw_gpstool("ws", self.far_start_criteria)
