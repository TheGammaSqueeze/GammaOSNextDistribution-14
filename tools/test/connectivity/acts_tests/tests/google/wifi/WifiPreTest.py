#!/usr/bin/env python3
#
#   Copyright 2023 - The Android Open Source Project
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

import logging
import paramiko
import time
from typing import List

from acts.test_decorators import test_tracker_info
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts import signals
from acts.controllers.utils_lib.ssh import connection


_POLL_AP_RETRY_INTERVAL_SEC = 1
_WAIT_OPENWRT_AP_BOOT_SEC = 30
_NO_ATTENUATION = 0

class WifiPreTest(WifiBaseTest):
  """ Wi-Fi PreTest."""
  def __init__(self, configs):
    super().__init__(configs)
    self.enable_packet_log = True

  def setup_class(self):
    super().setup_class()

    req_params = ["Attenuator", "OpenWrtAP"]
    self.unpack_userparams(req_param_names=req_params)

    self.dut = self.android_devices[0]

    # Reboot OpenWrt APs.
    if "OpenWrtAP" in self.user_params:
      for i, openwrt in enumerate(self.access_points):
        logging.info(f"Rebooting OpenWrt AP: {openwrt.ssh_settings.hostname}")
        openwrt.reboot()
    time.sleep(_WAIT_OPENWRT_AP_BOOT_SEC)

    # Polling OpenWrt APs until they are ready.
    for i, openwrt in enumerate(self.access_points):
      if self.poll_openwrt_over_ssh(openwrt):
        continue
      else:
        raise signals.TestFailure(
          f"Unable to connect to OpenWrt AP: {openwrt.ssh_settings.hostname}")

    # Set all attenuators to 0 dB.
    for i, attenuator in enumerate(self.attenuators):
      attenuator.set_atten(_NO_ATTENUATION)
      logging.info(f"Attenuator {i} set to {_NO_ATTENUATION} dB")

    self.start_openwrt()

    wutils.list_scan_results(self.dut, wait_time=30)

  def setup_test(self):
    super().setup_test()
    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()
    wutils.wifi_toggle_state(self.dut, True)
    wutils.reset_wifi(self.dut)

  def teardown_test(self):
    super().teardown_test()
    self.dut.droid.wakeLockRelease()
    self.dut.droid.goToSleepNow()
    wutils.reset_wifi(self.dut)

  def poll_openwrt_over_ssh(self,openwrt,
                            retry_duration: int=60):
    """
    Attempt to establish an SSH connection with the device at the given IP address and port.

    Args:
      ip: The IP address of the device to connect to.
      port: The port number for SSH connection.
      username: The username for SSH authentication.
      password: The password for SSH authentication.
      retry_duration: The maximum duration in seconds to attempt reconnection
                                      before giving up.

    Returns:
      bool: True if the connection was successful, False otherwise.
    """
    ip = openwrt.ssh_settings.hostname
    start_time = time.time()
    while time.time() - start_time < retry_duration:
      try:
        logging.info(f"Attempt to connect to {ip}")
        openwrt.close()
        openwrt.ssh = connection.SshConnection(openwrt.ssh_settings)
        openwrt.ssh.setup_master_ssh()
        return True
      except (paramiko.ssh_exception.NoValidConnectionsError,
              paramiko.ssh_exception.AuthenticationException,
              paramiko.ssh_exception.SSHException,
              TimeoutError) as e:
        logging.info(f"Connection error: {e}, reconnecting {ip} "
                      f"in {retry_duration} seconds.")
        time.sleep(_POLL_AP_RETRY_INTERVAL_SEC)
    logging.info(f"Connection attempts exhausted. Unable to connect to {ip}.")
    return False

  def start_openwrt(self):
    """Enable OpenWrts to generate Open Wi-Fi networks."""

    if "OpenWrtAP" in self.user_params:
      logging.info("Launching OpenWrt AP...")
      self.configure_openwrt_ap_and_start(open_network=True,
                                          ap_count=len(self.access_points))
      self.open_networks = []
      for i in range(len(self.access_points)):
        self.open_networks.append(self.open_network[i]["2g"])
        self.open_networks.append(self.open_network[i]["5g"])

      # stdout APs' information.
      for i, openwrt in enumerate(self.access_points):
        openwrt.log.info(f"AP_{i} Info: ")
        openwrt.log.info(f"IP address: {openwrt.ssh_settings.hostname}")

        radios = ["radio0", "radio1"]
        for radio in radios:
          ssid_radio_map = openwrt.get_ifnames_for_ssids(radio)
          for ssid, radio_ifname in ssid_radio_map.items():
              openwrt.log.info(f"{radio_ifname}:  {ssid}")

        band_bssid_map = openwrt.get_bssids_for_wifi_networks()
        openwrt.log.info(band_bssid_map)

  @test_tracker_info(uuid="913605ea-38bf-492c-b634-d1823caed4b3")
  def test_connects_all_testbed_wifi_networks(self):
    """Test whether the DUT can successfully connect to restarted APs."""
    for network in self.open_networks:
      wutils.connect_to_wifi_network(self.dut, network)
