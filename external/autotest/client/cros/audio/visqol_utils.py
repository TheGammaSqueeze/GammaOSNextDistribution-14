# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re
import subprocess

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.bluetooth.bluetooth_audio_test_data import (
        VISQOL_PATH, VISQOL_SIMILARITY_MODEL)


def parse_visqol_output(stdout, stderr, log_dir):
    """
    Parses stdout and stderr string from VISQOL output and parse into
    a float score.

    On error, stderr will contain the error message, otherwise will be None.
    On success, stdout will be a string, first line will be
    VISQOL version, followed by indication of speech mode. Followed by
    paths to reference and degraded file, and a float MOS-LQO score, which
    is what we're interested in. Followed by more detailed charts about
    specific scoring by segments of the files. Stdout is None on error.

    @param stdout: The stdout bytes from commandline output of VISQOL.
    @param stderr: The stderr bytes from commandline output of VISQOL.
    @param log_dir: Directory path for storing VISQOL log.

    @returns: A tuple of a float score and string representation of the
            srderr or None if there was no error.
    """
    stdout = '' if stdout is None else stdout.decode('utf-8')
    stderr = '' if stderr is None else stderr.decode('utf-8')

    # Log verbose VISQOL output:
    log_file = os.path.join(log_dir, 'VISQOL_LOG.txt')
    with open(log_file, 'a+') as f:
        f.write('String Error:\n{}\n'.format(stderr))
        f.write('String Out:\n{}\n'.format(stdout))

    # pattern matches first float or int after 'MOS-LQO:' in stdout,
    # e.g. it would match the line 'MOS-LQO       2.3' in the stdout
    score_pattern = re.compile(r'.*MOS-LQO:\s*(\d+.?\d*)')
    score_search = re.search(score_pattern, stdout)

    # re.search returns None if no pattern match found, otherwise the score
    # would be in the match object's group 1 matches just the float score
    score = float(score_search.group(1)) if score_search else -1.0
    return stderr, score


def get_visqol_score(ref_file,
                     deg_file,
                     log_dir,
                     speech_mode=True,
                     verbose=True):
    """
    Runs VISQOL using the subprocess library on the provided reference file
    and degraded file and returns the VISQOL score.

    Notes that the difference between the duration of reference and degraded
    audio must be smaller than 1.0 second.

    @param ref_file: File path to the reference wav file.
    @param deg_file: File path to the degraded wav file.
    @param log_dir: Directory path for storing VISQOL log.
    @param speech_mode: [Optional] Defaults to True, accepts 16k sample
            rate files and ignores frequencies > 8kHz for scoring.
    @param verbose: [Optional] Defaults to True, outputs more details.

    @returns: A float score for the tested file.
    """
    visqol_cmd = [VISQOL_PATH]
    visqol_cmd += ['--reference_file', ref_file]
    visqol_cmd += ['--degraded_file', deg_file]
    visqol_cmd += ['--similarity_to_quality_model', VISQOL_SIMILARITY_MODEL]

    if speech_mode:
        visqol_cmd.append('--use_speech_mode')
    if verbose:
        visqol_cmd.append('--verbose')

    visqol_process = subprocess.Popen(visqol_cmd,
                                      stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE)
    stdout, stderr = visqol_process.communicate()

    err, score = parse_visqol_output(stdout, stderr, log_dir)

    if err:
        raise error.TestError(err)
    elif score < 0.0:
        raise error.TestError('Failed to parse score, got {}'.format(score))

    return score
