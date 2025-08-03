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

"""A client that talks to Android Build APIs."""

import collections
import io
import json
import logging
import os
import ssl
import stat

import apiclient

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import base_cloud_client
from acloud.internal.lib import utils


logger = logging.getLogger(__name__)

# The BuildInfo namedtuple data structure.
# It will be the data structure returned by GetBuildInfo method.
BuildInfo = collections.namedtuple("BuildInfo", [
    "branch",  # The branch name string
    "build_id",  # The build id string
    "build_target",  # The build target string
    "release_build_id"])  # The release build id string
_DEFAULT_BRANCH = "aosp-master"


class AndroidBuildClient(base_cloud_client.BaseCloudApiClient):
    """Client that manages Android Build."""

    # API settings, used by BaseCloudApiClient.
    API_NAME = "androidbuildinternal"
    API_VERSION = "v2beta1"
    SCOPE = "https://www.googleapis.com/auth/androidbuild.internal"

    # other variables.
    DEFAULT_RESOURCE_ID = "0"
    # TODO(b/27269552): We should use "latest".
    DEFAULT_ATTEMPT_ID = "0"
    DEFAULT_CHUNK_SIZE = 20 * 1024 * 1024
    NO_ACCESS_ERROR_PATTERN = "does not have storage.objects.create access"
    # LKGB variables.
    BUILD_STATUS_COMPLETE = "complete"
    BUILD_TYPE_SUBMITTED = "submitted"
    ONE_RESULT = 1
    BUILD_SUCCESSFUL = True
    LATEST = "latest"
    # FETCH_CVD variables.
    FETCHER_NAME = "fetch_cvd"
    FETCHER_BRANCH = "aosp-master"
    FETCHER_BUILD_TARGET = "aosp_cf_x86_64_phone-userdebug"
    FETCHER_ARM_VERSION_BUILD_TARGET = "aosp_cf_arm64_phone-userdebug"
    MAX_RETRY = 3
    RETRY_SLEEP_SECS = 3

    # Message constant
    COPY_TO_MSG = ("build artifact (target: %s, build_id: %s, "
                   "artifact: %s, attempt_id: %s) to "
                   "google storage (bucket: %s, path: %s)")
    # pylint: disable=invalid-name
    def DownloadArtifact(self,
                         build_target,
                         build_id,
                         resource_id,
                         local_dest,
                         attempt_id=None):
        """Get Android build attempt information.

        Args:
            build_target: Target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            build_id: Build id, a string, e.g. "2263051", "P2804227"
            resource_id: Id of the resource, e.g "avd-system.tar.gz".
            local_dest: A local path where the artifact should be stored.
                        e.g. "/tmp/avd-system.tar.gz"
            attempt_id: String, attempt id, will default to DEFAULT_ATTEMPT_ID.
        """
        attempt_id = attempt_id or self.DEFAULT_ATTEMPT_ID
        api = self.service.buildartifact().get_media(
            buildId=build_id,
            target=build_target,
            attemptId=attempt_id,
            resourceId=resource_id)
        logger.info("Downloading artifact: target: %s, build_id: %s, "
                    "resource_id: %s, dest: %s", build_target, build_id,
                    resource_id, local_dest)
        try:
            with io.FileIO(local_dest, mode="wb") as fh:
                downloader = apiclient.http.MediaIoBaseDownload(
                    fh, api, chunksize=self.DEFAULT_CHUNK_SIZE)
                done = False
                while not done:
                    _, done = downloader.next_chunk()
            logger.info("Downloaded artifact: %s", local_dest)
        except (OSError, apiclient.errors.HttpError) as e:
            logger.error("Downloading artifact failed: %s", str(e))
            raise errors.DriverError(str(e))

    def DownloadFetchcvd(
            self,
            local_dest,
            fetch_cvd_version,
            is_arm_version=False):
        """Get fetch_cvd from Android Build.

        Args:
            local_dest: A local path where the artifact should be stored.
                        e.g. "/tmp/fetch_cvd"
            fetch_cvd_version: String of fetch_cvd version.
            is_arm_version: is ARM version fetch_cvd.
        """
        if fetch_cvd_version == constants.LKGB:
            fetch_cvd_version = self.GetFetcherVersion()
        fetch_cvd_build_target = (
            self.FETCHER_ARM_VERSION_BUILD_TARGET if is_arm_version
            else self.FETCHER_BUILD_TARGET)
        try:
            utils.RetryExceptionType(
                exception_types=(ssl.SSLError, errors.DriverError),
                max_retries=self.MAX_RETRY,
                functor=self.DownloadArtifact,
                sleep_multiplier=self.RETRY_SLEEP_SECS,
                retry_backoff_factor=utils.DEFAULT_RETRY_BACKOFF_FACTOR,
                build_target=fetch_cvd_build_target,
                build_id=fetch_cvd_version,
                resource_id=self.FETCHER_NAME,
                local_dest=local_dest,
                attempt_id=self.LATEST)
        except Exception:
            logger.debug("Download fetch_cvd with build id: %s",
                         constants.FETCH_CVD_SECOND_VERSION)
            utils.RetryExceptionType(
                exception_types=(ssl.SSLError, errors.DriverError),
                max_retries=self.MAX_RETRY,
                functor=self.DownloadArtifact,
                sleep_multiplier=self.RETRY_SLEEP_SECS,
                retry_backoff_factor=utils.DEFAULT_RETRY_BACKOFF_FACTOR,
                build_target=fetch_cvd_build_target,
                build_id=constants.FETCH_CVD_SECOND_VERSION,
                resource_id=self.FETCHER_NAME,
                local_dest=local_dest,
                attempt_id=self.LATEST)
        fetch_cvd_stat = os.stat(local_dest)
        os.chmod(local_dest, fetch_cvd_stat.st_mode | stat.S_IEXEC)

    @staticmethod
    def ProcessBuild(build_info):
        """Create a Cuttlefish fetch_cvd build string.

        Args:
            build_info: The dictionary that contains build information.

        Returns:
            A string, used in the fetch_cvd cmd or None if all args are None.
        """
        build_id = build_info.get(constants.BUILD_ID)
        build_target = build_info.get(constants.BUILD_TARGET)
        branch = build_info.get(constants.BUILD_BRANCH)
        if not build_target:
            return build_id or branch

        if build_target and not branch:
            branch = _DEFAULT_BRANCH
        return (build_id or branch) + "/" + build_target

    def GetFetchBuildArgs(self, default_build_info, system_build_info,
                          kernel_build_info, boot_build_info,
                          bootloader_build_info, ota_build_info,
                          host_package_build_info):
        """Get args from build information for fetch_cvd.

        Each build_info is a dictionary that contains 3 items, for example,
        {
            constants.BUILD_ID: "2263051",
            constants.BUILD_TARGET: "aosp_cf_x86_64_phone-userdebug",
            constants.BUILD_BRANCH: "aosp-master",
        }

        Args:
            default_build_info: The build that provides full cuttlefish images.
            system_build_info: The build that provides the system image.
            kernel_build_info: The build that provides the kernel.
            boot_build_info: The build that provides the boot image. This
                             dictionary may contain an additional key
                             constants.BUILD_ARTIFACT which is mapped to the
                             boot image name.
            bootloader_build_info: The build that provides the bootloader.
            ota_build_info: The build that provides the OTA tools.
            host_package_build_info: The build that provides the host package.

        Returns:
            List of string args for fetch_cvd.
        """
        fetch_cvd_args = []

        default_build = self.ProcessBuild(default_build_info)
        if default_build:
            fetch_cvd_args.append(f"-default_build={default_build}")
        system_build = self.ProcessBuild(system_build_info)
        if system_build:
            fetch_cvd_args.append(f"-system_build={system_build}")
        bootloader_build = self.ProcessBuild(bootloader_build_info)
        if bootloader_build:
            fetch_cvd_args.append(f"-bootloader_build={bootloader_build}")
        kernel_build = self.GetKernelBuild(kernel_build_info)
        if kernel_build:
            fetch_cvd_args.append(f"-kernel_build={kernel_build}")
        boot_build = self.ProcessBuild(boot_build_info)
        if boot_build:
            fetch_cvd_args.append(f"-boot_build={boot_build}")
            boot_artifact = boot_build_info.get(constants.BUILD_ARTIFACT)
            if boot_artifact:
                fetch_cvd_args.append(f"-boot_artifact={boot_artifact}")
        ota_build = self.ProcessBuild(ota_build_info)
        if ota_build:
            fetch_cvd_args.append(f"-otatools_build={ota_build}")
        host_package_build = self.ProcessBuild(host_package_build_info)
        if host_package_build:
            fetch_cvd_args.append(f"-host_package_build={host_package_build}")

        return fetch_cvd_args

    def GetFetcherVersion(self):
        """Get fetch_cvd build id from LKGB.

        Returns:
            The build id of fetch_cvd.
        """
        return self.GetLKGB(self.FETCHER_BUILD_TARGET, self.FETCHER_BRANCH)

    @staticmethod
    # pylint: disable=broad-except
    def GetFetchCertArg(certification_file):
        """Get cert arg from certification file for fetch_cvd.

        Parse the certification file to get access token of the latest
        credential data and pass it to fetch_cvd command.
        Example of certification file:
        {
          "data": [
          {
            "credential": {
              "_class": "OAuth2Credentials",
              "_module": "oauth2client.client",
              "access_token": "token_strings",
              "client_id": "179485041932",
            }
          }]
        }


        Args:
            certification_file: String of certification file path.

        Returns:
            String of certificate arg for fetch_cvd. If there is no
            certification file, return empty string for aosp branch.
        """
        cert_arg = ""
        try:
            with open(certification_file) as cert_file:
                auth_token = json.load(cert_file).get("data")[-1].get(
                    "credential").get("access_token")
                if auth_token:
                    cert_arg = f"-credential_source={auth_token}"
        except Exception as e:
            utils.PrintColorString(
                f"Fail to open the certification file "
                f"({certification_file}): {e}",
                utils.TextColors.WARNING)
        return cert_arg

    def GetKernelBuild(self, kernel_build_info):
        """Get kernel build args for fetch_cvd.

        Args:
            kernel_build_info: The dictionary that contains build information.

        Returns:
            String of kernel build args for fetch_cvd.
            If no kernel build then return None.
        """
        # kernel_target have default value "kernel". If user provide kernel_build_id
        # or kernel_branch, then start to process kernel image.
        if (kernel_build_info.get(constants.BUILD_ID) or
                kernel_build_info.get(constants.BUILD_BRANCH)):
            return self.ProcessBuild(kernel_build_info)
        return None

    def CopyTo(self,
               build_target,
               build_id,
               artifact_name,
               destination_bucket,
               destination_path,
               attempt_id=None):
        """Copy an Android Build artifact to a storage bucket.

        Args:
            build_target: Target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            build_id: Build id, a string, e.g. "2263051", "P2804227"
            artifact_name: Name of the artifact, e.g "avd-system.tar.gz".
            destination_bucket: String, a google storage bucket name.
            destination_path: String, "path/inside/bucket"
            attempt_id: String, attempt id, will default to DEFAULT_ATTEMPT_ID.
        """
        attempt_id = attempt_id or self.DEFAULT_ATTEMPT_ID
        copy_msg = "Copying %s" % self.COPY_TO_MSG
        logger.info(copy_msg, build_target, build_id, artifact_name,
                    attempt_id, destination_bucket, destination_path)
        api = self.service.buildartifact().copyTo(
            buildId=build_id,
            target=build_target,
            attemptId=attempt_id,
            artifactName=artifact_name,
            destinationBucket=destination_bucket,
            destinationPath=destination_path)
        try:
            self.Execute(api)
            finish_msg = "Finished copying %s" % self.COPY_TO_MSG
            logger.info(finish_msg, build_target, build_id, artifact_name,
                        attempt_id, destination_bucket, destination_path)
        except errors.HttpError as e:
            if e.code == 503:
                if self.NO_ACCESS_ERROR_PATTERN in str(e):
                    error_msg = "Please grant android build team's service account "
                    error_msg += "write access to bucket %s. Original error: %s"
                    error_msg %= (destination_bucket, str(e))
                    raise errors.HttpError(e.code, message=error_msg)
            raise

    def GetBranch(self, build_target, build_id):
        """Derives branch name.

        Args:
            build_target: Target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            build_id: Build ID, a string, e.g. "2263051", "P2804227"

        Returns:
            A string, the name of the branch
        """
        api = self.service.build().get(buildId=build_id, target=build_target)
        build = self.Execute(api)
        return build.get("branch", "")

    def GetLKGB(self, build_target, build_branch):
        """Get latest successful build id.

        From branch and target, we can use api to query latest successful build id.
        e.g. {u'nextPageToken':..., u'builds': [{u'completionTimestamp':u'1534157869286',
        ... u'buildId': u'4949805', u'machineName'...}]}

        Args:
            build_target: String, target name, e.g. "aosp_cf_x86_64_phone-userdebug"
            build_branch: String, git branch name, e.g. "aosp-master"

        Returns:
            A string, string of build id number.

        Raises:
            errors.CreateError: Can't get build id.
        """
        api = self.service.build().list(
            branch=build_branch,
            target=build_target,
            buildAttemptStatus=self.BUILD_STATUS_COMPLETE,
            buildType=self.BUILD_TYPE_SUBMITTED,
            maxResults=self.ONE_RESULT,
            successful=self.BUILD_SUCCESSFUL)
        build = self.Execute(api)
        logger.info("GetLKGB build API response: %s", build)
        if build:
            return str(build.get("builds")[0].get("buildId"))
        raise errors.GetBuildIDError(
            "No available good builds for branch: %s target: %s"
            % (build_branch, build_target)
        )

    def GetBuildInfo(self, build_target, build_id, branch):
        """Get build info namedtuple.

        Args:
          build_target: Target name.
          build_id: Build id, a string or None, e.g. "2263051", "P2804227"
                    If None or latest, the last green build id will be
                    returned.
          branch: Branch name, a string or None, e.g. git_master. If None, the
                  returned branch will be searched by given build_id.

        Returns:
          A build info namedtuple with keys build_target, build_id, branch and
          gcs_bucket_build_id
        """
        if build_id and build_id != self.LATEST:
            # Get build from build_id and build_target
            api = self.service.build().get(buildId=build_id,
                                           target=build_target)
            build = self.Execute(api) or {}
        elif branch:
            # Get last green build in the branch
            api = self.service.build().list(
                branch=branch,
                target=build_target,
                successful=True,
                maxResults=1,
                buildType="submitted")
            builds = self.Execute(api).get("builds", [])
            build = builds[0] if builds else {}
        else:
            build = {}

        build_id = build.get("buildId")
        build_target = build_target if build_id else None
        return BuildInfo(build.get("branch"), build_id, build_target,
                         build.get("releaseCandidateName"))
