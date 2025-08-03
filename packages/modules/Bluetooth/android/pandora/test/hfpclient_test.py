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
import collections
import logging

from avatar import BumblePandoraDevice, PandoraDevice, PandoraDevices
from avatar.pandora_server import AndroidPandoraServer
from bumble import rfcomm
from bumble.colors import color
from bumble.core import (
    BT_GENERIC_AUDIO_SERVICE,
    BT_HANDSFREE_AUDIO_GATEWAY_SERVICE,
    BT_L2CAP_PROTOCOL_ID,
    BT_RFCOMM_PROTOCOL_ID,
)
from bumble.rfcomm import DLC, Server as RfcommServer
from bumble.sdp import (
    SDP_BLUETOOTH_PROFILE_DESCRIPTOR_LIST_ATTRIBUTE_ID,
    SDP_PROTOCOL_DESCRIPTOR_LIST_ATTRIBUTE_ID,
    SDP_SERVICE_CLASS_ID_LIST_ATTRIBUTE_ID,
    SDP_SERVICE_RECORD_HANDLE_ATTRIBUTE_ID,
    DataElement,
    ServiceAttribute,
)
from mobly import base_test, test_runner
from mobly.asserts import assert_equal  # type: ignore
from mobly.asserts import assert_in  # type: ignore
from mobly.asserts import assert_not_equal  # type: ignore
from mobly.asserts import assert_not_in  # type: ignore
from pandora.host_pb2 import Connection as PandoraConnection
from pandora.security_pb2 import LEVEL2
from typing import Dict, List, Optional, Tuple, Union

SDP_PROFILE_SUPPORTED_FEATURES_ID = 0x0311

HFP_AG_FEATURE_HF_INDICATORS = 1 << 10
HFP_AG_FEATURE_DEFAULT = HFP_AG_FEATURE_HF_INDICATORS

HFP_HF_FEATURE_HF_INDICATORS = 1 << 8
HFP_HF_FEATURE_DEFAULT = hex(0x01B5)

PROPERTY_HF_ENABLED = 'bluetooth.profile.hfp.hf.enabled'
PROPERTY_HF_FEATURES = 'bluetooth.hfp.hf_client_features.config'
PROPERTY_HF_INDICATOR_ENHANCED_DRIVER_SAFETY = 'bluetooth.headset_client.indicator.enhanced_driver_safety.enabled'

HFP_VERSION_1_7 = 0x0107


# Stub for Audio Gateway implementation
# TODO: b/296471045
logger = logging.getLogger(__name__)


class HfpProtocol:
    dlc: rfcomm.DLC
    buffer: str
    lines: collections.deque[str]
    lines_available: asyncio.Event

    def __init__(self, dlc: rfcomm.DLC) -> None:
        self.dlc = dlc
        self.buffer = ''
        self.lines = collections.deque()
        self.lines_available = asyncio.Event()

        dlc.sink = self.feed

    def feed(self, data: Union[bytes, str]) -> None:
        # Convert the data to a string if needed
        if isinstance(data, bytes):
            data = data.decode('utf-8')

        logger.debug(f'<<< Data received: {data}')

        # Add to the buffer and look for lines
        self.buffer += data
        while (separator := self.buffer.find('\r')) >= 0:
            line = self.buffer[:separator].strip()
            self.buffer = self.buffer[separator + 1 :]
            if len(line) > 0:
                self.on_line(line)

    def on_line(self, line: str) -> None:
        self.lines.append(line)
        self.lines_available.set()

    def send_command_line(self, line: str) -> None:
        logger.debug(color(f'>>> {line}', 'yellow'))
        self.dlc.write(line + '\r')

    def send_response_line(self, line: str) -> None:
        logger.debug(color(f'>>> {line}', 'yellow'))
        self.dlc.write('\r\n' + line + '\r\n')

    async def next_line(self) -> str:
        await self.lines_available.wait()
        line = self.lines.popleft()
        if not self.lines:
            self.lines_available.clear()
        logger.debug(color(f'<<< {line}', 'green'))
        return line


