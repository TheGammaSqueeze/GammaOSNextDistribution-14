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

import bluetooth_packets_python3 as bt_packets
from bluetooth_packets_python3 import l2cap_packets
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_l2cap import PyLeL2cap, PyL2cap
from blueberry.tests.gd.cert.matchers import L2capMatchers
from blueberry.tests.gd.cert.metadata import metadata
from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.l2cap.classic.cert_l2cap import CertL2cap
from blueberry.tests.gd.l2cap.le.cert_le_l2cap import CertLeL2cap
from blueberry.facade import common_pb2 as common
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import le_acl_manager_facade_pb2 as le_acl_manager_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.neighbor import facade_pb2 as neighbor_facade
from mobly import test_runner
import hci_packets as hci

# Assemble a sample packet.
SAMPLE_PACKET = bt_packets.RawBuilder([0x19, 0x26, 0x08, 0x17])


class DualL2capTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='L2CAP', cert_module='HCI_INTERFACES')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)

        self.dut_address = self.dut.hci_controller.GetMacAddressSimple()
        cert_address = common.BluetoothAddress(
            address=self.cert.controller_read_only_property.ReadLocalAddress(empty_proto.Empty()).address)

        self.dut_l2cap = PyL2cap(self.dut, cert_address)
        self.cert_l2cap = CertL2cap(self.cert)
        self.dut_le_l2cap = PyLeL2cap(self.dut)
        self.cert_le_l2cap = CertLeL2cap(self.cert)
        self.dut_le_address = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'D0:05:04:03:02:01')), type=common.RANDOM_DEVICE_ADDRESS)
        self.cert_address = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'C0:11:FF:AA:33:22')), type=common.RANDOM_DEVICE_ADDRESS)
        dut_privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=self.dut_le_address,
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.dut_l2cap._device.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(dut_privacy_policy)
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=self.cert_address,
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.cert_le_l2cap._device.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)

    def teardown_test(self):
        self.cert_le_l2cap.close()
        self.dut_le_l2cap.close()
        self.cert_l2cap.close()
        self.dut_l2cap.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def _setup_acl_link_from_cert(self):
        self.dut.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))
        self.cert_l2cap.connect_acl(self.dut_address)

    def _setup_le_link_from_cert(self):
        # DUT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        create_response = self.dut.hci_le_advertising_manager.CreateAdvertiser(request)
        self.cert_le_l2cap.connect_le_acl(self.dut_le_address)

    def _open_le_coc_from_dut(self, psm=0x33, our_scid=None):
        response_future = self.dut_le_l2cap.connect_coc_to_cert(self.cert_address, psm)
        cert_channel = self.cert_le_l2cap.verify_and_respond_open_channel_from_remote(psm=psm, our_scid=our_scid)
        dut_channel = response_future.get_channel()
        return (dut_channel, cert_channel)

    def _open_channel_from_dut(self, psm=0x33, our_scid=None):
        dut_channel_future = self.dut_l2cap.connect_dynamic_channel_to_cert(psm)
        cert_channel = self.cert_l2cap.verify_and_respond_open_channel_from_remote(psm=psm, scid=our_scid)
        dut_channel = dut_channel_future.get_channel()

        cert_channel.verify_configuration_request_and_respond()
        cert_channel.send_configure_request([])
        cert_channel.verify_configuration_response()

        return (dut_channel, cert_channel)

    def _open_unconfigured_channel_from_cert(self, signal_id=1, scid=0x0101, psm=0x33):

        dut_channel = self.dut_l2cap.register_dynamic_channel(psm)
        cert_channel = self.cert_l2cap.open_channel(signal_id, psm, scid)

        return (dut_channel, cert_channel)

    def _open_channel_from_cert(self, signal_id=1, scid=0x0101, psm=0x33):
        (dut_channel, cert_channel) = self._open_unconfigured_channel_from_cert(signal_id, scid, psm)
        cert_channel.verify_configuration_request_and_respond()
        cert_channel.send_configure_request([])
        cert_channel.verify_configuration_response()

        return (dut_channel, cert_channel)

    def _open_le_coc_from_cert(self, signal_id=1, scid=0x0101, psm=0x35, mtu=1000, mps=100, initial_credit=6):

        dut_channel = self.dut_le_l2cap.register_coc(self.cert_address, psm)
        cert_channel = self.cert_le_l2cap.open_channel(signal_id, psm, scid, mtu, mps, initial_credit)

        return (dut_channel, cert_channel)

    @metadata(pts_test_id="L2CAP/LE/CID/BV-01-C", pts_test_name="Receiving DCID over BR/EDR and LE")
    def test_receiving_dcid_over_bredr_and_le(self):
        """
        Test that the L2CAP entity can receive the same DCID in L2CAP connect responses on both the
        BR/EDR and LE links.
        """
        self._setup_acl_link_from_cert()
        # TODO: We should let LE use public address, same as classic link.
        # TODO: Update AclManager::impl::create_le_connection
        self._setup_le_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_dut(0x33, 0x70)
        (le_dut_channel, le_cert_channel) = self._open_le_coc_from_dut(0x35, 0x70)

        dut_channel.send(b'abc')
        assertThat(cert_channel).emits(L2capMatchers.Data(b'abc'))

        le_dut_channel.send(b'hello')
        assertThat(le_cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))

        le_cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(le_dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

        cert_channel.disconnect_and_verify()
        le_cert_channel.disconnect_and_verify()

    @metadata(pts_test_id="L2CAP/LE/CID/BV-02-C", pts_test_name="Receiving SCID over BR/EDR and LE")
    def test_receiving_scid_over_bredr_and_le(self):
        """
        Test that the L2CAP entity can receive the same SCID in L2CAP connect requests on both the
        BR/EDR and LE links.
        """
        self._setup_acl_link_from_cert()
        # TODO: We should let LE use public address, same as classic link.
        # TODO: Update AclManager::impl::create_le_connection
        self._setup_le_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert(0x33, 0x70)
        (le_dut_channel, le_cert_channel) = self._open_le_coc_from_cert(0x35, 0x70)

        dut_channel.send(b'abc')
        assertThat(cert_channel).emits(L2capMatchers.Data(b'abc'))

        le_dut_channel.send(b'hello')
        assertThat(le_cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))

        le_cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(le_dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

        cert_channel.disconnect_and_verify()
        le_cert_channel.disconnect_and_verify()


if __name__ == '__main__':
    test_runner.main()
