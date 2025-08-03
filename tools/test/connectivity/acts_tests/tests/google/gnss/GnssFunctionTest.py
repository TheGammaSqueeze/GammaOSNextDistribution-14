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

from collections import defaultdict
import datetime
import os
import re
import time
import functools
import fnmatch
from statistics import mean

from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils
from acts.utils import get_current_epoch_time
from acts.utils import unzip_maintain_permissions
from acts_contrib.test_utils.wifi.wifi_test_utils import wifi_toggle_state
from acts_contrib.test_utils.tel.tel_bootloader_utils import flash_radio
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_connected_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.gnss.gnss_test_utils import get_baseband_and_gms_version
from acts_contrib.test_utils.gnss.gnss_test_utils import set_attenuator_gnss_signal
from acts_contrib.test_utils.gnss.gnss_test_utils import _init_device
from acts_contrib.test_utils.gnss.gnss_test_utils import check_location_service
from acts_contrib.test_utils.gnss.gnss_test_utils import clear_logd_gnss_qxdm_log
from acts_contrib.test_utils.gnss.gnss_test_utils import set_mobile_data
from acts_contrib.test_utils.gnss.gnss_test_utils import set_wifi_and_bt_scanning
from acts_contrib.test_utils.gnss.gnss_test_utils import get_gnss_qxdm_log
from acts_contrib.test_utils.gnss.gnss_test_utils import remount_device
from acts_contrib.test_utils.gnss.gnss_test_utils import reboot
from acts_contrib.test_utils.gnss.gnss_test_utils import check_network_location
from acts_contrib.test_utils.gnss.gnss_test_utils import launch_google_map
from acts_contrib.test_utils.gnss.gnss_test_utils import check_location_api
from acts_contrib.test_utils.gnss.gnss_test_utils import set_battery_saver_mode
from acts_contrib.test_utils.gnss.gnss_test_utils import start_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import process_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import connect_to_wifi_network
from acts_contrib.test_utils.gnss.gnss_test_utils import gnss_tracking_via_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import parse_gtw_gpstool_log
from acts_contrib.test_utils.gnss.gnss_test_utils import start_toggle_gnss_by_gtw_gpstool
from acts_contrib.test_utils.gnss.gnss_test_utils import grant_location_permission
from acts_contrib.test_utils.gnss.gnss_test_utils import is_mobile_data_on
from acts_contrib.test_utils.gnss.gnss_test_utils import is_wearable_btwifi
from acts_contrib.test_utils.gnss.gnss_test_utils import is_device_wearable
from acts_contrib.test_utils.gnss.gnss_test_utils import log_current_epoch_time
from acts_contrib.test_utils.gnss.testtracker_util import log_testtracker_uuid
from acts_contrib.test_utils.tel.tel_logging_utils import stop_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log


_PPS_KICKIN_WAITING_TIME_IN_SECOND = 30

