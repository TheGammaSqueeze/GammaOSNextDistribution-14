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
r"""Instance class.

Define the instance class used to hold details about an AVD instance.

The instance class will hold details about AVD instances (remote/local) used to
enable users to understand what instances they've created. This will be leveraged
for the list, delete, and reconnect commands.

The details include:
- instance name (for remote instances)
- creation date/instance duration
- instance image details (branch/target/build id)
- and more!
"""

import collections
import datetime
import json
import logging
import os
import re
import subprocess
import tempfile

# pylint: disable=import-error,too-many-lines
import dateutil.parser
import dateutil.tz

from acloud.create import local_image_local_instance
from acloud.internal import constants
from acloud.internal.lib import cvd_runtime_config
from acloud.internal.lib import utils
from acloud.internal.lib.adb_tools import AdbTools
from acloud.internal.lib.local_instance_lock import LocalInstanceLock
from acloud.internal.lib.gcompute_client import GetInstanceIP
from acloud.internal.lib.gcompute_client import GetGCEHostName


logger = logging.getLogger(__name__)

_ACLOUD_CVD_TEMP = os.path.join(tempfile.gettempdir(), "acloud_cvd_temp")
_CFG_KEY_INSTANCES = "instances"
_CVD_RUNTIME_FOLDER_NAME = "cuttlefish_runtime"
_CVD_BIN = "cvd"
_CVD_BIN_FOLDER = "host_bins/bin"
_CVD_STATUS_BIN = "cvd_status"
_CVD_STOP_ERROR_KEYWORDS = "cvd_internal_stop E"
# Default timeout 30 secs for cvd commands.
_CVD_TIMEOUT = 30
# Keywords read from runtime config.
_ADB_HOST_PORT = "adb_host_port"
# Keywords read from the output of "cvd status".
_DISPLAYS = "displays"
_WEBRTC_PORT = "webrtc_port"
_ADB_SERIAL = "adb_serial"
_INSTANCE_ASSEMBLY_DIR = "cuttlefish_assembly"
_LOCAL_INSTANCE_NAME_FORMAT = "local-instance-%(id)d"
_LOCAL_INSTANCE_NAME_PATTERN = re.compile(r"^local-instance-(?P<id>\d+)$")
_ACLOUDWEB_INSTANCE_START_STRING = "cf-"
_MSG_UNABLE_TO_CALCULATE = "Unable to calculate"
_NO_ANDROID_ENV = "android source not available"
_RE_GROUP_ADB = "local_adb_port"
_RE_GROUP_VNC = "local_vnc_port"
_RE_SSH_TUNNEL_PATTERN = (r"((.*\s*-L\s)(?P<%s>\d+):127.0.0.1:%s)"
                          r"((.*\s*-L\s)(?P<%s>\d+):127.0.0.1:%s)"
                          r"(.+(%s|%s))")
_RE_TIMEZONE = re.compile(r"^(?P<time>[0-9\-\.:T]*)(?P<timezone>[+-]\d+:\d+)$")
_RE_DEVICE_INFO = re.compile(r"(?s).*(?P<device_info>[{][\s\w\W]+})")

_COMMAND_PS_LAUNCH_CVD = ["ps", "-wweo", "lstart,cmd"]
_RE_RUN_CVD = re.compile(r"(?P<date_str>^[^/]+)(.*run_cvd)")
_X_RES = "x_res"
_Y_RES = "y_res"
_DPI = "dpi"
_DISPLAY_STRING = "%(x_res)sx%(y_res)s (%(dpi)s)"
_RE_ZONE = re.compile(r".+/zones/(?P<zone>.+)$")
_RE_PROJECT = re.compile(r".+/projects/(?P<project>.+)/zones/.+$")
_LOCAL_ZONE = "local"
_INDENT = " " * 3
LocalPorts = collections.namedtuple("LocalPorts", [constants.VNC_PORT,
                                                   constants.ADB_PORT])


def GetDefaultCuttlefishConfig():
    """Get the path of default cuttlefish instance config.

    Return:
        String, path of cf runtime config.
    """
    cfg_path = os.path.join(os.path.expanduser("~"), _CVD_RUNTIME_FOLDER_NAME,
                            constants.CUTTLEFISH_CONFIG_FILE)
    if os.path.isfile(cfg_path):
        return cfg_path
    return None


def GetLocalInstanceName(local_instance_id):
    """Get local cuttlefish instance name by instance id.

    Args:
        local_instance_id: Integer of instance id.

    Return:
        String, the instance name.
    """
    return _LOCAL_INSTANCE_NAME_FORMAT % {"id": local_instance_id}


def GetLocalInstanceIdByName(name):
    """Get local cuttlefish instance id by name.

    Args:
        name: String of instance name.

    Return:
        The instance id as an integer if the name is in valid format.
        None if the name does not represent a local cuttlefish instance.
    """
    match = _LOCAL_INSTANCE_NAME_PATTERN.match(name)
    if match:
        return int(match.group("id"))
    return None


