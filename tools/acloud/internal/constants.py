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
"""This module holds constants used by the driver."""
BRANCH_PREFIX = "git_"
BUILD_TARGET_MAPPING = {
    # TODO: Add aosp goldfish targets and internal cf targets to vendor code
    # base.
    "aosp_phone": "aosp_cf_x86_64_phone-userdebug",
    "aosp_tablet": "aosp_cf_x86_tablet-userdebug",
}
SPEC_NAMES = {
    "nexus5", "nexus6", "nexus7_2012", "nexus7_2013", "nexus9", "nexus10"
}

DEFAULT_SERIAL_PORT = 1
LOGCAT_SERIAL_PORT = 2

# Remote image parameters
BUILD_TARGET = "build_target"
BUILD_BRANCH = "branch"
BUILD_ID = "build_id"
BUILD_ARTIFACT = "artifact"

# Special value of local image parameters
FIND_IN_BUILD_ENV = ""

# AVD types
TYPE_CHEEPS = "cheeps"
TYPE_CF = "cuttlefish"
TYPE_GCE = "gce"
TYPE_GF = "goldfish"
TYPE_FVP = "fvp"

# Image types
IMAGE_SRC_REMOTE = "remote_image"
IMAGE_SRC_LOCAL = "local_image"

# AVD types in build target
AVD_TYPES_MAPPING = {
    TYPE_GCE: "gce",
    TYPE_CF: "cf",
    TYPE_GF: "sdk",
    # Cheeps uses the cheets target.
    TYPE_CHEEPS: "cheets",
}

# Instance types
INSTANCE_TYPE_REMOTE = "remote"
INSTANCE_TYPE_LOCAL = "local"
INSTANCE_TYPE_HOST = "host"

# CF_AVD_BUILD_TARGET_MAPPING
CF_X86_PATTERN = "cf_x86"
CF_ARM_PATTERN = "cf_arm"
CF_AVD_BUILD_TARGET_PATTERN_MAPPING = {
    INSTANCE_TYPE_REMOTE: CF_X86_PATTERN,
    INSTANCE_TYPE_LOCAL: CF_X86_PATTERN,
    INSTANCE_TYPE_HOST: CF_ARM_PATTERN,
}

# Flavor types
FLAVOR_PHONE = "phone"
FLAVOR_AUTO = "auto"
FLAVOR_WEAR = "wear"
FLAVOR_TV = "tv"
FLAVOR_IOT = "iot"
FLAVOR_TABLET = "tablet"
FLAVOR_TABLET_3G = "tablet_3g"
FLAVOR_FOLDABLE = "foldable"
ALL_FLAVORS = [
    FLAVOR_PHONE, FLAVOR_AUTO, FLAVOR_WEAR, FLAVOR_TV, FLAVOR_IOT,
    FLAVOR_TABLET, FLAVOR_TABLET_3G, FLAVOR_FOLDABLE
]

# HW Property
HW_ALIAS_CPUS = "cpu"
HW_ALIAS_RESOLUTION = "resolution"
HW_ALIAS_DPI = "dpi"
HW_ALIAS_MEMORY = "memory"
HW_ALIAS_DISK = "disk"
HW_PROPERTIES_CMD_EXAMPLE = (
    f" {HW_ALIAS_CPUS}:2,{HW_ALIAS_RESOLUTION}:1280x700,{HW_ALIAS_DPI}:160,"
    f"{HW_ALIAS_MEMORY}:2g,{HW_ALIAS_DISK}:2g"
)
HW_PROPERTIES = [HW_ALIAS_CPUS, HW_ALIAS_RESOLUTION, HW_ALIAS_DPI,
                 HW_ALIAS_MEMORY, HW_ALIAS_DISK]
HW_X_RES = "x_res"
HW_Y_RES = "y_res"

USER_ANSWER_YES = {"y", "yes", "Y"}

# Cuttlefish groups
LIST_CF_USER_GROUPS = ["kvm", "cvdnetwork", "render"]

