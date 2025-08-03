# Lint as: python2, python3
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import json
import logging
import uuid
import xml.etree.ElementTree as ET

import common
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth import bluetooth_adapter_tests
from six.moves import range


class bluetooth_SDP_Test(object):
    """Base class with Properties and methods common across SDP tests"""
    version = 1

    MIN_ATTR_BYTE_CNT                = 7
    MAX_ATTR_BYTE_CNT                = 300

    SDP_SERVER_CLASS_ID              = 0x1000
    GAP_CLASS_ID                     = 0x1800
    BROWSE_GROUP_LIST_ATTR_ID        = 0x0005
    PUBLIC_BROWSE_ROOT               = 0x1002

    DOCUMENTATION_URL_ATTR_ID        = 0x000A
    CLIENT_EXECUTABLE_URL_ATTR_ID    = 0x000B
    ICON_URL_ATTR_ID                 = 0x000C
    PROTOCOL_DESCRIPTOR_LIST_ATTR_ID = 0x0004
    L2CAP_UUID                       = 0x0100
    ATT_UUID                         = 0x0007
    ATT_PSM                          = 0x001F
    PNP_INFORMATION_CLASS_ID         = 0x1200
    VERSION_NUMBER_LIST_ATTR_ID      = 0x0200
    SERVICE_DATABASE_STATE_ATTR_ID   = 0x0201
    AVRCP_TG_CLASS_ID                = 0x110C
    PROFILE_DESCRIPTOR_LIST_ATTR_ID  = 0x0009
    ADDITIONAL_PROTOCOLLIST_ATTR_ID  = 0x000D

    FAKE_SERVICE_PATH                = '/autotest/fake_service'
    BLUEZ_URL                        = 'http://www.bluez.org/'

    FAKE_SERVICE_CLASS_ID            = 0xCDEF
    FAKE_ATTRIBUTE_VALUE             = 42
    LANGUAGE_BASE_ATTRIBUTE_ID       = 0x0006

    FAKE_GENERAL_ATTRIBUTE_IDS       = [
                                        0x0003, # TP/SERVER/SA/BV-04-C
                                        0x0002, # TP/SERVER/SA/BV-06-C
                                        0x0007, # TP/SERVER/SA/BV-07-C
                                        0x0008, # TP/SERVER/SA/BV-10-C
                                        # TP/SERVER/SA/BV-09-C:
                                        LANGUAGE_BASE_ATTRIBUTE_ID
                                       ]

    FAKE_LANGUAGE_ATTRIBUTE_OFFSETS  = [
                                        0x0000, # TP/SERVER/SA/BV-12-C
                                        0x0001, # TP/SERVER/SA/BV-13-C
                                        0x0002  # TP/SERVER/SA/BV-14-C
                                       ]

    BLUETOOTH_BASE_UUID              = 0x0000000000001000800000805F9B34FB
    SERVICE_CLASS_ID_ATTR_ID         = 0x0001
    ERROR_CODE_INVALID_RECORD_HANDLE = 0x0002
    ERROR_CODE_INVALID_SYNTAX        = 0x0003
    ERROR_CODE_INVALID_PDU_SIZE      = 0x0004
    INVALID_RECORD_HANDLE            = 0xFEEE
    INVALID_SYNTAX_REQUEST           = '123'
    INVALID_PDU_SIZE                 = 11


    def build_service_record(self):
        """Build SDP record manually for the fake service.

        @return resulting record as string

        """
        value = ET.Element('uint16', {'value': str(self.FAKE_ATTRIBUTE_VALUE)})

        sdp_record = ET.Element('record')

        service_id_attr = ET.Element(
            'attribute', {'id': str(self.SERVICE_CLASS_ID_ATTR_ID)})
        sequence = ET.Element('sequence')
        sequence.append(
                ET.Element('uuid',
                           {'value': '0x%X' % self.FAKE_SERVICE_CLASS_ID}))
        service_id_attr.append(sequence)
        sdp_record.append(service_id_attr)

        for attr_id in self.FAKE_GENERAL_ATTRIBUTE_IDS:
            attr = ET.Element('attribute', {'id': str(attr_id)})
            attr.append(value)
            sdp_record.append(attr)

        for offset in self.FAKE_LANGUAGE_ATTRIBUTE_OFFSETS:
            attr_id = self.FAKE_ATTRIBUTE_VALUE + offset
            attr = ET.Element('attribute', {'id': str(attr_id)})
            attr.append(value)
            sdp_record.append(attr)

        sdp_record_str = ('<?xml version="1.0" encoding="UTF-8"?>' +
                          ET.tostring(sdp_record).decode('utf-8'))
        return sdp_record_str


