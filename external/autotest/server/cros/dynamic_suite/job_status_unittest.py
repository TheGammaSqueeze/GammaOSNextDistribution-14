#!/usr/bin/env python3
#
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# pylint: disable-msg=C0111

"""Unit tests for server/cros/dynamic_suite/job_status.py."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os
import shutil
from six.moves import map
from six.moves import range
import tempfile
import unittest
from unittest import mock
from unittest.mock import patch

import common

from autotest_lib.server import frontend
from autotest_lib.server.cros.dynamic_suite import job_status
from autotest_lib.server.cros.dynamic_suite.fakes import FakeJob
from autotest_lib.server.cros.dynamic_suite.fakes import FakeStatus


DEFAULT_WAITTIMEOUT_MINS = 60 * 4


class StatusTest(unittest.TestCase):
    """Unit tests for job_status.Status.
    """


    def setUp(self):
        super(StatusTest, self).setUp()
        afe_patcher = patch.object(frontend, 'AFE')
        self.afe = afe_patcher.start()
        self.addCleanup(afe_patcher.stop)
        tko_patcher = patch.object(frontend, 'TKO')
        self.tko = tko_patcher.start()
        self.addCleanup(tko_patcher.stop)
        self.tmpdir = tempfile.mkdtemp(suffix=type(self).__name__)
        # These are called a few times, so we need to return via side_effect.
        # for some reason side_effect doesn't like appending, so just keeping
        # a list to then be added at once.
        self.tko.get_job_test_statuses_from_db.side_effect = []
        self.afe.run.side_effect = []
        self.run_list = []
        self.run_call_list = []
        self.job_statuses = []
        self.job_statuses_call_list = []

    def tearDown(self):
        super(StatusTest, self).tearDown()
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def expect_yield_job_entries(self, job):
        entries = [s.entry for s in job.statuses]
        self.run_list.append(entries)
        self.run_call_list.append(
                mock.call('get_host_queue_entries', job=job.id))

        if True not in ['aborted' in e and e['aborted'] for e in entries]:
            self.job_statuses.append(job.statuses)
            self.job_statuses_call_list.append(mock.call(job.id))

    @patch('autotest_lib.server.cros.dynamic_suite.job_status.JobResultWaiter._sleep'
           )
    def testJobResultWaiter(self, mock_sleep):
        """Should gather status and return records for job summaries."""
        jobs = [FakeJob(0, [FakeStatus('GOOD', 'T0', ''),
                            FakeStatus('GOOD', 'T1', '')]),
                FakeJob(1, [FakeStatus('ERROR', 'T0', 'err', False),
                            FakeStatus('GOOD', 'T1', '')]),
                FakeJob(2, [FakeStatus('TEST_NA', 'T0', 'no')]),
                FakeJob(3, [FakeStatus('FAIL', 'T0', 'broken')]),
                FakeJob(4, [FakeStatus('ERROR', 'SERVER_JOB', 'server error'),
                            FakeStatus('GOOD', 'T0', '')]),]
        # TODO: Write a better test for the case where we yield
        # results for aborts vs cannot yield results because of
        # a premature abort. Currently almost all client aborts
        # have been converted to failures, and when aborts do happen
        # they result in server job failures for which we always
        # want results.
        # FakeJob(5, [FakeStatus('ERROR', 'T0', 'gah', True)]),
        # The next job shouldn't be recorded in the results.
        # FakeJob(6, [FakeStatus('GOOD', 'SERVER_JOB', '')])]
        for status in jobs[4].statuses:
            status.entry['job'] = {'name': 'broken_infra_job'}

        job_id_set = set([job.id for job in jobs])
        yield_values = [
                [jobs[1]],
                [jobs[0], jobs[2]],
                jobs[3:6]
            ]

        yield_list = []
        called_list = []

        for yield_this in yield_values:
            yield_list.append(yield_this)

            # Expected list of calls...
            called_list.append(
                    mock.call(id__in=list(job_id_set), finished=True))
            for job in yield_this:
                self.expect_yield_job_entries(job)
                job_id_set.remove(job.id)
        self.afe.get_jobs.side_effect = yield_list
        self.afe.run.side_effect = self.run_list
        self.tko.get_job_test_statuses_from_db.side_effect = self.job_statuses

        waiter = job_status.JobResultWaiter(self.afe, self.tko)
        waiter.add_jobs(jobs)
        results = [result for result in waiter.wait_for_results()]
        for job in jobs[:6]:  # the 'GOOD' SERVER_JOB shouldn't be there.
            for status in job.statuses:
                self.assertTrue(True in list(map(status.equals_record, results)))

        self.afe.get_jobs.assert_has_calls(called_list)
        self.afe.run.assert_has_calls(self.run_call_list)
        self.tko.get_job_test_statuses_from_db.assert_has_calls(
                self.job_statuses_call_list)

    def testYieldSubdir(self):
        """Make sure subdir are properly set for test and non-test status."""
        job_tag = '0-owner/172.33.44.55'
        job_name = 'broken_infra_job'
        job = FakeJob(0, [FakeStatus('ERROR', 'SERVER_JOB', 'server error',
                                     subdir='---', job_tag=job_tag),
                          FakeStatus('GOOD', 'T0', '',
                                     subdir='T0.subdir', job_tag=job_tag)],
                      parent_job_id=54321)
        for status in job.statuses:
            status.entry['job'] = {'name': job_name}

        self.expect_yield_job_entries(job)
        self.afe.run.side_effect = self.run_list
        self.tko.get_job_test_statuses_from_db.side_effect = self.job_statuses
        results = list(job_status._yield_job_results(self.afe, self.tko, job))
        for i in range(len(results)):
            result = results[i]
            if result.test_name.endswith('SERVER_JOB'):
                expected_name = '%s_%s' % (job_name, job.statuses[i].test_name)
                expected_subdir = job_tag
            else:
                expected_name = job.statuses[i].test_name
                expected_subdir = os.path.join(job_tag, job.statuses[i].subdir)
            self.assertEqual(results[i].test_name, expected_name)
            self.assertEqual(results[i].subdir, expected_subdir)

        self.afe.run.assert_has_calls(self.run_call_list)
        self.tko.get_job_test_statuses_from_db.assert_has_calls(
                self.job_statuses_call_list)


if __name__ == '__main__':
    unittest.main()
