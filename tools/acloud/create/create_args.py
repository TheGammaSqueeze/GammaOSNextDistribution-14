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
r"""Create args.

Defines the create arg parser that holds create specific args.
"""

import argparse
import logging
import os

from acloud import errors
from acloud.create import create_common
from acloud.internal import constants
from acloud.internal.lib import utils

logger = logging.getLogger(__name__)
_DEFAULT_GPU = "default"
CMD_CREATE = "create"


# TODO: Add this into main create args once create_cf/gf is deprecated.
# pylint: disable=too-many-statements
def AddCommonCreateArgs(parser):
    """Adds arguments common to create parsers.

    Args:
        parser: ArgumentParser object, used to parse flags.
    """
    parser.add_argument(
        "--num",
        type=int,
        dest="num",
        required=False,
        default=1,
        help="Number of instances to create.")
    parser.add_argument(
        "--serial-log-file",
        type=str,
        dest="serial_log_file",
        required=False,
        help="Path to a *tar.gz file where serial logs will be saved "
             "when a device fails on boot.")
    parser.add_argument(
        "--autoconnect",
        type=str,
        nargs="?",
        const=constants.INS_KEY_WEBRTC,
        dest="autoconnect",
        required=False,
        choices=[constants.INS_KEY_VNC, constants.INS_KEY_ADB,
                 constants.INS_KEY_WEBRTC],
        help="Determines to establish a tunnel forwarding adb/vnc and "
             "launch VNC/webrtc. Establish a tunnel forwarding adb and vnc "
             "then launch vnc if --autoconnect vnc is provided. Establish a "
             "tunnel forwarding adb if --autoconnect adb is provided. "
             "Establish a tunnel forwarding adb and auto-launch on the browser "
             "if --autoconnect webrtc is provided. For local goldfish "
             "instance, create a window.")
    parser.add_argument(
        "--no-autoconnect",
        action="store_false",
        dest="autoconnect",
        required=False,
        help="Will not automatically create ssh tunnels forwarding adb & vnc "
             "when instance created.")
    parser.set_defaults(autoconnect=constants.INS_KEY_WEBRTC)
    parser.add_argument(
        "--unlock",
        action="store_true",
        dest="unlock_screen",
        required=False,
        default=False,
        help="This can unlock screen after invoke vnc client.")
    parser.add_argument(
        "--report-internal-ip",
        action="store_true",
        dest="report_internal_ip",
        required=False,
        help="Report internal ip of the created instance instead of external "
             "ip. Using the internal ip is used when connecting from another "
             "GCE instance.")
    parser.add_argument(
        "--disable-external-ip",
        action="store_true",
        dest="disable_external_ip",
        required=False,
        help="Disable the external ip of the created instance.")
    parser.add_argument(
        "--extra-files",
        nargs='+',
        type=str,
        dest="extra_files",
        required=False,
        help="Upload the extra files into GCE instance. e.g. "
             "/path/to/file_in_local,/path/to/file_in_gce")
    parser.add_argument(
        "--network",
        type=str,
        dest="network",
        required=False,
        help="Set the network the GCE instance will utilize.")
    parser.add_argument(
        "--skip-pre-run-check",
        action="store_true",
        dest="skip_pre_run_check",
        required=False,
        help="Skip the pre-run check.")
    parser.add_argument(
        "--force-sync",
        action="store_true",
        dest="force_sync",
        required=False,
        help="Force to sync image files from Android Build servers even if "
             "they are already existed for local instance mode.")
    parser.add_argument(
        "--boot-timeout",
        dest="boot_timeout_secs",
        type=int,
        required=False,
        help="The maximum time in seconds used to wait for the AVD to download "
             "artifacts and boot.")
    parser.add_argument(
        "--wait-for-ins-stable",
        dest="ins_timeout_secs",
        type=int,
        required=False,
        help="The maximum time in seconds used to wait for the instance boot "
             "up. The default value to wait for instance up time is 300 secs.")
    parser.add_argument(
        "--build-target",
        type=str,
        dest="build_target",
        help="Android build target, e.g. aosp_cf_x86_64_phone-userdebug, "
             "or short names: phone, tablet, or tablet_mobile.")
    parser.add_argument(
        "--branch",
        type=str,
        dest="branch",
        help="Android branch, e.g. mnc-dev or git_mnc-dev")
    parser.add_argument(
        "--build-id",
        type=str,
        dest="build_id",
        help="Android build id, e.g. 2145099, P2804227")
    parser.add_argument(
        "--bootloader-branch",
        type=str,
        dest="bootloader_branch",
        help="'cuttlefish only' Branch to consume the bootloader from.",
        required=False)
    parser.add_argument(
        "--bootloader-build-id",
        type=str,
        dest="bootloader_build_id",
        help="'cuttlefish only' Bootloader build id, e.g. P2804227",
        required=False)
    parser.add_argument(
        "--bootloader-build-target",
        type=str,
        dest="bootloader_build_target",
        help="'cuttlefish only' Bootloader build target.",
        required=False)
    parser.add_argument(
        "--kernel-build-id",
        type=str,
        dest="kernel_build_id",
        required=False,
        help="Android kernel build id, e.g. 4586590. This is to test a new"
        " kernel build with a particular Android build (--build-id). If neither"
        " kernel-branch nor kernel-build-id are specified, the kernel that's"
        " bundled with the Android build would be used.")
    parser.add_argument(
        "--kernel-branch",
        type=str,
        dest="kernel_branch",
        required=False,
        help="Android kernel build branch name, e.g."
        " kernel-common-android-4.14. This is to test a new kernel build with a"
        " particular Android build (--build-id). If specified without"
        " specifying kernel-build-id, the last green build in the branch will"
        " be used. If neither kernel-branch nor kernel-build-id are specified,"
        " the kernel that's bundled with the Android build would be used.")
    parser.add_argument(
        "--kernel-build-target",
        type=str,
        dest="kernel_build_target",
        default="kernel",
        help="Kernel build target, specify if different from 'kernel'")
    parser.add_argument(
        "--boot-build-id",
        type=str,
        dest="boot_build_id",
        required=False,
        help="Boot image build ID, e.g., 8747889, 8748012.")
    parser.add_argument(
        "--boot-branch",
        type=str,
        dest="boot_branch",
        required=False,
        help="Boot image branch, e.g., aosp-gki13-boot-release, aosp-master.")
    parser.add_argument(
        "--boot-build-target",
        type=str,
        dest="boot_build_target",
        required=False,
        help="Boot image build target, "
        "e.g., gki_x86_64-userdebug, aosp_cf_x86_64_phone-userdebug.")
    parser.add_argument(
        "--boot-artifact",
        type=str,
        dest="boot_artifact",
        required=False,
        help="The name of the boot image to be retrieved from Android build, "
        "e.g., boot-5.10.img, boot.img.")
    parser.add_argument(
        "--ota-branch",
        type=str,
        dest="ota_branch",
        required=False,
        help="'cuttlefish only' OTA tools branch name. e.g. aosp-master")
    parser.add_argument(
        "--ota-build-id",
        type=str,
        dest="ota_build_id",
        required=False,
        help="'cuttlefish only' OTA tools build id, e.g. 2145099, P2804227")
    parser.add_argument(
        "--ota-build-target",
        type=str,
        dest="ota_build_target",
        required=False,
        help="'cuttlefish only' OTA tools build target, e.g. "
        "cf_x86_64_phone-userdebug.")
    parser.add_argument(
        "--host-package-branch", "--host_package_branch",
        type=str,
        dest="host_package_branch",
        required=False,
        help="'cuttlefish only' Host package branch name. e.g. aosp-main")
    parser.add_argument(
        "--host-package-build-id", "--host_package_build_id",
        type=str,
        dest="host_package_build_id",
        required=False,
        help="'cuttlefish only' Host package build id, e.g. 2145099, P2804227")
    parser.add_argument(
        "--host-package-build-target", "--host_package_build_target",
        type=str,
        dest="host_package_build_target",
        required=False,
        help="'cuttlefish only' Host package build target, e.g. "
        "cf_x86_64_phone-userdebug.")
    parser.add_argument(
        "--system-branch",
        type=str,
        dest="system_branch",
        help="'cuttlefish only' Branch to consume the system image (system.img) "
        "from, will default to what is defined by --branch. "
        "That feature allows to (automatically) test various combinations "
        "of vendor.img (CF, e.g.) and system images (GSI, e.g.). ",
        required=False)
    parser.add_argument(
        "--system-build-id",
        type=str,
        dest="system_build_id",
        help="'cuttlefish only' System image build id, e.g. 2145099, P2804227",
        required=False)
    parser.add_argument(
        "--system-build-target",
        type=str,
        dest="system_build_target",
        help="'cuttlefish only' System image build target, specify if different "
        "from --build-target",
        required=False)
    parser.add_argument(
        "--launch-args",
        type=str,
        dest="launch_args",
        help="'cuttlefish only' Add extra args to launch_cvd command.",
        required=False)
    parser.add_argument(
        "--pet-name",
        "--webrtc_device_id",
        type=str,
        dest="webrtc_device_id",
        help="'cuttlefish only' Give the pet name of the instance.",
        required=False)
    parser.add_argument(
        "--gce-metadata",
        type=str,
        dest="gce_metadata",
        default=None,
        help="'GCE instance only' Record data into GCE instance metadata with "
        "key-value pair format. e.g. id:12,name:unknown.")
    parser.add_argument(
        "--fetch_cvd-build-id",
        type=str,
        dest="fetch_cvd_build_id",
        required=False,
        help="'cuttlefish only' Build id of fetch_cvd, e.g. 2145099, P2804227")
    # TODO(146314062): Remove --multi-stage-launch after infra don't use this
    # args.
    parser.add_argument(
        "--multi-stage-launch",
        dest="multi_stage_launch",
        action="store_true",
        required=False,
        default=True,
        help="Enable the multi-stage cuttlefish launch.")
    parser.add_argument(
        "--no-multi-stage-launch",
        dest="multi_stage_launch",
        action="store_false",
        required=False,
        default=None,
        help="Disable the multi-stage cuttlefish launch.")
    parser.add_argument(
        "--no-pull-log",
        dest="no_pull_log",
        action="store_true",
        required=False,
        default=None,
        help="Disable auto download logs when AVD booting up failed.")
    parser.add_argument(
        "--no-mkcert",
        dest="mkcert",
        action="store_false",
        required=False,
        default=True,
        help="Disable mkcert setup process on the host.")
    # TODO(147335651): Add gpu in user config.
    # TODO(147335651): Support "--gpu" without giving any value.
    parser.add_argument(
        "--gpu",
        type=str,
        const=_DEFAULT_GPU,
        nargs="?",
        dest="gpu",
        required=False,
        default=None,
        help="GPU accelerator to use if any. e.g. nvidia-tesla-k80. For local "
             "instances, this arg without assigning any value is to enable "
             "local gpu support.")
    parser.add_argument(
        "--num-avds-per-instance",
        "--num-instances",
        "--num_instances",
        type=int,
        dest="num_avds_per_instance",
        required=False,
        default=1,
        help="'cuttlefish only' Create multiple cuttlefish AVDs in one local "
             "instance.")
    parser.add_argument(
        "--connect-hostname",
        action="store_true",
        dest="connect_hostname",
        required=False,
        default=False,
        help="Ssh connects to the GCE instance with hostname.")
    parser.add_argument(
        "--gce-only",
        action="store_true",
        dest="gce_only",
        required=False,
        default=False,
        help="Only create the GCE instance. It won't create virtual devices.")
    # Hide following args for users, it is only used in infra.
    parser.add_argument(
        "--local-instance-dir",
        dest="local_instance_dir",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--remote-image-dir",
        dest="remote_image_dir",
        required=False,
        # 'cuttlefish remote host only' Upload images and cvd host package to
        # the remote directory instead of the instance's own directory. If the
        # directory has been initialized, acloud ignores the image arguments
        # given by command line and reuses the images in the directory.
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--oxygen",
        action="store_true",
        dest="oxygen",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--zone",
        type=str,
        dest="zone",
        required=False,
        help=argparse.SUPPRESS)

    # TODO(b/118439885): Old arg formats to support transition, delete when
    # transistion is done.
    parser.add_argument(
        "--serial_log_file",
        type=str,
        dest="serial_log_file",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--build_id",
        type=str,
        dest="build_id",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--build_target",
        type=str,
        dest="build_target",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--system_branch",
        type=str,
        dest="system_branch",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--system_build_id",
        type=str,
        dest="system_build_id",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--system_build_target",
        type=str,
        dest="system_build_target",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--kernel_build_id",
        type=str,
        dest="kernel_build_id",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--kernel_branch",
        type=str,
        dest="kernel_branch",
        required=False,
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--kernel_build_target",
        type=str,
        dest="kernel_build_target",
        default="kernel",
        help=argparse.SUPPRESS)
    parser.add_argument(
        "--bootloader_branch",
        type=str,
        dest="bootloader_branch",
        help=argparse.SUPPRESS,
        required=False)
    parser.add_argument(
        "--bootloader_build_id",
        type=str,
        dest="bootloader_build_id",
        help=argparse.SUPPRESS,
        required=False)
    parser.add_argument(
        "--bootloader_build_target",
        type=str,
        dest="bootloader_build_target",
        help=argparse.SUPPRESS,
        required=False)
    parser.add_argument(
        "--fetch_cvd_build_id",
        type=str,
        dest="fetch_cvd_build_id",
        help=argparse.SUPPRESS,
        required=False)
    parser.add_argument(
        "--remote-fetch",
        action="store_true",
        dest="remote_fetch",
        required=False,
        default=None,
        help="'cuttlefish only' Fetch artifacts in remote host.")
    parser.add_argument(
        "--fetch-cvd-wrapper",
        dest="fetch_cvd_wrapper",
        type=str,
        required=False,
        help="'cuttlefish only' Fetch artifacts in remote host by a"
        " provided static executable fetch cvd wrapper file. "
        " (Still in experiment, this flag only works on lab hosts"
        " with special setup.)")


