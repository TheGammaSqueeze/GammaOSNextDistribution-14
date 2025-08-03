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
import logging

from avatar import BumblePandoraDevice, PandoraDevice, PandoraDevices
from avatar.aio import asynchronous
from bumble import smp
from bumble.hci import Address
from bumble.pairing import PairingDelegate
from concurrent import futures
from contextlib import suppress
from mobly import base_test, signals, test_runner
from mobly.asserts import assert_equal  # type: ignore
from mobly.asserts import assert_false  # type: ignore
from mobly.asserts import assert_is_not_none  # type: ignore
from mobly.asserts import assert_true  # type: ignore
from pandora.host_pb2 import RANDOM, DataTypes, OwnAddressType, ScanningResponse
from pandora.security_pb2 import LE_LEVEL3, PairingEventAnswer
from typing import NoReturn, Optional


class SmpTest(base_test.BaseTestClass):  # type: ignore[misc]
    devices: Optional[PandoraDevices] = None

    dut: PandoraDevice
    ref: PandoraDevice

    def setup_class(self) -> None:
        self.devices = PandoraDevices(self)
        self.dut, self.ref, *_ = self.devices

        # Enable BR/EDR mode for Bumble devices.
        for device in self.devices:
            if isinstance(device, BumblePandoraDevice):
                device.config.setdefault('classic_enabled', True)

    def teardown_class(self) -> None:
        if self.devices:
            self.devices.stop_all()

    @asynchronous
    async def setup_test(self) -> None:
        await asyncio.gather(self.dut.reset(), self.ref.reset())

    async def handle_pairing_events(self) -> NoReturn:
        dut_pairing_stream = self.dut.aio.security.OnPairing()
        ref_pairing_stream = self.ref.aio.security.OnPairing()
        try:
            while True:
                dut_pairing_event = await anext(dut_pairing_stream)

                if dut_pairing_event.method_variant() == 'passkey_entry_notification':
                    ref_pairing_event = await anext(ref_pairing_stream)

                    assert_equal(ref_pairing_event.method_variant(), 'passkey_entry_request')
                    assert_is_not_none(dut_pairing_event.passkey_entry_notification)
                    assert dut_pairing_event.passkey_entry_notification is not None

                    ref_ev_answer = PairingEventAnswer(
                        event=ref_pairing_event,
                        passkey=dut_pairing_event.passkey_entry_notification,
                    )
                    ref_pairing_stream.send_nowait(ref_ev_answer)
                else:
                    dut_pairing_stream.send_nowait(PairingEventAnswer(
                        event=dut_pairing_event,
                        confirm=True,
                    ))
                    ref_pairing_event = await anext(ref_pairing_stream)

                    ref_pairing_stream.send_nowait(PairingEventAnswer(
                        event=ref_pairing_event,
                        confirm=True,
                    ))

        finally:
            dut_pairing_stream.cancel()

    async def dut_pair(self, dut_address_type: OwnAddressType, ref_address_type: OwnAddressType) -> ScanningResponse:
        advertisement = self.ref.aio.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=ref_address_type,
            data=DataTypes(manufacturer_specific_data=b'pause cafe'),
        )

        scan = self.dut.aio.host.Scan(own_address_type=dut_address_type)
        ref = await anext((x async for x in scan if b'pause cafe' in x.data.manufacturer_specific_data))
        scan.cancel()

        pairing = asyncio.create_task(self.handle_pairing_events())
        (dut_ref_res, ref_dut_res) = await asyncio.gather(
            self.dut.aio.host.ConnectLE(own_address_type=dut_address_type, **ref.address_asdict()),
            anext(aiter(advertisement)),
        )

        advertisement.cancel()
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection
        assert_is_not_none(dut_ref)
        assert dut_ref

        (secure, wait_security) = await asyncio.gather(
            self.dut.aio.security.Secure(connection=dut_ref, le=LE_LEVEL3),
            self.ref.aio.security.WaitSecurity(connection=ref_dut, le=LE_LEVEL3),
        )

        pairing.cancel()
        with suppress(asyncio.CancelledError, futures.CancelledError):
            await pairing

        assert_equal(secure.result_variant(), 'success')
        assert_equal(wait_security.result_variant(), 'success')

        await asyncio.gather(
            self.ref.aio.host.Disconnect(connection=ref_dut),
            self.dut.aio.host.WaitDisconnection(connection=dut_ref),
        )
        return ref

    @asynchronous
    async def test_le_pairing_delete_dup_bond_record(self) -> None:
        if isinstance(self.dut, BumblePandoraDevice):
            raise signals.TestSkip('TODO: Fix test for Bumble DUT')
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')

        class Session(smp.Session):
            # Hack to send same identity address from ref during both pairing
            def send_command(self: smp.Session, command: smp.SMP_Command) -> None:
                if isinstance(command, smp.SMP_Identity_Address_Information_Command):
                    command = smp.SMP_Identity_Address_Information_Command(
                        addr_type=Address.RANDOM_IDENTITY_ADDRESS,
                        bd_addr=Address(
                            'F6:F7:F8:F9:FA:FB',
                            Address.RANDOM_IDENTITY_ADDRESS,
                        ),
                    )
                self.manager.send_command(self.connection, command)

        self.ref.device.smp_session_proxy = Session

        # Pair with same device 2 times.
        # Ref device advertises with different random address but uses same identity address
        ref1 = await self.dut_pair(dut_address_type=RANDOM, ref_address_type=RANDOM)
        is_bonded = await self.dut.aio.security_storage.IsBonded(random=ref1.random)
        assert_true(is_bonded.value, "")

        await self.ref.reset()
        self.ref.device.smp_session_proxy = Session

        ref2 = await self.dut_pair(dut_address_type=RANDOM, ref_address_type=RANDOM)
        is_bonded = await self.dut.aio.security_storage.IsBonded(random=ref2.random)
        assert_true(is_bonded.value, "")

        is_bonded = await self.dut.aio.security_storage.IsBonded(random=ref1.random)
        assert_false(is_bonded.value, "")

    @asynchronous
    async def test_mitm_sec_req_on_enc(self) -> None:
        if not isinstance(self.ref, BumblePandoraDevice):
            raise signals.TestSkip('Test require Bumble as reference device(s)')

        io_capability = PairingDelegate.IoCapability.DISPLAY_OUTPUT_AND_KEYBOARD_INPUT
        self.ref.server_config.io_capability = io_capability

        advertisement = self.ref.aio.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=RANDOM,
            data=DataTypes(manufacturer_specific_data=b'pause cafe'),
        )

        scan = self.dut.aio.host.Scan(own_address_type=RANDOM)
        ref = await anext((x async for x in scan if b'pause cafe' in x.data.manufacturer_specific_data))
        scan.cancel()

        asyncio.create_task(self.handle_pairing_events())
        (dut_ref_res, ref_dut_res) = await asyncio.gather(
            self.dut.aio.host.ConnectLE(own_address_type=RANDOM, **ref.address_asdict()),
            anext(aiter(advertisement)),
        )

        advertisement.cancel()
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection
        assert_is_not_none(dut_ref)
        assert dut_ref

        # Pair with MITM requirements
        (secure, wait_security) = await asyncio.gather(
            self.dut.aio.security.Secure(connection=dut_ref, le=LE_LEVEL3),
            self.ref.aio.security.WaitSecurity(connection=ref_dut, le=LE_LEVEL3),
        )

        assert_equal(secure.result_variant(), 'success')
        assert_equal(wait_security.result_variant(), 'success')

        # Disconnect
        await asyncio.gather(
            self.ref.aio.host.Disconnect(connection=ref_dut),
            self.dut.aio.host.WaitDisconnection(connection=dut_ref),
        )

        advertisement = self.ref.aio.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=RANDOM,
            data=DataTypes(manufacturer_specific_data=b'pause cafe'),
        )

        scan = self.dut.aio.host.Scan(own_address_type=RANDOM)
        ref = await anext((x async for x in scan if b'pause cafe' in x.data.manufacturer_specific_data))
        scan.cancel()

        (dut_ref_res, ref_dut_res) = await asyncio.gather(
            self.dut.aio.host.ConnectLE(own_address_type=RANDOM, **ref.address_asdict()),
            anext(aiter(advertisement)),
        )
        ref_dut, dut_ref = ref_dut_res.connection, dut_ref_res.connection

        # Wait for the link to get encrypted
        connection = self.ref.device.lookup_connection(int.from_bytes(ref_dut.cookie.value, 'big'))
        assert_is_not_none(connection)
        assert connection

        self.ref.device.smp_manager.request_pairing(connection)

        def on_connection_encryption_change() -> None:
            assert isinstance(self.ref, BumblePandoraDevice)
            self.ref.device.smp_manager.request_pairing(connection)

        connection.on('connection_encryption_change', on_connection_encryption_change)

        # Fail if repairing is initiated
        fut = asyncio.get_running_loop().create_future()

        class Session(smp.Session):

            def on_smp_pairing_request_command(self, command: smp.SMP_Pairing_Request_Command) -> None:
                nonlocal fut
                fut.set_result(False)

        self.ref.device.smp_session_proxy = Session

        # Pass if the link is encrypted again
        def on_connection_encryption_key_refresh() -> None:
            nonlocal fut
            fut.set_result(True)

        connection.on('connection_encryption_key_refresh', on_connection_encryption_key_refresh)

        assert_true(await fut, "Repairing initiated")


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    test_runner.main()  # type: ignore
