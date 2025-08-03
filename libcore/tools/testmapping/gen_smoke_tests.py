#!/usr/bin/env python2
#
#   Copyright 2020 - The Android Open Source Project
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

"""Tool to generate data for smoke tests."""

from __future__ import print_function

import collections
import datetime
import gzip
import operator
import os
import re
import tempfile

import jinja2
import util

ANDROID_REPOSITORY_ROOT = util.android_repository_root()
LIBCORE_DIR = os.path.join(ANDROID_REPOSITORY_ROOT, 'libcore')
LOGS_DIR = os.path.join(LIBCORE_DIR, 'smoketest')
REQUIRED_RUNS = 3
CTS_LOG_LINE_PATTERN = (r'(\d+)-(\d+) +(\d+):(\d+):(\d+)\.(\d+) +\d+ +\d+ +'
                        r'[^ ]+ (CtsTestRunListener|TestRunner) *: *(.+)')
THIS_YEAR = datetime.datetime.now().year
START_MATCHER = ('CtsTestRunListener', r'Now executing\s*:\s*(.+)')
TESTS_RAN_MATCHER = ('TestRunner', r'started\s*:.*')
END_MATCHER = ('CtsTestRunListener', r'Total memory\s*:.+')
HARD_END_MATCHER = ('TestRunner', r'run finished\s*:.+')
JAVA_CLASS_PATTERN = r'(?:([a-zA-Z_][a-zA-Z0-9_]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)*)\.)?[a-zA-Z_][a-zA-Z0-9_]*'
MAX_RUN_TIME = datetime.timedelta(minutes=10)
OUT_DIR = tempfile.mkdtemp()
ROLL_UP_TEST_CLASSES_TO_PACKAGE = False
CTS_MODULE_NAME = 'CtsLibcoreTestCases'

TEST_MAPPING_TEMPLATE = jinja2.Template("""
{
  "presubmit": [
    {
      "name": "{{module}}",
      "options": [{% for test in exclusions %}
        {
          "exclude-filter": "{{test}}"
        }{% if not loop.last %},{% endif %}{% endfor %}
      ]
    }
  ]
}
""".strip())


def find_all_log_files():
  """Returns a list of the log files to read."""
  if not os.path.isdir(LOGS_DIR):
    raise Exception('Could not find logs directory ' + LOGS_DIR)
  filenames = os.listdir(LOGS_DIR)
  if not filenames:
    raise Exception('Found empty logs directory ' + LOGS_DIR)
  if len(filenames) != REQUIRED_RUNS:
    raise Exception('Expected to find exactly %d files in %s, found %d' %
                    (REQUIRED_RUNS, LOGS_DIR, len(filenames)))
  return map(lambda f: os.path.join(LOGS_DIR, f), filenames)


def read_cts_logs(filename):
  """Read CTS entries from a log file.

  Args:
    filename: The name of the file to read.

  Yields:
    Tuples of timestamps (as datetimes), log sources, and log messages.
  """
  print('Reading ' + util.printable_path(filename))
  with gzip.open(filename, mode='rt') as log_file:
    for line in log_file:
      cts_match = re.match(CTS_LOG_LINE_PATTERN, line)
      if cts_match:
        assert len(cts_match.groups()) == 8
        timestamp = datetime.datetime(
            year=THIS_YEAR,
            month=int(cts_match.group(1)),
            day=int(cts_match.group(2)),
            hour=int(cts_match.group(3)),
            minute=int(cts_match.group(4)),
            second=int(cts_match.group(5)),
            microsecond=1000 * int(cts_match.group(6)))
        source = cts_match.group(7)
        message = cts_match.group(8)
        yield (timestamp, source, message)


def match(matcher, got_source, message):
  """Check whether a log line matches requirements.

  Args:
    matcher: A pair of the required log source and a pattern to match messages.
    got_source: The actual log source.
    message: The actual message.

  Returns:
    A MatchObject from the pattern match against the message, or None.
  """
  (want_source, pattern) = matcher
  if got_source == want_source:
    return re.match(pattern, message)
  else:
    return None


