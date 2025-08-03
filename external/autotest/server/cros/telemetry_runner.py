# Lint as: python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import abc
import json
import logging
import numbers
import os
import tempfile
import six
import sys

import numpy

import common
from autotest_lib.client.common_lib import error, utils
from autotest_lib.server.cros import telemetry_setup

TELEMETRY_RUN_BENCHMARKS_SCRIPT = 'tools/perf/run_benchmark'
TELEMETRY_RUN_TESTS_SCRIPT = 'tools/telemetry/run_tests'
TELEMETRY_RUN_GPU_TESTS_SCRIPT = 'content/test/gpu/run_gpu_integration_test.py'
TELEMETRY_TIMEOUT_MINS = 150

DUT_CHROME_ROOT = '/usr/local/telemetry/src'

CHART_JSON_RESULT = 'results-chart.json'
HISTOGRAM_SET_RESULT = 'histograms.json'
PROFILE_ARTIFACTS = 'artifacts'

# Result Statuses
SUCCESS_STATUS = 'SUCCESS'
WARNING_STATUS = 'WARNING'
FAILED_STATUS = 'FAILED'

# A list of telemetry tests that cannot run on dut.
ON_DUT_BLOCKLIST = [
        'loading.desktop',  # crbug/882299
        'rendering.desktop',  # crbug/882291
]


class TelemetryResult(object):
    """Class to represent the results of a telemetry run.

    This class represents the results of a telemetry run, whether it ran
    successful, failed or had warnings.
    """

    def __init__(self, exit_code=0, stdout='', stderr=''):
        """Initializes this TelemetryResultObject instance.

        @param status: Status of the telemtry run.
        @param stdout: Stdout of the telemetry run.
        @param stderr: Stderr of the telemetry run.
        """
        if exit_code == 0:
            self.status = SUCCESS_STATUS
        else:
            self.status = FAILED_STATUS

        self._stdout = stdout
        self._stderr = stderr
        self.output = '\n'.join([stdout, stderr])


class TelemetryRunnerFactory(object):
    """A factory class to determine TelemetryRunner subclass to be used.

    The TelemetryRunner class, today, has various ways to execute the telemetry
    test. The test can be executed locally (using a tool like test_that) or can
    be executed in the Lab environment - for this usecase, either the drone OR
    the devserver can be used.

    A Factory class offloads this determination overhead from the clients. Users
    of the TelemetryRunner class are highly encouraged to go through this
    Factory class while determining the correct TelemetryRunner subclass.
    """

    def get_runner(self,
                   host,
                   local=False,
                   telemetry_on_dut=True,
                   is_lacros=False):
        """Method to determine which TelemetryRunner subclass to use."""
        if local:
            return LocalTelemetryRunner(host, telemetry_on_dut)
        else:
            return DroneTelemetryRunner(host, telemetry_on_dut, is_lacros)


