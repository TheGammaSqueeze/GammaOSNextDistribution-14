# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import glob
import os
import unittest

import common

from autotest_lib.server.cros.tradefed import cts_expected_failure_parser
from autotest_lib.server.hosts import cros_host


class MockHost(cros_host.CrosHost):
    """Simple host for running mock'd host commands"""

    def __init__(self, hostname, vulkan=False):
        self.hostname = hostname
        self.vulkan = vulkan

    def has_arc_hardware_vulkan(self):
        return self.vulkan


def glob_add_files(expected_fail_dir):
    """Return a list of files based on a directory path."""

    expected_fail_files = []
    expected_fail_dir_path = os.path.join(
            os.path.dirname(os.path.realpath(__file__)), expected_fail_dir)
    if os.path.exists(expected_fail_dir_path):
        expected_fail_files += glob.glob(expected_fail_dir_path + '/*.yaml')
    return expected_fail_files


class CtsExpectedFailureParserTest(unittest.TestCase):
    """Unittest for cts_expected_failure_parser."""

    def test_should_skip_if_no_vulkan(self):
        mockhost = MockHost('MockHost', False)
        expected_fail_files = glob_add_files(
                'cts_expected_failure_parser_unittest_data')

        waivers = cts_expected_failure_parser.ParseKnownCTSFailures(
                expected_fail_files)
        # params: arch, board, model, bundle_abi, sdk_ver, first_api_level, host
        found_waivers = waivers.find_waivers('x86', 'hatch', 'kohaku', 'x86',
                                             '30', '30', mockhost)
        self.assertFalse('GtsOpenglTestCases' in found_waivers)
        self.assertTrue('GtsVulkanTestCases' in found_waivers)

    def test_should_not_skip_if_has_vulkan(self):
        mockhost = MockHost('MockHost', True)
        expected_fail_files = glob_add_files(
                'cts_expected_failure_parser_unittest_data')

        waivers = cts_expected_failure_parser.ParseKnownCTSFailures(
                expected_fail_files)
        # params: arch, board, model, bundle_abi, sdk_ver, first_api_level, host
        found_waivers = waivers.find_waivers('x86', 'hatch', 'kohaku', 'x86',
                                             '30', '30', mockhost)

        self.assertTrue('GtsOpenglTestCases' in found_waivers)
        self.assertFalse('GtsVulkanTestCases' in found_waivers)

    def test_binarytranslated_tag(self):
        mockhost = MockHost('MockHost', False)
        expected_fail_files = glob_add_files(
                'cts_expected_failure_parser_unittest_data')

        waivers = cts_expected_failure_parser.ParseKnownCTSFailures(
                expected_fail_files)
        # params: arch, board, model, bundle_abi, sdk_ver, first_api_level, host
        found_waivers = waivers.find_waivers('x86', 'hatch', 'kohaku', 'arm',
                                             '30', '30', mockhost)

        self.assertTrue('GtsOnlyPrimaryAbiTestCases' in found_waivers)

        # params: arch, board, model, bundle_abi, sdk_ver, first_api_level, host
        found_waivers = waivers.find_waivers('x86', 'hatch', 'kohaku', 'x86',
                                             '30', '30', mockhost)

        self.assertFalse('GtsOnlyPrimaryAbiTestCases' in found_waivers)


if __name__ == '__main__':
    unittest.main()
