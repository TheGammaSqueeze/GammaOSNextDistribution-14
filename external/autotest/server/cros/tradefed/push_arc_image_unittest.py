# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from unittest.mock import Mock, ANY, call

from autotest_lib.server.cros.tradefed import push_arc_image

# Use this version for tests if there isn't a special version that has to be
# tested.
_TEST_DEFAULT_ARC_VERSION = '7750398'

# Use this host port combination if there isn't a special configuration that has
# to be tested.
_TEST_HOST_PORT = 'somehost:9384'

# Expected command to be run on the host (device) to mark the device for
# reprovisioning.
_MARK_DIRTY_PROVISION_COMMAND = 'touch /mnt/stateful_partition/.force_provision'

# The expected default values passed to run for mocks created with create*()
# methods, and they are unmodified.
_DEFAULT_EXPECTED_RUN_ARGS = [
        '--use-prebuilt-file',
        'arc-img.zip',
        '--sepolicy-artifacts-path',
        'sepolicy.zip',
        '--force',
        'somehost:9384',
]

# The expected default values passed to run as the script path for mocks created
# with create*() methods, and they are unmodified.
_DEFAULT_EXPECTED_PTD_PATH = 'some/extracted/dir/push_to_device.py'


class PushArcImageTest(unittest.TestCase):
    """Unittest for push_arc_image."""

    def createMockHost(self, version, abi):
        mock_host = Mock()
        mock_host.get_arc_version.return_value = version
        mock_host.get_arc_primary_abi.return_value = abi
        mock_host.host_port = _TEST_HOST_PORT
        return mock_host

    def createMockDownloadFunc(self):
        mock_download_func = Mock()
        mock_download_func.side_effect = ['arc-img.zip', 'sepolicy.zip']
        return mock_download_func

    def createMockInstallBundleFunc(self):
        mock_install_bundle_func = Mock()
        mock_install_bundle_func.return_value = 'some/extracted/dir'
        return mock_install_bundle_func

    def test_push_userdebug_image_bertha_arm64(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION, 'arm64-v8a')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'rvc-arc',
                                                    'bertha',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_arm64-userdebug/'
                '7750398/bertha_arm64-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_arm64-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_arm64-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    def test_push_userdebug_image_bertha_x86_64(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION, 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'rvc-arc',
                                                    'bertha',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_x86_64-userdebug/'
                '7750398/bertha_x86_64-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_x86_64-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_x86_64-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    def test_push_userdebug_image_cheets_arm(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION,
                                        'armeabi-v7a')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'pi-arc',
                                                    'cheets',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm-userdebug/'
                '7750398/cheets_arm-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    def test_push_userdebug_image_cheets_arm64(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION, 'arm64-v8a')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'pi-arc',
                                                    'cheets',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm64-userdebug/'
                '7750398/cheets_arm64-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm64-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_arm64-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    def test_push_userdebug_image_cheets_x86(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION, 'x86')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'pi-arc',
                                                    'cheets',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86-userdebug/'
                '7750398/cheets_x86-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    def test_push_userdebug_image_cheets_x86_64(self):
        mock_host = self.createMockHost(_TEST_DEFAULT_ARC_VERSION, 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        in_sequence = Mock()
        in_sequence.attach_mock(mock_run_func, 'run')
        in_sequence.attach_mock(mock_host.run, 'host_run')

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'pi-arc',
                                                    'cheets',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()
        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86_64-userdebug/'
                '7750398/cheets_x86_64-img-7750398.zip')

        mock_download_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86_64-userdebug/'
                '7750398/sepolicy.zip')

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86_64-userdebug/'
                '7750398/push_to_device.zip')

        expected_calls = [
                call.host_run(_MARK_DIRTY_PROVISION_COMMAND),
                call.run(
                        _DEFAULT_EXPECTED_PTD_PATH,
                        args=_DEFAULT_EXPECTED_RUN_ARGS,
                        ignore_status=ANY,
                        verbose=ANY,
                        nickname=ANY,
                ),
        ]
        self.assertEqual(in_sequence.mock_calls, expected_calls)

    # Only newer push to device has support for HOST:PORT format.
    # Verify that the if the build ID on the device is old, it
    # downloads a newer ptd.py that has the necessary features.
    def test_push_userdebug_image_old_image_bertha(self):
        mock_host = self.createMockHost('5985921', 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'rvc-arc',
                                                    'bertha',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_rvc-arc-*linux-bertha_x86_64-userdebug/'
                '7741959/push_to_device.zip')

    # Cheets has a different "minimum" version compared to bertha.
    def test_push_userdebug_image_old_image_cheets(self):
        mock_host = self.createMockHost('5985921', 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host, 'pi-arc',
                                                    'cheets',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_pi-arc-*linux-cheets_x86_64-userdebug/'
                '7740639/push_to_device.zip')

    # Even if the branch prefix is unknown, it should still try to get PTD tool.
    def test_push_userdebug_image_unknown_branch_prefix(self):
        mock_host = self.createMockHost('123456789', 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()
        self.assertTrue(
                push_arc_image.push_userdebug_image(mock_host,
                                                    'myspecialbranch',
                                                    'bertha',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))

        mock_host.get_arc_version.assert_called_once()
        mock_host.get_arc_primary_abi.assert_called_once()

        mock_install_bundle_func.assert_any_call(
                'gs://chromeos-arc-images/builds/'
                'git_myspecialbranch-*linux-bertha_x86_64-userdebug/'
                '123456789/push_to_device.zip')

    # ARC version returned by the host could be None. Verify the function
    # returns False.
    def test_push_userdebug_image_failed_to_get_arc_version(self):
        mock_host = self.createMockHost(None, 'x86_64')
        mock_download_func = self.createMockDownloadFunc()
        mock_install_bundle_func = self.createMockInstallBundleFunc()
        mock_run_func = Mock()

        self.assertFalse(
                push_arc_image.push_userdebug_image(mock_host, 'rvc-arc',
                                                    'bertha',
                                                    mock_download_func,
                                                    mock_install_bundle_func,
                                                    mock_run_func))