class TelemetryRunner(six.with_metaclass(abc.ABCMeta, object)):
    """Class responsible for telemetry for a given build.

    This class will extract and install telemetry environment and is
    responsible for executing the telemetry benchmarks and returning their
    output to the caller.
    """

    def __init__(self, host, telemetry_on_dut=True, is_lacros=False):
        """Initializes this telemetry runner instance.

        If telemetry is not installed for this build, it will be.

        @param host: Host where the test will be run.
        @param telemetry_on_dut: If set, telemetry itself (the test harness)
                                 will run on dut.
                                 It decides browser=[system|cros-chrome]
        @param is_lacros: If true, run telemetry on lacros chrome, by defining
                          browser=lacros-chrome. It is only valid for remote
                          test mode.
        """
        self._host = host
        self._telemetry_path = None
        self._perf_value_writer = None
        self._setup_telemetry()
        self._telemetry_on_dut = telemetry_on_dut
        self._benchmark_deps = None
        self._is_lacros = is_lacros
        logging.debug('Telemetry Path: %s', self._telemetry_path)

    def __enter__(self):
        """Called while entering context manager; does nothing."""
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        """Called while exiting context manager."""

    @abc.abstractmethod
    def _setup_telemetry(self):
        """Set up telemetry environment."""

    def _get_telemetry_cmd(self, script, test_or_benchmark, output_format,
                           *args, **kwargs):
        """Build command to execute telemetry based on script and benchmark.

        @param script: Telemetry script we want to run. For example:
                       [path_to_telemetry_src]/src/tools/telemetry/run_tests.
        @param test_or_benchmark: Name of the test or benchmark we want to run,
                                  with the page_set (if required) as part of
                                  the string.
        @param output_format: Format of the json result file: histogram or
                              chart-json.
        @param args: additional list of arguments to pass to the script.
        @param kwargs: additional list of keyword arguments to pass to the
                       script.

        @returns Full telemetry command to execute the script.
        """
        telemetry_cmd = []
        no_verbose = kwargs.get('no_verbose', False)

        output_dir = (DUT_CHROME_ROOT
                      if self._telemetry_on_dut else self._telemetry_path)
        # Create a temp directory to hold single test run.
        if self._perf_value_writer:
            output_dir = os.path.join(
                    output_dir, self._perf_value_writer.tmpdir.strip('/'))

        if self._telemetry_on_dut:
            telemetry_cmd.extend([
                    self._host.ssh_command(alive_interval=900,
                                           connection_attempts=4),
                    sys.executable,
                    script,
                    '--output-format=%s' % output_format,
                    '--output-dir=%s' % output_dir,
                    '--browser=system',
            ])
        else:
            browser = 'lacros-chrome' if self._is_lacros else 'cros-chrome'
            telemetry_cmd.extend([
                    sys.executable,
                    script,
                    '--browser=%s' % browser,
                    '--output-format=%s' % output_format,
                    '--output-dir=%s' % output_dir,
                    '--remote=%s' % self._host.hostname,
            ])
            if self._host.host_port != self._host.hostname and self._host.host_port:
                # If the user specify a different port for the DUT, we should
                # use different telemetry argument to set it up.
                #
                # e.g. When user is running experiments with ssh port
                # forwarding, they specify remote as 127.0.0.1:2222. Now
                # host_port is 127.0.0.1:2222 and hostname is 127.0.0.1
                # port is 2222
                telemetry_cmd.append('--remote-ssh-port=%s' % self._host.port)

        if not no_verbose:
            telemetry_cmd.append('--verbose')
        telemetry_cmd.extend(args)
        telemetry_cmd.append(test_or_benchmark)

        return ' '.join(telemetry_cmd)

    def _scp_telemetry_results_cmd(self, perf_results_dir, output_format,
                                   artifacts):
        """Build command to copy the telemetry results from the work directory.

        @param perf_results_dir: directory path where test output is to be
                                 collected.
        @param output_format: Format of the json result file: histogram or
                              chart-json.
        @param artifacts: Whether we want to copy artifacts directory.

        @returns SCP command to copy the results json to the specified
                 directory.
        """
        if not perf_results_dir:
            return ''

        output_filename = CHART_JSON_RESULT
        if output_format == 'histograms':
            output_filename = HISTOGRAM_SET_RESULT
        scp_cmd = []
        if self._telemetry_on_dut:
            scp_cmd.extend(['scp', '-r'])
            scp_cmd.append(
                    self._host.make_ssh_options(
                            alive_interval=900, connection_attempts=4))
            if not self._host.is_default_port:
                scp_cmd.append('-P %d' % self._host.port)
            src = 'root@%s:%s' % (self._host.hostname, DUT_CHROME_ROOT)
        else:
            # Use rsync --remove-source-file to move rather than copy from
            # work dir. This is because each run will generate certain artifacts
            # and will not be removed after, making result size getting larger.
            # We don't do this for results on DUT because 1) rsync doesn't work
            # 2) DUT will be reflashed frequently and no need to worry about
            # result size.
            scp_cmd.extend(['rsync', '-avz', '--remove-source-files'])
            src = self._telemetry_path

        if self._perf_value_writer:
            src = os.path.join(src, self._perf_value_writer.tmpdir.strip('/'))

        scp_cmd.append(os.path.join(src, output_filename))

        # Copy artifacts back to result directory if needed.
        if artifacts:
            scp_cmd.append(os.path.join(src, PROFILE_ARTIFACTS))

        scp_cmd.append(perf_results_dir)
        return ' '.join(scp_cmd)

    def _run_cmd(self, cmd):
        """Execute an command in a external shell and capture the output.

        @param cmd: String of is a valid shell command.

        @returns The standard out, standard error and the integer exit code of
                 the executed command.
        """
        logging.debug('Running: %s', cmd)

        output = six.StringIO()
        error_output = six.StringIO()
        exit_code = 0
        try:
            result = utils.run(
                    cmd,
                    stdout_tee=output,
                    stderr_tee=error_output,
                    timeout=TELEMETRY_TIMEOUT_MINS * 60)
            exit_code = result.exit_status
        except error.CmdError as e:
            logging.debug('Error occurred executing.')
            exit_code = e.result_obj.exit_status

        stdout = output.getvalue()
        stderr = error_output.getvalue()
        logging.debug('Completed with exit code: %d.\nstdout:%s\n'
                      'stderr:%s', exit_code, stdout, stderr)
        return stdout, stderr, exit_code

    def _run_telemetry(self, script, test_or_benchmark, output_format, *args,
                       **kwargs):
        """Runs telemetry on a dut.

        @param script: Telemetry script we want to run. For example:
                       [path_to_telemetry_src]/src/tools/telemetry/run_tests.
        @param test_or_benchmark: Name of the test or benchmark we want to run,
                                 with the page_set (if required) as part of the
                                 string.
        @param args: additional list of arguments to pass to the script.
        @param kwargs: additional list of keyword arguments to pass to the
                       script.

        @returns A TelemetryResult Instance with the results of this telemetry
                 execution.
        """
        # TODO (sbasi crbug.com/239933) add support for incognito mode.

        telemetry_cmd = self._get_telemetry_cmd(script, test_or_benchmark,
                                                output_format, *args, **kwargs)
        logging.info('Running Telemetry: %s', telemetry_cmd)

        stdout, stderr, exit_code = self._run_cmd(telemetry_cmd)

        return TelemetryResult(
                exit_code=exit_code, stdout=stdout, stderr=stderr)

    def _run_scp(self, perf_results_dir, output_format, artifacts=False):
        """Runs telemetry on a dut.

        @param perf_results_dir: The local directory that results are being
                                 collected.
        @param output_format: Format of the json result file.
        @param artifacts: Whether we want to copy artifacts directory.
        """
        scp_cmd = self._scp_telemetry_results_cmd(perf_results_dir,
                                                  output_format, artifacts)
        logging.debug('Retrieving Results: %s', scp_cmd)
        _, _, exit_code = self._run_cmd(scp_cmd)
        if exit_code != 0:
            raise error.TestFail('Unable to retrieve results.')

        if output_format == 'histograms':
            # Converts to chart json format.
            input_filename = os.path.join(perf_results_dir,
                                          HISTOGRAM_SET_RESULT)
            output_filename = os.path.join(perf_results_dir, CHART_JSON_RESULT)
            histograms = json.loads(open(input_filename).read())
            chartjson = TelemetryRunner.convert_chart_json(histograms)
            with open(output_filename, 'w') as fout:
                fout.write(json.dumps(chartjson, indent=2))

    def _run_test(self, script, test, *args):
        """Runs a telemetry test on a dut.

        @param script: Which telemetry test script we want to run. Can be
                       telemetry's base test script or the ChromeOS specific
                       test script.
        @param test: Telemetry test we want to run.
        @param args: additional list of arguments to pass to the script.

        @returns A TelemetryResult Instance with the results of this telemetry
                 execution.
        """
        logging.debug('Running telemetry test: %s', test)
        telemetry_script = os.path.join(self._telemetry_path, script)
        result = self._run_telemetry(telemetry_script, test, 'chartjson',
                                     *args)
        if result.status is FAILED_STATUS:
            raise error.TestFail('Telemetry test %s failed.' % test)
        return result

    def run_telemetry_test(self, test, *args):
        """Runs a telemetry test on a dut.

        @param test: Telemetry test we want to run.
        @param args: additional list of arguments to pass to the telemetry
                     execution script.

        @returns A TelemetryResult Instance with the results of this telemetry
                 execution.
        """
        return self._run_test(TELEMETRY_RUN_TESTS_SCRIPT, test, *args)

    def run_telemetry_benchmark(self,
                                benchmark,
                                perf_value_writer=None,
                                *args,
                                **kwargs):
        """Runs a telemetry benchmark on a dut.

        @param benchmark: Benchmark we want to run.
        @param perf_value_writer: Should be an instance with the function
                                  output_perf_value(), if None, no perf value
                                  will be written. Typically this will be the
                                  job object from an autotest test.
        @param args: additional list of arguments to pass to the telemetry
                     execution script.
        @param kwargs: additional list of keyword arguments to pass to the
                       telemetry execution script.

        @returns A TelemetryResult Instance with the results of this telemetry
                 execution.
        """
        logging.debug('Running telemetry benchmark: %s', benchmark)

        self._perf_value_writer = perf_value_writer

        if benchmark in ON_DUT_BLOCKLIST:
            self._telemetry_on_dut = False

        output_format = kwargs.get('ex_output_format', '')

        if not output_format:
            output_format = 'histograms'

        if self._telemetry_on_dut:
            telemetry_script = os.path.join(DUT_CHROME_ROOT,
                                            TELEMETRY_RUN_BENCHMARKS_SCRIPT)
            self._ensure_deps(self._host, benchmark)
        else:
            telemetry_script = os.path.join(self._telemetry_path,
                                            TELEMETRY_RUN_BENCHMARKS_SCRIPT)

        result = self._run_telemetry(telemetry_script, benchmark,
                                     output_format, *args, **kwargs)

        if result.status is WARNING_STATUS:
            raise error.TestWarn('Telemetry Benchmark: %s'
                                 ' exited with Warnings.\nOutput:\n%s\n' %
                                 (benchmark, result.output))
        elif result.status is FAILED_STATUS:
            raise error.TestFail('Telemetry Benchmark: %s'
                                 ' failed to run.\nOutput:\n%s\n' %
                                 (benchmark, result.output))
        elif '[  PASSED  ] 0 tests.' in result.output:
            raise error.TestWarn('Telemetry Benchmark: %s exited successfully,'
                                 ' but no test actually passed.\nOutput\n%s\n'
                                 % (benchmark, result.output))
        if perf_value_writer:
            artifacts = kwargs.get('artifacts', False)
            self._run_scp(perf_value_writer.resultsdir, output_format,
                          artifacts)
        return result

    def run_gpu_integration_test(self, test, *args):
        """Runs a gpu test on a dut.

        @param test: Gpu test we want to run.
        @param args: additional list of arguments to pass to the telemetry
                     execution script.

        @returns A TelemetryResult instance with the results of this telemetry
                 execution.
        """
        script = os.path.join(DUT_CHROME_ROOT, TELEMETRY_RUN_GPU_TESTS_SCRIPT)
        cmd = [
                self._host.ssh_command(alive_interval=900,
                                       connection_attempts=4), sys.executable,
                script
        ]
        cmd.extend(args)
        cmd.append(test)
        cmd = ' '.join(cmd)
        stdout, stderr, exit_code = self._run_cmd(cmd)

        if exit_code:
            raise error.TestFail('Gpu Integration Test: %s'
                                 ' failed to run.' % test)

        return TelemetryResult(
                exit_code=exit_code, stdout=stdout, stderr=stderr)

    def _ensure_deps(self, dut, test_name):
        """
        Ensure the dependencies are locally available on DUT.

        @param dut: The autotest host object representing DUT.
        @param test_name: Name of the telemetry test.
        """
        # Get DEPs using host's telemetry.
        # Example output, fetch_benchmark_deps.py --output-deps=deps octane:
        # {'octane': ['tools/perf/page_sets/data/octane_002.wprgo']}
        fetch_path = os.path.join(self._telemetry_path, 'tools', 'perf',
                                  'fetch_benchmark_deps.py')
        # Use a temporary file for |deps_path| to avoid race conditions. The
        # created temporary file is assigned to |self._benchmark_deps| to make
        # it valid until |self| is destroyed.
        self._benchmark_deps = tempfile.NamedTemporaryFile(
                prefix='fetch_benchmark_deps_result.', suffix='.json')
        deps_path = self._benchmark_deps.name
        command_fetch = (f'{sys.executable} {fetch_path} '
                         f'--output-deps={deps_path} {test_name}')
        command_get = f'cat {deps_path}'

        logging.info('Getting DEPs: %s', command_fetch)
        _, _, exit_code = self._run_cmd(command_fetch)
        if exit_code != 0:
            raise error.TestFail('Error occurred while fetching DEPs.')
        stdout, _, exit_code = self._run_cmd(command_get)
        if exit_code != 0:
            raise error.TestFail('Error occurred while getting DEPs.')

        # Download DEPs to DUT.
        # send_file() relies on rsync over ssh. Couldn't be better.
        deps = json.loads(stdout)
        for dep in deps[test_name]:
            src = os.path.join(self._telemetry_path, dep)
            dst = os.path.join(DUT_CHROME_ROOT, dep)
            if not os.path.isfile(src):
                raise error.TestFail('Error occurred while saving DEPs.')
            logging.info('Copying: %s -> %s', src, dst)
            dut.send_file(src, dst)

    @staticmethod
    def convert_chart_json(histogram_set):
        """
        Convert from histogram set to chart json format.

        @param histogram_set: result in histogram set format.

        @returns result in chart json format.
        """
        value_map = {}

        # Gets generic set values.
        for obj in histogram_set:
            if 'type' in obj and obj['type'] == 'GenericSet':
                value_map[obj['guid']] = obj['values']

        charts = {}
        benchmark_name = ''
        benchmark_desc = ''

        # Checks the unit test for how this conversion works.
        for obj in histogram_set:
            if 'name' not in obj or 'sampleValues' not in obj:
                continue
            metric_name = obj['name']
            diagnostics = obj['diagnostics']
            if 'stories' in diagnostics:
                story_name = value_map[diagnostics['stories']][0]
            else:
                story_name = 'default'
            local_benchmark_name = value_map[diagnostics['benchmarks']][0]
            if benchmark_name == '':
                benchmark_name = local_benchmark_name
                if 'benchmarkDescriptions' in diagnostics:
                    benchmark_desc = value_map[
                            diagnostics['benchmarkDescriptions']][0]
            if benchmark_name != local_benchmark_name:
                logging.warning(
                        'There are more than 1 benchmark names in the'
                        'result. old: %s, new: %s', benchmark_name,
                        local_benchmark_name)
                continue

            unit = obj['unit']
            smaller_postfixes = ('_smallerIsBetter', '-')
            bigger_postfixes = ('_biggerIsBetter', '+')
            all_postfixes = smaller_postfixes + bigger_postfixes

            improvement = 'up'
            for postfix in smaller_postfixes:
                if unit.endswith(postfix):
                    improvement = 'down'
            for postfix in all_postfixes:
                if unit.endswith(postfix):
                    unit = unit[:-len(postfix)]
                    break

            if unit == 'unitless':
                unit = 'score'

            values = [
                    x for x in obj['sampleValues']
                    if isinstance(x, numbers.Number)
            ]
            if metric_name not in charts:
                charts[metric_name] = {}
            charts[metric_name][story_name] = {
                    'improvement_direction': improvement,
                    'name': metric_name,
                    'std': numpy.std(values),
                    'type': 'list_of_scalar_values',
                    'units': unit,
                    'values': values
            }

        # Adds summaries.
        for metric_name in charts:
            values = []
            metric_content = charts[metric_name]
            for story_name in metric_content:
                story_content = metric_content[story_name]
                values += story_content['values']
                metric_type = story_content['type']
                units = story_content['units']
                improvement = story_content['improvement_direction']
            values.sort()
            std = numpy.std(values)
            metric_content['summary'] = {
                    'improvement_direction': improvement,
                    'name': metric_name,
                    'std': std,
                    'type': metric_type,
                    'units': units,
                    'values': values
            }

        benchmark_metadata = {
                'description': benchmark_desc,
                'name': benchmark_name,
                'type': 'telemetry_benchmark'
        }
        return {
                'benchmark_description': benchmark_desc,
                'benchmark_metadata': benchmark_metadata,
                'benchmark_name': benchmark_name,
                'charts': charts,
                'format_version': 1.0
        }


