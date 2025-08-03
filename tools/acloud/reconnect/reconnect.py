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
r"""Reconnect entry point.

Reconnect will:
 - re-establish ssh tunnels for adb/vnc port forwarding for a remote instance
 - adb connect to forwarded ssh port for remote instance
 - restart vnc for remote/local instances
"""

import logging
import os
import re

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import auth
from acloud.internal.lib import android_compute_client
from acloud.internal.lib import cvd_runtime_config
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import utils
from acloud.internal.lib import ssh as ssh_object
from acloud.internal.lib.adb_tools import AdbTools
from acloud.list import list as list_instance
from acloud.public import config
from acloud.public import report


logger = logging.getLogger(__name__)

_RE_DISPLAY = re.compile(r"([\d]+)x([\d]+)\s.*")
_VNC_STARTED_PATTERN = "ssvnc vnc://127.0.0.1:%(vnc_port)d"


def _IsWebrtcEnable(instance, host_user, host_ssh_private_key_path,
                    extra_args_ssh_tunnel):
    """Check local/remote instance webRTC is enable.

    Args:
        instance: Local/Remote Instance object.
        host_user: String of user login into the instance.
        host_ssh_private_key_path: String of host key for logging in to the
                                   host.
        extra_args_ssh_tunnel: String, extra args for ssh tunnel connection.

    Returns:
        Boolean: True if cf_runtime_cfg.enable_webrtc is True.
    """
    if instance.islocal:
        return instance.cf_runtime_cfg.enable_webrtc
    ssh = ssh_object.Ssh(ip=ssh_object.IP(ip=instance.ip), user=host_user,
                         ssh_private_key_path=host_ssh_private_key_path,
                         extra_args_ssh_tunnel=extra_args_ssh_tunnel)
    remote_cuttlefish_config = os.path.join(constants.REMOTE_LOG_FOLDER,
                                            constants.CUTTLEFISH_CONFIG_FILE)
    raw_data = ssh.GetCmdOutput("cat " + remote_cuttlefish_config)
    try:
        cf_runtime_cfg = cvd_runtime_config.CvdRuntimeConfig(
            raw_data=raw_data.strip())
        return cf_runtime_cfg.enable_webrtc
    except errors.ConfigError:
        logger.debug("No cuttlefish config[%s] found!",
                     remote_cuttlefish_config)
    return False


def StartVnc(vnc_port, display):
    """Start vnc connect to AVD.

    Confirm whether there is already a connection before VNC connection.
    If there is a connection, it will not be connected. If not, connect it.
    Before reconnecting, clear old disconnect ssvnc viewer.

    Args:
        vnc_port: Integer of vnc port number.
        display: String, vnc connection resolution. e.g., 1080x720 (240)
    """
    vnc_started_pattern = _VNC_STARTED_PATTERN % {"vnc_port": vnc_port}
    if not utils.IsCommandRunning(vnc_started_pattern):
        #clean old disconnect ssvnc viewer.
        utils.CleanupSSVncviewer(vnc_port)

        match = _RE_DISPLAY.match(display)
        if match:
            utils.LaunchVncClient(vnc_port, match.group(1), match.group(2))
        else:
            utils.LaunchVncClient(vnc_port)


def AddPublicSshRsaToInstance(cfg, user, instance_name):
    """Add the public rsa key to the instance's metadata.

    When the public key doesn't exist in the metadata, it will add it.

    Args:
        cfg: An AcloudConfig instance.
        user: String, the ssh username to access instance.
        instance_name: String, instance name.
    """
    credentials = auth.CreateCredentials(cfg)
    compute_client = android_compute_client.AndroidComputeClient(
        cfg, credentials)
    compute_client.AddSshRsaInstanceMetadata(
        user,
        cfg.ssh_public_key_path,
        instance_name)


