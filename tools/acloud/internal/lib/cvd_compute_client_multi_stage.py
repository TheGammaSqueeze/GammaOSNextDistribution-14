# Copyright 2019 - The Android Open Source Project
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
"""A client that manages Cuttlefish Virtual Device on compute engine.

** CvdComputeClient **

CvdComputeClient derives from AndroidComputeClient. It manges a google
compute engine project that is setup for running Cuttlefish Virtual Devices.
It knows how to create a host instance from Cuttlefish Stable Host Image, fetch
Android build, and start Android within the host instance.

** Class hierarchy **

  base_cloud_client.BaseCloudApiClient
                ^
                |
       gcompute_client.ComputeClient
                ^
                |
       android_compute_client.AndroidComputeClient
                ^
                |
       cvd_compute_client_multi_stage.CvdComputeClient

"""

import logging
import os
import subprocess
import tempfile
import time

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import android_compute_client
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.setup import mkcert


logger = logging.getLogger(__name__)

_DEFAULT_WEBRTC_DEVICE_ID = "cvd-1"
_FETCHER_NAME = "fetch_cvd"
_TRUST_REMOTE_INSTANCE_COMMAND = (
    f"\"sudo cp -p ~/{constants.WEBRTC_CERTS_PATH}/{constants.SSL_CA_NAME}.pem "
    f"{constants.SSL_TRUST_CA_DIR}/{constants.SSL_CA_NAME}.crt;"
    "sudo update-ca-certificates;\"")