class LocalTelemetryRunner(TelemetryRunner):
    """Specialized TelemetryRunner to handle local telemetry test runs."""

    def __init__(self, *args, **kwargs):
        """Initialize LocalTelemetryRunner.

        The telemetry test will run locally. Depending on whether
        telemetry_on_dut is True or False, there can be possible combinations
        for the execution of this test:

        telemetry_on_dut=False:
        python run_benchmark --browser=cros-chrome --remote=[dut] [test]

        telemetry_on_dut=True:
        ssh [dut] python run_benchmark --browser=system [test]

        @param args: The list of arguments to be passed. See Base class for a
                     complete list of accepted arguments.
        @param kwargs: Any keyword arguments to be passed. See Base class for a
                       complete list of accepted keyword arguments.
        """
        super(LocalTelemetryRunner, self).__init__(*args, **kwargs)

    def _setup_telemetry(self):
        """Setup Telemetry to use local path to its sources.

        First look for chrome source root, either externally mounted, or inside
        the chroot.  Prefer chrome-src-internal source tree to chrome-src.
        """
        TELEMETRY_DIR = 'src'
        CHROME_LOCAL_SRC = '/var/cache/chromeos-cache/distfiles/target/'
        CHROME_EXTERNAL_SRC = os.path.expanduser('~/chrome_root/')

        logging.debug('Setting up telemetry for local testing')

        sources_list = ('chrome-src-internal', 'chrome-src')
        dir_list = [CHROME_EXTERNAL_SRC]
        dir_list.extend(
                [os.path.join(CHROME_LOCAL_SRC, x) for x in sources_list])
        if 'CHROME_ROOT' in os.environ:
            dir_list.insert(0, os.environ['CHROME_ROOT'])

        telemetry_src = ''
        for dir in dir_list:
            if os.path.exists(dir):
                telemetry_src = os.path.join(dir, TELEMETRY_DIR)
                break
        else:
            raise error.TestError('Telemetry source directory not found.')

        self._telemetry_path = telemetry_src


