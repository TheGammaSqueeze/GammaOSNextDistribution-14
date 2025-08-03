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

"""Unittest constants.

Unittest constants get their own file since they're used purely for testing and
should not be combined with constants_defaults as part of normal atest
operation. These constants are used commonly as test data so when updating a
constant, do so with care and run all unittests to make sure nothing breaks.
"""

import os
from pathlib import Path
import zipfile

from atest import constants
from atest.test_finders import test_info
from atest.test_runners import atest_tf_test_runner as atf_tr

ROOT = '/'
MODULE_DIR = 'foo/bar/jank'
MODULE2_DIR = 'foo/bar/hello'
MODULE_NAME = 'CtsJankDeviceTestCases'
MODULE_CONFIG_NAME = 'CtsJankDeviceTestCases2'
HOST_UNIT_TEST_NAME_1 = 'host_unit_test1'
HOST_UNIT_TEST_NAME_2 = 'host_unit_test2'
TYPO_MODULE_NAME = 'CtsJankDeviceTestCase'
MODULE2_NAME = 'HelloWorldTests'
CLASS_NAME = 'CtsDeviceJankUi'
FULL_CLASS_NAME = 'android.jank.cts.ui.CtsDeviceJankUi'
PACKAGE = 'android.jank.cts.ui'
FIND_ONE = ROOT + 'foo/bar/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java\n'
FIND_TWO = ROOT + 'other/dir/test.java\n' + FIND_ONE
FIND_PKG = ROOT + 'foo/bar/jank/src/android/jank/cts/ui\n'
INT_NAME = 'example/reboot'
GTF_INT_NAME = 'some/gtf_int_test'


def get_atest_package():
  """Get Atest directory."""
  package = os.path.dirname(os.path.realpath(__file__))
  if not os.path.isdir(package):
    par_file_path = os.path.dirname(package)

    if os.path.isfile(par_file_path):
      destination_path = os.path.join(
          '/tmp/atest_extract/', str(os.path.getmtime(par_file_path))
      )

      if not os.path.isdir(destination_path):
        Path(destination_path).mkdir(parents=True, exist_ok=True)

      with zipfile.ZipFile(par_file_path, 'r') as zip_ref:
        zip_ref.extractall(destination_path)
      package = os.path.join(destination_path, 'atest')

  return package


ATEST_PKG_DIR = get_atest_package()
TEST_DATA_DIR = os.path.join(ATEST_PKG_DIR, 'unittest_data')
TEST_CONFIG_DATA_DIR = os.path.join(TEST_DATA_DIR, 'test_config')

INT_DIR = 'tf/contrib/res/config'
GTF_INT_DIR = 'gtf/core/res/config'