@utils.TimeExecute(function_description="Reconnect instances")
def ReconnectInstance(ssh_private_key_path,
                      instance,
                      reconnect_report,
                      extra_args_ssh_tunnel=None,
                      autoconnect=None,
                      connect_hostname=None):
    """Reconnect to the specified instance.

    It will:
     - re-establish ssh tunnels for adb/vnc port forwarding
     - re-establish adb connection
     - restart vnc client
     - update device information in reconnect_report

    Args:
        ssh_private_key_path: Path to the private key file.
                              e.g. ~/.ssh/acloud_rsa
        instance: list.Instance() object.
        reconnect_report: Report object.
        extra_args_ssh_tunnel: String, extra args for ssh tunnel connection.
        autoconnect: String, for decide whether to launch vnc/browser or not.
        connect_hostname: String, the hostname for ssh connect.

    Raises:
        errors.UnknownAvdType: Unable to reconnect to instance of unknown avd
                               type.
    """
    if instance.avd_type not in utils.AVD_PORT_DICT:
        raise errors.UnknownAvdType("Unable to reconnect to instance (%s) of "
                                    "unknown avd type: %s" %
                                    (instance.name, instance.avd_type))
    # Ignore extra ssh tunnel to connect with hostname.
    if connect_hostname:
        extra_args_ssh_tunnel = None

    adb_cmd = AdbTools(instance.adb_port)
    vnc_port = instance.vnc_port
    adb_port = instance.adb_port
    webrtc_port = instance.webrtc_port
    # ssh tunnel is up but device is disconnected on adb
    if instance.ssh_tunnel_is_connected and not adb_cmd.IsAdbConnectionAlive():
        adb_cmd.DisconnectAdb()
        adb_cmd.ConnectAdb()
    # ssh tunnel is down and it's a remote instance
    elif not instance.ssh_tunnel_is_connected and not instance.islocal:
        adb_cmd.DisconnectAdb()
        forwarded_ports = utils.AutoConnect(
            ip_addr=connect_hostname or instance.ip,
            rsa_key_file=ssh_private_key_path,
            target_vnc_port=utils.AVD_PORT_DICT[instance.avd_type].vnc_port,
            target_adb_port=utils.AVD_PORT_DICT[instance.avd_type].adb_port,
            ssh_user=constants.GCE_USER,
            extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        vnc_port = forwarded_ports.vnc_port
        adb_port = forwarded_ports.adb_port
    if autoconnect is constants.INS_KEY_WEBRTC:
        if not instance.islocal:
            webrtc_port = utils.GetWebrtcPortFromSSHTunnel(instance.ip)
            if not webrtc_port:
                webrtc_port = utils.PickFreePort()
                utils.EstablishWebRTCSshTunnel(
                    ip_addr=connect_hostname or instance.ip,
                    webrtc_local_port=webrtc_port,
                    rsa_key_file=ssh_private_key_path,
                    ssh_user=constants.GCE_USER,
                    extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        utils.LaunchBrowser(constants.WEBRTC_LOCAL_HOST,
                            webrtc_port)
    elif vnc_port and autoconnect is constants.INS_KEY_VNC:
        StartVnc(vnc_port, instance.display)

    device_dict = {
        constants.IP: instance.ip,
        constants.INSTANCE_NAME: instance.name,
        constants.VNC_PORT: vnc_port,
        constants.ADB_PORT: adb_port
    }
    if adb_port and not instance.islocal:
        device_dict[constants.DEVICE_SERIAL] = (
            constants.REMOTE_INSTANCE_ADB_SERIAL % adb_port)

    if (vnc_port or webrtc_port) and adb_port:
        reconnect_report.AddData(key="devices", value=device_dict)
    else:
        # We use 'ps aux' to grep adb/vnc fowarding port from ssh tunnel
        # command. Therefore we report failure here if no vnc_port and
        # adb_port found.
        reconnect_report.AddData(key="device_failing_reconnect", value=device_dict)
        reconnect_report.AddError(instance.name)


def GetSshConnectHostname(cfg, instance):
    """Get ssh connect hostname.

    Get GCE hostname with specific rule for cloudtop users.

    Args:
        cfg: AcloudConfig object.
        instance: list.Instance() object.

    Returns:
        String of hostname for ssh connect. None is for not connect with
        hostname such as local instance mode.
    """
    if instance.islocal:
        return None
    if cfg.connect_hostname:
        return gcompute_client.GetGCEHostName(
            cfg.project, instance.name, cfg.zone)
    return None


def Run(args):
    """Run reconnect.

    Args:
        args: Namespace object from argparse.parse_args.
    """
    cfg = config.GetAcloudConfig(args)
    instances_to_reconnect = []
    if args.instance_names is not None:
        # user input instance name to get instance object.
        instances_to_reconnect = list_instance.GetInstancesFromInstanceNames(
            cfg, args.instance_names)
    if not instances_to_reconnect:
        instances_to_reconnect = list_instance.ChooseInstances(cfg, args.all)

    reconnect_report = report.Report(command="reconnect")
    for instance in instances_to_reconnect:
        if instance.avd_type not in utils.AVD_PORT_DICT:
            utils.PrintColorString("Skipping reconnect of instance %s due to "
                                   "unknown avd type (%s)." %
                                   (instance.name, instance.avd_type),
                                   utils.TextColors.WARNING)
            continue
        if not instance.islocal:
            AddPublicSshRsaToInstance(cfg, constants.GCE_USER, instance.name)
        ReconnectInstance(cfg.ssh_private_key_path,
                          instance,
                          reconnect_report,
                          cfg.extra_args_ssh_tunnel,
                          autoconnect=(args.autoconnect or instance.autoconnect),
                          connect_hostname=GetSshConnectHostname(cfg, instance))

    utils.PrintDeviceSummary(reconnect_report)
