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
from datetime import timedelta
from blueberry.facade.hci import le_acl_manager_facade_pb2 as le_acl_manager_facade
import hci_packets as hci


class PyLeAclManagerAclConnection(IEventStream, Closable):

    def __init__(self, le_acl_manager, address, remote_addr, remote_addr_type, handle, event_stream):
        """
        An abstract representation for an LE ACL connection in GD certification test
        :param le_acl_manager: The LeAclManager from this GD device
        :param address: The local device address
        :param remote_addr: Remote device address
        :param remote_addr_type: Remote device address type
        :param handle: Connection handle
        :param event_stream: The connection event stream for this connection
        """
        self.le_acl_manager = le_acl_manager
        # todo enable filtering after sorting out handles
        # self.our_acl_stream = FilteringEventStream(acl_stream, None)
        self.handle = handle
        self.connection_event_stream = event_stream
        self.acl_stream = EventStream(
            self.le_acl_manager.FetchAclData(le_acl_manager_facade.LeHandleMsg(handle=self.handle)))
        self.remote_address = remote_addr
        self.remote_address_type = remote_addr_type
        self.own_address = address
        self.disconnect_reason = None

    def close(self):
        safeClose(self.connection_event_stream)
        safeClose(self.acl_stream)

    def disconnect(self):
        self.le_acl_manager.Disconnect(le_acl_manager_facade.LeHandleMsg(handle=self.handle))

    def wait_for_disconnection_complete(self, timeout=timedelta(seconds=30)):
        disconnection_complete = HciCaptures.DisconnectionCompleteCapture()
        assertThat(self.connection_event_stream).emits(disconnection_complete, timeout=timeout)
        self.disconnect_reason = disconnection_complete.get().reason

    def send(self, data):
        self.le_acl_manager.SendAclData(le_acl_manager_facade.LeAclData(handle=self.handle, payload=bytes(data)))

    def get_event_queue(self):
        return self.acl_stream.get_event_queue()


class PyLeAclManager(Closable):

    def __init__(self, device):
        """
        LE ACL Manager for GD Certification test
        :param device: The GD device
        """
        self.le_acl_manager = device.hci_le_acl_manager

        self.incoming_connection_event_stream = None
        self.outgoing_connection_event_streams = {}
        self.active_connections = []
        self.next_token = 1

    def close(self):
        safeClose(self.incoming_connection_event_stream)
        for v in self.outgoing_connection_event_streams.values():
            safeClose(v[0])
        for connection in self.active_connections:
            safeClose(connection)

    def listen_for_incoming_connections(self):
        assertThat(self.incoming_connection_event_stream).isNone()
        self.incoming_connection_event_stream = EventStream(
            self.le_acl_manager.FetchIncomingConnection(empty_proto.Empty()))

    def connect_to_remote(self, remote_addr):
        token = self.initiate_connection(remote_addr)
        return self.complete_outgoing_connection(token)

    def wait_for_connection(self):
        self.listen_for_incoming_connections()
        return self.complete_incoming_connection()

    def wait_for_connection_fail(self, token):
        assertThat(self.outgoing_connection_event_streams[token]).isNotNone()
        event_stream = self.outgoing_connection_event_streams[token][0]
        connection_fail = HciCaptures.LeConnectionCompleteCapture()
        assertThat(event_stream).emits(connection_fail, timeout=timedelta(seconds=35))
        complete = connection_fail.get()
        assertThat(complete.status == hci.ErrorCode.CONNECTION_ACCEPT_TIMEOUT).isTrue()

    def cancel_connection(self, token):
        assertThat(token in self.outgoing_connection_event_streams).isTrue()
        pair = self.outgoing_connection_event_streams.pop(token)
        safeClose(pair[0])
        self.le_acl_manager.CancelConnection(pair[1])

    def initiate_connection(self, remote_addr, is_direct=True):
        assertThat(self.next_token in self.outgoing_connection_event_streams).isFalse()
        create_connection_msg = le_acl_manager_facade.CreateConnectionMsg(peer_address=remote_addr, is_direct=is_direct)
        self.outgoing_connection_event_streams[self.next_token] = EventStream(
            self.le_acl_manager.CreateConnection(create_connection_msg)), remote_addr
        token = self.next_token
        self.next_token += 1
        return token

    def is_on_background_list(self, remote_addr):
        return self.le_acl_manager.IsOnBackgroundList(
            le_acl_manager_facade.BackgroundRequestMsg(peer_address=remote_addr))

    def remove_from_background_list(self, remote_addr):
        self.le_acl_manager.RemoveFromBackgroundList(
            le_acl_manager_facade.BackgroundRequestMsg(peer_address=remote_addr))

    def complete_connection(self, event_stream):
        connection_complete = HciCaptures.LeConnectionCompleteCapture()
        assertThat(event_stream).emits(connection_complete)
        complete = connection_complete.get()
        handle = complete.connection_handle
        remote = repr(complete.peer_address)
        remote_address_type = complete.peer_address_type
        if complete.subevent_code == hci.SubeventCode.ENHANCED_CONNECTION_COMPLETE:
            address = complete.local_resolvable_private_address
        else:
            address = None
        connection = PyLeAclManagerAclConnection(self.le_acl_manager, address, remote, remote_address_type, handle,
                                                 event_stream)
        self.active_connections.append(connection)
        return connection

    def complete_incoming_connection(self):
        assertThat(self.incoming_connection_event_stream).isNotNone()
        event_stream = self.incoming_connection_event_stream
        self.incoming_connection_event_stream = None
        return self.complete_connection(event_stream)

    def complete_outgoing_connection(self, token):
        assertThat(self.outgoing_connection_event_streams[token]).isNotNone()
        event_stream = self.outgoing_connection_event_streams.pop(token)[0]
        return self.complete_connection(event_stream)
