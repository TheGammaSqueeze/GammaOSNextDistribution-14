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

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_LINEAR_TONEMAP_CURVE = [0.0, 0.0, 1.0, 1.0]
_LOCKED = 3
_LUMA_DELTA_ATOL = 0.05
_LUMA_DELTA_ATOL_SAT = 0.10
_LUMA_SAT_THRESH = 0.75  # luma value at which ATOL changes from MID to SAT
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESH_CONVERGE_FOR_EV = 8  # AE must converge within this num auto reqs for EV


def create_request_with_ev(ev):
  """Create request with the ev compensation step."""
  req = capture_request_utils.auto_capture_request()
  req['android.control.aeExposureCompensation'] = ev
  req['android.control.aeLock'] = True
  req['android.control.awbLock'] = True
  # Use linear tonemap to avoid brightness being impacted by tone curves.
  req['android.tonemap.mode'] = 0
  req['android.tonemap.curve'] = {'red': _LINEAR_TONEMAP_CURVE,
                                  'green': _LINEAR_TONEMAP_CURVE,
                                  'blue': _LINEAR_TONEMAP_CURVE}
  return req


def create_ev_comp_changes(props):
  """Create the ev compensation steps and shifts from control params."""
  ev_compensation_range = props['android.control.aeCompensationRange']
  range_min = ev_compensation_range[0]
  range_max = ev_compensation_range[1]
  ev_per_step = capture_request_utils.rational_to_float(
      props['android.control.aeCompensationStep'])
  logging.debug('ev_step_size_in_stops: %.3f', ev_per_step)
  steps_per_ev = int(round(1.0 / ev_per_step))
  ev_steps = range(range_min, range_max + 1, steps_per_ev)
  ev_shifts = [pow(2, step * ev_per_step) for step in ev_steps]
  return ev_steps, ev_shifts


class EvCompensationAdvancedTest(its_base_test.ItsBaseTest):
  """Tests that EV compensation is applied."""

  def test_ev_compensation_advanced(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.ev_compensation(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.manual_post_proc(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.ae_lock(props) and
          camera_properties_utils.awb_lock(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Create ev compensation changes
      ev_steps, ev_shifts = create_ev_comp_changes(props)

      # Converge 3A, and lock AE once converged. skip AF trigger as
      # dark/bright scene could make AF convergence fail and this test
      # doesn't care the image sharpness.
      mono_camera = camera_properties_utils.mono_camera(props)
      cam.do_3a(ev_comp=0, lock_ae=True, lock_awb=True, do_af=False,
                mono_camera=mono_camera)

      # Create requests and capture
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      lumas = []
      for ev in ev_steps:
        # Capture a single shot with the same EV comp and locked AE.
        req = create_request_with_ev(ev)
        caps = cam.do_capture([req]*_THRESH_CONVERGE_FOR_EV, fmt)
        for cap in caps:
          if cap['metadata']['android.control.aeState'] == _LOCKED:
            ev_meta = cap['metadata']['android.control.aeExposureCompensation']
            if ev_meta != ev:
              raise AssertionError(
                  f'EV comp capture != request! cap: {ev_meta}, req: {ev}')
            lumas.append(image_processing_utils.extract_luma_from_patch(
                cap, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H))
            break
        if caps[_THRESH_CONVERGE_FOR_EV-1]['metadata'][
            'android.control.aeState'] != _LOCKED:
          raise AssertionError('AE does not reach locked state in '
                               f'{_THRESH_CONVERGE_FOR_EV} frames.')
        logging.debug('lumas in AE locked captures: %s', str(lumas))

      i_mid = len(ev_steps) // 2
      luma_normal = lumas[i_mid] / ev_shifts[i_mid]
      expected_lumas = [min(1.0, luma_normal*shift) for shift in ev_shifts]
      luma_delta_atols = [_LUMA_DELTA_ATOL if l < _LUMA_SAT_THRESH
                          else _LUMA_DELTA_ATOL_SAT for l in expected_lumas]

      # Create plot
      pylab.figure(_NAME)
      pylab.plot(ev_steps, lumas, '-ro', label='measured', alpha=0.7)
      pylab.plot(ev_steps, expected_lumas, '-bo', label='expected', alpha=0.7)
      pylab.title(_NAME)
      pylab.xlabel('EV Compensation')
      pylab.ylabel('Mean Luma (Normalized)')
      pylab.legend(loc='lower right', numpoints=1, fancybox=True)
      name_with_log_path = os.path.join(log_path, _NAME)
      matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_means.png')

      for i, luma in enumerate(lumas):
        luma_delta_atol = luma_delta_atols[i]
        logging.debug('EV step: %3d, luma: %.3f, model: %.3f, ATOL: %.2f',
                      ev_steps[i], luma, expected_lumas[i], luma_delta_atol)
        if not math.isclose(luma, expected_lumas[i],
                            abs_tol=luma_delta_atol):
          raise AssertionError('Modeled/measured luma deltas too large! '
                               f'meas: {lumas[i]}, model: {expected_lumas[i]}, '
                               f'ATOL: {luma_delta_atol}.')


if __name__ == '__main__':
  test_runner.main()
