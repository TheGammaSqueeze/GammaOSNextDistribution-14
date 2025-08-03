# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros import provisioner
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_Lacros(update_engine_test.UpdateEngineTest):
    """Performs a simple AU test and checks lacros."""
    version = 1

    def cleanup(self):
        super(autoupdate_Lacros, self).cleanup()

    def run_once(self,
                 full_payload,
                 job_repo_url=None,
                 m2n=False,
                 running_at_desk=False):
        """
        Performs autoupdate with Nebraska and checks rootfs-lacros.

        @param full_payload: True for full payload, False for delta
        @param job_repo_url: A url pointing to the devserver where the autotest
            package for this build should be staged.
        @param running_at_desk: Indicates test is run locally from workstation.
                                Flag does not work with M2N tests.

        """
        if m2n:
            # Provision latest stable build for the current board.
            build_name = self._get_latest_serving_stable_build()
            logging.debug('build name is %s', build_name)

            # Install the matching build with quick provision.
            autotest_devserver = dev_server.ImageServer.resolve(
                    build_name, self._host.hostname)
            update_url = autotest_devserver.get_update_url(build_name)
            logging.info('Installing source image with update url: %s',
                         update_url)
            provisioner.ChromiumOSProvisioner(
                    update_url, host=self._host,
                    is_release_bucket=True).run_provision()

        # Login and check rootfs-lacros version
        self._run_client_test_and_check_result('desktopui_RootfsLacros',
                                               tag='before')
        before_version = self._host.run(['cat',
                                         '/tmp/lacros_version.txt']).stdout
        logging.info('rootfs-lacros version before update: %s', before_version)

        # Get a payload to use for the test.
        payload_url = self.get_payload_for_nebraska(
                job_repo_url,
                full_payload=full_payload,
                public_bucket=running_at_desk)

        # Record DUT state before the update.
        active, inactive = kernel_utils.get_kernel_state(self._host)

        # Perform the update.
        self._run_client_test_and_check_result('autoupdate_CannedOmahaUpdate',
                                               payload_url=payload_url)

        # Verify the update completed successfully.
        self._host.reboot()
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
        rootfs_hostlog, _ = self._create_hostlog_files()
        self.verify_update_events(self._FORCED_UPDATE, rootfs_hostlog)

        # Check the rootfs-lacros version again.
        self._run_client_test_and_check_result('desktopui_RootfsLacros',
                                               tag='after',
                                               dont_override_profile=True)
        after_version = self._host.run(['cat',
                                        '/tmp/lacros_version.txt']).stdout
        logging.info('rootfs-lacros version after update: %s', after_version)
