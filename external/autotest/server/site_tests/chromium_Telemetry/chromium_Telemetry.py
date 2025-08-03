# Lint as: python2, python3
# Copyright (c) 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import logging
import os

from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros import chrome_sideloader
from autotest_lib.server.cros import telemetry_runner
from autotest_lib.server.cros.crosperf import device_setup_utils


class chromium_Telemetry(test.test):
    """Run a telemetry benchmark on browser infra built Chrome."""
    version = 1

    # The path where TLS provisioned the lacros image.
    CHROME_PROVISION = '/var/lib/imageloader/lacros'

    # The path where we install chromium/src. In this experimental
    # stage, we may use existing lacros image, which built at src.
    CHROME_BUILD = '/usr/local/lacros-build'

    # The path that telemetry backend can find chrome.
    # See go/lacros_browser_backend.
    CHROME_DIR = '/usr/local/lacros-chrome'

    def initialize(self, host=None, args=None):
        self.host = host
        assert self.host.path_exists(self.CHROME_PROVISION), (
                'lacros artifact'
                'is not provisioned by CTP. Please check the CTP request.')

        chrome_sideloader.setup_host(self.host, self.CHROME_BUILD, None)

        self.args_dict = utils.args_to_dict(args)
        path_to_chrome = os.path.join(
                self.CHROME_BUILD, self.args_dict.get('exe_rel_path',
                                                      'chrome'))
        logging.info('provisioned lacros to %s', path_to_chrome)

        self.host.run(['rm', '-rf', self.CHROME_DIR])
        self.host.run(['mkdir', '-p', '--mode', '0755', self.CHROME_DIR])
        self.host.run([
                'mv',
                '%s/*' % os.path.dirname(path_to_chrome),
                '%s/' % self.CHROME_DIR
        ])

        tpm_utils.ClearTPMOwnerRequest(self.host, wait_for_ready=True)

        # TODO(crbug/1233676): Read benchmark and filters from test_args.
        self.benchmark = 'speedometer2'
        self.telemetry_args = '--story-filter=Speedometer2'.split()
        repeat = self.args_dict.get('pageset_repeat')
        if repeat is not None:
            self.telemetry_args.append('--pageset-repeat=%s' % repeat)

    def run_once(self):
        """Run a telemetry benchmark."""

        dut_config_str = self.args_dict.get('dut_config', '{}')
        dut_config = json.loads(dut_config_str)
        if dut_config:
            device_setup_utils.setup_device(self.host, dut_config)

        with telemetry_runner.TelemetryRunnerFactory().get_runner(
                self.host, telemetry_on_dut=False,
                is_lacros=True) as telemetry:
            perf_value_writer = self
            telemetry.run_telemetry_benchmark(self.benchmark,
                                              perf_value_writer,
                                              *self.telemetry_args)

    def cleanup(self):
        chrome_sideloader.cleanup_host(self.host, self.CHROME_BUILD, None)
        chrome_sideloader.cleanup_host(self.host, self.CHROME_DIR, None)
