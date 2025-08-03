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
"""Verifies landscape to portrait override works as intended."""


import logging
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import opencv_processing_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]
_OPT_VAL_THRESHOLD = 0.5
_ENABLED = 'enabled'
_DISABLED = 'disabled'


def _get_true_sensor_orientation(cam, camera_id):
  """Returns a camera's properties and its true sensor orientation.

  Args:
    cam: The camera object
    camera_id: The id of the camera

  Returns:
    Pair of camera properties and sensor orientation.
  """
  props = cam.get_camera_properties_by_id(camera_id,
                                          override_to_portrait=False)
  props = cam.override_with_hidden_physical_camera_props(props)
  sensor_orientation = camera_properties_utils.sensor_orientation(props)
  return (props, sensor_orientation)


def _create_image_folder(log_path, suffix):
  """Creates a new folder for storing debugging images and returns its path.

  Args:
    log_path: The root log path for LandscapeToPortraitTest
    suffix: The suffix string for the image folder ("enabled" or "disabled")

  Returns:
    The newly created log path string, a subfolder of LandscapeToPortraitTest
  """
  image_path = os.path.join(log_path, f'test_landscape_to_portrait_{suffix}')
  os.mkdir(image_path)
  return image_path


def _verify_opt_val(chart):
  """Checks that the opt_val for template matching is sufficient to pass.

  Args:
    chart: The chart for the test
  """
  if chart.opt_val is None:
    raise AssertionError('Unable to find a template match!')
  elif chart.opt_val < _OPT_VAL_THRESHOLD:
    raise AssertionError('Poor template match (opt val: '
                         f'{chart.opt_val:.3f} thresh: {_OPT_VAL_THRESHOLD}'
                         '). Check jpeg output.')


class LandscapeToPortraitTest(its_base_test.ItsBaseTest):
  """Test the landscape to portrait override.

  Note the test does not require a specific target but does perform
  both automatic and manual captures so it requires a fixed scene
  where 3A can converge.
  """

  def test_landscape_to_portrait_enabled(self):
    logging.debug('Starting %s: %s', _NAME, _ENABLED)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id,
        override_to_portrait=True) as cam:

      # Read the properties with overrideToPortrait off to find the real sensor
      # orientation
      props, sensor_orientation = _get_true_sensor_orientation(
          cam, self.camera_id)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          cam.is_landscape_to_portrait_enabled() and
          (sensor_orientation == 0 or sensor_orientation == 180))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Initialize chart class and locate chart in scene
      # Make a separate log dir for the chart images, otherwise they'll get
      # overwritten
      image_path = _create_image_folder(self.log_path, _ENABLED)
      chart = opencv_processing_utils.Chart(
          cam, props, image_path, distance=self.chart_distance, rotation=90)

      _verify_opt_val(chart)

  def test_landscape_to_portrait_disabled(self):
    logging.debug('Starting %s: %s', _NAME, _DISABLED)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      # Read the properties with overrideToPortrait off to find the real sensor
      # orientation
      props, sensor_orientation = _get_true_sensor_orientation(
          cam, self.camera_id)

      # check SKIP conditions.
      camera_properties_utils.skip_unless(
          sensor_orientation == 0 or sensor_orientation == 180)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Initialize chart class and locate chart in scene
      # Make a separate log dir for the chart images, otherwise they'll get
      # overwritten
      image_path = _create_image_folder(self.log_path, _DISABLED)
      chart = opencv_processing_utils.Chart(
          cam, props, image_path, distance=self.chart_distance)

      _verify_opt_val(chart)

if __name__ == '__main__':
  test_runner.main()
