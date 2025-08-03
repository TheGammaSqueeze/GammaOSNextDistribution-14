# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test
from telemetry.core import exceptions

class autoupdate_UpdateFromUI(update_engine_test.UpdateEngineTest):
    """Starts an update from the ChromeOS Settings app. """
    version = 1

    _NOTIFICATION_INTERVAL = 1
    _NOTIFICATION_TIMEOUT = 10
    _NOTIFICATION_TITLE = "Update available"


    def initialize(self):
        """Test setup."""
        super(autoupdate_UpdateFromUI, self).initialize()
        self._clear_custom_lsb_release()
        self._cr = None


    def cleanup(self):
        """Test cleanup. Clears the custom lsb-release used by the test. """
        self._clear_custom_lsb_release()
        super(autoupdate_UpdateFromUI, self).cleanup()

    def _wait_for_update_notification(self):
        """Waits for the post-update notification to appear. """

        def find_notification():
            """Polls for visibility of the post-update notification. """
            notifications = self._cr.get_visible_notifications()
            if notifications is None:
                return False
            return any(n for n in notifications
                       if self._NOTIFICATION_TITLE in n['title'])

        utils.poll_for_condition(
                condition=find_notification,
                exception=error.TestFail('Post-update notification not found'),
                timeout=self._NOTIFICATION_TIMEOUT,
                sleep_interval=self._NOTIFICATION_INTERVAL)


    def run_once(self, payload_url):
        """
        Tests that a ChromeOS software update can be completed from the UI,
        and that the post-update notification appears when the update is
        complete.

        @param payload_url: The payload url to use.

        """
        with nebraska_wrapper.NebraskaWrapper(
            log_dir=self.resultsdir, payload_url=payload_url) as nebraska:
            # To check the post-update notification, we need to remain logged
            # in after the update completes. However, the DUT will auto-reboot
            # if we log out after completing an update. This will cause the
            # server test to fail when returning from the client test. To avoid
            # this, we stay logged in at the end of the client test by not
            # using a context manager for the Chrome session.
            try:
                self._cr = chrome.Chrome(autotest_ext=True)

                # Need to create a custom lsb-release file to point the UI
                # update button to Nebraska instead of the default update
                # server.
                self._create_custom_lsb_release(
                    nebraska.get_update_url(critical_update=True))

                # Go to the OS settings page and check for an update.
                tab = self._cr.browser.tabs[0]
                tab.Navigate('chrome://os-settings/help')
                tab.WaitForDocumentReadyStateToBeComplete()
                self._take_screenshot('before_check_for_updates.png')
                request_update_js = '''
                    async function checkForUpdate() {
                        return await import('chrome://os-settings/chromeos/os_settings.js').then(m =>
                          m.AboutPageBrowserProxyImpl.getInstance().requestUpdate());
                    }
                    checkForUpdate();
                '''
                try:
                    tab.EvaluateJavaScript(request_update_js)
                except exceptions.EvaluateException:
                    raise error.TestFail(
                        'Failed to find and click Check For Updates button.')
                self._take_screenshot('after_check_for_updates.png')
                self._wait_for_update_to_complete()

            except Exception as e:
                # The update didn't complete, so we can close the Chrome
                # session without worrying about auto-reboot.
                logging.exception("Failed to perform the update: %s", e)
                if self._cr:
                    self._cr.close()
                raise error.TestFail("Failed to perform the update: %s" % e)

            self._wait_for_update_notification()