class CvdComputeClient(android_compute_client.AndroidComputeClient):
    """Client that manages Android Virtual Device."""

    DATA_POLICY_CREATE_IF_MISSING = "create_if_missing"
    # Data policy to customize disk size.
    DATA_POLICY_ALWAYS_CREATE = "always_create"

    def __init__(self,
                 acloud_config,
                 oauth2_credentials,
                 ins_timeout_secs=None,
                 report_internal_ip=None,
                 gpu=None):
        """Initialize.

        Args:
            acloud_config: An AcloudConfig object.
            oauth2_credentials: An oauth2client.OAuth2Credentials instance.
            ins_timeout_secs: Integer, the maximum time to wait for the
                              instance ready.
            report_internal_ip: Boolean to report the internal ip instead of
                                external ip.
            gpu: String, GPU to attach to the device.
        """
        super().__init__(acloud_config, oauth2_credentials)

        self._build_api = (
            android_build_client.AndroidBuildClient(oauth2_credentials))
        self._ssh_private_key_path = acloud_config.ssh_private_key_path
        self._ins_timeout_secs = ins_timeout_secs
        self._report_internal_ip = report_internal_ip
        self._gpu = gpu
        # Store all failures result when creating one or multiple instances.
        # This attribute is only used by the deprecated create_cf command.
        self._all_failures = {}
        self._extra_args_ssh_tunnel = acloud_config.extra_args_ssh_tunnel
        self._ssh = None
        self._ip = None
        self._user = constants.GCE_USER
        self._openwrt = None
        self._stage = constants.STAGE_INIT
        self._execution_time = {constants.TIME_ARTIFACT: 0,
                                constants.TIME_GCE: 0,
                                constants.TIME_LAUNCH: 0}

    # pylint: disable=arguments-differ,broad-except
    def CreateInstance(self, instance, image_name, image_project,
                       avd_spec, extra_scopes=None):
        """Create/Reuse a GCE instance.

        Args:
            instance: instance name.
            image_name: A string, the name of the GCE image.
            image_project: A string, name of the project where the image lives.
                           Assume the default project if None.
            avd_spec: An AVDSpec instance.
            extra_scopes: A list of extra scopes to be passed to the instance.

        Returns:
            A string, representing instance name.
        """
        # A blank data disk would be created on the host. Make sure the size of
        # the boot disk is large enough to hold it.
        boot_disk_size_gb = (
            int(self.GetImage(image_name, image_project)["diskSizeGb"]) +
            avd_spec.cfg.extra_data_disk_size_gb)

        if avd_spec.instance_name_to_reuse:
            self._ip = self._ReusingGceInstance(avd_spec)
        else:
            self._VerifyZoneByQuota()
            self._ip = self._CreateGceInstance(instance, image_name, image_project,
                                               extra_scopes, boot_disk_size_gb,
                                               avd_spec)
        if avd_spec.connect_hostname:
            self._gce_hostname = gcompute_client.GetGCEHostName(
                self._project, instance, self._zone)
        self._ssh = Ssh(ip=self._ip,
                        user=constants.GCE_USER,
                        ssh_private_key_path=self._ssh_private_key_path,
                        extra_args_ssh_tunnel=self._extra_args_ssh_tunnel,
                        report_internal_ip=self._report_internal_ip,
                        gce_hostname=self._gce_hostname)
        try:
            self.SetStage(constants.STAGE_SSH_CONNECT)
            self._ssh.WaitForSsh(timeout=self._ins_timeout_secs)
            if avd_spec.instance_name_to_reuse:
                cvd_utils.CleanUpRemoteCvd(self._ssh, cvd_utils.GCE_BASE_DIR,
                                           raise_error=False)
        except Exception as e:
            self._all_failures[instance] = e
        return instance

    def _GetGCEHostName(self, instance):
        """Get the GCE host name with specific rule.

        Args:
            instance: Sting, instance name.

        Returns:
            One host name coverted by instance name, project name, and zone.
        """
        if ":" in self._project:
            domain = self._project.split(":")[0]
            project_no_domain = self._project.split(":")[1]
            project = f"{project_no_domain}.{domain}"
            return f"nic0.{instance}.{self._zone}.c.{project}.internal.gcpnode.com"
        return f"nic0.{instance}.{self._zone}.c.{self._project}.internal.gcpnode.com"

    @utils.TimeExecute(function_description="Launching AVD(s) and waiting for boot up",
                       result_evaluator=utils.BootEvaluator)
    def LaunchCvd(self, instance, avd_spec, base_dir, extra_args):
        """Launch CVD.

        Launch AVD with launch_cvd. If the process is failed, acloud would show
        error messages and auto download log files from remote instance.

        Args:
            instance: String, instance name.
            avd_spec: An AVDSpec instance.
            base_dir: The remote directory containing the images and tools.
            extra_args: Collection of strings, the extra arguments generated by
                        acloud. e.g., remote image paths.

        Returns:
           dict of faliures, return this dict for BootEvaluator to handle
           LaunchCvd success or fail messages.
        """
        self.SetStage(constants.STAGE_BOOT_UP)
        timestart = time.time()
        config = cvd_utils.GetConfigFromRemoteAndroidInfo(self._ssh, base_dir)
        cmd = cvd_utils.GetRemoteLaunchCvdCmd(
            base_dir, avd_spec, config, extra_args)
        boot_timeout_secs = self._GetBootTimeout(
            avd_spec.boot_timeout_secs or constants.DEFAULT_CF_BOOT_TIMEOUT)

        self.ExtendReportData(constants.LAUNCH_CVD_COMMAND, cmd)
        error_msg = cvd_utils.ExecuteRemoteLaunchCvd(
            self._ssh, cmd, boot_timeout_secs)
        self._execution_time[constants.TIME_LAUNCH] = time.time() - timestart

        if error_msg:
            return {instance: error_msg}
        self._openwrt = avd_spec.openwrt
        return {}

    def _GetBootTimeout(self, timeout_secs):
        """Get boot timeout.

        Timeout settings includes download artifacts and boot up.

        Args:
            timeout_secs: integer of timeout value.

        Returns:
            The timeout values for device boots up.
        """
        boot_timeout_secs = timeout_secs - self._execution_time[constants.TIME_ARTIFACT]
        logger.debug("Timeout for boot: %s secs", boot_timeout_secs)
        return boot_timeout_secs

    @utils.TimeExecute(function_description="Reusing GCE instance")
    def _ReusingGceInstance(self, avd_spec):
        """Reusing a cuttlefish existing instance.

        Args:
            avd_spec: An AVDSpec instance.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        gcompute_client.ComputeClient.AddSshRsaInstanceMetadata(
            self, constants.GCE_USER, avd_spec.cfg.ssh_public_key_path,
            avd_spec.instance_name_to_reuse)
        ip = gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=avd_spec.instance_name_to_reuse, zone=self._zone)

        return ip

    @utils.TimeExecute(function_description="Creating GCE instance")
    def _CreateGceInstance(self, instance, image_name, image_project,
                           extra_scopes, boot_disk_size_gb, avd_spec):
        """Create a single configured cuttlefish device.

        Override method from parent class.
        Args:
            instance: String, instance name.
            image_name: String, the name of the GCE image.
            image_project: String, the name of the project where the image.
            extra_scopes: A list of extra scopes to be passed to the instance.
            boot_disk_size_gb: Integer, size of the boot disk in GB.
            avd_spec: An AVDSpec instance.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        self.SetStage(constants.STAGE_GCE)
        timestart = time.time()
        metadata = self._metadata.copy()

        if avd_spec:
            metadata[constants.INS_KEY_AVD_TYPE] = avd_spec.avd_type
            metadata[constants.INS_KEY_AVD_FLAVOR] = avd_spec.flavor
            metadata[constants.INS_KEY_WEBRTC_DEVICE_ID] = (
                avd_spec.webrtc_device_id or _DEFAULT_WEBRTC_DEVICE_ID)
            metadata[constants.INS_KEY_DISPLAY] = ("%sx%s (%s)" % (
                avd_spec.hw_property[constants.HW_X_RES],
                avd_spec.hw_property[constants.HW_Y_RES],
                avd_spec.hw_property[constants.HW_ALIAS_DPI]))
            if avd_spec.gce_metadata:
                for key, value in avd_spec.gce_metadata.items():
                    metadata[key] = value
            # Record webrtc port, it will be removed if cvd support to show it.
            if avd_spec.connect_webrtc:
                metadata[constants.INS_KEY_WEBRTC_PORT] = constants.WEBRTC_LOCAL_PORT

        disk_args = self._GetDiskArgs(
            instance, image_name, image_project, boot_disk_size_gb)
        disable_external_ip = avd_spec.disable_external_ip if avd_spec else False
        gcompute_client.ComputeClient.CreateInstance(
            self,
            instance=instance,
            image_name=image_name,
            image_project=image_project,
            disk_args=disk_args,
            metadata=metadata,
            machine_type=self._machine_type,
            network=self._network,
            zone=self._zone,
            gpu=self._gpu,
            disk_type=avd_spec.disk_type if avd_spec else None,
            extra_scopes=extra_scopes,
            disable_external_ip=disable_external_ip)
        ip = gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=instance, zone=self._zone)
        logger.debug("'instance_ip': %s", ip.internal
                     if self._report_internal_ip else ip.external)

        self._execution_time[constants.TIME_GCE] = time.time() - timestart
        return ip

    @utils.TimeExecute(function_description="Uploading build fetcher to instance")
    def UpdateFetchCvd(self, fetch_cvd_version):
        """Download fetch_cvd from the Build API, and upload it to a remote instance.

        The version of fetch_cvd to use is retrieved from the configuration file. Once fetch_cvd
        is on the instance, future commands can use it to download relevant Cuttlefish files from
        the Build API on the instance itself.

        Args:
            fetch_cvd_version: String. The build id of fetch_cvd.
        """
        self.SetStage(constants.STAGE_ARTIFACT)
        download_dir = tempfile.mkdtemp()
        download_target = os.path.join(download_dir, _FETCHER_NAME)
        self._build_api.DownloadFetchcvd(download_target, fetch_cvd_version)
        self._ssh.ScpPushFile(src_file=download_target, dst_file=_FETCHER_NAME)
        os.remove(download_target)
        os.rmdir(download_dir)

    @utils.TimeExecute(function_description="Downloading build on instance")
    def FetchBuild(self, default_build_info, system_build_info,
                   kernel_build_info, boot_build_info, bootloader_build_info,
                   ota_build_info, host_package_build_info):
        """Execute fetch_cvd on the remote instance to get Cuttlefish runtime files.

        Args:
            default_build_info: The build that provides full cuttlefish images.
            system_build_info: The build that provides the system image.
            kernel_build_info: The build that provides the kernel.
            boot_build_info: The build that provides the boot image.
            bootloader_build_info: The build that provides the bootloader.
            ota_build_info: The build that provides the OTA tools.
            host_package_build_info: The build that provides the host package.

        Returns:
            List of string args for fetch_cvd.
        """
        timestart = time.time()
        fetch_cvd_args = ["-credential_source=gce"]
        fetch_cvd_build_args = self._build_api.GetFetchBuildArgs(
            default_build_info, system_build_info, kernel_build_info,
            boot_build_info, bootloader_build_info, ota_build_info,
            host_package_build_info)
        fetch_cvd_args.extend(fetch_cvd_build_args)

        self._ssh.Run("./fetch_cvd " + " ".join(fetch_cvd_args),
                      timeout=constants.DEFAULT_SSH_TIMEOUT)
        self._execution_time[constants.TIME_ARTIFACT] = time.time() - timestart

    @utils.TimeExecute(function_description="Update instance's certificates")
    def UpdateCertificate(self):
        """Update webrtc default certificates of the remote instance.

        For trusting both the localhost and remote instance, the process will
        upload certificates(rootCA.pem, server.crt, server.key) and the mkcert
        tool from the client workstation to remote instance where running the
        mkcert with the uploaded rootCA file and replace the webrtc frontend
        default certificates for connecting to a remote webrtc AVD without the
        insecure warning.
        """
        local_cert_dir = os.path.join(os.path.expanduser("~"),
                                      constants.SSL_DIR)
        if mkcert.AllocateLocalHostCert():
            upload_files = []
            for cert_file in (constants.WEBRTC_CERTS_FILES +
                              [f"{constants.SSL_CA_NAME}.pem"]):
                upload_files.append(os.path.join(local_cert_dir,
                                                 cert_file))
            try:
                self._ssh.ScpPushFiles(upload_files, constants.WEBRTC_CERTS_PATH)
                self._ssh.Run(_TRUST_REMOTE_INSTANCE_COMMAND)
            except subprocess.CalledProcessError:
                logger.debug("Update WebRTC frontend certificate failed.")

    @utils.TimeExecute(function_description="Upload extra files to instance")
    def UploadExtraFiles(self, extra_files):
        """Upload extra files into GCE instance.

        Args:
            extra_files: List of namedtuple ExtraFile.

        Raises:
            errors.CheckPathError: The provided path doesn't exist.
        """
        for extra_file in extra_files:
            if not os.path.exists(extra_file.source):
                raise errors.CheckPathError(
                    f"The path doesn't exist: {extra_file.source}")
            self._ssh.ScpPushFile(extra_file.source, extra_file.target)

    def GetSshConnectCmd(self):
        """Get ssh connect command.

        Returns:
            String of ssh connect command.
        """
        return self._ssh.GetBaseCmd(constants.SSH_BIN)

    def GetInstanceIP(self, instance=None):
        """Override method from parent class.

        It need to get the IP address in the common_operation. If the class
        already defind the ip address, return the ip address.

        Args:
            instance: String, representing instance name.

        Returns:
            ssh.IP object, that stores internal and external ip of the instance.
        """
        if self._ip:
            return self._ip
        return gcompute_client.ComputeClient.GetInstanceIP(
            self, instance=instance, zone=self._zone)

    def GetHostImageName(self, stable_image_name, image_family, image_project):
        """Get host image name.

        Args:
            stable_image_name: String of stable host image name.
            image_family: String of image family.
            image_project: String of image project.

        Returns:
            String of stable host image name.

        Raises:
            errors.ConfigError: There is no host image name in config file.
        """
        if stable_image_name:
            return stable_image_name

        if image_family:
            image_name = gcompute_client.ComputeClient.GetImageFromFamily(
                self, image_family, image_project)["name"]
            logger.debug("Get the host image name from image family: %s", image_name)
            return image_name

        raise errors.ConfigError(
            "Please specify 'stable_host_image_name' or 'stable_host_image_family'"
            " in config.")

    def SetStage(self, stage):
        """Set stage to know the create progress.

        Args:
            stage: Integer, the stage would like STAGE_INIT, STAGE_GCE.
        """
        self._stage = stage

    @property
    def all_failures(self):
        """Return all_failures"""
        return self._all_failures

    @property
    def execution_time(self):
        """Return execution_time"""
        return self._execution_time

    @property
    def stage(self):
        """Return stage"""
        return self._stage

    @property
    def openwrt(self):
        """Return openwrt"""
        return self._openwrt

    @property
    def build_api(self):
        """Return build_api"""
        return self._build_api
