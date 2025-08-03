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
"""Verifies the DNG RAW model parameters are correct."""


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


_BAYER_COLORS = ('R', 'GR', 'GB', 'B')
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 4
_PATCH_H = 0.02  # center 2%
_PATCH_W = 0.02
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_VAR_ATOL_THRESH = 0.0012  # absolute variance delta threshold
_VAR_RTOL_THRESH = 0.2  # relative variance delta threshold


class DngNoiseModelTest(its_base_test.ItsBaseTest):
  """Verify that the DNG raw model parameters are correct.

  Pass if the difference between expected and computed variances is small,
  defined as being within an absolute variance delta or relative variance
  delta of the expected variance, whichever is larger. This is to allow the
  test to pass in the presence of some randomness (since this test is
  measuring noise of a small patch) and some imperfect scene conditions
  (since ITS doesn't require a perfectly uniformly lit scene).
  """

  def test_dng_noise_model(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw(props) and
          camera_properties_utils.raw16(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.per_frame_control(props) and
          not camera_properties_utils.mono_camera(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Expose for the scene with min sensitivity
      white_level = float(props['android.sensor.info.whiteLevel'])
      cfa_idxs = image_processing_utils.get_canonical_cfa_order(props)
      sens_min, _ = props['android.sensor.info.sensitivityRange']
      sens_max_ana = props['android.sensor.maxAnalogSensitivity']
      sens_step = (sens_max_ana - sens_min) // _NUM_STEPS
      s_ae, e_ae, _, _, _ = cam.do_3a(get_results=True, do_af=False)
      # Focus at zero to intentionally blur the scene as much as possible.
      f_dist = 0.0
      s_e_prod = s_ae * e_ae
      sensitivities = range(sens_min, sens_max_ana+1, sens_step)

      var_exp = [[], [], [], []]
      var_meas = [[], [], [], []]
      sens_valid = []
      for sens in sensitivities:
        # Capture a raw frame with the desired sensitivity
        exp = int(s_e_prod / float(sens))
        req = capture_request_utils.manual_capture_request(sens, exp, f_dist)
        cap = cam.do_capture(req, cam.CAP_RAW)
        planes = image_processing_utils.convert_capture_to_planes(cap, props)
        s_read = cap['metadata']['android.sensor.sensitivity']
        logging.debug('iso_write: %d, iso_read: %d', sens, s_read)
        if self.debug_mode:
          img = image_processing_utils.convert_capture_to_rgb_image(
              cap, props=props)
          image_processing_utils.write_image(
              img, f'{name_with_log_path}_{sens}.jpg')

        # Test each raw color channel (R, GR, GB, B)
        noise_profile = cap['metadata']['android.sensor.noiseProfile']
        if len(noise_profile) != len(_BAYER_COLORS):
          raise AssertionError(
              f'noise_profile wrong length! {len(noise_profile)}')
        for i, ch in enumerate(_BAYER_COLORS):
          # Get the noise model parameters for this channel of this shot.
          s, o = noise_profile[cfa_idxs[i]]

          # Use a very small patch to ensure gross uniformity (i.e. so
          # non-uniform lighting or vignetting doesn't affect the variance
          # calculation)
          black_level = image_processing_utils.get_black_levels(
              props, cap['metadata'])[i]
          level_range = white_level - black_level
          plane = image_processing_utils.get_image_patch(
              planes[i], _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
          patch_raw = plane * white_level
          patch_norm = ((patch_raw - black_level) / level_range)

          # exit if distribution is clipped at 0, otherwise continue
          mean_img_ch = patch_norm.mean()
          var_model = s * mean_img_ch + o
          # This computation is suspicious because if the data were clipped,
          # the mean and standard deviation could be affected in a way that
          # affects this check. However, empirically, the mean and standard
          # deviation change more slowly than the clipping point itself does,
          # so the check remains correct even after the signal starts to clip.
          mean_minus_3sigma = mean_img_ch - math.sqrt(var_model) * 3
          if mean_minus_3sigma < 0:
            if mean_minus_3sigma >= 0:
              raise AssertionError(
                  'Pixel distribution crosses 0. Likely black level over-clips.'
                  f' Linear model is not valid. mean: {mean_img_ch:.3e},'
                  f' var: {var_model:.3e}, u-3s: {mean_minus_3sigma:.3e}')
          else:
            var = image_processing_utils.compute_image_variances(patch_norm)[0]
            var_meas[i].append(var)
            var_exp[i].append(var_model)
            abs_diff = abs(var - var_model)
            logging.debug('%s mean: %.3f, var: %.3e, var_model: %.3e',
                          ch, mean_img_ch, var, var_model)
            if var_model:
              rel_diff = abs_diff / var_model
            else:
              raise AssertionError(f'{ch} model variance = 0!')
            logging.debug('abs_diff: %.5f, rel_diff: %.3f', abs_diff, rel_diff)
        sens_valid.append(sens)

    # plot data and models
    pylab.figure(_NAME)
    for i, ch in enumerate(_BAYER_COLORS):
      pylab.plot(sens_valid, var_exp[i], 'rgkb'[i], label=ch+' expected')
      pylab.plot(sens_valid, var_meas[i], 'rgkb'[i]+'.--', label=ch+' measured')
    pylab.title(_NAME)
    pylab.xlabel('Sensitivity')
    pylab.ylabel('Center patch variance')
    pylab.ticklabel_format(axis='y', style='sci', scilimits=(-6, -6))
    pylab.legend(loc=2)
    matplotlib.pyplot.savefig(f'{name_with_log_path}_plot.png')

    # PASS/FAIL check
    for i, ch in enumerate(_BAYER_COLORS):
      var_diffs = [abs(var_meas[i][j] - var_exp[i][j])
                   for j in range(len(sens_valid))]
      logging.debug('%s variance diffs: %s', ch, str(var_diffs))
      for j, diff in enumerate(var_diffs):
        thresh = max(_VAR_ATOL_THRESH, _VAR_RTOL_THRESH*var_exp[i][j])
        if diff > thresh:
          raise AssertionError(f'var diff: {diff:.5f}, thresh: {thresh:.4f}')

if __name__ == '__main__':
  test_runner.main()
