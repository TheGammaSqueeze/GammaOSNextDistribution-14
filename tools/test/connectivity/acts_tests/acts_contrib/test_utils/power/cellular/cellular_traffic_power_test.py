#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import time

import scapy.all as scapy

from acts import asserts
from acts import utils
from acts.metrics.loggers.blackbox import BlackboxMetricLogger
from acts_contrib.test_utils.power import IperfHelper as IPH
from acts_contrib.test_utils.power import plot_utils
import acts_contrib.test_utils.power.cellular.cellular_power_base_test as PWCEL
from acts_contrib.test_utils.tel.tel_logging_utils import start_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import stop_adb_tcpdump
from acts_contrib.test_utils.tel.tel_logging_utils import get_tcpdump_log

class PowerTelTrafficTest(PWCEL.PowerCellularLabBaseTest):
    """ Cellular traffic power test.

    Inherits from PowerCellularLabBaseTest. Parses config specific
    to this kind of test. Contains methods to start data traffic
    between a local instance of iPerf and one running in the dut.

    """

    # Keywords for test name parameters
    PARAM_DIRECTION = 'direction'
    PARAM_DIRECTION_UL = 'ul'
    PARAM_DIRECTION_DL = 'dl'
    PARAM_DIRECTION_DL_UL = 'dlul'
    PARAM_BANDWIDTH_LIMIT = 'blimit'

    # Iperf waiting time
    IPERF_MARGIN = 10

    def __init__(self, controllers):
        """ Class initialization.

        Sets test parameters to initial values.
        """

        super().__init__(controllers)

        # These variables are passed to iPerf when starting data
        # traffic with the -b parameter to limit throughput on
        # the application layer.
        self.bandwidth_limit_dl = None
        self.bandwidth_limit_ul = None

        # Throughput obtained from iPerf
        self.iperf_results = {}

        # Blackbox metrics loggers

        self.dl_tput_logger = BlackboxMetricLogger.for_test_case(
            metric_name='avg_dl_tput')
        self.ul_tput_logger = BlackboxMetricLogger.for_test_case(
            metric_name='avg_ul_tput')

    def setup_class(self):
        super().setup_class()

        # Unpack test parameters used in this class
        self.unpack_userparams(tcp_window_fraction=0, tcp_dumps=False)

        # Verify that at least one PacketSender controller has been initialized
        if not hasattr(self, 'packet_senders'):
            raise RuntimeError('At least one packet sender controller needs '
                               'to be defined in the test config files.')

    def setup_test(self):
        """ Executed before every test case.

        Parses test configuration from the test name and prepares
        the simulation for measurement.
        """

        # Reset results at the start of the test
        self.iperf_results = {}

        # Call parent method first to setup simulation
        if not super().setup_test():
            return False

        # Traffic direction

        values = self.consume_parameter(self.PARAM_DIRECTION, 1)

        if not values:
            self.log.warning("The keyword {} was not included in the testname "
                             "parameters. Setting to {} by default.".format(
                                 self.PARAM_DIRECTION,
                                 self.PARAM_DIRECTION_DL_UL))
            self.traffic_direction = self.PARAM_DIRECTION_DL_UL
        elif values[1] in [
                self.PARAM_DIRECTION_DL, self.PARAM_DIRECTION_UL,
                self.PARAM_DIRECTION_DL_UL
        ]:
            self.traffic_direction = values[1]
        else:
            self.log.error("The test name has to include parameter {} "
                           "followed by {}/{}/{}.".format(
                               self.PARAM_DIRECTION, self.PARAM_DIRECTION_UL,
                               self.PARAM_DIRECTION_DL,
                               self.PARAM_DIRECTION_DL_UL))
            return False

        # Bandwidth limit

        values = self.consume_parameter(self.PARAM_BANDWIDTH_LIMIT, 2)

        if values:
            self.bandwidth_limit_dl = values[1]
            self.bandwidth_limit_ul = values[2]
        else:
            self.bandwidth_limit_dl = 0
            self.bandwidth_limit_ul = 0
            self.log.error(
                "No bandwidth limit was indicated in the test parameters. "
                "Setting to default value of 0 (no limit to bandwidth). To set "
                "a different value include parameter '{}' followed by two "
                "strings indicating downlink and uplink bandwidth limits for "
                "iPerf.".format(self.PARAM_BANDWIDTH_LIMIT))

        # No errors when parsing parameters
        return True

    def teardown_test(self):
        """Tear down necessary objects after test case is finished.

        """

        super().teardown_test()

        # Log the throughput values to Blackbox
        self.dl_tput_logger.metric_value = self.iperf_results.get('DL', 0)
        self.ul_tput_logger.metric_value = self.iperf_results.get('UL', 0)

        # Log the throughput values to Spanner
        self.power_logger.set_dl_tput(self.iperf_results.get('DL', 0))
        self.power_logger.set_ul_tput(self.iperf_results.get('UL', 0))

        try:
            dl_max_throughput = self.simulation.maximum_downlink_throughput()
            ul_max_throughput = self.simulation.maximum_uplink_throughput()
            self.power_logger.set_dl_tput_threshold(dl_max_throughput)
            self.power_logger.set_ul_tput_threshold(ul_max_throughput)
        except NotImplementedError as e:
            self.log.error("%s Downlink/uplink thresholds will not be "
                           "logged in the power proto" % e)

        for ips in self.iperf_servers:
            ips.stop()

    def power_tel_traffic_test(self):
        """ Measures power and throughput during data transmission.

        Measurement step in this test. Starts iPerf client in the DUT and then
        initiates power measurement. After that, DUT is connected again and
        the result from iPerf is collected. Pass or fail is decided with a
        threshold value.
        """

        # Start data traffic
        iperf_helpers = self.start_tel_traffic(self.dut)

        # Measure power
        self.collect_power_data()

        # Wait for iPerf to finish
        time.sleep(self.IPERF_MARGIN + 2)

        # Collect throughput measurement
        self.iperf_results = self.get_iperf_results(self.dut, iperf_helpers)

        # Check if power measurement is below the required value
        self.pass_fail_check(self.avg_current)

        return self.avg_current, self.iperf_results

    def get_iperf_results(self, device, iperf_helpers):
        """ Pulls iperf results from the device.

        Args:
            device: the device from which iperf results need to be pulled.

        Returns:
            a dictionary containing DL/UL throughput in Mbit/s.
        """

        # Pull TCP logs if enabled
        if self.tcp_dumps:
            self.log.info('Pulling TCP dumps.')
            stop_adb_tcpdump(self.dut)
            get_tcpdump_log(self.dut)

        throughput = {}

        for iph in iperf_helpers:

            self.log.info("Getting {} throughput results.".format(
                iph.traffic_direction))

            iperf_result = iph.process_iperf_results(device, self.log,
                                                     self.iperf_servers,
                                                     self.test_name)

            throughput[iph.traffic_direction] = iperf_result

        return throughput

    def check_throughput_results(self, iperf_results):
        """ Checks throughput results.

        Compares the obtained throughput with the expected value
        provided by the simulation class.

        """

        for direction, throughput in iperf_results.items():
            try:
                if direction == "UL":
                    expected_t = self.simulation.maximum_uplink_throughput()
                elif direction == "DL":
                    expected_t = self.simulation.maximum_downlink_throughput()
                else:
                    raise RuntimeError("Unexpected traffic direction value.")
            except NotImplementedError:
                # Some simulation classes might not have implemented the max
                # throughput calculation yet.
                self.log.debug("Expected throughput is not available for the "
                               "current simulation class.")
            else:

                self.log.info(
                    "The expected {} throughput is {} Mbit/s.".format(
                        direction, expected_t))
                asserts.assert_true(
                    0.90 < throughput / expected_t < 1.10,
                    "{} throughput differed more than 10% from the expected "
                    "value! ({}/{} = {})".format(
                        direction, round(throughput, 3), round(expected_t, 3),
                        round(throughput / expected_t, 3)))

    def pass_fail_check(self, average_current=None):
        """ Checks power consumption and throughput.

        Uses the base class method to check power consumption. Also, compares
        the obtained throughput with the expected value provided by the
        simulation class.

        """
        self.check_throughput_results(self.iperf_results)
        super().pass_fail_check(average_current)

    def start_tel_traffic(self, client_host):
        """ Starts iPerf in the indicated device and initiates traffic.

        Starts the required iperf clients and servers according to the traffic
        pattern config in the current test.

        Args:
            client_host: device handler in which to start the iperf client.

        Returns:
            A list of iperf helpers.
        """
        # The iPerf server is hosted in this computer
        self.iperf_server_address = scapy.get_if_addr(
            self.packet_senders[0].interface)

        self.log.info('Testing IP connectivity with ping.')
        if not utils.adb_shell_ping(
                client_host, count=10, dest_ip=self.iperf_server_address):
            raise RuntimeError('Ping between DUT and host failed.')

        # Start iPerf traffic
        iperf_helpers = []

        # If the tcp_window_fraction parameter was set, calculate the TCP
        # window size as a fraction of the peak throughput.
        ul_tcp_window = None
        dl_tcp_window = None
        if self.tcp_window_fraction == 0:
            self.log.info("tcp_window_fraction was not indicated. "
                          "Disabling fixed TCP window.")
        else:
            try:
                max_dl_tput = self.simulation.maximum_downlink_throughput()
                max_ul_tput = self.simulation.maximum_uplink_throughput()
                dl_tcp_window = max_dl_tput / self.tcp_window_fraction
                ul_tcp_window = max_ul_tput / self.tcp_window_fraction
            except NotImplementedError:
                self.log.error("Maximum downlink/uplink throughput method not "
                               "implemented for %s." %
                               type(self.simulation).__name__)

        if self.traffic_direction in [
                self.PARAM_DIRECTION_DL, self.PARAM_DIRECTION_DL_UL
        ]:
            # Downlink traffic
            iperf_helpers.append(
                self.start_iperf_traffic(client_host,
                                         server_idx=len(iperf_helpers),
                                         traffic_direction='DL',
                                         window=dl_tcp_window,
                                         bandwidth=self.bandwidth_limit_dl))

        if self.traffic_direction in [
                self.PARAM_DIRECTION_UL, self.PARAM_DIRECTION_DL_UL
        ]:
            # Uplink traffic
            iperf_helpers.append(
                self.start_iperf_traffic(client_host,
                                         server_idx=len(iperf_helpers),
                                         traffic_direction='UL',
                                         window=ul_tcp_window,
                                         bandwidth=self.bandwidth_limit_ul))

        # Enable TCP logger.
        if self.tcp_dumps:
            self.log.info('Enabling TCP logger.')
            start_adb_tcpdump(self.dut)

        return iperf_helpers

    def start_iperf_traffic(self,
                            client_host,
                            server_idx,
                            traffic_direction,
                            bandwidth=0,
                            window=None):
        """Starts iPerf data traffic.

        Starts an iperf client in an android device and a server locally.

        Args:
            client_host: device handler in which to start the iperf client
            server_idx: id of the iperf server to connect to
            traffic_direction: has to be either 'UL' or 'DL'
            bandwidth: bandwidth limit for data traffic
            window: the tcp window. if None, no window will be passed to iperf

        Returns:
            An IperfHelper object for the started client/server pair.
        """

        # Start the server locally
        self.iperf_servers[server_idx].start()

        config = {
            'traffic_type': 'TCP',
            'duration':
            self.mon_duration + self.mon_offset + self.IPERF_MARGIN,
            'start_meas_time': 4,
            'server_idx': server_idx,
            'port': self.iperf_servers[server_idx].port,
            'traffic_direction': traffic_direction,
            'window': window
        }

        # If bandwidth is equal to zero then no bandwidth requirements are set
        if bandwidth > 0:
            config['bandwidth'] = bandwidth

        iph = IPH.IperfHelper(config)

        # Start the client in the android device
        client_host.adb.shell_nb(
            "nohup >/dev/null 2>&1 sh -c 'iperf3 -c {} {} "
            "&'".format(self.iperf_server_address, iph.iperf_args))

        self.log.info('{} iPerf started on port {}.'.format(
            traffic_direction, iph.port))

        return iph


