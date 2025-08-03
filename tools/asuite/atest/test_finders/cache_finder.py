# Copyright 2019, The Android Open Source Project
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

"""Cache Finder class."""

import logging

from atest import atest_utils
from atest import constants
from atest.test_finders import test_filter_utils
from atest.test_finders import test_finder_base
from atest.test_finders import test_info


class CacheFinder(test_finder_base.TestFinderBase):
  """Cache Finder class."""

  NAME = 'CACHE'

  def __init__(self, module_info=None):
    super().__init__()
    self.module_info = module_info

  def _is_latest_testinfos(self, test_infos):
    """Check whether test_infos are up-to-date.

    Args:
        test_infos: A list of TestInfo.

    Returns:
        True if all keys in test_infos and TestInfo object are equal.
        Otherwise, False.
    """
    sorted_base_ti = sorted(vars(test_info.TestInfo(None, None, None)).keys())
    for cached_test_info in test_infos:
      sorted_cache_ti = sorted(vars(cached_test_info).keys())
      if not sorted_cache_ti == sorted_base_ti:
        logging.debug('test_info is not up-to-date.')
        return False
    return True

  def find_test_by_cache(self, test_reference):
    """Find the matched test_infos in saved caches.

    Args:
        test_reference: A string of the path to the test's file or dir.

    Returns:
        A list of TestInfo namedtuple if cache found and is in latest
        TestInfo format, else None.
    """
    test_infos = atest_utils.load_test_info_cache(test_reference)
    if test_infos and self._is_test_infos_valid(test_infos):
      return test_infos
    return None

  def _is_test_infos_valid(self, test_infos):
    """Check if the given test_infos are valid.

    Args:
        test_infos: A list of TestInfo.

    Returns:
        True if test_infos are all valid. Otherwise, False.
    """
    if not self._is_latest_testinfos(test_infos):
      return False
    for t_info in test_infos:
      if t_info.test_runner == 'BazelTestRunner':
        return False
      if not self._is_test_path_valid(t_info):
        return False
      if not self._is_test_build_target_valid(t_info):
        return False
      if not self._is_test_filter_valid(t_info):
        return False
    return True

  def _is_test_path_valid(self, t_info):
    """Check if test path is valid.

    Args:
        t_info: TestInfo that has been filled out by a find method.

    Returns:
        True if test path is valid. Otherwise, False.
    """
    # For RoboTest it won't have 'MODULES-IN-' as build target. Treat test
    # path is valid if cached_test_paths is None.
    cached_test_paths = t_info.get_test_paths()
    if cached_test_paths is None:
      return True
    current_test_paths = self.module_info.get_paths(t_info.test_name)
    if not current_test_paths:
      return False
    formatted_paths = [p.replace('/', '-') for p in current_test_paths]
    if sorted(cached_test_paths) != sorted(formatted_paths):
      logging.debug('Not a valid test path.')
      return False
    return True

  def _is_test_build_target_valid(self, t_info):
    """Check if test build targets are valid.

    Args:
        t_info: TestInfo that has been filled out by a find method.

    Returns:
        True if test's build target is valid. Otherwise, False.
    """
    # If the cached build target can be found in current module-info, then
    # it is a valid build targets of the test.
    for build_target in t_info.build_targets:
      if str(build_target).startswith(constants.MODULES_IN):
        continue
      if not self.module_info.is_module(build_target):
        logging.debug('%s is not a valid build target.', build_target)
        return False
    return True

  def _is_test_filter_valid(self, t_info):
    """Check if test filter is valid.

    Args:
        t_info: TestInfo that has been filled out by a find method.

    Returns:
        True if test filter is valid. Otherwise, False.
    """
    test_filters = t_info.data.get(constants.TI_FILTER, [])
    if not test_filters:
      return True
    for test_filter in test_filters:
      # Check if the class filter is under current module.
      # TODO: (b/172260100) The test_name may not be inevitably equal to
      #  the module_name.
      if not self._is_class_in_module(t_info.test_name, test_filter.class_name):
        logging.debug('Not a valid test filter.')
        return False
    return True

  def _is_class_in_module(self, module_name, filter_class):
    """Check if input class is part of input module.

    Args:
        module_name: A string of the module name of the test.
        filter_class: A string of the class name field of TI_FILTER.

    Returns:
        True if input filter_class is in the input module. Otherwise, False.
    """
    mod_info = self.module_info.get_module_info(module_name)
    if not mod_info:
      return False
    module_srcs = mod_info.get(constants.MODULE_SRCS, [])
    # If module didn't have src information treat the cached filter still
    # valid. Remove this after all java srcs could be found in module-info.
    if not module_srcs:
      return True

    for src_path in module_srcs:
      abs_src_path = atest_utils.get_build_top(src_path)
      if constants.CC_EXT_RE.match(src_path):
        # TODO: (b/172260100) Also check for CC.
        return True
      else:
        full_class_name = test_filter_utils.get_fully_qualified_class_name(
            abs_src_path
        )
        package_name = test_filter_utils.get_package_name(abs_src_path)
        if filter_class == full_class_name or filter_class == package_name:
          return True
    return False
