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

"""Utility functions that process cuttlefish images."""

import collections
import fnmatch
import glob
import json
import logging
import os
import posixpath as remote_path
import re
import shlex
import subprocess
import tempfile
import zipfile

from acloud import errors
from acloud.create import create_common
from acloud.internal import constants
from acloud.internal.lib import ota_tools
from acloud.internal.lib import ssh
from acloud.internal.lib import utils
from acloud.public import report


logger = logging.getLogger(__name__)

# Local build artifacts to be uploaded.
_ARTIFACT_FILES = ["*.img", "bootloader", "kernel"]
_SYSTEM_DLKM_IMAGE_NAMES = (
    "system_dlkm.flatten.ext4.img",  # GKI artifact
    "system_dlkm.img",  # cuttlefish artifact
)
_VENDOR_BOOT_IMAGE_NAME = "vendor_boot.img"
_KERNEL_IMAGE_NAMES = ("kernel", "bzImage", "Image")
_INITRAMFS_IMAGE_NAME = "initramfs.img"
_SUPER_IMAGE_NAME = "super.img"
_VENDOR_IMAGE_NAMES = ("vendor.img", "vendor_dlkm.img", "odm.img",
                       "odm_dlkm.img")
VendorImagePaths = collections.namedtuple(
    "VendorImagePaths",
    ["vendor", "vendor_dlkm", "odm", "odm_dlkm"])

# The relative path to the base directory containing cuttelfish runtime files.
# On a GCE instance, the directory is the SSH user's HOME.
GCE_BASE_DIR = "."
_REMOTE_HOST_BASE_DIR_FORMAT = "acloud_cf_%(num)d"
# By default, fetch_cvd or UploadArtifacts creates remote cuttlefish images and
# tools in the base directory. The user can set the image directory path by
# --remote-image-dir.
# The user may specify extra images such as --local-system-image and
# --local-kernel-image. UploadExtraImages uploads them to "acloud_image"
# subdirectory in the image directory. The following are the relative paths
# under the image directory.
_REMOTE_EXTRA_IMAGE_DIR = "acloud_image"
_REMOTE_BOOT_IMAGE_PATH = remote_path.join(_REMOTE_EXTRA_IMAGE_DIR, "boot.img")
_REMOTE_VENDOR_BOOT_IMAGE_PATH = remote_path.join(
    _REMOTE_EXTRA_IMAGE_DIR, _VENDOR_BOOT_IMAGE_NAME)
_REMOTE_VBMETA_IMAGE_PATH = remote_path.join(
    _REMOTE_EXTRA_IMAGE_DIR, "vbmeta.img")
_REMOTE_KERNEL_IMAGE_PATH = remote_path.join(
    _REMOTE_EXTRA_IMAGE_DIR, _KERNEL_IMAGE_NAMES[0])
_REMOTE_INITRAMFS_IMAGE_PATH = remote_path.join(
    _REMOTE_EXTRA_IMAGE_DIR, _INITRAMFS_IMAGE_NAME)
_REMOTE_SUPER_IMAGE_PATH = remote_path.join(
    _REMOTE_EXTRA_IMAGE_DIR, _SUPER_IMAGE_NAME)

# Remote host instance name
_REMOTE_HOST_INSTANCE_NAME_FORMAT = (
    constants.INSTANCE_TYPE_HOST +
    "-%(ip_addr)s-%(num)d-%(build_id)s-%(build_target)s")
_REMOTE_HOST_INSTANCE_NAME_PATTERN = re.compile(
    constants.INSTANCE_TYPE_HOST + r"-(?P<ip_addr>[\d.]+)-(?P<num>\d+)-.+")
# android-info.txt contents.
_CONFIG_PATTERN = re.compile(r"^config=(?P<config>.+)$", re.MULTILINE)
# launch_cvd arguments.
_DATA_POLICY_CREATE_IF_MISSING = "create_if_missing"
_DATA_POLICY_ALWAYS_CREATE = "always_create"
_NUM_AVDS_ARG = "-num_instances=%(num_AVD)s"
AGREEMENT_PROMPT_ARG = "-report_anonymous_usage_stats=y"
UNDEFOK_ARG = "-undefok=report_anonymous_usage_stats,config"
# Connect the OpenWrt device via console file.
_ENABLE_CONSOLE_ARG = "-console=true"
# WebRTC args
_WEBRTC_ID = "--webrtc_device_id=%(instance)s"
_WEBRTC_ARGS = ["--start_webrtc", "--vm_manager=crosvm"]
_VNC_ARGS = ["--start_vnc_server=true"]

# Cuttlefish runtime directory is specified by `-instance_dir <runtime_dir>`.
# Cuttlefish tools may create a symbolic link at the specified path.
# The actual location of the runtime directory depends on the version:
#
# In Android 10, the directory is `<runtime_dir>`.
#
# In Android 11 and 12, the directory is `<runtime_dir>.<num>`.
# `<runtime_dir>` is a symbolic link to the first device's directory.
#
# In the latest version, if `--instance-dir <runtime_dir>` is specified, the
# directory is `<runtime_dir>/instances/cvd-<num>`.
# `<runtime_dir>_runtime` and `<runtime_dir>.<num>` are symbolic links.
#
# If `--instance-dir <runtime_dir>` is not specified, the directory is
# `~/cuttlefish/instances/cvd-<num>`.
# `~/cuttlefish_runtime` and `~/cuttelfish_runtime.<num>` are symbolic links.
_LOCAL_LOG_DIR_FORMAT = os.path.join(
    "%(runtime_dir)s", "instances", "cvd-%(num)d", "logs")