CONFIG_FILE = os.path.join(MODULE_DIR, constants.MODULE_CONFIG)
EXTRA_CONFIG_FILE = os.path.join(MODULE_DIR, MODULE_CONFIG_NAME + '.xml')
CONFIG2_FILE = os.path.join(MODULE2_DIR, constants.MODULE_CONFIG)
JSON_FILE = 'module-info.json'
MODULE_INFO_TARGET = '/out/%s' % JSON_FILE
CC_DEP_FILE = 'module_bp_cc_deps.json'
JAVA_DEP_FILE = 'module_bp_java_deps.json'
MERGED_DEP_FILE = 'atest_merged_dep.json'
MODULE_BUILD_TARGETS = {
    'tradefed-core',
    MODULE_INFO_TARGET,
    'MODULES-IN-%s' % MODULE_DIR.replace('/', '-'),
    'module-specific-target',
}
MODULE_BUILD_TARGETS_W_DALVIK = MODULE_BUILD_TARGETS | {
    'cts-dalvik-device-test-runner',
    'cts-dalvik-host-test-runner',
    'cts-tradefed',
}
MODULE_BUILD_TARGETS2 = {'build-target2'}
MODULE_DATA = {
    constants.TI_REL_CONFIG: CONFIG_FILE,
    constants.TI_FILTER: frozenset(),
}
MODULE_DATA2 = {
    constants.TI_REL_CONFIG: CONFIG_FILE,
    constants.TI_FILTER: frozenset(),
}
MODULE_DATA_W_CONFIG = {
    constants.TI_REL_CONFIG: EXTRA_CONFIG_FILE,
    constants.TI_FILTER: frozenset(),
}
MODULE_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    MODULE_DATA,
)
MODULE_INFO_W_DALVIK = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS_W_DALVIK,
    MODULE_DATA,
    module_class=[constants.MODULE_CLASS_JAVA_LIBRARIES],
)
MODULE_INFO_W_CONFIG = test_info.TestInfo(
    MODULE_CONFIG_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    MODULE_DATA_W_CONFIG,
)
MODULE_INFO2 = test_info.TestInfo(
    MODULE2_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS2,
    MODULE_DATA2,
)
TEST_CONFIG_MODULE_INFO = test_info.TestInfo(
    MODULE_CONFIG_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    MODULE_DATA_W_CONFIG,
)
MODULE_INFO_HOST_1 = test_info.TestInfo(
    HOST_UNIT_TEST_NAME_1,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    MODULE_DATA,
)
MODULE_INFO_HOST_2 = test_info.TestInfo(
    HOST_UNIT_TEST_NAME_2,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    MODULE_DATA,
)
MODULE_INFOS = [MODULE_INFO]
MODULE_INFOS2 = [MODULE_INFO, MODULE_INFO2]
CLASS_FILTER = test_info.TestFilter(FULL_CLASS_NAME, frozenset())
CLASS_DATA = {
    constants.TI_REL_CONFIG: CONFIG_FILE,
    constants.TI_FILTER: frozenset([CLASS_FILTER]),
}
PACKAGE_FILTER = test_info.TestFilter(PACKAGE, frozenset())
PACKAGE_DATA = {
    constants.TI_REL_CONFIG: CONFIG_FILE,
    constants.TI_FILTER: frozenset([PACKAGE_FILTER]),
}
TEST_DATA_CONFIG = os.path.relpath(
    os.path.join(TEST_DATA_DIR, constants.MODULE_CONFIG), ROOT
)
PATH_DATA = {
    constants.TI_REL_CONFIG: TEST_DATA_CONFIG,
    constants.TI_FILTER: frozenset([PACKAGE_FILTER]),
}
EMPTY_PATH_DATA = {
    constants.TI_REL_CONFIG: TEST_DATA_CONFIG,
    constants.TI_FILTER: frozenset(),
}

CLASS_BUILD_TARGETS = {'class-specific-target'}
CLASS_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS,
    CLASS_DATA,
)
CLASS_INFOS = [CLASS_INFO]

