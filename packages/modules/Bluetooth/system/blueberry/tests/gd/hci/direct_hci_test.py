#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import logging

from blueberry.tests.gd.cert.captures import HalCaptures, HciCaptures
from blueberry.tests.gd.cert.matchers import HciMatchers
from blueberry.tests.gd.cert.py_hal import PyHal
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from blueberry.facade import common_pb2 as common
from mobly import test_runner

import hci_packets as hci
from blueberry.utils import bluetooth


class DirectHciTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI', cert_module='HAL')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.dut_hci = PyHci(self.dut, acl_streaming=True)
        self.cert_hal = PyHal(self.cert)
        self.cert_hal.send_hci_command(hci.Reset())

    def teardown_test(self):
        self.dut_hci.close()
        self.cert_hal.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def enqueue_acl_data(self, handle, pb_flag, b_flag, data):
        acl = hci.Acl(handle=handle, packet_boundary_flag=pb_flag, broadcast_flag=b_flag, payload=data)
        self.dut.hci.SendAcl(common.Data(payload=acl.serialize()))

    def test_local_hci_cmd_and_event(self):
        # Loopback mode responds with ACL and SCO connection complete
        self.dut_hci.register_for_events(hci.EventCode.LOOPBACK_COMMAND)
        self.dut_hci.send_command(hci.WriteLoopbackMode(loopback_mode=hci.LoopbackMode.ENABLE_LOCAL))

        self.dut_hci.send_command(hci.ReadLocalName())
        assertThat(self.dut_hci.get_event_stream()).emits(HciMatchers.LoopbackOf(hci.ReadLocalName().serialize()))

    def test_inquiry_from_dut(self):
        self.dut_hci.register_for_events(hci.EventCode.INQUIRY_RESULT)

        self.cert_hal.enable_inquiry_and_page_scan()
        self.dut_hci.send_command(hci.Inquiry(lap=hci.Lap(lap=0x33), inquiry_length=0x30, num_responses=0xff))
        assertThat(self.dut_hci.get_event_stream()).emits(HciMatchers.EventWithCode(hci.EventCode.INQUIRY_RESULT))

    def test_le_ad_scan_cert_advertises(self):
        self.dut_hci.register_for_le_events(hci.SubeventCode.EXTENDED_ADVERTISING_REPORT,
                                            hci.SubeventCode.ADVERTISING_REPORT)

        # DUT Scans
        self.dut_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0D:05:04:03:02:01')))

        self.dut_hci.send_command(
            hci.LeSetExtendedScanParameters(own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                            scanning_filter_policy=hci.LeScanningFilterPolicy.ACCEPT_ALL,
                                            scanning_phys=1,
                                            parameters=[
                                                hci.PhyScanParameters(le_scan_type=hci.LeScanType.ACTIVE,
                                                                      le_scan_interval=6553,
                                                                      le_scan_window=6553)
                                            ]))

        self.dut_hci.send_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.ENABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))

        # CERT Advertises
        advertising_handle = 0
        self.cert_hal.send_hci_command(
            hci.LeSetExtendedAdvertisingParametersLegacy(
                advertising_handle=advertising_handle,
                legacy_advertising_event_properties=hci.LegacyAdvertisingEventProperties.ADV_IND,
                primary_advertising_interval_min=512,
                primary_advertising_interval_max=768,
                primary_advertising_channel_map=7,
                own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                peer_address_type=hci.PeerAddressType.PUBLIC_DEVICE_OR_IDENTITY_ADDRESS,
                peer_address=bluetooth.Address('A6:A5:A4:A3:A2:A1'),
                advertising_filter_policy=hci.AdvertisingFilterPolicy.ALL_DEVICES,
                advertising_tx_power=0xF7,
                advertising_sid=1,
                scan_request_notification_enable=hci.Enable.DISABLED))

        self.cert_hal.send_hci_command(
            hci.LeSetAdvertisingSetRandomAddress(advertising_handle=advertising_handle,
                                                 random_address=bluetooth.Address('0C:05:04:03:02:01')))

        self.cert_hal.send_hci_command(
            hci.LeSetExtendedAdvertisingData(
                advertising_handle=advertising_handle,
                operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                advertising_data=[hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(b'Im_A_Cert'))]))

        self.cert_hal.send_hci_command(
            hci.LeSetExtendedScanResponseData(
                advertising_handle=advertising_handle,
                operation=hci.Operation.COMPLETE_ADVERTISEMENT,
                fragment_preference=hci.FragmentPreference.CONTROLLER_SHOULD_NOT,
                scan_response_data=[hci.GapData(data_type=hci.GapDataType.SHORTENED_LOCAL_NAME, data=list(b'Im_A_C'))]))

        self.cert_hal.send_hci_command(
            hci.LeSetExtendedAdvertisingEnable(enable=hci.Enable.ENABLED,
                                               enabled_sets=[
                                                   hci.EnabledSet(advertising_handle=advertising_handle,
                                                                  duration=0,
                                                                  max_extended_advertising_events=0)
                                               ]))

        assertThat(self.dut_hci.get_le_event_stream()).emits(lambda packet: b'Im_A_Cert' in packet.payload)

        self.cert_hal.send_hci_command(
            hci.LeSetExtendedAdvertisingEnable(enable=hci.Enable.DISABLED,
                                               enabled_sets=[
                                                   hci.EnabledSet(advertising_handle=advertising_handle,
                                                                  duration=0,
                                                                  max_extended_advertising_events=0)
                                               ]))

        self.dut_hci.send_command(hci.LeSetExtendedScanEnable(enable=hci.Enable.DISABLED))

    def _verify_le_connection_complete(self):
        cert_conn_complete_capture = HalCaptures.LeConnectionCompleteCapture()
        assertThat(self.cert_hal.get_hci_event_stream()).emits(cert_conn_complete_capture)
        cert_handle = cert_conn_complete_capture.get().connection_handle

        dut_conn_complete_capture = HciCaptures.LeConnectionCompleteCapture()
        assertThat(self.dut_hci.get_le_event_stream()).emits(dut_conn_complete_capture)
        dut_handle = dut_conn_complete_capture.get().connection_handle

        return (dut_handle, cert_handle)

    @staticmethod
    def _create_phy_scan_params():
        return hci.LeCreateConnPhyScanParameters(scan_interval=0x60,
                                                 scan_window=0x30,
                                                 conn_interval_min=0x18,
                                                 conn_interval_max=0x28,
                                                 conn_latency=0,
                                                 supervision_timeout=0x1f4,
                                                 min_ce_length=0,
                                                 max_ce_length=0)

    def test_le_connection_dut_advertises(self):
        self.dut_hci.register_for_le_events(hci.SubeventCode.CONNECTION_COMPLETE,
                                            hci.SubeventCode.ADVERTISING_SET_TERMINATED,
                                            hci.SubeventCode.READ_REMOTE_FEATURES_COMPLETE)
        # Cert Connects
        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.cert_hal.send_hci_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0C:05:04:03:02:01')))
        self.cert_hal.send_hci_command(
            hci.LeExtendedCreateConnection(initiator_filter_policy=hci.InitiatorFilterPolicy.USE_PEER_ADDRESS,
                                           own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address_type=hci.AddressType.RANDOM_DEVICE_ADDRESS,
                                           peer_address=bluetooth.Address('0D:05:04:03:02:01'),
                                           initiating_phys=1,
                                           phy_scan_parameters=[self._create_phy_scan_params()]))

        advertisement = self.dut_hci.create_advertisement(0, '0D:05:04:03:02:01')
        advertisement.set_data(b'Im_The_DUT')
        advertisement.set_scan_response(b'Im_The_D')
        advertisement.start()

        (dut_handle, cert_handle) = self._verify_le_connection_complete()

        self.dut_hci.send_command(hci.LeReadRemoteFeatures(connection_handle=dut_handle))
        assertThat(self.dut_hci.get_le_event_stream()).emits(lambda packet: packet.payload[0] == int(
            hci.EventCode.LE_META_EVENT) and packet.payload[2] == int(hci.SubeventCode.READ_REMOTE_FEATURES_COMPLETE))

        # Send ACL Data
        self.enqueue_acl_data(dut_handle, hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                              hci.BroadcastFlag.POINT_TO_POINT, bytes(b'Just SomeAclData'))
        self.cert_hal.send_acl_first(cert_handle, bytes(b'Just SomeMoreAclData'))

        assertThat(self.cert_hal.get_acl_stream()).emits(
            lambda packet: logging.debug(packet.payload) or b'SomeAclData' in packet.payload)
        assertThat(self.dut_hci.get_raw_acl_stream()).emits(
            lambda packet: logging.debug(packet.payload) or b'SomeMoreAclData' in packet.payload)

    def test_le_filter_accept_list_connection_cert_advertises(self):
        # DUT Connects
        self.dut_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0D:05:04:03:02:01')))
        self.dut_hci.send_command(
            hci.LeAddDeviceToFilterAcceptList(address_type=hci.FilterAcceptListAddressType.RANDOM,
                                              address=bluetooth.Address('0C:05:04:03:02:01')))
        self.dut_hci.send_command(
            hci.LeExtendedCreateConnection(initiator_filter_policy=hci.InitiatorFilterPolicy.USE_FILTER_ACCEPT_LIST,
                                           own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                           initiating_phys=1,
                                           phy_scan_parameters=[self._create_phy_scan_params()]))

        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        advertisement = self.cert_hal.create_advertisement(1,
                                                           '0C:05:04:03:02:01',
                                                           min_interval=512,
                                                           max_interval=768,
                                                           peer_address='A6:A5:A4:A3:A2:A1',
                                                           tx_power=0x7f,
                                                           sid=0)
        advertisement.set_data(b'Im_A_Cert')
        advertisement.start()

        # LeConnectionComplete
        self._verify_le_connection_complete()

    def test_le_filter_accept_list_connection_cert_advertises_legacy(self):
        # DUT Connects
        self.dut_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0D:05:04:03:02:01')))
        self.dut_hci.send_command(
            hci.LeAddDeviceToFilterAcceptList(address_type=hci.FilterAcceptListAddressType.RANDOM,
                                              address=bluetooth.Address('0C:05:04:03:02:01')))
        self.dut_hci.send_command(
            hci.LeExtendedCreateConnection(initiator_filter_policy=hci.InitiatorFilterPolicy.USE_FILTER_ACCEPT_LIST,
                                           own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                           initiating_phys=1,
                                           phy_scan_parameters=[self._create_phy_scan_params()]))

        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.cert_hal.send_hci_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0C:05:04:03:02:01')))

        advertisement = self.cert_hal.create_legacy_advertisement(min_interval=512,
                                                                  max_interval=768,
                                                                  peer_address='A6:A5:A4:A3:A2:A1')
        advertisement.set_data(b'Im_A_Cert')
        advertisement.start()

        # LeConnectionComplete
        self._verify_le_connection_complete()

    def test_le_ad_scan_cert_advertises_legacy(self):
        self.dut_hci.register_for_le_events(hci.SubeventCode.EXTENDED_ADVERTISING_REPORT,
                                            hci.SubeventCode.ADVERTISING_REPORT)

        # DUT Scans
        self.dut_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0D:05:04:03:02:01')))

        self.dut_hci.send_command(
            hci.LeSetExtendedScanParameters(own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                            scanning_filter_policy=hci.LeScanningFilterPolicy.ACCEPT_ALL,
                                            scanning_phys=1,
                                            parameters=[
                                                hci.PhyScanParameters(le_scan_type=hci.LeScanType.ACTIVE,
                                                                      le_scan_interval=6553,
                                                                      le_scan_window=6553)
                                            ]))

        self.dut_hci.send_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.ENABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))

        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.cert_hal.send_hci_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0C:05:04:03:02:01')))

        advertisement = self.cert_hal.create_legacy_advertisement(min_interval=512,
                                                                  max_interval=768,
                                                                  peer_address='A6:A5:A4:A3:A2:A1')
        advertisement.set_data(b'Im_A_Cert')
        advertisement.start()

        assertThat(self.dut_hci.get_le_event_stream()).emits(
            HciMatchers.LeAdvertisement(address='0C:05:04:03:02:01', data=b'Im_A_Cert'))

    def test_connection_dut_connects(self):
        self.dut_hci.send_command(hci.WritePageTimeout(page_timeout=0x4000))

        self.cert_hal.enable_inquiry_and_page_scan()
        address = self.cert_hal.read_own_address()

        self.dut_hci.initiate_connection(address)
        cert_acl = self.cert_hal.accept_connection()
        dut_acl = self.dut_hci.complete_connection()

        # Send ACL Data
        dut_acl.send_first(b'Just SomeAclData')
        cert_acl.send_first(b'Just SomeMoreAclData')

        assertThat(self.cert_hal.get_acl_stream()).emits(lambda packet: b'SomeAclData' in packet.payload)
        assertThat(self.dut_hci.get_raw_acl_stream()).emits(lambda packet: b'SomeMoreAclData' in packet.payload)

    def test_connection_cert_connects(self):
        self.cert_hal.send_hci_command(hci.WritePageTimeout(page_timeout=0x4000))

        self.dut_hci.enable_inquiry_and_page_scan()
        address = self.dut_hci.read_own_address()

        self.cert_hal.initiate_connection(address)
        dut_acl = self.dut_hci.accept_connection()
        cert_acl = self.cert_hal.complete_connection()

        # Send ACL Data
        dut_acl.send_first(b'This is just SomeAclData')
        cert_acl.send_first(b'This is just SomeMoreAclData')

        assertThat(self.cert_hal.get_acl_stream()).emits(lambda packet: b'SomeAclData' in packet.payload)
        assertThat(self.dut_hci.get_raw_acl_stream()).emits(lambda packet: b'SomeMoreAclData' in packet.payload)


if __name__ == '__main__':
    test_runner.main()
