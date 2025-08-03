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
"""Common operations to create remote devices."""

import logging
import os

from acloud import errors
from acloud.public import avd
from acloud.public import report
from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.internal.lib.adb_tools import AdbTools


logger = logging.getLogger(__name__)
_GCE_QUOTA_ERROR_KEYWORDS = [
    "Quota exceeded for quota",
    "ZONE_RESOURCE_POOL_EXHAUSTED",
    "ZONE_RESOURCE_POOL_EXHAUSTED_WITH_DETAILS"]
_DICT_ERROR_TYPE = {
    constants.STAGE_INIT: constants.ACLOUD_INIT_ERROR,
    constants.STAGE_GCE: constants.ACLOUD_CREATE_GCE_ERROR,
    constants.STAGE_SSH_CONNECT: constants.ACLOUD_SSH_CONNECT_ERROR,
    constants.STAGE_ARTIFACT: constants.ACLOUD_DOWNLOAD_ARTIFACT_ERROR,
    constants.STAGE_BOOT_UP: constants.ACLOUD_BOOT_UP_ERROR,
}


def CreateSshKeyPairIfNecessary(cfg):
    """Create ssh key pair if necessary.

    Args:
        cfg: An Acloudconfig instance.

    Raises:
        error.DriverError: If it falls into an unexpected condition.
    """
    if not cfg.ssh_public_key_path:
        logger.warning(
            "ssh_public_key_path is not specified in acloud config. "
            "Project-wide public key will "
            "be used when creating AVD instances. "
            "Please ensure you have the correct private half of "
            "a project-wide public key if you want to ssh into the "
            "instances after creation.")
    elif cfg.ssh_public_key_path and not cfg.ssh_private_key_path:
        logger.warning(
            "Only ssh_public_key_path is specified in acloud config, "
            "but ssh_private_key_path is missing. "
            "Please ensure you have the correct private half "
            "if you want to ssh into the instances after creation.")
    elif cfg.ssh_public_key_path and cfg.ssh_private_key_path:
        utils.CreateSshKeyPairIfNotExist(cfg.ssh_private_key_path,
                                         cfg.ssh_public_key_path)
    else:
        # Should never reach here.
        raise errors.DriverError(
            "Unexpected error in CreateSshKeyPairIfNecessary")


class DevicePool:
    """A class that manages a pool of virtual devices.

    Attributes:
        devices: A list of devices in the pool.
    """

    def __init__(self, device_factory, devices=None):
        """Constructs a new DevicePool.

        Args:
            device_factory: A device factory capable of producing a goldfish or
                cuttlefish device. The device factory must expose an attribute with
                the credentials that can be used to retrieve information from the
                constructed device.
            devices: List of devices managed by this pool.
        """
        self._devices = devices or []
        self._device_factory = device_factory
        self._compute_client = device_factory.GetComputeClient()

    def CreateDevices(self, num):
        """Creates |num| devices for given build_target and build_id.

        Args:
            num: Number of devices to create.
        """
        # Create host instances for cuttlefish/goldfish device.
        # Currently one instance supports only 1 device.
        for _ in range(num):
            instance = self._device_factory.CreateInstance()
            ip = self._compute_client.GetInstanceIP(instance)
            time_info = {
                stage: round(exec_time, 2) for stage, exec_time in
                getattr(self._compute_client, "execution_time", {}).items()}
            stage = self._compute_client.stage if hasattr(
                self._compute_client, "stage") else 0
            openwrt = self._compute_client.openwrt if hasattr(
                self._compute_client, "openwrt") else False
            gce_hostname = self._compute_client.gce_hostname if hasattr(
                self._compute_client, "gce_hostname") else None
            self.devices.append(
                avd.AndroidVirtualDevice(ip=ip, instance_name=instance,
                                         time_info=time_info, stage=stage,
                                         openwrt=openwrt, gce_hostname=gce_hostname))

    @utils.TimeExecute(function_description="Waiting for AVD(s) to boot up",
                       result_evaluator=utils.BootEvaluator)
    def WaitForBoot(self, boot_timeout_secs):
        """Waits for all devices to boot up.

        Args:
            boot_timeout_secs: Integer, the maximum time in seconds used to
                               wait for the AVD to boot.

        Returns:
            A dictionary that contains all the failures.
            The key is the name of the instance that fails to boot,
            and the value is an errors.DeviceBootError object.
        """
        failures = {}
        for device in self._devices:
            try:
                self._compute_client.WaitForBoot(device.instance_name, boot_timeout_secs)
            except errors.DeviceBootError as e:
                failures[device.instance_name] = e
        return failures

    def UpdateReport(self, reporter):
        """Update report from compute client.

        Args:
            reporter: Report object.
        """
        reporter.UpdateData(self._compute_client.dict_report)

    def CollectSerialPortLogs(self, output_file,
                              port=constants.DEFAULT_SERIAL_PORT):
        """Tar the instance serial logs into specified output_file.

        Args:
            output_file: String, the output tar file path
            port: The serial port number to be collected
        """
        # For emulator, the serial log is the virtual host serial log.
        # For GCE AVD device, the serial log is the AVD device serial log.
        with utils.TempDir() as tempdir:
            src_dict = {}
            for device in self._devices:
                logger.info("Store instance %s serial port %s output to %s",
                            device.instance_name, port, output_file)
                serial_log = self._compute_client.GetSerialPortOutput(
                    instance=device.instance_name, port=port)
                file_name = "%s_serial_%s.log" % (device.instance_name, port)
                file_path = os.path.join(tempdir, file_name)
                src_dict[file_path] = file_name
                with open(file_path, "w") as f:
                    f.write(serial_log.encode("utf-8"))
            utils.MakeTarFile(src_dict, output_file)

    def SetDeviceBuildInfo(self):
        """Add devices build info."""
        for device in self._devices:
            device.build_info = self._device_factory.GetBuildInfoDict()

    @property
    def devices(self):
        """Returns a list of devices in the pool.

        Returns:
            A list of devices in the pool.
        """
        return self._devices

