#!/usr/bin/python3.4
#
#   Copyright 2017 - The Android Open Source Project
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

import queue
import time

from acts import asserts
from acts import utils
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_constants as wconsts
from acts_contrib.test_utils.wifi.aware import aware_const as aconsts
from acts_contrib.test_utils.wifi.aware import aware_test_utils as autils
from acts_contrib.test_utils.wifi.aware.AwareBaseTest import AwareBaseTest
from acts_contrib.test_utils.wifi.p2p import wifi_p2p_test_utils as wp2putils
from acts_contrib.test_utils.wifi.p2p import wifi_p2p_const as p2pconsts

# arbitrary timeout for events
EVENT_TIMEOUT = 10


class NonConcurrencyTest(AwareBaseTest):
    """Tests lack of concurrency scenarios Wi-Fi Aware with WFD (p2p) and
  SoftAP

  Note: these tests should be modified if the concurrency behavior changes!"""

    SERVICE_NAME = "GoogleTestXYZ"
    TETHER_SSID = "GoogleTestSoftApXYZ"

    def teardown_test(self):
        AwareBaseTest.teardown_test(self)
        for ad in self.android_devices:
            ad.droid.wifiP2pClose()
            ad.droid.connectivityStopTethering(0)

    def run_aware_then_incompat_service(self, is_p2p):
        """Run test to validate that a running Aware session terminates when an
    Aware-incompatible service is started.
    P2P and SoftAp has same priority as Aware, will bring down all aware clients,
    but Aware will keep available.

    Args:
      is_p2p: True for p2p, False for SoftAP
    """
        dut = self.android_devices[0]
        p_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  aconsts.PUBLISH_TYPE_UNSOLICITED)

        # start Aware
        id = dut.droid.wifiAwareAttach()
        autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)

        time.sleep(EVENT_TIMEOUT)

        # start other service
        if is_p2p:
            dut.droid.wifiP2pInitialize()
            wp2putils.p2p_create_group(dut)
            dut.ed.pop_event(p2pconsts.CONNECTED_EVENT,
                        p2pconsts.DEFAULT_TIMEOUT)
            time.sleep(p2pconsts.DEFAULT_FUNCTION_SWITCH_TIME)
        else:
            wutils.start_wifi_tethering(dut, self.TETHER_SSID, password=None)

        # Will not make WiFi Aware unavailable
        autils.fail_on_event(dut, aconsts.BROADCAST_WIFI_AWARE_NOT_AVAILABLE)
        # Aware session has already been torn down, publish will fail.
        dut.droid.wifiAwarePublish(id, p_config)
        autils.wait_for_event(dut, aconsts.SESSION_CB_ON_SESSION_CONFIG_FAILED)

        # Aware will be available, and try to tear down other service when new service request.
        autils.wait_for_event(dut, aconsts.BROADCAST_WIFI_AWARE_AVAILABLE)

        # Try to attach again
        id = dut.droid.wifiAwareAttach()
        if is_p2p or dut.droid.isSdkAtLeastS():
            autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)
            dut.droid.wifiAwarePublish(id, p_config)
            autils.wait_for_event(dut, aconsts.SESSION_CB_ON_PUBLISH_STARTED)
        else:
            # SoftAp has higher priority on R device, attach should fail
            autils.fail_on_event(dut, aconsts.EVENT_CB_ON_ATTACHED)



    def run_incompat_service_then_aware(self, is_p2p):
        """Validate that if an Aware-incompatible service is already up then try to start Aware.
    P2P and SoftAp has same priority as Aware,  Aware can bring it down and start service

    Args:
      is_p2p: True for p2p, False for SoftAP
    """
        dut = self.android_devices[0]

        # start other service
        if is_p2p:
            dut.droid.wifiP2pInitialize()
        else:
            wutils.start_wifi_tethering(dut, self.TETHER_SSID, password=None)

        autils.fail_on_event(dut, aconsts.BROADCAST_WIFI_AWARE_NOT_AVAILABLE)

        # Change Wifi state and location mode to check if aware became available.
        wutils.wifi_toggle_state(dut, False)
        utils.set_location_service(dut, False)
        wutils.wifi_toggle_state(dut, True)
        utils.set_location_service(dut, True)

        autils.wait_for_event(dut, aconsts.BROADCAST_WIFI_AWARE_AVAILABLE)
        asserts.assert_true(dut.droid.wifiIsAwareAvailable(), "Aware should be available")

        dut.droid.wifiAwareAttach()
        if is_p2p or dut.droid.isSdkAtLeastS():
            autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)
        else:
            # SoftAp has higher priority on R device, attach should fail
            autils.fail_on_event(dut, aconsts.EVENT_CB_ON_ATTACHED)

    def run_aware_then_connect_to_new_ap(self):
        """Validate interaction of Wi-Fi Aware and infra (STA) association with randomized MAC
    address. Such an association may trigger interface down and up - possibly disrupting a Wi-Fi
    Aware session.

    Test behavior:
    - Start Aware
    - Associate STA
    - Check if an Aware state change Broadcast received
    - If necessary (Broadcast received) restart Aware
    - Start publish
    - Start Subscribe on peer
    - Verify discovery
    """
        dut = self.android_devices[0]
        dut_ap = self.android_devices[1]
        wutils.reset_wifi(dut)
        wutils.reset_wifi(dut_ap)
        p_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  aconsts.PUBLISH_TYPE_UNSOLICITED)
        s_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  aconsts.SUBSCRIBE_TYPE_PASSIVE)

        # create random SSID and start softAp on dut_ap
        ap_ssid = self.TETHER_SSID + utils.rand_ascii_str(8)
        ap_password = utils.rand_ascii_str(8)
        ap_band = wutils.WifiEnums.WIFI_CONFIG_SOFTAP_BAND_5G
        config = {wutils.WifiEnums.SSID_KEY: ap_ssid, wutils.WifiEnums.PWD_KEY: ap_password, wutils.WifiEnums.AP_BAND_KEY: ap_band}
        wutils.start_wifi_tethering(dut_ap, ap_ssid, ap_password, band=ap_band)
        asserts.assert_true(dut_ap.droid.wifiIsApEnabled(),
                            "SoftAp is not reported as running")

        # dut start Aware attach and connect to softAp on dut_ap
        p_id = dut.droid.wifiAwareAttach()
        autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)

        wutils.start_wifi_connection_scan_and_ensure_network_found(dut, ap_ssid)
        wutils.wifi_connect(dut, config, check_connectivity=False)
        autils.wait_for_event(dut, wconsts.WIFI_STATE_CHANGED)

        # Check if the WifiAwareState changes then restart the Aware
        state_change = False
        try:
            dut.ed.pop_event(aconsts.BROADCAST_WIFI_AWARE_AVAILABLE, EVENT_TIMEOUT)
            dut.log.info(aconsts.BROADCAST_WIFI_AWARE_AVAILABLE)
            p_id = dut.droid.wifiAwareAttach()
            autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)
            wutils.ensure_no_disconnect(dut)
            state_change = True
        except queue.Empty:
            dut.log.info('WifiAware state was not changed')

        # dut_ap stop softAp and start publish
        wutils.stop_wifi_tethering(dut_ap)
        if not dut_ap.droid.wifiIsAwareAvailable():
            autils.wait_for_event(dut_ap, aconsts.BROADCAST_WIFI_AWARE_AVAILABLE)
        s_id = dut_ap.droid.wifiAwareAttach()
        autils.wait_for_event(dut_ap, aconsts.EVENT_CB_ON_ATTACHED)
        s_disc_id = dut_ap.droid.wifiAwarePublish(s_id, s_config)
        autils.wait_for_event(dut_ap, aconsts.SESSION_CB_ON_PUBLISH_STARTED)

        # dut start subscribe
        wutils.wait_for_disconnect(dut, timeout=30)
        if state_change:
            autils.wait_for_event(dut, aconsts.BROADCAST_WIFI_AWARE_AVAILABLE)
            p_id = dut.droid.wifiAwareAttach()
            autils.wait_for_event(dut, aconsts.EVENT_CB_ON_ATTACHED)
        p_disc_id = dut.droid.wifiAwareSubscribe(p_id, p_config)
        autils.wait_for_event(dut, aconsts.SESSION_CB_ON_SUBSCRIBE_STARTED)

        # Check discovery session
        autils.wait_for_event(dut, aconsts.SESSION_CB_ON_SERVICE_DISCOVERED)

    ##########################################################################

    @test_tracker_info(uuid="b7c84cbe-d744-440a-9279-a0133e88e8cb")
    def test_run_p2p_then_aware(self):
        """Validate that a running p2p session terminates when Aware is started"""
        self.run_incompat_service_then_aware(is_p2p=True)

    @test_tracker_info(uuid="1e7b3a6d-575d-4911-80bb-6fcf1157ee9f")
    def test_run_aware_then_p2p(self):
        """Validate that a running Aware session terminates when p2p is started"""
        self.run_aware_then_incompat_service(is_p2p=True)

    @test_tracker_info(uuid="82a0bd98-3022-4831-ac9e-d81f58c742d2")
    def test_run_softap_then_aware(self):
        """Validate that a running softAp session terminates when Aware is started"""
        asserts.skip_if(
            self.android_devices[0].model not in self.dbs_supported_models,
            "Device %s doesn't support STA+AP." % self.android_devices[0].model)
        self.run_incompat_service_then_aware(is_p2p=False)

    @test_tracker_info(uuid="0da7661e-8ac2-4f68-b6d3-b3f612369d03")
    def test_run_aware_then_softap(self):
        """Validate that a running Aware session terminates when softAp is
    started"""
        #Adding dbs support verifying before test start
        asserts.skip_if(
            self.android_devices[0].model not in self.dbs_supported_models,
            "Device %s doesn't support STA+AP." % self.android_devices[0].model)
        self.run_aware_then_incompat_service(is_p2p=False)

    @test_tracker_info(uuid="2ac27ac6-8010-4d05-b892-00242420b075")
    def test_run_aware_then_connect_new_ap(self):
        """Validate connect new ap during Aware session"""
        self.run_aware_then_connect_to_new_ap()
