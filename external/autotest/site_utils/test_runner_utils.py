# Lint as: python2, python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import errno
import os
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import threading

import logging
# Turn the logging level to INFO before importing other autotest
# code, to avoid having failed import logging messages confuse the
# test_that user.
logging.basicConfig(level=logging.INFO)

import common
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.client.common_lib import logging_manager
from autotest_lib.server.cros.dynamic_suite import suite, constants
from autotest_lib.server.hosts import factory
from autotest_lib.server.hosts import file_store
from autotest_lib.server.hosts import host_info
from autotest_lib.server import autoserv_utils
from autotest_lib.server import server_logging_config
from autotest_lib.server import utils


_autoserv_proc = None
_sigint_handler_lock = threading.Lock()

_AUTOSERV_SIGINT_TIMEOUT_SECONDS = 5
NO_BOARD = 'ad_hoc_board'
NO_BUILD = 'ad_hoc_build'
NO_MODEL = 'ad_hoc_model'
_SUITE_REGEX = r'suite:(.*)'

_TEST_KEY_FILENAME = 'testing_rsa'
TEST_KEY_PATH = ('/mnt/host/source/src/scripts/mod_for_test_scripts/'
                  'ssh_keys/%s' % _TEST_KEY_FILENAME)

_LATEST_RESULTS_DIRECTORY = '/tmp/test_that_latest'
_HOST_INFO_SUBDIR = 'host_info_store'


class TestThatRunError(Exception):
    """Raised if test_that encounters something unexpected while running."""


class TestThatProvisioningError(Exception):
    """Raised when it fails to provision the DUT to the requested build."""


class TestThatControlError(Exception):
    """Raise when there is an issue the specified test's control file."""


def add_common_args(parser):
    """
    Add common arguments for both test_that and test_droid to their parser.

    @param parser: argparse.ArgumentParser object to add arguments to.
    """
    parser.add_argument('tests', nargs='+', metavar='TEST',
                        help='Run given test(s). Use suite:SUITE to specify '
                             'test suite. Use e:[NAME_PATTERN] to specify a '
                             'NAME-matching regular expression. Use '
                             'f:[FILE_PATTERN] to specify a filename matching '
                             'regular expression. Specified regular '
                             'expressions will be implicitly wrapped in '
                             '^ and $.')
    parser.add_argument('--fast', action='store_true', dest='fast_mode',
                        default=False,
                        help='Enable fast mode.  This will cause test_droid '
                             'to skip time consuming steps like sysinfo and '
                             'collecting crash information.')
    parser.add_argument('--args', metavar='ARGS',
                        help='Whitespace separated argument string to pass '
                             'through to test. Only supported for runs '
                             'against a local DUT. '
                             "e.g. --args='foo=bar cat=\"in a hat\"'.")
    parser.add_argument('--results_dir', metavar='RESULTS_DIR', default=None,
                        help='Instead of storing results in a new subdirectory'
                             ' of /tmp , store results in RESULTS_DIR. If '
                             'RESULTS_DIR already exists, it will be deleted.')
    parser.add_argument('--pretend', action='store_true', default=False,
                        help='Print autoserv commands that would be run, '
                             'rather than running them.')
    parser.add_argument('--no-experimental',
                        action='store_true',
                        default=False,
                        dest='no_experimental',
                        help='DEPRECATED DO NOT USE.')
    parser.add_argument('--enforce-deps', action='store_true',
                        default=False, dest='enforce_deps',
                        help='Skip tests whose DEPENDENCIES can not '
                             'be satisfied.')
    parser.add_argument('--debug', action='store_true',
                        help='Include DEBUG level messages in stdout. Note: '
                             'these messages will be included in output log '
                             'file regardless. In addition, turn on autoserv '
                             'verbosity.')
    parser.add_argument('--iterations', action='store', type=int, default=1,
                        help='Number of times to run the tests specified.')
    parser.add_argument('--ssh_verbosity', action='store', type=int,
                        choices=[0, 1, 2, 3], default=0,
                        help='Verbosity level for ssh, between 0 and 3 '
                             'inclusive.')
    parser.add_argument('--ssh_options', action='store', default=None,
                        help='A string giving additional options to be '
                        'added to ssh commands.')


