#!/usr/bin/python3
#
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Unit tests for server/cros/dynamic_suite/dynamic_suite.py."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import collections
from collections import OrderedDict
import os
import six
from six.moves import range
from six.moves import zip
import shutil
import tempfile
import unittest
from unittest.mock import patch, call, ANY

import common

from autotest_lib.client.common_lib import base_job
from autotest_lib.client.common_lib import control_data
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import priorities
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server import frontend
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import control_file_getter
from autotest_lib.server.cros.dynamic_suite import constants
from autotest_lib.server.cros.dynamic_suite import job_status
from autotest_lib.server.cros.dynamic_suite import suite as SuiteBase
from autotest_lib.server.cros.dynamic_suite import suite_common
from autotest_lib.server.cros.dynamic_suite.comparators import StatusContains
from autotest_lib.server.cros.dynamic_suite.fakes import FakeControlData
from autotest_lib.server.cros.dynamic_suite.fakes import FakeJob
from autotest_lib.server.cros.dynamic_suite.fakes import FakeMultiprocessingPool
from autotest_lib.server.cros.dynamic_suite.suite import RetryHandler
from autotest_lib.server.cros.dynamic_suite.suite import Suite

from unittest.mock import MagicMock


class TypeMatcher(object):
    """Matcher for object is of type."""

    def __init__(self, expected_type):
        self.expected_type = expected_type

    def __eq__(self, other):
        return isinstance(other, self.expected_type)