def GetCreateArgParser(subparser):
    """Return the create arg parser.

    Args:
       subparser: argparse.ArgumentParser that is attached to main acloud cmd.

    Returns:
        argparse.ArgumentParser with create options defined.
    """
    create_parser = subparser.add_parser(CMD_CREATE)
    create_parser.required = False
    create_parser.set_defaults(which=CMD_CREATE)
    # Use default=None to distinguish remote instance or local. The instance
    # type will be remote if the arg is not provided.
    create_parser.add_argument(
        "--local-instance",
        type=_PositiveInteger,
        const=0,
        metavar="ID",
        nargs="?",
        dest="local_instance",
        required=False,
        help="Create a local AVD instance using the resources associated with "
             "the ID. Choose an unused ID automatically if the value is "
             "not specified (primarily for infra usage).")
    create_parser.add_argument(
        "--adb-port", "-p",
        type=int,
        default=None,
        dest="adb_port",
        required=False,
        help="Specify port for adb forwarding.")
    create_parser.add_argument(
        "--base-instance-num",
        type=int,
        default=None,
        dest="base_instance_num",
        required=False,
        help="'cuttlefish only' The instance number of the created device.")
    create_parser.add_argument(
        "--avd-type",
        type=str,
        dest="avd_type",
        default=constants.TYPE_CF,
        choices=[constants.TYPE_GCE, constants.TYPE_CF, constants.TYPE_GF, constants.TYPE_CHEEPS,
                 constants.TYPE_FVP],
        help="Android Virtual Device type (default %s)." % constants.TYPE_CF)
    create_parser.add_argument(
        "--config", "--flavor",
        type=str,
        dest="flavor",
        help="The device flavor of the AVD (default %s). e.g. phone, tv, foldable."
        % constants.FLAVOR_PHONE)
    create_parser.add_argument(
        "--local-image",
        const=constants.FIND_IN_BUILD_ENV,
        type=str,
        dest="local_image",
        nargs="?",
        required=False,
        help="Use the locally built image for the AVD. Look for the image "
        "artifact in $ANDROID_PRODUCT_OUT if no args value is provided."
        "e.g --local-image or --local-image /path/to/dir or --local-image "
        "/path/to/file")
    create_parser.add_argument(
        "--local-kernel-image", "--local-boot-image",
        const=constants.FIND_IN_BUILD_ENV,
        type=str,
        dest="local_kernel_image",
        nargs="?",
        required=False,
        help="Use the locally built kernel and ramdisk for the AVD. Look "
        "for boot.img, vendor_boot.img, kernel, initramfs.img, etc. if the "
        "argument is a directory. Look for the images in $ANDROID_PRODUCT_OUT "
        "if no argument is provided. e.g., --local-kernel-image, "
        "--local-kernel-image /path/to/dir, or --local-kernel-image "
        "/path/to/boot.img")
    create_parser.add_argument(
        "--local-system-image",
        const=constants.FIND_IN_BUILD_ENV,
        type=str,
        dest="local_system_image",
        nargs="?",
        required=False,
        help="Use the locally built system images for the AVD. Look for the "
        "images in $ANDROID_PRODUCT_OUT if no args value is provided. "
        "e.g., --local-system-image, --local-system-image /path/to/dir, or "
        "--local-system-image /path/to/img")
    create_parser.add_argument(
        "--local-system_dlkm-image",
        const=constants.FIND_IN_BUILD_ENV,
        type=str,
        dest="local_system_dlkm_image",
        nargs="?",
        required=False,
        help="`remote host only` Use the locally built system_dlkm image for "
        "the AVD. Look for the image in $ANDROID_PRODUCT_OUT if no args value "
        "is provided.")
    create_parser.add_argument(
        "--local-vendor-image",
        const=constants.FIND_IN_BUILD_ENV,
        type=str,
        dest="local_vendor_image",
        nargs="?",
        required=False,
        help="'cuttlefish only' Use the locally built vendor images for the "
        "AVD. Look for vendor.img, vendor_dlkm.img, odm.img, and odm_dlkm.img "
        "if the argument is a directory. Look for the images in "
        "$ANDROID_PRODUCT_OUT if no argument is provided. e.g., "
        "--local-vendor-image, or --local-vendor-image /path/to/dir")
    create_parser.add_argument(
        "--local-tool",
        type=str,
        dest="local_tool",
        action="append",
        default=[],
        required=False,
        help="Use the tools in the specified directory to create local "
        "instances. The directory structure follows $ANDROID_SOONG_HOST_OUT "
        "or $ANDROID_EMULATOR_PREBUILTS.")
    create_parser.add_argument(
        "--cvd-host-package",
        type=str,
        dest="cvd_host_package",
        required=False,
        help="Use the specified path of the cvd host package to create "
        "instances. e.g. /path/cvd-host_package_v1.tar.gz")
    create_parser.add_argument(
        "--image-download-dir",
        type=str,
        dest="image_download_dir",
        required=False,
        help="Define remote image download directory, e.g. /usr/local/dl.")
    create_parser.add_argument(
        "--yes", "-y",
        action="store_true",
        dest="no_prompt",
        required=False,
        help=("Automatic yes to prompts. Assume 'yes' as answer to all prompts "
              "and run non-interactively."))
    create_parser.add_argument(
        "--reuse-gce",
        type=str,
        const=constants.SELECT_ONE_GCE_INSTANCE,
        nargs="?",
        dest="reuse_gce",
        required=False,
        help="'cuttlefish only' This can help users use their own instance. "
        "Reusing specific gce instance if --reuse-gce [instance_name] is "
        "provided. Select one gce instance to reuse if --reuse-gce is "
        "provided.")
    create_parser.add_argument(
        "--openwrt",
        action="store_true",
        dest="openwrt",
        required=False,
        help="'cuttlefish only' Create OpenWrt device when launching cuttlefish "
        "device.")
    create_parser.add_argument(
        "--use-launch_cvd",
        action="store_true",
        dest="use_launch_cvd",
        required=False,
        help="'cuttlefish only' Use launch_cvd to create cuttlefish devices.")
    create_parser.add_argument(
        "--host",
        type=str,
        dest="remote_host",
        default=None,
        help="'cuttlefish only' Provide host name to clean up the remote host. "
        "For example: '--host 1.1.1.1'")
    create_parser.add_argument(
        "--host-user",
        type=str,
        dest="host_user",
        default=constants.GCE_USER,
        help="'remote host only' Provide host user for logging in to the host. "
        "The default value is vsoc-01. For example: '--host 1.1.1.1 --host-user "
        "vsoc-02'")
    create_parser.add_argument(
        "--host-ssh-private-key-path",
        type=str,
        dest="host_ssh_private_key_path",
        default=None,
        help="'remote host only' Provide host key for login on on this host.")
    # User should not specify --spec and --hw_property at the same time.
    hw_spec_group = create_parser.add_mutually_exclusive_group()
    hw_spec_group.add_argument(
        "--hw-property",
        type=str,
        dest="hw_property",
        required=False,
        help="Supported HW properties and example values: %s" %
        constants.HW_PROPERTIES_CMD_EXAMPLE)
    hw_spec_group.add_argument(
        "--spec",
        type=str,
        dest="spec",
        required=False,
        choices=constants.SPEC_NAMES,
        help="The name of a pre-configured device spec that we are "
        "going to use.")
    create_parser.add_argument(
        "--disk-type",
        type=str,
        dest="disk_type",
        required=False,
        help="This is used to customize the GCE instance disk type, the "
        "default disk type is from the stable host image. Use pd-ssd or "
        "pd-standard to specify instance disk type.")
    create_parser.add_argument(
        "--stable-host-image-name",
        type=str,
        dest="stable_host_image_name",
        required=False,
        default=None,
        help=("'cuttlefish only' The Cuttlefish host image from which instances "
              "are launched. If specified here, the value set in Acloud config "
              "file will be overridden."))

    # Arguments for goldfish type.
    create_parser.add_argument(
        "--emulator-build-id",
        type=str,
        dest="emulator_build_id",
        required=False,
        help="'goldfish only' Emulator build ID used to run the images. "
        "e.g. 4669466.")
    create_parser.add_argument(
        "--emulator-build-target",
        dest="emulator_build_target",
        required=False,
        help="'goldfish remote host only' Emulator build target used to run "
        "the images. e.g. emulator-linux_x64_nolocationui.")
    create_parser.add_argument(
        "--emulator-zip",
        dest="emulator_zip",
        required=False,
        help="'goldfish remote host only' Emulator zip used to run the "
        "images. e.g., /path/sdk-repo-linux-emulator-1234567.zip.")

    # Arguments for cheeps type.
    create_parser.add_argument(
        "--stable-cheeps-host-image-name",
        type=str,
        dest="stable_cheeps_host_image_name",
        required=False,
        default=None,
        help=("'cheeps only' The Cheeps host image from which instances are "
              "launched. If specified here, the value set in Acloud config "
              "file will be overridden."))
    create_parser.add_argument(
        "--stable-cheeps-host-image-project",
        type=str,
        dest="stable_cheeps_host_image_project",
        required=False,
        default=None,
        help=("'cheeps only' The project hosting the specified Cheeps host "
              "image. If specified here, the value set in Acloud config file "
              "will be overridden."))
    create_parser.add_argument(
        "--user",
        type=str,
        dest="username",
        required=False,
        default=None,
        help="'cheeps only' username to log in to Chrome OS as.")
    create_parser.add_argument(
        "--password",
        type=str,
        dest="password",
        required=False,
        default=None,
        help="'cheeps only' password to log in to Chrome OS with.")
    create_parser.add_argument(
        "--betty-image",
        type=str,
        dest="cheeps_betty_image",
        required=False,
        default=None,
        help=("'cheeps only' The L1 betty version to use. Only makes sense "
              "when launching a controller image with "
              "stable-cheeps-host-image"))
    create_parser.add_argument(
        "--cheeps-feature",
        type=str,
        dest="cheeps_features",
        required=False,
        action="append",
        default=[],
        help=("'cheeps only' Cheeps feature to enable. Can be repeated."))

    AddCommonCreateArgs(create_parser)
    return create_parser


