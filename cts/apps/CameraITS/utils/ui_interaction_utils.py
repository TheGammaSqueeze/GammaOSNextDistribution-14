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
"""Utility functions for interacting with a device via the UI."""


import datetime
import logging
import types

import camera_properties_utils
import its_device_utils

ACTION_ITS_DO_JCA_CAPTURE = (
    'com.android.cts.verifier.camera.its.ACTION_ITS_DO_JCA_CAPTURE'
)
CAPTURE_BUTTON_RESOURCE_ID = 'CaptureButton'
FLASH_MODE_TO_CLICKS = types.MappingProxyType({
    'OFF': 3,
    'AUTO': 2
})
ITS_ACTIVITY_TEXT = 'Camera ITS Test'
QUICK_SETTINGS_RESOURCE_ID = 'QuickSettingDropDown'
QUICK_SET_FLASH_RESOURCE_ID = 'QuickSetFlash'
QUICK_SET_FLIP_CAMERA_RESOURCE_ID = 'QuickSetFlipCamera'
UI_DESCRIPTION_BACK_CAMERA = 'Back Camera'
UI_DESCRIPTION_FRONT_CAMERA = 'Front Camera'
UI_OBJECT_WAIT_TIME_SECONDS = datetime.timedelta(seconds=3)
VIEWFINDER_NOT_VISIBLE_PREFIX = 'viewfinder_not_visible'
VIEWFINDER_VISIBLE_PREFIX = 'viewfinder_visible'


def _find_ui_object_else_click(object_to_await, object_to_click):
  """Waits for a UI object to be visible. If not, clicks another UI object.

  Args:
    object_to_await: A snippet-uiautomator selector object to be awaited.
    object_to_click: A snippet-uiautomator selector object to be clicked.
  """
  if not object_to_await.wait.exists(UI_OBJECT_WAIT_TIME_SECONDS):
    object_to_click.click()


def verify_ui_object_visible(ui_object, call_on_fail=None):
  """Verifies that a UI object is visible.

  Args:
    ui_object: A snippet-uiautomator selector object.
    call_on_fail: [Optional] Callable; method to call on failure.
  """
  ui_object_visible = ui_object.wait.exists(UI_OBJECT_WAIT_TIME_SECONDS)
  if not ui_object_visible:
    if call_on_fail is not None:
      call_on_fail()
    raise AssertionError('UI object was not visible!')


def open_jca_viewfinder(dut, log_path):
  """Sends an intent to JCA and open its viewfinder.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
  Raises:
    AssertionError: If JCA viewfinder is not visible.
  """
  its_device_utils.start_its_test_activity(dut.serial)
  call_on_fail = lambda: dut.take_screenshot(log_path, prefix='its_not_found')
  verify_ui_object_visible(
      dut.ui(text=ITS_ACTIVITY_TEXT),
      call_on_fail=call_on_fail
  )

  # Send intent to ItsTestActivity, which will start the correct JCA activity.
  its_device_utils.run(
      f'adb -s {dut.serial} shell am broadcast -a {ACTION_ITS_DO_JCA_CAPTURE}'
  )
  jca_capture_button_visible = dut.ui(
      res=CAPTURE_BUTTON_RESOURCE_ID).wait.exists(
          UI_OBJECT_WAIT_TIME_SECONDS)
  if not jca_capture_button_visible:
    dut.take_screenshot(log_path, prefix=VIEWFINDER_NOT_VISIBLE_PREFIX)
    logging.debug('Current UI dump: %s', dut.ui.dump())
    raise AssertionError('JCA was not started successfully!')
  dut.take_screenshot(log_path, prefix=VIEWFINDER_VISIBLE_PREFIX)


def switch_jca_camera(dut, log_path, facing):
  """Interacts with JCA UI to switch camera if necessary.

  Args:
    dut: An Android controller device object.
    log_path: str; log path to save screenshots.
    facing: str; constant describing the direction the camera lens faces.
  Raises:
    AssertionError: If JCA does not report that camera has been switched.
  """
  if facing == camera_properties_utils.LENS_FACING['BACK']:
    ui_facing_description = UI_DESCRIPTION_BACK_CAMERA
  elif facing == camera_properties_utils.LENS_FACING['FRONT']:
    ui_facing_description = UI_DESCRIPTION_FRONT_CAMERA
  else:
    raise ValueError(f'Unknown facing: {facing}')
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()
  _find_ui_object_else_click(dut.ui(desc=ui_facing_description),
                             dut.ui(res=QUICK_SET_FLIP_CAMERA_RESOURCE_ID))
  if not dut.ui(desc=ui_facing_description).wait.exists(
      UI_OBJECT_WAIT_TIME_SECONDS):
    dut.take_screenshot(log_path, prefix='failed_to_switch_camera')
    logging.debug('JCA UI dump: %s', dut.ui.dump())
    raise AssertionError(f'Failed to switch to {ui_facing_description}!')
  dut.take_screenshot(
      log_path, prefix=f"switched_to_{ui_facing_description.replace(' ', '_')}"
  )
  dut.ui(res=QUICK_SETTINGS_RESOURCE_ID).click()
