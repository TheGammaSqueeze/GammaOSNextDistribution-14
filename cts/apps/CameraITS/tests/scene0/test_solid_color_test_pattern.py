# Copyright 2021 The Android Open Source Project
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
"""CameraITS test to check solid color test pattern generation."""

import logging
import os

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


# YUV TOLs
_BW_CH_ATOL_YUV = 6  # DN in [0,255]
_RGB_PRIMARY_MIN_YUV = 200
_RGB_SECONDARY_MAX_YUV = 60
_CH_VARIANCE_ATOL_YUV = 30
# RAW TOLs
_BW_CH_ATOL_RAW = 1  # DN in [0,255]
_RGB_PRIMARY_MIN_RAW = 250
_RGB_SECONDARY_MAX_RAW = 30  # experiments show secondary after CCM applied
_CH_VARIANCE_ATOL_RAW = 1
_OFF = 0x00000000
_SAT = 0xFFFFFFFF
_NAME = os.path.basename(__file__).split('.')[0]
_SHARPNESS_TOL = 0.1
_NUM_FRAMES = 4  # buffer a few frames to eliminate need for PER_FRAME_CONTROL

_BLACK = {'color': 'BLACK',
          'RGGB': (_OFF, _OFF, _OFF, _OFF),
          'RGB': (0, 0, 0)}
_WHITE = {'color': 'WHITE',
          'RGGB': (_SAT, _SAT, _SAT, _SAT),
          'RGB': (255, 255, 255)}
_RED = {'color': 'RED',
        'RGGB': (_SAT, _OFF, _OFF, _OFF),
        'RGB': (255, 0, 0)}
_GREEN = {'color': 'GREEN',
          'RGGB': (_OFF, _SAT, _SAT, _OFF),
          'RGB': (0, 255, 0)}
_BLUE = {'color': 'BLUE',
         'RGGB': (_OFF, _OFF, _OFF, _SAT),
         'RGB': (0, 0, 255)}

_COLORS_CHECKED_RAW = (_BLACK, _WHITE, _RED, _GREEN, _BLUE)
_COLORS_CHECKED_YUV = (_BLACK,)
_COLORS_CHECKED_MONO = (_BLACK, _WHITE)
_COLORS_CHECKED_UPGRADE = (_BLACK,)
_COLORS_CHECKED_BLACK = (_WHITE,)  # To make sure testPatternData is ignored
_FULL_CHECK_FIRST_API_LEVEL = 31
_SOLID_COLOR_TEST_PATTERN = 1
_BLACK_TEST_PATTERN = 5

_PATTERN_NAMES = {_SOLID_COLOR_TEST_PATTERN: 'SOLID_COLOR',
                  _BLACK_TEST_PATTERN: 'BLACK'}


def check_solid_color(img, exp_values, color, fmt):
  """Checks solid color test pattern image matches expected values.

  Args:
    img: capture converted to RGB image
    exp_values: list of RGB [0:1] expected values
    color: str; color to check
    fmt: str; capture format.
  Returns:
    True if any of the checks fail.
  """
  # Assign tolerances
  if fmt == 'raw':
    bw_ch_atol = _BW_CH_ATOL_RAW
    rgb_primary_min = _RGB_PRIMARY_MIN_RAW
    rgb_secondary_max = _RGB_SECONDARY_MAX_RAW
    ch_variance_atol = _CH_VARIANCE_ATOL_RAW
  else:
    bw_ch_atol = _BW_CH_ATOL_YUV
    rgb_primary_min = _RGB_PRIMARY_MIN_YUV
    rgb_secondary_max = _RGB_SECONDARY_MAX_YUV
    ch_variance_atol = _CH_VARIANCE_ATOL_YUV

  test_fail = False
  logging.debug('Checking %s solid test pattern w/ RGB values %s',
                color, str(exp_values))
  rgb_means = [m*255 for m in image_processing_utils.compute_image_means(img)]
  logging.debug('Captured frame averages: %s', str(rgb_means))
  rgb_vars = [v*255*255 for v in
              image_processing_utils.compute_image_variances(img)]
  logging.debug('Captured frame variances: %s', str(rgb_vars))
  if color in ['BLACK', 'WHITE']:
    if not np.allclose(rgb_means, exp_values, atol=bw_ch_atol):
      logging.error('Image not expected value for color %s. '
                    'RGB means: %s, expected: %s, ATOL: %d',
                    color, str(rgb_means), str(exp_values), bw_ch_atol)
      test_fail = True
    if not all(i < ch_variance_atol for i in rgb_vars):
      logging.error('Image has too much variance for color %s. '
                    'RGB variances: %s, ATOL: %d',
                    color, str(rgb_vars), ch_variance_atol)
      test_fail = True
  else:
    exp_values_mask = np.array(exp_values)//255
    primary = max(rgb_means*exp_values_mask)
    secondary = max((1-exp_values_mask)*rgb_means)
    if primary < rgb_primary_min:
      logging.error('Primary color %s not bright enough.'
                    'RGB means: %s, expected: %s, MIN: %d',
                    color, str(rgb_means), str(exp_values), rgb_primary_min)
      test_fail = True
    if secondary > rgb_secondary_max:
      logging.error('Secondary colors too bright in %s. '
                    'RGB means: %s, expected: %s, MAX: %d',
                    color, str(rgb_means), str(exp_values), rgb_secondary_max)
      test_fail = True

    primary_rgb_vars = max(rgb_vars*exp_values_mask)
    secondary_rgb_vars = max((1-exp_values_mask)*rgb_vars)
    if primary_rgb_vars > ch_variance_atol:
      logging.error('Image primary color has too much variance for %s. '
                    'RGB variances: %s, ATOL: %d',
                    color, str(rgb_vars), ch_variance_atol)
      test_fail = True
    elif secondary_rgb_vars > ch_variance_atol:
      logging.error('Image secondary color has too much variance for %s. '
                    'RGB variances: %s, ATOL: %d',
                    color, str(rgb_vars), ch_variance_atol)
  return test_fail


