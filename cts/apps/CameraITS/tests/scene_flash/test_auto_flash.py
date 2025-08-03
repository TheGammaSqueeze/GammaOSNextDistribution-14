# Copyright 2022 The Android Open Source Project
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
"""Verifies that flash is fired when lighting conditions are dark."""


import logging
import os.path
import pathlib

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import lighting_control_utils

_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_MEAN_DELTA_ATOL = 15  # mean used for reflective charts
_PATCH_H = 0.25  # center 25%
_PATCH_W = 0.25
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]


class AutoFlashTest(its_base_test.UiAutomatorItsBaseTest):
  """Test that flash is fired when lighting conditions are dark using JCA."""

  def setup_class(self):
    super().setup_class()
    self.ui_app = _JETPACK_CAMERA_APP_PACKAGE_NAME

  def test_auto_flash(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      test_name = os.path.join(self.log_path, _TEST_NAME)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      facing_front = (props['android.lens.facing'] ==
                      camera_properties_utils.LENS_FACING['FRONT'])
      should_run = camera_properties_utils.flash(props) or facing_front
      camera_properties_utils.skip_unless(
          should_run and
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL
      )

      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      # take capture with no flash as baseline
      path = pathlib.Path(
          cam.do_jca_capture(
              self.dut,
              self.log_path,
              flash='OFF',
              facing=props['android.lens.facing'],
          )
      )
      no_flash_capture_path = path.with_name(
          f'{path.stem}_no_flash{path.suffix}'
      )
      os.rename(path, no_flash_capture_path)
      cv2_no_flash_image = cv2.imread(str(no_flash_capture_path))
      y, _, _ = cv2.split(cv2.cvtColor(cv2_no_flash_image, cv2.COLOR_BGR2YUV))
      # Add a color channel dimension for interoperability
      y = np.expand_dims(y, axis=2)
      patch = image_processing_utils.get_image_patch(
          y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H
      )
      no_flash_mean = image_processing_utils.compute_image_means(patch)[0]
      image_processing_utils.write_image(y, f'{test_name}_no_flash_Y.jpg')
      logging.debug('No flash frames Y mean: %.4f', no_flash_mean)

      # take capture with auto flash enabled
      logging.debug('Taking capture with auto flash enabled.')
      path = pathlib.Path(
          cam.do_jca_capture(
              self.dut,
              self.log_path,
              flash='AUTO',
              facing=props['android.lens.facing']
          )
      )
      auto_flash_capture_path = path.with_name(
          f'{path.stem}_auto_flash{path.suffix}'
      )
      os.rename(path, auto_flash_capture_path)
      cv2_auto_flash_image = cv2.imread(str(auto_flash_capture_path))
      y, _, _ = cv2.split(cv2.cvtColor(cv2_auto_flash_image, cv2.COLOR_BGR2YUV))
      # Add a color channel dimension for interoperability
      y = np.expand_dims(y, axis=2)
      patch = image_processing_utils.get_image_patch(
          y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H
      )
      flash_mean = image_processing_utils.compute_image_means(patch)[0]
      image_processing_utils.write_image(y, f'{test_name}_auto_flash_Y.jpg')
      logging.debug('Flash frames Y mean: %.4f', flash_mean)

      # confirm correct behavior
      mean_delta = flash_mean - no_flash_mean
      if mean_delta <= _MEAN_DELTA_ATOL:
        raise AssertionError(f'mean FLASH-OFF: {mean_delta:.3f}, '
                             f'ATOL: {_MEAN_DELTA_ATOL}')

      # turn lights back ON
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'ON')

if __name__ == '__main__':
  test_runner.main()