class bluetooth_SDP_ServiceAttributeRequest(bluetooth_SDP_Test,
    bluetooth_adapter_tests.BluetoothAdapterTests):
    """
    Verify the correct behaviour of the device when searching for attributes of
    services.
    """
    version = 1

    MAX_REC_CNT                      = 3

    SERVICE_RECORD_HANDLE_ATTR_ID    = 0x0000

    NON_EXISTING_ATTRIBUTE_ID        = 0xFEDC

    @staticmethod
    def assert_equal(actual, expected):
        """Verify that |actual| is equal to |expected|.

        @param actual: The value we got.
        @param expected: The value we expected.
        @raise error.TestFail: If the values are unequal.
        """
        if actual != expected:
            raise error.TestFail(
                'Expected |%s|, got |%s|' % (expected, actual))


    @staticmethod
    def assert_nonempty_list(value):
        """Verify that |value| is a list, and that the list is non-empty.

        @param value: The value to check.
        @raise error.TestFail: If the value is not a list, or is empty.
        """
        if not isinstance(value, list):
            raise error.TestFail('Value is not a list. Got |%s|.' % value)

        if value == []:
            raise error.TestFail('List is empty')


    def get_single_handle(self, class_id):
        """Send a Service Search Request to get a handle for specific class ID.

        @param class_id: The class that we want a handle for.
        @return The record handle, as an int.
        @raise error.TestFail: If we failed to retrieve a handle.
        """
        res = json.loads(self.tester.service_search_request([class_id],
                         self.MAX_REC_CNT, dict()))
        if not (isinstance(res, list) and len(res) > 0):
            raise error.TestFail(
                    'Failed to retrieve handle for 0x%x' % class_id)
        return res[0]


    # TODO(quiche): Place this after get_attribute(), so all the tests are
    # grouped together.
    def test_record_handle_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-01-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        # Send Service Search Request to find out record handle for
        # SDP Server service.
        record_handle = self.get_single_handle(self.SDP_SERVER_CLASS_ID)

        # Send Service Attribute Request for Service Record Handle Attribute.
        res = json.loads(self.tester.service_attribute_request(
                  record_handle,
                  self.MAX_ATTR_BYTE_CNT,
                  [self.SERVICE_RECORD_HANDLE_ATTR_ID], {}))

        # Ensure that returned attribute is correct.
        self.assert_equal(res,
                          [self.SERVICE_RECORD_HANDLE_ATTR_ID, record_handle])


    def get_attribute(self, class_id, attr_id):
        """Get a single attribute of a single service

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @return attribute value if attribute exists, None otherwise

        """
        record_handle = self.get_single_handle(class_id)
        res = json.loads(self.tester.service_attribute_request(
                  record_handle, self.MAX_ATTR_BYTE_CNT, [attr_id], {}))
        if isinstance(res, list) and len(res) == 2 and res[0] == attr_id:
            return res[1]
        return None


    # TODO(quiche): Move this up, to be grouped with the other |assert|
    # methods.
    def assert_attribute_equals(self, class_id, attr_id, expected_value):
        """Verify that |attr_id| of service with |class_id| has |expected_value|

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @param expected_value: The expected value for the attribute.
        @raise error.TestFail: If the actual value differs from |expected_value|
        """
        self.assert_equal(self.get_attribute(class_id, attr_id),
                          expected_value)


    def test_browse_group_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-08-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        self.assert_attribute_equals(self.GAP_CLASS_ID,
                                     self.BROWSE_GROUP_LIST_ATTR_ID,
                                     [self.PUBLIC_BROWSE_ROOT])


    def test_icon_url_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-11-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        self.assert_attribute_equals(self.GAP_CLASS_ID,
                                     self.ICON_URL_ATTR_ID,
                                     self.BLUEZ_URL)


    def test_documentation_url_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-18-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        self.assert_attribute_equals(self.GAP_CLASS_ID,
                                     self.DOCUMENTATION_URL_ATTR_ID,
                                     self.BLUEZ_URL)


    def test_client_executable_url_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-19-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        self.assert_attribute_equals(self.GAP_CLASS_ID,
                                     self.CLIENT_EXECUTABLE_URL_ATTR_ID,
                                     self.BLUEZ_URL)


    def test_protocol_descriptor_list_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-05-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        value = self.get_attribute(self.GAP_CLASS_ID,
                                   self.PROTOCOL_DESCRIPTOR_LIST_ATTR_ID)

        # The first-layer protocol is L2CAP, using the PSM for ATT protocol.
        self.assert_equal(value[0], [self.L2CAP_UUID, self.ATT_PSM])

        # The second-layer protocol is ATT. The additional parameters are
        # ignored, since they may reasonably vary between implementations.
        self.assert_equal(value[1][0], self.ATT_UUID)


    def test_continuation_state(self):
        """Implementation of test TP/SERVER/SA/BV-03-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        record_handle = self.get_single_handle(self.PNP_INFORMATION_CLASS_ID)
        self.assert_nonempty_list(
            json.loads(self.tester.service_attribute_request(
                record_handle, self.MIN_ATTR_BYTE_CNT, [[0, 0xFFFF]], {})))


    def test_version_list_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-15-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        self.assert_nonempty_list(
            self.get_attribute(self.SDP_SERVER_CLASS_ID,
                self.VERSION_NUMBER_LIST_ATTR_ID))


    def test_service_database_state_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-16-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        state = self.get_attribute(self.SDP_SERVER_CLASS_ID,
                                   self.SERVICE_DATABASE_STATE_ATTR_ID)
        if not isinstance(state, int):
            raise error.TestFail('State is not an int: %s' % state)


    def test_profile_descriptor_list_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-17-C from SDP Specification.

        @raise error.TestFail: If list attribute not correct form.

        """
        profile_list = self.get_attribute(self.PNP_INFORMATION_CLASS_ID,
                                          self.PROFILE_DESCRIPTOR_LIST_ATTR_ID)

        if not isinstance(profile_list, list):
            raise error.TestFail('Value is not a list')
        self.assert_equal(len(profile_list), 1)

        if not isinstance(profile_list[0], list):
            raise error.TestFail('Item is not a list')
        self.assert_equal(len(profile_list[0]), 2)

        self.assert_equal(profile_list[0][0], self.PNP_INFORMATION_CLASS_ID)


    def test_additional_protocol_descriptor_list_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-21-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.

        """

        """AVRCP is not supported by Chromebook and no need to run this test
        self.assert_nonempty_list(
            self.get_attribute(self.AVRCP_TG_CLASS_ID,
                self.ADDITIONAL_PROTOCOLLIST_ATTR_ID))
        """

    def test_non_existing_attribute(self):
        """Implementation of test TP/SERVER/SA/BV-20-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        record_handle = self.get_single_handle(self.FAKE_SERVICE_CLASS_ID)
        res = json.loads(self.tester.service_attribute_request(
                  record_handle, self.MAX_ATTR_BYTE_CNT,
                  [self.NON_EXISTING_ATTRIBUTE_ID], {}))
        self.assert_equal(res, [])


    def test_fake_attributes(self):
        """Test values of attributes of the fake service record.

        @raise error.TestFail: If the DUT failed the test.
        """
        for attr_id in self.FAKE_GENERAL_ATTRIBUTE_IDS:
            self.assert_attribute_equals(self.FAKE_SERVICE_CLASS_ID,
                                         attr_id, self.FAKE_ATTRIBUTE_VALUE)

        for offset in self.FAKE_LANGUAGE_ATTRIBUTE_OFFSETS:
            record_handle = self.get_single_handle(self.FAKE_SERVICE_CLASS_ID)

            lang_base = json.loads(self.tester.service_attribute_request(
                            record_handle, self.MAX_ATTR_BYTE_CNT,
                            [self.LANGUAGE_BASE_ATTRIBUTE_ID], {}))
            attr_id = lang_base[1] + offset

            response = json.loads(self.tester.service_attribute_request(
                record_handle, self.MAX_ATTR_BYTE_CNT, [attr_id], {}))
            self.assert_equal(response, [attr_id, self.FAKE_ATTRIBUTE_VALUE])


    def test_invalid_record_handle(self):
        """Implementation of test TP/SERVER/SA/BI-01-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        res = json.loads(self.tester.service_attribute_request(
                          self.INVALID_RECORD_HANDLE, self.MAX_ATTR_BYTE_CNT,
                          [self.NON_EXISTING_ATTRIBUTE_ID], {}))
        self.assert_equal(res, self.ERROR_CODE_INVALID_RECORD_HANDLE)


    def test_invalid_request_syntax(self):
        """Implementation of test TP/SERVER/SA/BI-02-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        record_handle = self.get_single_handle(self.SDP_SERVER_CLASS_ID)
        res = json.loads(self.tester.service_attribute_request(
                          record_handle,
                          self.MAX_ATTR_BYTE_CNT,
                          [self.SERVICE_RECORD_HANDLE_ATTR_ID],
                          {'invalid_request':self.INVALID_SYNTAX_REQUEST}))
        self.assert_equal(res, self.ERROR_CODE_INVALID_SYNTAX)


    def test_invalid_pdu_size(self):
        """Implementation of test TP/SERVER/SA/BI-03-C from SDP Specification.

        @raise error.TestFail: If the DUT failed the test.
        """
        opts = dict({'forced_pdu_size':self.INVALID_PDU_SIZE})
        record_handle = self.get_single_handle(self.SDP_SERVER_CLASS_ID)
        res = json.loads(self.tester.service_attribute_request(record_handle,
            self.MAX_ATTR_BYTE_CNT, [self.SERVICE_RECORD_HANDLE_ATTR_ID], opts))
        self.assert_equal(res, self.ERROR_CODE_INVALID_PDU_SIZE)


    def correct_request_att_request_test(self):
        """Run basic tests for Service Attribute Request."""
        # Connect to the DUT via L2CAP using SDP socket.
        self.tester.connect(self.adapter['Address'])

        self.test_record_handle_attribute()
        self.test_browse_group_attribute()
        self.test_icon_url_attribute()
        self.test_documentation_url_attribute()
        self.test_client_executable_url_attribute()
        self.test_protocol_descriptor_list_attribute()
        self.test_continuation_state()
        self.test_version_list_attribute()
        self.test_service_database_state_attribute()
        self.test_profile_descriptor_list_attribute()
        self.test_additional_protocol_descriptor_list_attribute()
        self.test_fake_attributes()
        self.test_non_existing_attribute()
        self.test_invalid_record_handle()
        self.test_invalid_pdu_size()
        self.test_invalid_request_syntax()


    def sdp_service_attribute_request_test(self, device):
        """Runs service attribute request test"""

        if self.host.btpeer.get_platform() != 'RASPI':
            raise error.TestNAError('Test only runs on Raspi')

        self.tester = device
        # Reset the adapter to the powered on, discoverable state.
        if not self.bluetooth_facade.reset_on():
            raise error.TestFail('DUT adapter could not be powered on')
        if not self.bluetooth_facade.set_discoverable(True):
            raise error.TestFail('DUT could not be set as discoverable')

        self.adapter = self.bluetooth_facade.get_adapter_properties()

        # Create a fake service record in order to test attributes,
        # that are not present in any of existing services.
        uuid128 = ((self.FAKE_SERVICE_CLASS_ID << 96) +
                   self.BLUETOOTH_BASE_UUID)
        uuid_str = str(uuid.UUID(int=uuid128))
        sdp_record = self.build_service_record()
        self.bluetooth_facade.register_profile(self.FAKE_SERVICE_PATH,
                                     uuid_str,
                                     {"ServiceRecord": sdp_record})

        # Setup the tester as a generic computer.
        if not self.tester.setup('computer'):
            raise error.TestNAError('Tester could not be initialized')

        self.correct_request_att_request_test()



