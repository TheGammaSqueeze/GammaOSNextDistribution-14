# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import shutil
import time

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros import upstart
from autotest_lib.client.cros.power import power_utils

class power_VideoDetector(test.test):
    """
    Verify the backlight does not get dimmed while playing video.
    """

    version = 1
    tmp_path = '/tmp'

    def run_once(self, run_time_sec=60):
        """
        @param run_time_sec: time to run the test
        """
        if run_time_sec < 30:
            raise error.TestError('Must run for at least 30 seconds')


        # https://crbug.com/1288417, b/215442780
        # Copy file to tmpdir to avoid the need of setting up local http server.
        file_path = os.path.join(self.bindir, 'fade.html')
        self.dest_path = os.path.join(self.tmp_path, 'fade.html')
        shutil.copy(file_path, self.dest_path)
        http_path = 'file://' + self.dest_path

        with chrome.Chrome(init_network_controller=True) as cr:
            # Start powerd if not started.  Set timeouts for quick idle events.
            run_time_ms = run_time_sec * 1000
            # At the time of writing this test, the video detector gets a status
            # update from Chrome every ten seconds.
            dim_ms = 10000
            off_ms = max(3600000, run_time_ms * 10)
            prefs = { 'has_ambient_light_sensor' : 0,
                      'ignore_external_policy'   : 1,
                      'plugged_dim_ms'           : dim_ms,
                      'plugged_off_ms'           : off_ms,
                      'unplugged_dim_ms'         : dim_ms,
                      'unplugged_off_ms'         : off_ms, }
            self._pref_change = power_utils.PowerPrefChanger(prefs)

            keyvals = {}

            # Start with max brightness, so we can easily detect dimming.
            power_utils.BacklightController().set_brightness_to_max()
            backlight = power_utils.Backlight()
            initial_brightness = \
                utils.wait_for_value(backlight.get_max_level)

            # Open a tab to play video.
            tab = cr.browser.tabs[0]
            tab.Navigate(http_path)
            tab.WaitForDocumentReadyStateToBeComplete()


            # Sleep until the runtime is up.
            time.sleep(run_time_sec)

            # Stop powerd to avoid dimming when the video stops.
            utils.system_output('stop powerd')

            final_brightness = backlight.get_level()

            # Check that the backlight stayed the same.
            if initial_brightness != final_brightness:
                raise error.TestFail(
                    ('Backlight level changed from %d to %d when it should ' + \
                     'have stayed the same.') %
                    (initial_brightness, final_brightness))

            keyvals['initial_brightness'] = initial_brightness
            keyvals['final_brightness'] = final_brightness
            self.write_perf_keyval(keyvals)


    def cleanup(self):
        """
        Cleanup powerd after test.
        """
        if hasattr(self, 'dest_path'):
            os.remove(self.dest_path)
        upstart.restart_job('powerd')
