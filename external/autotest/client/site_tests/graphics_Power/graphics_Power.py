# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Client test for logging system performance metrics."""

from collections import namedtuple
import logging
import os
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.power import power_test

ROOT_DIR = '/tmp/graphics_Power/'
DEFAULT_SIGNAL_RUNNING_FILE = os.path.join(ROOT_DIR, 'signal_running')
DEFAULT_SIGNAL_CHECKPOINT_FILE = os.path.join(ROOT_DIR, 'signal_checkpoint')


def remove_file_if_exists(f):
    """Attempt to delete the file only if it exists."""
    if os.path.exists(f):
        os.remove(f)


class MonitoredFile():
    """Watches a file and supports querying changes to its status.

    Tracks a file's current and previous status based on its modified time and
    existence. Provides convenience functions that test for the occurrence of
    various changes, such as file creation, deletion, and modification.
    """

    MonitoredFileStatus = namedtuple('monitored_file_status',
                                     ('exists', 'mtime'))

    def __init__(self, filename):
        self.filename = filename
        self._prev_status = self._get_file_status()
        self._curr_status = self._prev_status

    def _get_file_status(self):
        exists = os.path.exists(self.filename)
        if exists:
            mtime = os.path.getmtime(self.filename)
        else:
            mtime = None

        return self.MonitoredFileStatus(exists=exists, mtime=mtime)

    def update(self):
        """Check file to update its status.

        This should be called once per an event loop iteration.
        """
        self._prev_status = self._curr_status
        self._curr_status = self._get_file_status()

    def exists(self):
        """Tests if the file exists."""
        return self._curr_status.exists

    def deleted(self):
        """Tests that the file was just deleted"""
        return not self._curr_status.exists and self._prev_status.exists

    def created(self):
        """Tests that the file was just created"""
        return self._curr_status.exists and not self._prev_status.exists

    def modified(self):
        """Tests that the file was just modified"""
        return (self.deleted() or self.created() or
                self._prev_status.mtime != self._curr_status.mtime)


