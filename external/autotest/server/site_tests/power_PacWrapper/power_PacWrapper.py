# Lint as: python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Wrapper test measures DUT power via servod with Servo devices."""

from autotest_lib.server.cros.power import power_base_wrapper
from autotest_lib.server.cros.power import power_telemetry_logger


class power_PacWrapper(power_base_wrapper.PowerBaseWrapper):
    """Wrapper test around a client test.

    This wrapper test runs 1 client test given by user, and measures DUT power
    via servod with Servo devices.
    """
    version = 1

    def _get_power_telemetry_logger(self, host, config, resultsdir):
        """Return powerlog telemetry logger.

        @param host: CrosHost object representing the DUT.
        @param config: the args argument from test_that in a dict. Settings for
                       power telemetry devices.
                       required data:
                       {'test': 'test_TestName.tag',
                        'servo_host': host of servod instance,
                        'servo_port: port that the servod instance is on}
        @param resultsdir: path to directory where current autotest results are
                           stored, e.g. /tmp/test_that_results/
                           results-1-test_TestName.tag/test_TestName.tag/
                           results/
        """
        self._pacman_telemetry_logger = power_telemetry_logger.PacTelemetryLogger(
                config, resultsdir, host)
        return self._pacman_telemetry_logger

    def postprocess(self):
        self._pacman_telemetry_logger.output_pacman_aggregates(self)
