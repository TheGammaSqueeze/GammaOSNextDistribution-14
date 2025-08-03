# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import test


class firmware_FingerprintCrosConfig(test.test):
    """Test ChromeOS config behavior for http://b/160271883."""
    version = 1

    def initialize(self, host):
        self.host = host

    def run_cmd(self, command, timeout=300):
        """Runs command on the DUT; return result with output and exit code."""
        logging.debug('DUT Execute: %s', command)
        result = self.host.run(command, timeout=timeout, ignore_status=True)
        logging.info('exit_code: %d', result.exit_status)
        logging.info('stdout:\n%s', result.stdout)
        logging.info('stderr:\n%s', result.stderr)
        return result

    def _run_cros_config_cmd_cat(self, command):
        """Runs cat /run/chromeos-config/v1 on DUT; return result."""
        cmd = "cat /run/chromeos-config/v1/{}".format(command)
        return self.run_cmd(cmd)

    def run_once(self):
        """Run the test."""
        result = self._run_cros_config_cmd_cat('fingerprint/board')
        if result.exit_status != 0:
            raise error.TestFail(
                'Unable to get fingerprint board with cros_config')
        logging.info('fingerprint board: %s\n', result.stdout.rstrip())