# Relative paths in a base directory.
_REMOTE_RUNTIME_DIR_FORMAT = remote_path.join(
    "cuttlefish", "instances", "cvd-%(num)d")
_REMOTE_LEGACY_RUNTIME_DIR_FORMAT = "cuttlefish_runtime.%(num)d"
HOST_KERNEL_LOG = report.LogFile(
    "/var/log/kern.log", constants.LOG_TYPE_KERNEL_LOG, "host_kernel.log")

# Contents of the target_files archive.
_DOWNLOAD_MIX_IMAGE_NAME = "{build_target}-target_files-{build_id}.zip"
_TARGET_FILES_META_DIR_NAME = "META"
_TARGET_FILES_IMAGES_DIR_NAME = "IMAGES"
_MISC_INFO_FILE_NAME = "misc_info.txt"
# glob patterns of target_files entries used by acloud.
_TARGET_FILES_ENTRIES = [
    "IMAGES/" + pattern for pattern in _ARTIFACT_FILES
] + ["META/misc_info.txt"]

# Represents a 64-bit ARM architecture.
_ARM_MACHINE_TYPE = "aarch64"


def GetAdbPorts(base_instance_num, num_avds_per_instance):
    """Get ADB ports of cuttlefish.

    Args:
        base_instance_num: An integer or None, the instance number of the first
                           device.
        num_avds_per_instance: An integer or None, the number of devices.

    Returns:
        The port numbers as a list of integers.
    """
    return [constants.CF_ADB_PORT + (base_instance_num or 1) - 1 + index
            for index in range(num_avds_per_instance or 1)]


def GetVncPorts(base_instance_num, num_avds_per_instance):
    """Get VNC ports of cuttlefish.

    Args:
        base_instance_num: An integer or None, the instance number of the first
                           device.
        num_avds_per_instance: An integer or None, the number of devices.

    Returns:
        The port numbers as a list of integers.
    """
    return [constants.CF_VNC_PORT + (base_instance_num or 1) - 1 + index
            for index in range(num_avds_per_instance or 1)]


@utils.TimeExecute(function_description="Extracting target_files zip.")
def ExtractTargetFilesZip(zip_path, output_dir):
    """Extract images and misc_info.txt from a target_files zip."""
    with zipfile.ZipFile(zip_path, "r") as zip_file:
        for entry in zip_file.namelist():
            if any(fnmatch.fnmatch(entry, pattern) for pattern in
                   _TARGET_FILES_ENTRIES):
                zip_file.extract(entry, output_dir)


def _UploadImageZip(ssh_obj, remote_image_dir, image_zip):
    """Upload an image zip to a remote host and a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        image_zip: The path to the image zip.
    """
    remote_cmd = f"/usr/bin/install_zip.sh {remote_image_dir} < {image_zip}"
    logger.debug("remote_cmd:\n %s", remote_cmd)
    ssh_obj.Run(remote_cmd)


def _UploadImageDir(ssh_obj, remote_image_dir, image_dir):
    """Upload an image directory to a remote host or a GCE instance.

    The images are compressed for faster upload.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        image_dir: The directory containing the files to be uploaded.
    """
    try:
        images_path = os.path.join(image_dir, "required_images")
        with open(images_path, "r", encoding="utf-8") as images:
            artifact_files = images.read().splitlines()
    except IOError:
        # Older builds may not have a required_images file. In this case
        # we fall back to *.img.
        artifact_files = []
        for file_name in _ARTIFACT_FILES:
            artifact_files.extend(
                os.path.basename(image) for image in glob.glob(
                    os.path.join(image_dir, file_name)))
    # Upload android-info.txt to parse config value.
    artifact_files.append(constants.ANDROID_INFO_FILE)
    cmd = (f"tar -cf - --lzop -S -C {image_dir} {' '.join(artifact_files)} | "
           f"{ssh_obj.GetBaseCmd(constants.SSH_BIN)} -- "
           f"tar -xf - --lzop -S -C {remote_image_dir}")
    logger.debug("cmd:\n %s", cmd)
    ssh.ShellCmdWithRetry(cmd)


def _UploadCvdHostPackage(ssh_obj, remote_image_dir, cvd_host_package):
    """Upload a CVD host package to a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote base directory.
        cvd_host_package: The path to the CVD host package.
    """
    if os.path.isdir(cvd_host_package):
        cmd = (f"tar -cf - --lzop -S -C {cvd_host_package} . | "
               f"{ssh_obj.GetBaseCmd(constants.SSH_BIN)} -- "
               f"tar -xf - --lzop -S -C {remote_image_dir}")
        logger.debug("cmd:\n %s", cmd)
        ssh.ShellCmdWithRetry(cmd)
    else:
        remote_cmd = f"tar -xzf - -C {remote_image_dir} < {cvd_host_package}"
        logger.debug("remote_cmd:\n %s", remote_cmd)
        ssh_obj.Run(remote_cmd)


