#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

"""Incremental dEQP

This script will run a subset of dEQP test on device to get dEQP dependency.

Usage 1: Compare with a base build to check if any dEQP dependency has
changed. Output a decision if dEQP could be skipped, and a cts-tradefed
command to be used based on the decision.

python3 incremental_deqp.py -s [device serial] -t [test directory] -b
[base build target file] -c [current build target file]

Usage 2: Generate a file containing a list of dEQP dependencies for the
build on device.

python3 incremental_deqp.py -s [device serial] -t [test directory]
--generate_deps_only

"""
import argparse
import importlib
import json
import logging
import os
import pkgutil
import re
import subprocess
import tempfile
import time
import uuid
from target_file_handler import TargetFileHandler
from custom_build_file_handler import CustomBuildFileHandler
from zipfile import ZipFile


DEFAULT_CTS_XML = ('<?xml version="1.0" encoding="utf-8"?>\n'
                   '<configuration description="Runs CTS from a pre-existing CTS installation">\n'
                   '   <include name="cts-common" />\n'
                   '   <include name="cts-exclude" />\n'
                   '   <include name="cts-exclude-instant" />\n'
                   '   <option name="enable-token-sharding" '
                   'value="true" />\n'
                   '   <option name="plan" value="cts" />\n'
                   '</configuration>\n')

INCREMENTAL_DEQP_XML = ('<?xml version="1.0" encoding="utf-8"?>\n'
                        '<configuration description="Runs CTS with incremental dEQP">\n'
                        '   <include name="cts-common" />\n'
                        '   <include name="cts-exclude" />\n'
                        '   <include name="cts-exclude-instant" />\n'
                        '   <option name="enable-token-sharding" '
                        'value="true" />\n'
                        '   <option name="compatibility:exclude-filter" '
                        'value="CtsDeqpTestCases" />\n'
                        '   <option name="plan" value="cts" />\n'
                        '</configuration>\n')

REPORT_FILENAME = 'incremental_dEQP_report.json'

logger = logging.getLogger()


class AtsError(Exception):
  """Error when running incremental dEQP with Android Test Station"""
  pass

class AdbError(Exception):
  """Error when running adb command."""
  pass

class TestError(Exception):
  """Error when running dEQP test."""
  pass

class TestResourceError(Exception):
  """Error with test resource. """
  pass

class BuildHelper(object):
  """Helper class for analyzing build."""

  def __init__(self, custom_handler=False):
    """Init BuildHelper.

    Args:
      custom_handler: use custom build handler.
    """
    self._build_file_handler = TargetFileHandler
    if custom_handler:
      self._build_file_handler = CustomBuildFileHandler


  def compare_base_build_with_current_build(self, deqp_deps, current_build_file,
                                            base_build_file):
    """Compare the difference of current build and base build with dEQP dependency.

    If the difference doesn't involve dEQP dependency, current build could skip dEQP test if
    base build has passed test.

    Args:
      deqp_deps: a set of dEQP dependency.
      current_build_file: current build's file name.
      base_build_file: base build's file name.
    Returns:
      True if current build could skip dEQP, otherwise False.
      Dictionary of changed dependencies and their details.
    """
    print('Comparing base build and current build...')
    current_build_handler = self._build_file_handler(current_build_file)
    current_build_hash = current_build_handler.get_file_hash(deqp_deps)

    base_build_handler = self._build_file_handler(base_build_file)
    base_build_hash = base_build_handler.get_file_hash(deqp_deps)

    return self._compare_build_hash(current_build_hash, base_build_hash)


  def compare_base_build_with_device_files(self, deqp_deps, adb, base_build_file):
    """Compare the difference of files on device and base build with dEQP dependency.

    If the difference doesn't involve dEQP dependency, current build could skip dEQP test if
    base build has passed test.

    Args:
      deqp_deps: a set of dEQP dependency.
      adb: an instance of AdbHelper for current device under test.
      base_build_file: base build file name.
    Returns:
      True if current build could skip dEQP, otherwise False.
      Dictionary of changed dependencies and their details.
    """
    print('Comparing base build and current build on the device...')
    # Get current build's hash.
    current_build_hash = dict()
    for dep in deqp_deps:
      content = adb.run_shell_command('cat ' + dep)
      current_build_hash[dep] = hash(content)

    base_build_handler = self._build_file_handler(base_build_file)
    base_build_hash = base_build_handler.get_file_hash(deqp_deps)

    return self._compare_build_hash(current_build_hash, base_build_hash)

  def get_system_fingerprint(self, build_file):
    """Get build fingerprint in SYSTEM partition.

    Returns:
      String of build fingerprint.
    """
    return self._build_file_handler(build_file).get_system_fingerprint()


  def _compare_build_hash(self, current_build_hash, base_build_hash):
    """Compare the hash value of current build and base build.

    Args:
      current_build_hash: map of current build where key is file name, and value is content hash.
      base_build_hash: map of base build where key is file name and value is content hash.
    Returns:
      Boolean about if two builds' hash is the same.
      Dictionary of changed dependencies and their details.
    """
    changes = {}
    if current_build_hash == base_build_hash:
      print('Done!')
      return True, changes

    for key, val in current_build_hash.items():
      if key not in base_build_hash:
        detail = 'File:{build_file} was not found in base build'.format(build_file=key)
        changes[key] = detail
        logger.info(detail)
      elif base_build_hash[key] != val:
        detail = ('Detected dEQP dependency file difference:{deps}. Base build hash:{base}, '
                  'current build hash:{current}'.format(deps=key, base=base_build_hash[key],
                                                        current=val))
        changes[key] = detail
        logger.info(detail)

    print('Done!')
    return False, changes


