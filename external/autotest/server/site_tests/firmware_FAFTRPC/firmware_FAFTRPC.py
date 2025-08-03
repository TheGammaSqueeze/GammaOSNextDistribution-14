# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import operator
import re
import six
import sys

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chip_utils
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


NO_ARGS = tuple()
ONE_INT_ARG = (1, )
ONE_STR_ARG = ("foo", )
SAMPLE_FILE = "/tmp/foo"
CHIP_FW_NAMES = (chip.fw_name for chip in chip_utils.chip_id_map.values())
SAMPLE_CGPT_A = {
    "UUID": "93EF7B23-606B-014B-A10C-E9D7CF53DFD3",
    "successful": 1,
    "partition": 2,
    "priority": 1,
    "tries": 0,
    "Type": "ChromeOS kernel",
}
SAMPLE_CGPT_B = {
    "UUID": "C6604D6B-5563-EE4E-9915-0C50530B158A",
    "successful": 0,
    "partition": 4,
    "priority": 0,
    "tries": 15,
    "Type": "ChromeOS kernel",
}

# RPC_CATEGORIES contains the test cases for all RPCs.
# For readability, the real definition is at the bottom of this file.
RPC_CATEGORIES = [{}]


def get_rpc_category_by_name(name):
    """Find a category from RPC_CATEGORIES by its category_name."""
    for rpc_category in RPC_CATEGORIES:
        if rpc_category["category_name"] == name:
            return rpc_category
    raise ValueError("No RPC category defined with category_name=%s" % name)


def get_rpc_method_names_from_test_case(test_case):
    """
    Extract the method_name or method_names from a test case configuration.

    @param test_case: An element from a test_cases array,
                      like those in RPC_CATEGORIES

    @return: A list of names of RPC methods in that test case.

    """
    if (("method_name" in test_case) ^ ("method_names" in test_case)):
        if "method_name" in test_case:
            return [test_case["method_name"]]
        elif "method_names" in test_case:
            return test_case["method_names"]
        else:
            err_msg = "Something strange happened while parsing RPC methods"
            raise ValueError(err_msg)
    else:
        err_msg = "test_case must contain EITHER method_name OR method_names"
        raise ValueError(err_msg)



