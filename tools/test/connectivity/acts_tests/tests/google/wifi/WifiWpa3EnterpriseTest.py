#!/usr/bin/env python3.4
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

from acts import asserts
from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest

WifiEnums = wutils.WifiEnums

EAP = WifiEnums.Eap
Ent = WifiEnums.Enterprise
WPA3_SECURITY = "SUITE_B_192"


class WifiWpa3EnterpriseTest(WifiBaseTest):
  """Tests for WPA3 Enterprise."""

  def setup_class(self):
    super().setup_class()

    self.dut = self.android_devices[0]
    wutils.wifi_test_device_init(self.dut)
    req_params = [
        "ec2_ca_cert", "ec2_client_cert", "ec2_client_key", "rsa3072_ca_cert",
        "rsa3072_client_cert", "rsa3072_client_key", "wpa3_ec2_network",
        "wpa3_rsa3072_network", "rsa2048_client_cert", "rsa2048_client_key",
        "rsa3072_client_cert_expired", "rsa3072_client_cert_corrupted",
        "rsa3072_client_cert_unsigned", "rsa3072_client_key_unsigned",
    ]
    self.unpack_userparams(req_param_names=req_params,)

  def setup_test(self):
    super().setup_test()
    for ad in self.android_devices:
      ad.droid.wakeLockAcquireBright()
      ad.droid.wakeUpNow()
    wutils.wifi_toggle_state(self.dut, True)

  def teardown_test(self):
    super().teardown_test()
    for ad in self.android_devices:
      ad.droid.wakeLockRelease()
      ad.droid.goToSleepNow()
    wutils.reset_wifi(self.dut)

  ### Tests ###

  @test_tracker_info(uuid="404c6165-6e23-4ec1-bc2c-9dfdd5c7dc87")
  def test_connect_to_wpa3_enterprise_ec2(self):
    asserts.skip_if(
        self.dut.build_info["build_id"].startswith("R"),
        "No SL4A support for EC certs in R builds. Skipping this testcase")
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.ec2_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_ec2_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.ec2_client_cert,
        Ent.PRIVATE_KEY_ID: self.ec2_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_ec2_network["identity"],
        "domain_suffix_match": self.wpa3_ec2_network["domain"],
        "cert_algo": self.wpa3_ec2_network["cert_algo"]
    }
    wutils.connect_to_wifi_network(self.dut, config)

  @test_tracker_info(uuid="b6d22585-f7c1-418d-bd4b-b627af8c228c")
  def test_connect_to_wpa3_enterprise_rsa3072(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert,
        Ent.PRIVATE_KEY_ID: self.rsa3072_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
    }
    # Synology AP is slow in sending out IP address after the connection.
    # Increasing the wait time to receive IP address to 60s from 15s.
    wutils.connect_to_wifi_network(self.dut, config, check_connectivity=False)
    wutils.validate_connection(self.dut, wait_time=60)

  @test_tracker_info(uuid="4779c662-1925-4c26-a4d6-3d729393796e")
  def test_connect_to_wpa3_enterprise_insecure_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa2048_client_cert,
        Ent.PRIVATE_KEY_ID: self.rsa2048_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
    }
    logcat_msg = "E WifiKeyStore: Invalid certificate type for Suite-B"
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      logcat_search = self.dut.search_logcat(logcat_msg)
      self.log.info("Logcat search results: %s" % logcat_search)
      asserts.assert_true(logcat_search, "No valid error msg in logcat")
    else:
      asserts.fail("WPA3 Ent worked with insecure RSA key. Expected to fail.")

  @test_tracker_info(uuid="897957f3-de25-4f9e-b6fc-9d7798ea1e6f")
  def test_connect_to_wpa3_enterprise_expired_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert_expired,
        Ent.PRIVATE_KEY_ID: self.rsa2048_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
    }
    logcat_msg = "E WifiKeyStore: Invalid certificate type for Suite-B"
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      logcat_search = self.dut.search_logcat(logcat_msg)
      self.log.info("Logcat search results: %s" % logcat_search)
      asserts.assert_true(logcat_search, "No valid error msg in logcat")
    else:
      asserts.fail("WPA3 Ent worked with expired cert. Expected to fail.")

  @test_tracker_info(uuid="f7ab30e2-f2b5-488a-8667-e45920fc24d1")
  def test_connect_to_wpa3_enterprise_corrupted_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert_corrupted,
        Ent.PRIVATE_KEY_ID: self.rsa2048_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
    }
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      asserts.explicit_pass("Connection failed as expected.")
    else:
      asserts.fail("WPA3 Ent worked with corrupted cert. Expected to fail.")

  @test_tracker_info(uuid="f934f388-dc0b-4c78-a493-026b798c15ca")
  def test_connect_to_wpa3_enterprise_unsigned_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert_unsigned,
        Ent.PRIVATE_KEY_ID: self.rsa3072_client_key_unsigned,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]
    }
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      asserts.explicit_pass("Connection failed as expected.")
    else:
      asserts.fail("WPA3 Ent worked with unsigned cert. Expected to fail.")

  @test_tracker_info(uuid="7082dc90-5eb8-4055-8b48-b555a98a837a")
  def test_connect_to_wpa3_enterprise_wrong_domain_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert,
        Ent.PRIVATE_KEY_ID: self.rsa3072_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]+"_wrong"
    }
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      asserts.explicit_pass("Connection failed as expected.")
    else:
      asserts.fail("WPA3 Ent worked with unsigned cert. Expected to fail.")

  @test_tracker_info(uuid="9ad5fd82-f115-42c3-b8e8-520144485ea1")
  def test_network_selection_status_for_wpa3_ent_wrong_domain_rsa_cert(self):
    config = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.rsa3072_ca_cert,
        WifiEnums.SSID_KEY: self.wpa3_rsa3072_network[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.rsa3072_client_cert,
        Ent.PRIVATE_KEY_ID: self.rsa2048_client_key,
        WifiEnums.SECURITY: WPA3_SECURITY,
        "identity": self.wpa3_rsa3072_network["identity"],
        "domain_suffix_match": self.wpa3_rsa3072_network["domain"]+"_wrong"
    }
    try:
      wutils.connect_to_wifi_network(self.dut, config)
    except:
      asserts.assert_true(
          self.dut.droid.wifiIsNetworkTemporaryDisabledForNetwork(config),
          "WiFi network is not temporary disabled.")
      asserts.explicit_pass(
          "Connection failed with correct network selection status.")
    else:
      asserts.fail("WPA3 Ent worked with corrupted cert. Expected to fail.")
