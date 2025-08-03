# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Command Line Translator for atest."""

# pylint: disable=too-many-lines

from __future__ import print_function

from dataclasses import dataclass
import fnmatch
import json
import logging
import os
from pathlib import Path
import re
import sys
import time
from typing import List, Set

from atest import atest_error
from atest import atest_utils
from atest import bazel_mode
from atest import constants
from atest import test_finder_handler
from atest import test_mapping
from atest.atest_enum import DetectType, ExitCode
from atest.metrics import metrics
from atest.metrics import metrics_utils
from atest.test_finders import module_finder
from atest.test_finders import test_finder_utils
from atest.test_finders import test_info

FUZZY_FINDER = 'FUZZY'
CACHE_FINDER = 'CACHE'
TESTNAME_CHARS = {'#', ':', '/'}

MAINLINE_LOCAL_DOC = 'go/mainline-local-build'

# Pattern used to identify comments start with '//' or '#' in TEST_MAPPING.
_COMMENTS_RE = re.compile(r'(?m)[\s\t]*(#|//).*|(\".*?\")')
_COMMENTS = frozenset(['//', '#'])


@dataclass
class TestIdentifier:
  """Class that stores test and the corresponding mainline modules (if any)."""

  test_name: str
  module_names: List[str]
  binary_names: List[str]


