#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import time

from acts import logger
from acts import signals
from acts import utils

TIME_TO_SLEEP_BETWEEN_RETRIES = 1
TIME_TO_WAIT_FOR_COUNTRY_CODE = 10


class WlanControllerError(signals.ControllerError):
    pass


class WlanController:
    """Contains methods related to wlan core, to be used in FuchsiaDevice object"""

    def __init__(self, fuchsia_device):
        self.device = fuchsia_device
        self.log = logger.create_tagged_trace_logger(
            'WlanController for FuchsiaDevice | %s' % self.device.ip)

    # TODO(70501): Wrap wlan_lib functions and setup from FuchsiaDevice here
    # (similar to how WlanPolicyController does it) to prevent FuchsiaDevice
    # from growing too large.
    def _configure_wlan(self):
        pass

    def _deconfigure_wlan(self):
        pass

    def update_wlan_interfaces(self):
        """ Retrieves WLAN interfaces from device and sets the FuchsiaDevice
        attributes.
        """
        wlan_interfaces = self.get_interfaces_by_role()
        self.device.wlan_client_interfaces = wlan_interfaces['client']
        self.device.wlan_ap_interfaces = wlan_interfaces['ap']

        # Set test interfaces to value from config, else the first found
        # interface, else None
        self.device.wlan_client_test_interface_name = self.device.conf_data.get(
            'wlan_client_test_interface',
            next(iter(self.device.wlan_client_interfaces), None))

        self.device.wlan_ap_test_interface_name = self.device.conf_data.get(
            'wlan_ap_test_interface',
            next(iter(self.device.wlan_ap_interfaces), None))

    def get_interfaces_by_role(self):
        """ Retrieves WLAN interface information, supplimented by netstack info.

        Returns:
            Dict with keys 'client' and 'ap', each of which contain WLAN
            interfaces.
        """

        # Retrieve WLAN interface IDs
        response = self.device.sl4f.wlan_lib.wlanGetIfaceIdList()
        if response.get('error'):
            raise WlanControllerError('Failed to get WLAN iface ids: %s' %
                                      response['error'])

        wlan_iface_ids = response.get('result', [])
        if len(wlan_iface_ids) < 1:
            return {'client': {}, 'ap': {}}

        # Use IDs to get WLAN interface info and mac addresses
        wlan_ifaces_by_mac = {}
        for id in wlan_iface_ids:
            response = self.device.sl4f.wlan_lib.wlanQueryInterface(id)
            if response.get('error'):
                raise WlanControllerError(
                    'Failed to query wlan iface id %s: %s' %
                    (id, response['error']))

            mac = response['result'].get('sta_addr', None)
            if mac is None:
                # Fallback to older field name to maintain backwards
                # compatibility with older versions of SL4F's
                # QueryIfaceResponse. See https://fxrev.dev/562146.
                mac = response['result'].get('mac_addr')

            wlan_ifaces_by_mac[utils.mac_address_list_to_str(
                mac)] = response['result']

        # Use mac addresses to query the interfaces from the netstack view,
        # which allows us to supplement the interface information with the name,
        # netstack_id, etc.

        # TODO(fxb/75909): This tedium is necessary to get the interface name
        # because only netstack has that information. The bug linked here is
        # to reconcile some of the information between the two perspectives, at
        # which point we can eliminate step.
        net_ifaces = self.device.netstack_controller.list_interfaces()
        wlan_ifaces_by_role = {'client': {}, 'ap': {}}
        for iface in net_ifaces:
            try:
                # Some interfaces might not have a MAC
                iface_mac = utils.mac_address_list_to_str(iface['mac'])
            except Exception as e:
                self.log.debug(f'Error {e} getting MAC for iface {iface}')
                continue
            if iface_mac in wlan_ifaces_by_mac:
                wlan_ifaces_by_mac[iface_mac]['netstack_id'] = iface['id']

                # Add to return dict, mapped by role then name.
                wlan_ifaces_by_role[
                    wlan_ifaces_by_mac[iface_mac]['role'].lower()][
                        iface['name']] = wlan_ifaces_by_mac[iface_mac]

        return wlan_ifaces_by_role

    def set_country_code(self, country_code):
        """Sets country code through the regulatory region service and waits
        for the code to be applied to WLAN PHY.

        Args:
            country_code: string, the 2 character country code to set

        Raises:
            EnvironmentError - failure to get/set regulatory region
            ConnectionError - failure to query PHYs
        """
        self.log.info('Setting DUT country code to %s' % country_code)
        country_code_response = self.device.sl4f.regulatory_region_lib.setRegion(
            country_code)
        if country_code_response.get('error'):
            raise EnvironmentError(
                'Failed to set country code (%s) on DUT. Error: %s' %
                (country_code, country_code_response['error']))

        self.log.info('Verifying DUT country code was correctly set to %s.' %
                      country_code)
        phy_ids_response = self.device.sl4f.wlan_lib.wlanPhyIdList()
        if phy_ids_response.get('error'):
            raise ConnectionError('Failed to get phy ids from DUT. Error: %s' %
                                  (country_code, phy_ids_response['error']))

        end_time = time.time() + TIME_TO_WAIT_FOR_COUNTRY_CODE
        while time.time() < end_time:
            for id in phy_ids_response['result']:
                get_country_response = self.device.sl4f.wlan_lib.wlanGetCountry(
                    id)
                if get_country_response.get('error'):
                    raise ConnectionError(
                        'Failed to query PHY ID (%s) for country. Error: %s' %
                        (id, get_country_response['error']))

                set_code = ''.join([
                    chr(ascii_char)
                    for ascii_char in get_country_response['result']
                ])
                if set_code != country_code:
                    self.log.debug(
                        'PHY (id: %s) has incorrect country code set. '
                        'Expected: %s, Got: %s' % (id, country_code, set_code))
                    break
            else:
                self.log.info('All PHYs have expected country code (%s)' %
                              country_code)
                break
            time.sleep(TIME_TO_SLEEP_BETWEEN_RETRIES)
        else:
            raise EnvironmentError('Failed to set DUT country code to %s.' %
                                   country_code)
