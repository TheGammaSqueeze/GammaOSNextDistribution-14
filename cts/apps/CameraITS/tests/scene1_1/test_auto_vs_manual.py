# Copyright 2014 The Android Open Source Project
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
"""Verifies auto and manual captures are similar with same scene."""


import logging
import math
import os.path
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_AWB_AUTO_ATOL = 0.10
_AWB_AUTO_RTOL = 0.25
_AWB_MANUAL_ATOL = 0.05
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_TONEMAP_GAMMA = sum([[t/63.0, math.pow(t/63.0, 1/2.2)] for t in range(64)], [])


def extract_awb_gains_and_xform(cap, cap_name, log_path):
  """Extract the AWB transform and gains, save image, and log info.

  Args:
    cap: camera capture
    cap_name: text string to identify cap type
    log_path: location to save images

  Returns:
    awb_gains, awb_xform
  """
  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  name_with_log_path = os.path.join(log_path, _NAME)
  image_processing_utils.write_image(
      img, f'{name_with_log_path}_{cap_name}.jpg')
  awb_gains = cap['metadata']['android.colorCorrection.gains']
  awb_xform = capture_request_utils.rational_to_float(
      cap['metadata']['android.colorCorrection.transform'])
  logging.debug('%s gains: %s', cap_name, str(awb_gains))
  logging.debug('%s transform: %s', cap_name, str(awb_xform))
  return awb_gains, awb_xform


class AutoVsManualTest(its_base_test.ItsBaseTest):
  """Capture auto and manual shots that should look the same.

  Manual shots taken with just manual WB, and also with manual WB+tonemap.

  In all cases, the general color/look of the shots should be the same,
  however there can be variations in brightness/contrast due to different
  'auto' ISP blocks that may be disabled in the manual flows.
  """

  def test_auto_vs_manual(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      mono_camera = camera_properties_utils.mono_camera(props)
      log_path = self.log_path

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.read_3a(props) and
          camera_properties_utils.per_frame_control(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Converge 3A and get the estimates
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      s, e, awb_gains, awb_xform, fd = cam.do_3a(get_results=True,
                                                 mono_camera=mono_camera)
      awb_xform_rat = capture_request_utils.float_to_rational(awb_xform)
      logging.debug('AE sensitivity: %d, exposure: %dms', s, e/1000000.0)
      logging.debug('AWB gains: %s', str(awb_gains))
      logging.debug('AWB transform: %s', str(awb_xform))
      logging.debug('AF distance: %.3f', fd)

      # Auto capture
      req = capture_request_utils.auto_capture_request()
      cap_auto = cam.do_capture(req, fmt)
      awb_gains_a, awb_xform_a = extract_awb_gains_and_xform(
          cap_auto, 'auto', log_path)

      # Manual capture 1: WB
      req = capture_request_utils.manual_capture_request(s, e, fd)
      req['android.colorCorrection.transform'] = awb_xform_rat
      req['android.colorCorrection.gains'] = awb_gains
      cap_man1 = cam.do_capture(req, fmt)
      awb_gains_m1, awb_xform_m1 = extract_awb_gains_and_xform(
          cap_man1, 'manual_wb', log_path)

      # Manual capture 2: WB + tonemap
      req['android.tonemap.mode'] = 0
      req['android.tonemap.curve'] = {'red': _TONEMAP_GAMMA,
                                      'green': _TONEMAP_GAMMA,
                                      'blue': _TONEMAP_GAMMA}
      cap_man2 = cam.do_capture(req, fmt)
      awb_gains_m2, awb_xform_m2 = extract_awb_gains_and_xform(
          cap_man2, 'manual_wb_tm', log_path)

      # Check AWB gains & transform in manual results match values from do_3a
      for g, x in [(awb_gains_m1, awb_xform_m1), (awb_gains_m2, awb_xform_m2)]:
        if not np.allclose(awb_xform, x, atol=_AWB_MANUAL_ATOL, rtol=0):
          raise AssertionError(
              f'awb_xform 3A: {awb_xform}, '
              f'manual: {x}, ATOL={_AWB_MANUAL_ATOL}')
        if not np.allclose(awb_gains, g, atol=_AWB_MANUAL_ATOL, rtol=0):
          raise AssertionError(
              f'awb_gains 3A: {awb_gains}, '
              f'manual: {g}, ATOL={_AWB_MANUAL_ATOL}')

      # Check AWB gains & transform in auto results match values from do_3a
      if not np.allclose(awb_xform_a, awb_xform, atol=_AWB_AUTO_ATOL,
                         rtol=_AWB_AUTO_RTOL):
        raise AssertionError(f'awb_xform 3A: {awb_xform}, auto: {awb_xform_a},'
                             f'RTOL={_AWB_AUTO_RTOL}, ATOL={_AWB_AUTO_ATOL}')
      if not np.allclose(awb_gains_a, awb_gains, atol=_AWB_AUTO_ATOL,
                         rtol=_AWB_AUTO_RTOL):
        raise AssertionError(f'awb_gains 3A: {awb_gains}, auto: {awb_gains_a},'
                             f'RTOL={_AWB_AUTO_RTOL}, ATOL={_AWB_AUTO_ATOL}')

if __name__ == '__main__':
  test_runner.main()

