#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import ipaddress
import unittest

from acts.controllers.ap_lib.dhcp_config import DhcpConfig, Subnet, StaticMapping


class DhcpConfigTest(unittest.TestCase):
    def setUp(self):
        super().setUp()
        # These config files may have long diffs, modify this setting to
        # ensure they're printed.
        self.maxDiff = None

    def test_basic_dhcp_config(self):
        dhcp_conf = DhcpConfig()

        expected_config = ('default-lease-time 600;\n' 'max-lease-time 7200;')

        self.assertEqual(expected_config, dhcp_conf.render_config_file())

    def test_dhcp_config_with_lease_times(self):
        default_lease_time = 350
        max_lease_time = 5000
        dhcp_conf = DhcpConfig(default_lease_time=default_lease_time,
                               max_lease_time=max_lease_time)

        expected_config = (f'default-lease-time {default_lease_time};\n'
                           f'max-lease-time {max_lease_time};')

        self.assertEqual(expected_config, dhcp_conf.render_config_file())

    def test_dhcp_config_with_subnets(self):
        default_lease_time = 150
        max_lease_time = 3000
        subnets = [
            # addresses from 10.10.1.0 - 10.10.1.255
            Subnet(ipaddress.ip_network('10.10.1.0/24')),
            # 4 addresses from 10.10.3.0 - 10.10.3.3
            Subnet(ipaddress.ip_network('10.10.3.0/30')),
            # 6 addresses from 10.10.5.20 - 10.10.5.25
            Subnet(ipaddress.ip_network('10.10.5.0/24'),
                   start=ipaddress.ip_address('10.10.5.20'),
                   end=ipaddress.ip_address('10.10.5.25'),
                   router=ipaddress.ip_address('10.10.5.255'),
                   lease_time=60)
        ]
        dhcp_conf = DhcpConfig(subnets=subnets,
                               default_lease_time=default_lease_time,
                               max_lease_time=max_lease_time)

        # Unless an explicit start/end address is provided, the second
        # address in the range is used for "start", and the second to
        # last address is used for "end".
        expected_config = (f'default-lease-time {default_lease_time};\n'
                           f'max-lease-time {max_lease_time};\n'
                           'subnet 10.10.1.0 netmask 255.255.255.0 {\n'
                           '\tpool {\n'
                           '\t\toption subnet-mask 255.255.255.0;\n'
                           '\t\toption routers 10.10.1.1;\n'
                           '\t\trange 10.10.1.2 10.10.1.254;\n'
                           '\t\toption domain-name-servers 8.8.8.8, 4.4.4.4;\n'
                           '\t}\n'
                           '}\n'
                           'subnet 10.10.3.0 netmask 255.255.255.252 {\n'
                           '\tpool {\n'
                           '\t\toption subnet-mask 255.255.255.252;\n'
                           '\t\toption routers 10.10.3.1;\n'
                           '\t\trange 10.10.3.2 10.10.3.2;\n'
                           '\t\toption domain-name-servers 8.8.8.8, 4.4.4.4;\n'
                           '\t}\n'
                           '}\n'
                           'subnet 10.10.5.0 netmask 255.255.255.0 {\n'
                           '\tpool {\n'
                           '\t\toption subnet-mask 255.255.255.0;\n'
                           '\t\toption routers 10.10.5.255;\n'
                           '\t\trange 10.10.5.20 10.10.5.25;\n'
                           '\t\tdefault-lease-time 60;\n'
                           '\t\tmax-lease-time 60;\n'
                           '\t\toption domain-name-servers 8.8.8.8, 4.4.4.4;\n'
                           '\t}\n'
                           '}')

        self.assertEqual(expected_config, dhcp_conf.render_config_file())

    def test_additional_subnet_parameters_and_options(self):
        default_lease_time = 150
        max_lease_time = 3000
        subnets = [
            Subnet(ipaddress.ip_network('10.10.1.0/24'),
                   additional_parameters={
                       'allow': 'unknown-clients',
                       'foo': 'bar'
                   },
                   additional_options={'my-option': 'some-value'}),
        ]
        dhcp_conf = DhcpConfig(subnets=subnets,
                               default_lease_time=default_lease_time,
                               max_lease_time=max_lease_time)

        # Unless an explicit start/end address is provided, the second
        # address in the range is used for "start", and the second to
        # last address is used for "end".
        expected_config = (f'default-lease-time {default_lease_time};\n'
                           f'max-lease-time {max_lease_time};\n'
                           'subnet 10.10.1.0 netmask 255.255.255.0 {\n'
                           '\tpool {\n'
                           '\t\toption subnet-mask 255.255.255.0;\n'
                           '\t\toption routers 10.10.1.1;\n'
                           '\t\trange 10.10.1.2 10.10.1.254;\n'
                           '\t\tallow unknown-clients;\n'
                           '\t\tfoo bar;\n'
                           '\t\toption my-option some-value;\n'
                           '\t\toption domain-name-servers 8.8.8.8, 4.4.4.4;\n'
                           '\t}\n'
                           '}')

        self.assertEqual(expected_config, dhcp_conf.render_config_file())


if __name__ == '__main__':
    unittest.main()