class HfpClientTest(base_test.BaseTestClass):  # type: ignore[misc]
    devices: Optional[PandoraDevices] = None

    # pandora devices.
    dut: PandoraDevice
    ref: BumblePandoraDevice

    def setup_class(self) -> None:
        self.devices = PandoraDevices(self)
        self.dut, ref, *_ = self.devices
        assert isinstance(ref, BumblePandoraDevice)
        self.ref = ref

        # Enable BR/EDR mode and SSP for Bumble devices.
        self.ref.config.setdefault('classic_enabled', True)
        self.ref.config.setdefault('classic_ssp_enabled', True)
        self.ref.config.setdefault(
            'server',
            {
                'io_capability': 'no_output_no_input',
            },
        )

        for server in self.devices._servers:
            if isinstance(server, AndroidPandoraServer):
                self.dut_adb = server.device.adb
                # Enable HFP Client
                self.dut_adb.shell(['setprop', PROPERTY_HF_ENABLED, 'true'])  # type: ignore
                # Set HF features if not set yet
                hf_feature_text = self.dut_adb.getprop(PROPERTY_HF_FEATURES)  # type: ignore
                if len(hf_feature_text) == 0:
                    self.dut_adb.shell(['setprop', PROPERTY_HF_FEATURES, HFP_HF_FEATURE_DEFAULT])  # type: ignore
                break

    def teardown_class(self) -> None:
        if self.devices:
            self.devices.stop_all()

    @avatar.asynchronous
    async def setup_test(self) -> None:
        self.ref._bumble.config.update({'server': {'identity_address_type': 'public'}})
        await asyncio.gather(self.dut.reset(), self.ref.reset())

    # TODO(b/286338264): Moving connecting and bonding methods to a shared util scripts
    async def make_classic_connection(self) -> Tuple[PandoraConnection, PandoraConnection]:
        dut_ref, ref_dut = await asyncio.gather(
            self.dut.aio.host.WaitConnection(address=self.ref.address),
            self.ref.aio.host.Connect(address=self.dut.address),
        )

        assert_equal(dut_ref.result_variant(), 'connection')
        assert_equal(ref_dut.result_variant(), 'connection')
        assert dut_ref.connection is not None and ref_dut.connection is not None

        return dut_ref.connection, ref_dut.connection

    async def make_classic_bond(self, dut_ref: PandoraConnection, ref_dut: PandoraConnection) -> None:
        dut_ref_sec, ref_dut_sec = await asyncio.gather(
            self.dut.aio.security.Secure(connection=dut_ref, classic=LEVEL2),
            self.ref.aio.security.WaitSecurity(connection=ref_dut, classic=LEVEL2),
        )
        assert_equal(dut_ref_sec.result_variant(), 'success')
        assert_equal(ref_dut_sec.result_variant(), 'success')

    async def make_hfp_connection(self) -> HfpProtocol:
        # Listen RFCOMM
        dlc_connected = asyncio.get_running_loop().create_future()

        def on_dlc(dlc: DLC) -> None:
            dlc_connected.set_result(dlc)

        rfcomm_server = RfcommServer(self.ref.device)  # type: ignore
        channel_number = rfcomm_server.listen(on_dlc)  # type: ignore

        # Setup SDP records
        self.ref.device.sdp_service_records = make_bumble_ag_sdp_records(HFP_VERSION_1_7, channel_number, 0)

        # Connect and pair
        dut_ref, ref_dut = await self.make_classic_connection()
        await self.make_classic_bond(dut_ref, ref_dut)

        # By default, Android HF should auto connect
        dlc = await dlc_connected
        assert isinstance(dlc, DLC)

        return HfpProtocol(dlc)  # type: ignore

    @avatar.parameterized((True,), (False,))  # type: ignore[misc]
    @avatar.asynchronous
    async def test_hf_indicator_setup(self, enhanced_driver_safety_enabled: bool) -> None:
        if enhanced_driver_safety_enabled:
            self.dut_adb.shell(['setprop', PROPERTY_HF_INDICATOR_ENHANCED_DRIVER_SAFETY, 'true'])  # type: ignore
        else:
            self.dut_adb.shell(['setprop', PROPERTY_HF_INDICATOR_ENHANCED_DRIVER_SAFETY, 'false'])  # type: ignore

        ref_dut_hfp_protocol = await self.make_hfp_connection()

        class TestAgServer(HfpAgServer):
            def on_brsf(self, hf_features: int) -> None:
                # HF indicators should be enabled
                assert_not_equal(hf_features & HFP_HF_FEATURE_HF_INDICATORS, 0)
                return super().on_brsf(hf_features)

            def on_bind_list(self, indicators: list[int]) -> None:
                if enhanced_driver_safety_enabled:
                    assert_in(1, indicators)
                else:
                    assert_not_in(1, indicators)
                self.terminated = True

        server = TestAgServer(ref_dut_hfp_protocol, ag_features=HFP_AG_FEATURE_HF_INDICATORS)
        await server.serve()