def GetLocalInstanceConfigPath(local_instance_id):
    """Get the path of instance config.

    Args:
        local_instance_id: Integer of instance id.

    Return:
        String, path of cf runtime config.
    """
    ins_assembly_dir = os.path.join(GetLocalInstanceHomeDir(local_instance_id),
                                    _INSTANCE_ASSEMBLY_DIR)
    return os.path.join(ins_assembly_dir, constants.CUTTLEFISH_CONFIG_FILE)


def GetLocalInstanceConfig(local_instance_id):
    """Get the path of existed config from local instance.

    Args:
        local_instance_id: Integer of instance id.

    Return:
        String, path of cf runtime config. None for config not exist.
    """
    cfg_path = GetLocalInstanceConfigPath(local_instance_id)
    if os.path.isfile(cfg_path):
        return cfg_path
    return None

def GetInstanceId(cfg_path):
    """Get the cuttlefish ID from config file.

    Args:
        cfg_path: Path to config file.

    Return:
        Cuttlefish ID.
    """
    with open(cfg_path, "r") as cf_config:
        config_dict = json.load(cf_config)
        instances = config_dict.get(_CFG_KEY_INSTANCES)
        ins_id = int(min(instances.keys())) if instances else 1
        return ins_id

def GetAllLocalInstanceConfigs():
    """Get all cuttlefish runtime configs from the known locations.

    Return:
        List of tuples. Each tuple consists of an instance id and a config
        path.
    """
    id_cfg_pairs = []
    id_set = set()
    # Check if any instance config is under home folder.
    cfg_path = GetDefaultCuttlefishConfig()
    if cfg_path:
        ins_id = GetInstanceId(cfg_path)

        # skip redundant id in HOME and /tmp
        if ins_id not in id_set:
            id_set.add(ins_id)
            id_cfg_pairs.append((ins_id, cfg_path))

    # Check if any instance config is under acloud cvd temp folder.
    if os.path.exists(_ACLOUD_CVD_TEMP):
        for ins_name in os.listdir(_ACLOUD_CVD_TEMP):
            ins_id = GetLocalInstanceIdByName(ins_name)
            if ins_id is not None and ins_id not in id_set:
                cfg_path = GetLocalInstanceConfig(ins_id)
                if cfg_path:
                    id_set.add(ins_id)
                    id_cfg_pairs.append((ins_id, cfg_path))
    return id_cfg_pairs


def GetLocalInstanceHomeDir(local_instance_id):
    """Get local instance home dir according to instance id.

    Args:
        local_instance_id: Integer of instance id.

    Return:
        String, path of instance home dir.
    """
    return os.path.join(_ACLOUD_CVD_TEMP,
                        GetLocalInstanceName(local_instance_id))


def GetLocalInstanceLock(local_instance_id):
    """Get local instance lock.

    Args:
        local_instance_id: Integer of instance id.

    Returns:
        LocalInstanceLock object.
    """
    file_path = os.path.join(_ACLOUD_CVD_TEMP,
                             GetLocalInstanceName(local_instance_id) + ".lock")
    return LocalInstanceLock(file_path)


def GetLocalInstanceRuntimeDir(local_instance_id):
    """Get instance runtime dir

    Args:
        local_instance_id: Integer of instance id.

    Return:
        String, path of instance runtime dir.
    """
    return os.path.join(GetLocalInstanceHomeDir(local_instance_id),
                        _CVD_RUNTIME_FOLDER_NAME)


def GetCuttleFishLocalInstances(cf_config_path):
    """Get all instances information from cf runtime config.

    Args:
        cf_config_path: String, path to the cf runtime config.

    Returns:
        List of LocalInstance object.
    """
    cf_runtime_cfg = cvd_runtime_config.CvdRuntimeConfig(cf_config_path)
    local_instances = []
    for ins_id in cf_runtime_cfg.instance_ids:
        local_instances.append(LocalInstance(cf_config_path, ins_id))
    return local_instances


def _GetCurrentLocalTime():
    """Return a datetime object for current time in local time zone."""
    return datetime.datetime.now(dateutil.tz.tzlocal()).replace(microsecond=0)


def _GetElapsedTime(start_time):
    """Calculate the elapsed time from start_time till now.

    Args:
        start_time: String of instance created time.

    Returns:
        datetime.timedelta of elapsed time, _MSG_UNABLE_TO_CALCULATE for
        datetime can't parse cases.
    """
    match = _RE_TIMEZONE.match(start_time)
    try:
        # Check start_time has timezone or not. If timezone can't be found,
        # use local timezone to get elapsed time.
        if match:
            return _GetCurrentLocalTime() - dateutil.parser.parse(
                start_time).replace(microsecond=0)

        return _GetCurrentLocalTime() - dateutil.parser.parse(
            start_time).replace(tzinfo=dateutil.tz.tzlocal(), microsecond=0)
    except ValueError:
        logger.debug(("Can't parse datetime string(%s)."), start_time)
        return _MSG_UNABLE_TO_CALCULATE

