# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.server.cros.network import iperf_runner


class IperfSession(object):
    """Runs iperf tests and reports average results."""

    MEASUREMENT_MAX_SAMPLES = 10
    MEASUREMENT_MAX_FAILURES = 2
    MEASUREMENT_MIN_SAMPLES = 3
    MAX_THROUGHPUT_CV = 0.03

    def __init__(self,
                 client_proxy,
                 server_proxy,
                 client_interface=None,
                 server_interface=None,
                 ignore_failures=False):
        """Construct an IperfSession.

        @param client_proxy: LinuxSystem object.
        @param server_proxy: LinuxSystem object.
        @param client_interface Interface object.
        @param server_interface Interface object.

        """
        self._client_proxy = client_proxy
        self._server_proxy = server_proxy
        self._client_interface = client_interface
        self._server_interface = server_interface
        self._ignore_failures = ignore_failures

    def run(self, config):
        """Run multiple iperf tests and take the average performance values.

        @param config IperfConfig.

        """

        logging.info('Performing %s measurements in iperf session.',
                     config.test_type)
        history = []
        failure_count = 0
        final_result = None
        with iperf_runner.IperfRunner(self._client_proxy, self._server_proxy,
                                      config, self._client_interface,
                                      self._server_interface) as runner:
            while len(history) + failure_count < self.MEASUREMENT_MAX_SAMPLES:
                result = runner.run(ignore_failures=self._ignore_failures)
                if result is None:
                    failure_count += 1
                    # Might occur when, e.g., signal strength is too low.
                    if failure_count > self.MEASUREMENT_MAX_FAILURES:
                        logging.error('Too many failures (%d), aborting',
                                      failure_count)
                        break
                    continue
                logging.info('Took iperf %s Measurement: %r', config.test_type,
                             result)

                history.append(result)
                if len(history) < self.MEASUREMENT_MIN_SAMPLES:
                    continue

                final_result = iperf_runner.IperfResult.from_samples(history)
                if final_result.throughput_cv_less_than_maximum(
                        self.MAX_THROUGHPUT_CV):
                    break

        if final_result is None:
            final_result = iperf_runner.IperfResult.from_samples(history)
        logging.info('Took averaged measurement from %s iperf %s runs: %r.',
                     len(history), config.test_type, final_result)
        return history or None
