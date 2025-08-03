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
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_hci import PyHci, PyHciAdvertisement
from blueberry.tests.gd.cert.py_le_acl_manager import PyLeAclManager
from blueberry.facade import common_pb2 as common
from blueberry.facade.hci import le_acl_manager_facade_pb2 as le_acl_manager_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.hci import hci_facade_pb2 as hci_facade
from bluetooth_packets_python3 import RawBuilder
from mobly import test_runner
import hci_packets as hci


class LeAclManagerTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.cert_hci = PyHci(self.cert, acl_streaming=True)
        self.dut_le_acl_manager = PyLeAclManager(self.dut)
        self.cert_public_address = self.cert_hci.read_own_address()
        self.dut_public_address = self.dut.hci_controller.GetMacAddressSimple().decode("utf-8")
        self.dut_random_address = 'd0:05:04:03:02:01'
        self.cert_random_address = 'c0:05:04:03:02:01'

    def teardown_test(self):
        safeClose(self.dut_le_acl_manager)
        self.cert_hci.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def set_privacy_policy_static(self):
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(self.dut_random_address, "utf-8")),
                type=common.RANDOM_DEVICE_ADDRESS))
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)

    def register_for_event(self, event_code):
        msg = hci_facade.EventRequest(code=int(event_code))
        self.cert.hci.RequestEvent(msg)

    def register_for_le_event(self, event_code):
        msg = hci_facade.EventRequest(code=int(event_code))
        self.cert.hci.RequestLeSubevent(msg)

    def enqueue_hci_command(self, command):
        cmd_bytes = bytes(command.serialize())
        cmd = common.Data(payload=cmd_bytes)
        self.cert.hci.SendCommand(cmd)

    def enqueue_acl_data(self, handle, pb_flag, b_flag, data):
        acl = hci.Acl(handle=handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.cert.hci.SendAcl(common.Data(payload=acl.serialize()))

    def dut_connects(self):
        # Cert Advertises
        advertising_handle = 0
        py_hci_adv = PyHciAdvertisement(advertising_handle, self.cert_hci)

        self.cert_hci.create_advertisement(
            advertising_handle,
            self.cert_random_address,
            hci.LegacyAdvertisingEventProperties.ADV_IND,
        )

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        dut_le_acl = self.dut_le_acl_manager.connect_to_remote(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=bytes(self.cert_random_address, 'utf8')),
                                                        type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)))

        cert_le_acl = self.cert_hci.incoming_le_connection()
        return dut_le_acl, cert_le_acl

    def cert_advertises_resolvable(self):
        self.cert_hci.add_device_to_resolving_list(hci.PeerAddressType.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS,
                                                   self.dut_public_address,
                                                   b'\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f',
                                                   b'\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f')

        # Cert Advertises
        advertising_handle = 0
        py_hci_adv = PyHciAdvertisement(advertising_handle, self.cert_hci)

        self.cert_hci.create_advertisement(advertising_handle,
                                           self.cert_random_address,
                                           hci.LegacyAdvertisingEventProperties.ADV_IND,
                                           own_address_type=hci.OwnAddressType.RESOLVABLE_OR_PUBLIC_ADDRESS,
                                           peer_address=self.dut_public_address,
                                           peer_address_type=hci.PeerAddressType.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

    def dut_connects_cert_resolvable(self):
        self.dut.hci_le_acl_manager.AddDeviceToResolvingList(
            le_acl_manager_facade.IrkMsg(
                peer=common.BluetoothAddressWithType(
                    address=common.BluetoothAddress(address=repr(self.cert_public_address).encode('utf-8')),
                    type=int(hci.AddressType.PUBLIC_DEVICE_ADDRESS)),
                peer_irk=b'\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f',
                local_irk=b'\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f',
            ))

        dut_le_acl = self.dut_le_acl_manager.connect_to_remote(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=repr(self.cert_public_address).encode('utf-8')),
                                                        type=int(hci.AddressType.PUBLIC_DEVICE_ADDRESS)))

        cert_le_acl = self.cert_hci.incoming_le_connection()
        return dut_le_acl, cert_le_acl

    def send_receive_and_check(self, dut_le_acl, cert_le_acl):
        self.enqueue_acl_data(cert_le_acl.handle, hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                              hci.BroadcastFlag.POINT_TO_POINT, bytes(b'\x19\x00\x07\x00SomeAclData from the Cert'))

        dut_le_acl.send(b'\x1C\x00\x07\x00SomeMoreAclData from the DUT')
        assertThat(cert_le_acl.our_acl_stream).emits(lambda packet: b'SomeMoreAclData' in packet.payload)
        assertThat(dut_le_acl).emits(lambda packet: b'SomeAclData' in packet.payload)

    def test_dut_connects(self):
        self.set_privacy_policy_static()
        dut_le_acl, cert_le_acl = self.dut_connects()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isEqualTo(self.dut_random_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_dut_connects_resolvable_address(self):
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_RESOLVABLE_ADDRESS,
            rotation_irk=b'\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f',
            minimum_rotation_time=7 * 60 * 1000,
            maximum_rotation_time=15 * 60 * 1000)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)
        dut_le_acl, cert_le_acl = self.dut_connects()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_public_address)
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_random_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_dut_connects_resolvable_address_public(self):
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_RESOLVABLE_ADDRESS,
            rotation_irk=b'\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f',
            minimum_rotation_time=7 * 60 * 1000,
            maximum_rotation_time=15 * 60 * 1000)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)
        self.cert_advertises_resolvable()
        dut_le_acl, cert_le_acl = self.dut_connects_cert_resolvable()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_public_address)
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_random_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(repr(self.cert_public_address))
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.PUBLIC_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_dut_connects_non_resolvable_address(self):
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_NON_RESOLVABLE_ADDRESS,
            rotation_irk=b'\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f',
            minimum_rotation_time=8 * 60 * 1000,
            maximum_rotation_time=14 * 60 * 1000)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)
        dut_le_acl, cert_le_acl = self.dut_connects()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_public_address)
        assertThat(cert_le_acl.peer).isNotEqualTo(self.dut_random_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_dut_connects_public_address(self):
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(
            le_initiator_address_facade.PrivacyPolicy(
                address_policy=le_initiator_address_facade.AddressPolicy.USE_PUBLIC_ADDRESS))
        dut_le_acl, cert_le_acl = self.dut_connects()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isEqualTo(self.dut_public_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.PUBLIC_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_dut_connects_public_address_cancelled(self):
        # TODO (Add cancel)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(
            le_initiator_address_facade.PrivacyPolicy(
                address_policy=le_initiator_address_facade.AddressPolicy.USE_PUBLIC_ADDRESS))
        dut_le_acl, cert_le_acl = self.dut_connects()

        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isEqualTo(self.dut_public_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.PUBLIC_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_cert_connects(self):
        self.set_privacy_policy_static()
        self.dut_le_acl_manager.listen_for_incoming_connections()

        # DUT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            peer_address_type=common.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS,
            peer_address=common.BluetoothAddress(address=bytes(b'A6:A5:A4:A3:A2:A1')),
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)

        self.dut.hci_le_advertising_manager.CreateAdvertiser(request)

        # Cert Connects
        self.cert_hci.set_random_le_address(self.cert_random_address)
        self.cert_hci.initiate_le_connection(self.dut_random_address)

        # Cert gets ConnectionComplete with a handle and sends ACL data
        cert_le_acl = self.cert_hci.incoming_le_connection()

        cert_le_acl.send(hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE, hci.BroadcastFlag.POINT_TO_POINT,
                         b'\x19\x00\x07\x00SomeAclData from the Cert')

        # DUT gets a connection complete event and sends and receives
        dut_le_acl = self.dut_le_acl_manager.complete_incoming_connection()
        assertThat(cert_le_acl.handle).isNotNone()
        assertThat(cert_le_acl.peer).isEqualTo(self.dut_random_address)
        assertThat(cert_le_acl.peer_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        assertThat(dut_le_acl.handle).isNotNone()
        assertThat(dut_le_acl.remote_address).isEqualTo(self.cert_random_address)
        assertThat(dut_le_acl.remote_address_type).isEqualTo(hci.AddressType.RANDOM_DEVICE_ADDRESS)

        self.send_receive_and_check(dut_le_acl, cert_le_acl)

    def test_recombination_l2cap_packet(self):
        self.set_privacy_policy_static()
        dut_le_acl, cert_le_acl = self.dut_connects()
        cert_handle = cert_le_acl.handle
        self.enqueue_acl_data(cert_handle, hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                              hci.BroadcastFlag.POINT_TO_POINT, bytes(b'\x06\x00\x07\x00Hello'))
        self.enqueue_acl_data(cert_handle, hci.PacketBoundaryFlag.CONTINUING_FRAGMENT, hci.BroadcastFlag.POINT_TO_POINT,
                              bytes(b'!'))

        assertThat(dut_le_acl).emits(lambda packet: b'Hello!' in packet.payload)

    def test_background_connection(self):
        self.set_privacy_policy_static()

        # Start background and direct connection
        token_direct = self.dut_le_acl_manager.initiate_connection(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=bytes('0C:05:04:03:02:02', 'utf8')),
                                                        type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)))

        token_background = self.dut_le_acl_manager.initiate_connection(remote_addr=common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(self.cert_random_address, 'utf8')),
            type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)),
                                                                       is_direct=False)

        # Wait for direct connection timeout
        self.dut_le_acl_manager.wait_for_connection_fail(token_direct)

        # Cert Advertises
        advertising_handle = 0

        py_hci_adv = self.cert_hci.create_advertisement(advertising_handle, self.cert_random_address,
                                                        hci.LegacyAdvertisingEventProperties.ADV_IND, 155, 165)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        # Check background connection complete
        self.dut_le_acl_manager.complete_outgoing_connection(token_background)

    def skip_flaky_test_multiple_background_connections(self):
        self.set_privacy_policy_static()

        # Start two background connections
        token_1 = self.dut_le_acl_manager.initiate_connection(remote_addr=common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(self.cert_random_address, 'utf8')),
            type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)),
                                                              is_direct=False)

        token_2 = self.dut_le_acl_manager.initiate_connection(remote_addr=common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes('0C:05:04:03:02:02', 'utf8')),
            type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)),
                                                              is_direct=False)

        # Cert Advertises
        advertising_handle = 0

        py_hci_adv = self.cert_hci.create_advertisement(advertising_handle, self.cert_random_address,
                                                        hci.LegacyAdvertisingEventProperties.ADV_IND, 155, 165)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        # First background connection completes
        connection = self.dut_le_acl_manager.complete_outgoing_connection(token_1)
        connection.close()

        # Cert Advertises again
        advertising_handle = 0

        py_hci_adv = self.cert_hci.create_advertisement(advertising_handle, '0C:05:04:03:02:02',
                                                        hci.LegacyAdvertisingEventProperties.ADV_IND, 155, 165)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        # Second background connection completes
        connection = self.dut_le_acl_manager.complete_outgoing_connection(token_2)
        connection.close()

    def test_direct_connection(self):
        self.set_privacy_policy_static()

        advertising_handle = 0
        py_hci_adv = self.cert_hci.create_advertisement(advertising_handle, self.cert_random_address,
                                                        hci.LegacyAdvertisingEventProperties.ADV_IND, 155, 165)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        # Start direct connection
        token = self.dut_le_acl_manager.initiate_connection(remote_addr=common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(self.cert_random_address, 'utf8')),
            type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)),
                                                            is_direct=True)
        self.dut_le_acl_manager.complete_outgoing_connection(token)

    def test_background_connection_list(self):
        self.set_privacy_policy_static()

        # Start background connection
        token_background = self.dut_le_acl_manager.initiate_connection(remote_addr=common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(self.cert_random_address, 'utf8')),
            type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)),
                                                                       is_direct=False)

        # Cert Advertises
        advertising_handle = 0

        py_hci_adv = self.cert_hci.create_advertisement(advertising_handle, self.cert_random_address,
                                                        hci.LegacyAdvertisingEventProperties.ADV_IND, 155, 165)

        py_hci_adv.set_data(b'Im_A_Cert')
        py_hci_adv.set_scan_response(b'Im_A_C')
        py_hci_adv.start()

        # Check background connection complete
        self.dut_le_acl_manager.complete_outgoing_connection(token_background)

        msg = self.dut_le_acl_manager.is_on_background_list(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=bytes(self.cert_random_address, 'utf8')),
                                                        type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)))
        assertThat(msg.is_on_background_list).isEqualTo(True)

        self.dut_le_acl_manager.remove_from_background_list(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=bytes(self.cert_random_address, 'utf8')),
                                                        type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)))

        msg = self.dut_le_acl_manager.is_on_background_list(
            remote_addr=common.BluetoothAddressWithType(address=common.BluetoothAddress(
                address=bytes(self.cert_random_address, 'utf8')),
                                                        type=int(hci.AddressType.RANDOM_DEVICE_ADDRESS)))
        assertThat(msg.is_on_background_list).isEqualTo(False)


if __name__ == '__main__':
    test_runner.main()
