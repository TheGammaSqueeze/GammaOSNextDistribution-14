#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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
import queue

from blueberry.tests.gd.cert.truth import assertThat


class L2cap:

    __l2cap_connection_timeout = 10  #seconds
    __device = None
    __active_client_coc = False
    __active_server_coc = False

    def __init__(self, device):
        self.__device = device

    def __wait_for_event(self, expected_event_name):
        try:
            event_info = self.__device.ed.pop_event(expected_event_name, self.__l2cap_connection_timeout)
            logging.info(event_info)
        except queue.Empty as error:
            logging.error("Failed to find event: %s", expected_event_name)
            return False
        return True

    def create_l2cap_le_coc(self, address, psm, secure):
        logging.info("creating l2cap channel with secure=%r and psm %s", secure, psm)
        self.__device.sl4a.bluetoothSocketConnBeginConnectThreadPsm(address, True, psm, secure)
        assertThat(self.__wait_for_event("BluetoothSocketConnectSuccess")).isTrue()
        self.__active_client_coc = True

    # Starts listening on the l2cap server socket, returns the psm
    def listen_using_l2cap_le_coc(self, secure):
        logging.info("Listening for l2cap channel with secure=%r", secure)
        self.__device.sl4a.bluetoothSocketConnBeginAcceptThreadPsm(self.__l2cap_connection_timeout, True, secure)
        self.__active_server_coc = True
        return self.__device.sl4a.bluetoothSocketConnGetPsm()

    def close_l2cap_le_coc_client(self):
        if self.__active_client_coc:
            logging.info("Closing LE L2CAP CoC Client")
            self.__device.sl4a.bluetoothSocketConnKillConnThread()
            self.__active_client_coc = False

    def close_l2cap_le_coc_server(self):
        if self.__active_server_coc:
            logging.info("Closing LE L2CAP CoC Server")
            self.__device.sl4a.bluetoothSocketConnEndAcceptThread()
            self.__active_server_coc = False

    def close(self):
        self.close_l2cap_le_coc_client()
        self.close_l2cap_le_coc_server()
        self.__device == None