class bluetooth_SDP_ServiceBrowse(bluetooth_SDP_Test,
    bluetooth_adapter_tests.BluetoothAdapterTests):
    """
    Verify that the IUT behave correct during Service Browse procedure.
    """
    version = 1

    MAX_BROWSE_REC_CNT             = 100
    MAX_ATTR_BYTE_CNT       = 300
    SERVICE_CLASS_ID_LIST   = 0x0001
    BROWSE_GROUP_DESCRIPTOR = 0x1001
    GROUP_ID                = 0x0200


    def get_attribute_ssr_sar(self, class_id, attr_id, size):
        """Get service attributes using Service Search Request and Service
        Attribute Request.

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @param size: Preferred size of UUID.

        @return attribute value if attribute exists, None otherwise

        """
        handles = json.loads(self.tester.service_search_request(
                      [class_id], self.MAX_BROWSE_REC_CNT,
                      {'preferred_size':size}))

        if not (isinstance(handles, list) and len(handles) > 0):
            return None

        res = []
        for record_handle in handles:
            value = json.loads(self.tester.service_attribute_request(
                        record_handle, self.MAX_ATTR_BYTE_CNT, [attr_id], {}))
            if not (isinstance(value, list) and len(value) == 2 and
                value[0] == attr_id):
                return None
            res.append(value[1])

        return res


    def get_attribute_ssar(self, class_id, attr_id, size):
        """Get service attributes using Service Search Attribute Request.

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @param size: Preferred size of UUID.

        @return attribute value if attribute exists, None otherwise

        """
        response = json.loads(self.tester.service_search_attribute_request(
                       [class_id], self.MAX_ATTR_BYTE_CNT, [attr_id],
                       {'preferred_size':size}))

        if not isinstance(response, list):
            return None

        res = []
        for elem in response:
            if not (isinstance(elem, list) and len(elem) == 2 and
                    elem[0] == attr_id):
                return None
            res.append(elem[1])

        return res


    def test_attribute(self, class_id, attr_id, get_attribute):
        """Test service attributes using 16-bit, 32-bit and 128-bit
        size of UUID.

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @param get_attribute: Method to use to get an attribute value.

        @return attribute value if attribute exists and values from three tests
        are equal, None otherwise

        """
        result_16 = get_attribute(class_id, attr_id, 16)

        for size in 32, 128:
            result_cur = get_attribute(class_id, attr_id, size)
            if result_16 != result_cur:
                return None

        return result_16


    def service_browse(self, get_attribute):
        """Execute a Service Browse procedure.

        @param get_attribute: Method to use to get an attribute value.

        @return sorted list of unique services on the DUT, or False if browse
        did not finish correctly

        """
        # Find services on top of hierarchy.
        root_services = self.test_attribute(self.PUBLIC_BROWSE_ROOT,
                                            self.SERVICE_CLASS_ID_LIST,
                                            get_attribute)

        if not root_services:
            return False

        # Find additional browse groups.
        group_ids = self.test_attribute(self.BROWSE_GROUP_DESCRIPTOR,
                                        self.GROUP_ID,
                                        get_attribute)
        if not group_ids:
            return False

        # Find services from all browse groups.
        all_services = []
        for group_id in group_ids:
            services = self.test_attribute(group_id,
                                           self.SERVICE_CLASS_ID_LIST,
                                           get_attribute)
            if not services:
                return False
            all_services.extend(services)

        # Ensure that root services are among all services.
        for service in root_services:
            if service not in all_services:
                return False

        # Sort all services and remove duplicates.
        all_services.sort()
        last = 0
        for service in all_services[1:]:
            if all_services[last] != service:
                last += 1
                all_services[last] = service

        return all_services[:last + 1]


    def correct_request_browse_test(self):
        """Run basic tests for Service Browse procedure.

        @return True if all tests finishes correctly, False otherwise

        """

        # Connect to the DUT via L2CAP using SDP socket.
        self.tester.connect(self.adapter['Address'])

        browse_ssar = self.service_browse(self.get_attribute_ssar)
        if not browse_ssar:
            return False

        browse_ssr_sar = self.service_browse(self.get_attribute_ssr_sar)


        # Ensure that two different browse methods return the same results.
        return browse_ssar == browse_ssr_sar


    def sdp_service_browse_test(self, device):
        """Runs service browse test"""

        if self.host.btpeer.get_platform() != 'RASPI':
            raise error.TestNAError('Test only runs on Raspi')

        self.tester = device
        # Reset the adapter to the powered on, discoverable state.
        if not (self.bluetooth_facade.reset_on() and
                self.bluetooth_facade.set_discoverable(True)):
            raise error.TestFail('DUT could not be reset to initial state')

        self.adapter = self.bluetooth_facade.get_adapter_properties()

        # Setup the tester as a generic computer.
        if not self.tester.setup('computer'):
            raise error.TestNAError('Tester could not be initialized')

        # Since radio is involved, this test is not 100% reliable; instead we
        # repeat a few times until it succeeds.
        for failed_attempts in range(0, 5):
            if self.correct_request_browse_test():
                break
        else:
            raise error.TestFail('Expected device was not found')

        # Record how many attempts this took, hopefully we'll one day figure out
        # a way to reduce this to zero and then the loop above can go away.
        self.write_perf_keyval({'failed_attempts': failed_attempts })


