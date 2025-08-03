# Copyright 2013 The Android Open Source Project
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
"""Verifies correct exposure control."""


import logging
import os.path
import matplotlib
from matplotlib import pylab

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_EXP_CORRECTION_FACTOR = 2  # mult or div factor to correct brightness
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_PTS_2X_GAIN = 3  # 3 points every 2x increase in gain
_PATCH_H = 0.1  # center 10% patch params
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RAW_STATS_GRID = 9  # define 9x9 (11.11%) spacing grid for rawStats processing
_RAW_STATS_XY = _RAW_STATS_GRID//2  # define X, Y location for center rawStats
_THRESH_MIN_LEVEL = 0.1
_THRESH_MAX_LEVEL = 0.9
_THRESH_MAX_LEVEL_DIFF = 0.045
_THRESH_MAX_LEVEL_DIFF_WIDE_RANGE = 0.06
_THRESH_MAX_OUTLIER_DIFF = 0.1
_THRESH_ROUND_DOWN_ISO = 0.04
_THRESH_ROUND_DOWN_EXP = 0.03
_THRESH_ROUND_DOWN_EXP0 = 1.00  # TOL at 0ms exp; theoretical limit @ 4-line exp
_THRESH_EXP_KNEE = 6E6  # exposures less than knee have relaxed tol
_WIDE_EXP_RANGE_THRESH = 64.0  # threshold for 'wide' range sensor


def adjust_exp_for_brightness(
    cam, props, fmt, exp, iso, sync_latency, test_name_with_path):
  """Take an image and adjust exposure and sensitivity.

  Args:
    cam: camera object
    props: camera properties dict
    fmt: capture format
    exp: exposure time (ns)
    iso: sensitivity
    sync_latency: number for sync latency
    test_name_with_path: path for saved files

  Returns:
    adjusted exposure
  """
  req = capture_request_utils.manual_capture_request(
      iso, exp, 0.0, True, props)
  cap = its_session_utils.do_capture_with_latency(
      cam, req, sync_latency, fmt)
  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  image_processing_utils.write_image(
      img, f'{test_name_with_path}.jpg')
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  r, g, b = image_processing_utils.compute_image_means(patch)
  logging.debug('Sample RGB values: %.3f, %.3f, %.3f', r, g, b)
  if g < _THRESH_MIN_LEVEL:
    exp *= _EXP_CORRECTION_FACTOR
    logging.debug('exp increased by %dx: %d', _EXP_CORRECTION_FACTOR, exp)
  elif g > _THRESH_MAX_LEVEL:
    exp //= _EXP_CORRECTION_FACTOR
    logging.debug('exp decreased to 1/%dx: %d', _EXP_CORRECTION_FACTOR, exp)
  return exp


def plot_rgb_means(title, x, r, g, b, test_name_with_path):
  """Plot the RGB mean data.

  Args:
    title: string for figure title
    x: x values for plot, gain multiplier
    r: r plane means
    g: g plane means
    b: b plane menas
    test_name_with_path: path for saved files
  """
  pylab.figure(title)
  pylab.semilogx(x, r, 'ro-')
  pylab.semilogx(x, g, 'go-')
  pylab.semilogx(x, b, 'bo-')
  pylab.title(f'{_NAME} {title}')
  pylab.xlabel('Gain Multiplier')
  pylab.ylabel('Normalized RGB Plane Avg')
  pylab.minorticks_off()
  pylab.xticks(x[0::_NUM_PTS_2X_GAIN], x[0::_NUM_PTS_2X_GAIN])
  pylab.ylim([0, 1])
  plot_name = f'{test_name_with_path}_plot_rgb_means.png'
  matplotlib.pyplot.savefig(plot_name)


def plot_raw_means(title, x, r, gr, gb, b, test_name_with_path):
  """Plot the RAW mean data.

  Args:
    title: string for figure title
    x: x values for plot, gain multiplier
    r: R plane means
    gr: Gr plane means
    gb: Gb plane means
    b: B plane menas
    test_name_with_path: path for saved files
  """
  pylab.figure(title)
  pylab.semilogx(x, r, 'ro-', label='R')
  pylab.semilogx(x, gr, 'go-', label='Gr')
  pylab.semilogx(x, gb, 'ko-', label='Gb')
  pylab.semilogx(x, b, 'bo-', label='B')
  pylab.title(f'{_NAME} {title}')
  pylab.xlabel('Gain Multiplier')
  pylab.ylabel('Normalized RAW Plane Avg')
  pylab.minorticks_off()
  pylab.xticks(x[0::_NUM_PTS_2X_GAIN], x[0::_NUM_PTS_2X_GAIN])
  pylab.ylim([0, 1])
  pylab.legend(numpoints=1)
  plot_name = f'{test_name_with_path}_plot_raw_means.png'
  matplotlib.pyplot.savefig(plot_name)


