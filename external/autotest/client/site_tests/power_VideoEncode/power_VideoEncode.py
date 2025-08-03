# Lint as: python2, python3
# # Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
import logging
import time

from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.input_playback import keyboard
from autotest_lib.client.cros.power import power_status
from autotest_lib.client.cros.power import power_test


class power_VideoEncode(power_test.power_Test):
    """class for power_VideoEncode test."""
    version = 1

    video_url = 'https://crospower.page.link/power_VideoEncode'

    formats = [
            # Video call with fewer participants.
            ('vp9', 'hd', 24),
            ('vp9', 'vga', 24),
            ('vp9', 'qvga', 24),
            ('vp8', 'hd', 24),
            ('vp8', 'vga', 24),
            ('vp8', 'qvga', 24),
            ('h264', 'hd', 24),
            ('h264', 'vga', 24),
            # Video call with more participants.
            ('vp9', 'hvga', 24),
            ('vp9', 'qhvga', 20),
            ('vp8', 'hvga', 24),
            ('vp8', 'qhvga', 15),
            # Higher resolution video
            ('vp9', 'fhd', 24),
            ('vp8', 'fhd', 24),
            ('h264', 'fhd', 24),
            # AV1 for informational
            ('av1', 'hvga', 24),
            ('av1', 'qhvga', 15),
    ]

    def run_once(self, seconds_per_test=120, format=formats):
        """run_once method.

        @param seconds_per_test: time in seconds for each subtest.
        @param format: list of formats to test.
                       Format is tuple of codec, resolution and framerate.
        """
        extra_browser_args = self.get_extra_browser_args_for_camera_test()
        # b/228256145 to avoid powerd restart
        extra_browser_args.append('--disable-features=FirmwareUpdaterApp')
        with chrome.Chrome(init_network_controller=True,
                           extra_browser_args=extra_browser_args) as cr:

            tab = cr.browser.tabs[0]
            tab.Activate()

            # Just measure power in full-screen.
            fullscreen = tab.EvaluateJavaScript('document.webkitIsFullScreen')
            if not fullscreen:
                with keyboard.Keyboard() as keys:
                    keys.press_key('f4')

            # Stop services again as Chrome might have restarted them.
            self._services.stop_services()

            url = self.video_url
            tab.Navigate(url)
            tab.WaitForDocumentReadyStateToBeComplete()
            video_init_time = power_status.VideoFpsLogger.time_until_ready(tab)
            self.keyvals['video_init_time'] = video_init_time

            self._vlog = power_status.VideoFpsLogger(tab,
                seconds_period=self._seconds_period,
                checkpoint_logger=self._checkpoint_logger)
            self._meas_logs.append(self._vlog)

            loop = 0
            self.start_measurements()
            for codec, resolution, fps in format:
                tagname = '%s_%s_%sfps' % (codec, resolution, fps)
                js = 'changeFormat("%s", "%s", %d)' % (codec, resolution, fps)
                logging.info(js)
                tab.EvaluateJavaScript(js)
                loop_start = time.time()
                self.loop_sleep(loop, seconds_per_test)
                self.checkpoint_measurements(tagname, loop_start)
                loop += 1
