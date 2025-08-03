# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""bluetooth audio test dat for A2DP, AVRCP, and HFP."""

import logging
import os
import subprocess

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.bin import utils


# Chameleon device's data storing path.
DEVICE_AUDIO_RECORD_DIR = '/tmp/audio'
# Refer to TEST_DATA_DIR in the chameleon/deploy/deploy file.
DEVICE_AUDIO_DATA_DIR = '/usr/share/autotest/audio-test-data'


DIST_FILES = 'gs://chromeos-localmirror/distfiles'
DOWNLOAD_TIMEOUT = 90 # timeout for gsutil downloads
DATA_DIR = '/tmp'


VISQOL_TARBALL = os.path.join(DIST_FILES, 'visqol-binary.tar.gz')
# Path to ViSQOL tarball in autotest server
VISQOL_TARBALL_LOCAL_PATH = os.path.join(DATA_DIR,
                                         os.path.split(VISQOL_TARBALL)[1])
VISQOL_FOLDER = os.path.join(DATA_DIR, 'visqol')
VISQOL_PATH = os.path.join(VISQOL_FOLDER, 'visqol')
# There are several available models for VISQOL, since these VISQOL based tests
# are primarily for voice quality, this model is more tuned for voice quality.
# experimentally, the scores have been fairly similar to the default model
# 'libsvm_nu_svr_model.txt'. Details:
# github.com/google/visqol/tree/61cdced26b7a03098f0c78f7ab71c25dc2e461f5/model
VISQOL_SIMILARITY_MODEL = os.path.join(
        VISQOL_FOLDER, 'visqol.runfiles', '__main__', 'model',
        'tcdvoip_nu.568_c5.31474325639_g3.17773760038_model.txt')
VISQOL_TEST_DIR = os.path.join(VISQOL_FOLDER, 'bt-test-output')


AUDIO_TARBALL = os.path.join(DIST_FILES, 'chameleon-bundle',
                             'audio-test-data.tar.gz')
AUDIO_TEST_DIR = '/usr/local/autotest/cros/audio/test_data'
AUDIO_RECORD_DIR = os.path.join(DATA_DIR, 'audio')

# AUDIO_TARBALL_NAME is the name of the tarball, i.e. audio-test-data.tar.gz
AUDIO_TARBALL_NAME = os.path.split(AUDIO_TARBALL)[1]
# AUDIO_TEST_DATA_DIR is the path of the audio-test-data directory,
# i.e. /tmp/audio-test-data/
AUDIO_TEST_DATA_DIR = os.path.join(DATA_DIR,
                                   AUDIO_TARBALL_NAME.split('.', 1)[0])
AUDIO_DATA_TARBALL_PATH = os.path.join(DATA_DIR, AUDIO_TARBALL_NAME)


A2DP = 'a2dp'
A2DP_MEDIUM = 'a2dp_medium'
A2DP_LONG = 'a2dp_long'
AVRCP = 'avrcp'
HFP_NBS = 'hfp_nbs'
HFP_NBS_MEDIUM = 'hfp_nbs_medium'
HFP_WBS = 'hfp_wbs'
HFP_WBS_MEDIUM = 'hfp_wbs_medium'
VISQOL_BUFFER_LENGTH = 10.0


def download_file_from_bucket(dir, file_address, verify_download):
    """Extract tarball specified by tar_path to directory dir.

    @param dir: Path to directory to download file to.
    @param file_address: URL of the file to download.
    @param verify_download: A function that accepts stdout, stderr, and the
            process as args and verifies if the download succeeded.

    @retuns: The result of a call to verify_download.
    """
    download_cmd = 'gsutil cp -r {0} {1}'.format(file_address, dir)
    download_proc = subprocess.Popen(download_cmd.split(),
                                     stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE)

    try:
        stdout, stderr = utils.poll_for_condition(
                download_proc.communicate,
                error.TestError('Failed to download'), timeout=DOWNLOAD_TIMEOUT,
                desc='Downloading {}'.format(file_address))
    except Exception as e:
        download_proc.terminate()
        return False
    else:
        return verify_download(stdout, stderr, download_proc)


