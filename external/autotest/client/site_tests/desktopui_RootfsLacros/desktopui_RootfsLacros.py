# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import ui_utils
from autotest_lib.client.common_lib.cros import chrome


class desktopui_RootfsLacros(test.test):
    """Tests logging in, opening lacros, and verifying the version number."""
    version = 1

    def is_lacros_running(self):
        """ Return True if lacros is running. """
        process = utils.run('pgrep -f /run/lacros/chrome',
                            ignore_status=True).stdout
        return len(process) > 0

    def run_once(self, dont_override_profile=False):
        """Check rootfs-lacros opens and its version number."""
        # Use args to keep test as hermetic as possible.
        # See crbug.com/1268252 and crbug.com/1268743 for details.
        browser_args = [
                '--lacros-selection=rootfs', '--enable-features=LacrosSupport',
                '--enable-features=LacrosPrimary',
                '--disable-lacros-keep-alive', '--disable-login-lacros-opening'
        ]

        with chrome.Chrome(autotest_ext=True,
                           dont_override_profile=dont_override_profile,
                           extra_browser_args=browser_args) as cr:
            # Use chrome.automation API to drive UI.
            self.ui = ui_utils.UI_Handler()
            self.ui.start_ui_root(cr)

            # Click the shelf button for lacors.
            self.ui.wait_for_ui_obj('Lacros', role='button')
            self.ui.doDefault_on_obj('Lacros', role='button')

            # Check that lacros process is running.
            try:
                utils.poll_for_condition(condition=self.is_lacros_running)
            except utils.TimeoutError:
                raise error.TestFail(
                        'No Lacros processes running after clicking shelf icon'
                )

            # Get lacros version
            res = utils.run('/run/lacros/chrome -version').stdout
            version = str(utils.parse_chrome_version(res)[0])
            logging.info('Lacros version is %s', version)

            # Save lacros version for other uses.
            save_file = os.path.join(self.resultsdir, 'lacros_version.txt')
            tmp_file = '/tmp/lacros_version.txt'
            utils.run(['echo', version, '>', save_file])
            utils.run(['echo', version, '>', tmp_file])

            # Wait to make sure lacros doesn't crash.
            time.sleep(10)
            try:
                utils.poll_for_condition(condition=self.is_lacros_running)
            except utils.TimeoutError:
                raise error.TestFail('No Lacros processes running after 10s')