class LocalSuite(suite.Suite):
    """Subclass of Suite with methods for running locally"""

    def handle_local_result(self, job_id, results_dir, record):
        """
        Handle recording and/or retrying a completed job run locally.

        @param job_id: int ID of job
        @param results_dir: absolute path where test results were stored.
        @param record: callable that records job status

        @returns: new job_id if a job was scheduled for retry, None otherwise.
        """
        logging.debug('Parsing test results for job %s',job_id)
        code = generate_report(results_dir, just_status_code=True)
        if not self._retry_handler:
            return None
        logging.debug('Handling result of job %s',job_id)
        logging.debug(self._retry_handler._retry_map)
        if code == 0:
            logging.debug('All tests for job %s succeeded, no retry', job_id)
            if self._retry_handler.job_present(job_id):
                self._retry_handler.set_attempted(job_id)
            return None

        new_job_id = None
        go_ahead = (self._job_retry and
                    self._retry_handler._should_retry_local_job(job_id))
        if go_ahead:
            new_job_id = self._retry_local_result(job_id, record)
        return new_job_id

    def _retry_local_result(self, job_id, record):
        """
        Retry a test job by id.

        @param job_id: int ID of job
        @param record: callable that records job status.
                 prototype:
                   record(base_job.status_log_entry)

        @returns: new job_id if a job was scheduled for retry, None otherwise.
        """
        test = self._jobs_to_tests[job_id]
        logging.debug('Attempting to retry job %s, test %s', job_id, test.name)
        test.fast = False
        new_job = self._schedule_test(
                record=record, test=test, retry_for=job_id)
        if new_job:
            return new_job.id
        return None

    def test_name_from_job(self, job_id):
        """Find the name of the test run by a job with a given job ID."""
        if self._jobs_to_tests[job_id]:
            return self._jobs_to_tests[job_id].name


def _run_autoserv(command, pretend=False):
    """Run autoserv command.

    Run the autoserv command and wait on it. Log the stdout.
    Ensure that SIGINT signals are passed along to autoserv.

    @param command: the autoserv command to run.
    @returns: exit code of the command.

    """
    if not pretend:
        logging.debug('Running autoserv command: %s', command)
        global _autoserv_proc
        _autoserv_proc = subprocess.Popen(command,
                                          stdout=subprocess.PIPE,
                                          stderr=subprocess.STDOUT)
        # This incantation forces unbuffered reading from stdout,
        # so that autoserv output can be displayed to the user
        # immediately.
        for message in iter(_autoserv_proc.stdout.readline, b''):
            logging.info('autoserv| %s', message.rstrip().decode('utf-8'))
        _autoserv_proc.wait()
        returncode = _autoserv_proc.returncode
        _autoserv_proc = None
    else:
        logging.info('Pretend mode. Would run autoserv command: %s',
                     command)
        returncode = 0
    return returncode


def run_provisioning_job(provision_label, host, info, autotest_path,
                         results_directory, fast_mode,
                         ssh_verbosity=0, ssh_options=None,
                         pretend=False, autoserv_verbose=False):
    """Shell out to autoserv to run provisioning job.

    @param provision_label: Label to provision the machine to.
    @param host: Hostname of DUT.
    @param info: A host_info.HostInfo for the remote host.
    @param autotest_path: Absolute path of autotest directory.
    @param results_directory: Absolute path of directory to store results in.
                              (results will be stored in subdirectory of this).
    @param fast_mode: bool to use fast mode (disables slow autotest features).
    @param ssh_verbosity: SSH verbosity level, passed along to autoserv_utils
    @param ssh_options: Additional ssh options to be passed to autoserv_utils
    @param pretend: If True, will print out autoserv commands rather than
                    running them.
    @param autoserv_verbose: If true, pass the --verbose flag to autoserv.

    @returns: Absolute path of directory where results were stored.

    """
    # TODO(fdeng): When running against a local DUT, autoserv
    # is still hitting the AFE in the lab.
    # provision_QuickProvision checks the current build of DUT by
    # retrieving build info from AFE. crosbug.com/295178
    results_directory = os.path.join(results_directory, 'results-provision')
    _write_host_info(results_directory, _HOST_INFO_SUBDIR, host, info)
    command = autoserv_utils.autoserv_run_job_command(
            os.path.join(autotest_path, 'server'),
            machines=host, job=None, verbose=autoserv_verbose,
            results_directory=results_directory,
            fast_mode=fast_mode, ssh_verbosity=ssh_verbosity,
            ssh_options=ssh_options,
            extra_args=['--provision', '--job-labels', provision_label],
            no_console_prefix=True,
            host_info_subdir=_HOST_INFO_SUBDIR)
    if _run_autoserv(command, pretend) != 0:
        raise TestThatProvisioningError('Command returns non-zero code: %s ' %
                                        command)
    return results_directory


