# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import logging
import time

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import upstart
from autotest_lib.client.cros.audio import alsa_utils
from autotest_lib.client.cros.audio import audio_helper
from autotest_lib.client.cros.audio import audio_spec
from autotest_lib.client.cros.audio import cras_utils

APLAY_FILE = '/dev/zero'  # raw data

# Expected results of 'aplay -v' commands.
APLAY_EXPECTED = set([('stream', 'PLAYBACK')])


def _play_audio(device_name, duration=1, channel_count=2):
    """Play a tone and try to ensure it played properly.

    Sample output from aplay -v:

    Playing raw data '/dev/zero' : Signed 16 bit Little Endian, Rate 44100 Hz,
    Stereo
    Hardware PCM card 0 'HDA Intel PCH' device 0 subdevice 0
    Its setup is:
      stream       : PLAYBACK
      access       : RW_INTERLEAVED  format       : S16_LE
      subformat    : STD
      channels     : 2
      rate         : 44100
      exact rate   : 44100 (44100/1)
      msbits       : 16
      buffer_size  : 16384
      period_size  : 4096
      period_time  : 92879
      tstamp_mode  : NONE
      period_step  : 1
      avail_min    : 4096
      period_event : 0
      start_threshold  : 16384
      stop_threshold   : 16384
      silence_threshold: 0
      silence_size : 0
      boundary     : 4611686018427387904
      appl_ptr     : 0
      hw_ptr       : 0

    @param device_name: The output device for aplay.
    @param duration: Duration supplied to aplay.
    @return String output from the command (may be empty).
    @raises CmdError when cmd returns <> 0.
    """
    cmd = [
            'aplay',
            '-v',  # show verbose details
            '-D %s' % device_name,
            '-d %d' % duration,
            '-c %d' % channel_count,
            '-r 44100',
            '-f S16_LE',
            APLAY_FILE,
            '2>&1'
    ]  # verbose details
    return utils.system_output(' '.join(cmd)).strip()


def _check_play(device_name, duration, channel_count, expected):
    """Runs aplay command and checks the output against an expected result.

    The expected results are compared as sets of tuples.

    @param device_name: The output device for aplay.
    @param duration: Duration supplied to aplay.
    @param channel_count: Channel count supplied to aplay
    @param expected: The set of expected tuples.
    @raises error.TestError for invalid output or invalidly matching expected.
    """
    error_msg = 'invalid response from aplay'
    results = _play_audio(device_name, duration, channel_count)
    if not results.startswith("Playing raw data '%s' :" % APLAY_FILE):
        raise error.TestError('%s: %s' % (error_msg, results))
    result_set = utils.set_from_keyval_output(results, '[\s]*:[\s]*')
    if set(expected) <= result_set:
        return
    raise error.TestError('%s: expected=%s.' %
                          (error_msg, sorted(set(expected) - result_set)))


class audio_Aplay(test.test):
    """Checks that simple aplay functions correctly."""
    version = 1

    def initialize(self):
        """Stop ui while running the test."""
        upstart.stop_job('ui')

    def cleanup(self):
        """Start ui back after the test."""
        upstart.restart_job('ui')

    def run_once(self, duration=1, test_headphone=False):
        """Run aplay and verify its output is as expected.

        @param duration: The duration to run aplay in seconds.
        @param test_headphone: If the value is true, test a headphone. If false,
                               test an internal speaker.
        """

        # Check CRAS server is alive. If not, restart it and wait a second to
        # get server ready.
        if utils.get_service_pid('cras') == 0:
            logging.debug("CRAS server is down. Restart it.")
            utils.start_service('cras', ignore_status=True)
            time.sleep(1)

        # Skip test if there is no internal speaker on the board.
        if not test_headphone:
            board_type = utils.get_board_type()
            board_name = utils.get_board()
            if not audio_spec.has_internal_speaker(board_type, board_name):
                logging.debug("No internal speaker. Skipping the test.")
                return

        if test_headphone:
            output_node = audio_spec.get_headphone_node(utils.get_board())
            channel_count = 2
        else:
            output_node = "INTERNAL_SPEAKER"
            channel_count = audio_spec.get_internal_speaker_channel_count(
                    utils.get_board_type(), utils.get_board(),
                    utils.get_platform(), utils.get_sku())
        logging.debug("Test output device %s", output_node)

        cras_utils.set_single_selected_output_node(output_node)

        cras_device_type = cras_utils.get_selected_output_device_type()
        logging.debug("Selected output device type=%s", cras_device_type)
        if cras_device_type != output_node:
            audio_helper.dump_audio_diagnostics(
                    os.path.join(self.resultsdir, "audio_diagnostics.txt"))
            raise error.TestFail("Fail to select output device.")

        cras_device_name = cras_utils.get_selected_output_device_name()
        logging.debug("Selected output device name=%s", cras_device_name)
        if cras_device_name is None:
            audio_helper.dump_audio_diagnostics(
                    os.path.join(self.resultsdir, "audio_diagnostics.txt"))
            raise error.TestFail("Fail to get selected output device.")

        alsa_device_name = alsa_utils.convert_device_name(cras_device_name)

        # Stop CRAS to make sure the audio device won't be occupied.
        utils.stop_service('cras', ignore_status=True)
        try:
            _check_play(alsa_device_name, duration, channel_count,
                        APLAY_EXPECTED)
        finally:
            #Restart CRAS
            utils.start_service('cras', ignore_status=True)