class firmware_FAFTRPC(FirmwareTest):
    """
    This test checks that all RPC commands work as intended.

    For now, we only need to verify that the RPC framework is intact,
    so we only verify that all RPCs can be called with the
    expected arguments.

    It would be good to expand this test to verify that all RPCs
    yields the expected results.

    """
    version = 1
    _stored_values = {}


    def initialize(self, host, cmdline_args, dev_mode=False):
        """Runs before test begins."""
        super(firmware_FAFTRPC, self).initialize(host, cmdline_args)
        self.backup_firmware()
        self.faft_client.rpc_settings.enable_test_mode()


    def cleanup(self):
        """Runs after test completion."""
        self.faft_client.rpc_settings.disable_test_mode()
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
            if self.reboot_after_completion:
                logging.info("Rebooting DUT, as specified in control file")
                self.switcher.mode_aware_reboot()
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_FAFTRPC, self).cleanup()


    def _log_success(self, rpc_name, params, success_message):
        """Report on an info level that a test passed."""
        logging.info("RPC test for %s%s successfully %s",
                     rpc_name, params, success_message)


    def _fail(self, rpc_name, params, error_msg):
        """Raise a TestFail error explaining why a test failed."""
        raise error.TestFail("RPC function %s%s had an unexpected result: %s"
                             % (rpc_name, params, error_msg))


    def _retrieve_stored_values(self, params):
        """
        Replace any operator.itemgetter params with corresponding stored values.

        @param params: A tuple of args that might be passed into an RPC method,
                       some of which might be operator.itemgetter objects.

        @return: A tuple of pargs to be passed into an RPC method,
                 with stored values swapped in for operator.itemgetters.

        """
        new_params = []
        for old_param in params:
            if isinstance(old_param, operator.itemgetter):
                retrieved_value = old_param(self._stored_values)
                new_params.append(retrieved_value)
            else:
                new_params.append(old_param)
        new_params = tuple(new_params)
        return new_params


    def _assert_passes(self, category, method, params, allow_error_msg=None,
                       expected_return_type=None, silence_result=False):
        """
        Check whether an RPC function with given input passes,
        and fail if it does not.

        If an expected_return_type is passed in, then require the RPC function
        to return a value matching that type, or else fail.

        @param category: The RPC subsystem category; ex. kernel, bios
        @param method: The name of the RPC function within the subsystem
        @param params: A tuple containing params to pass into the RPC function
        @param allow_error_msg: If a regexy string is passed in, and the RPC
                                returns an RPC error matching this regex,
                                then the test will pass instead of failing.
        @param expected_return_type: If not None, then the RPC return value
                                     must be this type, else the test fails.
        @param silence_result: If True, then the RPC return value will not be
                               logged.

        @raise error.TestFail: If the RPC raises any error (unless handled by
                               allow_error_msg).
        @raise error.TestFail: If expected_return_type is not None, and the RPC
                               return value is not expected_return_type.

        @return: Not meaningful.

        """
        rpc_function = self.get_rpc_function(category, method)
        if category:
            rpc_name = '%s.%s' % (category, method)
        else:
            rpc_name = method
        try:
            result = rpc_function(*params)
        except six.moves.xmlrpc_client.Fault as e:
            if allow_error_msg is not None and \
                    re.search(allow_error_msg, str(e)):
                success_msg = "raised an acceptable error during RPC handling"
                self._log_success(rpc_name, params, success_msg)
                return e
            error_msg = "Unexpected RPC error: %s" % e
            self._fail(rpc_name, params, error_msg)
        except:
            error_msg = "Unexpected misc error: %s" % sys.exc_info()[0]
            self._fail(rpc_name, params, error_msg)
        else:
            if expected_return_type is None:
                if silence_result:
                    success_msg = "passed with a silenced result"
                else:
                    success_msg = "passed with result %s" % result
                self._log_success(rpc_name, params, success_msg)
                return result
            elif isinstance(result, expected_return_type):
                if silence_result:
                    success_msg = "passed with a silenced result of " \
                            "expected type %s" % type(result)
                else:
                    success_msg = "passed with result %s of expected type %s" \
                            % (result, type(result))
                self._log_success(rpc_name, params, success_msg)
                return result
            else:
                error_msg = ("Expected a result of type %s, but got %s " +
                                "of type %s)") \
                            % (expected_return_type, result, type(result))
                self._fail(rpc_name, params, error_msg)


    def _assert_fails(self, category, method, params):
        """
        Check whether an RPC function with given input throws an RPC error,
        and fail if it does not.

        @param category: The RPC subsystem category; ex. kernel, bios
        @param method: The name of the RPC function within the subsystem
        @param params: A tuple containing params to pass into the RPC function

        @raise error.TestFail: If the RPC raises no error, or if it raises any
                               error other than six.moves.xmlrpc_client.Fault

        @return: Not meaningful.

        """
        rpc_function = self.get_rpc_function(category, method)
        if category:
            rpc_name = '%s.%s' % (category, method)
        else:
            rpc_name = method
        try:
            result = rpc_function(*params)
        except six.moves.xmlrpc_client.Fault as e:
            self._log_success(rpc_name, params, "raised RPC error")
        except:
            error_msg = "Unexpected misc error: %s" % sys.exc_info()[0]
            self._fail(rpc_name, params, error_msg)
        else:
            error_msg = "Should have raised an RPC error, but did not"
            self._fail(rpc_name, params, error_msg)


    def _assert_output(self, category, method, params, expected_output,
                       allow_error_msg=None):
        """
        Check whether an RPC function with given input
        returns a particular value, and fail if it does not.

        @param category: The RPC subsystem category; ex. kernel, bios
        @param method: The name of the RPC function within the subsystem
        @param params: A tuple containing params to pass into the RPC function
        @param expected_output: The value that the RPC function should return
        @param allow_error_msg: If a regexy string is passed in, and the RPC
                                returns an RPC error containing this string,
                                then the test will pass instead of failing.

        @raise error.TestFail: If self._assert_passes(...) fails, or if the
                               RPC return value does not match expected_output

        @return: Not meaningful.

        """
        rpc_name = ".".join([category, method])
        actual_output = self._assert_passes(category, method, params,
                                            allow_error_msg=allow_error_msg)
        if expected_output == actual_output:
            success_message = "returned the expected value <%s>" \
                              % expected_output
            self._log_success(rpc_name, params, success_message)
        else:
            error_msg = "Expected output <%s>, but actually returned <%s>" \
                        % (expected_output, actual_output)
            self._fail(rpc_name, params, error_msg)


    def get_rpc_function(self, category, method):
        """
        Find a callable RPC function given its name.

        @param category: The name of an RPC subsystem category; ex. kernel, ec
        @param method: The name of an RPC function within the subsystem

        @return: A callable method of the RPC proxy
        """
        if category:
            rpc_function_handler = getattr(self.faft_client, category)
        else:
            rpc_function_handler = self.faft_client
        rpc_function = getattr(rpc_function_handler, method)
        return rpc_function


    def run_once(self, category_under_test="*", reboot_after_completion=False):
        """
        Main test logic.

        For all RPC categories being tested,
        iterate through all test cases defined in RPC_CATEGORIES.

        @param category_under_test: The name of an RPC category to be tested,
                                    such as ec, bios, or kernel.
                                    Default is '*', which tests all categories.

        """
        if category_under_test == "*":
            logging.info("Testing all %d RPC categories", len(RPC_CATEGORIES))
            rpc_categories_to_test = RPC_CATEGORIES
        else:
            rpc_categories_to_test = [
                    get_rpc_category_by_name(category_under_test)]
            logging.info("Testing RPC category '%s'", category_under_test)
        self.reboot_after_completion = reboot_after_completion
        for rpc_category in rpc_categories_to_test:
            category_name = rpc_category["category_name"]
            if category_name == "ec" and not self.check_ec_capability(
                    suppress_warning=True):
                logging.info("No EC found on DUT. Skipping EC category.")
                continue

            # Re-enable test mode, in case another category's tests disabled it.
            self.faft_client.rpc_settings.enable_test_mode()

            test_cases = rpc_category["test_cases"]
            logging.info("Testing %d cases for RPC category %s",
                         len(test_cases), repr(category_name))
            for test_case in test_cases:
                method_names = get_rpc_method_names_from_test_case(test_case)
                passing_args = test_case.get("passing_args", [])
                ec_passing_args = test_case.get("ec_passing_args", [])
                failing_args = test_case.get("failing_args", [])
                allow_error_msg = test_case.get("allow_error_msg", None)
                expected_return_type = test_case.get("expected_return_type",
                                                     None)
                store_result_as = test_case.get("store_result_as", None)
                silence_result = test_case.get("silence_result", False)
                for method_name in method_names:
                    for passing_arg_tuple in passing_args:
                        passing_arg_tuple = self._retrieve_stored_values(
                                passing_arg_tuple)
                        result = self._assert_passes(category_name, method_name,
                                                     passing_arg_tuple,
                                                     allow_error_msg,
                                                     expected_return_type,
                                                     silence_result)
                        if store_result_as is not None:
                            self._stored_values[store_result_as] = result
                    for failing_arg_tuple in failing_args:
                        failing_arg_tuple = self._retrieve_stored_values(
                                failing_arg_tuple)
                        self._assert_fails(category_name, method_name,
                                           failing_arg_tuple)
                    for arg_tuple in ec_passing_args:
                        arg_tuple = self._retrieve_stored_values(arg_tuple)
                        if self.check_ec_capability(suppress_warning=True):
                            result = self._assert_passes(
                                    category_name, method_name, arg_tuple,
                                    allow_error_msg, expected_return_type,
                                    silence_result)
                            if store_result_as is not None:
                                self._stored_values[store_result_as] = result
                        else:
                            self._assert_fails(category_name, method_name,
                                               arg_tuple)