def parse_running_times(filename):
  """Parse the running times from a log file.

  Args:
    filename: The name of the file to read.

  Yields:
    Pairs of test names and running times (as timedeltas). Also emits the
    overall elapsed time from the start of the first test to the end of the last
    test with the key 'OVERALL'.

  Raises:
    Exception: The log file entries didn't look like we expected.
  """
  executing = None
  start_timestamp = None
  ran_tests = False
  overall_start_timestamp = None
  overall_end_timestamp = None
  for (timestamp, source, message) in read_cts_logs(filename):
    start_match = match(START_MATCHER, source, message)
    tests_ran_match = match(TESTS_RAN_MATCHER, source, message)
    end_match = match(END_MATCHER, source, message)
    hard_end_match = match(HARD_END_MATCHER, source, message)
    if not executing:
      if start_match:
        assert len(start_match.groups()) == 1
        executing = start_match.group(1)
        start_timestamp = timestamp
        if not overall_start_timestamp:
          overall_start_timestamp = timestamp
    else:
      if start_match:
        raise Exception(
            'Found start for %s while waiting for end for %s at %s in %s' %
            (start_match.group(1), executing, str(timestamp), filename))
      if tests_ran_match:
        ran_tests = True
      if end_match or hard_end_match:
        running_time = timestamp - start_timestamp
        # We see two types of execution in the logs. One appears to be some kind
        # of dry run which doesn't actually run any tests (and completes
        # significantly faster). We want the one that actually runs tests.
        if ran_tests:
          yield (executing, running_time)
        executing = None
        start_timestamp = None
        ran_tests = False
        overall_end_timestamp = timestamp
  if executing:
    raise Exception('Reached EOF while waiting for end for %s in %s' %
                    (executing, filename))
  yield ('OVERALL', overall_end_timestamp - overall_start_timestamp)


def collect_running_times(filenames):
  """Collect running times from some log file.

  Args:
    filenames: The names of the files to read.

  Returns:
    A tuple containing (1) a dictionary mapping test names to sets of running
    times (as timedeltas), and (2) a list of overall running times (i.e. elapsed
    times from the start of the first test to the end of the last test).
  """
  times_by_test = collections.defaultdict(list)
  output_path = os.path.join(OUT_DIR, 'test_times.txt')
  overall_times = []
  with open(output_path, 'w') as output:
    for filename in filenames:
      for (test_name, time) in parse_running_times(filename):
        output.write('%s: %g ms\n' % (test_name, time.total_seconds() * 1000))
        if test_name == 'OVERALL':
          overall_times.append(time)
        else:
          times_by_test[test_name].append(time)
  print('Wrote test times to ' + util.printable_path(output_path))
  return (times_by_test, overall_times)


def process_running_times(times_by_test):
  """Processes the collected running times.

  Args:
    times_by_test: A dictionary mapping test names to sets of running times.

  Returns:
    A dictionary mapping test names to fastest running times.
  """
  for (test, times) in times_by_test.iteritems():
    if len(times) != REQUIRED_RUNS:
      print('Warning: Only found %d runs for %s' % (len(times), test))
  return {test: min(times) for (test, times) in times_by_test.iteritems()}


def calculate_overhead_ratio(overall_times, fastest_time_by_test):
  """Calculates a ratio for the actual overall time to the sum of test times.

   The actual overall times are the elapsed times from the start of the first
   test to the end of the last test. The average of these is used. The ratio is
   taken with the sume of the fastest time for each test (which is what will be
   used for scoring).

  Args:
    overall_times: A list of overall running times.
    fastest_time_by_test: A dictionary mapping test names to fastest running
      times.

  Returns:
    The ratio.
  """
  average_overall_time = sum(overall_times,
                             datetime.timedelta(0)) / len(overall_times)
  total_time_by_test = sum(fastest_time_by_test.values(), datetime.timedelta(0))
  ratio = (
      average_overall_time.total_seconds() / total_time_by_test.total_seconds())
  print(
      'Average time for run is %g seconds, sum of fastest test times is %g, ratio is %g'
      % (average_overall_time.total_seconds(),
         total_time_by_test.total_seconds(), ratio))