@utils.TimeExecute(function_description="Processing and uploading local images")
def UploadArtifacts(ssh_obj, remote_image_dir, image_path, cvd_host_package):
    """Upload images and a CVD host package to a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        image_path: A string, the path to the image zip built by `m dist`,
                    the directory containing the images built by `m`, or
                    the directory containing extracted target files.
        cvd_host_package: A string, the path to the CVD host package in gzip.
    """
    if os.path.isdir(image_path):
        _UploadImageDir(ssh_obj, remote_image_dir, FindImageDir(image_path))
    else:
        _UploadImageZip(ssh_obj, remote_image_dir, image_path)
    _UploadCvdHostPackage(ssh_obj, remote_image_dir, cvd_host_package)


def FindBootImages(search_path):
    """Find boot and vendor_boot images in a path.

    Args:
        search_path: A path to an image file or an image directory.

    Returns:
        The boot image path and the vendor_boot image path. Each value can be
        None if the path doesn't exist.

    Raises:
        errors.GetLocalImageError if search_path contains more than one boot
        image or the file format is not correct.
    """
    boot_image_path = create_common.FindBootImage(search_path,
                                                  raise_error=False)
    vendor_boot_image_path = os.path.join(search_path, _VENDOR_BOOT_IMAGE_NAME)
    if not os.path.isfile(vendor_boot_image_path):
        vendor_boot_image_path = None

    return boot_image_path, vendor_boot_image_path


def FindKernelImages(search_path):
    """Find kernel and initramfs images in a path.

    Args:
        search_path: A path to an image directory.

    Returns:
        The kernel image path and the initramfs image path. Each value can be
        None if the path doesn't exist.
    """
    paths = [os.path.join(search_path, name) for name in _KERNEL_IMAGE_NAMES]
    kernel_image_path = next((path for path in paths if os.path.isfile(path)),
                             None)

    initramfs_image_path = os.path.join(search_path, _INITRAMFS_IMAGE_NAME)
    if not os.path.isfile(initramfs_image_path):
        initramfs_image_path = None

    return kernel_image_path, initramfs_image_path


@utils.TimeExecute(function_description="Uploading local kernel images.")
def _UploadKernelImages(ssh_obj, remote_image_dir, search_path):
    """Find and upload kernel or boot images to a remote host or a GCE
    instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        search_path: A path to an image file or an image directory.

    Returns:
        A list of strings, the launch_cvd arguments including the remote paths.

    Raises:
        errors.GetLocalImageError if search_path does not contain kernel
        images.
    """
    # Assume that the caller cleaned up the remote home directory.
    ssh_obj.Run("mkdir -p " +
                remote_path.join(remote_image_dir, _REMOTE_EXTRA_IMAGE_DIR))

    kernel_image_path, initramfs_image_path = FindKernelImages(search_path)
    if kernel_image_path and initramfs_image_path:
        remote_kernel_image_path = remote_path.join(
            remote_image_dir, _REMOTE_KERNEL_IMAGE_PATH)
        remote_initramfs_image_path = remote_path.join(
            remote_image_dir, _REMOTE_INITRAMFS_IMAGE_PATH)
        ssh_obj.ScpPushFile(kernel_image_path, remote_kernel_image_path)
        ssh_obj.ScpPushFile(initramfs_image_path, remote_initramfs_image_path)
        return ["-kernel_path", remote_kernel_image_path,
                "-initramfs_path", remote_initramfs_image_path]

    boot_image_path, vendor_boot_image_path = FindBootImages(search_path)
    if boot_image_path:
        remote_boot_image_path = remote_path.join(
            remote_image_dir, _REMOTE_BOOT_IMAGE_PATH)
        ssh_obj.ScpPushFile(boot_image_path, remote_boot_image_path)
        launch_cvd_args = ["-boot_image", remote_boot_image_path]
        if vendor_boot_image_path:
            remote_vendor_boot_image_path = remote_path.join(
                remote_image_dir, _REMOTE_VENDOR_BOOT_IMAGE_PATH)
            ssh_obj.ScpPushFile(vendor_boot_image_path,
                                remote_vendor_boot_image_path)
            launch_cvd_args.extend(["-vendor_boot_image",
                                    remote_vendor_boot_image_path])
        return launch_cvd_args

    raise errors.GetLocalImageError(
        f"{search_path} is not a boot image or a directory containing images.")


def _FindSystemDlkmImage(search_path):
    """Find system_dlkm image in a path.

    Args:
        search_path: A path to an image file or an image directory.

    Returns:
        The system_dlkm image path.

    Raises:
        errors.GetLocalImageError if search_path does not contain a
        system_dlkm image.
    """
    if os.path.isfile(search_path):
        return search_path

    for name in _SYSTEM_DLKM_IMAGE_NAMES:
        path = os.path.join(search_path, name)
        if os.path.isfile(path):
            return path

    raise errors.GetLocalImageError(
        f"{search_path} is not a system_dlkm image or a directory containing "
        "images.")