def check_line_fit(color, mults, values, thresh_max_level_diff):
  """Find line fit and check values.

  Check for linearity. Verify sample pixel mean values are close to each
  other. Also ensure that the images aren't clamped to 0 or 1
  (which would also make them look like flat lines).

  Args:
    color: string to define RGB or RAW channel
    mults: list of multiplication values for gain*m, exp/m
    values: mean values for chan
    thresh_max_level_diff: threshold for max difference
  """

  m, b = np.polyfit(mults, values, 1).tolist()
  min_val = min(values)
  max_val = max(values)
  max_diff = max_val - min_val
  logging.debug('Channel %s line fit (y = mx+b): m = %f, b = %f', color, m, b)
  logging.debug('Channel min %f max %f diff %f', min_val, max_val, max_diff)
  if max_diff >= thresh_max_level_diff:
    raise AssertionError(f'max_diff: {max_diff:.4f}, '
                         f'THRESH: {thresh_max_level_diff:.3f}')
  if not _THRESH_MAX_LEVEL > b > _THRESH_MIN_LEVEL:
    raise AssertionError(f'b: {b:.2f}, THRESH_MIN: {_THRESH_MIN_LEVEL}, '
                         f'THRESH_MAX: {_THRESH_MAX_LEVEL}')
  for v in values:
    if not _THRESH_MAX_LEVEL > v > _THRESH_MIN_LEVEL:
      raise AssertionError(f'v: {v:.2f}, THRESH_MIN: {_THRESH_MIN_LEVEL}, '
                           f'THRESH_MAX: {_THRESH_MAX_LEVEL}')

    if abs(v - b) >= _THRESH_MAX_OUTLIER_DIFF:
      raise AssertionError(f'v: {v:.2f}, b: {b:.2f}, '
                           f'THRESH_DIFF: {_THRESH_MAX_OUTLIER_DIFF}')


def get_raw_active_array_size(props):
  """Return the active array w, h from props."""
  aaw = (props['android.sensor.info.preCorrectionActiveArraySize']['right'] -
         props['android.sensor.info.preCorrectionActiveArraySize']['left'])
  aah = (props['android.sensor.info.preCorrectionActiveArraySize']['bottom'] -
         props['android.sensor.info.preCorrectionActiveArraySize']['top'])
  return aaw, aah


