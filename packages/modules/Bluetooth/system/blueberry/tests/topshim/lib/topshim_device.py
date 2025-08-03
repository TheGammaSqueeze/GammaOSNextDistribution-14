#!/usr/bin/env python3
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

import asyncio
import logging

from blueberry.tests.gd.cert.gd_device import GdHostOnlyDevice
from blueberry.tests.gd.cert.gd_device import MOBLY_CONTROLLER_CONFIG_NAME
from blueberry.tests.gd.cert.os_utils import get_gd_root
from blueberry.tests.topshim.lib.async_closable import AsyncClosable
from blueberry.tests.topshim.lib.async_closable import asyncSafeClose
from blueberry.tests.topshim.lib.oob_data import OobData
from blueberry.tests.topshim.lib.gatt_client import GattClient


def create(configs):
    return get_instances_with_configs(configs)


def destroy(devices):
    pass


def replace_vars_for_topshim(string, config):
    serial_number = config.get("serial_number")
    if serial_number is None:
        serial_number = ""
    rootcanal_port = config.get("rootcanal_port")
    if rootcanal_port is None:
        rootcanal_port = ""
    if serial_number == "DUT" or serial_number == "CERT":
        raise Exception("Did you forget to configure the serial number?")
    # We run bt_topshim_facade instead of bluetooth_stack_with_facade
    return string.replace("$GD_ROOT", get_gd_root()) \
                 .replace("bluetooth_stack_with_facade", "bt_topshim_facade") \
                 .replace("$(grpc_port)", config.get("grpc_port")) \
                 .replace("$(grpc_root_server_port)", config.get("grpc_root_server_port")) \
                 .replace("$(rootcanal_port)", rootcanal_port) \
                 .replace("$(signal_port)", config.get("signal_port")) \
                 .replace("$(serial_number)", serial_number)


def get_instances_with_configs(configs):
    logging.info(configs)
    devices = []
    for config in configs:
        resolved_cmd = []
        for arg in config["cmd"]:
            logging.debug(arg)
            resolved_cmd.append(replace_vars_for_topshim(arg, config))
        verbose_mode = bool(config.get('verbose_mode', False))
        device = GdHostOnlyDevice(config["grpc_port"], "-1", config["signal_port"], resolved_cmd, config["label"],
                                  MOBLY_CONTROLLER_CONFIG_NAME, config["name"], verbose_mode)
        device.setup()
        devices.append(device)
    return devices


TRANSPORT_CLASSIC = 1
TRANSPORT_LE = 2


