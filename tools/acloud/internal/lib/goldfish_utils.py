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

"""Utility functions that process goldfish images and arguments."""

import os
import re
import shutil

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import ota_tools


# File names under working directory.
_UNPACK_DIR_NAME = "unpacked_boot_img"
_MIXED_RAMDISK_IMAGE_NAME = "mixed_ramdisk"
# File names in unpacked boot image.
_UNPACKED_KERNEL_IMAGE_NAME = "kernel"
_UNPACKED_RAMDISK_IMAGE_NAME = "ramdisk"
# File names in a build environment or an SDK repository.
SYSTEM_QEMU_IMAGE_NAME = "system-qemu.img"
VERIFIED_BOOT_PARAMS_FILE_NAME = "VerifiedBootParams.textproto"
_SDK_REPO_SYSTEM_IMAGE_NAME = "system.img"
_MISC_INFO_FILE_NAME = "misc_info.txt"
_SYSTEM_QEMU_CONFIG_FILE_NAME = "system-qemu-config.txt"
# File names in the search order of emulator.
_DISK_IMAGE_NAMES = (SYSTEM_QEMU_IMAGE_NAME, _SDK_REPO_SYSTEM_IMAGE_NAME)
_KERNEL_IMAGE_NAMES = ("kernel-ranchu", "kernel-ranchu-64", "kernel")
_RAMDISK_IMAGE_NAMES = ("ramdisk-qemu.img", "ramdisk.img")
_SYSTEM_DLKM_IMAGE_NAMES = (
    "system_dlkm.flatten.erofs.img",  # GKI artifact
    "system_dlkm.flatten.ext4.img",  # GKI artifact
    "system_dlkm.img",  # goldfish artifact
)
# Remote host instance name.
_REMOTE_HOST_INSTANCE_NAME_FORMAT = (
    "host-goldfish-%(ip_addr)s-%(console_port)s-%(build_info)s")
_REMOTE_HOST_INSTANCE_NAME_PATTERN = re.compile(
    r"host-goldfish-(?P<ip_addr>[\d.]+)-(?P<console_port>\d+)-.+")


def _FindFileByNames(parent_dir, names):
    """Find file under a directory by names.

    Args:
        parent_dir: The directory to find the file in.
        names: A list of file names.

    Returns:
        The path to the first existing file in the list.

    Raises:
        errors.GetLocalImageError if none of the files exist.
    """
    for name in names:
        path = os.path.join(parent_dir, name)
        if os.path.isfile(path):
            return path
    raise errors.GetLocalImageError("No %s in %s." %
                                    (", ".join(names), parent_dir))


def _UnpackBootImage(output_dir, boot_image_path, ota):
    """Unpack a boot image and find kernel images.

    Args:
        output_dir: The directory where the boot image is unpacked.
        boot_image_path: The path to the boot image.
        ota: An instance of ota_tools.OtaTools.

    Returns:
        The kernel image path and the ramdisk image path.

    Raises:
        errors.GetLocalImageError if the kernel or the ramdisk is not found.
    """
    ota.UnpackBootImg(output_dir, boot_image_path)

    kernel_path = os.path.join(output_dir, _UNPACKED_KERNEL_IMAGE_NAME)
    ramdisk_path = os.path.join(output_dir, _UNPACKED_RAMDISK_IMAGE_NAME)
    if not os.path.isfile(kernel_path):
        raise errors.GetLocalImageError("No kernel in %s." % boot_image_path)
    if not os.path.isfile(ramdisk_path):
        raise errors.GetLocalImageError("No ramdisk in %s." % boot_image_path)
    return kernel_path, ramdisk_path


def _MixRamdiskImages(output_path, original_ramdisk_path,
                      boot_ramdisk_path):
    """Mix an emulator ramdisk with a boot ramdisk.

    An emulator ramdisk consists of a boot ramdisk and a vendor ramdisk.
    This method overlays a new boot ramdisk on the emulator ramdisk by
    concatenating them.

    Args:
        output_path: The path to the output ramdisk.
        original_ramdisk_path: The path to the emulator ramdisk.
        boot_ramdisk_path: The path to the boot ramdisk.
    """
    with open(output_path, "wb") as mixed_ramdisk:
        with open(original_ramdisk_path, "rb") as ramdisk:
            shutil.copyfileobj(ramdisk, mixed_ramdisk)
        with open(boot_ramdisk_path, "rb") as ramdisk:
            shutil.copyfileobj(ramdisk, mixed_ramdisk)


def MixWithBootImage(output_dir, image_dir, boot_image_path, ota):
    """Mix emulator kernel images with a boot image.

    Args:
        output_dir: The directory containing the output and intermediate files.
        image_dir: The directory containing emulator kernel and ramdisk images.
        boot_image_path: The path to the boot image.
        ota: An instance of ota_tools.OtaTools.

    Returns:
        The paths to the kernel and ramdisk images in output_dir.

    Raises:
        errors.GetLocalImageError if any image is not found.
    """
    unpack_dir = os.path.join(output_dir, _UNPACK_DIR_NAME)
    if os.path.exists(unpack_dir):
        shutil.rmtree(unpack_dir)
    os.makedirs(unpack_dir, exist_ok=True)

    kernel_path, boot_ramdisk_path = _UnpackBootImage(
        unpack_dir, boot_image_path, ota)
    # The ramdisk unpacked from boot_image_path does not include emulator's
    # kernel modules. The ramdisk in image_dir contains the modules. This
    # method mixes the two ramdisks.
    mixed_ramdisk_path = os.path.join(output_dir, _MIXED_RAMDISK_IMAGE_NAME)
    original_ramdisk_path = _FindFileByNames(image_dir, _RAMDISK_IMAGE_NAMES)
    _MixRamdiskImages(mixed_ramdisk_path, original_ramdisk_path,
                      boot_ramdisk_path)
    return kernel_path, mixed_ramdisk_path