def _GetDeviceFullName(device_serial, instance_name, elapsed_time,
                       webrtc_device_id=None):
    """Get the full name of device.

    The full name is composed with device serial, webrtc device id, instance
    name, and elapsed_time.

    Args:
        device_serial: String of device serial. e.g. 127.0.0.1:6520
        instance_name: String of instance name.
        elapsed time: String of elapsed time.
        webrtc_device_id: String of webrtc device id.

    Returns:
        String of device full name.
    """
    if webrtc_device_id:
        return (f"device serial: {device_serial} {webrtc_device_id} "
                f"({instance_name}) elapsed time: {elapsed_time}")

    return (f"device serial: {device_serial} ({instance_name}) "
            f"elapsed time: {elapsed_time}")


def _IsProcessRunning(process):
    """Check if this process is running.

    Returns:
        Boolean, True for this process is running.
    """
    match_pattern = re.compile(f"(.+)({process} )(.+)")
    process_output = utils.CheckOutput(constants.COMMAND_PS)
    for line in process_output.splitlines():
        process_match = match_pattern.match(line)
        if process_match:
            return True
    return False


# pylint: disable=useless-object-inheritance
class Instance(object):
    """Class to store data of instance."""

    # pylint: disable=too-many-locals
    def __init__(self, name, fullname, display, ip, status=None, adb_port=None,
                 vnc_port=None, ssh_tunnel_is_connected=None, createtime=None,
                 elapsed_time=None, avd_type=None, avd_flavor=None,
                 is_local=False, device_information=None, zone=None,
                 webrtc_port=None, webrtc_forward_port=None):
        self._name = name
        self._fullname = fullname
        self._status = status
        self._display = display  # Resolution and dpi
        self._ip = ip
        self._adb_port = adb_port  # adb port which is forwarding to remote
        self._vnc_port = vnc_port  # vnc port which is forwarding to remote
        self._webrtc_port = webrtc_port
        self._webrtc_forward_port = webrtc_forward_port
        # True if ssh tunnel is still connected
        self._ssh_tunnel_is_connected = ssh_tunnel_is_connected
        self._createtime = createtime
        self._elapsed_time = elapsed_time
        self._avd_type = avd_type
        self._avd_flavor = avd_flavor
        self._is_local = is_local  # True if this is a local instance
        self._device_information = device_information
        self._zone = zone
        self._autoconnect = self._GetAutoConnect()

    def __repr__(self):
        """Return full name property for print."""
        return self._fullname

    def Summary(self):
        """Let's make it easy to see what this class is holding."""
        representation = []
        representation.append(" name: %s" % self._name)
        representation.append("%s IP: %s" % (_INDENT, self._ip))
        representation.append("%s create time: %s" % (_INDENT, self._createtime))
        representation.append("%s elapse time: %s" % (_INDENT, self._elapsed_time))
        representation.append("%s status: %s" % (_INDENT, self._status))
        representation.append("%s avd type: %s" % (_INDENT, self._avd_type))
        representation.append("%s display: %s" % (_INDENT, self._display))
        representation.append("%s vnc: 127.0.0.1:%s" % (_INDENT, self._vnc_port))
        representation.append("%s zone: %s" % (_INDENT, self._zone))
        representation.append("%s autoconnect: %s" % (_INDENT, self._autoconnect))
        representation.append("%s webrtc port: %s" % (_INDENT, self._webrtc_port))
        representation.append("%s webrtc forward port: %s" %
                              (_INDENT, self._webrtc_forward_port))

        if self._adb_port and self._device_information:
            serial_ip = self._ip if self._ip == "0.0.0.0" else "127.0.0.1"
            representation.append("%s adb serial: %s:%s" %
                                  (_INDENT, serial_ip, self._adb_port))
            representation.append("%s product: %s" % (
                _INDENT, self._device_information["product"]))
            representation.append("%s model: %s" % (
                _INDENT, self._device_information["model"]))
            representation.append("%s device: %s" % (
                _INDENT, self._device_information["device"]))
            representation.append("%s transport_id: %s" % (
                _INDENT, self._device_information["transport_id"]))
        else:
            representation.append("%s adb serial: disconnected" % _INDENT)

        return "\n".join(representation)

    def AdbConnected(self):
        """Check AVD adb connected.

        Returns:
            Boolean, True when adb status of AVD is connected.
        """
        if self._adb_port and self._device_information:
            return True
        return False

    def _GetAutoConnect(self):
        """Get the autoconnect of instance.

        Returns:
            String of autoconnect type. None for no autoconnect.
        """
        if self._webrtc_port or self._webrtc_forward_port:
            return constants.INS_KEY_WEBRTC
        if self._vnc_port:
            return constants.INS_KEY_VNC
        if self._adb_port:
            return constants.INS_KEY_ADB
        return None

    @property
    def name(self):
        """Return the instance name."""
        return self._name

    @property
    def fullname(self):
        """Return the instance full name."""
        return self._fullname

    @property
    def ip(self):
        """Return the ip."""
        return self._ip

    @property
    def status(self):
        """Return status."""
        return self._status

    @property
    def display(self):
        """Return display."""
        return self._display

    @property
    def ssh_tunnel_is_connected(self):
        """Return the connect status."""
        return self._ssh_tunnel_is_connected

    @property
    def createtime(self):
        """Return create time."""
        return self._createtime

    @property
    def avd_type(self):
        """Return avd_type."""
        return self._avd_type

    @property
    def avd_flavor(self):
        """Return avd_flavor."""
        return self._avd_flavor

    @property
    def islocal(self):
        """Return if it is a local instance."""
        return self._is_local

    @property
    def adb_port(self):
        """Return adb_port."""
        return self._adb_port

    @property
    def vnc_port(self):
        """Return vnc_port."""
        return self._vnc_port

    @property
    def webrtc_port(self):
        """Return webrtc_port."""
        return self._webrtc_port

    @property
    def webrtc_forward_port(self):
        """Return webrtc_forward_port."""
        return self._webrtc_forward_port

    @property
    def zone(self):
        """Return zone."""
        return self._zone

    @property
    def autoconnect(self):
        """Return autoconnect."""
        return self._autoconnect


