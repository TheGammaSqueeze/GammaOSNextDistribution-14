#!/usr/bin/env python3
#
#   Copyright 2016 - Google, Inc.
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

import collections
import ipaddress
import os
import time
from typing import FrozenSet, Set

from acts import logger
from acts import utils

from acts.controllers import pdu
from acts.controllers.ap_lib import ap_get_interface
from acts.controllers.ap_lib import ap_iwconfig
from acts.controllers.ap_lib import bridge_interface
from acts.controllers.ap_lib import dhcp_config
from acts.controllers.ap_lib import dhcp_server
from acts.controllers.ap_lib import hostapd
from acts.controllers.ap_lib import hostapd_ap_preset
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib import hostapd_config
from acts.controllers.ap_lib import radvd
from acts.controllers.ap_lib import radvd_config
from acts.controllers.ap_lib.extended_capabilities import ExtendedCapabilities
from acts.controllers.ap_lib.wireless_network_management import BssTransitionManagementRequest
from acts.controllers.utils_lib.commands import ip
from acts.controllers.utils_lib.commands import route
from acts.controllers.utils_lib.commands import shell
from acts.controllers.utils_lib.ssh import connection
from acts.controllers.utils_lib.ssh import settings
from acts.libs.proc import job

MOBLY_CONTROLLER_CONFIG_NAME = 'AccessPoint'
ACTS_CONTROLLER_REFERENCE_NAME = 'access_points'
_BRCTL = 'brctl'

LIFETIME = 180
PROC_NET_SNMP6 = '/proc/net/snmp6'
SCAPY_INSTALL_COMMAND = 'sudo python setup.py install'
RA_MULTICAST_ADDR = '33:33:00:00:00:01'
RA_SCRIPT = 'sendra.py'


def create(configs):
    """Creates ap controllers from a json config.

    Creates an ap controller from either a list, or a single
    element. The element can either be just the hostname or a dictionary
    containing the hostname and username of the ap to connect to over ssh.

    Args:
        The json configs that represent this controller.

    Returns:
        A new AccessPoint.
    """
    return [AccessPoint(c) for c in configs]


def destroy(aps):
    """Destroys a list of access points.

    Args:
        aps: The list of access points to destroy.
    """
    for ap in aps:
        ap.close()


def get_info(aps):
    """Get information on a list of access points.

    Args:
        aps: A list of AccessPoints.

    Returns:
        A list of all aps hostname.
    """
    return [ap.ssh_settings.hostname for ap in aps]