def extract_tarball(dir, tar_path, verify_extraction):
    """Extract tarball specified by tar_path to directory dir.

    @param dir: Path to directory to extract to.
    @param tar_path: Path to the tarball to extract.
    @param verify_extraction: A function that accepts stdout, stderr, and the
            process as args and verifies if the extraction succeeded.

    @retuns: The result of a call to verify_extraction.
    """
    extract_cmd = 'tar -xf {0} -C {1}'.format(tar_path, dir)
    extract_proc = subprocess.Popen(extract_cmd.split(), stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE)

    try:
        stdout, stderr = utils.poll_for_condition(
                extract_proc.communicate, error.TestError('Failed to extract'),
                timeout=DOWNLOAD_TIMEOUT, desc='Extracting {}'.format(tar_path))
    except Exception as e:
        extract_proc.terminate()
        return False
    else:
        return verify_extraction(stdout, stderr, extract_proc)


def verify_visqol_extraction(stdout, stderr, process):
    """Verify all important components of VISQOL are present in expected
    locations.

    @param stdout: Output of the extract process.
    @param stderr: Error output of the extract process.
    @param process: The Popen object of the extract process.

    @returns: True if all required components are present and extraction process
            suceeded.
    """
    return (not stderr and
            os.path.isdir(VISQOL_FOLDER) and
            os.path.isdir(VISQOL_TEST_DIR) and
            os.path.exists(VISQOL_PATH) and
            os.path.exists(VISQOL_SIMILARITY_MODEL))


def get_visqol_binary():
    """Download visqol binary.

    If visqol binary not already available, download from DIST_FILES, otherwise
    skip this step.
    """
    logging.debug('Downloading ViSQOL binary on autotest server')
    if verify_visqol_extraction(None, None, None):
        logging.debug('VISQOL binary already exists, skipping')
        return

    # download from VISQOL_TARBALL
    if not download_file_from_bucket(DATA_DIR, VISQOL_TARBALL,
                                     lambda _, __, p: p.returncode == 0):
        raise error.TestError('Failed to download ViSQOL binary')
    # Extract tarball tp DATA_DIR
    if not extract_tarball(DATA_DIR, VISQOL_TARBALL_LOCAL_PATH,
                           verify_visqol_extraction):
        raise error.TestError('Failed to extract ViSQOL binary')


def get_audio_test_data():
    """Download audio test data files

    Download and unzip audio files for audio tests from DIST_FILES.
    """
    logging.debug('Downloading audio test data on autotest server')

    # download from AUDIO_TARBALL
    if not download_file_from_bucket(DATA_DIR, AUDIO_TARBALL,
                                     lambda _, __, p: p.returncode == 0):
        raise error.TestError('Failed to download audio test data')
    # Extract tarball to DATA_DIR
    if not extract_tarball(
            DATA_DIR, AUDIO_DATA_TARBALL_PATH,
            lambda _, __, ___: os.path.isdir(AUDIO_TEST_DATA_DIR)):
        raise error.TestError('Failed to extract audio test data')


