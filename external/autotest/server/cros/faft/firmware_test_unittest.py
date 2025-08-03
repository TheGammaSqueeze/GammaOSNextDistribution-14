# Lint as: python2, python3
# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from unittest import mock

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft import firmware_test


class TestRunOnce(unittest.TestCase):
    """Tests that run_once works as expected."""
    class GoodFirmwareTest(firmware_test.FirmwareTest):
        """A FirmwareTest stub providing the parts that should be tested."""
        def __init__(self, *_args, **_dargs):
            # pylint: disable=super-init-not-called
            """Init logic in FirmwareTest is not relevant to this test."""
            self.test = mock.MagicMock()
            self.test_good = mock.MagicMock()
            self.test_good_better = mock.MagicMock()
            self.test_host_mock = mock.MagicMock()
            self.test_arg2_mock = mock.MagicMock()

        def test_host(self, host, **kwargs):
            """Get at the 'host' mock"""
            self.test_host_mock(host, **kwargs)

        def test_arg2(self, arg2):
            """Get at the 'arg2' mock"""
            self.test_arg2_mock(arg2)

    def test_keyword_test_name(self):
        """Test that keyworded test names work"""
        ft = self.GoodFirmwareTest()

        ft.run_once(test_name='GoodFirmwareTest.good')
        ft.test_good.assert_called_with()

        ft.run_once('arg1', test_name='GoodFirmwareTest.good', arg2='arg2')
        ft.test_good.assert_called_with('arg1', arg2='arg2')

    def test_positional_test_name(self):
        """Test that positional test names work"""
        ft = self.GoodFirmwareTest()

        ft.run_once('GoodFirmwareTest.good')
        ft.test_good.assert_called_with()

        ft.run_once('GoodFirmwareTest.good', 'arg1', arg2='arg2')
        ft.test_good.assert_called_with('arg1', arg2='arg2')

    def test_no_test_name(self):
        """Test that running a whole test class works"""
        ft = self.GoodFirmwareTest()

        ft.run_once('GoodFirmwareTest')
        ft.test.assert_called_with()

        ft.run_once('GoodFirmwareTest', 'arg1', arg2='arg2')
        ft.test.assert_called_with('arg1', arg2='arg2')

    def test_sub_test_name(self):
        """Test that sub tests can be executed"""
        ft = self.GoodFirmwareTest()

        ft.run_once('GoodFirmwareTest.good.better')
        ft.test_good_better.assert_called_with()

        ft.run_once('GoodFirmwareTest.good.better', 'arg1', arg2='arg2')
        ft.test_good_better.assert_called_with('arg1', arg2='arg2')

    def test_missing_test_name(self):
        """Test that a test name must be passed"""
        ft = self.GoodFirmwareTest()

        with self.assertRaises(error.TestError):
            ft.run_once()

    def test_bad_class_name(self):
        """Test that the class name must be valid"""
        ft = self.GoodFirmwareTest()

        with self.assertRaises(error.TestError):
            ft.run_once(test_name='BadFirmwareTest')

    def test_bad_method_name(self):
        """Test that the method must be valid"""
        ft = self.GoodFirmwareTest()

        with self.assertRaises(error.TestError):
            ft.run_once(test_name='GoodFirmwareTest.bad')

    def test_host_arg(self):
        """Test operation with host arg used"""
        ft = self.GoodFirmwareTest()

        ft.run_once('GoodFirmwareTest.host', host='host', arg2='arg2')
        ft.test_host_mock.assert_called_with('host', arg2='arg2')

    def test_arg2(self):
        """Test operation with arg2 used"""
        ft = self.GoodFirmwareTest()

        ft.run_once('GoodFirmwareTest.arg2', host='host', arg2='arg2')
        ft.test_arg2_mock.assert_called_with('arg2')