def _GetErrorType(error):
    """Get proper error type from the exception error.

    Args:
        error: errors object.

    Returns:
        String of error type. e.g. "ACLOUD_BOOT_UP_ERROR".
    """
    if isinstance(error, errors.CheckGCEZonesQuotaError):
        return constants.GCE_QUOTA_ERROR
    if isinstance(error, errors.DownloadArtifactError):
        return constants.ACLOUD_DOWNLOAD_ARTIFACT_ERROR
    if isinstance(error, errors.DeviceConnectionError):
        return constants.ACLOUD_SSH_CONNECT_ERROR
    for keyword in _GCE_QUOTA_ERROR_KEYWORDS:
        if keyword in str(error):
            return constants.GCE_QUOTA_ERROR
    return constants.ACLOUD_UNKNOWN_ERROR

# pylint: disable=too-many-locals,unused-argument,too-many-branches,too-many-statements
def CreateDevices(command, cfg, device_factory, num, avd_type,
                  report_internal_ip=False, autoconnect=False,
                  serial_log_file=None, client_adb_port=None,
                  boot_timeout_secs=None, unlock_screen=False,
                  wait_for_boot=True, connect_webrtc=False,
                  ssh_private_key_path=None,
                  ssh_user=constants.GCE_USER):
    """Create a set of devices using the given factory.

    Main jobs in create devices.
        1. Create GCE instance: Launch instance in GCP(Google Cloud Platform).
        2. Starting up AVD: Wait device boot up.

    Args:
        command: The name of the command, used for reporting.
        cfg: An AcloudConfig instance.
        device_factory: A factory capable of producing a single device.
        num: The number of devices to create.
        avd_type: String, the AVD type(cuttlefish, goldfish...).
        report_internal_ip: Boolean to report the internal ip instead of
                            external ip.
        serial_log_file: String, the file path to tar the serial logs.
        autoconnect: Boolean, whether to auto connect to device.
        client_adb_port: Integer, Specify port for adb forwarding.
        boot_timeout_secs: Integer, boot timeout secs.
        unlock_screen: Boolean, whether to unlock screen after invoke vnc client.
        wait_for_boot: Boolean, True to check serial log include boot up
                       message.
        connect_webrtc: Boolean, whether to auto connect webrtc to device.
        ssh_private_key_path: String, the private key for SSH tunneling.
        ssh_user: String, the user name for SSH tunneling.

    Raises:
        errors: Create instance fail.

    Returns:
        A Report instance.
    """
    reporter = report.Report(command=command)
    try:
        CreateSshKeyPairIfNecessary(cfg)
        device_pool = DevicePool(device_factory)
        device_pool.CreateDevices(num)
        device_pool.SetDeviceBuildInfo()
        if wait_for_boot:
            failures = device_pool.WaitForBoot(boot_timeout_secs)
        else:
            failures = device_factory.GetFailures()

        if failures:
            reporter.SetStatus(report.Status.BOOT_FAIL)
        else:
            reporter.SetStatus(report.Status.SUCCESS)

        # Collect logs
        logs = device_factory.GetLogs()
        if serial_log_file:
            device_pool.CollectSerialPortLogs(
                serial_log_file, port=constants.DEFAULT_SERIAL_PORT)

        device_pool.UpdateReport(reporter)
        # Write result to report.
        for device in device_pool.devices:
            ip = (device.ip.internal if report_internal_ip
                  else device.ip.external)
            extra_args_ssh_tunnel=cfg.extra_args_ssh_tunnel
            # TODO(b/154175542): Report multiple devices.
            vnc_ports = device_factory.GetVncPorts()
            adb_ports = device_factory.GetAdbPorts()
            if not vnc_ports[0] and not adb_ports[0]:
                vnc_ports[0], adb_ports[0] = utils.AVD_PORT_DICT[avd_type]

            device_dict = {
                "ip": ip + (":" + str(adb_ports[0]) if adb_ports[0] else ""),
                "instance_name": device.instance_name
            }
            if device.build_info:
                device_dict.update(device.build_info)
            if device.time_info:
                device_dict.update(device.time_info)
            if device.openwrt:
                device_dict.update(device_factory.GetOpenWrtInfoDict())
            if device.gce_hostname:
                device_dict[constants.GCE_HOSTNAME] = device.gce_hostname
                logger.debug(
                    "To connect with hostname, erase the extra_args_ssh_tunnel: %s",
                    extra_args_ssh_tunnel)
                extra_args_ssh_tunnel=""
            if autoconnect and reporter.status == report.Status.SUCCESS:
                forwarded_ports = _EstablishAdbVncConnections(
                    device.gce_hostname or ip, vnc_ports, adb_ports,
                    client_adb_port, ssh_user,
                    ssh_private_key_path=(ssh_private_key_path or
                                          cfg.ssh_private_key_path),
                    extra_args_ssh_tunnel=extra_args_ssh_tunnel,
                    unlock_screen=unlock_screen)
                if forwarded_ports:
                    forwarded_port = forwarded_ports[0]
                    device_dict[constants.VNC_PORT] = forwarded_port.vnc_port
                    device_dict[constants.ADB_PORT] = forwarded_port.adb_port
                    device_dict[constants.DEVICE_SERIAL] = (
                        constants.REMOTE_INSTANCE_ADB_SERIAL %
                        forwarded_port.adb_port)
            if connect_webrtc and reporter.status == report.Status.SUCCESS:
                webrtc_local_port = utils.PickFreePort()
                device_dict[constants.WEBRTC_PORT] = webrtc_local_port
                utils.EstablishWebRTCSshTunnel(
                    ip_addr=device.gce_hostname or ip,
                    webrtc_local_port=webrtc_local_port,
                    rsa_key_file=(ssh_private_key_path or
                                  cfg.ssh_private_key_path),
                    ssh_user=ssh_user,
                    extra_args_ssh_tunnel=extra_args_ssh_tunnel)
            if device.instance_name in logs:
                device_dict[constants.LOGS] = logs[device.instance_name]
            if hasattr(device_factory, 'GetFetchCvdWrapperLogIfExist'):
                fetch_cvd_wrapper_log = device_factory.GetFetchCvdWrapperLogIfExist()
                if fetch_cvd_wrapper_log:
                    device_dict["fetch_cvd_wrapper_log"] = fetch_cvd_wrapper_log
            if device.instance_name in failures:
                reporter.SetErrorType(constants.ACLOUD_BOOT_UP_ERROR)
                if device.stage:
                    reporter.SetErrorType(_DICT_ERROR_TYPE[device.stage])
                reporter.AddData(key="devices_failing_boot", value=device_dict)
                reporter.AddError(str(failures[device.instance_name]))
            else:
                reporter.AddData(key="devices", value=device_dict)
    except (errors.DriverError, errors.CheckGCEZonesQuotaError) as e:
        reporter.SetErrorType(_GetErrorType(e))
        reporter.AddError(str(e))
        reporter.SetStatus(report.Status.FAIL)
    return reporter


