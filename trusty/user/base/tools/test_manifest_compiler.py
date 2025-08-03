#!/bin/sh
""":" # Shell script (in docstring to appease pylint)
# Find and invoke hermetic python3 interpreter
. "`dirname $0`/../../../vendor/google/aosp/scripts/envsetup.sh"
exec "$PY3" "$0" "$@"
# Shell script end

Command to run tests:
  python3 -m unittest -v test_manifest_compiler
"""

import unittest

import manifest_compiler

TEST_UUID = "SAMPLE_UUID"
TEST_PORT = "SAMPLE_PORT"
TEST_SIZE = "SAMPLE_SIZE"

class TestManifest(unittest.TestCase):
    """Test with integer value as input to get_string"""

    def test_get_string_1(self):
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"data": 1234}
        data = manifest_compiler.get_string(config_data, "data", constants, log)
        self.assertEqual(len(config_data), 0)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_string_2(self):
        """Test with valid uuid value as input to get_string"""
        constants = {}
        log = manifest_compiler.Log()
        uuid = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        config_data = {"data": uuid}
        data = manifest_compiler.get_string(config_data, "data", constants, log)
        self.assertEqual(len(config_data), 0)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, uuid)

    def test_get_string_3(self):
        """Test with empty string"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"data": ""}
        data = manifest_compiler.get_string(config_data, "data", constants, log)
        self.assertEqual(len(config_data), 0)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, "")

    def test_get_string_4(self):
        """Test with empty config data and non optional field"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_string(config_data, "data", constants, log)
        self.assertEqual(len(config_data), 0)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_string_5(self):
        """Test with non-existing attribute which is optional"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_string(config_data, "data", constants, log,
                                            optional=True, default="")
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, "")

    def test_get_string_6(self):
        """Test with empty config with required field"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_string(config_data, "data", constants, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_int_when_valid_values_given(self):
        """Test get_int called with valid values"""
        cases = [
            ("string of integers", "4096", 4096),
            ("integer value", 4096, 4096),
            ("valid hex", "0X7f010000", 0X7f010000),
        ]

        for msg, int_value, expected_value in cases:
            with self.subTest(msg, value=int_value,
                              expected_value=expected_value):
                constants = {}
                log = manifest_compiler.Log()
                config_data = {"data": int_value}
                data = manifest_compiler.get_int(config_data, "data", constants,
                                                 log)
                self.assertEqual(len(config_data), 0)
                self.assertFalse(log.error_occurred())
                self.assertEqual(data, expected_value)

    def test_get_int_when_invalid_values_given(self):
        """Test get_int called with invalid values"""
        cases = [
            ("empty string", ""),
            ("invalid hex", "0X7k010000"),
            ("contain non-integer value", "123A7"),
            ("boolean value", True),
        ]

        for msg, int_value in cases:
            with self.subTest(msg, value=int_value):
                constants = {}
                log = manifest_compiler.Log()
                config_data = {"data": int_value}
                data = manifest_compiler.get_int(config_data, "data", constants,
                                                 log)
                self.assertEqual(len(config_data), 0)
                self.assertTrue(log.error_occurred())
                self.assertIsNone(data)

    def test_get_int_missing_required_field(self):
        """Test with empty config data and non optional field"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_int(config_data, "data", constants, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_int_missing_optional_field(self):
        """Test with non-existing attribute which is optional field"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_int(config_data, "data", constants, log,
                                         optional=True)
        self.assertFalse(log.error_occurred())
        self.assertIsNone(data)

    def test_get_int_missing_optional_field_with_default(self):
        """Test with non-existing attribute, which is optional field and default
        value given.
        """
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_int(config_data, "data", constants, log,
                                         optional=True, default=0)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 0)

    def test_get_boolean_1(self):
        """Test with valid boolean values"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"data": True}
        data = manifest_compiler.get_boolean(config_data, "data", constants,
                                             log)
        self.assertFalse(log.error_occurred())
        self.assertTrue(data)

    def test_get_boolean_2(self):
        """Test with invalid values"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"data": "True"}
        data = manifest_compiler.get_boolean(config_data, "data", constants,
                                             log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_boolean_3(self):
        """Test with invalid values"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"data": 1}
        data = manifest_compiler.get_boolean(config_data, "data", constants,
                                             log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_boolean_4(self):
        """Test with empty config data with non optional field"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_boolean(config_data, "data", constants,
                                             log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_boolean_5(self):
        """ Test with non-existing attribute which is optional"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_boolean(config_data, "data", constants,
                                             log, optional=True, default=False)
        self.assertFalse(log.error_occurred())
        self.assertFalse(data)

    def test_get_list_1(self):
        """Test with valid value as list"""
        log = manifest_compiler.Log()
        sample_list = [1, 2, 3]
        config_data = {"data": sample_list}
        data = manifest_compiler.get_list(config_data, "data", log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, sample_list)

    def test_get_list_2(self):
        """Test with non-existing attribute with optional field"""
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_list(config_data, "data", log,
                                          optional=True, default=[])
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, [])

    def test_get_list_3(self):
        """Test with empty config data with required field"""
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_list(config_data, "data", log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_list_4(self):
        """Test with invalid value"""
        log = manifest_compiler.Log()
        config_data = {"data": 123}
        manifest_compiler.get_list(config_data, "data", log, optional=True)
        self.assertTrue(log.error_occurred())

    def test_get_dict_1(self):
        """Test with valid value as dict"""
        log = manifest_compiler.Log()
        sample_dict = {"attr": 1}
        config_data = {"data": sample_dict}
        data = manifest_compiler.get_dict(config_data, "data", log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, sample_dict)

    def test_get_dict_2(self):
        """Test with non-existing attribute with optional field"""
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_dict(config_data, "data", log,
                                          optional=True, default={})
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, {})

    def test_get_dict_3(self):
        """Test with empty config data with required field"""
        log = manifest_compiler.Log()
        config_data = {}
        data = manifest_compiler.get_dict(config_data, "data", log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_get_dict_4(self):
        """Test with invalid value"""
        log = manifest_compiler.Log()
        config_data = {"data": 123}
        manifest_compiler.get_dict(config_data, "data", log, optional=True)
        self.assertTrue(log.error_occurred())

    def test_validate_uuid_1(self):
        """Test with valid UUID with hex values"""
        log = manifest_compiler.Log()
        uuid_in = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        data = manifest_compiler.parse_uuid(uuid_in, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data.hex(), uuid_in.replace("-", ""))

    def test_validate_uuid_2(self):
        """Test with invalid UUID containing one byte less"""
        log = manifest_compiler.Log()
        uuid = "902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        data = manifest_compiler.parse_uuid(uuid, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_uuid_3(self):
        """Test with invalid number of bytes in UUID groups"""
        log = manifest_compiler.Log()
        uuid = "5f902ace5e-5c-4cd8-ae54-87b88c22ddaf"
        data = manifest_compiler.parse_uuid(uuid, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_uuid_6(self):
        """Test with valid UUID value but ungrouped"""
        log = manifest_compiler.Log()
        uuid = "5f902ace5e5c4cd8ae5487b88c22ddaf"
        data = manifest_compiler.parse_uuid(uuid, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_uuid_7(self):
        """Test with UUID containing invalid hex digits"""
        log = manifest_compiler.Log()
        uuid = "12345678-gggg-1222-3333-222111233222"
        data = manifest_compiler.parse_uuid(uuid, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_uuid_8(self):
        """Test with invalid UUID value"""
        log = manifest_compiler.Log()
        uuid = ""
        data = manifest_compiler.parse_uuid(uuid, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_1(self):
        """Test with valid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(4096,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 4096)

    def test_validate_memory_size_2(self):
        """Test with valid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(8192,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 8192)

    def test_validate_memory_size_3(self):
        """Test flag controlling whether zero is valid or not"""
        log = manifest_compiler.Log()

        data = manifest_compiler.parse_memory_size(0,
                                                   manifest_compiler.MIN_STACK,
                                                   log, zero_is_ok=True)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 0)

        data = manifest_compiler.parse_memory_size(0,
                                                   manifest_compiler.MIN_STACK,
                                                   log, zero_is_ok=False)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_4(self):
        """Test with invalid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(-4096,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_5(self):
        """Test with invalid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(4095,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_6(self):
        """Test with invalid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(16777217,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_7(self):
        """Test with invalid memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(1024,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_memory_size_8(self):
        """Test with valid large integer value (2**32) as memory size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_memory_size(4294967296,
                                                   manifest_compiler.MIN_STACK,
                                                   log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 4294967296)

    def test_parse_shadow_stack_size_1(self):
        """Test with valid shadow stack size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_shadow_stack_size(4096, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, 4096)

    def test_parse_shadow_stack_size_2(self):
        """Test without shadow stack size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_shadow_stack_size(None, log)
        self.assertFalse(log.error_occurred())
        self.assertIsNone(data)

    def test_parse_shadow_stack_size_3(self):
        """Test with invalid shadow stack size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_shadow_stack_size(-1, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_parse_shadow_stack_size_4(self):
        """Test with invalid shadow stack size"""
        log = manifest_compiler.Log()
        data = manifest_compiler.parse_shadow_stack_size(1, log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(data)

    def test_validate_mem_map_1(self):
        """Test with a single memory mapping"""
        constants = {}
        mem_map_ref_data = [{"id": 1, "addr": 0x70000000, "size": 0x1000}]

        mem_map_json_data = [{"id": 1, "addr": "0x70000000", "size": "0x1000"}]

        log = manifest_compiler.Log()
        mem_io_map_list = manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertFalse(log.error_occurred())

        for (memio_map, memio_ref_data) in zip(
                mem_io_map_list, mem_map_ref_data):
            self.assertEqual(memio_map.id, memio_ref_data["id"])
            self.assertEqual(memio_map.addr, memio_ref_data["addr"])
            self.assertEqual(memio_map.size, memio_ref_data["size"])

    def test_validate_mem_map_2(self):
        """Test with multiple memory mapping"""
        constants = {}
        mem_map_ref_data = [{"id": 1, "addr": 0x70000000, "size": 0x1000},
                            {"id": 2, "addr": 0x70010000, "size": 0x100},
                            {"id": 3, "addr": 0x70020000, "size": 0x4}]

        mem_map_json_data = [{"id": 1, "addr": "0x70000000", "size": "0x1000"},
                             {"id": 2, "addr": "0x70010000", "size": "0x100"},
                             {"id": 3, "addr": "0x70020000", "size": "0x4"}]

        log = manifest_compiler.Log()
        mem_io_map_list = manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertFalse(log.error_occurred())

        for (memio_map, memio_ref_data) in zip(
                mem_io_map_list, mem_map_ref_data):
            self.assertEqual(memio_map.id, memio_ref_data["id"])
            self.assertEqual(memio_map.addr, memio_ref_data["addr"])
            self.assertEqual(memio_map.size, memio_ref_data["size"])

    def test_validate_mem_map_3(self):
        """Test with a unknown entry in memory mapping"""
        constants = {}
        mem_map_json_data = [{"id": 1, "addr": "0x70000000", "size": "0x1000",
                              "offset": "0x70001000"}]

        log = manifest_compiler.Log()
        manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_mem_map_4(self):
        """Test with a empty memory mapping entry"""
        constants = {}
        mem_map_json_data = []

        log = manifest_compiler.Log()
        mem_io_map_list = manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertFalse(mem_io_map_list)

    def test_validate_mem_map_5(self):
        """Test with a memory mapping entry with missing "size" field"""
        constants = {}
        mem_map_json_data = [{"id": 1, "addr": "0x70000000"}]

        log = manifest_compiler.Log()
        manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_mem_map_6(self):
        """Test with a memory mapping entry with invalid JSON format. Pass
        invalid list of JSON attributes
        """
        constants = {}
        mem_map_json_data = ["id", 1, "addr", "0x70000000"]

        log = manifest_compiler.Log()
        manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_mem_map_7(self):
        """Test with a memory mapping entry with invalid JSON format. Pass a
        MEM_MAP JSON object instead of list of MEM_MAP JSON objects.
        """
        constants = {}
        config_data = {manifest_compiler.MEM_MAP:
                       {"id": 1, "addr": "0x70000000"}}

        log = manifest_compiler.Log()
        manifest_compiler.parse_mem_map(
            manifest_compiler.get_list(
                config_data, manifest_compiler.MEM_MAP, log,
                optional=True),
            manifest_compiler.MEM_MAP, constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_mem_map_8(self):
        """Test duplicate mem_map ID fails"""
        constants = {}
        mem_map_json_data = [{"id": 1, "addr": "0x70000000", "size": "0x1000"},
                             {"id": 1, "addr": "0x70010000", "size": "0x1000"}]

        log = manifest_compiler.Log()
        manifest_compiler.parse_mem_map(
            mem_map_json_data, manifest_compiler.MEM_MAP, constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_mgmt_flags_1(self):
        """Test with a valid management flags"""
        constants = {}
        mgmt_flags_ref_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: True,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: True,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: True}
        mgmt_flags_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: True,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: True,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: True}

        log = manifest_compiler.Log()
        mgmt_flags = manifest_compiler.parse_mgmt_flags(
            mgmt_flags_data, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(mgmt_flags.restart_on_exit,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT])
        self.assertEqual(mgmt_flags.deferred_start,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_DEFERRED_START])
        self.assertEqual(mgmt_flags.non_critical_app,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP])

    def test_validate_mgmt_flags_2(self):
        """Test with a valid management flags"""
        constants = {}
        mgmt_flags_ref_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False}
        mgmt_flags_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False}

        log = manifest_compiler.Log()
        mgmt_flags = manifest_compiler.parse_mgmt_flags(
            mgmt_flags_data, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(mgmt_flags.restart_on_exit,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT])
        self.assertEqual(mgmt_flags.deferred_start,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_DEFERRED_START])
        self.assertEqual(mgmt_flags.non_critical_app,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP])

    def test_validate_mgmt_flags_3(self):
        """Test with a valid management flags"""
        constants = {}
        mgmt_flags_ref_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: True,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False}
        mgmt_flags_data = {
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: True,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False}

        log = manifest_compiler.Log()
        mgmt_flags = manifest_compiler.parse_mgmt_flags(
            mgmt_flags_data, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(mgmt_flags.restart_on_exit,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT])
        self.assertEqual(mgmt_flags.deferred_start,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_DEFERRED_START])
        self.assertEqual(mgmt_flags.non_critical_app,
                         mgmt_flags_ref_data[
                             manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP])

    def test_validate_mgmt_flags_4(self):
        """Test with a management flags missing
        manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT
        """
        constants = {}
        mgmt_flags_data = {manifest_compiler.MGMT_FLAG_DEFERRED_START: True}

        log = manifest_compiler.Log()
        mgmt_flags = manifest_compiler.parse_mgmt_flags(
            mgmt_flags_data, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertIsNone(mgmt_flags.restart_on_exit)
        self.assertTrue(mgmt_flags.deferred_start)
        self.assertIsNone(mgmt_flags.non_critical_app)

    def test_validate_mgmt_flags_5(self):
        """Test with a empty management flags"""
        constants = {}
        mgmt_flags_data = {}

        log = manifest_compiler.Log()
        mgmt_flags = manifest_compiler.parse_mgmt_flags(
            mgmt_flags_data, constants, log)
        self.assertFalse(log.error_occurred())
        self.assertIsNone(mgmt_flags.restart_on_exit)
        self.assertIsNone(mgmt_flags.deferred_start)
        self.assertIsNone(mgmt_flags.non_critical_app)

    def test_validate_mgmt_flags_6(self):
        """Test with a mgmt_flags as array of flags"""
        constants = {}
        config_data = {manifest_compiler.MGMT_FLAGS: [{
            manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: True,
            manifest_compiler.MGMT_FLAG_DEFERRED_START: True,
            manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: True}]}

        log = manifest_compiler.Log()
        manifest_compiler.parse_mgmt_flags(
            manifest_compiler.get_dict(
                config_data, manifest_compiler.MGMT_FLAGS, log,
                optional=True),
            constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_start_port_1(self):
        """Test with a single start_ports entry"""
        constants_data = [{
            "header": "test.h",
            "constants": [{
                "name": "LOADABLE_START_PORT",
                "value": "com.android.trusty.appmgmt.loadable.start",
                "type": "port"}]
        }]
        ref_data = [{
            manifest_compiler.START_PORT_NAME:
                "com.android.trusty.appmgmt.loadable.start",
            manifest_compiler.START_PORT_FLAGS: {
                manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False}}]

        port_conf_data = [{
            manifest_compiler.START_PORT_NAME:
                "LOADABLE_START_PORT",
            manifest_compiler.START_PORT_FLAGS: {
                manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False}}]

        log = manifest_compiler.Log()
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)
        start_ports_list = manifest_compiler.parse_app_start_ports(
            port_conf_data, manifest_compiler.START_PORTS, constants, log)
        self.assertFalse(log.error_occurred())

        for (start_port, ref_port) in zip(
                start_ports_list, ref_data):
            self.assertEqual(start_port.name,
                             ref_port[manifest_compiler.START_PORT_NAME])
            ref_flags = ref_port[manifest_compiler.START_PORT_FLAGS]
            self.assertEqual(
                start_port.start_port_flags.allow_ta_connect,
                ref_flags[manifest_compiler.START_PORT_ALLOW_TA_CONNECT])
            self.assertEqual(
                start_port.start_port_flags.allow_ns_connect,
                ref_flags[manifest_compiler.START_PORT_ALLOW_NS_CONNECT])

    def test_validate_start_port_2(self):
        """Test with a  multiple start_ports entry"""
        constants = {}
        ref_data = [
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.loadable.start",
                manifest_compiler.START_PORT_FLAGS: {
                    manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                    manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False
                }
            },
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.portstartsrv.shutdown",
                manifest_compiler.START_PORT_FLAGS: {
                    manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                    manifest_compiler.START_PORT_ALLOW_NS_CONNECT: True
                }
            }]

        port_conf_data = [
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.loadable.start",
                manifest_compiler.START_PORT_FLAGS: {
                    manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                    manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False}
            },
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.portstartsrv.shutdown",
                manifest_compiler.START_PORT_FLAGS: {
                    manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                    manifest_compiler.START_PORT_ALLOW_NS_CONNECT: True}
            }]

        log = manifest_compiler.Log()
        start_ports_list = manifest_compiler.parse_app_start_ports(
            port_conf_data, manifest_compiler.START_PORTS, constants, log)
        self.assertFalse(log.error_occurred())

        for (start_port, ref_port) in zip(
                start_ports_list, ref_data):
            self.assertEqual(start_port.name,
                             ref_port[manifest_compiler.START_PORT_NAME])
            ref_port_flags = ref_port[manifest_compiler.START_PORT_FLAGS]
            self.assertEqual(
                start_port.start_port_flags.allow_ta_connect,
                ref_port_flags[manifest_compiler.START_PORT_ALLOW_TA_CONNECT])
            self.assertEqual(
                start_port.start_port_flags.allow_ns_connect,
                ref_port_flags[manifest_compiler.START_PORT_ALLOW_NS_CONNECT])

    def test_validate_start_port_3(self):
        """Test with a zero start_ports"""
        constants = {}
        start_ports_json_data = []

        log = manifest_compiler.Log()
        start_ports_list = manifest_compiler.parse_app_start_ports(
            start_ports_json_data, manifest_compiler.START_PORTS,
            constants, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(len(start_ports_list), 0)

    def test_validate_start_port_4(self):
        """Test with a unknown attribute in start_port entry"""
        constants = {}
        start_ports_json_data = [
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.loadable.start",
                manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                manifest_compiler.START_PORT_FLAGS: {
                    manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                    manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False}
            }]

        log = manifest_compiler.Log()
        manifest_compiler.parse_app_start_ports(
            start_ports_json_data, manifest_compiler.START_PORTS,
            constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_start_port_5(self):
        """Test with a flags missing in start_port entry"""
        constants = {}
        start_ports_json_data = [
            {
                manifest_compiler.START_PORT_NAME:
                    "com.android.trusty.appmgmt.loadable.start",
            }]

        log = manifest_compiler.Log()
        manifest_compiler.parse_app_start_ports(
            start_ports_json_data, manifest_compiler.START_PORTS,
            constants, log)
        self.assertTrue(log.error_occurred())

    def test_validate_app_name_1(self):
        log = manifest_compiler.Log()
        manifest_compiler.parse_app_name("", log)
        self.assertTrue(log.error_occurred())

    def test_validate_app_name_2(self):
        log = manifest_compiler.Log()
        app_name = "test-app-name"
        data = manifest_compiler.parse_app_name(app_name, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, app_name)

    def test_validate_app_name_3(self):
        log = manifest_compiler.Log()
        app_name = "   test-app-name  "
        data = manifest_compiler.parse_app_name(app_name, log)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data, app_name.strip())

    def test_manifest_valid_dict_1(self):
        """Test with valid UUID with hex values and valid values for min_heap
        and min_stack.
        """
        constants = {}
        uuid_in = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        min_heap = 4096
        min_stack = 4096
        id_ = 1
        addr = "0x70000000"
        size = "0x1000"
        default_app_name = "test_app"
        mem_map_data = [{"id": id_, "addr": addr, "size": size}]
        log = manifest_compiler.Log()

        config_data = {
            "uuid": uuid_in,
            "min_heap": min_heap,
            "min_stack": min_stack,
            "mem_map": mem_map_data
        }
        manifest = manifest_compiler.parse_manifest_config(config_data,
                                                           constants,
                                                           default_app_name,
                                                           log)
        self.assertFalse(log.error_occurred())
        self.assertIsNotNone(manifest)
        self.assertEqual(manifest.uuid.hex(), uuid_in.replace("-", ""))
        self.assertEqual(manifest.min_heap, min_heap)
        self.assertEqual(manifest.min_stack, min_stack)
        self.assertEqual(manifest.app_name, default_app_name)
        for memio_map in manifest.mem_io_maps:
            self.assertEqual(memio_map.id, id_)
            self.assertEqual(memio_map.addr, int(addr, 0))
            self.assertEqual(memio_map.size, int(size, 0))
        self.assertFalse(manifest.mgmt_flags.restart_on_exit)
        self.assertFalse(manifest.mgmt_flags.deferred_start)
        self.assertFalse(manifest.mgmt_flags.non_critical_app)

    def test_manifest_valid_dict_2(self):
        """Test with valid UUID, min_heap, min_stack, and min_shadow stack."""
        constants = {}
        uuid_in = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        min_heap = 4096
        min_stack = 4096
        min_shadow_stack = 2 * min_stack
        log = manifest_compiler.Log()

        config_data = {
            "uuid": uuid_in,
            "min_heap": min_heap,
            "min_stack": min_stack,
            "min_shadow_stack": min_shadow_stack
        }
        manifest = manifest_compiler.parse_manifest_config(config_data,
                                                           constants,
                                                           "shadowy_test_app",
                                                           log)
        self.assertFalse(log.error_occurred())
        self.assertIsNotNone(manifest)
        self.assertEqual(manifest.min_stack, min_stack)
        # we mainly care that we got the shadow stack size right
        self.assertEqual(manifest.min_shadow_stack, min_shadow_stack)
        # not to be confused with the regular stack
        self.assertNotEqual(manifest.min_shadow_stack, manifest.min_stack)

    def test_manifest_invalid_dict_2(self):
        """Test with invalid value in config, UUID with integer value and string
        values for min_stack.
        """
        constants = {}
        log = manifest_compiler.Log()
        default_app_name = "test"
        config_data = {"uuid": 123, "app_name": "test", "min_heap": "4096",
                       "min_stack": "8192"}
        manifest = manifest_compiler.parse_manifest_config(config_data,
                                                           constants,
                                                           default_app_name,
                                                           log)
        self.assertEqual(len(config_data), 0)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_manifest_invalid_dict_3(self):
        """ Test with empty config."""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {}
        default_app_name = "test"
        manifest = manifest_compiler.parse_manifest_config(config_data,
                                                           constants,
                                                           default_app_name,
                                                           log)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_manifest_invalid_dict_4(self):
        """Test with unknown entries"""
        constants = {}
        log = manifest_compiler.Log()
        config_data = {"uuid": "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
                       "min_heap": 4096, "min_stack": 4096, "max_heap": 234}
        default_app_name = "test"
        manifest = manifest_compiler.parse_manifest_config(config_data,
                                                           constants,
                                                           default_app_name,
                                                           log)
        self.assertNotEqual(len(config_data), 0)
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_const_config_1(self):
        """Test constant config with port and uuid constants"""
        uuid = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        port = "com.android.trusty.appmgmt.bootstartsrv"
        constants_data = [
            {
                "header": "test_uuid.h",
                "constants": [{"name": TEST_UUID,
                               "value": uuid,
                               "type": "uuid"}]
            },
            {
                "header": "test_port.h",
                "constants": [{"name": TEST_PORT,
                               "value": port,
                               "type": "port"}]
            }
        ]
        log = manifest_compiler.Log()
        config_data = {manifest_compiler.UUID: TEST_UUID,
                       manifest_compiler.START_PORT_NAME: TEST_PORT}
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)
        data_uuid = manifest_compiler.get_string(
            config_data, manifest_compiler.UUID, constants, log)
        data_port = manifest_compiler.get_string(
            config_data, manifest_compiler.START_PORT_NAME, constants, log)
        self.assertEqual(len(config_data), 0)
        self.assertFalse(log.error_occurred())
        self.assertEqual(data_uuid.hex(), uuid.replace("-", ""))
        self.assertEqual(data_port, port)

    def test_const_config_2(self):
        """Test constant config with unknown fields"""
        uuid = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        port = "com.android.trusty.appmgmt.bootstartsrv"
        constants_data = [
            {
                "header": "test_uuid.h",
                "constants": [{"name": TEST_UUID,
                               "value": uuid,
                               "type": "uuid",
                               "length": 16}]
            },
            {
                "header": "test_port.h",
                "constants": [{"name": TEST_PORT,
                               "value": port,
                               "type": "port",
                               "size": 42}]
            }
        ]
        log = manifest_compiler.Log()
        manifest_compiler.extract_config_constants(constants_data, log)
        self.assertTrue(log.error_occurred())

    def test_const_config_3(self):
        """Test constant config no constants"""
        constants_data = [{"header": "test_uuid.h", "constants": []}]
        log = manifest_compiler.Log()
        manifest_compiler.extract_config_constants(constants_data, log)
        self.assertFalse(log.error_occurred())

    def test_const_config_4(self):
        """Test constant config with missing header field"""
        constants_data = [{"constants": []}]
        log = manifest_compiler.Log()
        manifest_compiler.extract_config_constants(constants_data, log)
        self.assertTrue(log.error_occurred())

    def test_const_config_5(self):
        """Test constant config with invalid const type for literal TEST_PORT"""
        uuid = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        port = "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf"
        constants_data = [
            {
                "header": "test_uuid.h",
                "constants": [{"name": TEST_UUID,
                               "value": uuid,
                               "type": "uuid"}]
            },
            {
                "header": "test_port.h",
                "constants": [{"name": TEST_PORT,
                               "value": port,
                               "type": "uuid"}]
            }
        ]
        config_data = {manifest_compiler.START_PORT_NAME: TEST_PORT}
        log = manifest_compiler.Log()
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)
        self.assertFalse(log.error_occurred())
        manifest_compiler.get_string(config_data,
                                     manifest_compiler.START_PORT_NAME,
                                     constants, log)
        self.assertTrue(log.error_occurred())

    def test_const_config_6(self):
        """Test constant config with constant value missing"""
        constants_data = [{
            "header": "test_port.h",
            "constants": [{
                "name": TEST_SIZE,
                "type": "int"}]
        }]

        log = manifest_compiler.Log()
        manifest_compiler.extract_config_constants(constants_data, log)
        self.assertTrue(log.error_occurred())

    def test_const_config_7(self):
        """Test constant config with int constant value"""
        test_value = 4096
        constants_data = [{
            "header": "test_consts.h",
            "constants": [{
                "name": TEST_SIZE,
                "value": test_value,
                "type": "int",
                "unsigned": True}]
        }]
        config_data = {manifest_compiler.MIN_HEAP: TEST_SIZE}

        log = manifest_compiler.Log()
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        self.assertFalse(log.error_occurred())
        constants = manifest_compiler.index_constants(conf_constants)
        self.assertFalse(log.error_occurred())
        data = manifest_compiler.get_int(config_data,
                                         manifest_compiler.MIN_HEAP,
                                         constants, log)
        self.assertEqual(data, test_value)
        self.assertFalse(log.error_occurred())

    def test_const_config_8(self):
        """ Test constant config with hexadecimal int constant value"""
        test_value = "0x1000"
        constants_data = [{
            "header": "test_consts.h",
            "constants": [{
                "name": TEST_SIZE,
                "value": test_value,
                "type": "int",
                "unsigned": True}]
        }]
        config_data = {manifest_compiler.MIN_HEAP: TEST_SIZE}

        log = manifest_compiler.Log()
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        self.assertFalse(log.error_occurred())
        constants = manifest_compiler.index_constants(conf_constants)
        self.assertFalse(log.error_occurred())
        data = manifest_compiler.get_int(config_data,
                                         manifest_compiler.MIN_HEAP,
                                         constants, log)
        self.assertEqual(data, int(test_value, 0))
        self.assertFalse(log.error_occurred())

    def test_const_config_9(self):
        """Test constant config with unsigned field missing in int type constant
        """
        test_value = "0x1000"
        constants_data = [{
            "header": "test_consts.h",
            "constants": [{
                "name": TEST_SIZE,
                "value": test_value,
                "type": "int"}]
        }]

        log = manifest_compiler.Log()
        manifest_compiler.extract_config_constants(constants_data, log)
        self.assertTrue(log.error_occurred())

    def test_const_config_10(self):
        """Test constant config with boolean field"""
        test_bool = "SAMPLE_BOOL"
        test_value = True
        constants_data = [{
            "header": "test_consts.h",
            "constants": [{
                "name": test_bool,
                "value": test_value,
                "type": "bool"}]
        }]
        config_data = {manifest_compiler.START_PORT_ALLOW_TA_CONNECT: test_bool}

        log = manifest_compiler.Log()
        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        self.assertFalse(log.error_occurred())
        constants = manifest_compiler.index_constants(conf_constants)
        self.assertFalse(log.error_occurred())
        data = manifest_compiler.get_boolean(
            config_data, manifest_compiler.START_PORT_ALLOW_TA_CONNECT,
            constants, log)
        self.assertEqual(data, test_value)
        self.assertFalse(log.error_occurred())

    def test_manifest_valid_pack_1(self):
        """Test with valid UUID with hex values and
        valid values for min_heap and min_stack.
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        # PLZ DON'T EDIT VALUES
        constants = {}
        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        config_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_ref_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_2(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - memory mapping entries
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        ref_config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MEM_MAP: [
                {"id": 1, "addr": "0x70000000", "size": "0x1000",
                 "type": "cached", "non_secure": False},
                {"id": 2, "addr": "0x70010000", "size": "0x100",
                 "type": "uncached", "non_secure": True},
                {"id": 3, "addr": "0x70020000", "size": "0x4",
                 "type": "uncached_device", "non_secure": False}],
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MEM_MAP: [
                {"id": 1, "addr": "0x70000000", "size": "0x1000",
                 "type": "cached", "non_secure": False},
                {"id": 2, "addr": "0x70010000", "size": "0x100",
                 "type": "uncached", "non_secure": True},
                {"id": 3, "addr": "0x70020000", "size": "0x4"}]
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(ref_config_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_3(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap, min_stack, and min_shadow_stack
        - Management flags
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: True,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_4(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - start_ports
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # Reference manifest data structure
        ref_config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.START_PORTS: [
                {
                    manifest_compiler.START_PORT_NAME:
                        "com.android.trusty.appmgmt.loadable.start",
                    manifest_compiler.START_PORT_FLAGS: {
                        manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                        manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False
                    }
                }
            ],
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.START_PORTS: [
                {
                    manifest_compiler.START_PORT_NAME:
                        "com.android.trusty.appmgmt.loadable.start",
                    manifest_compiler.START_PORT_FLAGS: {
                        manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                        manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False
                    }
                }
            ]
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(ref_config_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_5(self):
        """Test with valid manifest config with multiple constants configs
        - UUID
        - start_ports
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants_data = [
            {
                "header": "port_constants.h",
                "constants": [{
                    "name": "LOADABLE_START_PORT",
                    "value": "com.android.trusty.appmgmt.loadable.start",
                    "type": "port"
                }]
            },
            {
                "header": "storage_constants.h",
                "constants": [{
                    "name": "STORAGE_UUID",
                    "value": "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
                    "type": "uuid"
                }]
            }
        ]

        log = manifest_compiler.Log()

        # Reference manifest data structure
        ref_config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.START_PORTS: [
                {
                    manifest_compiler.START_PORT_NAME:
                        "com.android.trusty.appmgmt.loadable.start",
                    manifest_compiler.START_PORT_FLAGS: {
                        manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                        manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False
                    }
                }
            ],
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "STORAGE_UUID",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.START_PORTS: [
                {
                    manifest_compiler.START_PORT_NAME:
                        "LOADABLE_START_PORT",
                    manifest_compiler.START_PORT_FLAGS: {
                        manifest_compiler.START_PORT_ALLOW_TA_CONNECT: True,
                        manifest_compiler.START_PORT_ALLOW_NS_CONNECT: False
                    }
                }
            ]
        }

        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(ref_config_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_6(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - pinned_cpu
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        # PLZ DON'T EDIT VALUES
        constants = {}
        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        config_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            },
            manifest_compiler.PINNED_CPU: 3
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.PINNED_CPU: 3
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_ref_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_7(self):
        """Test with valid manifest config with a constants config containing
        - UUID
        - min_heap and min_stack
        - pinned_cpu
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants_data = [{
            "header": "cpu_constants.h",
            "constants": [{
                "name": "CPU_NUM",
                "value": 3,
                "type": "int",
                "unsigned": True
            }]
        }]

        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        config_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            },
            manifest_compiler.PINNED_CPU: 3
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.PINNED_CPU: "CPU_NUM"
        }

        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_ref_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_8(self):
        """Test with valid manifest config with a constants config containing
        - UUID
        - min_heap, min_stack, and min_shadow_stack
        - version
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants_data = [{
            "header": "constants.h",
            "constants": [{
                "name": "VERSION",
                "value": 1,
                "type": "int",
                "unsigned": True
            },
                {
                    "name": "PAGE_SIZE",
                    "value": 4096,
                    "type": "int",
                    "unsigned": True
                }]
        }]

        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        config_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 1,
            manifest_compiler.MIN_SHADOW_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: "VERSION",
            manifest_compiler.MIN_SHADOW_STACK: "PAGE_SIZE",
        }

        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_ref_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_valid_pack_9(self):
        """Test with valid manifest config with a constants config containing
        - UUID
        - min_heap, min_stack
        - priority
        - apploader_flags
        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants_data = [{
            "header": "constants.h",
            "constants": [{
                "name": "REQUIRES_ENCRYPTION",
                "value": True,
                "type": "bool",
            }]
        }]

        log = manifest_compiler.Log()

        # Reference JSON manifest data structure
        config_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.PRIORITY: 10,
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: True,
            }
        }

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.PRIORITY: 10,
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION:
                    "REQUIRES_ENCRYPTION",
            }
        }

        conf_constants = manifest_compiler.extract_config_constants(
            constants_data, log)
        constants = manifest_compiler.index_constants(conf_constants)

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_ref_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_empty_priority(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - pinned_cpu
        - priority empty

        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.PINNED_CPU: 3,
            manifest_compiler.PRIORITY: None,
        }

        default_app_name = "test"

        manifest = manifest_compiler.parse_manifest_config(
            config_data,
            constants,
            default_app_name,
            log
        )
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_manifest_good_version(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - version
        - min_version
        - flags

        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 5,
            manifest_compiler.MIN_VERSION: 2,
            manifest_compiler.MGMT_FLAGS: {
                manifest_compiler.MGMT_FLAG_RESTART_ON_EXIT: False,
                manifest_compiler.MGMT_FLAG_DEFERRED_START: False,
                manifest_compiler.MGMT_FLAG_NON_CRITICAL_APP: False
            },
            manifest_compiler.APPLOADER_FLAGS: {
                manifest_compiler.APPLOADER_FLAGS_REQUIRES_ENCRYPTION: False,
            }
        }

        # Pack manifest config_data
        # Unpack the binary packed data to JSON text
        # Validate unpacked JSON text
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(config_data),
            manifest_compiler.unpack_binary_manifest_to_json(
                pack_manifest_config_data(
                    self, config_data, log, constants)))

    def test_manifest_bad_version_1(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - pinned_cpu
        - version empty
        - min_version

        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: None,
            manifest_compiler.MIN_VERSION: 1,
        }

        default_app_name = "test"

        manifest = manifest_compiler.parse_manifest_config(
            config_data,
            constants,
            default_app_name,
            log
        )
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_manifest_bad_version_2(self):
        """Test with valid manifest config containing
        - UUID
        - min_heap and min_stack
        - pinned_cpu
        - version empty
        - min_version

        Pack the manifest config data and unpack it and
        verify it with the expected values
        """
        constants = {}
        log = manifest_compiler.Log()

        # JSON manifest data structure
        config_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 1,
            manifest_compiler.MIN_VERSION: 2,
        }

        default_app_name = "test"

        manifest = manifest_compiler.parse_manifest_config(
            config_data,
            constants,
            default_app_name,
            log
        )
        self.assertTrue(log.error_occurred())
        self.assertIsNone(manifest)

    def test_manifest_overlay_1(self):
        """Test with two manifest configs (main + overlay)
        Main manifest contains the following entries
        - UUID, app_name
        - min_heap, min_stack
        - version
        - mem_maps with 2 entries
        Overlay manifest contains overlay min_heap
        and two more mem_maps entries, one of which is
        skipped as duplicate, another one is added to
        mem_maps from main manifest

        Check correctness of merging procedure
        """
        log = manifest_compiler.Log()

        # JSON manifest data structure
        main_manifest_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 1,
            manifest_compiler.MEM_MAP: [
                {"id": 1, "addr": "0x70000000", "size": "0x1000",
                 "type": "cached", "non_secure": False},
                {"id": 2, "addr": "0x70010000", "size": "0x100",
                 "type": "uncached", "non_secure": True}]
        }
        overlay_manifest_data = {
            manifest_compiler.MIN_HEAP: 1000,
            manifest_compiler.MEM_MAP: [
                {"id": 2, "addr": "0x70010000", "size": "0x100",
                 "type": "uncached", "non_secure": True},
                {"id": 3, "addr": "0x70020000", "size": "0x4"}]
        }

        # Reference JSON manifest data structure
        manifest_ref_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 1000, # from overlay
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 1,
            manifest_compiler.MEM_MAP: [
                {"id": 1, "addr": "0x70000000", "size": "0x1000", # from main
                 "type": "cached", "non_secure": False},
                {"id": 2, "addr": "0x70010000", "size": "0x100", # from main
                 "type": "uncached", "non_secure": True},
                {"id": 3, "addr": "0x70020000", "size": "0x4"}] # from overlay
        }

        merged_manifest_data = manifest_compiler.merge_manifest_dicts(
            [main_manifest_data, overlay_manifest_data], log)

        self.assertFalse(log.error_occurred())
        self.assertEqual(
            manifest_compiler.manifest_data_to_json(manifest_ref_data),
            manifest_compiler.manifest_data_to_json(merged_manifest_data))

    def test_manifest_overlay_2(self):
        """Test with two conflicting manifest configs (main + overlay)
        Manifest merge should fail because main and overlay
        manifests have different data types to merge.
        """
        log = manifest_compiler.Log()

        # JSON manifest data structure
        main_manifest_data = {
            manifest_compiler.UUID: "5f902ace-5e5c-4cd8-ae54-87b88c22ddaf",
            manifest_compiler.APP_NAME: "test-app-name",
            manifest_compiler.MIN_HEAP: 8192,
            manifest_compiler.MIN_STACK: 4096,
            manifest_compiler.VERSION: 1,
            manifest_compiler.MEM_MAP: [
                {"id": 1, "addr": "0x70000000", "size": "0x1000",
                 "type": "cached", "non_secure": False},
                {"id": 2, "addr": "0x70010000", "size": "0x100",
                 "type": "uncached", "non_secure": True}]
        }
        overlay_manifest_data = {
            manifest_compiler.MIN_HEAP: 1000,
            manifest_compiler.MEM_MAP: 25 # conflicting types, scalar vs list
        }

        merged_manifest_data = manifest_compiler.merge_manifest_dicts(
            [main_manifest_data, overlay_manifest_data], log)

        self.assertTrue(log.error_occurred())
        self.assertIsNone(merged_manifest_data)

def pack_manifest_config_data(self, config_data, log, constants):
    # parse manifest JSON data
    default_app_name = "test"
    manifest = manifest_compiler.parse_manifest_config(config_data, constants,
                                                       default_app_name, log)
    self.assertFalse(log.error_occurred())

    # pack manifest config data
    packed_data = manifest_compiler.pack_manifest_data(manifest)
    self.assertEqual(len(config_data), 0)
    self.assertFalse(log.error_occurred())
    self.assertIsNotNone(packed_data)

    return packed_data


if __name__ == "__main__":
    unittest.main()