def _PositiveInteger(arg):
    """Convert an argument from a string to a positive integer."""
    try:
        value = int(arg)
    except ValueError as e:
        raise argparse.ArgumentTypeError(arg + " is not an integer.") from e
    if value <= 0:
        raise argparse.ArgumentTypeError(arg + " is not positive.")
    return value


def _VerifyLocalArgs(args):
    """Verify args starting with --local.

    Args:
        args: Namespace object from argparse.parse_args.

    Raises:
        errors.CheckPathError: Image path doesn't exist.
        errors.UnsupportedCreateArgs: The specified avd type does not support
                                      --local-system-image.
        errors.UnsupportedLocalInstanceId: Local instance ID is invalid.
    """
    if args.local_image and not os.path.exists(args.local_image):
        raise errors.CheckPathError(
            "Specified path doesn't exist: %s" % args.local_image)

    if args.local_instance_dir and not os.path.exists(args.local_instance_dir):
        raise errors.CheckPathError(
            "Specified path doesn't exist: %s" % args.local_instance_dir)

    if not (args.local_system_image is None or
            args.avd_type in (constants.TYPE_CF, constants.TYPE_GF)):
        raise errors.UnsupportedCreateArgs("%s instance does not support "
                                           "--local-system-image" %
                                           args.avd_type)
    # TODO(b/179340595): To support local image remote instance with kernel build.
    if args.local_instance is None and args.local_image is not None and (
            args.kernel_branch or args.kernel_build_id):
        raise errors.UnsupportedCreateArgs(
            "Acloud didn't support local image with specific kernel. "
            "Please download the specific kernel and put it into "
            "your local image folder: '%s'." % (
            args.local_image if args.local_image else
            utils.GetBuildEnvironmentVariable(constants.ENV_ANDROID_PRODUCT_OUT)))

    if (args.local_system_image and
            not os.path.exists(args.local_system_image)):
        raise errors.CheckPathError(
            "Specified path doesn't exist: %s" % args.local_system_image)

    for tool_dir in args.local_tool:
        if not os.path.exists(tool_dir):
            raise errors.CheckPathError(
                "Specified path doesn't exist: %s" % tool_dir)


