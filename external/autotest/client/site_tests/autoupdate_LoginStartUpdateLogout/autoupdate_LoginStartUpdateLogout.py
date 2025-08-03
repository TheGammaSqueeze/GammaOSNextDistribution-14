# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test

class autoupdate_LoginStartUpdateLogout(update_engine_test.UpdateEngineTest):
    """
    Logs in, starts an update, then logs out.

    This test is used as part of the server test autoupdate_Interruptions.

    """
    version = 1

    def run_once(self,
                 payload_url,
                 progress_to_complete,
                 full_payload=True,
                 interrupt_network=False):
        """
        Login, start an update, and logout. If specified, this test will also
        disconnect the internet upon reaching a target update progress,
        wait a while, and reconnect the internet before logging out.

        @param payload_url: Payload url to pass to Nebraska.
        @param progress_to_complete: If interrupt_network is
                                     True, the internet will be disconnected
                                     when the update reaches this progress.
                                     This should be a number between 0 and 1.
        @param full_payload: True for a full payload. False for delta.
        @param interrupt_network: True to cause a network interruption when
                                  update progress reaches
                                  progress_to_complete. False to logout after
                                  the update starts.

        """
        # Login as regular user. Start an update. Then Logout

        with nebraska_wrapper.NebraskaWrapper(
                log_dir=self.resultsdir,
                payload_url=payload_url,
                persist_metadata=True) as nebraska:

            config = {'critical_update': True, 'full_payload': full_payload}
            nebraska.update_config(**config)
            update_url = nebraska.get_update_url()
            # Create a nebraska config, which causes nebraska to start up
            # before update_engine. This will allow nebraska to be up right
            # after system startup so it can be used in the reboot
            # interruption test.
            nebraska.create_startup_config(**config)

            with chrome.Chrome(logged_in=True):
                self._check_for_update(update_url)
                # Wait for the update to start.
                utils.poll_for_condition(self._is_update_started, timeout=30)

                if interrupt_network:
                    self._wait_for_progress(progress_to_complete)
                    completed = self._get_update_progress()
                    self._disconnect_reconnect_network_test()

                    if self._is_update_engine_idle():
                        raise error.TestFail(
                                'The update was IDLE after interrupt.')
                    if not self._update_continued_where_it_left_off(completed):
                        raise error.TestFail(
                                'The update did not continue where '
                                'it left off after interruption.')

            # Log in and out with a new user during the update.
            with chrome.Chrome(logged_in=True, dont_override_profile=False):
                pass
