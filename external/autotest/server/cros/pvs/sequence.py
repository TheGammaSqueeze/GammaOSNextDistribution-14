# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import shutil

from autotest_lib.server import autotest, test
from autotest_lib.client.common_lib import error


class test_sequence(test.test):
    """
    test_sequence extends the base test implementation to allow for
    encapsulating a series of (client or server) tests which must
    be run in a given sequence.
    """

    def initialize(self, sequence):
        """
        initialize implements the initialize call in test.test, is called before
        execution of the test

        @param sequence: the sequence of tests constructed in the wrapper
        @param sequence_verdicts: verdicts from each executed test in the
                sequence. Passed by reference and used by the caller to
                annotate results.
        """
        self._sequenced_tests = sequence
        self._sequence_verdicts = {}
        self._results_path = self.job._server_offload_dir_path()
        self._wrapper_results_dir = os.path.join(self._results_path,
                                                 self.tagged_testname)

    def process_test_results_post_hook(self):
        """
        process_test_results is used as a post_run_hook to record results to the
        status.log file following the execution of the run. For tests that were
        completed (i.e. no exceptions occurred to end the sequence), results are
        moved to the top level from the child results directory
        """
        for test, args, server_test in self._sequenced_tests:
            if test not in self._sequence_verdicts:
                continue

            if server_test:
                self.surface_server_test_resultsdir(test)
            else:
                self.surface_client_test_resultsdir(test)
            annotated_testname = self.tagged_testname + "." + test
            self.job.record('START', None, annotated_testname)
            self.job.record('INFO', None, annotated_testname)
            if self._sequence_verdicts[test]:
                self.job.record('END GOOD', None, annotated_testname, "")
            else:
                self.job.record('END FAIL', None, annotated_testname, "")

    def execute_sequenced_test(self, client, test, argv, server_test):
        """
        execute_sequenced_test runs a single test from the sequence with the
        given argument vector

        @param test: test name (url) to run
        @param argv: argument dictionary to run the test with

        @raises error.TestFail: on failure of the wrapped tests
        """
        try:
            self._sequence_verdicts[test] = True
            if server_test:
                err = self.job.run_test(test, **argv)
                if err == False:
                    raise error.TestFail()
            else:
                client.run_test(test, check_client_result=True, **argv)
        except:
            self._sequence_verdicts[test] = False
            self.postprocess()
            raise error.TestFail('Sequenced test %s failed, reason: ' % test)

    def surface_client_test_resultsdir(self, test):
        """
        surface_client_test_resultsdir retrieves the child test results from a
        sequenced client job

        @param test: the child test name to grab results from
        """
        wrapped_test_results_path = os.path.join(self._wrapper_results_dir,
                                                 test)
        tagged_destination = os.path.join(self._results_path,
                                          self.tagged_testname + "." + test)
        shutil.move(wrapped_test_results_path, tagged_destination)

    def surface_server_test_resultsdir(self, test):
        """
        surface_server_test_resultsdir renames the server test results from a sequenced child

        @param test: the child test name to grab results from
        """
        wrapped_test_results_path = os.path.join(self._results_path, test)
        tagged_destination = os.path.join(self._results_path,
                                          self.tagged_testname + "." + test)
        shutil.move(wrapped_test_results_path, tagged_destination)

    def run_once(self, host=None):
        """
        run_once implements the run_once call in test.test, is called to begin
        execution of the test

        @param host: host from control file with which to run the test
        """
        client_at = autotest.Autotest(host)
        for test, argv, server_test in self._sequenced_tests:
            self.execute_sequenced_test(client_at, test, argv, server_test)

    def postprocess(self):
        """
        postprocess is the post routine for test.test. We must add our post_hook
        in this function (as opposed to the initialize call) because if added in
        initialize, this will be called after each child server test as well as
        at the end of the function
        """
        self.job.add_post_run_hook(self.process_test_results_post_hook)
