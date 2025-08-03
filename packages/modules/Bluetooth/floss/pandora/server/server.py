# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Floss bluetooth test server."""

import asyncio
import logging

from floss.pandora.server import bluetooth as bluetooth_module
from floss.pandora.server import gatt
from floss.pandora.server import hid
from floss.pandora.server import host
from floss.pandora.server import security
import grpc
from pandora import host_grpc_aio
from pandora import security_grpc_aio
from pandora_experimental import gatt_grpc_aio
from pandora_experimental import hid_grpc_aio


async def serve(port):
    """Start serving the Floss bluetooth test server."""

    logging.basicConfig(filename='/var/log/grpc_server_log', filemode='a', level=logging.DEBUG)

    try:
        while True:
            bluetooth = bluetooth_module.Bluetooth()
            bluetooth.reset()
            logging.info("bluetooth initialized")

            server = grpc.aio.server()
            security_service = security.SecurityService(bluetooth)
            security_grpc_aio.add_SecurityServicer_to_server(security_service, server)

            host_service = host.HostService(server, bluetooth, security_service)
            host_grpc_aio.add_HostServicer_to_server(host_service, server)

            security_storage_service = security.SecurityStorageService(bluetooth)
            security_grpc_aio.add_SecurityStorageServicer_to_server(security_storage_service, server)

            gatt_service = gatt.GATTService(bluetooth)
            gatt_grpc_aio.add_GATTServicer_to_server(gatt_service, server)

            hid_service = hid.HIDService(bluetooth)
            hid_grpc_aio.add_HIDServicer_to_server(hid_service, server)

            server.add_insecure_port(f'[::]:{port}')

            await server.start()
            logging.info("server started")

            await server.wait_for_termination()
            bluetooth.cleanup()
            del bluetooth
    finally:
        await server.stop(None)
        bluetooth.cleanup()
        del bluetooth


if __name__ == '__main__':
    asyncio.run(serve(8999))
