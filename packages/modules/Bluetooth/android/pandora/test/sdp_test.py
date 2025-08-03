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

import asyncio
import avatar
import logging

from avatar import BumblePandoraDevice, PandoraDevice, PandoraDevices
from bumble.colors import color
from bumble.core import BT_AUDIO_SOURCE_SERVICE
from bumble.sdp import (
    SDP_ALL_ATTRIBUTES_RANGE,
    SDP_PUBLIC_BROWSE_ROOT,
    SDP_SERVICE_CLASS_ID_LIST_ATTRIBUTE_ID,
    Client as SDP_Client,
    ServiceAttribute,
)
from mobly import base_test, test_runner
from mobly.asserts import assert_equal  # type: ignore
from mobly.asserts import assert_in  # type: ignore
from mobly.asserts import assert_is_none  # type: ignore
from mobly.asserts import assert_is_not_none  # type: ignore
from mobly.asserts import fail  # type: ignore
from typing import Optional


class SdpTest(base_test.BaseTestClass):  # type: ignore[misc]
    '''
    This class aim to test SDP on Classic Bluetooth devices.
    '''

    devices: Optional[PandoraDevices] = None

    # pandora devices.
    dut: PandoraDevice
    ref: PandoraDevice

    @avatar.asynchronous
    async def setup_class(self) -> None:
        self.devices = PandoraDevices(self)
        self.dut, self.ref, *_ = self.devices

        # Enable BR/EDR mode and SSP for Bumble devices.
        for device in self.devices:
            if isinstance(device, BumblePandoraDevice):
                device.config.setdefault('classic_enabled', True)
                device.config.setdefault('classic_ssp_enabled', True)
                device.config.setdefault(
                    'server',
                    {
                        'io_capability': 'display_output_and_yes_no_input',
                    },
                )

        await asyncio.gather(self.dut.reset(), self.ref.reset())

    def teardown_class(self) -> None:
        if self.devices:
            self.devices.stop_all()

    @avatar.asynchronous
    async def test_sdp_connect_and_disconnect(self) -> None:
        # Pandora connection tokens
        ref_dut, dut_ref = None, None

        # Make classic connection
        ref_dut_res, dut_ref_res = await asyncio.gather(
            self.ref.aio.host.Connect(address=self.dut.address),
            self.dut.aio.host.WaitConnection(address=self.ref.address),
        )
        assert_is_not_none(ref_dut_res.connection)
        assert_is_not_none(dut_ref_res.connection)
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection
        assert ref_dut and dut_ref

        # Get connection handle
        connection_handle = int.from_bytes(ref_dut.cookie.value, 'big')
        connection = self.ref.device.lookup_connection(connection_handle)  # type: ignore

        # Connect to the SDP Server
        self.ref.log.info(f'Connecting to SDP Server')
        sdp_client = SDP_Client(self.ref.device)  # type: ignore
        await sdp_client.connect(connection)  # type: ignore
        self.ref.log.info(f'Connected to SDP Server')

        # SDP Client disconnect
        await sdp_client.disconnect()  # type: ignore
        await self.ref.aio.host.Disconnect(connection=ref_dut)

    @avatar.asynchronous
    async def test_sdp_list_services_and_attributes(self) -> None:
        # Pandora connection tokens
        ref_dut, dut_ref = None, None

        # Make classic connection
        ref_dut_res, dut_ref_res = await asyncio.gather(
            self.ref.aio.host.Connect(address=self.dut.address),
            self.dut.aio.host.WaitConnection(address=self.ref.address),
        )
        assert_is_not_none(ref_dut_res.connection)
        assert_is_not_none(dut_ref_res.connection)
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection
        assert ref_dut and dut_ref

        # Get connection handle
        connection_handle = int.from_bytes(ref_dut.cookie.value, 'big')
        connection = self.ref.device.lookup_connection(connection_handle)  # type: ignore

        # Connect to the SDP Server
        self.ref.log.info(f'Connecting to SDP Server')
        sdp_client = SDP_Client(self.ref.device)  # type: ignore
        await sdp_client.connect(connection)  # type: ignore
        self.ref.log.info(f'Connected to SDP Server')

        # List all services in the root browse group
        self.ref.log.info(f'Search Services')
        service_record_handles = await sdp_client.search_services([SDP_PUBLIC_BROWSE_ROOT])  # type: ignore
        assert bool(service_record_handles)  # type: ignore
        print(color('SERVICES:', 'yellow'), service_record_handles)  # type: ignore

        # For each service in the root browse group, get all its attributes
        for service_record_handle in service_record_handles:  # type: ignore
            attributes = await sdp_client.get_attributes(  # type: ignore
                service_record_handle, [SDP_ALL_ATTRIBUTES_RANGE]  # type: ignore
            )
            print(color(f'SERVICE {service_record_handle:04X} attributes:', 'yellow'))
            for attribute in attributes:  # type: ignore
                print('  ', attribute.to_string(with_colors=True))  # type: ignore

        # Sdp client disconnect
        await sdp_client.disconnect()  # type: ignore
        await self.ref.aio.host.Disconnect(connection=ref_dut)

    @avatar.asynchronous
    async def test_sdp_search_verify_audio_source_service(self) -> None:
        # Pandora connection tokens
        ref_dut, dut_ref = None, None

        # Make classic connection
        ref_dut_res, dut_ref_res = await asyncio.gather(
            self.ref.aio.host.Connect(address=self.dut.address),
            self.dut.aio.host.WaitConnection(address=self.ref.address),
        )
        assert_is_not_none(ref_dut_res.connection)
        assert_is_not_none(dut_ref_res.connection)
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection
        assert ref_dut and dut_ref

        # Get connection handle
        connection_handle = int.from_bytes(ref_dut.cookie.value, 'big')
        connection = self.ref.device.lookup_connection(connection_handle)  # type: ignore

        # Connect to the SDP Server
        self.ref.log.info(f'Connecting to SDP Server')
        sdp_client = SDP_Client(self.ref.device)  # type: ignore
        await sdp_client.connect(connection)  # type: ignore
        self.ref.log.info(f'Connected to SDP Server')

        # List all services in the root browse group
        self.ref.log.info(f'Search Services')
        service_record_handles = await sdp_client.search_services([SDP_PUBLIC_BROWSE_ROOT])  # type: ignore
        assert bool(service_record_handles)  # type: ignore

        # Verify Audio Source service is present
        service_found = False
        for service_record_handle in service_record_handles:  # type: ignore
            attributes = await sdp_client.get_attributes(  # type: ignore
                service_record_handle, [SDP_ALL_ATTRIBUTES_RANGE]  # type: ignore
            )
            for attribute in attributes:  # type: ignore
                if attribute.id == SDP_SERVICE_CLASS_ID_LIST_ATTRIBUTE_ID:  # type: ignore
                    if ServiceAttribute.is_uuid_in_value(BT_AUDIO_SOURCE_SERVICE, attribute.value):  # type: ignore
                        service_found = True
                        self.ref.log.info(f'Service found')
        assert service_found

        # SDP Client disconnect
        await sdp_client.disconnect()  # type: ignore
        await self.ref.aio.host.Disconnect(connection=ref_dut)


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    test_runner.main()  # type: ignore
