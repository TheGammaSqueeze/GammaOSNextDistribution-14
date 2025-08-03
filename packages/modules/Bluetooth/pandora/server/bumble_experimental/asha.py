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
import grpc
import logging
import struct

from bumble.core import AdvertisingData
from bumble.decoder import G722Decoder
from bumble.device import Connection, Connection as BumbleConnection, Device
from bumble.gatt import (
    GATT_ASHA_AUDIO_CONTROL_POINT_CHARACTERISTIC,
    GATT_ASHA_AUDIO_STATUS_CHARACTERISTIC,
    GATT_ASHA_LE_PSM_OUT_CHARACTERISTIC,
    GATT_ASHA_READ_ONLY_PROPERTIES_CHARACTERISTIC,
    GATT_ASHA_SERVICE,
    GATT_ASHA_VOLUME_CHARACTERISTIC,
    Characteristic,
    CharacteristicValue,
    TemplateService,
)
from bumble.l2cap import Channel
from bumble.pandora import utils
from bumble.utils import AsyncRunner
from google.protobuf.empty_pb2 import Empty  # pytype: disable=pyi-error
from pandora_experimental.asha_grpc_aio import AshaServicer
from pandora_experimental.asha_pb2 import CaptureAudioRequest, CaptureAudioResponse, RegisterRequest
from typing import AsyncGenerator, List, Optional


class AshaGattService(TemplateService):
    # TODO: update bumble and remove this when complete
    UUID = GATT_ASHA_SERVICE
    OPCODE_START = 1
    OPCODE_STOP = 2
    OPCODE_STATUS = 3
    PROTOCOL_VERSION = 0x01
    RESERVED_FOR_FUTURE_USE = [00, 00]
    FEATURE_MAP = [0x01]  # [LE CoC audio output streaming supported]
    SUPPORTED_CODEC_ID = [0x02, 0x01]  # Codec IDs [G.722 at 16 kHz]
    RENDER_DELAY = [00, 00]

    def __init__(self, capability: int, hisyncid: List[int], device: Device, psm: int = 0) -> None:
        self.hisyncid = hisyncid
        self.capability = capability  # Device Capabilities [Left, Monaural]
        self.device = device
        self.audio_out_data = b""
        self.psm: int = psm  # a non-zero psm is mainly for testing purpose

        logger = logging.getLogger(__name__)

        # Handler for volume control
        def on_volume_write(connection: Connection, value: bytes) -> None:
            logger.info(f"--- VOLUME Write:{value[0]}")
            self.emit("volume", connection, value[0])

        # Handler for audio control commands
        def on_audio_control_point_write(connection: Connection, value: bytes) -> None:
            logger.info(f"type {type(value)}")
            logger.info(f"--- AUDIO CONTROL POINT Write:{value.hex()}")
            opcode = value[0]
            if opcode == AshaGattService.OPCODE_START:
                # Start
                audio_type = ("Unknown", "Ringtone", "Phone Call", "Media")[value[2]]
                logger.info(
                    f"### START: codec={value[1]}, "
                    f"audio_type={audio_type}, "
                    f"volume={value[3]}, "
                    f"otherstate={value[4]}"
                )
                self.emit(
                    "start",
                    connection,
                    {
                        "codec": value[1],
                        "audiotype": value[2],
                        "volume": value[3],
                        "otherstate": value[4],
                    },
                )
            elif opcode == AshaGattService.OPCODE_STOP:
                logger.info("### STOP")
                self.emit("stop", connection)
            elif opcode == AshaGattService.OPCODE_STATUS:
                logger.info(f"### STATUS: connected={value[1]}")

            # OPCODE_STATUS does not need audio status point update
            if opcode != AshaGattService.OPCODE_STATUS:
                AsyncRunner.spawn(device.notify_subscribers(self.audio_status_characteristic, force=True))  # type: ignore[no-untyped-call]

        def on_read_only_properties_read(connection: Connection) -> bytes:
            value = (
                bytes(
                    [
                        AshaGattService.PROTOCOL_VERSION,  # Version
                        self.capability,
                    ]
                )
                + bytes(self.hisyncid)
                + bytes(AshaGattService.FEATURE_MAP)
                + bytes(AshaGattService.RENDER_DELAY)
                + bytes(AshaGattService.RESERVED_FOR_FUTURE_USE)
                + bytes(AshaGattService.SUPPORTED_CODEC_ID)
            )
            self.emit("read_only_properties", connection, value)
            return value

        def on_le_psm_out_read(connection: Connection) -> bytes:
            self.emit("le_psm_out", connection, self.psm)
            return struct.pack("<H", self.psm)

        self.read_only_properties_characteristic = Characteristic(
            GATT_ASHA_READ_ONLY_PROPERTIES_CHARACTERISTIC,
            Characteristic.READ,
            Characteristic.READABLE,
            CharacteristicValue(read=on_read_only_properties_read),  # type: ignore[no-untyped-call]
        )

        self.audio_control_point_characteristic = Characteristic(
            GATT_ASHA_AUDIO_CONTROL_POINT_CHARACTERISTIC,
            Characteristic.WRITE | Characteristic.WRITE_WITHOUT_RESPONSE,
            Characteristic.WRITEABLE,
            CharacteristicValue(write=on_audio_control_point_write),  # type: ignore[no-untyped-call]
        )
        self.audio_status_characteristic = Characteristic(
            GATT_ASHA_AUDIO_STATUS_CHARACTERISTIC,
            Characteristic.READ | Characteristic.NOTIFY,
            Characteristic.READABLE,
            bytes([0]),
        )
        self.volume_characteristic = Characteristic(
            GATT_ASHA_VOLUME_CHARACTERISTIC,
            Characteristic.WRITE_WITHOUT_RESPONSE,
            Characteristic.WRITEABLE,
            CharacteristicValue(write=on_volume_write),  # type: ignore[no-untyped-call]
        )

        # Register an L2CAP CoC server
        def on_coc(channel: Channel) -> None:
            def on_data(data: bytes) -> None:
                logging.debug(f"data received:{data.hex()}")

                self.emit("data", channel.connection, data)
                self.audio_out_data += data

            channel.sink = on_data  # type: ignore[no-untyped-call]

        # let the server find a free PSM
        self.psm = self.device.register_l2cap_channel_server(self.psm, on_coc, 8)  # type: ignore[no-untyped-call]
        self.le_psm_out_characteristic = Characteristic(
            GATT_ASHA_LE_PSM_OUT_CHARACTERISTIC,
            Characteristic.READ,
            Characteristic.READABLE,
            CharacteristicValue(read=on_le_psm_out_read),  # type: ignore[no-untyped-call]
        )

        characteristics = [
            self.read_only_properties_characteristic,
            self.audio_control_point_characteristic,
            self.audio_status_characteristic,
            self.volume_characteristic,
            self.le_psm_out_characteristic,
        ]

        super().__init__(characteristics)  # type: ignore[no-untyped-call]

    def get_advertising_data(self) -> bytes:
        # Advertisement only uses 4 least significant bytes of the HiSyncId.
        return bytes(
            AdvertisingData(
                [
                    (
                        AdvertisingData.SERVICE_DATA_16_BIT_UUID,
                        bytes(GATT_ASHA_SERVICE)
                        + bytes(
                            [
                                AshaGattService.PROTOCOL_VERSION,
                                self.capability,
                            ]
                        )
                        + bytes(self.hisyncid[:4]),
                    ),
                ]
            )
        )


