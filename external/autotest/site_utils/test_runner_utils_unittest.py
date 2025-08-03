#!/usr/bin/python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# pylint: disable-msg=C0111

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import os
import unittest
from unittest.mock import patch

import common

import shutil
import tempfile
import types
from autotest_lib.client.common_lib import control_data
from autotest_lib.server.cros.dynamic_suite import control_file_getter
from autotest_lib.server.cros.dynamic_suite import suite as suite_module
from autotest_lib.server.hosts import host_info
from autotest_lib.site_utils import test_runner_utils


class TypeMatcher(object):
    """Matcher for object is of type."""

    def __init__(self, expected_type):
        self.expected_type = expected_type

    def __eq__(self, other):
        return isinstance(other, self.expected_type)


class JobMatcher(object):
    """Matcher for JobObject + Name."""

    def __init__(self, expected_type, name):
        self.expected_type = expected_type
        self.name = name

    def __eq__(self, other):
        return (isinstance(other, self.expected_type)
                and self.name in other.name)


class hostinfoMatcher(object):
    """Match hostinfo stuff"""

    def __init__(self, labels, attributes):
        self.labels = labels.split(' ')
        self.attributes = attributes

    def __eq__(self, other):
        return self.labels == other.labels and self.attributes == other.attributes


class ContainsMatcher:
    """Matcher for object contains attr."""

    def __init__(self, key, value):
        self.key = key
        self.value = value

    def __eq__(self, rhs):
        try:
            return getattr(rhs, self._key) == self._value
        except Exception:
            return False


class SampleJob(object):
    """Sample to be used for mocks."""

    def __init__(self, id=1):
        self.id = id


class FakeTests(object):
    """A fake test to be used for mocks."""

    def __init__(self, text, deps=[], py_version=None):
        self.text = text
        self.test_type = 'client'
        self.dependencies = deps
        self.name = text
        self.py_version = py_version


