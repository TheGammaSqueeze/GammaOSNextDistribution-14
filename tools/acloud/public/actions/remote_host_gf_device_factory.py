# Copyright 2021 - The Android Open Source Project
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

"""RemoteInstanceDeviceFactory provides basic interface to create a goldfish
device factory."""

import collections
import logging
import os
import posixpath as remote_path
import re
import shutil
import subprocess
import tempfile
import time
import zipfile

from acloud import errors
from acloud.create import create_common
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import goldfish_utils
from acloud.internal.lib import emulator_console
from acloud.internal.lib import ota_tools
from acloud.internal.lib import remote_host_client
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public import report
from acloud.public.actions import base_device_factory


logger = logging.getLogger(__name__)
# Artifacts
_SDK_REPO_IMAGE_ZIP_NAME_FORMAT = ("sdk-repo-linux-system-images-"
                                   "%(build_id)s.zip")
_EXTRA_IMAGE_ZIP_NAME_FORMAT = "emu-extra-linux-system-images-%(build_id)s.zip"
_IMAGE_ZIP_NAME_FORMAT = "%(build_target)s-img-%(build_id)s.zip"
_OTA_TOOLS_ZIP_NAME = "otatools.zip"
_EMULATOR_INFO_NAME = "emulator-info.txt"
_EMULATOR_VERSION_PATTERN = re.compile(r"require\s+version-emulator="
                                       r"(?P<build_id>\w+)")
_EMULATOR_ZIP_NAME_FORMAT = "sdk-repo-%(os)s-emulator-%(build_id)s.zip"
_EMULATOR_BIN_DIR_NAMES = ("bin64", "qemu")
_EMULATOR_BIN_NAME = "emulator"
_SDK_REPO_EMULATOR_DIR_NAME = "emulator"
# Files in temporary artifact directory.
_DOWNLOAD_DIR_NAME = "download"
_OTA_TOOLS_DIR_NAME = "ota_tools"
_SYSTEM_IMAGE_NAME = "system.img"
# Base directory of an instance.
_REMOTE_INSTANCE_DIR_FORMAT = "acloud_gf_%d"
# Relative paths in a base directory.
_REMOTE_IMAGE_ZIP_PATH = "image.zip"
_REMOTE_EMULATOR_ZIP_PATH = "emulator.zip"
_REMOTE_IMAGE_DIR = "image"
_REMOTE_KERNEL_PATH = "kernel"
_REMOTE_RAMDISK_PATH = "mixed_ramdisk"
_REMOTE_EMULATOR_DIR = "emulator"
_REMOTE_RUNTIME_DIR = "instance"
_REMOTE_LOGCAT_PATH = remote_path.join(_REMOTE_RUNTIME_DIR, "logcat.txt")
_REMOTE_STDOUT_PATH = remote_path.join(_REMOTE_RUNTIME_DIR, "kernel.log")
_REMOTE_STDERR_PATH = remote_path.join(_REMOTE_RUNTIME_DIR, "emu_stderr.txt")
# Runtime parameters
_EMULATOR_DEFAULT_CONSOLE_PORT = 5554
_DEFAULT_BOOT_TIMEOUT_SECS = 150
# Error messages
_MISSING_EMULATOR_MSG = ("No emulator zip. Specify "
                         "--emulator-build-id, or --emulator-zip.")

ArtifactPaths = collections.namedtuple(
    "ArtifactPaths",
    ["image_zip", "emulator_zip", "ota_tools_dir",
     "system_image", "system_dlkm_image", "boot_image"])

RemotePaths = collections.namedtuple(
    "RemotePaths",
    ["image_dir", "emulator_dir", "kernel", "ramdisk"])