def run_job(job,
            host,
            info,
            autotest_path,
            results_directory,
            fast_mode,
            id_digits=1,
            ssh_verbosity=0,
            ssh_options=None,
            args=None,
            pretend=False,
            autoserv_verbose=False,
            companion_hosts=None,
            dut_servers=None,
            is_cft=False,
            ch_info=None):
    """
    Shell out to autoserv to run an individual test job.

    @param job: A Job object containing the control file contents and other
                relevent metadata for this test.
    @param host: Hostname of DUT to run test against.
    @param info: a host_info.HostInfo for the remote host.
    @param autotest_path: Absolute path of autotest directory.
    @param results_directory: Absolute path of directory to store results in.
                              (results will be stored in subdirectory of this).
    @param fast_mode: bool to use fast mode (disables slow autotest features).
    @param id_digits: The minimum number of digits that job ids should be
                      0-padded to when formatting as a string for results
                      directory.
    @param ssh_verbosity: SSH verbosity level, passed along to autoserv_utils
    @param ssh_options: Additional ssh options to be passed to autoserv_utils
    @param args: String that should be passed as args parameter to autoserv,
                 and then ultimitely to test itself.
    @param pretend: If True, will print out autoserv commands rather than
                    running them.
    @param autoserv_verbose: If true, pass the --verbose flag to autoserv.
    @param companion_hosts: Companion hosts for the test.
    @param dut_servers: DUT servers for the test.
    @param ch_info: hostinfo for companion hosts.

    @returns: a tuple, return code of the job and absolute path of directory
              where results were stored.
    """
    with tempfile.NamedTemporaryFile() as temp_file:
        temp_file.write(job.control_file.encode())
        temp_file.flush()

        name_tail = job.ctrlname.split('/')[-1]
        results_directory = os.path.join(results_directory,
                                         'results-%0*d-%s' % (id_digits, job.id,
                                                              name_tail))
        # Drop experimental keyval in the keval file in the job result folder.
        os.makedirs(results_directory)
        utils.write_keyval(results_directory,
                           {constants.JOB_EXPERIMENTAL_KEY: job.keyvals[
                                   constants.JOB_EXPERIMENTAL_KEY]})
        _write_host_info(results_directory, _HOST_INFO_SUBDIR, host, info)

        if ch_info:
            for chost in companion_hosts.split(" "):
                _write_host_info(results_directory, _HOST_INFO_SUBDIR, chost,
                                 ch_info[chost], False)

        extra_args = [temp_file.name]
        if args:
            extra_args.extend(['--args', args])

        command = autoserv_utils.autoserv_run_job_command(
                os.path.join(autotest_path, 'server'),
                machines=host,
                job=job,
                verbose=autoserv_verbose,
                results_directory=results_directory,
                fast_mode=fast_mode,
                ssh_verbosity=ssh_verbosity,
                ssh_options=ssh_options,
                extra_args=extra_args,
                no_console_prefix=True,
                use_packaging=False,
                host_attributes=info.attributes,
                host_info_subdir=_HOST_INFO_SUBDIR,
                companion_hosts=companion_hosts,
                dut_servers=dut_servers,
                is_cft=is_cft)

        code = _run_autoserv(command, pretend)
        return code, results_directory


def setup_local_afe():
    """
    Setup a local afe database and return a direct_afe object to access it.

    @returns: A autotest_lib.frontend.afe.direct_afe instance.
    """
    # This import statement is delayed until now rather than running at
    # module load time, because it kicks off a local sqlite :memory: backed
    # database, and we don't need that unless we are doing a local run.
    from autotest_lib.frontend import setup_django_lite_environment
    from autotest_lib.frontend.afe import direct_afe
    return direct_afe.directAFE()