class LocalInstance(Instance):
    """Class to store data of local cuttlefish instance."""
    def __init__(self, cf_config_path, ins_id=None):
        """Initialize a localInstance object.

        Args:
            cf_config_path: String, path to the cf runtime config.
            ins_id: Integer, the id to specify the instance information.
        """
        self._cf_runtime_cfg = cvd_runtime_config.CvdRuntimeConfig(cf_config_path)
        self._instance_dir = self._cf_runtime_cfg.instance_dir
        self._virtual_disk_paths = self._cf_runtime_cfg.virtual_disk_paths
        self._local_instance_id = int(ins_id or self._cf_runtime_cfg.instance_id)
        self._instance_home = GetLocalInstanceHomeDir(self._local_instance_id)
        if self._cf_runtime_cfg.root_dir:
            self._instance_home = os.path.dirname(self._cf_runtime_cfg.root_dir)

        ins_info = self._cf_runtime_cfg.instances.get(ins_id, {})
        adb_port = ins_info.get(_ADB_HOST_PORT) or self._cf_runtime_cfg.adb_port
        webrtc_device_id = (ins_info.get(constants.INS_KEY_WEBRTC_DEVICE_ID)
                            or f"cvd-{self._local_instance_id}")
        adb_serial = f"0.0.0.0:{adb_port}"
        display = []
        for display_config in self._cf_runtime_cfg.display_configs:
            display.append(_DISPLAY_STRING % {"x_res": display_config.get(_X_RES),
                                              "y_res": display_config.get(_Y_RES),
                                              "dpi": display_config.get(_DPI)})
        # TODO(143063678), there's no createtime info in
        # cuttlefish_config.json so far.
        webrtc_port = local_image_local_instance.LocalImageLocalInstance.GetWebrtcSigServerPort(
            self._local_instance_id)
        cvd_status_info = self._GetDevidInfoFromCvdStatus()
        if cvd_status_info:
            display = cvd_status_info.get(_DISPLAYS)
            webrtc_port = int(cvd_status_info.get(_WEBRTC_PORT))
            adb_serial = cvd_status_info.get(_ADB_SERIAL)

        name = GetLocalInstanceName(self._local_instance_id)
        fullname = _GetDeviceFullName(adb_serial, name, None, webrtc_device_id)
        adb_device = AdbTools(device_serial=adb_serial)
        device_information = None
        if adb_device.IsAdbConnected():
            device_information = adb_device.device_information

        super().__init__(
            name=name, fullname=fullname, display=display, ip="0.0.0.0",
            status=constants.INS_STATUS_RUNNING,
            adb_port=adb_port,
            vnc_port=self._cf_runtime_cfg.vnc_port,
            createtime=None, elapsed_time=None, avd_type=constants.TYPE_CF,
            is_local=True, device_information=device_information,
            zone=_LOCAL_ZONE, webrtc_port=webrtc_port)

    def Summary(self):
        """Return the string that this class is holding."""
        instance_home = "%s instance home: %s" % (_INDENT, self._instance_dir)
        return "%s\n%s" % (super().Summary(), instance_home)

    def _GetCvdEnv(self):
        """Get the environment to run cvd commands.

        Returns:
            os.environ with cuttlefish variables updated.
        """
        cvd_env = os.environ.copy()
        cvd_env[constants.ENV_ANDROID_HOST_OUT] = os.path.dirname(
            self._cf_runtime_cfg.cvd_tools_path)
        cvd_env[constants.ENV_ANDROID_SOONG_HOST_OUT] = os.path.dirname(
            self._cf_runtime_cfg.cvd_tools_path)
        cvd_env[constants.ENV_CUTTLEFISH_CONFIG_FILE] = self._cf_runtime_cfg.config_path
        cvd_env[constants.ENV_CVD_HOME] = self._instance_home
        cvd_env[constants.ENV_CUTTLEFISH_INSTANCE] = str(self._local_instance_id)
        return cvd_env

    def _GetDevidInfoFromCvdStatus(self):
        """Get device information from 'cvd status'.

        Execute 'cvd status --print -instance_name=name' cmd to get devices
        information.

        Returns
            Output of 'cvd status'. None for fail to run 'cvd status'.
        """
        try:
            cvd_tool = os.path.join(self._instance_home, _CVD_BIN_FOLDER, _CVD_BIN)
            ins_name = f"cvd-{self._local_instance_id}"
            cvd_status_cmd = f"{cvd_tool} status -print -instance_name={ins_name}"
            if not os.path.exists(cvd_tool):
                logger.warning("Cvd tools path doesn't exist:%s", cvd_tool)
                return None
            logger.debug("Running cmd [%s] to get device info.", cvd_status_cmd)
            process = subprocess.Popen(cvd_status_cmd, shell=True, text=True,
                                       env=self._GetCvdEnv(),
                                       stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE)
            stdout, _ = process.communicate(timeout=_CVD_TIMEOUT)
            logger.debug("Output of cvd status: %s", stdout)
            return json.loads(self._ParsingCvdFleetOutput(stdout))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired,
                json.JSONDecodeError) as error:
            logger.error("Failed to run 'cvd status': %s", str(error))
        return None

    @staticmethod
    def _ParsingCvdFleetOutput(output):
        """Parsing the output of cvd fleet.

        The output example:
            WARNING: cvd_server client version (8245608) does not match.
            {
                "adb_serial" : "0.0.0.0:6520",
                "assembly_dir" : "/home/cuttlefish_runtime/assembly",
                "displays" : ["720 x 1280 ( 320 )"],
                "instance_dir" : "/home/cuttlefish_runtime/instances/cvd-1",
                "instance_name" : "cvd-1",
                "status" : "Running",
                "web_access" : "https://0.0.0.0:8443/client.html?deviceId=cvd-1",
                "webrtc_port" : "8443"
            }

        Returns:
            Parsed output filtered warning message.
        """
        device_match = _RE_DEVICE_INFO.match(output)
        if device_match:
            return device_match.group("device_info")
        return ""

    def CvdStatus(self):
        """check if local instance is active.

        Execute cvd_status cmd to check if it exit without error.

        Returns
            True if instance is active.
        """
        if not self._cf_runtime_cfg.cvd_tools_path:
            logger.debug("No cvd tools path found from config:%s",
                         self._cf_runtime_cfg.config_path)
            return False
        try:
            cvd_status_cmd = os.path.join(self._cf_runtime_cfg.cvd_tools_path,
                                          _CVD_STATUS_BIN)
            # TODO(b/150575261): Change the cvd home and cvd artifact path to
            #  another place instead of /tmp to prevent from the file not
            #  found exception.
            if not os.path.exists(cvd_status_cmd):
                logger.warning("Cvd tools path doesn't exist:%s", cvd_status_cmd)
                for env_host_out in [constants.ENV_ANDROID_SOONG_HOST_OUT,
                                     constants.ENV_ANDROID_HOST_OUT]:
                    if os.environ.get(env_host_out, _NO_ANDROID_ENV) in cvd_status_cmd:
                        logger.warning(
                            "Can't find the cvd_status tool (Try lunching a "
                            "cuttlefish target like aosp_cf_x86_64_phone-userdebug "
                            "and running 'make hosttar' before list/delete local "
                            "instances)")
                return False
            logger.debug("Running cmd[%s] to check cvd status.", cvd_status_cmd)
            process = subprocess.Popen(cvd_status_cmd,
                                       stdin=None,
                                       stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT,
                                       env=self._GetCvdEnv())
            stdout, _ = process.communicate()
            if process.returncode != 0:
                if stdout:
                    logger.debug("Local instance[%s] is not active: %s",
                                 self.name, stdout.strip())
                return False
            return True
        except subprocess.CalledProcessError as cpe:
            logger.error("Failed to run cvd_status: %s", cpe.output)
            return False

    def Delete(self):
        """Execute "cvd stop" to stop local cuttlefish instance.

        - We should get the same host tool used to delete instance.
        - Add CUTTLEFISH_CONFIG_FILE env variable to tell cvd which cvd need to
          be deleted.
        - Stop adb since local instance use the fixed adb port and could be
         reused again soon.
        """
        ins_home_dir = GetLocalInstanceHomeDir(self._local_instance_id)
        cvd_tool = os.path.join(ins_home_dir, _CVD_BIN_FOLDER, _CVD_BIN)
        stop_cvd_cmd = f"{cvd_tool} stop"
        logger.debug("Running cmd[%s] to delete local cvd", stop_cvd_cmd)
        if not self.instance_dir:
            logger.error("instance_dir is null!! instance[%d] might not be"
                         " deleted", self._local_instance_id)
        try:
            output = subprocess.check_output(
                utils.AddUserGroupsToCmd(stop_cvd_cmd,
                                         constants.LIST_CF_USER_GROUPS),
                stderr=subprocess.STDOUT, shell=True, env=self._GetCvdEnv(),
                text=True, timeout=_CVD_TIMEOUT)
            # TODO: Remove workaround of stop_cvd when 'cvd stop' is stable.
            if _CVD_STOP_ERROR_KEYWORDS in output:
                logger.debug("Fail to stop cvd: %s", output)
                self._ExecuteStopCvd(os.path.join(ins_home_dir, _CVD_BIN_FOLDER))
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as e:
            logger.debug("'cvd stop' error: %s", str(e))
            self._ExecuteStopCvd(os.path.join(ins_home_dir, _CVD_BIN_FOLDER))

        adb_cmd = AdbTools(self.adb_port)
        # When relaunch a local instance, we need to pass in retry=True to make
        # sure adb device is completely gone since it will use the same adb port
        adb_cmd.DisconnectAdb(retry=True)

    def _ExecuteStopCvd(self, dir_path):
        """Execute "stop_cvd" to stop local cuttlefish instance.

        Args:
            bin_dir: String, directory path of "stop_cvd".
        """
        stop_cvd_cmd = os.path.join(dir_path, constants.CMD_STOP_CVD)
        subprocess.check_call(
            utils.AddUserGroupsToCmd(
                stop_cvd_cmd, constants.LIST_CF_USER_GROUPS),
            stderr=subprocess.STDOUT, shell=True, env=self._GetCvdEnv())

    def GetLock(self):
        """Return the LocalInstanceLock for this object."""
        return GetLocalInstanceLock(self._local_instance_id)

    @property
    def instance_dir(self):
        """Return _instance_dir."""
        return self._instance_dir

    @property
    def instance_id(self):
        """Return _local_instance_id."""
        return self._local_instance_id

    @property
    def virtual_disk_paths(self):
        """Return virtual_disk_paths"""
        return self._virtual_disk_paths

    @property
    def cf_runtime_cfg(self):
        """Return _cf_runtime_cfg"""
        return self._cf_runtime_cfg