class graphics_Power(power_test.power_Test):
    """Wrapper around power_Test client test for use in server tests.

    Wraps the client power_Test for acquiring system metrics related to graphics
    rendering performance (temperature, clock freqs, power states).

    This class should only be instantiated from a server test. For background
    logging, see
    <autotest_lib.server.cros.graphics.graphics_power.GraphicsPowerThread()>
    """
    version = 1

    def __init__(self, *args, **kwargs):
        super(graphics_Power, self).__init__(*args, **kwargs)
        self._last_checkpoint_time = None

    def initialize(self, sample_rate_seconds=1, pdash_note=''):
        """Setup power_Test base class.

        Args:
            sample_rate_seconds: Optional; Number defining seconds between data
                point acquisition.
            pdash_note: Optional; A tag that is included as a filter field on
                the ChromeOS power-dashboard.
        """
        super(graphics_Power, self).initialize(
            seconds_period=sample_rate_seconds,
            pdash_note=pdash_note,
            force_discharge=False)

    @staticmethod
    def _read_checkpoint_file(filename):
        """Parses checkpoint signal file and returns name and start_time.

        Args:
            filename: String path to the checkpoint file to be read.

        Returns:
            A 2-tuple: (name, start_time) containing a checkpoint name (string)
            and the checkpoint's start time (float; seconds since the epoch).

            If the start time is not provided in the checkpoint file, start_time
            is equal to None.
        """
        with open(filename, 'r') as f:
            name = f.readline().rstrip('\n')
            if not name:
                name = None

            start_time = f.readline().rstrip('\n')
            if start_time:
                start_time = float(start_time)
            else:
                start_time = None
        return name, start_time

    def checkpoint_measurements(self, name, start_time=None):
        """Save a power_Test measurement checkpoint.

        Wraps power_Test.checkpoint_measurements to change behavior of default
        start_time to continue from the end of the previous checkpoint, rather
        than from the test start time.

        Args:
            name: String defining the saved checkpoint's name.
            start_time: Optional; Float indicating the time (in seconds since
                the epoch) at which this checkpoint should actually start. This
                functionally discards data from the beginning of the logged
                duration until start_time.
        """
        # The default start_time is the test start time, but we want checkpoints
        # to start from the end of the previous one.
        if not start_time:
            start_time = self._last_checkpoint_time
        logging.debug('Saving measurements checkpoint "%s" with start time %f',
                      name, start_time)
        super(graphics_Power, self).checkpoint_measurements(name, start_time)
        self._last_checkpoint_time = time.time()

    def run_once(self,
                 max_duration_minutes,
                 result_dir=None,
                 signal_running_file=DEFAULT_SIGNAL_RUNNING_FILE,
                 signal_checkpoint_file=DEFAULT_SIGNAL_CHECKPOINT_FILE):
        """Run system performance loggers until stopped or timeout occurs.

        Temporal data logs are written to
        <test_results>/{power,cpu,temp,fan_rpm}_results_<timestamp>_raw.txt

        If result_dir points to a valid filesystem path, post-processing of logs
        will be performed and a more convenient temporal format will be saved in
        the result_dir.

        Args:
            max_duration_minutes: Number defining the maximum running time of
                the managed sub-test.
            result_dir: Optional; String defining the location on the test
                target where post-processed results from this sub-test should be
                saved for retrieval by the managing test process. Set to None if
                results output is not be created.
            signal_running_file: Optional; String defining the location of the
                'running' RPC flag file on the test target. Removal of this file
                triggers the subtest to finish logging and stop gracefully.
            signal_checkpoint_file: Optional; String defining the location of
                the 'checkpoint' RPC flag file on the test target. Modifying
                this file triggers the subtest to create a checkpoint with name
                equal to the utf-8-encoded contents of the first-line and
                optional alternative start time (in seconds since the epoch)
                equal to the second line of the file.
        """
        # Initiailize test state
        for f in (signal_running_file, signal_checkpoint_file):
            remove_file_if_exists(f)

        # Indicate 'running' state by touching a mutually-monitored file
        try:
            open(signal_running_file, 'w').close()
            if not os.path.exists(signal_running_file):
                raise RuntimeError(
                    'Signal "running" file %s was not properly initiailized' %
                    signal_running_file)
        except:
            logging.exception('Failed to set "running" state.')
            raise

        signal_running = MonitoredFile(signal_running_file)
        logging.info('Monitoring "running" signal file: %s',
                     signal_running_file)
        signal_checkpoint = MonitoredFile(signal_checkpoint_file)
        logging.info('Monitoring "checkpoint" signal file: %s',
                     signal_checkpoint_file)

        self.start_measurements()  # provided by power_Test class
        time_start = time.time()
        time_end = time_start + max_duration_minutes * 60.0
        self._last_checkpoint_time = time_start
        monitored_files = [signal_running, signal_checkpoint]
        while time.time() < time_end:
            for f in monitored_files:
                f.update()

            if signal_checkpoint.exists() and signal_checkpoint.modified():
                try:
                    checkpoint_name, checkpoint_start_time = \
                    self._read_checkpoint_file(signal_checkpoint_file)
                except ValueError as err:
                    logging.exception(err)
                    raise error.TestFail(
                        'Error while converting the checkpoint start time '
                        'string to a float.' % signal_checkpoint_file)
                self.checkpoint_measurements(checkpoint_name,
                                             checkpoint_start_time)

            if signal_running.deleted():
                logging.info('Signaled to stop by the managing test process')
                break

            time.sleep(1)

        self.checkpoint_measurements('default')

        # Rely on managing test to create/cleanup result_dir
        if result_dir:
            # TODO(ryanneph): Implement structured log output for raw power_Test
            # log files
            with open(
                    os.path.join(result_dir, 'graphics_Power_test_output.txt'),
                    'w') as f:
                f.write('{}\n')

        # Cleanup our test state from the filesystem.
        for f in (signal_running_file, signal_checkpoint_file):
            remove_file_if_exists(f)