def get_predicate_for_test_arg(test):
    """
    Gets a suite predicte function for a given command-line argument.

    @param test: String. An individual TEST command line argument, e.g.
                         'login_CryptohomeMounted' or 'suite:smoke'
    @returns: A (predicate, string) tuple with the necessary suite
              predicate, and a description string of the suite that
              this predicate will produce.
    """
    suitematch = re.match(_SUITE_REGEX, test)
    name_pattern_match = re.match(r'e:(.*)', test)
    file_pattern_match = re.match(r'f:(.*)', test)
    if suitematch:
        suitename = suitematch.group(1)
        return (suite.name_in_tag_predicate(suitename),
                'suite named %s' % suitename)
    if name_pattern_match:
        pattern = '^%s$' % name_pattern_match.group(1)
        return (suite.test_name_matches_pattern_predicate(pattern),
                'suite to match name pattern %s' % pattern)
    if file_pattern_match:
        pattern = '^%s$' % file_pattern_match.group(1)
        return (suite.test_file_matches_pattern_predicate(pattern),
                'suite to match file name pattern %s' % pattern)
    return (suite.test_name_equals_predicate(test),
            'job named %s' % test)


def get_predicate_for_possible_test_arg(test):
    """
    Gets a suite predicte function to calculate the similarity of given test
    and possible tests.

    @param test: String. An individual TEST command line argument, e.g.
                         'login_CryptohomeMounted' or 'suite:smoke'
    @returns: A (predicate, string) tuple with the necessary suite
              predicate, and a description string of the suite that
              this predicate will produce.
    """
    suitematch = re.match(_SUITE_REGEX, test)
    name_pattern_match = re.match(r'e:(.*)', test)
    file_pattern_match = re.match(r'f:(.*)', test)
    if suitematch:
        suitename = suitematch.group(1)
        return (suite.name_in_tag_similarity_predicate(suitename),
                'suite name similar to %s' % suitename)
    if name_pattern_match:
        pattern = '^%s$' % name_pattern_match.group(1)
        return (suite.test_name_similarity_predicate(pattern),
                'job name similar to %s' % pattern)
    if file_pattern_match:
        pattern = '^%s$' % file_pattern_match.group(1)
        return (suite.test_file_similarity_predicate(pattern),
                'suite to match file name similar to %s' % pattern)
    return (suite.test_name_similarity_predicate(test),
            'job name similar to %s' % test)