class SuiteTest(unittest.TestCase):
    """Unit tests for dynamic_suite Suite class.

    @var _BUILDS: fake build
    @var _TAG: fake suite tag
    """

    _BOARD = 'board:board'
    _BUILDS = {provision.CROS_VERSION_PREFIX:'build_1',
               provision.FW_RW_VERSION_PREFIX:'fwrw_build_1'}
    _TAG = 'au'
    _ATTR = {'attr:attr'}
    _DEVSERVER_HOST = 'http://dontcare:8080'
    _FAKE_JOB_ID = 10

    def setUp(self):
        """Setup."""
        super(SuiteTest, self).setUp()
        self.maxDiff = None
        self.use_batch = suite_common.ENABLE_CONTROLS_IN_BATCH
        suite_common.ENABLE_CONTROLS_IN_BATCH = False
        afe_patcher = patch.object(frontend, 'AFE')
        self.afe = afe_patcher.start()
        self.addCleanup(afe_patcher.stop)
        tko_patcher = patch.object(frontend, 'TKO')
        self.tko = tko_patcher.start()
        self.addCleanup(tko_patcher.stop)

        self.tmpdir = tempfile.mkdtemp(suffix=type(self).__name__)

        getter_patch = patch.object(control_file_getter, 'ControlFileGetter')
        self.getter = getter_patch.start()
        self.addCleanup(getter_patch.stop)

        self.devserver = dev_server.ImageServer(self._DEVSERVER_HOST)

        self.files = OrderedDict(
                [('one', FakeControlData(self._TAG, self._ATTR, 'data_one',
                                         'FAST', job_retries=None)),
                 ('two', FakeControlData(self._TAG, self._ATTR, 'data_two',
                                         'SHORT', dependencies=['feta'])),
                 ('three', FakeControlData(self._TAG, self._ATTR, 'data_three',
                                           'MEDIUM')),
                 ('four', FakeControlData('other', self._ATTR, 'data_four',
                                          'LONG', dependencies=['arugula'])),
                 ('five', FakeControlData(self._TAG, {'other'}, 'data_five',
                                          'LONG', dependencies=['arugula',
                                                                'caligula'])),
                 ('six', FakeControlData(self._TAG, self._ATTR, 'data_six',
                                         'LENGTHY')),
                 ('seven', FakeControlData(self._TAG, self._ATTR, 'data_seven',
                                           'FAST', job_retries=1))])

        self.files_to_filter = {
            'with/deps/...': FakeControlData(self._TAG, self._ATTR,
                                             'gets filtered'),
            'with/profilers/...': FakeControlData(self._TAG, self._ATTR,
                                                  'gets filtered')}


    def additional_mocking(self):
        patcher = patch.object(control_data, 'parse_control_string')
        self.cf_getter_string = patcher.start()
        self.addCleanup(patcher.stop)

    def tearDown(self):
        """Teardown."""
        suite_common.ENABLE_CONTROLS_IN_BATCH = self.use_batch
        super(SuiteTest, self).tearDown()
        shutil.rmtree(self.tmpdir, ignore_errors=True)


    def expect_control_file_parsing(self, suite_name=_TAG):
        """Expect an attempt to parse the 'control files' in |self.files|.

        @param suite_name: The suite name to parse control files for.
        """
        all_files = list(self.files.keys()) + list(self.files_to_filter.keys())
        self._set_control_file_parsing_expectations(False, all_files,
                                                    self.files, suite_name)


    def _set_control_file_parsing_expectations(self, already_stubbed,
                                               file_list, files_to_parse,
                                               suite_name):
        """Expect an attempt to parse the 'control files' in |files|.

        @param already_stubbed: parse_control_string already stubbed out.
        @param file_list: the files the dev server returns
        @param files_to_parse: the {'name': FakeControlData} dict of files we
                               expect to get parsed.
        """
        if not already_stubbed:
            self.additional_mocking()
        patcher = patch.object(suite_common.multiprocessing, 'Pool')
        self.mp_pool = patcher.start()
        self.addCleanup(patcher.stop)
        self.mp_pool.return_value = FakeMultiprocessingPool()

        self.getter.get_control_file_list.return_value = file_list
        get_control_file_contents_mock_list = []
        parse_mock_list = []
        for file, data in six.iteritems(files_to_parse):
            get_control_file_contents_mock_list.append(data.string)
            parse_mock_list.append(data)
        self.getter.get_control_file_contents.side_effect = get_control_file_contents_mock_list
        self.cf_getter_string.side_effect = parse_mock_list


    def expect_control_file_parsing_in_batch(self, suite_name=_TAG):
        """Expect an attempt to parse the contents of all control files in
        |self.files| and |self.files_to_filter|, form them to a dict.

        @param suite_name: The suite name to parse control files for.
        """
        DevServerGetter_patch = patch.object(control_file_getter,
                                             'DevServerGetter')
        self.getter = DevServerGetter_patch.start()
        self.addCleanup(DevServerGetter_patch.stop)

        patcher = patch.object(suite_common.multiprocessing, 'Pool')
        mp_pool = patcher.start()
        self.addCleanup(patcher.stop)
        mp_pool.return_value = FakeMultiprocessingPool()

        suite_info = {}
        call_list = []
        expected_calls = []
        for k, v in six.iteritems(self.files):
            suite_info[k] = v.string
            call_list.append(v)
            expected_calls.append(call(v.string, raise_warnings=True, path=k))
        self.cf_getter_string.side_effect = (call_list)
        for k, v in six.iteritems(self.files_to_filter):
            suite_info[k] = v.string
        self.getter._dev_server = self._DEVSERVER_HOST
        self.getter.get_suite_info.return_value = suite_info
        return expected_calls

    def testFindAllTestInBatch(self):
        """Test switch on enable_getting_controls_in_batch for function
        find_all_test."""
        self.additional_mocking()
        self.use_batch = suite_common.ENABLE_CONTROLS_IN_BATCH
        expected_calls = self.expect_control_file_parsing_in_batch()
        suite_common.ENABLE_CONTROLS_IN_BATCH = True

        with patch.object(suite_common, '_should_batch_with') as sbw_mock:
            sbw_mock.return_value = True

            predicate = lambda d: d.suite == self._TAG
            tests = SuiteBase.find_and_parse_tests(self.getter, predicate,
                                                   self._TAG)
            self.assertEquals(len(tests), 6)
            self.assertTrue(self.files['one'] in tests)
            self.assertTrue(self.files['two'] in tests)
            self.assertTrue(self.files['three'] in tests)
            self.assertTrue(self.files['five'] in tests)
            self.assertTrue(self.files['six'] in tests)
            self.assertTrue(self.files['seven'] in tests)
            suite_common.ENABLE_CONTROLS_IN_BATCH = self.use_batch

        self.cf_getter_string.assert_has_calls(expected_calls, any_order=True)

    def testFindAndParseStableTests(self):
        """Should find only tests that match a predicate."""
        self.additional_mocking()

        self.expect_control_file_parsing()

        predicate = lambda d: d.text == self.files['two'].string
        tests = SuiteBase.find_and_parse_tests(self.getter,
                                               predicate,
                                               self._TAG)
        self.assertEquals(len(tests), 1)
        self.assertEquals(tests[0], self.files['two'])


    def testFindSuiteSyntaxErrors(self):
        """Check all control files for syntax errors.

        This test actually parses all control files in the autotest directory
        for syntax errors, by using the un-forgiving parser and pretending to
        look for all control files with the suite attribute.
        """

        autodir = os.path.abspath(
            os.path.join(os.path.dirname(__file__), '..', '..', '..'))
        fs_getter = SuiteBase.create_fs_getter(autodir)
        predicate = lambda t: hasattr(t, 'suite')
        SuiteBase.find_and_parse_tests(fs_getter, predicate,
                                       forgiving_parser=False)


    def testFindAndParseTestsSuite(self):
        """Should find all tests that match a predicate."""
        self.additional_mocking()
        self.expect_control_file_parsing()

        predicate = lambda d: d.suite == self._TAG
        tests = SuiteBase.find_and_parse_tests(self.getter,
                                               predicate,
                                               self._TAG)
        self.assertEquals(len(tests), 6)
        self.assertTrue(self.files['one'] in tests)
        self.assertTrue(self.files['two'] in tests)
        self.assertTrue(self.files['three'] in tests)
        self.assertTrue(self.files['five'] in tests)
        self.assertTrue(self.files['six'] in tests)
        self.assertTrue(self.files['seven'] in tests)


    def testFindAndParseTestsAttr(self):
        """Should find all tests that match a predicate."""
        self.additional_mocking()
        self.expect_control_file_parsing()

        predicate = SuiteBase.matches_attribute_expression_predicate('attr:attr')
        tests = SuiteBase.find_and_parse_tests(self.getter,
                                               predicate,
                                               self._TAG)
        self.assertEquals(len(tests), 6)
        self.assertTrue(self.files['one'] in tests)
        self.assertTrue(self.files['two'] in tests)
        self.assertTrue(self.files['three'] in tests)
        self.assertTrue(self.files['four'] in tests)
        self.assertTrue(self.files['six'] in tests)
        self.assertTrue(self.files['seven'] in tests)


    def testAdHocSuiteCreation(self):
        """Should be able to schedule an ad-hoc suite by specifying
        a single test name."""
        self.additional_mocking()

        self.expect_control_file_parsing(suite_name='ad_hoc_suite')
        predicate = SuiteBase.test_name_equals_predicate('name-data_five')
        suite = Suite.create_from_predicates([predicate], self._BUILDS,
                                       self._BOARD, devserver=None,
                                       cf_getter=self.getter,
                                       afe=self.afe, tko=self.tko)

        self.assertFalse(self.files['one'] in suite.tests)
        self.assertFalse(self.files['two'] in suite.tests)
        self.assertFalse(self.files['four'] in suite.tests)
        self.assertTrue(self.files['five'] in suite.tests)


    def mock_control_file_parsing(self):
        """Fake out find_and_parse_tests(), returning content from |self.files|.
        """
        for test in self.files.values():
            test.text = test.string  # mimic parsing.
        parse_patch = patch.object(SuiteBase, 'find_and_parse_tests')
        self.parse_mock = parse_patch.start()
        self.addCleanup(parse_patch.stop)
        self.parse_mock.return_value = self.files.values()

    def expect_job_scheduling(self, recorder,
                              tests_to_skip=[], ignore_deps=False,
                              raises=False, suite_deps=[], suite=None,
                              extra_keyvals={}):
        """Expect jobs to be scheduled for 'tests' in |self.files|.

        @param recorder: object with a record_entry to be used to record test
                         results.
        @param tests_to_skip: [list, of, test, names] that we expect to skip.
        @param ignore_deps: If true, ignore tests' dependencies.
        @param raises: If True, expect exceptions.
        @param suite_deps: If True, add suite level dependencies.
        @param extra_keyvals: Extra keyvals set to tests.
        """
        record_job_id = suite and suite._results_dir
        if record_job_id:
            p = patch.object(suite, '_remember_job_keyval')
            p.start()
            self.addCleanup(p.stop)
        recorder.record_entry(
            StatusContains.CreateFromStrings('INFO', 'Start %s' % self._TAG),
            log_in_subdir=False)
        tests = list(self.files.values())
        n = 1
        for test in tests:
            if test.name in tests_to_skip:
                continue
            dependencies = []
            if not ignore_deps:
                dependencies.extend(test.dependencies)
            if suite_deps:
                dependencies.extend(suite_deps)
            dependencies.append(self._BOARD)
            build = self._BUILDS[provision.CROS_VERSION_PREFIX]
            keyvals = {
                'build': build,
                'suite': self._TAG,
                'builds': SuiteTest._BUILDS,
                'experimental':test.experimental,
            }
            keyvals.update(extra_keyvals)

            job_mock = self.afe.create_job(
                    control_file=test.text,
                    name=ANY,
                    control_type=ANY,
                    meta_hosts=[self._BOARD],
                    dependencies=dependencies,
                    keyvals=keyvals,
                    max_runtime_mins=24 * 60,
                    timeout_mins=1440,
                    parent_job_id=None,
                    reboot_before=ANY,
                    run_reset=ANY,
                    priority=priorities.Priority.DEFAULT,
                    synch_count=test.sync_count,
                    require_ssp=test.require_ssp)
            if raises:
                job_mock.side_effect = error.NoEligibleHostException()
                recorder.record_entry(
                        StatusContains.CreateFromStrings('START', test.name),
                        log_in_subdir=False)
                recorder.record_entry(
                        StatusContains.CreateFromStrings('TEST_NA', test.name),
                        log_in_subdir=False)
                recorder.record_entry(
                        StatusContains.CreateFromStrings('END', test.name),
                        log_in_subdir=False)
            else:
                fake_job = FakeJob(id=n)
                job_mock.return_value = fake_job
                if record_job_id:
                    suite._remember_job_keyval(fake_job)
                n += 1


    def testScheduleTestsAndRecord(self):
        """Should schedule stable and experimental tests with the AFE."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five', 'name-data_six',
                     'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: 7,
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()

        suite = Suite.create_from_name(self._TAG, self._BUILDS, self._BOARD,
                                       self.devserver,
                                       afe=self.afe, tko=self.tko,
                                       results_dir=self.tmpdir)
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder, suite=suite)
        with patch.object(utils, 'write_keyval'):
            utils.write_keyval(self.tmpdir, keyval_dict)
            suite.schedule(recorder.record_entry)
            for job in suite._jobs:
                self.assertTrue(hasattr(job, 'test_name'))


    def testScheduleTests(self):
        """Should schedule tests with the AFE."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five', 'name-data_six',
                     'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: len(name_list),
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)

            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           afe=self.afe,
                                           tko=self.tko)
            suite.schedule(recorder.record_entry)


    def testScheduleTestsIgnoreDeps(self):
        """Test scheduling tests ignoring deps."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five', 'name-data_six',
                     'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: len(name_list),
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder, ignore_deps=True)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)

            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           afe=self.afe,
                                           tko=self.tko,
                                           ignore_deps=True)
            suite.schedule(recorder.record_entry)


    def testScheduleUnrunnableTestsTESTNA(self):
        """Tests which fail to schedule should be TEST_NA."""
        # Since all tests will be fail to schedule, the num of scheduled tests
        # will be zero.
        name_list = []
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: 0,
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder, raises=True)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)
            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           afe=self.afe,
                                           tko=self.tko)
            suite.schedule(recorder.record_entry)


    def testRetryMapAfterScheduling(self):
        """Test job-test and test-job mapping are correctly updated."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five', 'name-data_six',
                     'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: 7,
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)
            all_files = list(self.files.items())
            # Sort tests in self.files so that they are in the same
            # order as they are scheduled.
            expected_retry_map = {}
            for n in range(len(all_files)):
                test = all_files[n][1]
                job_id = n + 1
                job_retries = 1 if test.job_retries is None else test.job_retries
                if job_retries > 0:
                    expected_retry_map[job_id] = {
                            'state': RetryHandler.States.NOT_ATTEMPTED,
                            'retry_max': job_retries
                    }

            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           afe=self.afe,
                                           tko=self.tko,
                                           job_retry=True)
            suite.schedule(recorder.record_entry)


    def testSuiteMaxRetries(self):
        """Test suite max retries."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five',
                     'name-data_six', 'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: 7,
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)
            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           afe=self.afe,
                                           tko=self.tko,
                                           job_retry=True,
                                           max_retries=1)
            suite.schedule(recorder.record_entry)
            self.assertEqual(suite._retry_handler._max_retries, 1)
            # Find the job_id of the test that allows retry
            job_id = next(six.iterkeys(suite._retry_handler._retry_map))
            suite._retry_handler.add_retry(old_job_id=job_id, new_job_id=10)
            self.assertEqual(suite._retry_handler._max_retries, 0)


    def testSuiteDependencies(self):
        """Should add suite dependencies to tests scheduled."""
        name_list = ['name-data_one', 'name-data_two', 'name-data_three',
                     'name-data_four', 'name-data_five', 'name-data_six',
                     'name-data_seven']
        keyval_dict = {constants.SCHEDULED_TEST_COUNT_KEY: len(name_list),
                       constants.SCHEDULED_TEST_NAMES_KEY: repr(name_list)}

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(recorder, suite_deps=['extra'])
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, keyval_dict)

            suite = Suite.create_from_name(self._TAG,
                                           self._BUILDS,
                                           self._BOARD,
                                           self.devserver,
                                           extra_deps=['extra'],
                                           afe=self.afe,
                                           tko=self.tko)
            suite.schedule(recorder.record_entry)


    def testInheritedKeyvals(self):
        """Tests should inherit some allowlisted job keyvals."""
        # Only keyvals in constants.INHERITED_KEYVALS are inherited to tests.
        job_keyvals = {
                constants.KEYVAL_CIDB_BUILD_ID: '111',
                constants.KEYVAL_CIDB_BUILD_STAGE_ID: '222',
                constants.KEYVAL_BRANCH: 'placeholder_branch',
                constants.KEYVAL_BUILDER_NAME: 'model-placeholder',
                constants.KEYVAL_MAIN_BUILDER_NAME: 'main-placeholder',
                'your': 'name',
        }
        test_keyvals = {
                constants.KEYVAL_CIDB_BUILD_ID: '111',
                constants.KEYVAL_CIDB_BUILD_STAGE_ID: '222',
                constants.KEYVAL_BRANCH: 'placeholder_branch',
                constants.KEYVAL_BUILDER_NAME: 'model-placeholder',
                constants.KEYVAL_MAIN_BUILDER_NAME: 'main-placeholder',
        }

        self.mock_control_file_parsing()
        recorder = MagicMock(base_job.base_job)
        self.expect_job_scheduling(
            recorder,
            extra_keyvals=test_keyvals)
        with patch.object(utils, 'write_keyval') as utils_patch:
            utils_patch.write_keyval(None, job_keyvals)
            utils_patch.write_keyval(None, ANY)

        suite = Suite.create_from_name(self._TAG, self._BUILDS, self._BOARD,
                                       self.devserver,
                                       afe=self.afe, tko=self.tko,
                                       job_keyvals=job_keyvals)
        suite.schedule(recorder.record_entry)


    def _createSuiteWithMockedTestsAndControlFiles(self, file_bugs=False):
        """Create a Suite, using mocked tests and control file contents.

        @return Suite object, after mocking out behavior needed to create it.
        """
        self.result_reporter = _MemoryResultReporter()
        self.expect_control_file_parsing()
        suite = Suite.create_from_name(
                self._TAG,
                self._BUILDS,
                self._BOARD,
                self.devserver,
                self.getter,
                afe=self.afe,
                tko=self.tko,
                file_bugs=file_bugs,
                job_retry=True,
                result_reporter=self.result_reporter,
        )
        return suite


    def _createSuiteMockResults(self, results_dir=None, result_status='FAIL'):
        """Create a suite, returned a set of mocked results to expect.

        @param results_dir: A mock results directory.
        @param result_status: A desired result status, e.g. 'FAIL', 'WARN'.

        @return List of mocked results to wait on.
        """
        self.suite = self._createSuiteWithMockedTestsAndControlFiles(
                         file_bugs=True)
        self.suite._results_dir = results_dir
        test_report = self._get_bad_test_report(result_status)
        test_predicates = test_report.predicates
        test_fallout = test_report.fallout

        self.recorder = MagicMock(base_job.base_job)
        self.recorder.record_entry = MagicMock(base_job.base_job.record_entry)
        self._mock_recorder_with_results([test_predicates], self.recorder)
        return [test_predicates, test_fallout]


    def _mock_recorder_with_results(self, results, recorder):
        """
        Checks that results are recoded in order, eg:
        START, (status, name, reason) END

        @param results: list of results
        @param recorder: status recorder
        """
        for result in results:
            status = result[0]
            test_name = result[1]
            recorder.record_entry(
                StatusContains.CreateFromStrings('START', test_name),
                log_in_subdir=False)
            recorder.record_entry(
                StatusContains.CreateFromStrings(*result),
                log_in_subdir=False).InAnyOrder('results')
            recorder.record_entry(
                StatusContains.CreateFromStrings('END %s' % status, test_name),
                log_in_subdir=False)


    def schedule_and_expect_these_results(self, suite, results, recorder):
        """Create stubs for call to suite.schedule and
        job_status.wait_for_results

        @param suite:    suite object for which to stub out schedule(...)
        @param results:  results object to be returned from
                         job_stats_wait_for_results(...)
        @param recorder: mocked recorder object to replay status messages
        """
        def result_generator(results):
            """A simple generator which generates results as Status objects.

            This generator handles 'send' by simply ignoring it.

            @param results: results object to be returned from
                            job_stats_wait_for_results(...)
            @yield: job_status.Status objects.
            """
            results = [job_status.Status(*r) for r in results]
            for r in results:
                new_input = (yield r)
                if new_input:
                    yield None

        suite_schedule_patch = patch.object(suite, 'schedule')
        self.suite_schedule_mock = suite_schedule_patch.start()
        self.addCleanup(suite_schedule_patch.stop)
        self.suite_schedule_mock(recorder.record_entry)
        suite._retry_handler = RetryHandler({})

        waiter_patch = patch.object(job_status.JobResultWaiter,
                                    'wait_for_results',
                                    autospec=True)
        waiter_mock = waiter_patch.start()
        waiter_mock.return_value = result_generator(results)
        self.addCleanup(waiter_patch.stop)

    @patch('autotest_lib.client.common_lib.base_job.base_job',
           autospec=base_job.base_job)
    def testRunAndWaitSuccess(self, recorder):
        """Should record successful results."""
        suite = self._createSuiteWithMockedTestsAndControlFiles()

        results = [('GOOD', 'good'), ('FAIL', 'bad', 'reason')]
        self._mock_recorder_with_results(results, recorder)
        self.schedule_and_expect_these_results(suite, results, recorder)
        suite.schedule(recorder.record_entry)
        suite.wait(recorder.record_entry)

    @patch('autotest_lib.client.common_lib.base_job.base_job',
           autospec=base_job.base_job)
    def testRunAndWaitFailure(self, recorder):
        """Should record failure to gather results."""
        suite = self._createSuiteWithMockedTestsAndControlFiles()

        recorder.record_entry(
            StatusContains.CreateFromStrings('FAIL', self._TAG, 'waiting'),
            log_in_subdir=False)

        with patch.object(suite, 'schedule') as ss:
            ss.schedule(recorder.record_entry)

            with patch.object(job_status.JobResultWaiter,
                              'wait_for_results',
                              autospec=True) as wait_mock:
                wait_mock.side_effect = Exception
                suite.schedule(recorder.record_entry)
                suite.wait(recorder.record_entry)

    @patch('autotest_lib.client.common_lib.base_job.base_job',
           autospec=base_job.base_job)
    def testRunAndWaitScheduleFailure(self, recorder):
        """Should record failure to schedule jobs."""
        self.additional_mocking()
        suite = self._createSuiteWithMockedTestsAndControlFiles()
        recorder.record_entry(
            StatusContains.CreateFromStrings('INFO', 'Start %s' % self._TAG),
            log_in_subdir=False)

        recorder.record_entry(
            StatusContains.CreateFromStrings('FAIL', self._TAG, 'scheduling'),
            log_in_subdir=False)

        local_patcher = patch.object(suite._job_creator, 'create_job')
        patcher = local_patcher.start()
        self.addCleanup(local_patcher.stop)
        patcher.side_effect = (Exception('Expected during test.'))

        suite.schedule(recorder.record_entry)
        suite.wait(recorder.record_entry)
        patcher.assert_called_with(ANY, retry_for=ANY)


    def testGetTestsSortedByTime(self):
        """Should find all tests and sorted by TIME setting."""
        self.additional_mocking()
        self.expect_control_file_parsing()
        # Get all tests.
        tests = SuiteBase.find_and_parse_tests(self.getter,
                                               lambda d: True,
                                               self._TAG)
        self.assertEquals(len(tests), 7)
        times = [control_data.ControlData.get_test_time_index(test.time)
                 for test in tests]
        self.assertTrue(all(x>=y for x, y in zip(times, times[1:])),
                        'Tests are not ordered correctly.')


    def _get_bad_test_report(self, result_status='FAIL'):
        """
        Fetch the predicates of a failing test, and the parameters
        that are a fallout of this test failing.
        """
        predicates = collections.namedtuple('predicates',
                                            'status, testname, reason')
        fallout = collections.namedtuple('fallout',
                                         ('time_start, time_end, job_id,'
                                          'username, hostname'))
        test_report = collections.namedtuple('test_report',
                                             'predicates, fallout')
        return test_report(predicates(result_status, 'bad_test',
                                      'dreadful_reason'),
                           fallout('2014-01-01 01:01:01', 'None',
                                   self._FAKE_JOB_ID, 'user', 'myhost'))


    def testJobRetryTestFail(self):
        """Test retry works."""
        self.additional_mocking()
        test_to_retry = self.files['seven']
        fake_new_job_id = self._FAKE_JOB_ID + 1
        fake_job = FakeJob(id=self._FAKE_JOB_ID)
        fake_new_job = FakeJob(id=fake_new_job_id)

        test_results = self._createSuiteMockResults()
        self.schedule_and_expect_these_results(
                self.suite,
                [test_results[0] + test_results[1]],
                self.recorder)
        with patch.object(self.suite._job_creator, 'create_job') as suite_mock:

            suite_mock.return_value = fake_new_job
            self.suite.schedule(self.recorder.record_entry)
            self.suite._retry_handler._retry_map = {
                    self._FAKE_JOB_ID: {
                            'state': RetryHandler.States.NOT_ATTEMPTED,
                            'retry_max': 1
                    }
            }
            self.suite._jobs_to_tests[self._FAKE_JOB_ID] = test_to_retry
            self.suite.wait(self.recorder.record_entry)
            expected_retry_map = {
                    self._FAKE_JOB_ID: {
                            'state': RetryHandler.States.RETRIED,
                            'retry_max': 1
                    },
                    fake_new_job_id: {
                            'state': RetryHandler.States.NOT_ATTEMPTED,
                            'retry_max': 0
                    }
            }
            # Check retry map is correctly updated
            self.assertEquals(self.suite._retry_handler._retry_map,
                              expected_retry_map)
            # Check _jobs_to_tests is correctly updated
            self.assertEquals(self.suite._jobs_to_tests[fake_new_job_id],
                              test_to_retry)

    def testJobRetryTestWarn(self):
        """Test that no retry is scheduled if test warns."""
        self.additional_mocking()
        test_to_retry = self.files['seven']
        fake_job = FakeJob(id=self._FAKE_JOB_ID)
        test_results = self._createSuiteMockResults(result_status='WARN')
        self.schedule_and_expect_these_results(
                self.suite,
                [test_results[0] + test_results[1]],
                self.recorder)
        self.suite.schedule(self.recorder.record_entry)
        self.suite._retry_handler._retry_map = {
                self._FAKE_JOB_ID: {'state': RetryHandler.States.NOT_ATTEMPTED,
                                    'retry_max': 1}
                }
        self.suite._jobs_to_tests[self._FAKE_JOB_ID] = test_to_retry
        expected_jobs_to_tests = self.suite._jobs_to_tests.copy()
        expected_retry_map = self.suite._retry_handler._retry_map.copy()
        self.suite.wait(self.recorder.record_entry)
        self.assertTrue(self.result_reporter.results)
        # Check retry map and _jobs_to_tests, ensure no retry was scheduled.
        self.assertEquals(self.suite._retry_handler._retry_map,
                          expected_retry_map)
        self.assertEquals(self.suite._jobs_to_tests, expected_jobs_to_tests)

    def testFailedJobRetry(self):
        """Make sure the suite survives even if the retry failed."""
        self.additional_mocking()
        test_to_retry = self.files['seven']
        FakeJob(id=self._FAKE_JOB_ID)

        test_results = self._createSuiteMockResults()
        self.schedule_and_expect_these_results(
                self.suite,
                [test_results[0] + test_results[1]],
                self.recorder)
        with patch.object(self.suite._job_creator,
                          'create_job') as suite_mock, patch.object(
                                  self.suite, '_should_report') as report_mock:
            suite_mock.side_effect = error.RPCException('Expected during test')

            # Do not file a bug.
            report_mock.return_value = False

            self.suite.schedule(self.recorder.record_entry)
            self.suite._retry_handler._retry_map = {
                    self._FAKE_JOB_ID: {
                            'state': RetryHandler.States.NOT_ATTEMPTED,
                            'retry_max': 1
                    }
            }
            self.suite._jobs_to_tests[self._FAKE_JOB_ID] = test_to_retry
            self.suite.wait(self.recorder.record_entry)
            expected_retry_map = {
                    self._FAKE_JOB_ID: {
                            'state': RetryHandler.States.ATTEMPTED,
                            'retry_max': 1
                    }
            }
            expected_jobs_to_tests = self.suite._jobs_to_tests.copy()
            self.assertEquals(self.suite._retry_handler._retry_map,
                              expected_retry_map)
            self.assertEquals(self.suite._jobs_to_tests,
                              expected_jobs_to_tests)

            suite_mock.assert_called_with(test_to_retry,
                                          retry_for=self._FAKE_JOB_ID)


class _MemoryResultReporter(SuiteBase._ResultReporter):
    """Reporter that stores results internally for testing."""
    def __init__(self):
        self.results = []

    def report(self, result):
        """Reports the result by storing it internally."""
        self.results.append(result)


if __name__ == '__main__':
    unittest.main()
