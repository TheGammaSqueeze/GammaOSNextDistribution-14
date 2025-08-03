#!/usr/bin/env python
#
# Copyright 2018 - The Android Open Source Project
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
r"""RemoteImageLocalInstance class.

Create class that is responsible for creating a local instance AVD with a
remote image.
"""
import logging
import os
import shutil
import subprocess
import sys

from acloud import errors
from acloud.create import create_common
from acloud.create import local_image_local_instance
from acloud.internal import constants
from acloud.internal.lib import android_build_client
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.setup import setup_common


logger = logging.getLogger(__name__)

# Download remote image variables.
_CUTTLEFISH_COMMON_BIN_PATH = "/usr/lib/cuttlefish-common/bin/"
_CONFIRM_DOWNLOAD_DIR = ("Download dir %(download_dir)s does not have enough "
                         "space (available space %(available_space)sGB, "
                         "require %(required_space)sGB).\nPlease enter "
                         "alternate path or 'q' to exit: ")
_HOME_FOLDER = os.path.expanduser("~")
# The downloaded image artifacts will take up ~8G:
#   $du -lh --time $ANDROID_PRODUCT_OUT/aosp_cf_x86_phone-img-eng.XXX.zip
#   422M
# And decompressed becomes 7.2G (as of 11/2018).
# Let's add an extra buffer (~2G) to make sure user has enough disk space
# for the downloaded image artifacts.
_REQUIRED_SPACE = 10

_SYSTEM_MIX_IMAGE_DIR = "mix_image_{build_id}"


def _ShouldClearFetchDir(fetch_dir, avd_spec, fetch_cvd_args_str,
                         fetch_cvd_args_file):
    """Check if the previous fetch directory should be removed.

    The fetch directory would be removed when the user explicitly sets the
    --force-sync flag, or when the fetch_cvd_args_str changed.

    Args:
        fetch_dir: String, path to the fetch directory.
        avd_spec: AVDSpec object that tells us what we're going to create.
        fetch_cvd_args_str: String, args for current fetch_cvd command.
        fetch_cvd_args_file: String, path to file of previous fetch_cvd args.

    Returns:
        Boolean. True if the fetch directory should be removed.
    """
    if not os.path.exists(fetch_dir):
        return False
    if avd_spec.force_sync:
        return True

    if not os.path.exists(fetch_cvd_args_file):
        return True
    with open(fetch_cvd_args_file, "r") as f:
        return fetch_cvd_args_str != f.read()