class SolidColorTestPattern(its_base_test.ItsBaseTest):
  """Solid Color test pattern generation test.

    Test: Capture frame for the SOLID_COLOR test pattern with the values set
    and check YUV image matches request.

    android.sensor.testPatternMode
    0: OFF
    1: SOLID_COLOR
    2: COLOR_BARS
    3: COLOR_BARS_FADE_TO_GREY
    4: PN9
    5: BLACK
  """

  def test_solid_color_test_pattern(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Determine patterns to check based on API level and test pattern
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      if first_api_level >= _FULL_CHECK_FIRST_API_LEVEL:
        if camera_properties_utils.mono_camera(props):
          colors_checked_solid = _COLORS_CHECKED_MONO
        else:
          if camera_properties_utils.raw16(props):
            colors_checked_solid = _COLORS_CHECKED_RAW
          else:
            colors_checked_solid = _COLORS_CHECKED_YUV
      else:
        colors_checked_solid = _COLORS_CHECKED_UPGRADE

      # Determine which patterns are available to test on the device
      available_patterns = set(props[
          'android.sensor.availableTestPatternModes'])
      patterns_to_check = available_patterns.intersection(
          {_SOLID_COLOR_TEST_PATTERN, _BLACK_TEST_PATTERN})

      # Determine if test is run or skipped
      if cam.is_camera_privacy_mode_supported():
        if not patterns_to_check:
          raise AssertionError('neither SOLID_COLOR or BLACK are '
                               'in android.sensor.availableTestPatternModes.')
      else:
        camera_properties_utils.skip_unless(patterns_to_check)

      # Take extra frames if no per-frame control
      if camera_properties_utils.per_frame_control(props):
        num_frames = 1
      else:
        num_frames = _NUM_FRAMES

      test_failed = False
      colors_failed = []
      # Start checking patterns
      for pattern in patterns_to_check:
        if pattern == _SOLID_COLOR_TEST_PATTERN:
          colors_checked = colors_checked_solid
        else:
          colors_checked = _COLORS_CHECKED_BLACK

        captured_pattern = _PATTERN_NAMES[pattern]
        # Start checking colors
        for color in colors_checked:
          logging.debug('Assigned RGGB values %s',
                        str([int(c/_SAT) for c in color['RGGB']]))
          req = capture_request_utils.auto_capture_request()
          req['android.sensor.testPatternMode'] = pattern
          req['android.sensor.testPatternData'] = color['RGGB']
          if camera_properties_utils.raw16(props):
            fmt = {'format': 'raw'}
          else:
            fmt = {'format': 'yuv'}
          logging.debug('Using format: %s', fmt['format'])
          caps = cam.do_capture([req]*num_frames, fmt)
          cap = caps[-1]
          logging.debug('Capture metadata RGGB pattern: %s, '
                        'testPatternData: %s', captured_pattern,
                        str(cap['metadata']['android.sensor.testPatternData']))
          # Save test pattern image
          if fmt['format'] == 'yuv':
            img = image_processing_utils.convert_capture_to_rgb_image(
                cap, props=props)
          else:
            img = image_processing_utils.convert_capture_to_rgb_image(
                cap, props=props, apply_ccm_raw_to_rgb=False)
          captured_color = color['color']
          image_processing_utils.write_image(
              img,
              f'{os.path.join(self.log_path, _NAME)}_{captured_color}_'
              f'{captured_pattern}.jpg')

          # Check solid pattern for correctness
          if pattern == _SOLID_COLOR_TEST_PATTERN:
            color_test_failed = check_solid_color(
                img, color['RGB'], color['color'], fmt['format'])
          else:
            color_test_failed = check_solid_color(
                img, _BLACK['RGB'], _BLACK['color'], fmt['format'])

          if color_test_failed:
            colors_failed.append(f'{captured_pattern}/{captured_color}')
            if not test_failed:
              test_failed = True

          logging.debug('Solid color test pattern for pattern %s color %s '
                        'is a %s', captured_pattern, captured_color,
                        ('FAIL' if color_test_failed else 'PASS'))
      if test_failed:
        raise AssertionError('Test solid_color_test_pattern failed for colors:'
                             f'{colors_failed}')

if __name__ == '__main__':
  test_runner.main()