# Audio test data for hfp narrow band speech
hfp_nbs_test_data = {
    'rate': 8000,
    'channels': 1,
    'frequencies': (3500,),
    'file': os.path.join(AUDIO_TEST_DIR,
                         'sine_3500hz_rate8000_ch1_5secs.raw'),
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'hfp_nbs_recorded_by_peer.wav'),
    'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                    'hfp_nbs_recorded_by_dut.raw'),
    'chunk_in_secs': 1,
    'bit_width': 16,
    'format': 'S16_LE',
    'duration': 5,
    'chunk_checking_duration': 5,

    # Device side data used by StartPlayingAudioSubprocess function in
    # bluetooth_audio.py.
    'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                'sine_3500hz_rate8000_ch1_5secs.wav'),

    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'hfp_nbs_recorded_by_peer_%d.raw'),

    'visqol_test_files': [
        {
            'file': os.path.join(AUDIO_TEST_DATA_DIR,
                                 'voice_8k.wav'),
            'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                             'voice_8k_deg_peer.wav'),
            'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                            'voice_8k_deg_dut.raw'),
            'channels': 1,
            'rate': 8000,
            'duration': 26.112 + VISQOL_BUFFER_LENGTH,
            'chunk_checking_duration': 26.112 + VISQOL_BUFFER_LENGTH,
            'bit_width': 16,
            'format': 'S16_LE',
            # convenient way to differentiate ViSQOL tests from regular tests
            'visqol_test': True,
            'encoding': 'signed-integer',
            'speech_mode': True,
            # Passing scored are determined mostly experimentally.
            # TODO(b/179501232) - NBS is currently not uniformly >= 4.0 on all
            # devices so reduce the passing score.
            'sink_passing_score': 3.5,
            'source_passing_score': 3.5,
            'reporting_type': 'voice-8k',

            # Device side data used by StartPlayingAudioSubprocess function in
            # bluetooth_audio.py.
            'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                        'voice_8k.wav'),
        },
        {
            'file': os.path.join(AUDIO_TEST_DATA_DIR,
                                 'sine_3500hz_rate8000_ch1_5secs.wav'),
            'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                             'sine_3k_deg_peer.wav'),
            'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                            'sine_3k_deg_dut.raw'),
            'channels': 1,
            'rate': 8000,
            'duration': 5.0 + VISQOL_BUFFER_LENGTH,
            'chunk_checking_duration': 5.0 + VISQOL_BUFFER_LENGTH,
            'bit_width': 16,
            'format': 'S16_LE',
            # convenient way to differentiate ViSQOL tests from regular tests
            'visqol_test': True,
            'encoding': 'signed-integer',
            'speech_mode': True,
            # Sine tones don't work very well with ViSQOL on the NBS tests, both
            # directions score fairly low, however I've kept it in as a test
            # file because its a good for reference, makes it easy to see
            # degradation and verify that this is transmitting the frequency
            # range we would expect
            # TODO(b/179501232) - NBS is currently not uniformly >= 2.0 on all
            # devices so reduce the passing score.
            'sink_passing_score': 1.0,
            'source_passing_score': 1.0,
            'reporting_type': 'sine-3.5k',

            # Device side data used by StartPlayingAudioSubprocess function in
            # bluetooth_audio.py.
            'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                        'sine_3500hz_rate8000_ch1_5secs.wav'),
        }
    ]
}


# Audio test data for hfp wide band speech
hfp_wbs_test_data = {
    'rate': 16000,
    'channels': 1,

    'frequencies': (7000,),
    'file': os.path.join(AUDIO_TEST_DIR,
                         'sine_7000hz_rate16000_ch1_5secs.raw'),
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'hfp_wbs_recorded_by_peer.wav'),
    'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                    'hfp_wbs_recorded_by_dut.raw'),
    'chunk_in_secs': 1,
    'bit_width': 16,
    'format': 'S16_LE',
    'duration': 5,
    'chunk_checking_duration': 5,

    # Device side data used by StartPlayingAudioSubprocess function in
    # bluetooth_audio.py.
    'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                'sine_7000hz_rate16000_ch1_5secs.wav'),

    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'hfp_wbs_recorded_by_peer_%d.raw'),

    'visqol_test_files': [
        {
            'file': os.path.join(AUDIO_TEST_DATA_DIR,
                                 'voice.wav'),
            'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                             'voice_deg_peer.wav'),
            'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                            'voice_deg_dut.raw'),
            'channels': 1,
            'rate': 16000,
            'duration': 26.112 + VISQOL_BUFFER_LENGTH,
            'chunk_checking_duration': 26.112 + VISQOL_BUFFER_LENGTH,
            'bit_width': 16,
            'format': 'S16_LE',
            # convenient way to differentiate ViSQOL tests from regular tests
            'visqol_test': True,
            'encoding': 'signed-integer',
            'speech_mode': True,
            # Passing scored are determined mostly experimentally.
            'sink_passing_score': 4.0,
            'source_passing_score': 4.0,
            'reporting_type': 'voice-16k',

            # Device side data used by StartPlayingAudioSubprocess function in
            # bluetooth_audio.py.
            'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                        'voice.wav'),
        },
        {
            'file': os.path.join(AUDIO_TEST_DATA_DIR,
                                 'sine_7000hz_rate16000_ch1_5secs.wav'),
            'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                             'sine_7k_deg_peer.wav'),
            'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                            'sine_7k_deg_dut.raw'),
            'channels': 1,
            'rate': 16000,
            'duration': 5.0 + VISQOL_BUFFER_LENGTH,
            'chunk_checking_duration': 5.0 + VISQOL_BUFFER_LENGTH,
            'bit_width': 16,
            'format': 'S16_LE',
            # convenient way to differentiate ViSQOL tests from regular tests
            'visqol_test': True,
            'encoding': 'signed-integer',
            'speech_mode': True,
            # Passing scored are determined mostly experimentally.
            'sink_passing_score': 4.0,
            'source_passing_score': 4.0,
            'reporting_type': 'sine-7k',

            # Device side data used by StartPlayingAudioSubprocess function in
            # bluetooth_audio.py.
            'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                        'sine_7000hz_rate16000_ch1_5secs.wav'),
        }
    ]
}

