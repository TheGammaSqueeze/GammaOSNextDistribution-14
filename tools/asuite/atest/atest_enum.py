# Copyright 2018, The Android Open Source Project
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

"""Atest custom enum class."""

from enum import Enum, IntEnum, unique


@unique
class DetectType(IntEnum):
  """An Enum class for local_detect_event."""

  # Detect type for local_detect_event; next expansion: 60
  BUG_DETECTED = 0
  ACLOUD_CREATE = 1
  FIND_BUILD = 2
  NO_FLAKE = 3  # Deprecated.
  HAS_FLAKE = 4  # Deprecated.
  TF_TEARDOWN_LOGCAT = 5
  REBUILD_MODULE_INFO = 6
  NOT_REBUILD_MODULE_INFO = 7
  ONLY_BUILD_MODULE_INFO = 8
  FUZZY_SEARCH_TIME = 9
  PERMISSION_INCONSISTENT = 10
  SMART_REBUILD_MODULE_INFO = 11
  CLEAN_BUILD = 12
  TESTABLE_MODULES = 13
  # Tradefed exit codes v.s. exit conditions
  # 0: NO_ERROR             1: CONFIG_EXCEPTION
  # 2: NO_BUILD             3: DEVICE_UNRESPONSIVE
  # 4: DEVICE_UNAVAILABLE   5: FATAL_HOST_ERROR
  # 6: THROWABLE_EXCEPTION  7: NO_DEVICE_ALLOCATED
  # 8: WRONG_JAVA_VERSION
  TF_EXIT_CODE = 14
  ATEST_CONFIG = 15
  TEST_WITH_ARGS = 16
  TEST_NULL_ARGS = 17
  MODULE_MERGE = 18  # Deprecated. Use MODULE_MERGE_MS instead.
  MODULE_INFO_INIT_TIME = 19  # Deprecated. Use MODULE_INFO_INIT_MS instead.
  MODULE_MERGE_MS = 20
  NATIVE_TEST_NOT_FOUND = 21
  BAZEL_WORKSPACE_GENERATE_TIME = 22
  MODULE_LOAD_MS = 23
  MODULE_INFO_INIT_MS = 24
  INIT_AND_FIND_MS = 25
  FOUND_INSTRUMENTATION_TEST = 26
  FOUND_TARGET_ARTIFACTS = 27
  FIND_TEST_IN_DEPS = 28
  FULL_GENERATE_BAZEL_WORKSPACE_TIME = 29
  # Below detect types are used for determine build conditions:
  # 1. *_CLEAN_OUT: when out/ dir is empty or does not exist.
  # 2. *_BPMK_CHANGE: when any Android.bp/Android.mk has changed.
  # 3. *_ENV_CHANGE: when build-related variable has changed.
  # 4. *_SRC_CHANGE: when source code has changed.
  # 5. *_OTHER: none of above reasons that triggers renewal of ninja file.
  # 6. *_INCREMENTAL: the build doesn't need to renew ninja file.
  MODULE_INFO_CLEAN_OUT = 30
  MODULE_INFO_BPMK_CHANGE = 31
  MODULE_INFO_ENV_CHANGE = 32
  MODULE_INFO_SRC_CHANGE = 33
  MODULE_INFO_OTHER = 34
  MODULE_INFO_INCREMENTAL = 35
  BUILD_CLEAN_OUT = 36
  BUILD_BPMK_CHANGE = 37
  BUILD_ENV_CHANGE = 38
  BUILD_SRC_CHANGE = 39
  BUILD_OTHER = 40
  BUILD_INCREMENTAL = 41
  BUILD_TIME_PER_TARGET = 42
  MODULE_INFO_GEN_NINJA = 43
  BUILD_GEN_NINJA = 44
  # To indicate if the invocation is using test-mapping, send non-zero value
  # if the invocation is test-mapping mode.
  IS_TEST_MAPPING = 45
  # The RBE_STATE indicates the combined state of the RBE and customized out.
  RBE_STATE = 46
  # Prompt the user to select multiple tests.
  INTERACTIVE_SELECTION = 47
  # Upload results to storage.
  # - UPLOAD_FLOW_MS is the total of upload preparation time, includes:
  # -- FETCH_CRED_MS: fetch credential.
  # -- UPLOAD_PREPARE_MS: insert a new record to server.
  UPLOAD_FLOW_MS = 48
  FETCH_CRED_MS = 49
  UPLOAD_PREPARE_MS = 50
  # Time of join the index.
  IDX_JOIN_MS = 51  # Deprecated. Use INDEX_TARGETS_MS instead.
  IS_MINIMAL_BUILD = 52
  # Elapsed time of the Tradefed runner.
  TF_PREPARATION_MS = 53
  TF_TEST_MS = 54
  TF_TEARDOWN_MS = 55
  TF_TOTAL_RUN_MS = 56
  ROBOLEAF_NON_MODULE_FINDER = 57  # Deprecated.
  ROBOLEAF_UNSUPPORTED_FLAG = 58  # Deprecated.
  INDEX_TARGETS_MS = 59


@unique
class ExitCode(IntEnum):
  """An Enum class for sys.exit()"""

  SUCCESS = 0
  ENV_NOT_SETUP = 1
  BUILD_FAILURE = 2
  ERROR = 3
  TEST_NOT_FOUND = 4
  TEST_FAILURE = 5
  VERIFY_FAILURE = 6
  OUTSIDE_ROOT = 7
  AVD_CREATE_FAILURE = 8
  AVD_INVALID_ARGS = 9
  EXIT_BEFORE_MAIN = 10
  DEVICE_NOT_FOUND = 11
  MIXED_TYPE_FILTER = 12
  INPUT_TEST_REFERENCE_ERROR = 13
  CONFIG_INVALID_FORMAT = 14
  INVALID_SMART_TESTING_PATH = 15  # deprecated.
  INVALID_EXEC_MODE = 16
  INVALID_OBSOLETE_BASELINE_ARGS = 17  # deprecated.
  INVALID_REGRESSION_ARGS = 18  # deprecated.
  INVALID_TM_ARGS = 19
  INVALID_TM_FORMAT = 20
  INSUFFICIENT_DEVICES = 21
  # The code > 100 are reserved for collecting data only, actually the run
  # doesn't finish at the point.
  COLLECT_ONLY_FILE_NOT_FOUND = 101


@unique
class FilterType(Enum):
  """An Enum class for filter types"""

  WILDCARD_FILTER = 'wildcard class_method'
  REGULAR_FILTER = 'regular class_method'
