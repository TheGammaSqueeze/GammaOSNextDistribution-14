# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros.network import interface
from autotest_lib.server.cros.network import expected_performance_results
from autotest_lib.server.cros.network import ip_config_context_manager
from autotest_lib.server.cros.network import perf_test_manager as perf_manager
from autotest_lib.server.cros.network import wifi_cell_perf_test_base


class network_WiFi_Perf(wifi_cell_perf_test_base.WiFiCellPerfTestBase):
    """Test maximal achievable bandwidth on several channels per band.

    Conducts a performance test for a set of specified router configurations
    and reports results as keyval pairs.

    """

    version = 1

    PERF_TEST_TYPES = [
            perf_manager.PerfTestTypes.TEST_TYPE_TCP_TX,
            perf_manager.PerfTestTypes.TEST_TYPE_TCP_RX,
            perf_manager.PerfTestTypes.TEST_TYPE_TCP_BIDIRECTIONAL,
            perf_manager.PerfTestTypes.TEST_TYPE_UDP_TX,
            perf_manager.PerfTestTypes.TEST_TYPE_UDP_RX,
            perf_manager.PerfTestTypes.TEST_TYPE_UDP_BIDIRECTIONAL,
    ]

    DEFAULT_ROUTER_LAN_IP_ADDRESS = "192.168.1.50"
    DEFAULT_PCAP_LAN_IP_ADDRESS = "192.168.1.51"
    DEFAULT_ROUTER_LAN_IFACE_NAME = "eth1"
    DEFAULT_PCAP_LAN_IFACE_NAME = "eth1"

    def parse_additional_arguments(self, commandline_args, additional_params):
        """Hook into super class to take control files parameters.

        @param commandline_args dict of parsed parameters from the autotest.
        @param additional_params list of HostapConfig objects.
        """
        self._should_required = 'should' in commandline_args
        self._power_save_off = 'power_save_off' in commandline_args

        get_arg_value_or_default = lambda attr, default: commandline_args[
                attr] if attr in commandline_args else default
        self._router_lan_ip_addr = get_arg_value_or_default(
                'router_lan_ip_addr', self.DEFAULT_ROUTER_LAN_IP_ADDRESS)
        self._router_lan_iface_name = get_arg_value_or_default(
                'router_lan_iface_name', self.DEFAULT_ROUTER_LAN_IFACE_NAME)
        self._pcap_lan_ip_addr = get_arg_value_or_default(
                'pcap_lan_ip_addr', self.DEFAULT_PCAP_LAN_IP_ADDRESS)
        self._pcap_lan_iface_name = get_arg_value_or_default(
                'pcap_lan_iface_name', self.DEFAULT_PCAP_LAN_IFACE_NAME)

        if 'governor' in commandline_args:
            self._governor = commandline_args['governor']
            # validate governor string. Not all machines will support all of
            # these governors, but this at least ensures that a potentially
            # valid governor was passed in
            if self._governor not in ('performance', 'powersave', 'userspace',
                                      'ondemand', 'conservative', 'schedutil'):
                logging.warning(
                        'Unrecognized CPU governor %s. Running test '
                        'without setting CPU governor...', self._governor)
                self._governor = None
        else:
            self._governor = None
        self._ap_configs, self._use_iperf = additional_params

    def verify_result(self, result, must_expected_throughput,
                      should_expected_throughput, test_type, failed_test_types,
                      power_save, ap_config):
        """Verfiy that performance test result passes the must and should
        throughput requirements.

        @param result: the throughput result object
        @param must_expected_throughput: the min must expected throughput
        @param should_expected_throughput: the min should expected throughput
        @param test_type: the performance_test_types test type
        @param failed_test_types: a set of failed test_types
        @param power_save: powersaving configuration
        @param ap_config: the AP configuration
        """
        must_tput_failed = False
        should_tput_failed = False

        # If the must requirement is greater than our maximum expecation for a
        # board, use the maximum expectation instead of the must requirement.
        board_max_expectation = expected_performance_results.get_board_max_expectation(
                test_type, self.context.client.board)
        if board_max_expectation and board_max_expectation < must_expected_throughput:
            must_expected_throughput = board_max_expectation

        if result.throughput < must_expected_throughput:
            logging.error(
                    'Throughput is too low for %s. Expected (must) %0.2f Mbps, got %0.2f.',
                    test_type, must_expected_throughput, result.throughput)
            must_tput_failed = True
        if result.throughput < should_expected_throughput:
            if self._should_required:
                logging.error(
                        'Throughput is too low for %s. Expected (should) %0.2f Mbps, got %0.2f.',
                        test_type, should_expected_throughput,
                        result.throughput)
                should_tput_failed = True
            else:
                logging.info(
                        'Throughput is below (should) expectation for %s. Expected (should) %0.2f Mbps, got %0.2f.',
                        test_type, should_expected_throughput,
                        result.throughput)
        if must_tput_failed or should_tput_failed:
            failed_test_type_list = [
                    '[test_type=%s' % test_type,
                    'channel=%d' % ap_config.channel,
                    'power_save_on=%r' % power_save,
                    'measured_Tput=%0.2f' % result.throughput
            ]
            if must_tput_failed:
                failed_test_type_list.append(
                        'must_expected_Tput_failed=%0.2f' %
                        must_expected_throughput)
            elif should_tput_failed:
                failed_test_type_list.append(
                        'should_expected_Tput_failed=%0.2f' %
                        should_expected_throughput)
            failed_test_types.add(', '.join(failed_test_type_list) + ']')

    def do_run(self, ap_config, manager, power_save, governor):
        """Run a single set of perf tests, for a given AP and DUT config.

        @param ap_config: the AP configuration that is being used
        @param manager: a PerfTestManager instance
        @param power_save: whether or not to use power-save mode on the DUT
                           (boolean)
        @ return set of failed configs
        """
        def get_current_governor(host):
            """
            @ return the CPU governor name used on a machine. If cannot find
                     the governor info of the host, or if there are multiple
                     different governors being used on different cores, return
                     'default'.
            """
            try:
                governors = set(utils.get_scaling_governor_states(host))
                if len(governors) != 1:
                    return 'default'
                return next(iter(governors))
            except:
                return 'default'
        if governor:
            client_governor = utils.get_scaling_governor_states(
                    self.context.client.host)
            router_governor = utils.get_scaling_governor_states(
                    self.context.router.host)
            utils.set_scaling_governors(governor, self.context.client.host)
            utils.set_scaling_governors(governor, self.context.router.host)
            governor_name = governor
        else:
            # try to get machine's current governor
            governor_name = get_current_governor(self.context.client.host)
            if governor_name != get_current_governor(self.context.router.host):
                governor_name = 'default'
            if governor_name == self._governor:
                # If CPU governor is already set to self._governor, don't
                # perform the run twice
                return

        failed_test_types = set()
        self.context.client.powersave_switch(power_save)
        ps_tag = 'PS%s' % ('on' if power_save else 'off')
        governor_tag = 'governor-%s' % governor_name
        ap_config_tag = '_'.join([ap_config.perf_loggable_description,
                                  ps_tag, governor_tag])
        signal_level = self.context.client.wifi_signal_level
        signal_description = '_'.join([ap_config_tag, 'signal'])
        self.write_perf_keyval({signal_description: signal_level})
        for test_type in self.PERF_TEST_TYPES:
            config = manager.get_config(test_type)
            pcap_lan_iface = interface.Interface(self._pcap_lan_iface_name,
                                                 self.context.pcap_host.host)
            session = manager.get_session(test_type,
                                          self.context.client,
                                          self.context.pcap_host,
                                          peer_device_interface=pcap_lan_iface)
            ch_width = ap_config.channel_width
            if ch_width is None:
                raise error.TestFail(
                        'Failed to get the channel width used by the AP and client'
                )
            expected_throughput = expected_performance_results.get_expected_throughput_wifi(
                    test_type, ap_config.mode, ch_width)
            results = session.run(config)
            if not results:
                logging.error('Failed to take measurement for %s', test_type)
                continue

            values = [sample.throughput for sample in results]
            self.output_perf_value(test_type,
                                   values,
                                   units='Mbps',
                                   higher_is_better=True,
                                   graph=ap_config_tag)
            result = manager.get_result(results)
            self.verify_result(result, expected_throughput[0],
                               expected_throughput[1], test_type,
                               failed_test_types, power_save, ap_config)
            self.write_perf_keyval(
                    result.get_keyval(
                            prefix='_'.join([ap_config_tag, test_type])))
        if governor:
            utils.restore_scaling_governor_states(client_governor,
                    self.context.client.host)
            utils.restore_scaling_governor_states(router_governor,
                    self.context.router.host)
        return failed_test_types


    def run_once(self):
        """Test body."""
        start_time = time.time()
        low_throughput_tests = set()
        logging.info(self.context.client.board)

        for ap_config in self._ap_configs:
            # Set up the router and associate the client with it.
            self.configure_and_connect_to_ap(ap_config)
            with ip_config_context_manager.IpConfigContextManager(
            ) as ip_context:

                ip_context.bring_interface_up(self.context.router.host,
                                              self._router_lan_iface_name)
                ip_context.bring_interface_up(self.context.pcap_host.host,
                                              self._pcap_lan_iface_name)
                ip_context.assign_ip_addr_to_iface(self.context.router.host,
                                                   self._router_lan_ip_addr,
                                                   self._router_lan_iface_name)
                ip_context.assign_ip_addr_to_iface(self.context.pcap_host.host,
                                                   self._pcap_lan_ip_addr,
                                                   self._pcap_lan_iface_name)
                ip_context.add_ip_route(self.context.client.host,
                                        self._pcap_lan_ip_addr,
                                        self.context.client.wifi_if,
                                        self.context.router.wifi_ip)
                ip_context.add_ip_route(self.context.pcap_host.host,
                                        self.context.client.wifi_ip,
                                        self._router_lan_iface_name,
                                        self._router_lan_ip_addr)

                manager = perf_manager.PerfTestManager(self._use_iperf)
                # Flag a test error if we disconnect for any reason.
                with self.context.client.assert_no_disconnects():
                    for governor in sorted(set([None, self._governor])):
                        # Run the performance test and record the test types
                        # which failed due to low throughput.
                        low_throughput_tests.update(
                                self.do_run(ap_config, manager,
                                            not (self._power_save_off),
                                            governor))

            # Clean up router and client state for the next run.
            self.context.client.shill.disconnect(self.context.router.get_ssid())
            self.context.router.deconfig()
        end_time = time.time()
        logging.info('Running time %0.1f seconds.', end_time - start_time)
        if len(low_throughput_tests) != 0:
            low_throughput_tags = list(low_throughput_tests)
            raise error.TestFail(
                    'Throughput performance too low for test type(s): %s' %
                    ', '.join(low_throughput_tags))
