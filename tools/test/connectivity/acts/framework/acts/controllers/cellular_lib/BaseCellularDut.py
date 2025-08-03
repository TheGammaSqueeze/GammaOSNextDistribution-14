#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from enum import Enum


class PreferredNetworkType(Enum):
    """ Available preferred network types that can be passed to
  set_preferred_network_type"""
    LTE_ONLY = 'lte-only'
    GSM_ONLY = 'gsm-only'
    WCDMA_ONLY = 'wcdma-only'
    NR_LTE = 'nr-lte'


class BaseCellularDut():
    """ Base class for DUTs used with cellular simulators. """
    def toggle_airplane_mode(self, new_state=True):
        """ Turns airplane mode on / off.

        Args:
          new_state: True if airplane mode needs to be enabled.
        """
        raise NotImplementedError()

    def toggle_data_roaming(self, new_state=True):
        """ Enables or disables cellular data roaming.

        Args:
          new_state: True if data roaming needs to be enabled.
        """
        raise NotImplementedError()

    def get_rx_tx_power_levels(self):
        """ Obtains Rx and Tx power levels measured from the DUT.

        Returns:
          A tuple where the first element is an array with the RSRP value
          in each Rx chain, and the second element is the Tx power in dBm.
          Values for invalid or disabled Rx / Tx chains are set to None.
        """
        raise NotImplementedError()

    def set_apn(self, name, apn, type='default'):
        """ Sets the Access Point Name.

        Args:
          name: the APN name
          apn: the APN
          type: the APN type
        """
        raise NotImplementedError()

    def set_preferred_network_type(self, type):
        """ Sets the preferred RAT.

        Args:
          type: an instance of class PreferredNetworkType
        """
        raise NotImplementedError()

    def get_telephony_signal_strength(self):
        """ Wrapper for the method with the same name in tel_utils.

        Will be deprecated and replaced by get_rx_tx_power_levels. """
        raise NotImplementedError()

    def start_modem_logging(self):
        """ Starts on-device log collection. """
        raise NotImplementedError()

    def stop_modem_logging(self):
        """ Stops log collection and pulls logs. """
        raise NotImplementedError()