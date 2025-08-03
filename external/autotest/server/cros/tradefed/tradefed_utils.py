# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import contextlib
import logging
import os
import random
import re
from xml.etree import ElementTree

from autotest_lib.client.common_lib import utils as common_utils
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros import lockfile

PERF_MODULE_NAME_PREFIX = 'CTS.'

@contextlib.contextmanager
def lock(filename):
    """Prevents other autotest/tradefed instances from accessing cache.

    @param filename: The file to be locked.
    """
    filelock = lockfile.FileLock(filename)
    # It is tempting just to call filelock.acquire(3600). But the implementation
    # has very poor temporal granularity (timeout/10), which is unsuitable for
    # our needs. See /usr/lib64/python2.7/site-packages/lockfile/
    attempts = 0
    total_wait_seconds = 0
    while not filelock.i_am_locking():
        try:
            attempts += 1
            logging.info('Waiting for cache lock...')
            # Cap the wait by 2 minutes. Setting too long wait makes long-lived
            # tasks to have less chance to obtain the lock, leading to failures.
            # We must not use a random integer as the filelock implementations
            # may underflow an integer division.
            wait = min(120.0, random.uniform(0.0, pow(2.0, attempts)))
            total_wait_seconds += wait
            filelock.acquire(wait)
        except (lockfile.AlreadyLocked, lockfile.LockTimeout):
            # Our goal is to wait long enough to be sure something very bad
            # happened to the locking thread. Wait 2 hours at maximum
            if total_wait_seconds >= 7200:
                # Normally we should acquire the lock immediately. Once we
                # wait on the order of 10 minutes either the dev server IO is
                # overloaded or a lock didn't get cleaned up. Take one for the
                # team, break the lock and report a failure. This should fix
                # the lock for following tests. If the failure affects more than
                # one job look for a deadlock or dev server overload.
                age = filelock.age_of_lock()
                logging.error('Permanent lock failure. Lock age = %d.', age)
                # Breaking a lock is a destructive operation that causes
                # abort of locking jobs, cleanup and redowloading of cache
                # contents. When the error was due to overloaded server,
                # it cause even more cascade of lock errors. Wait 4 hours
                # before breaking the lock. Tasks inside the critical section
                # is about downloading a few gigabytes of files. Taking 4 hours
                # strongly implies the job had went wrong.
                if age > 4 * 60 * 60:
                    logging.error('Trying to break lock.')
                    filelock.break_lock()
                raise error.TestFail('Error: permanent cache lock failure.')
        else:
            logging.info('Acquired cache lock after %d attempts.', attempts)
    try:
        yield
    finally:
        filelock.release()
        logging.info('Released cache lock.')


@contextlib.contextmanager
def adb_keepalive(targets, extra_paths):
    """A context manager that keeps the adb connection alive.

    AdbKeepalive will spin off a new process that will continuously poll for
    adb's connected state, and will attempt to reconnect if it ever goes down.
    This is the only way we can currently recover safely from (intentional)
    reboots.

    @param target: the hostname and port of the DUT.
    @param extra_paths: any additional components to the PATH environment
                        variable.
    """
    from autotest_lib.client.common_lib.cros import adb_keepalive as module
    # |__file__| returns the absolute path of the compiled bytecode of the
    # module. We want to run the original .py file, so we need to change the
    # extension back.
    script_filename = module.__file__.replace('.pyc', '.py')
    jobs = [common_utils.BgJob(
        [script_filename, target],
        nickname='adb_keepalive',
        stderr_level=logging.DEBUG,
        stdout_tee=common_utils.TEE_TO_LOGS,
        stderr_tee=common_utils.TEE_TO_LOGS,
        extra_paths=extra_paths) for target in targets]

    try:
        yield
    finally:
        # The adb_keepalive.py script runs forever until SIGTERM is sent.
        for job in jobs:
            common_utils.nuke_subprocess(job.sp)
        common_utils.join_bg_jobs(jobs)


def parse_tradefed_testresults_xml(test_result_xml_path, waivers=None):
    """ Check the result from tradefed through test_results.xml
    @param waivers: a set() of tests which are permitted to fail.
    """
    waived_count = dict()
    failed_tests = set()
    try:
        root = ElementTree.parse(test_result_xml_path)
        for module in root.iter('Module'):
            module_name = module.get('name')
            for testcase in module.iter('TestCase'):
                testcase_name = testcase.get('name')
                for test in testcase.iter('Test'):
                    test_case = test.get('name')
                    test_res = test.get('result')
                    test_name = '%s#%s' % (testcase_name, test_case)

                    if test_res == "fail":
                        test_fail = test.find('Failure')
                        failed_message = test_fail.get('message')
                        failed_stacktrace = test_fail.find('StackTrace').text

                        if waivers and test_name in waivers:
                            waived_count[test_name] = (
                                waived_count.get(test_name, 0) + 1)
                        else:
                            failed_tests.add(test_name)

        # Check for test completion.
        for summary in root.iter('Summary'):
            modules_done = summary.get('modules_done')
            modules_total = summary.get('modules_total')

        if failed_tests:
            logging.error('Failed (but not waived) tests:\n%s',
                          '\n'.join(sorted(failed_tests)))

        waived = []
        for testname, fail_count in waived_count.items():
            waived += [testname] * fail_count
            logging.info('Waived failure for %s %d time(s)',
                         testname, fail_count)
        logging.info('>> Total waived = %s', waived)
        return waived

    except Exception as e:
        logging.warning('Exception raised in '
                        '|tradefed_utils.parse_tradefed_testresults_xml|: {'
                        '0}'.format(e))
        return []