class bluetooth_SDP_ServiceSearchAttributeRequest(bluetooth_SDP_Test,
    bluetooth_adapter_tests.BluetoothAdapterTests):
    """
    Verify the correct behaviour of the device when searching for services and
    attributes.
    """


    NON_EXISTING_SERVICE_CLASS_ID    = 0x9875
    PUBLIC_BROWSE_GROUP_CLASS_ID     = 0x1002

    NON_EXISTING_ATTRIBUTE_ID        = 0xABCD
    SERVICE_CLASS_ID_ATTRIBUTE_ID    = 0x0001
    VERSION_NUMBER_LIST_ATTR_ID      = 0x0200


    def fail_test(self, testname, value):
        """Raise an error for a particular SDP test.

        @param testname: a string representation of the test name.
        @param value: the value that did not pass muster.

        """
        raise error.TestFail('SDP test %s failed: got %s.' % (testname, value))


    def test_non_existing(self, class_id, attr_id):
        """Check that a single attribute of a single service does not exist

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.

        @raises error.TestFail if service or attribute does exists.

        """
        for size in 16, 32, 128:
            result = json.loads(self.tester.service_search_attribute_request(
                         [class_id],
                         self.MAX_ATTR_BYTE_CNT,
                         [attr_id],
                         {'preferred_size':size}))
            if result != []:
                raise error.TestFail('Attribute %s of class %s exists when it '
                                     'should not!' % (class_id, attr_id))


    def get_attribute_sssar(self, class_id, attr_id, size):
        """Get a single attribute of a single service using Service Search
        Attribute Request.

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.
        @param size: Preferred size of UUID.

        @return attribute value if attribute exists

        @raises error.TestFail if attribute does not exist

        """
        res = json.loads(self.tester.service_search_attribute_request(
                  [class_id], self.MAX_ATTR_BYTE_CNT, [attr_id],
                  {'preferred_size':size}))

        if (isinstance(res, list) and len(res) == 1 and
            isinstance(res[0], list) and len(res[0]) == 2 and
            res[0][0] == attr_id):
            return res[0][1]

        raise error.TestFail('Attribute %s of class %s does not exist! (size '
                             '%s)' % (class_id, attr_id, size))


    def test_attribute_sssar(self, class_id, attr_id):
        """Test a single attribute of a single service using 16-bit, 32-bit and
        128-bit size of UUID.

        @param class_id: Class ID of service to check.
        @param attr_id: ID of attribute to check.

        @return attribute value if attribute exists and values from three tests
        are equal

        @raises error.TestFail if attribute doesn't exist or values not equal

        """
        result_16 = self.get_attribute_sssar(class_id, attr_id, 16)
        for size in 32, 128:
            result_cur = self.get_attribute_sssar(class_id, attr_id, size)
            if result_16 != result_cur:
                raise error.TestFail('Attribute test failed %s: expected %s, '
                                     'got %s' % (size, result_16, result_cur))

        return result_16


    def test_non_existing_service(self):
        """Implementation of test TP/SERVER/SSA/BV-01-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        self.test_non_existing(self.NON_EXISTING_SERVICE_CLASS_ID,
                               self.SERVICE_CLASS_ID_ATTRIBUTE_ID)


    def test_non_existing_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-02-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        self.test_non_existing(self.PUBLIC_BROWSE_GROUP_CLASS_ID,
                               self.NON_EXISTING_ATTRIBUTE_ID)


    def test_non_existing_service_attribute(self):
        """Implementation of test TP/SERVER/SSA/BV-03-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        self.test_non_existing(self.NON_EXISTING_SERVICE_CLASS_ID,
                               self.NON_EXISTING_ATTRIBUTE_ID)


    def test_existing_service_attribute(self):
        """Implementation of test TP/SERVER/SSA/BV-04-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.SDP_SERVER_CLASS_ID,
                                    self.SERVICE_CLASS_ID_ATTRIBUTE_ID)
        if not value == [self.SDP_SERVER_CLASS_ID]:
            self.fail_test('TP/SERVER/SSA/BV-04-C', value)


    def test_service_database_state_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-08-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.SDP_SERVER_CLASS_ID,
                                    self.SERVICE_DATABASE_STATE_ATTR_ID)
        if not isinstance(value, int):
            self.fail_test('TP/SERVER/SSA/BV-08-C', value)


    def test_protocol_descriptor_list_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-11-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.GAP_CLASS_ID,
                                    self.PROTOCOL_DESCRIPTOR_LIST_ATTR_ID)

        # The first-layer protocol is L2CAP, using the PSM for ATT protocol.
        if value[0] != [self.L2CAP_UUID, self.ATT_PSM]:
            self.fail_test('TP/SERVER/SSA/BV-11-C', value)

        # The second-layer protocol is ATT. The additional parameters are
        # ignored, since they may reasonably vary between implementations.
        if value[1][0] != self.ATT_UUID:
            self.fail_test('TP/SERVER/SSA/BV-11-C', value)



    def test_browse_group_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-12-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.GAP_CLASS_ID,
                                    self.BROWSE_GROUP_LIST_ATTR_ID)
        if not value == [self.PUBLIC_BROWSE_ROOT]:
            self.fail_test('TP/SERVER/SSA/BV-12-C', value)


    def test_icon_url_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-15-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.GAP_CLASS_ID,
                                    self.ICON_URL_ATTR_ID)
        if not value == self.BLUEZ_URL:
            self.fail_test('TP/SERVER/SSA/BV-15-C', value)


    def test_version_list_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-19-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.SDP_SERVER_CLASS_ID,
                                    self.VERSION_NUMBER_LIST_ATTR_ID)
        if not isinstance(value, list) and value != []:
            self.fail_test('TP/SERVER/SSA/BV-19-C', value)


    def test_profile_descriptor_list_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-20-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.PNP_INFORMATION_CLASS_ID,
                                    self.PROFILE_DESCRIPTOR_LIST_ATTR_ID)
        if not (isinstance(value, list) and len(value) == 1 and
                isinstance(value[0], list) and len(value[0]) == 2 and
                value[0][0] == self.PNP_INFORMATION_CLASS_ID):
            self.fail_test('TP/SERVER/SSA/BV-20-C', value)


    def test_documentation_url_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-21-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.GAP_CLASS_ID,
                                    self.DOCUMENTATION_URL_ATTR_ID)
        if not value == self.BLUEZ_URL:
            self.fail_test('TP/SERVER/SSA/BV-21-C', value)


    def test_client_executable_url_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-22-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        value = self.test_attribute_sssar(self.GAP_CLASS_ID,
                                    self.CLIENT_EXECUTABLE_URL_ATTR_ID)
        if not value == self.BLUEZ_URL:
            self.fail_test('TP/SERVER/SSA/BV-22-C', value)


    def test_additional_protocol_descriptor_list_attribute_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-23-C from SDP Specification.

        @raises error.TestFail if test fails

        """

        """AVRCP is not supported by Chromebook and no need to run this test
        value = self.test_attribute_sssar(self.AVRCP_TG_CLASS_ID,
                                    self.ADDITIONAL_PROTOCOLLIST_ATTR_ID)
        if not isinstance(value, list) and value != []:
            self.fail_test('TP/SERVER/SSA/BV-23-C', value)
        """

    def test_fake_attributes_sssar(self):
        """Test values of attributes of the fake service record.

        @raises error.TestFail if test fails

        """
        for attr_id in self.FAKE_GENERAL_ATTRIBUTE_IDS:
            value = self.test_attribute_sssar(self.FAKE_SERVICE_CLASS_ID,
                                              attr_id)
            if value != self.FAKE_ATTRIBUTE_VALUE:
                self.fail_test('fake service attributes', value)

        for offset in self.FAKE_LANGUAGE_ATTRIBUTE_OFFSETS:
            lang_base = self.test_attribute_sssar(self.FAKE_SERVICE_CLASS_ID,
                                            self.LANGUAGE_BASE_ATTRIBUTE_ID)
            attr_id = lang_base + offset
            value = self.test_attribute_sssar(self.FAKE_SERVICE_CLASS_ID,
                                              attr_id)
            if value != self.FAKE_ATTRIBUTE_VALUE:
                self.fail_test('fake service attributes', value)


    def test_continuation_state_sssar(self):
        """Implementation of test TP/SERVER/SSA/BV-06-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        for size in 16, 32, 128:
            # This request should generate a long response, which will be
            # split into 98 chunks.
            value = json.loads(self.tester.service_search_attribute_request(
                        [self.PUBLIC_BROWSE_GROUP_CLASS_ID],
                        self.MIN_ATTR_BYTE_CNT,
                        [[0, 0xFFFF]], {'preferred_size':size}))
            if not isinstance(value, list) or value == []:
                self.fail_test('TP/SERVER/SSA/BV-06-C', value)


    def test_invalid_request_syntax_sssar(self):
        """Implementation of test TP/SERVER/SSA/BI-01-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        for size in 16, 32, 128:
            value = json.loads(self.tester.service_search_attribute_request(
                        [self.SDP_SERVER_CLASS_ID],
                        self.MAX_ATTR_BYTE_CNT,
                        [self.SERVICE_CLASS_ID_ATTRIBUTE_ID],
                        {'preferred_size':size,
                        'invalid_request':'9875'}))
            if value != self.ERROR_CODE_INVALID_SYNTAX:
                self.fail_test('TP/SERVER/SSA/BI-01-C', value)


    def test_invalid_pdu_size_sssar(self):
        """Implementation of test TP/SERVER/SSA/BI-02-C from SDP Specification.

        @raises error.TestFail if test fails

        """
        for size in 16, 32, 128:
            value = json.loads(self.tester.service_search_attribute_request(
                        [self.SDP_SERVER_CLASS_ID],
                        self.MAX_ATTR_BYTE_CNT,
                        [self.SERVICE_CLASS_ID_ATTRIBUTE_ID],
                        {'preferred_size':size,
                        'forced_pdu_size':100}))
            if value != self.ERROR_CODE_INVALID_PDU_SIZE:
                self.fail_test('TP/SERVER/SSA/BI-02-C', value)


    def correct_request_search_att_test(self):
        """Run tests for Service Search Attribute request.

        @raises error.TestFail if any test fails

        """
        # connect to the DUT via L2CAP using SDP socket
        self.tester.connect(self.adapter['Address'])

        self.test_non_existing_service()
        self.test_non_existing_attribute_sssar()
        self.test_non_existing_service_attribute()
        #self.test_existing_service_attribute()
        self.test_service_database_state_attribute_sssar()
        self.test_protocol_descriptor_list_attribute_sssar()
        self.test_browse_group_attribute_sssar()
        self.test_icon_url_attribute_sssar()
        self.test_version_list_attribute_sssar()
        self.test_profile_descriptor_list_attribute_sssar()
        self.test_documentation_url_attribute_sssar()
        self.test_client_executable_url_attribute_sssar()
        self.test_additional_protocol_descriptor_list_attribute_sssar()
        self.test_fake_attributes_sssar()
        self.test_continuation_state_sssar()
        self.test_invalid_request_syntax_sssar()
        self.test_invalid_pdu_size_sssar()
        logging.info('correct_request finished successfully!')


    def sdp_service_search_attribute_request_test(self, device):
        """Runs service search attribute request test"""

        if self.host.btpeer.get_platform() != 'RASPI':
            raise error.TestNAError('Test only runs on Raspi')

        self.tester = device
        # Reset the adapter to the powered on, discoverable state.
        if not self.bluetooth_facade.reset_on():
            raise error.TestFail('DUT adapter could not be powered on')
        if not self.bluetooth_facade.set_discoverable(True):
            raise error.TestFail('DUT could not be set as discoverable')

        self.adapter = self.bluetooth_facade.get_adapter_properties()

        # Create a fake service record in order to test attributes,
        # that are not present in any of existing services.
        uuid128 = ((self.FAKE_SERVICE_CLASS_ID << 96) +
                   self.BLUETOOTH_BASE_UUID)
        uuid_str = str(uuid.UUID(int=uuid128))
        sdp_record = self.build_service_record()
        self.bluetooth_facade.register_profile(self.FAKE_SERVICE_PATH,
                                     uuid_str,
                                     {"ServiceRecord": sdp_record})

        # Setup the tester as a generic computer.
        if not self.tester.setup('computer'):
            raise error.TestNAError('Tester could not be initialized')

        # Since radio is involved, this test is not 100% reliable; instead we
        # repeat a few times until it succeeds.
        passing = False
        for failed_attempts in range(0, 4):
            try:
                self.correct_request_search_att_test()
                passing = True
            except error.TestFail as e:
                logging.warning('Ignoring error: %s', e)
            if passing:
                break
        else:
            self.correct_request_search_att_test()

        # Record how many attempts this took, hopefully we'll one day figure out
        # a way to reduce this to zero and then the loop above can go away.
        self.write_perf_keyval({'failed_attempts': failed_attempts})


