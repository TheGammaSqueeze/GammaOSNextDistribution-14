# Copyright 2022 - The Android Open Source Project
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

"""RemoteHostDeviceFactory implements the device factory interface and creates
cuttlefish instances on a remote host."""

import glob
import json
import logging
import os
import posixpath as remote_path
import shutil
import subprocess
import tempfile
import time

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import auth
from acloud.internal.lib import android_build_client
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import remote_host_client
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public.actions import base_device_factory
from acloud.pull import pull


logger = logging.getLogger(__name__)
_ALL_FILES = "*"
_HOME_FOLDER = os.path.expanduser("~")
_TEMP_PREFIX = "acloud_remote_host"
_IMAGE_ARGS_FILE_NAME = "acloud_image_args.txt"


class RemoteHostDeviceFactory(base_device_factory.BaseDeviceFactory):
    """A class that can produce a cuttlefish device.

    Attributes:
        avd_spec: AVDSpec object that tells us what we're going to create.
        local_image_artifact: A string, path to local image.
        cvd_host_package_artifact: A string, path to cvd host package.
        all_failures: A dictionary mapping instance names to errors.
        all_logs: A dictionary mapping instance names to lists of
                  report.LogFile.
        compute_client: An object of remote_host_client.RemoteHostClient.
        ssh: An Ssh object.
        android_build_client: An android_build_client.AndroidBuildClient that
                              is lazily initialized.
    """

    _USER_BUILD = "userbuild"

    def __init__(self, avd_spec, local_image_artifact=None,
                 cvd_host_package_artifact=None):
        """Initialize attributes."""
        self._avd_spec = avd_spec
        self._local_image_artifact = local_image_artifact
        self._cvd_host_package_artifact = cvd_host_package_artifact
        self._all_failures = {}
        self._all_logs = {}
        super().__init__(
            remote_host_client.RemoteHostClient(avd_spec.remote_host))
        self._ssh = None
        self._android_build_client = None

    @property
    def _build_api(self):
        """Return an android_build_client.AndroidBuildClient object."""
        if not self._android_build_client:
            credentials = auth.CreateCredentials(self._avd_spec.cfg)
            self._android_build_client = android_build_client.AndroidBuildClient(
                credentials)
        return self._android_build_client

    def CreateInstance(self):
        """Create a single configured cuttlefish device.

        Returns:
            A string, representing instance name.
        """
        start_time = time.time()
        self._compute_client.SetStage(constants.STAGE_SSH_CONNECT)
        instance = self._InitRemotehost()
        start_time = self._compute_client.RecordTime(
            constants.TIME_GCE, start_time)

        process_artifacts_timestart = start_time
        self._compute_client.SetStage(constants.STAGE_ARTIFACT)
        image_args = self._ProcessRemoteHostArtifacts()
        start_time = self._compute_client.RecordTime(
            constants.TIME_ARTIFACT, start_time)

        self._compute_client.SetStage(constants.STAGE_BOOT_UP)
        error_msg = self._LaunchCvd(image_args, process_artifacts_timestart)
        start_time = self._compute_client.RecordTime(
            constants.TIME_LAUNCH, start_time)

        if error_msg:
            self._all_failures[instance] = error_msg
        self._FindLogFiles(
            instance, (error_msg and not self._avd_spec.no_pull_log))
        return instance

    def _GetInstancePath(self, relative_path=""):
        """Append a relative path to the remote base directory.

        Args:
            relative_path: The remote relative path.

        Returns:
            The remote base directory if relative_path is empty.
            The remote path under the base directory otherwise.
        """
        base_dir = cvd_utils.GetRemoteHostBaseDir(
            self._avd_spec.base_instance_num)
        return (remote_path.join(base_dir, relative_path) if relative_path else
                base_dir)

    def _GetArtifactPath(self, relative_path=""):
        """Append a relative path to the remote image directory.

        Args:
            relative_path: The remote relative path.

        Returns:
            GetInstancePath if avd_spec.remote_image_dir is empty.
            avd_spec.remote_image_dir if relative_path is empty.
            The remote path under avd_spec.remote_image_dir otherwise.
        """
        remote_image_dir = self._avd_spec.remote_image_dir
        if remote_image_dir:
            return (remote_path.join(remote_image_dir, relative_path)
                    if relative_path else remote_image_dir)
        return self._GetInstancePath(relative_path)

    def _InitRemotehost(self):
        """Determine the remote host instance name and activate ssh.

        Returns:
            A string, representing instance name.
        """
        # Get product name from the img zip file name or TARGET_PRODUCT.
        image_name = os.path.basename(
            self._local_image_artifact) if self._local_image_artifact else ""
        build_target = (os.environ.get(constants.ENV_BUILD_TARGET)
                        if "-" not in image_name else
                        image_name.split("-", maxsplit=1)[0])
        build_id = self._USER_BUILD
        if self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE:
            build_id = self._avd_spec.remote_image[constants.BUILD_ID]

        instance = cvd_utils.FormatRemoteHostInstanceName(
            self._avd_spec.remote_host, self._avd_spec.base_instance_num,
            build_id, build_target)
        ip = ssh.IP(ip=self._avd_spec.remote_host)
        self._ssh = ssh.Ssh(
            ip=ip,
            user=self._avd_spec.host_user,
            ssh_private_key_path=(self._avd_spec.host_ssh_private_key_path or
                                  self._avd_spec.cfg.ssh_private_key_path),
            extra_args_ssh_tunnel=self._avd_spec.cfg.extra_args_ssh_tunnel,
            report_internal_ip=self._avd_spec.report_internal_ip)
        self._ssh.WaitForSsh(timeout=self._avd_spec.ins_timeout_secs)
        cvd_utils.CleanUpRemoteCvd(self._ssh, self._GetInstancePath(),
                                   raise_error=False)
        return instance

    def _ProcessRemoteHostArtifacts(self):
        """Initialize or reuse the images on the remote host.

        Returns:
            A list of strings, the launch_cvd arguments.
        """
        remote_image_dir = self._avd_spec.remote_image_dir
        if remote_image_dir:
            remote_args_path = remote_path.join(remote_image_dir,
                                                _IMAGE_ARGS_FILE_NAME)
            launch_cvd_args = cvd_utils.LoadRemoteImageArgs(
                self._ssh, remote_args_path)
            if launch_cvd_args is not None:
                logger.info("Reuse the images in %s", remote_image_dir)
                return launch_cvd_args
            logger.info("Create images in %s", remote_image_dir)

        launch_cvd_args = self._InitRemoteImageDir()

        if remote_image_dir:
            cvd_utils.SaveRemoteImageArgs(self._ssh, remote_args_path,
                                          launch_cvd_args)
        return launch_cvd_args

    def _InitRemoteImageDir(self):
        """Create remote host artifacts.

        - If images source is local, tool will upload images from local site to
          remote host.
        - If images source is remote, tool will download images from android
          build to local and unzip it then upload to remote host, because there
          is no permission to fetch build rom on the remote host.

        Returns:
            A list of strings, the launch_cvd arguments.
        """
        self._ssh.Run(f"mkdir -p {self._GetArtifactPath()}")

        launch_cvd_args = []
        temp_dir = None
        try:
            target_files_dir = None
            if cvd_utils.AreTargetFilesRequired(self._avd_spec):
                if self._avd_spec.image_source != constants.IMAGE_SRC_LOCAL:
                    temp_dir = tempfile.mkdtemp(prefix=_TEMP_PREFIX)
                    self._DownloadTargetFiles(temp_dir)
                    target_files_dir = temp_dir
                elif self._local_image_artifact:
                    temp_dir = tempfile.mkdtemp(prefix=_TEMP_PREFIX)
                    cvd_utils.ExtractTargetFilesZip(self._local_image_artifact,
                                                    temp_dir)
                    target_files_dir = temp_dir
                else:
                    target_files_dir = self._avd_spec.local_image_dir

            if self._avd_spec.image_source == constants.IMAGE_SRC_LOCAL:
                cvd_utils.UploadArtifacts(
                    self._ssh, self._GetArtifactPath(),
                    (target_files_dir or self._local_image_artifact or
                     self._avd_spec.local_image_dir),
                    self._cvd_host_package_artifact)
            else:
                temp_dir = tempfile.mkdtemp(prefix=_TEMP_PREFIX)
                logger.debug("Extracted path of artifacts: %s", temp_dir)
                if self._avd_spec.remote_fetch:
                    # TODO: Check fetch cvd wrapper file is valid.
                    if self._avd_spec.fetch_cvd_wrapper:
                        self._UploadFetchCvd(temp_dir)
                        self._DownloadArtifactsByFetchWrapper()
                    else:
                        self._UploadFetchCvd(temp_dir)
                        self._DownloadArtifactsRemotehost()
                else:
                    self._DownloadArtifacts(temp_dir)
                    self._UploadRemoteImageArtifacts(temp_dir)

            launch_cvd_args.extend(
                cvd_utils.UploadExtraImages(self._ssh, self._GetArtifactPath(),
                                            self._avd_spec, target_files_dir))
        finally:
            if temp_dir:
                shutil.rmtree(temp_dir)

        return launch_cvd_args

    def _DownloadTargetFiles(self, temp_dir):
        """Download and extract target files zip.

        Args:
            temp_dir: The directory where the zip is extracted.
        """
        build_target = self._avd_spec.remote_image[constants.BUILD_TARGET]
        build_id = self._avd_spec.remote_image[constants.BUILD_ID]
        with tempfile.NamedTemporaryFile(
                prefix=_TEMP_PREFIX, suffix=".zip") as target_files_zip:
            self._build_api.DownloadArtifact(
                build_target, build_id,
                cvd_utils.GetMixBuildTargetFilename(build_target, build_id),
                target_files_zip.name)
            cvd_utils.ExtractTargetFilesZip(target_files_zip.name,
                                            temp_dir)

    def _GetRemoteFetchCredentialArg(self):
        """Get the credential source argument for remote fetch_cvd.

        Remote fetch_cvd uses the service account key uploaded by
        _UploadFetchCvd if it is available. Otherwise, fetch_cvd uses the
        token extracted from the local credential file.

        Returns:
            A string, the credential source argument.
        """
        cfg = self._avd_spec.cfg
        if cfg.service_account_json_private_key_path:
            return "-credential_source=" + self._GetArtifactPath(
                constants.FETCH_CVD_CREDENTIAL_SOURCE)

        return self._build_api.GetFetchCertArg(
            os.path.join(_HOME_FOLDER, cfg.creds_cache_file))

    @utils.TimeExecute(
        function_description="Downloading artifacts on remote host by fetch "
                             "cvd wrapper.")
    def _DownloadArtifactsByFetchWrapper(self):
        """Generate fetch_cvd args and run fetch cvd wrapper on remote host
        to download artifacts.

        Fetch cvd wrapper will fetch from cluster cached artifacts, and
        fallback to fetch_cvd if the artifacts not exist.
        """
        fetch_cvd_build_args = self._build_api.GetFetchBuildArgs(
            self._avd_spec.remote_image,
            self._avd_spec.system_build_info,
            self._avd_spec.kernel_build_info,
            self._avd_spec.boot_build_info,
            self._avd_spec.bootloader_build_info,
            self._avd_spec.ota_build_info,
            self._avd_spec.host_package_build_info)

        fetch_cvd_args = self._avd_spec.fetch_cvd_wrapper.split(',') + [
            f"-directory={self._GetArtifactPath()}",
            f"-fetch_cvd_path={self._GetArtifactPath(constants.FETCH_CVD)}",
            self._GetRemoteFetchCredentialArg()]
        fetch_cvd_args.extend(fetch_cvd_build_args)

        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)
        cmd = (f"{ssh_cmd} -- " + " ".join(fetch_cvd_args))
        logger.debug("cmd:\n %s", cmd)
        ssh.ShellCmdWithRetry(cmd)

    @utils.TimeExecute(
        function_description="Downloading artifacts on remote host")
    def _DownloadArtifactsRemotehost(self):
        """Generate fetch_cvd args and run fetch_cvd on remote host to
        download artifacts.
        """
        fetch_cvd_build_args = self._build_api.GetFetchBuildArgs(
            self._avd_spec.remote_image,
            self._avd_spec.system_build_info,
            self._avd_spec.kernel_build_info,
            self._avd_spec.boot_build_info,
            self._avd_spec.bootloader_build_info,
            self._avd_spec.ota_build_info,
            self._avd_spec.host_package_build_info)

        fetch_cvd_args = [self._GetArtifactPath(constants.FETCH_CVD),
                          f"-directory={self._GetArtifactPath()}",
                          self._GetRemoteFetchCredentialArg()]
        fetch_cvd_args.extend(fetch_cvd_build_args)

        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)
        cmd = (f"{ssh_cmd} -- " + " ".join(fetch_cvd_args))
        logger.debug("cmd:\n %s", cmd)
        ssh.ShellCmdWithRetry(cmd)

    @utils.TimeExecute(function_description="Download and upload fetch_cvd")
    def _UploadFetchCvd(self, extract_path):
        """Download fetch_cvd, duplicate service account json private key when available and upload
           to remote host.

        Args:
            extract_path: String, a path include extracted files.
        """
        cfg = self._avd_spec.cfg
        is_arm_flavor = cvd_utils.RunOnArmMachine(self._ssh) and self._avd_spec.remote_fetch
        fetch_cvd = os.path.join(extract_path, constants.FETCH_CVD)
        self._build_api.DownloadFetchcvd(
            fetch_cvd, self._avd_spec.fetch_cvd_version, is_arm_flavor)
        # Duplicate fetch_cvd API key when available
        if cfg.service_account_json_private_key_path:
            shutil.copyfile(
                cfg.service_account_json_private_key_path,
                os.path.join(extract_path, constants.FETCH_CVD_CREDENTIAL_SOURCE))

        self._UploadRemoteImageArtifacts(extract_path)

    @utils.TimeExecute(function_description="Downloading Android Build artifact")
    def _DownloadArtifacts(self, extract_path):
        """Download the CF image artifacts and process them.

        - Download images from the Android Build system.
        - Download cvd host package from the Android Build system.

        Args:
            extract_path: String, a path include extracted files.

        Raises:
            errors.GetRemoteImageError: Fails to download rom images.
        """
        cfg = self._avd_spec.cfg

        # Download images with fetch_cvd
        fetch_cvd = os.path.join(extract_path, constants.FETCH_CVD)
        self._build_api.DownloadFetchcvd(
            fetch_cvd, self._avd_spec.fetch_cvd_version)
        fetch_cvd_build_args = self._build_api.GetFetchBuildArgs(
            self._avd_spec.remote_image,
            self._avd_spec.system_build_info,
            self._avd_spec.kernel_build_info,
            self._avd_spec.boot_build_info,
            self._avd_spec.bootloader_build_info,
            self._avd_spec.ota_build_info,
            self._avd_spec.host_package_build_info)
        creds_cache_file = os.path.join(_HOME_FOLDER, cfg.creds_cache_file)
        fetch_cvd_cert_arg = self._build_api.GetFetchCertArg(creds_cache_file)
        fetch_cvd_args = [fetch_cvd, f"-directory={extract_path}",
                          fetch_cvd_cert_arg]
        fetch_cvd_args.extend(fetch_cvd_build_args)
        logger.debug("Download images command: %s", fetch_cvd_args)
        try:
            subprocess.check_call(fetch_cvd_args)
        except subprocess.CalledProcessError as e:
            raise errors.GetRemoteImageError(f"Fails to download images: {e}")

    @utils.TimeExecute(function_description="Uploading remote image artifacts")
    def _UploadRemoteImageArtifacts(self, images_dir):
        """Upload remote image artifacts to instance.

        Args:
            images_dir: String, directory of local artifacts downloaded by
                        fetch_cvd.
        """
        artifact_files = [
            os.path.basename(image)
            for image in glob.glob(os.path.join(images_dir, _ALL_FILES))
        ]
        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)
        # TODO(b/182259589): Refactor upload image command into a function.
        cmd = (f"tar -cf - --lzop -S -C {images_dir} "
               f"{' '.join(artifact_files)} | "
               f"{ssh_cmd} -- "
               f"tar -xf - --lzop -S -C {self._GetArtifactPath()}")
        logger.debug("cmd:\n %s", cmd)
        ssh.ShellCmdWithRetry(cmd)

    @utils.TimeExecute(
        function_description="Launching AVD(s) and waiting for boot up",
        result_evaluator=utils.BootEvaluator)
    def _LaunchCvd(self, image_args, start_time):
        """Execute launch_cvd.

        Args:
            image_args: A list of strings, the extra arguments generated by
                        acloud for remote image paths.
            start_time: The timestamp when the remote host is initialized.

        Returns:
            The error message as a string. An empty string represents success.
        """
        config = cvd_utils.GetConfigFromRemoteAndroidInfo(
            self._ssh, self._GetArtifactPath())
        cmd = cvd_utils.GetRemoteLaunchCvdCmd(
            self._GetInstancePath(), self._avd_spec, config, image_args)
        boot_timeout_secs = (self._avd_spec.boot_timeout_secs or
                             constants.DEFAULT_CF_BOOT_TIMEOUT)
        boot_timeout_secs -= time.time() - start_time
        if boot_timeout_secs <= 0:
            return ("Timed out before launch_cvd. "
                    f"Remaining time: {boot_timeout_secs} secs.")

        self._compute_client.ExtendReportData(
            constants.LAUNCH_CVD_COMMAND, cmd)
        error_msg = cvd_utils.ExecuteRemoteLaunchCvd(
            self._ssh, cmd, boot_timeout_secs)
        self._compute_client.openwrt = not error_msg and self._avd_spec.openwrt
        return error_msg

    def _FindLogFiles(self, instance, download):
        """Find and pull all log files from instance.

        Args:
            instance: String, instance name.
            download: Whether to download the files to a temporary directory
                      and show messages to the user.
        """
        logs = []
        if (self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE and
                self._avd_spec.remote_fetch):
            logs.append(
                cvd_utils.GetRemoteFetcherConfigJson(self._GetArtifactPath()))
        logs.extend(cvd_utils.FindRemoteLogs(
            self._ssh,
            self._GetInstancePath(),
            self._avd_spec.base_instance_num,
            self._avd_spec.num_avds_per_instance))
        self._all_logs[instance] = logs

        if download:
            # To avoid long download time, fetch from the first device only.
            log_files = pull.GetAllLogFilePaths(
                self._ssh, self._GetInstancePath(constants.REMOTE_LOG_FOLDER))
            error_log_folder = pull.PullLogs(self._ssh, log_files, instance)
            self._compute_client.ExtendReportData(constants.ERROR_LOG_FOLDER,
                                                  error_log_folder)

    def GetOpenWrtInfoDict(self):
        """Get openwrt info dictionary.

        Returns:
            A openwrt info dictionary. None for the case is not openwrt device.
        """
        if not self._avd_spec.openwrt:
            return None
        return cvd_utils.GetOpenWrtInfoDict(self._ssh, self._GetInstancePath())

    def GetBuildInfoDict(self):
        """Get build info dictionary.

        Returns:
            A build info dictionary. None for local image case.
        """
        if self._avd_spec.image_source == constants.IMAGE_SRC_LOCAL:
            return None
        return cvd_utils.GetRemoteBuildInfoDict(self._avd_spec)

    def GetAdbPorts(self):
        """Get ADB ports of the created devices.

        Returns:
            The port numbers as a list of integers.
        """
        return cvd_utils.GetAdbPorts(self._avd_spec.base_instance_num,
                                     self._avd_spec.num_avds_per_instance)

    def GetVncPorts(self):
        """Get VNC ports of the created devices.

        Returns:
            The port numbers as a list of integers.
        """
        return cvd_utils.GetVncPorts(self._avd_spec.base_instance_num,
                                     self._avd_spec.num_avds_per_instance)

    def GetFailures(self):
        """Get failures from all devices.

        Returns:
            A dictionary that contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is a string or an errors.DeviceBootError object.
        """
        return self._all_failures

    def GetLogs(self):
        """Get all device logs.

        Returns:
            A dictionary that maps instance names to lists of report.LogFile.
        """
        return self._all_logs

    def GetFetchCvdWrapperLogIfExist(self):
        """Get FetchCvdWrapper log if exist.

        Returns:
            A dictionary that includes FetchCvdWrapper logs.
        """
        if not self._avd_spec.fetch_cvd_wrapper:
            return {}
        path = os.path.join(self._GetArtifactPath(), "fetch_cvd_wrapper_log.json")
        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN) + " cat " + path
        proc = subprocess.run(ssh_cmd, shell=True, capture_output=True,
                              check=False)
        if proc.stderr:
            logger.debug("`%s` stderr: %s", ssh_cmd, proc.stderr.decode())
        if proc.stdout:
            try:
                return json.loads(proc.stdout)
            except ValueError as e:
                return {"status": "FETCH_WRAPPER_REPORT_PARSE_ERROR"}
        return {}
