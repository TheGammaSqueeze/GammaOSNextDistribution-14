# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import subprocess
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.audio import audio_helper
from autotest_lib.client.cros.audio import sox_utils


class audio_CrasAec(test.test):
    """Verifies echo cancellation functions well."""
    version = 1

    INT_SPK_CRAS_NODE_TYPE = 'INTERNAL_SPEAKER'
    INT_MIC_CRAS_NODE_TYPE = 'INTERNAL_MIC'

    # (sample rate, channels, rms threshold)
    # The rms_threshold value is determined by experiments.
    TEST_DATA = [
            (48000, 1, 0.015),
            (44100, 1, 0.015),
            (16000, 1, 0.015),
            (44100, 2, 0.015),
            (48000, 2, 0.015),
            (16000, 2, 0.015),
    ]

    def play_sound(self):
        """Plays the given audio content."""
        cmd = [
                'cras_test_client', '--playback_file',
                os.path.join(self.bindir, 'human-voice.raw')
        ]
        self._play_sound_proc = subprocess.Popen(cmd)

    def record_aec(self, rate, channels):
        """Records the looped audio with AEC processing. """
        file_name = os.path.join(self.resultsdir,
                                 'record-%d-ch%d.raw' % (rate, channels))
        cmd = [
                'cras_test_client', '--loopback_file', file_name, '--effects',
                'aec', '--rate',
                str(rate), '--post_dsp', '2', '--num_channels',
                str(channels)
        ]
        self._record_aec_proc = subprocess.Popen(cmd)
        return file_name

    def aecdump(self, stream_id, rate, channels):
        """Do the AEC dump parallelly."""

        file_name = os.path.join(self.resultsdir,
                                 'aecdump-%d-ch%d.raw' % (rate, channels))
        cmd = [
                'cras_test_client', '--aecdump', file_name, '--stream_id',
                str(stream_id), '--duration',
                str(10)
        ]
        self._dump_aec_proc = subprocess.Popen(cmd)

    def setup_test_procs(self):
        """Initializes process variables for this test."""
        self._dump_aec_proc = None
        self._record_aec_proc = None
        self._play_sound_proc = None

    def cleanup_test_procs(self):
        """Cleans up all cras_test_client processes used in test."""
        if self._dump_aec_proc:
            self._dump_aec_proc.kill()
        if self._record_aec_proc:
            self._record_aec_proc.kill()
        if self._play_sound_proc:
            self._play_sound_proc.kill()

    def get_aec_stream_id(self):
        """Gets the first AEC stream id in decimal. """
        proc = subprocess.Popen(['cras_test_client', '--dump_a'],
                                stdout=subprocess.PIPE)
        output, err = proc.communicate()
        lines = output.decode().split('\n')
        # Filter through the summary lines by effects 0x0001 to find
        # the stream id.
        for line in lines:
            words = line.split(' ')
            if words[0] != 'Summary:':
                continue

            logging.debug("audio dump summaries: %s", line)
            if words[8] == '0x0001':
                return int(words[3], 16)

        return None

    def test_sample_rate_and_channels(self, rate, channels):
        """
        Configures CRAS to use aloop as input and output option.
        Plays the given audio content then record through aloop.
        Expects the AEC cancels well because the two-way data
        are the same except scaling and time shift.

        @param rarte: the sample rate to create capture stream
        @param channels: the number of channels to create capture stream

        @returns: the rms value reported by sox util.
        """
        self.setup_test_procs()

        try:
            self.play_sound()
            recorded_file = self.record_aec(rate, channels)

            # Wait at most 2 seconds for AEC stream to be ready for aecdump.
            stream_id = utils.poll_for_condition(self.get_aec_stream_id,
                                                 timeout=2,
                                                 sleep_interval=0.1)

            self.aecdump(stream_id, rate, channels)
            time.sleep(3)
        except utils.TimeoutError:
            # Possibly error has occurred in capture proess.
            audio_helper.dump_audio_diagnostics(
                    os.path.join(self.resultsdir, "audio_diagnostics.txt"))
            raise error.TestFail("Fail to find aec stream's id")
        finally:
            self.cleanup_test_procs()

        sox_stat = sox_utils.get_stat(recorded_file,
                                      channels=channels,
                                      rate=rate)
        return sox_stat.rms

    def run_once(self):
        """Entry point of this test."""
        rms_results = []
        test_pass = True
        try:
            for sample_rate, channels, rms_threshold in self.TEST_DATA:
                rms = self.test_sample_rate_and_channels(sample_rate, channels)
                if rms > rms_threshold:
                    test_pass = False
                rms_results.append(rms)
        finally:
            logging.debug("rms results: %s", rms_results)

        if not test_pass:
            raise error.TestFail("rms too high in at least one case %s" %
                                 rms_results)
