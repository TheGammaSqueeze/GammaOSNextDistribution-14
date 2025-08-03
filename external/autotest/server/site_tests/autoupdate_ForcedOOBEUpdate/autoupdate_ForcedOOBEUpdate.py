# Lint as: python2, python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import random
import time

from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server.cros import provisioner
from autotest_lib.server.cros.update_engine import update_engine_test

class autoupdate_ForcedOOBEUpdate(update_engine_test.UpdateEngineTest):
    """Runs a forced autoupdate during OOBE."""
    version = 1


    def cleanup(self):
        # Get the last two update_engine logs: before and after reboot.
        self._save_extra_update_engine_logs(number_of_logs=2)

        self._clear_custom_lsb_release()

        # Clean up the nebraska usr dir.
        self._clear_nebraska_dir()

        self._set_update_over_cellular_setting(False)

        # Cancel any update still in progress.
        if not self._is_update_engine_idle():
            logging.debug('Canceling the in-progress update.')
            self._restart_update_engine()
        super(autoupdate_ForcedOOBEUpdate, self).cleanup()
        if self._m2n:
            self._restore_stateful()

    def _wait_for_reboot_after_update(self, timeout_minutes=15):
        """
        Waits for the OOBE update to finish and autoreboot.

        The update goes through the following statuses: DOWNLOADING to
        FINALIZING to NEED_REBOOT. It then automatically reboots back to the
        same screen of OOBE. Detecting the reboot is done by:

        1) Checking the number of logs in /var/log/update_engine/ increased.
        2) Checking that the two recent statuses were FINALIZING and IDLE.

        @param timeout_minutes: How long to wait for the update to finish.
                                See crbug/1073855 for context on this default.

        """
        timeout = time.time() + 60 * timeout_minutes
        last_status = None
        logs_before = len(self._get_update_engine_logs())

        while True:
            # Use timeout so if called during reboot we fail early and retry.
            status = self._get_update_engine_status(timeout=10,
                                                    ignore_timeout=True)

            # Check that the status is not reporting an error.
            if status is not None:
                if self._is_checking_for_update(status):
                    continue
                if self._is_update_engine_reporting_error(status):
                    err_str = self._get_last_error_string()
                    raise error.TestFail('Update status reported error '
                                         'during OOBE update: %s' % err_str)
                # if status is IDLE we need to figure out if an error occurred
                # or the DUT autorebooted.
                elif self._is_update_engine_idle(status):
                    self._host.run(
                            'ls /mnt/stateful_partition/etc/lsb-release')
                    if self._is_update_finished_downloading(last_status):
                        if len(self._get_update_engine_logs()) > logs_before:
                            return
                    err_str = self._get_last_error_string()
                    raise error.TestFail('Update status was IDLE during '
                                         'update: %s' % err_str)
                last_status = status

            time.sleep(1)
            if time.time() > timeout:
                raise error.TestFail(
                    'OOBE update did not finish in %d minutes. Last status: %s,'
                    ' Last Progress: %s' % (timeout_minutes,
                    status[self._CURRENT_OP], status[self._PROGRESS]))


    def _wait_for_oobe_update_to_complete(self):
        """Wait for the update that started to complete."""
        self._wait_for_reboot_after_update()
        def found_post_reboot_event():
            """
            Now that the device is rebooted, we have to make sure update_engine
            is up and running and post reboot update check has been performed.

            """
            self._get_update_engine_status(timeout=10, ignore_timeout=False)
            return self._check_update_engine_log_for_entry(
                  'Omaha request response:')
        utils.poll_for_condition(found_post_reboot_event, timeout=60,
                                 desc='post-reboot event to fire after reboot')


    def run_once(self,
                 full_payload=True,
                 cellular=False,
                 interrupt=None,
                 job_repo_url=None,
                 moblab=False,
                 m2n=False):
        """
        Runs a forced autoupdate during ChromeOS OOBE.

        @param full_payload: True for a full payload. False for delta.
        @param cellular: True to do the update over a cellualar connection.
                         Requires that the DUT have a sim card slot.
        @param interrupt: Type of interrupt to try. See _SUPPORTED_INTERRUPTS.
        @param job_repo_url: Used for debugging locally. This is used to figure
                             out the current build and the devserver to use.
                             The test will read this from a host argument
                             when run in the lab.
        @param moblab: True if we are running on moblab.
        @param m2n: True if we should first provision the latest stable version
                    for the current board so that we can perform a M->N update.

        """
        if interrupt and interrupt not in self._SUPPORTED_INTERRUPTS:
            raise error.TestFail('Unknown interrupt type: %s' % interrupt)
        tpm_utils.ClearTPMOwnerRequest(self._host)

        self._m2n = m2n
        if self._m2n:
            # Provision latest stable build for the current build.
            build_name = self._get_latest_serving_stable_build()

            # Install the matching build with quick provision.
            autotest_devserver = dev_server.ImageServer.resolve(
                    build_name, self._host.hostname)
            update_url = autotest_devserver.get_update_url(build_name)
            logging.info('Installing source image with update url: %s',
                         update_url)
            provisioner.ChromiumOSProvisioner(
                    update_url, host=self._host,
                    is_release_bucket=True).run_provision()

        payload_url = None
        if cellular:
            self._set_update_over_cellular_setting(True)
            payload_url = self.get_payload_url_on_public_bucket(
                job_repo_url, full_payload=full_payload)
        else:
            payload_url = self.get_payload_for_nebraska(
                    job_repo_url, full_payload=full_payload)
        before_version = self._host.get_release_version()

        # Clear any previously started updates.
        self._remove_update_engine_pref(self._UPDATE_CHECK_RESPONSE_HASH)
        self._restart_update_engine(ignore_status=True)

        progress = None
        if interrupt is not None:
            # Choose a random downloaded progress to interrupt the update.
            # Moblab may have higher download speeds and take longer to return
            # from the client test, so use a reduced progress range there.
            progress_limit = 0.3 if moblab else 0.6
            progress = random.uniform(0.1, progress_limit)
            logging.info('Progress when we will interrupt: %f', progress)

        active, inactive = kernel_utils.get_kernel_state(self._host)
        # Call client test to start the forced OOBE update.
        self._run_client_test_and_check_result(
                'autoupdate_StartOOBEUpdate',
                payload_url=payload_url,
                full_payload=full_payload,
                cellular=cellular,
                critical_update=True,
                interrupt_network=interrupt == self._NETWORK_INTERRUPT,
                interrupt_progress=progress)

        if interrupt in [self._REBOOT_INTERRUPT, self._SUSPEND_INTERRUPT]:
            logging.info('Waiting to interrupt update.')
            self._wait_for_progress(progress)
            logging.info('The update will be interrupted now...')
            completed = self._get_update_progress()

            self._take_screenshot(self._BEFORE_INTERRUPT_FILENAME)
            if interrupt == self._REBOOT_INTERRUPT:
                self._host.reboot()
            elif interrupt == self._SUSPEND_INTERRUPT:
                self._suspend_then_resume()
            self._take_screenshot(self._AFTER_INTERRUPT_FILENAME)

            if self._is_update_engine_idle():
                raise error.TestFail('The update was IDLE after interrupt.')
            if not self._update_continued_where_it_left_off(
                completed, reboot_interrupt=interrupt is 'reboot'):
                raise error.TestFail('The update did not continue where it '
                                     'left off after interruption.')

            # Remove screenshots since interrupt test succeeded.
            self._remove_screenshots()

        # Set no_update=True in the nebraska startup config to get the
        # post-reboot update event.
        self._edit_nebraska_startup_config(no_update=True)

        self._wait_for_oobe_update_to_complete()

        # Verify the update was successful by checking hostlog and kernel.
        rootfs_hostlog, reboot_hostlog = self._create_hostlog_files()
        self.verify_update_events(self._CUSTOM_LSB_VERSION, rootfs_hostlog)
        self.verify_update_events(self._CUSTOM_LSB_VERSION, reboot_hostlog,
                                  self._CUSTOM_LSB_VERSION)
        kernel_utils.verify_boot_expectations(inactive, host=self._host)
        logging.info('Successfully force updated from %s to %s.',
                     before_version, self._host.get_release_version())
