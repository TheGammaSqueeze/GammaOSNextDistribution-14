# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import logging
import os
import shutil
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.input_playback import keyboard
from autotest_lib.client.cros.power import power_test

class power_Display(power_test.power_Test):
    """class for power_Display test.
    """
    version = 1
    tmp_path = '/tmp'

    # TODO(tbroch) find more patterns that typical display vendors use to show
    # average and worstcase display power.
    PAGES = ['checker1', 'black', 'white', 'red', 'green', 'blue']
    def run_once(self, pages=None, secs_per_page=60, brightness=''):
        """run_once method.

        @param pages: list of pages names that must be in
            <testdir>/html/<name>.html
        @param secs_per_page: time in seconds to display page and measure power.
        @param brightness: flag for brightness setting to use for testing.
                           possible value are 'max' (100%) and 'all' (all manual
                           brightness steps in ChromeOS)
        """
        if pages is None:
            pages = self.PAGES

        # https://crbug.com/1288417
        # Copy file to tmpdir to avoid the need of setting up local http server.
        file_path = os.path.join(self.bindir, 'html')
        dest_path = os.path.join(self.tmp_path, 'html')
        shutil.copytree(file_path, dest_path)
        http_path = 'file://' + dest_path

        # --disable-sync disables test account info sync, eg. Wi-Fi credentials,
        # so that each test run does not remember info from last test run.
        extra_browser_args = ['--disable-sync']
        # b/228256145 to avoid powerd restart
        extra_browser_args.append('--disable-features=FirmwareUpdaterApp')
        with chrome.Chrome(init_network_controller=True,
                           extra_browser_args=extra_browser_args) as self.cr:
            tab = self.cr.browser.tabs[0]
            tab.Activate()

            # Just measure power in full-screen.
            fullscreen = tab.EvaluateJavaScript('document.webkitIsFullScreen')
            if not fullscreen:
                with keyboard.Keyboard() as keys:
                    keys.press_key('f4')

            # Stop services again as Chrome might have restarted them.
            self._services.stop_services()

            if brightness not in ['', 'all', 'max']:
                raise error.TestFail(
                        'Invalid brightness flag: %s' % (brightness))

            if brightness == 'max':
                self.backlight.set_percent(100)

            brightnesses = []
            if brightness == 'all':
                self.backlight.set_percent(100)
                for step in range(16, 0, -1):
                    nonlinear = step * 6.25
                    linear = self.backlight.nonlinear_to_linear(nonlinear)
                    brightnesses.append((nonlinear, linear))
            else:
                linear = self.backlight.get_percent()
                nonlinear = self.backlight.linear_to_nonlinear(linear)
                brightnesses.append((nonlinear, linear))

            self.start_measurements()

            loop = 0
            for name in pages:
                url = os.path.join(http_path, name + '.html')
                logging.info('Navigating to url: %s', url)
                tab.Navigate(url)
                tab.WaitForDocumentReadyStateToBeComplete()

                for nonlinear, linear in brightnesses:
                    self.backlight.set_percent(linear)
                    tagname = '%s_%s' % (self.tagged_testname, name)
                    if len(brightnesses) > 1:
                        tagname += '_%.2f' % (nonlinear)
                    loop_start = time.time()
                    self.loop_sleep(loop, secs_per_page)
                    self.checkpoint_measurements(tagname, loop_start)
                    loop += 1

        shutil.rmtree(dest_path)