class LocalGoldfishInstance(Instance):
    """Class to store data of local goldfish instance.

    A goldfish instance binds to a console port and an adb port. The console
    port is for `adb emu` to send emulator-specific commands. The adb port is
    for `adb connect` to start a TCP connection. By convention, the console
    port is an even number, and the adb port is the console port + 1. The first
    instance uses port 5554 and 5555, the second instance uses 5556 and 5557,
    and so on.
    """

    _INSTANCE_NAME_PATTERN = re.compile(
        r"^local-goldfish-instance-(?P<id>\d+)$")
    _INSTANCE_NAME_FORMAT = "local-goldfish-instance-%(id)s"
    _EMULATOR_DEFAULT_CONSOLE_PORT = 5554
    _DEFAULT_ADB_LOCAL_TRANSPORT_MAX_PORT = 5585
    _DEVICE_SERIAL_FORMAT = "emulator-%(console_port)s"
    _DEVICE_SERIAL_PATTERN = re.compile(r"^emulator-(?P<console_port>\d+)$")

    def __init__(self, local_instance_id, avd_flavor=None, create_time=None,
                 x_res=None, y_res=None, dpi=None):
        """Initialize a LocalGoldfishInstance object.

        Args:
            local_instance_id: Integer of instance id.
            avd_flavor: String, the flavor of the virtual device.
            create_time: String, the creation date and time.
            x_res: Integer of x dimension.
            y_res: Integer of y dimension.
            dpi: Integer of dpi.
        """
        self._id = local_instance_id
        adb_port = self.console_port + 1
        self._adb = AdbTools(adb_port=adb_port,
                             device_serial=self.device_serial)

        name = self._INSTANCE_NAME_FORMAT % {"id": local_instance_id}

        elapsed_time = _GetElapsedTime(create_time) if create_time else None

        fullname = _GetDeviceFullName(self.device_serial, name, elapsed_time)

        if x_res and y_res and dpi:
            display = _DISPLAY_STRING % {"x_res": x_res, "y_res": y_res,
                                         "dpi": dpi}
        else:
            display = "unknown"

        device_information = (self._adb.device_information if
                              self._adb.device_information else None)

        super().__init__(
            name=name, fullname=fullname, display=display, ip="127.0.0.1",
            status=None, adb_port=adb_port, avd_type=constants.TYPE_GF,
            createtime=create_time, elapsed_time=elapsed_time,
            avd_flavor=avd_flavor, is_local=True,
            device_information=device_information)

    @staticmethod
    def _GetInstanceDirRoot():
        """Return the root directory of all instance directories."""
        return os.path.join(tempfile.gettempdir(), "acloud_gf_temp")

    @property
    def adb(self):
        """Return the AdbTools to send emulator commands to this instance."""
        return self._adb

    @property
    def console_port(self):
        """Return the console port as an integer."""
        # Emulator requires the console port to be an even number.
        return self._EMULATOR_DEFAULT_CONSOLE_PORT + (self._id - 1) * 2

    @property
    def device_serial(self):
        """Return the serial number that contains the console port."""
        return self._DEVICE_SERIAL_FORMAT % {"console_port": self.console_port}

    @property
    def instance_dir(self):
        """Return the path to instance directory."""
        return os.path.join(self._GetInstanceDirRoot(),
                            self._INSTANCE_NAME_FORMAT % {"id": self._id})

    @classmethod
    def GetIdByName(cls, name):
        """Get id by name.

        Args:
            name: String of instance name.

        Return:
            The instance id as an integer if the name is in valid format.
            None if the name does not represent a local goldfish instance.
        """
        match = cls._INSTANCE_NAME_PATTERN.match(name)
        if match:
            return int(match.group("id"))
        return None

    @classmethod
    def GetLockById(cls, instance_id):
        """Get LocalInstanceLock by id."""
        lock_path = os.path.join(
            cls._GetInstanceDirRoot(),
            (cls._INSTANCE_NAME_FORMAT % {"id": instance_id}) + ".lock")
        return LocalInstanceLock(lock_path)

    def GetLock(self):
        """Return the LocalInstanceLock for this object."""
        return self.GetLockById(self._id)

    @classmethod
    def GetExistingInstances(cls):
        """Get the list of instances that adb can send emu commands to."""
        instances = []
        for serial in AdbTools.GetDeviceSerials():
            match = cls._DEVICE_SERIAL_PATTERN.match(serial)
            if not match:
                continue
            port = int(match.group("console_port"))
            instance_id = (port - cls._EMULATOR_DEFAULT_CONSOLE_PORT) // 2 + 1
            instances.append(LocalGoldfishInstance(instance_id))
        return instances

    @classmethod
    def GetMaxNumberOfInstances(cls):
        """Get number of emulators that adb can detect."""
        max_port = os.environ.get("ADB_LOCAL_TRANSPORT_MAX_PORT",
                                  cls._DEFAULT_ADB_LOCAL_TRANSPORT_MAX_PORT)
        try:
            max_port = int(max_port)
        except ValueError:
            max_port = cls._DEFAULT_ADB_LOCAL_TRANSPORT_MAX_PORT
        if (max_port < cls._EMULATOR_DEFAULT_CONSOLE_PORT or
                max_port > constants.MAX_PORT):
            max_port = cls._DEFAULT_ADB_LOCAL_TRANSPORT_MAX_PORT
        return (max_port + 1 - cls._EMULATOR_DEFAULT_CONSOLE_PORT) // 2


