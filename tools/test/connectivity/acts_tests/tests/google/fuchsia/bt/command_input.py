#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Python script for wrappers to various libraries.

Class CmdInput inherts from the cmd library.

Functions that start with "do_" have a method
signature that doesn't match the actual command
line command and that is intended. This is so the
"help" command knows what to display (in this case
the documentation of the command itself).

For example:
Looking at the function "do_tool_set_target_device_name"
has the inputs self and line which is expected of this type
of method signature. When the "help" command is done on the
method name you get the function documentation as such:

(Cmd) help tool_set_target_device_name

        Description: Reset the target device name.
        Input(s):
            device_name: Required. The advertising name to connect to.
        Usage: tool_set_target_device_name new_target_device name
          Examples:
            tool_set_target_device_name le_watch

This is all to say this documentation pattern is expected.

"""

from acts_contrib.test_utils.audio_analysis_lib.check_quality import quality_analysis
from acts_contrib.test_utils.bt.bt_constants import audio_bits_per_sample_32
from acts_contrib.test_utils.bt.bt_constants import audio_sample_rate_48000
from acts_contrib.test_utils.abstract_devices.bluetooth_device import create_bluetooth_device
from acts_contrib.test_utils.bt.bt_constants import bt_attribute_values
from acts_contrib.test_utils.bt.bt_constants import sig_appearance_constants
from acts_contrib.test_utils.bt.bt_constants import sig_uuid_constants
from acts_contrib.test_utils.fuchsia.sdp_records import sdp_pts_record_list

import acts_contrib.test_utils.bt.gatt_test_database as gatt_test_database

import cmd
import pprint
import time
"""Various Global Strings"""
BASE_UUID = sig_uuid_constants['BASE_UUID']
CMD_LOG = "CMD {} result: {}"
FAILURE = "CMD {} threw exception: {}"
BASIC_ADV_NAME = "fs_test"


class CommandInput(cmd.Cmd):
    ble_adv_interval = 1000
    ble_adv_appearance = None
    ble_adv_data_include_tx_power_level = False
    ble_adv_include_name = True
    ble_adv_include_scan_response = False
    ble_adv_name = "fs_test"
    ble_adv_data_manufacturer_data = None
    ble_adv_data_service_data = None
    ble_adv_data_service_uuid_list = None
    ble_adv_data_uris = None

    bt_control_ids = []
    bt_control_names = []
    bt_control_devices = []
    bt_scan_poll_timer = 0.5
    target_device_name = ""
    le_ids = []
    unique_mac_addr_id = None

    def setup_vars(self, dut, target_device_name, log):
        self.pri_dut = dut
        # Note: test_dut is the start of a slow conversion from a Fuchsia specific
        # Tool to an abstract_device tool. Only commands that use test_dut will work
        # Otherwise this tool is primarially targeted at Fuchsia devices.
        self.test_dut = create_bluetooth_device(self.pri_dut)
        self.test_dut.initialize_bluetooth_controller()
        self.target_device_name = target_device_name
        self.log = log

    def emptyline(self):
        pass

    def do_EOF(self, line):
        "End Script"
        return True

    """ Useful Helper functions and cmd line tooling """

    def str_to_bool(self, s):
        if s.lower() == 'true':
            return True
        elif s.lower() == 'false':
            return False

    def _find_unique_id_over_le(self):
        scan_filter = {"name_substring": self.target_device_name}
        self.unique_mac_addr_id = None
        self.pri_dut.sl4f.gattc_lib.bleStartBleScan(scan_filter)
        tries = 10
        for i in range(tries):
            time.sleep(self.bt_scan_poll_timer)
            scan_res = self.pri_dut.sl4f.gattc_lib.bleGetDiscoveredDevices(
            )['result']
            for device in scan_res:
                name, did, connectable = device["name"], device["id"], device[
                    "connectable"]
                if (self.target_device_name in name):
                    self.unique_mac_addr_id = did
                    self.log.info(
                        "Successfully found device: name, id: {}, {}".format(
                            name, did))
                    break
            if self.unique_mac_addr_id:
                break
        self.pri_dut.sl4f.gattc_lib.bleStopBleScan()

    def _find_unique_id_over_bt_control(self):
        self.unique_mac_addr_id = None
        self.bt_control_devices = []
        self.pri_dut.sl4f.bts_lib.requestDiscovery(True)
        tries = 10
        for i in range(tries):
            if self.unique_mac_addr_id:
                break
            time.sleep(self.bt_scan_poll_timer)
            device_list = self.pri_dut.sl4f.bts_lib.getKnownRemoteDevices(
            )['result']
            for id_dict in device_list:
                device = device_list[id_dict]
                self.bt_control_devices.append(device)
                name = None
                if device['name'] is not None:
                    name = device['name']
                did, address = device['id'], device['address']

                self.bt_control_ids.append(did)
                if name is not None:
                    self.bt_control_names.append(name)
                    if self.target_device_name in name:
                        self.unique_mac_addr_id = did
                        self.log.info(
                            "Successfully found device: name, id, address: {}, {}, {}"
                            .format(name, did, address))
                        break
        self.pri_dut.sl4f.bts_lib.requestDiscovery(False)

    def do_tool_take_bt_snoop_log(self, custom_name):
        """
        Description: Takes the bt snoop log from the Fuchsia device.
        Logs will show up in your config files' logpath directory.

        Input(s):
            custom_name: Optional. Override the default pcap file name.

        Usage: tool_set_target_device_name new_target_device name
          Examples:
            tool_take_bt_snoop_log connection_error
            tool_take_bt_snoop_log
        """
        self.pri_dut.take_bt_snoop_log(custom_name)

    def do_tool_refresh_unique_id(self, line):
        """
        Description: Refresh command line tool mac unique id.
        Usage:
          Examples:
            tool_refresh_unique_id
        """
        try:
            self._find_unique_id_over_le()
        except Exception as err:
            self.log.error(
                "Failed to scan or find scan result: {}".format(err))

    def do_tool_refresh_unique_id_using_bt_control(self, line):
        """
        Description: Refresh command line tool mac unique id.
        Usage:
          Examples:
            tool_refresh_unique_id_using_bt_control
        """
        try:
            self._find_unique_id_over_bt_control()
        except Exception as err:
            self.log.error(
                "Failed to scan or find scan result: {}".format(err))

    def do_tool_set_target_device_name(self, line):
        """
        Description: Reset the target device name.
        Input(s):
            device_name: Required. The advertising name to connect to.
        Usage: tool_set_target_device_name new_target_device name
          Examples:
            tool_set_target_device_name le_watch
        """
        self.log.info("Setting target_device_name to: {}".format(line))
        self.target_device_name = line

    def do_tool_set_unique_mac_addr_id(self, line):
        """
        Description: Sets the unique mac address id (Specific to Fuchsia)
        Input(s):
            device_id: Required. The id to set the unique mac address id to
        Usage: tool_set_unique_mac_addr_id device_id
          Examples:
            tool_set_unique_mac_addr_id 7fb2cae53aad9e0d
        """
        self.unique_mac_addr_id = line

    """Begin BLE advertise wrappers"""

    def complete_ble_adv_data_include_name(self, text, line, begidx, endidx):
        roles = ["true", "false"]
        if not text:
            completions = roles
        else:
            completions = [s for s in roles if s.startswith(text)]
        return completions

    def do_ble_adv_data_include_name(self, line):
        cmd = "Include name in the advertisement."
        try:
            self.ble_adv_include_name = self.str_to_bool(line)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_ble_adv_data_set_name(self, line):
        cmd = "Set the name to be included in the advertisement."
        try:
            self.ble_adv_name = line
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_ble_adv_data_set_appearance(self, text, line, begidx, endidx):
        if not text:
            completions = list(sig_appearance_constants.keys())
        else:
            completions = [
                s for s in sig_appearance_constants.keys()
                if s.startswith(text)
            ]
        return completions

    def do_ble_adv_data_set_appearance(self, line):
        cmd = "Set the appearance to known SIG values."
        try:
            self.ble_adv_appearance = line
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_ble_adv_data_include_tx_power_level(self, text, line, begidx,
                                                     endidx):
        options = ['true', 'false']
        if not text:
            completions = list(options)[:]
        else:
            completions = [s for s in options if s.startswith(text)]
        return completions

    def do_ble_adv_data_include_tx_power_level(self, line):
        """Include the tx_power_level in the advertising data.
        Description: Adds tx_power_level to the advertisement data to the BLE
            advertisement.
        Input(s):
            value: Required. True or False
        Usage: ble_adv_data_include_tx_power_level bool_value
          Examples:
            ble_adv_data_include_tx_power_level true
            ble_adv_data_include_tx_power_level false
        """
        cmd = "Include tx_power_level in advertisement."
        try:
            self.ble_adv_data_include_tx_power_level = self.str_to_bool(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def complete_ble_adv_include_scan_response(self, text, line, begidx,
                                               endidx):
        options = ['true', 'false']
        if not text:
            completions = list(options)[:]
        else:
            completions = [s for s in options if s.startswith(text)]
        return completions

    def do_ble_adv_include_scan_response(self, line):
        """Include scan response in advertisement. inputs: [true|false]
            Note: Currently just sets the scan response data to the
                Advertisement data.
        """
        cmd = "Include tx_power_level in advertisement."
        try:
            self.ble_adv_include_scan_response = self.str_to_bool(line)
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_add_manufacturer_data(self, line):
        """Include manufacturer id and data to the advertisment
        Description: Adds manufacturer data to the BLE advertisement.
        Input(s):
            id: Required. The int representing the manufacturer id.
            data: Required. The string representing the data.
        Usage: ble_adv_data_add_manufacturer_data id data
          Examples:
            ble_adv_data_add_manufacturer_data 1 test
        """
        cmd = "Include manufacturer id and data to the advertisment."
        try:

            info = line.split()
            if self.ble_adv_data_manufacturer_data is None:
                self.ble_adv_data_manufacturer_data = []
            self.ble_adv_data_manufacturer_data.append({
                "id": int(info[0]),
                "data": info[1]
            })
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_add_service_data(self, line):
        """Include service data to the advertisment
        Description: Adds service data to the BLE advertisement.
        Input(s):
            uuid: Required. The string representing the uuid.
            data: Required. The string representing the data.
        Usage: ble_adv_data_add_service_data uuid data
          Examples:
            ble_adv_data_add_service_data 00001801-0000-1000-8000-00805f9b34fb test
        """
        cmd = "Include manufacturer id and data to the advertisment."
        try:
            info = line.split()
            if self.ble_adv_data_service_data is None:
                self.ble_adv_data_service_data = []
            self.ble_adv_data_service_data.append({
                "uuid": info[0],
                "data": info[1]
            })
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_add_service_uuid_list(self, line):
        """Include a list of service uuids to the advertisment:
        Description: Adds service uuid list to the BLE advertisement.
        Input(s):
            uuid: Required. A list of N string UUIDs to add.
        Usage: ble_adv_add_service_uuid_list uuid0 uuid1 ... uuidN
          Examples:
            ble_adv_add_service_uuid_list 00001801-0000-1000-8000-00805f9b34fb
            ble_adv_add_service_uuid_list 00001801-0000-1000-8000-00805f9b34fb 00001802-0000-1000-8000-00805f9b34fb
        """
        cmd = "Include service uuid list to the advertisment data."
        try:
            self.ble_adv_data_service_uuid_list = line
        except Exception as err:
            self.log.info(FAILURE.format(cmd, err))

    def do_ble_adv_data_set_uris(self, uris):
        """Set the URIs of the LE advertisement data:
        Description: Adds list of String UIRs
          See (RFC 3986 1.1.2 https://tools.ietf.org/html/rfc3986)
          Valid URI examples:
            ftp://ftp.is.co.za/rfc/rfc1808.txt
            http://www.ietf.org/rfc/rfc2396.txt
            ldap://[2001:db8::7]/c=GB?objectClass?one
            mailto:John.Doe@example.com
            news:comp.infosystems.www.servers.unix
            tel:+1-816-555-1212
            telnet://192.0.2.16:80/
            urn:oasis:names:specification:docbook:dtd:xml:4.1.2
        Input(s):
            uris: Required. A list of URIs to add.
        Usage: ble_adv_data_set_uris uri0 uri1 ... uriN
          Examples:
            ble_adv_data_set_uris telnet://192.0.2.16:80/
            ble_adv_data_set_uris tel:+1-816-555-1212
        """
        cmd = "Set the appearance to known SIG values."
        try:
            self.ble_adv_data_uris = uris.split()
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def start_advertisement(self, connectable):
        """ Handle setting advertising data and the advertisement
            Note: After advertisement is successful, clears values set for
                * Manufacturer data
                * Appearance information
                * Scan Response
                * Service UUIDs
                * URI list
            Args:
                connectable: Bool of whether to start a connectable
                    advertisement or not.
        """
        adv_data_name = self.ble_adv_name
        if not self.ble_adv_include_name:
            adv_data_name = None

        manufacturer_data = self.ble_adv_data_manufacturer_data

        tx_power_level = None
        if self.ble_adv_data_include_tx_power_level:
            tx_power_level = 1  # Not yet implemented so set to 1

        scan_response = self.ble_adv_include_scan_response

        adv_data = {
            "name": adv_data_name,
            "appearance": self.ble_adv_appearance,
            "service_data": self.ble_adv_data_service_data,
            "tx_power_level": tx_power_level,
            "service_uuids": self.ble_adv_data_service_uuid_list,
            "manufacturer_data": manufacturer_data,
            "uris": self.ble_adv_data_uris,
        }

        if not self.ble_adv_include_scan_response:
            scan_response = None
        else:
            scan_response = adv_data

        result = self.pri_dut.sl4f.ble_lib.bleStartBleAdvertising(
            adv_data, scan_response, self.ble_adv_interval, connectable)
        self.log.info("Result of starting advertisement: {}".format(result))
        self.ble_adv_data_manufacturer_data = None
        self.ble_adv_appearance = None
        self.ble_adv_include_scan_response = False
        self.ble_adv_data_service_uuid_list = None
        self.ble_adv_data_uris = None
        self.ble_adv_data_service_data = None

    def do_ble_start_generic_connectable_advertisement(self, line):
        """
        Description: Start a connectable LE advertisement

        Usage: ble_start_generic_connectable_advertisement
        """
        cmd = "Start a connectable LE advertisement"
        try:
            connectable = True
            self.start_advertisement(connectable)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_ble_start_generic_nonconnectable_advertisement(self, line):
        """
        Description: Start a non-connectable LE advertisement

        Usage: ble_start_generic_nonconnectable_advertisement
        """
        cmd = "Start a nonconnectable LE advertisement"
        try:
            connectable = False
            self.start_advertisement(connectable)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_ble_stop_advertisement(self, line):
        """
        Description: Stop a BLE advertisement.
        Usage: ble_stop_advertisement
        """
        cmd = "Stop a connectable LE advertisement"
        try:
            self.pri_dut.sl4f.ble_lib.bleStopBleAdvertising()
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End BLE advertise wrappers"""
    """Begin GATT client wrappers"""

    def complete_gattc_connect_by_id(self, text, line, begidx, endidx):
        if not text:
            completions = list(self.le_ids)[:]
        else:
            completions = [s for s in self.le_ids if s.startswith(text)]
        return completions

    def do_gattc_connect_by_id(self, line):
        """
        Description: Connect to a LE peripheral.
        Input(s):
            device_id: Required. The unique device ID from Fuchsia
                discovered devices.
        Usage:
          Examples:
            gattc_connect device_id
        """
        cmd = "Connect to a LE peripheral by input ID."
        try:

            connection_status = self.pri_dut.sl4f.gattc_lib.bleConnectToPeripheral(
                line)
            self.log.info("Connection status: {}".format(
                pprint.pformat(connection_status)))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_connect(self, line):
        """
        Description: Connect to a LE peripheral.
        Optional input: device_name
        Input(s):
            device_name: Optional. The peripheral ID to connect to.
        Usage:
          Examples:
            gattc_connect
            gattc_connect eddystone_123
        """
        cmd = "Connect to a LE peripheral."
        try:
            if len(line) > 0:
                self.target_device_name = line
                self.unique_mac_addr_id = None
            if not self.unique_mac_addr_id:
                try:
                    self._find_unique_id()
                except Exception as err:
                    self.log.info("Failed to scan or find device.")
                    return
            connection_status = self.pri_dut.sl4f.gattc_lib.bleConnectToPeripheral(
                self.unique_mac_addr_id)
            self.log.info("Connection status: {}".format(
                pprint.pformat(connection_status)))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_connect_disconnect_iterations(self, line):
        """
        Description: Connect then disconnect to a LE peripheral multiple times.
        Input(s):
            iterations: Required. The number of iterations to run.
        Usage:
          Examples:
            gattc_connect_disconnect_iterations 10
        """
        cmd = "Connect to a LE peripheral."
        try:
            if not self.unique_mac_addr_id:
                try:
                    self._find_unique_id()
                except Exception as err:
                    self.log.info("Failed to scan or find device.")
                    return
            for i in range(int(line)):
                self.log.info("Running iteration {}".format(i + 1))
                connection_status = self.pri_dut.sl4f.gattc_lib.bleConnectToPeripheral(
                    self.unique_mac_addr_id)
                self.log.info("Connection status: {}".format(
                    pprint.pformat(connection_status)))
                time.sleep(4)
                disc_status = self.pri_dut.sl4f.gattc_lib.bleDisconnectPeripheral(
                    self.unique_mac_addr_id)
                self.log.info("Disconnect status: {}".format(disc_status))
                time.sleep(3)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_disconnect(self, line):
        """
        Description: Disconnect from LE peripheral.
        Assumptions: Already connected to a peripheral.
        Usage:
          Examples:
            gattc_disconnect
        """
        cmd = "Disconenct from LE peripheral."
        try:
            disconnect_status = self.pri_dut.sl4f.gattc_lib.bleDisconnectPeripheral(
                self.unique_mac_addr_id)
            self.log.info("Disconnect status: {}".format(disconnect_status))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_list_services(self, discover_chars):
        """
        Description: List services from LE peripheral.
        Assumptions: Already connected to a peripheral.
        Input(s):
            discover_chars: Optional. An optional input to discover all
                characteristics on the service.
        Usage:
          Examples:
            gattc_list_services
            gattc_list_services true
        """
        cmd = "List services from LE peripheral."
        try:

            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            self.log.info("Discovered Services: \n{}".format(
                pprint.pformat(services)))
            discover_characteristics = self.str_to_bool(discover_chars)
            if discover_chars:
                for service in services.get('result'):
                    self.pri_dut.sl4f.gattc_lib.connectToService(
                        self.unique_mac_addr_id, service.get('id'))
                    chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics(
                    )
                    self.log.info("Discovered chars:\n{}".format(
                        pprint.pformat(chars)))

        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_connect_to_service(self, line):
        """
        Description: Connect to Peripheral GATT server service.
        Assumptions: Already connected to peripheral.
        Input(s):
            service_id: Required. The service id reference on the GATT server.
        Usage:
          Examples:
            gattc_connect_to_service service_id
        """
        cmd = "GATT client connect to GATT server service."
        try:
            self.pri_dut.sl4f.gattc_lib.connectToService(
                self.unique_mac_addr_id, int(line))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_discover_characteristics(self, line):
        """
        Description: Discover characteristics from a connected service.
        Assumptions: Already connected to a GATT server service.
        Usage:
          Examples:
            gattc_discover_characteristics
        """
        cmd = "Discover and list characteristics from a GATT server."
        try:
            chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
            self.log.info("Discovered chars:\n{}".format(
                pprint.pformat(chars)))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_notify_all_chars(self, line):
        """
        Description: Enable all notifications on all Characteristics on
            a GATT server.
        Assumptions: Basic GATT connection made.
        Usage:
          Examples:
            gattc_notify_all_chars
        """
        cmd = "Read all characteristics from the GATT service."
        try:
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading chars in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    # quick char filter for apple-4 test... remove later
                    print("found uuid {}".format(char_uuid))
                    try:
                        self.pri_dut.sl4f.gattc_lib.enableNotifyCharacteristic(
                            char_id)
                    except Exception as err:
                        print("error enabling notification")
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_all_chars(self, line):
        """
        Description: Read all Characteristic values from a GATT server across
            all services.
        Assumptions: Basic GATT connection made.
        Usage:
          Examples:
            gattc_read_all_chars
        """
        cmd = "Read all characteristics from the GATT service."
        try:
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading chars in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    try:
                        read_val =  \
                            self.pri_dut.sl4f.gattc_lib.readCharacteristicById(
                                char_id)
                        print("  Characteristic uuid / Value: {} / {}".format(
                            char_uuid, read_val['result']))
                        str_value = ""
                        for val in read_val['result']:
                            str_value += chr(val)
                        print("    str val: {}".format(str_value))
                    except Exception as err:
                        print(err)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_all_desc(self, line):
        """
        Description: Read all Descriptors values from a GATT server across
            all services.
        Assumptions: Basic GATT connection made.
        Usage:
          Examples:
            gattc_read_all_chars
        """
        cmd = "Read all descriptors from the GATT service."
        try:
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading descs in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    descriptors = char['descriptors']
                    print("  Reading descs in char uuid: {}".format(char_uuid))
                    for desc in descriptors:
                        desc_id = desc["id"]
                        desc_uuid = desc["uuid_type"]
                    try:
                        read_val = self.pri_dut.sl4f.gattc_lib.readDescriptorById(
                            desc_id)
                        print("    Descriptor uuid / Value: {} / {}".format(
                            desc_uuid, read_val['result']))
                    except Exception as err:
                        pass
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_all_desc(self, line):
        """
        Description: Write a value to all Descriptors on the GATT server.
        Assumptions: Basic GATT connection made.
        Input(s):
            offset: Required. The offset to start writing to.
            size: Required. The size of bytes to write (value will be generated).
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_all_desc 0 100
            gattc_write_all_desc 10 2
        """
        cmd = "Read all descriptors from the GATT service."
        try:
            args = line.split()
            if len(args) != 2:
                self.log.info("2 Arguments required: [Offset] [Size]")
                return
            offset = int(args[0])
            size = args[1]
            write_value = []
            for i in range(int(size)):
                write_value.append(i % 256)
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Writing descs in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    descriptors = char['descriptors']
                    print("  Reading descs in char uuid: {}".format(char_uuid))
                    for desc in descriptors:
                        desc_id = desc["id"]
                        desc_uuid = desc["uuid_type"]
                    try:
                        write_val = self.pri_dut.sl4f.gattc_lib.writeDescriptorById(
                            desc_id, offset, write_value)
                        print("    Descriptor uuid / Result: {} / {}".format(
                            desc_uuid, write_val['result']))
                    except Exception as err:
                        pass
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_all_long_desc(self, line):
        """
        Description: Read all long Characteristic Descriptors
        Assumptions: Basic GATT connection made.
        Input(s):
            offset: Required. The offset to start reading from.
            max_bytes: Required. The max size of bytes to return.
        Usage:
          Examples:
            gattc_read_all_long_desc 0 100
            gattc_read_all_long_desc 10 20
        """
        cmd = "Read all long descriptors from the GATT service."
        try:
            args = line.split()
            if len(args) != 2:
                self.log.info("2 Arguments required: [Offset] [Size]")
                return
            offset = int(args[0])
            max_bytes = int(args[1])
            services = self.pri_dut.sl4f.ble_lib.bleListServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading descs in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    descriptors = char['descriptors']
                    print("  Reading descs in char uuid: {}".format(char_uuid))
                    for desc in descriptors:
                        desc_id = desc["id"]
                        desc_uuid = desc["uuid_type"]
                    try:
                        read_val = self.pri_dut.sl4f.gattc_lib.readLongDescriptorById(
                            desc_id, offset, max_bytes)
                        print("    Descriptor uuid / Result: {} / {}".format(
                            desc_uuid, read_val['result']))
                    except Exception as err:
                        pass
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_all_long_char(self, line):
        """
        Description: Read all long Characteristic
        Assumptions: Basic GATT connection made.
        Input(s):
            offset: Required. The offset to start reading from.
            max_bytes: Required. The max size of bytes to return.
        Usage:
          Examples:
            gattc_read_all_long_char 0 100
            gattc_read_all_long_char 10 20
        """
        cmd = "Read all long Characteristics from the GATT service."
        try:
            args = line.split()
            if len(args) != 2:
                self.log.info("2 Arguments required: [Offset] [Size]")
                return
            offset = int(args[0])
            max_bytes = int(args[1])
            services = self.pri_dut.sl4f.ble_lib.bleListServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading chars in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    try:
                        read_val = self.pri_dut.sl4f.gattc_lib.readLongCharacteristicById(
                            char_id, offset, max_bytes)
                        print("    Char uuid / Result: {} / {}".format(
                            char_uuid, read_val['result']))
                    except Exception as err:
                        pass
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_all_chars(self, line):
        """
        Description: Write all characteristic values from a GATT server across
            all services.
        Assumptions: Basic GATT connection made.
        Input(s):
            offset: Required. The offset to start writing on.
            size: The write value size (value will be generated)
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_all_chars 0 10
            gattc_write_all_chars 10 1
        """
        cmd = "Read all characteristics from the GATT service."
        try:
            args = line.split()
            if len(args) != 2:
                self.log.info("2 Arguments required: [Offset] [Size]")
                return
            offset = int(args[0])
            size = int(args[1])
            write_value = []
            for i in range(size):
                write_value.append(i % 256)
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Writing chars in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    try:
                        write_result = self.pri_dut.sl4f.gattc_lib.writeCharById(
                            char_id, offset, write_value)
                        print("  Characteristic uuid write result: {} / {}".
                              format(char_uuid, write_result['result']))
                    except Exception as err:
                        print("error writing char {}".format(err))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_all_chars_without_response(self, line):
        """
        Description: Write all characteristic values from a GATT server across
            all services.
        Assumptions: Basic GATT connection made.
        Input(s):
            size: The write value size (value will be generated).
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_all_chars_without_response 100
        """
        cmd = "Read all characteristics from the GATT service."
        try:
            args = line.split()
            if len(args) != 1:
                self.log.info("1 Arguments required: [Size]")
                return
            size = int(args[0])
            write_value = []
            for i in range(size):
                write_value.append(i % 256)
            services = self.pri_dut.sl4f.gattc_lib.listServices(
                self.unique_mac_addr_id)
            for service in services['result']:
                service_id = service['id']
                service_uuid = service['uuid_type']
                self.pri_dut.sl4f.gattc_lib.connectToService(
                    self.unique_mac_addr_id, service_id)
                chars = self.pri_dut.sl4f.gattc_lib.discoverCharacteristics()
                print("Reading chars in service uuid: {}".format(service_uuid))

                for char in chars['result']:
                    char_id = char['id']
                    char_uuid = char['uuid_type']
                    try:
                        write_result = \
                            self.pri_dut.sl4f.gattc_lib.writeCharByIdWithoutResponse(
                                char_id, write_value)
                        print("  Characteristic uuid write result: {} / {}".
                              format(char_uuid, write_result['result']))
                    except Exception as err:
                        pass
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_char_by_id(self, line):
        """
        Description: Write char by characteristic id reference.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
            offset: The offset value to use
            size: Function will generate random bytes by input size.
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_char_by_id char_id 0 5
            gattc_write_char_by_id char_id 20 1
        """
        cmd = "Write to GATT server characteristic ."
        try:
            args = line.split()
            if len(args) != 3:
                self.log.info("3 Arguments required: [Id] [Offset] [Size]")
                return
            id = int(args[0], 16)
            offset = int(args[1])
            size = int(args[2])
            write_value = []
            for i in range(size):
                write_value.append(i % 256)
            self.test_dut.gatt_client_write_characteristic_by_handle(
                self.unique_mac_addr_id, id, offset, write_value)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_long_char_by_id(self, line):
        """
        Description: Write long char by characteristic id reference.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
            offset: The offset value to use
            size: Function will generate random bytes by input size.
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
            reliable_mode: Optional: Reliable writes represented as bool
        Usage:
          Examples:
            gattc_write_long_char_by_id char_id 0 5
            gattc_write_long_char_by_id char_id 20 1
            gattc_write_long_char_by_id char_id 20 1 true
            gattc_write_long_char_by_id char_id 20 1 false
        """
        cmd = "Long Write to GATT server characteristic ."
        try:
            args = line.split()
            if len(args) < 3:
                self.log.info("3 Arguments required: [Id] [Offset] [Size]")
                return
            id = int(args[0], 16)
            offset = int(args[1])
            size = int(args[2])
            reliable_mode = False
            if len(args) > 3:
                reliable_mode = self.str_to_bool(args[3])
            write_value = []
            for i in range(size):
                write_value.append(i % 256)
            self.test_dut.gatt_client_write_long_characteristic_by_handle(
                self.unique_mac_addr_id, id, offset, write_value,
                reliable_mode)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_long_desc_by_id(self, line):
        """
        Description: Write long char by descrioptor id reference.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
            offset: The offset value to use
            size: Function will generate random bytes by input size.
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_long_desc_by_id char_id 0 5
            gattc_write_long_desc_by_id char_id 20 1
        """
        cmd = "Long Write to GATT server descriptor ."
        try:
            args = line.split()
            if len(args) != 3:
                self.log.info("3 Arguments required: [Id] [Offset] [Size]")
                return
            id = int(args[0], 16)
            offset = int(args[1])
            size = int(args[2])
            write_value = []
            for i in range(size):
                write_value.append(i % 256)
            self.test_dut.gatt_client_write_long_descriptor_by_handle(
                self.unique_mac_addr_id, id, offset, write_value)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_char_by_id_without_response(self, line):
        """
        Description: Write char by characteristic id reference without response.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
            size: Function will generate random bytes by input size.
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_char_by_id_without_response char_id 5
        """
        cmd = "Write characteristic by id without response."
        try:
            args = line.split()
            if len(args) != 2:
                self.log.info("2 Arguments required: [Id] [Size]")
                return
            id = int(args[0], 16)
            size = args[1]
            write_value = []
            for i in range(int(size)):
                write_value.append(i % 256)
            self.test_dut.gatt_client_write_characteristic_without_response_by_handle(
                self.unique_mac_addr_id, id, write_value)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_enable_notify_char_by_id(self, line):
        """
        Description: Enable Characteristic notification on Characteristic ID.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
        Usage:
          Examples:
            gattc_enable_notify_char_by_id char_id
        """
        cmd = "Enable notifications by Characteristic id."
        try:
            id = int(line, 16)
            self.test_dut.gatt_client_enable_notifiy_characteristic_by_handle(
                self.unique_mac_addr_id, id)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_disable_notify_char_by_id(self, line):
        """
        Description: Disable Characteristic notification on Characteristic ID.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
        Usage:
          Examples:
            gattc_disable_notify_char_by_id char_id
        """
        cmd = "Disable notify Characteristic by id."
        try:
            id = int(line, 16)
            self.test_dut.gatt_client_disable_notifiy_characteristic_by_handle(
                self.unique_mac_addr_id, id)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_char_by_id(self, line):
        """
        Description: Read Characteristic by ID.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
        Usage:
          Examples:
            gattc_read_char_by_id char_id
        """
        cmd = "Read Characteristic value by ID."
        try:
            id = int(line, 16)
            read_val = self.test_dut.gatt_client_read_characteristic_by_handle(
                self.unique_mac_addr_id, id)
            self.log.info("Characteristic Value with id {}: {}".format(
                id, read_val))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_char_by_uuid(self, characteristic_uuid):
        """
        Description: Read Characteristic by UUID (read by type).
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_uuid: The characteristic id reference on the GATT
                service
        Usage:
          Examples:
            gattc_read_char_by_id char_id
        """
        cmd = "Read Characteristic value by ID."
        try:
            short_uuid_len = 4
            if len(characteristic_uuid) == short_uuid_len:
                characteristic_uuid = BASE_UUID.format(characteristic_uuid)

            read_val = self.test_dut.gatt_client_read_characteristic_by_uuid(
                self.unique_mac_addr_id, characteristic_uuid)
            self.log.info("Characteristic Value with id {}: {}".format(
                id, read_val))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_write_desc_by_id(self, line):
        """
        Description: Write Descriptor by characteristic id reference.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            descriptor_id: The Descriptor id reference on the GATT service
            offset: The offset value to use
            size: Function will generate random bytes by input size.
                IE: Input of 5 will send a byte array of [00, 01, 02, 03, 04]
        Usage:
          Examples:
            gattc_write_desc_by_id desc_id 0 5
            gattc_write_desc_by_id desc_id 20 1
        """
        cmd = "Write Descriptor by id."
        try:
            args = line.split()
            id = int(args[0], 16)
            offset = int(args[1])
            size = args[2]
            write_value = []
            for i in range(int(size)):
                write_value.append(i % 256)
            write_result = self.test_dut.gatt_client_write_descriptor_by_handle(
                self.unique_mac_addr_id, id, offset, write_value)
            self.log.info("Descriptor Write result {}: {}".format(
                id, write_result))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_desc_by_id(self, line):
        """
        Description: Read Descriptor by ID.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            descriptor_id: The Descriptor id reference on the GATT service
        Usage:
          Examples:
            gattc_read_desc_by_id desc_id
        """
        cmd = "Read Descriptor by ID."
        try:
            id = int(line, 16)
            read_val = self.test_dut.gatt_client_read_descriptor_by_handle(
                self.unique_mac_addr_id, id)
            self.log.info("Descriptor Value with id {}: {}".format(
                id, read_val))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_gattc_read_long_char_by_id(self, line):
        """
        Description: Read long Characteristic value by id.
        Assumptions: Already connected to a GATT server service.
        Input(s):
            characteristic_id: The characteristic id reference on the GATT
                service
            offset: The offset value to use.
            max_bytes: The max bytes size to return.
        Usage:
          Examples:
            gattc_read_long_char_by_id char_id 0 10
            gattc_read_long_char_by_id char_id 20 1
        """
        cmd = "Read long Characteristic value by id."
        try:
            args = line.split()
            if len(args) != 3:
                self.log.info("3 Arguments required: [Id] [Offset] [Size]")
                return
            id = int(args[0], 16)
            offset = int(args[1])
            max_bytes = int(args[2])
            read_val = self.test_dut.gatt_client_read_long_characteristic_by_handle(
                self.unique_mac_addr_id, id, offset, max_bytes)
            self.log.info("Characteristic Value with id {}: {}".format(
                id, read_val['result']))

        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End GATT client wrappers"""
    """Begin LE scan wrappers"""

    def _update_scan_results(self, scan_results):
        self.le_ids = []
        for scan in scan_results['result']:
            self.le_ids.append(scan['id'])

    def do_ble_start_scan(self, line):
        """
        Description: Perform a BLE scan.
        Default filter name: ""
        Optional input: filter_device_name
        Usage:
          Examples:
            ble_start_scan
            ble_start_scan eddystone
        """
        cmd = "Perform a BLE scan and list discovered devices."
        try:
            scan_filter = {"name_substring": ""}
            if line:
                scan_filter = {"name_substring": line}
            self.pri_dut.sl4f.gattc_lib.bleStartBleScan(scan_filter)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_ble_stop_scan(self, line):
        """
        Description: Stops a BLE scan and returns discovered devices.
        Usage:
          Examples:
            ble_stop_scan
        """
        cmd = "Stops a BLE scan and returns discovered devices."
        try:
            scan_results = self.pri_dut.sl4f.gattc_lib.bleStopBleScan()
            self._update_scan_results(scan_results)
            self.log.info(pprint.pformat(scan_results))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_ble_get_discovered_devices(self, line):
        """
        Description: Get discovered LE devices of an active scan.
        Usage:
          Examples:
            ble_stop_scan
        """
        cmd = "Get discovered LE devices of an active scan."
        try:
            scan_results = self.pri_dut.sl4f.gattc_lib.bleGetDiscoveredDevices(
            )
            self._update_scan_results(scan_results)
            self.log.info(pprint.pformat(scan_results))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End LE scan wrappers"""
    """Begin GATT Server wrappers"""

    def do_gatts_close(self, line):
        """
        Description: Close active GATT server.

        Usage:
          Examples:
            gatts_close
        """
        cmd = "Close active GATT server."
        try:
            result = self.pri_dut.sl4f.gatts_lib.closeServer()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_gatts_setup_database(self, text, line, begidx, endidx):
        if not text:
            completions = list(
                gatt_test_database.GATT_SERVER_DB_MAPPING.keys())
        else:
            completions = [
                s for s in gatt_test_database.GATT_SERVER_DB_MAPPING.keys()
                if s.startswith(text)
            ]
        return completions

    def do_gatts_setup_database(self, line):
        """
        Description: Setup a Gatt server database based on pre-defined inputs.
            Supports Tab Autocomplete.
        Input(s):
            descriptor_db_name: The descriptor db name that matches one in
                acts_contrib.test_utils.bt.gatt_test_database
        Usage:
          Examples:
            gatts_setup_database LARGE_DB_1
        """
        cmd = "Setup GATT Server Database Based of pre-defined dictionaries"
        try:
            scan_results = self.pri_dut.sl4f.gatts_lib.publishServer(
                gatt_test_database.GATT_SERVER_DB_MAPPING.get(line))
            self.log.info(scan_results)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End GATT Server wrappers"""
    """Begin Bluetooth Controller wrappers"""

    def complete_btc_pair(self, text, line, begidx, endidx):
        """ Provides auto-complete for btc_pair cmd.

        See Cmd module for full description.
        """
        arg_completion = len(line.split(" ")) - 1
        pairing_security_level_options = ['ENCRYPTED', 'AUTHENTICATED', 'NONE']
        bondable_options = ['BONDABLE', 'NON_BONDABLE', 'NONE']
        transport_options = ['BREDR', 'LE']
        if arg_completion == 1:
            if not text:
                completions = pairing_security_level_options
            else:
                completions = [
                    s for s in pairing_security_level_options
                    if s.startswith(text)
                ]
            return completions
        if arg_completion == 2:
            if not text:
                completions = bondable_options
            else:
                completions = [
                    s for s in bondable_options if s.startswith(text)
                ]
            return completions
        if arg_completion == 3:
            if not text:
                completions = transport_options
            else:
                completions = [
                    s for s in transport_options if s.startswith(text)
                ]
            return completions

    def do_btc_pair(self, line):
        """
        Description: Sends an outgoing pairing request.

        Input(s):
            pairing security level: ENCRYPTED, AUTHENTICATED, or NONE
            bondable: BONDABLE, NON_BONDABLE, or NONE
            transport: BREDR or LE

        Usage:
          Examples:
            btc_pair NONE NONE BREDR
            btc_pair ENCRYPTED NONE LE
            btc_pair AUTHENTICATED NONE LE
            btc_pair NONE NON_BONDABLE BREDR
        """
        cmd = "Send an outgoing pairing request."
        pairing_security_level_mapping = {
            "ENCRYPTED": 1,
            "AUTHENTICATED": 2,
            "NONE": None,
        }

        bondable_mapping = {
            "BONDABLE": True,
            "NON_BONDABLE": False,
            "NONE": None,
        }

        transport_mapping = {
            "BREDR": 1,
            "LE": 2,
        }

        try:
            options = line.split(" ")
            result = self.test_dut.init_pair(
                self.unique_mac_addr_id,
                pairing_security_level_mapping.get(options[0]),
                bondable_mapping.get(options[1]),
                transport_mapping.get(options[2]),
            )
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_btc_accept_pairing(self, text, line, begidx, endidx):
        """ Provides auto-complete for btc_set_io_capabilities cmd.

        See Cmd module for full description.
        """
        arg_completion = len(line.split(" ")) - 1
        input_options = ['NONE', 'CONFIRMATION', 'KEYBOARD']
        output_options = ['NONE', 'DISPLAY']
        if arg_completion == 1:
            if not text:
                completions = input_options
            else:
                completions = [s for s in input_options if s.startswith(text)]
            return completions
        if arg_completion == 2:
            if not text:
                completions = output_options
            else:
                completions = [s for s in output_options if s.startswith(text)]
            return completions

    def do_btc_accept_pairing(self, line):
        """
        Description: Accept all incoming pairing requests.

        Input(s):
            input: String - The input I/O capabilities to use
                Available Values:
                NONE - Input capability type None
                CONFIRMATION - Input capability type confirmation
                KEYBOARD - Input capability type Keyboard
            output: String - The output I/O Capabilities to use
                Available Values:
                NONE - Output capability type None
                DISPLAY - output capability type Display

        Usage:
          Examples:
            btc_accept_pairing
            btc_accept_pairing NONE DISPLAY
            btc_accept_pairing NONE NONE
            btc_accept_pairing KEYBOARD DISPLAY
        """
        cmd = "Accept incoming pairing requests"
        try:
            input_capabilities = "NONE"
            output_capabilities = "NONE"
            options = line.split(" ")
            if len(options) > 1:
                input_capabilities = options[0]
                output_capabilities = options[1]
            result = self.pri_dut.sl4f.bts_lib.acceptPairing(
                input_capabilities, output_capabilities)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_forget_device(self, line):
        """
        Description: Forget pairing of the current device under test.
            Current device under test is the device found by
            tool_refresh_unique_id from custom user param. This function
            will also perform a clean disconnect if actively connected.

        Usage:
          Examples:
            btc_forget_device
        """
        cmd = "For pairing of the current device under test."
        try:
            self.log.info("Forgetting device id: {}".format(
                self.unique_mac_addr_id))
            result = self.pri_dut.sl4f.bts_lib.forgetDevice(
                self.unique_mac_addr_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_set_discoverable(self, discoverable):
        """
        Description: Change Bluetooth Controller discoverablility.
        Input(s):
            discoverable: true to set discoverable
                          false to set non-discoverable
        Usage:
          Examples:
            btc_set_discoverable true
            btc_set_discoverable false
        """
        cmd = "Change Bluetooth Controller discoverablility."
        try:
            result = self.test_dut.set_discoverable(
                self.str_to_bool(discoverable))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_set_name(self, name):
        """
        Description: Change Bluetooth Controller local name.
        Input(s):
            name: The name to set the Bluetooth Controller name to.

        Usage:
          Examples:
            btc_set_name fs_test
        """
        cmd = "Change Bluetooth Controller local name."
        try:
            result = self.test_dut.set_bluetooth_local_name(name)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_request_discovery(self, discover):
        """
        Description: Change whether the Bluetooth Controller is in active.
            discovery or not.
        Input(s):
            discover: true to start discovery
                      false to end discovery
        Usage:
          Examples:
            btc_request_discovery true
            btc_request_discovery false
        """
        cmd = "Change whether the Bluetooth Controller is in active."
        try:
            result = self.pri_dut.sl4f.bts_lib.requestDiscovery(
                self.str_to_bool(discover))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_get_known_remote_devices(self, line):
        """
        Description: Get a list of known devices.

        Usage:
          Examples:
            btc_get_known_remote_devices
        """
        cmd = "Get a list of known devices."
        self.bt_control_devices = []
        try:
            device_list = self.pri_dut.sl4f.bts_lib.getKnownRemoteDevices(
            )['result']
            for id_dict in device_list:
                device = device_list[id_dict]
                self.bt_control_devices.append(device)
                self.log.info("Device found {}".format(device))

        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_forget_all_known_devices(self, line):
        """
        Description: Forget all known devices.

        Usage:
          Examples:
            btc_forget_all_known_devices
        """
        cmd = "Forget all known devices."
        try:
            device_list = self.pri_dut.sl4f.bts_lib.getKnownRemoteDevices(
            )['result']
            for device in device_list:
                d = device_list[device]
                if d['bonded'] or d['connected']:
                    self.log.info("Unbonding deivce: {}".format(d))
                    self.log.info(
                        self.pri_dut.sl4f.bts_lib.forgetDevice(
                            d['id'])['result'])
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_connect_device(self, line):
        """
        Description: Connect to device under test.
            Device under test is specified by either user params
            or
                tool_set_target_device_name <name>
                do_tool_refresh_unique_id_using_bt_control

        Usage:
          Examples:
            btc_connect_device
        """
        cmd = "Connect to device under test."
        try:
            result = self.pri_dut.sl4f.bts_lib.connectDevice(
                self.unique_mac_addr_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_btc_connect_device_by_id(self, text, line, begidx, endidx):
        if not text:
            completions = list(self.bt_control_ids)[:]
        else:
            completions = [
                s for s in self.bt_control_ids if s.startswith(text)
            ]
        return completions

    def do_btc_connect_device_by_id(self, device_id):
        """
        Description: Connect to device id based on pre-defined inputs.
            Supports Tab Autocomplete.
        Input(s):
            device_id: The device id to connect to.

        Usage:
          Examples:
            btc_connect_device_by_id <device_id>
        """
        cmd = "Connect to device id based on pre-defined inputs."
        try:
            result = self.pri_dut.sl4f.bts_lib.connectDevice(device_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def complete_btc_connect_device_by_name(self, text, line, begidx, endidx):
        if not text:
            completions = list(self.bt_control_names)[:]
        else:
            completions = [
                s for s in self.bt_control_names if s.startswith(text)
            ]
        return completions

    def do_btc_connect_device_by_name(self, device_name):
        """
        Description: Connect to device id based on pre-defined inputs.
            Supports Tab Autocomplete.
        Input(s):
            device_id: The device id to connect to.

        Usage:
          Examples:
            btc_connect_device_by_name <device_id>
        """
        cmd = "Connect to device name based on pre-defined inputs."
        try:
            for device in self.bt_control_devices:
                if device_name is device['name']:

                    result = self.pri_dut.sl4f.bts_lib.connectDevice(
                        device['id'])
                    self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_disconnect_device(self, line):
        """
        Description: Disconnect to device under test.
            Device under test is specified by either user params
            or
                tool_set_target_device_name <name>
                do_tool_refresh_unique_id_using_bt_control

        Usage:
          Examples:
            btc_disconnect_device
        """
        cmd = "Disconnect to device under test."
        try:
            result = self.pri_dut.sl4f.bts_lib.disconnectDevice(
                self.unique_mac_addr_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_init_bluetooth_control(self, line):
        """
        Description: Initialize the Bluetooth Controller.

        Usage:
          Examples:
            btc_init_bluetooth_control
        """
        cmd = "Initialize the Bluetooth Controller."
        try:
            result = self.test_dut.initialize_bluetooth_controller()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_get_local_address(self, line):
        """
        Description: Get the local BR/EDR address of the Bluetooth Controller.

        Usage:
          Examples:
            btc_get_local_address
        """
        cmd = "Get the local BR/EDR address of the Bluetooth Controller."
        try:
            result = self.test_dut.get_local_bluetooth_address()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_input_pairing_pin(self, line):
        """
        Description: Sends a pairing pin to SL4F's Bluetooth Control's
        Pairing Delegate.

        Usage:
          Examples:
            btc_input_pairing_pin 123456
        """
        cmd = "Input pairing pin to the Fuchsia device."
        try:
            result = self.pri_dut.sl4f.bts_lib.inputPairingPin(line)['result']
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_btc_get_pairing_pin(self, line):
        """
        Description: Gets the pairing pin from SL4F's Bluetooth Control's
        Pairing Delegate.

        Usage:
          Examples:
            btc_get_pairing_pin
        """
        cmd = "Get the pairing pin from the Fuchsia device."
        try:
            result = self.pri_dut.sl4f.bts_lib.getPairingPin()['result']
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End Bluetooth Control wrappers"""
    """Begin Profile Server wrappers"""

    def do_sdp_pts_example(self, num_of_records):
        """
        Description: An example of how to setup a generic SDP record
            and SDP search capabilities. This example will pass a few
            SDP tests.

        Input(s):
            num_of_records: The number of records to add.

        Usage:
          Examples:
            sdp_pts_example 1
            sdp pts_example 10
        """
        cmd = "Setup SDP for PTS testing."

        attributes = [
            bt_attribute_values['ATTR_PROTOCOL_DESCRIPTOR_LIST'],
            bt_attribute_values['ATTR_SERVICE_CLASS_ID_LIST'],
            bt_attribute_values['ATTR_BLUETOOTH_PROFILE_DESCRIPTOR_LIST'],
            bt_attribute_values['ATTR_A2DP_SUPPORTED_FEATURES'],
        ]

        try:
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['AudioSource'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['A/V_RemoteControl'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['PANU'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['SerialPort'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['DialupNetworking'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['OBEXObjectPush'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['OBEXFileTransfer'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['Headset'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['HandsfreeAudioGateway'],
                                16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['Handsfree'], 16))
            self.pri_dut.sl4f.sdp_lib.addSearch(
                attributes, int(sig_uuid_constants['SIM_Access'], 16))
            for i in range(int(num_of_records)):
                result = self.pri_dut.sl4f.sdp_lib.addService(
                    sdp_pts_record_list[i])
                self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_sdp_cleanup(self, line):
        """
        Description: Cleanup any existing SDP records

        Usage:
          Examples:
            sdp_cleanup
        """
        cmd = "Cleanup SDP objects."
        try:
            result = self.pri_dut.sl4f.sdp_lib.cleanUp()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_sdp_init(self, line):
        """
        Description: Init the profile proxy for setting up SDP records

        Usage:
          Examples:
            sdp_init
        """
        cmd = "Initialize profile proxy objects for adding SDP records"
        try:
            result = self.pri_dut.sl4f.sdp_lib.init()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_sdp_connect_l2cap(self, line):
        """
        Description: Send an l2cap connection request over an input psm value.

        Note: Must be already connected to a peer.

        Input(s):
            psm: The int hex value to connect over. Available PSMs:
                SDP 0x0001  See Bluetooth Service Discovery Protocol (SDP)
                RFCOMM  0x0003  See RFCOMM with TS 07.10
                TCS-BIN 0x0005  See Bluetooth Telephony Control Specification /
                    TCS Binary
                TCS-BIN-CORDLESS    0x0007  See Bluetooth Telephony Control
                    Specification / TCS Binary
                BNEP    0x000F  See Bluetooth Network Encapsulation Protocol
                HID_Control 0x0011  See Human Interface Device
                HID_Interrupt   0x0013  See Human Interface Device
                UPnP    0x0015  See [ESDP]
                AVCTP   0x0017  See Audio/Video Control Transport Protocol
                AVDTP   0x0019  See Audio/Video Distribution Transport Protocol
                AVCTP_Browsing  0x001B  See Audio/Video Remote Control Profile
                UDI_C-Plane 0x001D  See the Unrestricted Digital Information
                    Profile [UDI]
                ATT 0x001F  See Bluetooth Core Specification​
                ​3DSP   0x0021​ ​​See 3D Synchronization Profile.
                ​LE_PSM_IPSP    ​0x0023 ​See Internet Protocol Support Profile
                    (IPSP)
                OTS 0x0025  See Object Transfer Service (OTS)
                EATT    0x0027  See Bluetooth Core Specification
            mode: String - The channel mode to connect to. Available values:
                Basic mode: BASIC
                Enhanced Retransmission mode: ERTM

        Usage:
          Examples:
            sdp_connect_l2cap 0001 BASIC
            sdp_connect_l2cap 0019 ERTM
        """
        cmd = "Connect l2cap"
        try:
            info = line.split()
            result = self.pri_dut.sl4f.sdp_lib.connectL2cap(
                self.unique_mac_addr_id, int(info[0], 16), info[1])
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End Profile Server wrappers"""
    """Begin AVDTP wrappers"""

    def do_avdtp_init(self, initiator_delay):
        """
        Description: Init the A2DP component start and AVDTP service to
            initiate.

        Input(s):
            initiator_delay: [Optional] The stream initiator delay to set in
            milliseconds.

        Usage:
          Examples:
            avdtp_init 0
            avdtp_init 2000
            avdtp_init
        """
        cmd = "Initialize AVDTP proxy"
        try:
            if not initiator_delay:
                initiator_delay = None
            result = self.pri_dut.sl4f.avdtp_lib.init(initiator_delay)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_kill_a2dp(self, line):
        """
        Description: Quickly kill any A2DP components.

        Usage:
          Examples:
            avdtp_kill_a2dp
        """
        cmd = "Kill A2DP service"
        try:
            self.pri_dut.start_v1_component("bt-a2dp")
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_get_connected_peers(self, line):
        """
        Description: Get the connected peers for the AVDTP service

        Usage:
          Examples:
            avdtp_get_connected_peers
        """
        cmd = "AVDTP get connected peers"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.getConnectedPeers()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_set_configuration(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: set configuration

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_set_configuration <peer_id>
        """
        cmd = "Send AVDTP set configuration to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.setConfiguration(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_get_configuration(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: get configuration

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_get_configuration <peer_id>
        """
        cmd = "Send AVDTP get configuration to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.getConfiguration(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_get_capabilities(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: get capabilities

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_get_capabilities <peer_id>
        """
        cmd = "Send AVDTP get capabilities to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.getCapabilities(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_get_all_capabilities(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: get all capabilities

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_get_all_capabilities <peer_id>
        """
        cmd = "Send AVDTP get all capabilities to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.getAllCapabilities(
                int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_reconfigure_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: reconfigure stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_reconfigure_stream <peer_id>
        """
        cmd = "Send AVDTP reconfigure stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.reconfigureStream(
                int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_suspend_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: suspend stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_suspend_stream <peer_id>
        """
        cmd = "Send AVDTP suspend stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.suspendStream(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_suspend_reconfigure(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: suspend reconfigure

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_suspend_reconfigure <peer_id>
        """
        cmd = "Send AVDTP suspend reconfigure to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.suspendAndReconfigure(
                int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_release_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: release stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_release_stream <peer_id>
        """
        cmd = "Send AVDTP release stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.releaseStream(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_establish_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: establish stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_establish_stream <peer_id>
        """
        cmd = "Send AVDTP establish stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.establishStream(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_start_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: start stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_start_stream <peer_id>
        """
        cmd = "Send AVDTP start stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.startStream(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_abort_stream(self, peer_id):
        """
        Description: Send AVDTP command to connected peer: abort stream

        Input(s):
            peer_id: The specified peer_id.

        Usage:
          Examples:
            avdtp_abort_stream <peer_id>
        """
        cmd = "Send AVDTP abort stream to connected peer"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.abortStream(int(peer_id))
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_avdtp_remove_service(self, line):
        """
        Description: Removes the AVDTP service in use.

        Usage:
          Examples:
            avdtp_establish_stream <peer_id>
        """
        cmd = "Remove AVDTP service"
        try:
            result = self.pri_dut.sl4f.avdtp_lib.removeService()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End AVDTP wrappers"""
    """Begin Audio wrappers"""

    def do_audio_start_output_save(self, line):
        """
        Description: Start audio output save

        Usage:
          Examples:
            audio_start_output_save
        """
        cmd = "Start audio capture"
        try:
            result = self.pri_dut.sl4f.audio_lib.startOutputSave()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_audio_stop_output_save(self, line):
        """
        Description: Stop audio output save

        Usage:
          Examples:
            audio_stop_output_save
        """
        cmd = "Stop audio capture"
        try:
            result = self.pri_dut.sl4f.audio_lib.stopOutputSave()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_audio_get_output_audio(self, line):
        """
        Description: Get the audio output saved to a local file

        Usage:
          Examples:
            audio_get_output_audio
        """
        cmd = "Get audio capture"
        try:
            save_path = "{}/{}".format(self.pri_dut.log_path, "audio.raw")
            result = self.pri_dut.sl4f.audio_lib.getOutputAudio(save_path)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_audio_5_min_test(self, line):
        """
        Description: Capture and anlyize sine audio waves played from a Bluetooth A2DP
        Source device.

        Pre steps:
        1. Pair A2DP source device
        2. Prepare generated SOX file over preferred codec on source device.
            Quick way to generate necessary audio files:
            sudo apt-get install sox
            sox -b 16 -r 48000 -c 2 -n audio_file_2k1k_5_min.wav synth 300 sine 2000 sine 3000

        Usage:
          Examples:
            audio_5_min_test
        """
        cmd = "5 min audio capture test"
        input("Press Enter once Source device is streaming audio file")
        try:
            result = self.pri_dut.sl4f.audio_lib.startOutputSave()
            self.log.info(result)
            for i in range(5):
                print("Minutes left: {}".format(10 - i))
                time.sleep(60)
            result = self.pri_dut.sl4f.audio_lib.stopOutputSave()
            log_time = int(time.time())
            save_path = "{}/{}".format(self.pri_dut.log_path,
                                       "{}_audio.raw".format(log_time))
            analysis_path = "{}/{}".format(
                self.pri_dut.log_path,
                "{}_audio_analysis.txt".format(log_time))
            result = self.pri_dut.sl4f.audio_lib.getOutputAudio(save_path)

            channels = 1
            try:
                quality_analysis(filename=save_path,
                                 output_file=analysis_path,
                                 bit_width=audio_bits_per_sample_32,
                                 rate=audio_sample_rate_48000,
                                 channel=channels,
                                 spectral_only=False)

            except Exception as err:
                self.log.error("Failed to analyze raw audio: {}".format(err))
                return False

            self.log.info("Analysis output here: {}".format(analysis_path))
            self.log.info("Analysis Results: {}".format(
                open(analysis_path).readlines()))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End Audio wrappers"""
    """Begin HFP wrappers"""

    def do_hfp_init(self, line):
        """
        Description: Init the HFP component initiate.

        Usage:
          Examples:
            hfp_init
        """
        cmd = "Initialize HFP proxy"
        try:
            result = self.pri_dut.sl4f.hfp_lib.init()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_remove_service(self, line):
        """
        Description: Removes the HFP service in use.

        Usage:
          Examples:
            hfp_remove_service
        """
        cmd = "Remove HFP service"
        try:
            result = self.pri_dut.sl4f.hfp_lib.removeService()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_list_peers(self, line):
        """
        Description: List all HFP Hands-Free peers connected to the DUT.

        Input(s):

        Usage:
          Examples:
            hfp_list_peers
        """
        cmd = "Lists connected peers"
        try:
            result = self.pri_dut.sl4f.hfp_lib.listPeers()
            self.log.info(pprint.pformat(result))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_active_peer(self, line):
        """
        Description: Set the active HFP Hands-Free peer for the DUT.

        Input(s):
            peer_id: The id of the peer to be set active.

        Usage:
          Examples:
            hfp_set_active_peer <peer_id>
        """
        cmd = "Set the active peer"
        try:
            peer_id = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setActivePeer(peer_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_list_calls(self, line):
        """
        Description: List all calls known to the sl4f component on the DUT.

        Input(s):

        Usage:
          Examples:
            hfp_list_calls
        """
        cmd = "Lists all calls"
        try:
            result = self.pri_dut.sl4f.hfp_lib.listCalls()
            self.log.info(pprint.pformat(result))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_new_call(self, line):
        """
        Description: Simulate a call on the call manager

        Input(s):
            remote: The number of the remote party on the simulated call
            state: The state of the call. Must be one of "ringing", "waiting",
                   "dialing", "alerting", "active", "held".
            direction: The direction of the call. Must be one of "incoming", "outgoing".

        Usage:
          Examples:
            hfp_new_call <remote> <state> <direction>
            hfp_new_call 14085555555 active incoming
            hfp_new_call 14085555555 held outgoing
            hfp_new_call 14085555555 ringing incoming
            hfp_new_call 14085555555 waiting incoming
            hfp_new_call 14085555555 alerting outgoing
            hfp_new_call 14085555555 dialing outgoing
        """
        cmd = "Simulates a call"
        try:
            info = line.strip().split()
            if len(info) != 3:
                raise ValueError(
                    "Exactly three command line arguments required: <remote> <state> <direction>"
                )
            remote, state, direction = info[0], info[1], info[2]
            result = self.pri_dut.sl4f.hfp_lib.newCall(remote, state,
                                                       direction)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_incoming_call(self, line):
        """
        Description: Simulate an incoming call on the call manager

        Input(s):
            remote: The number of the remote party on the incoming call

        Usage:
          Examples:
            hfp_incoming_call <remote>
            hfp_incoming_call 14085555555
        """
        cmd = "Simulates an incoming call"
        try:
            remote = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.initiateIncomingCall(remote)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_waiting_call(self, line):
        """
        Description: Simulate an incoming call on the call manager when there is
        an onging active call already.

        Input(s):
            remote: The number of the remote party on the incoming call

        Usage:
          Examples:
            hfp_waiting_call <remote>
            hfp_waiting_call 14085555555
        """
        cmd = "Simulates an incoming call"
        try:
            remote = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.initiateIncomingWaitingCall(
                remote)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_outgoing_call(self, line):
        """
        Description: Simulate an outgoing call on the call manager

        Input(s):
            remote: The number of the remote party on the outgoing call

        Usage:
          Examples:
            hfp_outgoing_call <remote>
        """
        cmd = "Simulates an outgoing call"
        try:
            remote = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.initiateOutgoingCall(remote)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_call_active(self, line):
        """
        Description: Set the specified call to the "OngoingActive" state.

        Input(s):
            call_id: The unique id of the call.

        Usage:
          Examples:
            hfp_outgoing_call <call_id>
        """
        cmd = "Set the specified call to active"
        try:
            call_id = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setCallActive(call_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_call_held(self, line):
        """
        Description: Set the specified call to the "OngoingHeld" state.

        Input(s):
            call_id: The unique id of the call.

        Usage:
          Examples:
            hfp_outgoing_call <call_id>
        """
        cmd = "Set the specified call to held"
        try:
            call_id = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setCallHeld(call_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_call_terminated(self, line):
        """
        Description: Set the specified call to the "Terminated" state.

        Input(s):
            call_id: The unique id of the call.

        Usage:
          Examples:
            hfp_outgoing_call <call_id>
        """
        cmd = "Set the specified call to terminated"
        try:
            call_id = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setCallTerminated(call_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_call_transferred_to_ag(self, line):
        """
        Description: Set the specified call to the "TransferredToAg" state.

        Input(s):
            call_id: The unique id of the call.

        Usage:
          Examples:
            hfp_outgoing_call <call_id>
        """
        cmd = "Set the specified call to TransferredToAg"
        try:
            call_id = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setCallTransferredToAg(call_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_speaker_gain(self, line):
        """
        Description: Set the active peer's speaker gain.

        Input(s):
            value: The gain value to set. Must be between 0-15 inclusive.

        Usage:
          Examples:
            hfp_set_speaker_gain <value>
        """
        cmd = "Set the active peer's speaker gain"
        try:
            value = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setSpeakerGain(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_microphone_gain(self, line):
        """
        Description: Set the active peer's microphone gain.

        Input(s):
            value: The gain value to set. Must be between 0-15 inclusive.

        Usage:
          Examples:
            hfp_set_microphone_gain <value>
        """
        cmd = "Set the active peer's microphone gain"
        try:
            value = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setMicrophoneGain(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_service_available(self, line):
        """
        Description: Sets the simulated network service status reported by the call manager.

        Input(s):
            value: "true" to set the network connection to available.

        Usage:
          Examples:
            hfp_set_service_available <value>
            hfp_set_service_available true
            hfp_set_service_available false
        """
        cmd = "Sets the simulated network service status reported by the call manager"
        try:
            value = line.strip() == "true"
            result = self.pri_dut.sl4f.hfp_lib.setServiceAvailable(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_roaming(self, line):
        """
        Description: Sets the simulated roaming status reported by the call manager.

        Input(s):
            value: "true" to set the network connection to roaming.

        Usage:
          Examples:
            hfp_set_roaming <value>
            hfp_set_roaming true
            hfp_set_roaming false
        """
        cmd = "Sets the simulated roaming status reported by the call manager"
        try:
            value = line.strip() == "true"
            result = self.pri_dut.sl4f.hfp_lib.setRoaming(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_signal_strength(self, line):
        """
        Description: Sets the simulated signal strength reported by the call manager.

        Input(s):
            value: The signal strength value to set. Must be between 0-5 inclusive.

        Usage:
          Examples:
            hfp_set_signal_strength <value>
            hfp_set_signal_strength 0
            hfp_set_signal_strength 3
            hfp_set_signal_strength 5
        """
        cmd = "Sets the simulated signal strength reported by the call manager"
        try:
            value = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setSignalStrength(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_subscriber_number(self, line):
        """
        Description: Sets the subscriber number reported by the call manager.

        Input(s):
            value: The subscriber number to set. Maximum length 128 characters.

        Usage:
          Examples:
            hfp_set_subscriber_number <value>
            hfp_set_subscriber_number 14085555555
        """
        cmd = "Sets the subscriber number reported by the call manager"
        try:
            value = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.setSubscriberNumber(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_operator(self, line):
        """
        Description: Sets the operator value reported by the call manager.

        Input(s):
            value: The operator value to set. Maximum length 16 characters.

        Usage:
          Examples:
            hfp_set_operator <value>
            hfp_set_operator GoogleFi
        """
        cmd = "Sets the operator value reported by the call manager"
        try:
            value = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.setOperator(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_nrec_support(self, line):
        """
        Description: Sets the noise reduction/echo cancelation support reported by the call manager.

        Input(s):
            value: The nrec support bool.

        Usage:
          Examples:
            hfp_set_nrec_support <value>
            hfp_set_nrec_support true
            hfp_set_nrec_support false
        """
        cmd = "Sets the noise reduction/echo cancelation support reported by the call manager"
        try:
            value = line.strip() == "true"
            result = self.pri_dut.sl4f.hfp_lib.setNrecSupport(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_battery_level(self, line):
        """
        Description: Sets the battery level reported by the call manager.

        Input(s):
            value: The integer battery level value. Must be 0-5 inclusive.

        Usage:
          Examples:
            hfp_set_battery_level <value>
            hfp_set_battery_level 0
            hfp_set_battery_level 3
        """
        cmd = "Set the battery level reported by the call manager"
        try:
            value = int(line.strip())
            result = self.pri_dut.sl4f.hfp_lib.setBatteryLevel(value)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_last_dialed(self, line):
        """
        Description: Sets the last dialed number in the call manager.

        Input(s):
            number: The number of the remote party.

        Usage:
          Examples:
            hfp_set_last_dialed <number>
            hfp_set_last_dialed 14085555555
        """
        cmd = "Sets the last dialed number in the call manager."
        try:
            number = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.setLastDialed(number)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_clear_last_dialed(self, line):
        """
        Description: Clears the last dialed number in the call manager.

        Usage:
          Examples:
            hfp_clear_last_dialed
        """
        cmd = "Clears the last dialed number in the call manager."
        try:
            result = self.pri_dut.sl4f.hfp_lib.clearLastDialed()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_memory_location(self, line):
        """
        Description: Sets a memory location to point to a remote number.

        Input(s):
            location: The memory location at which to store the number.
            number: The number of the remote party to be stored.

        Usage:
          Examples:
            hfp_set_memory_location <location> <number>
            hfp_set_memory_location 0 14085555555
        """
        cmd = "Sets a memory location to point to a remote number."
        try:
            info = line.strip().split()
            if len(info) != 2:
                raise ValueError(
                    "Exactly two command line arguments required: <location> <number>"
                )
            location, number = info[0], info[1]
            result = self.pri_dut.sl4f.hfp_lib.setMemoryLocation(
                location, number)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_clear_memory_location(self, line):
        """
        Description: Sets a memory location to point to a remote number.

        Input(s):
            localtion: The memory location to clear.

        Usage:
          Examples:
            hfp_clear_memory_location <location>
            hfp_clear_memory_location 0
        """
        cmd = "Sets a memory location to point to a remote number."
        try:
            location = line.strip()
            result = self.pri_dut.sl4f.hfp_lib.clearMemoryLocation(location)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_dial_result(self, line):
        """
        Description: Sets the status result to be returned when the number is dialed.

        Input(s):
            number: The number of the remote party.
            status: The status to be returned when an outgoing call is initiated to the number.

        Usage:
          Examples:
            hfp_set_battery_level <value>
        """
        cmd = "Sets the status result to be returned when the number is dialed."
        try:
            info = line.strip().split()
            if len(info) != 2:
                raise ValueError(
                    "Exactly two command line arguments required: <number> <status>"
                )
            number, status = info[0], int(info[1])
            result = self.pri_dut.sl4f.hfp_lib.setDialResult(number, status)
            self.log.info(pprint.pformat(result))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_get_state(self, line):
        """
        Description: Get the call manager's complete state

        Usage:
          Examples:
            hfp_get_state
        """
        cmd = "Get the call manager's state"
        try:
            result = self.pri_dut.sl4f.hfp_lib.getState()
            self.log.info(pprint.pformat(result))
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_hfp_set_connection_behavior(self, line):
        """
        Description: Set the Service Level Connection (SLC) behavior when a new peer connects.

        Input(s):
            autoconnect: Enable/Disable autoconnection of SLC.

        Usage:
          Examples:
            hfp_set_connection_behavior <autoconnect>
            hfp_set_connection_behavior true
            hfp_set_connection_behavior false
        """
        cmd = "Set the Service Level Connection (SLC) behavior"
        try:
            autoconnect = line.strip().lower() == "true"
            result = self.pri_dut.sl4f.hfp_lib.setConnectionBehavior(
                autoconnect)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End HFP wrappers"""
    """Begin RFCOMM wrappers"""

    def do_rfcomm_init(self, line):
        """
        Description: Initialize the RFCOMM component services.

        Usage:
          Examples:
            rfcomm_init
        """
        cmd = "Initialize RFCOMM proxy"
        try:
            result = self.pri_dut.sl4f.rfcomm_lib.init()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_remove_service(self, line):
        """
        Description: Removes the RFCOMM service in use.

        Usage:
          Examples:
            rfcomm_remove_service
        """
        cmd = "Remove RFCOMM service"
        try:
            result = self.pri_dut.sl4f.rfcomm_lib.removeService()
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_disconnect_session(self, line):
        """
        Description: Closes the RFCOMM Session.

        Usage:
          Examples:
            rfcomm_disconnect_session
            rfcomm_disconnect_session
        """
        cmd = "Disconnect the RFCOMM Session"
        try:
            result = self.pri_dut.sl4f.rfcomm_lib.disconnectSession(
                self.unique_mac_addr_id)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_connect_rfcomm_channel(self, line):
        """
        Description: Make an outgoing RFCOMM connection.

        Usage:
          Examples:
            rfcomm_connect_rfcomm_channel <server_channel_number>
            rfcomm_connect_rfcomm_channel 2
        """
        cmd = "Make an outgoing RFCOMM connection"
        try:
            server_channel_number = int(line.strip())
            result = self.pri_dut.sl4f.rfcomm_lib.connectRfcommChannel(
                self.unique_mac_addr_id, server_channel_number)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_disconnect_rfcomm_channel(self, line):
        """
        Description: Close the RFCOMM connection with the peer

        Usage:
          Examples:
            rfcomm_disconnect_rfcomm_channel <server_channel_number>
            rfcomm_disconnect_rfcomm_channel 2
        """
        cmd = "Close the RFCOMM channel"
        try:
            server_channel_number = int(line.strip())
            result = self.pri_dut.sl4f.rfcomm_lib.disconnectRfcommChannel(
                self.unique_mac_addr_id, server_channel_number)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_send_remote_line_status(self, line):
        """
        Description: Send a remote line status for the RFCOMM channel.

        Usage:
          Examples:
            rfcomm_send_remote_line_status <server_channel_number>
            rfcomm_send_remote_line_status 2
        """
        cmd = "Send a remote line status update for the RFCOMM channel"
        try:
            server_channel_number = int(line.strip())
            result = self.pri_dut.sl4f.rfcomm_lib.sendRemoteLineStatus(
                self.unique_mac_addr_id, server_channel_number)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    def do_rfcomm_write_rfcomm(self, line):
        """
        Description: Send data over the RFCOMM channel.

        Usage:
          Examples:
            rfcomm_write_rfcomm <server_channel_number> <data>
            rfcomm_write_rfcomm 2 foobar
        """
        cmd = "Send data using the RFCOMM channel"
        try:
            info = line.strip().split()
            if len(info) != 2:
                raise ValueError(
                    "Exactly two command line arguments required: <server_channel_number> <data>"
                )
            server_channel_number = int(info[0])
            data = info[1]
            result = self.pri_dut.sl4f.rfcomm_lib.writeRfcomm(
                self.unique_mac_addr_id, server_channel_number, data)
            self.log.info(result)
        except Exception as err:
            self.log.error(FAILURE.format(cmd, err))

    """End RFCOMM wrappers"""
