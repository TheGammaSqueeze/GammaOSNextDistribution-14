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
"""Verifies color correction parameter settings."""


import logging
import os.path
import matplotlib
from matplotlib import pylab
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_CC_XFORM_BOOST_B = [1, 0, 0,
                     0, 1, 0,
                     0, 0, 2]  # blue channel 2x
_CC_XFORM_UNITY = [1, 0, 0,
                   0, 1, 0,
                   0, 0, 1]  # all channels equal
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RAW_GAIN_BOOST_R = [2, 1, 1, 1]  # red channel 2x
_RAW_GAIN_UNITY = [1, 1, 1, 1]  # all channels equal
_RGB_DIFF_THRESH = 0.1  # threshold for differences in asserts
_RGB_RANGE_THRESH = 0.2  # 0.2 < mean < 0.8 to avoid dark or saturated imgs


class ParamColorCorrectionTest(its_base_test.ItsBaseTest):
  """Test that the android.colorCorrection.* params are applied when set.

  Takes shots with different transform and gains values, and tests that
  they look correspondingly different. The transform and gains are chosen
  to make the output go redder or bluer.

  Uses a linear tonemap.
  """

  def test_param_color_correction(self):
    logging.debug('Starting %s', _NAME)
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
          not camera_properties_utils.mono_camera(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Define format
      sync_latency = camera_properties_utils.sync_latency(props)
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)

      # Define baseline request
      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['midSensitivity']
      req = capture_request_utils.manual_capture_request(s, e, 0.0, True, props)
      req['android.colorCorrection.mode'] = 0

      # Define transforms
      transforms = [capture_request_utils.int_to_rational(_CC_XFORM_UNITY),
                    capture_request_utils.int_to_rational(_CC_XFORM_UNITY),
                    capture_request_utils.int_to_rational(_CC_XFORM_BOOST_B)]

      # Define RAW gains:
      gains = [_RAW_GAIN_UNITY,
               _RAW_GAIN_BOOST_R,
               _RAW_GAIN_UNITY]

      # Capture requests:
      # 1. With unit gains, and identity transform.
      # 2. With a higher red gain, and identity transform.
      # 3. With unit gains, and a transform that boosts blue.
      r_means = []
      g_means = []
      b_means = []
      capture_idxs = range(len(transforms))
      for i in capture_idxs:
        req['android.colorCorrection.transform'] = transforms[i]
        req['android.colorCorrection.gains'] = gains[i]
        cap = its_session_utils.do_capture_with_latency(
            cam, req, sync_latency, fmt)
        img = image_processing_utils.convert_capture_to_rgb_image(cap)
        image_processing_utils.write_image(
            img, f'{name_with_log_path}_req={i}.jpg')
        patch = image_processing_utils.get_image_patch(
            img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
        rgb_means = image_processing_utils.compute_image_means(patch)
        r_means.append(rgb_means[0])
        g_means.append(rgb_means[1])
        b_means.append(rgb_means[2])
        ratios = [rgb_means[0] / rgb_means[1], rgb_means[2] / rgb_means[1]]
        logging.debug('Means: %s,  Ratios: %s', str(rgb_means), str(ratios))

      # Draw a plot
      pylab.figure(_NAME)
      for ch, means in enumerate([r_means, g_means, b_means]):
        pylab.plot(capture_idxs, means, '-'+'rgb'[ch]+'o')
      pylab.xticks(capture_idxs)
      pylab.ylim([0, 1])
      pylab.title(_NAME)
      pylab.xlabel('Cap Index [Unity, R boost, B boost]')
      pylab.ylabel('RGB patch means')
      matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_means.png')
      # Ensure that image is not clamped to white/black.
      if not all(_RGB_RANGE_THRESH < g_means[i] < 1.0-_RGB_RANGE_THRESH
                 for i in capture_idxs):
        raise AssertionError('Image too dark/bright! Check setup.')

      # Expect G0 == G1 == G2, R0 == 0.5*R1 == R2, B0 == B1 == 0.5*B2
      # assert planes in caps expected to be equal
      if abs(g_means[1] - g_means[0]) > _RGB_DIFF_THRESH:
        raise AssertionError('G[0] vs G[1] too different. '
                             f'[0]: {g_means[0]:.3f}, [1]: {g_means[1]:.3f}')
      if abs(g_means[2] - g_means[1]) > _RGB_DIFF_THRESH:
        raise AssertionError('G[1] vs G[2] too different. '
                             f'[1]: {g_means[1]:.3f}, [2]: {g_means[2]:.3f}')
      if abs(r_means[2] - r_means[0]) > _RGB_DIFF_THRESH:
        raise AssertionError('R[0] vs R[2] too different. '
                             f'[0]: {r_means[0]:.3f}, [2]: {r_means[2]:.3f}')
      if abs(b_means[1] - b_means[0]) > _RGB_DIFF_THRESH:
        raise AssertionError('B[0] vs B[1] too different. '
                             f'[0]: {b_means[0]:.3f}, [1]: {b_means[1]:.3f}')

      # assert boosted planes in caps
      if abs(r_means[1] - 2*r_means[0]) > _RGB_DIFF_THRESH:
        raise AssertionError('R[1] not boosted enough or too much. '
                             f'[0]: {r_means[0]:.4f}, [1]: {r_means[1]:.4f}')
      if abs(b_means[2] - 2*b_means[0]) > _RGB_DIFF_THRESH:
        raise AssertionError('B[2] not boosted enough or too much. '
                             f'[0]: {b_means[0]:.4f}, [2]: {b_means[2]:.4f}')


if __name__ == '__main__':
  test_runner.main()