................................................................................
  # N.B. Possible future enhancement: Currently, because# we take the fastest of
  # three runs, a real run will always be slightly slower than we predict. We
  # could multiply in another overhead factor to account for this, e.g. by
  # looking at the ratio of the mean of the three to the fastest of the three.
  # (This factor should be applied globally rather than individually to each
  # test so as not to penalize tests which happened to have a slow run or two.)
  # This is not a high priority since for now we can just set MAX_RUN_TIME a bit
  # low to allow for this.
  return ratio


def get_parent(name):
  """Returns the parent of a Java class or package name."""
  class_match = re.match(JAVA_CLASS_PATTERN, name)
  if not class_match:
    raise Exception('Could not parse Java class name ' + name)
  assert len(class_match.groups()) == 1
  return class_match.group(1)


def group_times_by_package(times_by_test):
  """Groups the test classes by package name, summing the times.

  Args:
    times_by_test: A dictionary mapping test names to fastest running times.

  Returns:
    A dictionary mapping package names to total fastest running times.
  """
  time_by_package = collections.defaultdict(datetime.timedelta)
  for (clazz, time) in times_by_test.iteritems():
    package = get_parent(clazz)
    if package:  # A few tests have no package. They're weird, let's skip them.
      time_by_package[package] = time_by_package[package] + time
  output_path = os.path.join(OUT_DIR, 'package_times.txt')
  with open(output_path, 'w') as output:
    for (package, time) in time_by_package.iteritems():
      output.write('%s: %s ms\n' % (package, time.total_seconds() * 1000.0))
  print('Wrote package times to ' + util.printable_path(output_path))
  return time_by_package


def find_tests_to_run(time_by_test, overhead_ratio):
  """Finds the tests to actually run.

  The tests chosen will be the fastest set such that their total time, when
  multiplied by the overhead ratio, is less than the maximum.

  Args:
    time_by_test: A dictionary mapping test names to total fastest running
      times. The names can be packages, classes, or a mixture of the two.
    overhead_ratio: A ratio for the actual overall time to the sum of test
      times.

  Returns:
    A list of test names whose running times are below the threshold.

  Raises:
    Exception: The total running time of all the tests is below the threhold.
  """
  test_inclusions = {test: False for test in time_by_test.keys()}
  included = 0
  total_time = datetime.timedelta()
  output_path = os.path.join(OUT_DIR, 'included_tests.txt')
  adjusted_max_run_time_seconds = MAX_RUN_TIME.total_seconds() / overhead_ratio
  with open(output_path, 'w') as output:
    for (test, time) in sorted(
        time_by_test.iteritems(), key=operator.itemgetter(1)):
      if (total_time + time).total_seconds() <= adjusted_max_run_time_seconds:
        test_inclusions[test] = True
        included += 1
        total_time += time
        output.write('%s: %g ms -> %g ms\n' %
                     (test, time.total_seconds() * 1000.0,
                      total_time.total_seconds() * 1000.0))
      else:
        print('Can run fastest %d of %d tests in %g * %g = %g seconds' %
              (included, len(time_by_test), total_time.total_seconds(),
               overhead_ratio, total_time.total_seconds() * overhead_ratio))
        print('Wrote tests to include to ' + util.printable_path(output_path))
        return test_inclusions
  raise Exception('Apparently we can run all the tests? This smells wrong.')


def build_test_tree(tests):
  """Builds a tree of tests.

  Args:
    tests: The list of tests to build into a tree. These can be packages,
      classes, or a mixture of the two.

  Returns:
    A dictionary mapping every test's ancestors to its children. The roots
    appear as children of None.
  """
  tree = collections.defaultdict(set)
  for test in tests:
    while True:
      parent = get_parent(test)
      tree[parent].add(test)
      if parent:
        test = parent
      else:
        break
  return tree