@utils.TimeExecute(function_description="Downloading Android Build image")
def DownloadAndProcessImageFiles(avd_spec):
    """Download the CF image artifacts and process them.

    To download rom images, Acloud would download the tool fetch_cvd that can
    help process mixed build images, and store the fetch_cvd_build_args in
    FETCH_CVD_ARGS_FILE when the fetch command executes successfully. Next
    time when this function is called with the same image_download_dir, the
    FETCH_CVD_ARGS_FILE would be used to check whether this image_download_dir
    can be reused or not.

    Args:
        avd_spec: AVDSpec object that tells us what we're going to create.

    Returns:
        extract_path: String, path to image folder.

    Raises:
        errors.GetRemoteImageError: Fails to download rom images.
    """
    cfg = avd_spec.cfg
    build_id = avd_spec.remote_image[constants.BUILD_ID]
    build_target = avd_spec.remote_image[constants.BUILD_TARGET]

    extract_path = os.path.join(
        avd_spec.image_download_dir,
        constants.TEMP_ARTIFACTS_FOLDER,
        build_id + build_target)

    logger.debug("Extract path: %s", extract_path)

    build_api = (
        android_build_client.AndroidBuildClient(auth.CreateCredentials(cfg)))
    fetch_cvd_build_args = build_api.GetFetchBuildArgs(
        avd_spec.remote_image,
        avd_spec.system_build_info,
        avd_spec.kernel_build_info,
        avd_spec.boot_build_info,
        avd_spec.bootloader_build_info,
        avd_spec.ota_build_info,
        avd_spec.host_package_build_info)

    fetch_cvd_args_str = " ".join(fetch_cvd_build_args)
    fetch_cvd_args_file = os.path.join(extract_path,
                                       constants.FETCH_CVD_ARGS_FILE)
    if _ShouldClearFetchDir(extract_path, avd_spec, fetch_cvd_args_str,
                            fetch_cvd_args_file):
        shutil.rmtree(extract_path)

    if not os.path.exists(extract_path):
        os.makedirs(extract_path)

        # Download rom images via fetch_cvd
        fetch_cvd = os.path.join(extract_path, constants.FETCH_CVD)
        build_api.DownloadFetchcvd(fetch_cvd, avd_spec.fetch_cvd_version)
        creds_cache_file = os.path.join(_HOME_FOLDER, cfg.creds_cache_file)
        fetch_cvd_cert_arg = build_api.GetFetchCertArg(creds_cache_file)
        fetch_cvd_args = [fetch_cvd, "-directory=%s" % extract_path,
                          fetch_cvd_cert_arg]
        fetch_cvd_args.extend(fetch_cvd_build_args)
        logger.debug("Download images command: %s", fetch_cvd_args)
        try:
            subprocess.check_call(fetch_cvd_args)
        except subprocess.CalledProcessError as e:
            raise errors.GetRemoteImageError("Fails to download images: %s" % e)

        # Save the fetch cvd build args when the fetch command succeeds
        with open(fetch_cvd_args_file, "w") as output:
            output.write(fetch_cvd_args_str)

    return extract_path


def ConfirmDownloadRemoteImageDir(download_dir):
    """Confirm download remote image directory.

    If available space of download_dir is less than _REQUIRED_SPACE, ask
    the user to choose a different download dir or to exit out since acloud will
    fail to download the artifacts due to insufficient disk space.

    Args:
        download_dir: String, a directory for download and decompress.

    Returns:
        String, Specific download directory when user confirm to change.
    """
    while True:
        download_dir = os.path.expanduser(download_dir)
        if not os.path.exists(download_dir):
            answer = utils.InteractWithQuestion(
                "No such directory %s.\nEnter 'y' to create it, enter "
                "anything else to exit out[y/N]: " % download_dir)
            if answer.lower() == "y":
                os.makedirs(download_dir)
            else:
                sys.exit(constants.EXIT_BY_USER)

        stat = os.statvfs(download_dir)
        available_space = stat.f_bavail*stat.f_bsize/(1024)**3
        if available_space < _REQUIRED_SPACE:
            download_dir = utils.InteractWithQuestion(
                _CONFIRM_DOWNLOAD_DIR % {"download_dir":download_dir,
                                         "available_space":available_space,
                                         "required_space":_REQUIRED_SPACE})
            if download_dir.lower() == "q":
                sys.exit(constants.EXIT_BY_USER)
        else:
            return download_dir


