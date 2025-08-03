#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from typing import Optional
import json
import logging
import os
import re
import subprocess
import time

from acts import context
from acts import logger as acts_logger
from acts import signals
from acts import utils
from acts.controllers import pdu
from acts.libs.proc import job
from acts.utils import get_fuchsia_mdns_ipv6_address, get_interface_ip_addresses

from acts.controllers.fuchsia_lib.ffx import FFX
from acts.controllers.fuchsia_lib.sl4f import SL4F
from acts.controllers.fuchsia_lib.lib_controllers.netstack_controller import NetstackController
from acts.controllers.fuchsia_lib.lib_controllers.wlan_controller import WlanController
from acts.controllers.fuchsia_lib.lib_controllers.wlan_policy_controller import WlanPolicyController
from acts.controllers.fuchsia_lib.package_server import PackageServer
from acts.controllers.fuchsia_lib.ssh import DEFAULT_SSH_PORT, DEFAULT_SSH_USER, SSHConfig, SSHProvider, FuchsiaSSHError
from acts.controllers.fuchsia_lib.utils_lib import flash

MOBLY_CONTROLLER_CONFIG_NAME = "FuchsiaDevice"
ACTS_CONTROLLER_REFERENCE_NAME = "fuchsia_devices"

CONTROL_PATH_REPLACE_VALUE = " ControlPath /tmp/fuchsia--%r@%h:%p"

FUCHSIA_DEVICE_EMPTY_CONFIG_MSG = "Configuration is empty, abort!"
FUCHSIA_DEVICE_NOT_LIST_CONFIG_MSG = "Configuration should be a list, abort!"
FUCHSIA_DEVICE_INVALID_CONFIG = ("Fuchsia device config must be either a str "
                                 "or dict. abort! Invalid element %i in %r")
FUCHSIA_DEVICE_NO_IP_MSG = "No IP address specified, abort!"
FUCHSIA_COULD_NOT_GET_DESIRED_STATE = "Could not %s %s."
FUCHSIA_INVALID_CONTROL_STATE = "Invalid control state (%s). abort!"

FUCHSIA_TIME_IN_NANOSECONDS = 1000000000

SL4F_APK_NAME = "com.googlecode.android_scripting"
DAEMON_INIT_TIMEOUT_SEC = 1

DAEMON_ACTIVATED_STATES = ["running", "start"]
DAEMON_DEACTIVATED_STATES = ["stop", "stopped"]

FUCHSIA_RECONNECT_AFTER_REBOOT_TIME = 5

CHANNEL_OPEN_TIMEOUT = 5

FUCHSIA_REBOOT_TYPE_SOFT = 'soft'
FUCHSIA_REBOOT_TYPE_SOFT_AND_FLASH = 'flash'
FUCHSIA_REBOOT_TYPE_HARD = 'hard'

FUCHSIA_DEFAULT_CONNECT_TIMEOUT = 90
FUCHSIA_DEFAULT_COMMAND_TIMEOUT = 60

FUCHSIA_DEFAULT_CLEAN_UP_COMMAND_TIMEOUT = 15

FUCHSIA_COUNTRY_CODE_TIMEOUT = 15
FUCHSIA_DEFAULT_COUNTRY_CODE_US = 'US'

MDNS_LOOKUP_RETRY_MAX = 3

VALID_ASSOCIATION_MECHANISMS = {None, 'policy', 'drivers'}
IP_ADDRESS_TIMEOUT = 15


class FuchsiaDeviceError(signals.ControllerError):
    pass


class FuchsiaConfigError(signals.ControllerError):
    """Incorrect FuchsiaDevice configuration."""


def create(configs):
    if not configs:
        raise FuchsiaDeviceError(FUCHSIA_DEVICE_EMPTY_CONFIG_MSG)
    elif not isinstance(configs, list):
        raise FuchsiaDeviceError(FUCHSIA_DEVICE_NOT_LIST_CONFIG_MSG)
    for index, config in enumerate(configs):
        if isinstance(config, str):
            configs[index] = {"ip": config}
        elif not isinstance(config, dict):
            raise FuchsiaDeviceError(FUCHSIA_DEVICE_INVALID_CONFIG %
                                     (index, configs))
    return get_instances(configs)


def destroy(fds):
    for fd in fds:
        fd.clean_up()
        del fd