class RemoteInstance(Instance):
    """Class to store data of remote instance."""

    # pylint: disable=too-many-locals
    def __init__(self, gce_instance):
        """Process the args into class vars.

        RemoteInstace initialized by gce dict object. We parse the required data
        from gce_instance to local variables.
        Reference:
        https://cloud.google.com/compute/docs/reference/rest/v1/instances/get

        We also gather more details on client side including the forwarding adb
        port and vnc port which will be used to determine the status of ssh
        tunnel connection.

        The status of gce instance will be displayed in _fullname property:
        - Connected: If gce instance and ssh tunnel and adb connection are all
         active.
        - No connected: If ssh tunnel or adb connection is not found.
        - Terminated: If we can't retrieve the public ip from gce instance.

        Args:
            gce_instance: dict object queried from gce.
        """
        name = gce_instance.get(constants.INS_KEY_NAME)

        create_time = gce_instance.get(constants.INS_KEY_CREATETIME)
        elapsed_time = _GetElapsedTime(create_time)
        status = gce_instance.get(constants.INS_KEY_STATUS)
        zone = self._GetZoneName(gce_instance.get(constants.INS_KEY_ZONE))

        instance_ip = GetInstanceIP(gce_instance)
        ip = instance_ip.external or instance_ip.internal
        project = self._GetProjectName(gce_instance.get(constants.INS_KEY_ZONE))
        hostname = GetGCEHostName(project, name, zone)

        # Get metadata, webrtc_port will be removed if "cvd fleet" show it.
        display = None
        avd_type = None
        avd_flavor = None
        webrtc_port = None
        webrtc_device_id = None
        for metadata in gce_instance.get("metadata", {}).get("items", []):
            key = metadata["key"]
            value = metadata["value"]
            if key == constants.INS_KEY_DISPLAY:
                display = value
            elif key == constants.INS_KEY_AVD_TYPE:
                avd_type = value
            elif key == constants.INS_KEY_AVD_FLAVOR:
                avd_flavor = value
            elif key == constants.INS_KEY_WEBRTC_PORT:
                webrtc_port = value
            elif key == constants.INS_KEY_WEBRTC_DEVICE_ID:
                webrtc_device_id = value
        # TODO(176884236): Insert avd information into metadata of instance.
        if not avd_type and name.startswith(_ACLOUDWEB_INSTANCE_START_STRING):
            avd_type = constants.TYPE_CF

        # Find ssl tunnel info.
        adb_port = None
        vnc_port = None
        webrtc_forward_port = None
        device_information = None
        if ip:
            forwarded_ports = self.GetAdbVncPortFromSSHTunnel(ip, hostname, avd_type)
            adb_port = forwarded_ports.adb_port
            vnc_port = forwarded_ports.vnc_port
            ssh_tunnel_is_connected = adb_port is not None
            webrtc_forward_port = utils.GetWebrtcPortFromSSHTunnel(ip)

            adb_device = AdbTools(adb_port)
            if adb_device.IsAdbConnected():
                device_information = adb_device.device_information
                fullname = _GetDeviceFullName("127.0.0.1:%d" % adb_port, name,
                                              elapsed_time, webrtc_device_id)
            else:
                fullname = _GetDeviceFullName("not connected", name,
                                              elapsed_time, webrtc_device_id)
        # If instance is terminated, its ip is None.
        else:
            ssh_tunnel_is_connected = False
            fullname = _GetDeviceFullName("terminated", name, elapsed_time,
                                          webrtc_device_id)

        super().__init__(
            name=name, fullname=fullname, display=display, ip=ip, status=status,
            adb_port=adb_port, vnc_port=vnc_port,
            ssh_tunnel_is_connected=ssh_tunnel_is_connected,
            createtime=create_time, elapsed_time=elapsed_time, avd_type=avd_type,
            avd_flavor=avd_flavor, is_local=False,
            device_information=device_information,
            zone=zone, webrtc_port=webrtc_port,
            webrtc_forward_port=webrtc_forward_port)

    @staticmethod
    def _GetZoneName(zone_info):
        """Get the zone name from the zone information of gce instance.

        Zone information is like:
        "https://www.googleapis.com/compute/v1/projects/project/zones/us-central1-c"
        We want to get "us-central1-c" as zone name.

        Args:
            zone_info: String, zone information of gce instance.

        Returns:
            Zone name of gce instance. None if zone name can't find.
        """
        zone_match = _RE_ZONE.match(zone_info)
        if zone_match:
            return zone_match.group("zone")

        logger.debug("Can't get zone name from %s.", zone_info)
        return None

    @staticmethod
    def _GetProjectName(zone_info):
        """Get the project name from the zone information of gce instance.

        Zone information is like:
        "https://www.googleapis.com/compute/v1/projects/project/zones/us-central1-c"
        We want to get "project" as project name.

        Args:
            zone_info: String, zone information of gce instance.

        Returns:
            Project name of gce instance. None if project name can't find.
        """
        project_match = _RE_PROJECT.match(zone_info)
        if project_match:
            return project_match.group("project")

        logger.debug("Can't get project name from %s.", zone_info)
        return None

    @staticmethod
    def GetAdbVncPortFromSSHTunnel(ip, hostname, avd_type):
        """Get forwarding adb and vnc port from ssh tunnel.

        Args:
            ip: String, ip address.
            hostname: String, hostname of GCE instance.
            avd_type: String, the AVD type.

        Returns:
            NamedTuple ForwardedPorts(vnc_port, adb_port) holding the ports
            used in the ssh forwarded call. Both fields are integers.
        """
        if avd_type not in utils.AVD_PORT_DICT:
            return utils.ForwardedPorts(vnc_port=None, adb_port=None)

        default_vnc_port = utils.AVD_PORT_DICT[avd_type].vnc_port
        default_adb_port = utils.AVD_PORT_DICT[avd_type].adb_port
        # TODO(165888525): Align the SSH tunnel for the order of adb port and
        # vnc port.
        re_pattern = re.compile(_RE_SSH_TUNNEL_PATTERN %
                                (_RE_GROUP_ADB, default_adb_port,
                                 _RE_GROUP_VNC, default_vnc_port, ip, hostname))
        adb_port = None
        vnc_port = None
        process_output = utils.CheckOutput(constants.COMMAND_PS)
        for line in process_output.splitlines():
            match = re_pattern.match(line)
            if match:
                adb_port = int(match.group(_RE_GROUP_ADB))
                vnc_port = int(match.group(_RE_GROUP_VNC))
                break

        logger.debug(("gathering detail for ssh tunnel. "
                      "IP:%s, forwarding (adb:%s, vnc:%s)"), ip, adb_port,
                     vnc_port)

        return utils.ForwardedPorts(vnc_port=vnc_port, adb_port=adb_port)
