# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import random

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.server.cros.update_engine import update_engine_test

class autoupdate_Interruptions(update_engine_test.UpdateEngineTest):
    """Ensures an update completes with multiple user interruptions."""
    version = 1

    def cleanup(self):
        self._save_extra_update_engine_logs(number_of_logs=2)

        # Clean up the nebraska usr dir.
        self._clear_nebraska_dir()

        super(autoupdate_Interruptions, self).cleanup()


    def run_once(self, full_payload=True, interrupt=None, job_repo_url=None):
        """
        Runs an update with interruptions from the user.

        @param full_payload: True for a full payload. False for delta.
        @param interrupt: The interrupt to perform: See _SUPPORTED_INTERRUPTS.
        @param job_repo_url: Used for debugging locally. This is used to figure
                             out the current build and the devserver to use.
                             The test will read this from a host argument
                             when run in the lab.

        """
        if interrupt and interrupt not in self._SUPPORTED_INTERRUPTS:
            raise error.TestFail('Unknown interrupt type: %s' % interrupt)

        # Clear any previously started updates.
        self._remove_update_engine_pref(self._UPDATE_CHECK_RESPONSE_HASH)
        self._restart_update_engine(ignore_status=True)

        payload_url = self.get_payload_for_nebraska(job_repo_url,
                                                    full_payload=full_payload)
        chromeos_version = self._host.get_release_version()
        active, inactive = kernel_utils.get_kernel_state(self._host)
        # Choose a random downloaded progress to interrupt the update.
        progress = random.uniform(0.1, 0.6)
        logging.info('Progress when we will begin interruptions: %f', progress)

        # Login, start the update, logout
        self._run_client_test_and_check_result(
                'autoupdate_LoginStartUpdateLogout',
                payload_url=payload_url,
                progress_to_complete=progress,
                full_payload=full_payload,
                interrupt_network=interrupt == self._NETWORK_INTERRUPT)

        if interrupt in [self._REBOOT_INTERRUPT, self._SUSPEND_INTERRUPT]:
            if self._is_update_finished_downloading():
                raise error.TestFail(
                    'Update finished before %s interrupt started. Interrupt '
                    'was supposed to be at %f' % (interrupt, progress))
            self._wait_for_progress(progress)
            completed = self._get_update_progress()

            if interrupt == self._REBOOT_INTERRUPT:
                self._host.reboot()
                utils.poll_for_condition(self._get_update_engine_status,
                                         desc='update engine to start')
                # The client test created a nebraska startup config, so
                # nebraska will be up after the reboot.
                self._check_for_update(self._get_nebraska_update_url())
            elif interrupt == self._SUSPEND_INTERRUPT:
                self._suspend_then_resume()

            if self._is_update_engine_idle():
                raise error.TestFail(
                    'The update was IDLE after interrupt. Last error: %s' %
                    self._get_last_error_string())
            if not self._update_continued_where_it_left_off(
                completed,
                reboot_interrupt=interrupt == self._REBOOT_INTERRUPT):
                raise error.TestFail('The update did not continue where it '
                                     'left off after interruption.')

        self._wait_for_update_to_complete()
        self._edit_nebraska_startup_config(no_update=True)
        self._host.reboot()
        # Check that update engine is ready after reboot.
        utils.poll_for_condition(self._get_update_engine_status,
                                 desc='update engine to start')
        # Do a final update check with no_update=True to get post reboot event.
        self._check_for_update(self._get_nebraska_update_url())

        # Verify the update was successful by checking hostlog and kernel.
        rootfs_hostlog, reboot_hostlog = self._create_hostlog_files()
        self.verify_update_events(chromeos_version, rootfs_hostlog)
        self.verify_update_events(chromeos_version, reboot_hostlog,
                                  chromeos_version)
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
