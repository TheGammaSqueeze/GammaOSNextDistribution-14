#!/usr/bin/python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from unittest import mock

import common
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros import provisioner


class _StubUpdateError(provisioner._AttributedUpdateError):
    STUB_MESSAGE = 'Stub message'
    STUB_PATTERN = 'Stub pattern matched'
    _SUMMARY = 'Stub summary'
    _CLASSIFIERS = [
            (STUB_MESSAGE, STUB_MESSAGE),
            ('Stub .*', STUB_PATTERN),
    ]

    def __init__(self, info, msg):
        super(_StubUpdateError, self).__init__('Stub %s' % info, msg)


class TestErrorClassifications(unittest.TestCase):
    """Test error message handling in `_AttributedUpdateError`."""

    def test_exception_message(self):
        """Test that the exception string includes its arguments."""
        info = 'info marker'
        msg = 'an error message'
        stub = _StubUpdateError(info, msg)
        self.assertIn(info, str(stub))
        self.assertIn(msg, str(stub))

    def test_classifier_message(self):
        """Test that the exception classifier can match a simple string."""
        info = 'info marker'
        stub = _StubUpdateError(info, _StubUpdateError.STUB_MESSAGE)
        self.assertNotIn(info, stub.failure_summary)
        self.assertIn(_StubUpdateError._SUMMARY, stub.failure_summary)
        self.assertIn(_StubUpdateError.STUB_MESSAGE, stub.failure_summary)

    def test_classifier_pattern(self):
        """Test that the exception classifier can match a regex."""
        info = 'info marker'
        stub = _StubUpdateError(info, 'Stub this is a test')
        self.assertNotIn(info, stub.failure_summary)
        self.assertIn(_StubUpdateError._SUMMARY, stub.failure_summary)
        self.assertIn(_StubUpdateError.STUB_PATTERN, stub.failure_summary)

    def test_classifier_unmatched(self):
        """Test exception summary when no classifier matches."""
        info = 'info marker'
        stub = _StubUpdateError(info, 'This matches no pattern')
        self.assertNotIn(info, stub.failure_summary)
        self.assertIn(_StubUpdateError._SUMMARY, stub.failure_summary)

    def test_host_update_error(self):
        """Test the `HostUpdateError` classifier."""
        exception = provisioner.HostUpdateError('chromeos6-row3-rack3-host19',
                                                'Fake message')
        self.assertTrue(isinstance(exception.failure_summary, str))

    def test_image_install_error(self):
        """Test the `ImageInstallError` classifier."""
        exception = provisioner.ImageInstallError(
                'chromeos6-row3-rack3-host19', 'chromeos4-devserver7.cros',
                'Fake message')
        self.assertTrue(isinstance(exception.failure_summary, str))

    def test_new_build_update_error(self):
        """Test the `NewBuildUpdateError` classifier."""
        exception = provisioner.NewBuildUpdateError('R68-10621.0.0',
                                                    'Fake message')
        self.assertTrue(isinstance(exception.failure_summary, str))


class TestProvisioner(unittest.TestCase):
    """Test provisioner module."""

    def testParseBuildFromUpdateUrlwithUpdate(self):
        """Test that we properly parse the build from an update_url."""
        update_url = ('http://172.22.50.205:8082/update/lumpy-release/'
                      'R27-3837.0.0')
        expected_value = 'lumpy-release/R27-3837.0.0'
        self.assertEqual(provisioner.url_to_image_name(update_url),
                         expected_value)

    def testGetRemoteScript(self):
        """Test _get_remote_script() behaviors."""
        update_url = ('http://172.22.50.205:8082/update/lumpy-chrome-perf/'
                      'R28-4444.0.0-b2996')
        script_name = 'fubar'
        local_script = '/usr/local/bin/%s' % script_name

        host = mock.MagicMock()
        cros_provisioner = provisioner.ChromiumOSProvisioner(update_url,
                                                             host=host)
        host.path_exists.return_value = True

        # Simple case:  file exists on DUT
        self.assertEqual(cros_provisioner._get_remote_script(script_name),
                         local_script)
        host.path_exists.assert_called_with(local_script)

        fake_shell = '/bin/ash'
        tmp_script = '/usr/local/tmp/%s' % script_name
        fake_res = fake_result('#!%s\n' % fake_shell)

        host.path_exists.return_value = False
        host.run.return_value = fake_res

        # Complicated case:  script not on DUT, so try to download it.
        self.assertEqual(cros_provisioner._get_remote_script(script_name),
                         '%s %s' % (fake_shell, tmp_script))
        host.path_exists.assert_called_with(local_script)


class fake_result(object):
    """A fake result with stdout attribute."""
    def __init__(self, result):
        self.stdout = result


class TestProvisioner2(unittest.TestCase):
    """Another test for provisioner module that using mock."""

    def testAlwaysRunQuickProvision(self):
        """Tests that we call quick provsion for all kinds of builds."""
        image = 'foo-whatever/R65-1234.5.6'
        devserver = 'http://mock_devserver'
        provisioner.dev_server = mock.MagicMock()
        provisioner.metrics = mock.MagicMock()
        host = mock.MagicMock()
        update_url = '%s/update/%s' % (devserver, image)
        cros_provisioner = provisioner.ChromiumOSProvisioner(update_url, host)
        cros_provisioner.check_update_status = mock.MagicMock()
        kernel_utils.verify_kernel_state_after_update = mock.MagicMock()
        kernel_utils.verify_kernel_state_after_update.return_value = 3
        kernel_utils.verify_boot_expectations = mock.MagicMock()

        cros_provisioner.run_provision()
        host.run.assert_any_call(
                '/usr/local/bin/quick-provision --noreboot %s '
                '%s/download/chromeos-image-archive' % (image, devserver))


if __name__ == '__main__':
    unittest.main()