def _VerifyHostArgs(args):
    """Verify args starting with --host.

    Args:
        args: Namespace object from argparse.parse_args.

    Raises:
        errors.UnsupportedCreateArgs: When a create arg is specified but
                                      unsupported for remote host mode.
    """
    if args.remote_host and args.local_instance is not None:
        raise errors.UnsupportedCreateArgs(
            "--host is not supported for local instance.")

    if args.remote_host and args.num > 1:
        raise errors.UnsupportedCreateArgs(
            "--num is not supported for remote host.")

    if args.host_user != constants.GCE_USER and args.remote_host is None:
        raise errors.UnsupportedCreateArgs(
            "--host-user is only supported for remote host.")

    if args.host_ssh_private_key_path and args.remote_host is None:
        raise errors.UnsupportedCreateArgs(
            "--host-ssh-private-key-path is only supported for remote host.")

    if args.remote_image_dir and args.remote_host is None:
        raise errors.UnsupportedCreateArgs(
            "--remote-image-dir is only supported for remote host.")


def _VerifyGoldfishArgs(args):
    """Verify goldfish args.

    Args:
        args: Namespace object from argparse.parse_args.

    Raises:
        errors.UnsupportedCreateArgs: When a create arg is specified but
                                      unsupported for goldfish.
    """
    goldfish_only_flags = [
        args.emulator_build_id,
        args.emulator_build_target,
        args.emulator_zip
    ]
    if args.avd_type != constants.TYPE_GF and any(goldfish_only_flags):
        raise errors.UnsupportedCreateArgs(
            f"--emulator-* is only valid with avd_type == {constants.TYPE_GF}")

    # Exclude kernel_build_target because the default value isn't empty.
    remote_kernel_flags = [
        args.kernel_build_id,
        args.kernel_branch,
    ]
    if args.avd_type == constants.TYPE_GF and any(remote_kernel_flags):
        raise errors.UnsupportedCreateArgs(
            "--kernel-* is not supported for goldfish.")

    remote_boot_flags = [
        args.boot_build_id,
        args.boot_build_target,
        args.boot_branch,
        args.boot_artifact,
    ]
    if (args.avd_type == constants.TYPE_GF and any(remote_boot_flags) and
            not all(remote_boot_flags)):
        raise errors.UnsupportedCreateArgs(
            "Either none or all of --boot-branch, --boot-build-target, "
            "--boot-build-id, and --boot-artifact must be specified for "
            "goldfish.")

    remote_system_flags = [
        args.system_build_target,
        args.system_build_id,
        args.system_branch,
    ]
    if (args.avd_type == constants.TYPE_GF and any(remote_system_flags) and
            not all(remote_system_flags)):
        raise errors.UnsupportedCreateArgs(
            "Either none or all of --system-branch, --system-build-target, "
            "and --system-build-id must be specified for goldfish.")

    remote_host_only_flags = remote_boot_flags + remote_system_flags
    if args.avd_type == constants.TYPE_GF and args.remote_host is None and any(
            remote_host_only_flags):
        raise errors.UnsupportedCreateArgs(
            "--boot-* and --system-* for goldfish are only supported for "
            "remote host.")


