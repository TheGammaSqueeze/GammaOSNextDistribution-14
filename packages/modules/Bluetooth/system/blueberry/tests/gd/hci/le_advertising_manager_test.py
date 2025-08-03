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

from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.matchers import AdvertisingMatchers
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from blueberry.facade import common_pb2 as common
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import controller_facade_pb2 as controller_facade
from blueberry.facade.hci import \
    le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.hci.le_advertising_manager_facade_pb2 import AdvertisingCallbackMsgType
from blueberry.facade.hci.le_advertising_manager_facade_pb2 import AdvertisingStatus

from mobly import asserts, test_runner

from blueberry.utils import bluetooth
import hci_packets as hci


class LeAdvertisingManagerTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.cert_hci = PyHci(self.cert, acl_streaming=True)
        self.dut.callback_event_stream = EventStream(
            self.dut.hci_le_advertising_manager.FetchCallbackEvents(empty_proto.Empty()))
        self.dut.address_event_stream = EventStream(
            self.dut.hci_le_advertising_manager.FetchAddressEvents(empty_proto.Empty()))

    def teardown_test(self):
        self.cert_hci.close()
        if self.dut.callback_event_stream is not None:
            safeClose(self.dut.callback_event_stream)
        else:
            logging.info("DUT: Callback Event Stream is None!")
        if self.dut.address_event_stream is not None:
            safeClose(self.dut.address_event_stream)
        else:
            logging.info("DUT: address Event Stream is None!")
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def set_address_policy_with_static_address(self):
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(b'd0:05:04:03:02:01')),
                type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=b'\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00',
            minimum_rotation_time=0,
            maximum_rotation_time=0)
        self.dut.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(privacy_policy)

    def create_advertiser(self):
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
        return create_response

    def test_le_ad_scan_dut_advertises(self):
        self.set_address_policy_with_static_address()
        self.cert_hci.register_for_le_events(hci.SubeventCode.ADVERTISING_REPORT,
                                             hci.SubeventCode.EXTENDED_ADVERTISING_REPORT)

        # CERT Scans
        self.cert_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0C:05:04:03:02:01')))

        self.cert_hci.send_command(
            hci.LeSetExtendedScanParameters(own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                            scanning_filter_policy=hci.LeScanningFilterPolicy.ACCEPT_ALL,
                                            scanning_phys=1,
                                            parameters=[
                                                hci.PhyScanParameters(le_scan_type=hci.LeScanType.ACTIVE,
                                                                      le_scan_interval=40,
                                                                      le_scan_window=20)
                                            ]))

        self.cert_hci.send_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.ENABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))

        create_response = self.create_advertiser()

        assertThat(self.cert_hci.get_le_event_stream()).emits(lambda packet: b'Im_The_DUT' in packet.payload)

        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
        self.dut.hci_le_advertising_manager.RemoveAdvertiser(remove_request)
        self.cert_hci.send_command(
            hci.LeSetScanEnable(le_scan_enable=hci.Enable.DISABLED, filter_duplicates=hci.Enable.DISABLED))

    def test_extended_create_advertises(self):
        self.set_address_policy_with_static_address()
        self.cert_hci.register_for_le_events(hci.SubeventCode.ADVERTISING_REPORT,
                                             hci.SubeventCode.EXTENDED_ADVERTISING_REPORT)

        # CERT Scans
        self.cert_hci.send_command(hci.LeSetRandomAddress(random_address=bluetooth.Address('0C:05:04:03:02:01')))

        self.cert_hci.send_command(
            hci.LeSetExtendedScanParameters(own_address_type=hci.OwnAddressType.RANDOM_DEVICE_ADDRESS,
                                            scanning_filter_policy=hci.LeScanningFilterPolicy.ACCEPT_ALL,
                                            scanning_phys=1,
                                            parameters=[
                                                hci.PhyScanParameters(le_scan_type=hci.LeScanType.ACTIVE,
                                                                      le_scan_interval=40,
                                                                      le_scan_window=20)
                                            ]))

        self.cert_hci.send_command(
            hci.LeSetExtendedScanEnable(enable=hci.Enable.ENABLED,
                                        filter_duplicates=hci.FilterDuplicates.DISABLED,
                                        duration=0,
                                        period=0))

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
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(advertising_config=config,
                                                                          connectable=True,
                                                                          scannable=False,
                                                                          directed=False,
                                                                          high_duty_directed_connectable=False,
                                                                          legacy_pdus=True,
                                                                          anonymous=False,
                                                                          include_tx_power=True,
                                                                          use_le_coded_phy=False,
                                                                          secondary_max_skip=0x00,
                                                                          secondary_advertising_phy=0x01,
                                                                          sid=0x00,
                                                                          enable_scan_request_notifications=0x00)
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        create_response = self.dut.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)

        assertThat(self.cert_hci.get_le_event_stream()).emits(lambda packet: b'Im_The_DUT' in packet.payload)

        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=create_response.advertiser_id)
        self.dut.hci_le_advertising_manager.RemoveAdvertiser(remove_request)
        self.cert_hci.send_command(
            hci.LeSetScanEnable(le_scan_enable=hci.Enable.DISABLED, filter_duplicates=hci.Enable.DISABLED))

    def test_advertising_set_started_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.ADVERTISING_SET_STARTED,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS,
                                                       0x00))

    def test_enable_advertiser_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        enable_advertiser_request = le_advertising_facade.EnableAdvertiserRequest(
            advertiser_id=create_response.advertiser_id, enable=True)
        self.dut.hci_le_advertising_manager.EnableAdvertiser(enable_advertiser_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.ADVERTISING_ENABLED,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS,
                                                       0x01))

    def test_disable_advertiser_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        disable_advertiser_request = le_advertising_facade.EnableAdvertiserRequest(
            advertiser_id=create_response.advertiser_id, enable=False)
        self.dut.hci_le_advertising_manager.EnableAdvertiser(disable_advertiser_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.ADVERTISING_ENABLED,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS,
                                                       0x00))

    def test_set_advertising_data_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT2')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())

        set_data_request = le_advertising_facade.SetDataRequest(advertiser_id=create_response.advertiser_id,
                                                                set_scan_rsp=False,
                                                                data=[gap_data])
        self.dut.hci_le_advertising_manager.SetData(set_data_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.ADVERTISING_DATA_SET,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS))

    def test_set_scan_response_data_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())

        set_data_request = le_advertising_facade.SetDataRequest(advertiser_id=create_response.advertiser_id,
                                                                set_scan_rsp=True,
                                                                data=[gap_data])
        self.dut.hci_le_advertising_manager.SetData(set_data_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.SCAN_RESPONSE_DATA_SET,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS))

    def test_set_parameters_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()

        # The Host shall not issue set parameters command when advertising is enabled
        disable_advertiser_request = le_advertising_facade.EnableAdvertiserRequest(
            advertiser_id=create_response.advertiser_id, enable=False)
        self.dut.hci_le_advertising_manager.EnableAdvertiser(disable_advertiser_request)

        config = le_advertising_facade.AdvertisingConfig(
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)

        set_parameters_request = le_advertising_facade.SetParametersRequest(advertiser_id=create_response.advertiser_id,
                                                                            config=config)
        self.dut.hci_le_advertising_manager.SetParameters(set_parameters_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.ADVERTISING_PARAMETERS_UPDATED,
                                                       create_response.advertiser_id, AdvertisingStatus.ADV_SUCCESS))

    def test_set_periodic_parameters_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()

        config = le_advertising_facade.PeriodicAdvertisingParameters(
            min_interval=512,
            max_interval=768,
            advertising_property=le_advertising_facade.AdvertisingProperty.INCLUDE_TX_POWER)

        set_periodic_parameters_request = le_advertising_facade.SetPeriodicParametersRequest(
            advertiser_id=create_response.advertiser_id, config=config)
        self.dut.hci_le_advertising_manager.SetPeriodicParameters(set_periodic_parameters_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(
                AdvertisingCallbackMsgType.PERIODIC_ADVERTISING_PARAMETERS_UPDATED, create_response.advertiser_id))

    def test_set_periodic_data_callback(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT2')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())

        set_periodic_data_request = le_advertising_facade.SetPeriodicDataRequest(
            advertiser_id=create_response.advertiser_id, data=[gap_data])
        self.dut.hci_le_advertising_manager.SetPeriodicData(set_periodic_data_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.PERIODIC_ADVERTISING_DATA_SET,
                                                       create_response.advertiser_id))

    def test_enable_periodic_advertising_callback(self):
        check_feature = self.dut.hci_controller.SupportsBlePeriodicAdvertising(empty_proto.Empty())
        if not check_feature.supported:
            asserts.skip("Periodic advertising not supported.")

        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        enable_periodic_advertising_request = le_advertising_facade.EnablePeriodicAdvertisingRequest(
            advertiser_id=create_response.advertiser_id, enable=True, include_adi=False)
        self.dut.hci_le_advertising_manager.EnablePeriodicAdvertising(enable_periodic_advertising_request)

        assertThat(self.dut.callback_event_stream).emits(
            AdvertisingMatchers.AdvertisingCallbackMsg(AdvertisingCallbackMsgType.PERIODIC_ADVERTISING_ENABLED,
                                                       create_response.advertiser_id))

    def test_get_own_address(self):
        self.set_address_policy_with_static_address()
        create_response = self.create_advertiser()
        get_own_address_request = le_advertising_facade.GetOwnAddressRequest(
            advertiser_id=create_response.advertiser_id)
        self.dut.hci_le_advertising_manager.GetOwnAddress(get_own_address_request)
        address_with_type = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'd0:05:04:03:02:01')), type=common.RANDOM_DEVICE_ADDRESS)
        assertThat(self.dut.address_event_stream).emits(
            AdvertisingMatchers.AddressMsg(AdvertisingCallbackMsgType.OWN_ADDRESS_READ, create_response.advertiser_id,
                                           address_with_type))


if __name__ == '__main__':
    test_runner.main()
