#!/usr/bin/env python3
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
from acts import utils

from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest


class WlanPhyComplianceABGTest(WifiBaseTest):
    """Tests for validating 11a, 11b, and 11g PHYS.

    Test Bed Requirement:
    * One Android device or Fuchsia device
    * One Access Point
    """

    def setup_class(self):
        super().setup_class()
        if 'dut' in self.user_params:
            if self.user_params['dut'] == 'fuchsia_devices':
                self.dut = create_wlan_device(self.fuchsia_devices[0])
            elif self.user_params['dut'] == 'android_devices':
                self.dut = create_wlan_device(self.android_devices[0])
            else:
                raise ValueError('Invalid DUT specified in config. (%s)' %
                                 self.user_params['dut'])
        else:
            # Default is an android device, just like the other tests
            self.dut = create_wlan_device(self.android_devices[0])

        self.access_point = self.access_points[0]
        open_network = self.get_open_network(False, [])
        open_network_min_len = self.get_open_network(
            False, [],
            ssid_length_2g=hostapd_constants.AP_SSID_MIN_LENGTH_2G,
            ssid_length_5g=hostapd_constants.AP_SSID_MIN_LENGTH_5G)
        open_network_max_len = self.get_open_network(
            False, [],
            ssid_length_2g=hostapd_constants.AP_SSID_MAX_LENGTH_2G,
            ssid_length_5g=hostapd_constants.AP_SSID_MAX_LENGTH_5G)
        self.open_network_2g = open_network['2g']
        self.open_network_5g = open_network['5g']
        self.open_network_max_len_2g = open_network_max_len['2g']
        self.open_network_max_len_2g['SSID'] = (
            self.open_network_max_len_2g['SSID'][3:])
        self.open_network_max_len_5g = open_network_max_len['5g']
        self.open_network_max_len_5g['SSID'] = (
            self.open_network_max_len_5g['SSID'][3:])
        self.open_network_min_len_2g = open_network_min_len['2g']
        self.open_network_min_len_2g['SSID'] = (
            self.open_network_min_len_2g['SSID'][3:])
        self.open_network_min_len_5g = open_network_min_len['5g']
        self.open_network_min_len_5g['SSID'] = (
            self.open_network_min_len_5g['SSID'][3:])

        self.utf8_ssid_2g = '2𝔤_𝔊𝔬𝔬𝔤𝔩𝔢'
        self.utf8_ssid_5g = '5𝔤_𝔊𝔬𝔬𝔤𝔩𝔢'

        self.utf8_ssid_2g_french = 'Château du Feÿ'
        self.utf8_password_2g_french = 'du Feÿ Château'

        self.utf8_ssid_2g_german = 'Rat für Straßenatlas'
        self.utf8_password_2g_german = 'für Straßenatlas Rat'

        self.utf8_ssid_2g_dutch = 'Die niet óúd, is níéuw!'
        self.utf8_password_2g_dutch = 'niet óúd, is níéuw! Die'

        self.utf8_ssid_2g_swedish = 'Det är femtioåtta'
        self.utf8_password_2g_swedish = 'femtioåtta Det är'

        self.utf8_ssid_2g_norwegian = 'Curaçao ØÆ æ å å å'
        self.utf8_password_2g_norwegian = 'ØÆ Curaçao æ å å å'

        #Danish and Norwegian has the same alphabet
        self.utf8_ssid_2g_danish = self.utf8_ssid_2g_norwegian
        self.utf8_password_2g_danish = self.utf8_password_2g_norwegian

        self.utf8_ssid_2g_japanese = 'あなた　はお母さん'
        self.utf8_password_2g_japanese = 'そっくりね。あな'

        self.utf8_ssid_2g_spanish = '¡No á,é,í,ó,ú,ü,ñ,¿,¡'
        self.utf8_password_2g_spanish = 'á,é,í,ó,ú,ü,ñ,¿,¡ ¡No'

        self.utf8_ssid_2g_italian = 'caffè Pinocchio è italiano?'
        self.utf8_password_2g_italian = 'Pinocchio è italiano? caffè'

        self.utf8_ssid_2g_korean = 'ㅘㅙㅚㅛㅜㅝㅞㅟㅠ'
        self.utf8_password_2g_korean = 'ㅜㅝㅞㅟㅠㅘㅙㅚㅛ'

        self.access_point.stop_all_aps()

    def setup_test(self):
        if hasattr(self, "android_devices"):
            for ad in self.android_devices:
                ad.droid.wakeLockAcquireBright()
                ad.droid.wakeUpNow()
        self.dut.wifi_toggle_state(True)

    def teardown_test(self):
        if hasattr(self, "android_devices"):
            for ad in self.android_devices:
                ad.droid.wakeLockRelease()
                ad.droid.goToSleepNow()
        self.dut.turn_location_off_and_scan_toggle_off()
        self.dut.disconnect()
        self.dut.reset_wifi()
        self.download_ap_logs()
        self.access_point.stop_all_aps()

    def on_fail(self, test_name, begin_time):
        super().on_fail(test_name, begin_time)
        self.access_point.stop_all_aps()

    def test_associate_11b_only_long_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=False)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_short_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=True)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_minimal_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=15)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_maximum_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=1024)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_frag_threshold_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_rts_threshold_256(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_rts_256_frag_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256,
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_high_dtim_low_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=3,
                 beacon_interval=100)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_low_dtim_high_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=1,
                 beacon_interval=300)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_with_default_values(self):
        setup_ap(
            access_point=self.access_point,
            profile_name='whirlwind_11ab_legacy',
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            ssid=self.open_network_2g['SSID'],
            force_wmm=True,
            additional_ap_parameters=hostapd_constants.WMM_11B_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_with_non_default_values(self):
        setup_ap(
            access_point=self.access_point,
            profile_name='whirlwind_11ab_legacy',
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            ssid=self.open_network_2g['SSID'],
            force_wmm=True,
            additional_ap_parameters=hostapd_constants.WMM_NON_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BK(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BE(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BK_BE_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BK_BE_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BK_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_WMM_ACM_on_BE_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_11B_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['UNITED_STATES'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_non_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['NON_COUNTRY'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_hidden_ssid(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 hidden=True)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_vendor_ie_in_beacon_correct_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['correct_length_beacon'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_vendor_ie_in_beacon_zero_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['zero_length_beacon_without_data'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_vendor_ie_in_assoc_correct_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['correct_length_association_response'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11b_only_with_vendor_ie_in_assoc_zero_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.VENDOR_IE[
                     'zero_length_association_'
                     'response_without_data'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_long_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 preamble=False)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_short_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 preamble=True)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_minimal_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 beacon_interval=15)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_maximum_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 beacon_interval=1024)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_frag_threshold_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_rts_threshold_256(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 rts_threshold=256)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_rts_256_frag_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 rts_threshold=256,
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_high_dtim_low_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 dtim_period=3,
                 beacon_interval=100)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_low_dtim_high_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 dtim_period=1,
                 beacon_interval=300)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_with_default_values(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=hostapd_constants.
                 WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_with_non_default_values(self):
        setup_ap(
            access_point=self.access_point,
            profile_name='whirlwind_11ab_legacy',
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
            ssid=self.open_network_5g['SSID'],
            force_wmm=True,
            additional_ap_parameters=hostapd_constants.WMM_NON_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BK(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BE(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BK_BE_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BK_BE_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BK_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_WMM_ACM_on_BE_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['UNITED_STATES'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_non_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['NON_COUNTRY'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_hidden_ssid(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 hidden=True)
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_vendor_ie_in_beacon_correct_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['correct_length_beacon'])
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_vendor_ie_in_beacon_zero_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['zero_length_beacon_without_data'])
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_vendor_ie_in_assoc_correct_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['correct_length_association_response'])
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11a_only_with_vendor_ie_in_assoc_zero_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_5g['SSID'],
                 additional_ap_parameters=hostapd_constants.VENDOR_IE[
                     'zero_length_association_'
                     'response_without_data'])
        asserts.assert_true(self.dut.associate(self.open_network_5g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_long_preamble(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=False,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_short_preamble(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=True,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_minimal_beacon_interval(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=15,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_maximum_beacon_interval(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=1024,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_frag_threshold_430(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 frag_threshold=430,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_rts_threshold_256(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_rts_256_frag_430(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256,
                 frag_threshold=430,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_high_dtim_low_beacon_interval(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=3,
                 beacon_interval=100,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_low_dtim_high_beacon_interval(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=1,
                 beacon_interval=300,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_with_default_values(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_with_non_default_values(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.WMM_NON_DEFAULT_PARAMS)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BK(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BE(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_VI(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_VO(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VO, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BK_BE_VI(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VI, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BK_BE_VO(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VO, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BK_VI_VO(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_WMM_ACM_on_BE_VI_VO(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO, data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_country_code(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['UNITED_STATES'], data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_non_country_code(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['NON_COUNTRY'], data_rates)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_hidden_ssid(self):
        data_rates = utils.merge_dicts(hostapd_constants.OFDM_DATA_RATES,
                                       hostapd_constants.OFDM_ONLY_BASIC_RATES)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 hidden=True,
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_beacon_correct_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['correct_length_beacon'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_beacon_zero_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['zero_length_beacon_without_data'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_assoc_correct_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['correct_length_association_response'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_assoc_zero_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['correct_length_association_response'],
            hostapd_constants.VENDOR_IE['zero_length_association_'
                                        'response_without_data'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_only_long_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=False)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_short_preamble(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 preamble=True)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_minimal_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=15)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_maximum_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 beacon_interval=1024)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_frag_threshold_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_rts_threshold_256(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_rts_256_frag_430(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 rts_threshold=256,
                 frag_threshold=430)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_high_dtim_low_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=3,
                 beacon_interval=100)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_low_dtim_high_beacon_interval(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 dtim_period=1,
                 beacon_interval=300)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_with_default_values(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=hostapd_constants.
                 WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_with_non_default_values(self):
        setup_ap(
            access_point=self.access_point,
            profile_name='whirlwind_11ag_legacy',
            channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
            ssid=self.open_network_2g['SSID'],
            force_wmm=True,
            additional_ap_parameters=hostapd_constants.WMM_NON_DEFAULT_PARAMS)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BK(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BE(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BK_BE_VI(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VI)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BK_BE_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_BE,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BK_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BK, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_WMM_ACM_on_BE_VI_VO(self):
        wmm_acm_bits_enabled = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_BE, hostapd_constants.WMM_ACM_VI,
            hostapd_constants.WMM_ACM_VO)
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 force_wmm=True,
                 additional_ap_parameters=wmm_acm_bits_enabled)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['UNITED_STATES'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_non_country_code(self):
        country_info = utils.merge_dicts(
            hostapd_constants.ENABLE_IEEE80211D,
            hostapd_constants.COUNTRY_STRING['ALL'],
            hostapd_constants.COUNTRY_CODE['NON_COUNTRY'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=country_info)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_only_with_hidden_ssid(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 hidden=True)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_vendor_ie_in_beacon_correct_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['correct_length_beacon'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11bg_with_vendor_ie_in_beacon_zero_length(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=hostapd_constants.
                 VENDOR_IE['zero_length_beacon_without_data'])
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_assoc_correct_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['correct_length_association_response'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_associate_11g_only_with_vendor_ie_in_assoc_zero_length(self):
        data_rates = utils.merge_dicts(
            hostapd_constants.OFDM_DATA_RATES,
            hostapd_constants.OFDM_ONLY_BASIC_RATES,
            hostapd_constants.VENDOR_IE['correct_length_association_response'],
            hostapd_constants.VENDOR_IE['zero_length_association_'
                                        'response_without_data'])
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ag_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_2g['SSID'],
                 additional_ap_parameters=data_rates)
        asserts.assert_true(self.dut.associate(self.open_network_2g['SSID']),
                            'Failed to associate.')

    def test_minimum_ssid_length_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_min_len_2g['SSID'])
        asserts.assert_true(
            self.dut.associate(self.open_network_min_len_2g['SSID']),
            'Failed to associate.')

    def test_minimum_ssid_length_5g_11ac_80mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_min_len_5g['SSID'])
        asserts.assert_true(
            self.dut.associate(self.open_network_min_len_5g['SSID']),
            'Failed to associate.')

    def test_maximum_ssid_length_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.open_network_max_len_2g['SSID'])
        asserts.assert_true(
            self.dut.associate(self.open_network_max_len_2g['SSID']),
            'Failed to associate.')

    def test_maximum_ssid_length_5g_11ac_80mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.open_network_max_len_5g['SSID'])
        asserts.assert_true(
            self.dut.associate(self.open_network_max_len_5g['SSID']),
            'Failed to associate.')

    def test_ssid_with_UTF8_characters_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_5g_11ac_80mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_5G,
                 ssid=self.utf8_ssid_5g)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_5g),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_french_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_french)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_french),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_german_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_german)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_german),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_dutch_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_dutch)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_dutch),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_swedish_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_swedish)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_swedish),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_norwegian_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_norwegian)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_norwegian),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_danish_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_danish)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_danish),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_japanese_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_japanese)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_japanese),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_spanish_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_spanish)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_spanish),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_italian_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_italian)
        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_italian),
                            'Failed to associate.')

    def test_ssid_with_UTF8_characters_korean_2g_11n_20mhz(self):
        setup_ap(access_point=self.access_point,
                 profile_name='whirlwind_11ab_legacy',
                 channel=hostapd_constants.AP_DEFAULT_CHANNEL_2G,
                 ssid=self.utf8_ssid_2g_korean)

        asserts.assert_true(self.dut.associate(self.utf8_ssid_2g_korean),
                            'Failed to associate.')