class AshaService(AshaServicer):
    DECODE_FRAME_LENGTH = 80

    device: Device
    asha_service: Optional[AshaGattService]

    def __init__(self, device: Device) -> None:
        self.log = utils.BumbleServerLoggerAdapter(logging.getLogger(), {"service_name": "Asha", "device": device})
        self.device = device
        self.asha_service = None

    @utils.rpc
    async def Register(self, request: RegisterRequest, context: grpc.ServicerContext) -> Empty:
        logging.info("Register")
        if self.asha_service:
            self.asha_service.capability = request.capability
            self.asha_service.hisyncid = request.hisyncid
        else:
            self.asha_service = AshaGattService(request.capability, request.hisyncid, self.device)
            self.device.add_service(self.asha_service)  # type: ignore[no-untyped-call]
        return Empty()

    @utils.rpc
    async def CaptureAudio(
        self, request: CaptureAudioRequest, context: grpc.ServicerContext
    ) -> AsyncGenerator[CaptureAudioResponse, None]:
        connection_handle = int.from_bytes(request.connection.cookie.value, "big")
        logging.info(f"CaptureAudioData connection_handle:{connection_handle}")

        if not (connection := self.device.lookup_connection(connection_handle)):
            raise RuntimeError(f"Unknown connection for connection_handle:{connection_handle}")

        decoder = G722Decoder()  # type: ignore
        queue: asyncio.Queue[bytes] = asyncio.Queue()

        def on_data(asha_connection: BumbleConnection, data: bytes) -> None:
            if asha_connection == connection:
                queue.put_nowait(data)

        self.asha_service.on("data", on_data)  # type: ignore

        try:
            while data := await queue.get():
                output_bytes = bytearray()
                # First byte is sequence number, last 160 bytes are audio payload.
                audio_payload = data[1:]
                data_length = int(len(audio_payload) / AshaService.DECODE_FRAME_LENGTH)
                for i in range(0, data_length):
                    input_data = audio_payload[
                        i * AshaService.DECODE_FRAME_LENGTH : i * AshaService.DECODE_FRAME_LENGTH
                        + AshaService.DECODE_FRAME_LENGTH
                    ]
                    decoded_data = decoder.decode_frame(input_data)  # type: ignore
                    output_bytes.extend(decoded_data)

                yield CaptureAudioResponse(data=bytes(output_bytes))
        finally:
            self.asha_service.remove_listener("data", on_data)  # type: ignore
