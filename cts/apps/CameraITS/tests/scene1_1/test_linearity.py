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
"""Verifies linear behavior in exposure/gain space."""


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

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 6
_PATCH_H = 0.1  # center 10% patch params
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RESIDUAL_THRESH = 0.0003  # sample error of ~2/255 in np.arange(0, 0.5, 0.1)
_VGA_W, _VGA_H = 640, 480

# HAL3.2 spec requires curves up to 64 control points in length be supported
_L = 63
_GAMMA_LUT = np.array(
    sum([[i/_L, math.pow(i/_L, 1/2.2)] for i in range(_L+1)], []))
_INV_GAMMA_LUT = np.array(
    sum([[i/_L, math.pow(i/_L, 2.2)] for i in range(_L+1)], []))


class LinearityTest(its_base_test.ItsBaseTest):
  """Test that device processing can be inverted to linear pixels.

  Captures a sequence of shots with the device pointed at a uniform
  target. Attempts to invert all the ISP processing to get back to
  linear R,G,B pixel data.
  """

  def test_linearity(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props))
      sync_latency = camera_properties_utils.sync_latency(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Determine sensitivities to test over
      e_mid, s_mid = target_exposure_utils.get_target_exposure_combos(
          self.log_path, cam)['midSensitivity']
      sens_range = props['android.sensor.info.sensitivityRange']
      sensitivities = [s_mid*x/_NUM_STEPS for x in range(1, _NUM_STEPS)]
      sensitivities = [s for s in sensitivities
                       if s > sens_range[0] and s < sens_range[1]]

      # Initialize capture request
      req = capture_request_utils.manual_capture_request(0, e_mid)
      req['android.blackLevel.lock'] = True
      req['android.tonemap.mode'] = 0
      req['android.tonemap.curve'] = {'red': _GAMMA_LUT.tolist(),
                                      'green': _GAMMA_LUT.tolist(),
                                      'blue': _GAMMA_LUT.tolist()}
      # Do captures and calculate center patch RGB means
      r_means = []
      g_means = []
      b_means = []
      fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
      for sens in sensitivities:
        req['android.sensor.sensitivity'] = sens
        cap = its_session_utils.do_capture_with_latency(
            cam, req, sync_latency, fmt)
        img = image_processing_utils.convert_capture_to_rgb_image(cap)
        image_processing_utils.write_image(
            img, f'{name_with_log_path}_sens={int(sens):04d}.jpg')
        img = image_processing_utils.apply_lut_to_image(
            img, _INV_GAMMA_LUT[1::2] * _L)
        patch = image_processing_utils.get_image_patch(
            img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
        rgb_means = image_processing_utils.compute_image_means(patch)
        r_means.append(rgb_means[0])
        g_means.append(rgb_means[1])
        b_means.append(rgb_means[2])

      # Plot means
      pylab.figure(_NAME)
      pylab.plot(sensitivities, r_means, '-ro')
      pylab.plot(sensitivities, g_means, '-go')
      pylab.plot(sensitivities, b_means, '-bo')
      pylab.title(_NAME)
      pylab.xlim([sens_range[0], sens_range[1]/2])
      pylab.ylim([0, 1])
      pylab.xlabel('sensitivity(ISO)')
      pylab.ylabel('RGB avg [0, 1]')
      matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_means.png')
      channel_color = ''
      # Assert plot curves are linear w/ + slope by examining polyfit residual
      for means in [r_means, g_means, b_means]:
        if means == r_means:
          channel_color = 'Red'
        elif means == g_means:
          channel_color = 'Green'
        else:
          channel_color = 'Blue'
        line, residuals, _, _, _ = np.polyfit(
            range(len(sensitivities)), means, 1, full=True)
        logging.debug('Line: m=%f, b=%f, resid=%f',
                      line[0], line[1], residuals[0])
        if residuals[0] > _RESIDUAL_THRESH:
          raise AssertionError(
              f'residual: {residuals[0]:.5f}, THRESH: {_RESIDUAL_THRESH},'
              f' color: {channel_color}')
        if line[0] <= 0:
          raise AssertionError(f'slope {line[0]:.6f} <=  0!')

if __name__ == '__main__':
  test_runner.main()
