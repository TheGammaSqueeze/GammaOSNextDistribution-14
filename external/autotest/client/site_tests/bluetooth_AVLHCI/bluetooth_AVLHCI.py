# Lint as: python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.bluetooth.hcitool import Hcitool
from autotest_lib.client.common_lib.cros.bluetooth import chipinfo
from autotest_lib.client.cros.multimedia import bluetooth_facade


class bluetooth_AVLHCI(test.test):
    """Test bluetooth avl HCI requirements."""
    version = 1
    MIN_ACL_BUFFER_SIZE = 1021
    ACL_DATA_PACKET_LENGTH_VALUE_INDEX = 1
    MIN_ACL_PACKETS_NUMBER = 4
    MIN_ACL_PACKETS_NUMBER_OPTIONAL = 6
    TOTAL_NUM_ACL_DATA_PACKETS_VALUE_INDEX = 3
    MIN_SCO_PACKETS_NUMBER = 6
    TOTAL_NUM_SYNCHRONOUS_DATA_PACKETS_VALUE_INDEX = 4
    NON_FLUSHABLE_PACKET_BOUNDARY_FEATURE = 'Non-flushable Packet Boundary Flag'
    ERRONEOUS_DATA_REPORTING_FEATURE = 'Erroneous Data Reporting'
    MAC_EVENT_FILTERS = [['1', '2', '00 17 C9 AA AA AA'],
                         ['1', '2', '00 17 9B AA AA AA'],
                         ['1', '2', '00 17 94 AA AA AA'],
                         ['1', '2', '00 17 95 AA AA AA'],
                         ['1', '2', '00 17 B0 AA AA AA'],
                         ['1', '2', '00 17 C0 AA AA AA'],
                         ['1', '2', '00 17 08 AA AA AA'],
                         ['1', '2', '00 16 EA AA AA AA']]

    CONTROLLER_MEMORY_FULL_STATUS_VALUE = 7
    CONTROLLER_SUCCESS_STATUS_VALUE = 0
    SCO_BUFFER_SIZE_VALUE_INDEX = 2
    MIN_SCO_BUFFER_SIZE = 60
    LE_CONTROLLER_FEATURE = 'LE Supported (Controller)'
    BR_EDR_NOT_SUPPORT_FEATURE = 'BR/EDR Not Supported'
    LE_AND_BR_EDR_CONTROLLER_FEATURE = (
            'Simultaneous LE and BR/EDR to Same Device Capable (Controller)')
    MIN_ACCEPT_LIST_SIZE_ENTRIES = 8
    BR_SECURE_CONNECTION_FEATURE = 'Secure Connections (Controller Support)'
    LE_DATA_PACKETS_LENGTH_EXTENSION_FEATURE = 'LE Data Packet Length Extension'
    LE_LINK_LAYER_PRIVACY_FEATURE = 'LL Privacy'
    MAX_PACKET_LENGTH = 251
    MIN_RESOLVING_LIST_SIZE_ENTRIES = 8
    LE_EXTENDED_ADVERTISING_FEATURE = 'LE Extended Advertising'
    LE_TWO_MEGA_PHYSICAL_CHANNEL_FEATURE = 'LE 2M PHY'
    MIN_ADVERTISEMENT_SETS_NUMBER = 10
    LE_ISOCHRONOUS_CHANNELS_FEATURE = 'Isochronous Channels (Host Support)'
    LE_POWER_CONTROL_REQUEST_FEATURE = 'LE Power Control Request'
    LE_POWER_CHANGE_INDICATION_FEATURE = 'LE Power Change Indication'
    GOOGLE_FEATURE_SPECIFICATION_VERSION = 98
    LE_ADV_RSSI_MONITORING = 'RSSI Monitoring of LE advertisements'
    LE_ADV_MONITORING = 'Advertising Monitoring of LE advertisements'

    def initialize(self):
        """Initializes Autotest."""
        self.hcitool = Hcitool()
        self.facade = bluetooth_facade.BluezFacadeLocal()

    def spec_legacy_test(self):
        """Checks Bluetooth legacy specification."""
        logging.info('* Running Bluetooth spec_legacy_test:')
        self.test_flushable_data_packets()
        self.test_erroneous_data_reporting()
        self.test_event_filter_size()
        self.test_acl_min_buffer_number()
        self.test_acl_min_buffer_number_optional()
        self.test_acl_min_buffer_size()
        self.test_sco_min_buffer_number()
        self.test_sco_min_buffer_size()

    def spec_4_0_test(self):
        """Checks Bluetooth version 4.0 specification."""
        logging.info('* Running Bluetooth spec_4_0_test:')
        self.test_low_energy_feature()
        self.test_accept_list_size()

    def spec_4_1_test(self):
        """Checks Bluetooth version 4.1 specification."""
        logging.info('* Running Bluetooth spec_4_1_test:')
        self.test_le_dual_mode_topology_feature()
        self.test_br_edr_controller_secure_connection_feature()

    def spec_4_2_test(self):
        """Checks Bluetooth version 4.2 specification."""
        logging.info('* Running Bluetooth spec_4_2_test:')
        self.test_le_data_packet_length_extension_feature()
        self.test_packet_data_length()
        self.test_le_link_layer_privacy_feature()
        self.test_resolving_list_size()

    def spec_5_0_test(self):
        """Check Bluetooth version 5.0 specification."""
        logging.info('* Running Bluetooth spec_5_0_test:')
        self.test_le_extended_advertising_feature()
        self.test_advertisement_sets_number()
        self.test_le_two_mega_physical_channel_feature()

    def spec_5_2_test(self):
        """Checks Bluetooth version 5.0 specification."""
        logging.info('* Running Bluetooth spec_5_2_test:')
        self.test_le_isochronous_channels_feature()
        self.test_le_power_control_feature()

    def hci_ext_msft_test(self):
        """Checks Microsoft Bluetooth HCI command execution."""
        logging.info('* Running Bluetooth hci_ext_msft_test:')
        self.test_hci_vs_msft_read_supported_features()

    def hci_ext_aosp_test(self):
        """Checks Android Bluetooth HCI command execution."""
        logging.info('* Running Bluetooth hci_ext_aosp_test:')
        self.test_aosp_quality_report()
        self.test_le_apcf()
        self.test_le_batch_scan_and_events()
        self.test_le_extended_set_scan_parameters()
        self.test_le_get_controller_activity_energy_info()
        self.test_get_controller_debug_info_sub_event()

    def assert_not_support(self, feature, supported_features):
        """Verifies that the feature is not supported.

        @param feature: The feature which should be unsupported.
        @param supported_features: List of supported features.

        @raise error.TestFail: If the feature is supported.
        """
        if feature in supported_features:
            raise error.TestFail(feature + ' should not be supported')
        logging.info('%s is not supported as expected.', feature)

    def assert_support(self, feature, supported_features):
        """Verifies that the feature is supported.

        @param feature: The feature which should be supported.
        @param supported_features: List of supported features.

        @raise error.TestFail: If the feature is unsupported.
        """
        if feature not in supported_features:
            raise error.TestFail(feature + ' should be supported')
        logging.info('%s is supported.', feature)

    def assert_equal(self, actual, expected, value_name):
        """Verifies that actual value is equal to expected value.

        @param actual: The value we got.
        @param expected: The value we expected.
        @param value_name: The name of the value. It is used for TestFail
        message.

        @raise error.TestFail: If the values are unequal.
        """
        if actual != expected:
            raise error.TestFail('%s: Got %s, expected %s' %
                                 (value_name, actual, expected))
        logging.info('%s = %d, which is expected.' % (value_name, actual))

    def assert_greater_equal(self, value, threshold, value_name):
        """Verifies that value is greater than or equal to threshold.

        @param value: The value we got.
        @param threshold: The threshold of the value.
        @param value_name: The name of the value. It is used for TestFail
        message.

        @raise error.TestFail: If the value is less than threshold.
        """
        if value < threshold:
            raise error.TestFail('%s: %s is below the threshold %s' %
                                 (value_name, value, threshold))
        logging.info('%s = %d, which is >= %d.' %
                     (value_name, value, threshold))

    def test_flushable_data_packets(self):
        """Checks the Bluetooth controller must support flushable data packets.

        Note: As long as the chips are verified by SIG, setting the
                'Non-flushable Packet Boundary Flag' bit guarantees the related
                functionalities.
        """
        logging.info('** Running Bluetooth flushable data packets test:')
        supported_features = self.hcitool.read_local_supported_features()[1]
        self.assert_support(self.NON_FLUSHABLE_PACKET_BOUNDARY_FEATURE,
                            supported_features)

    def test_erroneous_data_reporting(self):
        """Checks the Bluetooth controller supports Erroneous Data Reporting."""
        logging.info('** Running Bluetooth erroneous data reporting test:')
        supported_features = self.hcitool.read_local_supported_features()[1]
        self.assert_support(self.ERRONEOUS_DATA_REPORTING_FEATURE,
                            supported_features)

    def test_event_filter_size(self):
        """Checks the Bluetooth controller event filter entries count.

        Checks the Bluetooth controller event filter has at least 8 entries.
        """
        logging.info('** Running Bluetooth event filter size test:')
        number_of_added_filters = 0
        for event_filter in self.MAC_EVENT_FILTERS:
            set_filter_result = self.hcitool.set_event_filter(
                    event_filter[0], event_filter[1], event_filter[2])[0]
            if set_filter_result == self.CONTROLLER_MEMORY_FULL_STATUS_VALUE:
                self.facade.reset_on()
                raise error.TestFail('Filter ' + ''.join(event_filter) +
                                     ' failed to apply. Only ' +
                                     str(number_of_added_filters) +
                                     ' filters were added')

            elif set_filter_result != self.CONTROLLER_SUCCESS_STATUS_VALUE:
                self.facade.reset_on()
                raise error.TestError(
                        'Failed to apply filter, status code is ' +
                        set_filter_result)
            number_of_added_filters += 1
        logging.info(
                'All 8 event filters were set successfully with values %s',
                self.MAC_EVENT_FILTERS)
        # Reset filter after done with test
        if not self.hcitool.set_event_filter('0', '0', '0'):
            logging.error('Unable to clear filter, reset bluetooth')
            self.facade.reset_on()
        else:
            logging.debug('Filter cleared')

    def test_acl_min_buffer_number(self):
        """Checks if ACL minimum buffers count(number of data packets) >=4."""
        logging.info('** Running Bluetooth acl min buffer number test:')
        acl_buffers_count = self.hcitool.read_buffer_size()[
                self.TOTAL_NUM_ACL_DATA_PACKETS_VALUE_INDEX]
        self.assert_greater_equal(acl_buffers_count,
                                  self.MIN_ACL_PACKETS_NUMBER,
                                  'ACL buffers count')

    def test_acl_min_buffer_number_optional(self):
        """Checks if ACL minimum buffers count(number of data packets) >=6."""
        logging.info(
                '** Running Bluetooth acl min buffer number test (optional)":')
        acl_buffers_count = self.hcitool.read_buffer_size()[
                self.TOTAL_NUM_ACL_DATA_PACKETS_VALUE_INDEX]
        if acl_buffers_count < self.MIN_ACL_PACKETS_NUMBER_OPTIONAL:
            raise error.TestWarn(
                    'ACL buffers count: %d is below the optional threshold %d'
                    %
                    (acl_buffers_count, self.MIN_ACL_PACKETS_NUMBER_OPTIONAL))
        logging.info('ACL buffers count = %d, which is >= %d.' %
                     (acl_buffers_count, self.MIN_ACL_PACKETS_NUMBER_OPTIONAL))

    def test_acl_min_buffer_size(self):
        """Checks if ACL minimum buffers size >=1021."""
        logging.info('** Running Bluetooth acl min buffer size test:')
        acl_buffer_size = self.hcitool.read_buffer_size()[
                self.ACL_DATA_PACKET_LENGTH_VALUE_INDEX]
        self.assert_greater_equal(acl_buffer_size, self.MIN_ACL_BUFFER_SIZE,
                                  'ACL buffer size')

    def test_sco_min_buffer_number(self):
        """Checks if SCO minimum buffer size(number of data packets) >=6."""
        logging.info('** Running Bluetooth sco min buffer number test:')
        sco_buffers_count = self.hcitool.read_buffer_size()[
                self.TOTAL_NUM_SYNCHRONOUS_DATA_PACKETS_VALUE_INDEX]
        self.assert_greater_equal(sco_buffers_count,
                                  self.MIN_SCO_PACKETS_NUMBER,
                                  'SCO buffers count')

    def test_sco_min_buffer_size(self):
        """Checks if SCO minimum buffer size >=60."""
        logging.info('** Running Bluetooth SCO min buffer size test:')
        sco_buffer_size = self.hcitool.read_buffer_size()[
                self.SCO_BUFFER_SIZE_VALUE_INDEX]
        self.assert_greater_equal(sco_buffer_size, self.MIN_SCO_BUFFER_SIZE,
                                  'SCO buffer size')

    def test_low_energy_feature(self):
        """Checks if Bluetooth controller must use support
        Bluetooth Low Energy (BLE)."""
        logging.info(
                '** Running support Bluetooth Low Energy (BLE) feature test:')
        supported_features = self.hcitool.read_local_supported_features()[1]
        self.assert_support(self.LE_CONTROLLER_FEATURE, supported_features)

    def test_accept_list_size(self):
        """Checks if accept list size >= 8 entries."""
        logging.info('** Running accept list size test:')
        accept_list_entries_count = self.hcitool.le_read_accept_list_size()[1]
        self.assert_greater_equal(accept_list_entries_count,
                                  self.MIN_ACCEPT_LIST_SIZE_ENTRIES,
                                  'Accept list size')

    def test_le_dual_mode_topology_feature(self):
        """Checks if Bluetooth controller supports LE dual mode topology."""
        logging.info('** Running LE dual mode topology feature test:')
        supported_features = self.hcitool.read_local_supported_features()[1]
        self.assert_not_support(self.BR_EDR_NOT_SUPPORT_FEATURE,
                                supported_features)
        self.assert_support(self.LE_CONTROLLER_FEATURE, supported_features)
        self.assert_support(self.LE_AND_BR_EDR_CONTROLLER_FEATURE,
                            supported_features)

    def test_br_edr_controller_secure_connection_feature(self):
        """Checks if Bluetooth controller supports BR/EDR secure connections."""
        logging.info('** Running BR/EDR controller secure connection feature '
                     'test:')
        supported_features = self.hcitool.read_local_extended_features(2)[3]
        self.assert_support(self.BR_SECURE_CONNECTION_FEATURE,
                            supported_features)

    def test_le_data_packet_length_extension_feature(self):
        """Checks LE data packet length extension support."""
        logging.info('** Running LE data packet length extension test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_DATA_PACKETS_LENGTH_EXTENSION_FEATURE,
                            supported_features)

    def test_packet_data_length(self):
        """Checks if data packet length <= 251."""
        logging.info('** Running packet data length test:')
        packet_data_length = self.hcitool.le_read_maximum_data_length()[1]
        self.assert_equal(packet_data_length, self.MAX_PACKET_LENGTH,
                          'Packet data length')

    def test_le_link_layer_privacy_feature(self):
        """Checks if Bluetooth controller supports link layer privacy."""
        logging.info('** Running link layer privacy test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_LINK_LAYER_PRIVACY_FEATURE,
                            supported_features)

    def test_resolving_list_size(self):
        """Checks if resolving list size >= 8 entries."""
        logging.info('** Running resolving list size test:')
        resolving_list_entries_count = self.hcitool.le_read_resolving_list_size(
        )[1]
        self.assert_greater_equal(resolving_list_entries_count,
                                  self.MIN_RESOLVING_LIST_SIZE_ENTRIES,
                                  'Resolving list size')

    def test_le_extended_advertising_feature(self):
        """Checks if Bluetooth controller supports LE advertising extension."""
        logging.info('** Running LE extended advertising feature test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_EXTENDED_ADVERTISING_FEATURE,
                            supported_features)

    def test_advertisement_sets_number(self):
        """Checks if number of advertisement sets >= 10."""
        logging.info('** Running advertisement sets number feature test:')
        advertisement_sets_number = (
                self.hcitool.le_read_number_of_supported_advertising_sets()[1])
        self.assert_greater_equal(advertisement_sets_number,
                                  self.MIN_ADVERTISEMENT_SETS_NUMBER,
                                  'Advertisement sets number')

    def test_le_two_mega_physical_channel_feature(self):
        """Checks if Bluetooth controller supports 2 Msym/s PHY for LE."""
        logging.info('** Running LE two mega physical channel feature test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_TWO_MEGA_PHYSICAL_CHANNEL_FEATURE,
                            supported_features)

    def test_le_isochronous_channels_feature(self):
        """Checks if ISO channels feature is supported."""
        logging.info('** Running LE isochronous channels feature test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_ISOCHRONOUS_CHANNELS_FEATURE,
                            supported_features)

    def test_le_power_control_feature(self):
        """Checks if Bluetooth controller supports LE power control."""
        logging.info('** Running LE power control feature test:')
        supported_features = self.hcitool.read_le_local_supported_features()[1]
        self.assert_support(self.LE_POWER_CONTROL_REQUEST_FEATURE,
                            supported_features)
        self.assert_support(self.LE_POWER_CHANGE_INDICATION_FEATURE,
                            supported_features)

    def test_hci_vs_msft_read_supported_features(self):
        """Checks if Bluetooth controller supports VS MSFT features."""
        logging.info('** Running hci VS MSFT read supported features:')
        chipset_name = self.facade.get_chipset_name()
        chip_info = chipinfo.query(chipset_name)
        if not chip_info.msft_support:
            raise error.TestNAError('Chipset ' + chipset_name +
                                    ' does not support MSFT HCI extensions')
        vs_msft_supported_features = (
                self.hcitool.vs_msft_read_supported_features(
                        chip_info.msft_ocf)[2])
        self.assert_support(self.LE_ADV_RSSI_MONITORING,
                            vs_msft_supported_features)
        self.assert_support(self.LE_ADV_MONITORING, vs_msft_supported_features)

    def assert_aosp_hci(self):
        """Checks if a chipset supports AOSP HCI extensions."""
        chipset_name = self.facade.get_chipset_name()
        chip_info = chipinfo.query(chipset_name)
        if not chip_info.aosp_support:
            raise error.TestNAError('Chipset ' + chipset_name +
                                    ' does not support AOSP HCI extensions')

    def test_aosp_quality_report(self):
        """Checks if Bluetooth controller supports AOSP quality report."""
        logging.info('** Running aosp quality report test:')
        self.assert_aosp_hci()
        version_supported = self.hcitool.le_get_vendor_capabilities_command(
        )[8]
        if version_supported < self.GOOGLE_FEATURE_SPECIFICATION_VERSION:
            raise error.TestFail('Version supported = ' + version_supported +
                                 ' but expected >=' +
                                 self.GOOGLE_FEATURE_SPECIFICATION_VERSION)
        bluetooth_quality_report_support = (
                self.hcitool.le_get_vendor_capabilities_command()[14])
        if not bluetooth_quality_report_support:
            raise error.TestFail('AOSP Quality Report is not supported')
        logging.info(
                'With bluetooth_quality_report_support =%d and '
                'version_supported >=%s, the controller supports the '
                'Android HCI Extension Bluetooth Quality Report.',
                bluetooth_quality_report_support, version_supported)

    def test_le_apcf(self):
        """Checks if APCF filtering feature is supported."""
        logging.info('** Running LE APCF test:')
        self.assert_aosp_hci()
        filtering_support = self.hcitool.le_get_vendor_capabilities_command(
        )[5]
        if not filtering_support:
            raise error.TestFail('LE APCF feature is not supported')
        logging.info('LE APCF feature is supported.')

    def test_le_batch_scan_and_events(self):
        """Checks if LE batch scan and events feature is supported."""
        logging.info('** Running LE batch scan and events test:')
        self.assert_aosp_hci()
        total_scan_result_storage = (
                self.hcitool.le_get_vendor_capabilities_command()[3])
        if total_scan_result_storage == 0:
            raise error.TestFail(
                    'LE batch scan and events feature is not supported')
        logging.info('LE batch scan and events feature is supported.')

    def test_le_extended_set_scan_parameters(self):
        """Checks if LE extended set scan parameters feature is supported."""
        logging.info('** Running LE extended set scan parameters test:')
        self.assert_aosp_hci()
        extended_scan_support = self.hcitool.le_get_vendor_capabilities_command(
        )[10]
        if not extended_scan_support:
            raise error.TestFail(
                    'LE extended set scan parameters feature is not supported')
        logging.info('LE extended set scan parameters feature is supported.')

    def test_le_get_controller_activity_energy_info(self):
        """Checks if LE get controller activity energy info feature is
        supported. """
        logging.info('** Running LE get controller activity energy info test:')
        self.assert_aosp_hci()
        activity_energy_info_support = (
                self.hcitool.le_get_vendor_capabilities_command()[7])
        if not activity_energy_info_support:
            raise error.TestFail(
                    'LE get controller activity energy info feature is '
                    'not supported')
        logging.info(
                'LE get controller activity energy info feature is supported.')

    def test_get_controller_debug_info_sub_event(self):
        """Checks if get controller debug info and sub-event features is
        supported. """
        logging.info('** Running get controller debug info sub-event test:')
        self.assert_aosp_hci()
        debug_logging_support = self.hcitool.le_get_vendor_capabilities_command(
        )[11]
        if not debug_logging_support:
            raise error.TestFail(
                    'Get controller debug info and sub-event features is not '
                    'supported')
        logging.info(
                'Get controller debug info and sub-event features is supported.'
        )

    def avl_hci_batch_run(self, test_name=None):
        """Runs bluetooth_AVLHCI test batch (all test).

        @param test_name: test name as string from control file.
        """
        if test_name is None:
            self.spec_legacy_test()
            self.spec_4_0_test()
            self.spec_4_1_test()
            self.spec_4_2_test()
            self.spec_5_0_test()
            self.spec_5_2_test()
            self.hci_ext_msft_test()
            self.hci_ext_aosp_test()
        else:
            getattr(self, test_name)()

    def run_once(self, test_name=None):
        """Runs bluetooth_AVLHCI.

        @param test_name: test name as string from control file.
        """
        self.facade.reset_on()
        self.avl_hci_batch_run(test_name)
        self.facade.reset_on()
