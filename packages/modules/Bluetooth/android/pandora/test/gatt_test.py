# Copyright 2022 Google LLC
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
import grpc
import logging

from avatar import BumblePandoraDevice, PandoraDevice, PandoraDevices
from bumble import pandora as bumble_server
from bumble.gatt import Characteristic, Service
from bumble.l2cap import L2CAP_Control_Frame
from bumble.pairing import PairingConfig
from bumble_experimental.gatt import GATTService
from mobly import base_test, signals, test_runner
from mobly.asserts import assert_equal  # type: ignore
from mobly.asserts import assert_in  # type: ignore
from mobly.asserts import assert_is_not_none  # type: ignore
from mobly.asserts import assert_not_in  # type: ignore
from mobly.asserts import assert_true  # type: ignore
from pandora.host_pb2 import RANDOM, Connection, DataTypes
from pandora.security_pb2 import LE_LEVEL3, PairingEventAnswer, SecureResponse
from pandora_experimental.gatt_grpc import GATT
from pandora_experimental.gatt_grpc_aio import GATT as AioGATT, add_GATTServicer_to_server
from pandora_experimental.gatt_pb2 import SUCCESS, ReadCharacteristicsFromUuidResponse
from typing import Optional, Tuple


class GattTest(base_test.BaseTestClass):  # type: ignore[misc]
    devices: Optional[PandoraDevices] = None

    # pandora devices.
    dut: PandoraDevice
    ref: PandoraDevice

    def setup_class(self) -> None:
        # Register experimental bumble servicers hook.
        bumble_server.register_servicer_hook(
            lambda bumble, _, server: add_GATTServicer_to_server(GATTService(bumble.device), server)
        )

        self.devices = PandoraDevices(self)
        self.dut, self.ref, *_ = self.devices

    def teardown_class(self) -> None:
        if self.devices:
            self.devices.stop_all()

    @avatar.asynchronous
    async def setup_test(self) -> None:
        await asyncio.gather(self.dut.reset(), self.ref.reset())

    def test_print_dut_gatt_services(self) -> None:
        advertise = self.ref.host.Advertise(legacy=True, connectable=True)
        dut_ref = self.dut.host.ConnectLE(public=self.ref.address, own_address_type=RANDOM).connection
        assert_is_not_none(dut_ref)
        assert dut_ref
        advertise.cancel()

        gatt = GATT(self.dut.channel)
        services = gatt.DiscoverServices(dut_ref)
        self.dut.log.info(f'DUT services: {services}')

    def test_print_ref_gatt_services(self) -> None:
        advertise = self.dut.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=RANDOM,
            data=DataTypes(manufacturer_specific_data=b'pause cafe'),
        )

        scan = self.ref.host.Scan()
        dut = next((x for x in scan if b'pause cafe' in x.data.manufacturer_specific_data))
        scan.cancel()

        ref_dut = self.ref.host.ConnectLE(own_address_type=RANDOM, **dut.address_asdict()).connection
        assert_is_not_none(ref_dut)
        assert ref_dut
        advertise.cancel()

        gatt = GATT(self.ref.channel)
        services = gatt.DiscoverServices(ref_dut)
        self.ref.log.info(f'REF services: {services}')

    async def connect_dut_to_ref(self) -> Tuple[Connection, Connection]:
        ref_advertisement = self.ref.aio.host.Advertise(
            legacy=True,
            connectable=True,
        )

        dut_connection_to_ref = (
            await self.dut.aio.host.ConnectLE(public=self.ref.address, own_address_type=RANDOM)
        ).connection
        assert_is_not_none(dut_connection_to_ref)
        assert dut_connection_to_ref

        ref_connection_to_dut = (await anext(aiter(ref_advertisement))).connection
        ref_advertisement.cancel()

        return dut_connection_to_ref, ref_connection_to_dut

    @avatar.asynchronous
    async def test_read_characteristic_while_pairing(self) -> None:
        if isinstance(self.dut, BumblePandoraDevice):
            raise signals.TestSkip('TODO: b/273941061')
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')

        # arrange: set up GATT service on REF side with a characteristic
        # that can only be read after pairing
        SERVICE_UUID = "00005A00-0000-1000-8000-00805F9B34FB"
        CHARACTERISTIC_UUID = "00006A00-0000-1000-8000-00805F9B34FB"
        service = Service(
            SERVICE_UUID,
            [
                Characteristic(
                    CHARACTERISTIC_UUID,
                    Characteristic.READ,
                    Characteristic.READ_REQUIRES_ENCRYPTION,
                    b"Hello, world!",
                ),
            ],
        )
        self.ref.device.add_service(service)  # type:ignore
        # disable MITM requirement on REF side (since it only does just works)
        self.ref.device.pairing_config_factory = lambda _: PairingConfig(  # type:ignore
            sc=True, mitm=False, bonding=True
        )
        # manually handle pairing on the DUT side
        dut_pairing_events = self.dut.aio.security.OnPairing()
        # set up connection
        dut_connection_to_ref, ref_connection_to_dut = await self.connect_dut_to_ref()

        # act: initiate pairing from REF side (send a security request)
        async def ref_secure() -> SecureResponse:
            return await self.ref.aio.security.Secure(connection=ref_connection_to_dut, le=LE_LEVEL3)

        ref_secure_task = asyncio.create_task(ref_secure())

        # wait for pairing to start
        event = await anext(dut_pairing_events)

        # before acknowledging pairing, start a GATT read
        dut_gatt = AioGATT(self.dut.aio.channel)

        async def dut_read() -> ReadCharacteristicsFromUuidResponse:
            return await dut_gatt.ReadCharacteristicsFromUuid(dut_connection_to_ref, CHARACTERISTIC_UUID, 1, 0xFFFF)

        dut_read_task = asyncio.create_task(dut_read())

        await asyncio.sleep(3)

        # now continue with pairing
        dut_pairing_events.send_nowait(PairingEventAnswer(event=event, confirm=True))

        # android pops up a second pairing notification for some reason, accept it
        event = await anext(dut_pairing_events)
        dut_pairing_events.send_nowait(PairingEventAnswer(event=event, confirm=True))

        # assert: that the read succeeded (so Android re-tried the read after pairing)
        read_response = await dut_read_task
        self.ref.log.info(read_response)
        assert_equal(read_response.characteristics_read[0].status, SUCCESS)
        assert_equal(read_response.characteristics_read[0].value.value, b"Hello, world!")

        # make sure pairing was successful
        ref_secure_res = await ref_secure_task
        assert_equal(ref_secure_res.result_variant(), 'success')

    @avatar.asynchronous
    async def test_rediscover_whenever_unbonded(self) -> None:
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')

        # arrange: set up one GATT service on REF side
        dut_gatt = AioGATT(self.dut.aio.channel)
        SERVICE_UUID_1 = "00005A00-0000-1000-8000-00805F9B34FB"
        SERVICE_UUID_2 = "00005A01-0000-1000-8000-00805F9B34FB"
        self.ref.device.add_service(Service(SERVICE_UUID_1, []))  # type:ignore
        # connect both devices
        dut_connection_to_ref, ref_connection_to_dut = await self.connect_dut_to_ref()

        # act: perform service discovery, disconnect, add the second service, reconnect, and try discovery again
        first_discovery = await dut_gatt.DiscoverServices(dut_connection_to_ref)
        await self.ref.aio.host.Disconnect(ref_connection_to_dut)
        self.ref.device.add_service(Service(SERVICE_UUID_2, []))  # type:ignore
        dut_connection_to_ref, _ = await self.connect_dut_to_ref()
        second_discovery = await dut_gatt.DiscoverServices(dut_connection_to_ref)

        # assert: that we found only one service in the first discovery
        assert_in(SERVICE_UUID_1, (service.uuid for service in first_discovery.services))
        assert_not_in(SERVICE_UUID_2, (service.uuid for service in first_discovery.services))
        # assert: but found both in the second discovery
        assert_in(SERVICE_UUID_1, (service.uuid for service in second_discovery.services))
        assert_in(SERVICE_UUID_2, (service.uuid for service in second_discovery.services))

    @avatar.asynchronous
    async def test_do_not_discover_when_bonded(self) -> None:
        # NOTE: if service change indication is ever enabled in Bumble, both this test + the previous test must DISABLE IT
        # otherwise this test will fail, and the previous test will pass even on a broken implementation

        raise signals.TestSkip('TODO(aryarahul): b/276757181')
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')

        # arrange: set up one GATT service on REF side
        dut_gatt = AioGATT(self.dut.aio.channel)
        SERVICE_UUID_1 = "00005A00-0000-1000-8000-00805F9B34FB"
        SERVICE_UUID_2 = "00005A01-0000-1000-8000-00805F9B34FB"
        self.ref.device.add_service(Service(SERVICE_UUID_1, []))  # type:ignore
        # connect both devices
        dut_connection_to_ref, ref_connection_to_dut = await self.connect_dut_to_ref()
        # bond devices and disconnect
        await self.dut.aio.security.Secure(connection=dut_connection_to_ref, le=LE_LEVEL3)
        await self.ref.aio.host.Disconnect(ref_connection_to_dut)

        # act: connect, perform service discovery, disconnect, add the second service, reconnect, and try discovery again
        dut_connection_to_ref, ref_connection_to_dut = await self.connect_dut_to_ref()
        first_discovery = await dut_gatt.DiscoverServices(dut_connection_to_ref)
        await self.ref.aio.host.Disconnect(ref_connection_to_dut)

        self.ref.device.add_service(Service(SERVICE_UUID_2, []))  # type:ignore
        dut_connection_to_ref, _ = await self.connect_dut_to_ref()
        second_discovery = await dut_gatt.DiscoverServices(dut_connection_to_ref)

        # assert: that we found only one service in the first discovery
        assert_in(SERVICE_UUID_1, (service.uuid for service in first_discovery.services))
        assert_not_in(SERVICE_UUID_2, (service.uuid for service in first_discovery.services))
        # assert: but found both in the second discovery
        assert_in(SERVICE_UUID_1, (service.uuid for service in second_discovery.services))
        assert_in(SERVICE_UUID_2, (service.uuid for service in second_discovery.services))

    @avatar.asynchronous
    async def test_eatt_when_not_encrypted_no_timeout(self) -> None:
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')
        advertise = self.dut.aio.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=RANDOM,
            data=DataTypes(manufacturer_specific_data=b'pause cafe'),
        )

        scan = self.ref.aio.host.Scan()
        dut = await anext((x async for x in scan if b'pause cafe' in x.data.manufacturer_specific_data))
        scan.cancel()

        ref_dut = (await self.ref.aio.host.ConnectLE(own_address_type=RANDOM, **dut.address_asdict())).connection
        assert_is_not_none(ref_dut)
        assert ref_dut
        advertise.cancel()

        connection = self.ref.device.lookup_connection(int.from_bytes(ref_dut.cookie.value, 'big'))
        assert connection

        connection_request = L2CAP_Control_Frame.from_bytes(
            (
                b"\x17"  # code of L2CAP_CREDIT_BASED_CONNECTION_REQ
                b"\x01"  # identifier
                b"\x0a\x00"  # data length
                b"\x27\x00"  # psm(EATT)
                b"\x64\x00"  # MTU
                b"\x64\x00"  # MPS
                b"\x64\x00"  # initial credit
                b"\x40\x00"  # source cid[0]
            )
        )

        fut = asyncio.get_running_loop().create_future()
        setattr(self.ref.device.l2cap_channel_manager, "on_[0x18]", lambda _, _1, frame: fut.set_result(frame))
        self.ref.device.l2cap_channel_manager.send_control_frame(  # type:ignore
            connection, 0x05, connection_request
        )
        control_frame = await fut

        assert_equal(bytes(control_frame)[10], 0x05)  # All connections refused – insufficient authentication
        assert_true(await is_connected(self.ref, ref_dut), "Device is no longer connected")


async def is_connected(device: PandoraDevice, connection: Connection) -> bool:
    try:
        await device.aio.host.WaitDisconnection(connection=connection, timeout=5)
        return False
    except grpc.RpcError as e:
        assert_equal(e.code(), grpc.StatusCode.DEADLINE_EXCEEDED)  # type: ignore
        return True


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    test_runner.main()  # type: ignore
