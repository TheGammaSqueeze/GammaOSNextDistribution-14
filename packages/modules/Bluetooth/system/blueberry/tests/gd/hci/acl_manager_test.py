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

from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.py_acl_manager import PyAclManager
from blueberry.tests.gd.cert.truth import assertThat
from datetime import timedelta
from blueberry.facade.neighbor import facade_pb2 as neighbor_facade
from mobly import test_runner
import hci_packets as hci


class AclManagerTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI')

    # todo: move into GdBaseTestClass, based on modules inited
    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.cert_hci = PyHci(self.cert, acl_streaming=True)
        self.dut_acl_manager = PyAclManager(self.dut)

    def teardown_test(self):
        self.cert_hci.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def test_dut_connects(self):
        self.cert_hci.enable_inquiry_and_page_scan()
        cert_address = self.cert_hci.read_own_address()

        self.dut_acl_manager.initiate_connection(repr(cert_address))
        cert_acl = self.cert_hci.accept_connection()
        with self.dut_acl_manager.complete_outgoing_connection() as dut_acl:
            cert_acl.send_first(b'\x26\x00\x07\x00This is just SomeAclData from the Cert')
            dut_acl.send(b'\x29\x00\x07\x00This is just SomeMoreAclData from the DUT')

            assertThat(cert_acl).emits(lambda packet: b'SomeMoreAclData' in packet.payload)
            assertThat(dut_acl).emits(lambda packet: b'SomeAclData' in packet.payload)

    def test_cert_connects(self):
        dut_address = self.dut.hci_controller.GetMacAddressSimple()
        self.dut.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))

        self.dut_acl_manager.listen_for_an_incoming_connection()
        self.cert_hci.initiate_connection(dut_address)
        with self.dut_acl_manager.complete_incoming_connection() as dut_acl:
            cert_acl = self.cert_hci.complete_connection()

            dut_acl.send(b'\x29\x00\x07\x00This is just SomeMoreAclData from the DUT')

            cert_acl.send_first(b'\x26\x00\x07\x00This is just SomeAclData from the Cert')

            assertThat(cert_acl).emits(lambda packet: b'SomeMoreAclData' in packet.payload)
            assertThat(dut_acl).emits(lambda packet: b'SomeAclData' in packet.payload)

    def test_reject_broadcast(self):
        dut_address = self.dut.hci_controller.GetMacAddressSimple()
        self.dut.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))

        self.dut_acl_manager.listen_for_an_incoming_connection()
        self.cert_hci.initiate_connection(dut_address)
        with self.dut_acl_manager.complete_incoming_connection() as dut_acl:
            cert_acl = self.cert_hci.complete_connection()

            cert_acl.send(hci.PacketBoundaryFlag.FIRST_AUTOMATICALLY_FLUSHABLE,
                          hci.BroadcastFlag.ACTIVE_PERIPHERAL_BROADCAST,
                          b'\x26\x00\x07\x00This is a Broadcast from the Cert')
            assertThat(dut_acl).emitsNone(timeout=timedelta(seconds=0.5))

            cert_acl.send(hci.PacketBoundaryFlag.FIRST_AUTOMATICALLY_FLUSHABLE, hci.BroadcastFlag.POINT_TO_POINT,
                          b'\x26\x00\x07\x00This is just SomeAclData from the Cert')
            assertThat(dut_acl).emits(lambda packet: b'SomeAclData' in packet.payload)

    def test_cert_connects_disconnects(self):
        dut_address = self.dut.hci_controller.GetMacAddressSimple()
        self.dut.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))

        self.dut_acl_manager.listen_for_an_incoming_connection()
        self.cert_hci.initiate_connection(dut_address)
        with self.dut_acl_manager.complete_incoming_connection() as dut_acl:
            cert_acl = self.cert_hci.complete_connection()

            dut_acl.send(b'\x29\x00\x07\x00This is just SomeMoreAclData from the DUT')

            cert_acl.send_first(b'\x26\x00\x07\x00This is just SomeAclData from the Cert')

            assertThat(cert_acl).emits(lambda packet: b'SomeMoreAclData' in packet.payload)
            assertThat(dut_acl).emits(lambda packet: b'SomeAclData' in packet.payload)

            dut_acl.disconnect(hci.DisconnectReason.REMOTE_USER_TERMINATED_CONNECTION)
            dut_acl.wait_for_disconnection_complete()

    def test_recombination_l2cap_packet(self):
        self.cert_hci.enable_inquiry_and_page_scan()
        cert_address = self.cert_hci.read_own_address()

        self.dut_acl_manager.initiate_connection(repr(cert_address))
        cert_acl = self.cert_hci.accept_connection()
        with self.dut_acl_manager.complete_outgoing_connection() as dut_acl:
            cert_acl.send_first(b'\x06\x00\x07\x00Hello')
            cert_acl.send_continuing(b'!')
            cert_acl.send_first(b'\xe8\x03\x07\x00' + b'Hello' * 200)

            assertThat(dut_acl).emits(lambda packet: b'Hello!' in packet.payload,
                                      lambda packet: b'Hello' * 200 in packet.payload).inOrder()


if __name__ == '__main__':
    test_runner.main()