class RemoteHostGoldfishDeviceFactory(base_device_factory.BaseDeviceFactory):
    """A class that creates a goldfish device on a remote host.

    Attributes:
        avd_spec: AVDSpec object that tells us what we're going to create.
        android_build_client: An AndroidBuildClient that is lazily initialized.
        temp_artifact_dir: The temporary artifact directory that is lazily
                           initialized during PrepareArtifacts.
        ssh: Ssh object that executes commands on the remote host.
        failures: A dictionary the maps instance names to
                  error.DeviceBootError objects.
        logs: A dictionary that maps instance names to lists of report.LogFile.
    """
    def __init__(self, avd_spec):
        """Initialize the attributes and the compute client."""
        self._avd_spec = avd_spec
        self._android_build_client = None
        self._temp_artifact_dir = None
        self._ssh = ssh.Ssh(
            ip=ssh.IP(ip=self._avd_spec.remote_host),
            user=self._ssh_user,
            ssh_private_key_path=self._ssh_private_key_path,
            extra_args_ssh_tunnel=self._ssh_extra_args,
            report_internal_ip=False)
        self._failures = {}
        self._logs = {}
        super().__init__(compute_client=(
            remote_host_client.RemoteHostClient(avd_spec.remote_host)))

    @property
    def _build_api(self):
        """Initialize android_build_client."""
        if not self._android_build_client:
            credentials = auth.CreateCredentials(self._avd_spec.cfg)
            self._android_build_client = android_build_client.AndroidBuildClient(
                credentials)
        return self._android_build_client

    @property
    def _artifact_dir(self):
        """Initialize temp_artifact_dir."""
        if not self._temp_artifact_dir:
            self._temp_artifact_dir = tempfile.mkdtemp("host_gf")
            logger.info("Create temporary artifact directory: %s",
                        self._temp_artifact_dir)
        return self._temp_artifact_dir

    @property
    def _download_dir(self):
        """Get the directory where the artifacts are downloaded."""
        if self._avd_spec.image_download_dir:
            return self._avd_spec.image_download_dir
        return os.path.join(self._artifact_dir, _DOWNLOAD_DIR_NAME)

    @property
    def _ssh_user(self):
        return self._avd_spec.host_user or constants.GCE_USER

    @property
    def _ssh_private_key_path(self):
        return (self._avd_spec.host_ssh_private_key_path or
                self._avd_spec.cfg.ssh_private_key_path)

    @property
    def _ssh_extra_args(self):
        return self._avd_spec.cfg.extra_args_ssh_tunnel

    def _GetConsolePort(self):
        """Calculate the console port from the instance number.

        By convention, the console port is an even number, and the adb port is
        the console port + 1. The first instance uses port 5554 and 5555. The
        second instance uses 5556 and 5557, and so on.
        """
        return (_EMULATOR_DEFAULT_CONSOLE_PORT +
                ((self._avd_spec.base_instance_num or 1) - 1) * 2)

    def _GetInstancePath(self, relative_path):
        """Append a relative path to the instance directory."""
        return remote_path.join(
            _REMOTE_INSTANCE_DIR_FORMAT %
            (self._avd_spec.base_instance_num or 1),
            relative_path)

    def CreateInstance(self):
        """Create a goldfish instance on the remote host.

        Returns:
            The instance name.
        """
        instance_name = goldfish_utils.FormatRemoteHostInstanceName(
            self._avd_spec.remote_host,
            self._GetConsolePort(),
            self._avd_spec.remote_image)

        client = self.GetComputeClient()
        timed_stage = constants.TIME_GCE
        start_time = time.time()
        try:
            client.SetStage(constants.STAGE_SSH_CONNECT)
            self._InitRemoteHost()

            start_time = client.RecordTime(timed_stage, start_time)
            timed_stage = constants.TIME_ARTIFACT
            client.SetStage(constants.STAGE_ARTIFACT)
            remote_paths = self._PrepareArtifacts()

            start_time = client.RecordTime(timed_stage, start_time)
            timed_stage = constants.TIME_LAUNCH
            client.SetStage(constants.STAGE_BOOT_UP)
            self._logs[instance_name] = self._GetEmulatorLogs()
            self._StartEmulator(remote_paths)
            self._WaitForEmulator()
        except (errors.DriverError, subprocess.CalledProcessError) as e:
            # Catch the generic runtime error and CalledProcessError which is
            # raised by the ssh module.
            self._failures[instance_name] = e
        finally:
            client.RecordTime(timed_stage, start_time)

        return instance_name

    def _InitRemoteHost(self):
        """Remove the existing instance and the instance directory."""
        # Disable authentication for emulator console.
        self._ssh.Run("""'echo -n "" > .emulator_console_auth_token'""")
        try:
            with emulator_console.RemoteEmulatorConsole(
                    self._avd_spec.remote_host,
                    self._GetConsolePort(),
                    self._ssh_user,
                    self._ssh_private_key_path,
                    self._ssh_extra_args) as console:
                console.Kill()
            logger.info("Killed existing emulator.")
        except errors.DeviceConnectionError as e:
            logger.info("Did not kill existing emulator: %s", str(e))
        # Delete instance files.
        self._ssh.Run(f"rm -rf {self._GetInstancePath('')}")

    def _PrepareArtifacts(self):
        """Prepare artifacts on remote host.

        This method retrieves artifacts from cache or Android Build API and
        uploads them to the remote host.

        Returns:
            An object of RemotePaths.
        """
        try:
            artifact_paths = self._RetrieveArtifacts()
            return self._UploadArtifacts(artifact_paths)
        finally:
            if self._temp_artifact_dir:
                shutil.rmtree(self._temp_artifact_dir, ignore_errors=True)
                self._temp_artifact_dir = None

    @staticmethod
    def _InferEmulatorZipName(build_target, build_id):
        """Determine the emulator zip name in build artifacts.

        The emulator zip name is composed of build variables that are not
        revealed in the artifacts. This method infers the emulator zip name
        from its build target name.

        Args:
            build_target: The emulator build target name, e.g.,
                          "emulator-linux_x64_nolocationui", "aarch64_sdk_tools_mac".
            build_id: A string, the emulator build ID.

        Returns:
            The name of the emulator zip. e.g.,
            "sdk-repo-linux-emulator-123456.zip",
            "sdk-repo-darwin_aarch64-emulator-123456.zip".
        """
        split_target = [x for product_variant in build_target.split("-")
                        for x in product_variant.split("_")]
        if "darwin" in split_target or "mac" in split_target:
            os_name = "darwin"
        else:
            os_name = "linux"
        if "aarch64" in split_target:
            os_name = os_name + "_aarch64"
        return _EMULATOR_ZIP_NAME_FORMAT % {"os": os_name,
                                            "build_id": build_id}

    def _RetrieveArtifact(self, build_target, build_id,
                          resource_id):
        """Retrieve an artifact from cache or Android Build API.

        Args:
            build_target: A string, the build target of the artifact. e.g.,
                          "sdk_phone_x86_64-userdebug".
            build_id: A string, the build ID of the artifact.
            resource_id: A string, the name of the artifact. e.g.,
                         "sdk-repo-linux-system-images-123456.zip".

        Returns:
            The path to the artifact in download_dir.
        """
        local_path = os.path.join(self._download_dir, build_id, build_target,
                                  resource_id)
        if os.path.isfile(local_path):
            logger.info("Skip downloading existing artifact: %s", local_path)
            return local_path

        complete = False
        try:
            os.makedirs(os.path.dirname(local_path), exist_ok=True)
            self._build_api.DownloadArtifact(
                build_target, build_id, resource_id, local_path,
                self._build_api.LATEST)
            complete = True
        finally:
            if not complete and os.path.isfile(local_path):
                os.remove(local_path)
        return local_path

    @utils.TimeExecute(function_description="Download Android Build artifacts")
    def _RetrieveArtifacts(self):
        """Retrieve goldfish images and tools from cache or Android Build API.

        Returns:
            An object of ArtifactPaths.

        Raises:
            errors.GetRemoteImageError: Fails to download rom images.
            errors.GetLocalImageError: Fails to validate local image zip.
            errors.GetSdkRepoPackageError: Fails to retrieve emulator zip.
        """
        # Device images.
        if self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE:
            image_zip_path = self._RetrieveDeviceImageZip()
        elif self._avd_spec.image_source == constants.IMAGE_SRC_LOCAL:
            image_zip_path = self._avd_spec.local_image_artifact
            if not image_zip_path or not zipfile.is_zipfile(image_zip_path):
                raise errors.GetLocalImageError(
                    f"{image_zip_path or self._avd_spec.local_image_dir} is "
                    "not an SDK repository zip.")
        else:
            raise errors.CreateError(
                f"Unknown image source: {self._avd_spec.image_source}")

        # Emulator tools.
        emu_zip_path = (self._avd_spec.emulator_zip or
                        self._RetrieveEmulatorZip())
        if not emu_zip_path:
            raise errors.GetSdkRepoPackageError(_MISSING_EMULATOR_MSG)

        # System image.
        if self._avd_spec.local_system_image:
            # No known use case requires replacing system_ext and product.
            system_image_path = create_common.FindSystemImages(
                self._avd_spec.local_system_image).system
        else:
            system_image_path = self._RetrieveSystemImage()

        # system_dlkm image.
        if self._avd_spec.local_system_dlkm_image:
            system_dlkm_image_path = goldfish_utils.FindSystemDlkmImage(
                self._avd_spec.local_system_dlkm_image)
        else:
            # No known use case requires remote system_dlkm.
            system_dlkm_image_path = None

        # Boot image.
        if self._avd_spec.local_kernel_image:
            boot_image_path = create_common.FindBootImage(
                self._avd_spec.local_kernel_image)
        else:
            boot_image_path = self._RetrieveBootImage()

        # OTA tools.
        ota_tools_dir = None
        if system_image_path or system_dlkm_image_path or boot_image_path:
            if self._avd_spec.image_source == constants.IMAGE_SRC_REMOTE:
                ota_tools_dir = self._RetrieveOtaTools()
            else:
                ota_tools_dir = ota_tools.FindOtaToolsDir(
                    self._avd_spec.local_tool_dirs +
                    create_common.GetNonEmptyEnvVars(
                        constants.ENV_ANDROID_SOONG_HOST_OUT,
                        constants.ENV_ANDROID_HOST_OUT))

        return ArtifactPaths(image_zip_path, emu_zip_path, ota_tools_dir,
                             system_image_path, system_dlkm_image_path,
                             boot_image_path)

    def _RetrieveDeviceImageZip(self):
        """Retrieve device image zip from cache or Android Build API.

        Returns:
            The path to the device image zip in download_dir.
        """
        build_id = self._avd_spec.remote_image.get(constants.BUILD_ID)
        build_target = self._avd_spec.remote_image.get(constants.BUILD_TARGET)
        image_zip_name_format = (_EXTRA_IMAGE_ZIP_NAME_FORMAT if
                                 self._ShouldMixDiskImage() else
                                 _SDK_REPO_IMAGE_ZIP_NAME_FORMAT)
        return self._RetrieveArtifact(
            build_target, build_id,
            image_zip_name_format % {"build_id": build_id})

    def _RetrieveEmulatorBuildID(self):
        """Retrieve required emulator build from a goldfish image build.

        Returns:
            A string, the emulator build ID.
            None if the build info is empty.
        """
        build_id = self._avd_spec.remote_image.get(constants.BUILD_ID)
        build_target = self._avd_spec.remote_image.get(constants.BUILD_TARGET)
        if build_id and build_target:
            emu_info_path = self._RetrieveArtifact(build_target, build_id,
                                                   _EMULATOR_INFO_NAME)
            with open(emu_info_path, "r", encoding="utf-8") as emu_info:
                for line in emu_info:
                    match = _EMULATOR_VERSION_PATTERN.fullmatch(line.strip())
                    if match:
                        logger.info("Found emulator build ID: %s", line)
                        return match.group("build_id")
        return None

    def _RetrieveEmulatorZip(self):
        """Retrieve emulator zip from cache or Android Build API.

        Returns:
            The path to the emulator zip in download_dir.
            None if this method cannot determine the emulator build ID.
        """
        emu_build_id = (self._avd_spec.emulator_build_id or
                        self._RetrieveEmulatorBuildID())
        if not emu_build_id:
            return None
        emu_build_target = (self._avd_spec.emulator_build_target or
                            self._avd_spec.cfg.emulator_build_target)
        emu_zip_name = self._InferEmulatorZipName(emu_build_target,
                                                  emu_build_id)
        return self._RetrieveArtifact(emu_build_target, emu_build_id,
                                      emu_zip_name)

    def _RetrieveSystemImage(self):
        """Retrieve and unzip system image if system build info is not empty.

        Returns:
            The path to the temporary system image.
            None if the system build info is empty.
        """
        build_id = self._avd_spec.system_build_info.get(constants.BUILD_ID)
        build_target = self._avd_spec.system_build_info.get(
            constants.BUILD_TARGET)
        if not build_id or not build_target:
            return None
        image_zip_name = _IMAGE_ZIP_NAME_FORMAT % {
            "build_target": build_target.split("-", 1)[0],
            "build_id": build_id}
        image_zip_path = self._RetrieveArtifact(build_target, build_id,
                                                image_zip_name)
        logger.debug("Unzip %s from %s to %s.",
                     _SYSTEM_IMAGE_NAME, image_zip_path, self._artifact_dir)
        with zipfile.ZipFile(image_zip_path, "r") as zip_file:
            zip_file.extract(_SYSTEM_IMAGE_NAME, self._artifact_dir)
        return os.path.join(self._artifact_dir, _SYSTEM_IMAGE_NAME)

    def _RetrieveBootImage(self):
        """Retrieve boot image if boot build info is not empty.

        Returns:
            The path to the boot image in download_dir.
            None if the boot build info is empty.
        """
        build_id = self._avd_spec.boot_build_info.get(constants.BUILD_ID)
        build_target = self._avd_spec.boot_build_info.get(
            constants.BUILD_TARGET)
        image_name = self._avd_spec.boot_build_info.get(
            constants.BUILD_ARTIFACT)
        if build_id and build_target and image_name:
            return self._RetrieveArtifact(build_target, build_id, image_name)
        return None

    def _RetrieveOtaTools(self):
        """Retrieve and unzip OTA tools.

        This method retrieves OTA tools from the goldfish build which contains
        mk_combined_img.

        Returns:
            The path to the temporary OTA tools directory.
        """
        build_id = self._avd_spec.remote_image.get(constants.BUILD_ID)
        build_target = self._avd_spec.remote_image.get(constants.BUILD_TARGET)
        zip_path = self._RetrieveArtifact(build_target, build_id,
                                          _OTA_TOOLS_ZIP_NAME)
        ota_tools_dir = os.path.join(self._artifact_dir, _OTA_TOOLS_DIR_NAME)
        logger.debug("Unzip %s to %s.", zip_path, ota_tools_dir)
        os.mkdir(ota_tools_dir)
        with zipfile.ZipFile(zip_path, "r") as zip_file:
            zip_file.extractall(ota_tools_dir)
        return ota_tools_dir

    @staticmethod
    def _GetSubdirNameInZip(zip_path):
        """Get the name of the only subdirectory in a zip.

        In an SDK repository zip, the images and the binaries are located in a
        subdirectory. This class needs to find out the subdirectory name in
        order to construct the remote commands.

        For example, in a sdk-repo-linux-system-images-*.zip for arm64, all
        files are in "arm64-v8a/". The zip entries are:

        arm64-v8a/NOTICE.txt
        arm64-v8a/system.img
        arm64-v8a/data/local.prop
        ...

        This method scans the entries and returns the common subdirectory name.
        """
        sep = "/"
        with zipfile.ZipFile(zip_path, 'r') as zip_obj:
            entries = zip_obj.namelist()
            if len(entries) > 0 and sep in entries[0]:
                subdir = entries[0].split(sep, 1)[0]
                if all(e.startswith(subdir + sep) for e in entries):
                    return subdir
            logger.warning("Expect one subdirectory in %s. Actual entries: %s",
                           zip_path, " ".join(entries))
            return ""

    def _UploadArtifacts(self, artifact_paths):
        """Process and upload all images and tools to the remote host.

        Args:
            artifact_paths: An object of ArtifactPaths.

        Returns:
            An object of RemotePaths.
        """
        remote_emulator_dir, remote_image_dir = self._UploadDeviceImages(
            artifact_paths.emulator_zip, artifact_paths.image_zip)

        remote_kernel_path = None
        remote_ramdisk_path = None

        if (artifact_paths.boot_image or artifact_paths.system_image or
                artifact_paths.system_dlkm_image):
            with tempfile.TemporaryDirectory("host_gf") as temp_dir:
                ota = ota_tools.OtaTools(artifact_paths.ota_tools_dir)

                image_dir = os.path.join(temp_dir, "images")
                logger.debug("Unzip %s.", artifact_paths.image_zip)
                with zipfile.ZipFile(artifact_paths.image_zip,
                                     "r") as zip_file:
                    zip_file.extractall(image_dir)
                image_dir = os.path.join(
                    image_dir,
                    self._GetSubdirNameInZip(artifact_paths.image_zip))

                if (artifact_paths.system_image or
                        artifact_paths.system_dlkm_image):
                    self._MixAndUploadDiskImage(
                        remote_image_dir, image_dir,
                        artifact_paths.system_image,
                        artifact_paths.system_dlkm_image, ota)

                if artifact_paths.boot_image:
                    remote_kernel_path, remote_ramdisk_path = (
                        self._MixAndUploadKernelImages(
                            image_dir, artifact_paths.boot_image, ota))

        return RemotePaths(remote_image_dir, remote_emulator_dir,
                           remote_kernel_path, remote_ramdisk_path)

    def _ShouldMixDiskImage(self):
        """Determines whether a mixed disk image is required.

        This method checks whether the user requires to replace an image that
        is part of the disk image. Acloud supports replacing system,
        system_dlkm, and kernel images. system and system_dlkm are installed
        on the disk.

        Returns:
            Boolean, whether a mixed disk image is required.
        """
        return (self._avd_spec.local_system_image or
                self._avd_spec.local_system_dlkm_image or
                (self._avd_spec.system_build_info.get(constants.BUILD_ID) and
                 self._avd_spec.system_build_info.get(constants.BUILD_TARGET)))

    @utils.TimeExecute(
        function_description="Processing and uploading tools and images")
    def _UploadDeviceImages(self, emulator_zip_path, image_zip_path):
        """Upload artifacts to remote host and extract them.

        Args:
            emulator_zip_path: The local path to the emulator zip.
            image_zip_path: The local path to the image zip.

        Returns:
            The remote paths to the extracted emulator tools and images.
        """
        remote_emulator_dir = self._GetInstancePath(_REMOTE_EMULATOR_DIR)
        remote_image_dir = self._GetInstancePath(_REMOTE_IMAGE_DIR)
        remote_emulator_zip_path = self._GetInstancePath(
            _REMOTE_EMULATOR_ZIP_PATH)
        remote_image_zip_path = self._GetInstancePath(_REMOTE_IMAGE_ZIP_PATH)
        self._ssh.Run(f"mkdir -p {remote_emulator_dir} {remote_image_dir}")
        self._ssh.ScpPushFile(emulator_zip_path, remote_emulator_zip_path)
        self._ssh.ScpPushFile(image_zip_path, remote_image_zip_path)

        self._ssh.Run(f"unzip -d {remote_emulator_dir} "
                      f"{remote_emulator_zip_path}")
        self._ssh.Run(f"unzip -d {remote_image_dir} {remote_image_zip_path}")
        remote_emulator_subdir = remote_path.join(
            remote_emulator_dir, _SDK_REPO_EMULATOR_DIR_NAME)
        remote_image_subdir = remote_path.join(
            remote_image_dir, self._GetSubdirNameInZip(image_zip_path))
        # TODO(b/141898893): In Android build environment, emulator gets build
        # information from $ANDROID_PRODUCT_OUT/system/build.prop.
        # If image_dir is an extacted SDK repository, the file is at
        # image_dir/build.prop. Acloud copies it to
        # image_dir/system/build.prop.
        src_path = remote_path.join(remote_image_subdir, "build.prop")
        dst_path = remote_path.join(remote_image_subdir, "system",
                                    "build.prop")
        self._ssh.Run("'test -f %(dst)s || "
                      "{ mkdir -p %(dst_dir)s && cp %(src)s %(dst)s ; }'" %
                      {"src": src_path,
                       "dst": dst_path,
                       "dst_dir": remote_path.dirname(dst_path)})
        return remote_emulator_subdir, remote_image_subdir

    def _MixAndUploadDiskImage(self, remote_image_dir, image_dir,
                               system_image_path, system_dlkm_image_path, ota):
        """Mix emulator, system, and system_dlkm images and upload them.

        Args:
            remote_image_dir: The remote directory where the mixed disk image
                              is uploaded.
            image_dir: The directory containing emulator images.
            system_image_path: The path to the system image.
            system_dlkm_image_path: The path to the system_dlkm image.
            ota: An instance of ota_tools.OtaTools.

        Returns:
            The remote path to the mixed disk image.
        """
        with tempfile.TemporaryDirectory("host_gf_disk") as temp_dir:
            mixed_image = goldfish_utils.MixDiskImage(
                temp_dir, image_dir, system_image_path, system_dlkm_image_path,
                ota)

            # TODO(b/142228085): Use -system instead of overwriting the file.
            remote_disk_image_path = os.path.join(
                remote_image_dir, goldfish_utils.SYSTEM_QEMU_IMAGE_NAME)
            self._ssh.ScpPushFile(mixed_image, remote_disk_image_path)

        # Adding the parameter to remote VerifiedBootParams.textproto unlocks
        # the device so that the disabled vbmeta takes effect. An alternative
        # is to append the parameter to the kernel command line by
        # `emulator -qemu -append`, but that does not pass the compliance test.
        remote_params_path = remote_path.join(
            remote_image_dir, goldfish_utils.VERIFIED_BOOT_PARAMS_FILE_NAME)
        # \\n is interpreted by shell and echo. \" is interpreted by shell.
        param = r'\\nparam: \"androidboot.verifiedbootstate=orange\"'
        self._ssh.Run(f"'test -f {remote_params_path} && "
                      f"echo -e {param} >> {remote_params_path}'")

        return remote_disk_image_path

    def _MixAndUploadKernelImages(self, image_dir, boot_image_path, ota):
        """Mix emulator kernel images with a boot image and upload them.

        Args:
            image_dir: The directory containing emulator images.
            boot_image_path: The path to the boot image.
            ota: An instance of ota_tools.OtaTools.

        Returns:
            The remote paths to the kernel image and the ramdisk image.
        """
        remote_kernel_path = self._GetInstancePath(_REMOTE_KERNEL_PATH)
        remote_ramdisk_path = self._GetInstancePath(_REMOTE_RAMDISK_PATH)
        with tempfile.TemporaryDirectory("host_gf_kernel") as temp_dir:
            kernel_path, ramdisk_path = goldfish_utils.MixWithBootImage(
                temp_dir, image_dir, boot_image_path, ota)

            self._ssh.ScpPushFile(kernel_path, remote_kernel_path)
            self._ssh.ScpPushFile(ramdisk_path, remote_ramdisk_path)

        return remote_kernel_path, remote_ramdisk_path

    def _GetEmulatorLogs(self):
        """Return the logs created by the remote emulator command."""
        return [report.LogFile(self._GetInstancePath(_REMOTE_STDOUT_PATH),
                               constants.LOG_TYPE_KERNEL_LOG),
                report.LogFile(self._GetInstancePath(_REMOTE_STDERR_PATH),
                               constants.LOG_TYPE_TEXT),
                report.LogFile(self._GetInstancePath(_REMOTE_LOGCAT_PATH),
                               constants.LOG_TYPE_LOGCAT)]

    @utils.TimeExecute(function_description="Start emulator")
    def _StartEmulator(self, remote_paths):
        """Start emulator command as a remote background process.

        Args:
            remote_emulator_dir: The emulator tool directory on remote host.
            remote_image_dir: The image directory on remote host.
        """
        remote_emulator_bin_path = remote_path.join(
            remote_paths.emulator_dir, _EMULATOR_BIN_NAME)
        remote_bin_paths = [
            remote_path.join(remote_paths.emulator_dir, name) for
            name in _EMULATOR_BIN_DIR_NAMES]
        remote_bin_paths.append(remote_emulator_bin_path)
        self._ssh.Run("chmod -R +x %s" % " ".join(remote_bin_paths))

        remote_runtime_dir = self._GetInstancePath(_REMOTE_RUNTIME_DIR)
        self._ssh.Run(f"mkdir -p {remote_runtime_dir}")
        env = {constants.ENV_ANDROID_PRODUCT_OUT: remote_paths.image_dir,
               constants.ENV_ANDROID_TMP: remote_runtime_dir,
               constants.ENV_ANDROID_BUILD_TOP: remote_runtime_dir}
        cmd = ["nohup", remote_emulator_bin_path, "-verbose", "-show-kernel",
               "-read-only", "-ports",
               str(self._GetConsolePort()) + "," + str(self.GetAdbPorts()[0]),
               "-no-window",
               "-logcat-output", self._GetInstancePath(_REMOTE_LOGCAT_PATH)]

        if remote_paths.kernel:
            cmd.extend(("-kernel", remote_paths.kernel))

        if remote_paths.ramdisk:
            cmd.extend(("-ramdisk", remote_paths.ramdisk))

        cmd.extend(goldfish_utils.ConvertAvdSpecToArgs(self._avd_spec))

        # Emulator does not support -stdouterr-file on macOS.
        self._ssh.Run(
            "'export {env} ; {cmd} 1> {stdout} 2> {stderr} &'".format(
                env=" ".join(k + "=~/" + v for k, v in env.items()),
                cmd=" ".join(cmd),
                stdout=self._GetInstancePath(_REMOTE_STDOUT_PATH),
                stderr=self._GetInstancePath(_REMOTE_STDERR_PATH)))

    @utils.TimeExecute(function_description="Wait for emulator")
    def _WaitForEmulator(self):
        """Wait for remote emulator console to be active.

        Raises:
            errors.DeviceBootError if connection fails.
            errors.DeviceBootTimeoutError if boot times out.
        """
        ip_addr = self._avd_spec.remote_host
        console_port = self._GetConsolePort()
        poll_timeout_secs = (self._avd_spec.boot_timeout_secs or
                             _DEFAULT_BOOT_TIMEOUT_SECS)
        try:
            with emulator_console.RemoteEmulatorConsole(
                    ip_addr,
                    console_port,
                    self._ssh_user,
                    self._ssh_private_key_path,
                    self._ssh_extra_args) as console:
                utils.PollAndWait(
                    func=lambda: (True if console.Ping() else
                                  console.Reconnect()),
                    expected_return=True,
                    timeout_exception=errors.DeviceBootTimeoutError,
                    timeout_secs=poll_timeout_secs,
                    sleep_interval_secs=5)
        except errors.DeviceConnectionError as e:
            raise errors.DeviceBootError("Fail to connect to %s:%d." %
                                         (ip_addr, console_port)) from e

    def GetBuildInfoDict(self):
        """Get build info dictionary.

        Returns:
            A build info dictionary.
        """
        build_info_dict = {key: val for key, val in
                           self._avd_spec.remote_image.items() if val}
        return build_info_dict

    def GetAdbPorts(self):
        """Get ADB ports of the created devices.

        This class does not support --num-avds-per-instance.

        Returns:
            The port numbers as a list of integers.
        """
        return [self._GetConsolePort() + 1]

    def GetFailures(self):
        """Get Failures from all devices.

        Returns:
            A dictionary the contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is an errors.DeviceBootError object.
        """
        return self._failures

    def GetLogs(self):
        """Get log files of created instances.

        Returns:
            A dictionary that maps instance names to lists of report.LogFile.
        """
        return self._logs
