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
#
import time
from acts import logger
from acts import utils


def create(configs):
    """Factory method for PDU.

    Args:
        configs: list of dicts with pdu settings. settings must contain the
        following type (string denoting type of pdu)
    """
    objs = []
    for config in configs:
        try:
            pdu_class = globals()[config['device']]
        except KeyError:
            raise KeyError('Invalid pdu configuration.')
        objs.append(pdu_class(config))
    return objs


def destroy(objs):
    return


class Pdu(object):
    """Base class implementation for PDU.

    Base class provides functions whose implementation is shared by all
    chambers.
    """

    def on_all(self):
        """Turn on all outlets."""
        raise NotImplementedError("Base class: cannot be called directly")

    def off_all(self):
        """Turn off all outlets."""
        raise NotImplementedError("Base class: cannot be called directly")

    def _set_status(self, action, status):
        """Set outlets to on or off."""
        raise NotImplementedError("Base class: cannot be called directly")

    def get_status(self):
        """Get outlets status."""
        raise NotImplementedError("Base class: cannot be called directly")

    def turn_on_outlets(self, outlets):
        """Turn on specific outlets."""
        raise NotImplementedError("Base class: cannot be called directly")

    def turn_off_outlets(self, outlets):
        """Turn off specific outlets."""
        raise NotImplementedError("Base class: cannot be called directly")


class PanioPs1158(Pdu):
    def __init__(self, config):
        self.config = config.copy()
        self.device_id = self.config['device_id']
        self.log = logger.create_tagged_trace_logger('pdu_ps1158[{}]'.format(
            self.device_id))

    def on_all(self):
        """Turn on all outlets"""
        self._set_status("on", '11111111')

    def off_all(self):
        """Turn off all outlets"""
        self._set_status("off", "11111111")

    def _set_status(self, action, status):
        """Set outlets to on or off.

        Args:
            action: "on" or "off"
            status: 8 bits of 0 or 1. e.g., "11111111"
        """
        cmd = "curl http://{}:{}@{}/{}s.cgi?led={}".format(self.config['username'],
                                                           self.config['password'],
                                                           self.config['host'],
                                                           action,
                                                           status)
        self.log.info("PDU cmd: {}".format(cmd))
        utils.start_standing_subprocess(cmd)
        time.sleep(10)

    def get_status(self):
        """Get outlets status

        Returns:
            A tuple of (outlets_list, outlets_str)
                outlets_list:
                    A List indicates the status of the outlets.
                    e.g., outlet 1 is ON, returns:
                        ['1', '0', '0', '0', '0', '0', '0', '0',]
                    e.g., outlets 1 & 8 are ON, returns:
                        ['1', '0', '0', '0', '0', '0', '0', '1']

                outlets_str:
                    A string indicates the status of the outlets.
                    e.g., outlet 1 is ON:
                        returns: '1'
                    e.g., outlet 1 & 3 $ 8 are ON:
                        returns: '138'
        """
        outlets_str = ""
        cmd = "curl http://{}:{}@{}/status.xml".format(self.config['username'],
                                                       self.config['password'],
                                                       self.config['host'])
        proc = utils.start_standing_subprocess(cmd)
        time.sleep(1)
        try:
            outlets_list = proc.communicate()[0].decode().split(",")[10:18]

            """Translate a list of strings to a sequence of strings.
            e.g.
                ['1', '0', '0', '0', '0', '0', '0', '0',] turns into '1'
                ['1', '1', '1', '1', '1', '1', '1', '1'] turns into '12345678'
            """
            for i in range(len(outlets_list)):
                if outlets_list[i] == '1':
                    outlets_str = outlets_str + str(i + 1)
        except:
            raise KeyError("Fail to get status from PDU.")

        return outlets_list, outlets_str

    def turn_on_outlets(self, outlets):
        """Turn specific outlets on

        Args:
            outlets: A string of outlet numbers.
            e.g., '1' means outlets status will be: '10000000'
            e.g., '378' means outlets status will be: '00100011'
        """
        self.off_all()
        expect_outlets = ["1" if str(i) in outlets else "0" for i in range(1, 9)]
        self._set_status("on", "".join(expect_outlets))

        # Check if outlets are on as expected.
        actual_outlets, _ = self.get_status()
        self.log.info("Expect outlets : {}".format(expect_outlets))
        self.log.info("Actual outlets : {}".format(actual_outlets))
        if expect_outlets == actual_outlets:
            self.log.info("Outlets are ON as expected")
        else:
            self.log.error("Outlets are not correctly turn on")

    def turn_off_outlets(self, outlets):
        """Turn specific outlets off

        Args:
            outlets: A string of outlet numbers.
            e.g., '1' means outlets status will be: '01111111'
            e.g., '378' means outlets status will be: '11011100'

        """
        self.on_all()
        expect_outlets = ["1" if str(i) in outlets else "0" for i in range(1, 9)]
        self._set_status("off", "".join(expect_outlets))

        # Check if outlets are on as expected.
        actual_outlets, _ = self.get_status()
        temp_list = []

        """When Turn off outlets, Panio ps1158 use "1" to turn off a outlet
        (e.g., curl http://{}@{}/offs.cgi?led=00000001 to turn off outlet 8,
        but actual outlets status will be '11111110', so need to
        Turn "1" into "0" and vice versa to match the actual outlets status.
        """
        for outlet in expect_outlets:
            if outlet == '1':
                outlet = '0'
                temp_list.append(outlet)
            elif outlet == '0':
                outlet = '1'
                temp_list.append(outlet)
        expect_outlets = temp_list
        self.log.info("Expect outlets : {}".format(expect_outlets))
        self.log.info("Actual outlets : {}".format(actual_outlets))
        if expect_outlets == actual_outlets:
            self.log.info("Outlets are OFF as expected")
        else:
            self.log.error("Outlets are not correctly turn off")