class DroneTelemetryRunner(TelemetryRunner):
    """Handle telemetry test setup on the drone.

    Users of this class are strongly advised to use this class as a context
    manager. Since the setup for telemetry environment happens on the drone, it
    is imperative that this setup be cleaned up once the test is done. Using
    this class as a context manager will transfer the burden of clean up from
    the user to Python.
    """

    def __init__(self, *args, **kwargs):
        """Initialize DroneTelemetryRunner.

        The telemetry test will run on the drone. Depending on whether
        telemetry_on_dut is True or False, there can be possible combinations
        for the execution of this test:

        telemetry_on_dut=False:
        python run_benchmark --browser=cros-chrome --remote=[dut] [test]

        telemetry_on_dut=True:
        ssh [dut] python run_benchmark --browser=system [test]

        @param args: The list of arguments to be passed. See Base class for a
                     complete list of accepted arguments.
        @param kwargs: Any keyword arguments to be passed. See Base class for a
                       complete list of accepted keyword arguments.
        """
        self._telemetry_setup = None
        super(DroneTelemetryRunner, self).__init__(*args, **kwargs)

    def __enter__(self):
        """Called while entering context manager; does nothing."""
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        """Called while exiting context manager; cleans up temp files."""
        logging.info('Cleaning up the telemetry environment on the drone.')
        self._telemetry_setup.Cleanup()

    def _setup_telemetry(self):
        """Setup Telemetry on the drone."""
        logging.debug('Setting up telemetry on the drone')
        info = self._host.host_info_store.get()
        if not info.build:
            logging.error('Unable to locate build label for host: %s.',
                          self._host.host_port)
            raise error.AutotestError('Failed to grab build for host %s.' %
                                      self._host.host_port)

        logging.debug('Setting up telemetry for build: %s', info.build)
        try:
            self._telemetry_setup = telemetry_setup.TelemetrySetup(
                    hostname=self._host.hostname, build=info.build)
            self._telemetry_path = self._telemetry_setup.Setup()
        except telemetry_setup.TelemetrySetupError as e:
            raise error.AutotestError('Telemetry Environment could not be '
                                      'setup: %s.' % e)
