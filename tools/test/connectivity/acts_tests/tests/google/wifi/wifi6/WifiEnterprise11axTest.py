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
from ..WifiEnterpriseTest import WifiEnterpriseTest

WifiEnums = wutils.WifiEnums
# EAP Macros
EAP = WifiEnums.Eap
EapPhase2 = WifiEnums.EapPhase2
Ent = WifiEnums.Enterprise


class WifiEnterprise11axTest(WifiEnterpriseTest):
  """Tests for WPA2 Enterprise 11ax.

  Test Bed Requirement:
    One Android device, 1 Asus AXE11000 Access Point and Radius server
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.tests = (
        "test_eap_connect_with_config_tls",
        "test_eap_connect_with_config_sim",
        "test_eap_connect_with_config_aka",
        "test_eap_connect_with_config_aka_prime",
        "test_eap_connect_with_config_ttls_none",
        "test_eap_connect_with_config_ttls_pap",
        "test_eap_connect_with_config_ttls_mschap",
        "test_eap_connect_with_config_ttls_mschapv2",
        "test_eap_connect_with_config_ttls_gtc",
        "test_eap_connect_with_config_peap0_mschapv2",
        "test_eap_connect_with_config_peap0_gtc",
        "test_eap_connect_with_config_peap1_mschapv2",
        "test_eap_connect_with_config_peap1_gtc",
        "test_eap_connect_config_store_with_config_tls",
        "test_eap_connect_config_store_with_config_sim",
        "test_eap_connect_config_store_with_config_aka",
        "test_eap_connect_config_store_with_config_aka_prime",
        "test_eap_connect_config_store_with_config_ttls_none",
        "test_eap_connect_config_store_with_config_ttls_pap",
        "test_eap_connect_config_store_with_config_ttls_mschap",
        "test_eap_connect_config_store_with_config_ttls_mschapv2",
        "test_eap_connect_config_store_with_config_ttls_gtc",
        "test_eap_connect_config_store_with_config_peap0_mschapv2",
        "test_eap_connect_config_store_with_config_peap0_gtc",
        "test_eap_connect_config_store_with_config_peap1_mschapv2",
        "test_eap_connect_config_store_with_config_peap1_gtc",
    )

  def setup_class(self):
    WifiBaseTest.setup_class(self)
    self.dut = self.android_devices[0]
    required_userparam_names = [
        "ca_cert", "client_cert", "client_key", "eap_identity", "eap_password",
        "device_password", "radius_conf_2g", "radius_conf_5g", "wifi6_models"
    ]
    self.unpack_userparams(required_userparam_names, ocsp=0)
    self.ap = self.access_points[0]
    self.ap.configure_ap({
        "2g": {
            "security": "wpa2",
            "radius_server_ip": self.radius_conf_2g["radius_server_ip"],
            "radius_server_port": self.radius_conf_2g["radius_server_port"],
            "radius_server_secret": self.radius_conf_2g["radius_server_secret"],
        },
        "5g": {
            "security": "wpa2",
            "radius_server_ip": self.radius_conf_5g["radius_server_ip"],
            "radius_server_port": self.radius_conf_5g["radius_server_port"],
            "radius_server_secret": self.radius_conf_5g["radius_server_secret"],
        }
    })
    self.ent_network_2g = self.ap.get_wifi_network("2g")
    self.ent_network_5g = self.ap.get_wifi_network("5g")

    # Default configs for EAP networks.
    self.config_peap0 = {
        Ent.EAP: int(EAP.PEAP),
        Ent.CA_CERT: self.ca_cert,
        Ent.IDENTITY: self.eap_identity,
        Ent.PASSWORD: self.eap_password,
        Ent.PHASE2: int(EapPhase2.MSCHAPV2),
        WifiEnums.SSID_KEY: self.ent_network_5g[WifiEnums.SSID_KEY],
        Ent.OCSP: self.ocsp,
    }
    self.config_peap1 = dict(self.config_peap0)
    self.config_peap1[WifiEnums.SSID_KEY] = self.ent_network_2g[
        WifiEnums.SSID_KEY]
    self.config_tls = {
        Ent.EAP: int(EAP.TLS),
        Ent.CA_CERT: self.ca_cert,
        WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
        Ent.CLIENT_CERT: self.client_cert,
        Ent.PRIVATE_KEY_ID: self.client_key,
        Ent.IDENTITY: self.eap_identity,
        Ent.OCSP: self.ocsp,
    }
    self.config_ttls = {
        Ent.EAP: int(EAP.TTLS),
        Ent.CA_CERT: self.ca_cert,
        Ent.IDENTITY: self.eap_identity,
        Ent.PASSWORD: self.eap_password,
        Ent.PHASE2: int(EapPhase2.MSCHAPV2),
        WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
        Ent.OCSP: self.ocsp,
    }
    self.config_sim = {
        Ent.EAP: int(EAP.SIM),
        WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
    }
    self.config_aka = {
        Ent.EAP: int(EAP.AKA),
        WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
    }
    self.config_aka_prime = {
        Ent.EAP: int(EAP.AKA_PRIME),
        WifiEnums.SSID_KEY: self.ent_network_2g[WifiEnums.SSID_KEY],
    }

    # Set screen lock password so ConfigStore is unlocked.
    self.dut.droid.setDevicePassword(self.device_password)
