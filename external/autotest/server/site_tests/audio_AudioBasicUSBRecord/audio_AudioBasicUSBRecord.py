# Lint as: python2, python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""This is a server side USB playback audio test using the Chameleon board."""

import logging
import os
import time

from autotest_lib.client.cros.audio import audio_test_data
from autotest_lib.client.cros.chameleon import audio_test_utils
from autotest_lib.client.cros.chameleon import chameleon_audio_helper
from autotest_lib.client.cros.chameleon import chameleon_audio_ids
from autotest_lib.server.cros.audio import audio_test
from autotest_lib.client.common_lib import error


class audio_AudioBasicUSBRecord(audio_test.AudioTest):
    """Server side USB capture audio test.

    This test talks to a Chameleon board and a Cros device to verify
    USB audio record function of the Cros device.

    """
    version = 1
    RECORD_SECONDS = 5

    def run_once(self, suspend=False, blocked_boards=[]):
        """Runs Basic Audio USB recording test.

        @param suspend: True for suspend the device before recording.
                        False for not suspend.

        """

        if self.host.get_board().split(':')[1] in blocked_boards:
            raise error.TestNAError('Board pending fix for b/233962403!')

        golden_file = audio_test_data.GenerateAudioTestData(
                path=os.path.join(self.bindir, 'fix_1k_440_16.wav'),
                duration_secs=6,
                frequencies=[1000, 440])

        source = self.widget_factory.create_widget(
                chameleon_audio_ids.ChameleonIds.USBOUT)
        recorder = self.widget_factory.create_widget(
                chameleon_audio_ids.CrosIds.USBIN)
        binder = self.widget_factory.create_binder(source, recorder)

        with chameleon_audio_helper.bind_widgets(binder):
            # Checks the node selected by cras is correct.
            audio_test_utils.dump_cros_audio_logs(
                    self.host, self.facade, self.resultsdir, 'after_binding')

            audio_test_utils.check_and_set_chrome_active_node_types(
                    self.facade, None, 'USB')
            audio_test_utils.dump_cros_audio_logs(
                    self.host, self.facade, self.resultsdir, 'after_select')

            audio_test_utils.check_audio_nodes(self.facade, (None, ['USB']))
            logging.info('Setting playback data on Cros device')

            self.facade.set_selected_output_volume(70)

            source.set_playback_data(golden_file)

            if suspend:
                audio_test_utils.suspend_resume_and_verify(
                        self.host, self.factory)

                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'after_suspend')

                # Directly select the node through cras
                # Should switch to check_and_set_chrome_active_node_types
                # to set the active node through chrome.audio API when
                # the telemetry bug is fixed (crbug.com/965704)
                self.facade.set_selected_node_types(None, ['USB'])

                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'after_resume_select')

                audio_test_utils.check_audio_nodes(self.facade,
                                                   (None, ['USB']))
            # Starts playing from Chameleon (which waits for Cros device),
            # waits for some time, and then starts recording from Cros device.
            logging.info('Start playing %s on Chameleon device',
                         golden_file.path)
            source.start_playback()

            logging.info('Start recording from Cros.')
            recorder.start_recording()

            time.sleep(self.RECORD_SECONDS)

            recorder.stop_recording()
            logging.info('Stopped recording from Cros.')

            audio_test_utils.dump_cros_audio_logs(
                    self.host, self.facade, self.resultsdir, 'after_recording')

            recorder.read_recorded_binary()
            logging.info('Read recorded binary from Cros.')

        recorded_file = os.path.join(self.resultsdir, "recorded.raw")
        logging.info('Saving recorded data to %s', recorded_file)
        recorder.save_file(recorded_file)

        audio_test_utils.check_recorded_frequency(golden_file, recorder)
