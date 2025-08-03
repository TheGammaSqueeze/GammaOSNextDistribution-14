#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
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

import itertools
import pprint
import time

import acts.signals
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils

from acts import asserts
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts.controllers import iperf_server as ipf
from acts.controllers import attenuator
from acts.controllers.sl4a_lib import rpc_client

import json
import logging
import math
import os
from acts import utils
import csv

import serial
import sys
import urllib.request

from acts_contrib.test_utils.wifi import wifi_performance_test_utils_RSSI as wperfutils

WifiEnums = wutils.WifiEnums


class WifiRvrTwTest(WifiBaseTest):
  """ Tests for wifi RVR performance.

        Test Bed Requirement:
          * One Android device
          * Wi-Fi networks visible to the device
  """
  TEST_TIMEOUT = 10
  IPERF_SETUP_TIME = 5
  TURN_TABLE_SETUP_TIME = 5

  def __init__(self, controllers):
    WifiBaseTest.__init__(self, controllers)

  def setup_class(self):
    self.dut = self.android_devices[0]

    req_params = ["rvr_networks", "rvr_test_params", "attenuators"]
    opt_params = ["angle_params", "usb_port"]
    self.unpack_userparams(
        req_param_names=req_params, opt_param_names=opt_params)
    asserts.assert_true(
        len(self.rvr_networks) > 0, "Need at least one network.")

    if "rvr_test_params" in self.user_params:
      self.iperf_server = self.iperf_servers[0]
      self.maxdb = self.rvr_test_params["rvr_atten_maxdb"]
      self.mindb = self.rvr_test_params["rvr_atten_mindb"]
      self.stepdb = self.rvr_test_params["rvr_atten_step"]
      self.country_code = self.rvr_test_params["country_code"]
    if "angle_params" in self.user_params:
      self.angle_list = self.angle_params
    if "usb_port" in self.user_params:
      self.turntable_port = self.read_comport(self.usb_port["turntable"])

    # Init DUT
    wutils.wifi_test_device_init(self.dut, self.country_code)
    self.dut.droid.bluetoothToggleState(False)
    utils.set_location_service(self.dut, False)
    wutils.wifi_toggle_state(self.dut, True)
    utils.subprocess.check_output(
        "adb root", shell=True, timeout=self.TEST_TIMEOUT)
    utils.subprocess.check_output(
        "adb shell settings put system screen_off_timeout 18000000",
        shell=True,
        timeout=self.TEST_TIMEOUT)
    utils.subprocess.check_output(
        "adb shell svc power stayon true",
        shell=True,
        timeout=self.TEST_TIMEOUT)

    # create folder for rvr test result
    self.log_path = os.path.join(logging.log_path, "rvr_results")
    utils.create_dir(self.log_path)

    Header = ("Test_date", "Project", "Device_SN", "ROM", "HW_Stage",
              "test_SSID", "Frequency", "Turn_table_orientation",
              "Attenuate_dB", "Signal_poll_avg_rssi", "Chain_0_rssi",
              "Chain_1_rssi", "Link_speed", "TX_throughput_Mbps",
              "RX_throughput_Mbps", "HE_Capable", "Country_code", "Channel",
              "WiFi_chip", "Type", "Host_name", "AP_model",
              "Incremental_build_id", "Build_type", "TCP_UDP_Protocol",
              "Security_type", "Test_tool", "Airplane_mode_status", "BT_status",
              "Bug_ID", "Comment")
    self.csv_write(Header)

  def setup_test(self):
    self.dut.droid.wakeLockAcquireBright()
    self.dut.droid.wakeUpNow()
    rom_info = self.get_rominfo()
    self.testdate = time.strftime("%Y-%m-%d", time.localtime())
    self.rom = rom_info[0]
    self.build_id = rom_info[1]
    self.build_type = rom_info[2]
    self.project = rom_info[3]
    self.ret_country_code = self.get_country_code()
    self.ret_hw_stage = self.get_hw_stage()
    self.ret_platform = wperfutils.detect_wifi_platform(self.dut)

  def teardown_test(self):
    self.dut.droid.wakeLockRelease()
    self.dut.droid.goToSleepNow()
    wutils.set_attns(self.attenuators, "default")

  def teardown_class(self):
    if "rvr_test_params" in self.user_params:
      self.iperf_server.stop()

  def on_fail(self, test_name, begin_time):
    self.dut.take_bug_report(test_name, begin_time)
    self.dut.cat_adb_log(test_name, begin_time)

  """Helper Functions"""

  def csv_write(self, data):
    """Output .CSV file for test result.

    Args:
        data: Dict containing attenuation, throughput and other meta data.
    """
    with open(
        "{}/Result.csv".format(self.log_path), "a", newline="") as csv_file:
      csv_writer = csv.writer(csv_file, delimiter=",")
      csv_writer.writerow(data)
      csv_file.close()

  def set_atten(self, db):
    """Setup attenuator dB for current test.

    Args:
       db: Attenuator setup dB.
    """
    if db < 0:
      db = 0
    elif db > 95:
      db = 95
    self.log.info("[Attenuation] %s", "Set dB = " + str(db) + "dB")
    for atten in self.attenuators:
      atten.set_atten(db)
      self.log.info("[Attenuation] %s",
                    "Current dB = " + str(atten.get_atten()) + "dB")
      retry = 0
      while atten.get_atten() != db and retry < 11:
        retry = retry + 1
        self.log.info(
            "[Attenuation] %s", "Fail to set Attenuator to " + str(db) + ", " +
            str(retry) + " times try to reset")
        self.set_atten(db)
      if retry == 11:
        self.log.info("Attenuation] %s",
                      "Retry Attenuator fail for 10 cycles, end test!")
        sys.exit()

  def read_comport(self, com):
    """Read com port for current test.

    Args:
        com: Serial port.

    Returns:
        port: Serial port with baud rate.
    """
    port = serial.Serial(com, 9600, timeout=1)
    time.sleep(1)
    return port

  def get_angle(self, port):
    """Get turn table angle for current test.

    Args:
        port: Turn table com port.

    Returns:
        angle: Angle from turn table.
    """
    angle = ""
    port.write("DG?;".encode())
    time.sleep(0.1)
    degree_data = port.readline().decode("utf-8")
    for data in range(len(degree_data)):
      if (degree_data[data].isdigit()) is True:
        angle = angle + degree_data[data]
    if angle == "":
      return -1
    return int(angle)

  def set_angle(self, port, angle):
    """Setup turn table angle for current test.

    Args:
        port: Turn table com port
        angle: Turn table setup angle
    """
    if angle > 359:
      angle = 359
    elif angle < 0:
      angle = 0
    self.log.info("Set angle to " + str(angle))
    input_angle = str("DG") + str(angle) + str(";")
    port.write(input_angle.encode())
    time.sleep(self.TURN_TABLE_SETUP_TIME)

  def check_angle(self, port, angle):
    """Check turn table angle for current test.

    Args:
        port: Turn table com port
        angle: Turn table setup angle
    """
    retrytime = self.TEST_TIMEOUT
    retry = 0
    while self.get_angle(port) != angle and retry < retrytime:
      retry = retry + 1
      self.log.info("Turntable] %s",
                    "Current angle = " + str(self.get_angle(port)))
      self.log.info(
          "Turntable] %s", "Fail set angle to " + str(angle) + ", " +
          str(retry) + " times try to reset")
      self.set_angle(port, angle)
      time.sleep(self.TURN_TABLE_SETUP_TIME)
    if retry == retrytime:
      self.log.info(
          "Turntable] %s",
          "Retry turntable fail for " + str(retry) + " cycles, end test!")
      sys.exit()

  def get_wifiinfo(self):
    """Get WiFi RSSI/ link speed/ frequency for current test.

    Returns:
        [rssi,link_speed,frequency]: DUT WiFi RSSI,Link speed and Frequency.
    """
    def is_number(string):
      for i in string:
        if i.isdigit() is False:
          if (i == "-" or i == "."):
            continue
          return str(-1)
      return string

    try:
      cmd = "adb shell iw wlan0 link"
      wifiinfo = utils.subprocess.check_output(
          cmd, shell=True, timeout=self.TEST_TIMEOUT)

      # Check RSSI Enhance
      rssi = self.get_rssi_func()

      # Check link speed
      link_speed = wifiinfo.decode(
          "utf-8")[wifiinfo.decode("utf-8").find("bitrate:") +
                   8:wifiinfo.decode("utf-8").find("Bit/s") - 2]
      link_speed = link_speed.strip(" ")
      link_speed = is_number(link_speed)
      # Check frequency
      frequency = wifiinfo.decode(
          "utf-8")[wifiinfo.decode("utf-8").find("freq:") +
                   6:wifiinfo.decode("utf-8").find("freq:") + 10]
      frequency = frequency.strip(" ")
      frequency = is_number(frequency)
    except:
      return -1, -1, -1
    return [rssi, link_speed, frequency]

  def get_rssi_func(self):
    """Get RSSI from brcm/qcom wifi chip.

    Returns:
         current_rssi: DUT WiFi RSSI.
    """
    if self.ret_platform == "brcm":
      rssi_future = wperfutils.get_connected_rssi_brcm(self.dut)
      signal_poll_avg_rssi_tmp = rssi_future.pop("signal_poll_avg_rssi").pop(
          "mean")
      chain_0_rssi_tmp = rssi_future.pop("chain_0_rssi").pop("mean")
      chain_1_rssi_tmp = rssi_future.pop("chain_1_rssi").pop("mean")
      current_rssi = {
          "signal_poll_avg_rssi": signal_poll_avg_rssi_tmp,
          "chain_0_rssi": chain_0_rssi_tmp,
          "chain_1_rssi": chain_1_rssi_tmp
      }
    elif self.ret_platform == "qcom":
      rssi_future = wperfutils.get_connected_rssi_qcom(
          self.dut, interface="wlan0")
      signal_poll_avg_rssi_tmp = rssi_future.pop("signal_poll_avg_rssi").pop(
          "mean")
      chain_0_rssi_tmp = rssi_future.pop("chain_0_rssi").pop("mean")
      chain_1_rssi_tmp = rssi_future.pop("chain_1_rssi").pop("mean")
      if math.isnan(signal_poll_avg_rssi_tmp):
        signal_poll_avg_rssi_tmp = -1
      if math.isnan(chain_0_rssi_tmp):
        chain_0_rssi_tmp = -1
      if math.isnan(chain_1_rssi_tmp):
        chain_1_rssi_tmp = -1

      if signal_poll_avg_rssi_tmp == -1 & chain_0_rssi_tmp == -1 & chain_1_rssi_tmp == -1:
        current_rssi = -1
      else:
        current_rssi = {
            "signal_poll_avg_rssi": signal_poll_avg_rssi_tmp,
            "chain_0_rssi": chain_0_rssi_tmp,
            "chain_1_rssi": chain_1_rssi_tmp
        }
    else:
      current_rssi = {
          "signal_poll_avg_rssi": float("nan"),
          "chain_0_rssi": float("nan"),
          "chain_1_rssi": float("nan")
      }
    return current_rssi

  def get_rominfo(self):
    """Get DUT ROM build info.

    Returns:
         rom, build_id, build_type, project: DUT Build info,Build ID,
         Build type, and Project name
    """
    rom = "NA"
    build_id = "NA"
    build_type = "NA"
    project = "NA"
    rominfo = self.dut.adb.shell("getprop ro.build.display.id").split()

    if rominfo:
      rom = rominfo[2]
      build_id = rominfo[3]
      project, build_type = rominfo[0].split("-")

    return rom, build_id, build_type, project

  def get_hw_stage(self):
    """Get DUT HW stage.

    Returns:
         hw_stage: DUT HW stage e.g. EVT/DVT/PVT..etc.
    """
    cmd = "adb shell getprop ro.boot.hardware.revision"
    hw_stage_temp = utils.subprocess.check_output(
        cmd, shell=True, timeout=self.TEST_TIMEOUT)
    hw_stage = hw_stage_temp.decode("utf-8").split("\n")[0]
    return hw_stage

  def get_country_code(self):
    """Get DUT country code.

    Returns:
         country_code: DUT country code e.g. US/JP/GE..etc.
    """
    cmd = "adb shell cmd wifi get-country-code"
    country_code_temp = utils.subprocess.check_output(
        cmd, shell=True, timeout=self.TEST_TIMEOUT)
    country_code = country_code_temp.decode("utf-8").split(" ")[4].split(
        "\n")[0]
    return country_code

  def get_channel(self):
    """Get DUT WiFi channel.

    Returns:
         country_code: DUT channel e.g. 6/36/37..etc.
    """
    if self.ret_platform == "brcm":
      cmd = 'adb shell wl assoc | grep "Primary channel:"'
      channel_temp = utils.subprocess.check_output(
          cmd, shell=True, timeout=self.TEST_TIMEOUT)
      channel = channel_temp.decode("utf-8").split(": ")[1].split("\n")[0]
    elif self.ret_platform == "qcom":
      cmd = "adb shell iw wlan0 info | grep channel"
      channel_temp = utils.subprocess.check_output(
          cmd, shell=True, timeout=self.TEST_TIMEOUT)
      channel = channel_temp.decode("utf-8").split(" ")[1].split("\n")[0]
    return channel

  def get_he_capable(self):
    """Get DUT WiFi high efficiency capable status .

    Returns:
         he_capable: DUT high efficiency capable status.
    """
    if self.ret_platform == "brcm":
      cmd = 'adb shell wl assoc | grep "Chanspec:"'
      he_temp = utils.subprocess.check_output(
          cmd, shell=True, timeout=self.TEST_TIMEOUT)
      he_capable = he_temp.decode("utf-8").split(": ")[1].split("\n")[0].split(
          "MHz")[0].split(" ")[3]
    elif self.ret_platform == "qcom":
      cmd = "adb shell iw wlan0 info | grep channel"
      he_temp = utils.subprocess.check_output(
          cmd, shell=True, timeout=self.TEST_TIMEOUT)
      he_capable = he_temp.decode("utf-8").split("width: ")[1].split(" ")[0]
    return he_capable

  def post_process_results(self, rvr_result):
    """Saves JSON formatted results.

    Args:
        rvr_result: Dict containing attenuation, throughput and other meta data
    Returns:
        wifiinfo[0]: To check WiFi connection by RSSI value
    """
    # Save output as text file
    wifiinfo = self.get_wifiinfo()
    if wifiinfo[0] != -1:
      rvr_result["signal_poll_avg_rssi"] = wifiinfo[0]["signal_poll_avg_rssi"]
      rvr_result["chain_0_rssi"] = wifiinfo[0]["chain_0_rssi"]
      rvr_result["chain_1_rssi"] = wifiinfo[0]["chain_1_rssi"]
    else:
      rvr_result["signal_poll_avg_rssi"] = wifiinfo[0]
      rvr_result["chain_0_rssi"] = wifiinfo[0]
      rvr_result["chain_1_rssi"] = wifiinfo[0]
    if rvr_result["signal_poll_avg_rssi"] == -1:
      rvr_result["channel"] = "NA"
    else:
      rvr_result["channel"] = self.ret_channel
    rvr_result["country_code"] = self.ret_country_code
    rvr_result["hw_stage"] = self.ret_hw_stage
    rvr_result["wifi_chip"] = self.ret_platform
    rvr_result["test_ssid"] = self.ssid
    rvr_result["test_angle"] = self.angle_list[self.angle]
    rvr_result["test_dB"] = self.db
    rvr_result["test_link_speed"] = wifiinfo[1]
    rvr_result["test_frequency"] = wifiinfo[2]

    data = (
        self.testdate,
        self.project,
        self.dut.serial,
        self.rom,
        rvr_result["hw_stage"],
        rvr_result["test_ssid"],
        rvr_result["test_frequency"],
        rvr_result["test_angle"],
        rvr_result["test_dB"],
        rvr_result["signal_poll_avg_rssi"],
        rvr_result["chain_0_rssi"],
        rvr_result["chain_1_rssi"],
        rvr_result["test_link_speed"],
        rvr_result["throughput_TX"][0],
        rvr_result["throughput_RX"][0],
        "HE" + self.he_capable,
        rvr_result["country_code"],
        rvr_result["channel"],
        rvr_result["wifi_chip"],
        "OTA_RvR",
        "OTA_Testbed2",
        "RAXE500",
        self.build_id,
        self.build_type,
        "TCP",
        "WPA3",
        "iperf3",
        "OFF",
        "OFF",
    )
    self.csv_write(data)

    results_file_path = "{}/{}_angle{}_{}dB.json".format(
        self.log_path, self.ssid, self.angle_list[self.angle], self.db)
    with open(results_file_path, "w") as results_file:
      json.dump(rvr_result, results_file, indent=4)
    return wifiinfo[0]

  def connect_to_wifi_network(self, network):
    """Connection logic for wifi networks.

    Args:
        params: Dictionary with network info.
    """
    ssid = network[WifiEnums.SSID_KEY]
    self.dut.ed.clear_all_events()
    wutils.start_wifi_connection_scan(self.dut)
    scan_results = self.dut.droid.wifiGetScanResults()
    wutils.assert_network_in_list({WifiEnums.SSID_KEY: ssid}, scan_results)
    wutils.wifi_connect(self.dut, network, num_of_tries=3)

  def run_iperf_init(self, network):
    self.iperf_server.start(tag="init")
    self.log.info("[Iperf] %s", "Starting iperf traffic init.")
    time.sleep(self.IPERF_SETUP_TIME)
    try:
      port_arg = "-p {} -J -R -t10".format(self.iperf_server.port)
      self.dut.run_iperf_client(
          self.rvr_test_params["iperf_server_address"],
          port_arg,
          timeout=self.rvr_test_params["iperf_duration"] + self.TEST_TIMEOUT)
      self.iperf_server.stop()
      self.log.info("[Iperf] %s", "iperf traffic init Pass")
    except:
      self.log.warning("ValueError: iperf init ERROR.")

  def run_iperf_client(self, network):
    """Run iperf TX throughput after connection.

    Args:
        network: Dictionary with network info.

    Returns:
        rvr_result: Dict containing TX rvr_results.
    """
    rvr_result = []
    try:
      self.iperf_server.start(tag="TX_server_{}_angle{}_{}dB".format(
          self.ssid, self.angle_list[self.angle], self.db))
      ssid = network[WifiEnums.SSID_KEY]
      self.log.info("[Iperf] %s",
                    "Starting iperf traffic TX through {}".format(ssid))
      time.sleep(self.IPERF_SETUP_TIME)
      port_arg = "-p {} -J {}".format(self.iperf_server.port,
                                      self.rvr_test_params["iperf_port_arg"])
      success, data = self.dut.run_iperf_client(
          self.rvr_test_params["iperf_server_address"],
          port_arg,
          timeout=self.rvr_test_params["iperf_duration"] + self.TEST_TIMEOUT)
      # Parse and log result
      client_output_path = os.path.join(
          self.iperf_server.log_path,
          "IperfDUT,{},TX_client_{}_angle{}_{}dB".format(
              self.iperf_server.port, self.ssid, self.angle_list[self.angle],
              self.db))
      with open(client_output_path, "w") as out_file:
        out_file.write("\n".join(data))
      self.iperf_server.stop()

      iperf_file = self.iperf_server.log_files[-1]
      iperf_result = ipf.IPerfResult(iperf_file)
      curr_throughput = (math.fsum(iperf_result.instantaneous_rates[
          self.rvr_test_params["iperf_ignored_interval"]:-1]) /
                         len(iperf_result.instantaneous_rates[
                             self.rvr_test_params["iperf_ignored_interval"]:-1])
                        ) * 8 * (1.024**2)
      rvr_result.append(curr_throughput)
      self.log.info(
          "[Iperf] %s", "TX Throughput at {0:.2f} dB is {1:.2f} Mbps".format(
              self.db, curr_throughput))
      self.log.debug(pprint.pformat(data))
      asserts.assert_true(success, "Error occurred in iPerf traffic.")
      return rvr_result
    except:
      rvr_result = ["NA"]
      self.log.warning("ValueError: TX iperf ERROR.")
      self.iperf_server.stop()
      return rvr_result

  def run_iperf_server(self, network):
    """Run iperf RX throughput after connection.

    Args:
        network: Dictionary with network info.

    Returns:
        rvr_result: Dict containing RX rvr_results.
    """

    rvr_result = []
    try:
      self.iperf_server.start(tag="RX_client_{}_angle{}_{}dB".format(
          self.ssid, self.angle_list[self.angle], self.db))
      ssid = network[WifiEnums.SSID_KEY]
      self.log.info("[Iperf] %s",
                    "Starting iperf traffic RX through {}".format(ssid))
      time.sleep(self.IPERF_SETUP_TIME)
      port_arg = "-p {} -J -R {}".format(self.iperf_server.port,
                                         self.rvr_test_params["iperf_port_arg"])
      success, data = self.dut.run_iperf_client(
          self.rvr_test_params["iperf_server_address"],
          port_arg,
          timeout=self.rvr_test_params["iperf_duration"] + self.TEST_TIMEOUT)
      # Parse and log result
      client_output_path = os.path.join(
          self.iperf_server.log_path,
          "IperfDUT,{},RX_server_{}_angle{}_{}dB".format(
              self.iperf_server.port, self.ssid, self.angle_list[self.angle],
              self.db))
      with open(client_output_path, "w") as out_file:
        out_file.write("\n".join(data))
      self.iperf_server.stop()

      iperf_file = client_output_path
      iperf_result = ipf.IPerfResult(iperf_file)
      curr_throughput = (math.fsum(iperf_result.instantaneous_rates[
          self.rvr_test_params["iperf_ignored_interval"]:-1]) /
                         len(iperf_result.instantaneous_rates[
                             self.rvr_test_params["iperf_ignored_interval"]:-1])
                        ) * 8 * (1.024**2)
      rvr_result.append(curr_throughput)
      self.log.info(
          "[Iperf] %s", "RX Throughput at {0:.2f} dB is {1:.2f} Mbps".format(
              self.db, curr_throughput))

      self.log.debug(pprint.pformat(data))
      asserts.assert_true(success, "Error occurred in iPerf traffic.")
      return rvr_result
    except:
      rvr_result = ["NA"]
      self.log.warning("ValueError: RX iperf ERROR.")
      self.iperf_server.stop()
      return rvr_result

  def iperf_test_func(self, network):
    """Main function to test iperf TX/RX.

    Args:
        network: Dictionary with network info.
    """
    # Initialize
    rvr_result = {}
    # Run RvR and log result
    rvr_result["throughput_RX"] = self.run_iperf_server(network)
    retry_time = 2
    for retry in range(retry_time):
      if rvr_result["throughput_RX"] == ["NA"]:
        if not self.iperf_retry():
          time.sleep(self.IPERF_SETUP_TIME)
          rvr_result["throughput_RX"] = self.run_iperf_server(network)
        else:
          break
      else:
        break
    rvr_result["throughput_TX"] = self.run_iperf_client(network)
    retry_time = 2
    for retry in range(retry_time):
      if rvr_result["throughput_TX"] == ["NA"]:
        if not self.iperf_retry():
          time.sleep(self.IPERF_SETUP_TIME)
          rvr_result["throughput_TX"] = self.run_iperf_client(network)
        else:
          break
      else:
        break
    self.post_process_results(rvr_result)
    self.rssi = wifiinfo[0]
    return self.rssi

  def iperf_retry(self):
    """Check iperf TX/RX status and retry."""
    try:
      cmd = "adb -s {} shell pidof iperf3| xargs adb shell kill -9".format(
          self.dut.serial)
      utils.subprocess.call(cmd, shell=True, timeout=self.TEST_TIMEOUT)
      self.log.warning("ValueError: Killed DUT iperf process, keep test")
    except:
      self.log.info("[Iperf] %s", "No iperf DUT process found, keep test")

    wifiinfo = self.get_wifiinfo()
    print("--[iperf_retry]--", wifiinfo[0])
    self.log.info("[WiFiinfo] %s", "Current RSSI = " + str(wifiinfo[0]) + "dBm")
    if wifiinfo[0] == -1:
      self.log.warning("ValueError: Cannot get RSSI, stop throughput test")
      return True
    else:
      return False

  def rvr_test(self, network):
    """Test function to run RvR.

    The function runs an RvR test in the current device/AP configuration.
    Function is called from another wrapper function that sets up the
    testbed for the RvR test

    Args:
        params: Dictionary with network info
    """
    self.ssid = network[WifiEnums.SSID_KEY]
    self.log.info("Start rvr test")

    for angle in range(len(self.angle_list)):
      self.angle = angle
      self.set_angle(self.turntable_port, self.angle_list[angle])
      self.check_angle(self.turntable_port, self.angle_list[angle])
      self.set_atten(0)
      self.connect_to_wifi_network(network)
      self.ret_channel = self.get_channel()
      self.he_capable = self.get_he_capable()
      self.run_iperf_init(network)
      for db in range(self.mindb, self.maxdb + self.stepdb, self.stepdb):
        self.db = db
        self.set_atten(self.db)
        self.iperf_test_func(network)
        if self.rssi == -1:
          self.log.warning("ValueError: Cannot get RSSI. Run next angle")
          break
        else:
          continue
      wutils.reset_wifi(self.dut)

  """Tests"""
  def test_rvr_2g(self):
    network = self.rvr_networks[0]
    self.rvr_test(network)

  def test_rvr_5g(self):
    network = self.rvr_networks[1]
    self.rvr_test(network)

  def test_rvr_6g(self):
    network = self.rvr_networks[2]
    self.rvr_test(network)
