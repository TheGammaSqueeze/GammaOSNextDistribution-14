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

import csv
import json
import os
import time
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils
from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.wifi import pdu_controller_utils

WifiEnums = wutils.WifiEnums
WAIT_BETWEEN_ACTIONS = 5

class WifiIOTConnectionTest(WifiBaseTest):
    """ Tests for wifi IOT Connection

        Test Bed Requirement:
          * One Android device
          * Wi-Fi IOT networks visible to the device
    """

    def __init__(self, controllers):
        WifiBaseTest.__init__(self, controllers)
        self.generate_test_list()

    def setup_class(self):
        self.dut = self.android_devices[0]
        self.pdu = pdu_controller_utils.create(self.user_params['Pdu'])[0]

        if hasattr(self, 'packet_capture'):
            self.packet_capture = self.packet_capture[0]

        wutils.wifi_test_device_init(self.dut)

        self.csv_write(["Project", "TimeStamp", "Build_ID", "Test_Name",
                        "Host", "RSSI", "Link_speed", "freq", "#iteration",
                        "#Pass"], )

        req_params = ['iot_password', 'pdu_wait_time',
                      'iot_ssid', 'Pdu', 'iot_connection_iteration']
        self.unpack_userparams(
            req_param_names=req_params)

    def setup_test(self):
        self.dut.droid.wakeLockAcquireBright()
        self.dut.droid.wakeUpNow()
        wutils.reset_wifi(self.dut)

    def teardown_test(self):
        self.dut.droid.wakeLockRelease()
        self.dut.droid.goToSleepNow()
        wutils.stop_pcap(self.packet_capture, self.pcap_procs, False)

    def on_fail(self, test_name, begin_time):
        self.dut.take_bug_report(test_name, begin_time)
        self.dut.cat_adb_log(test_name, begin_time)

    def csv_write(self, data):
        """Output .CSV file as a result.

        Args:
            data: a dict containing:
                'project', 'TimeStamp', 'test_name', 'test_name',
                'host', 'rssi', 'link_speed', 'freq', '#Iteration', '#Pass'
        """
        time_str = time.strftime("%Y-%m-%d")
        file_name_format = "wifi_iot_connection_" + time_str + ".csv"
        file_name = os.path.join(self.log_path, file_name_format)
        with open(file_name, "a", newline="") as csv_file:
            csv_writer = csv.writer(csv_file, delimiter=',')
            csv_writer.writerow(data)

    def get_wifi_info(self):
        """Get current connected WiFi AP's info.

        Returns:
            rssi: Wi-Fi rssi
            link_speed: Wi-Fi link speed
            freq: Wi-Fi freq
        """
        freq = "NA"
        link_speed = "NA"
        rssi = "NA"

        try:
            out = self.dut.adb.shell("iw wlan0 link")
            if out:
                for line in out.split("\n"):
                    if "freq:" in line:
                        freq = line.split()[1]
                    elif "signal" in line:
                        rssi = line.split()[1]
                    elif "bitrate" in line:
                        link_speed = line.split()[2]
        except AttributeError as e:
            self.log.debug("No wifi info, check Wi-Fi connection.", e)
        finally:
            return rssi, link_speed, freq

    def getprop(self, property_name):
        """Get dut's property

        Args:
            property_name: property name, e.g., "ro.build.product"
        Return:
            property
        """
        return self.dut.adb.getprop(property_name)

    def generate_test_list(self):
        """Generates a test list which is sorted by host number."""
        sorted_list = sorted(
            self.user_params["iot_ssid"], key=lambda ssid: ssid["host"])
        for test_item in sorted_list:
            self.init_test_case(self.wifi_iot_connection_test, test_item)

    def init_test_case(self, wifi_iot_connection_test, test_item):
        """Generates a single test case from the given data.

        Args:
            wifi_iot_connection_test: The base test case function to run.
            test_item: test case required info, include:
                "ssid", "uuid", "host", "band", "channel"
        """
        test_name = test_item["ssid"]
        test_tracker_uuid = test_item["uuid"]
        if not test_name.startswith("test_"):
            test_name = "test_{}".format(test_name)
        test_case = test_tracker_info(uuid=test_tracker_uuid)(
            lambda: wifi_iot_connection_test(test_item,
                                             self.user_params["iot_password"]))
        setattr(self, test_name, test_case)
        self.tests.append(test_name)

    def pdu_status_check(self):
        """ Check pdu status
        if pdu's currently ON outlet != next test case's host number,
        switch ON outlet to the host number.
        for example: pdu's outlet 3 is on, next test case host number is 4,
        turn off pdu outlet 3 and turn on outlet 4.
        """
        _, self.pdu_status = self.pdu.get_status()
        self.log.info("Current host of the pdu : {}".format(self.pdu_status))
        self.log.info("Next host of the test case : {}".format(self.host))
        if str(self.host) != self.pdu_status:
            self.pdu.off_all()
            self.log.info("Switch PDU to {}".format(self.host))
            self.pdu.turn_on_outlets(str(self.host))
            self.log.info("Wait {} secs to bring up the APs"
                          .format(self.user_params["pdu_wait_time"]))
            time.sleep(self.user_params["pdu_wait_time"])

    def start_packet_capture(self, band, channel):
        """Configure wireless packet capture on pre-defined channels.

        Args:
            band: '2G' or '5G'. pre-defined in config file.
            channel: Wi-fi Channel, pre-defined in the config file.
        """
        self.log.info("Capturing packets from Channel: {}".format(channel))
        result = self.packet_capture.configure_monitor_mode(band, channel)
        if not result:
            self.dut.log.error("Failed to configure channel "
                               "for {} band".format(band))
        self.pcap_procs = wutils.start_pcap(
            self.packet_capture, band, self.current_test_name)
        time.sleep(3)

    def ping_public_gateway_ip(self):
        """Ping 8.8.8.8"""
        try:
            ping_result = self.dut.adb.shell('ping -w 5 8.8.8.8')
            if '0%' in ping_result:
                self.dut.log.info('Ping success')
            return True
        except:
            self.dut.log.error('Faild to ping public gateway 8.8.8.8')
            return False

    def connect_to_network_and_ping(self, network):
        ssid = network[WifiEnums.SSID_KEY]
        connection_pass = 0
        for i in range(self.user_params['iot_connection_iteration']):
            self.dut.log.info('Connection iteration : {}'.format(i + 1))
            try:
                wutils.connect_to_wifi_network(self.dut, network,
                        num_of_connect_tries=1,
                        check_connectivity=False)
                time.sleep(WAIT_BETWEEN_ACTIONS)
                self.rssi, self.link_speed, self.freq = self.get_wifi_info()
                time.sleep(WAIT_BETWEEN_ACTIONS)
                if self.ping_public_gateway_ip():
                    connection_pass += 1
                wutils.wifi_forget_network(self.dut, ssid)
                self.dut.log.info("connection_pass: {}"
                                    .format(connection_pass))
                time.sleep(WAIT_BETWEEN_ACTIONS)
            except:
                self.dut.log.error("connection_fail")

        # Create a dictionary to store data in a json file.
        connection_result = {}
        connection_result["project"] = self.getprop("ro.build.product")
        connection_result["TimeStamp"] = time.strftime("%Y-%m-%d %H:%M:%S",
                                                       time.localtime())
        connection_result["Build ID"] = self.getprop("ro.build.id")
        connection_result["test_name"] = self.current_test_name
        connection_result["host"] = self.host
        connection_result['rssi'] = self.rssi
        connection_result['link_speed'] = self.link_speed
        connection_result['freq'] = self.freq
        connection_result['#Iteration'] = self.user_params[
            'iot_connection_iteration']
        connection_result['#Pass'] = connection_pass

        # Create a json file for each test case.
        results_file_path = os.path.join(self.log_path, "{}.json".format(
            self.current_test_name))

        with open(results_file_path, 'w') as results_file:
            json.dump(connection_result, results_file, indent=4)

        data = (self.getprop("ro.build.product"),
                time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
                self.getprop("ro.build.id"),
                connection_result["test_name"],
                connection_result["host"],
                connection_result['rssi'],
                connection_result['link_speed'],
                connection_result['freq'],
                connection_result['#Iteration'],
                connection_result['#Pass']
                )

        self.csv_write(data)

        self.rssi = "NA"
        self.link_speed = "NA"
        self.freq = "NA"

        if connection_pass < self.user_params['iot_connection_iteration']:
            raise signals.TestFailure("connection failed more than expected")

    def wifi_iot_connection_test(self, test_item, password):
        """The base test case logic for Wifi IOT generated test cases.

            1. Check pdu outlets is ON as expected.
            2. Scan SSIDs of 10 Wi-Fi APs.
            3. Start Packet Capture on pre-defined channel.
            3. Connect to the AP and ping Gateway 8.8.8.8 for 5 times.

        Args:
            test_item: Test info include:
                'ssid', 'host', 'uuid', 'band', 'channel'.
            password: pwd for login to the access point.
        """
        network = {'SSID': test_item["ssid"], 'password': password}
        self.host = test_item["host"]
        self.pdu_status_check()

        # Capturing wireless packets before DUT connect to Wi-Fi network.
        self.band = test_item["band"]
        self.channel = test_item["channel"]
        if hasattr(self, 'packet_capture'):
            self.start_packet_capture(self.band, self.channel)

        # Connect to Wi-Fi network and ping public gateway for 5 times.
        self.connect_to_network_and_ping(network)
