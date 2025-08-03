# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_Periodic(update_engine_test.UpdateEngineTest):
    """This verifies periodic update checks by update_engine."""
    version = 1

    def run_once(self,
                 periodic_interval=5,
                 full_payload=True,
                 job_repo_url=None,
                 running_at_desk=False):
        """
        Runs the periodic update check test.

        @param periodic_interval: Seconds between each periodic update check.
        @param full_payload: True for full payload. False for delta.
        @param job_repo_url: A url pointing to the devserver where the autotest
            package for this build should be staged.
        @param running_at_desk: True if the test is being run locally.

        """
        # Get a payload to use for the test.
        payload_url = self.get_payload_for_nebraska(
                job_repo_url,
                full_payload=full_payload,
                public_bucket=running_at_desk)

        # Record kernel state before any updates.
        active, inactive = kernel_utils.get_kernel_state(self._host)

        # Check that the periodic update checks are activated and fire.
        self._run_client_test_and_check_result(
                'autoupdate_PeriodicCheck',
                payload_url=payload_url,
                periodic_interval=periodic_interval)

        # Verify the update completed successfully.
        self._host.reboot()
        rootfs_hostlog, _ = self._create_hostlog_files(
                ignore_event_rootfs=True)
        self.verify_update_events(self._CUSTOM_LSB_VERSION, rootfs_hostlog)
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
