# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros.network import ping_runner
from autotest_lib.server.cros.network import ip_config_context_manager
from autotest_lib.server.cros.network import wifi_cell_test_base


class WiFiCellPerfTestBase(wifi_cell_test_base.WiFiCellTestBase):
    """An abstract base class for autotests in WiFi performance cells.

    Similar to WiFiCellTestBase with one major exception:

    The pcap device is also used as an endpoint in performance tests, so the
    router and pcap device must have a direct Ethernet connection over their LAN
    ports in a WiFiCellPerfTestBase.
    """

    def configure_and_connect_to_ap(self, ap_config):
        """Configure the router as an AP with the given config and connect
        the DUT to it.

        @param ap_config HostapConfig object.

        @return name of the configured AP
        """
        # self.context.configure has a similar check - but that one only
        # errors out if the AP *requires* VHT i.e. AP is requesting
        # MODE_11AC_PURE and the client does not support it.
        # For performance tests we don't want to run MODE_11AC_MIXED on the AP if
        # the client does not support VHT, as we are guaranteed to get the
        # same results at 802.11n/HT40 in that case.
        if ap_config.is_11ac and not self.context.client.is_vht_supported():
            raise error.TestNAError('Client does not have AC support')
        return super(WiFiCellPerfTestBase,
                     self).configure_and_connect_to_ap(ap_config)

    def _verify_additional_setup_requirements(self):
        """Ensure that the router and pcap device in the cell have a direct
        connection available over their respective LAN ports. Raises a test NA
        error if this connection cannot be verified.
        """
        router_lan_ip_addr = "192.168.1.50"
        pcap_lan_ip_addr = "192.168.1.51"
        router_lan_iface_name = "eth1"
        pcap_lan_iface_name = "eth1"

        with ip_config_context_manager.IpConfigContextManager() as ip_context:
            try:
                ip_context.bring_interface_up(self.context.router.host,
                                              router_lan_iface_name)
                ip_context.bring_interface_up(self.context.pcap_host.host,
                                              pcap_lan_iface_name)
                ip_context.assign_ip_addr_to_iface(self.context.router.host,
                                                   router_lan_ip_addr,
                                                   router_lan_iface_name)
                ip_context.assign_ip_addr_to_iface(self.context.pcap_host.host,
                                                   pcap_lan_ip_addr,
                                                   pcap_lan_iface_name)
                ping_config = ping_runner.PingConfig(
                        pcap_lan_ip_addr,
                        count=5,
                        source_iface=router_lan_iface_name,
                        ignore_result=True)
                ping_result = self.context.router.ping(ping_config)
                if ping_result.received == 0:
                    raise Exception("Ping failed (%s)" % (ping_result))
            except Exception as e:
                raise error.TestNAError(
                        'Could not verify connection between router and pcap '
                        'devices. Router and pcap device must have a direct '
                        'Ethernet connection over their LAN ports to run '
                        'performance tests: %s' % (e))