def _MixSuperImage(super_image_path, avd_spec, target_files_dir, ota):
    """Mix super image from device images and extra images.

    Args:
        super_image_path: The path to the output mixed super image.
        avd_spec: An AvdSpec object.
        target_files_dir: The path to the extracted target_files zip containing
                          device images and misc_info.txt.
        ota: An OtaTools object.
    """
    misc_info_path = FindMiscInfo(target_files_dir)
    image_dir = FindImageDir(target_files_dir)

    system_image_path = None
    system_ext_image_path = None
    product_image_path = None
    system_dlkm_image_path = None
    vendor_image_path = None
    vendor_dlkm_image_path = None
    odm_image_path = None
    odm_dlkm_image_path = None

    if avd_spec.local_system_image:
        (
            system_image_path,
            system_ext_image_path,
            product_image_path,
        ) = create_common.FindSystemImages(avd_spec.local_system_image)

    if avd_spec.local_system_dlkm_image:
        system_dlkm_image_path = _FindSystemDlkmImage(
            avd_spec.local_system_dlkm_image)

    if avd_spec.local_vendor_image:
        (
            vendor_image_path,
            vendor_dlkm_image_path,
            odm_image_path,
            odm_dlkm_image_path,
        ) = FindVendorImages(avd_spec.local_vendor_image)

    ota.MixSuperImage(super_image_path, misc_info_path, image_dir,
                      system_image=system_image_path,
                      system_ext_image=system_ext_image_path,
                      product_image=product_image_path,
                      system_dlkm_image=system_dlkm_image_path,
                      vendor_image=vendor_image_path,
                      vendor_dlkm_image=vendor_dlkm_image_path,
                      odm_image=odm_image_path,
                      odm_dlkm_image=odm_dlkm_image_path)


@utils.TimeExecute(function_description="Uploading disabled vbmeta image.")
def _UploadVbmetaImage(ssh_obj, remote_image_dir, vbmeta_image_path):
    """Upload disabled vbmeta image to a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        vbmeta_image_path: The path to the vbmeta image.

    Returns:
        A list of strings, the launch_cvd arguments including the remote paths.
    """
    remote_vbmeta_image_path = remote_path.join(remote_image_dir,
                                                _REMOTE_VBMETA_IMAGE_PATH)
    ssh_obj.ScpPushFile(vbmeta_image_path, remote_vbmeta_image_path)
    return ["-vbmeta_image", remote_vbmeta_image_path]


def AreTargetFilesRequired(avd_spec):
    """Return whether UploadExtraImages requires target_files_dir."""
    return bool(avd_spec.local_system_image or avd_spec.local_vendor_image or
                avd_spec.local_system_dlkm_image)


def UploadExtraImages(ssh_obj, remote_image_dir, avd_spec, target_files_dir):
    """Find and upload the images specified in avd_spec.

    This function finds the kernel, system, and vendor images specified in
    avd_spec. It processes them and uploads kernel, super, and vbmeta images.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        avd_spec: An AvdSpec object containing extra image paths.
        target_files_dir: The path to an extracted target_files zip if the
                          avd_spec requires building a super image.

    Returns:
        A list of strings, the launch_cvd arguments including the remote paths.

    Raises:
        errors.GetLocalImageError if any specified image path does not exist.
        errors.CheckPathError if avd_spec.local_tool_dirs do not contain OTA
        tools, or target_files_dir does not contain misc_info.txt.
        ValueError if target_files_dir is required but not specified.
    """
    extra_img_args = []
    if avd_spec.local_kernel_image:
        extra_img_args += _UploadKernelImages(ssh_obj, remote_image_dir,
                                              avd_spec.local_kernel_image)

    if AreTargetFilesRequired(avd_spec):
        if not target_files_dir:
            raise ValueError("target_files_dir is required when avd_spec has "
                             "local system image, local system_dlkm image, or "
                             "local vendor image.")
        ota = ota_tools.FindOtaTools(
            avd_spec.local_tool_dirs + create_common.GetNonEmptyEnvVars(
                constants.ENV_ANDROID_SOONG_HOST_OUT,
                constants.ENV_ANDROID_HOST_OUT))
        ssh_obj.Run(
            "mkdir -p " +
            remote_path.join(remote_image_dir, _REMOTE_EXTRA_IMAGE_DIR))
        with tempfile.TemporaryDirectory() as super_image_dir:
            _MixSuperImage(os.path.join(super_image_dir, _SUPER_IMAGE_NAME),
                           avd_spec, target_files_dir, ota)
            extra_img_args += _UploadSuperImage(ssh_obj, remote_image_dir,
                                                super_image_dir)

            vbmeta_image_path = os.path.join(super_image_dir, "vbmeta.img")
            ota.MakeDisabledVbmetaImage(vbmeta_image_path)
            extra_img_args += _UploadVbmetaImage(ssh_obj, remote_image_dir,
                                                 vbmeta_image_path)

    return extra_img_args


