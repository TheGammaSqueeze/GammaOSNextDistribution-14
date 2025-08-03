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

from acts.controllers.fuchsia_lib.base_lib import BaseLib

import uuid


class FuchsiaBleLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "ble")

    def _convert_human_readable_uuid_to_byte_list(self, readable_uuid):
        """Converts a readable uuid to a byte list.

        Args:
            readable_uuid: string, A readable uuid in the format:
                Input: "00001101-0000-1000-8000-00805f9b34fb"
                Output: ['fb', '34', '9b', '5f', '80', '00', '00', '80', '00',
                         '10', '00', '00', '01', '11', '00', '00']

        Returns:
            A byte list representing the readable uuid.
        """
        hex_uuid_str = uuid.UUID(readable_uuid).hex
        break_n_bytes = 2
        byte_list = [
            hex_uuid_str[i:i + break_n_bytes]
            for i in range(0, len(hex_uuid_str), break_n_bytes)
        ]
        byte_list.reverse()
        return byte_list

    def bleStopBleAdvertising(self):
        """BleStopAdvertising command

        Returns:
            Dictionary, None if success, error string if error.
        """
        test_cmd = "ble_advertise_facade.BleStopAdvertise"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def bleStartBleAdvertising(self,
                               advertising_data,
                               scan_response,
                               interval,
                               connectable=True):
        """BleStartAdvertising command

        Args:
            advertising_data: dictionary, advertising data required for ble
                advertise.
            scan_response: dictionary, optional scan respones data to send.
            interval: int, Advertising interval (in ms).
            connectable: bool, whether the advertisement is connectable or not.

        Returns:
            Dictionary, None if success, error string if error.
        """
        test_cmd = "ble_advertise_facade.BleAdvertise"
        service_uuid_list = None
        if type(advertising_data['service_uuids']) == list:
            service_uuid_list = []
            for single_uuid in advertising_data['service_uuids']:
                service_uuid_list.append(
                    self._convert_human_readable_uuid_to_byte_list(
                        single_uuid))
            advertising_data['service_uuids'] = service_uuid_list

        service_uuid_list = None
        if scan_response and type(scan_response['service_uuids']) == list:
            service_uuid_list = []
            for single_uuid in scan_response['service_uuids']:
                service_uuid_list.append(
                    self._convert_human_readable_uuid_to_byte_list(
                        single_uuid))
            scan_response['service_uuids'] = service_uuid_list

        if scan_response and type(scan_response['service_data']) == list:
            for service_data in scan_response['service_data']:
                service_data[
                    "uuid"] = self._convert_human_readable_uuid_to_byte_list(
                        service_data["uuid"])

        if type(advertising_data['service_data']) == list:
            for service_data in advertising_data['service_data']:
                service_data[
                    "uuid"] = self._convert_human_readable_uuid_to_byte_list(
                        service_data["uuid"])

        test_args = {
            "advertising_data": advertising_data,
            "scan_response": scan_response,
            "interval_ms": interval,
            "connectable": connectable
        }
        return self.send_command(test_cmd, test_args)

    def blePublishService(self, primary, type_, service_id):
        """Publishes services specified by input args

        Args:
            primary: bool, Flag of service.
            type: string, Canonical 8-4-4-4-12 uuid of service.
            service_proxy_key: string, Unique identifier to specify where to publish service

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "bluetooth.BlePublishService"
        test_args = {
            "primary": primary,
            "type": type_,
            "local_service_id": service_id
        }

        return self.send_command(test_cmd, test_args)
