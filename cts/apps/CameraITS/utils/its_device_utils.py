# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Utility functions to manage and interact with devices for ITS."""


import os
import subprocess

ITS_TEST_ACTIVITY = 'com.android.cts.verifier/.camera.its.ItsTestActivity'


def run(cmd):
  """Replacement for os.system, with hiding of stdout+stderr messages.

  Args:
    cmd: Command to be executed in string format.
  """
  with open(os.devnull, 'wb') as devnull:
    subprocess.check_call(cmd.split(), stdout=devnull, stderr=subprocess.STDOUT)


def start_its_test_activity(device_id):
  """Starts ItsTestActivity, waking the device if necessary.

  Args:
    device_id: str; ID of the device.
  """
  run(f'adb -s {device_id} shell input keyevent KEYCODE_WAKEUP')
  run(f'adb -s {device_id} shell input keyevent KEYCODE_MENU')
  run(f'adb -s {device_id} shell am start -n '
      f'{ITS_TEST_ACTIVITY} --activity-brought-to-front')