def make_bumble_ag_sdp_records(
    hfp_version: int, rfcomm_channel: int, ag_sdp_features: int
) -> Dict[int, List[ServiceAttribute]]:
    return {
        0x00010001: [
            ServiceAttribute(
                SDP_SERVICE_RECORD_HANDLE_ATTRIBUTE_ID,
                DataElement.unsigned_integer_32(0x00010001),
            ),
            ServiceAttribute(
                SDP_SERVICE_CLASS_ID_LIST_ATTRIBUTE_ID,
                DataElement.sequence(
                    [
                        DataElement.uuid(BT_HANDSFREE_AUDIO_GATEWAY_SERVICE),
                        DataElement.uuid(BT_GENERIC_AUDIO_SERVICE),
                    ]
                ),
            ),
            ServiceAttribute(
                SDP_PROTOCOL_DESCRIPTOR_LIST_ATTRIBUTE_ID,
                DataElement.sequence(
                    [
                        DataElement.sequence([DataElement.uuid(BT_L2CAP_PROTOCOL_ID)]),
                        DataElement.sequence(
                            [
                                DataElement.uuid(BT_RFCOMM_PROTOCOL_ID),
                                DataElement.unsigned_integer_8(rfcomm_channel),
                            ]
                        ),
                    ]
                ),
            ),
            ServiceAttribute(
                SDP_BLUETOOTH_PROFILE_DESCRIPTOR_LIST_ATTRIBUTE_ID,
                DataElement.sequence(
                    [
                        DataElement.sequence(
                            [
                                DataElement.uuid(BT_HANDSFREE_AUDIO_GATEWAY_SERVICE),
                                DataElement.unsigned_integer_16(hfp_version),
                            ]
                        )
                    ]
                ),
            ),
            ServiceAttribute(
                SDP_PROFILE_SUPPORTED_FEATURES_ID,
                DataElement.unsigned_integer_16(ag_sdp_features),
            ),
        ]
    }


class HfpAgServer:
    enabled_hf_indicators: list[int]
    hf_features: int

    def __init__(self, protocol: HfpProtocol, ag_features: int = HFP_AG_FEATURE_DEFAULT) -> None:
        self.protocol = protocol
        self.ag_features = ag_features
        self.terminated = False
        self.hf_features = 0  # Unknown

    def send_response_line(self, response: str) -> None:
        self.protocol.send_response_line(response)  # type: ignore

    async def serve(self) -> None:
        while not self.terminated:
            line = await self.protocol.next_line()  # type: ignore

            if line.startswith('AT+BRSF='):
                hf_features = int(line[len('AT+BRSF=') :])
                self.on_brsf(hf_features)
            elif line.startswith('AT+BIND=?'):
                self.on_bind_read_capabilities()
            elif line.startswith('AT+BIND='):
                indicators = [int(i) for i in line[len('AT+BIND=') :].split(',')]
                self.on_bind_list(indicators)
            elif line.startswith('AT+BIND?'):
                self.on_bind_read_configuration()
            elif line.startswith('AT+CIND=?'):
                self.on_cind_read()
            elif line.startswith('AT+CIND?'):
                self.on_cind_test()
            # TODO(b/286226902): Implement handlers for these commands
            elif line.startswith(
                (
                    'AT+CLIP=',
                    'AT+VGS=',
                    'AT+BIA=',
                    'AT+CMER=',
                    'AT+XEVENT=',
                    'AT+XAPL=',
                )
            ):
                self.send_response_line('OK')
            else:
                self.send_response_line('ERROR')

    def on_brsf(self, hf_features: int) -> None:
        self.hf_features = hf_features
        self.send_response_line(f'+BRSF: {self.ag_features}')
        self.send_response_line('OK')

    # AT+CIND?
    def on_cind_read(self) -> None:
        self.send_response_line('+CIND: 0,0,1,4,1,5,0')
        self.send_response_line('OK')

    # AT+CIND=?
    def on_cind_test(self) -> None:
        self.send_response_line(
            '+CIND: ("call",(0,1)),("callsetup",(0-3)),("service",(0-1)),'
            '("signal",(0-5)),("roam",(0,1)),("battchg",(0-5)),'
            '("callheld",(0-2))'
        )
        self.send_response_line('OK')

    # AT+BIND=
    def on_bind_list(self, indicators: list[int]) -> None:
        self.enabled_hf_indicators = indicators[:]
        self.send_response_line('OK')

    # AT+BIND=?
    def on_bind_read_capabilities(self) -> None:
        self.send_response_line('+BIND: ' + ','.join(map(str, self.enabled_hf_indicators)))
        self.send_response_line('OK')

    # AT+BIND?
    def on_bind_read_configuration(self) -> None:
        for i in self.enabled_hf_indicators:
            self.send_response_line(f'+BIND: {i},1')
        self.send_response_line('OK')


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    test_runner.main()  # type: ignore
