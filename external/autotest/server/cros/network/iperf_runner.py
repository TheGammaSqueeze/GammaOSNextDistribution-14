# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os.path
import logging
import time
import math
import numbers
import numpy
from enum import IntEnum

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import path_utils


class IperfResult(object):
    """Logic for parsing and representing iperf results."""

    @staticmethod
    def from_iperf_output(results, config):
        """Parse the text output of iperf and return an IperfResult.

        @param results string raw results from iperf.
        @param config IperfConfig the config for the test.

        @return IperfResult result.

        """

        class IperfIndex(IntEnum):
            """Defines the indices of certain values in iperf output."""
            LOG_ID_INDEX = 5
            INTERVAL_INDEX = 6
            DATA_TRANSFERED_INDEX = 7
            PERCENT_LOSS_INDEX = 12

        NUM_FIELDS_IN_SERVER_OUTPUT = 14

        lines = results.splitlines()
        total_throughput = 0
        test_durations = []
        percent_losses = []
        for line in lines:
            fields = line.split(',')
            # Negative Log ID values are used for sum total output which we
            # don't use.
            if float(fields[IperfIndex.LOG_ID_INDEX]) < 0:
                continue
            # Filter out client side logs from UDP results. We only want server
            # side results because they reflect the amount of data that was
            # actually received by the server. Server output has 14 fields while
            # client output has 9 fields, so we use this to differentiate.
            # Ideally we'd use the '-x D' option to filter the client side data,
            # but this option makes the iperf output unreliable.
            if config.udp and len(fields) < NUM_FIELDS_IN_SERVER_OUTPUT:
                continue
            total_data_bytes = float(fields[IperfIndex.DATA_TRANSFERED_INDEX])
            test_interval = fields[IperfIndex.INTERVAL_INDEX]
            test_start_end = test_interval.split('-')
            duration = float(test_start_end[1]) - float(test_start_end[0])
            test_durations.append(duration)
            total_throughput += IperfResult._calculate_throughput(
                    total_data_bytes, duration)
            if (config.udp):
                percent_losses.append(
                        float(fields[IperfIndex.PERCENT_LOSS_INDEX]))

        # We should get one line of output for each port used in the test. In
        # rare cases, the data from one of the ports is not included in the
        # iperf output, so discard results in these cases.
        expected_num_output_lines = config.num_ports
        if config.bidirectional:
            expected_num_output_lines *= 2
        if len(test_durations) != expected_num_output_lines:
            logging.info(
                    'iperf command output was missing some data, ignoring test run.'
            )
            return None

        test_duration = math.fsum(test_durations) / len(test_durations)
        if config.udp:
            percent_loss = math.fsum(percent_losses) / len(percent_losses)
        else:
            percent_loss = None
        return IperfResult(test_duration, total_throughput, percent_loss)

    @staticmethod
    def from_samples(samples):
        """Build an averaged IperfResult from |samples|.

        Calculate an representative sample with averaged values
        and standard deviation of throughput from samples.

        @param samples list of IperfResult objects.
        @return IperfResult object.

        """
        if len(samples) == 0:
            return None
        duration_samples = [float(sample.duration) for sample in samples]
        duration_mean = numpy.mean(duration_samples)

        throughput_samples = [float(sample.throughput) for sample in samples]
        throughput_mean = numpy.mean(throughput_samples)
        throughput_dev = numpy.std(throughput_samples)

        # For TCP connections, the packet loss is 0 by definition. In these
        # cases, the percent_loss will be None for all samples, and UDP results
        # should never have a percent_loss of None, so we can just check the
        # first sample.
        if samples[0].percent_loss == None:
            percent_loss_mean = None
        else:
            percent_loss_samples = [
                    float(sample.percent_loss) for sample in samples
            ]
            percent_loss_mean = numpy.mean(percent_loss_samples)

        return IperfResult(duration_mean,
                           throughput_mean,
                           percent_loss_mean,
                           throughput_dev=throughput_dev)

    def throughput_cv_less_than_maximum(self, max_cv):
        """Check that the throughput from this result is "accurate" enough.

        We say that an IperfResult is "accurate" enough when the coefficient of
        variance (standard deviation / mean) is below the passed in fraction.

        @param fraction float maximum coefficient of variance for the
        throughput sample.
        @return True on above condition.

        """
        if self.throughput is None or self.throughput_dev is None:
            return True

        if not self.throughput_dev and not self.throughput:
            # 0/0 is undefined, but take this to be good for our purposes.
            return True

        if self.throughput_dev and not self.throughput:
            # Deviation is non-zero, but the average is 0.  Deviation
            # as a fraction of the self.throughput is undefined but in theory
            # a "very large number."
            return False

        if self.throughput_dev / self.throughput > max_cv:
            return False

        return True

    @staticmethod
    def _calculate_throughput(total_data, duration):
        """Calculate the throughput from the total bytes transeferred and the
        duration of the test.

        @param total_data int The number of bytes transferred during the test.
        @param duration float The duration of the test in seconds.

        @return float The throughput of the test in Mbps.
        """
        if duration == 0:
            return 0
        total_bits = total_data * 8
        bits_per_second = total_bits / duration
        return bits_per_second / 1000000

    def __init__(self, duration, throughput, percent_loss,
                 throughput_dev=None):
        """Construct an IperfResult.

        @param duration float how long the test took in seconds.
        @param throughput float test throughput in Mbps.
        @param percent_loss float percentage of packets lost in UDP transfer.
        @param throughput_dev standard deviation of throughputs.
        """
        self.duration = duration
        self.throughput = throughput
        self.percent_loss = percent_loss
        self.throughput_dev = throughput_dev

    def get_keyval(self, prefix=''):
        ret = {}
        if prefix:
            prefix = prefix + '_'
        if self.throughput_dev is None:
            margin = ''
        else:
            margin = '+-%0.2f' % self.throughput_dev
        if self.throughput is not None:
            ret[prefix + 'throughput'] = '%0.2f%s' % (self.throughput, margin)
        return ret

    def __repr__(self):
        fields = []
        fields += [
                '%s=%0.2f' % item for item in list(vars(self).items())
                if item[1] is not None and isinstance(item[1], numbers.Number)
        ]
        return '%s(%s)' % (self.__class__.__name__, ', '.join(fields))


