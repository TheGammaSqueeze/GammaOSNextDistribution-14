# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging, os, sys, time

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib.cros import chrome


class desktopui_SimpleLogin(test.test):
    """Login and wait until exit flag file is seen."""
    version = 2


    def run_once(self, start_url=None, exit_without_logout=False):
        """
        Entrance point for test.

        @param exit_without_logout: True if exit without logout
                                    False otherwise
        """
        terminate_path = '/tmp/simple_login_exit'
        if os.path.isfile(terminate_path):
            os.remove(terminate_path)

        cr = chrome.Chrome()
        if start_url is not None:
            tab = cr.browser.tabs[0]
            try:
                tab.Navigate(start_url)
            except Exception as e:
                logging.debug(e)
                pass
        if exit_without_logout is True:
            sys.exit(0)
        while True:
            time.sleep(1)
            if os.path.isfile(terminate_path):
                logging.info('Exit flag detected; exiting.')
                cr.browser.Close()
                return


