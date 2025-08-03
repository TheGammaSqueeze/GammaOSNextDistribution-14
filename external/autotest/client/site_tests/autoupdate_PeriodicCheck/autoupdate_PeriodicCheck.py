# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test


class autoupdate_PeriodicCheck(update_engine_test.UpdateEngineTest):
    """Tests update_engine's periodic update check mechanism."""
    version = 1

    _PERIODIC_INTERVAL_PREF = 'test-update-check-interval-timeout'
    _PERIODIC_LOG = ('Unofficial build, but periodic update check interval '
                     'timeout is defined, so update is not blocked.')

    def cleanup(self):
        """Cleans up the state and extra files this test created."""
        self._remove_update_engine_pref(self._PERIODIC_INTERVAL_PREF)
        self._clear_custom_lsb_release()
        super(autoupdate_PeriodicCheck, self).cleanup()

    def run_once(self, payload_url, periodic_interval):
        """
        Tests update_engine's periodic update check.

        @param payload_url: The payload url.
        @param periodic_interval: Seconds between periodic update checks.

        """
        # Setup the DUT for the test.
        pref_file = os.path.join(self._UPDATE_ENGINE_PREFS_DIR,
                                 self._PERIODIC_INTERVAL_PREF)
        utils.run(['echo', str(periodic_interval), '>', pref_file])
        utils.run(['touch', '/home/chronos/.oobe_completed'])

        with nebraska_wrapper.NebraskaWrapper(
                log_dir=self.resultsdir, payload_url=payload_url) as nebraska:

            logging.info('Setting first update response to return no update.')
            nebraska.update_config(no_update=True)
            self._create_custom_lsb_release(nebraska.get_update_url())
            self._restart_update_engine()

            # Wait for the first update check.
            try:
                utils.poll_for_condition(
                    lambda: len(self._get_update_requests()) == 1,
                    desc='1st periodic update check.',
                    timeout=1.5 * periodic_interval)
            except utils.TimeoutError:
                raise error.TestFail('1st periodic check not found.')
            self._check_update_engine_log_for_entry(self._PERIODIC_LOG,
                                                    raise_error=True)
            logging.info('First periodic update was initiated.')

            logging.info('Setting the next update response to be an update.')
            nebraska.update_config(no_update=False)

            # Wait for the subsequent update checks.
            try:
                utils.poll_for_condition(lambda: len(self._get_update_requests(
                )) > 1,
                                         desc='2nd periodic update check.',
                                         timeout=2 * periodic_interval)
                logging.info(
                        'Setting further update responses back to no update.')
                nebraska.update_config(no_update=True)
            except utils.TimeoutError:
                raise error.TestFail('2nd periodic check not found.')
            logging.info('Second periodic update was initiated.')
            self._wait_for_update_to_complete()