class AdbHelper(object):
  """Helper class for running adb."""

  def __init__(self, device_serial=None):
    """Initialize AdbHelper.

    Args:
      device_serial: A string of device serial number, optional.
    """
    self._device_serial = device_serial

  def _run_adb_command(self, *args):
    """Run adb command."""
    adb_cmd = ['adb']
    if self._device_serial:
      adb_cmd.extend(['-s', self._device_serial])
    adb_cmd.extend(args)
    adb_cmd = ' '.join(adb_cmd)
    completed = subprocess.run(adb_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    if completed.returncode != 0:
      raise AdbError('adb command: {cmd} failed with error: {error}'
                     .format(cmd=adb_cmd, error=completed.stderr))

    return completed.stdout

  def push_file(self, source_file, destination_file):
    """Push a file from device to host.

    Args:
      source_file: A string representing file to push.
      destination_file: A string representing target on device to push to.
    """
    return self._run_adb_command('push', source_file, destination_file)

  def pull_file(self, source_file, destination_file):
    """Pull a file to device.

    Args:
      source_file: A string representing file on device to pull.
      destination_file: A string representing target on host to pull to.
    """
    return self._run_adb_command('pull', source_file, destination_file)

  def get_fingerprint(self):
    """Get fingerprint of the device."""
    return self._run_adb_command('shell', 'getprop ro.build.fingerprint').decode("utf-8").strip()

  def run_shell_command(self, command):
    """Run a adb shell command.

    Args:
      command: A string of command to run, executed through 'adb shell'
    """
    return self._run_adb_command('shell', command)


class DeqpDependencyCollector(object):
  """Collect dEQP dependency from device under test."""

  def __init__(self, work_dir, test_dir, adb):
    """Init DeqpDependencyCollector.

    Args:
      work_dir: path of directory for saving script result and logs.
      test_dir: path of directory for incremental dEQP test file.
      adb: an instance of AdbHelper.
    """
    self._work_dir = work_dir
    self._test_dir = test_dir
    self._adb = adb
    # dEQP dependency with pattern below are not an actual file:
    # files has prefix /data/ are not system files, e.g. intermediate files.
    # [vdso] is virtual dynamic shared object.
    # /dmabuf is a temporary file.
    self._exclude_deqp_pattern = re.compile('(^/data/|^\[vdso\]|^/dmabuf)')

  def check_test_log(self, test_file, log_file):
    """Check test's log to see if all tests are executed.

    Args:
      test_file: Name of test .txt file.
      log_content: Name of log file.
    Returns:
      True if all tests are executed, otherwise False.
    """
    test_cnt = 0
    with open(test_file, 'r') as f:
      for _ in f:
        test_cnt += 1

    executed_test_cnt = 0

    with open(log_file, 'r') as f:
      for line in f:
        # 'NotSupported' status means test is not supported in device.
        # TODO(yichunli): Check with graphics team if failed test is allowed.
        if ('StatusCode="Pass"' in line or 'StatusCode="NotSupported"' in line or
            'StatusCode="Fail"' in line):
          executed_test_cnt += 1
    return executed_test_cnt == test_cnt

  def update_dependency(self, deps, dump):
    """Parse perf dump file and update dEQP dependency.

     Below is an example of how dump file looks like:
     630 record comm: type 3, misc 0, size 64
     631   pid 23365, tid 23365, comm simpleperf
     632   sample_id: pid 0, tid 0
     633   sample_id: time 0
     634   sample_id: id 23804
     635   sample_id: cpu 0, res 0
  .......
     684 record comm: type 3, misc 8192, size 64
     685   pid 23365, tid 23365, comm deqp-binary64
     686   sample_id: pid 23365, tid 23365
     687   sample_id: time 595063921159958
     688   sample_id: id 23808
     689   sample_id: cpu 4, res 0
  .......
     698 record mmap2: type 10, misc 8194, size 136
     699   pid 23365, tid 23365, addr 0x58b817b000, len 0x3228000
     700   pgoff 0x0, maj 253, min 9, ino 14709, ino_generation 2575019956
     701   prot 1, flags 6146, filename /data/local/tmp/deqp-binary64
     702   sample_id: pid 23365, tid 23365
     703   sample_id: time 595063921188552
     704   sample_id: id 23808
     705   sample_id: cpu 4, res 0

    Args:
      deps: a set of string containing dEQP dependency.
      dump: perf dump file's name.
    """
    binary_executed = False
    correct_mmap = False
    with open(dump, 'r') as f:
      for line in f:
        # It means dEQP binary starts to be executed.
        if re.search(' comm .*deqp-binary', line):
          binary_executed = True
        if not binary_executed:
          continue
        # We get a new perf event
        if not line.startswith(' '):
          # mmap with misc 1 is not for deqp binary.
          correct_mmap = line.startswith('record mmap') and 'misc 1,' not in line
        # Get file name in memory map.
        if 'filename' in line and correct_mmap:
          deps_file = line[line.find('filename') + 9:].strip()
          if not re.search(self._exclude_deqp_pattern, deps_file):
            deps.add(deps_file)


  def get_test_binary_name(self, test_name):
    """Get dEQP binary's name based on test name.

    Args:
      test_name: name of test.
    Returns:
      dEQP binary's name.
    """
    if test_name.endswith('32'):
      return 'deqp-binary'
    elif test_name.endswith('64'):
      return 'deqp-binary64'
    else:
      raise TestError('Fail to get dEQP binary due to unknonw test name: ' + test_name)

  def get_test_log_name(self, test_name):
    """Get test log's name based on test name.

    Args:
      test_name: name of test.
    Returns:
      test log's name when running dEQP test.
    """
    return test_name + '.qpa'

  def get_test_perf_name(self, test_name):
    """Get perf file's name based on test name.

    Args:
      test_name: name of test.
    Returns:
      perf file's name.
    """
    return test_name + '.data'

  def get_perf_dump_name(self, test_name):
    """Get perf dump file's name based on test name.

    Args:
      test_name: name of test.
    Returns:
      perf dump file's name.
    """
    return test_name + '-perf-dump.txt'

  def get_test_list_name(self, test_name):
    """Get test list file's name based on test name.

    test list file is used to run dEQP test.

    Args:
      test_name: name of test.
    Returns:
      test list file's name.
    """
    if test_name.startswith('vk'):
      return 'vk-master-subset.txt'
    elif test_name.startswith('gles3'):
      return 'gles3-master-subset.txt'
    else:
      raise TestError('Fail to get test list due to unknown test name: ' + test_name)

  def get_deqp_dependency(self):
    """Get dEQP dependency.

    Returns:
      A set of dEQP dependency.
    """
    device_deqp_dir = '/data/local/tmp'
    device_deqp_out_dir = '/data/local/tmp/out'
    test_list = ['vk-32', 'vk-64', 'gles3-32', 'gles3-64']

    # Clean up the device.
    self._adb.run_shell_command('mkdir -p ' + device_deqp_out_dir)

    # Copy test resources to device.
    logger.info(self._adb.push_file(self._test_dir + '/*', device_deqp_dir))

    # Run the dEQP binary with simpleperf
    print('Running a subset of dEQP tests as binary on the device...')
    deqp_deps = set()
    for test in test_list:
      test_file = os.path.join(device_deqp_dir, self.get_test_list_name(test))
      log_file = os.path.join(device_deqp_out_dir, self.get_test_log_name(test))
      perf_file = os.path.join(device_deqp_out_dir, self.get_test_perf_name(test))
      deqp_binary = os.path.join(device_deqp_dir, self.get_test_binary_name(test))
      simpleperf_command = ('"cd {device_deqp_dir} && simpleperf record -o {perf_file} {binary} '
                            '--deqp-caselist-file={test_list} --deqp-log-images=disable '
                            '--deqp-log-shader-sources=disable --deqp-log-filename={log_file} '
                            '--deqp-surface-type=fbo --deqp-surface-width=2048 '
                            '--deqp-surface-height=2048"')
      self._adb.run_shell_command(
          simpleperf_command.format(device_deqp_dir=device_deqp_dir, binary=deqp_binary,
                                    perf_file=perf_file, test_list=test_file, log_file=log_file))

      # Check test log.
      host_log_file = os.path.join(self._work_dir, self.get_test_log_name(test))
      self._adb.pull_file(log_file, host_log_file )
      if not self.check_test_log(os.path.join(self._test_dir, self.get_test_list_name(test)),
                                 host_log_file):
        error_msg = ('Fail to run incremental dEQP because of crashed test. Check test'
                     'log {} for more detail.').format(host_log_file)
        logger.error(error_msg)
        raise TestError(error_msg)
    print('Tests are all passed!')

    # Parse perf dump result to get dependency.
    print('Analyzing dEQP dependency...')
    for test in test_list:
      perf_file = os.path.join(device_deqp_out_dir, self.get_test_perf_name(test))
      dump_file = os.path.join(self._work_dir, self.get_perf_dump_name(test))
      self._adb.run_shell_command('simpleperf dump {perf_file} > {dump_file}'
                                  .format(perf_file=perf_file, dump_file=dump_file))
      self.update_dependency(deqp_deps, dump_file)
    print('Done!')
    return deqp_deps

def _is_deqp_dependency(dependency_name):
  """Check if dependency is related to dEQP."""
  # dEQP dependency with pattern below will not be used to compare build:
  # files has /apex/ prefix are not related to dEQP.
  return not re.search(re.compile('^/apex/'), dependency_name)

def _get_parser():
  parser = argparse.ArgumentParser(description='Run incremental dEQP on devices.')
  parser.add_argument('-s', '--serial', help='Optional. Use device with given serial.')
  parser.add_argument('-t', '--test', help=('Optional. Directory of incremental deqp test file. '
                                            'This directory should have test resources and dEQP '
                                            'binaries.'))
  parser.add_argument('-b', '--base_build', help=('Target file of base build that has passed dEQP '
                                                  'test, e.g. flame-target_files-6935423.zip.'))
  parser.add_argument('-c', '--current_build',
                      help=('Optional. When empty, the script will read files in the build from '
                            'the device via adb. When set, the script will read build files from '
                            'the file provided by this argument. And this file should be the '
                            'current build that is flashed to device, such as a target file '
                            'like flame-target_files-6935424.zip. This argument can be used when '
                            'some dependencies files are not accessible via adb.'))
  parser.add_argument('--generate_deps_only', action='store_true',
                      help=('Run test and generate dEQP dependency list only '
                            'without comparing build.'))
  parser.add_argument('--custom_handler', action='store_true',
                      help='Use custome build file handler')
  parser.add_argument('--ats_mode', action='store_true',
                      help=('Run incremental dEQP with Android Test Station.'))
  parser.add_argument('--userdebug_build', action='store_true',
                      help=('ATS mode option. Current build on device is userdebug.'))
  return parser

def _create_logger(log_file_name):
  """Create logger.

  Args:
    log_file_name: absolute path of the log file.
  Returns:
    a logging.Logger
  """
  logging.basicConfig(filename=log_file_name)
  logger = logging.getLogger()
  logger.setLevel(level=logging.NOTSET)
  return logger

def _save_deqp_deps(deqp_deps, file_name):
  """Save dEQP dependency to file.

  Args:
    deqp_deps: a set of dEQP dependency.
    file_name: name of the file to save dEQP dependency.
  Returns:
    name of the file that saves dEQP dependency.
  """
  with open(file_name, 'w') as f:
    for dep in sorted(deqp_deps):
      f.write(dep+'\n')
  return file_name

def _generate_report(
    report_name,
    base_build_fingerprint,
    current_build_fingerprint,
    deqp_deps,
    extra_deqp_deps,
    deqp_deps_changes):
  """Generate a json report.

  Args:
    report_name: absolute file name of report.
    base_build_fingerprint: fingerprint of the base build.
    current_build_fingerprint: fingerprint of the current build.
    deqp_deps: list of dEQP dependencies generated by the tool.
    extra_deqp_deps: list of extra dEQP dependencies.
    deqp_deps_changes: dictionary of dependency changes.
  """
  data = {}
  data['base_build_fingerprint'] = base_build_fingerprint
  data['current_build_fingerprint'] = current_build_fingerprint
  data['deqp_deps'] = sorted(list(deqp_deps))
  data['extra_deqp_deps'] = sorted(list(extra_deqp_deps))
  data['deqp_deps_changes'] = deqp_deps_changes

  with open(report_name, 'w') as f:
    json.dump(data, f, indent=4)

  print('Incremental dEQP report is generated at: ' + report_name)


def _local_run(args, work_dir):
  """Run incremental dEQP locally.

  Args:
    args: return of parser.parse_args().
    work_dir: path of directory for saving script result and logs.
  """
  print('Logs and simpleperf results will be copied to: ' + work_dir)
  if args.test:
    test_dir = args.test
  else:
    test_dir = os.path.dirname(os.path.abspath(__file__))
  # Extra dEQP dependencies are the files can't be loaded to memory such as firmware.
  extra_deqp_deps = set()
  extra_deqp_deps_file = os.path.join(test_dir, 'extra_deqp_dependency.txt')
  if not os.path.exists(extra_deqp_deps_file):
    if not args.generate_deps_only:
      raise TestResourceError('{test_resource} doesn\'t exist'
                             .format(test_resource=extra_deqp_deps_file))
  else:
    with open(extra_deqp_deps_file, 'r') as f:
      for line in f:
        extra_deqp_deps.add(line.strip())

  if args.serial:
    adb = AdbHelper(args.serial)
  else:
    adb = AdbHelper()

  dependency_collector = DeqpDependencyCollector(work_dir, test_dir, adb)
  deqp_deps = dependency_collector.get_deqp_dependency()
  aggregated_deqp_deps = deqp_deps.union(extra_deqp_deps)

  deqp_deps_file_name = _save_deqp_deps(aggregated_deqp_deps,
                                        os.path.join(work_dir, 'dEQP-dependency.txt'))
  print('dEQP dependency list has been generated in: ' + deqp_deps_file_name)

  if args.generate_deps_only:
    return

  # Compare the build difference with dEQP dependency
  valid_deqp_deps = [dep for dep in aggregated_deqp_deps if _is_deqp_dependency(dep)]
  build_helper = BuildHelper(args.custom_handler)
  if args.current_build:
    skip_dEQP, changes = build_helper.compare_base_build_with_current_build(
        valid_deqp_deps, args.current_build, args.base_build)
  else:
    skip_dEQP, changes = build_helper.compare_base_build_with_device_files(
        valid_deqp_deps, adb, args.base_build)
  if skip_dEQP:
    print('Congratulations, current build could skip dEQP test.\n'
          'If you run CTS through suite, you could pass filter like '
          '\'--exclude-filter CtsDeqpTestCases\'.')
  else:
    print('Sorry, current build can\'t skip dEQP test because dEQP dependency has been '
          'changed.\nPlease check logs for more details.')

  _generate_report(os.path.join(work_dir, REPORT_FILENAME),
                   build_helper.get_system_fingerprint(args.base_build),
                   adb.get_fingerprint(),
                   deqp_deps,
                   extra_deqp_deps,
                   changes)

def _generate_cts_xml(out_dir, content):
  """Generate cts configuration for Android Test Station.

  Args:
   out_dir: output directory for cts confiugration.
   content: configuration content.
  """
  with open(os.path.join(out_dir, 'incremental_deqp.xml'), 'w') as f:
    f.write(content)


def _ats_run(args, work_dir):
  """Run incremental dEQP with Android Test Station.

  Args:
    args: return of parser.parse_args().
    work_dir: path of directory for saving script result and logs.
  """
  # Extra dEQP dependencies are the files can't be loaded to memory such as firmware.
  extra_deqp_deps = set()
  with open(os.path.join(work_dir, 'extra_deqp_dependency.txt'), 'r') as f:
    for line in f:
      if line.strip():
        extra_deqp_deps.add(line.strip())

  android_serials = os.getenv('ANDROID_SERIALS')
  if not android_serials:
    raise AtsError('Fail to read environment variable ANDROID_SERIALS.')
  first_device_serial = android_serials.split(',')[0]
  adb = AdbHelper(first_device_serial)

  dependency_collector = DeqpDependencyCollector(work_dir,
                                                 os.path.join(work_dir, 'test_resources'), adb)
  deqp_deps = dependency_collector.get_deqp_dependency()
  aggregated_deqp_deps = deqp_deps.union(extra_deqp_deps)

  deqp_deps_file_name = _save_deqp_deps(aggregated_deqp_deps,
                                        os.path.join(work_dir, 'dEQP-dependency.txt'))

  if args.generate_deps_only:
    _generate_cts_xml(work_dir, DEFAULT_CTS_XML)
    return

  # Compare the build difference with dEQP dependency
  valid_deqp_deps = [dep for dep in aggregated_deqp_deps if _is_deqp_dependency(dep)]

  # base build target file is from test resources.
  base_build_target = os.path.join(work_dir, 'base_build_target_files')
  build_helper = BuildHelper(args.custom_handler)
  if args.userdebug_build:
    current_build_fingerprint = adb.get_fingerprint()
    skip_dEQP, changes = build_helper.compare_base_build_with_device_files(
        valid_deqp_deps, adb, base_build_target)
  else:
    current_build_target = os.path.join(work_dir, 'current_build_target_files')
    current_build_fingerprint = build_helper.get_system_fingerprint(current_build_target)
    skip_dEQP, changes = build_helper.compare_base_build_with_current_build(
        valid_deqp_deps, current_build_target, base_build_target)
  if skip_dEQP:
    _generate_cts_xml(work_dir, INCREMENTAL_DEQP_XML)
  else:
    _generate_cts_xml(work_dir, DEFAULT_CTS_XML)

  _generate_report(os.path.join(*[work_dir, 'logs', REPORT_FILENAME]),
                   build_helper.get_system_fingerprint(base_build_target),
                   current_build_fingerprint,
                   deqp_deps,
                   extra_deqp_deps,
                   changes)

def main():
  parser = _get_parser()
  args = parser.parse_args()
  if not args.generate_deps_only and not args.base_build and not args.ats_mode:
    parser.error('Base build argument: \'-b [file] or --base_build [file]\' '
                 'is required to compare build.')

  work_dir = ''
  log_file_name = ''
  if args.ats_mode:
    work_dir = os.getenv('TF_WORK_DIR')
    log_file_name = os.path.join(*[work_dir, 'logs', 'incremental-deqp-log-'+str(uuid.uuid4())])
  else:
    work_dir = tempfile.mkdtemp(prefix='incremental-deqp-'
                                + time.strftime("%Y%m%d-%H%M%S"))
    log_file_name = os.path.join(work_dir, 'incremental-deqp-log')
  global logger
  logger = _create_logger(log_file_name)

  if args.ats_mode:
    _ats_run(args, work_dir)
  else:
    _local_run(args, work_dir)

if __name__ == '__main__':
  main()

