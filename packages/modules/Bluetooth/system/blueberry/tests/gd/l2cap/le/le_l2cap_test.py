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
from bluetooth_packets_python3.l2cap_packets import LeCreditBasedConnectionResponseResult
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_l2cap import PyLeL2cap
from blueberry.tests.gd.cert.matchers import L2capMatchers
from blueberry.tests.gd.cert.metadata import metadata
from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.l2cap.le.cert_le_l2cap import CertLeL2cap
from blueberry.facade import common_pb2 as common
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import le_acl_manager_facade_pb2 as le_acl_manager_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.l2cap.le.facade_pb2 import SecurityLevel
from mobly import test_runner
import hci_packets as hci

# Assemble a sample packet.
SAMPLE_PACKET = bt_packets.RawBuilder([0x19, 0x26, 0x08, 0x17])


class LeL2capTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='L2CAP', cert_module='HCI_INTERFACES')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)

        self.dut_l2cap = PyLeL2cap(self.dut)
        self.cert_l2cap = CertLeL2cap(self.cert)
        self.dut_address = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'D0:05:04:03:02:01')), type=common.RANDOM_DEVICE_ADDRESS)
        self.cert_address = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'C0:11:FF:AA:33:22')), type=common.RANDOM_DEVICE_ADDRESS)
        dut_privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=self.dut_address,
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
        self.cert_l2cap._device.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)

    def teardown_test(self):
        self.cert_l2cap.close()
        self.dut_l2cap.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def _setup_link_from_cert(self):
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
        self.cert_l2cap.connect_le_acl(self.dut_address)

    def _set_link_from_dut_and_open_channel(self,
                                            signal_id=1,
                                            scid=0x0101,
                                            psm=0x33,
                                            mtu=1000,
                                            mps=100,
                                            initial_credit=6):
        # Cert Advertises
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
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm)
        self.cert_l2cap.wait_for_connection()
        # TODO: Currently we can only connect by using Dynamic channel API. Use fixed channel instead.
        cert_channel = self.cert_l2cap.verify_and_respond_open_channel_from_remote(psm)
        dut_channel = response_future.get_channel()
        return (dut_channel, cert_channel)

    def _open_channel_from_cert(self, signal_id=1, scid=0x0101, psm=0x33, mtu=1000, mps=100, initial_credit=6):

        dut_channel = self.dut_l2cap.register_coc(self.cert_address, psm)
        cert_channel = self.cert_l2cap.open_channel(signal_id, psm, scid, mtu, mps, initial_credit)

        return (dut_channel, cert_channel)

    def _open_channel_from_dut(self, psm=0x33):
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm)
        cert_channel = self.cert_l2cap.verify_and_respond_open_channel_from_remote(psm)
        dut_channel = response_future.get_channel()
        return (dut_channel, cert_channel)

    def _open_fixed_channel(self, cid=4):
        dut_channel = self.dut_l2cap.get_fixed_channel(cid)
        cert_channel = self.cert_l2cap.open_fixed_channel(cid)
        return (dut_channel, cert_channel)

    def test_fixed_channel_send(self):
        self.dut_l2cap.enable_fixed_channel(4)
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_fixed_channel(4)
        dut_channel.send(b'hello' * 40)
        assertThat(cert_channel).emits(L2capMatchers.Data(b'hello' * 40))

    def test_fixed_channel_receive(self):
        self.dut_l2cap.enable_fixed_channel(4)
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_fixed_channel(4)
        cert_channel.send(SAMPLE_PACKET)
        assertThat(dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

    def test_connect_from_dut_and_open_dynamic_channel(self):
        """
        Internal test for GD stack only
        """
        self._set_link_from_dut_and_open_channel()

    @metadata(pts_test_id="L2CAP/LE/CPU/BV-01-C", pts_test_name="Send Connection Parameter Update Request")
    def test_send_connection_parameter_update_request(self):
        """
        Verify that the IUT is able to send the connection parameter update Request to Lower Tester when acting as a peripheral device.
        NOTE: This is an optional feature. Also if both LL central and peripheral supports 4.1+ connection parameter update, this should happen in LL only, not L2CAP
        NOTE: Currently we need to establish at least one dynamic channel to allow update.
        """
        self._setup_link_from_cert()
        self._open_channel_from_dut()
        self.dut_l2cap.update_connection_parameter()
        assertThat(self.cert_l2cap.get_control_channel()).emits(L2capMatchers.LeConnectionParameterUpdateRequest())

    @metadata(pts_test_id="L2CAP/LE/CPU/BV-02-C", pts_test_name="Accept Connection Parameter Update Request")
    def test_accept_connection_parameter_update_request(self):
        """
        Verify that the IUT is able to receive and handle a request for connection parameter update when acting as a central device.
        NOTE: Currently we need to establish at least one dynamic channel to allow update.
        """
        self._set_link_from_dut_and_open_channel()
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.ConnectionParameterUpdateRequestBuilder(2, 0x10, 0x10, 0x0a, 0x64))
        assertThat(self.cert_l2cap.get_control_channel()).emits(
            L2capMatchers.LeConnectionParameterUpdateResponse(
                l2cap_packets.ConnectionParameterUpdateResponseResult.ACCEPTED))

    @metadata(pts_test_id="L2CAP/LE/CPU/BI-01-C", pts_test_name="Reject Connection Parameter Update Parameters")
    def test_reject_connection_parameter_update_parameters(self):
        """
        Verify that the IUT is able to reject a request for connection parameter update with illegal parameters.
        NOTE: Currently we need to establish at least one dynamic channel to allow update.
        """
        self._set_link_from_dut_and_open_channel()
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.ConnectionParameterUpdateRequestBuilder(2, 0x10, 0x10, 512, 0x64))
        assertThat(self.cert_l2cap.get_control_channel()).emits(
            L2capMatchers.LeConnectionParameterUpdateResponse(
                l2cap_packets.ConnectionParameterUpdateResponseResult.REJECTED))

    @metadata(pts_test_id="L2CAP/LE/CPU/BI-02-C", pts_test_name="Reject Connection Parameter Update Request")
    def test_reject_connection_parameter_update_request(self):
        """
        Verify that the IUT is able to reject a request for connection parameter update in peripheral mode.
        """
        self._setup_link_from_cert()
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.ConnectionParameterUpdateRequestBuilder(2, 0x10, 0x10, 0x0a, 0x64))
        assertThat(self.cert_l2cap.get_control_channel()).emits(L2capMatchers.LeCommandReject())

    @metadata(pts_test_id="L2CAP/COS/CFC/BV-01-C", pts_test_name="Segmentation")
    def test_segmentation(self):
        """
        Verify that the IUT can send data segments which are larger than the LE frame size.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert(mtu=1000, mps=102)
        dut_channel.send(b'hello' * 20 + b'world')
        # The first LeInformation packet contains 2 bytes of SDU size.
        # The packet is divided into first 100 bytes from 'hellohello....'
        # and remaining 5 bytes 'world'
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello' * 20, sdu_size=105),
                                       L2capMatchers.Data(b'world')).inOrder()

    @metadata(pts_test_id="L2CAP/COS/CFC/BV-02-C", pts_test_name="No Segmentation")
    def test_no_segmentation(self):
        """
        Verify that the IUT can send data segments which do not require segmentation.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert(mtu=1000, mps=202)
        dut_channel.send(b'hello' * 40)
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello' * 40, sdu_size=200))

    def test_no_segmentation_dut_is_central(self):
        """
        L2CAP/COS/CFC/BV-02-C
        """
        (dut_channel, cert_channel) = self._set_link_from_dut_and_open_channel()
        dut_channel.send(b'hello' * 40)
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello' * 40, sdu_size=200))

    @metadata(pts_test_id="L2CAP/COS/CFC/BV-03-C", pts_test_name="Reassembling")
    def test_reassembling(self):
        """
        Verify that the IUT can correctly reassemble data received from the Lower Tester which is greater than the IUT LE-frame size.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        sdu_size_for_two_sample_packet = 8
        cert_channel.send_first_le_i_frame(sdu_size_for_two_sample_packet, SAMPLE_PACKET)
        cert_channel.send(SAMPLE_PACKET)
        assertThat(dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17' * 2))

    @metadata(pts_test_id="L2CAP/COS/CFC/BV-04-C", pts_test_name="Data Receiving")
    def test_data_receiving(self):
        """
        Verify that the IUT can receive unsegmented data correctly.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

    def test_data_receiving_dut_is_central(self):
        """
        L2CAP/COS/CFC/BV-04-C
        """
        (dut_channel, cert_channel) = self._set_link_from_dut_and_open_channel()
        cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

    @metadata(pts_test_id="L2CAP/COS/CFC/BV-05-C", pts_test_name="Multiple Channels with Interleaved Data Streams")
    def test_multiple_channels_with_interleaved_data_streams(self):
        """
        Verify that an IUT can create multiple channels and receives data streams on the channels when the streams are interleaved.
        """
        self._setup_link_from_cert()
        (dut_channel_x, cert_channel_x) = self._open_channel_from_cert(signal_id=1, scid=0x0103, psm=0x33)
        (dut_channel_y, cert_channel_y) = self._open_channel_from_cert(signal_id=2, scid=0x0105, psm=0x35)
        (dut_channel_z, cert_channel_z) = self._open_channel_from_cert(signal_id=3, scid=0x0107, psm=0x37)
        cert_channel_y.send_first_le_i_frame(4, SAMPLE_PACKET)
        cert_channel_z.send_first_le_i_frame(4, SAMPLE_PACKET)
        cert_channel_y.send_first_le_i_frame(4, SAMPLE_PACKET)
        cert_channel_z.send_first_le_i_frame(4, SAMPLE_PACKET)
        cert_channel_y.send_first_le_i_frame(4, SAMPLE_PACKET)
        # TODO: We should assert two events in order, but it got stuck
        assertThat(dut_channel_y).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'), at_least_times=3)
        assertThat(dut_channel_z).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'),
                                        L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17')).inOrder()
        cert_channel_z.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(dut_channel_z).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

    @metadata(pts_test_id="L2CAP/LE/REJ/BI-01-C", pts_test_name="Reject Unknown Command in LE Signaling Channel")
    def test_reject_unknown_command_in_le_sigling_channel(self):
        """
        Verify that the IUT is able to reject unknown command.
        """
        self._setup_link_from_cert()
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.InformationRequestBuilder(
                2, l2cap_packets.InformationRequestInfoType.EXTENDED_FEATURES_SUPPORTED))
        assertThat(self.cert_l2cap.get_control_channel()).emits(L2capMatchers.LeCommandReject())

    @metadata(pts_test_id="L2CAP/LE/REJ/BI-02-C", pts_test_name="Command Reject – Reserved PDU Codes")
    def test_command_reject_reserved_pdu_codes(self):
        """
        Verify that an IUT receiving a PDU with a reserved command code sends a command reject.
        """
        self._setup_link_from_cert()
        self.cert_l2cap.get_control_channel().send(l2cap_packets.MoveChannelRequestBuilder(2, 0, 0))
        assertThat(self.cert_l2cap.get_control_channel()).emits(L2capMatchers.LeCommandReject())

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-01-C", pts_test_name="LE Credit Based Connection Request - Legacy Peer")
    def test_le_credit_based_connection_request_legacy_peer(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request to a legacy peer and receiving a Command Reject does not establish the channel.
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_reject_open_channel_from_remote(psm=0x33)
        assertThat(response_future.get_status()).isNotEqualTo(LeCreditBasedConnectionResponseResult.SUCCESS)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-02-C",
              pts_test_name="LE Credit Based Connection Request on Supported LE_PSM")
    def test_le_credit_based_connection_request_on_supported_le_psm(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request to a peer will establish the channel upon receiving the LE Credit Based Connection Response.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_dut()
        cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        assertThat(dut_channel).emits(L2capMatchers.PacketPayloadRawData(b'\x19\x26\x08\x17'))

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-03-C",
              pts_test_name="LE Credit Based Connection Response on Supported LE_PSM")
    def test_credit_based_connection_response_on_supported_le_psm(self):
        """
        Verify that an IUT receiving a valid LE Credit Based Connection Request from a peer will send an LE Credit Based Connection Response and establish the channel.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        dut_channel.send(b'hello')
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-04-C",
              pts_test_name="LE Credit Based Connection Request on an Unsupported LE_PSM")
    def test_credit_based_connection_request_on_an_unsupported_le_psm(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request on an unsupported LE_PSM will not establish a channel upon receiving an LE Credit Based Connection Response refusing the connection.
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.LE_PSM_NOT_SUPPORTED)
        assertThat(response_future.get_status()).isEqualTo(LeCreditBasedConnectionResponseResult.LE_PSM_NOT_SUPPORTED)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-05-C",
              pts_test_name="LE Credit Based Connection Request - unsupported LE_PSM")
    def test_credit_based_connection_request_unsupported_le_psm(self):
        """
        Verify that an IUT receiving an LE Credit Based Connection Request on an unsupported LE_PSM will respond with an LE Credit Based Connection Response refusing the connection.
        """
        self._setup_link_from_cert()
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.LeCreditBasedConnectionRequestBuilder(1, 0x34, 0x0101, 2000, 1000, 1000))
        assertThat(self.cert_l2cap.get_control_channel()).emits(
            L2capMatchers.CreditBasedConnectionResponse(
                result=LeCreditBasedConnectionResponseResult.LE_PSM_NOT_SUPPORTED))

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-06-C", pts_test_name="Credit Exchange – Receiving Incremental Credits")
    def test_credit_exchange_receiving_incremental_credits(self):
        """
        Verify the IUT handles flow control correctly, by handling the LE Flow Control Credit sent by the peer.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert(initial_credit=0)
        for _ in range(4):
            dut_channel.send(b'hello')
        cert_channel.send_credits(1)
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))
        cert_channel.send_credits(1)
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))
        cert_channel.send_credits(2)
        assertThat(cert_channel).emits(L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5),
                                       L2capMatchers.FirstLeIFrame(b'hello', sdu_size=5))

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-07-C", pts_test_name="Credit Exchange – Sending Credits")
    def test_credit_exchange_sending_credits(self):
        """
        Verify that the IUT sends LE Flow Control Credit to the peer.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        credits = cert_channel.credits_left()
        # Note: DUT only needs to send credit when ALL credits are consumed.
        # Here we enforce that DUT sends credit after receiving 3 packets, to
        # test without sending too many packets (may take too long).
        # This behavior is not expected for all Bluetooth stacks.
        for _ in range(min(credits + 1, 3)):
            cert_channel.send_first_le_i_frame(4, SAMPLE_PACKET)
        self.cert_l2cap.verify_le_flow_control_credit(cert_channel)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-08-C", pts_test_name="Disconnection Request")
    def test_disconnection_request(self):
        """
        Verify that the IUT can disconnect the channel.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        dut_channel.close_channel()
        cert_channel.verify_disconnect_request()

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-09-C", pts_test_name="Disconnection Response")
    def test_disconnection_response(self):
        """
        Verify that the IUT responds correctly to reception of a Disconnection Request.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        cert_channel.disconnect_and_verify()

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-10-C", pts_test_name="Security - Insufficient Authentication – Initiator")
    def test_security_insufficient_authentication_initiator(self):
        """
        Verify that the IUT does not establish the channel upon receipt of an LE Credit Based Connection Response indicating the connection was refused with Result “0x0005 – Connection Refused – Insufficient Authentication".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHENTICATION)
        assertThat(response_future.get_status()).isEqualTo(
            LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHENTICATION)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-11-C", pts_test_name="Security - Insufficient Authentication – Responder")
    def test_security_insufficient_authentication_responder(self):
        """
        Verify that an IUT refuses to create a connection upon reception of an LE Credit Based Connection
Request which fails to satisfy authentication requirements.
        """
        self._setup_link_from_cert()
        psm = 0x33
        self.dut_l2cap.register_coc(self.cert_address, psm, SecurityLevel.AUTHENTICATED_PAIRING_WITH_ENCRYPTION)
        self.cert_l2cap.open_channel_with_expected_result(
            psm, LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHENTICATION)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-12-C", pts_test_name="Security - Insufficient Authorization – Initiator")
    def test_security_insufficient_authorization_initiator(self):
        """
        Verify that the IUT does not establish the channel upon receipt of an LE Credit Based Connection Response indicating the connection was refused with Result “0x0006 – Connection Refused – Insufficient Authorization”.
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHORIZATION)
        assertThat(response_future.get_status()).isEqualTo(
            LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHORIZATION)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-13-C", pts_test_name="Security - Insufficient Authorization – Responder")
    def test_security_insufficient_authorization_responder(self):
        """
        Verify that an IUT refuses to create a connection upon reception of an LE Credit Based Connection
        Request which fails to satisfy authentication requirements.
        """
        self._setup_link_from_cert()
        psm = 0x33
        self.dut_l2cap.register_coc(self.cert_address, psm, SecurityLevel.AUTHORIZATION)
        self.cert_l2cap.open_channel_with_expected_result(
            psm, LeCreditBasedConnectionResponseResult.INSUFFICIENT_AUTHORIZATION)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-14-C", pts_test_name="Security - Insufficient Key Size – Initiator")
    def test_security_insufficient_key_size_initiator(self):
        """
        Verify that the IUT does not establish the channel upon receipt of an
        LE Credit Based Connection Response indicating the connection was
        refused with Result "0x0007 – Connection Refused – Insufficient
        Encryption Key Size".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.INSUFFICIENT_ENCRYPTION_KEY_SIZE)
        assertThat(response_future.get_status()).isEqualTo(
            LeCreditBasedConnectionResponseResult.INSUFFICIENT_ENCRYPTION_KEY_SIZE)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-15-C",
              pts_test_name="Security - Insufficient Encryption Key Size – Responder")
    def test_security_insufficient_encryption_key_size_responder(self):
        """
        Verify that an IUT refuses to create a connection upon receipt of an LE Credit Based Connection
        Request which fails to satisfy Encryption Key Size requirements.
        """
        self._setup_link_from_cert()
        psm = 0x33
        self.dut_l2cap.register_coc(self.cert_address, psm, SecurityLevel.AUTHENTICATED_PAIRING_WITH_128_BIT_KEY)
        self.cert_l2cap.open_channel_with_expected_result(
            psm, LeCreditBasedConnectionResponseResult.INSUFFICIENT_ENCRYPTION_KEY_SIZE)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-16-C",
              pts_test_name="LE Credit Based Connection Request - refuse due to insufficient resources - Initiator")
    def test_le_connection_request_insufficient_resources_initiator(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request does
        not establish the channel upon receiving an LE Credit Based Connection
        Response refusing the connection with result "0x0004 – Connection
        refused – no resources available".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.NO_RESOURCES_AVAILABLE)
        assertThat(response_future.get_status()).isEqualTo(LeCreditBasedConnectionResponseResult.NO_RESOURCES_AVAILABLE)

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-18-C",
              pts_test_name="LE Credit Based Connection Request - refused due to Invalid Source CID - Initiator")
    def test_request_refused_due_to_invalid_source_cid_initiator(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request does not establish the channel upon receiving an LE Credit Based Connection Response refusing the connection with result "0x0009 – Connection refused – Invalid Source CID".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.INVALID_SOURCE_CID)
        assertThat(response_future.get_status()).isEqualTo(LeCreditBasedConnectionResponseResult.INVALID_SOURCE_CID)

    @metadata(
        pts_test_id="L2CAP/LE/CFC/BV-19-C",
        pts_test_name="LE Credit Based Connection Request - refused due to source CID already allocated - Initiator")
    def test_request_refused_due_to_source_cid_already_allocated_initiator(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request does not establish the channel upon receiving an LE Credit Based Connection Response refusing the connection with result "0x000A – Connection refused – Source CID already allocated".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.SOURCE_CID_ALREADY_ALLOCATED)
        assertThat(response_future.get_status()).isEqualTo(
            LeCreditBasedConnectionResponseResult.SOURCE_CID_ALREADY_ALLOCATED)

    @metadata(
        pts_test_id="L2CAP/LE/CFC/BV-20-C",
        pts_test_name="LE Credit Based Connection Response - refused due to Source CID already allocated - Responder")
    def test_request_refused_due_to_source_cid_already_allocated_responder(self):
        """
        Verify that an IUT receiving an LE Credit Based Connection Request for a second channel will refuse the connection with result "0x000A - Connection refused – Source CID already allocated" if it receives a Source CID which is already in use.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert(psm=0x33, scid=0x0101)
        self.dut_l2cap.register_coc(self.cert_address, psm=0x35)
        self.cert_l2cap.get_control_channel().send(
            l2cap_packets.LeCreditBasedConnectionRequestBuilder(2, 0x35, 0x0101, 1000, 1000, 1000))
        assertThat(self.cert_l2cap.get_control_channel()).emits(L2capMatchers.CreditBasedConnectionResponseUsedCid())

    @metadata(pts_test_id="L2CAP/LE/CFC/BV-21-C",
              pts_test_name="LE Credit Based Connection Request - refused due to Unacceptable Parameters - Initiator")
    def test_request_refused_due_to_unacceptable_parameters_initiator(self):
        """
        Verify that an IUT sending an LE Credit Based Connection Request does not establish the channel upon receiving an LE Credit Based Connection Response refusing the connection with result "0x000B – Connection refused – Unacceptable Parameters".
        """
        self._setup_link_from_cert()
        response_future = self.dut_l2cap.connect_coc_to_cert(self.cert_address, psm=0x33)
        self.cert_l2cap.verify_and_respond_open_channel_from_remote(
            psm=0x33, result=LeCreditBasedConnectionResponseResult.UNACCEPTABLE_PARAMETERS)
        assertThat(response_future.get_status()).isEqualTo(
            LeCreditBasedConnectionResponseResult.UNACCEPTABLE_PARAMETERS)

    @metadata(pts_test_id="L2CAP/LE/CFC/BI-01-C", pts_test_name="Credit Exchange – Exceed Initial Credits")
    def test_credit_exchange_exceed_initial_credits(self):
        """
        Verify that the IUT disconnects the LE Data Channel when the credit count exceeds 65535.
        """
        self._setup_link_from_cert()
        (dut_channel, cert_channel) = self._open_channel_from_cert()
        cert_channel.send_credits(65535)
        cert_channel.verify_disconnect_request()


if __name__ == '__main__':
    test_runner.main()
