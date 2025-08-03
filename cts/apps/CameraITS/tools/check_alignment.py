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
"""Tool to check camera alignment to test chart."""

import logging
import os.path

import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
from mobly import test_runner
import opencv_processing_utils
import run_all_tests

_CIRCLE_COLOR = 0  # [0: black, 255: white]
_CIRCLE_MIN_AREA = 0.01  # 1% of image size
_FMT = 'JPEG'
_NAME = os.path.basename(__file__).split('.')[0]
_SCENE = 'scene4'  # Using scene with circle


def _circle_and_image_center_offset(cam, props, name_with_log_path):
  """Find offset between circle center and image center.

  Args:
    cam: its_session_utils.ItsSession object
    props: camera properties object
    name_with_log_path: path to saved data

  Returns:
    x_offset: circle center's x-position vs. image center
    y_offset: circle center's y-position vs. image center
  """

  # Take a single JPEG capture
  logging.debug('Using %s for reference', _FMT)
  fmt = capture_request_utils.get_largest_jpeg_format(props)
  req = capture_request_utils.auto_capture_request()
  cap = cam.do_capture(req, fmt)
  logging.debug('Captured %s %dx%d', _FMT, cap['width'], cap['height'])
  img = image_processing_utils.convert_capture_to_rgb_image(cap, props)
  size = (cap['height'], cap['width'])

  # Get image size
  w = size[1]
  h = size[0]
  img_name = f'{name_with_log_path}_{_FMT}_w{w}_h{h}.png'
  image_processing_utils.write_image(img, img_name, True)

  # Find circle.
  img *= 255  # cv2 needs images between [0,255]
  circle = opencv_processing_utils.find_circle(
      img, img_name, _CIRCLE_MIN_AREA, _CIRCLE_COLOR)
  opencv_processing_utils.append_circle_center_to_img(circle, img, img_name)

  # Determine final return values.
  x_offset, y_offset = circle['x_offset'], circle['y_offset']

  return x_offset, y_offset


class CheckAlignmentTest(its_base_test.ItsBaseTest):
  """Create a single capture that checks for scene center vs. circle center.
  """

  def test_check_alignment(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)
      logging.info('Starting %s for camera %s', _NAME, cam.get_camera_name())

      # Load chart for scene
      run_all_tests.load_scenes_on_tablet(_SCENE, self.tablet.serial)
      its_session_utils.load_scene(
          cam, props, _SCENE, self.tablet, self.chart_distance)

      # Find offset between circle center and image center
      x_offset, y_offset = _circle_and_image_center_offset(
          cam, props, name_with_log_path)
      logging.info('Circle center position wrt to image center: %.3fx%.3f',
                   x_offset, y_offset)

if __name__ == '__main__':
  test_runner.main()

