# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import arc, chrome
from autotest_lib.client.cros import upstart


class power_UiResume(arc.ArcTest):
    """
    Suspend the system while simulating user behavior.

    If ARC is present, open ARC before suspending. If ARC is not present, log
    into Chrome before suspending. To suspend, call autotest power_Resume. This
    reduces duplicate code, while cover all 3 cases: with ARC, with Chrome but
    without ARC, and without Chrome.

    """
    version = 3

    def initialize(self, no_arc=False):
        """
        Entry point. Initialize ARC if it is enabled on the DUT, otherwise log
        in Chrome browser.

        """
        # --disable-sync disables test account info sync, eg. Wi-Fi credentials,
        # so that each test run does not remember info from last test run.
        extra_browser_args = ['--disable-sync']

        # TODO(b/191251229): Only enable ARC if ARC is available and it is not
        # running ARCVM.
        self._enable_arc = (utils.is_arc_available() and not utils.is_arcvm()
                            and not no_arc)
        if self._enable_arc:
            super(power_UiResume,
                  self).initialize(extra_browser_args=extra_browser_args)
        else:
            self._chrome = chrome.Chrome(extra_browser_args=extra_browser_args)


    def run_once(self, max_devs_returned=10, seconds=0,
                 ignore_kernel_warns=False):
        """
        Run client side autotest power_Resume, to reduce duplicate code.

        """
        service = 'powerd'
        err = 0
        pid_start = upstart.get_pid(service)
        if not pid_start:
            upstart.restart_job(service)
        pid_start = upstart.get_pid(service)
        if not pid_start:
            logging.error('%s is not running at start of test', service)
            err += 1

        self.job.run_test(
                'power_Resume',
                max_devs_returned=max_devs_returned,
                seconds=seconds,
                ignore_kernel_warns=ignore_kernel_warns,
                measure_arc=self._enable_arc)

        pid_end = upstart.get_pid('powerd')
        if not pid_end:
            logging.error('%s is not running at end of test', service)
            err += 1

        if pid_start and pid_end and pid_start != pid_end:
            logging.error('%s respawned during test', service)
            err += 1

        if err:
            raise error.TestFail('Test failed.  See errors for details.')

    def cleanup(self):
        """
        Clean up ARC if it is enabled on the DUT, otherwise close the Chrome
        browser.
        """
        if self._enable_arc:
            super(power_UiResume, self).cleanup()
        else:
            self._chrome.close()