def FindKernelImages(image_dir):
    """Find emulator kernel images in a directory.

    Args:
        image_dir: The directory to find the images in.

    Returns:
        The paths to the kernel image and the ramdisk image.

    Raises:
        errors.GetLocalImageError if any image is not found.
    """
    return (_FindFileByNames(image_dir, _KERNEL_IMAGE_NAMES),
            _FindFileByNames(image_dir, _RAMDISK_IMAGE_NAMES))


def FindSystemDlkmImage(search_path):
    """Find system_dlkm image in a path.

    Args:
        search_path: A path to an image file or an image directory.

    Returns:
        The system_dlkm image path.

    Raises:
        errors.GetLocalImageError if search_path does not contain a
        system_dlkm image.
    """
    return (search_path if os.path.isfile(search_path) else
            _FindFileByNames(search_path, _SYSTEM_DLKM_IMAGE_NAMES))


def FindDiskImage(image_dir):
    """Find an emulator disk image in a directory.

    Args:
        image_dir: The directory to find the image in.

    Returns:
        The path to the disk image.

    Raises:
        errors.GetLocalImageError if the image is not found.
    """
    return _FindFileByNames(image_dir, _DISK_IMAGE_NAMES)


def MixDiskImage(output_dir, image_dir, system_image_path,
                 system_dlkm_image_path, ota):
    """Mix emulator images into a disk image.

    Args:
        output_dir: The path to the output directory.
        image_dir: The input directory that provides images except
                   system.img.
        system_image_path: A string or None, the system image path.
        system_dlkm_image_path: A string or None, the system_dlkm image path.
        ota: An instance of ota_tools.OtaTools.

    Returns:
        The path to the mixed disk image in output_dir.

    Raises:
        errors.GetLocalImageError if any required file is not found.
    """
    os.makedirs(output_dir, exist_ok=True)

    # Create the super image.
    mixed_super_image_path = os.path.join(output_dir, "mixed_super.img")
    ota.BuildSuperImage(
        mixed_super_image_path,
        _FindFileByNames(image_dir, [_MISC_INFO_FILE_NAME]),
        lambda partition: ota_tools.GetImageForPartition(
            partition, image_dir,
            system=system_image_path,
            system_dlkm=system_dlkm_image_path))

    # Create the vbmeta image.
    vbmeta_image_path = os.path.join(output_dir, "disabled_vbmeta.img")
    ota.MakeDisabledVbmetaImage(vbmeta_image_path)

    # Create the disk image.
    disk_image = os.path.join(output_dir, "mixed_disk.img")
    ota.MkCombinedImg(
        disk_image,
        _FindFileByNames(image_dir, [_SYSTEM_QEMU_CONFIG_FILE_NAME]),
        lambda partition: ota_tools.GetImageForPartition(
            partition, image_dir, super=mixed_super_image_path,
            vbmeta=vbmeta_image_path))
    return disk_image


def FormatRemoteHostInstanceName(ip_addr, console_port, build_info):
    """Convert address and build info to a remote host instance name.

    Args:
        ip_addr: A string, the IP address of the host.
        console_port: An integer, the emulator console port.
        build_info: A dict containing the build ID and target.

    Returns:
        A string, the instance name.
    """
    build_id = build_info.get(constants.BUILD_ID)
    build_target = build_info.get(constants.BUILD_TARGET)
    build_info_str = (f"{build_id}-{build_target}" if
                      build_id and build_target else
                      "userbuild")
    return _REMOTE_HOST_INSTANCE_NAME_FORMAT % {
        "ip_addr": ip_addr,
        "console_port": console_port,
        "build_info": build_info_str,
    }


def ParseRemoteHostConsoleAddress(instance_name):
    """Parse emulator console address from a remote host instance name.

    Args:
        instance_name: A string, the instance name.

    Returns:
        The IP address as a string and the console port as an integer.
        None if the name does not represent a goldfish instance on remote host.
    """
    match = _REMOTE_HOST_INSTANCE_NAME_PATTERN.fullmatch(instance_name)
    return ((match.group("ip_addr"), int(match.group("console_port")))
            if match else None)


def ConvertAvdSpecToArgs(avd_spec):
    """Convert hardware specification to emulator arguments.

    Args:
        avd_spec: The AvdSpec object.

    Returns:
        A list of strings, the arguments.
    """
    args = []
    if avd_spec.gpu:
        args.extend(("-gpu", avd_spec.gpu))

    if not avd_spec.hw_customize:
        return args

    cores = avd_spec.hw_property.get(constants.HW_ALIAS_CPUS)
    if cores:
        args.extend(("-cores", cores))
    x_res = avd_spec.hw_property.get(constants.HW_X_RES)
    y_res = avd_spec.hw_property.get(constants.HW_Y_RES)
    if x_res and y_res:
        args.extend(("-skin", ("%sx%s" % (x_res, y_res))))
    dpi = avd_spec.hw_property.get(constants.HW_ALIAS_DPI)
    if dpi:
        args.extend(("-dpi-device", dpi))
    memory_size_mb = avd_spec.hw_property.get(constants.HW_ALIAS_MEMORY)
    if memory_size_mb:
        args.extend(("-memory", memory_size_mb))
    userdata_size_mb = avd_spec.hw_property.get(constants.HW_ALIAS_DISK)
    if userdata_size_mb:
        args.extend(("-partition-size", userdata_size_mb))

    return args
