#!/usr/bin/env python3
# Copyright 2023, The Android Open Source Project
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

"""Atest start_avd functions."""

from __future__ import print_function

import argparse
import json
import logging
import os
from pathlib import Path
import re
import subprocess
import time

from atest import atest_utils as au
from atest import constants
from atest.atest_enum import DetectType, ExitCode
from atest.metrics import metrics

ACLOUD_DURATION = 'duration'
ACLOUD_REPORT_FILE_RE = re.compile(
    r'.*--report[_-]file(=|\s+)(?P<report_file>[\w/.]+)'
)


def get_report_file(results_dir, acloud_args):
  """Get the acloud report file path.

  This method can parse either string:
      --acloud-create '--report-file=/tmp/acloud.json'
      --acloud-create '--report-file /tmp/acloud.json'
  and return '/tmp/acloud.json' as the report file. Otherwise returning the
  default path(/tmp/atest_result/<hashed_dir>/acloud_status.json).

  Args:
      results_dir: string of directory to store atest results.
      acloud_args: string of acloud create.

  Returns:
      A string path of acloud report file.
  """
  match = ACLOUD_REPORT_FILE_RE.match(acloud_args)
  if match:
    return match.group('report_file')
  return os.path.join(results_dir, 'acloud_status.json')


def acloud_create(report_file, args, no_metrics_notice=True):
  """Method which runs acloud create with specified args in background.

  Args:
      report_file: A path string of acloud report file.
      args: A string of arguments.
      no_metrics_notice: Boolean whether sending data to metrics or not.
  """
  notice = constants.NO_METRICS_ARG if no_metrics_notice else ''
  match = ACLOUD_REPORT_FILE_RE.match(args)
  report_file_arg = f'--report-file={report_file}' if not match else ''

  # (b/161759557) Assume yes for acloud create to streamline atest flow.
  acloud_cmd = (
      'acloud create -y {ACLOUD_ARGS} {REPORT_FILE_ARG} {METRICS_NOTICE} '
  ).format(
      ACLOUD_ARGS=args, REPORT_FILE_ARG=report_file_arg, METRICS_NOTICE=notice
  )
  au.colorful_print('\nCreating AVD via acloud...', constants.CYAN)
  logging.debug('Executing: %s', acloud_cmd)
  start = time.time()
  proc = subprocess.Popen(acloud_cmd, shell=True)
  proc.communicate()
  acloud_duration = time.time() - start
  logging.info('"acloud create" process has completed.')
  # Insert acloud create duration into the report file.
  result = au.load_json_safely(report_file)
  if result:
    result[ACLOUD_DURATION] = acloud_duration
    try:
      with open(report_file, 'w+') as _wfile:
        _wfile.write(json.dumps(result))
    except OSError as e:
      logging.error('Failed dumping duration to the report file: %s', str(e))


def acloud_create_validator(results_dir: str, args: argparse.ArgumentParser):
  """Check lunch'd target before running 'acloud create'.

  Args:
      results_dir: A string of the results directory.
      args: An argparse.Namespace object.

  Returns:
      If the target is valid:
          A tuple of (multiprocessing.Process,
                      report_file path)
      else:
          A tuple of (None, None)
  """
  target = os.getenv('TARGET_PRODUCT')
  if not re.match(r'^(aosp_|)cf_.*', target):
    au.colorful_print(
        f'{target} is not in cuttlefish family; will not create any AVD.'
        'Please lunch target which belongs to cuttlefish.',
        constants.RED,
    )
    return None, None
  if args.start_avd:
    args.acloud_create = []
  acloud_args = ' '.join(args.acloud_create)
  report_file = get_report_file(results_dir, acloud_args)
  acloud_proc = au.run_multi_proc(
      func=acloud_create, args=[report_file, acloud_args, args.no_metrics]
  )
  return acloud_proc, report_file


def probe_acloud_status(report_file, find_build_duration):
  """Method which probes the 'acloud create' result status.

  If the report file exists and the status is 'SUCCESS', then the creation is
  successful.

  Args:
      report_file: A path string of acloud report file.
      find_build_duration: A float of seconds.

  Returns:
      0: success.
      8: acloud creation failure.
      9: invalid acloud create arguments.
  """
  # 1. Created but the status is not 'SUCCESS'
  if Path(report_file).exists():
    result = au.load_json_safely(report_file)
    if not result:
      return ExitCode.AVD_CREATE_FAILURE
    if result.get('status') == 'SUCCESS':
      logging.info('acloud create successfully!')
      # Always fetch the adb of the first created AVD.
      adb_port = result.get('data').get('devices')[0].get('adb_port')
      is_remote_instance = result.get('command') == 'create_cf'
      adb_ip = '127.0.0.1' if is_remote_instance else '0.0.0.0'
      os.environ[constants.ANDROID_SERIAL] = f'{adb_ip}:{adb_port}'

      acloud_duration = get_acloud_duration(report_file)
      if find_build_duration - acloud_duration >= 0:
        # find+build took longer, saved acloud create time.
        logging.debug('Saved acloud create time: %ss.', acloud_duration)
        metrics.LocalDetectEvent(
            detect_type=DetectType.ACLOUD_CREATE, result=round(acloud_duration)
        )
      else:
        # acloud create took longer, saved find+build time.
        logging.debug('Saved Find and Build time: %ss.', find_build_duration)
        metrics.LocalDetectEvent(
            detect_type=DetectType.FIND_BUILD, result=round(find_build_duration)
        )
      return ExitCode.SUCCESS
    au.colorful_print(
        'acloud create failed. Please check\n{}\nfor detail'.format(
            report_file
        ),
        constants.RED,
    )
    return ExitCode.AVD_CREATE_FAILURE

  # 2. Failed to create because of invalid acloud arguments.
  msg = 'Invalid acloud arguments found!'
  au.colorful_print(msg, constants.RED)
  logging.debug(msg)
  return ExitCode.AVD_INVALID_ARGS


def get_acloud_duration(report_file):
  """Method which gets the duration of 'acloud create' from a report file.

  Args:
      report_file: A path string of acloud report file.

  Returns:
      An float of seconds which acloud create takes.
  """
  content = au.load_json_safely(report_file)
  if not content:
    return 0
  return content.get(ACLOUD_DURATION, 0)