# Report keys
IP = "ip"
INSTANCE_NAME = "instance_name"
GCE_USER = "vsoc-01"
VNC_PORT = "vnc_port"
ADB_PORT = "adb_port"
WEBRTC_PORT = "webrtc_port"
DEVICE_SERIAL = "device_serial"
LOGS = "logs"
BASE_INSTANCE_NUM = "base_instance_num"
LAUNCH_CVD_COMMAND = "launch_cvd_command"
# For cuttlefish remote instances
CF_ADB_PORT = 6520
CF_VNC_PORT = 6444
# For cheeps remote instances
CHEEPS_ADB_PORT = 9222
CHEEPS_VNC_PORT = 5900
# For gce_x86_phones remote instances
GCE_ADB_PORT = 5555
GCE_VNC_PORT = 6444
# For ssh connect with GCE hostname
GCE_HOSTNAME = "gce_hostname"
# For goldfish remote instances
GF_ADB_PORT = 5555
GF_VNC_PORT = 6444
# For FVP remote instances (no VNC support)
FVP_ADB_PORT = 5555
# Maximum port number
MAX_PORT = 65535
# Time info to write in report.
TIME_ARTIFACT = "fetch_artifact_time"
TIME_GCE = "gce_create_time"
TIME_LAUNCH = "launch_cvd_time"

COMMAND_PS = ["ps", "aux"]
CMD_CVD = "cvd"
CMD_LAUNCH_CVD = "launch_cvd"
CMD_PGREP = "pgrep"
CMD_STOP_CVD = "stop_cvd"
CMD_RUN_CVD = "run_cvd"
ENV_ANDROID_BUILD_TOP = "ANDROID_BUILD_TOP"
ENV_ANDROID_EMULATOR_PREBUILTS = "ANDROID_EMULATOR_PREBUILTS"
ENV_ANDROID_HOST_OUT = "ANDROID_HOST_OUT"
ENV_ANDROID_PRODUCT_OUT = "ANDROID_PRODUCT_OUT"
ENV_ANDROID_SOONG_HOST_OUT = "ANDROID_SOONG_HOST_OUT"
ENV_ANDROID_TMP = "ANDROID_TMP"
ENV_BUILD_TARGET = "TARGET_PRODUCT"
# For cvd to run in compatible mode.
ENV_CVD_ACQUIRE_FILE_LOCK = "CVD_ACQUIRE_FILE_LOCK"
ENV_LAUNCHED_BY_ACLOUD = "LAUNCHED_BY_ACLOUD"

LOCALHOST = "0.0.0.0"
LOCALHOST_ADB_SERIAL = LOCALHOST + ":%d"
REMOTE_INSTANCE_ADB_SERIAL = "127.0.0.1:%s"

SSH_BIN = "ssh"
SCP_BIN = "scp"
ADB_BIN = "adb"
# Default timeout, the unit is seconds.
DEFAULT_SSH_TIMEOUT = 300
DEFAULT_CF_BOOT_TIMEOUT = 450

LABEL_CREATE_BY = "created_by"

# for list and delete cmd
INS_KEY_NAME = "name"
INS_KEY_FULLNAME = "full_name"
INS_KEY_STATUS = "status"
INS_KEY_DISPLAY = "display"
INS_KEY_IP = "ip"
INS_KEY_ADB = "adb"
INS_KEY_VNC = "vnc"
INS_KEY_WEBRTC = "webrtc"
INS_KEY_WEBRTC_PORT = "webrtc_port"
INS_KEY_WEBRTC_DEVICE_ID = "webrtc_device_id"
INS_KEY_CREATETIME = "creationTimestamp"
INS_KEY_AVD_TYPE = "avd_type"
INS_KEY_AVD_FLAVOR = "flavor"
INS_KEY_IS_LOCAL = "remote"
INS_KEY_ZONE = "zone"
INS_STATUS_RUNNING = "RUNNING"
ENV_CUTTLEFISH_CONFIG_FILE = "CUTTLEFISH_CONFIG_FILE"
ENV_CUTTLEFISH_INSTANCE = "CUTTLEFISH_INSTANCE"
ENV_CVD_HOME = "HOME"
ANDROID_INFO_FILE = "android-info.txt"
CUTTLEFISH_CONFIG_FILE = "cuttlefish_config.json"

TEMP_ARTIFACTS_FOLDER = "acloud_image_artifacts"
CVD_HOST_PACKAGE = "cvd-host_package"
CVD_HOST_TARBALL = "cvd-host_package.tar.gz"
# cvd tools symbolic link name of local instance.
CVD_TOOLS_LINK_NAME = "host_bins"
TOOL_NAME = "acloud"
# Exit code in metrics
EXIT_SUCCESS = 0
EXIT_BY_USER = 1
EXIT_BY_WRONG_CMD = 2
EXIT_BY_FAIL_REPORT = 3
EXIT_BY_ERROR = -99

