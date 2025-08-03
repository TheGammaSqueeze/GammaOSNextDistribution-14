#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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

from google.protobuf import empty_pb2 as empty_proto
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.event_stream import IEventStream
from blueberry.tests.gd.cert.captures import HciCaptures
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.facade.hci import acl_manager_facade_pb2 as acl_manager_facade
from blueberry.utils import bluetooth
import hci_packets as hci


class PyAclManagerAclConnection(IEventStream, Closable):

    def __init__(self, acl_manager, remote_addr, handle, event_stream):
        self.acl_manager = acl_manager
        self.handle = handle
        self.remote_addr = remote_addr
        self.connection_event_stream = event_stream
        self.acl_stream = EventStream(self.acl_manager.FetchAclData(acl_manager_facade.HandleMsg(handle=self.handle)))

    def disconnect(self, reason):
        packet_bytes = hci.Disconnect(connection_handle=self.handle, reason=reason).serialize()
        self.acl_manager.ConnectionCommand(acl_manager_facade.ConnectionCommandMsg(packet=packet_bytes))

    def close(self):
        safeClose(self.connection_event_stream)
        safeClose(self.acl_stream)

    def wait_for_disconnection_complete(self):
        disconnection_complete = HciCaptures.DisconnectionCompleteCapture()
        assertThat(self.connection_event_stream).emits(disconnection_complete)
        self.disconnect_reason = disconnection_complete.get().reason

    def send(self, data):
        self.acl_manager.SendAclData(acl_manager_facade.AclData(handle=self.handle, payload=bytes(data)))

    def get_event_queue(self):
        return self.acl_stream.get_event_queue()


class PyAclManager:

    def __init__(self, device):
        self.acl_manager = device.hci_acl_manager
        self.incoming_connection_event_stream = None
        self.outgoing_connection_event_stream = None

    def close(self):
        safeClose(self.incoming_connection_event_stream)
        safeClose(self.outgoing_connection_event_stream)

    def listen_for_an_incoming_connection(self):
        assertThat(self.incoming_connection_event_stream).isNone()
        self.incoming_connection_event_stream = EventStream(
            self.acl_manager.FetchIncomingConnection(empty_proto.Empty()))

    def initiate_connection(self, remote_addr):
        assertThat(self.outgoing_connection_event_stream).isNone()
        remote_addr_bytes = remote_addr if isinstance(remote_addr, bytes) else remote_addr.encode('utf-8')
        self.outgoing_connection_event_stream = EventStream(
            self.acl_manager.CreateConnection(acl_manager_facade.ConnectionMsg(address=remote_addr_bytes)))

    def complete_connection(self, event_stream):
        connection_complete = HciCaptures.ConnectionCompleteCapture()
        assertThat(event_stream).emits(connection_complete)
        complete = connection_complete.get()
        handle = complete.connection_handle
        address = repr(complete.bd_addr)
        return PyAclManagerAclConnection(self.acl_manager, address, handle, event_stream)

    def complete_incoming_connection(self):
        assertThat(self.incoming_connection_event_stream).isNotNone()
        event_stream = self.incoming_connection_event_stream
        self.incoming_connection_event_stream = None
        return self.complete_connection(event_stream)

    def complete_outgoing_connection(self):
        assertThat(self.outgoing_connection_event_stream).isNotNone()
        event_stream = self.outgoing_connection_event_stream
        self.outgoing_connection_event_stream = None
        return self.complete_connection(event_stream)
