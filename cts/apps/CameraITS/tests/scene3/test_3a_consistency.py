# Copyright 2017 The Android Open Source Project
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
"""Verifies 3A settles consistently 3x."""


import logging
import math
import os.path

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import error_util
import image_processing_utils
import its_session_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]

_AWB_GREEN_CH = 2
_GGAIN_TOL = 0.1
_FD_TOL = 0.1
_ISO_EXP_ISP_TOL = 0.2   # TOL used w/o postRawCapabilityBoost not available.
_ISO_EXP_TOL = 0.16  # TOL used w/ postRawCapabilityBoost available

_NUM_TEST_ITERATIONS = 3


def _write_imgs(imgs, filename):
  for i, img in enumerate(imgs):
    image_processing_utils.write_image(img, f'{filename}_{i}.png')


class ConsistencyTest(its_base_test.ItsBaseTest):
  """Basic test for 3A consistency.

  To PASS, 3A must converge for exp, gain, awb, fd within defined TOLs.
  TOLs are based on camera capabilities. If postRawSensitivityBoost can be
  fixed TOL is tighter. The TOL values in the CONSTANTS area are described in
  b/144452069.

  Note ISO and sensitivity are interchangeable for Android cameras.
  """

  def test_3a_consistency(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = f'{os.path.join(self.log_path, _NAME)}'

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Check skip conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.read_3a(props))
      mono_camera = camera_properties_utils.mono_camera(props)

      # Set postRawSensitivityBoost to minimum if available
      req = capture_request_utils.auto_capture_request()
      if camera_properties_utils.post_raw_sensitivity_boost(props):
        min_iso_boost, _ = props['android.control.postRawSensitivityBoostRange']
        req['android.control.postRawSensitivityBoost'] = min_iso_boost
        iso_exp_tol = _ISO_EXP_TOL
        logging.debug('Setting post RAW sensitivity boost to minimum')
      else:
        iso_exp_tol = _ISO_EXP_ISP_TOL

      iso_exps = []
      g_gains = []
      fds = []
      imgs = []
      cam.do_3a()  # do 3A 1x up front to 'prime the pump'
      # Do 3A _NUM_TEST_ITERATIONS times and save data
      for _ in range(_NUM_TEST_ITERATIONS):
        try:
          iso, exposure, awb_gains, awb_transform, focus_distance = cam.do_3a(
              get_results=True, mono_camera=mono_camera)
          logging.debug('req iso: %d, exp: %d, iso*exp: %d',
                        iso, exposure, exposure * iso)
          logging.debug('req awb_gains: %s, awb_transform: %s',
                        awb_gains, awb_transform)
          logging.debug('req fd: %s', focus_distance)
          req = capture_request_utils.manual_capture_request(
              iso, exposure, focus_distance)
          cap = cam.do_capture(req, cam.CAP_YUV)
          imgs.append(image_processing_utils.convert_capture_to_rgb_image(cap))

          # Extract and save metadata
          iso_result = cap['metadata']['android.sensor.sensitivity']
          exposure_result = cap['metadata']['android.sensor.exposureTime']
          awb_gains_result = cap['metadata']['android.colorCorrection.gains']
          awb_transform_result = capture_request_utils.rational_to_float(
              cap['metadata']['android.colorCorrection.transform'])
          focus_distance_result = cap['metadata']['android.lens.focusDistance']
          logging.debug(
              'res iso: %d, exposure: %d, iso*exp: %d',
              iso_result, exposure_result, exposure_result*iso_result)
          logging.debug('res awb_gains: %s, awb_transform: %s',
                        awb_gains_result, awb_transform_result)
          logging.debug('res fd: %s', focus_distance_result)
          iso_exps.append(exposure_result*iso_result)
          g_gains.append(awb_gains_result[_AWB_GREEN_CH])
          fds.append(focus_distance_result)
        except error_util.CameraItsError:
          logging.debug('FAIL')

      # Check for correct behavior
      if len(iso_exps) != _NUM_TEST_ITERATIONS:
        _write_imgs(imgs, f'{name_with_log_path}_num')
        raise AssertionError(f'number of captures: {len(iso_exps)}, '
                             f'NUM_TEST_ITERATIONS: {_NUM_TEST_ITERATIONS}.')
      iso_exp_min = np.amin(iso_exps)
      iso_exp_max = np.amax(iso_exps)
      if not math.isclose(iso_exp_max, iso_exp_min, rel_tol=iso_exp_tol):
        _write_imgs(imgs, f'{name_with_log_path}_iso_exp')
        raise AssertionError(f'ISO*exp min: {iso_exp_min}, max: {iso_exp_max}, '
                             f'RTOL:{iso_exp_tol}')
      g_gain_min = np.amin(g_gains)
      g_gain_max = np.amax(g_gains)
      if not math.isclose(g_gain_max, g_gain_min, rel_tol=_GGAIN_TOL):
        _write_imgs(imgs, f'{name_with_log_path}_g_gain')
        raise AssertionError(f'G gain min: {g_gain_min}, max: {g_gain_min}, '
                             f'RTOL: {_GGAIN_TOL}')
      fd_min = np.amin(fds)
      fd_max = np.amax(fds)
      if not math.isclose(fd_max, fd_min, rel_tol=_FD_TOL):
        _write_imgs(imgs, f'{name_with_log_path}_fd')
        raise AssertionError(f'FD min: {fd_min}, max: {fd_max} RTOL: {_FD_TOL}')
      for g in awb_gains:
        if np.isnan(g):
          raise AssertionError('AWB gain entry is not a number.')
      for x in awb_transform:
        if np.isnan(x):
          raise AssertionError('AWB transform entry is not a number.')

if __name__ == '__main__':
  test_runner.main()