def get_info(fds):
    """Get information on a list of FuchsiaDevice objects.

    Args:
        fds: A list of FuchsiaDevice objects.

    Returns:
        A list of dict, each representing info for FuchsiaDevice objects.
    """
    device_info = []
    for fd in fds:
        info = {"ip": fd.ip}
        device_info.append(info)
    return device_info


def get_instances(fds_conf_data):
    """Create FuchsiaDevice instances from a list of Fuchsia ips.

    Args:
        fds_conf_data: A list of dicts that contain Fuchsia device info.

    Returns:
        A list of FuchsiaDevice objects.
    """

    return [FuchsiaDevice(fd_conf_data) for fd_conf_data in fds_conf_data]


class FuchsiaDevice:
    """Class representing a Fuchsia device.

    Each object of this class represents one Fuchsia device in ACTS.

    Attributes:
        ip: The full address or Fuchsia abstract name to contact the Fuchsia
            device at
        log: A logger object.
        ssh_port: The SSH TCP port number of the Fuchsia device.
        sl4f_port: The SL4F HTTP port number of the Fuchsia device.
        ssh_config: The ssh_config for connecting to the Fuchsia device.
    """

    def __init__(self, fd_conf_data):
        """
        Args:
            fd_conf_data: A dict of a fuchsia device configuration data
                Required keys:
                    ip: IP address of fuchsia device
                optional key:
                    sl4_port: Port for the sl4f web server on the fuchsia device
                              (Default: 80)
                    ssh_config: Location of the ssh_config file to connect to
                        the fuchsia device
                        (Default: None)
                    ssh_port: Port for the ssh server on the fuchsia device
                              (Default: 22)
        """
        self.conf_data = fd_conf_data
        if "ip" not in fd_conf_data:
            raise FuchsiaDeviceError(FUCHSIA_DEVICE_NO_IP_MSG)
        self.ip: str = fd_conf_data["ip"]
        self.orig_ip: str = fd_conf_data["ip"]
        self.sl4f_port: int = fd_conf_data.get("sl4f_port", 80)
        self.ssh_port: int = fd_conf_data.get("ssh_port", DEFAULT_SSH_PORT)
        self.ssh_config: Optional[str] = fd_conf_data.get("ssh_config", None)
        self.ssh_priv_key: Optional[str] = fd_conf_data.get(
            "ssh_priv_key", None)
        self.authorized_file: Optional[str] = fd_conf_data.get(
            "authorized_file_loc", None)
        self.serial_number: Optional[str] = fd_conf_data.get(
            "serial_number", None)
        self.device_type: Optional[str] = fd_conf_data.get("device_type", None)
        self.product_type: Optional[str] = fd_conf_data.get(
            "product_type", None)
        self.board_type: Optional[str] = fd_conf_data.get("board_type", None)
        self.build_number: Optional[str] = fd_conf_data.get(
            "build_number", None)
        self.build_type: Optional[str] = fd_conf_data.get("build_type", None)
        self.server_path: Optional[str] = fd_conf_data.get("server_path", None)
        self.specific_image: Optional[str] = fd_conf_data.get(
            "specific_image", None)
        self.ffx_binary_path: Optional[str] = fd_conf_data.get(
            "ffx_binary_path", None)
        # Path to a tar.gz archive with pm and amber-files, as necessary for
        # starting a package server.
        self.packages_archive_path: Optional[str] = fd_conf_data.get(
            "packages_archive_path", None)
        self.mdns_name: Optional[str] = fd_conf_data.get("mdns_name", None)

        # Instead of the input ssh_config, a new config is generated with proper
        # ControlPath to the test output directory.
        output_path = context.get_current_context().get_base_output_path()
        generated_ssh_config = os.path.join(output_path,
                                            "ssh_config_{}".format(self.ip))
        self._set_control_path_config(self.ssh_config, generated_ssh_config)
        self.ssh_config = generated_ssh_config

        self.ssh_username = fd_conf_data.get("ssh_username", DEFAULT_SSH_USER)
        self.hard_reboot_on_fail = fd_conf_data.get("hard_reboot_on_fail",
                                                    False)
        self.take_bug_report_on_fail = fd_conf_data.get(
            "take_bug_report_on_fail", False)
        self.device_pdu_config = fd_conf_data.get("PduDevice", None)
        self.config_country_code = fd_conf_data.get(
            'country_code', FUCHSIA_DEFAULT_COUNTRY_CODE_US).upper()

        # WLAN interface info is populated inside configure_wlan
        self.wlan_client_interfaces = {}
        self.wlan_ap_interfaces = {}
        self.wlan_client_test_interface_name = fd_conf_data.get(
            'wlan_client_test_interface', None)
        self.wlan_ap_test_interface_name = fd_conf_data.get(
            'wlan_ap_test_interface', None)

        # Whether to use 'policy' or 'drivers' for WLAN connect/disconnect calls
        # If set to None, wlan is not configured.
        self.association_mechanism = None
        # Defaults to policy layer, unless otherwise specified in the config
        self.default_association_mechanism = fd_conf_data.get(
            'association_mechanism', 'policy')

        # Whether to clear and preserve existing saved networks and client
        # connections state, to be restored at device teardown.
        self.default_preserve_saved_networks = fd_conf_data.get(
            'preserve_saved_networks', True)

        if not utils.is_valid_ipv4_address(
                self.ip) and not utils.is_valid_ipv6_address(self.ip):
            mdns_ip = None
            for retry_counter in range(MDNS_LOOKUP_RETRY_MAX):
                mdns_ip = get_fuchsia_mdns_ipv6_address(self.ip)
                if mdns_ip:
                    break
                else:
                    time.sleep(1)
            if mdns_ip and utils.is_valid_ipv6_address(mdns_ip):
                # self.ip was actually an mdns name. Use it for self.mdns_name
                # unless one was explicitly provided.
                self.mdns_name = self.mdns_name or self.ip
                self.ip = mdns_ip
            else:
                raise ValueError('Invalid IP: %s' % self.ip)

        self.log = acts_logger.create_tagged_trace_logger(
            "FuchsiaDevice | %s" % self.orig_ip)

        self.ping_rtt_match = re.compile(r'RTT Min/Max/Avg '
                                         r'= \[ (.*?) / (.*?) / (.*?) \] ms')
        self.serial = re.sub('[.:%]', '_', self.ip)
        log_path_base = getattr(logging, 'log_path', '/tmp/logs')
        self.log_path = os.path.join(log_path_base,
                                     'FuchsiaDevice%s' % self.serial)
        self.fuchsia_log_file_path = os.path.join(
            self.log_path, "fuchsialog_%s_debug.txt" % self.serial)
        self.log_process = None
        self.package_server = None

        self.init_controllers()

    @property
    def sl4f(self):
        """Get the sl4f module configured for this device.

        The sl4f module uses lazy-initialization; it will initialize an sl4f
        server on the host device when it is required.
        """
        if not hasattr(self, '_sl4f'):
            self._sl4f = SL4F(self.ssh, self.sl4f_port)
            self.log.info('Started SL4F server')
        return self._sl4f

    @sl4f.deleter
    def sl4f(self):
        if not hasattr(self, '_sl4f'):
            return
        del self._sl4f

    @property
    def ssh(self):
        """Get the SSH provider module configured for this device."""
        if not hasattr(self, '_ssh'):
            if not self.ssh_port:
                raise FuchsiaConfigError(
                    'Must provide "ssh_port: <int>" in the device config')
            if not self.ssh_priv_key:
                raise FuchsiaConfigError(
                    'Must provide "ssh_priv_key: <file path>" in the device config'
                )
            self._ssh = SSHProvider(
                SSHConfig(self.ip, self.ssh_priv_key, port=self.ssh_port))
        return self._ssh

    @ssh.deleter
    def ssh(self):
        if not hasattr(self, '_ssh'):
            return
        del self._ssh

    @property
    def ffx(self):
        """Get the ffx module configured for this device.

        The ffx module uses lazy-initialization; it will initialize an ffx
        connection to the device when it is required.

        If ffx needs to be reinitialized, delete the "ffx" property and attempt
        access again. Note re-initialization will interrupt any running ffx
        calls.
        """
        if not hasattr(self, '_ffx'):
            if not self.ffx_binary_path:
                raise FuchsiaConfigError(
                    'Must provide "ffx_binary_path: <path to FFX binary>" in the device config'
                )
            if not self.mdns_name:
                raise FuchsiaConfigError(
                    'Must provide "mdns_name: <device mDNS name>" in the device config'
                )
            self._ffx = FFX(self.ffx_binary_path, self.mdns_name, self.ip,
                            self.ssh_priv_key)
        return self._ffx

    @ffx.deleter
    def ffx(self):
        if not hasattr(self, '_ffx'):
            return
        self._ffx.clean_up()
        del self._ffx

    def _set_control_path_config(self, old_config, new_config):
        """Given an input ssh_config, write to a new config with proper
        ControlPath values in place, if it doesn't exist already.

        Args:
            old_config: string, path to the input config
            new_config: string, path to store the new config
        """
        if os.path.isfile(new_config):
            return

        ssh_config_copy = ""

        with open(old_config, 'r') as file:
            ssh_config_copy = re.sub('(\sControlPath\s.*)',
                                     CONTROL_PATH_REPLACE_VALUE,
                                     file.read(),
                                     flags=re.M)
        with open(new_config, 'w') as file:
            file.write(ssh_config_copy)

    def init_controllers(self):
        # Contains Netstack functions
        self.netstack_controller = NetstackController(self)

        # Contains WLAN core functions
        self.wlan_controller = WlanController(self)

        # Contains WLAN policy functions like save_network, remove_network, etc
        self.wlan_policy_controller = WlanPolicyController(self.sl4f, self.ffx)

    def start_package_server(self):
        if not self.packages_archive_path:
            self.log.warn(
                "packages_archive_path is not specified. "
                "Assuming a package server is already running and configured on "
                "the DUT. If this is not the case, either run your own package "
                "server, or configure these fields appropriately. "
                "This is usually required for the Fuchsia iPerf3 client or "
                "other testing utilities not on device cache.")
            return
        if self.package_server:
            self.log.warn(
                "Skipping to start the package server since is already running"
            )
            return

        self.package_server = PackageServer(self.packages_archive_path)
        self.package_server.start()
        self.package_server.configure_device(self.ssh)

    def run_commands_from_config(self, cmd_dicts):
        """Runs commands on the Fuchsia device from the config file. Useful for
        device and/or Fuchsia specific configuration.

        Args:
            cmd_dicts: list of dictionaries containing the following
                'cmd': string, command to run on device
                'timeout': int, seconds to wait for command to run (optional)
                'skip_status_code_check': bool, disregard errors if true

        Raises:
            FuchsiaDeviceError: if any of the commands return a non-zero status
                code and skip_status_code_check is false or undefined.
        """
        for cmd_dict in cmd_dicts:
            try:
                cmd = cmd_dict['cmd']
            except KeyError:
                raise FuchsiaDeviceError(
                    'To run a command via config, you must provide key "cmd" '
                    'containing the command string.')

            timeout = cmd_dict.get('timeout', FUCHSIA_DEFAULT_COMMAND_TIMEOUT)
            # Catch both boolean and string values from JSON
            skip_status_code_check = 'true' == str(
                cmd_dict.get('skip_status_code_check', False)).lower()

            if skip_status_code_check:
                self.log.info(f'Running command "{cmd}" and ignoring result.')
            else:
                self.log.info(f'Running command "{cmd}".')

            try:
                result = self.ssh.run(cmd, timeout_sec=timeout)
                self.log.debug(result)
            except FuchsiaSSHError as e:
                if not skip_status_code_check:
                    raise FuchsiaDeviceError(
                        'Failed device specific commands for initial configuration'
                    ) from e

    def configure_wlan(self,
                       association_mechanism=None,
                       preserve_saved_networks=None):
        """
        Readies device for WLAN functionality. If applicable, connects to the
        policy layer and clears/saves preexisting saved networks.

        Args:
            association_mechanism: string, 'policy' or 'drivers'. If None, uses
                the default value from init (can be set by ACTS config)
            preserve_saved_networks: bool, whether to clear existing saved
                networks, and preserve them for restoration later. If None, uses
                the default value from init (can be set by ACTS config)

        Raises:
            FuchsiaDeviceError, if configuration fails
        """

        # Set the country code US by default, or country code provided
        # in ACTS config
        self.configure_regulatory_domain(self.config_country_code)

        # If args aren't provided, use the defaults, which can be set in the
        # config.
        if association_mechanism is None:
            association_mechanism = self.default_association_mechanism
        if preserve_saved_networks is None:
            preserve_saved_networks = self.default_preserve_saved_networks

        if association_mechanism not in VALID_ASSOCIATION_MECHANISMS:
            raise FuchsiaDeviceError(
                'Invalid FuchsiaDevice association_mechanism: %s' %
                association_mechanism)

        # Allows for wlan to be set up differently in different tests
        if self.association_mechanism:
            self.log.info('Deconfiguring WLAN')
            self.deconfigure_wlan()

        self.association_mechanism = association_mechanism

        self.log.info('Configuring WLAN w/ association mechanism: %s' %
                      association_mechanism)
        if association_mechanism == 'drivers':
            self.log.warn(
                'You may encounter unusual device behavior when using the '
                'drivers directly for WLAN. This should be reserved for '
                'debugging specific issues. Normal test runs should use the '
                'policy layer.')
            if preserve_saved_networks:
                self.log.warn(
                    'Unable to preserve saved networks when using drivers '
                    'association mechanism (requires policy layer control).')
        else:
            # This requires SL4F calls, so it can only happen with actual
            # devices, not with unit tests.
            self.wlan_policy_controller.configure_wlan(preserve_saved_networks)

        # Retrieve WLAN client and AP interfaces
        self.wlan_controller.update_wlan_interfaces()

    def deconfigure_wlan(self):
        """
        Stops WLAN functionality (if it has been started). Used to allow
        different tests to use WLAN differently (e.g. some tests require using
        wlan policy, while the abstract wlan_device can be setup to use policy
        or drivers)

        Raises:
            FuchsiaDeviveError, if deconfigure fails.
        """
        if not self.association_mechanism:
            self.log.debug(
                'WLAN not configured before deconfigure was called.')
            return
        # If using policy, stop client connections. Otherwise, just clear
        # variables.
        if self.association_mechanism != 'drivers':
            self.wlan_policy_controller._deconfigure_wlan()
        self.association_mechanism = None

    def reboot(self,
               use_ssh: bool = False,
               unreachable_timeout: int = FUCHSIA_DEFAULT_CONNECT_TIMEOUT,
               ping_timeout: int = FUCHSIA_DEFAULT_CONNECT_TIMEOUT,
               ssh_timeout: int = FUCHSIA_DEFAULT_CONNECT_TIMEOUT,
               reboot_type: int = FUCHSIA_REBOOT_TYPE_SOFT,
               testbed_pdus: list[pdu.PduDevice] = None) -> None:
        """Reboot a FuchsiaDevice.

        Soft reboots the device, verifies it becomes unreachable, then verifies
        it comes back online. Re-initializes services so the tests can continue.

        Args:
            use_ssh: if True, use fuchsia shell command via ssh to reboot
                instead of SL4F.
            unreachable_timeout: time to wait for device to become unreachable.
            ping_timeout:time to wait for device to respond to pings.
            ssh_timeout: time to wait for device to be reachable via ssh.
            reboot_type: 'soft', 'hard' or 'flash'.
            testbed_pdus: all testbed PDUs.

        Raises:
            ConnectionError, if device fails to become unreachable or fails to
                come back up.
        """
        if reboot_type == FUCHSIA_REBOOT_TYPE_SOFT:
            if use_ssh:
                self.log.info('Soft rebooting via SSH')
                try:
                    self.ssh.run(
                        'dm reboot',
                        timeout_sec=FUCHSIA_RECONNECT_AFTER_REBOOT_TIME)
                except FuchsiaSSHError as e:
                    if 'closed by remote host' not in e.result.stderr:
                        raise e
            else:
                self.log.info('Soft rebooting via SL4F')
                self.sl4f.hardware_power_statecontrol_lib.suspendReboot(
                    timeout=3)
            self._check_unreachable(timeout_sec=unreachable_timeout)

        elif reboot_type == FUCHSIA_REBOOT_TYPE_HARD:
            self.log.info('Hard rebooting via PDU')
            if not testbed_pdus:
                raise AttributeError('Testbed PDUs must be supplied '
                                     'to hard reboot a fuchsia_device.')
            device_pdu, device_pdu_port = pdu.get_pdu_port_for_device(
                self.device_pdu_config, testbed_pdus)
            self.log.info('Killing power to FuchsiaDevice')
            device_pdu.off(str(device_pdu_port))
            self._check_unreachable(timeout_sec=unreachable_timeout)
            self.log.info('Restoring power to FuchsiaDevice')
            device_pdu.on(str(device_pdu_port))

        elif reboot_type == FUCHSIA_REBOOT_TYPE_SOFT_AND_FLASH:
            flash(self, use_ssh, FUCHSIA_RECONNECT_AFTER_REBOOT_TIME)

        else:
            raise ValueError('Invalid reboot type: %s' % reboot_type)

        self._check_reachable(timeout_sec=ping_timeout)

        # Cleanup services
        self.stop_services()

        self.log.info('Waiting for device to allow ssh connection.')
        end_time = time.time() + ssh_timeout
        while time.time() < end_time:
            try:
                self.ssh.run('echo')
            except Exception as e:
                self.log.debug(f'Retrying SSH to device. Details: {e}')
            else:
                break
        else:
            raise ConnectionError('Failed to connect to device via SSH.')
        self.log.info('Device now available via ssh.')

        # TODO (b/246852449): Move configure_wlan to other controllers.
        # If wlan was configured before reboot, it must be configured again
        # after rebooting, as it was before reboot. No preserving should occur.
        if self.association_mechanism:
            pre_reboot_association_mechanism = self.association_mechanism
            # Prevent configure_wlan from thinking it needs to deconfigure first
            self.association_mechanism = None
            self.configure_wlan(
                association_mechanism=pre_reboot_association_mechanism,
                preserve_saved_networks=False)

        self.log.info('Device has rebooted')

    def version(self):
        """Returns the version of Fuchsia running on the device.

        Returns:
            A string containing the Fuchsia version number or nothing if there
            is no version information attached during the build.
            For example, "5.20210713.2.1" or "".

        Raises:
            FFXTimeout: when the command times out.
            FFXError: when the command returns non-zero and skip_status_code_check is False.
        """
        target_info_json = self.ffx.run("target show --json").stdout
        target_info = json.loads(target_info_json)
        build_info = [
            entry for entry in target_info if entry["label"] == "build"
        ]
        if len(build_info) != 1:
            self.log.warning(
                f'Expected one entry with label "build", found {build_info}')
            return ""
        version_info = [
            child for child in build_info[0]["child"]
            if child["label"] == "version"
        ]
        if len(version_info) != 1:
            self.log.warning(
                f'Expected one entry child with label "version", found {build_info}'
            )
            return ""
        return version_info[0]["value"]

    def ping(self,
             dest_ip,
             count=3,
             interval=1000,
             timeout=1000,
             size=25,
             additional_ping_params=None):
        """Pings from a Fuchsia device to an IPv4 address or hostname

        Args:
            dest_ip: (str) The ip or hostname to ping.
            count: (int) How many icmp packets to send.
            interval: (int) How long to wait between pings (ms)
            timeout: (int) How long to wait before having the icmp packet
                timeout (ms).
            size: (int) Size of the icmp packet.
            additional_ping_params: (str) command option flags to
                append to the command string

        Returns:
            A dictionary for the results of the ping.  The dictionary contains
            the following items:
                status: Whether the ping was successful.
                rtt_min: The minimum round trip time of the ping.
                rtt_max: The minimum round trip time of the ping.
                rtt_avg: The avg round trip time of the ping.
                stdout: The standard out of the ping command.
                stderr: The standard error of the ping command.
        """
        rtt_min = None
        rtt_max = None
        rtt_avg = None
        self.log.debug("Pinging %s..." % dest_ip)
        if not additional_ping_params:
            additional_ping_params = ''

        try:
            ping_result = self.ssh.run(
                f'ping -c {count} -i {interval} -t {timeout} -s {size} '
                f'{additional_ping_params} {dest_ip}')
        except FuchsiaSSHError as e:
            ping_result = e.result

        if ping_result.stderr:
            status = False
        else:
            status = True
            rtt_line = ping_result.stdout.split('\n')[:-1]
            rtt_line = rtt_line[-1]
            rtt_stats = re.search(self.ping_rtt_match, rtt_line)
            rtt_min = rtt_stats.group(1)
            rtt_max = rtt_stats.group(2)
            rtt_avg = rtt_stats.group(3)
        return {
            'status': status,
            'rtt_min': rtt_min,
            'rtt_max': rtt_max,
            'rtt_avg': rtt_avg,
            'stdout': ping_result.stdout,
            'stderr': ping_result.stderr
        }

    def can_ping(self,
                 dest_ip,
                 count=1,
                 interval=1000,
                 timeout=1000,
                 size=25,
                 additional_ping_params=None):
        """Returns whether fuchsia device can ping a given dest address"""
        ping_result = self.ping(dest_ip,
                                count=count,
                                interval=interval,
                                timeout=timeout,
                                size=size,
                                additional_ping_params=additional_ping_params)
        return ping_result['status']

    def clean_up(self):
        """Cleans up the FuchsiaDevice object, releases any resources it
        claimed, and restores saved networks if applicable. For reboots, use
        clean_up_services only.

        Note: Any exceptions thrown in this method must be caught and handled,
        ensuring that clean_up_services is run. Otherwise, the syslog listening
        thread will never join and will leave tests hanging.
        """
        # If and only if wlan is configured, and using the policy layer
        if self.association_mechanism == 'policy':
            try:
                self.wlan_policy_controller.clean_up()
            except Exception as err:
                self.log.warning('Unable to clean up WLAN Policy layer: %s' %
                                 err)

        self.stop_services()

        if self.package_server:
            self.package_server.clean_up()

    def get_interface_ip_addresses(self, interface):
        return get_interface_ip_addresses(self, interface)

    def wait_for_ipv4_addr(self, interface: str) -> None:
        """Checks if device has an ipv4 private address. Sleeps 1 second between
        retries.

        Args:
            interface: name of interface from which to get ipv4 address.

        Raises:
            ConnectionError, if device does not have an ipv4 address after all
            timeout.
        """
        self.log.info(
            f'Checking for valid ipv4 addr. Retry {IP_ADDRESS_TIMEOUT} seconds.'
        )
        timeout = time.time() + IP_ADDRESS_TIMEOUT
        while time.time() < timeout:
            ip_addrs = self.get_interface_ip_addresses(interface)

            if len(ip_addrs['ipv4_private']) > 0:
                self.log.info("Device has an ipv4 address: "
                              f"{ip_addrs['ipv4_private'][0]}")
                break
            else:
                self.log.debug(
                    'Device does not yet have an ipv4 address...retrying in 1 '
                    'second.')
                time.sleep(1)
        else:
            raise ConnectionError('Device failed to get an ipv4 address.')

    def wait_for_ipv6_addr(self, interface: str) -> None:
        """Checks if device has an ipv6 private local address. Sleeps 1 second
        between retries.

        Args:
            interface: name of interface from which to get ipv6 address.

        Raises:
            ConnectionError, if device does not have an ipv6 address after all
            timeout.
        """
        self.log.info(
            f'Checking for valid ipv6 addr. Retry {IP_ADDRESS_TIMEOUT} seconds.'
        )
        timeout = time.time() + IP_ADDRESS_TIMEOUT
        while time.time() < timeout:
            ip_addrs = self.get_interface_ip_addresses(interface)
            if len(ip_addrs['ipv6_private_local']) > 0:
                self.log.info("Device has an ipv6 private local address: "
                              f"{ip_addrs['ipv6_private_local'][0]}")
                break
            else:
                self.log.debug(
                    'Device does not yet have an ipv6 address...retrying in 1 '
                    'second.')
                time.sleep(1)
        else:
            raise ConnectionError('Device failed to get an ipv6 address.')

    def _check_reachable(self,
                         timeout_sec: int = FUCHSIA_DEFAULT_CONNECT_TIMEOUT
                         ) -> None:
        """Checks the reachability of the Fuchsia device."""
        end_time = time.time() + timeout_sec
        self.log.info('Verifying device is reachable.')
        while time.time() < end_time:
            # TODO (b/249343632): Consolidate ping commands and fix timeout in
            # utils.can_ping.
            if utils.can_ping(job, self.ip):
                self.log.info('Device is reachable.')
                break
            else:
                self.log.debug(
                    'Device is not reachable. Retrying in 1 second.')
                time.sleep(1)
        else:
            raise ConnectionError('Device is unreachable.')

    def _check_unreachable(self,
                           timeout_sec: int = FUCHSIA_DEFAULT_CONNECT_TIMEOUT
                           ) -> None:
        """Checks the Fuchsia device becomes unreachable."""
        end_time = time.time() + timeout_sec
        self.log.info('Verifying device is unreachable.')
        while (time.time() < end_time):
            if utils.can_ping(job, self.ip):
                self.log.debug(
                    'Device is still reachable. Retrying in 1 second.')
                time.sleep(1)
            else:
                self.log.info('Device is not reachable.')
                break
        else:
            raise ConnectionError('Device failed to become unreachable.')

    def check_connect_response(self, connect_response):
        if connect_response.get("error") is None:
            # Checks the response from SL4F and if there is no error, check
            # the result.
            connection_result = connect_response.get("result")
            if not connection_result:
                # Ideally the error would be present but just outputting a log
                # message until available.
                self.log.debug("Connect call failed, aborting!")
                return False
            else:
                # Returns True if connection was successful.
                return True
        else:
            # the response indicates an error - log and raise failure
            self.log.debug("Aborting! - Connect call failed with error: %s" %
                           connect_response.get("error"))
            return False

    def check_disconnect_response(self, disconnect_response):
        if disconnect_response.get("error") is None:
            # Returns True if disconnect was successful.
            return True
        else:
            # the response indicates an error - log and raise failure
            self.log.debug("Disconnect call failed with error: %s" %
                           disconnect_response.get("error"))
            return False

    # TODO(fxb/64657): Determine more stable solution to country code config on
    # device bring up.
    def configure_regulatory_domain(self, desired_country_code):
        """Allows the user to set the device country code via ACTS config

        Usage:
            In FuchsiaDevice config, add "country_code": "<CC>"
        """
        if self.ssh_config:
            # Country code can be None, from ACTS config.
            if desired_country_code:
                desired_country_code = desired_country_code.upper()
                response = self.sl4f.regulatory_region_lib.setRegion(
                    desired_country_code)
                if response.get('error'):
                    raise FuchsiaDeviceError(
                        'Failed to set regulatory domain. Err: %s' %
                        response['error'])
                end_time = time.time() + FUCHSIA_COUNTRY_CODE_TIMEOUT
                while time.time() < end_time:
                    ascii_cc = self.sl4f.wlan_lib.wlanGetCountry(0).get(
                        'result')
                    # Convert ascii_cc to string, then compare
                    if ascii_cc and (''.join(chr(c) for c in ascii_cc).upper()
                                     == desired_country_code):
                        self.log.debug('Country code successfully set to %s.' %
                                       desired_country_code)
                        return
                    self.log.debug('Country code not yet updated. Retrying.')
                    time.sleep(1)
                raise FuchsiaDeviceError('Country code never updated to %s' %
                                         desired_country_code)

    def stop_services(self):
        """Stops the ffx daemon and deletes SL4F property."""
        self.log.info('Stopping host device services.')
        del self.sl4f
        del self.ffx

    def load_config(self, config):
        pass

    def take_bug_report(self, test_name=None, begin_time=None):
        """Takes a bug report on the device and stores it in a file.

        Args:
            test_name: DEPRECATED. Do not specify this argument; it is only used
                for logging. Name of the test case that triggered this bug
                report.
            begin_time: DEPRECATED. Do not specify this argument; it allows
                overwriting of bug reports when this function is called several
                times in one test. Epoch time when the test started. If not
                specified, the current time will be used.
        """
        if not self.ssh_config:
            self.log.warn(
                'Skipping take_bug_report because ssh_config is not specified')
            return

        if test_name:
            self.log.info(
                f"Taking snapshot of {self.mdns_name} for {test_name}")
        else:
            self.log.info(f"Taking snapshot of {self.mdns_name}")

        epoch = begin_time if begin_time else utils.get_current_epoch_time()
        time_stamp = acts_logger.normalize_log_line_timestamp(
            acts_logger.epoch_to_log_line_timestamp(epoch))
        out_dir = context.get_current_context().get_full_output_path()
        out_path = os.path.join(out_dir, f'{self.mdns_name}_{time_stamp}.zip')

        try:
            subprocess.run(
                [f"ssh -F {self.ssh_config} {self.ip} snapshot > {out_path}"],
                shell=True)
            self.log.info(f'Snapshot saved to {out_path}')
        except Exception as err:
            self.log.error(f'Failed to take snapshot: {err}')

    def take_bt_snoop_log(self, custom_name=None):
        """Takes a the bt-snoop log from the device and stores it in a file
        in a pcap format.
        """
        bt_snoop_path = context.get_current_context().get_full_output_path()
        time_stamp = acts_logger.normalize_log_line_timestamp(
            acts_logger.epoch_to_log_line_timestamp(time.time()))
        out_name = "FuchsiaDevice%s_%s" % (
            self.serial, time_stamp.replace(" ", "_").replace(":", "-"))
        out_name = "%s.pcap" % out_name
        if custom_name:
            out_name = "%s_%s.pcap" % (self.serial, custom_name)
        else:
            out_name = "%s.pcap" % out_name
        full_out_path = os.path.join(bt_snoop_path, out_name)
        bt_snoop_data = self.ssh.run('bt-snoop-cli -d -f pcap').raw_stdout
        bt_snoop_file = open(full_out_path, 'wb')
        bt_snoop_file.write(bt_snoop_data)
        bt_snoop_file.close()