# Audio test data for hfp nbs medium test.
hfp_nbs_medium_test_data = {
    'rate': 8000,
    'channels': 1,
    'frequencies': (3500,),
    'file': os.path.join(AUDIO_TEST_DIR,
                         'sine_3500hz_rate8000_ch1_60secs.raw'),
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'hfp_nbs_medium_recorded_by_peer.raw'),
    'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                    'hfp_nbs_medium_recorded_by_dut.raw'),
    'chunk_in_secs': 1,
    'bit_width': 16,
    'format': 'S16_LE',
    'duration': 60,
    'chunk_checking_duration': 5,

    # Device side data used by StartPlayingAudioSubprocess function in
    # bluetooth_audio.py.
    'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                'sine_3500hz_rate8000_ch1_60secs.wav'),
    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'hfp_nbs_medium_recorded_by_peer_%d.raw'),
}


# Audio test data for hfp wbs medium test.
hfp_wbs_medium_test_data = {
    'rate': 16000,
    'channels': 1,
    'frequencies': (7000,),
    'file': os.path.join(AUDIO_TEST_DIR,
                         'sine_7000hz_rate16000_ch1_60secs.raw'),
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'hfp_wbs_medium_recorded_by_peer.raw'),
    'recorded_by_dut': os.path.join(AUDIO_RECORD_DIR,
                                    'hfp_wbs_medium_recorded_by_dut.raw'),
    'chunk_in_secs': 1,
    'bit_width': 16,
    'format': 'S16_LE',
    'duration': 60,
    'chunk_checking_duration': 5,

    # Device side data used by StartPlayingAudioSubprocess function in
    # bluetooth_audio.py.
    'device_file': os.path.join(DEVICE_AUDIO_DATA_DIR,
                                'sine_7000hz_rate16000_ch1_60secs.wav'),
    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'hfp_wbs_medium_recorded_by_peer_%d.raw'),
}


# Audio test data for a2dp
a2dp_test_data = {
    'rate': 48000,
    'channels': 2,
    'frequencies': (440, 20000),
    'file': os.path.join(AUDIO_TEST_DIR,
                         'binaural_sine_440hz_20000hz_rate48000_%dsecs.raw'),
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'a2dp_recorded_by_peer.raw'),
    'chunk_in_secs': 5,
    'bit_width': 16,
    'format': 'S16_LE',
    'duration': 5,

    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'a2dp_recorded_by_peer_%d.raw'),
}


# Audio test data for a2dp long test. The file and duration attributes
# are dynamic and will be determined during run time.
a2dp_long_test_data = a2dp_test_data.copy()
a2dp_long_test_data.update({
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'a2dp_long_recorded_by_peer.raw'),
    'duration': 0,       # determined at run time
    'chunk_in_secs': 1,
    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'a2dp_long_recorded_by_peer_%d.raw'),
})


# Audio test data for a2dp medium test.
a2dp_medium_test_data = a2dp_test_data.copy()
a2dp_medium_test_data.update({
    'recorded_by_peer': os.path.join(AUDIO_RECORD_DIR,
                                     'a2dp_medium_recorded_by_peer.raw'),
    'duration': 60,
    'chunk_in_secs': 1,
    'chunk_checking_duration': 5,
    # Device side data used by HandleOneChunk function in bluetooth_audio.py.
    'chunk_file': os.path.join(DEVICE_AUDIO_RECORD_DIR,
                               'a2dp_medium_recorded_by_peer_%d.raw'),
})


audio_test_data = {
    A2DP: a2dp_test_data,
    A2DP_MEDIUM: a2dp_medium_test_data,
    A2DP_LONG: a2dp_long_test_data,
    HFP_WBS: hfp_wbs_test_data,
    HFP_WBS_MEDIUM: hfp_wbs_medium_test_data,
    HFP_NBS: hfp_nbs_test_data,
    HFP_NBS_MEDIUM: hfp_nbs_medium_test_data,
}
