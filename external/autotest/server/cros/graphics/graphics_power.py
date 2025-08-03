# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Provides a management class for using graphics_Power in server tests."""

import logging
import os
import tempfile
import threading
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest

ROOT_DIR = '/tmp/graphics_Power/'
DEFAULT_SIGNAL_RUNNING_FILE = os.path.join(ROOT_DIR, 'signal_running')
DEFAULT_SIGNAL_CHECKPOINT_FILE = os.path.join(ROOT_DIR, 'signal_checkpoint')


class GraphicsPowerThread(threading.Thread):
    """Thread for running the graphics_Power client test.

    Provides a threaded management interface for the graphics_Power subtest.
    This class can be used from an autotest server test to log system
    performance metrics in the background on the test host.
    """

    class Error(Exception):
        """Base error that can be inherited to define more specific errors."""
        pass

    class ThreadNotInitializedError(Error):
        """An error indicating that the thread was not properly initialized."""
        pass

    class InitTimeoutError(Error):
        """An error indicating that a timeout occurred during a blocking call."""
        pass

    def __init__(self,
                 host,
                 max_duration_minutes,
                 sample_rate_seconds=1,
                 test_tag=None,
                 pdash_note=None,
                 result_dir=None,
                 signal_running_file=DEFAULT_SIGNAL_RUNNING_FILE,
                 signal_checkpoint_file=DEFAULT_SIGNAL_CHECKPOINT_FILE):
        """Initializes the thread.

        Args:
            host: An autotest host instance.
            max_duration_minutes: Float defining the maximum running time of the
                managed sub-test.
            sample_rate_seconds: Optional; Number defining seconds between data
                point acquisition.
            test_tag: Optional; String describing the test that initiated this
                monitoring process; appended to the true test name.
            pdash_note: Optional; A tag that is included as a filter field on
                the ChromeOS power-dashboard.
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
        super(GraphicsPowerThread, self).__init__(name=__name__)
        self._running = False
        self._autotest_client = autotest.Autotest(host)
        self._host = host
        self._test_thread = None

        self.max_duration_minutes = max_duration_minutes
        self.sample_rate_seconds = sample_rate_seconds
        self.test_tag = test_tag
        self.pdash_note = pdash_note
        self.result_dir = result_dir
        self.signal_running_file = signal_running_file
        self.signal_checkpoint_file = signal_checkpoint_file

    def is_running(self):
        """Return a bool indicating the 'running' state of the subtest.

        This check can be used to ensure system logging is initialized and
        running before beginning other subtests.
        """
        try:
            self._host.run('test -f %s' % self.signal_running_file)
            return True
        except (error.AutotestHostRunCmdError, error.AutoservRunError):
            return False

    def wait_until_running(self, timeout=120):
        """Block execution until the subtest reports it is logging properly.

        Args:
            timeout: Optional; Float that defines how long to block before
                timeout occurs. If timeout=None, then block forever

        Raises:
            RuntimeError: The subtest ended unexpectedly before initialization
                finished.
            GraphicsPowerThread.ThreadNotInitializedError: The thread hasn't
                been started by the managing server test yet.
            GraphicsPowerThread.InitTimeoutError: A timeout occurred while
                waiting for subtest to report itself as running.
        """
        if timeout:
            time_start = time.time()
            time_end = time_start + timeout
        while True:
            if timeout and time.time() >= time_end:
                self.stop()
                raise self.InitTimeoutError(
                    'The graphics_Power subtest initialization timed out after'
                    ' %d second(s).' % timeout)
            if not self.is_alive():
                raise RuntimeError(
                    'The graphics_Power subtest failed to initialize')
            if self.is_running():
                break
            time.sleep(1)

        if not self._test_thread:
            raise self.ThreadNotInitializedError

    def stop(self, timeout=None):
        """Gracefully stop the subtest on the test host.

        If timeout is None, then this is a blocking call that waits forever.
        If timeout is a positive number, then it waits for 'timeout' seconds.
        If timeout is 0, then it returns immediately.

        Args:
            timeout: Time (seconds) before giving up on joining the thread.

        Returns:
            A bool indicating if thread was stopped.
        """
        self._running = False
        self.join(timeout)
        return not self.is_alive()

    def checkpoint_measurements(self, name, start_time=None):
        """Save the current log buffers with an associated name.

        The power-dashboard displays time series data in one or more
        checkpoints that can be used to annotate different phases of a test.

        By saving a checkpoint, the time series data collected since the end of
        the most recently committed checkpoint (or the test start if no
        checkpoints are saved yet) is annotated on the power-dashboard with the
        specified name. The checkpoint start time can be adjusted with the
        optional 'start_time' argument.

        Args:
            name: String defining the saved checkpoint's name.
            start_time: Optional; Float indicating the time (in seconds since
                the epoch) at which this checkpoint should actually start. This
                functionally discards data from the beginning of the logged
                duration until start_time.
        """
        with tempfile.NamedTemporaryFile('w') as tf:
            tf.write(str(name) + '\n')
            if start_time:
                tf.write(str(start_time))
            tf.flush()
            self._host.send_file(tf.name, self.signal_checkpoint_file)

    def _run_test_async(self):
        self._autotest_client.run_test(
            'graphics_Power',
            tag=self.test_tag,
            max_duration_minutes=self.max_duration_minutes,
            sample_rate_seconds=self.sample_rate_seconds,
            pdash_note=self.pdash_note,
            result_dir=self.result_dir,
            signal_running_file=self.signal_running_file,
            signal_checkpoint_file=self.signal_checkpoint_file)

    def run(self):
        self._running = True
        self._test_thread = threading.Thread(target=self._run_test_async)
        self._test_thread.start()
        logging.info('Started thread: %s', self.__class__.__name__)

        def send_stop_signal_and_join():
            """Emits a stop signal to the test host and joins the thread.

            Deletes a monitored file on the test host over ssh and waits for
            the graphics_Power sub-test to end gracefully as a consequence.
            """
            while True:
                self._host.run('rm %s 2>/dev/null || true' %
                               self.signal_running_file)
                self._test_thread.join(5)
                if not self._test_thread.is_alive():
                    break

        while True:
            time.sleep(1)
            if not self._test_thread.is_alive():
                logging.debug('The graphics_Power subtest ended')
                break
            elif not self._running:
                logging.debug(
                    'Sending stop signal to the graphics_Power subtest')
                send_stop_signal_and_join()
                break
