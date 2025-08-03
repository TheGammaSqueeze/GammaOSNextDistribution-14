#!/usr/bin/env python3
#
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

"""Unittests for atest_tf_test_runner."""

# pylint: disable=missing-function-docstring
# pylint: disable=too-many-lines
# pylint: disable=unused-argument

from argparse import Namespace
from io import StringIO
import json
import os
from pathlib import Path
import shlex
import sys
import tempfile
import unittest
from unittest import mock
from atest import atest_configs
from atest import atest_utils
from atest import constants
from atest import module_info
from atest import unittest_constants as uc
from atest import unittest_utils
from atest.test_finders import test_finder_utils
from atest.test_finders import test_info
from atest.test_runner_invocation import TestRunnerInvocation
from atest.test_runners import atest_tf_test_runner as atf_tr
from atest.test_runners import event_handler
from pyfakefs import fake_filesystem_unittest

# pylint: disable=protected-access
# pylint: disable=invalid-name
# TODO(147567606): Replace {serial} with {extra_args} for general extra
# arguments testing.
RUN_CMD_ARGS = (
    '--log-level-display VERBOSE --log-level VERBOSE'
    '{device_early_release}{serial}'
)
LOG_ARGS = atf_tr.AtestTradefedTestRunner._LOG_ARGS.format(
    log_root_option_name=constants.LOG_ROOT_OPTION_NAME,
    log_ext_option=constants.LOG_SAVER_EXT_OPTION,
    log_path=os.path.join(uc.TEST_INFO_DIR, atf_tr.LOG_FOLDER_NAME),
    proto_path=os.path.join(
        uc.TEST_INFO_DIR, constants.ATEST_TEST_RECORD_PROTO
    ),
)
RUN_ENV_STR = ''
RUN_CMD = atf_tr.AtestTradefedTestRunner._RUN_CMD.format(
    env=RUN_ENV_STR,
    exe=atf_tr.AtestTradefedTestRunner.EXECUTABLE,
    template='{template}',
    log_saver=constants.ATEST_TF_LOG_SAVER,
    tf_customize_template='{tf_customize_template}',
    args=RUN_CMD_ARGS,
    log_args=LOG_ARGS,
)
FULL_CLASS2_NAME = 'android.jank.cts.ui.SomeOtherClass'
CLASS2_FILTER = test_info.TestFilter(FULL_CLASS2_NAME, frozenset())
METHOD2_FILTER = test_info.TestFilter(
    uc.FULL_CLASS_NAME, frozenset([uc.METHOD2_NAME])
)
MODULE_ARG1 = [
    (constants.TF_INCLUDE_FILTER_OPTION, 'A'),
    (constants.TF_INCLUDE_FILTER_OPTION, 'B'),
]
MODULE_ARG2 = []
CLASS2_METHOD_FILTER = test_info.TestFilter(
    FULL_CLASS2_NAME, frozenset([uc.METHOD_NAME, uc.METHOD2_NAME])
)
MODULE2_INFO = test_info.TestInfo(
    uc.MODULE2_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={
        constants.TI_REL_CONFIG: uc.CONFIG2_FILE,
        constants.TI_FILTER: frozenset(),
    },
)
CLASS1_BUILD_TARGETS = {'class_1_build_target'}
CLASS1_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS1_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset([uc.CLASS_FILTER]),
    },
)
CLASS2_BUILD_TARGETS = {'class_2_build_target'}
CLASS2_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS2_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset([CLASS2_FILTER]),
    },
)
CLASS3_BUILD_TARGETS = {'class_3_build_target'}
CLASS3_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS3_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset(),
        constants.TI_MODULE_ARG: MODULE_ARG1,
    },
)
CLASS4_BUILD_TARGETS = {'class_4_build_target'}
CLASS4_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS4_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset(),
        constants.TI_MODULE_ARG: MODULE_ARG2,
    },
)
CLASS1_CLASS2_MODULE_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    uc.MODULE_BUILD_TARGETS | CLASS1_BUILD_TARGETS | CLASS2_BUILD_TARGETS,
    uc.MODULE_DATA,
)
FLAT_CLASS_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS1_BUILD_TARGETS | CLASS2_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset([uc.CLASS_FILTER, CLASS2_FILTER]),
    },
)
FLAT2_CLASS_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    CLASS3_BUILD_TARGETS | CLASS4_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset(),
        constants.TI_MODULE_ARG: MODULE_ARG1 + MODULE_ARG2,
    },
)
GTF_INT_CONFIG = os.path.join(uc.GTF_INT_DIR, uc.GTF_INT_NAME + '.xml')
CLASS2_METHOD_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset([
            test_info.TestFilter(
                FULL_CLASS2_NAME, frozenset([uc.METHOD_NAME, uc.METHOD2_NAME])
            )
        ]),
    },
)
METHOD_AND_CLASS2_METHOD = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    uc.MODULE_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset(
            [uc.METHOD_FILTER, CLASS2_METHOD_FILTER]
        ),
    },
)
METHOD_METHOD2_AND_CLASS2_METHOD = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    uc.MODULE_BUILD_TARGETS,
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset(
            [uc.FLAT_METHOD_FILTER, CLASS2_METHOD_FILTER]
        ),
    },
)
METHOD2_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    data={
        constants.TI_REL_CONFIG: uc.CONFIG_FILE,
        constants.TI_FILTER: frozenset([METHOD2_FILTER]),
    },
)

INT_INFO = test_info.TestInfo(
    uc.INT_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    test_finder='INTEGRATION',
)

MOD_INFO = test_info.TestInfo(
    uc.MODULE_NAME,
    atf_tr.AtestTradefedTestRunner.NAME,
    set(),
    test_finder='MODULE',
)

MOD_INFO_NO_TEST_FINDER = test_info.TestInfo(
    uc.MODULE_NAME, atf_tr.AtestTradefedTestRunner.NAME, set()
)

EVENTS_NORMAL = [
    (
        'TEST_MODULE_STARTED',
        {
            'moduleContextFileName': 'serial-util1146216{974}2772610436.ser',
            'moduleName': 'someTestModule',
        },
    ),
    ('TEST_RUN_STARTED', {'testCount': 2}),
    (
        'TEST_STARTED',
        {
            'start_time': 52,
            'className': 'someClassName',
            'testName': 'someTestName',
        },
    ),
    (
        'TEST_ENDED',
        {
            'end_time': 1048,
            'className': 'someClassName',
            'testName': 'someTestName',
        },
    ),
    (
        'TEST_STARTED',
        {
            'start_time': 48,
            'className': 'someClassName2',
            'testName': 'someTestName2',
        },
    ),
    (
        'TEST_FAILED',
        {
            'className': 'someClassName2',
            'testName': 'someTestName2',
            'trace': 'someTrace',
        },
    ),
    (
        'TEST_ENDED',
        {
            'end_time': 9876450,
            'className': 'someClassName2',
            'testName': 'someTestName2',
        },
    ),
    ('TEST_RUN_ENDED', {}),
    ('TEST_MODULE_ENDED', {'foo': 'bar'}),
]