def add_ssh_identity(temp_directory, ssh_private_key=TEST_KEY_PATH):
    """Add an ssh identity to the agent.

    TODO (sbasi) b/26186193: Add support for test_droid and make TEST_KEY_PATH
    not ChromeOS specific.

    @param temp_directory: A directory to copy the |private key| into.
    @param ssh_private_key: Path to the ssh private key to use for testing.
    """
    # Add the testing key to the current ssh agent.
    if 'SSH_AGENT_PID' in os.environ:
        # Copy the testing key to the temp directory and make it NOT
        # world-readable. Otherwise, ssh-add complains.
        shutil.copy(ssh_private_key, temp_directory)
        key_copy_path = os.path.join(temp_directory,
                                     os.path.basename(ssh_private_key))
        os.chmod(key_copy_path, stat.S_IRUSR | stat.S_IWUSR)
        p = subprocess.Popen(['ssh-add', key_copy_path],
                             stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
        p_out, _ = p.communicate()
        for line in p_out.splitlines():
            logging.info(line)
    else:
        logging.warning('There appears to be no running ssh-agent. Attempting '
                        'to continue without running ssh-add, but ssh commands '
                        'may fail.')


def _auto_detect_labels(remote):
    """Automatically detect host labels and return them.

    Note that the label of board will not be auto-detected.

    @param remote: The hostname of the remote device.

    @returns: the detected labels as a list of strings.
    """
    cros_host = factory.create_host(remote)
    labels_to_create = [label for label in cros_host.get_labels()
                        if not label.startswith(constants.BOARD_PREFIX)]
    return labels_to_create


def get_all_control_files(test, autotest_path):
    """Get all control files for specified test in the given autotest_path.

    @param test: name of the test or suite to fetch
    @praram autotest_path:  Absolute path of autotest installed in sysroot
    """
    (predicate, description) = get_predicate_for_test_arg(test)
    logging.info('Fetching suite for %s...', description)
    return get_control_files(autotest_path=autotest_path, pred=predicate)


def get_possible_tests(test, autotest_path):
    fs_getter = suite.create_fs_getter(autotest_path)

    (similarity_predicate,
     similarity_description) = (get_predicate_for_possible_test_arg(test))

    logging.error('No test found, searching for possible tests with %s',
                  similarity_description)
    possible_tests = suite.find_possible_tests(fs_getter, similarity_predicate)
    raise SystemExit('Found no tests. Check your suite name, test name, '
                     'or test matching wildcard.\nDid you mean any of '
                     'following tests?\n  %s' % '\n  '.join(possible_tests))


def perform_local_run(autotest_path,
                      tests,
                      remote,
                      fast_mode,
                      build=NO_BUILD,
                      board=NO_BOARD,
                      model=NO_MODEL,
                      args=None,
                      pretend=False,
                      ignore_deps=True,
                      results_directory=None,
                      ssh_verbosity=0,
                      ssh_options=None,
                      autoserv_verbose=False,
                      iterations=1,
                      host_attributes={},
                      job_retry=True,
                      companion_hosts=None,
                      minus=[],
                      dut_servers=None,
                      is_cft=False,
                      host_labels=None,
                      label=None):
    """Perform local run of tests.

    This method enforces satisfaction of test dependencies for tests that are
    run as a part of a suite.

    @param autotest_path: Absolute path of autotest installed in sysroot or
                          custom autotest path set by --autotest_dir.
    @param tests: List of strings naming tests and suites to run. Suite strings
                  should be formed like "suite:smoke".
    @param remote: Remote hostname.
    @param fast_mode: bool to use fast mode (disables slow autotest features).
    @param build: String specifying build for local run.
    @param board: String specifying board for local run.
    @param model: String specifying model for local run.
    @param args: String that should be passed as args parameter to autoserv,
                 and then ultimitely to test itself.
    @param pretend: If True, will print out autoserv commands rather than
                    running them.
    @param results_directory: Directory to store results in. Defaults to None,
                              in which case results will be stored in a new
                              subdirectory of /tmp
    @param ssh_verbosity: SSH verbosity level, passed through to
                          autoserv_utils.
    @param ssh_options: Additional ssh options to be passed to autoserv_utils
    @param autoserv_verbose: If true, pass the --verbose flag to autoserv.
    @param iterations: int number of times to schedule tests.
    @param host_attributes: Dict of host attributes to pass into autoserv.
    @param job_retry: If False, tests will not be retried at all.
    @param companion_hosts: companion hosts for the test.
    @param dut_servers: dut servers for the test.
    @param label: Optional label to use for the jobname. Will be appended to
        the keyval file via server_job.

    @returns: A list of return codes each job that has run. Or [1] if
              provision failed prior to running any jobs.
    """
    args = _set_default_servo_args(args)

    # version doesn't really matter for local runs...
    if not host_labels:
        host_labels = [
                u'cros-version:ad_hoc_build',
                u'board:%s' % board,
                u'model:%s' % model
        ]
        if not ignore_deps:
            logging.info('Auto-detecting labels for %s', remote)
            # Auto-detected labels may duplicate explicitly set ones.
            host_labels += list(set(_auto_detect_labels(remote)))

    else:
        host_labels = host_labels.split(" ")
    info = host_info.HostInfo(host_labels, host_attributes)

    # If using test_that, there needs to a hostinfo file (even if blank)
    # for each host (including companions).
    # TODO: Determine if we want to auto-detect labels, and/or expose
    # CLI options for them (which might be required in CFT)
    ch_info = {}
    if companion_hosts:
        for chost in companion_hosts.split(" "):
            chost_labels = []
            if not ignore_deps:
                logging.info('Auto-detecting labels for %s', chost)
                # Auto-detected labels may duplicate explicitly set ones.
                chost_labels += list(set(_auto_detect_labels(chost)))
            ch_info[chost] = host_info.HostInfo(chost_labels, {})

    job_queue = []
    test_num = 0

    m_queue = []
    for m in minus:
        ctrl_files = get_all_control_files(m, autotest_path)
        for ctrl in ctrl_files:
            m_queue.append(ctrl)

    if iterations > 1:
        logging.info("Scheduling for %s iterations", iterations)
    for _ in range(iterations):
        for test in tests:
            ctrl_files = get_all_control_files(test, autotest_path)
            if len(ctrl_files) == 0:
                get_possible_tests(test, autotest_path)
            for control in ctrl_files:
                if any([control.name == no_run.name for no_run in m_queue]):
                    continue
                test_num += 1
                if label:
                    name = label
                else:
                    name = "adhoc/{}".format(control.name)
                job = SimpleJob(name=name,
                                owner='autotest_system',
                                test_num=test_num,
                                ctrlname=control.name)
                job.set_control_file(control)
                if ignore_deps:
                    job_queue.append(job)
                elif job.deps_satisfied(host_labels):
                    job_queue.append(job)
    _set_pyversion(job_queue)
    codes = []
    job_id_digits = 0
    for job in job_queue:
        logging.info('%s jobs in job queue', len(job_queue))
        # could also math.log10... but for a single conversion, not worth.
        job_id_digits = len(str(job.id))
        logging.debug('Running job %s of test %s', job.id, (job.name))
        code, abs_dir = run_job(job=job,
                                host=remote,
                                info=info,
                                autotest_path=autotest_path,
                                results_directory=results_directory,
                                fast_mode=fast_mode,
                                id_digits=job_id_digits,
                                ssh_verbosity=ssh_verbosity,
                                ssh_options=ssh_options,
                                args=args,
                                pretend=pretend,
                                autoserv_verbose=autoserv_verbose,
                                companion_hosts=companion_hosts,
                                dut_servers=dut_servers,
                                is_cft=is_cft,
                                ch_info=ch_info)
        codes.append(code)
        logging.debug("Code: %s, Results in %s", code, abs_dir)

    return codes


def _set_default_servo_args(args):
    """Add default servo arguments for backward compatibitlity.

    See crbug.com/881006 for context.  Some servo related defaults were baked
    into the autotest ServoHost code. These have now been deleted. A side effect
    was that users of test_that relied on these defaults for some tests to work
    magically in the chroot environment.

    Current plan is to add back these defaults to test_that invocations for
    backwards compatibility of these use cases. There is no planned removal date
    for this hack.

    @return modified args str.
    """
    # args is a str with whitespace separated key=value arguments.
    # Avoid parsing args here (to avoid adding another implicit constraint on
    # the exact args format) by adding defaults only in the obvious cases where
    # relevant keys are entirely missing.
    if args is None:
        args = ''
    if 'servo_host' not in args:
        args += ' servo_host=localhost'
    if 'servo_port' not in args:
        args += ' servo_port=9999'
    return args


def sigint_handler(signum, stack_frame):
    #pylint: disable-msg=C0111
    """Handle SIGINT or SIGTERM to a local test_that run.

    This handler sends a SIGINT to the running autoserv process,
    if one is running, giving it up to 5 seconds to clean up and exit. After
    the timeout elapses, autoserv is killed. In either case, after autoserv
    exits then this process exits with status 1.
    """
    # If multiple signals arrive before handler is unset, ignore duplicates
    if not _sigint_handler_lock.acquire(False):
        return
    try:
        # Ignore future signals by unsetting handler.
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        signal.signal(signal.SIGTERM, signal.SIG_IGN)

        logging.warning('Received SIGINT or SIGTERM. Cleaning up and exiting.')
        if _autoserv_proc:
            logging.warning('Sending SIGINT to autoserv process. Waiting up '
                            'to %s seconds for cleanup.',
                            _AUTOSERV_SIGINT_TIMEOUT_SECONDS)
            _autoserv_proc.send_signal(signal.SIGINT)
            timed_out, _ = retry.timeout(_autoserv_proc.wait,
                    timeout_sec=_AUTOSERV_SIGINT_TIMEOUT_SECONDS)
            if timed_out:
                _autoserv_proc.kill()
                logging.warning('Timed out waiting for autoserv to handle '
                                'SIGINT. Killed autoserv.')
    finally:
        _sigint_handler_lock.release() # this is not really necessary?
        sys.exit(1)


def create_results_directory(results_directory=None, board_name=None):
    """Create a results directory.

    If no directory is specified this method will create and return a
    temp directory to hold results. If a directory name is specified this
    method will create a directory at the given path, provided it doesn't
    already exist.

    @param results_directory: The path to the results_directory to create.

    @return results_directory: A path to the results_directory, ready for use.
    """
    if results_directory is None:
        # Create a results_directory as subdir of /tmp
        dirname_prefix='test_that_results_'
        if board_name is not None:
            dirname_prefix += (board_name + '_')
        results_directory = tempfile.mkdtemp(prefix=dirname_prefix)
    else:
        # Delete results_directory if it already exists.
        try:
            shutil.rmtree(results_directory)
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise

        # Create results_directory if it does not exist
        try:
            os.makedirs(results_directory)
        except OSError as e:
            if e.errno != errno.EEXIST:
                raise
    return results_directory


def generate_report(directory,
                    allow_chrome_crashes=False,
                    just_status_code=False,
                    html_report=False,
                    is_cft=False):
    """Parse the test result files in the given directory into a report.

    @param directory: string, the absolute path of the directory to look in
    @param allow_chrome_crashes: boolean, ignore Chrome crashes in the
    report. Default: False, report Chrome crashes.
    @param just_status_code: boolean, skip the report and only parse the files
    to determine whether there were failures. Default: False, generate report.
    """
    test_report_command = [os.path.join(os.path.dirname(__file__),
                                        'generate_test_report')]
    # Experimental test results do not influence the exit code.
    test_report_command.append('--ignore_experimental_tests')
    if is_cft:
        test_report_command.append('--cft')
    if html_report:
        test_report_command.append('--html')
        test_report_command.append('--html-report-dir=%s' % directory)
    if allow_chrome_crashes:
        test_report_command.append('--allow_chrome_crashes')
    if just_status_code:
        test_report_command.append('--just_status_code')
    test_report_command.append(directory)
    status_code = subprocess.call(test_report_command)
    if not just_status_code:
        with open(os.path.join(directory, 'test_report.log'),
                  'w') as report_log:
            subprocess.call(test_report_command, stdout=report_log)
    return status_code


def perform_run_from_autotest_root(autotest_path,
                                   argv,
                                   tests,
                                   remote,
                                   build=NO_BUILD,
                                   board=NO_BOARD,
                                   model=NO_MODEL,
                                   args=None,
                                   pretend=False,
                                   ignore_deps=True,
                                   results_directory=None,
                                   ssh_verbosity=0,
                                   ssh_options=None,
                                   iterations=1,
                                   fast_mode=False,
                                   debug=False,
                                   allow_chrome_crashes=False,
                                   host_attributes={},
                                   job_retry=True,
                                   companion_hosts=None,
                                   minus=[],
                                   dut_servers=None,
                                   is_cft=False,
                                   host_labels=None,
                                   label=None):
    """
    Perform a test_that run, from the |autotest_path|.

    This function is to be called from test_that/test_droid's main() script,
    when tests are executed from the |autotest_path|. It handles all stages
    of a test run that come after the bootstrap into |autotest_path|.

    @param autotest_path: Full absolute path to the autotest root directory.
    @param argv: The arguments list, as passed to main(...)
    @param tests: List of strings naming tests and suites to run. Suite strings
                  should be formed like "suite:smoke".
    @param remote: Remote hostname.
    @param build: String specifying build for local run.
    @param board: String specifying board for local run.
    @param model: String specifying model for local run.
    @param args: String that should be passed as args parameter to autoserv,
                 and then ultimitely to test itself.
    @param pretend: If True, will print out autoserv commands rather than
                    running them.
    @param ignore_deps: If True, test dependencies will be ignored.
    @param results_directory: Directory to store results in. Defaults to None,
                              in which case results will be stored in a new
                              subdirectory of /tmp
    @param ssh_verbosity: SSH verbosity level, passed through to
                          autoserv_utils.
    @param ssh_options: Additional ssh options to be passed to autoserv_utils
    @param autoserv_verbose: If true, pass the --verbose flag to autoserv.
    @param iterations: int number of times to schedule tests.
    @param fast_mode: bool to use fast mode (disables slow autotest features).
    @param debug: Logging and autoserv verbosity.
    @param allow_chrome_crashes: If True, allow chrome crashes.
    @param host_attributes: Dict of host attributes to pass into autoserv.
    @param job_retry: If False, tests will not be retried at all.
    @param companion_hosts: companion hosts for the test.
    @param dut_servers: dut servers for the test.
    @param label: Optional label to use for the jobname. Will be appended to
        the keyval file via server_job.

    @return: A return code that test_that should exit with.
    """
    if results_directory is None or not os.path.exists(results_directory):
        raise ValueError('Expected valid results directory, got %s' %
                          results_directory)

    logging_manager.configure_logging(
            server_logging_config.ServerLoggingConfig(),
            results_dir=results_directory,
            use_console=True,
            verbose=debug,
            debug_log_name='test_that')
    logging.info('Began logging to %s', results_directory)

    logging.debug('test_that command line was: %s', argv)

    signal.signal(signal.SIGINT, sigint_handler)
    signal.signal(signal.SIGTERM, sigint_handler)

    codes = perform_local_run(autotest_path,
                              tests,
                              remote,
                              fast_mode,
                              build,
                              board,
                              model,
                              args=args,
                              pretend=pretend,
                              ignore_deps=ignore_deps,
                              results_directory=results_directory,
                              ssh_verbosity=ssh_verbosity,
                              ssh_options=ssh_options,
                              autoserv_verbose=debug,
                              iterations=iterations,
                              host_attributes=host_attributes,
                              job_retry=job_retry,
                              companion_hosts=companion_hosts,
                              minus=minus,
                              dut_servers=dut_servers,
                              is_cft=is_cft,
                              host_labels=host_labels,
                              label=label)
    if pretend:
        logging.info('Finished pretend run. Exiting.')
        return 0

    final_result = generate_report(results_directory,
                                   allow_chrome_crashes=allow_chrome_crashes,
                                   html_report=True,
                                   is_cft=is_cft)
    try:
        os.unlink(_LATEST_RESULTS_DIRECTORY)
    except OSError:
        pass
    link_target = os.path.relpath(results_directory,
                                  os.path.dirname(_LATEST_RESULTS_DIRECTORY))
    if any(codes):
        logging.error('Autoserv encountered unexpected errors '
                      'when executing jobs.')
        final_result = final_result or 1
    os.symlink(link_target, _LATEST_RESULTS_DIRECTORY)
    logging.info('Finished running tests. Results can be found in %s or %s',
                 results_directory, _LATEST_RESULTS_DIRECTORY)
    return final_result


def _write_host_info(results_dir,
                     host_info_subdir,
                     hostname,
                     info,
                     new_dir=True):
    """ Write HostInfo to a FileStore to be used by autoserv.

    @param results_dir: Path to the results directory.
    @param host_info_subdir: Subdirectory of results directory for host info.
    @param hostname: Hostname passed into autoserv.
    @param info: hosts.HostInfo to write.
    """
    d = os.path.join(results_dir, host_info_subdir)
    if new_dir:
        os.makedirs(d)
    store = file_store.FileStore(os.path.join(d, '%s.store' % hostname))
    store.commit(info)


class SimpleJob(object):
    """
    A Simple job for running autotests without an AFE.

    The goal here is to remove the deps to frontend/afe, and their dependent
    libs. Autotests will be run via 2 methods going forward: Skylab world, and
    test_that. Skylab invokes autoserv directly, bypassing all of this.
    test_that is a CLI, not a UI, and should be split free of the AFE libs.
    """

    def __init__(self,
                 owner,
                 name,
                 control_type='client',
                 test_num=1,
                 ctrlname=None):
        self.owner = owner
        self.name = name
        self.control_type = control_type
        self.id = test_num
        self.keyvals = {'experimental': False}
        self.dependencies = []
        self.py_version = None
        self.ctrlname = ctrlname

    def set_control_file(self, control):
        self.control_file = control.text
        self.control_type = control.test_type.capitalize()
        if hasattr(control, 'dependencies'):
            self.dependencies = set(control.dependencies)
        if control.py_version and control.py_version not in (2, 3):
            raise TestThatControlError(
                    "Test py_version not compatible. Expected 2 or 3 got %s" %
                    control.py_version)
        self.py_version = control.py_version

    def deps_satisfied(self, labels):
        """Verify the deps for this job are satisfied on the given labels"""
        return self.dependencies.issubset(labels)


def _set_pyversion(tests):
    """If there is a py_version specified, set it in the env.

    If not, set it to 2. If 2 is set, lock the entire suite into 2.
    Different versions in the same suite is *not* supported.
    """
    set2 = all(v.py_version == 2 for v in tests)
    set3 = all(v.py_version == 3 for v in tests)
    if not set2 and not set3:
        return
    if set2:
        os.environ['PY_VERSION'] = "2"
    elif set3:
        os.environ['PY_VERSION'] = "3"


def get_control_files(autotest_path, pred):
    cf_getter = suite.create_fs_getter(autotest_path)
    return list(suite.find_and_parse_tests(cf_getter, pred))