class bluetooth_SDP_ServiceSearchRequestBasic(
    bluetooth_adapter_tests.BluetoothAdapterTests):
    """
    Verify the correct behaviour of the device when searching for services.
    """
    version = 1

    SDP_SERVER_CLASS_ID                = 0x1000
    NO_EXISTING_SERVICE_CLASS_ID       = 0x0001
    FAKE_SERVICES_CNT                  = 300
    FAKE_SERVICES_PATH                 = '/autotest/fake_service_'
    FAKE_SERVICES_CLASS_ID             = 0xABCD
    BLUETOOTH_BASE_UUID                = 0x0000000000001000800000805F9B34FB
    SSRB_INVALID_PDU_SIZE              = 9875
    ERROR_CODE_INVALID_REQUEST_SYNTAX  = 0x0003
    ERROR_CODE_INVALID_PDU_SIZE        = 0x0004


    def correct_request_basic_test(self):
        """Search the existing service on the DUT using the Tester.

        @return True if found, False if not found

        """
        # connect to the DUT via L2CAP using SDP socket
        self.tester.connect(self.adapter['Address'])

        for size in 16, 32, 128:
            # test case TP/SERVER/SS/BV-01-C:
            # at least the SDP server service exists
            resp = json.loads(self.tester.service_search_request(
                              [self.SDP_SERVER_CLASS_ID], 3,
                              {'preferred_size':size}))
            if resp != [0]:
                return False
            # test case TP/SERVER/SS/BV-04-C:
            # Service with Class ID = 0x0001 should never exist, as this UUID is
            # reserved as Bluetooth Core Specification UUID
            resp = json.loads(self.tester.service_search_request(
                              [self.NO_EXISTING_SERVICE_CLASS_ID], 3,
                              {'preferred_size':size}))
            if resp != []:
                return False
            # test case TP/SERVER/SS/BV-03-C:
            # request the fake services' Class ID to force SDP to use
            # continuation state
            resp = json.loads(self.tester.service_search_request(
                              [self.FAKE_SERVICES_CLASS_ID],
                              self.FAKE_SERVICES_CNT * 2,
                              {'preferred_size':size}))
            if len(resp) != self.FAKE_SERVICES_CNT:
                return False
            # test case TP/SERVER/SS/BI-01-C:
            # send a Service Search Request with intentionally invalid PDU size
            resp = json.loads(self.tester.service_search_request(
                              [self.SDP_SERVER_CLASS_ID], 3,
                              {'preferred_size':size,
                              'forced_pdu_size':self.SSRB_INVALID_PDU_SIZE}))
            if resp != self.ERROR_CODE_INVALID_PDU_SIZE:
                return False
            # test case TP/SERVER/SS/BI-02-C:
            # send a Service Search Request with invalid syntax
            resp = json.loads(self.tester.service_search_request(
                              [self.SDP_SERVER_CLASS_ID], 3,
                              {'preferred_size':size, 'invalid_request':True}))
            if resp != self.ERROR_CODE_INVALID_REQUEST_SYNTAX:
                return False

        return True


    def sdp_service_search_request_basic_test(self, device):
        """Runs service search request basic test"""

        if self.host.btpeer.get_platform() != 'RASPI':
            raise error.TestNAError('Test only runs on Raspi')

        self.tester = device
        # Reset the adapter to the powered on, discoverable state.
        if not (self.bluetooth_facade.reset_on() and
                self.bluetooth_facade.set_discoverable(True)):
            raise error.TestFail('DUT could not be reset to initial state')

        self.adapter = self.bluetooth_facade.get_adapter_properties()

        # Setup the tester as a generic computer.
        if not self.tester.setup('computer'):
            raise error.TestNAError('Tester could not be initialized')

        # Create many fake services with the same Class ID
        for num in range(0, self.FAKE_SERVICES_CNT):
            path_str = self.FAKE_SERVICES_PATH + str(num)
            uuid128 = ((self.FAKE_SERVICES_CLASS_ID << 96) +
                      self.BLUETOOTH_BASE_UUID)
            uuid_str = str(uuid.UUID(int=uuid128))
            self.bluetooth_facade.register_profile(path_str, uuid_str, {})

        # Since radio is involved, this test is not 100% reliable; instead we
        # repeat a few times until it succeeds.
        for failed_attempts in range(0, 5):
            if self.correct_request_basic_test():
                break
        else:
            raise error.TestFail('Expected device was not found')

        # Record how many attempts this took, hopefully we'll one day figure out
        # a way to reduce this to zero and then the loop above can go away.
        self.write_perf_keyval({'failed_attempts': failed_attempts })


class BluetoothSDPTests(bluetooth_SDP_ServiceAttributeRequest,
                        bluetooth_SDP_ServiceBrowse,
                        bluetooth_SDP_ServiceSearchAttributeRequest,
                        bluetooth_SDP_ServiceSearchRequestBasic):
    """Derived class that simplifies inheritance of sdp tests"""
    pass
