# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging


class IpConfigContextManager(object):
    """Allows changes to IP configs on multiple host test devices which are
    guaranteed to be reverted when the context is exited.
    """

    def bring_interface_up(self, host, dev_name):
        """Bring a device interface up on the host. This interface will
        automatically be brought down when the context is exited.

        @param host Host Device to bring the interface up on.
        @param dev_name String name of the device to bring up.

        """
        clear_command = 'sudo ip link set %s down' % dev_name
        if host in self._iface_cleanup_dict:
            self._iface_cleanup_dict[host].append(clear_command)
        else:
            self._iface_cleanup_dict[host] = [clear_command]
        host.run('sudo ip link set %s up' % dev_name)

    def add_ip_route(self, host, dest_ip, iface_name, via_ip=None):
        """Add an ip route to the device. This route will be deleted when the
        context is exited.

        @param host Host Device to assign the ip route on.
        @param dest_ip String destination ip address of the ip route.
        @param iface_name String The local iface to route the traffic from.
        @param via_ip String an optional ip address to route the traffic through.

        """
        via = "via %s " % via_ip if via_ip else ""
        clear_command = 'sudo ip route del table 255 %s %sdev %s' % (
                dest_ip, via, iface_name)
        if host in self._ip_route_cleanup_dict:
            self._ip_route_cleanup_dict[host].append(clear_command)
        else:
            self._ip_route_cleanup_dict[host] = [clear_command]
        host.run('sudo ip route replace table 255 %s %sdev %s' %
                 (dest_ip, via, iface_name))

    def assign_ip_addr_to_iface(self, host, ip_addr, iface_name):
        """Assign an ip address to an interface on the host. This address will be
        deleted when the context is exited.

        @param host Host Device to assign the ip address on.
        @param ip_addr String ip address to assign.
        @param iface_name String The interface to assign the ip address to.

        """
        clear_command = 'sudo ip addr del %s/24 dev %s' % (ip_addr, iface_name)
        if host in self._ip_addr_cleanup_dict:
            self._ip_addr_cleanup_dict[host].append(clear_command)
        else:
            self._ip_addr_cleanup_dict[host] = [clear_command]
        host.run('sudo ip addr replace %s/24 dev %s' % (ip_addr, iface_name))

    def __init__(self):
        """Construct an IpConfigContextManager. This class uses dictionaries to
        store the cleanup commands that must be run on various hosts when the
        context is exited.
        """
        self._iface_cleanup_dict = dict()
        self._ip_route_cleanup_dict = dict()
        self._ip_addr_cleanup_dict = dict()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        logging.info('Cleaning up ip configs from test devices.')
        for host in self._ip_route_cleanup_dict:
            for command in self._ip_route_cleanup_dict[host]:
                host.run(command)

        for host in self._ip_addr_cleanup_dict:
            for command in self._ip_addr_cleanup_dict[host]:
                host.run(command)

        for host in self._iface_cleanup_dict:
            for command in self._iface_cleanup_dict[host]:
                host.run(command)
