# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Server side bluetooth tests on adapter ble advertising.

The Mnemonics describing the test cases:
    CD: check advertising duration and intervals
    RA: register advertisements
    UA: unregister advertisements
    SI: set advertising intervals
    RS: reset advertising
    FRA: fail to register extra advertisements when max ones
         have been registered.
    FSI: fail to set advertising intervals beyond legitimate range
         of [20 ms, 10,240 ms].
    PC: power cycle the bluetooth adapter (controller).
    SR: suspend and resume the DUT (chromebook)

A test represents a component of a test case which comprises a
sequence of tests. A test case usually requires a tester (user)
to perform a sequence of actions and make a sequence of
observations if the test case is performed manually.

A test consists of an optional action such as "register n
advertisements" and a number of test criteria such as "verifying
if min advertising interval is set to an expected value" or
"verifying if advertising is disabled".

"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import copy
import logging
import re
import time

import common
from autotest_lib.server.cros.bluetooth import advertisements_data
from autotest_lib.server.cros.bluetooth import bluetooth_adapter_tests
from six.moves import range
from six.moves import zip

test_case_log = bluetooth_adapter_tests.test_case_log
test_retry_and_log = bluetooth_adapter_tests.test_retry_and_log

class bluetooth_AdapterLEAdvertising(
        bluetooth_adapter_tests.BluetoothAdapterTests):
    """Server side bluetooth adapter advertising Test.

    This class comprises a number of test cases to verify
    bluetooth low-energy advertising.

    Refer to BluetoothAdapterTests for the implementation of the tests
    performed in this autotest test.

    Refer to the design doc for more details:
    "Test Cases for Bluetooth Low-Energy Advertising".

    """

    # The software advertising rotation is a default bluez parameter, 2 seconds
    SOFTWARE_ROTATION_INTERVAL_S = 2

    # If hardware offloading is available, a 'default' discovery time is used,
    # that will not depend on number of advertisements registered since they are
    # advertised in parallel.
    DEFAULT_DISCOVERY_TIME_S = 3

    @staticmethod
    def get_instance_ids(advertisements):
        """Get the list of instace IDs starting at 1.

        @param advertisements: a list of advertisements.

        """
        return list(range(1, len(advertisements) + 1))


    def register_advertisements(self, advertisements, min_adv_interval_ms,
                                max_adv_interval_ms, instance_ids=None):
        """Register multiple advertisements continuously.

        @param advertisements: a list of advertisement instances.
        @param min_adv_interval_ms: min_adv_interval in milliseconds.
        @param max_adv_interval_ms: max_adv_interval in milliseconds.
        @param instance_ids: the list of instance IDs to register.
        """
        if instance_ids is None:
            instance_ids = self.get_instance_ids(advertisements)

        for instance_id, advertisement in zip(instance_ids, advertisements):
            advertisement['MinInterval'] = min_adv_interval_ms
            advertisement['MaxInterval'] = max_adv_interval_ms

            self.test_register_advertisement(advertisement, instance_id)


    def unregister_advertisements(self, advertisements, instance_ids=None):
        """Register multiple advertisements.

        @param advertisements: a list of advertisement instances.
        @param instance_ids: the list of instance IDs to unregister.

        """
        if instance_ids is None:
            instance_ids = self.get_instance_ids(advertisements)

        count = 0
        number_advs = len(advertisements)
        for instance_id, advertisement in zip(instance_ids, advertisements):
            # Advertising is only disabled at the removal of the
            # last advertisement.
            count += 1
            advertising_disabled = count == number_advs
            self.test_unregister_advertisement(advertisement,
                                               instance_id,
                                               advertising_disabled)


    def check_kernel_version(self):
        """ Check if test can execute on this kernel version."""

        logging.info("Checking kernel version {}".format(self.kernel_version))
        #
        # Due to crbug/729648, we cannot set advertising intervals
        # on kernels that are 3.8.11 and below, so we raise TestNAError.
        # 3.8.12 used so that version of the form 3.8.11<suffix> fails the check
        #
        self.is_supported_kernel_version(self.kernel_version, "3.8.12",
                                         'Test cannnot proceed on old kernels')
        #
        # Due to crbug/946835, some messages does not reach btmon
        # causing our tests to fails. This is seen on kernel 3.18 and lower.
        # Remove this check when the issue is fixed
        # TODO(crbug/946835)
        #
        self.is_supported_kernel_version(self.kernel_version, "3.19",
                                         'Test cannnot proceed on this'
                                         'kernel due to crbug/946835 ')
        logging.debug("Test is supported on this kernel version")


    # ---------------------------------------------------------------
    # Definitions of all test cases
    # ---------------------------------------------------------------

    def _get_uuids_from_advertisement(self, adv, type):
        """Parses Solicit or Service UUIDs from advertising data

        Data to be parsed has the following structure:
        16-bit Service UUIDs (complete): 2 entries
            Heart Rate (0x180d)
            Battery Service (0x180f)

        @param adv: string advertising data as collected by btmon
        @param type: Type of UUIDs to parse, either 'Solicit' or 'Service'

        @returns: list of UUIDs as ints
        """
        if not adv:
            return []

        lines = adv.split('\n')
        num_uuids = 0
        # Find Service UUID section in adv and grab number of entries
        for idx, line in enumerate(lines):
            if '{} UUIDs (complete)'.format(type) in line:
                search_res = re.search('(\d) entr', line)
                if search_res and search_res.group(1):
                    num_uuids = int(search_res.group(1))
                    break

        found_uuids = []
        # Iterate through each entry and collect UUIDs. UUIDs are saved as ints
        # to reduce complexity of comparing hex strings, i.e. ab vs AB vs 0xab
        for lineidx in range(idx+1, idx+num_uuids+1):
            line = lines[lineidx]
            search_res = re.search('\((.*?)\)', line)
            if search_res and search_res.group(1):
                uuid = search_res.group(1)
                found_uuids.append(int(uuid, 16))

        return found_uuids


    def _get_company_data_from_advertisement(self, adv):
        """Parses Company ID and associated company data from advertisement

        Data to be parsed has the following structure:
        Company: not assigned (65281)
            Data: 1a1b1c1d1e

        @param adv: string advertising data as collected by btmon

        @returns: dictionary with structure {company uuid: company data}
        """

        lines = adv.split('\n')

        for idx, line in enumerate(lines):
            if 'Company:' in line:
                search_res = re.search('\((.*?)\)', line)
                if search_res and search_res.group(1):
                    company_id = int(search_res.group(1))
                    break

        # Company data is on the line after the header, and is the last block
        # printed
        if company_id and idx+1 < len(lines):
            company_data = lines[idx+1].split(' ')[-1]
            return {company_id: company_data}

        return {}


    def _get_service_data_from_advertisement(self, adv):
        """Parses Service data from advertisement

        Data to be parsed has the following structure:
        Service Data (UUID 0x9991): 1112131415

        @param adv: string advertising data as collected by btmon

        @returns: dictionary with structure {company uuid: company data}
        """

        lines = adv.split('\n')

        discovered_service_data = {}
        # Iterate through lines in advertisement, grabbing Service Data entries
        for line in lines:
            if 'Service Data' in line:
                search_res = re.search('\(UUID (.*?)\)', line)
                if search_res and search_res.group(1):
                    found_uuid = search_res.group(1)
                    found_data = line.split(' ')[-1]

                    discovered_service_data[int(found_uuid, 16)] = found_data

        return discovered_service_data


    def validate_scan_rsp_reception(self, peer, advertisement, discover_time):
        """Validate our advertisement's scan response is located by the peer

        If our advertisements are configured with scan response data, we wish
        to confirm that a scanning peer will be able to discover this content.

        @param peer: Handle to peer device for advertisement collection
        @param advertisement: Advertisement data that has been enabled on DUT
            side
        @param discover_time: Number of seconds we should spend discovering
            before considering the device undiscoverable

        @returns: True if scan response is discovered and is correct, else False
        """

        scan_rsp_data = advertisement.get('ScanResponseData', {})

        # For now, scan response can only contain service data (ad type 0x16):
        # It appears in a scan response event with the following format:
        # 'Service Data (UUID 0xfef3): 010203...'
        if '0x16' in scan_rsp_data:
            service_uuid_data = scan_rsp_data['0x16']

            # First two bytes of data make up 16 bit service UUID
            uuid = service_uuid_data[1] * 256 + service_uuid_data[0]
            # Subsequent bytes make up the service data
            service_data = ''.join(
                    ['{:02x}'.format(data) for data in service_uuid_data[2:]])

            search_str = 'Service Data (UUID 0x{:4x}): {}'.format(
                    uuid, service_data)
            logging.debug('Searching btmon for content: {}'.format(search_str))

            # Locate a scan response with the above entry. Pass if it is found
            start_time = time.time()
            found_adv = peer.FindAdvertisementWithAttributes(
                    [search_str, 'SCAN_RSP'], discover_time)

            logging.info('Scan response discovered after %fs',
                         time.time() - start_time)

            return bool(found_adv)

        return True

    def _test_peer_received_correct_adv(self, peer, advertisement,
                                        discover_time):
        """Test that configured advertisements are found by peer

        We need to verify quality of advertising service from the perspective of
        the client, as this is externally visible in cases like Nearby features.
        This test ensures advertisements are discovered and are correct,
        helping to confirm quality provided, especially with multi-advertising

        @param peer: Handle to peer device for advertisement collection
        @param advertisement: Advertisement data that has been enabled on DUT
            side
        @param discover_time: Number of seconds we should spend discovering
            before considering the device undiscoverable

        @returns: True if advertisement is discovered and is correct, else False
        """

        self.results = {}

        # We locate the advertisement by searching for the ServiceData
        # attribute we configured.
        data_to_match = list(advertisement['ServiceData'].keys())[0]

        start_time = time.time()
        found_adv = peer.FindAdvertisementWithAttributes([data_to_match],
                                                         discover_time)
        logging.info('Advertisement discovered after %fs',
                     time.time() - start_time)

        if not found_adv:
            self.results['advertisement_found'] = False

        # Check that our service UUIDs match what we expect
        found_service_uuids = self._get_uuids_from_advertisement(
                found_adv, 'Service')

        for UUID in advertisement.get('ServiceUUIDs', []):
            if int(UUID, 16) not in found_service_uuids:
                logging.info('Service id %d not found in %s', int(UUID, 16),
                             str(found_service_uuids))
                self.results['service_ids_found'] = False
                return False

        # Check that our solicit UUIDs match what we expect
        found_solicit_uuids = self._get_uuids_from_advertisement(
                found_adv, 'Solicit')

        for UUID in advertisement.get('SolicitUUIDs', []):
            if int(UUID, 16) not in found_solicit_uuids:
                logging.info('Solicid ID %d not found in %s', int(UUID, 16),
                             str(found_solicit_uuids))
                self.results['solicit_ids_found'] = False
                return False

        # Check that our Manufacturer info is correct
        company_info = self._get_company_data_from_advertisement(found_adv)

        expected_company_info = advertisement.get('ManufacturerData', {})
        for UUID in expected_company_info:
            if int(UUID, 16) not in company_info:
                logging.info('Company ID %d not found in advertisement',
                        int(UUID, 16))
                self.results['manufacturer_uuid_found'] = False
                return False

            expected_data = expected_company_info.get(UUID, None)
            formatted_data = ''.join([format(d, 'x') for d in expected_data])

            if formatted_data != company_info.get(int(UUID, 16)):
                logging.info('Manufacturer data %s didn\'t match expected %s',
                        company_info.get(int(UUID, 16)), formatted_data)
                self.results['manufacturer_data_found'] = False
                return False

        # Check that our service data is correct
        service_data = self._get_service_data_from_advertisement(found_adv)

        expected_service_data = advertisement.get('ServiceData', {})
        for UUID in expected_service_data:
            if int(UUID, 16) not in service_data:
                logging.info('Service UUID %d not found in advertisement',
                             int(UUID, 16))
                self.results['service_data_uuid_found'] = False
                return False

            expected_data = expected_service_data.get(UUID, None)
            formatted_data = ''.join([format(d, 'x') for d in expected_data])

            if formatted_data != service_data.get(int(UUID, 16)):
                logging.info('Service data %s didn\'t match expected %s',
                             service_data.get(int(UUID, 16)), formatted_data)
                self.results['service_data_found'] = False
                return False

        # Validate scan response from peer's perspective
        if not self.validate_scan_rsp_reception(peer, advertisement,
                                                discover_time):
            self.results['scan_rsp_found'] = False
            return False

        return True


    def get_host_discovery_time(self, num_adv):
        """Estimates how long it will take the peer to discover the host

        The amount of time we wait for the peer to discover the host's
        advertisement depends on how many advertisements are registered, and
        whether the host platform is using hardware offloaded multi-advertising
        or software rotation.

        @param num_adv: Number of advertisements that are active
        @returns: number of seconds we should wait for discovery
        """

        if self.ext_adv_enabled():
            return self.DEFAULT_DISCOVERY_TIME_S

        return num_adv * self.SOFTWARE_ROTATION_INTERVAL_S

    @test_retry_and_log(False)
    def test_peer_received_correct_adv(self, peer, advertisement,
                                       discover_time):
        """Tests that advertisement can be received by the peer"""

        return self._test_peer_received_correct_adv(peer, advertisement,
                                                    discover_time)

    @test_retry_and_log(False, messages_start=False, messages_stop=False)
    def test_peer_failed_received_correct_adv(self, peer, advertisement,
                                              discover_time):
        """Tests that advertisement can not be received by the peer"""

        return not self._test_peer_received_correct_adv(
                peer, advertisement, discover_time)

    def advertising_peer_test(self, peer):
        """Verifies that advertisements registered on DUT are seen by peer

        @param peer: handle to peer used in test
        """

        self.kernel_version = self.host.get_kernel_version()
        self.check_kernel_version()

        self.bluetooth_le_facade = self.bluetooth_facade

        # Register some advertisements
        num_adv = 3
        self.test_reset_advertising()

        for i in range(0, num_adv):
            self.bluetooth_le_facade.register_advertisement(
                    advertisements_data.ADVERTISEMENTS[i])

        discover_time = self.get_host_discovery_time(num_adv)

        for i in range(0, num_adv):
            res = self.test_peer_received_correct_adv(
                    peer, advertisements_data.ADVERTISEMENTS[i], discover_time)

    def advertising_peer_suspend_resume_test(self, peer):
        """Verify expected advertising behavior around suspend/resume

        For power and usage sake, we expect that when the system suspends, any
        advertising instances should be paused. When we resume from suspend,
        they should be re-enabled again. To confirm this behavior, the test
        performs the following steps:

        - Register some advertisements
        - Verify that advertisements are discoverable by remote device
        - Enter suspend
        - Verify that advertisements are NOT discoverable by remote device
        - Exit suspend
        - Verify that advertisements are discoverable by remote device

        @param peer: handle to peer used in test
        """

        self.kernel_version = self.host.get_kernel_version()
        self.check_kernel_version()

        self.bluetooth_le_facade = self.bluetooth_facade

        # Register some advertisements
        num_adv = 3
        discover_time = self.get_host_discovery_time(num_adv)
        self.test_reset_advertising()

        for i in range(0, num_adv):
            self.bluetooth_le_facade.register_advertisement(
                    advertisements_data.ADVERTISEMENTS[i])

        # Verify they can all be discovered
        for i in range(0, num_adv):
            res = self.test_peer_received_correct_adv(
                    peer, advertisements_data.ADVERTISEMENTS[i], discover_time)

        # Enter suspend long enough to verify none of the registered
        # advertisements are discoverable. Give a few extra seconds in suspend
        # to be safe
        suspend_time = discover_time * num_adv + 10
        logging.debug(
                'suspend_time(%d) = discover_time(%d) * num_adv(%d) + 10',
                suspend_time, discover_time, num_adv)

        # Trigger suspend, asynchronously trigger wake and wait for resume
        boot_id = self.host.get_boot_id()
        suspend = self.suspend_async(suspend_time=suspend_time)
        start_time = self.bluetooth_facade.get_device_utc_time()
        self.test_suspend_and_wait_for_sleep(suspend, sleep_timeout=5)

        # Verify they can not be discovered
        for i in range(0, num_adv):
            res = self.test_peer_failed_received_correct_adv(
                    peer, advertisements_data.ADVERTISEMENTS[i], discover_time)

        # Wait for device to come out of suspend
        logging.debug('test_wait_for_resume(resume_timeout=%d, start_time=%s)',
                      suspend_time, start_time)
        self.test_wait_for_resume(boot_id,
                                  suspend,
                                  resume_timeout=suspend_time,
                                  test_start_time=start_time)

        # Verify reception of advertisements again
        for i in range(0, num_adv):
            res = self.test_peer_received_correct_adv(
                    peer, advertisements_data.ADVERTISEMENTS[i], discover_time)


    @test_case_log
    def test_case_SI200_RA3_CD_UA3(self):
        """Test Case: SI(200) - RA(3) - CD - UA(3)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_SI200_RA3_CD_RA1_CD_UA1_CD_UA3(self):
        """Test Case: SI(200) - RA(3) - CD - RA(1) - CD - UA(1) - CD - UA(3)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        # Make a copy of advertisements since we are going to modify it.
        advertisements = copy.copy(self.three_advertisements)
        number_advs = len(advertisements)
        one_more_advertisement = [self.sixth_advertisement]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Register one more advertisement.
        # The instance ID to register is len(advertisements) + 1 = 4
        self.register_advertisements(one_more_advertisement,
                                     new_min_adv_interval_ms,
                                     new_max_adv_interval_ms,
                                     instance_ids=[number_advs + 1])

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs + 1)

        # Unregister the 3rd advertisement.
        # After removing the advertisement, the remaining instance IDs
        # would be [1, 2, 4]
        instance_id = 3
        self.test_unregister_advertisement(advertisements.pop(instance_id - 1),
                                           instance_id,
                                           advertising_disabled=False)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs)

        # Unregister all existing advertisements which are [1, 2, 4]
        # since adv 3 was removed in the previous step.
        self.unregister_advertisements(advertisements + one_more_advertisement,
                                       instance_ids=[1, 2, 4])


    @test_case_log
    def test_case_SI200_RA3_CD_RS(self):
        """Test Case: SI(200) - RA(3) - CD - RS"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        self.test_reset_advertising(self.get_instance_ids(advertisements))


    @test_case_log
    def test_case_SI200_RA3_CD_UA1_CD_RS(self):
        """Test Case: SI(200) - RA(3) - CD - UA(1) - CD - RS"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        # Make a copy of advertisements since we are going to modify it.
        advertisements = copy.copy(self.three_advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        # Unregister the 1st advertisement.
        # After removing the advertisement, the remaining instance IDs
        # would be [2, 3]
        instance_id = 1
        self.test_unregister_advertisement(advertisements.pop(instance_id - 1),
                                           instance_id,
                                           advertising_disabled=False)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   len(advertisements) - 1)

        self.test_reset_advertising([2, 3])


    @test_case_log
    def test_case_SI200_RA3_CD_UA1_CD_RA2_CD_UA4(self):
        """Test Case: SI(200) - RA(3) - CD - UA(1) - CD - RA(2) - CD - UA(4)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        # Make a copy of three_advertisements since we are going to modify it.
        advertisements1 = copy.copy(self.three_advertisements)
        advertisements2 = self.two_advertisements
        number_advs1 = len(advertisements1)
        number_advs2 = len(advertisements2)

        self.test_reset_advertising()

        self.register_advertisements(advertisements1, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs1)

        # Unregister the 2nd advertisement.
        # After removing the 2nd advertisement, the remaining instance IDs
        # would be [1, 3]
        instance_id = 2
        self.test_unregister_advertisement(advertisements1.pop(instance_id - 1),
                                           instance_id,
                                           advertising_disabled=False)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs1 - 1)

        # Register two more advertisements.
        # The instance IDs to register would be [2, 4]
        self.register_advertisements(advertisements2, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms,
                                     instance_ids=[2, 4])

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs1 + number_advs2 - 1)

        # Unregister all advertisements.
        # The instance_ids of advertisements1 is [1, 3].
        # The instance_ids of advertisements2 is [2, 4].
        self.unregister_advertisements(advertisements1 + advertisements2,
                                       instance_ids=[1, 3, 2, 4])


    @test_case_log
    def test_case_SI200_RA5_CD_FRA1_CD_UA5(self):
        """Test Case: SI(200) - RA(5) - CD - FRA(1) - CD - UA(5)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.five_advertisements
        extra_advertisement = self.sixth_advertisement
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        self.test_fail_to_register_advertisement(extra_advertisement,
                                                 new_min_adv_interval_ms,
                                                 new_max_adv_interval_ms)

        # If the registration fails and extended advertising is available,
        # there will be no events in btmon. Therefore, we only run this part of
        # the test if extended advertising is not available, indicating that
        # software advertisement rotation is being used.
        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs)

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_SI200_RA3_CD_PC_CD_UA3(self):
        """Test Case: SI(200) - RA(3) - CD - PC - CD - UA(3)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        # Turn off and then turn on the adapter.
        self.test_power_off_adapter()
        time.sleep(1)
        self.test_power_on_adapter()

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_SI200_RA3_CD_SR_CD_UA3(self):
        """Test Case: SI(200) - RA(3) - CD - SR - CD - UA(3)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        # Suspend for a while and resume.
        self.suspend_resume()

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA3_CD_SI200_CD_UA3(self):
        """Test Case: RA(3) - CD - SI(200) - CD - UA(3)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA3_CD_SI200_CD_RS(self):
        """Test Case: RA(3) - CD - SI(200) - CD - RS"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        self.test_reset_advertising(self.get_instance_ids(advertisements))



    @test_case_log
    def test_case_RA3_CD_SI200_CD_UA1_CD_RS(self):
        """Test Case: RA(3) - CD - SI(200) - CD - UA(1) - CD - RS"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Unregister the 2nd advertisement.
        instance_id = 2
        self.test_unregister_advertisement(advertisements[instance_id - 1],
                                           instance_id,
                                           advertising_disabled=False)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs - 1)

        # Test if advertising is reset correctly.Only instances [1, 3] are left.
        self.test_reset_advertising([1, 3])


    @test_case_log
    def test_case_RA3_CD_SI200_CD_SI2000_CD_UA3(self):
        """Test Case: RA(3) - CD - SI(200) - CD - SI(2000) - CD - UA(3)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_small_min_adv_interval_ms = 200
        new_small_max_adv_interval_ms = 200
        new_large_min_adv_interval_ms = 2000
        new_large_max_adv_interval_ms = 2000
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements,
                                     new_small_min_adv_interval_ms,
                                     new_small_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_small_min_adv_interval_ms,
                                               new_small_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements,
                                     new_large_min_adv_interval_ms,
                                     new_large_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_large_min_adv_interval_ms,
                                               new_large_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA5_CD_SI200_CD_FRA1_CD_UA5(self):
        """Test Case: RA(5) - CD - SI(200) - CD - FRA(1) - CD - UA(5)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.five_advertisements
        extra_advertisement = self.sixth_advertisement
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        self.test_fail_to_register_advertisement(extra_advertisement,
                                                 new_min_adv_interval_ms,
                                                 new_max_adv_interval_ms)

        # If the registration fails and extended advertising is available,
        # there will be no events in btmon. Therefore, we only run this part of
        # the test if extended advertising is not available, indicating that
        # software advertisement rotation is being used.
        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs)

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA3_CD_SI200_CD_FSI10_CD_FSI20000_CD_UA3(self):
        """Test Case: RA(3) - CD - SI(200) - CD - FSI(10) - CD - FSI(20000) - CD
        - UA(3)
        """
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        invalid_small_min_adv_interval_ms = 10
        invalid_small_max_adv_interval_ms = 10
        invalid_large_min_adv_interval_ms = 20000
        invalid_large_max_adv_interval_ms = 20000
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Fails to set intervals that are too small. Intervals remain the same.
        self.test_fail_to_set_advertising_intervals(
                invalid_small_min_adv_interval_ms,
                invalid_small_max_adv_interval_ms,
                new_min_adv_interval_ms, new_max_adv_interval_ms)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs)

        # Fails to set intervals that are too large. Intervals remain the same.
        self.test_fail_to_set_advertising_intervals(
                invalid_large_min_adv_interval_ms,
                invalid_large_max_adv_interval_ms,
                new_min_adv_interval_ms, new_max_adv_interval_ms)

        if not self.ext_adv_enabled():
            self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                                   new_max_adv_interval_ms,
                                                   number_advs)

        # Unregister all advertisements.
        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA3_CD_SI200_CD_PC_CD_UA3(self):
        """Test Case: RA(3) - CD - SI(200) - CD - PC - CD - UA(3)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Turn off and then turn on the adapter.
        self.test_power_off_adapter()
        time.sleep(1)
        self.test_power_on_adapter()

        # Check if the advertising durations remain the same after resume.
        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Unregister all advertisements.
        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA3_CD_SI200_CD_SR_CD_UA3(self):
        """Test Case: RA(3) - CD - SI(200) - CD - SR - CD - UA(3)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = self.three_advertisements
        number_advs = len(advertisements)

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               number_advs)

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Suspend for a while and resume.
        self.suspend_resume()

        # Check if the advertising durations remain the same after resume.
        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               number_advs)

        # Unregister all advertisements.
        self.unregister_advertisements(advertisements)

    # SINGLE TEST CASES
    @test_case_log
    def test_case_SI200_RA1_CD_UA1(self):
        """Test Case: SI(200) - RA(1) - CD - UA(1)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.sixth_advertisement]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_SI200_RA1_CD_RS(self):
        """Test Case: SI(200) - RA(1) - CD - RS"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.first_advertisement]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.test_reset_advertising()


    @test_case_log
    def test_case_SI200_RA1_CD_SR_CD_UA1(self):
        """Test Case: SI(200) - RA(1) - CD - SR - CD - UA(1)"""
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.sixth_advertisement]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.suspend_resume()

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA1_CD_SI200_CD_UA1(self):
        """Test Case: RA(1) - CD - SI(200) - CD - UA(1)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.first_advertisement]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA1_CD_SI200_CD_RS(self):
        """Test Case: RA(1) - CD - SI(200) - CD - RS"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.sixth_advertisement]
        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))
        self.test_reset_advertising()


    @test_case_log
    def test_case_RA1_CD_SI200_CD_FSI10_UA1_RA1_CD_UA1(self):
        """Test Case:  RA(1) - CD - SI(200) - CD - FSI(10) - UA(1)
         - RA(1) - CD - UA(1)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        invalid_small_min_adv_interval_ms = 10
        invalid_small_max_adv_interval_ms = 10
        advertisements = [self.three_advertisements[1]]
        new_advertisement = [self.three_advertisements[2]]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        # Fails to set intervals that are too small. Intervals remain the same.
        self.test_fail_to_set_advertising_intervals(
                invalid_small_min_adv_interval_ms,
                invalid_small_max_adv_interval_ms,
                new_min_adv_interval_ms, new_max_adv_interval_ms)

        self.unregister_advertisements(advertisements)

        # Register a new advertisement in order to avoid kernel caching.
        self.register_advertisements(new_advertisement, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(new_advertisement)


    @test_case_log
    def test_case_RA1_CD_SI200_CD_FSI20000_UA1_RA1_CD_UA1(self):
        """Test Case:  RA(1) - CD - SI(200) - CD - FSI(20000) - UA(1)
         - RA(1) - CD - UA(1)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        invalid_large_min_adv_interval_ms = 20000
        invalid_large_max_adv_interval_ms = 20000
        advertisements = [self.three_advertisements[1]]
        new_advertisement = [self.three_advertisements[2]]

        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))
        # Fails to set intervals that are too large. Intervals remain the same.
        self.test_fail_to_set_advertising_intervals(
                invalid_large_min_adv_interval_ms,
                invalid_large_max_adv_interval_ms,
                new_min_adv_interval_ms, new_max_adv_interval_ms)

        self.unregister_advertisements(advertisements)

        # Register a new advertisement in order to avoid kernel caching.
        self.register_advertisements(new_advertisement, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(new_advertisement)


    @test_case_log
    def test_case_RA1_CD_SI200_CD_PC_CD_UA1(self):
        """Test Case: RA(1) - CD - SI(200) - CD - PC - CD - UA(1)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.sixth_advertisement]
        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        # Turn off and then turn on the adapter.
        self.test_power_off_adapter()
        time.sleep(1)
        self.test_power_on_adapter()

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)


    @test_case_log
    def test_case_RA1_CD_SI200_CD_SR_CD_UA1(self):
        """Test Case: RA(1) - CD - SI(200) - CD - SR - CD - UA(1)"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        new_min_adv_interval_ms = 200
        new_max_adv_interval_ms = 200
        advertisements = [self.first_advertisement]
        self.test_reset_advertising()

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        self.test_check_duration_and_intervals(orig_min_adv_interval_ms,
                                               orig_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)
        self.register_advertisements(advertisements, new_min_adv_interval_ms,
                                     new_max_adv_interval_ms)

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.suspend_resume()

        self.test_check_duration_and_intervals(new_min_adv_interval_ms,
                                               new_max_adv_interval_ms,
                                               len(advertisements))

        self.unregister_advertisements(advertisements)

    @test_case_log
    def test_case_nearby_mediums_fast(self):
        """Verify minimal test case for nearby sharing"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS

        # We set a specific advertisement with fields required by Nearby
        # sharing service
        advertisements = [advertisements_data.NEARBY_MEDIUMS_FAST_ADV]

        self.test_reset_advertising()

        # Nearby share requires general discoverable advertising flag be set.
        # Bluez sets this flag based on the adapter's Discoverable property,
        # so we apply this setting here
        self.bluetooth_facade.set_discoverable(True)

        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        # Ensure that our discoverable flag is advertised
        self.test_advertising_flags(['Advertise as Discoverable'])

    @test_case_log
    def test_case_adv_before_scan(self):
        """Verify we can scan after advertising starts

        We found that when extended advertising is available, any Set Adv
        Disable HCI command would mark the hdev as not advertising, even if
        other instances were active at the time. Later attempts to start
        discovery would fail, because kernel tries to update the random address
        without knowing to pause the advertisements. This test case replicates
        this failure condition to validate the fix.
        """
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        advertisements = self.three_advertisements

        self.test_reset_advertising()

        # Register several advertisements
        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

        # Unregister one active advertisement.
        instance_id = 2
        self.test_unregister_advertisement(advertisements[instance_id - 1],
                                           instance_id,
                                           advertising_disabled=False)

        self.test_start_discovery()

        # Test if advertising is reset correctly.Only instances [1, 3] are left.
        self.test_reset_advertising([1, 3])

    @test_case_log
    def test_case_broadcast(self):
        """Verify minimal test case for broadcasted advertising"""
        orig_min_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS
        orig_max_adv_interval_ms = self.DEFAULT_MIN_ADVERTISEMENT_INTERVAL_MS

        # We set a specific advertisement that uses the 'broadcast' mode
        advertisements = [advertisements_data.NEARBY_BROADCAST_ADV]

        self.test_reset_advertising()

        # Verify that registration is successful, and that all configured
        # properties are sent to the controller.
        self.register_advertisements(advertisements, orig_min_adv_interval_ms,
                                     orig_max_adv_interval_ms)

    def run_le_advertising_test(self, host, advertisements, test_type, \
                                num_iterations=1):
        """Running Bluetooth adapter LE advertising autotest.

        @param host: device under test host.
        @param advertisements: a list of advertisement instances.
        @param test_type: indicating one of three test types: multi-advertising,
                          single-advertising, reboot (stress only), or
                          suspend_resume (stress only).

        @raises TestNAError: if DUT has low kernel version (<=3.8.11)

        """
        self.host = host
        self.kernel_version = self.host.get_kernel_version()
        self.check_kernel_version()

        self.advertisements = advertisements
        self.first_advertisement = advertisements[0]
        self.two_advertisements = advertisements[3:5]
        self.three_advertisements = advertisements[0:3]
        self.five_advertisements = advertisements[0:5]
        self.sixth_advertisement = advertisements[5]

        self.bluetooth_le_facade = self.bluetooth_facade

        # Reset the adapter to forget previous stored data and turn it on.
        self.test_reset_on_adapter()

        if test_type == 'multi_advertising':
            # Run all test cases for multiple advertisements.
            self.test_case_SI200_RA3_CD_UA3()
            self.test_case_SI200_RA3_CD_RA1_CD_UA1_CD_UA3()
            self.test_case_SI200_RA3_CD_RS()
            self.test_case_SI200_RA3_CD_UA1_CD_RS()
            self.test_case_SI200_RA3_CD_UA1_CD_RA2_CD_UA4()
            # TODO (b/169603469) this test will fail on platforms supporting
            # >5 advertising slots due to new advertising feature, so disable
            # until test can be refactored.
            # self.test_case_SI200_RA5_CD_FRA1_CD_UA5()
            self.test_case_RA3_CD_SI200_CD_UA3()
            self.test_case_RA3_CD_SI200_CD_RS()
            self.test_case_RA3_CD_SI200_CD_UA1_CD_RS()
            self.test_case_RA3_CD_SI200_CD_SI2000_CD_UA3()
            # TODO (b/169603469) this test will fail on platforms supporting
            # >5 advertising slots due to new advertising feature, so disable
            # until test can be refactored.
            # self.test_case_RA5_CD_SI200_CD_FRA1_CD_UA5()
            self.test_case_RA3_CD_SI200_CD_FSI10_CD_FSI20000_CD_UA3()
            self.test_case_SI200_RA3_CD_PC_CD_UA3()
            self.test_case_RA3_CD_SI200_CD_PC_CD_UA3()

        elif test_type == 'single_advertising':
            # Run all test cases for single advertisement.
            # Note: it is required to change the advertisement instance
            #       so that the advertisement data could be monitored by btmon.
            #       Otherwise, the advertisement data would be just cached and
            #       reused such that the data would not be visible in btmon.
            self.test_case_SI200_RA1_CD_UA1()
            self.test_case_SI200_RA1_CD_RS()
            self.test_case_RA1_CD_SI200_CD_UA1()
            self.test_case_RA1_CD_SI200_CD_RS()
            self.test_case_RA1_CD_SI200_CD_FSI10_UA1_RA1_CD_UA1()
            self.test_case_RA1_CD_SI200_CD_FSI20000_UA1_RA1_CD_UA1()
            self.test_case_RA1_CD_SI200_CD_PC_CD_UA1()

        elif test_type == 'suspend_resume':
            # Run all test cases for suspend resume stress testing.
            for i in range(num_iterations):
                logging.info('Starting suspend resume loop #%d', i+1)
                self.test_case_SI200_RA3_CD_SR_CD_UA3()
                self.test_case_RA3_CD_SI200_CD_SR_CD_UA3()
                self.test_case_SI200_RA1_CD_SR_CD_UA1()
                self.test_case_RA1_CD_SI200_CD_SR_CD_UA1()

        elif test_type == 'reboot':
            # Run all test cases for reboot stress testing.
            for i in range(num_iterations):
                logging.info('Starting reboot loop #%d', i+1)
                self.test_case_SI200_RA3_CD_PC_CD_UA3()
                self.test_case_RA3_CD_SI200_CD_PC_CD_UA3()
                self.test_case_RA1_CD_SI200_CD_PC_CD_UA1()

        elif test_type == 'nearby':
            self.test_case_nearby_mediums_fast()
            self.test_case_adv_before_scan()

        elif test_type == 'broadcast':
            self.test_case_broadcast()
