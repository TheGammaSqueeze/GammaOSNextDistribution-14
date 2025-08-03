# Copyright 2018 The Android Open Source Project
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
"""CameraITS test for tonemap curve with sensor test pattern."""

import logging
import os

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_NAME = os.path.basename(__file__).split('.')[0]
_COLOR_BAR_PATTERN = 2  # Note scene0/test_test_patterns must PASS
_COLOR_BARS = ['WHITE', 'YELLOW', 'CYAN', 'GREEN', 'MAGENTA', 'RED',
               'BLUE', 'BLACK']
_N_BARS = len(_COLOR_BARS)
_COLOR_CHECKER = {'BLACK': [0, 0, 0], 'RED': [1, 0, 0], 'GREEN': [0, 1, 0],
                  'BLUE': [0, 0, 1], 'MAGENTA': [1, 0, 1], 'CYAN': [0, 1, 1],
                  'YELLOW': [1, 1, 0], 'WHITE': [1, 1, 1]}
_DELTA = 0.005  # crop on each edge of color bars
_RAW_TOL = 0.001  # 1 DN in [0:1] (1/(1023-64)
_RGB_VAR_TOL = 0.0039  # 1/255
_RGB_MEAN_TOL = 0.1
_TONEMAP_MAX = 0.5
_YUV_H = 480
_YUV_W = 640
# Normalized co-ordinates for the color bar patch.
_Y_NORM = 0.0
_W_NORM = 1.0 / _N_BARS - 2 * _DELTA
_H_NORM = 1.0

# Linear tonemap with maximum of 0.5
_LINEAR_TONEMAP = sum([[i/63.0, i/126.0] for i in range(64)], [])


def get_yuv_patch_coordinates(num, w_orig, w_crop):
  """Returns the normalized x co-ordinate for the title.

  Args:
   num: int; position on color in the color bar.
   w_orig: float; original RAW image W
   w_crop: float; cropped RAW image W

  Returns:
    normalized x, w values for color patch.
  """
  if w_crop == w_orig:  # uncropped image
    x_norm = num / _N_BARS + _DELTA
    w_norm = 1 / _N_BARS - 2 * _DELTA
    logging.debug('x_norm: %.5f, w_norm: %.5f', x_norm, w_norm)
  elif w_crop < w_orig:  # adjust patch width to match vertical RAW crop
    w_delta_edge = (w_orig - w_crop) / 2
    w_bar_orig = w_orig / _N_BARS
    if num == 0:  # left-most bar
      x_norm = _DELTA
      w_norm = (w_bar_orig - w_delta_edge) / w_crop - 2 * _DELTA
    elif num == _N_BARS:  # right-most bar
      x_norm = (w_bar_orig * num - w_delta_edge) / w_crop + _DELTA
      w_norm = (w_bar_orig - w_delta_edge) / w_crop - 2 * _DELTA
    else:  # middle bars
      x_norm = (w_bar_orig * num - w_delta_edge) / w_crop + _DELTA
      w_norm = w_bar_orig / w_crop - 2 * _DELTA
    logging.debug('x_norm: %.5f, w_norm: %.5f (crop-corrected)', x_norm, w_norm)
  else:
    raise AssertionError('Cropped image is larger than original!')
  return x_norm, w_norm


def get_x_norm(num):
  """Returns the normalized x co-ordinate for the title.

  Args:
   num: int; position on color in the color bar.

  Returns:
    normalized x co-ordinate.
  """
  return float(num) / _N_BARS + _DELTA


def check_raw_pattern(img_raw):
  """Checks for RAW capture matches color bar pattern.

  Args:
    img_raw: RAW image
  """
  logging.debug('Checking RAW/PATTERN match')
  color_match = []
  for n in range(_N_BARS):
    x_norm = get_x_norm(n)
    raw_patch = image_processing_utils.get_image_patch(img_raw, x_norm, _Y_NORM,
                                                       _W_NORM, _H_NORM)
    raw_means = image_processing_utils.compute_image_means(raw_patch)
    logging.debug('patch: %d, x_norm: %.3f, RAW means: %s',
                  n, x_norm, str(raw_means))
    for color in _COLOR_BARS:
      if np.allclose(_COLOR_CHECKER[color], raw_means, atol=_RAW_TOL):
        color_match.append(color)
        logging.debug('%s match', color)
        break
      else:
        logging.debug('No match w/ %s: %s, ATOL: %.3f',
                      color, str(_COLOR_CHECKER[color]), _RAW_TOL)
  if set(color_match) != set(_COLOR_BARS):
    raise AssertionError(
        'RAW _COLOR_BARS test pattern does not have all colors')