def build_exclusion_list(test_inclusions):
  """Builds a list of tests to exclude.

  Args:
    test_inclusions: A dictionary mapping test names to whether or not they
      should be included in the smoke tests. The names can be packages, classes,
      or a mixture of the two.

  Returns:
    A list of the exclusions. These could be individual tests, or packages or
    some part of the package hierarchy if they are to be entirely excluded.
  """
  tree = build_test_tree(test_inclusions.keys())
  exclusions = []

  # We do a DFS of the tree, rolling up exclusions as far as possible, i.e. if
  # an entire branch is excluded, we exclude that branch rather than all of its
  # leaves.

  def visit(test):
    """Visitor for a DFS of the tree.

    Args:
      test: The test to visit (either a class or package name).

    Returns:
      Whether or not the parent should include this node in the tree.

    Raises:
      Exception: The tree had an unexpected structure.
    """
    if test in test_inclusions:
      # We only expect to have inclusion status for the leaves of the tree.
      # Check that this is the case.
      if test in tree:
        raise Exception('The name %s is used for a leaf and a branch!' % test)
      # Return to the parent node whether this leaf is included.
      return test_inclusions[test]
    else:
      # We expect to have inclusion status for all leaves of the tree. Check
      # that this is the case.
      if test not in tree:
        raise Exception('Leaf %s has no inclusion status!' % test)
      # Look at all the children of this node.
      any_child_included = False
      child_exclusions = []
      for child in tree[test]:
        child_included = visit(child)
        if child_included:
          any_child_included = True
        else:
          child_exclusions.append(child)
      # If any children are included, we will count this node as included, so we
      # have to add any excluded children to the exclusion list.
      if any_child_included:
        for child in child_exclusions:
          exclusions.append(child)
      # Now return whether this node should be counted as included, which means
      # whether any children are included.
      return any_child_included

  # Start the DFS at each root of the tree.
  for root in tree[None]:
    root_included = visit(root)
    # If any of the roots are counted as excluded, add them to the list.
    if not root_included:
      exclusions.append(root)

  # Finally, sort and return the list of exclusions.
  return sorted(exclusions)


def self_test():
  """Self-test for the build_include_exclude_list logic."""
  test_inclusions = {
      'a.x': True,
      'a.y': True,
      'b.x': True,
      'b.y': False,
      'c.x': False,
      'c.y': False,
      'd.x.m': True,
      'd.x.n': True,
      'd.y.m': True,
      'd.y.n': False,
      'd.z.m': False,
      'd.z.n': False,
  }
  expected_exclusions = [
      'b.y',
      'c',
      'd.y.n',
      'd.z',
  ]
  actual_exclusions = build_exclusion_list(test_inclusions)
  if actual_exclusions != expected_exclusions:
    raise Exception('Self test failed! Expected %s but got %s' %
                    (expected_exclusions, actual_exclusions))


def main():
  """The main method."""
  self_test()
  filenames = find_all_log_files()
  (times_by_test, overall_times) = collect_running_times(filenames)
  fastest_time_by_test = process_running_times(times_by_test)
  overhead_ratio = calculate_overhead_ratio(overall_times, fastest_time_by_test)
  if ROLL_UP_TEST_CLASSES_TO_PACKAGE:
    time_by_package = group_times_by_package(fastest_time_by_test)
    test_inclusions = find_tests_to_run(time_by_package, overhead_ratio)
  else:
    test_inclusions = find_tests_to_run(fastest_time_by_test, overhead_ratio)
  exclusions = build_exclusion_list(test_inclusions)
  output_path = os.path.join(LIBCORE_DIR, 'TEST_MAPPING')
  with open(output_path, 'w') as output:
    output.write(
        TEST_MAPPING_TEMPLATE.render(
            module=CTS_MODULE_NAME, exclusions=exclusions))
  print('Wrote test mapping to ' + util.printable_path(output_path))


main()