class CLITranslator:
  """CLITranslator class contains public method translate() and some private

  helper methods. The atest tool can call the translate() method with a list
  of strings, each string referencing a test to run. Translate() will
  "translate" this list of test strings into a list of build targets and a
  list of TradeFederation run commands.

  Translation steps for a test string reference:
      1. Narrow down the type of reference the test string could be, i.e.
         whether it could be referencing a Module, Class, Package, etc.
      2. Try to find the test files assuming the test string is one of these
         types of reference.
      3. If test files found, generate Build Targets and the Run Command.
  """

  def __init__(
      self,
      mod_info=None,
      print_cache_msg=True,
      bazel_mode_enabled=False,
      host=False,
      bazel_mode_features: List[bazel_mode.Features] = None,
  ):
    """CLITranslator constructor

    Args:
        mod_info: ModuleInfo class that has cached module-info.json.
        print_cache_msg: Boolean whether printing clear cache message or not.
          True will print message while False won't print.
        bazel_mode_enabled: Boolean of args.bazel_mode.
        host: Boolean of args.host.
        bazel_mode_features: List of args.bazel_mode_features.
    """
    self.mod_info = mod_info
    self.root_dir = os.getenv(constants.ANDROID_BUILD_TOP, os.sep)
    self._bazel_mode = bazel_mode_enabled
    self._bazel_mode_features = bazel_mode_features or []
    self._host = host
    self.enable_file_patterns = False
    self.msg = ''
    if print_cache_msg:
      self.msg = (
          '(Test info has been cached for speeding up the next '
          'run, if test info needs to be updated, please add -c '
          'to clean the old cache.)'
      )
    self.fuzzy_search = True

  # pylint: disable=too-many-locals
  # pylint: disable=too-many-branches
  # pylint: disable=too-many-statements
  def _find_test_infos(self, test, tm_test_detail) -> List[test_info.TestInfo]:
    """Return set of TestInfos based on a given test.

    Args:
        test: A string representing test references.
        tm_test_detail: The TestDetail of test configured in TEST_MAPPING files.

    Returns:
        List of TestInfos based on the given test.
    """
    test_infos = []
    test_find_starts = time.time()
    test_found = False
    test_finders = []
    test_info_str = ''
    find_test_err_msg = None
    test_identifier = parse_test_identifier(test)
    test_name = test_identifier.test_name
    if not self._verified_mainline_modules(test_identifier):
      return test_infos
    find_methods = test_finder_handler.get_find_methods_for_test(
        self.mod_info, test
    )
    if self._bazel_mode:
      find_methods = [
          bazel_mode.create_new_finder(
              self.mod_info,
              f,
              host=self._host,
              enabled_features=self._bazel_mode_features,
          )
          for f in find_methods
      ]
    for finder in find_methods:
      # For tests in TEST_MAPPING, find method is only related to
      # test name, so the details can be set after test_info object
      # is created.
      try:
        found_test_infos = finder.find_method(
            finder.test_finder_instance, test_name
        )
      except atest_error.TestDiscoveryException as e:
        find_test_err_msg = e
      if found_test_infos:
        finder_info = finder.finder_info
        for t_info in found_test_infos:
          test_deps = set()
          if self.mod_info:
            test_deps = self.mod_info.get_install_module_dependency(
                t_info.test_name
            )
            logging.debug(
                '(%s) Test dependencies: %s', t_info.test_name, test_deps
            )
          if tm_test_detail:
            t_info.data[constants.TI_MODULE_ARG] = tm_test_detail.options
            t_info.from_test_mapping = True
            t_info.host = tm_test_detail.host
          if finder_info != CACHE_FINDER:
            t_info.test_finder = finder_info
          mainline_modules = test_identifier.module_names
          if mainline_modules:
            t_info.test_name = test
            # TODO(b/261607500): Replace usages of raw_test_name
            # with test_name once we can ensure that it doesn't
            # break any code that expects Mainline modules in the
            # string.
            t_info.raw_test_name = test_name
            # TODO: remove below statement when soong can also
            # parse TestConfig and inject mainline modules information
            # to module-info.
            for mod in mainline_modules:
              t_info.add_mainline_module(mod)

          # Only add dependencies to build_targets when they are in
          # module info
          test_deps_in_mod_info = [
              test_dep
              for test_dep in test_deps
              if self.mod_info.is_module(test_dep)
          ]
          for dep in test_deps_in_mod_info:
            t_info.add_build_target(dep)
          test_infos.append(t_info)
        test_found = True
        print("Found '%s' as %s" % (atest_utils.mark_green(test), finder_info))
        if finder_info == CACHE_FINDER and test_infos:
          test_finders.append(list(test_infos)[0].test_finder)
        test_finders.append(finder_info)
        test_info_str = ','.join([str(x) for x in found_test_infos])
        break
    if not test_found:
      print('No test found for: {}'.format(atest_utils.mark_red(test)))
      if self.fuzzy_search:
        f_results = self._fuzzy_search_and_msg(test, find_test_err_msg)
        if f_results:
          test_infos.extend(f_results)
          test_found = True
          test_finders.append(FUZZY_FINDER)
    metrics.FindTestFinishEvent(
        duration=metrics_utils.convert_duration(time.time() - test_find_starts),
        success=test_found,
        test_reference=test,
        test_finders=test_finders,
        test_info=test_info_str,
    )
    # Cache test_infos by default except running with TEST_MAPPING which may
    # include customized flags and they are likely to mess up other
    # non-test_mapping tests.
    if test_infos and not tm_test_detail:
      atest_utils.update_test_info_cache(test, test_infos)
      if self.msg:
        print(self.msg)
    return test_infos

  def _verified_mainline_modules(self, test_identifier: TestIdentifier) -> bool:
    """Verify the test with mainline modules is acceptable.

    The test must be a module and mainline modules are in module-info.
    The syntax rule of mainline modules will check in build process.
    The rule includes mainline modules are sorted alphabetically, no space,
    and no duplication.

    Args:
        test_identifier: a TestIdentifier object.

    Returns:
        True if this test is acceptable. Otherwise, print the reason and
        return False.
    """
    mainline_binaries = test_identifier.binary_names
    if not mainline_binaries:
      return True

    # Exit earlier when any test name or mainline modules are not valid.
    if not self._valid_modules(test_identifier):
      return False

    # Exit earlier if Atest cannot find relationship between the test and
    # the mainline binaries.
    return self._declared_mainline_modules(test_identifier)

  def _valid_modules(self, identifier: TestIdentifier) -> bool:
    """Determine the test_name and mainline modules are modules."""
    if not self.mod_info.is_module(identifier.test_name):
      print(
          'Error: "{}" is not a testable module.'.format(
              atest_utils.mark_red(identifier.test_name)
          )
      )
      return False

    # Exit earlier if the given mainline modules are unavailable in the
    # branch.
    unknown_modules = [
        module
        for module in identifier.module_names
        if not self.mod_info.is_module(module)
    ]
    if unknown_modules:
      print(
          'Error: Cannot find {} in module info!'.format(
              atest_utils.mark_red(', '.join(unknown_modules))
          )
      )
      return False

    # Exit earlier if found unsupported `capex` files.
    unsupported_binaries = []
    for name in identifier.module_names:
      info = self.mod_info.get_module_info(name)
      if info.get('installed'):
        for bin in info.get('installed'):
          if not re.search(atest_utils.MAINLINE_MODULES_EXT_RE, bin):
            unsupported_binaries.append(bin)
    if unsupported_binaries:
      print(
          'The output format {} are not in a supported format; '
          'did you run mainline local setup script? '
          'Please refer to {}.'.format(
              atest_utils.mark_red(', '.join(unsupported_binaries)),
              atest_utils.mark_yellow(MAINLINE_LOCAL_DOC),
          )
      )
      return False

    return True

  def _declared_mainline_modules(self, identifier: TestIdentifier) -> bool:
    """Determine if all mainline modules were associated to the test."""
    test = identifier.test_name
    mainline_binaries = identifier.binary_names
    if not self.mod_info.has_mainline_modules(test, mainline_binaries):
      print(
          'Error: Mainline modules "{}" were not defined for {} in '
          'neither build file nor test config.'.format(
              atest_utils.mark_red(', '.join(mainline_binaries)),
              atest_utils.mark_red(test),
          )
      )
      return False

    return True

  def _fuzzy_search_and_msg(self, test, find_test_err_msg):
    """Fuzzy search and print message.

    Args:
        test: A string representing test references
        find_test_err_msg: A string of find test error message.

    Returns:
        A list of TestInfos if found, otherwise None.
    """
    # Currently we focus on guessing module names. Append names on
    # results if more finders support fuzzy searching.
    if atest_utils.has_chars(test, TESTNAME_CHARS):
      return None
    mod_finder = module_finder.ModuleFinder(self.mod_info)
    results = mod_finder.get_fuzzy_searching_results(test)
    if len(results) == 1 and self._confirm_running(results):
      found_test_infos = mod_finder.find_test_by_module_name(results[0])
      # found_test_infos is a list with at most 1 element.
      if found_test_infos:
        return found_test_infos
    elif len(results) > 1:
      self._print_fuzzy_searching_results(results)
    else:
      print('No matching result for {0}.'.format(test))
    if find_test_err_msg:
      print(f'{atest_utils.mark_magenta(find_test_err_msg)}\n')
    return None

  def _get_test_infos(self, tests, test_mapping_test_details=None):
    """Return set of TestInfos based on passed in tests.

    Args:
        tests: List of strings representing test references.
        test_mapping_test_details: List of TestDetail for tests configured in
          TEST_MAPPING files.

    Returns:
        Set of TestInfos based on the passed in tests.
    """
    test_infos = []
    if not test_mapping_test_details:
      test_mapping_test_details = [None] * len(tests)
    for test, tm_test_detail in zip(tests, test_mapping_test_details):
      found_test_infos = self._find_test_infos(test, tm_test_detail)
      test_infos.extend(found_test_infos)
    return test_infos

  def _confirm_running(self, results):
    """Listen to an answer from raw input.

    Args:
        results: A list of results.

    Returns:
        True is the answer is affirmative.
    """
    return atest_utils.prompt_with_yn_result(
        'Did you mean {0}?'.format(atest_utils.mark_green(results[0])), True
    )

  def _print_fuzzy_searching_results(self, results):
    """Print modules when fuzzy searching gives multiple results.

    If the result is lengthy, just print the first 10 items only since we
    have already given enough-accurate result.

    Args:
        results: A list of guessed testable module names.
    """
    atest_utils.colorful_print(
        'Did you mean the following modules?', constants.WHITE
    )
    for mod in results[:10]:
      atest_utils.colorful_print(mod, constants.GREEN)

  def filter_comments(self, test_mapping_file):
    """Remove comments in TEST_MAPPING file to valid format.

    Only '//' and '#' are regarded as comments.

    Args:
        test_mapping_file: Path to a TEST_MAPPING file.

    Returns:
        Valid json string without comments.
    """

    def _replace(match):
      """Replace comments if found matching the defined regular

      expression.

      Args:
          match: The matched regex pattern

      Returns:
          "" if it matches _COMMENTS, otherwise original string.
      """
      line = match.group(0).strip()
      return '' if any(map(line.startswith, _COMMENTS)) else line

    with open(test_mapping_file, encoding='utf-8') as json_file:
      return re.sub(_COMMENTS_RE, _replace, json_file.read())

  def _read_tests_in_test_mapping(self, test_mapping_file):
    """Read tests from a TEST_MAPPING file.

    Args:
        test_mapping_file: Path to a TEST_MAPPING file.

    Returns:
        A tuple of (all_tests, imports), where
        all_tests is a dictionary of all tests in the TEST_MAPPING file,
            grouped by test group.
        imports is a list of test_mapping.Import to include other test
            mapping files.
    """
    all_tests = {}
    imports = []
    test_mapping_dict = {}
    try:
      test_mapping_dict = json.loads(self.filter_comments(test_mapping_file))
    except json.JSONDecodeError as e:
      msg = 'Test Mapping file has invalid format: %s.' % e
      logging.debug(msg)
      atest_utils.colorful_print(msg, constants.RED)
      sys.exit(ExitCode.INVALID_TM_FORMAT)
    for test_group_name, test_list in test_mapping_dict.items():
      if test_group_name == constants.TEST_MAPPING_IMPORTS:
        for import_detail in test_list:
          imports.append(test_mapping.Import(test_mapping_file, import_detail))
      else:
        grouped_tests = all_tests.setdefault(test_group_name, set())
        tests = []
        for test in test_list:
          if (
              self.enable_file_patterns
              and not test_mapping.is_match_file_patterns(
                  test_mapping_file, test
              )
          ):
            continue
          test_name = parse_test_identifier(test['name']).test_name
          test_mod_info = self.mod_info.name_to_module_info.get(test_name)
          if not test_mod_info:
            print(
                'WARNING: %s is not a valid build target and '
                'may not be discoverable by TreeHugger. If you '
                'want to specify a class or test-package, '
                "please set 'name' to the test module and use "
                "'options' to specify the right tests via "
                "'include-filter'.\nNote: this can also occur "
                'if the test module is not built for your '
                'current lunch target.\n'
                % atest_utils.mark_red(test['name'])
            )
          elif not any(
              x in test_mod_info.get('compatibility_suites', [])
              for x in constants.TEST_MAPPING_SUITES
          ):
            print(
                'WARNING: Please add %s to either suite: %s for '
                'this TEST_MAPPING file to work with TreeHugger.'
                % (
                    atest_utils.mark_red(test['name']),
                    atest_utils.mark_green(constants.TEST_MAPPING_SUITES),
                )
            )
          tests.append(test_mapping.TestDetail(test))
        grouped_tests.update(tests)
    return all_tests, imports

  def _get_tests_from_test_mapping_files(self, test_groups, test_mapping_files):
    """Get tests in the given test mapping files with the match group.

    Args:
        test_groups: Groups of tests to run. Default is set to `presubmit` and
          `presubmit-large`.
        test_mapping_files: A list of path of TEST_MAPPING files.

    Returns:
        A tuple of (tests, all_tests, imports), where,
        tests is a set of tests (test_mapping.TestDetail) defined in
        TEST_MAPPING file of the given path, and its parent directories,
        with matching test_group.
        all_tests is a dictionary of all tests in TEST_MAPPING files,
        grouped by test group.
        imports is a list of test_mapping.Import objects that contains the
        details of where to import a TEST_MAPPING file.
    """
    all_imports = []
    # Read and merge the tests in all TEST_MAPPING files.
    merged_all_tests = {}
    for test_mapping_file in test_mapping_files:
      all_tests, imports = self._read_tests_in_test_mapping(test_mapping_file)
      all_imports.extend(imports)
      for test_group_name, test_list in all_tests.items():
        grouped_tests = merged_all_tests.setdefault(test_group_name, set())
        grouped_tests.update(test_list)
    tests = set()
    for test_group in test_groups:
      temp_tests = set(merged_all_tests.get(test_group, []))
      tests.update(temp_tests)
      if test_group == constants.TEST_GROUP_ALL:
        for grouped_tests in merged_all_tests.values():
          tests.update(grouped_tests)
    return tests, merged_all_tests, all_imports

  # pylint: disable=too-many-arguments
  # pylint: disable=too-many-locals
  def _find_tests_by_test_mapping(
      self,
      path='',
      test_groups=None,
      file_name=constants.TEST_MAPPING,
      include_subdirs=False,
      checked_files=None,
  ):
    """Find tests defined in TEST_MAPPING in the given path.

    Args:
        path: A string of path in source. Default is set to '', i.e., CWD.
        test_groups: A List of test groups to run.
        file_name: Name of TEST_MAPPING file. Default is set to `TEST_MAPPING`.
          The argument is added for testing purpose.
        include_subdirs: True to include tests in TEST_MAPPING files in sub
          directories.
        checked_files: Paths of TEST_MAPPING files that have been checked.

    Returns:
        A tuple of (tests, all_tests), where,
        tests is a set of tests (test_mapping.TestDetail) defined in
        TEST_MAPPING file of the given path, and its parent directories,
        with matching test_group.
        all_tests is a dictionary of all tests in TEST_MAPPING files,
        grouped by test group.
    """
    path = os.path.realpath(path)
    # Default test_groups is set to [`presubmit`, `presubmit-large`].
    if not test_groups:
      test_groups = constants.DEFAULT_TEST_GROUPS
    test_mapping_files = set()
    all_tests = {}
    test_mapping_file = os.path.join(path, file_name)
    if os.path.exists(test_mapping_file):
      test_mapping_files.add(test_mapping_file)
    # Include all TEST_MAPPING files in sub-directories if `include_subdirs`
    # is set to True.
    if include_subdirs:
      test_mapping_files.update(atest_utils.find_files(path, file_name))
    # Include all possible TEST_MAPPING files in parent directories.
    while path not in (self.root_dir, os.sep):
      path = os.path.dirname(path)
      test_mapping_file = os.path.join(path, file_name)
      if os.path.exists(test_mapping_file):
        test_mapping_files.add(test_mapping_file)

    if checked_files is None:
      checked_files = set()
    test_mapping_files.difference_update(checked_files)
    checked_files.update(test_mapping_files)
    if not test_mapping_files:
      return test_mapping_files, all_tests

    tests, all_tests, imports = self._get_tests_from_test_mapping_files(
        test_groups, test_mapping_files
    )

    # Load TEST_MAPPING files from imports recursively.
    if imports:
      for import_detail in imports:
        path = import_detail.get_path()
        # (b/110166535 #19) Import path might not exist if a project is
        # located in different directory in different branches.
        if path is None:
          logging.warning('Failed to import TEST_MAPPING at %s', import_detail)
          continue
        # Search for tests based on the imported search path.
        import_tests, import_all_tests = self._find_tests_by_test_mapping(
            path, test_groups, file_name, include_subdirs, checked_files
        )
        # Merge the collections
        tests.update(import_tests)
        for group, grouped_tests in import_all_tests.items():
          all_tests.setdefault(group, set()).update(grouped_tests)

    return tests, all_tests

  def _get_test_mapping_tests(self, args, exit_if_no_test_found=True):
    """Find the tests in TEST_MAPPING files.

    Args:
        args: arg parsed object. exit_if_no_test(s)_found: A flag to exit atest
          if no test mapping tests found.

    Returns:
        A tuple of (test_names, test_details_list), where
        test_names: a list of test name
        test_details_list: a list of test_mapping.TestDetail objects for
            the tests in TEST_MAPPING files with matching test group.
    """
    # Pull out tests from test mapping
    src_path = ''
    test_groups = constants.DEFAULT_TEST_GROUPS
    if args.tests:
      if ':' in args.tests[0]:
        src_path, test_group = args.tests[0].split(':')
        test_groups = [test_group]
      else:
        src_path = args.tests[0]

    test_details, all_test_details = self._find_tests_by_test_mapping(
        path=src_path,
        test_groups=test_groups,
        include_subdirs=args.include_subdirs,
        checked_files=set(),
    )
    test_details_list = list(test_details)
    if not test_details_list and exit_if_no_test_found:
      logging.warning(
          'No tests of group `%s` found in %s or its '
          'parent directories. (Available groups: %s)\n'
          'You might be missing atest arguments,'
          ' try `atest --help` for more information.',
          test_groups,
          os.path.join(src_path, constants.TEST_MAPPING),
          ', '.join(all_test_details.keys()),
      )
      if all_test_details:
        tests = ''
        for test_group, test_list in all_test_details.items():
          tests += '%s:\n' % test_group
          for test_detail in sorted(test_list, key=str):
            tests += '\t%s\n' % test_detail
        logging.warning(
            'All available tests in TEST_MAPPING files are:\n%s', tests
        )
      metrics_utils.send_exit_event(ExitCode.TEST_NOT_FOUND)
      sys.exit(ExitCode.TEST_NOT_FOUND)

    logging.debug(
        'Test details:\n%s',
        '\n'.join([str(detail) for detail in test_details_list]),
    )
    test_names = [detail.name for detail in test_details_list]
    return test_names, test_details_list

  def _extract_testable_modules_by_wildcard(self, user_input):
    """Extract the given string with wildcard symbols to testable

    module names.

    Assume the available testable modules is:
        ['Google', 'google', 'G00gle', 'g00gle']
    and the user_input is:
        ['*oo*', 'g00gle']
    This method will return:
        ['Google', 'google', 'g00gle']

    Args:
        user_input: A list of input.

    Returns:
        A list of testable modules.
    """
    testable_mods = self.mod_info.get_testable_modules()
    extracted_tests = []
    for test in user_input:
      if atest_utils.has_wildcard(test):
        extracted_tests.extend(fnmatch.filter(testable_mods, test))
      else:
        extracted_tests.append(test)
    return extracted_tests

  def translate(self, args):
    """Translate atest command line into build targets and run commands.

    Args:
        args: arg parsed object.

    Returns:
        A tuple with set of build_target strings and list of TestInfos.
    """
    tests = args.tests
    detect_type = DetectType.TEST_WITH_ARGS
    # Disable fuzzy searching when running with test mapping related args.
    if not args.tests or atest_utils.is_test_mapping(args):
      self.fuzzy_search = False
      detect_type = DetectType.TEST_NULL_ARGS
    start = time.time()
    # Not including host unit tests if user specify --test-mapping.
    host_unit_tests = []
    if not any((args.tests, args.test_mapping)):
      logging.debug('Finding Host Unit Tests...')
      host_unit_tests = test_finder_utils.find_host_unit_tests(
          self.mod_info, str(Path(os.getcwd()).relative_to(self.root_dir))
      )
      logging.debug('Found host_unit_tests: %s', host_unit_tests)
    # Test details from TEST_MAPPING files
    test_details_list = None
    if atest_utils.is_test_mapping(args):
      if args.enable_file_patterns:
        self.enable_file_patterns = True
      tests, test_details_list = self._get_test_mapping_tests(
          args, not bool(host_unit_tests)
      )
    atest_utils.colorful_print('\nFinding Tests...', constants.CYAN)
    logging.debug('Finding Tests: %s', tests)
    # Clear cache if user pass -c option
    if args.clear_cache:
      atest_utils.clean_test_info_caches(tests + host_unit_tests)
    # Process tests which might contain wildcard symbols in advance.
    if atest_utils.has_wildcard(tests):
      tests = self._extract_testable_modules_by_wildcard(tests)
    test_infos = self._get_test_infos(tests, test_details_list)
    if host_unit_tests:
      host_unit_test_details = [
          test_mapping.TestDetail({'name': test, 'host': True})
          for test in host_unit_tests
      ]
      host_unit_test_infos = self._get_test_infos(
          host_unit_tests, host_unit_test_details
      )
      test_infos.extend(host_unit_test_infos)
    if atest_utils.has_mixed_type_filters(test_infos):
      atest_utils.colorful_print(
          'Mixed type filters found. '
          'Please separate tests into different runs.',
          constants.YELLOW,
      )
      sys.exit(ExitCode.MIXED_TYPE_FILTER)
    finished_time = time.time() - start
    logging.debug('Finding tests finished in %ss', finished_time)
    metrics.LocalDetectEvent(detect_type=detect_type, result=int(finished_time))
    for t_info in test_infos:
      logging.debug('%s\n', t_info)
    return test_infos


# TODO: (b/265359291) Raise Exception when the brackets are not in pair.
def parse_test_identifier(test: str) -> TestIdentifier:
  """Get mainline module names and binaries information."""
  result = atest_utils.get_test_and_mainline_modules(test)
  if not result:
    return TestIdentifier(test, [], [])
  test_name = result.group('test')
  mainline_binaries = result.group('mainline_modules').split('+')
  mainline_modules = [Path(m).stem for m in mainline_binaries]
  logging.debug('mainline_modules: %s', mainline_modules)
  return TestIdentifier(test_name, mainline_modules, mainline_binaries)