CLASS_BUILD_TARGETS2 = {'class-specific-target2'}
CLASS_DATA2 = {
    constants.TI_REL_CONFIG: CONFIG_FILE,
    constants.TI_FILTER: frozenset([CLASS_FILTER]),
}
CLASS_INFO2 = test_info.TestInfo(
    MODULE2_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS2,
    CLASS_DATA2,
)
CLASS_INFOS = [CLASS_INFO]
CLASS_INFOS2 = [CLASS_INFO, CLASS_INFO2]
PACKAGE_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS,
    PACKAGE_DATA,
)
PATH_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    PATH_DATA,
)
EMPTY_PATH_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    EMPTY_PATH_DATA,
)
MODULE_CLASS_COMBINED_BUILD_TARGETS = MODULE_BUILD_TARGETS | CLASS_BUILD_TARGETS
INT_CONFIG = os.path.join(INT_DIR, INT_NAME + '.xml')
GTF_INT_CONFIG = os.path.join(GTF_INT_DIR, GTF_INT_NAME + '.xml')
METHOD_NAME = 'method1'
METHOD_FILTER = test_info.TestFilter(FULL_CLASS_NAME, frozenset([METHOD_NAME]))
METHOD_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_FILTER: frozenset([METHOD_FILTER]),
        constants.TI_REL_CONFIG: CONFIG_FILE,
    },
)
METHOD2_NAME = 'method2'
FLAT_METHOD_FILTER = test_info.TestFilter(
    FULL_CLASS_NAME, frozenset([METHOD_NAME, METHOD2_NAME])
)
INT_INFO = test_info.TestInfo(
    INT_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={
        constants.TI_REL_CONFIG: INT_CONFIG,
        constants.TI_FILTER: frozenset(),
    },
)
# Golden sample test filter for method under parameterized java.
PARAMETERIZED_METHOD_FILTER = test_info.TestFilter(
    FULL_CLASS_NAME, frozenset([METHOD_NAME + '*'])
)
PARAMETERIZED_METHOD_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_FILTER: frozenset([PARAMETERIZED_METHOD_FILTER]),
        constants.TI_REL_CONFIG: CONFIG_FILE,
    },
)
PARAMETERIZED_FLAT_METHOD_FILTER = test_info.TestFilter(
    FULL_CLASS_NAME, frozenset([METHOD_NAME + '*', METHOD2_NAME + '*'])
)
PARAMETERIZED_FLAT_METHOD_INFO = test_info.TestInfo(
    MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_FILTER: frozenset([PARAMETERIZED_FLAT_METHOD_FILTER]),
        constants.TI_REL_CONFIG: CONFIG_FILE,
    },
)
GTF_INT_INFO = test_info.TestInfo(
    GTF_INT_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={
        constants.TI_FILTER: frozenset(),
        constants.TI_REL_CONFIG: GTF_INT_CONFIG,
    },
)

# Sample test configurations in TEST_MAPPING file.
TEST_MAPPING_TEST = {'name': MODULE_NAME, 'host': True}
TEST_MAPPING_TEST_WITH_OPTION = {
    'name': CLASS_NAME,
    'options': [{'arg1': 'val1'}, {'arg2': ''}],
}
TEST_MAPPING_TEST_WITH_OPTION_STR = '%s (arg1: val1, arg2:)' % CLASS_NAME
TEST_MAPPING_TEST_WITH_BAD_OPTION = {
    'name': CLASS_NAME,
    'options': [{'arg1': 'val1', 'arg2': ''}],
}
TEST_MAPPING_TEST_WITH_BAD_HOST_VALUE = {'name': CLASS_NAME, 'host': 'true'}
# Constrants of cc test unittest
FIND_CC_ONE = ROOT + 'foo/bt/hci/test/pf_test.cc\n'
CC_MODULE_NAME = 'net_test_hci'
CC_CLASS_NAME = 'PFTest'
CC_MODULE_DIR = 'system/bt/hci'
CC_CLASS_FILTER = test_info.TestFilter(CC_CLASS_NAME, frozenset())
CC_CONFIG_FILE = os.path.join(CC_MODULE_DIR, constants.MODULE_CONFIG)
CC_MODULE_CLASS_DATA = {
    constants.TI_REL_CONFIG: CC_CONFIG_FILE,
    constants.TI_FILTER: frozenset([CC_CLASS_FILTER]),
}
CC_MODULE_CLASS_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS,
    CC_MODULE_CLASS_DATA,
)
CC_MODULE2_DIR = 'foo/bar/hello'
CC_MODULE2_NAME = 'hello_world_test'
CC_PATH = 'pf_test.cc'
CC_FIND_ONE = (
    ROOT
    + 'system/bt/hci/test/pf_test.cc:TEST_F(PFTest, test1) {\n'
    + ROOT
    + 'system/bt/hci/test/pf_test.cc:TEST_F(PFTest, test2) {\n'
)
CC_FIND_TWO = (
    ROOT
    + 'other/dir/test.cpp:TEST(PFTest, test_f) {\n'
    + ROOT
    + 'other/dir/test.cpp:TEST(PFTest, test_p) {\n'
)
CC_CONFIG2_FILE = os.path.join(CC_MODULE2_DIR, constants.MODULE_CONFIG)
CC_CLASS_FILTER = test_info.TestFilter(CC_CLASS_NAME + '.*', frozenset())
CC_CLASS_DATA = {
    constants.TI_REL_CONFIG: CC_CONFIG_FILE,
    constants.TI_FILTER: frozenset([CC_CLASS_FILTER]),
}
CC_CLASS_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS,
    CC_CLASS_DATA,
)
CC_METHOD_NAME = 'test1'
CC_METHOD2_NAME = 'test2'
CC_METHOD_FILTER = test_info.TestFilter(
    CC_CLASS_NAME + '.' + CC_METHOD_NAME, frozenset()
)
CC_METHOD2_FILTER = test_info.TestFilter(
    CC_CLASS_NAME
    + '.'
    + CC_METHOD_NAME
    + ':'
    + CC_CLASS_NAME
    + '.'
    + CC_METHOD2_NAME,
    frozenset(),
)
CC_METHOD3_FILTER = test_info.TestFilter(
    CC_CLASS_NAME, frozenset([CC_METHOD_NAME])
)
CC_METHOD_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: CC_CONFIG_FILE,
        constants.TI_FILTER: frozenset([CC_METHOD_FILTER]),
    },
)
CC_METHOD2_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: CC_CONFIG_FILE,
        constants.TI_FILTER: frozenset([CC_METHOD2_FILTER]),
    },
)
CC_METHOD3_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: CC_CONFIG_FILE,
        constants.TI_FILTER: frozenset([CC_METHOD3_FILTER]),
    },
)
CC_PATH_DATA = {
    constants.TI_REL_CONFIG: TEST_DATA_CONFIG,
    constants.TI_FILTER: frozenset(),
}
CC_PATH_INFO = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    MODULE_BUILD_TARGETS,
    CC_PATH_DATA,
)
CC_PATH_DATA2 = {
    constants.TI_REL_CONFIG: CC_CONFIG_FILE,
    constants.TI_FILTER: frozenset(),
}
CC_PATH_INFO2 = test_info.TestInfo(
    CC_MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS_BUILD_TARGETS,
    CC_PATH_DATA2,
)
CTS_INT_DIR = 'test/suite_harness/tools/cts-tradefed/res/config'
# Constrants of java, kt, cc, cpp test_find_class_file() unittest
FIND_PATH_TESTCASE_JAVA = 'hello_world_test'
FIND_PATH_FILENAME_CC = 'hello_world_test'
FIND_PATH_TESTCASE_CC = 'HelloWorldTest'
FIND_PATH_FOLDER = 'class_file_path_testing'
FIND_PATH = os.path.join(TEST_DATA_DIR, FIND_PATH_FOLDER)