class IperfConfig(object):
    """ Defines the configuration for an iperf run. """
    DEFAULT_TEST_TIME = 10
    DEFAULT_MAX_BANDWIDTH = '10000M'
    DEFAULT_NUM_PORTS = 4

    IPERF_TEST_TYPE_TCP_TX = 'tcp_tx'
    IPERF_TEST_TYPE_TCP_RX = 'tcp_rx'
    IPERF_TEST_TYPE_TCP_BIDIRECTIONAL = 'tcp_bidirectional'
    IPERF_TEST_TYPE_UDP_TX = 'udp_tx'
    IPERF_TEST_TYPE_UDP_RX = 'udp_rx'
    IPERF_TEST_TYPE_UDP_BIDIRECTIONAL = 'udp_bidirectional'

    def __init__(self,
                 test_type,
                 max_bandwidth=DEFAULT_MAX_BANDWIDTH,
                 test_time=DEFAULT_TEST_TIME,
                 num_ports=DEFAULT_NUM_PORTS):
        """ Construct an IperfConfig.

        @param test_type string, PerfTestTypes test type.
        @param max_bandwidth string maximum bandwidth to be used during the test
        e.x. 100M (100 Mbps).
        @param test_time int number of seconds to run the test for.
        @param num_ports int number of ports use in the test.
        """

        if test_type == IperfConfig.IPERF_TEST_TYPE_TCP_TX:
            self.udp = False
            self.bidirectional = False
        elif test_type == IperfConfig.IPERF_TEST_TYPE_TCP_RX:
            self.udp = False
            self.bidirectional = False
        elif test_type == IperfConfig.IPERF_TEST_TYPE_TCP_BIDIRECTIONAL:
            self.udp = False
            self.bidirectional = True
        elif test_type == IperfConfig.IPERF_TEST_TYPE_UDP_TX:
            self.udp = True
            self.bidirectional = False
        elif test_type == IperfConfig.IPERF_TEST_TYPE_UDP_RX:
            self.udp = True
            self.bidirectional = False
        elif test_type == IperfConfig.IPERF_TEST_TYPE_UDP_BIDIRECTIONAL:
            self.udp = True
            self.bidirectional = True
        else:
            raise error.TestFail(
                    'Test type %s is not supported by iperf_runner.' %
                    test_type)
        self.max_bandwidth = max_bandwidth
        self.test_time = test_time
        self.num_ports = num_ports
        self.test_type = test_type