def VerifyArgs(args):
    """Verify args.

    Args:
        args: Namespace object from argparse.parse_args.

    Raises:
        errors.UnsupportedMultiAdbPort: multi adb port doesn't support.
        errors.UnsupportedCreateArgs: When a create arg is specified but
                                      unsupported for a particular avd type.
                                      (e.g. --system-build-id for gf)
    """
    # Verify that user specified flavor name is in support list.
    # We don't use argparse's builtin validation because we need to be able to
    # tell when a user doesn't specify a flavor.
    if args.flavor and args.flavor not in constants.ALL_FLAVORS:
        logger.debug("Flavor[%s] isn't in default support list: %s",
                     args.flavor, constants.ALL_FLAVORS)

    if args.avd_type not in (constants.TYPE_CF, constants.TYPE_GF):
        if args.system_branch or args.system_build_id or args.system_build_target:
            raise errors.UnsupportedCreateArgs(
                "--system-* args are not supported for AVD type: %s"
                % args.avd_type)

    if args.num > 1 and args.adb_port:
        raise errors.UnsupportedMultiAdbPort(
            "--adb-port is not supported for multi-devices.")

    if args.num > 1 and args.local_instance is not None:
        raise errors.UnsupportedCreateArgs(
            "--num is not supported for local instance.")

    if args.local_instance is None and args.gpu == _DEFAULT_GPU:
        raise errors.UnsupportedCreateArgs(
            "Please assign one gpu model for GCE instance. Reference: "
            "https://cloud.google.com/compute/docs/gpus")

    if args.adb_port:
        utils.CheckPortFree(args.adb_port)

    hw_properties = create_common.ParseKeyValuePairArgs(args.hw_property)
    for key in hw_properties:
        if key not in constants.HW_PROPERTIES:
            raise errors.InvalidHWPropertyError(
                "[%s] is an invalid hw property, supported values are:%s. "
                % (key, constants.HW_PROPERTIES))

    cheeps_only_flags = [args.stable_cheeps_host_image_name,
                         args.stable_cheeps_host_image_project,
                         args.username,
                         args.password,
                         args.cheeps_betty_image,
                         args.cheeps_features]
    if args.avd_type != constants.TYPE_CHEEPS and any(cheeps_only_flags):
        raise errors.UnsupportedCreateArgs(
            "--stable-cheeps-*, --betty-image, --cheeps-feature, --username "
            "and --password are only valid with avd_type == %s"
            % constants.TYPE_CHEEPS)
    if (args.username or args.password) and not (args.username and args.password):
        raise ValueError("--username and --password must both be set")
    if not args.autoconnect and args.unlock_screen:
        raise ValueError("--no-autoconnect and --unlock couldn't be "
                         "passed in together.")

    _VerifyGoldfishArgs(args)
    _VerifyLocalArgs(args)
    _VerifyHostArgs(args)