"""
RPC_CATEGORIES contains all the test cases for our RPC tests.
Each element of RPC_CATEGORIES must be a dict containing the following keys:

@key category_name: A string naming the RPC category, such as bios or kernel.
@key test_cases: A list of test cases, each of which must be a dict containing
                 the following keys:
    @key method_name (optional): A string naming an RPC method within
                                 this category. Either this key or method_names
                                 is required (but not both).
    @key method_names (optional): An array of strings naming RPC methods within
                                  this category. Either this key or method_name
                                  is required (but not both).
    @key passing_args: A list of tuples, each of which could be unpacked and
                       then passed into the RPC method as a valid set of
                       parameters. Each tuple might contain instances of
                       operator.itemgetter. If so, those instances will be
                       replaced with values from firmware_FAFTRPC._stored_values
                       before being passed into the RPC method.
    @key failing_args: A list of tuples, each of which could be unpacked and
                       then passed into the RPC method as a set of parameters
                       which should yield an RPC error. Each tuple might contain
                       instances of operator.itemgetter. If so, those instances
                       will be replaced with values from
                       firmware_FAFTRPC._stored_values before being passed into
                       the RPC method.
    @key silence_result: Normally, the RPC return value is logged. However, if
                         this key is truthy, then the result is not logged.
    @key allow_error_msg (optional): String representing a regex pattern.
                                     If the RPC method is called with a
                                     passing_args tuple, but it yields an RPC
                                     error whose message is matched by
                                     re.search(allow_error_msg, error_msg),
                                     then the test will be considered a pass.
    @key store_result_as (optional): String. If this field is specified, then
                                     the result from the RPC call will be stored
                                     in firmware_FAFTRPC._stored_values. This
                                     allows us to later reference the result
                                     via an operator.itemgetter, as described
                                     above in the docstrings for passing_args
                                     and failing_args.

"""
RPC_CATEGORIES = [
        {
                "category_name":
                "system",
                "test_cases": [
                        {
                                "method_names": [
                                        "is_available",
                                        "get_platform_name",
                                        "get_model_name",
                                        "dev_tpm_present",
                                        "get_boot_mode",
                                        "get_root_dev",
                                        "get_root_part",
                                        "get_minios_priority",
                                        "get_fw_vboot2",
                                        "request_recovery_boot",
                                        "is_removable_device_boot",
                                        "get_internal_device",
                                ],
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                        },
                        {
                                "method_name":
                                "run_shell_command",
                                "passing_args": [("ls -l", ), ("ls -l", False),
                                                 ("ls -l", True)],
                                "failing_args": [
                                        NO_ARGS,
                                        ("ls", "-l", 'foo'),
                                ],
                        },
                        {
                                "method_name": "run_shell_command_get_status",
                                "passing_args": [
                                        ("ls", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("ls", "-l", 'foo'),
                                ],
                        },
                        {
                                "method_name": "run_shell_command_get_status",
                                "passing_args": [
                                        ("ls ''", ),
                                ],
                        },
                        {
                                "method_name": "run_shell_command",
                                "failing_args": [
                                        ("ls ''", ),
                                ],
                        },
                        {
                                "method_name":
                                "run_shell_command_check_output",
                                "passing_args": [
                                        ("ls -l", "total"),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                ],
                        },
                        {
                                "method_name": "run_shell_command_get_output",
                                "passing_args": [
                                        ("ls -l", True),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                ],
                        },
                        {
                                "method_name": "get_crossystem_value",
                                "passing_args": [
                                        ("fwid", ),
                                ],
                                "failing_args": [NO_ARGS],
                        },
                        {
                                "method_name": "set_try_fw_b",
                                "passing_args": [
                                        NO_ARGS,
                                        (1, ),
                                ],
                                "failing_args": [
                                        (1, 1),
                                ],
                        },
                        {
                                "method_name": "set_fw_try_next",
                                "passing_args": [
                                        ("A", ),
                                        ("A", 1),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("A", 1, "B"),
                                ],
                        },
                        {
                                "method_name": "set_minios_priority",
                                "passing_args": [
                                        ("A"),
                                        ("B"),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("A", 1),
                                ],
                        },
                        {
                                "method_name": "get_dev_boot_usb",
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "store_result_as": "dev_boot_usb",
                        },
                        {
                                "method_name":
                                "set_dev_boot_usb",
                                "passing_args": [
                                        (operator.itemgetter("dev_boot_usb"),
                                         ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        (True, False),
                                ],
                        },
                        {
                                "method_name": "create_temp_dir",
                                "passing_args": [
                                        NO_ARGS,
                                        ONE_STR_ARG,
                                ],
                                "failing_args": [
                                        ONE_INT_ARG,
                                        ("foo", "bar"),
                                ],
                                "expected_return_type": str,
                                "store_result_as": "temp_dir",
                        },
                        {
                                "method_name": "remove_file",
                                "passing_args": [
                                        (SAMPLE_FILE, ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        (1, 2),
                                ],
                        },
                        {
                                "method_name": "remove_dir",
                                "passing_args": [
                                        (operator.itemgetter("temp_dir"), ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        (1, 2),
                                ]
                        },
                        {
                                "method_name": "check_keys",
                                "passing_args": [
                                        ([], ),
                                        ([116], ),
                                        ([28, 29, 32], ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ([], [116]),
                                ],
                                "expected_return_type": int,
                        },
                ]
        },
        {
                "category_name":
                "bios",
                "test_cases": [
                        {
                                "method_names": [
                                        "reload",
                                ],
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG]
                        },
                        {
                                "method_name": "get_gbb_flags",
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "expected_return_type": int,
                                "store_result_as": "gbb_flags",
                        },
                        {
                                "method_name": "set_gbb_flags",
                                "passing_args": [
                                        (operator.itemgetter("gbb_flags"), ),
                                ],
                                "failing_args": [NO_ARGS],
                        },
                        {
                                "method_name": "get_preamble_flags",
                                "passing_args": [
                                        ("a", ),
                                ],
                                "failing_args": [NO_ARGS, ONE_INT_ARG],
                                "store_result_as": "preamble_flags",
                        },
                        {
                                "method_name":
                                "set_preamble_flags",
                                "passing_args": [
                                        (
                                                "a",
                                                operator.itemgetter(
                                                        "preamble_flags"),
                                        ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                        ONE_STR_ARG,
                                        (
                                                "c",
                                                operator.itemgetter(
                                                        "preamble_flags"),
                                        ),
                                ],
                        },
                        {
                                "method_names": [
                                        "get_body_sha",
                                        "get_sig_sha",
                                        "get_section_fwid",
                                        "get_version",
                                        "get_datakey_version",
                                        "get_kernel_subkey_version",
                                ],
                                "passing_args": [
                                        ("a", ),
                                        ("b", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                        (("a", "b"), ),
                                        ("c", ),
                                ]
                        },
                        {
                                "method_name": "set_version",
                                "passing_args": [
                                        ("a", 0),
                                        ("b", 1),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("a", ),
                                        ("b", -1),
                                ],
                        },
                        {
                                "method_names": [
                                        "get_sig_one_byte",
                                        "get_body_one_byte",
                                ],
                                "passing_args": [
                                        ("a", ),
                                        ("b", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                        ("c", ),
                                ]
                        },
                        {
                                "method_names": [
                                        "modify_sig",
                                        "modify_body",
                                ],
                                "passing_args": [
                                        ("a", 0, 0xff),
                                        ("b", 1, 0xff),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                ],
                        },
                        {
                                "method_names": [
                                        "dump_whole",
                                        "write_whole",
                                ],
                                "passing_args": [
                                        (SAMPLE_FILE, ),
                                ],
                                "failing_args": [NO_ARGS],
                        },
                        {
                                "method_name": "strip_modified_fwids",
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "expected_return_type": dict
                        },
                        {
                                "method_name":
                                "set_write_protect_region",
                                "passing_args": [("WP_RO", ), ("WP_RO", None),
                                                 ("WP_RO", True),
                                                 ("WP_RO", False)],
                                "failing_args":
                                [NO_ARGS, (None, ), ("WP_RO", None, "EXTRA")],
                        },
                        {
                                "method_name": "get_write_protect_status",
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "expected_return_type": dict
                        },
                        {
                                "method_name":
                                "get_write_cmd",
                                "passing_args": [
                                        NO_ARGS,
                                        (""),
                                        ("bios.bin", ),
                                ],
                                "failing_args": [("bios.bin", []),
                                                 ("bios.bin", 1),
                                                 ("bios.bin", [], 'extra')],
                                "expected_return_type":
                                str
                        },
                ],
        },
        {
                "category_name":
                "ec",
                "test_cases": [
                        {
                                "method_names": [
                                        "reload",
                                        "get_active_hash",
                                        "is_efs",
                                ],
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "allow_error_msg":
                                "list index out of range",
                        },
                        {
                                "method_name":
                                "get_version",
                                "passing_args": [
                                        NO_ARGS,
                                        ("ro", ),
                                        ("RW", ),
                                        (None, ),
                                ],
                        },
                        {
                                "method_names":
                                ["dump_whole", "write_whole", "dump_firmware"],
                                "passing_args": [
                                        (SAMPLE_FILE, ),
                                ],
                                "failing_args": [NO_ARGS],
                        },
                        {
                                "method_name":
                                "corrupt_body",
                                "passing_args": [
                                        ("rw", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                        ("ro", ),
                                        ("rw", "rw"),
                                ],
                        },
                        {
                                "method_name": "set_write_protect",
                                "passing_args": [
                                        (True, ),
                                        (False, ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        (True, False),
                                ]
                        },
                        {
                                "method_name":
                                "copy_rw",
                                "passing_args": [
                                        ("rw", "rw"),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("rw", "ro"),
                                        ("ro", "rw"),
                                        ("rw", ),
                                ],
                        },
                        {
                                "method_name": "reboot_to_switch_slot",
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                                "allow_error_msg": "CmdError",
                        },
                        {
                                "method_name":
                                "get_write_cmd",
                                "passing_args": [
                                        NO_ARGS,
                                        (""),
                                        ("ec.bin", ),
                                ],
                                "failing_args": [("ec.bin", []), ("ec.bin", 1),
                                                 ("ec.bin", [], 'extra')],
                                "expected_return_type":
                                str
                        },
                ],
        },
        {
                "category_name":
                "kernel",
                "test_cases": [{
                        "method_names": [
                                "corrupt_sig",
                                "restore_sig",
                                "move_version_backward",
                                "move_version_forward",
                        ],
                        "passing_args": [
                                ("a", ),
                                ("b", ),
                        ],
                        "failing_args": [
                                NO_ARGS,
                                ONE_INT_ARG,
                                ("c", ),
                                ("a", "b"),
                        ],
                }, {
                        "method_names": [
                                "get_version",
                                "get_datakey_version",
                                "get_sha",
                        ],
                        "passing_args": [
                                ("a", ),
                                ("b", ),
                        ],
                        "failing_args": [
                                (("a", "b"), ),
                                ("c", ),
                                NO_ARGS,
                                ONE_INT_ARG,
                        ],
                }, {
                        "method_name": "diff_a_b",
                        "passing_args": [NO_ARGS],
                        "failing_args": [
                                ONE_INT_ARG,
                                ONE_STR_ARG,
                        ],
                        "expected_return_type": bool,
                }, {
                        "method_name":
                        "resign_with_keys",
                        "passing_args": [
                                ("a", ),
                                ("b", ),
                                ("b", SAMPLE_FILE),
                        ],
                        "failing_args": [
                                (("a", "b"), ),
                                ("c", ),
                                NO_ARGS,
                                ONE_INT_ARG,
                        ],
                }, {
                        "method_names": [
                                "dump",
                                "write",
                        ],
                        "passing_args": [
                                ("a", SAMPLE_FILE),
                                ("b", SAMPLE_FILE),
                        ],
                        "failing_args": [
                                (("a", "b"), SAMPLE_FILE),
                                ("c", SAMPLE_FILE),
                                ("a", ),
                                NO_ARGS,
                        ]
                }],
        },
        {
                "category_name":
                "tpm",
                "test_cases": [
                        {
                                "method_names": [
                                        "get_firmware_version",
                                        "get_firmware_datakey_version",
                                        "get_kernel_version",
                                        "get_kernel_datakey_version",
                                        "get_tpm_version",
                                        "stop_daemon",
                                        "restart_daemon",
                                ],
                                "passing_args": [NO_ARGS],
                                "failing_args": [ONE_INT_ARG, ONE_STR_ARG],
                        },
                ]
        },
        {
                "category_name":
                "cgpt",
                "test_cases": [{
                        "method_name": "get_attributes",
                        "passing_args": [NO_ARGS],
                        "failing_args": [
                                ONE_INT_ARG,
                                ONE_STR_ARG,
                        ],
                }, {
                        "method_name":
                        "set_attributes",
                        "passing_args": [
                                NO_ARGS,
                                (SAMPLE_CGPT_A, ),
                                (None, SAMPLE_CGPT_B),
                                (SAMPLE_CGPT_A, SAMPLE_CGPT_B),
                                (None, None),
                        ],
                        "failing_args": [
                                (None, None, None),
                        ],
                }]
        },
        {
                "category_name":
                "updater",
                "test_cases": [
                        {
                                "method_names": [
                                        "cleanup",
                                        "stop_daemon",
                                        "start_daemon",
                                        # "modify_ecid_and_flash_to_bios",
                                        "get_ec_hash",
                                        "reset_shellball",
                                        # "run_factory_install",
                                        # "run_recovery",
                                        "cbfs_setup_work_dir",
                                        # "cbfs_sign_and_flash",
                                        "get_temp_path",
                                        "get_keys_path",
                                        "get_work_path",
                                        "get_bios_relative_path",
                                        "get_ec_relative_path",
                                        "get_ec_hash"
                                ],
                                "passing_args": [
                                        NO_ARGS,
                                ],
                                "failing_args": [
                                        ONE_INT_ARG,
                                        ONE_STR_ARG,
                                ],
                                "allow_error_msg":
                                ("command cp -rf "
                                 "/usr/local/tmp/faft/autest/work "
                                 "/usr/local/tmp/faft/autest/cbfs failed|"
                                 "Could not detect a usable ec flash device")
                        },
                        {
                                "method_name":
                                "get_section_fwid",
                                "passing_args": [
                                        NO_ARGS,
                                        ("bios", ),
                                        ("ec", ),
                                        ("bios", "b"),
                                        ("ec", "rw"),
                                ],
                                "failing_args": [
                                        ("foo", ),
                                        ("bios", "foo"),
                                        ("ec", "foo"),
                                ],
                                "expected_return_type":
                                str,
                                "allow_error_msg":
                                r"is empty|does not contain",
                        },
                        {
                                "method_names": [
                                        "get_device_fwids",
                                        "get_image_fwids",
                                ],
                                "passing_args": [
                                        NO_ARGS,
                                        ("bios", ),
                                ],
                                "ec_passing_args": [
                                        ("ec", ),
                                ],
                                "failing_args": [
                                        ("foo", ),
                                ],
                                "expected_return_type":
                                dict,
                                "allow_error_msg": (r"is already modified|"
                                                    r"is empty|"
                                                    r"does not contain"),
                        },
                        {
                                "method_name":
                                "modify_image_fwids",
                                "passing_args": [
                                        NO_ARGS,
                                        ("bios", ),
                                        ("ec", ),
                                        ("bios", ("b", "rec")),
                                        ("ec", ("rw_b", )),
                                ],
                                "failing_args": [
                                        ("foo", ),
                                        ("bios", ("foo", )),
                                        ("ec", ("foo", )),
                                ],
                                "expected_return_type":
                                dict,
                                "allow_error_msg": (r"is already modified|"
                                                    r"is empty|"
                                                    r"does not contain"),
                        },
                        {
                                "method_name": "resign_firmware",
                                "passing_args": [
                                        ONE_INT_ARG,
                                        (None, ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_STR_ARG,
                                        (1, 1),
                                ],
                        },
                        {
                                "method_names": [
                                        "repack_shellball",
                                        "extract_shellball",
                                ],
                                "passing_args": [
                                        NO_ARGS,
                                        ("test", ),
                                        (None, ),
                                ],
                                "failing_args": [
                                        ("foo", "bar"),
                                ]
                        },
                        {
                                "method_name":
                                "run_firmwareupdate",
                                "passing_args": [
                                        ("autoupdate", ),
                                        ("recovery", ),
                                        ("bootok", ),
                                        ("factory_install", ),
                                        ("bootok", None),
                                        ("bootok", "test"),
                                        ("bootok", "test", ()),
                                        ("bootok", "test", ("--noupdate_ec",
                                                            "--wp=1")),
                                ],
                                "failing_args": [NO_ARGS],
                        },
                        {
                                "method_name":
                                "get_firmwareupdate_command",
                                "passing_args": [
                                        ("autoupdate", ),
                                        ("recovery", ),
                                        ("factory_install", ),
                                ],
                                "failing_args": [NO_ARGS],
                                "expected_return_type":
                                str
                        },
                        {
                                "method_names": [
                                        "run_autoupdate",
                                        "run_bootok",
                                ],
                                "passing_args": [
                                        ("test", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ("foo", "bar"),
                                ],
                        },
                        {
                                "method_names": [
                                        "cbfs_extract_chip",
                                        "cbfs_get_chip_hash",
                                        "cbfs_replace_chip",
                                ],
                                "passing_args":
                                [(chip_fw_name, )
                                 for chip_fw_name in CHIP_FW_NAMES],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                ],
                                "allow_error_msg":
                                "cbfstool /usr/local/tmp/faft/"
                        },
                        {
                                "method_name":
                                "copy_bios",
                                "passing_args": [('/tmp/fake-bios.bin', )],
                                "failing_args":
                                [NO_ARGS, ('/tmp/fake-bios.bin', "foo")],
                                "expected_return_type":
                                str
                        },
                        {
                                "method_name": "get_image_gbb_flags",
                                "passing_args":
                                [NO_ARGS, ('/tmp/fake-bios.bin', )],
                                "failing_args":
                                [('/tmp/fake-bios.bin', 'bogus')],
                                "store_result_as": "gbb_flags"
                        },
                        {
                                "method_name":
                                "set_image_gbb_flags",
                                "passing_args": [
                                        (operator.itemgetter('gbb_flags'), ),
                                        (operator.itemgetter('gbb_flags'),
                                         '/tmp/fake-bios.bin'),
                                ],
                                "failing_args":
                                [NO_ARGS, ('too', 'many', 'args')]
                        }
                ]
        },
        {
                "category_name":
                "rootfs",
                "test_cases": [
                        {
                                "method_name":
                                "verify_rootfs",
                                "passing_args": [
                                        ("A", ),
                                        ("B", ),
                                ],
                                "failing_args": [
                                        NO_ARGS,
                                        ONE_INT_ARG,
                                        ("C", ),
                                        ("A", "B"),
                                ],
                        },
                ]
        },
        {
                "category_name":
                '',
                "test_cases": [
                        # explicit connect
                        {
                                "method_name": "quit",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "connect",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "ready",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "disconnect",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "connect",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "ready",
                                "passing_args": [NO_ARGS]
                        },

                        # implicit connect
                        {
                                "method_name": "quit",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "ready",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "disconnect",
                                "passing_args": [NO_ARGS]
                        },
                        {
                                "method_name": "ready",
                                "passing_args": [NO_ARGS]
                        },
                ]
        }
]
