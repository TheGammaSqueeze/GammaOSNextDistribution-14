# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server.cros.update_engine import update_engine_test

class autoupdate_NonBlockingOOBEUpdate(update_engine_test.UpdateEngineTest):
    """Try a non-critical (non-blocking) update at OOBE which should fail."""
    version = 1

    _NON_CRITICAL_ERROR = 'finished OmahaRequestAction with code ' \
                          'ErrorCode::kNonCriticalUpdateInOOBE'

    def cleanup(self):
        """Remove the custom lsb-release used by the test."""
        self._clear_custom_lsb_release()
        super(autoupdate_NonBlockingOOBEUpdate, self).cleanup()


    def run_once(self,
                 full_payload=True,
                 job_repo_url=None,
                 running_at_desk=False):
        """
        Tries an autoupdate during ChromeOS OOBE without a deadline.

        An Omaha response to update with a deadline attribute is considered
        critical and should be performed during OOBE. Non critical updates do
        not have a deadline and should not be executed.

        @param full_payload: True for a full payload. False for delta.
        @param job_repo_url: Used for debugging locally. This is used to figure
                             out the current build and the devserver to use.
                             The test will read this from a host argument
                             when run in the lab.
        @param running_at_desk: Indicates test is run locally from a
                                workstation.

        """
        tpm_utils.ClearTPMOwnerRequest(self._host)
        payload_url = self.get_payload_for_nebraska(
                job_repo_url,
                full_payload=full_payload,
                public_bucket=running_at_desk)
        self._run_client_test_and_check_result('autoupdate_StartOOBEUpdate',
                                               payload_url=payload_url,
                                               full_payload=full_payload,
                                               critical_update=False)

        # Check that the update failed as expected.
        if self._check_update_engine_log_for_entry(self._NON_CRITICAL_ERROR):
            return

        err_str = "The update was expected to fail with error code " \
                  "'kNonCriticalUpdateInOOBE' because the update response " \
                  "was not critical and we are still at OOBE. This didn't " \
                  "happen."

        # Is there an update in progress?
        if not self._is_update_engine_idle():
            raise error.TestFail('An update was in progress when it should '
                                 'not have started. %s' % err_str)
        # Were any update requests fired?
        elif not self._get_update_requests():
            raise error.TestFail('There were no update requests in '
                                 'update_engine.log. OOBE update screen was '
                                 'missed. %s' % err_str)
        else:
            err_code = self._get_last_error_string()
            if err_code is not None:
                err_str = '%s Actual Error: %s' % (err_str, err_code)
            raise error.TestFail(err_str)