class PowerTelRvRTest(PowerTelTrafficTest):
    """ Gets Range vs Rate curves while measuring power consumption.

    Uses PowerTelTrafficTest as a base class.
    """

    # Test name configuration keywords
    PARAM_SWEEP = "sweep"
    PARAM_SWEEP_UPLINK = "uplink"
    PARAM_SWEEP_DOWNLINK = "downlink"

    # Sweep values. Need to be set before starting test by test
    # function or child class.
    downlink_power_sweep = None
    uplink_power_sweep = None

    def setup_test(self):
        """ Executed before every test case.

        Parses test configuration from the test name and prepares
        the simulation for measurement.
        """

        # Call parent method first to setup simulation
        if not super().setup_test():
            return False

        # Get which power value to sweep from config

        try:
            values = self.consume_parameter(self.PARAM_SWEEP, 1)

            if values[1] == self.PARAM_SWEEP_UPLINK:
                self.sweep = self.PARAM_SWEEP_UPLINK
            elif values[1] == self.PARAM_SWEEP_DOWNLINK:
                self.sweep = self.PARAM_SWEEP_DOWNLINK
            else:
                raise ValueError()
        except:
            self.log.error(
                "The test name has to include parameter {} followed by "
                "either {} or {}.".format(self.PARAM_SWEEP,
                                          self.PARAM_SWEEP_DOWNLINK,
                                          self.PARAM_SWEEP_UPLINK))
            return False

        return True

    def power_tel_rvr_test(self):
        """ Main function for the RvR test.

        Produces the RvR curve according to the indicated sweep values.
        """

        if self.sweep == self.PARAM_SWEEP_DOWNLINK:
            sweep_range = self.downlink_power_sweep
        elif self.sweep == self.PARAM_SWEEP_UPLINK:
            sweep_range = self.uplink_power_sweep

        current = []
        throughput = []

        for pw in sweep_range:

            if self.sweep == self.PARAM_SWEEP_DOWNLINK:
                self.simulation.set_downlink_rx_power(self.simulation.bts1, pw)
            elif self.sweep == self.PARAM_SWEEP_UPLINK:
                self.simulation.set_uplink_tx_power(self.simulation.bts1, pw)

            i, t = self.power_tel_traffic_test()
            self.log.info("---------------------")
            self.log.info("{} -- {} --".format(self.sweep, pw))
            self.log.info("{} ----- {}".format(i, t[0]))
            self.log.info("---------------------")

            current.append(i)
            throughput.append(t[0])

        print(sweep_range)
        print(current)
        print(throughput)


