#!/usr/bin/env python3.4
#
#   Copyright 2020 - Google
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
"""
    Test Script for CellBroadcast Module Test
"""

import xml.etree.ElementTree as ET
import time
import random
import os
import re

from acts import signals
from acts.logger import epoch_to_log_line_timestamp
from acts.keys import Config
from acts.test_decorators import test_tracker_info
from acts.utils import load_config
from acts.utils import start_standing_subprocess
from acts.utils import wait_for_standing_subprocess
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CARRIER_TEST_CONF_XML_PATH
from acts_contrib.test_utils.tel.tel_defines import NO_SOUND_TIME
from acts_contrib.test_utils.tel.tel_defines import NO_VIBRATION_TIME
from acts_contrib.test_utils.tel.tel_defines import UK_EE
from acts_contrib.test_utils.tel.tel_defines import COLUMBIA_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import JAPAN_EMOBILE
from acts_contrib.test_utils.tel.tel_defines import JAPAN_WIRELESSCITYPLANNING
from acts_contrib.test_utils.tel.tel_defines import JAPAN_DOCOMO
from acts_contrib.test_utils.tel.tel_defines import JAPAN_RAKUTEN
from acts_contrib.test_utils.tel.tel_defines import KOREA_SKT
from acts_contrib.test_utils.tel.tel_defines import KOREA_LGU
from acts_contrib.test_utils.tel.tel_defines import VENEZUELA
from acts_contrib.test_utils.tel.tel_defines import RUSSIA
from acts_contrib.test_utils.tel.tel_defines import RUSSIA_MEGAFON
from acts_contrib.test_utils.tel.tel_defines import TURKEY
from acts_contrib.test_utils.tel.tel_defines import US
from acts_contrib.test_utils.tel.tel_defines import US_SPRINT
from acts_contrib.test_utils.tel.tel_defines import US_USC
from acts_contrib.test_utils.tel.tel_defines import AZERBAIJAN
from acts_contrib.test_utils.tel.tel_defines import CHINA
from acts_contrib.test_utils.tel.tel_defines import SOUTHAFRICA_TELKOM
from acts_contrib.test_utils.tel.tel_defines import GUATEMALA_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import INDIA
from acts_contrib.test_utils.tel.tel_defines import HUNGARY_TELEKOM
from acts_contrib.test_utils.tel.tel_defines import CROATIA_HRVATSKI
from acts_contrib.test_utils.tel.tel_defines import CZECH_TMOBILE
from acts_contrib.test_utils.tel.tel_defines import SLOVAKIA_TELEKOM
from acts_contrib.test_utils.tel.tel_defines import AUSTRIA_MAGENTA
from acts_contrib.test_utils.tel.tel_defines import POLAND_TMOBILE
from acts_contrib.test_utils.tel.tel_defines import AUSTRIA_TMOBILE
from acts_contrib.test_utils.tel.tel_defines import MACEDONIA_TELEKOM
from acts_contrib.test_utils.tel.tel_defines import MONTENEGRO_TELEKOM
from acts_contrib.test_utils.tel.tel_defines import MEXICO
from acts_contrib.test_utils.tel.tel_defines import BAHAMAS
from acts_contrib.test_utils.tel.tel_defines import UKRAINE
from acts_contrib.test_utils.tel.tel_defines import NORWAY
from acts_contrib.test_utils.tel.tel_defines import BULGARIA
from acts_contrib.test_utils.tel.tel_defines import GERMANY_TELEKOM
from acts_contrib.test_utils.tel.tel_defines import QATAR_VODAFONE
from acts_contrib.test_utils.tel.tel_defines import CBR_APEX_PACKAGE
from acts_contrib.test_utils.tel.tel_defines import CLEAR_NOTIFICATION_BAR
from acts_contrib.test_utils.tel.tel_defines import DEFAULT_ALERT_TYPE
from acts_contrib.test_utils.tel.tel_defines import EXPAND_NOTIFICATION_BAR
from acts_contrib.test_utils.tel.tel_defines import COLLAPSE_NOTIFICATION_BAR
from acts_contrib.test_utils.tel.tel_defines import UAE
from acts_contrib.test_utils.tel.tel_defines import JAPAN_KDDI
from acts_contrib.test_utils.tel.tel_defines import NEWZEALAND
from acts_contrib.test_utils.tel.tel_defines import HONGKONG
from acts_contrib.test_utils.tel.tel_defines import CHILE_ENTEL
from acts_contrib.test_utils.tel.tel_defines import CHILE_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import MEXICO_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import ELSALVADOR_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import PERU_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import SPAIN_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import PERU_ENTEL
from acts_contrib.test_utils.tel.tel_defines import KOREA
from acts_contrib.test_utils.tel.tel_defines import TAIWAN
from acts_contrib.test_utils.tel.tel_defines import CANADA
from acts_contrib.test_utils.tel.tel_defines import AUSTRALIA
from acts_contrib.test_utils.tel.tel_defines import BRAZIL
from acts_contrib.test_utils.tel.tel_defines import COLUMBIA
from acts_contrib.test_utils.tel.tel_defines import ECUADOR_TELEFONICA
from acts_contrib.test_utils.tel.tel_defines import ECUADOR_CLARO
from acts_contrib.test_utils.tel.tel_defines import FRANCE
from acts_contrib.test_utils.tel.tel_defines import PUERTORICO
from acts_contrib.test_utils.tel.tel_defines import NETHERLANDS
from acts_contrib.test_utils.tel.tel_defines import ROMANIA
from acts_contrib.test_utils.tel.tel_defines import ESTONIA
from acts_contrib.test_utils.tel.tel_defines import LITHUANIA
from acts_contrib.test_utils.tel.tel_defines import LATVIA
from acts_contrib.test_utils.tel.tel_defines import GREECE
from acts_contrib.test_utils.tel.tel_defines import ITALY
from acts_contrib.test_utils.tel.tel_defines import SOUTHAFRICA
from acts_contrib.test_utils.tel.tel_defines import UK
from acts_contrib.test_utils.tel.tel_defines import US_VZW
from acts_contrib.test_utils.tel.tel_defines import US_ATT
from acts_contrib.test_utils.tel.tel_defines import US_TMO
from acts_contrib.test_utils.tel.tel_defines import ISRAEL
from acts_contrib.test_utils.tel.tel_defines import OMAN
from acts_contrib.test_utils.tel.tel_defines import JAPAN_SOFTBANK
from acts_contrib.test_utils.tel.tel_defines import SAUDIARABIA
from acts_contrib.test_utils.tel.tel_defines import MAIN_ACTIVITY
from acts_contrib.test_utils.tel.tel_defines import CBR_PACKAGE
from acts_contrib.test_utils.tel.tel_defines import SYSUI_PACKAGE
from acts_contrib.test_utils.tel.tel_defines import CBR_ACTIVITY
from acts_contrib.test_utils.tel.tel_defines import CBR_TEST_APK
from acts_contrib.test_utils.tel.tel_defines import MCC_MNC
from acts_contrib.test_utils.tel.tel_defines import IMSI
from acts_contrib.test_utils.tel.tel_defines import PLMN_ADB_PROPERTY
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_FOR_ALERTS_TO_POPULATE
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_FOR_UI
from acts_contrib.test_utils.tel.tel_defines import SCROLL_DOWN
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_ANDROID_STATE_SETTLING
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_FOR_ALERT_TO_RECEIVE
from acts_contrib.test_utils.tel.tel_defines import EXIT_ALERT_LIST
from acts_contrib.test_utils.tel.tel_defines import CMD_DND_OFF
from acts_contrib.test_utils.tel.tel_defines import DUMPSYS_VIBRATION
from acts_contrib.test_utils.tel.tel_defines import DEFAULT_SOUND_TIME
from acts_contrib.test_utils.tel.tel_defines import DEFAULT_VIBRATION_TIME
from acts_contrib.test_utils.tel.tel_defines import DEFAULT_OFFSET
from acts_contrib.test_utils.tel.tel_defines import DIRECTION_MOBILE_ORIGINATED
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_DATA_SUB_CHANGE
from acts_contrib.test_utils.tel.tel_defines import WFC_MODE_WIFI_ONLY
from acts_contrib.test_utils.tel.tel_defines import MAX_WAIT_TIME_WFC_ENABLED
from acts_contrib.test_utils.tel.tel_defines import GEN_5G
from acts_contrib.test_utils.tel.tel_defines import GEN_4G
from acts_contrib.test_utils.tel.tel_defines import GEN_3G
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_log
from acts_contrib.test_utils.tel.tel_test_utils import reboot_device
from acts_contrib.test_utils.tel.tel_test_utils import get_device_epoch_time
from acts_contrib.test_utils.tel.tel_data_utils import wait_for_data_connection
from acts_contrib.test_utils.tel.tel_wifi_utils import wifi_toggle_state
from acts_contrib.test_utils.tel.tel_test_utils import toggle_airplane_mode
from acts_contrib.test_utils.tel.tel_wifi_utils import ensure_wifi_connected
from acts_contrib.test_utils.tel.tel_subscription_utils import get_default_data_sub_id
from acts_contrib.test_utils.net import ui_utils as uutils
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import call_setup_teardown
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_data_for_subscription
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_voice_general
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_on_rat
from acts_contrib.test_utils.net.ui_utils import get_element_attributes
from acts_contrib.test_utils.tel.tel_5g_test_utils import provision_device_for_5g
from acts_contrib.test_utils.tel.tel_ims_utils import set_wfc_mode_for_subscription
from acts_contrib.test_utils.tel.tel_ims_utils import wait_for_wfc_enabled

VIBRATION_START_TIME = "startTime"
VIBRATION_END_TIME = "endTime"
INVALID_VIBRATION_TIME = "0"
INVALID_SUBSCRIPTION_ID = -1
WEA_ROAMING_MSG = "While you're roaming, you may get some alerts" \
                  " that aren't included in these settings"

class CellBroadcastTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        req_param = ["region_plmn_list", "emergency_alert_settings", "emergency_alert_channels", "carrier_test_conf"]
        self.unpack_userparams(req_param_names=req_param)
        if hasattr(self, "region_plmn_list"):
            if isinstance(self.region_plmn_list, list):
                self.region_plmn_list = self.region_plmn_list[0]
            if not os.path.isfile(self.region_plmn_list):
                self.region_plmn_list = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.region_plmn_list)
        if hasattr(self, "emergency_alert_settings"):
            if isinstance(self.emergency_alert_settings, list):
                self.emergency_alert_settings = self.emergency_alert_settings[0]
            if not os.path.isfile(self.emergency_alert_settings):
                self.emergency_alert_settings = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.emergency_alert_settings)
        if hasattr(self, "emergency_alert_channels"):
            if isinstance(self.emergency_alert_channels, list):
                self.emergency_alert_channels = self.emergency_alert_channels[0]
            if not os.path.isfile(self.emergency_alert_channels):
                self.emergency_alert_channels = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.emergency_alert_channels)

        subInfo = self.android_devices[0].droid.subscriptionGetAllSubInfoList()
        self.slot_sub_id_list = {}
        for info in subInfo:
            if info["simSlotIndex"] >= 0:
                self.slot_sub_id_list[info["subscriptionId"]] = info["simSlotIndex"]
        self._check_multisim()
        self.current_sub_id = self.android_devices[0].droid.subscriptionGetDefaultSubId()
        # Sets default sub id to pSIM to avoid crashing if returning INVALID_SUBSCRIPTION_ID.
        if self.current_sub_id == INVALID_SUBSCRIPTION_ID:
            for sub_id in self.slot_sub_id_list.keys():
                if self.slot_sub_id_list[sub_id] == 0:
                    psim_sub_id = sub_id
                    break;
            self.android_devices[0].droid.subscriptionSetDefaultSubId(psim_sub_id)
            self.current_sub_id = self.android_devices[0].droid.subscriptionGetDefaultSubId()

        self.android_devices[0].log.info("Active slot: %d, active subscription id: %d",
                                         self.slot_sub_id_list[self.current_sub_id],
                                         self.current_sub_id)

        if hasattr(self, "carrier_test_conf"):
            if isinstance(self.carrier_test_conf, list):
                self.carrier_test_conf = self.carrier_test_conf[0]
            if not os.path.isfile(self.carrier_test_conf):
                self.carrier_test_conf = os.path.join(
                    self.user_params[Config.key_config_path.value],
                    self.carrier_test_conf)
        self.verify_vibration = self.user_params.get("verify_vibration", True)
        self._disable_vibration_check_for_11()
        self.verify_sound = self.user_params.get("verify_sound", True)
        self.region_plmn_dict = load_config(self.region_plmn_list)
        self.emergency_alert_settings_dict = load_config(self.emergency_alert_settings)
        self.emergency_alert_channels_dict = load_config(self.emergency_alert_channels)
        self._verify_cbr_test_apk_install(self.android_devices[0])
        self.cbr_version = ""
        self.cbr_upgrade_version = ""
        self.cbr_rollback_version = ""


    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        self.number_of_devices = 1

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)


    def _check_multisim(self):
        if "dsds" in self.android_devices[0].adb.shell("getprop | grep persist.radio.multisim.config"):
            self.android_devices[0].log.info("device is operated at DSDS!")
        else:
            self.android_devices[0].log.info("device is operated at single SIM!")


    def _verify_cbr_test_apk_install(self, ad):
        if not ad.is_apk_installed(CBR_TEST_APK):
            cbrtestapk = self.user_params.get("cbrtestapk")
            ad.adb.install("%s" % cbrtestapk)
        else:
            ad.log.debug("%s apk already installed", CBR_TEST_APK)


    def _verify_device_in_specific_region(self, ad, region=None):
        mccmnc = self.region_plmn_dict[region][MCC_MNC]
        plmns = ad.adb.getprop(PLMN_ADB_PROPERTY)
        plmn_list = plmns.split(",")
        current_plmn = plmn_list[self.slot_sub_id_list[self.current_sub_id]]
        if current_plmn == mccmnc:
            ad.log.info("device in %s region", region.upper())
            return True
        else:
            ad.log.info("device not in %s region", region.upper())
            return False

    def _disable_vibration_check_for_11(self):
        if self.android_devices[0].adb.getprop("ro.build.version.release") in ("11", "R"):
            self.verify_vibration = False

    def _get_carrier_test_config_name(self):
        build_version = self.android_devices[0].adb.getprop("ro.build.version.release")
        self.android_devices[0].log.info("The device's android release build version: %s",
                                         build_version)
        slot_index = self.slot_sub_id_list[self.current_sub_id]
        try:
            # S build and below only apply to the single sim, use carrier_test_conf.xml.
            if type(int(build_version)) == int and int(build_version) <= 12:
                return f'{CARRIER_TEST_CONF_XML_PATH}carrier_test_conf.xml'
            # T build and above apply to the dual sim, use carrier_test_conf_sim0.xml or
            # carrier_test_conf_sim1.xml
            return f'{CARRIER_TEST_CONF_XML_PATH}carrier_test_conf_sim{slot_index}.xml'
        except ValueError:
            # S build and below only apply to the single sim, use carrier_test_conf.xml.
            if build_version <= "S":
                return f'{CARRIER_TEST_CONF_XML_PATH}carrier_test_conf.xml'
            # T build and above apply to the dual sim, use carrier_test_conf_sim0.xml or
            # carrier_test_conf_sim1.xml
            return f'{CARRIER_TEST_CONF_XML_PATH}carrier_test_conf_sim{slot_index}.xml'

    def _get_toggle_value(self, ad, alert_text=None):
        if alert_text == "Alerts":
            node = uutils.wait_and_get_xml_node(ad, timeout=30, matching_node=2, text=alert_text)
        else:
            node = uutils.wait_and_get_xml_node(ad, timeout=30, text=alert_text)
        return node.parentNode.nextSibling.firstChild.attributes['checked'].value

    def _wait_and_click(self, ad, alert_text=None):
        if alert_text == "Alerts":
            uutils.wait_and_click(ad, text=alert_text, matching_node=2)
        else:
            uutils.wait_and_click(ad, text=alert_text)

    def _has_element(self, ad, alert_text=None):
        # The Saudiarabia has an alert setting, "Alerts", whose name is the same as the title of
        #  Wireless emergency alerts UI. We have to skip the title node of Wireless emergency
        #  alerts UI. So set matching_node to 2 if searching "Alerts" setting.
        if alert_text == "Alerts":
            element_exist = uutils.has_element(ad, text=alert_text, matching_node=2)
        else:
            element_exist = uutils.has_element(ad, text=alert_text)
        # Checks if the alert title node also has a sibling node for switch button.
        if element_exist:
            if alert_text == "Alerts":
                node = uutils.wait_and_get_xml_node(ad, timeout=30, matching_node=2, text=alert_text)
            else:
                node = uutils.wait_and_get_xml_node(ad, timeout=30, text=alert_text)
            if not node.parentNode.nextSibling.firstChild:
                element_exist = False
        return element_exist

    def _open_wea_settings_page(self, ad):
        ad.adb.shell("am start -a %s -n %s/%s" % (MAIN_ACTIVITY, CBR_PACKAGE, CBR_ACTIVITY))


    def _close_wea_settings_page(self, ad):
        pid = ad.adb.shell("pidof %s" % CBR_PACKAGE, ignore_status=True)
        ad.adb.shell("kill -9 %s" % pid, ignore_status=True)


    def _set_device_to_specific_region(self, ad, region=None):
        """
        Args:
            ad: AndroidDevice
            country: name of country
        """
        # fetch country codes
        mccmnc = self.region_plmn_dict[region][MCC_MNC]
        imsi = self.region_plmn_dict[region][IMSI]
        ad.log.info("setting device to %s with mccmnc %s imsi %s",
                    region.upper(), mccmnc, imsi)

        # update carrier xml file
        tree = ET.parse(self.carrier_test_conf)
        root = tree.getroot()
        root[1].attrib['value'] = mccmnc
        root[2].attrib['value'] = imsi
        tree.write(self.carrier_test_conf)

        # push carrier xml to device
        carrier_test_config_xml = self._get_carrier_test_config_name()
        ad.log.info("push %s to %s" % (self.carrier_test_conf, carrier_test_config_xml))
        ad.adb.push("%s %s" % (self.carrier_test_conf, carrier_test_config_xml))

        # reboot device
        reboot_device(ad)
        # b/259586331#18 reboot twice to ensure the correct subscription info of the new region
        # is correctly loaded to CBR module.
        reboot_device(ad)
        time.sleep(WAIT_TIME_FOR_ALERTS_TO_POPULATE)

        # verify adb property
        if not self._verify_device_in_specific_region(ad, region):
            raise signals.TestSkip("unable to set device to %s region" % region.upper())
        return True


    def _verify_wea_default_settings(self, ad, region=None):
        result = True
        for key, value in self.emergency_alert_settings_dict[region].items():
            alert_text = key
            alert_value = value["default_value"]
            self._open_wea_settings_page(ad)
            # scroll till bottom
            if not self._has_element(ad, alert_text):
                for _ in range(3):
                    ad.adb.shell(SCROLL_DOWN)
                if not self._has_element(ad, alert_text):
                    ad.log.error("UI - %s missing", alert_text)
                    result = False
                    continue
            current_value = self._get_toggle_value(ad, alert_text)
            if current_value == alert_value:
                ad.log.info("UI - %s, default: %s",
                            alert_text, alert_value)
            else:
                ad.log.error("UI - %s, default: %s, expected: %s",
                             alert_text, current_value, alert_value)
                result = False
        return result


    def _verify_wea_toggle_settings(self, ad, region=None):
        result = True
        for key, value in self.emergency_alert_settings_dict[region].items():
            alert_text = key
            alert_toggle = value["toggle_avail"]
            if alert_toggle == "true":
                self._open_wea_settings_page(ad)
                if not self._has_element(ad, alert_text):
                    for _ in range(3):
                        ad.adb.shell(SCROLL_DOWN)
                    if not self._has_element(ad, alert_text):
                        ad.log.error("UI - %s missing", alert_text)
                        result = False
                        continue
                before_toggle = self._get_toggle_value(ad, alert_text)
                self._wait_and_click(ad, alert_text)
                after_toggle = self._get_toggle_value(ad, alert_text)
                if before_toggle == after_toggle:
                    for _ in range(3):
                        ad.adb.shell(SCROLL_DOWN)
                    self._wait_and_click(ad, alert_text)
                    after_toggle = self._get_toggle_value(ad, alert_text)
                    if before_toggle == after_toggle:
                        ad.log.error("UI - fail to toggle %s", alert_text)
                        result = False
                else:
                    self._wait_and_click(ad, alert_text)
                    reset_toggle = self._get_toggle_value(ad, alert_text)
                    if reset_toggle != before_toggle:
                        ad.log.error("UI - fail to reset toggle %s", alert_text)
                        result = False
                    else:
                        ad.log.info("UI - toggle verified for %s", alert_text)
        return result


    def _convert_formatted_time_to_secs(self, formatted_time):
        try:
            time_list = formatted_time.split(":")
            return int(time_list[0]) * 3600 + int(time_list[1]) * 60 + int(time_list[2])
        except Exception as e:
            self.log.error(e)


    def _get_current_time_in_secs(self, ad):
        try:
            c_time = get_device_epoch_time(ad)
            c_time = epoch_to_log_line_timestamp(c_time).split()[1].split('.')[0]
            return self._convert_formatted_time_to_secs(c_time)
        except Exception as e:
            ad.log.error(e)


    def _verify_flashlight(self, ad):
        count = 0
        while(count < 10):
            status = ad.adb.shell("settings get secure flashlight_available")
            if status == "1":
                ad.log.info("LED lights OK")
                return True
        ad.log.error("LED lights not OK")
        return False



    def _verify_vibration(self, ad, begintime, expectedtime, offset):
        if not self.verify_vibration:
            return True
        out = ad.adb.shell(DUMPSYS_VIBRATION)
        if out:
            try:
                starttime = self._get_vibration_time(ad, out, time_info=VIBRATION_START_TIME)
                if starttime == INVALID_VIBRATION_TIME:
                    return False
                endtime = self._get_vibration_time(ad, out, time_info=VIBRATION_END_TIME)
                if endtime == INVALID_VIBRATION_TIME:
                    return False
                starttime = self._convert_formatted_time_to_secs(starttime)
                endtime = self._convert_formatted_time_to_secs(endtime)
                vibration_time = endtime - starttime
                if (starttime < begintime):
                    ad.log.error("vibration: actualtime:%s logtime:%s Not OK", begintime, starttime)
                    return False
                if not vibration_time in range(expectedtime - offset, expectedtime + offset + 1):
                    ad.log.error("vibration: %d secs Not OK", vibration_time)
                    return False
                ad.log.info("vibration: %d secs OK", vibration_time)
                return True
            except Exception as e:
                ad.log.error("vibration parsing is broken %s", e)
                return False
        return False


    def _get_vibration_time(self, ad, out, time_info=VIBRATION_START_TIME):
        vibration_info_list = out.split(',')
        for info in vibration_info_list:
            if time_info in info:
                return info.strip().split(' ')[2].split('.')[0]
        ad.log.error(" Not found %s in the vibration info!", time_info)
        return INVALID_VIBRATION_TIME

    def _verify_sound(self, ad, begintime, expectedtime, offset, calling_package=CBR_PACKAGE):
        if not self.verify_sound:
            return True
        cbr_pid = ad.adb.shell("pidof %s" % calling_package)
        DUMPSYS_START_AUDIO = "dumpsys audio | grep %s | grep requestAudioFocus | tail -1" % cbr_pid
        DUMPSYS_END_AUDIO = "dumpsys audio | grep %s | grep abandonAudioFocus | tail -1" % cbr_pid
        start_audio = ad.adb.shell(DUMPSYS_START_AUDIO)
        end_audio = ad.adb.shell(DUMPSYS_END_AUDIO)
        if start_audio and end_audio:
            try:
                starttime = start_audio.split()[1]
                endtime = end_audio.split()[1]
                starttime = self._convert_formatted_time_to_secs(starttime)
                endtime = self._convert_formatted_time_to_secs(endtime)
                sound_time = endtime - starttime
                if (starttime < begintime):
                    ad.log.error("sound: actualtime:%s logtime:%s Not OK", begintime, starttime)
                    return False
                if not sound_time in range(expectedtime - offset, expectedtime + offset + 1):
                    ad.log.error("sound: %d secs Not OK", sound_time)
                    return False
                ad.log.info("sound: %d secs OK", sound_time)
                return True
            except Exception as e:
                ad.log.error("sound parsing is broken %s", e)
                return False
        return False


    def _exit_alert_pop_up(self, ad):
        for text in EXIT_ALERT_LIST:
            try:
                uutils.wait_and_click(ad, text_contains=text, timeout=1)
            except Exception:
                continue


    def _verify_text_present_on_ui(self, ad, alert_text):
        if uutils.has_element(ad, text=alert_text, timeout=5):
            return True
        elif uutils.has_element(ad, text_contains=alert_text, timeout=5):
            return True
        else:
            return False


    def _log_and_screenshot_alert_fail(self, ad, state, region, channel):
        ad.log.error("Fail for alert: %s for %s: %s", state, region, channel)
        log_screen_shot(ad, "alert_%s_for_%s_%s" % (state, region, channel))


    def _show_statusbar_notifications(self, ad):
        ad.adb.shell(EXPAND_NOTIFICATION_BAR)


    def _hide_statusbar_notifications(self, ad):
        ad.adb.shell(COLLAPSE_NOTIFICATION_BAR)


    def _clear_statusbar_notifications(self, ad):
        ad.adb.shell(CLEAR_NOTIFICATION_BAR)


    def _popup_alert_in_statusbar_notifications(self, ad, alert_text):
        alert_in_notification = False
        # Open status bar notifications.
        self._show_statusbar_notifications(ad)
        # Wait for status bar notifications showing.
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        if self._verify_text_present_on_ui(ad, alert_text):
            # Found alert in notifications, display it.
            uutils.wait_and_click(ad, text=alert_text)
            alert_in_notification = True
        else:
            # Close status bar notifications
            self._hide_statusbar_notifications(ad)
        return alert_in_notification


    def _verify_send_receive_wea_alerts(self, ad, region=None, call=False,
                                        call_direction=DIRECTION_MOBILE_ORIGINATED,
                                        test_channel=None, screen_off=False):
        result = True
        # Always clear notifications in the status bar before testing to find alert notification easily.
        self._clear_statusbar_notifications(ad)
        for key, value in self.emergency_alert_channels_dict[region].items():
            channel = int(key)
            if test_channel:
                if test_channel != channel:
                    continue
            if call:
                if not self._setup_voice_call(self.log,
                                              self.android_devices,
                                              call_direction=call_direction):
                    self.log("Fail to set up voice call!")
                    return False

            # Configs
            iteration_result = True
            alert_text = value["title"]
            alert_expected = value["default_value"]
            wait_for_alert = value.get("alert_time", WAIT_TIME_FOR_ALERT_TO_RECEIVE)
            vibration_time = value.get("vibration_time", DEFAULT_VIBRATION_TIME)
            sound_time = value.get("sound_time", DEFAULT_SOUND_TIME)
            offset = value.get("offset", DEFAULT_OFFSET)
            alert_type = value.get("alert_type", DEFAULT_ALERT_TYPE)
            if sound_time == NO_SOUND_TIME:
                ad.log.info("Skip the verification of sound time because no sound time"
                            + " is defined for the channel!")
                self.verify_sound = False
            if vibration_time == NO_VIBRATION_TIME:
                ad.log.info("Skip the verification of vibration time because no vibration time"
                            + " is defined for the channel!")
                self.verify_vibration = False

            # Begin Iteration
            begintime = self._get_current_time_in_secs(ad)
            sequence_num = random.randrange(10000, 40000)
            ad.log.info("Iteration: %s for %s: %s", alert_text, region, channel)

            # Send Alert
            ad.droid.cbrSendTestAlert(sequence_num, channel)
            if region == NEWZEALAND:
                if not self._verify_flashlight(ad):
                    iteration_result = False

            time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
            if call:
                hangup_call(self.log, ad)

            if screen_off:
                ad.adb.shell("input keyevent KEYCODE_POWER")
            time.sleep(wait_for_alert)
            if screen_off:
                ad.adb.shell("input keyevent KEYCODE_POWER")

            # Receive Alert
            if not self._verify_text_present_on_ui(ad, alert_text):
                alert_in_notification = False
                # Check if alert message is expected to be in the notification drawer
                if alert_expected == "true" and alert_type == "notification":
                    # Verify expected notification in notification drawer and open the message
                    if self._popup_alert_in_statusbar_notifications(ad, alert_text):
                        ad.log.info("Found alert channel %d in status bar notifications, pop it up.", channel)
                        # Verify alert text in message.
                        alert_in_notification = self._verify_text_present_on_ui(ad, alert_text)
                        if alert_in_notification:
                            # Verify vibration and notification sound.
                            # We check sound generated by com.android.systemui package.
                            # For the reason of offset + 1, refer to b/199565843
                            # TODO: The notification sound is initiated by system
                            #  rather than CellBroadcastReceiver. In case there are
                            #  any non-emergency notifications coming during testing, we
                            #  should consider to validate notification id instead of
                            #  com.android.systemui package. b/199565843
                            if not (self._verify_vibration(ad, begintime, vibration_time, offset) and
                                    self._verify_sound(ad, begintime, sound_time, offset+1, SYSUI_PACKAGE)):
                                iteration_result = False
                if alert_expected == "true" and not alert_in_notification:
                    iteration_result = False
                    self._log_and_screenshot_alert_fail(ad, "missing", region, channel)
            else:
                if alert_expected == "true":
                    ad.log.info("Alert received OK")
                    self._exit_alert_pop_up(ad)
                    time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

                    # Vibration and Sound
                    if not self._verify_text_present_on_ui(ad, alert_text):
                        ad.log.info("Alert exited OK")
                        if not (self._verify_vibration(ad, begintime, vibration_time, offset) and
                                self._verify_sound(ad, begintime, sound_time, offset)):
                            iteration_result = False
                    else:
                        iteration_result = False
                        self._log_and_screenshot_alert_fail(ad, "present", region, channel)
                else:
                    iteration_result = False
                    self._log_and_screenshot_alert_fail(ad, "present", region, channel)
            if iteration_result:
                ad.log.info("Success alert: %s for %s: %s", alert_text, region, channel)
            else:
                ad.log.error("Failure alert: %s for %s: %s", alert_text, region, channel)
                result = iteration_result
            self._exit_alert_pop_up(ad)
        return result


    def _set_device_to_roaming_region(self, ad, roaming_region):
        """Sets device to the roaming region."""

        mccmnc = self.region_plmn_dict[roaming_region][MCC_MNC]
        set_roaming_region = f'am broadcast -a com.android.internal.telephony.TestServiceState' \
                                f' --ei voice_roaming_type 1 --es operator test,test,{mccmnc}'
        ad.log.info("Set roaming region to %s", roaming_region)
        ad.log.info(set_roaming_region.format(mccmnc))
        ad.adb.shell(set_roaming_region.format(mccmnc))

    def _verify_device_in_roaming_region(self, ad, roaming_region):
        """Verifies a roaming message appearing in WEA setting UI."""

        result = True
        self._open_wea_settings_page(ad)
        ad.log.info("Verify if WEA Setting UI has a roaming message: '%s' ", WEA_ROAMING_MSG)
        if not self._verify_text_present_on_ui(ad, alert_text=WEA_ROAMING_MSG):
            ad.log.error("Not found a roaming message in WEA Setting UI!")
            result = False
        else:
            ad.log.info("Found a roaming message in WEA Setting UI")
        time.sleep(WAIT_TIME_FOR_UI)
        log_screen_shot(ad, "default_settings_%s" % roaming_region)
        self._close_wea_settings_page(ad)
        return result



    def _settings_test_flow(self, region, roaming=False, roaming_region=None):
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, region)
        time.sleep(WAIT_TIME_FOR_UI)

        # If roaming is enabled, set device to the roaming region.
        if roaming:
            if roaming_region:
                self._set_device_to_roaming_region(ad, roaming_region)
                time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
            else:
                ad.log.error("Roaming region is %s, please set a valid region!", roaming_region)
                result = False

        if not self._verify_wea_default_settings(ad, region):
            result = False
        log_screen_shot(ad, "default_settings_%s" % region)
        self._close_wea_settings_page(ad)
        # Here close wea setting UI and then immediately open the UI that sometimes causes
        # failing to open the wea setting UI. So we just delay 1 sec after closing
        # the wea setting UI.
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)

        # if roaming is enabled, Verify the device is in roaming region.
        if roaming and not self._verify_device_in_roaming_region(ad, roaming_region):
            result = False

        if not self._verify_wea_toggle_settings(ad, region):
            log_screen_shot(ad, "toggle_settings_%s" % region)
            result = False
        get_screen_shot_log(ad)
        self._close_wea_settings_page(ad)
        return result


    def _settings_upgrade_cbr_test_flow(self,
                                        region,
                                        upgrade_cbr_train_build=False,
                                        rollback_cbr_train_build=False):
        """Verifies wea alert settings for upgrade and rollback of a new cbr build.

        The method is also able to verify alert settings on the device UI after
            upgrading a new cbr build and rolling back.
        The full path of the new cbr build to be upgraded should be specified
            in the flag of acts config file, cbr_train_build.

        Args:
            upgrade_cbr_train_build: perform installing cbr build if True.
            rollback_cbr_train_build: perform cbr package rollback if True.
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, region)
        time.sleep(WAIT_TIME_FOR_UI)

        test_iteration = [True, rollback_cbr_train_build]

        if upgrade_cbr_train_build:
            if not self._install_verify_upgrade_cbr_train_build(ad):
                return False;

        for index in range(len(test_iteration)):
            test_wea_default_settings = test_iteration[index]
            if test_wea_default_settings:
                time.sleep(WAIT_TIME_FOR_UI)
                if not self._verify_wea_default_settings(ad, region):
                    result = False
                log_screen_shot(ad, "default_settings_%s" % region)
                self._close_wea_settings_page(ad)
                # Here close wea setting UI and then immediately open the UI that sometimes causes
                # failing to open the wea setting UI. So we just delay 1 sec after closing
                # the wea setting UI.
                time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
                if not self._verify_wea_toggle_settings(ad, region):
                    log_screen_shot(ad, "toggle_settings_%s" % region)
                    result = False
                get_screen_shot_log(ad)
                self._close_wea_settings_page(ad)

                if index == 0 and rollback_cbr_train_build:
                    if not self._rollback_verify_original_cbr_build(ad):
                        return False;

        return result


    def _send_receive_test_flow(self, region, test_channel=None,
                                roaming=False, roaming_region=None):
        """Verifies wea alert channels.

        Args:
            test_channel: specify a specific alert channel to be tested. Otherwise,
                          all alert channels will be tested.
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, region)

        # If roaming is enabled, set device to the roaming region.
        if roaming:
            if roaming_region:
                self._set_device_to_roaming_region(ad, roaming_region)
            else:
                ad.log.error("Roaming region is %s, please set a valid region!", roaming_region)
                result = False

        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)

        # if roaming is enabled, verify alert channels of the roaming region.
        if roaming:
            ad.log.info("Verifies alert channels of the roaming region, %s!", roaming_region)
            if not self._verify_send_receive_wea_alerts(ad, roaming_region,
                                                        test_channel=test_channel):
                result = False
        else:
            # Verify alert channels of the home region.
            if not self._verify_send_receive_wea_alerts(ad, region, test_channel=test_channel):
                result = False
        get_screen_shot_log(ad)
        return result


    def _send_receive_upgrade_cbr_test_flow(self, region,
                                            test_channel=None,
                                            upgrade_cbr_train_build=False,
                                            rollback_cbr_train_build=False):
        """Verifies wea alert channels for upgrade and rollback of a new cbr build.

        The method is also able to verify alert channels on the device after upgrading
            a new cbr build and rolling back.
        The full path of the new cbr build to be upgraded should be specified in the flag
            of acts config file, cbr_train_build.
        Args:
            test_channel: specify a specific alert channel to be tested. Otherwise,
                            all alert channels will be tested.
            upgrade_cbr_train_build: perform installing cbr build if True.
            rollback_cbr_train_build: perform cbr package rollback if True.
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, region)

        test_iteration = [True, rollback_cbr_train_build]

        if upgrade_cbr_train_build:
            if not self._install_verify_upgrade_cbr_train_build(ad):
                return False;

        for index in range(len(test_iteration)):
            test_wea_default_settings = test_iteration[index]
            if test_wea_default_settings:
                time.sleep(WAIT_TIME_FOR_UI)
                ad.log.info("disable DND: %s", CMD_DND_OFF)
                ad.adb.shell(CMD_DND_OFF)
                if not self._verify_send_receive_wea_alerts(ad,
                                                            region,
                                                            test_channel=test_channel):
                    result = False
                get_screen_shot_log(ad)
                if index == 0 and rollback_cbr_train_build:
                    if not self._rollback_verify_original_cbr_build(ad):
                        return False;
        return result


    def _setup_receive_test_flow_wifi(self, region, gen, data):
        """ Setup send/receive WEA with wifi enabled and various RAT."""
        ad = self.android_devices[0]
        self._set_device_to_specific_region(ad, region)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        if gen == GEN_5G:
            if not provision_device_for_5g(self.log, ad):
                return False
        else:
            phone_setup_data_for_subscription(ad.log,
                                              ad,
                                              get_default_data_sub_id(ad),
                                              gen)
        if data:
            ad.log.info("Enable data network!")
        else:
            ad.log.info("Disable data network!")
        ad.droid.telephonyToggleDataConnection(data)
        if not wait_for_data_connection(ad.log, ad, data,
                                        MAX_WAIT_TIME_DATA_SUB_CHANGE):
            if data:
                ad.log.error("Failed to enable data network!")
            else:
                ad.log.error("Failed to disable data network!")
            return False

        wifi_toggle_state(ad.log, ad, True)
        if not ensure_wifi_connected(ad.log, ad,
                                     self.wifi_network_ssid,
                                     self.wifi_network_pass):
            ad.log.error("WiFi connect fail.")
            return False
        return True

    def _setup_voice_call(self, log, ads, call_direction=DIRECTION_MOBILE_ORIGINATED):
        if call_direction == DIRECTION_MOBILE_ORIGINATED:
            ad_caller = ads[0]
            ad_callee = ads[1]
        else:
            ad_caller = ads[1]
            ad_callee = ads[0]
        return call_setup_teardown(log, ad_caller, ad_callee, wait_time_in_call=0)

    def _setup_wfc_mode(self, ad):
        if not set_wfc_mode_for_subscription(ad,
                                             WFC_MODE_WIFI_ONLY,
                                             get_default_data_sub_id(ad)):
            ad.log.error("Unable to set WFC mode to %s.", WFC_MODE_WIFI_ONLY)
            return False

        if not wait_for_wfc_enabled(ad.log, ad, max_time=MAX_WAIT_TIME_WFC_ENABLED):
            ad.log.error("WFC is not enabled")
            return False
        return True

    def _execute_command_line(self, ad, command):
        """ Executes command line.

        Returns:
            [True, stdout] if successful. Otherwise, [False, stderr].
        """
        ad.log.info("Execute %s", command)
        install_proc = start_standing_subprocess(command)
        wait_for_standing_subprocess(install_proc)
        out, err = install_proc.communicate()
        if err:
            ad.log.error("stderr: %s", err.decode('utf-8'))
            return [False, err.decode('utf-8')]
        ad.log.info("stdout: %s",out.decode('utf-8'))
        return [True, out.decode('utf-8')]


    def _get_current_cbr_build_version_code(self, ad):
        """ Gets the version code of CBR package.

        Returns:
            Version code if the rollback is successful. Otherwise, None.
        """
        cbr_version_code_command = f'adb shell pm list packages --apex-only' + \
                                   f' --show-versioncode | grep {CBR_APEX_PACKAGE}'
        result, out = self._execute_command_line(ad, cbr_version_code_command)
        if not result:
            return None
        version_code_regex = f'^package:{CBR_APEX_PACKAGE}\sversionCode:(\d+)$'
        version_code = re.findall(version_code_regex, out)
        if not version_code:
            ad.log.error("Fail to filter version code, check the match pattern: %s!",
                         version_code_regex)
            return None
        ad.log.info("The version of %s in the device is %s.", CBR_APEX_PACKAGE, version_code)

        return version_code



    def _install_cbr_train_build(self, ad):
        """ Installs a rollback-enabled CBR train build.

        Returns:
            True if the rollback is successful. Otherwise, False.
        """

        cbr_train_build = self.user_params.get("cbr_train_build", "")
        if not cbr_train_build:
            ad.log.error("Not define 'cbr_train_build' flag in the config file.")
            return False
        ad.log.info("Install %s.", cbr_train_build)
        cbr_install_command = f'adb install-multi-package  --staged' \
                              f' --enable-rollback {cbr_train_build}'
        result, out = self._execute_command_line(ad, cbr_install_command)

        return result

    def _install_verify_upgrade_cbr_train_build(self, ad):
        """Installs and verifies upgraded cbr train build."""

        self.cbr_version = self._get_current_cbr_build_version_code(ad)
        if self.cbr_version is None:
            ad.log.error("Unexpectedly Fail to get cbr version.")
            return False;
        if not self._install_cbr_train_build(ad):
            return False
        reboot_device(ad)
        self.cbr_upgrade_version = self._get_current_cbr_build_version_code(ad)
        if self.cbr_upgrade_version is None:
            ad.log.error("Unexpectedly Fail to get cbr version.")
            return False;
        ad.log.info("The new installed cbr version is %s", self.cbr_upgrade_version)
        if self.cbr_version[0] == self.cbr_upgrade_version[0]:
            ad.log.error("The upgrade version shouldn't be the same as the original version,"
                         " roll back the cbr build!")
            return False;
        return True;

    def _rollback_cbr_train_build(self, ad):
        """ Rolls back to the previous CBR build.

        Returns:
            True if the rollback is successful. Otherwise, False.
         """
        ad.log.info("Roll back CBR module...")
        cbr_rollback_command = f'adb shell pm rollback-app {CBR_APEX_PACKAGE}'
        result, out = self._execute_command_line(ad, cbr_rollback_command)

        return result

    def _rollback_verify_original_cbr_build(self, ad):
        """ Checks if the cbr build is rolled back the factory build. """
        if not self._rollback_cbr_train_build(ad):
            return False
        reboot_device(ad)
        self.cbr_rollback_version = self._get_current_cbr_build_version_code(ad)
        ad.log.info("The rollback cbr version is %s", self.cbr_rollback_version)
        if self.cbr_version[0] != self.cbr_rollback_version[0]:
            ad.log.error("The rollback version should be the same as the original version!")
            return False;
        return True

    """ Tests Begin """


    @test_tracker_info(uuid="a4df03a7-2e44-4f8a-8d62-18435d92fc75")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_uae(self):
        """ Verifies Wireless Emergency Alert settings for UAE

        configures the device to UAE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(UAE)


    @test_tracker_info(uuid="ac4639ca-b77e-4200-b3f0-9079e2783f60")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_australia(self):
        """ Verifies Wireless Emergency Alert settings for Australia

        configures the device to Australia
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(AUSTRALIA)


    @test_tracker_info(uuid="d0255023-d9bb-45c5-bede-446d720e619a")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_france(self):
        """ Verifies Wireless Emergency Alert settings for France

        configures the device to France
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(FRANCE)


    @test_tracker_info(uuid="fd461335-21c0-470c-aca7-74c8ebb67711")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_kddi(self):
        """ Verifies Wireless Emergency Alert settings for Japan (KDDI)

        configures the device to KDDI carrier on Japan
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_KDDI)


    @test_tracker_info(uuid="63806dbe3-3cce-4b03-b92c-18529f81b7c5")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_newzealand(self):
        """ Verifies Wireless Emergency Alert settings for NZ

        configures the device to NZ
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(NEWZEALAND)


    @test_tracker_info(uuid="426a295e-f64b-43f7-a0df-3959f07ff568")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_hongkong(self):
        """ Verifies Wireless Emergency Alert settings for HongKong

        configures the device to HongKong
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(HONGKONG)


    @test_tracker_info(uuid="d9e2dca2-4965-48d5-9d79-352c4ccf9e0f")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_chile_entel(self):
        """ Verifies Wireless Emergency Alert settings for Chile_Entel

        configures the device to Chile_Entel
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CHILE_ENTEL)


    @test_tracker_info(uuid="2a045a0e-145c-4677-b454-b0b63a69ea10")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_chile_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Chile_Telefonica

        configures the device to Chile_Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CHILE_TELEFONICA)


    @test_tracker_info(uuid="77cff297-fe3b-4b4c-b502-5324b4e91506")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_peru_entel(self):
        """ Verifies Wireless Emergency Alert settings for Peru_Entel

        configures the device to Peru_Entel
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(PERU_ENTEL)


    @test_tracker_info(uuid="8b683505-288f-4587-95f2-9a8705476f09")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_peru_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Peru_Telefonica

        configures the device to Peru_Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(PERU_TELEFONICA)


    @test_tracker_info(uuid="087da90b-d847-4bf7-8504-4006bb1d6816")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_spain_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Spain_Telefonica

        configures the device to Spain_Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(SPAIN_TELEFONICA)


    @test_tracker_info(uuid="cc0e0f64-2c77-4e20-b55e-6f555f7ecb97")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_elsalvador_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Elsalvador_Telefonica

        configures the device to Elsalvador_Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ELSALVADOR_TELEFONICA)


    @test_tracker_info(uuid="339be9ef-7e0e-463a-ad45-12b7e74bb1c4")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_mexico_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Mexico_Telefonica

        configures the device to Mexico_Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(MEXICO_TELEFONICA)


    @test_tracker_info(uuid="4c3c4e65-c624-4eba-9a81-263f4ee01e12")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_korea(self):
        """ Verifies Wireless Emergency Alert settings for Korea

        configures the device to Korea
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(KOREA)


    @test_tracker_info(uuid="fbaf258e-b596-4bfa-a20f-4b93fc4ccc4c")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_taiwan(self):
        """ Verifies Wireless Emergency Alert settings for Taiwan

        configures the device to Taiwan
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(TAIWAN)


    @test_tracker_info(uuid="3f8e4110-a7d3-4b3b-ac2b-36ea17cfc141")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_canada(self):
        """ Verifies Wireless Emergency Alert settings for Canada

        configures the device to Canada
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CANADA)


    @test_tracker_info(uuid="fa0cd219-b0f2-4a38-8733-cd4212a954c5")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_brazil(self):
        """ Verifies Wireless Emergency Alert settings for Brazil

        configures the device to Brazil
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(BRAZIL)


    @test_tracker_info(uuid="581ecebe-9f68-4270-ab5d-182b1ee4e13b")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_columbia(self):
        """ Verifies Wireless Emergency Alert settings for Columbia

        configures the device to Columbia
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(COLUMBIA)


    @test_tracker_info(uuid="2ebfc05b-3512-4eff-9c09-5d8f49fe0b5e")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_ecuador_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for Ecuador Telefonica

        configures the device to Ecuador Telefonica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ECUADOR_TELEFONICA)


    @test_tracker_info(uuid="694bf8f6-9e6e-46b4-98df-c7ab1a9a3ec8")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_ecuador_claro(self):
        """ Verifies Wireless Emergency Alert settings for Ecuador Claro

        configures the device to Ecuador Claro
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ECUADOR_CLARO)


    @test_tracker_info(uuid="96628975-a23f-47f7-ab18-1aa7a7dc08b5")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_puertorico(self):
        """ Verifies Wireless Emergency Alert settings for Puertorico

        configures the device to Puertorico
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(PUERTORICO)


    @test_tracker_info(uuid="9f73f7ec-cb2a-45e5-8829-db14798dcdac")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_netherlands(self):
        """ Verifies Wireless Emergency Alert settings for Netherlands

        configures the device to Netherlands
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(NETHERLANDS)


    @test_tracker_info(uuid="b3caf3b4-3024-4431-9a7a-4982e20b178b")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_romania(self):
        """ Verifies Wireless Emergency Alert settings for Romania

        configures the device to Romania
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ROMANIA)


    @test_tracker_info(uuid="081a5329-d23f-4df8-b472-d4f3ca5ee3c1")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_estonia(self):
        """ Verifies Wireless Emergency Alert settings for Estonia

        configures the device to Estonia
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ESTONIA)


    @test_tracker_info(uuid="7e0d3b96-f11c-44d9-b3a3-9ce9e21bf37d")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_lithuania(self):
        """ Verifies Wireless Emergency Alert settings for Lithuania

        configures the device to Lithuania
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(LITHUANIA)


    @test_tracker_info(uuid="b40648a0-d04f-4c45-9051-76e64756ef00")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_latvia(self):
        """ Verifies Wireless Emergency Alert settings for Latvia

        configures the device to Latvia
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(LATVIA)


    @test_tracker_info(uuid="9488a6ef-2903-421d-adec-fd65df3aac60")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_greece(self):
        """ Verifies Wireless Emergency Alert settings for Greece

        configures the device to Greece
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(GREECE)


    @test_tracker_info(uuid="53cf276e-8617-45ce-b3f5-e8995b4be279")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_italy(self):
        """ Verifies Wireless Emergency Alert settings for Italy

        configures the device to Italy
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ITALY)


    @test_tracker_info(uuid="a1a57aa8-c229-4f04-bc65-1f17688159a1")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_southafrica(self):
        """ Verifies Wireless Emergency Alert settings for SouthAfrica

        configures the device to SouthAfrica
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(SOUTHAFRICA)


    @test_tracker_info(uuid="a0ed231e-07e0-4dc8-a071-14ec7818e96f")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_uk(self):
        """ Verifies Wireless Emergency Alert settings for UK

        configures the device to UK
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(UK)


    @test_tracker_info(uuid="00c77647-0986-41f8-9202-cc0e2e51e278")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_israel(self):
        """ Verifies Wireless Emergency Alert settings for Israel

        configures the device to Israel
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(ISRAEL)


    @test_tracker_info(uuid="7f2ca9f5-31f6-4477-9383-5acd1ed2598f")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_oman(self):
        """ Verifies Wireless Emergency Alert settings for Oman

        configures the device to Oman
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(OMAN)


    @test_tracker_info(uuid="97525c27-3cba-4472-b00d-d5dabc5a2fe5")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_softbank(self):
        """ Verifies Wireless Emergency Alert settings for Japan (Softbank)

        configures the device to Japan (Softbank)
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_SOFTBANK)


    @test_tracker_info(uuid="109494df-3ae2-4b77-ae52-fb0c22e654c8")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_saudiarabia(self):
        """ Verifies Wireless Emergency Alert settings for SaudiArabia

        configures the device to SaudiArabia
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(SAUDIARABIA)


    @test_tracker_info(uuid="a5f232c4-e0fa-4ce6-aa00-c838f0d86272")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_att(self):
        """ Verifies Wireless Emergency Alert settings for US ATT

        configures the device to US ATT
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US_ATT)


    @test_tracker_info(uuid="a712c136-8ce9-4bc2-9dda-05ecdd11e8ad")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_tmo(self):
        """ Verifies Wireless Emergency Alert settings for US TMO

        configures the device to US TMO
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US_TMO)


    @test_tracker_info(uuid="20403705-f627-42d7-9dc2-4e820273a622")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_vzw(self):
        """ Verifies Wireless Emergency Alert settings for US VZW

        configures the device to US VZW
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US_VZW)


    @test_tracker_info(uuid="fb4cda9e-7b4c-469e-a480-670bfb9dc6d7")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_germany_telekom(self):
        """ Verifies Wireless Emergency Alert settings for Germany telecom

        configures the device to Germany telecom
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(GERMANY_TELEKOM)


    @test_tracker_info(uuid="f4afbef9-c1d7-4fab-ad0f-e03bc961a689")
    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_qatar_vodafone(self):
        """ Verifies Wireless Emergency Alert settings for Qatar vodafone

        configures the device to Qatar vodafone
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(QATAR_VODAFONE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_mexico(self):
        """ Verifies Wireless Emergency Alert settings for Mexico

        configures the device to Mexico
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(MEXICO)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_bahamas(self):
        """ Verifies Wireless Emergency Alert settings for Bahamas

        configures the device to Bahamas
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(BAHAMAS)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_uk_ee(self):
        """ Verifies Wireless Emergency Alert settings for UK_EE

        configures the device to UK EE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(UK_EE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_columbia_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for COLUMBIA_TELEFONICA

        configures the device to COLUMBIA TELEFONICA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(COLUMBIA_TELEFONICA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_emobile(self):
        """ Verifies Wireless Emergency Alert settings for JAPAN_EMOBILE

        configures the device to JAPAN EMOBILE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_EMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_wirelesscityplanning(self):
        """ Verifies Wireless Emergency Alert settings for JAPAN_WIRELESSCITYPLANNING

        configures the device to JAPAN WIRELESS CITY PLANNING
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_WIRELESSCITYPLANNING)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_docomo(self):
        """ Verifies Wireless Emergency Alert settings for JAPAN_DOCOMO

        configures the device to JAPAN DOCOMO
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_DOCOMO)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_rakuten(self):
        """ Verifies Wireless Emergency Alert settings for JAPAN_RAKUTEN

        configures the device to JAPAN RAKUTEN
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(JAPAN_RAKUTEN)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_korea_skt(self):
        """ Verifies Wireless Emergency Alert settings for KOREA_SKT

        configures the device to KOREA SKT
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(KOREA_SKT)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_korea_lgu(self):
        """ Verifies Wireless Emergency Alert settings for KOREA_LGU

        configures the device to KOREA LGU
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(KOREA_LGU)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_venezuela(self):
        """ Verifies Wireless Emergency Alert settings for VENEZUELA

        configures the device to VENEZUELA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(VENEZUELA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_russia(self):
        """ Verifies Wireless Emergency Alert settings for RUSSIA

        configures the device to RUSSIA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(RUSSIA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_russia_megafon(self):
        """ Verifies Wireless Emergency Alert settings for RUSSIA_MEGAFON

        configures the device to RUSSIA MEGAFON
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(RUSSIA_MEGAFON)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_turkey(self):
        """ Verifies Wireless Emergency Alert settings for TURKEY

        configures the device to TURKEY
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(TURKEY)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us(self):
        """ Verifies Wireless Emergency Alert settings for US

        configures the device to US
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_sprint(self):
        """ Verifies Wireless Emergency Alert settings for US_SPRINT

        configures the device to US SPRINT
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US_SPRINT)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_usc(self):
        """ Verifies Wireless Emergency Alert settings for US_USC

        configures the device to US USC
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(US_USC)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_azerbaijan(self):
        """ Verifies Wireless Emergency Alert settings for AZERBAIJAN

        configures the device to AZERBAIJAN
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(AZERBAIJAN)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_china(self):
        """ Verifies Wireless Emergency Alert settings for CHINA

        configures the device to CHINA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CHINA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_southafrica_telkom(self):
        """ Verifies Wireless Emergency Alert settings for SOUTHAFRICA_TELKOM

        configures the device to SOUTH AFRICA TELKOM
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(SOUTHAFRICA_TELKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_guatemala_telefonica(self):
        """ Verifies Wireless Emergency Alert settings for GUATEMALA_TELEFONICA

        configures the device to GUATEMALA TELEFONICA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(GUATEMALA_TELEFONICA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_india(self):
        """ Verifies Wireless Emergency Alert settings for INDIA

        configures the device to INDIA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(INDIA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_hungary_telekom(self):
        """ Verifies Wireless Emergency Alert settings for HUNGARY_TELEKOM

        configures the device to HUNGARY TELEKOM
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(HUNGARY_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_croatia_hrvatski(self):
        """ Verifies Wireless Emergency Alert settings for CROATIA_HRVATSKI

        configures the device to CROATIA HRVATSKI
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CROATIA_HRVATSKI)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_czech_tmobile(self):
        """ Verifies Wireless Emergency Alert settings for CZECH_TMOBILE

        configures the device to CZECH TMOBILE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(CZECH_TMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_slovakia_telekom(self):
        """ Verifies Wireless Emergency Alert settings for SLOVAKIA_TELEKOM

        configures the device to SLOVAKIA TELEKOM
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(SLOVAKIA_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_austria_magenta(self):
        """ Verifies Wireless Emergency Alert settings for AUSTRIA_MAGENTA

        configures the device to AUSTRIA MAGENTA
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(AUSTRIA_MAGENTA)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_poland_tmobile(self):
        """ Verifies Wireless Emergency Alert settings for POLAND_TMOBILE

        configures the device to POLAND TMOBILE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(POLAND_TMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_austria_tmobile(self):
        """ Verifies Wireless Emergency Alert settings for AUSTRIA_TMOBILE

        configures the device to AUSTRIA TMOBILE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(AUSTRIA_TMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_macedonia_telekom(self):
        """ Verifies Wireless Emergency Alert settings for MACEDONIA_TELEKOM

        configures the device to MACEDONIA TELEKOM
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(MACEDONIA_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_montenegro_telekom(self):
        """ Verifies Wireless Emergency Alert settings for MONTENEGRO_TELEKOM

        configures the device to MONTENEGRO TELEKOM
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(MONTENEGRO_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_ukraine(self):
        """ Verifies Wireless Emergency Alert settings for UKRAINE

        configures the device to UKRAINE
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(UKRAINE)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_norway(self):
        """ Verifies Wireless Emergency Alert settings for NORWAY

        configures the device to NORWAY
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(NORWAY)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_bulgaria(self):
        """ Verifies Wireless Emergency Alert settings for Bulgaria

        configures the device to Bulgaria
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(BULGARIA)

    @test_tracker_info(uuid="f3a99475-a23f-427c-a371-d2a46d357d75")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_australia(self):
        """ Verifies Wireless Emergency Alerts for AUSTRALIA

        configures the device to AUSTRALIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(AUSTRALIA)


    @test_tracker_info(uuid="73c98624-2935-46ea-bf7c-43c431177ebd")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_brazil(self):
        """ Verifies Wireless Emergency Alerts for BRAZIL

        configures the device to BRAZIL
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(BRAZIL)


    @test_tracker_info(uuid="8c2e16f8-9b7f-4733-a65e-f087d2480e92")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_canada(self):
        """ Verifies Wireless Emergency Alerts for CANADA

        configures the device to CANADA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CANADA)


    @test_tracker_info(uuid="feea4e42-99cc-4075-bd78-15b149cb2e4c")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_chile_entel(self):
        """ Verifies Wireless Emergency Alerts for CHILE_ENTEL

        configures the device to CHILE_ENTEL
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CHILE_ENTEL)


    @test_tracker_info(uuid="d2ec84ad-7f9a-4aa2-97e8-ca9ffa6c58a7")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_chile_telefonica(self):
        """ Verifies Wireless Emergency Alerts for CHILE_TELEFONICA

        configures the device to CHILE_TELEFONICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CHILE_TELEFONICA)


    @test_tracker_info(uuid="4af30b94-50ea-4e19-8866-31fd3573a059")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_columbia(self):
        """ Verifies Wireless Emergency Alerts for COLUMBIA

        configures the device to COLUMBIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(COLUMBIA)


    @test_tracker_info(uuid="2378b651-2097-48e6-b409-885bde9f4586")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_ecuador_telefonica(self):
        """ Verifies Wireless Emergency Alerts for ECUADOR Telefonica

        configures the device to ECUADOR Telefonica
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ECUADOR_TELEFONICA)


    @test_tracker_info(uuid="cd064259-6cb2-460b-8225-de613f6cf967")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_ecuador_claro(self):
        """ Verifies Wireless Emergency Alerts for ECUADOR Claro

        configures the device to ECUADOR Claro
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ECUADOR_CLARO)


    @test_tracker_info(uuid="b11d1dd7-2090-463a-ba3a-39703db7f376")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_elsalvador_telefonica(self):
        """ Verifies Wireless Emergency Alerts for ELSALVADOR telefonica

        configures the device to ELSALVADOR telefonica
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ELSALVADOR_TELEFONICA)


    @test_tracker_info(uuid="46d6c612-21df-476e-a41b-3baa621b52f0")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_estonia(self):
        """ Verifies Wireless Emergency Alerts for ESTONIA

        configures the device to ESTONIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ESTONIA)


    @test_tracker_info(uuid="6de32af0-9545-4143-b327-146e4d0af28c")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_france(self):
        """ Verifies Wireless Emergency Alerts for FRANCE

        configures the device to FRANCE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(FRANCE)


    @test_tracker_info(uuid="9c5826db-0457-4c6f-9d06-6973b5f77e3f")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_greece(self):
        """ Verifies Wireless Emergency Alerts for GREECE

        configures the device to GREECE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(GREECE)


    @test_tracker_info(uuid="57dd9a79-6ac2-41c7-b7eb-3afb01f35bd2")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_hongkong(self):
        """ Verifies Wireless Emergency Alerts for Japan HONGKONG

        configures the device to HONGKONG
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(HONGKONG)


    @test_tracker_info(uuid="8ffdfaf8-5925-4e66-be22-e1ac25165784")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_israel(self):
        """ Verifies Wireless Emergency Alerts for ISRAEL

        configures the device to ISRAEL
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ISRAEL)


    @test_tracker_info(uuid="f38e289c-4c7d-48a7-9b21-f7d872e3eb98")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_italy(self):
        """ Verifies Wireless Emergency Alerts for ITALY

        configures the device to ITALY
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ITALY)


    @test_tracker_info(uuid="d434dbf8-72e8-44a7-ab15-d418133088c6")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_kddi(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_KDDI

        configures the device to JAPAN_KDDI
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_KDDI)


    @test_tracker_info(uuid="c597995f-8937-4987-91db-7f83a0f5f4ec")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_softbank(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_SOFTBANK

        configures the device to JAPAN_SOFTBANK
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_SOFTBANK)


    @test_tracker_info(uuid="b159d6b2-b900-4329-9b77-c9ba9e83dddc")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_korea(self):
        """ Verifies Wireless Emergency Alerts for KOREA

        configures the device to KOREA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(KOREA)


    @test_tracker_info(uuid="9b59c594-179a-44d6-9dbf-68adc43aa820")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_latvia(self):
        """ Verifies Wireless Emergency Alerts for LATVIA

        configures the device to LATVIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(LATVIA)


    @test_tracker_info(uuid="af7d916b-42f0-4420-8a1c-b39d3f184953")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_lithuania(self):
        """ Verifies Wireless Emergency Alerts for LITHUANIA

        configures the device to LITHUANIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(LITHUANIA)


    @test_tracker_info(uuid="061cd0f3-cefa-4e5d-a1aa-f6125ccf9347")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_mexico_telefonica(self):
        """ Verifies Wireless Emergency Alerts for MEXICO telefonica

        configures the device to MEXICO telefonica
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(MEXICO_TELEFONICA)


    @test_tracker_info(uuid="a9c7cdbe-5a9e-49fb-af60-953e8c1547c0")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_netherlands(self):
        """ Verifies Wireless Emergency Alerts for NETHERLANDS

        configures the device to NETHERLANDS
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(NETHERLANDS)


    @test_tracker_info(uuid="23db0b77-1a1c-494c-bcc6-1355fb037a6f")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_newzealand(self):
        """ Verifies Wireless Emergency Alerts for NEWZEALAND

        configures the device to NEWZEALAND
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(NEWZEALAND)


    @test_tracker_info(uuid="a4216cbb-4ed7-4e72-98e7-2ebebe904956")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_oman(self):
        """ Verifies Wireless Emergency Alerts for OMAN

        configures the device to OMAN
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(OMAN)


    @test_tracker_info(uuid="35f0f156-1555-4bf1-98b1-b5848d8e2d39")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_peru_entel(self):
        """ Verifies Wireless Emergency Alerts for PERU_ENTEL

        configures the device to PERU_ENTEL
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(PERU_ENTEL)


    @test_tracker_info(uuid="4708c783-ca89-498d-b74c-a6bc9df3fb32")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_peru_telefonica(self):
        """ Verifies Wireless Emergency Alerts for PERU_TELEFONICA

        configures the device to PERU_TELEFONICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(PERU_TELEFONICA)


    @test_tracker_info(uuid="73a4cefc-42e1-4e68-9680-1ac135f424a4")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_spain_telefonica(self):
        """ Verifies Wireless Emergency Alerts for SPAIN_TELEFONICA

        configures the device to SPAIN_TELEFONICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(SPAIN_TELEFONICA)


    @test_tracker_info(uuid="fefb293a-5c22-45b2-9323-ccb355245c9a")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_puertorico(self):
        """ Verifies Wireless Emergency Alerts for PUERTORICO

        configures the device to PUERTORICO
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(PUERTORICO)


    @test_tracker_info(uuid="7df5a2fd-fc20-46a1-8a57-c7690daf97ff")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_romania(self):
        """ Verifies Wireless Emergency Alerts for ROMANIA

        configures the device to ROMANIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(ROMANIA)


    @test_tracker_info(uuid="cb1a2e92-eddb-4d8a-8b8d-96a0b8c558dd")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_saudiarabia(self):
        """ Verifies Wireless Emergency Alerts for SAUDIARABIA

        configures the device to SAUDIARABIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(SAUDIARABIA)


    @test_tracker_info(uuid="0bf0196a-e456-4fa8-a735-b8d6d014ce7f")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_southafrica(self):
        """ Verifies Wireless Emergency Alerts for SOUTHAFRICA

        configures the device to SOUTHAFRICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(SOUTHAFRICA)


    @test_tracker_info(uuid="513c7d24-4957-49a4-98a2-f8a9444124ae")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_taiwan(self):
        """ Verifies Wireless Emergency Alerts for TAIWAN

        configures the device to TAIWAN
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(TAIWAN)


    @test_tracker_info(uuid="43d54588-95e2-4e8a-b322-f6c99b9d3fbb")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_uae(self):
        """ Verifies Wireless Emergency Alerts for UAE

        configures the device to UAE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(UAE)


    @test_tracker_info(uuid="b44425c3-0d5b-498a-8322-86cc03eefd7d")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_uk(self):
        """ Verifies Wireless Emergency Alerts for UK

        configures the device to UK
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(UK)


    @test_tracker_info(uuid="b3e73b61-6232-44f0-9507-9954387ab25b")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us_att(self):
        """ Verifies Wireless Emergency Alerts for US ATT

        configures the device to US ATT
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US_ATT)


    @test_tracker_info(uuid="f993d21d-c240-4196-8015-ea8f5967fdb3")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us_tmo(self):
        """ Verifies Wireless Emergency Alerts for US TMO

        configures the device to US TMO
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US_TMO)


    @test_tracker_info(uuid="173293f2-4876-4891-ad2c-2b0d5269b2e0")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us_vzw(self):
        """ Verifies Wireless Emergency Alerts for US Verizon

        configures the device to US Verizon
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US_VZW)


    @test_tracker_info(uuid="b94cc715-d2e2-47a4-91cd-acb47d64e6b2")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_germany_telekom(self):
        """ Verifies Wireless Emergency Alerts for Germany telekom

        configures the device to Germany telekom
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(GERMANY_TELEKOM)


    @test_tracker_info(uuid="f0b0cdbf-32c4-4dfd-b8fb-03d8b6169fd1")
    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_qatar_vodafone(self):
        """ Verifies Wireless Emergency Alerts for Qatar vodafone.

        configures the device to Qatar vodafone
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(QATAR_VODAFONE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_mexico(self):
        """ Verifies Wireless Emergency Alerts for Mexico.

        configures the device to Mexico
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(MEXICO)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_bahamas(self):
        """ Verifies Wireless Emergency Alerts for Bahamas.

        configures the device to Bahamas
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(BAHAMAS)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_uk_ee(self):
        """ Verifies Wireless Emergency Alerts for UK_EE.

        configures the device to UK EE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(UK_EE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_columbia_telefonica(self):
        """ Verifies Wireless Emergency Alerts for COLUMBIA_TELEFONICA.

        configures the device to COLUMBIA TELEFONICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(COLUMBIA_TELEFONICA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_emobile(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_EMOBILE.

        configures the device to JAPAN EMOBILE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_EMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_wirelesscityplanning(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_WIRELESSCITYPLANNING.

        configures the device to JAPAN WIRELESS CITY PLANNING
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_WIRELESSCITYPLANNING)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_docomo(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_DOCOMO.

        configures the device to JAPAN DOCOMO
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_DOCOMO)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_japan_rakuten(self):
        """ Verifies Wireless Emergency Alerts for JAPAN_RAKUTEN.

        configures the device to JAPAN RAKUTEN
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(JAPAN_RAKUTEN)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_korea_skt(self):
        """ Verifies Wireless Emergency Alerts for KOREA_SKT.

        configures the device to KOREA SKT
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(KOREA_SKT)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_korea_lgu(self):
        """ Verifies Wireless Emergency Alerts for KOREA_LGU.

        configures the device to KOREA LGU
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(KOREA_LGU)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_venezuela(self):
        """ Verifies Wireless Emergency Alerts for VENEZUELA.

        configures the device to VENEZUELA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(VENEZUELA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_russia(self):
        """ Verifies Wireless Emergency Alerts for RUSSIA.

        configures the device to RUSSIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(RUSSIA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_russia_megafon(self):
        """ Verifies Wireless Emergency Alerts for RUSSIA_MEGAFON.

        configures the device to RUSSIA MEGAFON
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(RUSSIA_MEGAFON)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_turkey(self):
        """ Verifies Wireless Emergency Alerts for TURKEY.

        configures the device to TURKEY
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(TURKEY)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us(self):
        """ Verifies Wireless Emergency Alerts for US.

        configures the device to US
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us_sprint(self):
        """ Verifies Wireless Emergency Alerts for US_SPRINT.

        configures the device to US SPRINT
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US_SPRINT)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_us_usc(self):
        """ Verifies Wireless Emergency Alerts for US_USC.

        configures the device to US USC
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(US_USC)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_azerbaijan(self):
        """ Verifies Wireless Emergency Alerts for AZERBAIJAN.

        configures the device to AZERBAIJAN
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(AZERBAIJAN)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_china(self):
        """ Verifies Wireless Emergency Alerts for CHINA.

        configures the device to CHINA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CHINA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_southafrica_telkom(self):
        """ Verifies Wireless Emergency Alerts for SOUTHAFRICA_TELKOM.

        configures the device to SOUTHAFRICA TELKOM
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(SOUTHAFRICA_TELKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_guatemala_telefonica(self):
        """ Verifies Wireless Emergency Alerts for GUATEMALA_TELEFONICA.

        configures the device to GUATEMALA TELEFONICA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(GUATEMALA_TELEFONICA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_india(self):
        """ Verifies Wireless Emergency Alerts for INDIA.

        configures the device to INDIA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(INDIA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_hungary_telekom(self):
        """ Verifies Wireless Emergency Alerts for HUNGARY_TELEKOM.

        configures the device to HUNGARY_TELEKOM
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(HUNGARY_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_croatia_hrvatski(self):
        """ Verifies Wireless Emergency Alerts for CROATIA_HRVATSKI.

        configures the device to CROATIA HRVATSKI
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CROATIA_HRVATSKI)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_czech_tmobile(self):
        """ Verifies Wireless Emergency Alerts for CZECH_TMOBILE.

        configures the device to CZECH TMOBILE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(CZECH_TMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_slovakia_telekom(self):
        """ Verifies Wireless Emergency Alerts for SLOVAKIA_TELEKOM.

        configures the device to SLOVAKIA TELEKOM
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(SLOVAKIA_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_austria_magenta(self):
        """ Verifies Wireless Emergency Alerts for AUSTRIA_MAGENTA.

        configures the device to AUSTRIA MAGENTA
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(AUSTRIA_MAGENTA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_poland_tmobile(self):
        """ Verifies Wireless Emergency Alerts for POLAND_TMOBILE.

        configures the device to POLAND TMOBILE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(POLAND_TMOBILE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_macedonia_telekom(self):
        """ Verifies Wireless Emergency Alerts for MACEDONIA_TELEKOM.

        configures the device to MACEDONIA TELEKOM
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(MACEDONIA_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_montenegro_telekom(self):
        """ Verifies Wireless Emergency Alerts for MONTENEGRO_TELEKOM.

        configures the device to MONTENEGRO_TELEKOM
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(MONTENEGRO_TELEKOM)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_ukraine(self):
        """ Verifies Wireless Emergency Alerts for UKRAINE.

        configures the device to UKRAINE
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(UKRAINE)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_norway(self):
        """ Verifies Wireless Emergency Alerts for NORWAY.

        configures the device to NORWAY
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(NORWAY)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_bulgaria(self):
        """ Verifies Wireless Emergency Alerts for Bulgaria.

        configures the device to Bulgaria
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._send_receive_test_flow(BULGARIA)


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_5g_wifi_us_vzw(self):
        """ Verifies WEA with WiFi and 5G NSA data network enabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and 5G NSA data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_5G, True):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_4g_wifi_us_vzw(self):
        """ Verifies WEA with WiFi and 4G data network enabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and 4G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_4G, True):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_3g_wifi_us_vzw(self):
        """ Verifies WEA with WiFi and 3G data network enabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and 3G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_3G, True):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_5g_wifi_only_us_vzw(self):
        """ Verifies WEA with WiFi enabled and 5G NSA data network disabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and disable 5G NSA data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_5G, False):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_4g_wifi_only_us_vzw(self):
        """ Verifies WEA with WiFi enabled and 4G data network disabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and disable 4G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_4G, False):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_3g_wifi_only_us_vzw(self):
        """ Verifies WEA with WiFi enabled and 3G data network disabled for US Verizon.

        configures the device to US Verizon
        enables WiFi and disable 3G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_3G, False):
            result = False
        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_5g_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA with WFC mode and 5G NSA data network disabled for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 5G NSA data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_5G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_4g_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA with WFC mode and 4G data network disabled for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 4G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_4G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return True


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_3g_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA with WFC mode and 3G data network disabled for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 3G data network.
        connects to internet via WiFi.
        send alerts across all channels,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_3G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return True


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_5g_epdg_mo_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA during VoWiFi call for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 5G NSA data network.
        connects to internet via WiFi.
        sends alerts across all channels and initiates mo VoWiFi call respectively.
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_5G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        phone_setup_voice_general(self.log, self.android_devices[1] )

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW, call=True):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_4g_epdg_mo_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA during VoWiFi call for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 4G data network.
        connects to internet via WiFi.
        sends alerts across all channels and initiates mo VoWiFi call respectively.
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_4G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        phone_setup_voice_general(self.log, self.android_devices[1] )

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW, call=True):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_3g_epdg_mo_wfc_wifi_only_us_vzw(self):
        """ Verifies WEA during VoWiFi call for US Verizon.

        configures the device to US Verizon
        enables WFC mode and disable 3G data network.
        connects to internet via WiFi.
        sends alerts across all channels and initiates mo VoWiFi call respectively.
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        if not self._setup_receive_test_flow_wifi(US_VZW, GEN_3G, False)\
                or not self._setup_wfc_mode(self.android_devices[0]):
            result = False

        phone_setup_voice_general(self.log, self.android_devices[1] )

        if result:
            if not self._verify_send_receive_wea_alerts(self.android_devices[0], US_VZW, call=True):
                result = False

        get_screen_shot_log(self.android_devices[0])
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_toggle_apm(self):
        """Verify WEA at APM on and off.

        Set device’s region to US Verizon
        Turn off APM mode
        Verify emergency alerts
        Turn on APM mode
        Verify emergency alerts
        Turn off APM mode
        Verify emergency alerts

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, US_VZW)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        ad.log.info("set device to US Verizon and APM off!")
        toggle_airplane_mode(ad.log, ad, False)
        time.sleep(WAIT_TIME_FOR_UI)
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("set APM on and verify WEA setting!")
        toggle_airplane_mode(ad.log, ad, True)
        time.sleep(WAIT_TIME_FOR_UI)
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("set APM off and verify WEA setting!")
        toggle_airplane_mode(ad.log, ad, False)
        time.sleep(WAIT_TIME_FOR_UI)
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False
        get_screen_shot_log(ad)
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_handover_lte_3g(self):
        """Verify WEA during handover btw lte and 3g.

        Set device to US Verizon
        Set network preferred mode to RAT LTE
        Verify emergency alerts
        Set network preferred mode to RAT 3G
        Verify emergency alerts
        Set network preferred mode to RAT LTE
        Verify emergency alerts

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, US_VZW)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        time.sleep(WAIT_TIME_FOR_UI)

        ad.log.info("Set device on volte!")
        if not phone_setup_on_rat(ad.log, ad, 'volte'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("Set device on 3g!")
        if not phone_setup_on_rat(ad.log, ad, '3g'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("Set device on volte!")
        if not phone_setup_on_rat(ad.log, ad, 'volte'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False
        get_screen_shot_log(ad)

        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_handover_5g_lte(self):
        """Verify WEA during handover btw 5g and lte.

        Set device to US Verizon
        Set network preferred mode to RAT 5G NSA
        Verify emergency alerts
        Set network preferred mode to RAT LTE
        Verify emergency alerts
        Set network preferred mode to RAT 5G NSA
        Verify emergency alerts

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, US_VZW)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        time.sleep(WAIT_TIME_FOR_UI)

        ad.log.info("Set device on 5g nsa!")
        if not phone_setup_on_rat(ad.log, ad, '5g'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("Set device on volte!")
        if not phone_setup_on_rat(ad.log, ad, 'volte'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False

        ad.log.info("Set device on 5g nsa!")
        if not phone_setup_on_rat(ad.log, ad, '5g'):
            return False
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False
        get_screen_shot_log(ad)
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_sideload_cbr_module(self):
        """Verify WEA after sideloading a patch

        Get value of cbr_patch parameter defined in acts config file.
            If cbr_patch is not defined or empty, skip the test.
        Install cbr patch.
        Reboot device.
        Set device to US Verizon.
        Verify emergency alerts.

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        ad = self.android_devices[0]
        cbr_patch_fetch = self.user_params.get("cbr_patch", "")
        if cbr_patch_fetch:
            ad.log.info("Download %s and install it.", cbr_patch_fetch)
            cbr_patch_fetch = cbr_patch_fetch + " " + self.log_path
            ad.log.info("%s", cbr_patch_fetch)
            self.fetch_proc = start_standing_subprocess(cbr_patch_fetch)
            wait_for_standing_subprocess(self.fetch_proc)
            out, err = self.fetch_proc.communicate()
            if err:
                ad.log.info("%s", err.decode('utf-8'))
                return False
        else:
            raise signals.TestSkip("No available cbr patch. Skip test!");

        ad.log.info("Successfully install it.")
        ad.adb.install("-r %s" % self.log_path+"/com.google.android.cellbroadcast.apex")
        reboot_device(ad)
        self._set_device_to_specific_region(ad, US_VZW)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        time.sleep(WAIT_TIME_FOR_UI)
        if not self._verify_send_receive_wea_alerts(ad, US_VZW, test_channel=4370):
            result = False
        get_screen_shot_log(ad)
        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_alert_not_dismiss_after_clicking_home_button(self):
        """Verify if alert dismisses when clicking home button.

        1. Set device region to Chile
        2. Send CBR 4370 alert
        3. Hide alert in notification drawer by clicking home button
        4. Verify if alert is in notification drawer
        5. Show and verify alert dialog is 4370 alert
        6. dismiss alert

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        self._clear_statusbar_notifications(ad)
        self._set_device_to_specific_region(ad, CHILE_TELEFONICA)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)

        alert_text = self.emergency_alert_channels_dict[CHILE_TELEFONICA]["4370"]["title"]
        sequence_num = random.randrange(10000, 40000)
        ad.log.info("%s for %s: %s", alert_text, CHILE_TELEFONICA, 4370)
        # Send Alert
        ad.droid.cbrSendTestAlert(sequence_num, 4370)

        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("Hide the alert channel %s in the notification drawer by clicking home button!", 4370)
        ad.adb.shell("input keyevent KEYCODE_HOME")
        time.sleep(WAIT_TIME_FOR_UI)
        alert_in_notification = False
        if self._popup_alert_in_statusbar_notifications(ad, alert_text):
            ad.log.info("Found the alert channel %d in the notification drawer!", 4370)
            # Verify alert text in message.
            alert_in_notification = self._verify_text_present_on_ui(ad, alert_text)
            if not alert_in_notification:
                ad.log.error("The alert title is not expected, %s", alert_text)
            self._exit_alert_pop_up(ad)
        else:
            ad.log.error(" Couldn't find the alert channel %d in the notification drawer!", 4370)

        return alert_in_notification


    @TelephonyBaseTest.tel_test_wrap
    def test_alert_unread_count(self):
        """ Verify unread alert count.

        1. Set device region to Korea
        2. Open alert setting UI and turn off show full screen messages
        3. Send and hide 4370, 4371 and 4372 alerts for three times each in sequence
        4. Show alert dialog from notification drawer
        5. Verify if alert count is 9
        6. Verify if each alert is correct

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        self._clear_statusbar_notifications(ad)

        self._set_device_to_specific_region(ad, KOREA)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)

        self._open_wea_settings_page(ad)
        full_screen_setting = "Show full-screen messages"
        if not self._has_element(ad, full_screen_setting):
            for _ in range(3):
                ad.adb.shell(SCROLL_DOWN)
            if not self._has_element(ad, full_screen_setting):
                ad.log.error("UI - %s missing", full_screen_setting)
                return False

        full_screen_setting_value = self._get_toggle_value(ad, full_screen_setting)
        if full_screen_setting_value == "true":
            # Turn off show full-screen messages
            self._wait_and_click(ad, full_screen_setting)
            time.sleep(WAIT_TIME_FOR_UI)
        self._close_wea_settings_page(ad)
        time.sleep(WAIT_TIME_FOR_UI)

        test_alert_channels = [4370, 4371, 4372]
        for channel in test_alert_channels:
        # Send and hide alert
            for iterate in range(1, 4):
                alert_text = self.emergency_alert_channels_dict[KOREA][str(channel)]["title"]
                sequence_num = random.randrange(10000, 40000)
                ad.log.info("%s for %s: %s", alert_text, KOREA, channel)
                ad.droid.cbrSendTestAlert(sequence_num, channel)
                time.sleep(WAIT_TIME_FOR_UI)
                ad.adb.shell("input keyevent KEYCODE_HOME")

        if self._popup_alert_in_statusbar_notifications(ad, "New alerts"):
            ad.log.info("Found alerts in the notification drawer!")
        else:
            ad.log.error(" Couldn't find the alert in the notification drawer!")
            return False

        ok_button_attrs = get_element_attributes(ad, text_contains="OK")
        result = True
        if not "1/9" in ok_button_attrs["text"].value:
            result = False
            ad.log.error("Unread alert count is incorrect, %s", ok_button_attrs["text"])
        else:
            ad.log.info("Unread alert count is 9!")
        test_alert_channels.reverse()
        for channel in test_alert_channels:
            for iterate in range(1, 4):
                alert_text = self.emergency_alert_channels_dict[KOREA][str(channel)]["title"]
                ad.log.info("Try to dismiss %s alert", alert_text)
                if not self._has_element(ad, alert_text):
                    result = False
                    ad.log.error("Alert is incorrect, should be %s", alert_text)
                self._exit_alert_pop_up(ad)

        return result

    @TelephonyBaseTest.tel_test_wrap
    def test_alert_datetime_chile_telefonica(self):
        """ Verifies the datetime format of alert messages for Chile.

        Set the device's region to Chile
        Send channel 4370 alert
        Get the alert time string on the alert dialog
        Verify if the format of the alert time is DD/MM/YYYY hh:mm PM|AM
        Get device time
        Verify if the alert time is same as device time

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        ad = self.android_devices[0]
        self._set_device_to_specific_region(ad, CHILE_TELEFONICA)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        alert_text = self.emergency_alert_channels_dict[CHILE_TELEFONICA][str(4370)]["title"]
        sequence_num = random.randrange(10000, 40000)
        ad.log.info("%s for %s: %s", alert_text, CHILE_TELEFONICA, 4370)
        ad.droid.cbrSendTestAlert(sequence_num, 4370)
        time.sleep(WAIT_TIME_FOR_UI)
        # get the device time
        stdout = ad.adb.shell("date +\"%d/%m/%Y,%I,%M,%p\"")
        device_time = stdout.split(',')
        title_attrs = get_element_attributes(ad, text_contains=alert_text)
        # Verify the format(DD/MM/YYYY hh:mm PM|AM) of alert date and time.
        format = "(\d{2}/\d{2}/\d{4}) ([0]\d|[1][012]):\d{2} (PM|AM)$"
        alert_time = re.search(format, title_attrs["text"].value)
        ad.log.info("The alert title is %s\nverify the format of the alert time!", title_attrs["text"].value)
        if not alert_time:
            result = False
            ad.log.error("The format of the alert time is incorrect. The correct format is DD/MM/YYYY hh:mm PM|AM")
        else:
            ad.log.info("The format of the alert time is correct!")
        # Verify the alert time
        ad.log.info("The device time is %s %s:%s %s", device_time[0], device_time[1], device_time[2], device_time[3])
        ad.log.info("Verify the alert time...")
        # The exact matched pattern count must be 3.
        if not alert_time or len(alert_time.groups()) != 3:
            result = False
            ad.log.error("The matched alert time %s is incorrect!", alert_time.group(0))

        if result and (not ((device_time[0] == alert_time.group(1)
                 and device_time[1] == alert_time.group(2)
                 and device_time[3] == alert_time.group(3)))):
            result = False
            ad.log.error("The alert time is incorrect!")
        else:
            ad.log.info("The alert time is correct!")
        self._exit_alert_pop_up(ad)

        return result

    @TelephonyBaseTest.tel_test_wrap
    def test_allow_alerts_japan_kddi(self):
        """ Verify alert is received after switch over 'Allow alerts' setting.

        Set the device's region to Japan kddi
        Turn off "Allow alerts" setting
        Reboot
        Turn on "Allow alerts" setting
        Send channel 4353 alert
        Verify if channel 4353 alert is received

        Returns:
            True if pass; False if fail and collects screenshot
        """
        ad = self.android_devices[0]
        result = True
        self._set_device_to_specific_region(ad, JAPAN_KDDI)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)

        allow_alerts_title = "Allow alerts"
        self._open_wea_settings_page(ad)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        if not self._has_element(ad, allow_alerts_title):
            for _ in range(3):
                ad.adb.shell(SCROLL_DOWN)
            if not self._has_element(ad, allow_alerts_title):
                ad.log.error("UI - %s missing", allow_alerts_title)
                return False
        # Get switch button's value of Allow alerts
        node = uutils.wait_and_get_xml_node(ad, timeout=30, text=allow_alerts_title)
        allow_alerts_value = node.nextSibling.attributes['checked'].value

        if allow_alerts_value == "true":
            # Turn off Allow alerts
            ad.log.info("Switch off Allow alerts!")
            self._wait_and_click(ad, allow_alerts_title)
        else:
            ad.log.info("Allow alerts is off!")
        time.sleep(WAIT_TIME_FOR_UI)
        self._close_wea_settings_page(ad)

        reboot_device(ad)
        time.sleep(WAIT_TIME_FOR_ALERTS_TO_POPULATE)
        self._open_wea_settings_page(ad)
        time.sleep(WAIT_TIME_ANDROID_STATE_SETTLING)
        # Turn on Allow alerts
        ad.log.info("Switch on Allow alerts!")
        self._wait_and_click(ad, allow_alerts_title)
        time.sleep(WAIT_TIME_FOR_UI)
        self._close_wea_settings_page(ad)
        if not self._verify_send_receive_wea_alerts(ad, JAPAN_KDDI, test_channel=4353):
            result = False
        get_screen_shot_log(ad)

        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_alert_screen_off_chile_telefonica(self):
        """Verify the vibration is on when the screen is off after receiving alerts.

        Set the device's region to Chile
        Send channel 4378 alert
        Turn off screen
        Wait for alert time
        turn on screen
        Verify the vibration time of the alert

        Returns:
            True if pass; False if fail and collects screenshot
        """
        result = True
        ad = self.android_devices[0]
        self._set_device_to_specific_region(ad, CHILE_TELEFONICA)
        time.sleep(WAIT_TIME_FOR_UI)
        ad.log.info("disable DND: %s", CMD_DND_OFF)
        ad.adb.shell(CMD_DND_OFF)
        if not self._verify_send_receive_wea_alerts(ad, CHILE_TELEFONICA, test_channel=4378, screen_off=True):
            result = False
        get_screen_shot_log(ad)

        return result


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_uae_upgrading_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for UAE

        configures the device to UAE
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(UAE,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_australia_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for AUSTRALIA

        configures the device to Australia
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_test_flow(AUSTRALIA,
                                        upgrade_cbr_train_build=True,
                                        rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_france_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for FRANCE

        configures the device to France
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(FRANCE,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_kddi_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Japan (KDDI)

        configures the device to Japan (KDDI)
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(JAPAN_KDDI,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_newzealand_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for NZ

        configures the device to NZ
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(NEWZEALAND,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_hongkong_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for HongKong

        configures the device to HongKong
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(HONGKONG,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_chile_entel_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Chile Entel

        configures the device to Chile Entel
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(CHILE_ENTEL,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_chile_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Chile Telefonica

        configures the device to Chile Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(CHILE_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_peru_entel_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Peru_Entel

        configures the device to Peru_Entel
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(PERU_ENTEL,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_peru_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Peru_Telefonica

        configures the device to Peru Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(PERU_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_spain_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Spain_Telefonica

        configures the device to Spain Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(SPAIN_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_elsalvador_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for
            Elsalvador_Telefonica

        configures the device to Elsalvador Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ELSALVADOR_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_mexico_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Mexico_Telefonica

        configures the device to Mexico Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(MEXICO_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_korea_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Korea

        configures the device to Korea
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(KOREA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_taiwan_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Taiwan

        configures the device to Taiwan
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
        the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(TAIWAN,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_canada_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Canada

        configures the device to Canada
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(CANADA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_brazil_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Brazil

        configures the device to Brazil
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(BRAZIL,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_columbia_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Columbia

        configures the device to Columbia
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(COLUMBIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_ecuador_telefonica_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Ecuador Telefonica

        configures the device to Ecuador Telefonica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ECUADOR_TELEFONICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_ecuador_claro_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Ecuador Claro

        configures the device to Ecuador Claro
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ECUADOR_CLARO,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_puertorico_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Puertorico

        configures the device to Puertorico
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(PUERTORICO,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_netherlands_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Netherlands

        configures the device to Netherlands
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(NETHERLANDS,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_romania_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Romania

        configures the device to Romania
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ROMANIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_estonia_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Estonia

        configures the device to Estonia
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ESTONIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_lithuania_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Lithuania

        configures the device to Lithuania
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(LITHUANIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_latvia_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Latvia

        configures the device to Latvia
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(LATVIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_greece_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Greece

        configures the device to Greece
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(GREECE,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_italy_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Italy

        configures the device to Italy
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ITALY,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_southafrica_upgrade_rollback_cbr_build(self):
        """ Verifies wea after upgrading a new cbr build and rollback for SouthAfrica

        configures the device to SouthAfrica
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(SOUTHAFRICA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_uk_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for UK

        configures the device to UK
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(UK,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_israel_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Israel

        configures the device to Israel
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(ISRAEL,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_oman_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Oman

        configures the device to Oman
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(OMAN,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_japan_softbank_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Japan (Softbank)

        configures the device to Japan (Softbank)
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(JAPAN_SOFTBANK,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_saudiarabia_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for SaudiArabia

        configures the device to SaudiArabia
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(SAUDIARABIA,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_att_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for US ATT

        configures the device to US ATT
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(US_ATT,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_tmo_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for US TMO

        configures the device to US TMO
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(US_TMO,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_us_vzw_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for US VZW

        configures the device to US VZW
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(US_VZW,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_germany_telekom_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Germany telecom

        configures the device to Germany telecom
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(GERMANY_TELEKOM,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)


    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_qatar_vodafone_upgrade_rollback_cbr_build(self):
        """ Verifies wea settings after upgrading a new cbr build and rollback for Qatar vodafone

        configures the device to Qatar vodafone
        upgrades a new cbr build
        reports errors if the versions of the upgraded cbr build and
            the factory cbr build are the same
        verifies alert names and its default values
        toggles the alert twice if available
        rolls back the factory cbr build
        reports errors if the versions of the factory cbr build and
            the rollback cbr build are different
        verifies alert names and its default values
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """
        return self._settings_upgrade_cbr_test_flow(QATAR_VODAFONE,
                                                    upgrade_cbr_train_build=True,
                                                    rollback_cbr_train_build=True)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_italy_roaming_france(self):
        """Verifies WEA settings for Italy when roaming in France

        configures the device to Italy
        Set the roaming region to France
        verifies alert names and its default values for Italy
        verifies if a roaming message is on alert setting UI
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._settings_test_flow(ITALY, roaming=True, roaming_region=FRANCE)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_france_roaming_genmany_telekom(self):
        """Verifies WEA settings for France when roaming in Germany Telekom

        configures the device to France
        Set the roaming region to Germany Telekom
        verifies alert names and its default values for France
        verifies if a roaming message is on alert setting UI
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._settings_test_flow(FRANCE, roaming=True, roaming_region=GERMANY_TELEKOM)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_norway_roaming_qatar_vodafone(self):
        """Verifies WEA settings for Norway when roaming in Qatar Vodafone

        configures the device to Norway
        Set the roaming region to Qatar
        verifies alert names and its default values for Norway
        verifies if a roaming message is on alert setting UI
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._settings_test_flow(NORWAY, roaming=True, roaming_region=QATAR_VODAFONE)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_germany_telekom_roaming_norway(self):
        """Verifies WEA settings for Germany when roaming in Norway

        configures the device to Germany
        Set the roaming region to Norway
        verifies alert names and its default values for Germany
        verifies if a roaming message is on alert setting UI
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._settings_test_flow(GERMANY_TELEKOM, roaming=True, roaming_region=NORWAY)

    @TelephonyBaseTest.tel_test_wrap
    def test_default_alert_settings_qatar_vodafone_roaming_italy(self):
        """Verifies WEA settings for Qatar when roaming in Italy

        configures the device to Qatar
        Set the roaming region to Italy
        verifies alert names and its default values for Qatar
        verifies if a roaming message is on alert setting UI
        toggles the alert twice if available

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._settings_test_flow(QATAR_VODAFONE, roaming=True, roaming_region=ITALY)

    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_italy_roaming_france(self):
        """ Verifies Wireless Emergency Alerts for Italy when roaming in France.

        configures the device to Italy
        Set the roaming region to France
        send alerts across all channels for France,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._send_receive_test_flow(ITALY, roaming=True, roaming_region=FRANCE)

    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_france_roaming_germany_telekom(self):
        """ Verifies Wireless Emergency Alerts for France when roaming in Germany.

        configures the device to France
        Set the roaming region to Germany
        send alerts across all channels for Germany
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._send_receive_test_flow(FRANCE, roaming=True, roaming_region=GERMANY_TELEKOM)

    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_norway_roaming_qatar_vodafone(self):
        """ Verifies Wireless Emergency Alerts for Norway when roaming in Qatar.

        configures the device to Norway
        Set the roaming region to Qatar vodafone
        send alerts across all channels for Qatar vodafone
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._send_receive_test_flow(NORWAY, roaming=True, roaming_region=QATAR_VODAFONE)

    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_germany_telekom_roaming_norway(self):
        """ Verifies Wireless Emergency Alerts for Germany when roaming in Norway.

        configures the device to Germany
        Set the roaming region to Norway
        send alerts across all channels for Norway,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._send_receive_test_flow(GERMANY_TELEKOM, roaming=True, roaming_region=NORWAY)

    @TelephonyBaseTest.tel_test_wrap
    def test_send_receive_alerts_qatar_vodafone_roaming_italy(self):
        """ Verifies Wireless Emergency Alerts for Qatar when roaming in Italy.

        configures the device to Qatar vodafone
        Set the roaming region to Italy
        send alerts across all channels for Italy,
        verify if alert is received correctly
        verify sound and vibration timing
        click on OK/exit alert and verify text

        Returns:
            True if pass; False if fail and collects screenshot
        """

        return self._send_receive_test_flow(QATAR_VODAFONE, roaming=True, roaming_region=ITALY)