class GnssFunctionTest(BaseTestClass):
    """ GNSS Function Tests"""
    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = ["pixel_lab_network",
                      "standalone_ws_criteria", "standalone_hs_criteria",
                      "supl_cs_criteria",
                      "supl_hs_criteria",
                      "standalone_cs_criteria",
                      "wearable_reboot_hs_criteria",
                      "first_satellite_criteria",
                      "default_gnss_signal_attenuation",
                      "weak_gnss_signal_attenuation",
                      "gnss_init_error_list",
                      "gnss_init_error_allowlist", "pixel_lab_location",
                      "qdsp6m_path", "ttff_test_cycle",
                      "collect_logs", "dpo_threshold",
                      "brcm_error_log_allowlist", "onchip_interval", "adr_ratio_threshold",
                      "set_attenuator", "weak_signal_criteria", "weak_signal_cs_criteria"]
        self.unpack_userparams(req_param_names=req_params)
        # create hashmap for SSID
        self.ssid_map = {}
        for network in self.pixel_lab_network:
            SSID = network["SSID"]
            self.ssid_map[SSID] = network
        self.ttff_mode = {"cs": "Cold Start",
                          "ws": "Warm Start",
                          "hs": "Hot Start",
                          "csa": "CSWith Assist"}
        if self.collect_logs and gutils.check_chipset_vendor_by_qualcomm(self.ad):
            self.flash_new_radio_or_mbn()
            self.push_gnss_cfg()
        self.init_device()

    def init_device(self):
        gutils._init_device(self.ad)
        gutils.enable_supl_mode(self.ad)
        gutils.enable_vendor_orbit_assistance_data(self.ad)
        gutils.disable_ramdump(self.ad)
        gutils.enable_compact_and_particle_fusion_log(self.ad)

    def setup_test(self):
        log_current_epoch_time(self.ad, "test_start_time")
        log_testtracker_uuid(self.ad, self.current_test_name)
        get_baseband_and_gms_version(self.ad)
        if self.collect_logs:
            clear_logd_gnss_qxdm_log(self.ad)
        if self.set_attenuator:
            set_attenuator_gnss_signal(self.ad, self.attenuators,
                                       self.default_gnss_signal_attenuation)
        # TODO (b/202101058:chenstanley): Need to double check how to disable wifi successfully in wearable projects.
        if is_wearable_btwifi(self.ad):
            wifi_toggle_state(self.ad, True)
            connect_to_wifi_network(
                self.ad, self.ssid_map[self.pixel_lab_network[0]["SSID"]])
        else:
            wifi_toggle_state(self.ad, False)
            set_mobile_data(self.ad, True)

        if not verify_internet_connection(self.ad.log, self.ad, retries=3,
                                          expected_state=True):
            raise signals.TestFailure("Fail to connect to LTE network.")

    def teardown_test(self):
        if self.collect_logs:
            gutils.stop_pixel_logger(self.ad)
            stop_adb_tcpdump(self.ad)
        if self.set_attenuator:
            set_attenuator_gnss_signal(self.ad, self.attenuators,
                                       self.default_gnss_signal_attenuation)
        # TODO(chenstanley): sim structure issue
        if not is_device_wearable(self.ad):
            if check_call_state_connected_by_adb(self.ad):
                hangup_call(self.ad.log, self.ad)
        if self.ad.droid.connectivityCheckAirplaneMode():
            self.ad.log.info("Force airplane mode off")
            toggle_airplane_mode(self.ad.log, self.ad, new_state=False)
        if int(self.ad.adb.shell(
            "settings get global wifi_scan_always_enabled")) != 1:
            set_wifi_and_bt_scanning(self.ad, True)
        log_current_epoch_time(self.ad, "test_end_time")

    def keep_logs(self):
        # for debug cs is faster than ws issue
        test_name = self.test_name
        self.ad.take_bug_report(test_name, self.begin_time)
        get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
        get_tcpdump_log(self.ad, test_name, self.begin_time)

    def on_fail(self, test_name, begin_time):
        if self.collect_logs:
            self.ad.take_bug_report(test_name, begin_time)
            get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
            get_tcpdump_log(self.ad, test_name, begin_time)

    def push_gnss_cfg(self):
        """Push required GNSS cfg file to DUT for PixelLogger to use as
        default GNSS logging mask."""
        gnss_cfg_path = "/vendor/etc/mdlog"
        gnss_cfg_file = self.user_params.get("gnss_cfg")
        if isinstance(gnss_cfg_file, list):
            gnss_cfg_file = gnss_cfg_file[0]
        os.system("chmod -R 777 %s" % gnss_cfg_file)
        self.ad.log.info("GNSS Required CFG = %s" % gnss_cfg_file)
        self.ad.log.info("Push %s to %s" % (gnss_cfg_file, gnss_cfg_path))
        self.ad.push_system_file(gnss_cfg_file, gnss_cfg_path)

    def flash_new_radio_or_mbn(self):
        paths = {}
        path = self.user_params.get("radio_image")
        if isinstance(path, list):
            path = path[0]
        if not path or "dev/null" in path:
            self.ad.log.info("Radio image path is not defined in Test flag.")
            return False
        for path_key in os.listdir(path):
            if fnmatch.fnmatch(path_key, "*.img"):
                paths["radio_image"] = os.path.join(path, path_key)
                os.system("chmod -R 777 %s" % paths["radio_image"])
                self.ad.log.info("radio_image = %s" % paths["radio_image"])
            if fnmatch.fnmatch(path_key, "*.zip"):
                zip_path = os.path.join(path, path_key)
                self.ad.log.info("Unzip %s", zip_path)
                dest_path = os.path.join(path, "mbn")
                unzip_maintain_permissions(zip_path, dest_path)
                paths["mbn_path"] = dest_path
                os.system("chmod -R 777 %s" % paths["mbn_path"])
                self.ad.log.info("mbn_path = %s" % paths["mbn_path"])
                self.ad.log.info(os.listdir(paths["mbn_path"]))
        if not paths.get("radio_image"):
            self.ad.log.info("No radio image is provided on X20. "
                             "Skip flashing radio step.")
            return False
        else:
            get_baseband_and_gms_version(self.ad, "Before flash radio")
            flash_radio(self.ad, paths["radio_image"])
            get_baseband_and_gms_version(self.ad, "After flash radio")
        if not paths.get("mbn_path"):
            self.ad.log.info("No need to push mbn files")
            return False
        else:
            try:
                mcfg_ver = self.ad.adb.shell(
                    "cat /vendor/rfs/msm/mpss/readonly/vendor/mbn/mcfg.version")
                if mcfg_ver:
                    self.ad.log.info("Before push mcfg, mcfg.version = %s",
                                     mcfg_ver)
                else:
                    self.ad.log.info("There is no mcfg.version before push, "
                                     "unmatching device")
                    return False
            except Exception as e:
                self.ad.log.info("There is no mcfg.version before push, "
                                 "unmatching device %s" % e)
                return False
            get_baseband_and_gms_version(self.ad, "Before push mcfg")
            try:
                remount_device(self.ad)
                cmd = "%s %s" % (paths["mbn_path"]+"/.",
                                 "/vendor/rfs/msm/mpss/readonly/vendor/mbn/")
                out = self.ad.adb.push(cmd)
                self.ad.log.info(out)
                reboot(self.ad)
            except Exception as e:
                self.ad.log.error("Push mbn files error %s", e)
                return False
            get_baseband_and_gms_version(self.ad, "After push mcfg")
            try:
                new_mcfg_ver = self.ad.adb.shell(
                    "cat /vendor/rfs/msm/mpss/readonly/vendor/mbn/mcfg.version")
                if new_mcfg_ver:
                    self.ad.log.info("New mcfg.version = %s", new_mcfg_ver)
                    if new_mcfg_ver == mcfg_ver:
                        self.ad.log.error("mcfg.version is the same before and "
                                          "after push")
                        return True
                else:
                    self.ad.log.error("Unable to get new mcfg.version")
                    return False
            except Exception as e:
                self.ad.log.error("cat mcfg.version with error %s", e)
                return False

    def standalone_ttff_airplane_mode_on(self, mode, criteria):
        """Verify Standalone GNSS TTFF functionality while airplane mode is on.

        Args:
            mode: "cs", "ws" or "hs"
            criteria: Criteria for the test.
        """
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        self.ad.log.info("Turn airplane mode on")
        toggle_airplane_mode(self.ad.log, self.ad, new_state=True)
        """
        The change for arguments here is for b/277667310
        Randomized interval is used for breaking CS GLNS only fix sequence.
        """
        gutils.run_ttff_via_gtw_gpstool(
            self.ad,
            mode,
            criteria,
            self.ttff_test_cycle,
            self.pixel_lab_location,
            raninterval=True,
            mininterval=15,
            maxinterval=20,)

    """ Test Cases """

    def test_cs_first_fixed_system_server_restart(self):
        """Verify cs first fixed after system server restart.

        Steps:
            1. Get location fixed within supl_cs_criteria.
            2. Restarts android runtime.
            3. Get location fixed within supl_cs_criteria.

        Expected Results:
            Location fixed within supl_cs_criteria.
        """
        overall_test_result = []
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        for test_loop in range(1, 6):
            gutils.process_gnss_by_gtw_gpstool(self.ad, self.supl_cs_criteria)
            gutils.start_gnss_by_gtw_gpstool(self.ad, False)
            self.ad.restart_runtime()
            self.ad.unlock_screen(password=None)
            test_result = gutils.process_gnss_by_gtw_gpstool(self.ad, self.supl_cs_criteria)
            gutils.start_gnss_by_gtw_gpstool(self.ad, False)
            self.ad.log.info("Iteration %d => %s" % (test_loop, test_result))
            overall_test_result.append(test_result)

        asserts.assert_true(all(overall_test_result),
                            "SUPL fail after system server restart.")

    def test_recovery_and_location_time_after_gnss_services_restart(self):
        """Verify gpsd recover time after gpsd being killed.

        Steps:
            1. Start GPS tracking
            2. Restart GPS daemons by killing PID.
            3. Waiting for GPS service to restart
            4. Waiting for the first fixed
            4. Re-run steps 1~4 for 5 times.

        Expected Results:
            1. The time GPSd services take to restart must be within 3 seconds.
            2. Location fix time must be within supl_hs_criteria
        """
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Skip the test due to Qualcomm chipset")
        test_times = 5
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        satellite_times = defaultdict(list)
        location_fix_times = defaultdict(list)

        kill_functions = (
            gutils.get_gps_process_and_kill_function_by_vendor(self.ad))
        for time in range(1, test_times+1):
            self.ad.log.info("Performing test times %d", time)
            first_fixed_time = process_gnss_by_gtw_gpstool(
                self.ad,
                criteria=self.supl_hs_criteria,
                clear_data=False)

            begin_time = int(first_fixed_time.timestamp() * 1000)
            self.ad.log.info("Start tracking")
            gutils.wait_n_mins_for_gnss_tracking(self.ad,
                                                 begin_time,
                                                 testtime=0.5,
                                                 ignore_hal_crash=False)


            for num, (process, kill_function) in enumerate(kill_functions.items()):
                kill_start_time = kill_function()
                first_gpsd_update_time = (gutils.get_gpsd_update_time(
                    self.ad,
                    kill_start_time))
                self.ad.log.info("Resume tracking ... ")
                gutils.wait_n_mins_for_gnss_tracking(self.ad,
                                                     begin_time,
                                                     testtime=num+1,
                                                     ignore_hal_crash=True)

                location_fix_time = (gutils.
                                     get_location_fix_time_via_gpstool_log(
                                         self.ad, first_gpsd_update_time))

                satellite_times[process].append(first_gpsd_update_time - kill_start_time)
                location_fix_times[process].append(location_fix_time - first_gpsd_update_time)
                # gpsd recovery time : Time between gpsd killed to first satellite update.
                self.ad.log.info("%s recovery time : %d ms",
                                 process, (first_gpsd_update_time - kill_start_time))
                # TTFF Hot Start : Time between first satellite update to first location fix.
                self.ad.log.info("TTFF Hot Start %d ms",
                                 (location_fix_time - first_gpsd_update_time))
            start_gnss_by_gtw_gpstool(self.ad, state=False)

        for num, process in enumerate(kill_functions):
            prop_basename = gutils.UPLOAD_TO_SPONGE_PREFIX + f"{process}_recovery_time_"
            self.ad.log.info(prop_basename + "AVG %d",
                             mean(satellite_times[process]))
            self.ad.log.info(prop_basename + "MAX %d",
                             max(satellite_times[process]))
            prop_basename = gutils.UPLOAD_TO_SPONGE_PREFIX + f"{process}_ttff_hs_"
            self.ad.log.info(prop_basename + "AVG %d",
                             mean(location_fix_times[process]))
            self.ad.log.info(prop_basename + "MAX %d",
                             max(location_fix_times[process]))
            asserts.assert_true(mean(satellite_times[process])/1000 <=
                                self.first_satellite_criteria,
                                f"{process} takes more than {self.first_satellite_criteria}"
                                "seconds in average to recover")
            asserts.assert_true(mean(location_fix_times[process])/1000 <=
                                self.supl_hs_criteria,
                                f"Location fix time is more than {self.supl_hs_criteria}"
                                "seconds in average")

    def test_gnss_one_hour_tracking(self):
        """Verify GNSS tracking performance of signal strength and position
        error.

        Steps:
            1. Launch GTW_GPSTool.
            2. GNSS tracking for 60 minutes.

        Expected Results:
            DUT could finish 60 minutes test and output track data.
        """
        test_time = 60
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        gnss_tracking_via_gtw_gpstool(self.ad, self.standalone_cs_criteria,
                                      api_type="gnss", testtime=test_time)
        location_data = parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss")
        gutils.validate_location_fix_rate(self.ad, location_data, run_time=test_time,
                                          fix_rate_criteria=0.99)
        gutils.verify_gps_time_should_be_close_to_device_time(self.ad, location_data)

    def test_duty_cycle_function(self):
        """Verify duty cycle Functionality.

        Steps:
            1. Launch GTW_GPSTool.
            2. Enable GnssMeasurement.
            3. GNSS tracking for 5 minutes.
            4. Calculate the count diff of "HardwareClockDiscontinuityCount"

        Expected Results:
            Duty cycle should be engaged in 5 minutes GNSS tracking.
        """
        tracking_minutes = 5
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        dpo_begin_time = get_current_epoch_time()
        gnss_tracking_via_gtw_gpstool(self.ad,
                                      self.standalone_cs_criteria,
                                      api_type="gnss",
                                      testtime=tracking_minutes,
                                      meas_flag=True)
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            gutils.check_dpo_rate_via_gnss_meas(self.ad,
                                                dpo_begin_time,
                                                self.dpo_threshold)
        else:
            gutils.check_dpo_rate_via_brcm_log(self.ad,
                                               self.dpo_threshold,
                                               self.brcm_error_log_allowlist)

    def test_gnss_init_error(self):
        """Check if there is any GNSS initialization error after reboot.

        Steps:
            1. Reboot DUT.
            2. Check logcat if the following error pattern shows up.
              "E LocSvc.*", ".*avc.*denied.*u:r:location:s0",
              ".*avc.*denied.*u:r:hal_gnss_qti:s0"

        Expected Results:
            There should be no GNSS initialization error after reboot.
        """
        error_mismatch = True
        for attr in self.gnss_init_error_list:
            error = self.ad.adb.shell("logcat -d | grep -E '%s'" % attr)
            if error:
                for allowlist in self.gnss_init_error_allowlist:
                    if allowlist in error:
                        error = re.sub(".*"+allowlist+".*\n?", "", error)
                        self.ad.log.info("\"%s\" is in allow-list and removed "
                                         "from error." % allowlist)
                if error:
                    error_mismatch = False
                    self.ad.log.error("\n%s" % error)
            else:
                self.ad.log.info("NO \"%s\" initialization error found." % attr)
        asserts.assert_true(error_mismatch, "Error message found after GNSS "
                                            "init")

    def test_sap_valid_modes(self):
        """Verify SAP Valid Modes.

        Steps:
            1. Root DUT.
            2. Check SAP Valid Modes.

        Expected Results:
            SAP=PREMIUM
        """
        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Not Qualcomm chipset. Skip the test.")
        sap_state = str(self.ad.adb.shell("cat vendor/etc/izat.conf | grep "
                                          "SAP="))
        self.ad.log.info("SAP Valid Modes - %s" % sap_state)
        asserts.assert_true("SAP=PREMIUM" in sap_state,
                            "Wrong SAP Valid Modes is set")

    def test_network_location_provider_cell(self):
        """Verify LocationManagerService API reports cell Network Location.

        Steps:
            1. WiFi scanning and Bluetooth scanning in Location Setting are OFF.
            2. Launch GTW_GPSTool.
            3. Verify whether test devices could report cell Network Location.
            4. Repeat Step 2. to Step 3. for 5 times.

        Expected Results:
            Test devices could report cell Network Location.
        """
        test_result_all = []
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        set_wifi_and_bt_scanning(self.ad, False)
        for i in range(1, 6):
            test_result = check_network_location(
                self.ad, retries=3, location_type="cell")
            test_result_all.append(test_result)
            self.ad.log.info("Iteration %d => %s" % (i, test_result))
        set_wifi_and_bt_scanning(self.ad, True)
        asserts.assert_true(all(test_result_all),
                            "Fail to get networkLocationType=cell")

    def test_network_location_provider_wifi(self):
        """Verify LocationManagerService API reports wifi Network Location.

        Steps:
            1. WiFi scanning and Bluetooth scanning in Location Setting are ON.
            2. Launch GTW_GPSTool.
            3. Verify whether test devices could report wifi Network Location.
            4. Repeat Step 2. to Step 3. for 5 times.

        Expected Results:
            Test devices could report wifi Network Location.
        """
        test_result_all = []
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        set_wifi_and_bt_scanning(self.ad, True)
        for i in range(1, 6):
            test_result = check_network_location(
                self.ad, retries=3, location_type="wifi")
            test_result_all.append(test_result)
            self.ad.log.info("Iteration %d => %s" % (i, test_result))
        asserts.assert_true(all(test_result_all),
                            "Fail to get networkLocationType=wifi")

    def test_gmap_location_report_battery_saver(self):
        """Verify GnssLocationProvider API reports location to Google Map
           when Battery Saver is enabled.

        Steps:
            1. GPS and NLP are on.
            2. Enable Battery Saver.
            3. Launch Google Map.
            4. Verify whether test devices could report location.
            5. Repeat Step 3. to Step 4. for 5 times.
            6. Disable Battery Saver.

        Expected Results:
            Test devices could report location to Google Map.
        """
        test_result_all = []
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        set_battery_saver_mode(self.ad, True)
        for i in range(1, 6):
            grant_location_permission(self.ad, True)
            launch_google_map(self.ad)
            test_result = check_location_api(self.ad, retries=3)
            self.ad.send_keycode("HOME")
            test_result_all.append(test_result)
            self.ad.log.info("Iteration %d => %s" % (i, test_result))
        set_battery_saver_mode(self.ad, False)
        asserts.assert_true(all(test_result_all), "Fail to get location update")

    def test_gnss_ttff_cs_airplane_mode_on(self):
        """Verify Standalone GNSS functionality of TTFF Cold Start while
        airplane mode is on.

        Steps:
            1. Turn on airplane mode.
            2. TTFF Cold Start for 10 iteration.

        Expected Results:
            All Standalone TTFF Cold Start results should be within
            standalone_cs_criteria.
        """
        self.standalone_ttff_airplane_mode_on("cs", self.standalone_cs_criteria)

    def test_gnss_ttff_ws_airplane_mode_on(self):
        """Verify Standalone GNSS functionality of TTFF Warm Start while
        airplane mode is on.

        Steps:
            1. Turn on airplane mode.
            2. TTFF Warm Start for 10 iteration.

        Expected Results:
            All Standalone TTFF Warm Start results should be within
            standalone_ws_criteria.
        """
        self.standalone_ttff_airplane_mode_on("ws", self.standalone_ws_criteria)

    def test_gnss_ttff_hs_airplane_mode_on(self):
        """Verify Standalone GNSS functionality of TTFF Hot Start while
        airplane mode is on.

        Steps:
            1. Turn on airplane mode.
            2. TTFF Hot Start for 10 iteration.

        Expected Results:
            All Standalone TTFF Hot Start results should be within
            standalone_hs_criteria.
        """
        self.standalone_ttff_airplane_mode_on("hs", self.standalone_hs_criteria)

    def test_cs_ttff_in_weak_gnss_signal(self):
        """Verify TTFF cold start under weak GNSS signal.

        Steps:
            1. Set attenuation value to weak GNSS signal.
            2. TTFF Cold Start for 10 iteration.

        Expected Results:
            TTFF CS results should be within weak_signal_cs_criteria.
        """
        gutils.set_attenuator_gnss_signal(self.ad, self.attenuators,
                                          self.weak_gnss_signal_attenuation)
        gutils.run_ttff(self.ad, mode="cs", criteria=self.weak_signal_cs_criteria,
                        test_cycle=self.ttff_test_cycle, base_lat_long=self.pixel_lab_location,
                        collect_logs=self.collect_logs)

    def test_ws_ttff_in_weak_gnss_signal(self):
        """Verify TTFF warm start under weak GNSS signal.

        Steps:
            2. Set attenuation value to weak GNSS signal.
            3. TTFF Warm Start for 10 iteration.

        Expected Results:
            TTFF WS result should be within weak_signal_criteria.
        """
        gutils.set_attenuator_gnss_signal(self.ad, self.attenuators,
                                          self.weak_gnss_signal_attenuation)
        gutils.run_ttff(self.ad, mode="ws", criteria=self.weak_signal_criteria,
                        test_cycle=self.ttff_test_cycle, base_lat_long=self.pixel_lab_location,
                        collect_logs=self.collect_logs)

    def test_hs_ttff_in_weak_gnss_signal(self):
        """Verify TTFF hot start under weak GNSS signal.

        Steps:
            2. Set attenuation value to weak GNSS signal.
            3. TTFF Hot Start for 10 iteration.

        Expected Results:
            TTFF HS result should be within weak_signal_criteria.
        """
        gutils.set_attenuator_gnss_signal(self.ad, self.attenuators,
                                          self.weak_gnss_signal_attenuation)
        gutils.run_ttff(self.ad, mode="hs", criteria=self.weak_signal_criteria,
                        test_cycle=self.ttff_test_cycle, base_lat_long=self.pixel_lab_location,
                        collect_logs=self.collect_logs)

    def test_quick_toggle_gnss_state(self):
        """Verify GNSS can still work properly after quick toggle GNSS off
        to on.

        Steps:
            1. Launch GTW_GPSTool.
            2. Go to "Advance setting"
            3. Set Cycle = 10 & Time-out = 60
            4. Go to "Toggle GPS" tab
            5. Execute "Start"

        Expected Results:
            No single Timeout is seen in 10 iterations.
        """
        gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
        start_toggle_gnss_by_gtw_gpstool(
            self.ad, iteration=self.ttff_test_cycle)

    def test_gnss_init_after_reboot(self):
        """Verify SUPL and XTRA/LTO functionality after reboot.

        Steps:
            1. Get location fixed within supl_cs_criteria.
            2. Reboot DUT.
            3. Get location fixed within supl_hs_criteria.
            4. Repeat Step 2. to Step 3. for 10 times.

        Expected Results:
            Location fixed within supl_hs_criteria.
        """
        overall_test_result = []
        # As b/252971345 requests, we need the log before reboot for debugging.
        gutils.start_pixel_logger(self.ad)
        process_gnss_by_gtw_gpstool(self.ad, self.supl_cs_criteria)
        start_gnss_by_gtw_gpstool(self.ad, False)
        gutils.stop_pixel_logger(self.ad)
        for test_loop in range(1, 11):
            reboot(self.ad)
            gutils.start_qxdm_and_tcpdump_log(self.ad, self.collect_logs)
            if is_device_wearable(self.ad):
                test_result = process_gnss_by_gtw_gpstool(
                    self.ad, self.wearable_reboot_hs_criteria, clear_data=False)
            else:
                test_result = process_gnss_by_gtw_gpstool(
                    self.ad, self.supl_hs_criteria, clear_data=False)
            start_gnss_by_gtw_gpstool(self.ad, False)
            self.ad.log.info("Iteration %d => %s" % (test_loop, test_result))
            overall_test_result.append(test_result)
            gutils.stop_pixel_logger(self.ad)
            stop_adb_tcpdump(self.ad)
        pass_rate = overall_test_result.count(True)/len(overall_test_result)
        self.ad.log.info("TestResult Pass_rate %s" % format(pass_rate, ".0%"))
        asserts.assert_true(all(overall_test_result),
                            "GNSS init fail after reboot.")

    def test_host_gnssstatus_validation(self):
        """Verify GnssStatus integrity during host tracking for 1 minute.

        Steps:
            1. Launch GTW_GPSTool.
            2. GNSS tracking for 1 minute with 1 second frequency.
            3. Validate all the GnssStatus raw data.(SV, SVID, Elev, Azim)

        Expected Results:
            GnssStatus obj should return no failures
        """
        gnss_tracking_via_gtw_gpstool(self.ad, self.standalone_cs_criteria,
                                      api_type="gnss", testtime=1)
        parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss",
                              validate_gnssstatus=True)

    def test_onchip_gnssstatus_validation(self):
        """Verify GnssStatus integrity during onchip tracking for 1 minute.

        Steps:
            1. Launch GTW_GPSTool.
            2. GNSS tracking for 1 minute with 6 second frequency.
            3. Validate all the GnssStatus raw data.(SV, SVID, Elev, Azim)

        Expected Results:
            GnssStatus obj should return no failures
        """
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Not BRCM chipset. Skip the test.")
        gnss_tracking_via_gtw_gpstool(self.ad, self.standalone_cs_criteria,
                                      api_type="gnss", testtime=1, freq=self.onchip_interval)
        parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss",
                              validate_gnssstatus=True)

    def test_location_update_after_resuming_from_deep_suspend(self):
        """Verify the GPS location reported after resume from deep doze mode
        1. Enable GPS location report for 1 min to make sure the GPS is working
        2. Force DUT into deep doze mode for 60s
        3. Enable GPS location report for 5 mins
        4. Check the report frequency
        5. Check the location fix rate
        """

        gps_enable_minutes = 1
        gnss_tracking_via_gtw_gpstool(self.ad, criteria=self.supl_cs_criteria, api_type="gnss",
                                      testtime=gps_enable_minutes)
        result = parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss")
        self.ad.log.debug("Location report details before deep doze")
        self.ad.log.debug(result)
        gutils.validate_location_fix_rate(self.ad, result, run_time=gps_enable_minutes,
                                          fix_rate_criteria=0.95)

        gutils.enter_deep_doze_mode(self.ad, lasting_time_in_seconds=60)

        gps_enable_minutes = 5
        gnss_tracking_via_gtw_gpstool(self.ad, criteria=self.supl_cs_criteria, api_type="gnss",
                                      testtime=gps_enable_minutes)
        result = parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss")
        self.ad.log.debug("Location report details after deep doze")
        self.ad.log.debug(result)

        location_report_time = list(result.keys())
        gutils.check_location_report_interval(self.ad, location_report_time,
                                              gps_enable_minutes * 60, tolerance=0.01)
        gutils.validate_location_fix_rate(self.ad, result, run_time=gps_enable_minutes,
                                          fix_rate_criteria=0.99)

    def test_location_mode_in_battery_saver_with_screen_off(self):
        """Ensure location request with foreground permission can work
        in battery saver mode (screen off)

        1. unplug power
        2. enter battery saver mode
        3. start tracking for 2 mins with screen off
        4. repest step 3 for 3 times
        """
        try:
            gutils.set_battery_saver_mode(self.ad, state=True)
            test_time = 2
            for i in range(1, 4):
                self.ad.log.info("Tracking attempt %s" % str(i))
                gnss_tracking_via_gtw_gpstool(
                    self.ad, criteria=self.supl_cs_criteria, api_type="gnss", testtime=test_time,
                    is_screen_off=True)
                result = parse_gtw_gpstool_log(self.ad, self.pixel_lab_location, api_type="gnss")
                gutils.validate_location_fix_rate(self.ad, result, run_time=test_time,
                                                  fix_rate_criteria=0.99)
        finally:
            gutils.set_battery_saver_mode(self.ad, state=False)

    def test_measure_adr_rate_after_10_mins_tracking(self):
        """Verify ADR rate

        1. Enable "Force full gnss measurement"
        2. Start tracking with GnssMeasurement enabled for 10 mins
        3. Check ADR usable rate / valid rate
        4. Disable "Force full gnss measurement"
        """
        adr_threshold = self.adr_ratio_threshold.get(self.ad.model)
        if not adr_threshold:
            self.ad.log.warn((f"Can't get '{self.ad.model}' threshold from config "
                              f"{self.adr_ratio_threshold}, use default threshold 0.5"))
            adr_threshold = 0.5
        with gutils.full_gnss_measurement(self.ad):
            gnss_tracking_via_gtw_gpstool(self.ad, criteria=self.supl_cs_criteria, api_type="gnss",
                                          testtime=10, meas_flag=True)
            gutils.validate_adr_rate(self.ad, pass_criteria=float(adr_threshold))


    def test_hal_crashing_should_resume_tracking(self):
        """Make sure location request can be resumed after HAL restart.

        1. Start GPS tool and get First Fixed
        2. Wait for 1 min for tracking
        3. Restart HAL service
        4. Wait for 1 min for tracking
        5. Check fix rate
        """

        first_fixed_time = process_gnss_by_gtw_gpstool(self.ad, criteria=self.supl_cs_criteria)
        begin_time = int(first_fixed_time.timestamp() * 1000)

        self.ad.log.info("Start 2 mins tracking")

        gutils.wait_n_mins_for_gnss_tracking(self.ad, begin_time, testtime=1,
                                             ignore_hal_crash=False)
        gutils.restart_hal_service(self.ad)
        # The test case is designed to run the tracking for 2 mins, so we assign testime to 2 to
        # indicate the total run time is 2 mins (starting from begin_time).
        gutils.wait_n_mins_for_gnss_tracking(self.ad, begin_time, testtime=2, ignore_hal_crash=True)

        start_gnss_by_gtw_gpstool(self.ad, state=False)

        result = parse_gtw_gpstool_log(self.ad, self.pixel_lab_location)
        gutils.validate_location_fix_rate(self.ad, result, run_time=2,
                                          fix_rate_criteria=0.95)


    def test_power_save_mode_should_apply_latest_measurement_setting(self):
        """Ensure power save mode will apply the GNSS measurement setting.

        1. Turn off full GNSS measurement.
        2. Run tracking for 2 mins
        3. Check the power save mode status
        4. Turn on full GNSS measurement and re-register measurement callback
        6. Run tracking for 30s
        7. Check the power save mode status
        8. Turn off full GNSS measurement and re-register measurement callback
        9. Run tracking for 2 mins
        10. Check the power save mode status
        """
        def wait_for_power_state_changes(wait_time):
            gutils.re_register_measurement_callback(self.ad)
            tracking_begin_time = get_current_epoch_time()
            gutils.wait_n_mins_for_gnss_tracking(self.ad, tracking_begin_time, testtime=wait_time)
            return tracking_begin_time

        if self.ad.model.lower() == "sunfish":
            raise signals.TestSkip(
                "According to b/241049795, it's HW issue and won't be fixed.")

        gutils.start_pixel_logger(self.ad)
        with gutils.run_gnss_tracking(self.ad, criteria=self.supl_cs_criteria, meas_flag=True):
            start_time = wait_for_power_state_changes(wait_time=2)
            gutils.check_power_save_mode_status(
                self.ad, full_power=False, begin_time=start_time,
                brcm_error_allowlist=self.brcm_error_log_allowlist)

            with gutils.full_gnss_measurement(self.ad):
                start_time = wait_for_power_state_changes(wait_time=0.5)
                gutils.check_power_save_mode_status(
                    self.ad, full_power=True, begin_time=start_time,
                    brcm_error_allowlist=self.brcm_error_log_allowlist)

            start_time = wait_for_power_state_changes(wait_time=2)
            gutils.check_power_save_mode_status(
                self.ad, full_power=False, begin_time=start_time,
                brcm_error_allowlist=self.brcm_error_log_allowlist)

        gutils.stop_pixel_logger(self.ad)

    def test_the_diff_of_gps_clock_and_elapsed_realtime_should_be_stable(self):
        gutils.start_pixel_logger(self.ad)
        with gutils.full_gnss_measurement(self.ad):
            first_fixed_time = gnss_tracking_via_gtw_gpstool(
                self.ad, criteria=self.supl_cs_criteria, api_type="gnss",
                testtime=5, meas_flag=True)
        gutils.stop_pixel_logger(self.ad)
        start_time = first_fixed_time + datetime.timedelta(
            seconds=time.timezone + _PPS_KICKIN_WAITING_TIME_IN_SECOND)
        self.ad.log.debug("Start time is %s" % start_time)
        gutils.validate_diff_of_gps_clock_elapsed_realtime(self.ad, start_time)
