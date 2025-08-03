# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_ConsecutiveUpdatesBeforeReboot(
        update_engine_test.UpdateEngineTest):
    """Performs consecutive updates while waiting for reboot. """
    version = 1

    def cleanup(self):
        """Clean up the test state."""
        # Disable repeated updates using update_engine_client.
        self._set_feature(feature_name=self._REPEATED_UPDATES_FEATURE,
                          enable=False)

    def run_once(self, job_repo_url=None, running_at_desk=False):
        """
        @param job_repo_url: A url pointing to the devserver where the autotest
            package for this build should be staged.
        @param running_at_desk: indicates test is run locally from a workstation.

        """
        # Enable repeated updates using update_engine_client.
        self._set_feature(feature_name=self._REPEATED_UPDATES_FEATURE,
                          enable=True)

        # Get a payload to use for the test.
        payload_url_full = self.get_payload_for_nebraska(
                job_repo_url, full_payload=True, public_bucket=running_at_desk)

        # Record DUT state before the update.
        _, inactive = kernel_utils.get_kernel_state(self._host)

        # Perform an update.
        self._run_client_test_and_check_result(self._CLIENT_TEST,
                                               payload_url=payload_url_full)

        # Verify the first update finished successfully.
        self._wait_for_update_to_complete()

        payload_url_delta = self.get_payload_for_nebraska(
                job_repo_url,
                full_payload=False,
                public_bucket=running_at_desk)

        # Perform another update. This should also succeed because the delta
        # and full payloads have different fingerprint values.
        self._run_client_test_and_check_result(self._CLIENT_TEST,
                                               payload_url=payload_url_delta)

        self._wait_for_update_to_complete()
        # Verify the both updates completed successfully by checking the logs
        # for two successful updates.
        self._check_update_engine_log_for_entry(
                'Update successfully applied, waiting to reboot.',
                raise_error=True,
                min_count=2)

        self._host.reboot()
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
        rootfs_hostlog, _ = self._create_hostlog_files()
        self.verify_update_events(self._FORCED_UPDATE, rootfs_hostlog)
