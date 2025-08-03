# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import sys

# TODO (b/206008069), remove this when migrated to new env
sys.path.insert(0,
                '/usr/local/lib/python2.7/dist-packages/six-1.16.0-py2.7.egg')
try:
    # This is weird. But it seems something is bringing in six earlier
    # Going to force a reload after the egg is inserted.
    import six
    if six.PY2:
        reload(six)
    else:
        import importlib
        importlib.reload(six)
    logging.debug("six version is {}".format(six.__version__))
    if six.__version__ != '1.16.0':
        logging.debug(sys.path)
except ImportError as e:
    logging.warning("Could not import six due to %s", e)

from autotest_lib.server import test
from autotest_lib.server.cros import telemetry_runner


class telemetry_ScrollingActionTests(test.test):
    """Run the telemetry scrolling action tests."""
    version = 1


    def run_once(self, host=None):
        """Run the telemetry scrolling action tests.

        @param host: host we are running telemetry on.
        """
        with telemetry_runner.TelemetryRunnerFactory().get_runner(
                host) as telemetry:
            result = telemetry.run_telemetry_test('ScrollingActionTest')
            logging.debug(
                    'Telemetry completed with a status of: %s with '
                    'output: %s', result.status, result.output)
