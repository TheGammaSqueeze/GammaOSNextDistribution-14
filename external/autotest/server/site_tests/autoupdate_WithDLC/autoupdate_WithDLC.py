# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_WithDLC(update_engine_test.UpdateEngineTest):
    """Tests basic DLC installation and n-to-n updating. """

    version = 1
    _CLIENT_TEST = 'autoupdate_InstallAndUpdateDLC'

    def initialize(self, host=None):
        """Remove all DLCs on the DUT before starting the test. """
        super(autoupdate_WithDLC, self).initialize(host=host)
        installed = self._dlc_util.list().keys()
        for dlc_id in installed:
            self._dlc_util.purge(dlc_id)
        # DLCs may be present but not mounted, so they won't be purged above.
        self._dlc_util.purge(self._dlc_util._SAMPLE_DLC_ID, ignore_status=True)


    def cleanup(self):
        self._save_extra_update_engine_logs(number_of_logs=2)
        super(autoupdate_WithDLC, self).cleanup()


    def run_once(self,
                 full_payload=True,
                 job_repo_url=None,
                 running_at_desk=False):
        """
        Tests that we can successfully install a DLC, and then update it along
        with the OS.

        @param full_payload: True for a full payload. False for delta.
        @param job_repo_url: Used for debugging locally. This is used to figure
                             out the current build and the devserver to use.
                             The test will read this from a host argument
                             when run in the lab.
        @param running_at_desk: Indicates test is run locally from a
                                workstation.

        """
        payload_urls = []

        # Payload URL for the platform (OS) update
        payload_urls.append(
                self.get_payload_for_nebraska(job_repo_url=job_repo_url,
                                              full_payload=full_payload,
                                              public_bucket=running_at_desk))

        # Payload URLs for sample-dlc, a test DLC package.
        # We'll always need a full payload for DLC installation,
        # and optionally a delta payload if required by the test.
        payload_urls.append(
                self.get_payload_for_nebraska(
                        job_repo_url=job_repo_url,
                        full_payload=True,
                        payload_type=self._PAYLOAD_TYPE.DLC,
                        public_bucket=running_at_desk))
        if not full_payload:
            payload_urls.append(
                    self.get_payload_for_nebraska(
                            job_repo_url=job_repo_url,
                            full_payload=False,
                            payload_type=self._PAYLOAD_TYPE.DLC,
                            public_bucket=running_at_desk))

        active, inactive = kernel_utils.get_kernel_state(self._host)

        # Install and update sample-dlc, a DLC package made for test purposes.
        self._run_client_test_and_check_result(self._CLIENT_TEST,
                                               payload_urls=payload_urls,
                                               full_payload=full_payload)

        self._host.reboot()

        # Verify the update was successful by checking hostlog and kernel.
        rootfs_hostlog, _ = self._create_hostlog_files()
        dlc_rootfs_hostlog, _ = self._create_dlc_hostlog_files()

        logging.info('Checking platform update events')
        self.verify_update_events(self._FORCED_UPDATE, rootfs_hostlog)

        logging.info('Checking DLC update events')
        self.verify_update_events(
                self._FORCED_UPDATE,
                dlc_rootfs_hostlog[self._dlc_util._SAMPLE_DLC_ID])

        kernel_utils.verify_boot_expectations(inactive, host=self._host)

        # After the update and reboot, the DLC will be installed but not yet
        # mounted. If the DLC was updated correctly, calling
        # |dlcservice_util --install| in this state should mount the DLC
        # without hitting Omaha. We can verify this by checking:
        # 1. the DLC is not being preloaded (|dlcservice_util --install| will
        #    show the same behavior if the DLC is preloaded)
        # 2. the install doesn't hit Omaha/Nebraska, by using a bad omaha_url
        self._dlc_util.remove_preloaded(self._dlc_util._SAMPLE_DLC_ID)
        self._dlc_util.install(self._dlc_util._SAMPLE_DLC_ID,
                               omaha_url='fake_url')
        if not self._dlc_util.is_installed(self._dlc_util._SAMPLE_DLC_ID):
            raise error.TestFail('Test DLC was not installed.')