class IperfRunner(object):
    """Delegate to run iperf on a client/server pair."""

    DEFAULT_TEST_TIME = 10
    IPERF_SERVER_MAX_STARTUP_WAIT_TIME = 11
    IPERF_CLIENT_TURNDOWN_WAIT_TIME = 1
    IPERF_COMMAND_TIMEOUT_MARGIN = 20

    def __init__(
            self,
            client_proxy,
            server_proxy,
            config,
            client_interface=None,
            server_interface=None,
    ):
        """Construct an IperfRunner. Use the IP addresses of the passed
        interfaces if they are provided. Otherwise, attempt to use the WiFi
        interface on the devices.

        @param client LinuxSystem object.
        @param server LinuxSystem object.
        @param client_interface Interface object.
        @param server_interface Interface object.

        """
        self._client_proxy = client_proxy
        self._server_proxy = server_proxy
        self._server_host = server_proxy.host
        self._client_host = client_proxy.host
        if server_interface:
            self._server_ip = server_interface.ipv4_address
        # If a server interface was not explicitly provided, attempt to use
        # the WiFi IP of the device.
        else:
            try:
                self._server_ip = server_proxy.wifi_ip
            except:
                raise error.TestFail('Server device has no WiFi IP address, '\
                    'and no alternate interface was specified.')

        if client_interface:
            self._client_ip = client_interface.ipv4_address
        # If a client interface was not explicitly provided, use the WiFi IP
        # address of the WiFiClient device.
        else:
            try:
                self._client_ip = client_proxy.wifi_ip
            except:
                raise error.TestFail('Client device has no WiFi IP address, '\
                    'and no alternate interface was specified.')

        # Assume minijail0 is on ${PATH}, but raise exception if it's not
        # available on both server and client.
        self._minijail = 'minijail0'
        path_utils.must_be_installed(self._minijail, host=self._server_host)
        path_utils.must_be_installed(self._minijail, host=self._client_host)
        # Bind mount a tmpfs over /tmp, since netserver hard-codes the /tmp
        # path. netserver's log files aren't useful anyway.
        self._minijail = ("%s -v -k 'tmpfs,/tmp,tmpfs,"
                          "MS_NODEV|MS_NOEXEC|MS_NOSUID,mode=755,size=10M'" %
                          self._minijail)

        self._config = config
        self._command_iperf_server = path_utils.must_be_installed(
                'iperf', host=self._server_host)
        self._command_iperf_client = path_utils.must_be_installed(
                'iperf', host=self._client_host)
        self._udp_flag = '-u' if config.udp else ''
        self._bidirectional_flag = '-d' if config.bidirectional else ''

    def __enter__(self):
        self._restart_iperf_server()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self._client_proxy.firewall_cleanup()
        self._server_proxy.firewall_cleanup()
        self._kill_iperf_server()
        self._kill_iperf_client()

    def _kill_iperf_client(self):
        """Kills any existing iperf process on the client."""
        self._client_host.run('pkill -9 %s' %
                              os.path.basename(self._command_iperf_client),
                              ignore_status=True)

    def _kill_iperf_server(self):
        """Kills any existing iperf process on the serving host."""
        self._server_host.run('pkill -9 %s' %
                              os.path.basename(self._command_iperf_server),
                              ignore_status=True)

    def _restart_iperf_server(self):
        """Start an iperf server on the server device. Also opens up firewalls
        on the test devices.
        """
        logging.info('Starting iperf server...')
        self._kill_iperf_server()
        logging.debug('iperf server invocation: %s %s -s -B %s -D %s -w 320k',
                      self._minijail, self._command_iperf_server,
                      self._server_ip, self._udp_flag)
        devnull = open(os.devnull, "w")
        # 320kB is the maximum socket buffer size on Gale (default is 208kB).
        self._server_host.run('%s %s -s -B %s -D %s -w 320k' %
                              (self._minijail, self._command_iperf_server,
                               self._server_ip, self._udp_flag),
                              stderr_tee=devnull)
        startup_time = time.time()
        # Ensure the endpoints aren't firewalled.
        protocol = 'udp' if self._config.udp else 'tcp'
        self._client_proxy.firewall_open(protocol, self._server_ip)
        self._server_proxy.firewall_open(protocol, self._client_ip)

        # Run a client iperf test. The client will attempt to connect to the
        # server for 10 seconds, but will exit early if it succeeds before
        # that. This ensures that the server has had suffiecient time to come
        # online before we begin the tests. We don't fail on timeout here
        # because the logic for failed connections is contained in run().
        iperf_test = '%s -c %s -B %s -t 1 %s' % (
                self._command_iperf_client, self._server_ip, self._client_ip,
                self._udp_flag)
        result = self._client_host.run(
                iperf_test,
                ignore_status=True,
                ignore_timeout=True,
                timeout=self.IPERF_SERVER_MAX_STARTUP_WAIT_TIME)
        if not result or result.exit_status:
            logging.debug(
                    'Failed to make a connection to the server in %s seconds.',
                    self.IPERF_SERVER_MAX_STARTUP_WAIT_TIME)
        else:
            logging.debug('Successfully made a connection to the server.')
        # TODO(b:198343041) When iperf2 clients are run too quickly back to
        # back, the server is unable to distinguish between them. Wait briefly
        # to allow the server to reset.
        time.sleep(self.IPERF_CLIENT_TURNDOWN_WAIT_TIME)

    def run(self, ignore_failures=False, retry_count=3):
        """Run iperf and take a performance measurement.

        @param ignore_failures bool True iff iperf runs that fail should be
                ignored.  If this happens, run will return a None value rather
                than an IperfResult.
        @param retry_count int number of times to retry the iperf command if
                it fails due to an internal timeout within iperf.
        @return IperfResult summarizing an iperf run.

        """
        iperf_client = '%s -c %s -B %s -b %s -x C -y c -P 4 -t %s %s %s' % (
                self._command_iperf_client, self._server_ip, self._client_ip,
                self._config.max_bandwidth, self._config.test_time,
                self._udp_flag, self._bidirectional_flag)

        logging.info('Running iperf client for %d seconds.',
                     self._config.test_time)
        logging.debug('iperf client invocation: %s', iperf_client)
        timeout = self._config.test_time + self.IPERF_COMMAND_TIMEOUT_MARGIN

        for _ in range(retry_count):
            result = self._client_host.run(iperf_client,
                                           ignore_status=True,
                                           ignore_timeout=ignore_failures,
                                           timeout=timeout)
            if not result:
                logging.info('Retrying iperf after empty result.')
                continue

            # Exit retry loop on success.
            if not result.exit_status:
                break

            # We are in an unhandled error case.
            logging.info('Retrying iperf after an unknown error.')

        if ignore_failures and (result is None or result.exit_status):
            return None

        if result is None:
            raise error.TestFail("No results; cmd: %s", iperf_client)

        if result.exit_status:
            raise error.CmdError(iperf_client, result,
                                 "Command returned non-zero exit status")
        # TODO(b:198343041) When iperf2 clients are run too quickly back to
        # back, the server is unable to distinguish between them. Wait briefly
        # to allow the server to reset.
        time.sleep(self.IPERF_CLIENT_TURNDOWN_WAIT_TIME)
        return IperfResult.from_iperf_output(result.stdout, self._config)