@utils.TimeExecute(function_description="Uploading super image.")
def _UploadSuperImage(ssh_obj, remote_image_dir, super_image_dir):
    """Upload a super image to a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.
        super_image_dir: The path to the directory containing the super image.

    Returns:
        A list of strings, the launch_cvd arguments including the remote paths.
    """
    remote_super_image_path = remote_path.join(remote_image_dir,
                                               _REMOTE_SUPER_IMAGE_PATH)
    remote_super_image_dir = remote_path.dirname(remote_super_image_path)
    cmd = (f"tar -cf - --lzop -S -C {super_image_dir} {_SUPER_IMAGE_NAME} | "
           f"{ssh_obj.GetBaseCmd(constants.SSH_BIN)} -- "
           f"tar -xf - --lzop -S -C {remote_super_image_dir}")
    ssh.ShellCmdWithRetry(cmd)
    launch_cvd_args = ["-super_image", remote_super_image_path]
    return launch_cvd_args


def CleanUpRemoteCvd(ssh_obj, remote_dir, raise_error):
    """Call stop_cvd and delete the files on a remote host.

    Args:
        ssh_obj: An Ssh object.
        remote_dir: The remote base directory.
        raise_error: Whether to raise an error if the remote instance is not
                     running.

    Raises:
        subprocess.CalledProcessError if any command fails.
    """
    # TODO(b/293966645): Find stop_cvd in --remote-image-dir.
    home = remote_path.join("$HOME", remote_dir)
    stop_cvd_path = remote_path.join(remote_dir, "bin", "stop_cvd")
    stop_cvd_cmd = f"'HOME={home} {stop_cvd_path}'"
    if raise_error:
        ssh_obj.Run(stop_cvd_cmd)
    else:
        try:
            ssh_obj.Run(stop_cvd_cmd, retry=0)
        except Exception as e:
            logger.debug(
                "Failed to stop_cvd (possibly no running device): %s", e)

    # This command deletes all files except hidden files under HOME.
    # It does not raise an error if no files can be deleted.
    ssh_obj.Run(f"'rm -rf {remote_path.join(remote_dir, '*')}'")


def GetRemoteHostBaseDir(base_instance_num):
    """Get remote base directory by instance number.

    Args:
        base_instance_num: Integer or None, the instance number of the device.

    Returns:
        The remote base directory.
    """
    return _REMOTE_HOST_BASE_DIR_FORMAT % {"num": base_instance_num or 1}


def FormatRemoteHostInstanceName(ip_addr, base_instance_num, build_id,
                                 build_target):
    """Convert an IP address and build info to an instance name.

    Args:
        ip_addr: String, the IP address of the remote host.
        base_instance_num: Integer or None, the instance number of the device.
        build_id: String, the build id.
        build_target: String, the build target, e.g., aosp_cf_x86_64_phone.

    Return:
        String, the instance name.
    """
    return _REMOTE_HOST_INSTANCE_NAME_FORMAT % {
        "ip_addr": ip_addr,
        "num": base_instance_num or 1,
        "build_id": build_id,
        "build_target": build_target}


def ParseRemoteHostAddress(instance_name):
    """Parse IP address from a remote host instance name.

    Args:
        instance_name: String, the instance name.

    Returns:
        The IP address and the base directory as strings.
        None if the name does not represent a remote host instance.
    """
    match = _REMOTE_HOST_INSTANCE_NAME_PATTERN.fullmatch(instance_name)
    if match:
        return (match.group("ip_addr"),
                GetRemoteHostBaseDir(int(match.group("num"))))
    return None


def LoadRemoteImageArgs(ssh_obj, remote_args_path):
    """Load launch_cvd arguments from a remote path.

    This method assumes that one acloud process accesses the path at a time.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote path containing the arguments.

    Returns:
        A list of strings, the launch_cvd arguments.
        None if the directory has not been initialized.
    """
    # If the file doesn't exist, the command returns 0 and outputs nothing.
    args_str = ssh_obj.Run(shlex.quote(
        f"test ! -f {remote_args_path} || cat {remote_args_path}"))
    if not args_str:
        return None
    try:
        return json.loads(args_str)
    except json.JSONDecodeError as e:
        logger.error("Unable to load %s: %s", remote_args_path, e)
        return None


def SaveRemoteImageArgs(ssh_obj, remote_args_path, launch_cvd_args):
    """Save launch_cvd arguments to a remote path.

    This method assumes that one acloud process accesses the path at a time.

    Args:
        ssh_obj: An Ssh object.
        remote_args_path: The remote path containing the arguments.
        launch_cvd_args: A list of strings, the launch_cvd arguments.
    """
    # The json string is interpreted twice by SSH client and remote shell.
    args_str = shlex.quote(json.dumps(launch_cvd_args))
    ssh_obj.Run(shlex.quote(f"echo {args_str} > {remote_args_path}"))


def GetConfigFromRemoteAndroidInfo(ssh_obj, remote_image_dir):
    """Get config from android-info.txt on a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_image_dir: The remote image directory.

    Returns:
        A string, the config value. For example, "phone".
    """
    android_info = ssh_obj.GetCmdOutput(
        "cat " +
        remote_path.join(remote_image_dir, constants.ANDROID_INFO_FILE))
    logger.debug("Android info: %s", android_info)
    config_match = _CONFIG_PATTERN.search(android_info)
    if config_match:
        return config_match.group("config")
    return None