class TestRunnerUnittests(unittest.TestCase):
    """Test test_runner_utils."""

    autotest_path = 'ottotest_path'
    suite_name = 'sweet_name'
    test_arg = 'suite:' + suite_name
    remote = 'remoat'
    build = 'bild'
    board = 'bored'
    fast_mode = False
    suite_control_files = ['c1', 'c2', 'c3', 'c4']
    results_dir = '/tmp/test_that_results_fake'
    id_digits = 1
    ssh_verbosity = 2
    ssh_options = '-F /dev/null -i /dev/null'
    args = 'matey'
    retry = True

    def _results_directory_from_results_list(self, results_list):
        """Generate a temp directory filled with provided test results.

        @param results_list: List of results, each result is a tuple of strings
                             (test_name, test_status_message).
        @returns: Absolute path to the results directory.
        """
        global_dir = tempfile.mkdtemp()
        for index, (test_name, test_status_message) in enumerate(results_list):
            dir_name = '-'.join(['results',
                                 "%02.f" % (index + 1),
                                 test_name])
            local_dir = os.path.join(global_dir, dir_name)
            os.mkdir(local_dir)
            os.mkdir('%s/debug' % local_dir)
            with open("%s/status.log" % local_dir, mode='w+') as status:
                status.write(test_status_message)
                status.flush()
        return global_dir

    def test_handle_local_result_for_good_test(self):
        patcher = patch.object(control_file_getter, 'DevServerGetter')
        getter = patcher.start()
        self.addCleanup(patcher.stop)
        getter.get_control_file_list.return_value = []
        job = SampleJob()

        test_patcher = patch.object(control_data, 'ControlData')
        test = test_patcher.start()
        self.addCleanup(test_patcher.stop)
        test.job_retries = 5

        suite = test_runner_utils.LocalSuite([], "tag", [], None, getter,
                                             job_retry=True)
        suite._retry_handler = suite_module.RetryHandler({job.id: test})

        #No calls, should not be retried
        directory = self._results_directory_from_results_list([
            ("dummy_Good", "GOOD: nonexistent test completed successfully")])
        new_id = suite.handle_local_result(
            job.id, directory,
            lambda log_entry, log_in_subdir=False: None)
        self.assertIsNone(new_id)
        shutil.rmtree(directory)

    def test_handle_local_result_for_bad_test(self):
        patcher = patch.object(control_file_getter, 'DevServerGetter')
        getter = patcher.start()
        self.addCleanup(patcher.stop)
        getter.get_control_file_list.return_value = []

        job = SampleJob()

        test_patcher = patch.object(control_data, 'ControlData')
        test = test_patcher.start()
        self.addCleanup(test_patcher.stop)
        test.job_retries = 5

        utils_mock = patch.object(test_runner_utils.LocalSuite,
                                  '_retry_local_result')
        test_runner_utils_mock = utils_mock.start()
        self.addCleanup(utils_mock.stop)
        test_runner_utils_mock._retry_local_result.return_value = 42

        suite = test_runner_utils.LocalSuite([], "tag", [], None, getter,
                                             job_retry=True)
        suite._retry_handler = suite_module.RetryHandler({job.id: test})

        directory = self._results_directory_from_results_list([
            ("dummy_Bad", "FAIL")])
        new_id = suite.handle_local_result(
            job.id, directory,
            lambda log_entry, log_in_subdir=False: None)
        self.assertIsNotNone(new_id)
        shutil.rmtree(directory)


    def test_generate_report_status_code_success_with_retries(self):
        global_dir = self._results_directory_from_results_list([
            ("dummy_Flaky", "FAIL"),
            ("dummy_Flaky", "GOOD: nonexistent test completed successfully")])
        status_code = test_runner_utils.generate_report(
            global_dir, just_status_code=True)
        self.assertEquals(status_code, 0)
        shutil.rmtree(global_dir)


    def test_generate_report_status_code_failure_with_retries(self):
        global_dir = self._results_directory_from_results_list([
            ("dummy_Good", "GOOD: nonexistent test completed successfully"),
            ("dummy_Bad", "FAIL"),
            ("dummy_Bad", "FAIL")])
        status_code = test_runner_utils.generate_report(
            global_dir, just_status_code=True)
        self.assertNotEquals(status_code, 0)
        shutil.rmtree(global_dir)


    def test_get_predicate_for_test_arg(self):
        # Assert the type signature of get_predicate_for_test(...)
        # Because control.test_utils_wrapper calls this function,
        # it is imperative for backwards compatilbility that
        # the return type of the tested function does not change.
        tests = ['dummy_test', 'e:name_expression', 'f:expression',
                 'suite:suitename']
        for test in tests:
            pred, desc = test_runner_utils.get_predicate_for_test_arg(test)
            self.assertTrue(isinstance(pred, types.FunctionType))
            self.assertTrue(isinstance(desc, str))

    def test_perform_local_run(self):
        """Test a local run that should pass."""
        patcher = patch.object(test_runner_utils, '_auto_detect_labels')
        _auto_detect_labels_mock = patcher.start()
        self.addCleanup(patcher.stop)

        patcher2 = patch.object(test_runner_utils, 'get_all_control_files')
        get_all_control_files_mock = patcher2.start()
        self.addCleanup(patcher2.stop)

        _auto_detect_labels_mock.return_value = [
                'os:cros', 'has_chameleon:True'
        ]

        get_all_control_files_mock.return_value = [
                FakeTests(test, deps=['has_chameleon:True'])
                for test in self.suite_control_files
        ]

        patcher3 = patch.object(test_runner_utils, 'run_job')
        run_job_mock = patcher3.start()
        self.addCleanup(patcher3.stop)

        for control_file in self.suite_control_files:
            run_job_mock.return_value = (0, '/fake/dir')
        test_runner_utils.perform_local_run(self.autotest_path,
                                            ['suite:' + self.suite_name],
                                            self.remote,
                                            self.fast_mode,
                                            build=self.build,
                                            board=self.board,
                                            ssh_verbosity=self.ssh_verbosity,
                                            ssh_options=self.ssh_options,
                                            args=self.args,
                                            results_directory=self.results_dir,
                                            job_retry=self.retry,
                                            ignore_deps=False,
                                            minus=[])

        run_job_mock.assert_called_with(job=TypeMatcher(
                test_runner_utils.SimpleJob),
                                        host=self.remote,
                                        info=TypeMatcher(host_info.HostInfo),
                                        autotest_path=self.autotest_path,
                                        results_directory=self.results_dir,
                                        fast_mode=self.fast_mode,
                                        id_digits=self.id_digits,
                                        ssh_verbosity=self.ssh_verbosity,
                                        ssh_options=self.ssh_options,
                                        args=TypeMatcher(str),
                                        pretend=False,
                                        autoserv_verbose=False,
                                        companion_hosts=None,
                                        dut_servers=None,
                                        is_cft=False,
                                        ch_info={})

    def test_perform_local_run_missing_deps(self):
        """Test a local run with missing dependencies. No tests should run."""
        patcher = patch.object(test_runner_utils, '_auto_detect_labels')
        getter = patcher.start()
        self.addCleanup(patcher.stop)

        getter.return_value = ['os:cros', 'has_chameleon:True']

        patcher2 = patch.object(test_runner_utils, 'get_all_control_files')
        test_runner_utils_mock = patcher2.start()
        self.addCleanup(patcher2.stop)
        test_runner_utils_mock.return_value = [
                FakeTests(test, deps=['has_chameleon:False'])
                for test in self.suite_control_files
        ]

        res = test_runner_utils.perform_local_run(
                self.autotest_path, ['suite:' + self.suite_name],
                self.remote,
                self.fast_mode,
                build=self.build,
                board=self.board,
                ssh_verbosity=self.ssh_verbosity,
                ssh_options=self.ssh_options,
                args=self.args,
                results_directory=self.results_dir,
                job_retry=self.retry,
                ignore_deps=False,
                minus=[])

        # Verify when the deps are not met, the tests are not run.
        self.assertEquals(res, [])

    def test_minus_flag(self):
        """Verify the minus flag skips tests."""
        patcher = patch.object(test_runner_utils, '_auto_detect_labels')
        getter = patcher.start()
        self.addCleanup(patcher.stop)

        getter.return_value = ['os:cros', 'has_chameleon:True']

        patcher2 = patch.object(test_runner_utils, 'get_all_control_files')
        test_runner_utils_mock = patcher2.start()
        self.addCleanup(patcher2.stop)

        patcher3 = patch.object(test_runner_utils, 'run_job')
        run_job_mock = patcher3.start()
        self.addCleanup(patcher3.stop)

        minus_tests = [FakeTests(self.suite_control_files[0])]
        all_tests = [
                FakeTests(test, deps=[]) for test in self.suite_control_files
        ]

        test_runner_utils_mock.side_effect = [minus_tests, all_tests]
        run_job_mock.side_effect = [(0, 'fakedir') for _ in range(3)]
        test_labels = "'a' 'test' 'label'"
        test_attributes = {"servo": "yes"}

        res = test_runner_utils.perform_local_run(
                self.autotest_path, ['suite:' + self.suite_name],
                self.remote,
                self.fast_mode,
                build=self.build,
                board=self.board,
                ssh_verbosity=self.ssh_verbosity,
                ssh_options=self.ssh_options,
                args=self.args,
                results_directory=self.results_dir,
                host_attributes=test_attributes,
                job_retry=self.retry,
                ignore_deps=False,
                minus=[self.suite_control_files[0]],
                is_cft=True,
                host_labels=test_labels,
                label=None)

        from mock import call

        calls = []
        for name in self.suite_control_files[1:]:
            calls.append(
                    call(job=JobMatcher(test_runner_utils.SimpleJob,
                                        name=name),
                         host=self.remote,
                         info=hostinfoMatcher(labels=test_labels,
                                              attributes=test_attributes),
                         autotest_path=self.autotest_path,
                         results_directory=self.results_dir,
                         fast_mode=self.fast_mode,
                         id_digits=self.id_digits,
                         ssh_verbosity=self.ssh_verbosity,
                         ssh_options=self.ssh_options,
                         args=TypeMatcher(str),
                         pretend=False,
                         autoserv_verbose=False,
                         companion_hosts=None,
                         dut_servers=None,
                         is_cft=True,
                         ch_info={}))

        run_job_mock.assert_has_calls(calls, any_order=True)
        assert run_job_mock.call_count == len(calls)

    def test_set_pyversion(self):
        """Test the tests can properly set the python version."""

        # When a test is missing a version, use the current setting.
        starting_version = os.getenv('PY_VERSION')

        try:
            fake_test1 = FakeTests('foo')
            fake_test2 = FakeTests('foo', py_version=2)
            fake_test3 = FakeTests('foo', py_version=3)

            test_runner_utils._set_pyversion(
                    [fake_test1, fake_test2, fake_test3])
            self.assertEqual(os.getenv('PY_VERSION'), starting_version)

            # When there is a mix, use the current setting.
            starting_version = os.getenv('PY_VERSION')
            fake_test1 = FakeTests('foo', py_version=2)
            fake_test2 = FakeTests('foo', py_version=2)
            fake_test3 = FakeTests('foo', py_version=3)

            test_runner_utils._set_pyversion(
                    [fake_test1, fake_test2, fake_test3])
            self.assertEqual(os.getenv('PY_VERSION'), starting_version)

            # When all agree, but still 1 missing, use the current setting.
            fake_test1 = FakeTests('foo')
            fake_test2 = FakeTests('foo', py_version=3)
            fake_test3 = FakeTests('foo', py_version=3)

            test_runner_utils._set_pyversion(
                    [fake_test1, fake_test2, fake_test3])
            self.assertEqual(os.getenv('PY_VERSION'), starting_version)

            # When all are set to 3, use 3.
            fake_test1 = FakeTests('foo', py_version=3)
            fake_test2 = FakeTests('foo', py_version=3)
            fake_test3 = FakeTests('foo', py_version=3)

            test_runner_utils._set_pyversion(
                    [fake_test1, fake_test2, fake_test3])
            self.assertEqual(os.getenv('PY_VERSION'), '3')

            # When all are set to 2, use 2.
            fake_test1 = FakeTests('foo', py_version=2)
            fake_test2 = FakeTests('foo', py_version=2)
            fake_test3 = FakeTests('foo', py_version=2)

            test_runner_utils._set_pyversion(
                    [fake_test1, fake_test2, fake_test3])
            self.assertEqual(os.getenv('PY_VERSION'), '2')
        finally:
            # In the event something breaks, reset the pre-test version.
            os.environ['PY_VERSION'] = starting_version

    def test_host_info_write(self):

        dirpath = tempfile.mkdtemp()

        info = host_info.HostInfo(['some', 'labels'], {'attrib1': '1'})
        import pathlib
        expected_path = os.path.join(
                pathlib.Path(__file__).parent.absolute(),
                'host_info_store_testfile')
        try:

            test_runner_utils._write_host_info(dirpath, 'host_info_store',
                                               'localhost:1234', info)
            test_path = os.path.join(dirpath, 'host_info_store',
                                     'localhost:1234.store')
            with open(test_path, 'r') as rf:
                test_data = rf.read()
            with open(expected_path, 'r') as rf:
                expected_data = rf.read()
            self.assertEqual(test_data, expected_data)

        finally:
            shutil.rmtree(dirpath)


if __name__ == '__main__':
    unittest.main()
