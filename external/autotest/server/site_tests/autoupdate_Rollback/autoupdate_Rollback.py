# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test

POWERWASH_COMMAND = 'safe fast keepimg'
POWERWASH_MARKER_FILE = '/mnt/stateful_partition/factory_install_reset'
STATEFUL_MARKER_FILE = '/mnt/stateful_partition/autoupdate_Rollback_flag'

class autoupdate_Rollback(update_engine_test.UpdateEngineTest):
    """Test that updates the machine and performs rollback."""
    version = 1

    def _powerwash(self):
        """Powerwashes DUT."""
        logging.info('Powerwashing device before rollback.')
        self._host.run(['echo', 'car', '>', STATEFUL_MARKER_FILE])
        self._host.run(['echo', "'%s'" % POWERWASH_COMMAND, '>',
                        POWERWASH_MARKER_FILE])
        self._host.reboot()
        marker = self._host.run(['test', '-e', STATEFUL_MARKER_FILE],
                                ignore_status=True, ignore_timeout=True)
        if marker is None or marker.exit_status == 0:
            raise error.TestFail("Powerwash cycle didn't remove the marker "
                                 "file on the stateful partition.")


    def cleanup(self):
        """Clean up test state."""
        # Save update_engine logs for the update, rollback, and post-reboot.
        self._save_extra_update_engine_logs(number_of_logs=3)

        # Restore the stateful partition so tests can still use this DUT.
        if self._powerwash_attempted:
            self._restore_stateful()

        # Delete rollback-version and rollback-happened pref which are
        # generated during Rollback and Enterprise Rollback.
        # rollback-version is written when update_engine Rollback D-Bus API is
        # called. The existence of rollback-version prevents update_engine to
        # apply payload whose version is the same as rollback-version.
        # rollback-happened is written when update_engine finished Enterprise
        # Rollback operation.
        preserved_prefs_path = ('/mnt/stateful_partition/unencrypted/preserve'
                                '/update_engine/prefs/')
        self._host.run(
            ['rm', os.path.join(preserved_prefs_path, 'rollback-version'),
             os.path.join(preserved_prefs_path, 'rollback-happened')],
            ignore_status=True)
        # Restart update-engine to pick up new prefs.
        self._restart_update_engine(ignore_status=True)


    def run_once(self, job_repo_url=None, powerwash_before_rollback=False):
        """Runs the test.

        @param job_repo_url: URL to get the image.
        @param powerwash_before_rollback: True if we should rollback before
                                          powerwashing.

        @raise error.TestError if anything went wrong with setting up the test;
               error.TestFail if any part of the test has failed.

        """
        self._powerwash_attempted = False
        payload_url = self.get_payload_for_nebraska(job_repo_url)
        active, inactive = kernel_utils.get_kernel_state(self._host)
        logging.info('Initial device state: active kernel %s, '
                     'inactive kernel %s.', active, inactive)

        logging.info('Performing an update.')
        self._run_client_test_and_check_result('autoupdate_CannedOmahaUpdate',
                                               payload_url=payload_url)
        self._host.reboot()
        # Ensure the update completed successfully.
        rootfs_hostlog, _ = self._create_hostlog_files()
        self.verify_update_events(self._FORCED_UPDATE, rootfs_hostlog)
        # We should be booting from the new partition.
        error_msg = 'Failed to set up test by updating DUT.'
        kernel_utils.verify_boot_expectations(inactive, error_msg, self._host)

        if powerwash_before_rollback:
            self._powerwash_attempted = True
            self._powerwash()

        logging.info('Update verified, initiating rollback.')
        # Powerwash is tested separately from rollback.
        self._rollback(powerwash=False)
        self._host.reboot()

        # We should be back on our initial partition.
        error_msg = ('Autoupdate reported that rollback succeeded but we '
                         'did not boot into the correct partition.')
        kernel_utils.verify_boot_expectations(active, error_msg, self._host)
        logging.info('We successfully rolled back to initial kernel.')