# pylint:disable=too-many-branches
def _GetLaunchCvdArgs(avd_spec, config):
    """Get launch_cvd arguments for remote instances.

    Args:
        avd_spec: An AVDSpec instance.
        config: A string or None, the name of the predefined hardware config.
                e.g., "auto", "phone", and "tv".

    Returns:
        A list of strings, arguments of launch_cvd.
    """
    launch_cvd_args = []

    blank_data_disk_size_gb = avd_spec.cfg.extra_data_disk_size_gb
    if blank_data_disk_size_gb and blank_data_disk_size_gb > 0:
        launch_cvd_args.append(
            "-data_policy=" + _DATA_POLICY_CREATE_IF_MISSING)
        launch_cvd_args.append(
            "-blank_data_image_mb=" + str(blank_data_disk_size_gb * 1024))

    if config:
        launch_cvd_args.append("-config=" + config)
    if avd_spec.hw_customize or not config:
        launch_cvd_args.append(
            "-x_res=" + avd_spec.hw_property[constants.HW_X_RES])
        launch_cvd_args.append(
            "-y_res=" + avd_spec.hw_property[constants.HW_Y_RES])
        launch_cvd_args.append(
            "-dpi=" + avd_spec.hw_property[constants.HW_ALIAS_DPI])
        if constants.HW_ALIAS_DISK in avd_spec.hw_property:
            launch_cvd_args.append(
                "-data_policy=" + _DATA_POLICY_ALWAYS_CREATE)
            launch_cvd_args.append(
                "-blank_data_image_mb="
                + avd_spec.hw_property[constants.HW_ALIAS_DISK])
        if constants.HW_ALIAS_CPUS in avd_spec.hw_property:
            launch_cvd_args.append(
                "-cpus=" + str(avd_spec.hw_property[constants.HW_ALIAS_CPUS]))
        if constants.HW_ALIAS_MEMORY in avd_spec.hw_property:
            launch_cvd_args.append(
                "-memory_mb=" +
                str(avd_spec.hw_property[constants.HW_ALIAS_MEMORY]))

    if avd_spec.connect_webrtc:
        launch_cvd_args.extend(_WEBRTC_ARGS)
        if avd_spec.webrtc_device_id:
            launch_cvd_args.append(
                _WEBRTC_ID % {"instance": avd_spec.webrtc_device_id})
    if avd_spec.connect_vnc:
        launch_cvd_args.extend(_VNC_ARGS)
    if avd_spec.openwrt:
        launch_cvd_args.append(_ENABLE_CONSOLE_ARG)
    if avd_spec.num_avds_per_instance > 1:
        launch_cvd_args.append(
            _NUM_AVDS_ARG % {"num_AVD": avd_spec.num_avds_per_instance})
    if avd_spec.base_instance_num:
        launch_cvd_args.append(
            "--base_instance_num=" + str(avd_spec.base_instance_num))
    if avd_spec.launch_args:
        # b/286321583: Need to process \" as ".
        launch_cvd_args.append(avd_spec.launch_args.replace("\\\"", "\""))

    launch_cvd_args.append(UNDEFOK_ARG)
    launch_cvd_args.append(AGREEMENT_PROMPT_ARG)
    return launch_cvd_args


def GetRemoteLaunchCvdCmd(remote_dir, avd_spec, config, extra_args):
    """Get launch_cvd command for remote instances.

    Args:
        remote_dir: The remote base directory.
        avd_spec: An AVDSpec instance.
        config: A string or None, the name of the predefined hardware config.
                e.g., "auto", "phone", and "tv".
        extra_args: Collection of strings, the extra arguments.

    Returns:
        A string, the launch_cvd command.
    """
    # launch_cvd requires ANDROID_HOST_OUT to be absolute.
    cmd = ([f"{constants.ENV_ANDROID_HOST_OUT}="
            f"$(readlink -n -m {avd_spec.remote_image_dir})",
            f"{constants.ENV_ANDROID_PRODUCT_OUT}="
            f"${constants.ENV_ANDROID_HOST_OUT}"]
           if avd_spec.remote_image_dir else [])
    cmd.extend(["HOME=" + remote_path.join("$HOME", remote_dir),
                remote_path.join(avd_spec.remote_image_dir or remote_dir,
                                 "bin", "launch_cvd"),
                "-daemon"])
    cmd.extend(extra_args)
    cmd.extend(_GetLaunchCvdArgs(avd_spec, config))
    return " ".join(cmd)


def ExecuteRemoteLaunchCvd(ssh_obj, cmd, boot_timeout_secs):
    """launch_cvd command on a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        cmd: A string generated by GetRemoteLaunchCvdCmd.
        boot_timeout_secs: A float, the timeout for the command.

    Returns:
        The error message as a string if the command fails.
        An empty string if the command succeeds.
    """
    try:
        ssh_obj.Run(f"-t '{cmd}'", boot_timeout_secs, retry=0)
    except (subprocess.CalledProcessError, errors.DeviceConnectionError,
            errors.LaunchCVDFail) as e:
        error_msg = ("Device did not finish on boot within "
                     f"{boot_timeout_secs} secs)")
        if constants.ERROR_MSG_VNC_NOT_SUPPORT in str(e):
            error_msg = ("VNC is not supported in the current build. Please "
                         "try WebRTC such as '$acloud create' or "
                         "'$acloud create --autoconnect webrtc'")
        if constants.ERROR_MSG_WEBRTC_NOT_SUPPORT in str(e):
            error_msg = ("WEBRTC is not supported in the current build. "
                         "Please try VNC such as "
                         "'$acloud create --autoconnect vnc'")
        utils.PrintColorString(str(e), utils.TextColors.FAIL)
        return error_msg
    return ""


