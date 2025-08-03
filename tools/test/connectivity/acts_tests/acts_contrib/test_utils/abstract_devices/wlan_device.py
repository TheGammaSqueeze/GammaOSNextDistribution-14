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

import inspect
import logging
import time

import acts_contrib.test_utils.wifi.wifi_test_utils as awutils
from acts.utils import adb_shell_ping

from acts import asserts
from acts.controllers import iperf_client
from acts.controllers.fuchsia_device import FuchsiaDevice
from acts.controllers.android_device import AndroidDevice

FUCHSIA_VALID_SECURITY_TYPES = {"none", "wep", "wpa", "wpa2", "wpa3"}


def create_wlan_device(hardware_device):
    """Creates a generic WLAN device based on type of device that is sent to
    the functions.

    Args:
        hardware_device: A WLAN hardware device that is supported by ACTS.
    """
    if isinstance(hardware_device, FuchsiaDevice):
        return FuchsiaWlanDevice(hardware_device)
    elif isinstance(hardware_device, AndroidDevice):
        return AndroidWlanDevice(hardware_device)
    else:
        raise ValueError('Unable to create WlanDevice for type %s' %
                         type(hardware_device))


class WlanDevice(object):
    """Class representing a generic WLAN device.

    Each object of this class represents a generic WLAN device.
    Android device and Fuchsia devices are the currently supported devices/

    Attributes:
        device: A generic WLAN device.
    """

    def __init__(self, device):
        self.device = device
        self.log = logging
        self.identifier = None

    def wifi_toggle_state(self, state):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def reset_wifi(self):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def take_bug_report(self, test_name=None, begin_time=None):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def get_log(self, test_name, begin_time):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def turn_location_off_and_scan_toggle_off(self):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def associate(self,
                  target_ssid,
                  target_pwd=None,
                  check_connectivity=True,
                  hidden=False,
                  target_security=None):
        """Base generic WLAN interface.  Only called if not overriden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def disconnect(self):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def get_wlan_interface_id_list(self):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def get_default_wlan_test_interface(self):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def destroy_wlan_interface(self, iface_id):
        """Base generic WLAN interface.  Only called if not overridden by
        another supported device.
        """
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def send_command(self, command):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def is_connected(self, ssid=None):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def can_ping(self,
                 dest_ip,
                 count=3,
                 interval=1000,
                 timeout=1000,
                 size=25,
                 additional_ping_params=None):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def ping(self,
             dest_ip,
             count=3,
             interval=1000,
             timeout=1000,
             size=25,
             additional_ping_params=None):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def hard_power_cycle(self, pdus=None):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def save_network(self, ssid):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def clear_saved_networks(self):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))

    def create_iperf_client(self, test_interface=None):
        raise NotImplementedError("{} must be defined.".format(
            inspect.currentframe().f_code.co_name))


class AndroidWlanDevice(WlanDevice):
    """Class wrapper for an Android WLAN device.

    Each object of this class represents a generic WLAN device.
    Android device and Fuchsia devices are the currently supported devices/

    Attributes:
        android_device: An Android WLAN device.
    """

    def __init__(self, android_device):
        super().__init__(android_device)
        self.identifier = android_device.serial

    def wifi_toggle_state(self, state):
        awutils.wifi_toggle_state(self.device, state)

    def reset_wifi(self):
        awutils.reset_wifi(self.device)

    def take_bug_report(self, test_name=None, begin_time=None):
        self.device.take_bug_report(test_name, begin_time)

    def get_log(self, test_name, begin_time):
        self.device.cat_adb_log(test_name, begin_time)

    def turn_location_off_and_scan_toggle_off(self):
        awutils.turn_location_off_and_scan_toggle_off(self.device)

    def associate(self,
                  target_ssid,
                  target_pwd=None,
                  key_mgmt=None,
                  check_connectivity=True,
                  hidden=False,
                  target_security=None):
        """Function to associate an Android WLAN device.

        Args:
            target_ssid: SSID to associate to.
            target_pwd: Password for the SSID, if necessary.
            key_mgmt: The hostapd wpa_key_mgmt value, distinguishes wpa3 from
                wpa2 for android tests.
            check_connectivity: Whether to check for internet connectivity.
            hidden: Whether the network is hidden.
        Returns:
            True if successfully connected to WLAN, False if not.
        """
        network = {'SSID': target_ssid, 'hiddenSSID': hidden}
        if target_pwd:
            network['password'] = target_pwd
        if key_mgmt:
            network['security'] = key_mgmt
        try:
            awutils.connect_to_wifi_network(
                self.device,
                network,
                check_connectivity=check_connectivity,
                hidden=hidden)
            return True
        except Exception as e:
            self.device.log.info('Failed to associated (%s)' % e)
            return False

    def disconnect(self):
        awutils.turn_location_off_and_scan_toggle_off(self.device)

    def get_wlan_interface_id_list(self):
        pass

    def get_default_wlan_test_interface(self):
        return 'wlan0'

    def destroy_wlan_interface(self, iface_id):
        pass

    def send_command(self, command):
        return self.device.adb.shell(str(command))

    def is_connected(self, ssid=None):
        wifi_info = self.device.droid.wifiGetConnectionInfo()
        if ssid:
            return 'BSSID' in wifi_info and wifi_info['SSID'] == ssid
        return 'BSSID' in wifi_info

    def can_ping(self,
                 dest_ip,
                 count=3,
                 interval=1000,
                 timeout=1000,
                 size=25,
                 additional_ping_params=None):
        return adb_shell_ping(self.device,
                              dest_ip=dest_ip,
                              count=count,
                              timeout=timeout)

    def ping(self, dest_ip, count=3, interval=1000, timeout=1000, size=25):
        pass

    def hard_power_cycle(self, pdus):
        pass

    def save_network(self, ssid):
        pass

    def clear_saved_networks(self):
        pass

    def create_iperf_client(self, test_interface=None):
        """ Returns an iperf client on the Android, without requiring a
        specific config.

        Args:
            test_interface: optional, string, name of test interface.

        Returns:
            IPerfClient object
        """
        if not test_interface:
            test_interface = self.get_default_wlan_test_interface()

        return iperf_client.IPerfClientOverAdb(
            android_device_or_serial=self.device,
            test_interface=test_interface)


class FuchsiaWlanDevice(WlanDevice):
    """Class wrapper for an Fuchsia WLAN device.

    Each object of this class represents a generic WLAN device.
    Android device and Fuchsia devices are the currently supported devices/

    Attributes:
        fuchsia_device: A Fuchsia WLAN device.
    """

    def __init__(self, fuchsia_device):
        super().__init__(fuchsia_device)
        self.identifier = fuchsia_device.ip
        self.device.configure_wlan()

    def wifi_toggle_state(self, state):
        """Stub for Fuchsia implementation."""

    def reset_wifi(self):
        """Stub for Fuchsia implementation."""

    def take_bug_report(self, test_name=None, begin_time=None):
        """Stub for Fuchsia implementation."""
        self.device.take_bug_report(test_name, begin_time)

    def get_log(self, test_name, begin_time):
        """Stub for Fuchsia implementation."""

    def turn_location_off_and_scan_toggle_off(self):
        """Stub for Fuchsia implementation."""

    def associate(self,
                  target_ssid,
                  target_pwd=None,
                  key_mgmt=None,
                  check_connectivity=True,
                  hidden=False,
                  target_security=None):
        """Function to associate a Fuchsia WLAN device.

        Args:
            target_ssid: SSID to associate to.
            target_pwd: Password for the SSID, if necessary.
            key_mgmt: the hostapd wpa_key_mgmt, if specified.
            check_connectivity: Whether to check for internet connectivity.
            hidden: Whether the network is hidden.
            target_security: string, target security for network, used to
                save the network in policy connects (see wlan_policy_lib)
        Returns:
            True if successfully connected to WLAN, False if not.
        """
        if self.device.association_mechanism == 'drivers':
            bss_scan_response = self.device.sl4f.wlan_lib.wlanScanForBSSInfo()
            if bss_scan_response.get('error'):
                self.log.error('Scan for BSS info failed. Err: %s' %
                               bss_scan_response['error'])
                return False

            bss_descs_for_ssid = bss_scan_response['result'].get(
                target_ssid, None)
            if not bss_descs_for_ssid or len(bss_descs_for_ssid) < 1:
                self.log.error(
                    'Scan failed to find a BSS description for target_ssid %s'
                    % target_ssid)
                return False

            connection_response = self.device.sl4f.wlan_lib.wlanConnectToNetwork(
                target_ssid, bss_descs_for_ssid[0], target_pwd=target_pwd)
            return self.device.check_connect_response(connection_response)
        else:
            return self.device.wlan_policy_controller.save_and_connect(
                target_ssid, target_security, password=target_pwd)

    def disconnect(self):
        """Function to disconnect from a Fuchsia WLAN device.
           Asserts if disconnect was not successful.
        """
        if self.device.association_mechanism == 'drivers':
            disconnect_response = self.device.sl4f.wlan_lib.wlanDisconnect()
            return self.device.check_disconnect_response(disconnect_response)
        else:
            return self.device.wlan_policy_controller.remove_all_networks_and_wait_for_no_connections(
            )

    def status(self):
        return self.device.sl4f.wlan_lib.wlanStatus()

    def can_ping(self,
                 dest_ip,
                 count=3,
                 interval=1000,
                 timeout=1000,
                 size=25,
                 additional_ping_params=None):
        return self.device.can_ping(
            dest_ip,
            count=count,
            interval=interval,
            timeout=timeout,
            size=size,
            additional_ping_params=additional_ping_params)

    def ping(self,
             dest_ip,
             count=3,
             interval=1000,
             timeout=1000,
             size=25,
             additional_ping_params=None):
        return self.device.ping(dest_ip,
                                count=count,
                                interval=interval,
                                timeout=timeout,
                                size=size,
                                additional_ping_params=additional_ping_params)

    def get_wlan_interface_id_list(self):
        """Function to list available WLAN interfaces.

        Returns:
            A list of wlan interface IDs.
        """
        return self.device.sl4f.wlan_lib.wlanGetIfaceIdList().get('result')

    def get_default_wlan_test_interface(self):
        """Returns name of the WLAN client interface"""
        return self.device.wlan_client_test_interface_name

    def destroy_wlan_interface(self, iface_id):
        """Function to associate a Fuchsia WLAN device.

        Args:
            target_ssid: SSID to associate to.
            target_pwd: Password for the SSID, if necessary.
            check_connectivity: Whether to check for internet connectivity.
            hidden: Whether the network is hidden.
        Returns:
            True if successfully destroyed wlan interface, False if not.
        """
        result = self.device.sl4f.wlan_lib.wlanDestroyIface(iface_id)
        if result.get('error') is None:
            return True
        else:
            self.log.error("Failed to destroy interface with: {}".format(
                result.get('error')))
            return False

    def send_command(self, command):
        return self.device.ssh.run(str(command)).stdout

    def is_connected(self, ssid=None):
        """ Determines if wlan_device is connected to wlan network.

        Args:
            ssid (optional): string, to check if device is connect to a specific
                network.

        Returns:
            True, if connected to a network or to the correct network when SSID
                is provided.
            False, if not connected or connect to incorrect network when SSID is
                provided.
        """
        response = self.status()
        if response.get('error'):
            raise ConnectionError(
                'Failed to get client network connection status')
        result = response.get('result')
        if isinstance(result, dict):
            connected_to = result.get('Connected')
            # TODO(https://fxbug.dev/85938): Remove backwards compatibility once
            # ACTS is versioned with Fuchsia.
            if not connected_to:
                connected_to = result.get('connected_to')
            if not connected_to:
                return False

            if ssid:
                # Replace encoding errors instead of raising an exception.
                # Since `ssid` is a string, this will not affect the test
                # for equality.
                connected_ssid = bytearray(connected_to['ssid']).decode(
                    encoding='utf-8', errors='replace')
                return ssid == connected_ssid
            return True
        return False

    def hard_power_cycle(self, pdus):
        self.device.reboot(reboot_type='hard', testbed_pdus=pdus)

    def save_network(self, target_ssid, security_type=None, target_pwd=None):
        if self.device.association_mechanism == 'drivers':
            raise EnvironmentError(
                'Cannot save network using the drivers. Saved networks are a '
                'policy layer concept.')
        if security_type and security_type not in FUCHSIA_VALID_SECURITY_TYPES:
            raise TypeError('Invalid security type: %s' % security_type)
        if not self.device.wlan_policy_controller.save_network(
                target_ssid, security_type, password=target_pwd):
            raise EnvironmentError('Failed to save network: %s' % target_ssid)

    def clear_saved_networks(self):
        if self.device.association_mechanism == 'drivers':
            raise EnvironmentError(
                'Cannot clear saved network using the drivers. Saved networks '
                'are a policy layer concept.')
        if not self.device.wlan_policy_controller.remove_all_networks():
            raise EnvironmentError('Failed to clear saved networks')

    def create_iperf_client(self, test_interface=None):
        """ Returns an iperf client on the FuchsiaDevice, without requiring a
        specific config.

        Args:
            test_interface: optional, string, name of test interface. Defaults
                to first found wlan client interface.

        Returns:
            IPerfClient object
        """
        if not test_interface:
            test_interface = self.get_default_wlan_test_interface()

        # A package server is necessary to acquire the iperf3 client for
        # some builds.
        self.device.start_package_server()

        return iperf_client.IPerfClientOverSsh(
            {
                'user': 'fuchsia',
                'host': self.device.ip,
                'ssh_config': self.device.ssh_config
            },
            ssh_provider=self.device.ssh,
            test_interface=test_interface)
