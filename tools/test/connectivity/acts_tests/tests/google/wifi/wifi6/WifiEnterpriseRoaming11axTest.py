#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from ..WifiEnterpriseRoamingTest import WifiEnterpriseRoamingTest

WifiEnums = wutils.WifiEnums
# EAP Macros
EAP = WifiEnums.Eap
EapPhase2 = WifiEnums.EapPhase2
Ent = WifiEnums.Enterprise


class WifiEnterpriseRoaming11axTest(WifiEnterpriseRoamingTest):
  """Tests for WPA2 Enterprise 11ax.

  Test Bed Requirement:
    One Android device, 2 Asus AXE11000 Access Point and Radius server
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_roaming_with_config_tls",
        "test_roaming_with_config_ttls_none",
        "test_roaming_with_config_ttls_pap",
        "test_roaming_with_config_ttls_mschap",
        "test_roaming_with_config_ttls_mschapv2",
        "test_roaming_with_config_ttls_gtc",
        "test_roaming_with_config_peap_mschapv2",
        "test_roaming_with_config_peap_gtc",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)

    self.dut = self.android_devices[0]
    req_params = [
        "attn_vals", "roam_interval", "ca_cert", "client_cert", "client_key",
        "eap_identity", "eap_password", "device_password", "wifi6_models",
        "bssid_map", "radius_conf_2g", "radius_conf_5g", "roaming_attn"
    ]
    self.unpack_userparams(req_params)
    self.ap1 = self.access_points[0]
    self.ap2 = self.access_points[1]
    self.ent_roaming_ssid = "test_ent_roaming"
    self.ap1.configure_ap({
        "2g": {
            "security": "wpa2",
            "radius_server_ip": self.radius_conf_2g["radius_server_ip"],
            "radius_server_port": self.radius_conf_2g["radius_server_port"],
            "radius_server_secret": self.radius_conf_2g["radius_server_secret"],
            "ssid": self.ent_roaming_ssid,
        }
    })
    self.ap2.configure_ap({
        "2g": {
            "security": "wpa2",
            "radius_server_ip": self.radius_conf_2g["radius_server_ip"],
            "radius_server_port": self.radius_conf_2g["radius_server_port"],
            "radius_server_secret": self.radius_conf_2g["radius_server_secret"],
            "ssid": self.ent_roaming_ssid,
        }
    })
    self.bssid_a = self.bssid_map[0]["2g"]
    self.bssid_b = self.bssid_map[1]["2g"]
    self.config_peap = {
        Ent.EAP: int(EAP.PEAP),
        Ent.CA_CERT: self.ca_cert,
        Ent.IDENTITY: self.eap_identity,
        Ent.PASSWORD: self.eap_password,
        Ent.PHASE2: int(EapPhase2.MSCHAPV2),
        WifiEnums.SSID_KEY: self.ent_roaming_ssid
    }
    self.config_tls = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.ca_cert,
        WifiEnums.SSID_KEY: self.ent_roaming_ssid,
        Ent.CLIENT_CERT: self.client_cert,
        Ent.PRIVATE_KEY_ID: self.client_key,
        Ent.IDENTITY: self.eap_identity,
    }
    self.config_ttls = {
        Ent.EAP: int(EAP.TTLS),
        Ent.CA_CERT: self.ca_cert,
        Ent.IDENTITY: self.eap_identity,
        Ent.PASSWORD: self.eap_password,
        Ent.PHASE2: int(EapPhase2.MSCHAPV2),
        WifiEnums.SSID_KEY: self.ent_roaming_ssid
    }
    self.attn_a = self.attenuators[0]
    self.attn_b = self.attenuators[2]

    # Set screen lock password so ConfigStore is unlocked.
    self.dut.droid.setDevicePassword(self.device_password)