class RemoteImageLocalInstance(local_image_local_instance.LocalImageLocalInstance):
    """Create class for a remote image local instance AVD.

    RemoteImageLocalInstance just defines logic in downloading the remote image
    artifacts and leverages the existing logic to launch a local instance in
    LocalImageLocalInstance.
    """

    # pylint: disable=too-many-locals
    def GetImageArtifactsPath(self, avd_spec):
        """Download the image artifacts and return the paths to them.

        Args:
            avd_spec: AVDSpec object that tells us what we're going to create.

        Raises:
            errors.NoCuttlefishCommonInstalled: cuttlefish-common doesn't install.

        Returns:
            local_image_local_instance.ArtifactPaths object.
        """
        if not setup_common.PackageInstalled("cuttlefish-common"):
            raise errors.NoCuttlefishCommonInstalled(
                "Package [cuttlefish-common] is not installed!\n"
                "Please run 'acloud setup --host' to install.")

        avd_spec.image_download_dir = ConfirmDownloadRemoteImageDir(
            avd_spec.image_download_dir)

        image_dir = DownloadAndProcessImageFiles(avd_spec)
        launch_cvd_path = os.path.join(image_dir, "bin",
                                       constants.CMD_LAUNCH_CVD)
        if not os.path.exists(launch_cvd_path):
            raise errors.GetCvdLocalHostPackageError(
                "No launch_cvd found. Please check downloaded artifacts dir: %s"
                % image_dir)

        mix_image_dir = None
        misc_info_path = None
        ota_tools_dir = None
        system_image_path = None
        system_ext_image_path = None
        product_image_path = None
        boot_image_path = None
        vendor_boot_image_path = None
        kernel_image_path = None
        initramfs_image_path = None
        vendor_image_path = None
        vendor_dlkm_image_path = None
        odm_image_path = None
        odm_dlkm_image_path = None
        host_bins_path = image_dir
        if avd_spec.local_system_image or avd_spec.local_vendor_image:
            build_id = avd_spec.remote_image[constants.BUILD_ID]
            build_target = avd_spec.remote_image[constants.BUILD_TARGET]
            mix_image_dir = os.path.join(
                image_dir, _SYSTEM_MIX_IMAGE_DIR.format(build_id=build_id))
            if not os.path.exists(mix_image_dir):
                os.makedirs(mix_image_dir)
                create_common.DownloadRemoteArtifact(
                    avd_spec.cfg, build_target, build_id,
                    cvd_utils.GetMixBuildTargetFilename(
                        build_target, build_id),
                    mix_image_dir, decompress=True)
            misc_info_path = cvd_utils.FindMiscInfo(mix_image_dir)
            mix_image_dir = cvd_utils.FindImageDir(mix_image_dir)
            tool_dirs = (avd_spec.local_tool_dirs +
                         create_common.GetNonEmptyEnvVars(
                             constants.ENV_ANDROID_SOONG_HOST_OUT,
                             constants.ENV_ANDROID_HOST_OUT))
            ota_tools_dir = os.path.abspath(
                ota_tools.FindOtaToolsDir(tool_dirs))

            # When using local vendor image, use cvd in local-tool if it
            # exists. Fall back to downloaded tools in case it's missing

            if avd_spec.local_vendor_image and avd_spec.local_tool_dirs:
                try:
                    host_bins_path = self._FindCvdHostBinaries(tool_dirs)
                except errors.GetCvdLocalHostPackageError:
                    logger.debug("fall back to downloaded cvd host binaries")

        if avd_spec.local_system_image:
            (
                system_image_path,
                system_ext_image_path,
                product_image_path,
            ) = create_common.FindSystemImages(avd_spec.local_system_image)

        if avd_spec.local_kernel_image:
            (
                boot_image_path,
                vendor_boot_image_path,
                kernel_image_path,
                initramfs_image_path,
            ) = self.FindBootOrKernelImages(
                os.path.abspath(avd_spec.local_kernel_image))

        if avd_spec.local_vendor_image:
            vendor_image_paths = cvd_utils.FindVendorImages(
                avd_spec.local_vendor_image)
            vendor_image_path = vendor_image_paths.vendor
            vendor_dlkm_image_path = vendor_image_paths.vendor_dlkm
            odm_image_path = vendor_image_paths.odm
            odm_dlkm_image_path = vendor_image_paths.odm_dlkm

        return local_image_local_instance.ArtifactPaths(
            image_dir=mix_image_dir or image_dir,
            host_bins=host_bins_path,
            host_artifacts=image_dir,
            misc_info=misc_info_path,
            ota_tools_dir=ota_tools_dir,
            system_image=system_image_path,
            system_ext_image=system_ext_image_path,
            product_image=product_image_path,
            vendor_image=vendor_image_path,
            vendor_dlkm_image=vendor_dlkm_image_path,
            odm_image=odm_image_path,
            odm_dlkm_image=odm_dlkm_image_path,
            boot_image=boot_image_path,
            vendor_boot_image=vendor_boot_image_path,
            kernel_image=kernel_image_path,
            initramfs_image=initramfs_image_path)
