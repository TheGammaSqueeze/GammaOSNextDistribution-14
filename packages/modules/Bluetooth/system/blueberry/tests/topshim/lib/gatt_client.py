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

import asyncio
import grpc

from blueberry.facade.topshim import facade_pb2
from blueberry.facade.topshim import facade_pb2_grpc
from blueberry.tests.topshim.lib.async_closable import AsyncClosable
from blueberry.tests.topshim.lib.async_closable import asyncSafeClose

from google.protobuf import empty_pb2 as empty_proto


class GattClient(AsyncClosable):
    """
    Wrapper gRPC interface to the GATT Service
    """
    # Timeout for async wait
    DEFAULT_TIMEOUT = 2
    __task_list = []
    __channel = None
    __gatt_stub = None
    __adapter_event_stream = None

    def __init__(self, port=8999):
        self.__channel = grpc.aio.insecure_channel("localhost:%d" % port)
        self.__gatt_stub = facade_pb2_grpc.GattServiceStub(self.__channel)
        #self.__gatt_event_stream = self.__gatt_stub.FetchEvents(facade_pb2.FetchEventsRequest())

    async def close(self):
        """
        Terminate the current tasks
        """
        for task in self.__task_list:
            task.cancel()
            task = None
        self.__task_list.clear()
        await self.__channel.close()

    async def register_advertiser(self):
        """
        """
        await self.__gatt_stub.RegisterAdvertiser(empty_proto.Empty())

    async def unregister_advertiser(self, advertiser_id):
        """
        Stop advertising for advertiser id
        """
        # TODO(optedoblivion): make message to pass advertiser id
        await self.__gatt_stub.UnregisterAdvertiser(empty_proto.Empty())

    async def get_own_address(self):
        """
        """
        await self.__gatt_stub.GetOwnAddress(empty_proto.Empty())

    async def set_parameters(self):
        """
        """
        await self.__gatt_stub.SetParameters(empty_proto.Empty())

    async def set_data(self):
        """
        """
        await self.__gatt_stub.SetData(empty_proto.Empty())

    async def advertising_enable(self):
        """
        """
        await self.__gatt_stub.AdvertisingEnable(empty_proto.Empty())

    async def advertising_disable(self):
        """
        """
        await self.__gatt_stub.AdvertisingDisable(empty_proto.Empty())

    async def set_periodic_advertising_parameters(self):
        """
        """
        await self.__gatt_stub.SetPeriodicAdvertisingParameters(empty_proto.Empty())

    async def set_periodic_advertising_data(self):
        """
        """
        await self.__gatt_stub.SetPeriodicAdvertisingData(empty_proto.Empty())

    async def set_periodic_advertising_enable(self):
        """
        """
        await self.__gatt_stub.SetPeriodicAdvertisingEnable(empty_proto.Empty())

    async def start_advertising(self):
        """
        """
        await self.__gatt_stub.StartAdvertising(empty_proto.Empty())

    async def start_advertising_set(self):
        """
        Start advertising with the given parameters
        """
        await self.__gatt_stub.StartAdvertisingSet(empty_proto.Empty())

    async def register_scanner(self):
        """
        """
        await self.__gatt_stub.RegisterScanner(empty_proto.Empty())

    async def unregister_scanner(self):
        """
        """
        await self.__gatt_stub.UnregisterScanner(empty_proto.Empty())

    async def start_scan(self):
        """
        """
        await self.__gatt_stub.StartScan(empty_proto.Empty())

    async def stop_scan(self):
        """
        """
        await self.__gatt_stub.StopScan(empty_proto.Empty())

    async def scan_filter_setup(self):
        """
        """
        await self.__gatt_stub.ScanFilterSetup(empty_proto.Empty())

    async def scan_filter_add(self):
        """
        """
        await self.__gatt_stub.ScanFilterAdd(empty_proto.Empty())

    async def scan_filter_clear(self):
        """
        """
        await self.__gatt_stub.ScanFilterClear(empty_proto.Empty())

    async def scan_filter_enable(self):
        """
        """
        await self.__gatt_stub.ScanFilterEnable(empty_proto.Empty())

    async def scan_filter_disable(self):
        """
        """
        await self.__gatt_stub.ScanFilterDisable(empty_proto.Empty())

    async def set_scan_parameters(self):
        """
        """
        await self.__gatt_stub.SetScanParameters(empty_proto.Empty())

    async def batch_scan_config_storage(self):
        """
        """
        await self.__gatt_stub.BatchScanConfigStorage(empty_proto.Empty())

    async def batch_scan_enable(self):
        """
        """
        await self.__gatt_stub.BatchScanEnable(empty_proto.Empty())

    async def batch_scan_disable(self):
        """
        """
        await self.__gatt_stub.BatchScanDisable(empty_proto.Empty())

    async def batch_scan_read_reports(self):
        """
        """
        await self.__gatt_stub.BatchScanReadReports(empty_proto.Empty())

    async def start_sync(self):
        """
        """
        await self.__gatt_stub.StartSync(empty_proto.Empty())

    async def stop_sync(self):
        """
        """
        await self.__gatt_stub.StopSync(empty_proto.Empty())

    async def cancel_create_sync(self):
        """
        """
        await self.__gatt_stub.CancelCreateSync(empty_proto.Empty())

    async def transfer_sync(self):
        """
        """
        await self.__gatt_stub.TransferSync(empty_proto.Empty())

    async def transfer_set_info(self):
        """
        """
        await self.__gatt_stub.TransferSetInfo(empty_proto.Empty())

    async def sync_tx_parameters(self):
        """
        """
        await self.__gatt_stub.SyncTxParameters(empty_proto.Empty())

    async def register_client(self):
        """
        """
        await self.__gatt_stub.RegisterClient(empty_proto.Empty())

    async def unregister_client(self):
        """
        """
        await self.__gatt_stub.UnregisterClient(empty_proto.Empty())

    async def connect(self):
        """
        """
        await self.__gatt_stub.Connect(empty_proto.Empty())

    async def disconnect(self):
        """
        """
        await self.__gatt_stub.Disconnect(empty_proto.Empty())

    async def refresh(self):
        """
        """
        await self.__gatt_stub.Refresh(empty_proto.Empty())

    async def search_service(self):
        """
        """
        await self.__gatt_stub.SearchService(empty_proto.Empty())

    async def btif_gattc_discover_service_by_uuid(self):
        """
        """
        await self.__gatt_stub.BtifGattcDiscoverServiceByUuid(empty_proto.Empty())

    async def read_characteristic(self):
        """
        """
        await self.__gatt_stub.ReadCharacteristic(empty_proto.Empty())

    async def read_using_characteristic_uuid(self):
        """
        """
        await self.__gatt_stub.ReadUsingCharacteristicUuid(empty_proto.Empty())

    async def write_characteristic(self):
        """
        """
        await self.__gatt_stub.WriteCharacteristic(empty_proto.Empty())

    async def read_descriptor(self):
        """
        """
        await self.__gatt_stub.ReadDescriptor(empty_proto.Empty())

    async def write_descriptor(self):
        """
        """
        await self.__gatt_stub.WriteDescriptor(empty_proto.Empty())

    async def execute_write(self):
        """
        """
        await self.__gatt_stub.ExecuteWrite(empty_proto.Empty())

    async def register_for_notification(self):
        """
        """
        await self.__gatt_stub.RegisterForNotification(empty_proto.Empty())

    async def deregister_for_notification(self):
        """
        """
        await self.__gatt_stub.DeregisterForNotification(empty_proto.Empty())

    async def read_remote_rssi(self):
        """
        """
        await self.__gatt_stub.ReadRemoteRssi(empty_proto.Empty())

    async def get_device_type(self):
        """
        """
        await self.__gatt_stub.GetDeviceType(empty_proto.Empty())

    async def configure_mtu(self):
        """
        """
        await self.__gatt_stub.ConfigureMtu(empty_proto.Empty())

    async def conn_parameter_update(self):
        """
        """
        await self.__gatt_stub.ConnParameterUpdate(empty_proto.Empty())

    async def set_preferred_phy(self):
        """
        """
        await self.__gatt_stub.SetPreferredPhy(empty_proto.Empty())

    async def read_phy(self):
        """
        """
        await self.__gatt_stub.ReadPhy(empty_proto.Empty())

    async def test_command(self):
        """
        """
        await self.__gatt_stub.TestCommand(empty_proto.Empty())

    async def get_gatt_db(self):
        """
        """
        await self.__gatt_stub.GetGattDb(empty_proto.Empty())

    async def register_server(self):
        """
        """
        await self.__gatt_stub.RegisterServer(empty_proto.Empty())

    async def unregister_server(self):
        """
        """
        await self.__gatt_stub.UnregisterServer(empty_proto.Empty())

    async def connect(self):
        """
        """
        await self.__gatt_stub.Connect(empty_proto.Empty())

    async def disconnect(self):
        """
        """
        await self.__gatt_stub.Disconnect(empty_proto.Empty())

    async def add_service(self):
        """
        """
        await self.__gatt_stub.AddService(empty_proto.Empty())

    async def stop_service(self):
        """
        """
        await self.__gatt_stub.StopService(empty_proto.Empty())

    async def delete_service(self):
        """
        """
        await self.__gatt_stub.DeleteService(empty_proto.Empty())

    async def send_indication(self):
        """
        """
        await self.__gatt_stub.SendIndication(empty_proto.Empty())

    async def send_response(self):
        """
        """
        await self.__gatt_stub.SendResponse(empty_proto.Empty())

    async def set_preferred_phy(self):
        """
        """
        await self.__gatt_stub.SetPreferredPhy(empty_proto.Empty())

    async def read_phy(self):
        """
        """
        await self.__gatt_stub.ReadPhy(empty_proto.Empty())
