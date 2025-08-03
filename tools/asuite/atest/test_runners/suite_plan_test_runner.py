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

"""SUITE Tradefed test runner class."""

import copy
import logging
import os
from typing import Any, Dict, List

from atest import atest_utils
from atest import constants
from atest.atest_enum import ExitCode
from atest.logstorage import atest_gcp_utils
from atest.logstorage import logstorage_utils
from atest.metrics import metrics
from atest.test_finders import test_info
from atest.test_runners import atest_tf_test_runner


class SuitePlanTestRunner(atest_tf_test_runner.AtestTradefedTestRunner):
  """Suite Plan Test Runner class."""

  NAME = 'SuitePlanTestRunner'
  EXECUTABLE = '%s-tradefed'
  _RUN_CMD = '{exe} run commandAndExit {test} {args}'

  def __init__(self, results_dir: str, extra_args: Dict[str, Any], **kwargs):
    """Init stuff for suite tradefed runner class."""
    super().__init__(results_dir, extra_args, **kwargs)
    self.run_cmd_dict = {'exe': '', 'test': '', 'args': ''}

  def get_test_runner_build_reqs(self, test_infos: List[test_info.TestInfo]):
    """Return the build requirements.

    Args:
        test_infos: List of TestInfo.

    Returns:
        Set of build targets.
    """
    build_req = set()
    build_req |= super().get_test_runner_build_reqs(test_infos)
    return build_req

  def run_tests(self, test_infos, extra_args, reporter):
    """Run the list of test_infos.

    Args:
        test_infos: List of TestInfo.
        extra_args: Dict of extra args to add to test run.
        reporter: An instance of result_report.ResultReporter.

    Returns:
        Return code of the process for running tests.
    """
    reporter.register_unsupported_runner(self.NAME)
    creds, inv = atest_gcp_utils.do_upload_flow(extra_args)

    run_cmds = self.generate_run_commands(test_infos, extra_args)
    ret_code = ExitCode.SUCCESS
    for run_cmd in run_cmds:
      try:
        proc = super().run(
            run_cmd,
            output_to_stdout=True,
            env_vars=self.generate_env_vars(extra_args),
        )
        ret_code |= self.wait_for_subprocess(proc)
      finally:
        if inv:
          try:
            logging.disable(logging.INFO)
            # Always set invocation status to completed due to
            # the ATest handle whole process by its own.
            inv['schedulerState'] = 'completed'
            logstorage_utils.BuildClient(creds).update_invocation(inv)
            reporter.test_result_link = (
                constants.RESULT_LINK % inv['invocationId']
            )
          finally:
            logging.disable(logging.NOTSET)
    return ret_code

  # pylint: disable=arguments-differ
  def _parse_extra_args(self, extra_args):
    """Convert the extra args into something *ts-tf can understand.

    We want to transform the top-level args from atest into specific args
    that *ts-tradefed supports. The only arg we take as is
    EXTRA_ARG since that is what the user intentionally wants to pass to
    the test runner.

    Args:
        extra_args: Dict of args

    Returns:
        List of args to append.
    """
    args_to_append = []
    args_not_supported = []
    for arg in extra_args:
      if constants.SERIAL == arg:
        args_to_append.append('--serial')
        args_to_append.append(extra_args[arg])
        continue
      if constants.CUSTOM_ARGS == arg:
        args_to_append.extend(extra_args[arg])
        continue
      if constants.INVOCATION_ID == arg:
        args_to_append.append(
            '--invocation-data invocation_id=%s' % extra_args[arg]
        )
      if constants.WORKUNIT_ID == arg:
        args_to_append.append(
            '--invocation-data work_unit_id=%s' % extra_args[arg]
        )
      if arg in (constants.DRY_RUN, constants.REQUEST_UPLOAD_RESULT):
        continue
      if constants.TF_DEBUG == arg:
        debug_port = extra_args.get(constants.TF_DEBUG, '')
        port = debug_port if debug_port else constants.DEFAULT_DEBUG_PORT
        print('Please attach process to your IDE...(%s)' % port)
        continue
      args_not_supported.append(arg)
    if args_not_supported:
      logging.info(
          '%s does not support the following args: %s',
          self.EXECUTABLE,
          args_not_supported,
      )
    return args_to_append

  # pylint: disable=arguments-differ
  def generate_run_commands(self, test_infos, extra_args):
    """Generate a list of run commands from TestInfos.

    Args:
        test_infos: List of TestInfo tests to run.
        extra_args: Dict of extra args to add to test run.

    Returns:
        A List of strings that contains the run command
        which *ts-tradefed supports.
    """
    cmds = []
    args = []
    args.extend(self._parse_extra_args(extra_args))
    args.extend(atest_utils.get_result_server_args())
    for test_info in test_infos:
      cmd_dict = copy.deepcopy(self.run_cmd_dict)
      cmd_dict['test'] = test_info.test_name
      cmd_dict['args'] = ' '.join(args)
      cmd_dict['exe'] = self.EXECUTABLE % test_info.suite
      cmds.append(self._RUN_CMD.format(**cmd_dict))
      if constants.DETECT_TYPE_XTS_SUITE:
        xts_detect_type = constants.DETECT_TYPE_XTS_SUITE.get(
            test_info.suite, ''
        )
        if xts_detect_type:
          metrics.LocalDetectEvent(detect_type=xts_detect_type, result=1)
    return cmds

  def generate_env_vars(self, extra_args):
    """Convert extra args into env vars."""
    env_vars = os.environ.copy()
    debug_port = extra_args.get(constants.TF_DEBUG, '')
    if debug_port:
      env_vars['TF_DEBUG'] = 'true'
      env_vars['TF_DEBUG_PORT'] = str(debug_port)
    if constants.TF_GLOBAL_CONFIG:
      env_vars['TF_GLOBAL_CONFIG'] = constants.TF_GLOBAL_CONFIG
    return env_vars
