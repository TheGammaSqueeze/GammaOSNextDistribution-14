# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Implementation of the graphics_TraceReplayExtended server test."""

from enum import Enum
import logging
import os
import threading
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server.cros.graphics import graphics_power
from autotest_lib.server.site_tests.tast import tast


class TastTestResult():
    """Stores the test result for a single Tast subtest"""

    class TestStatus(Enum):
        """Encodes all actionable Tast subtest completion statuses"""
        Passed = 1
        Skipped = 2
        Failed = 3

    def __init__(self, name, status, errors):
        self.name = name  # type: str
        self.status = status  # type: self.TestStatus
        self.errors = errors  # type: List[json-string]


class TastManagerThread(threading.Thread):
    """Thread for running a local tast test from an autotest server test."""

    def __init__(self,
                 host,
                 tast_instance,
                 client_test,
                 max_duration_minutes,
                 build_bundle,
                 varslist=None,
                 command_args=None):
        """Initializes the thread.

        Args:
            host: An autotest host instance.
            tast_instance: An instance of the tast.tast() class.
            client_test: String identifying which tast test to run.
            max_duration_minutes: Float defining the maximum running time of the
                managed sub-test.
            build_bundle: String defining which tast test bundle to build and
                query for the client_test.
            varslist: list of strings that define dynamic variables made
                available to tast tests at runtime via `tast run -var=name=value
                ...`. Each string should be formatted as 'name=value'.
            command_args: list of strings that are passed as args to the `tast
                run` command.
        """
        super(TastManagerThread, self).__init__(name=__name__)
        self.tast = tast_instance
        self.tast.initialize(
            host=host,
            test_exprs=[client_test],
            ignore_test_failures=True,
            max_run_sec=max_duration_minutes * 60,
            command_args=command_args if command_args else [],
            build_bundle=build_bundle,
            varslist=varslist)

    def run(self):
        logging.info('Started thread: %s', self.__class__.__name__)
        self.tast.run_once()

    def get_subtest_results(self):
        """Returns the status for the tast subtest managed by this class.

        Parses the Tast client tests' json-formatted result payloads to
        determine the status and associated messages for each.

        self.tast._test_results is populated with JSON objects for each test
        during self.tast.run_once(). The JSON spec is detailed at
        src/platform/tast/src/chromiumos/tast/cmd/tast/internal/run/results.go.
        """
        subtest_results = []
        for res in self.tast._test_results:
            name = res.get('name')
            skip_reason = res.get('skipReason')
            errors = res.get('errors')
            if skip_reason:
                logging.info('Tast subtest "%s" was skipped with reason: %s',
                             name, skip_reason)
                status = TastTestResult.TestStatus.Skipped
            elif errors:
                logging.info('Tast subtest "%s" failed with errors: %s', name,
                             str([err.get('reason') for err in errors]))
                status = TastTestResult.TestStatus.Failed
            else:
                logging.info('Tast subtest "%s" succeeded.', name)
                status = TastTestResult.TestStatus.Passed
            subtest_results.append(TastTestResult(name, status, errors))
        return subtest_results


