#
#   Copyright 2021 - The Android Open Source Project
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

from blueberry.tests.gd.cert.matchers import IsoMatchers
from blueberry.tests.gd.cert.metadata import metadata
from blueberry.tests.gd.cert.py_l2cap import PyLeL2cap
from blueberry.tests.gd.cert.py_le_iso import PyLeIso
from blueberry.tests.gd.cert.py_le_iso import CisTestParameters
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.iso.cert_le_iso import CertLeIso
from blueberry.tests.gd.l2cap.le.cert_le_l2cap import CertLeL2cap
from blueberry.facade import common_pb2 as common
from blueberry.facade.hci import controller_facade_pb2 as controller_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from mobly import asserts
from mobly import test_runner
import hci_packets as hci


class LeIsoTest(gd_base_test.GdBaseTestClass):

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

        self.dut_iso = PyLeIso(self.dut)
        self.cert_iso = CertLeIso(self.cert)

    def teardown_test(self):
        self.dut_iso.close()
        self.cert_iso.close()

        self.cert_l2cap.close()
        self.dut_l2cap.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    #cert becomes central of connection, dut peripheral
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

    def _setup_cis_from_cert(self, cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, peripherals_clock_accuracy,
                             packing, framing, max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_id,
                             max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m, bn_m_to_s, bn_s_to_m):

        self.cert_iso.le_set_cig_parameters(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m,
                                            peripherals_clock_accuracy, packing, framing, max_transport_latency_m_to_s,
                                            max_transport_latency_s_to_m, cis_id, max_sdu_m_to_s, max_sdu_s_to_m,
                                            phy_m_to_s, phy_s_to_m, bn_m_to_s, bn_s_to_m)

        cis_handles = self.cert_iso.wait_le_set_cig_parameters_complete()

        cis_handle = cis_handles[0]

        acl_connection_handle = self.cert_l2cap._le_acl.handle
        self.cert_iso.le_cretate_cis([(cis_handle, acl_connection_handle)])
        dut_cis_stream = self.dut_iso.wait_le_cis_established()
        cert_cis_stream = self.cert_iso.wait_le_cis_established()
        return (dut_cis_stream, cert_cis_stream)

    def skip_if_iso_not_supported(self):
        supported = self.dut.hci_controller.IsSupportedCommand(
            controller_facade.OpCodeMsg(op_code=int(hci.OpCode.LE_SET_CIG_PARAMETERS)))
        if (not supported.supported):
            asserts.skip("Skipping this test.  The chip doesn't support LE ISO")

    @metadata(pts_test_id="IAL/CIS/UNF/SLA/BV-01-C",
              pts_test_name="connected isochronous stream, unframed data, peripheral role")
    def test_iso_cis_unf_sla_bv_01_c(self):
        self.skip_if_iso_not_supported()
        """
            Verify that the IUT can send an SDU with length ≤ the Isochronous PDU length.
        """
        cig_id = 0x01
        sdu_interval_m_to_s = 0
        sdu_interval_s_to_m = 0x186a
        peripherals_clock_accuracy = 0
        packing = 0
        framing = 0
        max_transport_latency_m_to_s = 0
        max_transport_latency_s_to_m = 7
        cis_id = 0x01
        max_sdu_m_to_s = 0
        max_sdu_s_to_m = 100
        phy_m_to_s = 0x02
        phy_s_to_m = 0x02
        bn_m_to_s = 0
        bn_s_to_m = 2

        self._setup_link_from_cert()
        (dut_cis_stream,
         cert_cis_stream) = self._setup_cis_from_cert(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m,
                                                      peripherals_clock_accuracy, packing, framing,
                                                      max_transport_latency_m_to_s, max_transport_latency_s_to_m,
                                                      cis_id, max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m,
                                                      bn_m_to_s, bn_s_to_m)
        dut_cis_stream.send(b'abcdefgh' * 10)
        assertThat(cert_cis_stream).emits(IsoMatchers.Data(b'abcdefgh' * 10))

    @metadata(pts_test_id="IAL/CIS/UNF/SLA/BV-25-C",
              pts_test_name="connected isochronous stream, unframed data, peripheral role")
    def test_iso_cis_unf_sla_bv_25_c(self):
        self.skip_if_iso_not_supported()
        """
            Verify that the IUT can send an SDU with length ≤ the Isochronous PDU length.
        """
        cig_id = 0x01
        sdu_interval_m_to_s = 0x7530
        sdu_interval_s_to_m = 0x7530
        peripherals_clock_accuracy = 0
        packing = 0
        framing = 0
        max_transport_latency_m_to_s = 30
        max_transport_latency_s_to_m = 30
        cis_id = 0x01
        max_sdu_m_to_s = 100
        max_sdu_s_to_m = 100
        phy_m_to_s = 0x02
        phy_s_to_m = 0x02
        bn_m_to_s = 3
        bn_s_to_m = 1

        self._setup_link_from_cert()
        (dut_cis_stream,
         cert_cis_stream) = self._setup_cis_from_cert(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m,
                                                      peripherals_clock_accuracy, packing, framing,
                                                      max_transport_latency_m_to_s, max_transport_latency_s_to_m,
                                                      cis_id, max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m,
                                                      bn_m_to_s, bn_s_to_m)
        dut_cis_stream.send(b'abcdefgh' * 10)
        assertThat(cert_cis_stream).emits(IsoMatchers.Data(b'abcdefgh' * 10))

    @metadata(pts_test_id="IAL/CIS/FRA/SLA/BV-03-C",
              pts_test_name="connected isochronous stream, framed data, peripheral role")
    def test_iso_cis_fra_sla_bv_03_c(self):
        self.skip_if_iso_not_supported()
        """
            Verify that the IUT can send an SDU with length ≤ the Isochronous PDU length.
        """
        cig_id = 0x01
        sdu_interval_m_to_s = 0x0000
        sdu_interval_s_to_m = 0x4e30
        peripherals_clock_accuracy = 0
        packing = 0
        framing = 1
        max_transport_latency_m_to_s = 0
        max_transport_latency_s_to_m = 21
        cis_id = 0x01
        max_sdu_m_to_s = 0
        max_sdu_s_to_m = 100
        phy_m_to_s = 0x02
        phy_s_to_m = 0x02
        bn_m_to_s = 0
        bn_s_to_m = 2

        self._setup_link_from_cert()
        (dut_cis_stream,
         cert_cis_stream) = self._setup_cis_from_cert(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m,
                                                      peripherals_clock_accuracy, packing, framing,
                                                      max_transport_latency_m_to_s, max_transport_latency_s_to_m,
                                                      cis_id, max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m,
                                                      bn_m_to_s, bn_s_to_m)
        dut_cis_stream.send(b'abcdefgh' * 10)
        assertThat(cert_cis_stream).emits(IsoMatchers.Data(b'abcdefgh' * 10))

    @metadata(pts_test_id="IAL/CIS/FRA/SLA/BV-26-C",
              pts_test_name="connected isochronous stream, framed data, peripheral role")
    def test_iso_cis_fra_sla_bv_26_c(self):
        self.skip_if_iso_not_supported()
        """
            Verify that the IUT can send an SDU with length ≤ the Isochronous PDU length.
        """
        cig_id = 0x01
        sdu_interval_m_to_s = 0x14D5
        sdu_interval_s_to_m = 0x14D5
        peripherals_clock_accuracy = 0
        packing = 0
        framing = 1
        max_transport_latency_m_to_s = 6
        max_transport_latency_s_to_m = 6
        cis_id = 0x01
        max_sdu_m_to_s = 100
        max_sdu_s_to_m = 100
        phy_m_to_s = 0x02
        phy_s_to_m = 0x02
        bn_m_to_s = 1
        bn_s_to_m = 1

        self._setup_link_from_cert()
        (dut_cis_stream,
         cert_cis_stream) = self._setup_cis_from_cert(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m,
                                                      peripherals_clock_accuracy, packing, framing,
                                                      max_transport_latency_m_to_s, max_transport_latency_s_to_m,
                                                      cis_id, max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m,
                                                      bn_m_to_s, bn_s_to_m)
        dut_cis_stream.send(b'abcdefgh' * 10)
        assertThat(cert_cis_stream).emits(IsoMatchers.Data(b'abcdefgh' * 10))


if __name__ == '__main__':
    test_runner.main()
