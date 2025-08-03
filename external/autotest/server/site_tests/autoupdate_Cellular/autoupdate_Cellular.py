# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test

class autoupdate_Cellular(update_engine_test.UpdateEngineTest):
    """
    Tests auto updating over cellular.

    Usually with AU tests we use a lab devserver to hold the payload, and to be
    the omaha instance that DUTs ping. However, over cellular they will not be
    able to reach the devserver. So we will need to put the payload on a
    public google storage location. We will setup an omaha instance on the DUT
    (via autoupdate_CannedOmahaUpdate) that points to the payload on GStorage.

    """
    version = 1

    def _check_for_cellular_entries_in_update_log(self):
        """Check update_engine.log for log entries about cellular."""
        logging.info('Making sure we have cellular entries in update_engine '
                     'log.')
        line1 = ('Allowing updates over cellular as permission preference is '
                 'set to true.')
        line2 = 'We are connected via cellular, Updates allowed: Yes'
        self._check_update_engine_log_for_entry([line1, line2],
                                                raise_error=True)


    def cleanup(self):
        """Clean up the test state."""
        self._set_update_over_cellular_setting(False)
        super(autoupdate_Cellular, self).cleanup()


    def run_once(self, job_repo_url=None, full_payload=True):
        """
        Runs the autoupdate test over cellular once.

        @param job_repo_url: The URL of the current build.
        @param full_payload: Whether the payload should be full or delta.

        """
        payload_url = self.get_payload_for_nebraska(
            job_repo_url, full_payload=full_payload, public_bucket=True)
        active, inactive = kernel_utils.get_kernel_state(self._host)
        self._set_update_over_cellular_setting(True)
        self._run_client_test_and_check_result('autoupdate_CannedOmahaUpdate',
                                               payload_url=payload_url,
                                               use_cellular=True)
        self._check_for_cellular_entries_in_update_log()
        self._host.reboot()
        rootfs_hostlog, _ = self._create_hostlog_files()
        self.verify_update_events(self._FORCED_UPDATE, rootfs_hostlog)
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