def parse_tradefed_result(result, waivers=None):
    """Check the result from the tradefed output.

    @param result: The result stdout string from the tradefed command.
    @param waivers: a set() of tests which are permitted to fail.
    @return List of the waived tests.
    """
    # Regular expressions for start/end messages of each test-run chunk.
    abi_re = r'arm\S*|x86\S*'
    # TODO(kinaba): use the current running module name.
    module_re = r'\S+'
    start_re = re.compile(r'(?:Start|Continu)ing (%s) %s with'
                          r' (\d+(?:,\d+)?) test' % (abi_re, module_re))
    end_re = re.compile(r'(%s) %s (?:complet|fail)ed in .*\.'
                        r' (\d+) passed, (\d+) failed, (\d+) not executed' %
                        (abi_re, module_re))
    fail_re = re.compile(r'I/ConsoleReporter.* (\S+) fail:')
    inaccurate_re = re.compile(r'IMPORTANT: Some modules failed to run to '
                                'completion, tests counts may be inaccurate')
    abis = set()
    waived_count = dict()
    failed_tests = set()
    accurate = True
    for line in result.splitlines():
        match = start_re.search(line)
        if match:
            abis = abis.union([match.group(1)])
            continue
        match = end_re.search(line)
        if match:
            abi = match.group(1)
            if abi not in abis:
                logging.error('Trunk end with %s abi but have not seen '
                              'any trunk start with this abi.(%s)', abi, line)
            continue
        match = fail_re.search(line)
        if match:
            testname = match.group(1)
            if waivers and testname in waivers:
                waived_count[testname] = waived_count.get(testname, 0) + 1
            else:
                failed_tests.add(testname)
            continue
        # b/66899135, tradefed may reported inaccuratly with `list results`.
        # Add warning if summary section shows that the result is inacurrate.
        match = inaccurate_re.search(line)
        if match:
            accurate = False

    logging.info('Total ABIs: %s', abis)
    if failed_tests:
        logging.error('Failed (but not waived) tests:\n%s',
            '\n'.join(sorted(failed_tests)))

    # TODO(dhaddock): Find a more robust way to apply waivers.
    waived = []
    for testname, fail_count in waived_count.items():
        if fail_count > len(abis):
            # This should be an error.TestFail, but unfortunately
            # tradefed has a bug that emits "fail" twice when a
            # test failed during teardown. It will anyway causes
            # a test count inconsistency and visible on the dashboard.
            logging.error('Found %d failures for %s but there are only %d '
                          'abis: %s', fail_count, testname, len(abis), abis)
            fail_count = len(abis)
        waived += [testname] * fail_count
        logging.info('Waived failure for %s %d time(s)', testname, fail_count)
    logging.info('Total waived = %s', waived)
    return waived, accurate


# A similar implementation in Java can be found at
# https://android.googlesource.com/platform/test/suite_harness/+/refs/heads/\
# pie-cts-dev/common/util/src/com/android/compatibility/common/util/\
# ResultHandler.java
def get_test_result_xml_path(results_destination):
    """Get the path of test_result.xml from the last session.
    Raises:
        Should never raise!
    """
    try:
        last_result_path = None
        for dir in os.listdir(results_destination):
            result_dir = os.path.join(results_destination, dir)
            result_path = os.path.join(result_dir, 'test_result.xml')
            # We use the lexicographically largest path, because |dir| are
            # of format YYYY.MM.DD_HH.MM.SS. The last session will always
            # have the latest date which leads to the lexicographically
            # largest path.
            if last_result_path and last_result_path > result_path:
                continue
            # We need to check for `islink` as `isdir` returns true if
            # |result_dir| is a symbolic link to a directory.
            if not os.path.isdir(result_dir) or os.path.islink(result_dir):
                continue
            if not os.path.exists(result_path):
                continue
            last_result_path = result_path
        return last_result_path
    except Exception as e:
        logging.warning(
            'Exception raised in '
            '|tradefed_utils.get_test_result_xml_path|: {'
            '0}'.format(e))


def get_perf_metrics_from_test_result_xml(result_path, resultsdir):
    """Parse test_result.xml and each <Metric /> is mapped to a dict that
    can be used as kwargs of |TradefedTest.output_perf_value|.
    Raises:
        Should never raise!
    """
    try:
        root = ElementTree.parse(result_path)
        for module in root.iter('Module'):
            module_name = module.get('name')
            for testcase in module.iter('TestCase'):
                testcase_name = testcase.get('name')
                for test in testcase.iter('Test'):
                    test_name = test.get('name')
                    for metric in test.iter('Metric'):
                        score_type = metric.get('score_type')
                        if score_type == None:
                            continue
                        if score_type not in ['higher_better', 'lower_better']:
                            logging.warning(
                                'Unsupported score_type in %s/%s/%s',
                                module_name, testcase_name, test_name)
                            continue
                        higher_is_better = (score_type == 'higher_better')
                        units = metric.get('score_unit')
                        yield dict(
                            description=testcase_name + '#' + test_name,
                            value=metric[0].text,
                            units=units,
                            higher_is_better=higher_is_better,
                            resultsdir=os.path.join(resultsdir, 'tests',
                                PERF_MODULE_NAME_PREFIX + module_name)
                        )
    except Exception as e:
        logging.warning(
            'Exception raised in '
            '|tradefed_utils.get_perf_metrics_from_test_result_xml|: {'
            '0}'.format(e))
