# Lint as: python2, python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import glob
import os

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.input_playback import input_playback


class platform_InputScreenshot(test.test):
    """Tests if key combinations will create a screenshot."""
    version = 1
    _TMP = '/tmp'
    _DOWNLOADS = '/home/chronos/user/Downloads'
    _SCREENSHOT = 'Screenshot*'
    _ERROR = list()
    _MIN_SIZE = 1000

    def warmup(self):
        """Test setup."""
        # Emulate keyboard.
        # See input_playback. The keyboard is used to play back shortcuts.
        # Remove all screenshots.
        self.player = input_playback.InputPlayback()
        self.player.emulate(input_type='keyboard')
        self.player.find_connected_inputs()
        self.remove_screenshot()


    def remove_screenshot(self):
        """Remove all screenshots."""
        utils.system_output('rm -f %s/%s %s/%s' %(self._TMP, self._SCREENSHOT,
                            self._DOWNLOADS, self._SCREENSHOT))


    def confirm_file_exist(self, filepath):
        """Check if screenshot file can be found and with minimum size.

        @param filepath file path.

        @raises: error.TestFail if screenshot file does not exist.

        """
        if not os.path.isdir(filepath):
            raise error.TestNAError("%s folder is not found" % filepath)

        try:
            paths = utils.poll_for_condition(lambda: glob.glob(
                    os.path.join(filepath, self._SCREENSHOT)),
                                             timeout=20,
                                             sleep_interval=1)
        except utils.TimeoutError:
            self._ERROR.append('Screenshot was not found under: %s' % filepath)
            return

        if len(paths) > 1:
            self._ERROR.append('Found too many screenshots: %s' % paths)
            return

        filesize = os.stat(paths[0]).st_size
        if filesize < self._MIN_SIZE:
            self._ERROR.append('Screenshot size:%d at %s is wrong' %
                               (filesize, filepath))


    def create_screenshot(self):
        """Create a screenshot."""
        self.player.blocking_playback_of_default_file(
               input_type='keyboard', filename='keyboard_ctrl+f5')


    def run_once(self):
        # Screenshot under /tmp without login.
        self.create_screenshot()
        self.confirm_file_exist(self._TMP)

        # Screenshot under /Downloads after login.
        with chrome.Chrome() as cr:
            self.create_screenshot()
            self.confirm_file_exist(self._DOWNLOADS)

        if self._ERROR:
            raise error.TestFail('; '.join(self._ERROR))

    def cleanup(self):
        """Test cleanup."""
        self.player.close()
        self.remove_screenshot()
