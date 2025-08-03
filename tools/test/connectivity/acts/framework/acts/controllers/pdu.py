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

import importlib
import logging

from acts import tracelogger

MOBLY_CONTROLLER_CONFIG_NAME = 'PduDevice'
ACTS_CONTROLLER_REFERENCE_NAME = 'pdu_devices'


def create(configs):
    """Creates a PduDevice for each config in configs.

    Args:
        configs: List of configs from PduDevice field.
            Fields:
                device: a string "<brand>.<model>" that corresponds to module
                    in pdu_lib/
                host: a string of the device ip address
                username (optional): a string of the username for device sign-in
                password (optional): a string of the password for device sign-in
    Return:
        A list of PduDevice objects.
    """
    if configs:
        pdus = []
        for config in configs:
            device = config.get('device')
            if not device:
                raise PduError("Config must provide a device")

            host = config.get('host')
            if not device:
                raise PduError("Config must provide a host ip address")
            username = config.get('username')
            password = config.get('password')
            pdu = _create_device(device, host, username, password)
            pdus.append(pdu)
        return pdus


def destroy(pdu_list):
    """Ensure any connections to devices are closed.

    Args:
        pdu_list: A list of PduDevice objects.
    """
    for pdu in pdu_list:
        pdu.close()


def get_info(pdu_list):
    """Retrieves info from a list of PduDevice objects.

    Args:
        pdu_list: A list of PduDevice objects.
    Return:
        A list containing a dictionary for each PduDevice, with keys:
            'host': a string of the device ip address
            'username': a string of the username
            'password': a string of the password
    """
    info = []
    for pdu in pdu_list:
        info.append({
            'host': pdu.host,
            'username': pdu.username,
            'password': pdu.password
        })
    return info


def _create_device(device, host, username, password):
    """Factory method that returns an instance of PduDevice implementation
    based on the device string.
    """
    module_name = 'acts.controllers.pdu_lib.' + device
    module = importlib.import_module(module_name)
    return module.PduDevice(host, username, password)


def get_pdu_port_for_device(device_pdu_config, pdus):
    """Retrieves the pdu object and port of that PDU powering a given device.
    This is especially necessary when there are multilpe devices on a single PDU
    or multiple PDUs registered.

    Args:
        device_pdu_config: a dict, representing the config of the device.
        pdus: a list of registered PduDevice objects.

    Returns:
        A tuple: (PduObject for the device, string port number on that PDU).

    Raises:
        ValueError, if there is no PDU matching the given host in the config.

    Example ACTS config:
        ...
        "testbed": [
            ...
            "FuchsiaDevice": [
                {
                    "ip": "<device_ip>",
                    "ssh_config": "/path/to/sshconfig",
                    "PduDevice": {
                        "host": "192.168.42.185",
                        "port": 2
                    }
                }
            ],
            "AccessPoint": [
                {
                    "ssh_config": {
                        ...
                    },
                    "PduDevice": {
                        "host": "192.168.42.185",
                        "port" 1
                    }
                }
            ],
            "PduDevice": [
                {
                    "device": "synaccess.np02b",
                    "host": "192.168.42.185"
                }
            ]
        ],
        ...
    """
    pdu_ip = device_pdu_config['host']
    port = device_pdu_config['port']
    for pdu in pdus:
        if pdu.host == pdu_ip:
            return pdu, port
    raise ValueError('No PduDevice with host: %s' % pdu_ip)


class PduDevice(object):
    """An object that defines the basic Pdu functionality and abstracts
    the actual hardware.

    This is a pure abstract class. Implementations should be of the same
    class name (eg. class PduDevice(pdu.PduDevice)) and exist in
    pdu_lib/<brand>/<device_name>.py. PduDevice objects should not be
    instantiated by users directly.
    """

    def __init__(self, host, username, password):
        if type(self) is PduDevice:
            raise NotImplementedError(
                "Base class: cannot be instantiated directly")
        self.host = host
        self.username = username
        self.password = password
        self.log = tracelogger.TraceLogger(logging.getLogger())

    def on_all(self):
        """Turns on all outlets on the device."""
        raise NotImplementedError("Base class: cannot be called directly")

    def off_all(self):
        """Turns off all outlets on the device."""
        raise NotImplementedError("Base class: cannot be called directly")

    def on(self, outlet):
        """Turns on specific outlet on the device.
        Args:
            outlet: a string of the outlet to turn on.
        """
        raise NotImplementedError("Base class: cannot be called directly")

    def off(self, outlet):
        """Turns off specific outlet on the device.
        Args:
            outlet: a string of the outlet to turn off.
        """
        raise NotImplementedError("Base class: cannot be called directly")

    def reboot(self, outlet):
        """Toggles a specific outlet on the device to off, then to on.
        Args:
            outlet: a string of the outlet to reboot.
        """
        raise NotImplementedError("Base class: cannot be called directly")

    def status(self):
        """Retrieves the status of the outlets on the device.

        Return:
            A dictionary matching outlet string to:
                True: if outlet is On
                False: if outlet is Off
        """
        raise NotImplementedError("Base class: cannot be called directly")

    def close(self):
        """Closes connection to the device."""
        raise NotImplementedError("Base class: cannot be called directly")


class PduError(Exception):
    """An exception for use within PduDevice implementations"""