class GraphicsTraceReplayExtendedBase(test.test):
    """Base Autotest server test for running repeated trace replays in a VM.

    This test simultaneously initiates system performance logging and extended
    trace replay processes on a target host, and parses their test results for
    combined analysis and reporting.
    """
    version = 1

    @staticmethod
    def _initialize_dir_on_host(host, directory):
        """Initialize a directory to a consistent (empty) state on the host.

        Args:
            host: An autotest host instance.
            directory: String defining the location of the directory to
                initialize.

        Raises:
            TestFail: If the directory cannot be initialized.
        """
        try:
            host.run('rm -r %(0)s 2>/dev/null || true; ! test -d %(0)s' %
                     {'0': directory})
            host.run('mkdir -p %s' % directory)
        except (error.AutotestHostRunCmdError, error.AutoservRunError) as err:
            logging.exception(err)
            raise error.TestFail(
                'Failed to initialize directory "%s" on the test host' %
                directory)

    @staticmethod
    def _cleanup_dir_on_host(host, directory):
        """Ensure that a directory and its contents are deleted on the host.

        Args:
            host: An autotest host instance.
            directory: String defining the location of the directory to delete.

        Raises:
            TestFail: If the directory remains on the host.
        """
        try:
            host.run('rm -r %(0)s || true; ! test -d %(0)s' % {'0': directory})
        except (error.AutotestHostRunCmdError, error.AutoservRunError) as err:
            logging.exception(err)
            raise error.TestFail(
                'Failed to cleanup directory "%s" on the test host' % directory)

    def run_once(self,
                 host,
                 client_tast_test,
                 max_duration_minutes,
                 tast_build_bundle='cros',
                 tast_varslist=None,
                 tast_command_args=None,
                 pdash_note=None):
        """Runs the test.

        Args:
            host: An autotest host instance.
            client_tast_test: String defining which tast test to run.
            max_duration_minutes: Float defining the maximum running time of the
                managed sub-test.
            tast_build_bundle: String defining which tast test bundle to build
                and query for the client_test.
            tast_varslist: list of strings that define dynamic variables made
                available to tast tests at runtime via `tast run -var=name=value
                ...`. Each string should be formatted as 'name=value'.
            tast_command_args: list of strings that are passed as args to the
                `tast run` command.
        """
        # Construct a suffix tag indicating which managing test is using logged
        # data from the graphics_Power subtest.
        trace_name = client_tast_test.split('.')[-1]

        # workaround for running test locally since crrev/c/2374267 and
        # crrev/i/2374267
        if not tast_command_args:
            tast_command_args = []
        tast_command_args.extend([
                'extraallowedbuckets=termina-component-testing,cros-containers-staging'
        ])

        # Define paths of signal files for basic RPC/IPC between sub-tests.
        temp_io_root = '/tmp/%s/' % self.__class__.__name__
        result_dir = os.path.join(temp_io_root, 'results')
        signal_running_file = os.path.join(temp_io_root, 'signal_running')
        signal_checkpoint_file = os.path.join(temp_io_root, 'signal_checkpoint')

        # This test is responsible for creating/deleting root and resultdir.
        logging.debug('Creating temporary IPC/RPC dir: %s', temp_io_root)
        self._initialize_dir_on_host(host, temp_io_root)
        self._initialize_dir_on_host(host, result_dir)

        # Start background system performance monitoring process on the test
        # target (via an autotest client 'power_Test').
        logging.debug('Connecting to autotest client on host')
        graphics_power_thread = graphics_power.GraphicsPowerThread(
            host=host,
            max_duration_minutes=max_duration_minutes,
            test_tag='Trace' + '.' + trace_name,
            pdash_note=pdash_note,
            result_dir=result_dir,
            signal_running_file=signal_running_file,
            signal_checkpoint_file=signal_checkpoint_file)
        graphics_power_thread.start()

        logging.info('Waiting for graphics_Power subtest to initialize...')
        try:
            graphics_power_thread.wait_until_running(timeout=10 * 60)
        except Exception as err:
            logging.exception(err)
            raise error.TestFail(
                'An error occured during graphics_Power subtest initialization')
        logging.info('The graphics_Power subtest was properly initialized')

        # Start repeated trace replay process on the test target (via a tast
        # local test).
        logging.info('Running Tast test: %s', client_tast_test)
        tast_outputdir = os.path.join(self.outputdir, 'tast')
        if not os.path.exists(tast_outputdir):
            logging.debug('Creating tast outputdir: %s', tast_outputdir)
            os.makedirs(tast_outputdir)

        if not tast_varslist:
            tast_varslist = []
        tast_varslist.extend([
            'PowerTest.resultDir=' + result_dir,
            'PowerTest.signalRunningFile=' + signal_running_file,
            'PowerTest.signalCheckpointFile=' + signal_checkpoint_file,
        ])

        tast_instance = tast.tast(
            job=self.job, bindir=self.bindir, outputdir=tast_outputdir)
        tast_manager_thread = TastManagerThread(
            host,
            tast_instance,
            client_tast_test,
            max_duration_minutes,
            tast_build_bundle,
            varslist=tast_varslist,
            command_args=tast_command_args)
        tast_manager_thread.start()

        # Block until both subtests finish.
        threads = [graphics_power_thread, tast_manager_thread]
        stop_attempts = 0
        while threads:
            # TODO(ryanneph): Move stop signal emission to tast test instance.
            if (not tast_manager_thread.is_alive() and
                    graphics_power_thread.is_alive() and stop_attempts < 1):
                logging.info('Attempting to stop graphics_Power thread')
                graphics_power_thread.stop(timeout=0)
                stop_attempts += 1

            # Raise test failure if graphics_Power thread ends before tast test.
            if (not graphics_power_thread.is_alive() and
                    tast_manager_thread.is_alive()):
                raise error.TestFail(
                    'The graphics_Power subtest ended too soon.')

            for thread in list(threads):
                if not thread.is_alive():
                    logging.info('Thread "%s" has ended',
                                 thread.__class__.__name__)
                    threads.remove(thread)
            time.sleep(1)

        # Aggregate subtest results and report overall test result
        subtest_results = tast_manager_thread.get_subtest_results()
        num_failed_subtests = 0
        for res in subtest_results:
            num_failed_subtests += int(
                res.status == TastTestResult.TestStatus.Failed)
        if num_failed_subtests:
            raise error.TestFail('%d of %d Tast subtests have failed.' %
                                 (num_failed_subtests, len(subtest_results)))
        elif all([res.status == TastTestResult.TestStatus.Skipped
                  for res in subtest_results]):
            raise error.TestNAError('All %d Tast subtests have been skipped' %
                                    len(subtest_results))

        client_result_dir = os.path.join(self.outputdir, 'client_results')
        logging.info('Saving client results to %s', client_result_dir)
        host.get_file(result_dir, client_result_dir)

        # Ensure the host filesystem is clean for the next test.
        self._cleanup_dir_on_host(host, result_dir)
        self._cleanup_dir_on_host(host, temp_io_root)

        # TODO(ryanneph): Implement results parsing/analysis/reporting
