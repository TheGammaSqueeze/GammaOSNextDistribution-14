# Copyright 2016 The Android Open Source Project
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
"""CameraITS test to check test patterns generation."""

import logging
import math
import os

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_NAME = os.path.basename(__file__).split('.')[0]
_CHECKED_PATTERNS = [1, 2, 5]  # [SOLID_COLOR, COLOR_BARS, BLACK]
_COLOR_BAR_ORDER = ['WHITE', 'YELLOW', 'CYAN', 'GREEN', 'MAGENTA', 'RED',
                    'BLUE', 'BLACK']
_COLOR_CHECKER = {'BLACK': [0, 0, 0], 'RED': [1, 0, 0], 'GREEN': [0, 1, 0],
                  'BLUE': [0, 0, 1], 'MAGENTA': [1, 0, 1], 'CYAN': [0, 1, 1],
                  'YELLOW': [1, 1, 0], 'WHITE': [1, 1, 1]}
_CH_TOL = 2E-3  # 1/2 DN in [0:1]


def check_solid_color(cap, props):
  """Checks for solid color test pattern.

  Args:
    cap: capture element
    props: capture properties

  Returns:
    True/False
  """
  logging.debug('Checking solid TestPattern...')
  r, gr, gb, b = image_processing_utils.convert_capture_to_planes(cap, props)
  r_tile = image_processing_utils.get_image_patch(r, 0.0, 0.0, 1.0, 1.0)
  gr_tile = image_processing_utils.get_image_patch(gr, 0.0, 0.0, 1.0, 1.0)
  gb_tile = image_processing_utils.get_image_patch(gb, 0.0, 0.0, 1.0, 1.0)
  b_tile = image_processing_utils.get_image_patch(b, 0.0, 0.0, 1.0, 1.0)
  var_max = max(
      np.amax(r_tile), np.amax(gr_tile), np.amax(gb_tile), np.amax(b_tile))
  var_min = min(
      np.amin(r_tile), np.amin(gr_tile), np.amin(gb_tile), np.amin(b_tile))
  white_level = int(props['android.sensor.info.whiteLevel'])
  logging.debug('pixel min: %.f, pixel max: %.f', white_level * var_min,
                white_level * var_max)
  return math.isclose(var_max, var_min, abs_tol=_CH_TOL)


def check_color_bars(cap, props, mirror=False):
  """Checks for color bar test pattern.Compute avg of bars and compare to ideal.

  Args:
    cap: capture element
    props: capture properties
    mirror: boolean; whether to mirror image or not

  Returns:
    True/False


  """
  logging.debug('Checking color bar TestPattern...')
  delta = 0.0005
  num_bars = len(_COLOR_BAR_ORDER)
  color_match = []
  img = image_processing_utils.convert_capture_to_rgb_image(cap, props=props)
  if mirror:
    logging.debug('Image mirrored')
    img = np.fliplr(img)
  for i, color in enumerate(_COLOR_BAR_ORDER):
    tile = image_processing_utils.get_image_patch(img,
                                                  float(i) / num_bars + delta,
                                                  0.0,
                                                  1.0 / num_bars - 2 * delta,
                                                  1.0)
    color_match.append(
        np.allclose(
            image_processing_utils.compute_image_means(tile),
            _COLOR_CHECKER[color],
            atol=_CH_TOL))
  logging.debug(_COLOR_BAR_ORDER)
  logging.debug(color_match)
  return all(color_match)


def check_pattern(cap, props, pattern):
  """Checks for pattern correctness.

  Args:
    cap: capture element
    props: capture properties
    pattern (int): valid number for pattern

  Returns:
    True/False
  """
  if pattern == 1 or pattern == 5:  # solid color or black
    return check_solid_color(cap, props)
  elif pattern == 2:  # color bars
    striped = check_color_bars(cap, props, mirror=False)
    # check mirrored version in case image rotated from sensor orientation
    if not striped:
      striped = check_color_bars(cap, props, mirror=True)
    return striped
  else:
    logging.debug('No specific test for TestPattern: %d', pattern)
    return True


def test_test_patterns_impl(cam, props, af_fd, name_with_log_path):
  """Image sensor test patterns implementation.

  Args:
    cam: An open device session.
    props: Properties of cam
    af_fd: Focus distance
    name_with_log_path: Path to save the captured image.
  """

  avail_patterns = props['android.sensor.availableTestPatternModes']
  logging.debug('avail_patterns: %s', avail_patterns)
  sens_min, _ = props['android.sensor.info.sensitivityRange']
  exposure = min(props['android.sensor.info.exposureTimeRange'])

  for pattern in _CHECKED_PATTERNS:
    if pattern in avail_patterns:
      req = capture_request_utils.manual_capture_request(
          int(sens_min), exposure)
      req['android.lens.focusDistance'] = af_fd
      req['android.sensor.testPatternMode'] = pattern
      fmt = {'format': 'raw'}
      cap = cam.do_capture(req, fmt)
      img = image_processing_utils.convert_capture_to_rgb_image(
          cap, props=props)
      # Save pattern
      image_processing_utils.write_image(
          img, f'{name_with_log_path}_{pattern}.jpg', True)

      # Check pattern for correctness
      if not check_pattern(cap, props, pattern):
        raise AssertionError(f'Pattern {pattern} failed')
    else:
      logging.debug('%d not in android.sensor.availableTestPatternModes.',
                    pattern)


class TestPatterns(its_base_test.ItsBaseTest):
  """Test pattern generation test.

    Test: Capture frames for each valid test pattern and check if
    generated correctly.
    android.sensor.testPatternMode
    0: OFF
    1: SOLID_COLOR
    2: COLOR_BARS
    3: COLOR_BARS_FADE_TO_GREY
    4: PN9
    5: BLACK (test/system only)
  """

  def test_test_patterns(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw16(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.per_frame_control(props))

      # For test pattern, use min_fd
      focus_distance = props['android.lens.info.minimumFocusDistance']
      name_with_log_path = os.path.join(self.log_path, _NAME)
      test_test_patterns_impl(cam, props, focus_distance, name_with_log_path)

if __name__ == '__main__':
  test_runner.main()
