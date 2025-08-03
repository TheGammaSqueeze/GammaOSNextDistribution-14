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
"""Verifies EV compensation is applied."""


import logging
import math
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

_LOCKED = 3
_LUMA_LOCKED_RTOL_EV_SM = 0.05
_LUMA_LOCKED_RTOL_EV_LG = 0.10
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_UNSATURATED_EVS = 3
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESH_CONVERGE_FOR_EV = 8  # AE must converge within this num
_VGA_W, _VGA_H = 640, 480
_YUV_FULL_SCALE = 255.0
_YUV_SAT_MIN = 250.0
_YUV_SAT_TOL = 3.0


def create_request_with_ev(ev):
  """Create request with EV value."""
  req = capture_request_utils.auto_capture_request()
  req['android.control.aeExposureCompensation'] = ev
  req['android.control.aeLock'] = True
  req['android.control.awbLock'] = True
  return req


class EvCompensationBasicTest(its_base_test.ItsBaseTest):
  """Tests that EV compensation is applied."""

  def test_ev_compensation_basic(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      test_name_w_path = os.path.join(log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.ev_compensation(props) and
          camera_properties_utils.ae_lock(props) and
          camera_properties_utils.awb_lock(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Create ev compensation changes
      ev_per_step = capture_request_utils.rational_to_float(
          props['android.control.aeCompensationStep'])
      steps_per_ev = int(1.0 / ev_per_step)
      evs = range(-2 * steps_per_ev, 2 * steps_per_ev + 1, steps_per_ev)
      luma_locked_rtols = [_LUMA_LOCKED_RTOL_EV_LG,
                           _LUMA_LOCKED_RTOL_EV_SM,
                           _LUMA_LOCKED_RTOL_EV_SM,
                           _LUMA_LOCKED_RTOL_EV_SM,
                           _LUMA_LOCKED_RTOL_EV_LG]

      # Converge 3A, and lock AE once converged. skip AF trigger as
      # dark/bright scene could make AF convergence fail and this test
      # doesn't care the image sharpness.
      mono_camera = camera_properties_utils.mono_camera(props)
      cam.do_3a(ev_comp=0, lock_ae=True, lock_awb=True, do_af=False,
                mono_camera=mono_camera)

      # Do captures and extract information
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      if fmt['width'] * fmt['height'] > _VGA_W * _VGA_H:
        fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
      logging.debug('YUV size: %d x %d', fmt['width'], fmt['height'])
      lumas = []
      for j, ev in enumerate(evs):
        luma_locked_rtol = luma_locked_rtols[j]
        # Capture a single shot with the same EV comp and locked AE.
        req = create_request_with_ev(ev)
        caps = cam.do_capture([req]*_THRESH_CONVERGE_FOR_EV, fmt)
        luma_locked = []
        for i, cap in enumerate(caps):
          if cap['metadata']['android.control.aeState'] == _LOCKED:
            ev_meta = cap['metadata']['android.control.aeExposureCompensation']
            exp = cap['metadata']['android.sensor.exposureTime']
            iso = cap['metadata']['android.sensor.sensitivity']
            logging.debug('cap EV: %d, exp: %dns, ISO: %d', ev_meta, exp, iso)
            if ev != ev_meta:
              raise AssertionError(
                  f'EV compensation cap != req! cap: {ev_meta}, req: {ev}')
            luma = image_processing_utils.extract_luma_from_patch(
                cap, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
            luma_locked.append(luma)
            if i == _THRESH_CONVERGE_FOR_EV-1:
              lumas.append(luma)
              if not math.isclose(min(luma_locked), max(luma_locked),
                                  rel_tol=luma_locked_rtol):
                raise AssertionError(f'EV {ev} burst lumas: {luma_locked}, '
                                     f'RTOL: {luma_locked_rtol}')
        logging.debug('lumas per frame ev %d: %s', ev, str(luma_locked))
      logging.debug('mean lumas in AE locked captures: %s', str(lumas))
      if caps[_THRESH_CONVERGE_FOR_EV-1]['metadata'][
          'android.control.aeState'] != _LOCKED:
        raise AssertionError(f'No AE lock by {_THRESH_CONVERGE_FOR_EV} frame.')

    # Create plot
    pylab.figure(_NAME)
    pylab.plot(evs, lumas, '-ro')
    pylab.title(_NAME)
    pylab.xlabel('EV Compensation')
    pylab.ylabel('Mean Luma (Normalized)')
    matplotlib.pyplot.savefig(f'{test_name_w_path}_plot_means.png')

    # Trim extra saturated images
    while (lumas[-2] >= _YUV_SAT_MIN/_YUV_FULL_SCALE and
           lumas[-1] >= _YUV_SAT_MIN/_YUV_FULL_SCALE and
           len(lumas) > 2):
      lumas.pop(-1)
      logging.debug('Removed saturated image.')

    # Only allow positive EVs to give saturated image
    if len(lumas) < _NUM_UNSATURATED_EVS:
      raise AssertionError(
          f'>{_NUM_UNSATURATED_EVS-1} unsaturated images needed.')
    min_luma_diffs = min(np.diff(lumas))
    logging.debug('Min of luma value difference between adjacent ev comp: %.3f',
                  min_luma_diffs)

    # Assert unsaturated lumas increasing with increasing ev comp.
    if min_luma_diffs <= 0:
      raise AssertionError('Lumas not increasing with ev comp! '
                           f'EVs: {list(evs)}, lumas: {lumas}')


if __name__ == '__main__':
  test_runner.main()
