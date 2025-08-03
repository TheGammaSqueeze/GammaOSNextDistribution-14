from multiprocessing import Process
import time

from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils
from acts_contrib.test_utils.gnss import supl
from acts_contrib.test_utils.gnss import gnss_defines
from acts_contrib.test_utils.gnss.testtracker_util import log_testtracker_uuid
from acts_contrib.test_utils.tel.tel_data_utils import http_file_download_by_sl4a
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log
from acts_contrib.test_utils.tel.tel_logging_utils import stop_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log
from acts_contrib.test_utils.tel.tel_test_utils import check_call_state_connected_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import verify_internet_connection
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts.utils import get_current_epoch_time


class GnssSuplTest(BaseTestClass):
    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = [
            "pixel_lab_network", "standalone_cs_criteria", "supl_cs_criteria", "supl_ws_criteria",
            "supl_hs_criteria", "default_gnss_signal_attenuation", "pixel_lab_location",
            "qdsp6m_path", "collect_logs", "ttff_test_cycle",
            "supl_capabilities", "no_gnss_signal_attenuation", "set_attenuator"
        ]
        self.unpack_userparams(req_param_names=req_params)
        # create hashmap for SSID
        self.ssid_map = {}
        for network in self.pixel_lab_network:
            SSID = network["SSID"]
            self.ssid_map[SSID] = network
        self.init_device()

    def only_brcm_device_runs_wifi_case(self):
        """SUPL over wifi is only supported by BRCM devices, for QUAL device, skip the test.
        """
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Qualcomm device doesn't support SUPL over wifi")

    def wearable_btwifi_should_skip_mobile_data_case(self):
        if gutils.is_wearable_btwifi(self.ad):
            raise signals.TestSkip("Skip mobile data case for BtWiFi sku")

    def init_device(self):
        """Init GNSS test devices for SUPL suite."""
        gutils._init_device(self.ad)
        gutils.disable_vendor_orbit_assistance_data(self.ad)
        gutils.enable_supl_mode(self.ad)
        self.enable_supl_over_wifi()
        gutils.reboot(self.ad)

    def enable_supl_over_wifi(self):
        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            supl.set_supl_over_wifi_state(self.ad, turn_on=True)

    def setup_test(self):
        gutils.log_current_epoch_time(self.ad, "test_start_time")
        log_testtracker_uuid(self.ad, self.current_test_name)
        gutils.clear_logd_gnss_qxdm_log(self.ad)
        gutils.get_baseband_and_gms_version(self.ad)
        toggle_airplane_mode(self.ad.log, self.ad, new_state=False)
        if gutils.is_wearable_btwifi(self.ad):
            wutils.wifi_toggle_state(self.ad, True)
            gutils.connect_to_wifi_network(self.ad,
                                           self.ssid_map[self.pixel_lab_network[0]["SSID"]])
        else:
            wutils.wifi_toggle_state(self.ad, False)
            gutils.set_mobile_data(self.ad, state=True)
        if not verify_internet_connection(self.ad.log, self.ad, retries=3,
                                          expected_state=True):
            raise signals.TestFailure("Fail to connect to LTE network.")
        # Once the device is rebooted, the xtra service will be alive again
        # In order not to affect the supl case, disable it in setup_test.
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            gutils.disable_qualcomm_orbit_assistance_data(self.ad)

    def teardown_test(self):
        if self.collect_logs:
            gutils.stop_pixel_logger(self.ad)
            stop_adb_tcpdump(self.ad)
        if self.set_attenuator:
            gutils.set_attenuator_gnss_signal(self.ad, self.attenuators,
                                              self.default_gnss_signal_attenuation)
        gutils.log_current_epoch_time(self.ad, "test_end_time")

    def on_fail(self, test_name, begin_time):
        if self.collect_logs:
            self.ad.take_bug_report(test_name, begin_time)
            gutils.get_gnss_qxdm_log(self.ad, self.qdsp6m_path)
            self.get_brcm_gps_xml_to_sponge()
            get_tcpdump_log(self.ad, test_name, begin_time)

    def get_brcm_gps_xml_to_sponge(self):
        # request from b/250506003 - to check the SUPL setting
        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            self.ad.pull_files(gnss_defines.BCM_GPS_XML_PATH, self.ad.device_log_path)

    def run_ttff(self, mode, criteria):
        """Triggers TTFF.

        Args:
            mode: "cs", "ws" or "hs"
            criteria: Criteria for the test.
        """
        return gutils.run_ttff(self.ad, mode, criteria, self.ttff_test_cycle,
                               self.pixel_lab_location, self.collect_logs)

    def supl_ttff_weak_gnss_signal(self, mode, criteria):
        """Verify SUPL TTFF functionality under weak GNSS signal.

        Args:
            mode: "cs", "ws" or "hs"
            criteria: Criteria for the test.
        """
        gutils.set_attenuator_gnss_signal(self.ad, self.attenuators,
                                          self.weak_gnss_signal_attenuation)
        self.run_ttff(mode, criteria)

    def connect_to_wifi_with_mobile_data_off(self):
        gutils.set_mobile_data(self.ad, False)
        wutils.wifi_toggle_state(self.ad, True)
        gutils.connect_to_wifi_network(self.ad, self.ssid_map[self.pixel_lab_network[0]["SSID"]])

    def connect_to_wifi_with_airplane_mode_on(self):
        toggle_airplane_mode(self.ad.log, self.ad, new_state=True)
        wutils.wifi_toggle_state(self.ad, True)
        gutils.connect_to_wifi_network(self.ad, self.ssid_map[self.pixel_lab_network[0]["SSID"]])

    def check_position_mode(self, begin_time: int, mode: str):
        logcat_results = self.ad.search_logcat(
            matching_string="setting position_mode to", begin_time=begin_time)
        return all([result["log_message"].split(" ")[-1] == mode for result in logcat_results])

    def test_supl_capabilities(self):
        """Verify SUPL capabilities.

        Steps:
            1. Root DUT.
            2. Check SUPL capabilities.

        Expected Results:
            CAPABILITIES=0x37 which supports MSA + MSB.
            CAPABILITIES=0x17 = ON_DEMAND_TIME | MSA | MSB | SCHEDULING
        """
        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Not Qualcomm chipset. Skip the test.")
        capabilities_state = str(
            self.ad.adb.shell(
                "cat vendor/etc/gps.conf | grep CAPABILITIES")).split("=")[-1]
        self.ad.log.info("SUPL capabilities - %s" % capabilities_state)

        asserts.assert_true(capabilities_state in self.supl_capabilities,
                            "Wrong default SUPL capabilities is set. Found %s, "
                            "expected any of %r" % (capabilities_state,
                                                    self.supl_capabilities))


    def test_supl_ttff_cs(self):
        """Verify SUPL functionality of TTFF Cold Start.

        Steps:
            1. Kill XTRA/LTO daemon to support SUPL only case.
            2. SUPL TTFF Cold Start for 10 iteration.

        Expected Results:
            All SUPL TTFF Cold Start results should be less than
            supl_cs_criteria.
        """
        self.run_ttff("cs", self.supl_cs_criteria)

    def test_supl_ttff_ws(self):
        """Verify SUPL functionality of TTFF Warm Start.

        Steps:
            1. Kill XTRA/LTO daemon to support SUPL only case.
            2. SUPL TTFF Warm Start for 10 iteration.

        Expected Results:
            All SUPL TTFF Warm Start results should be less than
            supl_ws_criteria.
        """
        self.run_ttff("ws", self.supl_ws_criteria)

    def test_supl_ttff_hs(self):
        """Verify SUPL functionality of TTFF Hot Start.

        Steps:
            1. Kill XTRA/LTO daemon to support SUPL only case.
            2. SUPL TTFF Hot Start for 10 iteration.

        Expected Results:
            All SUPL TTFF Hot Start results should be less than
            supl_hs_criteria.
        """
        self.run_ttff("hs", self.supl_hs_criteria)

    def test_cs_ttff_supl_over_wifi_with_airplane_mode_on(self):
        """ Test supl can works through wifi with airplane mode on

        Test steps are executed in the following sequence.
        - Turn on airplane mode
        - Connect to wifi
        - Run SUPL CS TTFF
        """
        self.only_brcm_device_runs_wifi_case()

        self.connect_to_wifi_with_airplane_mode_on()

        self.run_ttff(mode="cs", criteria=self.supl_cs_criteria)

    def test_ws_ttff_supl_over_wifi_with_airplane_mode_on(self):
        """ Test supl can works through wifi with airplane mode on

        Test steps are executed in the following sequence.
        - Turn on airplane mode
        - Connect to wifi
        - Run SUPL WS TTFF
        """
        self.only_brcm_device_runs_wifi_case()

        self.connect_to_wifi_with_airplane_mode_on()

        self.run_ttff("ws", self.supl_ws_criteria)

    def test_hs_ttff_supl_over_wifi_with_airplane_mode_on(self):
        """ Test supl can works through wifi with airplane mode on

        Test steps are executed in the following sequence.
        - Turn on airplane mode
        - Connect to wifi
        - Run SUPL WS TTFF
        """
        self.only_brcm_device_runs_wifi_case()

        self.connect_to_wifi_with_airplane_mode_on()

        self.run_ttff("hs", self.supl_ws_criteria)

    def test_ttff_gla_on(self):
        """ Test the turn on "Google Location Accuracy" in settings work or not.

        Test steps are executed in the following sequence.
        - Turn off airplane mode
        - Connect to Cellular
        - Turn off LTO/RTO
        - Turn on SUPL
        - Turn on GLA
        - Run CS TTFF

        Expected Results:
        - The position mode must be "MS_BASED"
        - The TTFF time should be less than 10 seconds
        """
        begin_time = get_current_epoch_time()
        gutils.gla_mode(self.ad, True)

        self.run_ttff("cs", self.supl_cs_criteria)
        asserts.assert_true(self.check_position_mode(begin_time, "MS_BASED"),
                                msg=f"Fail to enter the MS_BASED mode")

    def test_ttff_gla_off(self):
        """ Test the turn off "Google Location Accuracy" in settings work or not.

        Test steps are executed in the following sequence.
        - Turn off airplane mode
        - Connect to Cellular
        - Turn off LTO/RTO
        - Turn on SUPL
        - Turn off GLA
        - Run CS TTFF

        Expected Results:
        - The position mode must be "standalone"
        - The TTFF time must be between slower than supl_ws and faster than standalone_cs.
        """
        begin_time = get_current_epoch_time()
        gutils.gla_mode(self.ad, False)

        ttff_data = self.run_ttff("cs", self.standalone_cs_criteria)

        asserts.assert_true(any(float(ttff_data[key].ttff_sec) > self.supl_ws_criteria
                                for key in ttff_data.keys()),
                            msg=f"One or more TTFF Cold Start are faster than \
                            test criteria {self.supl_ws_criteria} seconds")

        asserts.assert_true(self.check_position_mode(begin_time, "standalone"),
                                msg=f"Fail to enter the standalone mode")
