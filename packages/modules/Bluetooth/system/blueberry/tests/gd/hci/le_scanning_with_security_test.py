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

from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import hci_facade_pb2 as hci_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.hci import le_scanning_manager_facade_pb2 as le_scanning_facade
from blueberry.facade import common_pb2 as common
from mobly import test_runner
import hci_packets as hci


class LeScanningWithSecurityTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='SECURITY', cert_module='HCI_INTERFACES')

    def register_for_event(self, event_code):
        msg = hci_facade.EventCodeMsg(code=int(event_code))
        self.cert.hci.RegisterEventHandler(msg)

    def register_for_le_event(self, event_code):
        msg = hci_facade.LeSubeventCodeMsg(code=int(event_code))
        self.cert.hci.RegisterLeEventHandler(msg)

    def enqueue_hci_command(self, command, expect_complete):
        cmd_bytes = bytes(command.serialize())
        cmd = common.Data(command=cmd_bytes)
        if (expect_complete):
            self.cert.hci.EnqueueCommandWithComplete(cmd)
        else:
            self.cert.hci.EnqueueCommandWithStatus(cmd)

    def test_le_ad_scan_dut_scans(self):
        """
            Verify that the IUT address policy is correctly initiated by SecurityManager, and we can start a scan.
        """
        cert_privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(b'C0:05:04:03:02:01')),
                type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(cert_privacy_policy)
        with EventStream(
                # DUT Scans
                self.dut.hci_le_scanning_manager.FetchAdvertisingReports(empty_proto.Empty()
                                                                        )) as advertising_event_stream:

            # CERT Advertises
            gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_CERT!')))
            gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
            gap_scan_name = hci.GapData(data_type=hci.GapDataType.SHORTENED_LOCAL_NAME, data=list(bytes(b'CERT!')))
            gap_scan_data = le_advertising_facade.GapDataMsg(data=gap_scan_name.serialize())
            config = le_advertising_facade.AdvertisingConfig(
                advertisement=[gap_data],
                scan_response=[gap_scan_data],
                interval_min=512,
                interval_max=768,
                advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
                own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
                channel_map=7,
                filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
            request = le_advertising_facade.CreateAdvertiserRequest(config=config)

            create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)

            scan_request = le_scanning_facade.ScanRequest(start=True)
            self.dut.hci_le_scanning_manager.Scan(scan_request)

            assertThat(advertising_event_stream).emits(lambda packet: b'Im_The_CERT' in packet.event)

            remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
            self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)


if __name__ == '__main__':
    test_runner.main()
