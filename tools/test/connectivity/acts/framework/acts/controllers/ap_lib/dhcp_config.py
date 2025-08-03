#   Copyright 2016 - The Android Open Source Project
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

import copy

_ROUTER_DNS = '8.8.8.8, 4.4.4.4'


class Subnet(object):
    """Configs for a subnet  on the dhcp server.

    Attributes:
        network: ipaddress.IPv4Network, the network that this subnet is in.
        start: ipaddress.IPv4Address, the start ip address.
        end: ipaddress.IPv4Address, the end ip address.
        router: The router to give to all hosts in this subnet.
        lease_time: The lease time of all hosts in this subnet.
        additional_parameters: A dictionary corresponding to DHCP parameters.
        additional_options: A dictionary corresponding to DHCP options.
    """

    def __init__(self,
                 subnet,
                 start=None,
                 end=None,
                 router=None,
                 lease_time=None,
                 additional_parameters={},
                 additional_options={}):
        """
        Args:
            subnet: ipaddress.IPv4Network, The address space of the subnetwork
                    served by the DHCP server.
            start: ipaddress.IPv4Address, The start of the address range to
                   give hosts in this subnet. If not given, the second ip in
                   the network is used, under the assumption that the first
                   address is the router.
            end: ipaddress.IPv4Address, The end of the address range to give
                 hosts. If not given then the address prior to the broadcast
                 address (i.e. the second to last ip in the network) is used.
            router: ipaddress.IPv4Address, The router hosts should use in this
                    subnet. If not given the first ip in the network is used.
            lease_time: int, The amount of lease time in seconds
                        hosts in this subnet have.
            additional_parameters: A dictionary corresponding to DHCP parameters.
            additional_options: A dictionary corresponding to DHCP options.
        """
        self.network = subnet

        if start:
            self.start = start
        else:
            self.start = self.network[2]

        if not self.start in self.network:
            raise ValueError('The start range is not in the subnet.')
        if self.start.is_reserved:
            raise ValueError('The start of the range cannot be reserved.')

        if end:
            self.end = end
        else:
            self.end = self.network[-2]

        if not self.end in self.network:
            raise ValueError('The end range is not in the subnet.')
        if self.end.is_reserved:
            raise ValueError('The end of the range cannot be reserved.')
        if self.end < self.start:
            raise ValueError(
                'The end must be an address larger than the start.')

        if router:
            if router >= self.start and router <= self.end:
                raise ValueError('Router must not be in pool range.')
            if not router in self.network:
                raise ValueError('Router must be in the given subnet.')

            self.router = router
        else:
            # TODO: Use some more clever logic so that we don't have to search
            # every host potentially.
            # This is especially important if we support IPv6 networks in this
            # configuration. The improved logic that we can use is:
            #    a) erroring out if start and end encompass the whole network, and
            #    b) picking any address before self.start or after self.end.
            self.router = None
            for host in self.network.hosts():
                if host < self.start or host > self.end:
                    self.router = host
                    break

            if not self.router:
                raise ValueError('No useable host found.')

        self.lease_time = lease_time
        self.additional_parameters = additional_parameters
        self.additional_options = additional_options
        if 'domain-name-servers' not in self.additional_options:
            self.additional_options['domain-name-servers'] = _ROUTER_DNS


class StaticMapping(object):
    """Represents a static dhcp host.

    Attributes:
        identifier: How id of the host (usually the mac addres
                    e.g. 00:11:22:33:44:55).
        address: ipaddress.IPv4Address, The ipv4 address to give the host.
        lease_time: How long to give a lease to this host.
    """

    def __init__(self, identifier, address, lease_time=None):
        self.identifier = identifier
        self.ipv4_address = address
        self.lease_time = lease_time


class DhcpConfig(object):
    """The configs for a dhcp server.

    Attributes:
        subnets: A list of all subnets for the dhcp server to create.
        static_mappings: A list of static host addresses.
        default_lease_time: The default time for a lease.
        max_lease_time: The max time to allow a lease.
    """

    def __init__(self,
                 subnets=None,
                 static_mappings=None,
                 default_lease_time=600,
                 max_lease_time=7200):
        self.subnets = copy.deepcopy(subnets) if subnets else []
        self.static_mappings = (copy.deepcopy(static_mappings)
                                if static_mappings else [])
        self.default_lease_time = default_lease_time
        self.max_lease_time = max_lease_time

    def render_config_file(self):
        """Renders the config parameters into a format compatible with
        the ISC DHCP server (dhcpd).
        """
        lines = []

        if self.default_lease_time:
            lines.append('default-lease-time %d;' % self.default_lease_time)
        if self.max_lease_time:
            lines.append('max-lease-time %s;' % self.max_lease_time)

        for subnet in self.subnets:
            address = subnet.network.network_address
            mask = subnet.network.netmask
            router = subnet.router
            start = subnet.start
            end = subnet.end
            lease_time = subnet.lease_time
            additional_parameters = subnet.additional_parameters
            additional_options = subnet.additional_options

            lines.append('subnet %s netmask %s {' % (address, mask))
            lines.append('\tpool {')
            lines.append('\t\toption subnet-mask %s;' % mask)
            lines.append('\t\toption routers %s;' % router)
            lines.append('\t\trange %s %s;' % (start, end))
            if lease_time:
                lines.append('\t\tdefault-lease-time %d;' % lease_time)
                lines.append('\t\tmax-lease-time %d;' % lease_time)
            for param, value in additional_parameters.items():
                lines.append('\t\t%s %s;' % (param, value))
            for option, value in additional_options.items():
                lines.append('\t\toption %s %s;' % (option, value))
            lines.append('\t}')
            lines.append('}')

        for mapping in self.static_mappings:
            identifier = mapping.identifier
            fixed_address = mapping.ipv4_address
            host_fake_name = 'host%s' % identifier.replace(':', '')
            lease_time = mapping.lease_time

            lines.append('host %s {' % host_fake_name)
            lines.append('\thardware ethernet %s;' % identifier)
            lines.append('\tfixed-address %s;' % fixed_address)
            if lease_time:
                lines.append('\tdefault-lease-time %d;' % lease_time)
                lines.append('\tmax-lease-time %d;' % lease_time)
            lines.append('}')

        config_str = '\n'.join(lines)

        return config_str