ANDROID_HOST_OUT = '/my/android/out/host/abc'


# pylint: disable=too-many-public-methods
class AtestTradefedTestRunnerUnittests(unittest.TestCase):
  """Unit tests for atest_tf_test_runner.py"""

  def setUp(self):
    self.tr = atf_tr.AtestTradefedTestRunner(
        results_dir=uc.TEST_INFO_DIR, extra_args={constants.HOST: False}
    )
    if not atest_configs.GLOBAL_ARGS:
      atest_configs.GLOBAL_ARGS = Namespace()
    atest_configs.GLOBAL_ARGS.device_count_config = None

  def tearDown(self):
    mock.patch.stopall()

  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_start_socket_server')
  @mock.patch.object(atf_tr.AtestTradefedTestRunner, 'run')
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_create_test_args',
      return_value=['some_args'],
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      'generate_run_commands',
      return_value='some_cmd',
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner, '_process_connection', return_value=None
  )
  @mock.patch('select.select')
  @mock.patch('os.killpg', return_value=None)
  @mock.patch('os.getpgid', return_value=None)
  @mock.patch('signal.signal', return_value=None)
  def test_run_tests_pretty(
      self,
      _signal,
      _pgid,
      _killpg,
      mock_select,
      _process,
      _run_cmd,
      _test_args,
      mock_run,
      mock_start_socket_server,
  ):
    """Test _run_tests_pretty method."""
    mock_subproc = mock.Mock()
    mock_run.return_value = mock_subproc
    mock_subproc.returncode = 0
    mock_subproc.poll.side_effect = [True, True, None]
    mock_server = mock.Mock()
    mock_server.getsockname.return_value = ('', '')
    mock_start_socket_server.return_value = mock_server
    mock_reporter = mock.Mock()

    # Test no early TF exit
    mock_conn = mock.Mock()
    mock_server.accept.return_value = (mock_conn, 'some_addr')
    mock_server.close.return_value = True
    mock_select.side_effect = [
        ([mock_server], None, None),
        ([mock_conn], None, None),
    ]
    self.tr.run_tests_pretty([MODULE2_INFO], {}, mock_reporter)

    # Test early TF exit
    with tempfile.NamedTemporaryFile() as tmp_file:
      with open(tmp_file.name, 'w', encoding='utf-8') as f:
        f.write('tf msg')
      self.tr.test_log_file = tmp_file
      mock_select.side_effect = [([], None, None)]
      mock_subproc.poll.side_effect = None
      capture_output = StringIO()
      sys.stdout = capture_output
      self.assertRaises(
          atf_tr.TradeFedExitError,
          self.tr.run_tests_pretty,
          [MODULE2_INFO],
          {},
          mock_reporter,
      )

    sys.stdout = sys.__stdout__
    self.assertTrue('tf msg' in capture_output.getvalue())

  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_process_connection')
  @mock.patch('select.select')
  def test_start_monitor_2_connection(self, mock_select, mock_process):
    """Test _start_monitor method."""
    mock_server = mock.Mock()
    mock_subproc = mock.Mock()
    mock_reporter = mock.Mock()
    mock_conn1 = mock.Mock()
    mock_conn2 = mock.Mock()
    mock_server.accept.side_effect = [
        (mock_conn1, 'addr 1'),
        (mock_conn2, 'addr 2'),
    ]
    mock_select.side_effect = [
        ([mock_server], None, None),
        ([mock_server], None, None),
        ([mock_conn1], None, None),
        ([mock_conn2], None, None),
        ([mock_conn1], None, None),
        ([mock_conn2], None, None),
    ]
    mock_process.side_effect = ['abc', 'def', False, False]
    mock_subproc.poll.side_effect = [None, None, None, None, None, True]
    self.tr._start_monitor(mock_server, mock_subproc, mock_reporter, {})
    self.assertEqual(mock_process.call_count, 4)
    calls = [mock.call.accept(), mock.call.close()]
    mock_server.assert_has_calls(calls)
    mock_conn1.assert_has_calls([mock.call.close()])
    mock_conn2.assert_has_calls([mock.call.close()])

  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_process_connection')
  @mock.patch('select.select')
  def test_start_monitor_tf_exit_before_2nd_connection(
      self, mock_select, mock_process
  ):
    """Test _start_monitor method."""
    mock_server = mock.Mock()
    mock_subproc = mock.Mock()
    mock_reporter = mock.Mock()
    mock_conn1 = mock.Mock()
    mock_conn2 = mock.Mock()
    mock_server.accept.side_effect = [
        (mock_conn1, 'addr 1'),
        (mock_conn2, 'addr 2'),
    ]
    mock_select.side_effect = [
        ([mock_server], None, None),
        ([mock_server], None, None),
        ([mock_conn1], None, None),
        ([mock_conn2], None, None),
        ([mock_conn1], None, None),
        ([mock_conn2], None, None),
    ]
    mock_process.side_effect = ['abc', 'def', False, False]
    # TF exit early but have not processed data in socket buffer.
    mock_subproc.poll.side_effect = [None, None, True, True, True, True]
    self.tr._start_monitor(mock_server, mock_subproc, mock_reporter, {})
    self.assertEqual(mock_process.call_count, 4)
    calls = [mock.call.accept(), mock.call.close()]
    mock_server.assert_has_calls(calls)
    mock_conn1.assert_has_calls([mock.call.close()])
    mock_conn2.assert_has_calls([mock.call.close()])

  def test_start_socket_server(self):
    """Test start_socket_server method."""
    server = self.tr._start_socket_server()
    host, port = server.getsockname()
    self.assertEqual(host, atf_tr.SOCKET_HOST)
    self.assertLessEqual(port, 65535)
    self.assertGreaterEqual(port, 1024)
    server.close()

  @mock.patch('os.path.exists')
  @mock.patch.dict('os.environ', {'APE_API_KEY': '/tmp/123.json'})
  def test_try_set_gts_authentication_key_is_set_by_user(self, mock_exist):
    """Test try_set_authentication_key_is_set_by_user method."""
    # Test key is set by user.
    self.tr._try_set_gts_authentication_key()
    mock_exist.assert_not_called()

  @mock.patch('os.path.join', return_value='/tmp/file_not_exist.json')
  def test_try_set_gts_authentication_key_not_set(self, _):
    """Test try_set_authentication_key_not_set method."""
    # Delete the environment variable if it's set. This is fine for this
    # method because it's for validating the APE_API_KEY isn't set.
    if os.environ.get('APE_API_KEY'):
      del os.environ['APE_API_KEY']
    self.tr._try_set_gts_authentication_key()
    self.assertEqual(os.environ.get('APE_API_KEY'), None)

  @mock.patch.object(event_handler.EventHandler, 'process_event')
  def test_process_connection(self, mock_pe):
    """Test _process_connection method."""
    mock_socket = mock.Mock()
    for name, data in EVENTS_NORMAL:
      data_map = {mock_socket: ''}
      socket_data = '%s %s' % (name, json.dumps(data))
      mock_socket.recv.return_value = socket_data
      self.tr._process_connection(data_map, mock_socket, mock_pe)

    calls = [
        mock.call.process_event(name, data) for name, data in EVENTS_NORMAL
    ]
    mock_pe.assert_has_calls(calls)
    mock_socket.recv.return_value = ''
    self.assertFalse(
        self.tr._process_connection(data_map, mock_socket, mock_pe)
    )

  @mock.patch.object(event_handler.EventHandler, 'process_event')
  def test_process_connection_multiple_lines_in_single_recv(self, mock_pe):
    """Test _process_connection when recv reads multiple lines in one go."""
    mock_socket = mock.Mock()
    squashed_events = '\n'.join(
        ['%s %s' % (name, json.dumps(data)) for name, data in EVENTS_NORMAL]
    )
    socket_data = [squashed_events, '']
    mock_socket.recv.side_effect = socket_data
    data_map = {mock_socket: ''}
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    calls = [
        mock.call.process_event(name, data) for name, data in EVENTS_NORMAL
    ]
    mock_pe.assert_has_calls(calls)

  @mock.patch.object(event_handler.EventHandler, 'process_event')
  def test_process_connection_with_buffering(self, mock_pe):
    """Test _process_connection when events overflow socket buffer size."""
    mock_socket = mock.Mock()
    module_events = [EVENTS_NORMAL[0], EVENTS_NORMAL[-1]]
    socket_events = [
        '%s %s' % (name, json.dumps(data)) for name, data in module_events
    ]
    # test try-block code by breaking apart first event after first }
    index = socket_events[0].index('}') + 1
    socket_data = [socket_events[0][:index], socket_events[0][index:]]
    # test non-try block buffering with second event
    socket_data.extend([socket_events[1][:-4], socket_events[1][-4:], ''])
    mock_socket.recv.side_effect = socket_data
    data_map = {mock_socket: ''}
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    calls = [
        mock.call.process_event(name, data) for name, data in module_events
    ]
    mock_pe.assert_has_calls(calls)

  @mock.patch.object(event_handler.EventHandler, 'process_event')
  def test_process_connection_with_not_completed_event_data(self, mock_pe):
    """Test _process_connection when event have \n prefix."""
    mock_socket = mock.Mock()
    mock_socket.recv.return_value = '\n%s %s' % (
        EVENTS_NORMAL[0][0],
        json.dumps(EVENTS_NORMAL[0][1]),
    )
    data_map = {mock_socket: ''}
    self.tr._process_connection(data_map, mock_socket, mock_pe)
    calls = [mock.call.process_event(EVENTS_NORMAL[0][0], EVENTS_NORMAL[0][1])]
    mock_pe.assert_has_calls(calls)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_without_serial_env(
      self, mock_resultargs, _, _mock_all
  ):
    """Test generate_run_command method."""
    # Basic Run Cmd
    mock_resultargs.return_value = []
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], {}),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], {}),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )

  @mock.patch(
      'atest.test_runners.atest_tf_test_runner.is_log_upload_enabled',
      return_value=True,
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_with_upload_enabled(
      self, mock_resultargs, _, _mock_all, _upload_enabled
  ):
    result_arg = '--result_arg'
    mock_resultargs.return_value = [result_arg]

    run_command = self.tr.generate_run_commands([], {})

    self.assertIn('--result_arg', run_command[0].split())

  @mock.patch(
      'atest.test_runners.atest_tf_test_runner.is_log_upload_enabled',
      return_value=False,
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_without_upload_enabled(
      self, mock_resultargs, _, _mock_all, _upload_enabled
  ):
    result_arg = '--result_arg'
    mock_resultargs.return_value = [result_arg]

    run_command = self.tr.generate_run_commands([], {})

    self.assertNotIn('--result_arg', run_command[0].split())

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get')
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_with_serial_env(
      self, mock_resultargs, mock_env, _mock_all
  ):
    """Test generate_run_command method."""
    # Basic Run Cmd
    env_device_serial = 'env-device-0'
    mock_resultargs.return_value = []
    mock_env.return_value = env_device_serial
    env_serial_arg = ' --serial %s' % env_device_serial
    # Serial env be set and without --serial arg.
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], {}),
        [
            RUN_CMD.format(
                serial=env_serial_arg,
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )
    # Serial env be set but with --serial arg.
    arg_device_serial = 'arg-device-0'
    arg_serial_arg = ' --serial %s' % arg_device_serial
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands(
            [], {constants.SERIAL: [arg_device_serial]}
        ),
        [
            RUN_CMD.format(
                serial=arg_serial_arg,
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )
    # Serial env be set but with -n arg
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], {constants.HOST: True}),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICELESS_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
            + ' -n --prioritize-host-config --skip-host-arch-check'
        ],
    )

  def test_flatten_test_filters(self):
    """Test flatten_test_filters method."""
    # No Flattening
    filters = self.tr.flatten_test_filters({uc.CLASS_FILTER})
    unittest_utils.assert_strict_equal(
        self, frozenset([uc.CLASS_FILTER]), filters
    )
    filters = self.tr.flatten_test_filters({CLASS2_FILTER})
    unittest_utils.assert_strict_equal(
        self, frozenset([CLASS2_FILTER]), filters
    )
    filters = self.tr.flatten_test_filters({uc.METHOD_FILTER})
    unittest_utils.assert_strict_equal(
        self, frozenset([uc.METHOD_FILTER]), filters
    )
    filters = self.tr.flatten_test_filters(
        {uc.METHOD_FILTER, CLASS2_METHOD_FILTER}
    )
    unittest_utils.assert_strict_equal(
        self, frozenset([uc.METHOD_FILTER, CLASS2_METHOD_FILTER]), filters
    )
    # Flattening
    filters = self.tr.flatten_test_filters({uc.METHOD_FILTER, METHOD2_FILTER})
    unittest_utils.assert_strict_equal(
        self, filters, frozenset([uc.FLAT_METHOD_FILTER])
    )
    filters = self.tr.flatten_test_filters({
        uc.METHOD_FILTER,
        METHOD2_FILTER,
        CLASS2_METHOD_FILTER,
    })
    unittest_utils.assert_strict_equal(
        self, filters, frozenset([uc.FLAT_METHOD_FILTER, CLASS2_METHOD_FILTER])
    )

  def test_flatten_test_infos(self):
    """Test _flatten_test_infos method."""
    # No Flattening
    test_infos = self.tr._flatten_test_infos({uc.MODULE_INFO})
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {uc.MODULE_INFO}
    )

    test_infos = self.tr._flatten_test_infos([uc.MODULE_INFO, MODULE2_INFO])
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {uc.MODULE_INFO, MODULE2_INFO}
    )

    test_infos = self.tr._flatten_test_infos({CLASS1_INFO})
    unittest_utils.assert_equal_testinfo_sets(self, test_infos, {CLASS1_INFO})

    test_infos = self.tr._flatten_test_infos({uc.INT_INFO})
    unittest_utils.assert_equal_testinfo_sets(self, test_infos, {uc.INT_INFO})

    test_infos = self.tr._flatten_test_infos({uc.METHOD_INFO})
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {uc.METHOD_INFO}
    )

    # Flattening
    test_infos = self.tr._flatten_test_infos({CLASS1_INFO, CLASS2_INFO})
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {FLAT_CLASS_INFO}
    )

    test_infos = self.tr._flatten_test_infos(
        {CLASS1_INFO, uc.INT_INFO, CLASS2_INFO}
    )
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {uc.INT_INFO, FLAT_CLASS_INFO}
    )

    test_infos = self.tr._flatten_test_infos(
        {CLASS1_INFO, uc.MODULE_INFO, CLASS2_INFO}
    )
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {CLASS1_CLASS2_MODULE_INFO}
    )

    test_infos = self.tr._flatten_test_infos(
        {MODULE2_INFO, uc.INT_INFO, CLASS1_INFO, CLASS2_INFO, uc.GTF_INT_INFO}
    )
    unittest_utils.assert_equal_testinfo_sets(
        self,
        test_infos,
        {uc.INT_INFO, uc.GTF_INT_INFO, FLAT_CLASS_INFO, MODULE2_INFO},
    )

    test_infos = self.tr._flatten_test_infos(
        {uc.METHOD_INFO, CLASS2_METHOD_INFO}
    )
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {METHOD_AND_CLASS2_METHOD}
    )

    test_infos = self.tr._flatten_test_infos(
        {uc.METHOD_INFO, METHOD2_INFO, CLASS2_METHOD_INFO}
    )
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {METHOD_METHOD2_AND_CLASS2_METHOD}
    )
    test_infos = self.tr._flatten_test_infos({
        uc.METHOD_INFO,
        METHOD2_INFO,
        CLASS2_METHOD_INFO,
        MODULE2_INFO,
        uc.INT_INFO,
    })
    unittest_utils.assert_equal_testinfo_sets(
        self,
        test_infos,
        {uc.INT_INFO, MODULE2_INFO, METHOD_METHOD2_AND_CLASS2_METHOD},
    )

    test_infos = self.tr._flatten_test_infos({CLASS3_INFO, CLASS4_INFO})
    unittest_utils.assert_equal_testinfo_sets(
        self, test_infos, {FLAT2_CLASS_INFO}
    )

  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_create_test_args(self, mock_config):
    """Test _create_test_args method."""
    # Only compile '--skip-loading-config-jar' in TF if it's not
    # INTEGRATION finder or the finder property isn't set.
    mock_config.return_value = '', ''
    args = self.tr._create_test_args([MOD_INFO])
    self.assertTrue(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

    args = self.tr._create_test_args([INT_INFO])
    self.assertFalse(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

    args = self.tr._create_test_args([MOD_INFO_NO_TEST_FINDER])
    self.assertFalse(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

    args = self.tr._create_test_args([MOD_INFO_NO_TEST_FINDER, INT_INFO])
    self.assertFalse(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

    args = self.tr._create_test_args([MOD_INFO_NO_TEST_FINDER])
    self.assertFalse(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

    args = self.tr._create_test_args(
        [MOD_INFO_NO_TEST_FINDER, INT_INFO, MOD_INFO]
    )
    self.assertFalse(constants.TF_SKIP_LOADING_CONFIG_JAR in args)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_collect_tests_only(
      self, mock_resultargs, _, _mock_is_all
  ):
    """Test generate_run_command method."""
    # Testing  without collect-tests-only
    mock_resultargs.return_value = []
    extra_args = {}
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], extra_args),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )
    # Testing  with collect-tests-only
    mock_resultargs.return_value = []
    extra_args = {constants.COLLECT_TESTS_ONLY: True}
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], extra_args),
        [
            RUN_CMD.format(
                serial=' --collect-tests-only',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release',
            )
        ],
    )

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_with_tf_template(
      self, mock_resultargs, _, _mock_all
  ):
    """Test generate_run_command method."""
    tf_tmplate_key1 = 'tf_tmplate_key1'
    tf_tmplate_val1 = 'tf_tmplate_val1'
    tf_tmplate_key2 = 'tf_tmplate_key2'
    tf_tmplate_val2 = 'tf_tmplate_val2'
    # Testing with only one tradefed template command
    mock_resultargs.return_value = []
    extra_args = {
        constants.TF_TEMPLATE: [
            '{}={}'.format(tf_tmplate_key1, tf_tmplate_val1)
        ]
    }
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], extra_args),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                device_early_release=' --no-early-device-release',
                tf_customize_template='--template:map {}={}',
            ).format(tf_tmplate_key1, tf_tmplate_val1)
        ],
    )
    # Testing with two tradefed template commands
    extra_args = {
        constants.TF_TEMPLATE: [
            '{}={}'.format(tf_tmplate_key1, tf_tmplate_val1),
            '{}={}'.format(tf_tmplate_key2, tf_tmplate_val2),
        ]
    }
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], extra_args),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                device_early_release=' --no-early-device-release',
                tf_customize_template=(
                    '--template:map {}={} --template:map {}={}'
                ),
            ).format(
                tf_tmplate_key1,
                tf_tmplate_val1,
                tf_tmplate_key2,
                tf_tmplate_val2,
            )
        ],
    )

  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_has_instant_app_config(self, mock_config):
    """test _has_instant_app_config method."""
    no_instant_config = os.path.join(
        uc.TEST_DATA_DIR, 'parameter_config', 'parameter.cfg'
    )
    instant_config = os.path.join(
        uc.TEST_DATA_DIR, 'parameter_config', 'instant_app_parameter.cfg'
    )
    # Test find instant app config
    mock_config.return_value = instant_config, ''
    self.assertTrue(
        atf_tr.AtestTradefedTestRunner._has_instant_app_config(
            ['test_info'], 'module_info_obj'
        )
    )
    # Test not find instant app config
    mock_config.return_value = no_instant_config, ''
    self.assertFalse(
        atf_tr.AtestTradefedTestRunner._has_instant_app_config(
            ['test_info'], 'module_info_obj'
        )
    )

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_has_instant_app_config',
      return_value=True,
  )
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_has_instant_app_config(
      self, mock_resultargs, _, _mock_has_config, _mock_is_all
  ):
    """Test generate_run_command method which has instant app config."""
    # Basic Run Cmd
    mock_resultargs.return_value = []
    extra_tf_arg = (
        '{tf_test_arg} {tf_class}:{option_name}:{option_value}'.format(
            tf_test_arg=constants.TF_TEST_ARG,
            tf_class=constants.TF_AND_JUNIT_CLASS,
            option_name=constants.TF_EXCLUDE_ANNOTATE,
            option_value=constants.INSTANT_MODE_ANNOTATE,
        )
    )
    unittest_utils.assert_strict_equal(
        self,
        self.tr.generate_run_commands([], {}),
        [
            RUN_CMD.format(
                serial='',
                template=self.tr._TF_DEVICE_TEST_TEMPLATE,
                tf_customize_template='',
                device_early_release=' --no-early-device-release '
                + extra_tf_arg,
            )
        ],
    )

  @mock.patch.object(atest_utils, 'get_config_parameter')
  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_is_parameter_auto_enabled_cfg(self, mock_config, mock_cfg_para):
    """test _is_parameter_auto_enabled_cfg method."""
    # Test if TF_PARA_INSTANT_APP is match
    mock_config.return_value = 'test_config', ''
    mock_cfg_para.return_value = {
        list(constants.DEFAULT_EXCLUDE_PARAS)[1],
        list(constants.DEFAULT_EXCLUDE_PARAS)[0],
    }
    self.assertFalse(
        atf_tr.AtestTradefedTestRunner._is_parameter_auto_enabled_cfg(
            ['test_info'], 'module_info_obj'
        )
    )
    # Test if DEFAULT_EXCLUDE_NOT_PARAS is match
    mock_cfg_para.return_value = {
        list(constants.DEFAULT_EXCLUDE_NOT_PARAS)[2],
        list(constants.DEFAULT_EXCLUDE_NOT_PARAS)[0],
    }
    self.assertFalse(
        atf_tr.AtestTradefedTestRunner._is_parameter_auto_enabled_cfg(
            ['test_info'], 'module_info_obj'
        )
    )
    # Test if have parameter not in default exclude paras
    mock_cfg_para.return_value = {
        'not match parameter',
        list(constants.DEFAULT_EXCLUDE_PARAS)[1],
        list(constants.DEFAULT_EXCLUDE_NOT_PARAS)[2],
    }
    self.assertTrue(
        atf_tr.AtestTradefedTestRunner._is_parameter_auto_enabled_cfg(
            ['test_info'], 'module_info_obj'
        )
    )

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_parameter_auto_enabled_cfg',
      return_value=True,
  )
  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_create_test_args_with_auto_enable_parameter(
      self, mock_config, _mock_is_enable
  ):
    """Test _create_test_args method with auto enabled parameter config."""
    # Should have --m on args and should not have --include-filter.
    mock_config.return_value = '', ''
    args = self.tr._create_test_args([MOD_INFO])
    self.assertTrue(constants.TF_MODULE_FILTER in args)
    self.assertFalse(constants.TF_INCLUDE_FILTER in args)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner, '_is_parameter_auto_enabled_cfg'
  )
  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_parse_extra_args(self, mock_config, _mock_is_enable):
    """Test _parse_extra_args ."""
    # If extra_arg enable instant_app or secondary users, should not have
    # --exclude-module-parameters even though test config parameter is auto
    # enabled.
    mock_config.return_value = '', ''
    _mock_is_enable.return_value = True
    args, _ = self.tr._parse_extra_args([MOD_INFO], {constants.INSTANT: ''})
    self.assertFalse('--exclude-module-parameters' in args)

    # If extra_arg not enable instant_app or secondary users, should have
    # --exclude-module-rameters if config parameter is auto enabled.
    _mock_is_enable.return_value = True
    args, _ = self.tr._parse_extra_args([MOD_INFO], {constants.ALL_ABI: ''})
    self.assertTrue('--exclude-module-parameters' in args)

    # If extra_arg not enable instant_app or secondary users, should not
    # have --exclude-module-rameters if config parameter is not auto enabled
    _mock_is_enable.return_value = False
    args, _ = self.tr._parse_extra_args([MOD_INFO], {constants.ALL_ABI: ''})
    self.assertFalse('--exclude-module-parameters' in args)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_parameter_auto_enabled_cfg',
      return_value=False,
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_has_instant_app_config',
      return_value=False,
  )
  def test_parse_extra_args_has_instant_app(
      self, _mock_has_instant, _mock_is_para
  ):
    """Test _parse_extra_args with instant app in customize flag."""
    # If customize_arg has module-parameter should also include
    # --enable-parameterized-modules.
    args, _ = self.tr._parse_extra_args(
        [MOD_INFO], {constants.CUSTOM_ARGS: [constants.TF_MODULE_PARAMETER]}
    )
    self.assertTrue(constants.TF_ENABLE_PARAMETERIZED_MODULES in args)

  @mock.patch('atest.atest_utils.get_prebuilt_sdk_tools_dir')
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner, '_is_missing_exec', return_value=False
  )
  def test_generate_env_vars_aapt_already_in_system_path(
      self, _mock_is_missing_exec, mock_prebuilt_sdk_dir
  ):
    """Test generate_env_vars if aapt already in system path."""
    prebuilt_sdk_dir = Path('/my/test/sdk/dir')
    mock_prebuilt_sdk_dir.return_value = prebuilt_sdk_dir

    env_vars = self.tr.generate_env_vars(extra_args={})

    self.assertFalse(str(prebuilt_sdk_dir) + ':' in env_vars.get('PATH', ''))

  @mock.patch('os.path.exists', return_value=True)
  @mock.patch('atest.atest_utils.get_prebuilt_sdk_tools_dir')
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner, '_is_missing_exec', return_value=True
  )
  def test_generate_env_vars_aapt_not_in_system_path(
      self, _mock_is_missing_exec, mock_prebuilt_sdk_dir, _mock_exist
  ):
    """Test generate_env_vars if aapt not in system path."""
    prebuilt_sdk_dir = Path('/my/test/sdk/dir')
    mock_prebuilt_sdk_dir.return_value = prebuilt_sdk_dir

    env_vars = self.tr.generate_env_vars(extra_args={})

    self.assertTrue(str(prebuilt_sdk_dir) + ':' in env_vars.get('PATH', ''))

  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_handle_native_tests')
  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_parse_extra_args')
  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_create_test_args')
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_generate_run_commands_for_aggregate_metric_result(
      self,
      mock_resultargs,
      _mock_env,
      _mock_create,
      _mock_parse,
      _mock_handle_native,
  ):
    """Test generate_run_command method for test need aggregate metric."""
    mock_resultargs.return_value = []
    _mock_create.return_value = []
    _mock_parse.return_value = [], []
    test_info_with_aggregate_metrics = test_info.TestInfo(
        test_name='perf_test', test_runner='test_runner', build_targets=set()
    )
    test_info_with_aggregate_metrics.aggregate_metrics_result = True

    run_cmd = self.tr.generate_run_commands(
        [test_info_with_aggregate_metrics], extra_args={}
    )

    self.assertTrue(
        str(run_cmd).find(
            'metric_post_processor='
            'google/template/postprocessors/metric-file-aggregate-disabled'
        )
        > 0
    )

  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_handle_native_tests')
  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_parse_extra_args')
  @mock.patch.object(atf_tr.AtestTradefedTestRunner, '_create_test_args')
  @mock.patch('os.environ.get', return_value=None)
  @mock.patch('atest.atest_utils.get_result_server_args')
  def test_run_commands_for_aggregate_metric_result_with_manually_input(
      self,
      mock_resultargs,
      _mock_env,
      _mock_create,
      _mock_parse,
      _mock_handle_native,
  ):
    """Test generate_run_command method for test need aggregate metric."""
    mock_resultargs.return_value = []
    _mock_create.return_value = []
    _mock_parse.return_value = [], []
    test_info_with_aggregate_metrics = test_info.TestInfo(
        test_name='perf_test', test_runner='test_runner', build_targets=set()
    )
    test_info_with_aggregate_metrics.aggregate_metrics_result = True

    run_cmd = self.tr.generate_run_commands(
        [test_info_with_aggregate_metrics],
        extra_args={constants.TF_TEMPLATE: ['metric_post_processor=a/b/c']},
    )

    self.assertTrue(
        str(run_cmd).find(
            'metric_post_processor='
            'google/template/postprocessors/metric-file-aggregate-disabled'
        )
        < 0
    )

    self.assertTrue(str(run_cmd).find('metric_post_processor=a/b/c') > 0)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_create_test_args_with_auto_enable_parameter_but_not_all(
      self, mock_config, _mock_is_all
  ):
    """Test _create_test_args method with not all configs are auto enabled

    parameter.
    """
    # Should not --m on args and should have --include-filter.
    mock_config.return_value = '', ''
    args = self.tr._create_test_args([MOD_INFO])

    self.assertFalse(constants.TF_MODULE_FILTER in args)
    self.assertTrue(constants.TF_INCLUDE_FILTER in args)

  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner,
      '_is_all_tests_parameter_auto_enabled',
      return_value=False,
  )
  @mock.patch.object(
      atf_tr.AtestTradefedTestRunner, '_is_parameter_auto_enabled_cfg'
  )
  @mock.patch.object(test_finder_utils, 'get_test_config_and_srcs')
  def test_parse_extra_args_not_all_cfg_auto_enable_parameter(
      self, mock_config, _mock_is_enable, _mock_is_all
  ):
    """Test _parse_extra_args without all config is auto parameter."""
    # If not all test config is parameter auto enabled, should not find
    # --enable-parameterized-modules in test config.
    mock_config.return_value = '', ''
    _mock_is_enable.return_value = True

    args, _ = self.tr._parse_extra_args([MOD_INFO], {})
    self.assertFalse('--enable-parameterized-modules' in args)

  def test_create_invocations_returns_invocations_for_device_and_deviceless_tests(
      self,
  ):
    self.tr.module_info = module_info.ModuleInfo(
        name_to_module_info={
            'deviceless_test': robolectric_test_module(name='deviceless_test'),
            'device_test': device_driven_test_module(name='device_test'),
        }
    )
    test_info_deviceless = test_info_of('deviceless_test')
    test_info_device = test_info_of('device_test')

    invocations = self.tr.create_invocations(
        {}, [test_info_deviceless, test_info_device]
    )
    expected_invocation_deviceless = TestRunnerInvocation(
        test_runner=self.tr,
        extra_args={constants.HOST: True},
        test_infos=[test_info_deviceless],
    )
    expected_invocation_device = TestRunnerInvocation(
        test_runner=self.tr, extra_args={}, test_infos=[test_info_device]
    )

    self.assertEqual(
        invocations,
        [expected_invocation_deviceless, expected_invocation_device],
    )

  def test_create_invocations_returns_invocation_only_for_device_tests(self):
    self.tr.module_info = module_info.ModuleInfo(
        name_to_module_info={
            'device_test_1': device_driven_test_module(name='device_test_1'),
            'device_test_2': host_driven_device_test_module(
                name='device_test_2'
            ),
        }
    )
    test_info_device_1 = test_info_of('device_test_1')
    test_info_device_2 = test_info_of('device_test_2')

    invocations = self.tr.create_invocations(
        {}, [test_info_device_1, test_info_device_2]
    )
    expected_invocation = TestRunnerInvocation(
        test_runner=self.tr,
        extra_args={},
        test_infos=[test_info_device_1, test_info_device_2],
    )

    self.assertEqual(invocations, [expected_invocation])

  def test_create_invocations_returns_invocations_for_device_tests_without_module_info(
      self,
  ):
    self.tr.module_info = module_info.ModuleInfo()

    test_info_device = test_info_of('device_test_without_module_info')

    invocations = self.tr.create_invocations({}, [test_info_device])
    expected_invocation = TestRunnerInvocation(
        test_runner=self.tr,
        extra_args={},
        test_infos=[test_info_device],
    )

    self.assertEqual(invocations, [expected_invocation])

  def assertTokensIn(self, expected_tokens, s):
    tokens = shlex.split(s)
    for token in expected_tokens:
      self.assertIn(token, tokens)

  def assertTokensNotIn(self, unwanted_tokens, s):
    tokens = shlex.split(s)
    for token in unwanted_tokens:
      self.assertNotIn(token, tokens)