class TopshimDevice(AsyncClosable):
    __adapter = None
    __gatt = None
    __security = None
    __hfp = None
    __hf_client = None

    async def __le_rand_wrapper(self, async_fn):
        result = await async_fn
        await self.__adapter.le_rand()
        le_rand_future = await self.__adapter._listen_for_event(facade_pb2.EventType.LE_RAND)
        return result

    def __post(self, async_fn):
        return asyncio.get_event_loop().run_until_complete(async_fn)

    def __init__(self, adapter, gatt, security, hfp, hf_client):
        self.__adapter = adapter
        self.__gatt = gatt
        self.__security = security
        self.__hfp = hfp
        self.__hf_client = hf_client

    async def close(self):
        """
        Implement abstract method to close out any streams or jobs.
        """
        await asyncSafeClose(self.__adapter)
        await asyncSafeClose(self.__gatt)
        await asyncSafeClose(self.__security)
        await asyncSafeClose(self.__hfp)
        await asyncSafeClose(self.__hf_client)

    def enable_inquiry_scan(self):
        f = self.__post(self.__adapter.enable_inquiry_scan())
        return self.__post(self.__discovery_mode_waiter(f))

    def enable_page_scan(self):
        f = self.__post(self.__adapter.enable_page_scan())
        return self.__post(self.__discovery_mode_waiter(f))

    def disable_page_scan(self):
        f = self.__post(self.__adapter.disable_page_scan())
        return self.__post(self.__discovery_mode_waiter(f))

    async def __discovery_mode_waiter(self, f):
        params = await f
        status, discovery_mode = params["status"].data[0], params["AdapterScanMode"].data[0]
        return (status, discovery_mode)

    def start_advertising(self):
        """
        Starts BLE Advertiser for the stack.
        Assumes stack defaults.  Which in our case would be RRPA
        """
        self.__post(self.__gatt.advertising_enable())

    def stop_advertising(self):
        """
        Stop BLE Advertiser.
        """
        self.__post(self.__gatt.advertising_disable())

    def start_scanning(self):
        pass

    def stop_scanning(self):
        pass

    def clear_event_mask(self):
        self.__post(self.__adapter.clear_event_mask())

    def clear_event_filter(self):
        self.__post(self.__adapter.clear_event_filter())

    def clear_filter_accept_list(self):
        self.__post(self.__adapter.clear_filter_accept_list())

    def disconnect_all_acls(self):
        self.__post(self.__adapter.disconnect_all_acls())

    def allow_wake_by_hid(self):
        self.__post(self.__adapter.allow_wake_by_hid())

    def set_default_event_mask_except(self, mask, le_mask):
        self.__post(self.__adapter.set_default_event_mask_except(mask, le_mask))

    def set_event_filter_inquiry_result_all_devices(self):
        self.__post(self.__adapter.set_event_filter_inquiry_result_all_devices())

    def set_event_filter_connection_setup_all_devices(self):
        self.__post(self.__adapter.set_event_filter_connection_setup_all_devices())

    def le_rand(self):
        self.__post(self.__adapter.le_rand())

    def create_bond(self, address, transport=1):
        """
        Create a bonding entry for a given address with a particular transport type.
        """
        f = self.__post(self.__security.create_bond(address, transport))
        return self.__post(self.__bond_change_waiter(f))

    def remove_bonded_device(self, address):
        """
        Removes a bonding entry for a given address.
        """
        self.__post(self.__security.remove_bond(address))

    async def __bond_change_waiter(self, f):
        params = await f
        state, address = params["bond_state"].data[0], params["address"].data[0]
        return (state, address)

    def generate_local_oob_data(self, transport=TRANSPORT_LE):
        """
        Generate local Out of Band data

        @param transport TRANSPORT_CLASSIC or TRANSPORT_LE

        @return a future to await on fo the data
        """
        f = self.__post(self.__security.generate_local_oob_data(transport))

        async def waiter(f):
            params = await f
            return OobData(params["is_valid"].data[0], params["transport"].data[0], params["address"].data[0],
                           params["confirmation"].data[0], params["randomizer"].data[0])

        return self.__post(waiter(f))

    def set_local_io_caps(self, io_capability=0):
        f = self.__post(self.__adapter.set_local_io_caps(io_capability))

        async def waiter(f):
            params = await f
            status, io_caps = params["status"].data[0], params["LocalIoCaps"].data[0]
            return (status, io_caps)

        return self.__post(waiter(f))

    def toggle_discovery(self, is_start):
        f = self.__post(self.__adapter.toggle_discovery(is_start))

        async def waiter(f):
            params = await f
            return params["discovery_state"].data[0]

        return self.__post(waiter(f))

    def find_device(self):
        """
        Attempts to find discoverable devices when discovery is toggled on.

        @return a list of properties of found device.
        """
        f = self.__post(self.__adapter.find_device())

        async def waiter(f):
            try:
                params = await f
                return params["BdAddr"].data[0]
            except:
                # The future `f` has a timeout after 2s post which it is cancelled.
                print("No device was found. Timed out.")
            return None

        return self.__post(waiter(f))

    def start_slc(self, address):
        f = self.__post(self.__hfp.start_slc(address))
        return self.__post(self.__hfp_connection_state_waiter(f))

    def stop_slc(self, address):
        f = self.__post(self.__hfp.stop_slc(address))
        return self.__post(self.__hfp_connection_state_waiter(f))

    def wait_for_hfp_connection_state_change(self):
        f = self.__post(self.__hfp.wait_for_hfp_connection_state_change())
        return self.__post(self.__hfp_connection_state_waiter(f))

    async def __hfp_connection_state_waiter(self, f):
        data = await f
        data_list = data.split(", ")
        state, address = data_list[0].strip(), data_list[1].strip()
        return (state, address)
