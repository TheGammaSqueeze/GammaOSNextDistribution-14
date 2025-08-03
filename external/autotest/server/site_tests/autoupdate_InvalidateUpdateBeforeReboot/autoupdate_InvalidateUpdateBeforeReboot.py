# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_InvalidateUpdateBeforeReboot(
        update_engine_test.UpdateEngineTest):
    """Tests to see if we can invalidate an update before a reboot."""
    version = 1
    _CLIENT_TEST = 'autoupdate_InvalidateSuccessfulUpdate'

    def run_once(self,
                 full_payload=True,
                 job_repo_url=None,
                 running_at_desk=False):
        """
        Runs the invalidate successful update test.

        @param full_payload: True for full payload, False for delta.
        @param job_repo_url: A url pointing to the devserver where the autotest
            package for this build should be staged.
        @param running_at_desk: indicates test is run locally from a workstation.

        """
        # Get a payload to use for the test.
        payload_url = self.get_payload_for_nebraska(
                job_repo_url,
                full_payload=full_payload,
                public_bucket=running_at_desk)

        # Perform an update, invalidate it and verify successful invalidation.
        self._run_client_test_and_check_result(self._CLIENT_TEST,
                                               payload_url=payload_url)

        # Verify via the logs the update was applied.
        self._check_update_engine_log_for_entry(
                'Update successfully applied, waiting to reboot.',
                raise_error=True)

        # Verify via the logs the update was invalidated.
        self._check_update_engine_log_for_entry(
                'Invalidating previous update.', raise_error=True)
