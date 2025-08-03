# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
VirtualEthernetPair provides methods for setting up and tearing down a virtual
ethernet interface for use in tests.  You will probably need to be root on test
devices to use this class.  The constructor allows you to specify your IP's to
assign to both ends of the pair, however, if you wish to leave the interface
unconfigured, simply pass None.  You may also specify the subnet of your ip
addresses.  Failing to do so leaves them with default in ifconfig.

Example usage:
vif = virtual_ethernet_pair.VirtualEthernetPair(interface_name="main",
                                                peer_interface_name="peer",
                                                interface_ip="10.9.8.1/24",
                                                peer_interface_ip=None)
vif.setup()
if not vif.is_healthy:
    # bad things happened while creating the interface
    # ... abort gracefully

interface_name = vif.interface_name
peer_interface_name = vif.peer_interface_name
#... do things with your interface

# You must call this if you want to leave the system in a good state.
vif.teardown()

Alternatively:

with virtual_ethernet_pair.VirtualEthernetPair(...) as vif:
    if not vif.is_healthy:
        # bad things happened while creating the interface
        # ... abort gracefully

    interface_name = vif.interface_name
    peer_interface_name = vif.peer_interface_name
    #... do things with your interface

"""

import logging
import re

from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros.network import interface

class VirtualEthernetPair(object):
    """ Class for configuring virtual ethernet device pair. """

    def __init__(self,
                 interface_name='veth_main',
                 peer_interface_name='veth_secondary',
                 interface_ip='10.9.8.1/24',
                 peer_interface_ip='10.9.8.2/24',
                 interface_ipv6=None,
                 peer_interface_ipv6=None,
                 interface_ns=None,
                 ignore_shutdown_errors=False,
                 host=None):
        """
        Construct a object managing a virtual ethernet pair.  One end of the
        interface will be called |interface_name|, and the peer end
        |peer_interface_name|.  You may get the interface names later with
        VirtualEthernetPair.get_[peer_]interface_name().  The ends of the
        interface are manually configured with the given IPv4 address strings
        (like "10.9.8.2/24").  You may skip the IP configuration by passing None
        as the address for either interface.
        """
        super(VirtualEthernetPair, self).__init__()
        self._is_healthy = True
        self._interface_name = interface_name
        self._peer_interface_name = peer_interface_name
        self._interface_ip = interface_ip
        self._peer_interface_ip = peer_interface_ip
        self._interface_ipv6 = interface_ipv6
        self._peer_interface_ipv6 = peer_interface_ipv6
        self._interface_ns = interface_ns
        self._ns_exec = ''
        if interface_ns:
            self._ns_exec = 'ip netns exec %s ' % self._interface_ns
        self._ignore_shutdown_errors = ignore_shutdown_errors
        self._run = utils.run
        self._host = host
        if host is not None:
            self._run = host.run
        (self._eth_name, self._eth_ip) = self._get_ipv4_config()

    def _get_ipv4_config(self):
        """@return Tuple with interface name and IP address used for
        external communication."""
        route = utils.system_output("ip route get 8.8.8.8")
        # Only first line is interesting - match it for interface and
        # IP address
        m = re.search(r"dev (\S+) .*? src ((?:\d+\.){3}\d+)",
                      route[:route.find('\n')])
        return (m.group(1), m.group(2)) if m else (None, None)

    def setup(self):
        """
        Installs a virtual ethernet interface and configures one side with an IP
        address.  First does some confidence checking and tries to remove an
        existing interface by the same name, and logs messages on failures.
        """
        self._is_healthy = False
        if self._either_interface_exists():
            logging.warning('At least one test interface already existed.'
                            '  Attempting to remove.')
            self._remove_test_interface()
            if self._either_interface_exists():
                logging.error('Failed to remove unexpected test '
                              'interface.  Aborting.')
                return

        self._create_test_interface()
        if not self._interface_exists(self._interface_name,
                                      self._interface_ns):
            logging.error('Failed to create main test interface.')
            return

        if not self._interface_exists(self._peer_interface_name):
            logging.error('Failed to create peer test interface.')
            return
        # Unless you tell the firewall about the interface, you're not going to
        # get any IP traffic through.  Since this is basically a loopback
        # device, just allow all traffic.
        for name in (self._interface_name, self._peer_interface_name):
            command = 'iptables -w -I INPUT -i %s -j ACCEPT' % name
            if name == self._interface_name and self._interface_ns:
                status = self._run(self._ns_exec + command, ignore_status=True)
            else:
                status = self._run(command, ignore_status=True)
            if status.exit_status != 0:
                logging.error('iptables rule addition failed for interface %s: '
                              '%s', name, status.stderr)
        # In addition to INPUT configure also FORWARD'ing for the case
        # of interface being moved to its own namespace so that there is
        # contact with "the world" from within that namespace.
        if self._interface_ns and self._eth_ip:
            command = 'iptables -w -I FORWARD -i %s -j ACCEPT' \
                      % self._peer_interface_name
            status = self._run(command, ignore_status=True)
            if status.exit_status != 0:
                logging.warning(
                        'failed to configure forwarding rule for %s: '
                        '%s', self._peer_interface_name, status.stderr)
            command = 'iptables -w -t nat -I POSTROUTING ' \
                      '--src %s -o %s -j MASQUERADE' % \
                      (self._interface_ip, self._eth_name)
            status = self._run(command, ignore_status=True)
            if status.exit_status != 0:
                logging.warning('failed to configure nat rule for %s: '
                                '%s', self._peer_interface_name, status.stderr)
            # Add default route in namespace to the address used for
            # outbound traffic
            commands = [
                    'ip r add %s dev %s', 'ip route add default via %s dev %s'
            ]
            for command in commands:
                command = command % (self._eth_ip, self._interface_name)
                status = self._run(self._ns_exec + command, ignore_status=True)
                if status.exit_status != 0:
                    logging.warning(
                            'failed to configure GW route for %s: '
                            '%s', self._interface_name, status.stderr)
        self._is_healthy = True


    def teardown(self):
        """
        Removes the interface installed by VirtualEthernetPair.setup(), with
        some simple confidence checks that print warnings when either the
        interface isn't there or fails to be removed.
        """
        for name in (self._interface_name, self._peer_interface_name):
            command = 'iptables -w -D INPUT -i %s -j ACCEPT' % name
            if name == self._interface_name and self._interface_ns:
                self._run(self._ns_exec + command, ignore_status=True)
            else:
                self._run(command, ignore_status=True)
        if self._interface_ns and self._eth_ip:
            self._run('iptables -w -D FORWARD -i %s -j ACCEPT' %
                      self._peer_interface_name,
                      ignore_status=True)
            command = 'iptables -w -t nat -I POSTROUTING ' \
                      '--src %s -o %s -j MASQUERADE' % \
                      (self._interface_ip, self._eth_name)
            self._run(command, ignore_status=True)
        if not self._either_interface_exists():
            logging.warning('VirtualEthernetPair.teardown() called, '
                            'but no interface was found.')
            return

        self._remove_test_interface()
        if self._either_interface_exists():
            logging.error('Failed to destroy test interface.')


    @property
    def is_healthy(self):
        """@return True if virtual ethernet pair is configured."""
        return self._is_healthy


    @property
    def interface_name(self):
        """@return string name of the interface."""
        return self._interface_name


    @property
    def peer_interface_name(self):
        """@return string name of the peer interface."""
        return self._peer_interface_name


    @property
    def interface_ip(self):
        """@return string IPv4 address of the interface."""
        return interface.Interface(self.interface_name,
                                   netns=self._interface_ns).ipv4_address


    @property
    def peer_interface_ip(self):
        """@return string IPv4 address of the peer interface."""
        return interface.Interface(self.peer_interface_name).ipv4_address


    @property
    def interface_subnet_mask(self):
        """@return string IPv4 subnet mask of the interface."""
        return interface.Interface(self.interface_name,
                                   netns=self._interface_ns).ipv4_subnet_mask


    @property
    def interface_prefix(self):
        """@return int IPv4 prefix length."""
        return interface.Interface(self.interface_name,
                                   netns=self._interface_ns).ipv4_prefix


    @property
    def peer_interface_subnet_mask(self):
        """@return string IPv4 subnet mask of the peer interface."""
        return interface.Interface(self.peer_interface_name).ipv4_subnet_mask


    @property
    def interface_mac(self):
        """@return string MAC address of the interface."""
        return interface.Interface(self.interface_name,
                                   netns=self._interface_ns).mac_address


    @property
    def peer_interface_mac(self):
        """@return string MAC address of the peer interface."""
        return interface.Interface(self._peer_interface_name).mac_address

    @property
    def interface_namespace(self):
        """@return interface name space if configured, None otherwise."""
        return self._interface_ns

    def __enter__(self):
        self.setup()
        return self


    def __exit__(self, exc_type, exc_value, traceback):
        self.teardown()


    def _interface_exists(self, interface_name, netns=None):
        """
        Returns True iff we found an interface with name |interface_name|.
        """
        return interface.Interface(interface_name,
                                   host=self._host,
                                   netns=netns).exists


    def _either_interface_exists(self):
        return (self._interface_exists(self._interface_name,
                                       self._interface_ns)
                or self._interface_exists(self._peer_interface_name))


    def _remove_test_interface(self):
        """
        Remove the virtual ethernet device installed by
        _create_test_interface().
        """
        self._run(self._ns_exec + 'ip link set %s down' % self._interface_name,
                  ignore_status=self._ignore_shutdown_errors)
        self._run('ip link set %s down' % self._peer_interface_name,
                  ignore_status=self._ignore_shutdown_errors)
        self._run(self._ns_exec +
                  'ip link delete %s >/dev/null 2>&1' % self._interface_name,
                  ignore_status=self._ignore_shutdown_errors)

        # Under most normal circumstances a successful deletion of
        # |_interface_name| should also remove |_peer_interface_name|,
        # but if we elected to ignore failures above, that may not be
        # the case.
        self._run('ip link delete %s >/dev/null 2>&1' %
                  self._peer_interface_name, ignore_status=True)

        if self._interface_ns:
            self._run('ip netns del %s' % self._interface_ns,
                      ignore_status=True)

    def _create_test_interface(self):
        """
        Set up a virtual ethernet device and configure the host side with a
        fake IP address.
        """
        self._run('ip link add name %s '
                  'type veth peer name %s >/dev/null 2>&1' %
                  (self._interface_name, self._peer_interface_name))
        if self._interface_ns:
            self._run('ip netns add %s' % self._interface_ns,
                      ignore_status=True)
            self._run('ip link set dev %s netns %s' %
                      (self._interface_name, self._interface_ns))
        self._run(self._ns_exec + 'ip link set %s up' % self._interface_name)
        self._run('ip link set %s up' % self._peer_interface_name)
        if self._interface_ip is not None:
            self._run(self._ns_exec + 'ip addr add %s dev %s' %
                      (self._interface_ip, self._interface_name))
        if self._peer_interface_ip is not None:
            self._run('ip addr add %s dev %s' % (self._peer_interface_ip,
                                                 self._peer_interface_name))
        if self._interface_ipv6 is not None:
            self._run(self._ns_exec + 'ip -6 addr add %s dev %s' %
                      (self._interface_ipv6, self._interface_name))
        if self._peer_interface_ipv6 is not None:
            self._run('ip -6 addr add %s dev %s' % (self._peer_interface_ipv6,
                                                    self._peer_interface_name))
