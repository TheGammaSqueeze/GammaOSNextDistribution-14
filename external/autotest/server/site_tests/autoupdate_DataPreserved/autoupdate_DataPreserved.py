# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server.cros import provisioner
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_DataPreserved(update_engine_test.UpdateEngineTest):
    """Ensure user data and preferences are preserved during an update."""

    version = 1
    _USER_DATA_TEST = 'autoupdate_UserData'


    def cleanup(self):
        self._save_extra_update_engine_logs(number_of_logs=2)
        super(autoupdate_DataPreserved, self).cleanup()
        self._restore_stateful()


    def run_once(self, full_payload=True, job_repo_url=None):
        """
        Tests that users timezone, input methods, and downloads are preserved
        during an update.

        @param full_payload: True for a full payload. False for delta.
        @param job_repo_url: Used for debugging locally. This is used to figure
                             out the current build and the devserver to use.
                             The test will read this from a host argument
                             when run in the lab.

        """
        # Provision latest stable build for the current board.
        build_name = self._get_latest_serving_stable_build()

        # Install the matching build with quick provision.
        autotest_devserver = dev_server.ImageServer.resolve(
                build_name, self._host.hostname)
        update_url = autotest_devserver.get_update_url(build_name)
        logging.info('Installing source image with update url: %s', update_url)
        provisioner.ChromiumOSProvisioner(
                update_url, host=self._host,
                is_release_bucket=True).run_provision()

        # Get payload for the update to ToT.
        payload_url = self.get_payload_for_nebraska(job_repo_url,
                                                    full_payload=full_payload)

        # Change input method and timezone, create a file, then start update.
        self._run_client_test_and_check_result(self._USER_DATA_TEST,
                                               payload_url=payload_url,
                                               tag='before_update')
        self._host.reboot()

        # Ensure preferences and downloads are the same as before the update.
        self._run_client_test_and_check_result(self._USER_DATA_TEST,
                                               tag='after_update')