class TestCheckPowerState(unittest.TestCase):
    """Test that power_state matching is precise"""
    # Mock out EC behavior to return definable power states
    class MockedECFirmwareTest(firmware_test.FirmwareTest):
        """A stubbed out FirmwareTest to check the precision behavior"""

        def __init__(self, *_args, **_dargs):
            # pylint: disable=super-init-not-called
            pass

    # power_state is supposed to be a string, but lists seem somewhat common,
    # so guard against them.
    def test_fails_on_list(self):
        ft = self.MockedECFirmwareTest()

        with self.assertRaises(error.TestError):
            ft._check_power_state([], 'S0')

    def test_s0ix_isnt_s0(self):
        ft = self.MockedECFirmwareTest()

        self.assertEqual(False, ft._check_power_state("S0", "S0ix"))

    def test_s0_is_found(self):
        ft = self.MockedECFirmwareTest()

        self.assertEqual(True, ft._check_power_state("S0", "S0"))

    def test_s0_is_found_unicode(self):
        ft = self.MockedECFirmwareTest()

        self.assertEqual(True, ft._check_power_state(u"S0", "S0"))
        self.assertEqual(True, ft._check_power_state("S0", u"S0"))
        self.assertEqual(True, ft._check_power_state(u"S0", u"S0"))

    def test_s0_or_s3_is_found(self):
        ft = self.MockedECFirmwareTest()

        self.assertEqual(True, ft._check_power_state("(S0|S3)", "S0"))
        self.assertEqual(True, ft._check_power_state("(S0|S3)", "S3"))
        self.assertEqual(False, ft._check_power_state("(S0|S3)", "G3"))


class Test_stage_build_to_usbkey(unittest.TestCase):
    """stage_build_to_usbkey test"""

    class MockFirmwareTest(firmware_test.FirmwareTest):
        """Mock of FirmwareTest"""

        def __init__(self):
            self._client = mock.MagicMock()
            self.faft_client = mock.MagicMock()

    def setUp(self):
        self.test = self.MockFirmwareTest()

    def test_stage_build_to_usbkey(self):
        self.test._client.host_info_store.get.return_value.build = "placeholder_build"
        self.test._client._servo_host.validate_image_usbkey.return_value = (
            "another_build")
        self.assertTrue(self.test.stage_build_to_usbkey())
        self.test._client.stage_build_to_usb.assert_called_with(
                "placeholder_build")

    def test_stage_build_to_usbkey_same_build(self):
        self.test._client.host_info_store.get.return_value.build = "placeholder_build"
        self.test._client._servo_host.validate_image_usbkey.return_value = (
                "placeholder_build")
        self.assertTrue(self.test.stage_build_to_usbkey())
        self.test._client.stage_build_to_usb.assert_not_called()

    def test_stage_build_to_usbkey_no_build(self):
        self.test._client.host_info_store.get.return_value.build = None
        self.assertFalse(self.test.stage_build_to_usbkey())
        self.test._client.stage_build_to_usb.assert_not_called()

    def test_stage_build_to_usbkey_download_error(self):
        self.test._client.host_info_store.get.return_value.build = "placeholder_build"
        self.test._client._servo_host.validate_image_usbkey.return_value = (
            "another_build")
        self.test._client.stage_build_to_usb = (
            mock.MagicMock(side_effect=error.AutotestError("download")))
        self.assertFalse(self.test.stage_build_to_usbkey())
        self.test._client.stage_build_to_usb.assert_called_with(
                "placeholder_build")

    def test_setup_usbkey(self):
        self.test._client.host_info_store.get.return_value.build = "placeholder_build"
        self.test._client._servo_host.validate_image_usbkey.return_value = (
            "another_build")
        self.test.assert_test_image_in_usb_disk = mock.MagicMock()
        self.test.set_servo_v4_role_to_snk = mock.MagicMock()
        with mock.patch('time.sleep'):
            self.test.setup_usbkey(usbkey=True)
        self.test._client.stage_build_to_usb.assert_called_with(
                "placeholder_build")
        self.test.assert_test_image_in_usb_disk.assert_called()
        self.test.set_servo_v4_role_to_snk.assert_called()

    def test_setup_usbkey_no_stage(self):
        self.test._client.host_info_store.get.return_value.build = "placeholder_build"
        self.test._client._servo_host.validate_image_usbkey.return_value = (
            "another_build")
        self.test.assert_test_image_in_usb_disk = mock.MagicMock()
        self.test.set_servo_v4_role_to_snk = mock.MagicMock()
        self.test.servo = mock.MagicMock()
        self.test.setup_usbkey(usbkey=False)
        self.test._client.stage_build_to_usb.assert_not_called()
        self.test.assert_test_image_in_usb_disk.assert_not_called()
        self.test.servo.switch_usbkey.assert_called_with('host')
        self.test.set_servo_v4_role_to_snk.assert_not_called()


if __name__ == '__main__':
    unittest.main()
