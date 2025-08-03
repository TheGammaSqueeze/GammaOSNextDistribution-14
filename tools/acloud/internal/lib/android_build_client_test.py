#!/usr/bin/env python
#
# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for acloud.internal.lib.android_build_client."""

import io
import time

import unittest

from unittest import mock

import apiclient

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import driver_test_lib


# pylint: disable=protected-access
class AndroidBuildClientTest(driver_test_lib.BaseDriverTest):
    """Test AndroidBuildClient."""

    BUILD_BRANCH = "fake_branch"
    BUILD_TARGET = "fake_target"
    BUILD_ID = 12345
    RESOURCE_ID = "avd-system.tar.gz"
    LOCAL_DEST = "/fake/local/path"
    DESTINATION_BUCKET = "fake_bucket"

    def setUp(self):
        """Set up test."""
        super().setUp()
        self.Patch(android_build_client.AndroidBuildClient,
                   "InitResourceHandle")
        self.client = android_build_client.AndroidBuildClient(mock.MagicMock())
        self.client._service = mock.MagicMock()

    # pylint: disable=no-member
    def testDownloadArtifact(self):
        """Test DownloadArtifact."""
        # Create mocks.
        mock_file = mock.MagicMock()
        mock_file_io = mock.MagicMock()
        mock_file_io.__enter__.return_value = mock_file
        mock_downloader = mock.MagicMock()
        mock_downloader.next_chunk = mock.MagicMock(
            side_effect=[(mock.MagicMock(), False), (mock.MagicMock(), True)])
        mock_api = mock.MagicMock()
        self.Patch(io, "FileIO", return_value=mock_file_io)
        self.Patch(
            apiclient.http,
            "MediaIoBaseDownload",
            return_value=mock_downloader)
        mock_resource = mock.MagicMock()
        self.client._service.buildartifact = mock.MagicMock(
            return_value=mock_resource)
        mock_resource.get_media = mock.MagicMock(return_value=mock_api)
        # Make the call to the api
        self.client.DownloadArtifact(self.BUILD_TARGET, self.BUILD_ID,
                                     self.RESOURCE_ID, self.LOCAL_DEST)
        # Verify
        mock_resource.get_media.assert_called_with(
            buildId=self.BUILD_ID,
            target=self.BUILD_TARGET,
            attemptId="0",
            resourceId=self.RESOURCE_ID)
        io.FileIO.assert_called_with(self.LOCAL_DEST, mode="wb")
        mock_call = mock.call(
            mock_file,
            mock_api,
            chunksize=android_build_client.AndroidBuildClient.
            DEFAULT_CHUNK_SIZE)
        apiclient.http.MediaIoBaseDownload.assert_has_calls([mock_call])
        self.assertEqual(mock_downloader.next_chunk.call_count, 2)

    def testDownloadArtifactOSError(self):
        """Test DownloadArtifact when OSError is raised."""
        self.Patch(io, "FileIO", side_effect=OSError("fake OSError"))
        self.assertRaises(errors.DriverError, self.client.DownloadArtifact,
                          self.BUILD_TARGET, self.BUILD_ID, self.RESOURCE_ID,
                          self.LOCAL_DEST)

    def testCopyTo(self):
        """Test CopyTo."""
        mock_resource = mock.MagicMock()
        self.client._service.buildartifact = mock.MagicMock(
            return_value=mock_resource)
        self.client.CopyTo(
            build_target=self.BUILD_TARGET,
            build_id=self.BUILD_ID,
            artifact_name=self.RESOURCE_ID,
            destination_bucket=self.DESTINATION_BUCKET,
            destination_path=self.RESOURCE_ID)
        mock_resource.copyTo.assert_called_once_with(
            buildId=self.BUILD_ID,
            target=self.BUILD_TARGET,
            attemptId=self.client.DEFAULT_ATTEMPT_ID,
            artifactName=self.RESOURCE_ID,
            destinationBucket=self.DESTINATION_BUCKET,
            destinationPath=self.RESOURCE_ID)

    def testCopyToWithRetry(self):
        """Test CopyTo with retry."""
        self.Patch(time, "sleep")
        mock_resource = mock.MagicMock()
        mock_api_request = mock.MagicMock()
        mock_resource.copyTo.return_value = mock_api_request
        self.client._service.buildartifact.return_value = mock_resource
        mock_api_request.execute.side_effect = errors.HttpError(503,
                                                                "fake error")
        self.assertRaises(
            errors.HttpError,
            self.client.CopyTo,
            build_id=self.BUILD_ID,
            build_target=self.BUILD_TARGET,
            artifact_name=self.RESOURCE_ID,
            destination_bucket=self.DESTINATION_BUCKET,
            destination_path=self.RESOURCE_ID)
        self.assertEqual(mock_api_request.execute.call_count, 6)

    def testGetBranch(self):
        """Test GetBuild."""
        build_info = {"branch": "aosp-master"}
        mock_api = mock.MagicMock()
        mock_build = mock.MagicMock()
        mock_build.get.return_value = mock_api
        self.client._service.build = mock.MagicMock(return_value=mock_build)
        mock_api.execute = mock.MagicMock(return_value=build_info)
        branch = self.client.GetBranch(self.BUILD_TARGET, self.BUILD_ID)
        mock_build.get.assert_called_once_with(
            target=self.BUILD_TARGET,
            buildId=self.BUILD_ID)
        self.assertEqual(branch, build_info["branch"])

    def testGetLKGB(self):
        """Test GetLKGB."""
        build_info = {"nextPageToken":"Test", "builds": [{"buildId": "3950000"}]}
        mock_api = mock.MagicMock()
        mock_build = mock.MagicMock()
        mock_build.list.return_value = mock_api
        self.client._service.build = mock.MagicMock(return_value=mock_build)
        mock_api.execute = mock.MagicMock(return_value=build_info)
        build_id = self.client.GetLKGB(self.BUILD_TARGET, self.BUILD_BRANCH)
        mock_build.list.assert_called_once_with(
            target=self.BUILD_TARGET,
            branch=self.BUILD_BRANCH,
            buildAttemptStatus=self.client.BUILD_STATUS_COMPLETE,
            buildType=self.client.BUILD_TYPE_SUBMITTED,
            maxResults=self.client.ONE_RESULT,
            successful=self.client.BUILD_SUCCESSFUL)
        self.assertEqual(build_id, build_info.get("builds")[0].get("buildId"))

    def testGetFetchBuildArgs(self):
        """Test GetFetchBuildArgs."""
        default_build = {constants.BUILD_ID: "1234",
                         constants.BUILD_BRANCH: "base_branch",
                         constants.BUILD_TARGET: "base_target"}
        system_build = {constants.BUILD_ID: "2345",
                        constants.BUILD_BRANCH: "system_branch",
                        constants.BUILD_TARGET: "system_target"}
        kernel_build = {constants.BUILD_ID: "3456",
                        constants.BUILD_BRANCH: "kernel_branch",
                        constants.BUILD_TARGET: "kernel_target"}
        ota_build = {constants.BUILD_ID: "4567",
                     constants.BUILD_BRANCH: "ota_branch",
                     constants.BUILD_TARGET: "ota_target"}
        boot_build = {constants.BUILD_ID: "5678",
                      constants.BUILD_BRANCH: "boot_branch",
                      constants.BUILD_TARGET: "boot_target",
                      constants.BUILD_ARTIFACT: "boot-5.10.img"}
        host_package_build = {constants.BUILD_ID: "6789",
                              constants.BUILD_BRANCH: "host_package_branch",
                              constants.BUILD_TARGET: "host_package_target"}

        # Test base image.
        expected_args = ["-default_build=1234/base_target"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, {}, {}, {}, {}, {}, {}))

        # Test base image with system image.
        expected_args = ["-default_build=1234/base_target",
                         "-system_build=2345/system_target"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, system_build, {}, {}, {}, {}, {}))

        # Test base image with kernel image.
        expected_args = ["-default_build=1234/base_target",
                         "-kernel_build=3456/kernel_target"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, {}, kernel_build, {}, {}, {}, {}))

        # Test base image with boot image.
        expected_args = ["-default_build=1234/base_target",
                         "-boot_build=5678/boot_target",
                         "-boot_artifact=boot-5.10.img"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, {}, {}, boot_build, {}, {}, {}))

        # Test base image with otatools.
        expected_args = ["-default_build=1234/base_target",
                         "-otatools_build=4567/ota_target"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, {}, {}, {}, {}, ota_build, {}))

        # Test base image with host_package.
        expected_args = ["-default_build=1234/base_target",
                         "-host_package_build=6789/host_package_target"]
        self.assertEqual(
            expected_args,
            self.client.GetFetchBuildArgs(
                default_build, {}, {}, {}, {}, {}, host_package_build))

    def testGetFetchCertArg(self):
        """Test GetFetchCertArg."""
        cert_file_path = "fake_path"
        certification = (
            "{"
            "  \"data\": ["
            "    {"
            "      \"credential\": {"
            "        \"access_token\": \"fake_token\""
            "      }"
            "    }"
            "  ]"
            "}"
        )
        expected_arg = "-credential_source=fake_token"
        with mock.patch("builtins.open",
                        mock.mock_open(read_data=certification)):
            cert_arg = self.client.GetFetchCertArg(cert_file_path)
            self.assertEqual(expected_arg, cert_arg)

    def testProcessBuild(self):
        """Test creating "cuttlefish build" strings."""
        build_id = constants.BUILD_ID
        branch = constants.BUILD_BRANCH
        build_target = constants.BUILD_TARGET
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: "123", branch: "abc", build_target: "def"}),
            "123/def")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: None, branch: "abc", build_target: "def"}),
            "abc/def")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: "123", branch: None, build_target: "def"}),
            "123/def")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: "123", branch: "abc", build_target: None}),
            "123")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: None, branch: "abc", build_target: None}),
            "abc")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: "123", branch: None, build_target: None}),
            "123")
        self.assertEqual(
            self.client.ProcessBuild(
                {build_id: None, branch: None, build_target: None}),
            None)


if __name__ == "__main__":
    unittest.main()
