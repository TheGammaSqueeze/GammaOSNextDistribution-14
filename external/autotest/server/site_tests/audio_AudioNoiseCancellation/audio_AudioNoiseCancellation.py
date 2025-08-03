# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""This is a server side noise cancellation test using the Chameleon board."""

import logging
import os
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.audio import audio_test_data
from autotest_lib.client.cros.audio import sox_utils
from autotest_lib.client.cros.audio import visqol_utils
from autotest_lib.client.cros.bluetooth.bluetooth_audio_test_data import (
        download_file_from_bucket, get_visqol_binary)
from autotest_lib.client.cros.chameleon import audio_test_utils
from autotest_lib.client.cros.chameleon import chameleon_audio_ids
from autotest_lib.client.cros.chameleon import chameleon_audio_helper
from autotest_lib.server.cros.audio import audio_test

DIST_FILES_DIR = 'gs://chromeos-localmirror/distfiles/test_noise_cancellation'
DATA_DIR = '/tmp'


# Verification steps for the Noise Cancellation processing (NC):
# 1. Prepare the audio source file and reference file.
# 2. Play the source file by Chameleon.
# 3. Record by DUT Internal Mic when NC is on and get ViSQOL score A.
# 4. Repeat step 2.
# 5. Record by DUT Internal Mic when NC is off and get ViSQOL score B.
# 6. Check if A - B >= threshold
#
# In practice, ViSQOL is not the most suitable metrics for NC due to its
# intrusive design (reference: go/visqol). However, it is fair enough to compare
# the relative gain (or degradation) between before and after de-noising.
#
# TODO(johnylin): replace ViSQOL with other metrics if applicable.
# TODO(johnylin): add more speech and noise test inputs for inclusion.
class audio_AudioNoiseCancellation(audio_test.AudioTest):
    """Server side input audio noise cancellation test.

    This test talks to a Chameleon board and a Cros device to verify
    input audio noise cancellation function of the Cros device.

    """
    version = 1
    DELAY_BEFORE_PLAYBACK_SECONDS = 3.0
    DELAY_AFTER_PLAYBACK_SECONDS = 2.0
    DELAY_AFTER_BINDING = 0.5
    DELAY_AFTER_NC_TOGGLED = 0.5

    cleanup_files = []

    def cleanup(self):
        # Restore the default state of bypass blocking mechanism in Cras.
        # Restarting Cras is only way because we are not able to know the
        # default state.
        self.host.run('restart cras')

        # Start Chrome UI.
        self.host.run('start ui')

        # Remove downloaded files and the temporary generated files.
        for cleanup_file in self.cleanup_files:
            if os.path.isfile(cleanup_file):
                os.remove(cleanup_file)

    def download_file_from_bucket(self, file):
        """Download the file from GS bucket.

        @param file: the file name for download.

        @raises: error.TestError if failed.

        @returns: the local path of the downloaded file.
        """
        remote_path = os.path.join(DIST_FILES_DIR, file)
        if not download_file_from_bucket(
                DATA_DIR, remote_path, lambda _, __, p: p.returncode == 0):
            logging.error('Failed to download %s to %s', remote_path, DATA_DIR)
            raise error.TestError('Failed to download file %s from bucket.' %
                                  file)

        return os.path.join(DATA_DIR, file)

    def generate_noisy_speech_file(self, speech_path, noise_path):
        """Generate the mixed audio file of speech and noise data.

        @param speech_path: the file path of the pure speech audio.
        @param noise_path: the file path of the noise audio.

        @raises: error.TestError if failed.

        @returns: the file path of the mixed audio.
        """
        mixed_wav_path = os.path.join(DATA_DIR, 'speech_noise_mixed.wav')
        if os.path.exists(mixed_wav_path):
            os.remove(mixed_wav_path)
        sox_utils.mix_two_wav_files(speech_path,
                                    noise_path,
                                    mixed_wav_path,
                                    input_volume=1.0)
        if not os.path.isfile(mixed_wav_path):
            logging.error('WAV file %s does not exist.', mixed_wav_path)
            raise error.TestError('Failed to mix %s and %s by sox commands.' %
                                  (speech_path, noise_path))

        return mixed_wav_path

    def run_once(self, test_data):
        """Runs Audio Noise Cancellation test.

        Test scenarios can be distinguished by the elements (keys) in test_data.
        Noisy environment test:
            test_data = dict(
                speech_file: the WAV file for the pure speech data.
                noise_file: the WAV file for the noise data.
                threshold: the min required score gain for NC effect.)
        Quiet environment test:
            test_data = dict(
                speech_file: the WAV file for the pure speech data.
                threshold: the min score diff tolerance for NC effect.)

        @param test_data: the dict for files and threshold as mentioned above.
        """
        if not self.facade.get_noise_cancellation_supported():
            logging.warning('Noise Cancellation is not supported.')
            raise error.TestWarn('Noise Cancellation is not supported.')

        def _remove_at_cleanup(filepath):
            self.cleanup_files.append(filepath)

        # Download the files from bucket.
        speech_path = self.download_file_from_bucket(test_data['speech_file'])
        _remove_at_cleanup(speech_path)

        ref_infos = sox_utils.get_infos_from_wav_file(speech_path)
        if ref_infos is None:
            raise error.TestError('Failed to get infos from wav file %s.' %
                                  speech_path)

        if 'noise_file' in test_data:
            # Noisy environment test when 'noise_file' is given.
            noise_path = self.download_file_from_bucket(
                    test_data['noise_file'])
            _remove_at_cleanup(noise_path)

            test_audio_path = self.generate_noisy_speech_file(
                    speech_path, noise_path)
            _remove_at_cleanup(test_audio_path)

            test_infos = sox_utils.get_infos_from_wav_file(test_audio_path)
            if test_infos is None:
                raise error.TestError('Failed to get infos from wav file %s.' %
                                      test_audio_path)
        else:
            # Quiet environment test.
            test_audio_path = speech_path
            test_infos = ref_infos

        playback_testdata = audio_test_data.AudioTestData(
                path=test_audio_path,
                data_format=dict(file_type='wav',
                                 sample_format='S{}_LE'.format(
                                         test_infos['bits']),
                                 channel=test_infos['channels'],
                                 rate=test_infos['rate']),
                duration_secs=test_infos['duration'])

        # Get and set VISQOL working environment.
        get_visqol_binary()

        # Bypass blocking mechanism in Cras to make sure Noise Cancellation is
        # enabled.
        self.facade.set_bypass_block_noise_cancellation(bypass=True)

        source = self.widget_factory.create_widget(
                chameleon_audio_ids.ChameleonIds.LINEOUT)
        sink = self.widget_factory.create_widget(
                chameleon_audio_ids.PeripheralIds.SPEAKER)
        binder = self.widget_factory.create_binder(source, sink)

        recorder = self.widget_factory.create_widget(
                chameleon_audio_ids.CrosIds.INTERNAL_MIC)

        # Select and check the node selected by cras is correct.
        audio_test_utils.check_and_set_chrome_active_node_types(
                self.facade, None,
                audio_test_utils.get_internal_mic_node(self.host))

        # Adjust the proper input gain.
        self.facade.set_chrome_active_input_gain(50)

        # Stop Chrome UI to avoid NC state preference intervened by Chrome.
        self.host.run('stop ui')
        logging.info(
                'UI is stopped to avoid NC preference intervention from Chrome'
        )

        def _run_routine(recorded_filename, nc_enabled):
            # Set NC state via D-Bus control.
            self.facade.set_noise_cancellation_enabled(nc_enabled)
            time.sleep(self.DELAY_AFTER_NC_TOGGLED)

            with chameleon_audio_helper.bind_widgets(binder):
                time.sleep(self.DELAY_AFTER_BINDING)

                logfile_suffix = 'nc_on' if nc_enabled else 'nc_off'
                audio_test_utils.dump_cros_audio_logs(
                        self.host, self.facade, self.resultsdir,
                        'after_binding.{}'.format(logfile_suffix))

                logging.info('Set playback data on Chameleon')
                source.set_playback_data(playback_testdata)

                # Start recording, wait a few seconds, and then start playback.
                # Make sure the recorded data has silent samples in the
                # beginning to trim, and includes the entire playback content.
                logging.info('Start recording from Cros device')
                recorder.start_recording()
                time.sleep(self.DELAY_BEFORE_PLAYBACK_SECONDS)

                logging.info('Start playing %s from Chameleon',
                             playback_testdata.path)
                source.start_playback()

                time.sleep(test_infos['duration'] +
                           self.DELAY_AFTER_PLAYBACK_SECONDS)

                recorder.stop_recording()
                logging.info('Stopped recording from Cros device.')

                audio_test_utils.dump_cros_audio_logs(
                        self.host, self.facade, self.resultsdir,
                        'after_recording.{}'.format(logfile_suffix))

                recorder.read_recorded_binary()
                logging.info('Read recorded binary from Cros device.')

            # Remove the beginning of recorded data. This is to avoid artifact
            # caused by Cros device codec initialization in the beginning of
            # recording.
            recorder.remove_head(1.0)

            recorded_file = os.path.join(self.resultsdir,
                                         recorded_filename + '.raw')
            logging.info('Saving recorded data to %s', recorded_file)
            recorder.save_file(recorded_file)
            _remove_at_cleanup(recorded_file)

            # WAV file is also saved by recorder.save_file().
            recorded_wav_path = recorded_file + '.wav'
            if not os.path.isfile(recorded_wav_path):
                logging.error('WAV file %s does not exist.', recorded_wav_path)
                raise error.TestError('Failed to find recorded wav file.')
            _remove_at_cleanup(recorded_wav_path)

            rec_infos = sox_utils.get_infos_from_wav_file(recorded_wav_path)
            if rec_infos is None:
                raise error.TestError('Failed to get infos from wav file %s.' %
                                      recorded_wav_path)

            # Downsample the recorded data from 48k to 16k rate. It is required
            # for getting ViSQOL score in speech mode.
            recorded_16k_path = '{}_16k{}'.format(
                    *os.path.splitext(recorded_wav_path))
            sox_utils.convert_format(recorded_wav_path,
                                     rec_infos['channels'],
                                     rec_infos['bits'],
                                     rec_infos['rate'],
                                     recorded_16k_path,
                                     ref_infos['channels'],
                                     ref_infos['bits'],
                                     ref_infos['rate'],
                                     1.0,
                                     use_src_header=True,
                                     use_dst_header=True)

            # Remove the silence in the beginning and trim to the same duration
            # as the reference file.
            trimmed_recorded_16k_path = '{}_trim{}'.format(
                    *os.path.splitext(recorded_16k_path))
            sox_utils.trim_silence_from_wav_file(recorded_16k_path,
                                                 trimmed_recorded_16k_path,
                                                 ref_infos['duration'],
                                                 duration_threshold=0.05)

            score = visqol_utils.get_visqol_score(
                    ref_file=speech_path,
                    deg_file=trimmed_recorded_16k_path,
                    log_dir=self.resultsdir,
                    speech_mode=True)

            logging.info('Recorded audio %s got ViSQOL score: %f',
                         recorded_filename, score)
            return score

        logging.info('Run routine with NC enabled...')
        nc_on_score = _run_routine('record_nc_enabled', nc_enabled=True)
        logging.info('Run routine with NC disabled...')
        nc_off_score = _run_routine('record_nc_disabled', nc_enabled=False)

        score_diff = nc_on_score - nc_off_score

        # Track ViSQOL performance score
        test_desc = 'internal_mic_noise_cancellation_visqol_diff'
        self.write_perf_keyval({test_desc: score_diff})

        if score_diff < test_data['threshold']:
            raise error.TestFail(
                    'ViSQOL score diff for NC(=%f) is lower than threshold(=%f)'
                    % (score_diff, test_data['threshold']))
