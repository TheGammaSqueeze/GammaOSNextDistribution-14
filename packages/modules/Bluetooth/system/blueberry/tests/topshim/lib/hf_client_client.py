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


class HfClientClient(AsyncClosable):
    """
    Wrapper gRPC interface to the HF Client Service
    """
    __channel = None
    __hf_client_stub = None

    def __init__(self, port=8999):
        self.__channel = grpc.aio.insecure_channel("localhost:%d" % port)
        self.__hf_client_stub = facade_pb2_grpc.HfClientServiceStub(self.__channel)

    async def close(self):
        await self.__channel.close()

    async def start_slc(self, address):
        """
        """
        await self.__hf_client_stub.StartSlc(
            facade_pb2.StartSlcRequest(connection=facade_pb2.Connection(cookie=address.encode())))

    async def stop_slc(self, address):
        """
        """
        await self.__hf_client_stub.StopSlc(
            facade_pb2.StopSlcRequest(connection=facade_pb2.Connection(cookie=address.encode())))

    async def connect_audio(self, address):
        """
        """
        await self.__hf_client_stub.ConnectAudio(
            facade_pb2.ConnectAudioRequest(connection=facade_pb2.Connection(cookie=address.encode())))

    async def disconnect_audio(self, address):
        """
        """
        await self.__hf_client_stub.DisconnectAudio(
            facade_pb2.DisconnectAudioRequest(connection=facade_pb2.Connection(cookie=address.encode())))