class PowerTelTxPowerSweepTest(PowerTelTrafficTest):
    """ Gets Average Current vs Tx Power plot.

    Uses PowerTelTrafficTest as a base class.
    """

    # Test config keywords
    KEY_TX_STEP = 'step'
    KEY_UP_TOLERANCE = 'up_tolerance'
    KEY_DOWN_TOLERANCE = 'down_tolerance'

    # Test name parameters
    PARAM_TX_POWER_SWEEP = 'sweep'

    def setup_class(self):
        super().setup_class()
        self.unpack_userparams(
            [self.KEY_TX_STEP, self.KEY_UP_TOLERANCE, self.KEY_DOWN_TOLERANCE])

    def setup_test(self):
        """ Executed before every test case.

        Parses test configuration from the test name and prepares
        the simulation for measurement.
        """
        # Call parent method first to setup simulation
        if not super().setup_test():
            return False

        # Determine power range to sweep from test case params
        try:
            values = self.consume_parameter(self.PARAM_TX_POWER_SWEEP, 2)

            if len(values) == 3:
                self.start_dbm = int(values[1].replace('n', '-'))
                self.end_dbm = int(values[2].replace('n', '-'))
            else:
                raise ValueError('Not enough params specified for sweep.')
        except ValueError as e:
            self.log.error("Unable to parse test param sweep: {}".format(e))
            return False

        return True

    def pass_fail_check(self, currents, txs, iperf_results):
        """ Compares the obtained throughput with the expected
        value provided by the simulation class. Also, ensures
        consecutive currents do not increase or decrease beyond
        specified tolerance
        """
        for iperf_result in iperf_results:
            self.check_throughput_results(iperf_result)

        # x = reference current value, y = next current value, i = index of x
        for i, (x, y) in enumerate(zip(currents[::], currents[1::])):
            measured_change = (y - x) / x * 100
            asserts.assert_true(
                -self.down_tolerance < measured_change < self.up_tolerance,
                "Current went from {} to {} ({}%) between {} dBm and {} dBm. "
                "Tolerance range: -{}% to {}%".format(x, y, measured_change,
                                                      txs[i], txs[i + 1],
                                                      self.down_tolerance,
                                                      self.up_tolerance))

    def create_power_plot(self, currents, txs):
        """ Creates average current vs tx power plot
        """
        title = '{}_{}_{}_tx_power_sweep'.format(
            self.test_name, self.dut.model, self.dut.build_info['build_id'])

        plot_utils.monsoon_tx_power_sweep_plot(self.mon_info.data_path, title,
                                               currents, txs)

    def power_tel_tx_sweep(self):
        """ Main function for the Tx power sweep test.

        Produces a plot of power consumption vs tx power
        """
        currents = []
        txs = []
        iperf_results = []
        for tx in range(self.start_dbm, self.end_dbm + 1, self.step):

            self.simulation.set_uplink_tx_power(tx)

            iperf_helpers = self.start_tel_traffic(self.dut)

            # Measure power
            self.collect_power_data()

            # Wait for iPerf to finish
            time.sleep(self.IPERF_MARGIN + 2)

            # Collect and check throughput measurement
            iperf_result = self.get_iperf_results(self.dut, iperf_helpers)

            currents.append(self.avg_current)

            # Get the actual Tx power as measured from the callbox side
            measured_tx = self.simulation.get_measured_ul_power()

            txs.append(measured_tx)
            iperf_results.append(iperf_result)

        self.create_power_plot(currents, txs)
        self.pass_fail_check(currents, txs, iperf_results)