def setup_ap(
        access_point,
        profile_name,
        channel,
        ssid,
        mode=None,
        preamble=None,
        beacon_interval=None,
        dtim_period=None,
        frag_threshold=None,
        rts_threshold=None,
        force_wmm=None,
        hidden=False,
        security=None,
        pmf_support=None,
        additional_ap_parameters=None,
        password=None,
        n_capabilities=None,
        ac_capabilities=None,
        vht_bandwidth=None,
        wnm_features: FrozenSet[hostapd_constants.WnmFeature] = frozenset(),
        setup_bridge=False,
        is_ipv6_enabled=False,
        is_nat_enabled=True):
    """Creates a hostapd profile and runs it on an ap. This is a convenience
    function that allows us to start an ap with a single function, without first
    creating a hostapd config.

    Args:
        access_point: An ACTS access_point controller
        profile_name: The profile name of one of the hostapd ap presets.
        channel: What channel to set the AP to.
        preamble: Whether to set short or long preamble (True or False)
        beacon_interval: The beacon interval (int)
        dtim_period: Length of dtim period (int)
        frag_threshold: Fragmentation threshold (int)
        rts_threshold: RTS threshold (int)
        force_wmm: Enable WMM or not (True or False)
        hidden: Advertise the SSID or not (True or False)
        security: What security to enable.
        pmf_support: int, whether pmf is not disabled, enabled, or required
        additional_ap_parameters: Additional parameters to send the AP.
        password: Password to connect to WLAN if necessary.
        check_connectivity: Whether to check for internet connectivity.
        wnm_features: WNM features to enable on the AP.
        setup_bridge: Whether to bridge the LAN interface WLAN interface.
            Only one WLAN interface can be bridged with the LAN interface
            and none of the guest networks can be bridged.
        is_ipv6_enabled: If True, start a IPv6 router advertisement daemon
        is_nat_enabled: If True, start NAT on the AP to allow the DUT to be able
            to access the internet if the WAN port is connected to the internet.

    Returns:
        An identifier for each ssid being started. These identifiers can be
        used later by this controller to control the ap.

    Raises:
        Error: When the ap can't be brought up.
    """
    ap = hostapd_ap_preset.create_ap_preset(profile_name=profile_name,
                                            iface_wlan_2g=access_point.wlan_2g,
                                            iface_wlan_5g=access_point.wlan_5g,
                                            channel=channel,
                                            ssid=ssid,
                                            mode=mode,
                                            short_preamble=preamble,
                                            beacon_interval=beacon_interval,
                                            dtim_period=dtim_period,
                                            frag_threshold=frag_threshold,
                                            rts_threshold=rts_threshold,
                                            force_wmm=force_wmm,
                                            hidden=hidden,
                                            bss_settings=[],
                                            security=security,
                                            pmf_support=pmf_support,
                                            n_capabilities=n_capabilities,
                                            ac_capabilities=ac_capabilities,
                                            vht_bandwidth=vht_bandwidth,
                                            wnm_features=wnm_features)
    return access_point.start_ap(
        hostapd_config=ap,
        radvd_config=radvd_config.RadvdConfig() if is_ipv6_enabled else None,
        setup_bridge=setup_bridge,
        is_nat_enabled=is_nat_enabled,
        additional_parameters=additional_ap_parameters)


class Error(Exception):
    """Error raised when there is a problem with the access point."""


_ApInstance = collections.namedtuple('_ApInstance', ['hostapd', 'subnet'])

# These ranges were split this way since each physical radio can have up
# to 8 SSIDs so for the 2GHz radio the DHCP range will be
# 192.168.1 - 8 and the 5Ghz radio will be 192.168.9 - 16
_AP_2GHZ_SUBNET_STR_DEFAULT = '192.168.1.0/24'
_AP_5GHZ_SUBNET_STR_DEFAULT = '192.168.9.0/24'

# The last digit of the ip for the bridge interface
BRIDGE_IP_LAST = '100'