class ExposureXIsoTest(its_base_test.ItsBaseTest):
  """Test that a constant brightness is seen as ISO and exposure time vary.

  Take a series of shots that have ISO and exposure time chosen to balance
  each other; result should be the same brightness, but over the sequence
  the images should get noisier.
  """

  def test_exposure_x_iso(self):
    mults = []
    r_means = []
    g_means = []
    b_means = []
    raw_r_means = []
    raw_gr_means = []
    raw_gb_means = []
    raw_b_means = []
    thresh_max_level_diff = _THRESH_MAX_LEVEL_DIFF

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      test_name_with_path = os.path.join(self.log_path, _NAME)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Initialize params for requests
      debug = self.debug_mode
      raw_avlb = (camera_properties_utils.raw16(props) and
                  camera_properties_utils.manual_sensor(props))
      sync_latency = camera_properties_utils.sync_latency(props)
      logging.debug('sync latency: %d frames', sync_latency)
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      e, s = target_exposure_utils.get_target_exposure_combos(
          self.log_path, cam)['minSensitivity']

      # Take a shot and adjust parameters for brightness
      logging.debug('Target exposure combo values. exp: %d, iso: %d',
                    e, s)
      e = adjust_exp_for_brightness(
          cam, props, fmt, e, s, sync_latency, test_name_with_path)

      # Initialize values to define test range
      s_e_product = s * e
      expt_range = props['android.sensor.info.exposureTimeRange']
      sens_range = props['android.sensor.info.sensitivityRange']
      m = 1.0

      # Do captures with a range of exposures, but constant s*e
      while s*m < sens_range[1] and e/m > expt_range[0]:
        mults.append(m)
        s_req = round(s * m)
        e_req = s_e_product // s_req
        logging.debug('Testing s: %d, e: %dns', s_req, e_req)
        req = capture_request_utils.manual_capture_request(
            s_req, e_req, 0.0, True, props)
        cap = its_session_utils.do_capture_with_latency(
            cam, req, sync_latency, fmt)
        s_res = cap['metadata']['android.sensor.sensitivity']
        e_res = cap['metadata']['android.sensor.exposureTime']
        # determine exposure tolerance based on exposure time
        if e_req >= _THRESH_EXP_KNEE:
          thresh_round_down_exp = _THRESH_ROUND_DOWN_EXP
        else:
          thresh_round_down_exp = (
              _THRESH_ROUND_DOWN_EXP +
              (_THRESH_ROUND_DOWN_EXP0 - _THRESH_ROUND_DOWN_EXP) *
              (_THRESH_EXP_KNEE - e_req) / _THRESH_EXP_KNEE)
        if not 0 <= s_req - s_res < s_req * _THRESH_ROUND_DOWN_ISO:
          raise AssertionError(f's_req: {s_req}, s_res: {s_res}, '
                               f'TOL=-{_THRESH_ROUND_DOWN_ISO*100}%')
        if not 0 <= e_req - e_res < e_req * thresh_round_down_exp:
          raise AssertionError(f'e_req: {e_req}ns, e_res: {e_res}ns, '
                               f'TOL=-{thresh_round_down_exp*100}%')
        s_e_product_res = s_res * e_res
        req_res_ratio = s_e_product / s_e_product_res
        logging.debug('Capture result s: %d, e: %dns', s_res, e_res)
        img = image_processing_utils.convert_capture_to_rgb_image(cap)
        image_processing_utils.write_image(
            img, f'{test_name_with_path}_mult={m:.2f}.jpg')
        patch = image_processing_utils.get_image_patch(
            img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
        rgb_means = image_processing_utils.compute_image_means(patch)

        # Adjust for the difference between request and result
        r_means.append(rgb_means[0] * req_res_ratio)
        g_means.append(rgb_means[1] * req_res_ratio)
        b_means.append(rgb_means[2] * req_res_ratio)

        # Do with RAW_STATS space if debug
        if raw_avlb and debug:
          aaw, aah = get_raw_active_array_size(props)
          fmt_raw = {'format': 'rawStats',
                     'gridWidth': aaw//_RAW_STATS_GRID,
                     'gridHeight': aah//_RAW_STATS_GRID}
          raw_cap = its_session_utils.do_capture_with_latency(
              cam, req, sync_latency, fmt_raw)
          r, gr, gb, b = image_processing_utils.convert_capture_to_planes(
              raw_cap, props)
          raw_r_means.append(r[_RAW_STATS_XY, _RAW_STATS_XY] * req_res_ratio)
          raw_gr_means.append(gr[_RAW_STATS_XY, _RAW_STATS_XY] * req_res_ratio)
          raw_gb_means.append(gb[_RAW_STATS_XY, _RAW_STATS_XY] * req_res_ratio)
          raw_b_means.append(b[_RAW_STATS_XY, _RAW_STATS_XY] * req_res_ratio)

          # Test number of points per 2x gain
        m *= pow(2, 1.0/_NUM_PTS_2X_GAIN)

      # Loosen threshold for devices with wider exposure range
      if m >= _WIDE_EXP_RANGE_THRESH:
        thresh_max_level_diff = _THRESH_MAX_LEVEL_DIFF_WIDE_RANGE

    # Draw plots and check data
    if raw_avlb and debug:
      plot_raw_means('RAW data', mults, raw_r_means, raw_gr_means, raw_gb_means,
                     raw_b_means, test_name_with_path)
      for ch, color in enumerate(['R', 'Gr', 'Gb', 'B']):
        values = [raw_r_means, raw_gr_means, raw_gb_means, raw_b_means][ch]
        check_line_fit(color, mults, values, thresh_max_level_diff)

    plot_rgb_means(f'RGB (1x: iso={s}, exp={e})', mults,
                   r_means, g_means, b_means, test_name_with_path)
    for ch, color in enumerate(['R', 'G', 'B']):
      values = [r_means, g_means, b_means][ch]
      check_line_fit(color, mults, values, thresh_max_level_diff)

if __name__ == '__main__':
  test_runner.main()