# For reuse gce instance
SELECT_ONE_GCE_INSTANCE = "select_one_gce_instance"

# Webrtc
WEBRTC_LOCAL_PORT = 8443
WEBRTC_LOCAL_HOST = "localhost"
WEBRTC_CERTS_PATH = "usr/share/webrtc/certs"
WEBRTC_CERTS_FILES = ["server.crt", "server.key"]
SSL_DIR = ".config/acloud/mkcert"
SSL_CA_NAME = "ACloud-webRTC-CA"
SSL_TRUST_CA_DIR = "/usr/local/share/ca-certificates"

# Remote Log
REMOTE_LOG_FOLDER = "cuttlefish_runtime"

# Key name in report
ERROR_LOG_FOLDER = "error_log_folder"

# Type of "logs" entries in report.
# The values must be consistent with LogDataType in TradeFed.
LOG_TYPE_DIR = "DIR"
LOG_TYPE_KERNEL_LOG = "KERNEL_LOG"
LOG_TYPE_LOGCAT = "LOGCAT"
LOG_TYPE_TEXT = "TEXT"
LOG_TYPE_CUTTLEFISH_LOG = "CUTTLEFISH_LOG"

# Stages for create progress
STAGE_INIT = 0
STAGE_GCE = 1
STAGE_SSH_CONNECT = 2
STAGE_ARTIFACT = 3
STAGE_BOOT_UP = 4

# Acloud error types
# Also update InfraErrorIdentifier.java in TradeFed for the errors to be
# properly reported.
ACLOUD_CONFIG_ERROR = "ACLOUD_CONFIG_ERROR"
ACLOUD_UNKNOWN_ARGS_ERROR = "ACLOUD_UNKNOWN_ARGS_ERROR"
ACLOUD_BOOT_UP_ERROR = "ACLOUD_BOOT_UP_ERROR"
ACLOUD_CREATE_GCE_ERROR = "ACLOUD_CREATE_GCE_ERROR"
ACLOUD_DOWNLOAD_ARTIFACT_ERROR = "ACLOUD_DOWNLOAD_ARTIFACT_ERROR"
ACLOUD_INIT_ERROR = "ACLOUD_INIT_ERROR"
ACLOUD_UNKNOWN_ERROR = "ACLOUD_UNKNOWN_ERROR"
ACLOUD_SSH_CONNECT_ERROR = "ACLOUD_SSH_CONNECT_ERROR"
GCE_QUOTA_ERROR = "GCE_QUOTA_ERROR"
ACLOUD_OXYGEN_LEASE_ERROR = "ACLOUD_OXYGEN_LEASE_ERROR"
ACLOUD_OXYGEN_RELEASE_ERROR = "ACLOUD_OXYGEN_RELEASE_ERROR"

# Key words of error messages.
ERROR_MSG_VNC_NOT_SUPPORT = "unknown command line flag 'start_vnc_server'"
ERROR_MSG_WEBRTC_NOT_SUPPORT = "unknown command line flag 'start_webrtc'"
ERROR_MSG_SSO_INVALID = "stuck at SSO"

# The name of download image tool.
FETCH_CVD = "fetch_cvd"
FETCH_CVD_ARGS_FILE = "fetch-cvd-args.txt"
# The version is used when downloading LKGB fetch_cvd failed.
FETCH_CVD_SECOND_VERSION = "10176216"
# Last known good build
LKGB = "LKGB"

# The name of credential source key json file, a copy of
# cfg.service_account_json_private_key_path for remote host case.
FETCH_CVD_CREDENTIAL_SOURCE = "credential_key.json"

# For setup and cleanup
# Packages "devscripts" and "equivs" are required for "mk-build-deps".
# Packages from: https://android.googlesource.com/device/google/cuttlefish/
AVD_REQUIRED_PKGS = [
    "devscripts", "equivs", "libvirt-clients", "libvirt-daemon-system",
    "config-package-dev", "golang"]
BASE_REQUIRED_PKGS = ["ssvnc", "lzop", "python3-tk"]
CUTTLEFISH_COMMOM_PKG = "cuttlefish-common"