def _GetRemoteRuntimeDirs(ssh_obj, remote_dir, base_instance_num,
                          num_avds_per_instance):
    """Get cuttlefish runtime directories on a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_dir: The remote base directory.
        base_instance_num: An integer, the instance number of the first device.
        num_avds_per_instance: An integer, the number of devices.

    Returns:
        A list of strings, the paths to the runtime directories.
    """
    runtime_dir = remote_path.join(
        remote_dir, _REMOTE_RUNTIME_DIR_FORMAT % {"num": base_instance_num})
    try:
        ssh_obj.Run(f"test -d {runtime_dir}", retry=0)
        return [remote_path.join(remote_dir,
                                 _REMOTE_RUNTIME_DIR_FORMAT %
                                 {"num": base_instance_num + num})
                for num in range(num_avds_per_instance)]
    except subprocess.CalledProcessError:
        logger.debug("%s is not the runtime directory.", runtime_dir)

    legacy_runtime_dirs = [
        remote_path.join(remote_dir, constants.REMOTE_LOG_FOLDER)]
    legacy_runtime_dirs.extend(
        remote_path.join(remote_dir,
                         _REMOTE_LEGACY_RUNTIME_DIR_FORMAT %
                         {"num": base_instance_num + num})
        for num in range(1, num_avds_per_instance))
    return legacy_runtime_dirs


def GetRemoteFetcherConfigJson(remote_image_dir):
    """Get the config created by fetch_cvd on a remote host or a GCE instance.

    Args:
        remote_image_dir: The remote image directory.

    Returns:
        An object of report.LogFile.
    """
    return report.LogFile(
        remote_path.join(remote_image_dir, "fetcher_config.json"),
        constants.LOG_TYPE_CUTTLEFISH_LOG)


def _GetRemoteTombstone(runtime_dir, name_suffix):
    """Get log object for tombstones in a remote cuttlefish runtime directory.

    Args:
        runtime_dir: The path to the remote cuttlefish runtime directory.
        name_suffix: The string appended to the log name. It is used to
                     distinguish log files found in different runtime_dirs.

    Returns:
        A report.LogFile object.
    """
    return report.LogFile(remote_path.join(runtime_dir, "tombstones"),
                          constants.LOG_TYPE_DIR,
                          "tombstones-zip" + name_suffix)


def _GetLogType(file_name):
    """Determine log type by file name.

    Args:
        file_name: A file name.

    Returns:
        A string, one of the log types defined in constants.
        None if the file is not a log file.
    """
    if file_name == "kernel.log":
        return constants.LOG_TYPE_KERNEL_LOG
    if file_name == "logcat":
        return constants.LOG_TYPE_LOGCAT
    if file_name.endswith(".log") or file_name == "cuttlefish_config.json":
        return constants.LOG_TYPE_CUTTLEFISH_LOG
    return None


def FindRemoteLogs(ssh_obj, remote_dir, base_instance_num,
                   num_avds_per_instance):
    """Find log objects on a remote host or a GCE instance.

    Args:
        ssh_obj: An Ssh object.
        remote_dir: The remote base directory.
        base_instance_num: An integer or None, the instance number of the first
                           device.
        num_avds_per_instance: An integer or None, the number of devices.

    Returns:
        A list of report.LogFile objects.
    """
    runtime_dirs = _GetRemoteRuntimeDirs(
        ssh_obj, remote_dir,
        (base_instance_num or 1), (num_avds_per_instance or 1))
    logs = []
    for log_path in utils.FindRemoteFiles(ssh_obj, runtime_dirs):
        file_name = remote_path.basename(log_path)
        log_type = _GetLogType(file_name)
        if not log_type:
            continue
        base, ext = remote_path.splitext(file_name)
        # The index of the runtime_dir containing log_path.
        index_str = ""
        for index, runtime_dir in enumerate(runtime_dirs):
            if log_path.startswith(runtime_dir + remote_path.sep):
                index_str = "." + str(index) if index else ""
        log_name = ("full_gce_logcat" + index_str if file_name == "logcat" else
                    base + index_str + ext)

        logs.append(report.LogFile(log_path, log_type, log_name))

    logs.extend(_GetRemoteTombstone(runtime_dir,
                                    ("." + str(index) if index else ""))
                for index, runtime_dir in enumerate(runtime_dirs))
    return logs


def FindLocalLogs(runtime_dir, instance_num):
    """Find log objects in a local runtime directory.

    Args:
        runtime_dir: A string, the runtime directory path.
        instance_num: An integer, the instance number.

    Returns:
        A list of report.LogFile.
    """
    log_dir = _LOCAL_LOG_DIR_FORMAT % {"runtime_dir": runtime_dir,
                                       "num": instance_num}
    if not os.path.isdir(log_dir):
        log_dir = runtime_dir

    logs = []
    for parent_dir, _, file_names in os.walk(log_dir, followlinks=False):
        for file_name in file_names:
            log_path = os.path.join(parent_dir, file_name)
            log_type = _GetLogType(file_name)
            if os.path.islink(log_path) or not log_type:
                continue
            logs.append(report.LogFile(log_path, log_type))
    return logs


