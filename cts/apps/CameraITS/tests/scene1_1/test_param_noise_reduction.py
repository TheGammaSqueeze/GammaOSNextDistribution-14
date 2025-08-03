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
"""Verifies android.noiseReduction.mode parameters is applied when set."""


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
import target_exposure_utils

_COLORS = ('R', 'G', 'B')
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NR_MODES = {'OFF': 0, 'FAST': 1, 'HQ': 2, 'MIN': 3, 'ZSL': 4}
_NR_MODES_LIST = list(_NR_MODES.values())
_NUM_COLORS = len(_COLORS)
_NUM_FRAMES_PER_MODE = 4
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_SNR_TOLERANCE = 3  # unit in dB


class ParamNoiseReductionTest(its_base_test.ItsBaseTest):
  """Test that the android.noiseReduction.mode param is applied when set.

  Capture images with the camera dimly lit.

  Capture images with low gain and noise reduction off, and use the
  variance of these captures as the baseline.

  Use high analog gain on remaining tests to ensure captured images are noisy.
  """

  def test_param_noise_reduction(self):
    logging.debug('Starting %s', _NAME)
    logging.debug('NR_MODES: %s', str(_NR_MODES))
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.noise_reduction_mode(props, 0))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      snrs = [[], [], []]  # List of SNRs for R,G,B
      ref_snr = []  # Reference (baseline) SNR for each of R,G,B
      nr_modes_reported = []

      # NR mode 0 with low gain
      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['minSensitivity']
      req = capture_request_utils.manual_capture_request(s, e)
      req['android.noiseReduction.mode'] = 0
      cap = cam.do_capture(req)
      rgb_image = image_processing_utils.convert_capture_to_rgb_image(cap)
      image_processing_utils.write_image(
          rgb_image, f'{name_with_log_path}_low_gain.jpg')
      rgb_patch = image_processing_utils.get_image_patch(
          rgb_image, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
      ref_snr = image_processing_utils.compute_image_snrs(rgb_patch)
      logging.debug('Ref SNRs: %s', str(ref_snr))

      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['maxSensitivity']
      for mode in _NR_MODES_LIST:
        # Skip unavailable modes
        if not camera_properties_utils.noise_reduction_mode(props, mode):
          nr_modes_reported.append(mode)
          for channel in range(_NUM_COLORS):
            snrs[channel].append(0)
          continue

        rgb_snr_list = []
        # Capture several images to account for per frame noise variations
        for n in range(_NUM_FRAMES_PER_MODE):
          req = capture_request_utils.manual_capture_request(s, e)
          req['android.noiseReduction.mode'] = mode
          cap = cam.do_capture(req)
          rgb_image = image_processing_utils.convert_capture_to_rgb_image(cap)
          if n == 0:
            nr_modes_reported.append(
                cap['metadata']['android.noiseReduction.mode'])
            image_processing_utils.write_image(
                rgb_image, f'{name_with_log_path}_high_gain_nr={mode}.jpg')
          rgb_patch = image_processing_utils.get_image_patch(
              rgb_image, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
          rgb_snrs = image_processing_utils.compute_image_snrs(rgb_patch)
          rgb_snr_list.append(rgb_snrs)

        r_snrs = [rgb[0] for rgb in rgb_snr_list]
        g_snrs = [rgb[1] for rgb in rgb_snr_list]
        b_snrs = [rgb[2] for rgb in rgb_snr_list]
        rgb_snrs = [np.mean(r_snrs), np.mean(g_snrs), np.mean(b_snrs)]
        logging.debug('NR mode %s SNRs', mode)
        logging.debug('R SNR: %.2f, Min: %.2f, Max: %.2f',
                      rgb_snrs[0], min(r_snrs), max(r_snrs))
        logging.debug('G SNR: %.2f, Min: %.2f, Max: %.2f',
                      rgb_snrs[1], min(g_snrs), max(g_snrs))
        logging.debug('B SNR: %.2f, Min: %.2f, Max: %.2f',
                      rgb_snrs[2], min(b_snrs), max(b_snrs))

        for chan in range(_NUM_COLORS):
          snrs[chan].append(rgb_snrs[chan])

    # Draw plot
    pylab.figure(_NAME)
    pylab.title(f'{_NAME}')
    for j in range(_NUM_COLORS):
      pylab.plot(_NR_MODES_LIST, snrs[j], '-'+'rgb'[j]+'o')
    pylab.xlabel(f'{str(_NR_MODES)[1:-1]}')  # strip '{' '}' off string
    pylab.ylabel('SNR (dB)')
    pylab.xticks(_NR_MODES_LIST)
    matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_SNRs.png')

    if nr_modes_reported != _NR_MODES_LIST:
      raise AssertionError(f'{nr_modes_reported} != {_NR_MODES_LIST}')

    for j in range(_NUM_COLORS):
      # Higher SNR is better
      # Verify OFF is not better than FAST
      if (snrs[j][_NR_MODES['OFF']] >= snrs[j][_NR_MODES['FAST']] +
          _SNR_TOLERANCE):
        raise AssertionError(
            f"{_COLORS[j]} OFF: {snrs[j][_NR_MODES['OFF']]:.3f}, "
            f"FAST: {snrs[j][_NR_MODES['FAST']]:.3f}, TOL: {_SNR_TOLERANCE}")

      # Verify FAST is not better than HQ
      if (snrs[j][_NR_MODES['FAST']] >= snrs[j][_NR_MODES['HQ']] +
          _SNR_TOLERANCE):
        raise AssertionError(
            f"{_COLORS[j]} FAST: {snrs[j][_NR_MODES['FAST']]:.3f}, "
            f"HQ: {snrs[j][_NR_MODES['HQ']]:.3f}, TOL: {_SNR_TOLERANCE}")

      # Verify HQ is better than OFF
      if snrs[j][_NR_MODES['HQ']] <= snrs[j][_NR_MODES['OFF']]:
        raise AssertionError(
            f"{_COLORS[j]} OFF: {snrs[j][_NR_MODES['OFF']]:.3f}, "
            f"HQ: {snrs[j][_NR_MODES['HQ']]:.3f}")

      if camera_properties_utils.noise_reduction_mode(props, _NR_MODES['MIN']):
        # Verify OFF is not better than MINIMAL
        if not(snrs[j][_NR_MODES['OFF']] < snrs[j][_NR_MODES['MIN']] +
               _SNR_TOLERANCE):
          raise AssertionError(
              f"{_COLORS[j]} OFF: {snrs[j][_NR_MODES['OFF']]:.3f}, "
              f"MIN: {snrs[j][_NR_MODES['MIN']]:.3f}, TOL: {_SNR_TOLERANCE}")

        # Verify MINIMAL is not better than HQ
        if not (snrs[j][_NR_MODES['MIN']] < snrs[j][_NR_MODES['HQ']] +
                _SNR_TOLERANCE):
          raise AssertionError(
              f"{_COLORS[j]} MIN: {snrs[j][_NR_MODES['MIN']]:.3f}, "
              f"HQ: {snrs[j][_NR_MODES['HQ']]:.3f}, TOL: {_SNR_TOLERANCE}")

        # Verify ZSL is close to MINIMAL
        if camera_properties_utils.noise_reduction_mode(
            props, _NR_MODES['ZSL']):
          if not math.isclose(snrs[j][_NR_MODES['ZSL']],
                              snrs[j][_NR_MODES['MIN']],
                              abs_tol=_SNR_TOLERANCE):
            raise AssertionError(
                f"{_COLORS[j]} ZSL: {snrs[j][_NR_MODES['ZSL']]:.3f}, "
                f"MIN: {snrs[j][_NR_MODES['MIN']]:.3f}, TOL: {_SNR_TOLERANCE}")

      elif camera_properties_utils.noise_reduction_mode(
          props, _NR_MODES['ZSL']):
        # Verify ZSL is close to OFF
        if not math.isclose(
            snrs[j][_NR_MODES['ZSL']], snrs[j][_NR_MODES['OFF']],
            abs_tol=_SNR_TOLERANCE):
          raise AssertionError(
              f"{_COLORS[j]} OFF: {snrs[j][_NR_MODES['OFF']]:.3f}, "
              f"ZSL: {snrs[j][_NR_MODES['ZSL']]:.3f}, TOL: {_SNR_TOLERANCE}")


if __name__ == '__main__':
  test_runner.main()

