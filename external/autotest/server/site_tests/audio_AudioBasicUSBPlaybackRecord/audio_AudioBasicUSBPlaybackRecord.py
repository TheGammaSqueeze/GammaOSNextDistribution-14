# Lint as: python2, python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
This is a server side USB playback/record audio test using the
Chameleon board.
"""

import logging
import os
import time

from autotest_lib.client.cros.audio import audio_test_data
from autotest_lib.client.cros.chameleon import audio_test_utils
from autotest_lib.client.cros.chameleon import chameleon_audio_ids
from autotest_lib.client.cros.chameleon import chameleon_audio_helper
from autotest_lib.server.cros.audio import audio_test
from autotest_lib.client.common_lib import error


class audio_AudioBasicUSBPlaybackRecord(audio_test.AudioTest):
    """Server side USB playback/record audio test.

    This test talks to a Chameleon board and a Cros device to verify
    USB audio playback/record function of the Cros device.

    """
    version = 1
    RECORD_SECONDS = 5

    def run_once(self, suspend=False, blocked_boards=[]):
        """Runs Basic Audio USB playback/record test.

        @param suspend: True for suspend the device before playback/record.
                        False for not suspend.

        """
        if self.host.get_board().split(':')[1] in blocked_boards:
            raise error.TestNAError('Board pending fix for b/233962403!')

        golden_file = audio_test_data.GenerateAudioTestData(
                path=os.path.join(self.bindir, 'fix_1k_440_16.wav'),
                duration_secs=6,
                frequencies=[1000, 440])

        playback_source = self.widget_factory.create_widget(
                chameleon_audio_ids.CrosIds.USBOUT)
        playback_recorder = self.widget_factory.create_widget(
                chameleon_audio_ids.ChameleonIds.USBIN)
        playback_binder = self.widget_factory.create_binder(
                playback_source, playback_recorder)

        record_source = self.widget_factory.create_widget(
                chameleon_audio_ids.ChameleonIds.USBOUT)
        record_recorder = self.widget_factory.create_widget(
                chameleon_audio_ids.CrosIds.USBIN)
        record_binder = self.widget_factory.create_binder(
                record_source, record_recorder)

        with chameleon_audio_helper.bind_widgets(playback_binder):
            with chameleon_audio_helper.bind_widgets(record_binder):
                # Checks the node selected by cras is correct.
                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'after_binding')

                audio_test_utils.check_and_set_chrome_active_node_types(
                        self.facade, 'USB', 'USB')
                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'after_select')

                audio_test_utils.check_audio_nodes(self.facade,
                                                   (['USB'], ['USB']))

                logging.info('Setting playback data on Cros device')

                self.facade.set_selected_output_volume(70)

                playback_source.set_playback_data(golden_file)
                record_source.set_playback_data(golden_file)

                if suspend:
                    audio_test_utils.suspend_resume_and_verify(
                            self.host, self.factory)

                    audio_test_utils.dump_cros_audio_logs(
                            self.host, self.facade, self.resultsdir,
                            'after_suspend')

                    # Explicitly select the node as there is a known issue
                    # that the selected node might change after a suspension.
                    # We should remove this after the issue is addressed
                    # (crbug:987529).
                    # Directly select the node through cras
                    # Should switch to check_and_set_chrome_active_node_types
                    # to set the active node through chrome.audio API when
                    # the telemetry bug is fixed (crbug.com/965704)
                    self.facade.set_selected_node_types(['USB'], ['USB'])

                    audio_test_utils.dump_cros_audio_logs(
                            self.host, self.facade, self.resultsdir,
                            'after_resume_select')

                    audio_test_utils.check_audio_nodes(self.facade,
                                                       (['USB'], ['USB']))

                logging.info('Start recording from Chameleon.')
                playback_recorder.start_recording()
                logging.info('Start recording from Cros device.')
                record_recorder.start_recording(block_size=1024)

                logging.info('Start playing %s on Cros device',
                             golden_file.path)
                playback_source.start_playback(block_size=1024)
                logging.info('Start playing %s on Chameleon', golden_file.path)
                record_source.start_playback()

                time.sleep(self.RECORD_SECONDS)

                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'during_recording')

                playback_recorder.stop_recording()
                logging.info('Stopped recording from Chameleon.')
                record_recorder.stop_recording()
                logging.info('Stopped recording from Cros device.')

                audio_test_utils.dump_cros_audio_logs(self.host, self.facade,
                                                      self.resultsdir,
                                                      'after_recording')

                playback_recorder.read_recorded_binary()
                logging.info('Read recorded binary from Chameleon.')
                record_recorder.read_recorded_binary()
                logging.info('Read recorded binary from Cros device.')

        playback_recorded_file = os.path.join(self.resultsdir,
                                              "playback_recorded.raw")
        logging.info('Saving Cros playback recorded data to %s',
                     playback_recorded_file)
        playback_recorder.save_file(playback_recorded_file)

        record_recorded_file = os.path.join(self.resultsdir,
                                            "record_recorded.raw")
        logging.info('Saving Cros record recorded data to %s',
                     record_recorded_file)
        record_recorder.save_file(record_recorded_file)

        audio_test_utils.check_recorded_frequency(golden_file,
                                                  playback_recorder)
        audio_test_utils.check_recorded_frequency(golden_file, record_recorder)
