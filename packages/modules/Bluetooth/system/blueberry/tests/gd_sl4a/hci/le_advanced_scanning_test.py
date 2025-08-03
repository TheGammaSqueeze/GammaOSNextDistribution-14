#!/usr/bin/env python3
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

import queue
import logging

from google.protobuf import empty_pb2 as empty_proto

import hci_packets as hci
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade import common_pb2 as common
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd_sl4a.lib import gd_sl4a_base_test
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_scan_settings_modes, ble_address_types, scan_result, ble_scan_settings_phys, ble_scan_settings_callback_types
from blueberry.tests.gd_sl4a.lib.ble_lib import generate_ble_scan_objects

from mobly import test_runner


class LeAdvancedScanningTest(gd_sl4a_base_test.GdSl4aBaseTestClass):

    def setup_class(self):
        super().setup_class(cert_module='HCI_INTERFACES')
        self.default_timeout = 60  # seconds

    def setup_test(self):
        super().setup_test()

    def teardown_test(self):
        super().teardown_test()

    def __get_test_irk(self):
        return bytes(
            bytearray([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10]))

    def _set_cert_privacy_policy_with_random_address(self, random_address):
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=bytes(random_address, encoding='utf8')),
                type=common.RANDOM_DEVICE_ADDRESS))
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)

    def _set_cert_privacy_policy_with_random_address_but_advertise_resolvable(self, irk):
        random_address_bytes = "DD:34:02:05:5C:EE".encode()
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_RESOLVABLE_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=random_address_bytes), type=common.RANDOM_DEVICE_ADDRESS),
            rotation_irk=irk)
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)
        # Bluetooth MAC address must be upper case
        return random_address_bytes.decode('utf-8').upper()

    def __advertise_rpa_random_policy(self, legacy_pdus, irk):
        DEVICE_NAME = 'Im_The_CERT!'
        logging.info("Getting public address")
        ADDRESS = self._set_cert_privacy_policy_with_random_address_but_advertise_resolvable(irk)
        logging.info("Done %s" % ADDRESS)

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(
            include_tx_power=True,
            connectable=True,
            legacy_pdus=legacy_pdus,
            advertising_config=config,
            secondary_advertising_phy=ble_scan_settings_phys["1m"])
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        logging.info("Creating %s PDU advertiser..." % ("Legacy" if legacy_pdus else "Extended"))
        create_response = self.cert.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)
        logging.info("%s PDU advertiser created." % ("Legacy" if legacy_pdus else "Extended"))
        return (ADDRESS, create_response)

    def _advertise_rpa_random_legacy_pdu(self, irk):
        return self.__advertise_rpa_random_policy(True, irk)

    def _advertise_rpa_random_extended_pdu(self, irk):
        return self.__advertise_rpa_random_policy(False, irk)

    def _set_cert_privacy_policy_with_public_address(self):
        public_address_bytes = self.cert.hci_controller.GetMacAddress(empty_proto.Empty()).address
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_PUBLIC_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=public_address_bytes), type=common.PUBLIC_DEVICE_ADDRESS))
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)
        # Bluetooth MAC address must be upper case
        return public_address_bytes.decode('utf-8').upper()

    def _set_cert_privacy_policy_with_public_address_but_advertise_resolvable(self, irk):
        public_address_bytes = self.cert.hci_controller.GetMacAddress(empty_proto.Empty()).address
        private_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_RESOLVABLE_ADDRESS,
            address_with_type=common.BluetoothAddressWithType(
                address=common.BluetoothAddress(address=public_address_bytes), type=common.PUBLIC_DEVICE_ADDRESS),
            rotation_irk=irk)
        self.cert.hci_le_initiator_address.SetPrivacyPolicyForInitiatorAddress(private_policy)
        # Bluetooth MAC address must be upper case
        return public_address_bytes.decode('utf-8').upper()

    def __advertise_rpa_public_policy(self, legacy_pdus, irk):
        DEVICE_NAME = 'Im_The_CERT!'
        logging.info("Getting public address")
        ADDRESS = self._set_cert_privacy_policy_with_public_address_but_advertise_resolvable(irk)
        logging.info("Done %s" % ADDRESS)

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(
            include_tx_power=True,
            connectable=True,
            legacy_pdus=legacy_pdus,
            advertising_config=config,
            secondary_advertising_phy=ble_scan_settings_phys["1m"])
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        logging.info("Creating %s PDU advertiser..." % ("Legacy" if legacy_pdus else "Extended"))
        create_response = self.cert.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)
        logging.info("%s PDU advertiser created." % ("Legacy" if legacy_pdus else "Extended"))
        return (ADDRESS, create_response)

    def _advertise_rpa_public_legacy_pdu(self, irk):
        return self.__advertise_rpa_public_policy(True, irk)

    def _advertise_rpa_public_extended_pdu(self, irk):
        return self.__advertise_rpa_public_policy(False, irk)

    def _wait_for_scan_result_event(self, expected_event_name):
        try:
            # Verify if there is scan result
            event_info = self.dut.ed.pop_event(expected_event_name, self.default_timeout)
            # Print out scan result
            mac_address = event_info['data']['Result']['deviceInfo']['address']
            logging.info("Filter advertisement with address {}".format(mac_address))
            return True
        except queue.Empty as error:
            logging.error("Could not find initial advertisement.")
            return False

    def _stop_advertising(self, advertiser_id):
        logging.info("Stop advertising")
        remove_request = le_advertising_facade.RemoveAdvertiserRequest(advertiser_id=advertiser_id)
        self.cert.hci_le_advertising_manager.RemoveAdvertiser(remove_request)
        logging.info("Stopped advertising")

    def _stop_scanning(self, scan_callback):
        logging.info("Stop scanning")
        self.dut.sl4a.bleStopBleScan(scan_callback)
        logging.info("Stopped scanning")

    def test_scan_filter_device_public_address_with_irk_legacy_pdu(self):
        """
        The cert side will advertise a RRPA generated from the test IRK using Legacy PDU

        DUT will scan for the device using the Identity Address + Address Type + IRK

        Results received via ScanCallback
        """
        PUBLIC_ADDRESS, create_response = self._advertise_rpa_public_legacy_pdu(self.__get_test_irk())
        addr_type = ble_address_types["public"]
        logging.info("Start scanning for PUBLIC_ADDRESS %s with address type %d and IRK %s" %
                     (PUBLIC_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))

        # Setup SL4A DUT side to scan
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(PUBLIC_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_public_address_with_irk_legacy_pdu_pending_intent(self):
        """
        The cert side will advertise a RRPA generated from the test IRK using Legacy PDU

        DUT will scan for the device using the Identity Address + Address Type + IRK

        Results received via PendingIntent
        """
        PUBLIC_ADDRESS, create_response = self._advertise_rpa_public_legacy_pdu(self.__get_test_irk())
        addr_type = ble_address_types["public"]
        logging.info("Start scanning for PUBLIC_ADDRESS %s with address type %d and IRK %s" %
                     (PUBLIC_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))

        # Setup SL4A DUT side to scan
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        # The event name needs to be set to this otherwise the index iterates from the scancallbacks
        # being run consecutively.  This is a PendingIntent callback but it hooks into the
        # ScanCallback and uses just the 1 for the index.
        expected_event_name = "BleScan1onScanResults"

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(PUBLIC_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScanPendingIntent(filter_list, scan_settings)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_public_address_with_irk_extended_pdu(self):
        """
        The cert side will advertise a RRPA generated from the test IRK using Extended PDU

        DUT will scan for the device using the Identity Address + Address Type + IRK

        Results received via PendingIntent
        """
        PUBLIC_ADDRESS, create_response = self._advertise_rpa_public_extended_pdu(self.__get_test_irk())
        addr_type = ble_address_types["public"]
        logging.info("Start scanning for PUBLIC_ADDRESS %s with address type %d and IRK %s" %
                     (PUBLIC_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))

        # Setup SL4A DUT side to scan
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(PUBLIC_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_name_legacy_pdu(self):
        """
        The cert side will advertise using PUBLIC address and device name data on legacy PDU

        DUT will scan for the device using the Device Name

        Results received via ScanCallback
        """
        # Use public address on cert side
        logging.info("Setting public address")
        DEVICE_NAME = 'Im_The_CERT!'
        public_address = self._set_cert_privacy_policy_with_public_address()
        logging.info("Set public address")

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_PUBLIC_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES,
            tx_power=20)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        logging.info("Creating advertiser")
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)
        logging.info("Created advertiser")

        # Setup SL4A DUT side to scan
        logging.info("Start scanning with public address %s" % public_address)
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceName(DEVICE_NAME)
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_random_address_legacy_pdu(self):
        """
        The cert side will advertise using RANDOM STATIC address with legacy PDU

        DUT will scan for the device using the RANDOM STATIC Address of the advertising device

        Results received via ScanCallback
        """
        # Use random address on cert side
        logging.info("Setting random address")
        RANDOM_ADDRESS = 'D0:05:04:03:02:01'
        DEVICE_NAME = 'Im_The_CERT!'
        self._set_cert_privacy_policy_with_random_address(RANDOM_ADDRESS)
        logging.info("Set random address")

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
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
        logging.info("Creating advertiser")
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)
        logging.info("Created advertiser")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d" % (RANDOM_ADDRESS, addr_type))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressAndType(RANDOM_ADDRESS, int(addr_type))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_public_address_extended_pdu(self):
        """
        The cert side will advertise using PUBLIC address with Extended PDU

        DUT will scan for the device using the PUBLIC Address of the advertising device

        Results received via ScanCallback
        """
        # Use public address on cert side
        logging.info("Setting public address")
        DEVICE_NAME = 'Im_The_CERT!'
        public_address = self._set_cert_privacy_policy_with_public_address()
        logging.info("Set public address")

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_PUBLIC_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(
            advertising_config=config, secondary_advertising_phy=ble_scan_settings_phys["1m"])
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        logging.info("Creating advertiser")
        create_response = self.cert.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)
        logging.info("Created advertiser")

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["public"]
        logging.info("Start scanning for PUBLIC_ADDRESS %s with address type %d" % (public_address, addr_type))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressAndType(public_address, int(addr_type))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_public_address_with_irk_extended_pdu_pending_intent(self):
        """
        The cert side will advertise using RRPA with Extended PDU

        DUT will scan for the device using the pre-shared PUBLIC ADDRESS of the advertising
        device + IRK

        Results received via PendingIntent
        """
        PUBLIC_ADDRESS, create_response = self._advertise_rpa_public_extended_pdu(self.__get_test_irk())

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["public"]
        logging.info("Start scanning for PUBLIC_ADDRESS %s with address type %d and IRK %s" %
                     (PUBLIC_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        # Hard code here since callback index iterates and will cause this to fail if ran
        # Second as the impl in SL4A sends this since its a single callback for broadcast.
        expected_event_name = "BleScan1onScanResults"

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(PUBLIC_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScanPendingIntent(filter_list, scan_settings)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_random_address_with_irk_extended_pdu(self):
        """
        The cert side will advertise using RRPA with Extended PDU

        DUT will scan for the device using the pre-shared RANDOM STATIC ADDRESS of the advertising
        device + IRK

        Results received via ScanCallback
        """
        RANDOM_ADDRESS, create_response = self._advertise_rpa_random_extended_pdu(self.__get_test_irk())

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d and IRK %s" %
                     (RANDOM_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Setup SL4A DUT filter
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(RANDOM_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_random_address_with_irk_extended_pdu_pending_intent(self):
        """
        The cert side will advertise using RRPA with Extended PDU

        DUT will scan for the device using the pre-shared RANDOM STATIC ADDRESS of the advertising
        device + IRK

        Results received via PendingIntent
        """
        RANDOM_ADDRESS, create_response = self._advertise_rpa_random_extended_pdu(self.__get_test_irk())

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d and IRK %s" %
                     (RANDOM_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        # Hard code here since callback index iterates and will cause this to fail if ran
        # Second as the impl in SL4A sends this since its a single callback for broadcast.
        expected_event_name = "BleScan1onScanResults"

        # Setup SL4A DUT filter
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(RANDOM_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleStartBleScanPendingIntent(filter_list, scan_settings)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_random_address_with_irk_extended_pdu_scan_twice(self):
        """
        The cert side will advertise using RRPA with Extended PDU

        DUT will scan for the device using the pre-shared RANDOM STATIC ADDRESS of the advertising
        device + IRK

        DUT will stop scanning, then start scanning again for results

        Results received via ScanCallback
        """
        RANDOM_ADDRESS, create_response = self._advertise_rpa_random_extended_pdu(self.__get_test_irk())

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d and IRK %s" %
                     (RANDOM_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(RANDOM_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning...again")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_device_random_address_with_irk_extended_pdu_pending_intent_128_640(self):
        """
        The CERT side will advertise an RPA derived from the IRK.

        The DUT (SL4A) side will scan for a RPA with matching IRK.

        Adjust the scan intervals to Digital Carkey specific timings.
        """
        DEVICE_NAME = 'Im_The_CERT!'
        logging.info("Getting public address")
        RANDOM_ADDRESS = self._set_cert_privacy_policy_with_random_address_but_advertise_resolvable(
            self.__get_test_irk())
        logging.info("Done %s" % RANDOM_ADDRESS)

        legacy_pdus = False

        # Setup cert side to advertise
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                               data=list(bytes(DEVICE_NAME, encoding='utf8')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=128,
            interval_max=640,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_RANDOM_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        extended_config = le_advertising_facade.ExtendedAdvertisingConfig(
            include_tx_power=True,
            connectable=True,
            legacy_pdus=legacy_pdus,
            advertising_config=config,
            secondary_advertising_phy=ble_scan_settings_phys["1m"])
        request = le_advertising_facade.ExtendedCreateAdvertiserRequest(config=extended_config)
        logging.info("Creating %s PDU advertiser..." % ("Legacy" if legacy_pdus else "Extended"))
        create_response = self.cert.hci_le_advertising_manager.ExtendedCreateAdvertiser(request)
        logging.info("%s PDU advertiser created." % ("Legacy" if legacy_pdus else "Extended"))

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d and IRK %s" %
                     (RANDOM_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['ambient_discovery'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        # Hard code here since callback index iterates and will cause this to fail if ran
        # Second as the impl in SL4A sends this since its a single callback for broadcast.
        expected_event_name = "BleScan1onScanResults"

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(RANDOM_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScanPendingIntent(filter_list, scan_settings)
        logging.info("Started scanning")

        # Wait for results
        got_result = self._wait_for_scan_result_event(expected_event_name)

        # Test over
        self._stop_scanning(scan_callback)
        self._stop_advertising(create_response.advertiser_id)

        assertThat(got_result).isTrue()

    def test_scan_filter_lost_random_address_with_irk(self):
        RANDOM_ADDRESS, create_response = self._advertise_rpa_random_extended_pdu(self.__get_test_irk())

        # Setup SL4A DUT side to scan
        addr_type = ble_address_types["random"]
        logging.info("Start scanning for RANDOM_ADDRESS %s with address type %d and IRK %s" %
                     (RANDOM_ADDRESS, addr_type, self.__get_test_irk().decode("utf-8")))
        self.dut.sl4a.bleSetScanSettingsScanMode(ble_scan_settings_modes['low_latency'])
        self.dut.sl4a.bleSetScanSettingsLegacy(False)
        self.dut.sl4a.bleSetScanSettingsCallbackType(ble_scan_settings_callback_types['found_and_lost'])
        filter_list, scan_settings, scan_callback = generate_ble_scan_objects(self.dut.sl4a)
        expected_event_name = scan_result.format(scan_callback)

        # Start scanning on SL4A DUT side
        self.dut.sl4a.bleSetScanFilterDeviceAddressTypeAndIrk(RANDOM_ADDRESS, int(addr_type),
                                                              self.__get_test_irk().decode("utf-8"))
        self.dut.sl4a.bleBuildScanFilter(filter_list)
        self.dut.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        logging.info("Started scanning")

        # Wait for found event to ensure scanning is started before stopping the advertiser
        # to trigger lost event, else the lost event might not be caught by the test
        got_found_result = self._wait_for_scan_result_event(expected_event_name)
        assertThat(got_found_result).isTrue()

        self._stop_advertising(create_response.advertiser_id)

        # Wait for lost event
        got_lost_result = self._wait_for_scan_result_event(expected_event_name)
        assertThat(got_lost_result).isTrue()

        # Test over
        self._stop_scanning(scan_callback)


if __name__ == '__main__':
    test_runner.main()