class ExtraArgsTest(AtestTradefedTestRunnerUnittests):
  """Unit tests for parsing extra args"""

  def test_args_with_wait_for_debug_and_generate_in_run_cmd(self):
    extra_args = {constants.WAIT_FOR_DEBUGGER: None}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--wait-for-debugger'], cmd[0])

  def test_args_with_disable_installed_and_generate_in_run_cmd(self):
    extra_args = {constants.DISABLE_INSTALL: None}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--disable-target-preparers'], cmd[0])

  def test_multidevice_in_config_and_generate_in_run_cmd(self):
    atest_configs.GLOBAL_ARGS.device_count_config = 2
    cmd = self.tr.generate_run_commands([], {})
    self.assertTokensIn(
        ['--replicate-parent-setup', '--multi-device-count', '2'], cmd[0]
    )

    atest_configs.GLOBAL_ARGS.device_count_config = 1
    cmd = self.tr.generate_run_commands([], {})
    self.assertTokensNotIn(
        ['--replicate-parent-setup', '--multi-device-count'], cmd[0]
    )

    atest_configs.GLOBAL_ARGS.device_count_config = None
    cmd = self.tr.generate_run_commands([], {})
    self.assertTokensNotIn(
        ['--replicate-parent-setup', '--multi-device-count'], cmd[0]
    )

  def test_args_with_serial_no_and_generate_in_run_cmd(self):
    extra_args = {constants.SERIAL: ['device1']}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--serial', 'device1'], cmd[0])

  def test_args_with_multi_serial_no_and_generate_in_run_cmd(self):
    extra_args = {constants.SERIAL: ['device1', 'device2']}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertIn('--serial device1 --serial device2', cmd[0])

  def test_args_with_sharding_and_generate_in_run_cmd(self):
    extra_args = {constants.SHARDING: 2}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertIn('--shard-count 2', cmd[0])

  def test_args_with_disable_teardown_and_generate_in_run_cmd(self):
    extra_args = {constants.DISABLE_TEARDOWN: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--disable-teardown'], cmd[0])

  def test_args_with_host_and_generate_in_run_cmd(self):
    extra_args = {constants.HOST: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(
        ['-n', '--prioritize-host-config', '--skip-host-arch-check'], cmd[0]
    )

  def test_args_with_custom_args_and_generate_in_run_cmd(self):
    extra_args = {constants.CUSTOM_ARGS: ['--a=b']}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--a=b'], cmd[0])

  def test_args_with_multi_custom_args_and_generate_in_run_cmd(self):
    extra_args = {constants.CUSTOM_ARGS: ['--a=b', '--c=d']}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--a=b', '--c=d'], cmd[0])

  def test_args_with_all_abi_and_generate_in_run_cmd(self):
    extra_args = {constants.ALL_ABI: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--all-abi'], cmd[0])

  def test_args_with_dry_run_but_not_generate_in_run_cmd(self):
    extra_args = {constants.DRY_RUN: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensNotIn(['--dry-run'], cmd[0])

  def test_args_with_instant_and_generate_in_run_cmd(self):
    extra_args = {constants.INSTANT: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(
        ['--enable-parameterized-modules', '--module-parameter', 'instant_app'],
        cmd[0],
    )

  def test_args_with_user_type_and_generate_in_run_cmd(self):
    extra_args = {constants.USER_TYPE: 'hello_user'}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(
        [
            '--enable-parameterized-modules',
            '--enable-optional-parameterization',
            '--module-parameter',
            'hello_user',
        ],
        cmd[0],
    )

  def test_args_with_iterations_and_generate_in_run_cmd(self):
    extra_args = {constants.ITERATIONS: 2}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertIn('--retry-strategy ITERATIONS', cmd[0])
    self.assertIn('--max-testcase-run-count 2', cmd[0])

  def test_args_with_retry_until_failure_and_generate_in_run_cmd(self):
    extra_args = {constants.RERUN_UNTIL_FAILURE: 2}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertIn('--retry-strategy RERUN_UNTIL_FAILURE', cmd[0])
    self.assertIn('--max-testcase-run-count 2', cmd[0])

  def test_args_with_retry_any_failure_and_generate_in_run_cmd(self):
    extra_args = {constants.RETRY_ANY_FAILURE: 2}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertIn('--retry-strategy RETRY_ANY_FAILURE', cmd[0])
    self.assertIn('--max-testcase-run-count 2', cmd[0])

  def test_args_with_collect_test_only_and_generate_in_run_cmd(self):
    extra_args = {constants.COLLECT_TESTS_ONLY: True}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(['--collect-tests-only'], cmd[0])

  def test_args_with_tf_template_but_not_generate_in_run_cmd(self):
    extra_args = {constants.TF_TEMPLATE: ['hello']}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensNotIn(['--tf-template'], cmd[0])

  def test_args_with_timeout_and_generate_in_run_cmd(self):
    extra_args = {constants.TEST_TIMEOUT: 10000}

    cmd = self.tr.generate_run_commands([], extra_args)

    self.assertTokensIn(
        [
            '--test-arg',
            (
                'com.android.tradefed.testtype.AndroidJUnitTest:'
                'shell-timeout:10000'
            ),
            '--test-arg',
            'com.android.tradefed.testtype.AndroidJUnitTest:test-timeout:10000',
            '--test-arg',
            'com.android.tradefed.testtype.HostGTest:native-test-timeout:10000',
            '--test-arg',
            'com.android.tradefed.testtype.GTest:native-test-timeout:10000',
        ],
        cmd[0],
    )


class ModuleInfoTestFixture(fake_filesystem_unittest.TestCase):
  """Fixture for tests that require module-info."""

  def setUp(self):
    self.setUpPyfakefs()

    self.product_out_path = Path('/src/out/product')
    self.product_out_path.mkdir(parents=True)

  def create_empty_module_info(self):
    fake_temp_file = self.product_out_path.joinpath(
        next(tempfile._get_candidate_names())
    )
    self.fs.create_file(fake_temp_file, contents='{}')
    return module_info.load_from_file(module_file=fake_temp_file)

  def create_module_info(self, modules=None):
    mod_info = self.create_empty_module_info()
    modules = modules or []

    for m in modules:
      mod_info.name_to_module_info[m[constants.MODULE_INFO_ID]] = m

    return mod_info

  def assertContainsSubset(self, expected_subset, actual_set):
    """Checks whether actual iterable is a superset of expected iterable."""
    missing = set(expected_subset) - set(actual_set)
    if not missing:
      return

    self.fail(
        f'Missing elements {missing}\n'
        f'Expected: {expected_subset}\n'
        f'Actual: {actual_set}'
    )


def test_info_of(module_name):
  return test_info.TestInfo(
      module_name, atf_tr.AtestTradefedTestRunner.NAME, []
  )


class DeviceDrivenTestTest(ModuleInfoTestFixture):
  """Tests for device driven test."""

  def test_multi_config_unit_test_without_host_arg(self):
    mod_info = self.create_module_info(
        modules=[multi_config_unit_test_module(name='hello_world_test')]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )
    expect_deps = {
        'aapt2-host',
        'hello_world_test-target',
        'compatibility-tradefed-host',
        'adb-host',
        'atest-tradefed-host',
        'aapt-host',
        'atest_tradefed.sh-host',
        'tradefed-host',
        'atest_script_help.sh-host',
    }

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertContainsSubset(expect_deps, deps)

  @mock.patch.dict('os.environ', {'ANDROID_HOST_OUT': ANDROID_HOST_OUT})
  def test_host_jar_env_device_driven_test_without_host_arg(self):
    tf_host_jar_path = os.path.join(ANDROID_HOST_OUT, 'tradefed.jar')
    atest_host_jar_path = os.path.join(ANDROID_HOST_OUT, 'atest-tradefed.jar')
    host_required_jar_path = os.path.join(ANDROID_HOST_OUT, 'host-required.jar')
    mod_info = self.create_module_info(
        modules=[
            device_driven_test_module(
                name='hello_world_test', host_deps=['host-required']
            ),
            host_jar_module(name='tradefed', installed=[tf_host_jar_path]),
            host_jar_module(
                name='atest-tradefed', installed=[atest_host_jar_path]
            ),
            host_jar_module(
                name='host-required', installed=[host_required_jar_path]
            ),
        ]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )
    expect_path = {
        tf_host_jar_path,
        atest_host_jar_path,
        host_required_jar_path,
    }

    runner.get_test_runner_build_reqs(test_infos)
    env = runner.generate_env_vars(dict())

    self.assertSetEqual(set(env['ATEST_HOST_JARS'].split(':')), expect_path)

  def test_host_driven_device_test(self):
    mod_info = self.create_module_info(
        modules=[
            host_driven_device_test_module(
                name='hello_world_test', libs=['lib']
            ),
            module(
                name='lib',
                supported_variants=['HOST'],
                installed=[f'out/host/linux-x86/lib/lib.jar'],
            ),
        ]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertContainsSubset({'hello_world_test-host', 'lib-host'}, deps)

  def test_device_driven_test_with_mainline_modules(self):
    mod_info = self.create_module_info(
        modules=[device_driven_test_module(name='hello_world_test')]
    )
    t_info = test_info_of('hello_world_test')
    t_info.add_mainline_module('mainline_module_1')
    t_info.add_mainline_module('mainline_module_2')
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )

    deps = runner.get_test_runner_build_reqs([t_info])

    self.assertContainsSubset(
        {'hello_world_test-target', 'mainline_module_1', 'mainline_module_2'},
        deps,
    )

  def test_device_driven_test_with_vts_suite(self):
    mod_info = self.create_module_info(
        modules=[
            device_driven_test_module(
                name='hello_world_test', compatibility_suites=['vts']
            ),
        ]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertIn('vts-core-tradefed-harness-host', deps)
    self.assertNotIn('compatibility-tradefed-host', deps)

  def test_deps_contain_google_tradefed(self):
    mod_info = self.create_module_info(
        modules=[multi_config_unit_test_module(name='hello_world_test')]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )
    gtf_target = set([str(t) + '-host' for t in constants.GTF_TARGETS])

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertContainsSubset(gtf_target, deps)

  def test_java_device_test(self):
    mod_info = self.create_module_info(
        modules=[
            device_driven_test_module(
                name='HelloWorldTest',
                installed=['out/product/vsoc_x86/testcases/HelloWorldTest.jar'],
                class_type=['JAVA_LIBRARIES'],
            )
        ]
    )
    t_info = test_info_of('HelloWorldTest')
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )

    deps = runner.get_test_runner_build_reqs([t_info])

    self.assertContainsSubset(
        {
            'cts-dalvik-device-test-runner-target',
            'cts-dalvik-host-test-runner-host',
        },
        deps,
    )


class DevicelessTestTest(ModuleInfoTestFixture):
  """Tests for deviceless test."""

  def test_multi_config_unit_test_with_host_arg(self):
    mod_info = self.create_module_info(
        modules=[multi_config_unit_test_module(name='hello_world_test')]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: True}, mod_info, minimal_build=True
    )
    expect_deps = {
        'hello_world_test-host',
        'adb-host',
        'atest-tradefed-host',
        'atest_tradefed.sh-host',
        'tradefed-host',
        'atest_script_help.sh-host',
    }

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertContainsSubset(expect_deps, deps)

  def test_robolectric_test(self):
    mod_info = self.create_module_info(
        modules=[robolectric_test_module(name='hello_world_test')]
    )
    test_infos = [test_info_of('hello_world_test')]
    runner = atf_tr.AtestTradefedTestRunner(
        'result_dir', {constants.HOST: False}, mod_info, minimal_build=True
    )
    expect_deps = {
        'hello_world_test-target',
        'adb-host',
        'atest-tradefed-host',
        'atest_tradefed.sh-host',
        'tradefed-host',
        'atest_script_help.sh-host',
    }

    deps = runner.get_test_runner_build_reqs(test_infos)

    self.assertContainsSubset(expect_deps, deps)


class TestExtraArgsToTfArgs(unittest.TestCase):
  """Tests for extra_args_to_tf_args function."""

  def test_supported_args(self):
    extra_args = {
        constants.WAIT_FOR_DEBUGGER: True,
        constants.SHARDING: 5,
        constants.SERIAL: ['device1', 'device2'],
        constants.CUSTOM_ARGS: ['arg1', 'arg2'],
        constants.ITERATIONS: 3,
    }
    expected_supported_args = [
        '--wait-for-debugger',
        '--shard-count',
        '5',
        '--serial',
        'device1',
        '--serial',
        'device2',
        'arg1',
        'arg2',
        '--retry-strategy',
        constants.ITERATIONS,
        '--max-testcase-run-count',
        '3',
    ]

    supported_args, _ = atf_tr.extra_args_to_tf_args(extra_args)

    self.assertEqual(expected_supported_args, supported_args)

  def test_unsupported_args(self):
    extra_args = {
        'invalid_arg': 'value',
        'tf_template': 'template',
    }
    expected_unsupported_args = ['invalid_arg', 'tf_template']

    _, unsupported_args = atf_tr.extra_args_to_tf_args(extra_args)

    self.assertEqual(expected_unsupported_args, unsupported_args)

  def test_empty_extra_args(self):
    extra_args = {}

    supported_args, unsupported_args = atf_tr.extra_args_to_tf_args(extra_args)

    self.assertEqual([], supported_args)
    self.assertEqual([], unsupported_args)


def host_jar_module(name, installed):

  return module(
      name=name,
      supported_variants=['HOST'],
      installed=installed,
      auto_test_config=[],
      compatibility_suites=[],
  )


def device_driven_test_module(
    name,
    installed=None,
    compatibility_suites=None,
    host_deps=None,
    class_type=None,
):

  name = name or 'hello_world_test'

  return test_module(
      name=name,
      supported_variants=['DEVICE'],
      compatibility_suites=compatibility_suites,
      installed=installed or [f'out/product/vsoc_x86/{name}/{name}.apk'],
      host_deps=host_deps,
      class_type=class_type or ['APP'],
  )


def robolectric_test_module(name):
  name = name or 'hello_world_test'
  return test_module(
      name=name,
      supported_variants=['DEVICE'],
      installed=[f'out/host/linux-x86/{name}/{name}.jar'],
      compatibility_suites=['robolectric-tests'],
  )


def host_driven_device_test_module(name, libs=None):
  name = name or 'hello_world_test'
  return test_module(
      name=name,
      supported_variants=['HOST'],
      installed=[f'out/host/linux-x86/{name}/{name}.jar'],
      compatibility_suites=['null-suite'],
      libs=libs,
  )


def multi_config_unit_test_module(name):

  name = name or 'hello_world_test'

  return test_module(
      name=name,
      supported_variants=['HOST', 'DEVICE'],
      installed=[
          f'out/host/linux-x86/{name}/{name}.cc',
          f'out/product/vsoc_x86/{name}/{name}.cc',
      ],
      compatibility_suites=['host-unit-tests'],
  )


def test_module(
    name,
    supported_variants,
    installed,
    compatibility_suites=None,
    libs=None,
    host_deps=None,
    class_type=None,
):

  return module(
      name=name,
      supported_variants=supported_variants,
      installed=installed,
      auto_test_config=[True],
      compatibility_suites=compatibility_suites or ['null-suite'],
      libs=libs,
      host_deps=host_deps,
      class_type=class_type,
  )


def module(
    name,
    supported_variants,
    installed,
    auto_test_config=None,
    compatibility_suites=None,
    libs=None,
    host_deps=None,
    class_type=None,
):

  m = {}

  m[constants.MODULE_INFO_ID] = name
  m[constants.MODULE_SUPPORTED_VARIANTS] = supported_variants
  m[constants.MODULE_INSTALLED] = installed
  m[constants.MODULE_AUTO_TEST_CONFIG] = auto_test_config or []
  m[constants.MODULE_COMPATIBILITY_SUITES] = compatibility_suites or []
  m[constants.MODULE_LIBS] = libs or []
  m[constants.MODULE_HOST_DEPS] = host_deps or []
  m[constants.MODULE_CLASS] = class_type or []

  return m


if __name__ == '__main__':
  unittest.main()
