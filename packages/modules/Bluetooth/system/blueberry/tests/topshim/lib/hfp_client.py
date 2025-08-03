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

from google.protobuf import empty_pb2 as empty_proto


class HfpClient(AsyncClosable):
    """
    Wrapper gRPC interface to the HFP Service
    """
    # Timeout for async wait
    DEFAULT_TIMEOUT = 2
    __task_list = []
    __channel = None
    __hfp_stub = None
    __hfp_event_stream = None

    def __init__(self, port=8999):
        self.__channel = grpc.aio.insecure_channel("localhost:%d" % port)
        self.__hfp_stub = facade_pb2_grpc.HfpServiceStub(self.__channel)
        self.__hfp_event_stream = self.__hfp_stub.FetchEvents(facade_pb2.FetchEventsRequest())

    async def close(self):
        """
        Terminate the current tasks.
        """
        for task in self.__task_list:
            task.cancel()
            task = None
        self.__task_list.clear()
        await self.__channel.close()

    async def start_slc(self, address):
        """
        """
        await self.__hfp_stub.StartSlc(
            facade_pb2.StartSlcRequest(connection=facade_pb2.Connection(cookie=address.encode())))
        return await self._listen_for_event(facade_pb2.EventType.HFP_CONNECTION_STATE)

    async def stop_slc(self, address):
        """
        """
        await self.__hfp_stub.StopSlc(
            facade_pb2.StopSlcRequest(connection=facade_pb2.Connection(cookie=address.encode())))
        return await self._listen_for_event(facade_pb2.EventType.HFP_CONNECTION_STATE)

    async def connect_audio(self, address, is_sco_offload_enabled=False, disabled_codecs=0):
        """
        """
        await self.__hfp_stub.ConnectAudio(
            facade_pb2.ConnectAudioRequest(connection=facade_pb2.Connection(cookie=address.encode()),
                                           is_sco_offload_enabled=is_sco_offload_enabled,
                                           disabled_codecs=disabled_codecs))

    async def disconnect_audio(self, address):
        """
        """
        await self.__hfp_stub.DisconnectAudio(
            facade_pb2.DisconnectAudioRequest(connection=facade_pb2.Connection(cookie=address.encode())))

    async def set_volume(self, address, volume):
        """
        """
        await self.__hfp_stub.DisconnectAudio(
            facade_pb2.DisconnectAudioRequest(connection=facade_pb2.Connection(cookie=address.encode()), volume=volume))

    async def wait_for_hfp_connection_state_change(self):
        return await self._listen_for_event(facade_pb2.EventType.HFP_CONNECTION_STATE)

    async def __get_next_event(self, event, future):
        """Get the future of next event from the stream"""
        while True:
            e = await self.__hfp_event_stream.read()

            # Match event by some condition.
            if e.event_type == event:
                future.set_result(e.data)
                break
            else:
                print("Got '%s'; expecting '%s'" % (e.event_type, event))
                print(e)

    async def _listen_for_event(self, event):
        """Start fetching events"""
        future = asyncio.get_running_loop().create_future()
        self.__task_list.append(asyncio.get_running_loop().create_task(self.__get_next_event(event, future)))
        try:
            await asyncio.wait_for(future, HfpClient.DEFAULT_TIMEOUT)
        except:
            print("Failed to get event", event)
        return future