def check_yuv_vs_raw(img_raw, img_yuv, name_with_log_path):
  """Checks for YUV vs RAW match in 8 patches.

  Check for correct values and color consistency

  Args:
    img_raw: RAW image
    img_yuv: YUV image
    name_with_log_path: string for test name with path
  """
  logging.debug('Checking YUV/RAW match')
  raw_w = img_raw.shape[1]
  raw_h = img_raw.shape[0]
  raw_aspect_ratio = raw_w/raw_h
  yuv_aspect_ratio = _YUV_W/_YUV_H
  logging.debug('raw_img: W, H, AR: %d, %d, %.3f',
                raw_w, raw_h, raw_aspect_ratio)

  # Crop RAW to match YUV 4:3 format
  raw_w_cropped = raw_w
  if raw_aspect_ratio > yuv_aspect_ratio:  # vertical crop sensor
    logging.debug('Cropping RAW to match YUV aspect ratio.')
    w_norm_raw = yuv_aspect_ratio / raw_aspect_ratio
    x_norm_raw = (1 - w_norm_raw) / 2
    img_raw = image_processing_utils.get_image_patch(
        img_raw, x_norm_raw, 0, w_norm_raw, 1)
    raw_w_cropped = img_raw.shape[1]
    logging.debug('New RAW W, H: %d, %d', raw_w_cropped, img_raw.shape[0])
    image_processing_utils.write_image(
        img_raw, f'{name_with_log_path}_raw_cropped_COLOR_BARS.jpg', True)

  # Compare YUV and RAW color patches
  color_match_errs = []
  color_variance_errs = []
  for n in range(_N_BARS):
    x_norm, w_norm = get_yuv_patch_coordinates(n, raw_w, raw_w_cropped)
    raw_patch = image_processing_utils.get_image_patch(img_raw, x_norm, _Y_NORM,
                                                       w_norm, _H_NORM)
    yuv_patch = image_processing_utils.get_image_patch(img_yuv, x_norm, _Y_NORM,
                                                       w_norm, _H_NORM)
    raw_means = np.array(image_processing_utils.compute_image_means(raw_patch))
    raw_vars = np.array(
        image_processing_utils.compute_image_variances(raw_patch))
    yuv_means = np.array(image_processing_utils.compute_image_means(yuv_patch))
    yuv_means /= _TONEMAP_MAX  # Normalize to tonemap max
    yuv_vars = np.array(
        image_processing_utils.compute_image_variances(yuv_patch))
    if not np.allclose(raw_means, yuv_means, atol=_RGB_MEAN_TOL):
      color_match_errs.append(
          f'means RAW: {raw_means}, RGB(norm): {np.round(yuv_means, 3)}, '
          f'ATOL: {_RGB_MEAN_TOL}')
      image_processing_utils.write_image(
          raw_patch, f'{name_with_log_path}_match_error_raw_{n}.jpg',
          apply_gamma=True)
      image_processing_utils.write_image(
          yuv_patch, f'{name_with_log_path}_match_error_yuv_{n}.jpg',
          apply_gamma=True)
    if not np.allclose(raw_vars, yuv_vars, atol=_RGB_VAR_TOL):
      color_variance_errs.append(
          f'variances RAW: {raw_vars}, RGB: {yuv_vars}, '
          f'ATOL: {_RGB_VAR_TOL}')
      image_processing_utils.write_image(
          raw_patch, f'{name_with_log_path}_variance_error_raw_{n}.jpg',
          apply_gamma=True)
      image_processing_utils.write_image(
          yuv_patch, f'{name_with_log_path}_variance_error_yuv_{n}.jpg',
          apply_gamma=True)

  # Print all errors before assertion
  if color_match_errs:
    for err in color_match_errs:
      logging.debug(err)
    for err in color_variance_errs:
      logging.error(err)
    raise AssertionError('Color match errors. See test_log.DEBUG')
  if color_variance_errs:
    for err in color_variance_errs:
      logging.error(err)
    raise AssertionError('Color variance errors. See test_log.DEBUG')


def test_tonemap_curve_impl(name_with_log_path, cam, props):
  """Test tonemap curve with sensor test pattern.

  Args:
   name_with_log_path: Path to save the captured image.
   cam: An open device session.
   props: Properties of cam.
  """

  avail_patterns = props['android.sensor.availableTestPatternModes']
  logging.debug('Available Patterns: %s', avail_patterns)
  sens_min, _ = props['android.sensor.info.sensitivityRange']
  min_exposure = min(props['android.sensor.info.exposureTimeRange'])

  # RAW image
  req_raw = capture_request_utils.manual_capture_request(
      int(sens_min), min_exposure)
  req_raw['android.sensor.testPatternMode'] = _COLOR_BAR_PATTERN
  fmt_raw = {'format': 'raw'}
  cap_raw = cam.do_capture(req_raw, fmt_raw)
  img_raw = image_processing_utils.convert_capture_to_rgb_image(
      cap_raw, props=props)

  # Save RAW pattern
  image_processing_utils.write_image(
      img_raw, f'{name_with_log_path}_raw_COLOR_BARS.jpg', True)

  # Check pattern for correctness
  check_raw_pattern(img_raw)

  # YUV image
  req_yuv = capture_request_utils.manual_capture_request(
      int(sens_min), min_exposure)
  req_yuv['android.sensor.testPatternMode'] = _COLOR_BAR_PATTERN
  req_yuv['android.distortionCorrection.mode'] = 0
  req_yuv['android.tonemap.mode'] = 0
  req_yuv['android.tonemap.curve'] = {
      'red': _LINEAR_TONEMAP,
      'green': _LINEAR_TONEMAP,
      'blue': _LINEAR_TONEMAP
  }
  fmt_yuv = {'format': 'yuv', 'width': _YUV_W, 'height': _YUV_H}
  cap_yuv = cam.do_capture(req_yuv, fmt_yuv)
  img_yuv = image_processing_utils.convert_capture_to_rgb_image(cap_yuv, True)

  # Save YUV pattern
  image_processing_utils.write_image(
      img_yuv, f'{name_with_log_path}_yuv_COLOR_BARS.jpg', True)

  # Check pattern for correctness
  check_yuv_vs_raw(img_raw, img_yuv, name_with_log_path)


class TonemapCurveTest(its_base_test.ItsBaseTest):
  """Test conversion of test pattern from RAW to YUV with linear tonemap.

  Test makes use of android.sensor.testPatternMode 2 (_COLOR_BARS).
  """

  def test_tonemap_curve(self):
    name_with_log_path = os.path.join(self.log_path, _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw16(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.manual_post_proc(props) and
          camera_properties_utils.color_bars_test_pattern(props))

      test_tonemap_curve_impl(name_with_log_path, cam, props)


if __name__ == '__main__':
  test_runner.main()
