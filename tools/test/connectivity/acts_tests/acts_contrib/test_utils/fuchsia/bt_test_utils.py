#!/usr/bin/env python3
#
# Copyright (C) 2018 The Android Open Source Project
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

import json
import re
import subprocess
import time

PERSISTENT_BLUETOOTH_STORAGE_LOCATION = "/data/persistent/c1a6d0aebbf7c092c53e8e696636af8ec0629ff39b7f2e548430b0034d809da4/stash_secure.store"


def le_scan_for_device_by_name(fd,
                               log,
                               search_name,
                               timeout,
                               partial_match=False,
                               self_manage_scan=True):
    """Scan for and returns the first BLE advertisement with the device name.

    Args:
        fd: The Fuchsia device to start LE scanning on.
        log: The log var passed in from the test.
        search_name: The name to find.
        timeout: How long to scan for.
        partial_match: Only do a partial match for the LE advertising name.
          This will return the first result that had a partial match.
        self_manage_scan: Whther or not this function should start/stop (True)
          scans or if the caller should (False).

    Returns:
        The dictionary of device information.
    """
    if self_manage_scan:
        scan_filter = {"name_substring": search_name}
        fd.sl4f.gattc_lib.bleStartBleScan(scan_filter)
    end_time = time.time() + timeout
    found_device = None
    while time.time() < end_time and not found_device:
        time.sleep(1)
        scan_res = fd.sl4f.gattc_lib.bleGetDiscoveredDevices()['result']
        for device in scan_res:
            name, did, connectable = device["name"], device["id"], device[
                "connectable"]
            if name == search_name or (partial_match and search_name in name):
                log.info("Successfully found advertisement! name, id: {}, {}".
                         format(name, did))
                found_device = device
    if self_manage_scan:
        fd.sl4f.gattc_lib.bleStopBleScan()
    if not found_device:
        log.error("Failed to find device with name {}.".format(search_name))
    return found_device


def bredr_scan_for_device_by_name(fd,
                                  log,
                                  search_name,
                                  timeout,
                                  partial_match=False):
    """Discover for and returns the first Classic device that matches search_name.

    Args:
        fd: The Fuchsia device to start Classic discovery on.
        log: The log var passed in from the test.
        search_name: The name to find.
        timeout: How long to scan for.
        partial_match: Only do a partial match for the search_name.
          This will return the first result that had a partial match.

    Returns:
        The dictionary of device information.
    """
    fd.sl4f.bts_lib.requestDiscovery(True)

    end_time = time.time() + timeout
    found_device = None
    while time.time() < end_time and not found_device:
        scan_res = fd.sl4f.bts_lib.getKnownRemoteDevices()['result']
        for device in scan_res:
            name, did = scan_res[device]["name"], scan_res[device]["id"]
            if name == search_name or (partial_match and search_name in name):
                log.info("Successfully found peer! name, id: {}, {}".format(
                    name, did))
                found_device = did
        time.sleep(1)
    fd.sl4f.bts_lib.requestDiscovery(False)
    if not found_device:
        log.error("Failed to find device with name {}.".format(search_name))
        return found_device
    return found_device


def unbond_all_known_devices(fd, log):
    """Unbond all known devices from input Fuchsia Device.

    Args:
        fd: The Fuchsia device to unbond devices from.
        log: The log var passed in from the test.
    """
    fd.sl4f.bts_lib.requestDiscovery(True)
    device_list = fd.sl4f.bts_lib.getKnownRemoteDevices()['result']
    fd.sl4f.bts_lib.requestDiscovery(False)
    for device in device_list:
        d = device_list[device]
        if d['bonded'] or d['connected']:
            log.info("Unbonding device: {}".format(d))
            log.info(fd.sl4f.bts_lib.forgetDevice(d['id'])['result'])


def verify_device_state_by_name(fd, log, search_name, state, services=None):
    """Verify a connection state change happened an input device.

    Args:
        fd: The Fuchsia device to unbond devices from.
        log: The log var passed in from the test.
        search_name: The device name to find.
        state: The expected state.
        services: An optional list of services to expect based on the connected
            device.
    """
    fd.sl4f.bts_lib.requestDiscovery(True)

    seconds_allowed_for_state_change = 10
    end_time = time.time() + seconds_allowed_for_state_change
    found_state = None
    while time.time() < end_time and not found_state:
        device_list = fd.sl4f.bts_lib.getKnownRemoteDevices()['result']
        for device in device_list:
            d = device_list[device]
            name = d['name']
            if name == search_name:
                print(d)
                if state == "CONNECTED" and d['connected']:
                    log.info("Found connected device {}".format(d))
                    found_state = True
                    break
                if state == "BONDED" and d['bonded']:
                    log.info("Found bonded device {}".format(d))
                    found_state = True
                    break
        time.sleep(1)
    #TODO: Verify services.
    fd.sl4f.bts_lib.requestDiscovery(False)
    return found_state


def decode_list_to_link_key(raw_list):
    """ Decodes the input int list to a string link key
    Args:
        raw_list: The list of int values to convert
    Returns:
        A string represetnation of the link key
    """
    str_list = ""
    raw_list.reverse()
    for item in raw_list:
        check = str(hex(int(item)))[2:]
        if len(check) == 1:
            check = "0{}".format(check)
        str_list += check
    return str_list


def get_link_keys(fd, save_path):
    """Get Bluetooth link keys and LTKs for input Fuchsia device.

    Args:
        fd: The Fuchsia device object.
        save_path: The custom save path.
    Returns:
        Dictionary of known LTKs and link keys
    """
    subprocess.run([
        f"scp -F {fd.ssh_config} -6 [{fd.ip}]:{PERSISTENT_BLUETOOTH_STORAGE_LOCATION} {save_path}"
    ],
                   shell=True)
    stash_secure_output = ""
    with open(save_path, 'rb') as file:
        stash_secure_output = file.read()
    non_ascii_bytes_removed = re.sub(rb'[^\x00-\x7f]', rb'',
                                     stash_secure_output).decode('utf-8')

    bonding_data_split = non_ascii_bytes_removed.split("bonding-data:")
    bonding_data_split.pop(0)
    data_dict = {}
    for data in bonding_data_split:
        if "saved_networks" in data:
            data = data.split("saved_networks")[0]
        trailing_data_removed = re.sub(r'^.*?{', '{', data).strip()

        more_trailing_data = trailing_data_removed.rsplit('}', 1)[0] + "}"
        # Sometimes 'ost-data' will be apended at the end.
        even_more_trailing_info = more_trailing_data.split('ost-data')[0]

        # Remove the special chars at the end of the string that start with x1b
        clean_json = more_trailing_data.split('\x1b')[0]

        json_conversion = json.loads(clean_json)
        identifier = json_conversion.get("identifier")
        device_name = json_conversion.get("name")

        device_address = decode_list_to_link_key(
            json_conversion.get("address").get("value"))
        device_address = ':'.join([
            device_address[i:i + 2] for i in range(0, len(device_address), 2)
        ])

        data_dict[identifier] = {
            "device_name": device_name,
            "device_address": device_address
        }

        if json_conversion.get("bredr") is not None:
            link_key = decode_list_to_link_key(
                json_conversion.get("bredr").get("linkKey").get("value"))
            data_dict[identifier]["bredr_link_key"] = link_key

        if json_conversion.get("le") is not None:
            ltk_key = decode_list_to_link_key(
                json_conversion.get("le").get("localLtk").get("key").get(
                    "value"))
            data_dict[identifier]["le_ltk"] = ltk_key

    return data_dict
