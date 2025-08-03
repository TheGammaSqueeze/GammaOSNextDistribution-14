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

from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.matchers import ScanningMatchers
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from blueberry.facade import common_pb2 as common
from blueberry.facade import rootservice_pb2 as facade_rootservice
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import hci_facade_pb2 as hci_facade
from blueberry.facade.hci import le_scanning_manager_facade_pb2 as le_scanning_facade
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.hci.le_scanning_manager_facade_pb2 import ScanningCallbackMsgType
from blueberry.facade.hci.le_scanning_manager_facade_pb2 import ScanningStatus
from mobly import test_runner
import hci_packets as hci


class LeScanningManagerTestBase():

    def setup_test(self, cert, dut):
        self.cert = cert
        self.dut = dut
        self.dut.callback_event_stream = EventStream(
            self.dut.hci_le_scanning_manager.FetchCallbackEvents(empty_proto.Empty()))
        self.dut.advertising_report_stream = EventStream(
            self.dut.hci_le_scanning_manager.FetchAdvertisingReports(empty_proto.Empty()))

    def teardown_test(self):
        if self.dut.callback_event_stream is not None:
            safeClose(self.dut.callback_event_stream)
        else:
            logging.info("DUT: Callback Event Stream is None!")
        if self.dut.advertising_report_stream is not None:
            safeClose(self.dut.advertising_report_stream)
        else:
            logging.info("DUT: Advertising Report Stream is None!")

    def register_for_event(self, event_code):
        msg = hci_facade.EventCodeMsg(code=int(event_code))
        self.cert.hci.RegisterEventHandler(msg)

    def register_for_le_event(self, event_code):
        msg = hci_facade.LeSubeventCodeMsg(code=int(event_code))
        self.cert.hci.RegisterLeEventHandler(msg)

    def enqueue_hci_command(self, command, expect_complete):
        cmd_bytes = bytes(command.serialize())
        cmd = common.Data(payload=cmd_bytes)
        if (expect_complete):
            self.cert.hci.EnqueueCommandWithComplete(cmd)
        else:
            self.cert.hci.EnqueueCommandWithStatus(cmd)

    def set_address_policy_with_static_address(self):
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(b'D0:05:04:03:02:01')),
                type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)
        cert_privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(b'C0:05:04:03:02:01')),
                type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(cert_privacy_policy)

    def test_le_ad_scan_dut_scans(self):
        self.set_address_policy_with_static_address()
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

        self.dut.advertising_report_stream.assert_event_occurs(lambda packet: b'Im_The_CERT' in packet.event)

        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)

    def test_register_scanner_callback(self):
        self.set_address_policy_with_static_address()
        register_request = le_scanning_facade.RegisterScannerRequest(uuid=123)
        self.dut.hci_le_scanning_manager.RegisterScanner(register_request)
        assertThat(self.dut.callback_event_stream).emits(
            ScanningMatchers.ScanningCallbackMsg(ScanningCallbackMsgType.SCANNER_REGISTERED,
                                                 ScanningStatus.SCAN_SUCCESS, 123))

    def test_register_scanner_with_same_uuid(self):
        self.set_address_policy_with_static_address()
        register_request = le_scanning_facade.RegisterScannerRequest(uuid=123)
        self.dut.hci_le_scanning_manager.RegisterScanner(register_request)
        assertThat(self.dut.callback_event_stream).emits(
            ScanningMatchers.ScanningCallbackMsg(ScanningCallbackMsgType.SCANNER_REGISTERED,
                                                 ScanningStatus.SCAN_SUCCESS, 123))
        self.dut.hci_le_scanning_manager.RegisterScanner(register_request)
        assertThat(self.dut.callback_event_stream).emits(
            ScanningMatchers.ScanningCallbackMsg(ScanningCallbackMsgType.SCANNER_REGISTERED,
                                                 ScanningStatus.SCAN_INTERNAL_ERROR, 123))

    def test_set_scan_parameters_callback(self):
        self.set_address_policy_with_static_address()
        set_scan_parameters_request = le_scanning_facade.SetScanParametersRequest(
            scanner_id=0x01, scan_type=le_scanning_facade.LeScanType.ACTIVE, scan_interval=0x10, scan_window=0x04)
        self.dut.hci_le_scanning_manager.SetScanParameters(set_scan_parameters_request)

        assertThat(self.dut.callback_event_stream).emits(
            ScanningMatchers.ScanningCallbackMsg(ScanningCallbackMsgType.SET_SCANNER_PARAMETER_COMPLETE,
                                                 ScanningStatus.SCAN_SUCCESS, 0x01))

    def test_set_scan_parameters_with_invalid_parameter(self):
        self.set_address_policy_with_static_address()
        set_scan_parameters_request = le_scanning_facade.SetScanParametersRequest(
            scanner_id=0x01, scan_type=le_scanning_facade.LeScanType.ACTIVE, scan_interval=0x00, scan_window=0x00)
        self.dut.hci_le_scanning_manager.SetScanParameters(set_scan_parameters_request)

        assertThat(self.dut.callback_event_stream).emits(
            ScanningMatchers.ScanningCallbackMsg(ScanningCallbackMsgType.SET_SCANNER_PARAMETER_COMPLETE,
                                                 ScanningStatus.SCAN_ILLEGAL_PARAMETER))

    def test_active_scan(self):
        self.set_address_policy_with_static_address()
        # CERT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Scan response data')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            scan_response=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)

        set_scan_parameters_request = le_scanning_facade.SetScanParametersRequest(
            scanner_id=0x01, scan_type=le_scanning_facade.LeScanType.ACTIVE, scan_interval=0x10, scan_window=0x04)
        self.dut.hci_le_scanning_manager.SetScanParameters(set_scan_parameters_request)
        scan_request = le_scanning_facade.ScanRequest(start=True)
        self.dut.hci_le_scanning_manager.Scan(scan_request)

        self.dut.advertising_report_stream.assert_event_occurs(lambda packet: b'Scan response data' in packet.event)

        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)

    def test_passive_scan(self):
        self.set_address_policy_with_static_address()
        # CERT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Scan response data')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            scan_response=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)

        set_scan_parameters_request = le_scanning_facade.SetScanParametersRequest(
            scanner_id=0x01, scan_type=le_scanning_facade.LeScanType.PASSIVE, scan_interval=0x10, scan_window=0x04)
        self.dut.hci_le_scanning_manager.SetScanParameters(set_scan_parameters_request)
        scan_request = le_scanning_facade.ScanRequest(start=True)
        self.dut.hci_le_scanning_manager.Scan(scan_request)

        self.dut.advertising_report_stream.assert_event_occurs_at_most(
            lambda packet: b'Scan response data' in packet.event, 0)

        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)


class LeScanningManagerTest(gd_base_test.GdBaseTestClass, LeScanningManagerTestBase):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI_INTERFACES')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.set_controller_properties_path(self,
                                                                    'blueberry/tests/gd/hci/le_legacy_config.json')
        gd_base_test.GdBaseTestClass.setup_test(self)
        LeScanningManagerTestBase.setup_test(self, self.cert, self.dut)

    def teardown_test(self):
        LeScanningManagerTestBase.teardown_test(self)
        gd_base_test.GdBaseTestClass.teardown_test(self)


if __name__ == '__main__':
    test_runner.main()