class AccessPoint(object):
    """An access point controller.

    Attributes:
        ssh: The ssh connection to this ap.
        ssh_settings: The ssh settings being used by the ssh connection.
        dhcp_settings: The dhcp server settings being used.
    """

    def __init__(self, configs):
        """
        Args:
            configs: configs for the access point from config file.
        """
        self.ssh_settings = settings.from_config(configs['ssh_config'])
        self.log = logger.create_logger(
            lambda msg: f'[Access Point|{self.ssh_settings.hostname}] {msg}')
        self.device_pdu_config = configs.get('PduDevice', None)
        self.identifier = self.ssh_settings.hostname

        if 'ap_subnet' in configs:
            self._AP_2G_SUBNET_STR = configs['ap_subnet']['2g']
            self._AP_5G_SUBNET_STR = configs['ap_subnet']['5g']
        else:
            self._AP_2G_SUBNET_STR = _AP_2GHZ_SUBNET_STR_DEFAULT
            self._AP_5G_SUBNET_STR = _AP_5GHZ_SUBNET_STR_DEFAULT

        self._AP_2G_SUBNET = dhcp_config.Subnet(
            ipaddress.ip_network(self._AP_2G_SUBNET_STR))
        self._AP_5G_SUBNET = dhcp_config.Subnet(
            ipaddress.ip_network(self._AP_5G_SUBNET_STR))

        self.ssh = connection.SshConnection(self.ssh_settings)

        # Singleton utilities for running various commands.
        self._ip_cmd = ip.LinuxIpCommand(self.ssh)
        self._route_cmd = route.LinuxRouteCommand(self.ssh)

        # A map from network interface name to _ApInstance objects representing
        # the hostapd instance running against the interface.
        self._aps = dict()
        self._dhcp = None
        self._dhcp_bss = dict()
        self._radvd = None
        self.bridge = bridge_interface.BridgeInterface(self)
        self.iwconfig = ap_iwconfig.ApIwconfig(self)

        # Check to see if wan_interface is specified in acts_config for tests
        # isolated from the internet and set this override.
        self.interfaces = ap_get_interface.ApInterfaces(
            self, configs.get('wan_interface'))

        # Get needed interface names and initialize the unnecessary ones.
        self.wan = self.interfaces.get_wan_interface()
        self.wlan = self.interfaces.get_wlan_interface()
        self.wlan_2g = self.wlan[0]
        self.wlan_5g = self.wlan[1]
        self.lan = self.interfaces.get_lan_interface()
        self._initial_ap()
        self.scapy_install_path = None
        self.setup_bridge = False

    def _initial_ap(self):
        """Initial AP interfaces.

        Bring down hostapd if instance is running, bring down all bridge
        interfaces.
        """
        # This is necessary for Gale/Whirlwind flashed with dev channel image
        # Unused interfaces such as existing hostapd daemon, guest, mesh
        # interfaces need to be brought down as part of the AP initialization
        # process, otherwise test would fail.
        try:
            self.ssh.run('stop wpasupplicant')
        except job.Error:
            self.log.info('No wpasupplicant running')
        try:
            self.ssh.run('stop hostapd')
        except job.Error:
            self.log.info('No hostapd running')
        # Bring down all wireless interfaces
        for iface in self.wlan:
            WLAN_DOWN = f'ip link set {iface} down'
            self.ssh.run(WLAN_DOWN)
        # Bring down all bridge interfaces
        bridge_interfaces = self.interfaces.get_bridge_interface()
        if bridge_interfaces:
            for iface in bridge_interfaces:
                BRIDGE_DOWN = f'ip link set {iface} down'
                BRIDGE_DEL = f'brctl delbr {iface}'
                self.ssh.run(BRIDGE_DOWN)
                self.ssh.run(BRIDGE_DEL)

    def start_ap(self,
                 hostapd_config,
                 radvd_config=None,
                 setup_bridge=False,
                 is_nat_enabled=True,
                 additional_parameters=None):
        """Starts as an ap using a set of configurations.

        This will start an ap on this host. To start an ap the controller
        selects a network interface to use based on the configs given. It then
        will start up hostapd on that interface. Next a subnet is created for
        the network interface and dhcp server is refreshed to give out ips
        for that subnet for any device that connects through that interface.

        Args:
            hostapd_config: hostapd_config.HostapdConfig, The configurations
                to use when starting up the ap.
            radvd_config: radvd_config.RadvdConfig, The IPv6 configuration
                to use when starting up the ap.
            setup_bridge: Whether to bridge the LAN interface WLAN interface.
                Only one WLAN interface can be bridged with the LAN interface
                and none of the guest networks can be bridged.
            is_nat_enabled: If True, start NAT on the AP to allow the DUT to be
                able to access the internet if the WAN port is connected to the
                internet.
            additional_parameters: A dictionary of parameters that can sent
                directly into the hostapd config file.  This can be used for
                debugging and or adding one off parameters into the config.

        Returns:
            An identifier for each ssid being started. These identifiers can be
            used later by this controller to control the ap.

        Raises:
            Error: When the ap can't be brought up.
        """
        if hostapd_config.frequency < 5000:
            interface = self.wlan_2g
            subnet = self._AP_2G_SUBNET
        else:
            interface = self.wlan_5g
            subnet = self._AP_5G_SUBNET

        # In order to handle dhcp servers on any interface, the initiation of
        # the dhcp server must be done after the wlan interfaces are figured
        # out as opposed to being in __init__
        self._dhcp = dhcp_server.DhcpServer(self.ssh, interface=interface)

        # For multi bssid configurations the mac address
        # of the wireless interface needs to have enough space to mask out
        # up to 8 different mac addresses. So in for one interface the range is
        # hex 0-7 and for the other the range is hex 8-f.
        interface_mac_orig = None
        cmd = f"ip link show {interface}|grep ether|awk -F' ' '{{print $2}}'"
        interface_mac_orig = self.ssh.run(cmd)
        if interface == self.wlan_5g:
            hostapd_config.bssid = interface_mac_orig.stdout[:-1] + '0'
            last_octet = 1
        if interface == self.wlan_2g:
            hostapd_config.bssid = interface_mac_orig.stdout[:-1] + '8'
            last_octet = 9
        if interface in self._aps:
            raise ValueError('No WiFi interface available for AP on '
                             f'channel {hostapd_config.channel}')

        apd = hostapd.Hostapd(self.ssh, interface)
        new_instance = _ApInstance(hostapd=apd, subnet=subnet)
        self._aps[interface] = new_instance

        # Turn off the DHCP server, we're going to change its settings.
        self.stop_dhcp()
        # Clear all routes to prevent old routes from interfering.
        self._route_cmd.clear_routes(net_interface=interface)

        self._dhcp_bss = dict()
        if hostapd_config.bss_lookup:
            # The self._dhcp_bss dictionary is created to hold the key/value
            # pair of the interface name and the ip scope that will be
            # used for the particular interface.  The a, b, c, d
            # variables below are the octets for the ip address.  The
            # third octet is then incremented for each interface that
            # is requested.  This part is designed to bring up the
            # hostapd interfaces and not the DHCP servers for each
            # interface.
            counter = 1
            for bss in hostapd_config.bss_lookup:
                if interface_mac_orig:
                    hostapd_config.bss_lookup[bss].bssid = (
                        interface_mac_orig.stdout[:-1] + hex(last_octet)[-1:])
                self._route_cmd.clear_routes(net_interface=str(bss))
                if interface is self.wlan_2g:
                    starting_ip_range = self._AP_2G_SUBNET_STR
                else:
                    starting_ip_range = self._AP_5G_SUBNET_STR
                a, b, c, d = starting_ip_range.split('.')
                self._dhcp_bss[bss] = dhcp_config.Subnet(
                    ipaddress.ip_network(f'{a}.{b}.{int(c) + counter}.{d}'))
                counter = counter + 1
                last_octet = last_octet + 1

        apd.start(hostapd_config, additional_parameters=additional_parameters)

        # The DHCP serer requires interfaces to have ips and routes before
        # the server will come up.
        interface_ip = ipaddress.ip_interface(
            f'{subnet.router}/{subnet.network.netmask}')
        if setup_bridge is True:
            bridge_interface_name = 'eth_test'
            self.create_bridge(bridge_interface_name, [interface, self.lan])
            self._ip_cmd.set_ipv4_address(bridge_interface_name, interface_ip)
        else:
            self._ip_cmd.set_ipv4_address(interface, interface_ip)
        if hostapd_config.bss_lookup:
            # This loop goes through each interface that was setup for
            # hostapd and assigns the DHCP scopes that were defined but
            # not used during the hostapd loop above.  The k and v
            # variables represent the interface name, k, and dhcp info, v.
            for k, v in self._dhcp_bss.items():
                bss_interface_ip = ipaddress.ip_interface(
                    f'{self._dhcp_bss[k].router}/{self._dhcp_bss[k].network.netmask}'
                )
                self._ip_cmd.set_ipv4_address(str(k), bss_interface_ip)

        # Restart the DHCP server with our updated list of subnets.
        configured_subnets = self.get_configured_subnets()
        dhcp_conf = dhcp_config.DhcpConfig(subnets=configured_subnets)
        self.start_dhcp(dhcp_conf=dhcp_conf)
        if is_nat_enabled:
            self.start_nat()
            self.enable_forwarding()
        else:
            self.stop_nat()
            self.enable_forwarding()
        if radvd_config:
            radvd_interface = bridge_interface_name if setup_bridge else interface
            self._radvd = radvd.Radvd(self.ssh, radvd_interface)
            self._radvd.start(radvd_config)

        bss_interfaces = [bss for bss in hostapd_config.bss_lookup]
        bss_interfaces.append(interface)

        return bss_interfaces

    def get_configured_subnets(self):
        """Get the list of configured subnets on the access point.

        This allows consumers of the access point objects create custom DHCP
        configs with the correct subnets.

        Returns: a list of dhcp_config.Subnet objects
        """
        configured_subnets = [x.subnet for x in self._aps.values()]
        for k, v in self._dhcp_bss.items():
            configured_subnets.append(v)
        return configured_subnets

    def start_dhcp(self, dhcp_conf):
        """Start a DHCP server for the specified subnets.

        This allows consumers of the access point objects to control DHCP.

        Args:
            dhcp_conf: A dhcp_config.DhcpConfig object.

        Raises:
            Error: Raised when a dhcp server error is found.
        """
        self._dhcp.start(config=dhcp_conf)

    def stop_dhcp(self):
        """Stop DHCP for this AP object.

        This allows consumers of the access point objects to control DHCP.
        """
        self._dhcp.stop()

    def get_dhcp_logs(self):
        """Get DHCP logs for this AP object.

        This allows consumers of the access point objects to validate DHCP
        behavior.

        Returns:
            A string of the dhcp server logs, or None is a DHCP server has not
            been started.
        """
        if self._dhcp:
            return self._dhcp.get_logs()
        return None

    def get_hostapd_logs(self):
        """Get hostapd logs for all interfaces on AP object.

        This allows consumers of the access point objects to validate hostapd
        behavior.

        Returns: A dict with {interface: log} from hostapd instances.
        """
        hostapd_logs = dict()
        for identifier in self._aps:
            hostapd_logs[identifier] = self._aps.get(
                identifier).hostapd.pull_logs()
        return hostapd_logs

    def enable_forwarding(self):
        """Enable IPv4 and IPv6 forwarding on the AP.

        When forwarding is enabled, the access point is able to route IP packets
        between devices in the same subnet.
        """
        self.ssh.run('echo 1 > /proc/sys/net/ipv4/ip_forward')
        self.ssh.run('echo 1 > /proc/sys/net/ipv6/conf/all/forwarding')

    def start_nat(self):
        """Start NAT on the AP.

        This allows consumers of the access point objects to enable NAT
        on the AP.

        Note that this is currently a global setting, since we don't
        have per-interface masquerade rules.
        """
        # The following three commands are needed to enable NAT between
        # the WAN and LAN/WLAN ports.  This means anyone connecting to the
        # WLAN/LAN ports will be able to access the internet if the WAN port
        # is connected to the internet.
        self.ssh.run('iptables -t nat -F')
        self.ssh.run(
            f'iptables -t nat -A POSTROUTING -o {self.wan} -j MASQUERADE')

    def stop_nat(self):
        """Stop NAT on the AP.

        This allows consumers of the access point objects to disable NAT on the
        AP.

        Note that this is currently a global setting, since we don't have
        per-interface masquerade rules.
        """
        self.ssh.run('iptables -t nat -F')

    def create_bridge(self, bridge_name, interfaces):
        """Create the specified bridge and bridge the specified interfaces.

        Args:
            bridge_name: The name of the bridge to create.
            interfaces: A list of interfaces to add to the bridge.
        """

        # Create the bridge interface
        self.ssh.run(f'brctl addbr {bridge_name}')

        for interface in interfaces:
            self.ssh.run(f'brctl addif {bridge_name} {interface}')

        self.ssh.run(f'ip link set {bridge_name} up')

    def remove_bridge(self, bridge_name):
        """Removes the specified bridge

        Args:
            bridge_name: The name of the bridge to remove.
        """
        # Check if the bridge exists.
        #
        # Cases where it may not are if we failed to initialize properly
        #
        # Or if we're doing 2.4Ghz and 5Ghz SSIDs and we've already torn
        # down the bridge once, but we got called for each band.
        result = self.ssh.run(f'brctl show {bridge_name}', ignore_status=True)

        # If the bridge exists, we'll get an exit_status of 0, indicating
        # success, so we can continue and remove the bridge.
        if result.exit_status == 0:
            self.ssh.run(f'ip link set {bridge_name} down')
            self.ssh.run(f'brctl delbr {bridge_name}')

    def get_bssid_from_ssid(self, ssid, band):
        """Gets the BSSID from a provided SSID

        Args:
            ssid: An SSID string.
            band: 2G or 5G Wifi band.
        Returns: The BSSID if on the AP or None if SSID could not be found.
        """
        if band == hostapd_constants.BAND_2G:
            interfaces = [self.wlan_2g, ssid]
        else:
            interfaces = [self.wlan_5g, ssid]

        # Get the interface name associated with the given ssid.
        for interface in interfaces:
            iw_output = self.ssh.run(
                f"iw dev {interface} info|grep ssid|awk -F' ' '{{print $2}}'")
            if 'command failed: No such device' in iw_output.stderr:
                continue
            else:
                # If the configured ssid is equal to the given ssid, we found
                # the right interface.
                if iw_output.stdout == ssid:
                    iw_output = self.ssh.run(
                        f"iw dev {interface} info|grep addr|awk -F' ' '{{print $2}}'"
                    )
                    return iw_output.stdout
        return None

    def stop_ap(self, identifier):
        """Stops a running ap on this controller.

        Args:
            identifier: The identify of the ap that should be taken down.
        """

        if identifier not in list(self._aps.keys()):
            raise ValueError(f'Invalid identifier {identifier} given')

        instance = self._aps.get(identifier)

        if self._radvd:
            self._radvd.stop()
        try:
            self.stop_dhcp()
        except dhcp_server.NoInterfaceError:
            pass
        self.stop_nat()
        instance.hostapd.stop()
        self._ip_cmd.clear_ipv4_addresses(identifier)

        del self._aps[identifier]
        bridge_interfaces = self.interfaces.get_bridge_interface()
        if bridge_interfaces:
            for iface in bridge_interfaces:
                BRIDGE_DOWN = f'ip link set {iface} down'
                BRIDGE_DEL = f'brctl delbr {iface}'
                self.ssh.run(BRIDGE_DOWN)
                self.ssh.run(BRIDGE_DEL)

    def stop_all_aps(self):
        """Stops all running aps on this device."""

        for ap in list(self._aps.keys()):
            self.stop_ap(ap)

    def close(self):
        """Called to take down the entire access point.

        When called will stop all aps running on this host, shutdown the dhcp
        server, and stop the ssh connection.
        """

        if self._aps:
            self.stop_all_aps()
        self.ssh.close()

    def generate_bridge_configs(self, channel):
        """Generate a list of configs for a bridge between LAN and WLAN.

        Args:
            channel: the channel WLAN interface is brought up on
            iface_lan: the LAN interface to bridge
        Returns:
            configs: tuple containing iface_wlan, iface_lan and bridge_ip
        """

        if channel < 15:
            iface_wlan = self.wlan_2g
            subnet_str = self._AP_2G_SUBNET_STR
        else:
            iface_wlan = self.wlan_5g
            subnet_str = self._AP_5G_SUBNET_STR

        iface_lan = self.lan

        a, b, c, _ = subnet_str.strip('/24').split('.')
        bridge_ip = f'{a}.{b}.{c}.{BRIDGE_IP_LAST}'

        configs = (iface_wlan, iface_lan, bridge_ip)

        return configs

    def install_scapy(self, scapy_path, send_ra_path):
        """Install scapy

        Args:
            scapy_path: path where scapy tar file is located on server
            send_ra_path: path where sendra path is located on server
        """
        self.scapy_install_path = self.ssh.run('mktemp -d').stdout.rstrip()
        self.log.info(f'Scapy install path: {self.scapy_install_path}')
        self.ssh.send_file(scapy_path, self.scapy_install_path)
        self.ssh.send_file(send_ra_path, self.scapy_install_path)

        scapy = os.path.join(self.scapy_install_path,
                             scapy_path.split('/')[-1])

        self.ssh.run(f'tar -xvf {scapy} -C {self.scapy_install_path}')
        self.ssh.run(f'cd {self.scapy_install_path}; {SCAPY_INSTALL_COMMAND}')

    def cleanup_scapy(self):
        """ Cleanup scapy """
        if self.scapy_install_path:
            cmd = f'rm -rf {self.scapy_install_path}'
            self.log.info(f'Cleaning up scapy {cmd}')
            output = self.ssh.run(cmd)
            self.scapy_install_path = None

    def send_ra(self,
                iface,
                mac=RA_MULTICAST_ADDR,
                interval=1,
                count=None,
                lifetime=LIFETIME,
                rtt=0):
        """Invoke scapy and send RA to the device.

        Args:
          iface: string of the WiFi interface to use for sending packets.
          mac: string HWAddr/MAC address to send the packets to.
          interval: int Time to sleep between consecutive packets.
          count: int Number of packets to be sent.
          lifetime: int original RA's router lifetime in seconds.
          rtt: retrans timer of the RA packet
        """
        scapy_command = os.path.join(self.scapy_install_path, RA_SCRIPT)
        options = f' -m {mac} -i {interval} -c {count} -l {lifetime} -in {iface} -rtt {rtt}'
        cmd = scapy_command + options
        self.log.info(f'Scapy cmd: {cmd}')
        self.ssh.run(cmd)

    def get_icmp6intype134(self):
        """Read the value of Icmp6InType134 and return integer.

        Returns:
            Integer value >0 if grep is successful; 0 otherwise.
        """
        ra_count_str = self.ssh.run(
            f'grep Icmp6InType134 {PROC_NET_SNMP6} || true').stdout
        if ra_count_str:
            return int(ra_count_str.split()[1])

    def ping(self,
             dest_ip,
             count=3,
             interval=1000,
             timeout=1000,
             size=56,
             additional_ping_params=None):
        """Pings from AP to dest_ip, returns dict of ping stats (see utils.ping)
        """
        return utils.ping(self.ssh,
                          dest_ip,
                          count=count,
                          interval=interval,
                          timeout=timeout,
                          size=size,
                          additional_ping_params=additional_ping_params)

    def can_ping(self,
                 dest_ip,
                 count=1,
                 interval=1000,
                 timeout=1000,
                 size=56,
                 additional_ping_params=None):
        """Returns whether ap can ping dest_ip (see utils.can_ping)"""
        return utils.can_ping(self.ssh,
                              dest_ip,
                              count=count,
                              interval=interval,
                              timeout=timeout,
                              size=size,
                              additional_ping_params=additional_ping_params)

    def hard_power_cycle(self,
                         pdus,
                         unreachable_timeout=30,
                         ping_timeout=60,
                         ssh_timeout=30,
                         hostapd_configs=None):
        """Kills, then restores power to AccessPoint, verifying it goes down and
        comes back online cleanly.

        Args:
            pdus: list, PduDevices in the testbed
            unreachable_timeout: int, time to wait for AccessPoint to become
                unreachable
            ping_timeout: int, time to wait for AccessPoint to responsd to pings
            ssh_timeout: int, time to wait for AccessPoint to allow SSH
            hostapd_configs (optional): list, containing hostapd settings. If
                present, these networks will be spun up after the AP has
                rebooted. This list can either contain HostapdConfig objects, or
                    dictionaries with the start_ap params
                    (i.e  { 'hostapd_config': <HostapdConfig>,
                            'setup_bridge': <bool>,
                            'additional_parameters': <dict> } ).
        Raise:
            Error, if no PduDevice is provided in AccessPoint config.
            ConnectionError, if AccessPoint fails to go offline or come back.
        """
        if not self.device_pdu_config:
            raise Error('No PduDevice provided in AccessPoint config.')

        if hostapd_configs is None:
            hostapd_configs = []

        self.log.info(f'Power cycling')
        ap_pdu, ap_pdu_port = pdu.get_pdu_port_for_device(
            self.device_pdu_config, pdus)

        self.log.info(f'Killing power')
        ap_pdu.off(str(ap_pdu_port))

        self.log.info('Verifying AccessPoint is unreachable.')
        timeout = time.time() + unreachable_timeout
        while time.time() < timeout:
            if not utils.can_ping(job, self.ssh_settings.hostname):
                self.log.info('AccessPoint is unreachable as expected.')
                break
            else:
                self.log.debug(
                    'AccessPoint is still responding to pings. Retrying in 1 '
                    'second.')
                time.sleep(1)
        else:
            raise ConnectionError(
                f'Failed to bring down AccessPoint ({self.ssh_settings.hostname})'
            )
        self._aps.clear()

        self.log.info(f'Restoring power')
        ap_pdu.on(str(ap_pdu_port))

        self.log.info('Waiting for AccessPoint to respond to pings.')
        timeout = time.time() + ping_timeout
        while time.time() < timeout:
            if utils.can_ping(job, self.ssh_settings.hostname):
                self.log.info('AccessPoint responded to pings.')
                break
            else:
                self.log.debug('AccessPoint is not responding to pings. '
                               'Retrying in 1 second.')
                time.sleep(1)
        else:
            raise ConnectionError(
                f'Timed out waiting for AccessPoint ({self.ssh_settings.hostname}) '
                'to respond to pings.')

        self.log.info('Waiting for AccessPoint to allow ssh connection.')
        timeout = time.time() + ssh_timeout
        while time.time() < timeout:
            try:
                self.ssh.run('echo')
            except connection.Error:
                self.log.debug('AccessPoint is not allowing ssh connection. '
                               'Retrying in 1 second.')
                time.sleep(1)
            else:
                self.log.info('AccessPoint available via ssh.')
                break
        else:
            raise ConnectionError(
                f'Timed out waiting for AccessPoint ({self.ssh_settings.hostname}) '
                'to allow ssh connection.')

        # Allow 5 seconds for OS to finish getting set up
        time.sleep(5)
        self._initial_ap()
        self.log.info('Power cycled successfully')

        for settings in hostapd_configs:
            if type(settings) == hostapd_config.HostapdConfig:
                config = settings
                setup_bridge = False
                additional_parameters = None

            elif type(settings) == dict:
                config = settings['hostapd_config']
                setup_bridge = settings.get('setup_bridge', False)
                additional_parameters = settings.get('additional_parameters',
                                                     None)
            else:
                raise TypeError(
                    'Items in hostapd_configs list must either be '
                    'hostapd.HostapdConfig objects or dictionaries.')

            self.log.info(f'Restarting network {config.ssid}')
            self.start_ap(config,
                          setup_bridge=setup_bridge,
                          additional_parameters=additional_parameters)

    def channel_switch(self, identifier, channel_num):
        """Switch to a different channel on the given AP."""
        if identifier not in list(self._aps.keys()):
            raise ValueError(f'Invalid identifier {identifier} given')
        instance = self._aps.get(identifier)
        self.log.info(f'channel switch to channel {channel_num}')
        instance.hostapd.channel_switch(channel_num)

    def get_current_channel(self, identifier):
        """Find the current channel on the given AP."""
        if identifier not in list(self._aps.keys()):
            raise ValueError(f'Invalid identifier {identifier} given')
        instance = self._aps.get(identifier)
        return instance.hostapd.get_current_channel()

    def get_stas(self, identifier) -> Set[str]:
        """Return MAC addresses of all associated STAs on the given AP."""
        if identifier not in list(self._aps.keys()):
            raise ValueError(f'Invalid identifier {identifier} given')
        instance = self._aps.get(identifier)
        return instance.hostapd.get_stas()

    def get_sta_extended_capabilities(self, identifier,
                                      sta_mac: str) -> ExtendedCapabilities:
        """Get extended capabilities for the given STA, as seen by the AP."""
        if identifier not in list(self._aps.keys()):
            raise ValueError(f'Invalid identifier {identifier} given')
        instance = self._aps.get(identifier)
        return instance.hostapd.get_sta_extended_capabilities(sta_mac)

    def send_bss_transition_management_req(
            self, identifier, sta_mac: str,
            request: BssTransitionManagementRequest):
        """Send a BSS Transition Management request to an associated STA."""
        if identifier not in list(self._aps.keys()):
            raise ValueError('Invalid identifier {identifier} given')
        instance = self._aps.get(identifier)
        return instance.hostapd.send_bss_transition_management_req(
            sta_mac, request)
