# Copyright 2023 The Android Open Source Project
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

import ast
import logging
import os
import platform
import subprocess
import time

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device


class DeviceAsWebcamTest(base_test.BaseTestClass):
  # Tests device as webcam functionality with Mobly base test class to run.

  _ACTION_WEBCAM_RESULT = 'com.android.cts.verifier.camera.webcam.ACTION_WEBCAM_RESULT'
  _WEBCAM_RESULTS = 'camera.webcam.extra.RESULTS'
  _WEBCAM_TEST_ACTIVITY = 'com.android.cts.verifier/.camera.webcam.WebcamTestActivity'
  _DAC_PREVIEW_ACTIVITY = 'com.android.DeviceAsWebcam/.DeviceAsWebcamPreview'
  _ACTIVITY_START_WAIT = 1.5  # seconds
  _ADB_RESTART_WAIT = 9  # seconds
  _FPS_TOLERANCE = 0.15 # 15 percent
  _RESULT_PASS = 'PASS'
  _RESULT_FAIL = 'FAIL'
  _RESULT_NOT_EXECUTED = 'NOT_EXECUTED'
  _MANUAL_FRAME_CHECK_DURATION = 8  # seconds
  _WINDOWS_OS = 'Windows'
  _MAC_OS = 'Darwin'
  _LINUX_OS = 'Linux'

  def run_os_specific_test(self):
    """Runs the os specific webcam test script.

    Returns:
      A result list of tuples (tested_fps, actual_fps)
    """
    results = []
    current_os = platform.system()

    if current_os == self._WINDOWS_OS:
      import windows_webcam_test
      logging.info('Starting test on Windows')
      # Due to compatibility issues directly running the windows
      # main function, the results from the windows_webcam_test script
      # are printed to the stdout and retrieved
      output = subprocess.check_output(['python', 'windows_webcam_test.py'])
      output_str = output.decode('utf-8')
      results = ast.literal_eval(output_str.strip())
    elif current_os == self._LINUX_OS:
      import linux_webcam_test
      logging.info('Starting test on Linux')
      results = linux_webcam_test.main()
    elif current_os == self._MAC_OS:
      import mac_webcam_test
      logging.info('Starting test on Mac')
      results = mac_webcam_test.main()
    else:
      logging.info('Running on an unknown OS')

    return results

  def validate_fps(self, results):
    """Verifies the webcam FPS falls within the acceptable range of the tested FPS.

    Args:
        results: A result list of tuples (tested_fps, actual_fps)

    Returns:
        True if all FPS are within tolerance range, False otherwise
    """
    result = True

    for elem in results:
      tested_fps = elem[0]
      actual_fps = elem[1]

      max_diff = tested_fps * self._FPS_TOLERANCE

      if abs(tested_fps - actual_fps) > max_diff:
        logging.error('FPS is out of tolerance range! '
                      ' Tested: %d Actual FPS: %d', tested_fps, actual_fps)
        result = False

    return result

  def run_cmd(self, cmd):
    """Replaces os.system call, while hiding stdout+stderr messages."""
    with open(os.devnull, 'wb') as devnull:
      subprocess.check_call(cmd.split(), stdout=devnull,
                            stderr=subprocess.STDOUT)

  def setup_class(self):
    # Registering android_device controller module declares the test
    # dependencies on Android device hardware. By default, we expect at least
    # one object is created from this.
    devices = self.register_controller(android_device, min_number=1)
    self.dut = devices[0]
    self.dut.adb.root()

  def test_webcam(self):

    adb = f'adb -s {self.dut.serial}'

    # Keep device on while testing since it requires a manual check on the
    # webcam frames
    # '7' is a combination of flags ORed together to keep the device on
    # in all cases
    self.dut.adb.shell(['settings', 'put', 'global',
                        'stay_on_while_plugged_in', '7'])

    cmd = f"""{adb} shell am start {self._WEBCAM_TEST_ACTIVITY}
        --activity-brought-to-front"""
    self.run_cmd(cmd)

    # Check if webcam feature is enabled
    dut_webcam_enabled = self.dut.adb.shell(['getprop', 'ro.usb.uvc.enabled'])
    if 'true' in dut_webcam_enabled.decode('utf-8'):
      logging.info('Webcam enabled, testing webcam')
    else:
      logging.info('Webcam not enabled, skipping webcam test')

      # Notify CTSVerifier test that the webcam test was skipped,
      # the test will be marked as PASSED for this case
      cmd = (f"""{adb} shell am broadcast -a
          {self._ACTION_WEBCAM_RESULT} --es {self._WEBCAM_RESULTS}
          {self._RESULT_NOT_EXECUTED}""")
      self.run_cmd(cmd)

      return

    # Set USB preference option to webcam
    set_uvc = self.dut.adb.shell(['svc', 'usb', 'setFunctions', 'uvc'])
    if not set_uvc:
      logging.error('USB preference option to set webcam unsuccessful')

      # Notify CTSVerifier test that setting webcam option was unsuccessful
      cmd = (f"""{adb} shell am broadcast -a
          {self._ACTION_WEBCAM_RESULT} --es {self._WEBCAM_RESULTS}
          {self._RESULT_FAIL}""")
      self.run_cmd(cmd)
      return

    # After resetting the USB preference, adb disconnects
    # and reconnects so wait for device
    time.sleep(self._ADB_RESTART_WAIT)

    fps_results = self.run_os_specific_test()
    logging.info('FPS test results (Expected, Actual): %s', fps_results)
    result = self.validate_fps(fps_results)

    test_status = self._RESULT_PASS
    if not result or not fps_results:
      logging.info('FPS testing failed')
      test_status = self._RESULT_FAIL

    # Send result to CTSVerifier test
    time.sleep(self._ACTIVITY_START_WAIT)
    cmd = (f"""{adb} shell am broadcast -a
        {self._ACTION_WEBCAM_RESULT} --es {self._WEBCAM_RESULTS}
        {test_status}""")
    self.run_cmd(cmd)

    # Enable the webcam service preview activity for a manual
    # check on webcam frames
    cmd = f"""{adb} shell am start {self._DAC_PREVIEW_ACTIVITY}
        --activity-no-history"""
    self.run_cmd(cmd)
    time.sleep(self._MANUAL_FRAME_CHECK_DURATION)

    cmd = f"""{adb} shell am start {self._WEBCAM_TEST_ACTIVITY}
        --activity-brought-to-front"""
    self.run_cmd(cmd)

    asserts.assert_true(test_status == self._RESULT_PASS, 'Results: Failed')

    self.dut.adb.shell(['settings', 'put',
                        'global', 'stay_on_while_plugged_in', '0'])

if __name__ == '__main__':
  test_runner.main()