def GetOpenWrtInfoDict(ssh_obj, remote_dir):
    """Return the commands to connect to a remote OpenWrt console.

    Args:
        ssh_obj: An Ssh object.
        remote_dir: The remote base directory.

    Returns:
        A dict containing the OpenWrt info.
    """
    console_path = remote_path.join(remote_dir, "cuttlefish_runtime",
                                    "console")
    return {"ssh_command": ssh_obj.GetBaseCmd(constants.SSH_BIN),
            "screen_command": "screen " + console_path}


def GetRemoteBuildInfoDict(avd_spec):
    """Convert remote build infos to a dictionary for reporting.

    Args:
        avd_spec: An AvdSpec object containing the build infos.

    Returns:
        A dict containing the build infos.
    """
    build_info_dict = {
        key: val for key, val in avd_spec.remote_image.items() if val}

    # kernel_target has a default value. If the user provides kernel_build_id
    # or kernel_branch, then convert kernel build info.
    if (avd_spec.kernel_build_info.get(constants.BUILD_ID) or
            avd_spec.kernel_build_info.get(constants.BUILD_BRANCH)):
        build_info_dict.update(
            {"kernel_" + key: val
             for key, val in avd_spec.kernel_build_info.items() if val}
        )
    build_info_dict.update(
        {"system_" + key: val
         for key, val in avd_spec.system_build_info.items() if val}
    )
    build_info_dict.update(
        {"bootloader_" + key: val
         for key, val in avd_spec.bootloader_build_info.items() if val}
    )
    return build_info_dict


def GetMixBuildTargetFilename(build_target, build_id):
    """Get the mix build target filename.

    Args:
        build_id: String, Build id, e.g. "2263051", "P2804227"
        build_target: String, the build target, e.g. cf_x86_phone-userdebug

    Returns:
        String, a file name, e.g. "cf_x86_phone-target_files-2263051.zip"
    """
    return _DOWNLOAD_MIX_IMAGE_NAME.format(
        build_target=build_target.split('-')[0],
        build_id=build_id)


def FindMiscInfo(image_dir):
    """Find misc info in build output dir or extracted target files.

    Args:
        image_dir: The directory to search for misc info.

    Returns:
        image_dir if the directory structure looks like an output directory
        in build environment.
        image_dir/META if it looks like extracted target files.

    Raises:
        errors.CheckPathError if this function cannot find misc info.
    """
    misc_info_path = os.path.join(image_dir, _MISC_INFO_FILE_NAME)
    if os.path.isfile(misc_info_path):
        return misc_info_path
    misc_info_path = os.path.join(image_dir, _TARGET_FILES_META_DIR_NAME,
                                  _MISC_INFO_FILE_NAME)
    if os.path.isfile(misc_info_path):
        return misc_info_path
    raise errors.CheckPathError(
        f"Cannot find {_MISC_INFO_FILE_NAME} in {image_dir}. The "
        f"directory is expected to be an extracted target files zip or "
        f"{constants.ENV_ANDROID_PRODUCT_OUT}.")


def FindImageDir(image_dir):
    """Find images in build output dir or extracted target files.

    Args:
        image_dir: The directory to search for images.

    Returns:
        image_dir if the directory structure looks like an output directory
        in build environment.
        image_dir/IMAGES if it looks like extracted target files.

    Raises:
        errors.GetLocalImageError if this function cannot find any image.
    """
    if glob.glob(os.path.join(image_dir, "*.img")):
        return image_dir
    subdir = os.path.join(image_dir, _TARGET_FILES_IMAGES_DIR_NAME)
    if glob.glob(os.path.join(subdir, "*.img")):
        return subdir
    raise errors.GetLocalImageError(
        "Cannot find images in %s." % image_dir)


def RunOnArmMachine(ssh_obj):
    """Check if the AVD will be run on an ARM-based machine.

    Args:
        ssh_obj: An Ssh object.

    Returns:
        A boolean, whether the AVD will be run on an ARM-based machine.
    """
    cmd = "uname -m"
    cmd_output = ssh_obj.GetCmdOutput(cmd).strip()
    logger.debug("cmd: %s, cmd output: %s", cmd, cmd_output)
    return cmd_output == _ARM_MACHINE_TYPE


def FindVendorImages(image_dir):
    """Find vendor, vendor_dlkm, odm, and odm_dlkm image in build output dir.

    Args:
        image_dir: The directory to search for images.

    Returns:
        An object of VendorImagePaths.

    Raises:
        errors.GetLocalImageError if this function cannot find images.
    """
    image_dir = FindImageDir(image_dir)
    image_paths = []
    for image_name in _VENDOR_IMAGE_NAMES:
        image_path = os.path.join(image_dir, image_name)
        if not os.path.isfile(image_path):
            raise errors.GetLocalImageError(
                f"Cannot find {image_path} in {image_dir}.")
        image_paths.append(image_path)

    return VendorImagePaths(*image_paths)