def _EstablishAdbVncConnections(ip, vnc_ports, adb_ports, client_adb_port,
                                ssh_user, ssh_private_key_path,
                                extra_args_ssh_tunnel, unlock_screen):
    """Establish the adb and vnc connections.

    Create the ssh tunnels with adb ports and vnc ports. Then unlock the device
    screen via the adb port.

    Args:
        ip: String, the IPv4 address.
        vnc_ports: List of integer, the vnc ports.
        adb_ports: List of integer, the adb ports.
        client_adb_port: Integer, Specify port for adb forwarding.
        ssh_user: String, the user name for SSH tunneling.
        ssh_private_key_path: String, the private key for SSH tunneling.
        extra_args_ssh_tunnel: String, extra args for ssh tunnel connection.
        unlock_screen: Boolean, whether to unlock screen after invoking vnc client.

    Returns:
        A list of namedtuple of (vnc_port, adb_port)
    """
    forwarded_ports = []
    for vnc_port, adb_port in zip(vnc_ports, adb_ports):
        forwarded_port = utils.AutoConnect(
            ip_addr=ip,
            rsa_key_file=ssh_private_key_path,
            target_vnc_port=vnc_port,
            target_adb_port=adb_port,
            ssh_user=ssh_user,
            client_adb_port=client_adb_port,
            extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        forwarded_ports.append(forwarded_port)
        if unlock_screen:
            AdbTools(forwarded_port.adb_port).AutoUnlockScreen()
    return forwarded_ports