DEFAULT_INSTALL_PATH = ['/path/to/install']
# Module names
MOD1 = 'mod1'
MOD2 = 'mod2'
MOD3 = 'mod3'
FUZZY_MOD1 = 'Mod1'
FUZZY_MOD2 = 'nod2'
FUZZY_MOD3 = 'mod3mod3'

INDEX_DIR = os.path.join('/tmp', 'indices')
LOCATE_CACHE = os.path.join(INDEX_DIR, 'plocate.db')
LOCATE_CACHE_MD5 = os.path.join(INDEX_DIR, 'plocate.md5')
CLASS_INDEX = os.path.join(INDEX_DIR, 'classes.idx')
QCLASS_INDEX = os.path.join(INDEX_DIR, 'fqcn.idx')
CC_CLASS_INDEX = os.path.join(INDEX_DIR, 'cc_classes.idx')
PACKAGE_INDEX = os.path.join(INDEX_DIR, 'packages.idx')
MODULE_INFO_MD5 = os.path.join(INDEX_DIR, 'module-info.md5')

# TF's log dir
TEST_INFO_DIR = '/tmp/atest_run_1510085893_pi_Nbi'

# Constants for get_test_config unit tests.
ANDTEST_CONFIG_PATH = 'my/android/config/path'
SINGLE_CONFIG_PATH = 'my/single/config/path'
MULTIPLE_CONFIG_PATH = 'my/multiple/config/path'
MAIN_CONFIG_NAME = 'main_test_config.xml'
SINGLE_CONFIG_NAME = 'test_config.xml'
SUB_CONFIG_NAME_2 = 'Multiple2.xml'
