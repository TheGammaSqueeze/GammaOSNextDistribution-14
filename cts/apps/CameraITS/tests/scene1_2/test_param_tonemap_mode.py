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
"""Verifies android.tonemap.mode parameter applies."""


import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_COLORS = ('R', 'G', 'B')
_L_TMAP = 32
_MAX_RGB_MEANS_DIFF = 0.05  # max RBG means diff for same tonemaps
_MIN_RGB_RATIO_DIFF = 0.1  # min RGB ratio diff for different tonemaps
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_COLORS = len(_COLORS)
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2


def compute_means_and_save(cap, img_name):
  """Compute the RGB means of a capture and save image.

  Args:
    cap: 'YUV' or 'JPEG' capture.
    img_name: text for saved image name.

  Returns:
    RGB means.
  """
  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  image_processing_utils.write_image(img, img_name)
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  rgb_means = image_processing_utils.compute_image_means(patch)
  logging.debug('RGB means: %s', str(rgb_means))
  return rgb_means


class ParamTonemapModeTest(its_base_test.ItsBaseTest):
  """Test that android.tonemap.mode param is applied.

  Applies different tonemap curves to each R,G,B channel, and checks that the
  output images are modified as expected.

  The HAL3.2 spec requires curves up to l=64 control pts must be supported.

  Test #1: test tonemap curves have expected effect.
  Take two shots where each has a linear tonemap, with the 2nd shot having a
  steeper gradient. The gradient for each R,G,B channel increases.
  i.e. R[n=1] should be brighter than R[n=0], and G[n=1] should be brighter
  than G[n=0] by a larger margin, etc.

  Test #2: length of tonemap curve (i.e. num of pts) has no effect
  Take two shots with tonemap curve of length _L_TMAP and _L_TMAP*2
  The two shots should be the same.
  """

  def test_param_tonemap_mode(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.tonemap_mode(props, 0))
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Determine format, exposure and gain for requests
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      exp, sens = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['midExposureTime']
      exp //= 2

      # Test 1
      means_1 = []
      for n in [0, 1]:
        req = capture_request_utils.manual_capture_request(sens, exp)
        req['android.tonemap.mode'] = 0
        req['android.tonemap.curve'] = {
            'red': sum([[i/(_L_TMAP-1), min(1.0, (1+0.5*n)*i/(_L_TMAP-1))]
                        for i in range(_L_TMAP)], []),
            'green': sum([[i/(_L_TMAP-1), min(1.0, (1+1.0*n)*i/(_L_TMAP-1))]
                          for i in range(_L_TMAP)], []),
            'blue': sum([[i/(_L_TMAP-1), min(1.0, (1+1.5*n)*i/(_L_TMAP-1))]
                         for i in range(_L_TMAP)], [])}
        cap = cam.do_capture(req, fmt)
        img_name = f'{name_with_log_path}_n={n}.jpg'
        means_1.append(compute_means_and_save(cap, img_name))
      if 0.0 in means_1[1]:
        raise AssertionError(f'0.0 value in test 1 means: {means_1[0]}')
      rgb_ratios = [means_1[1][i]/means_1[0][i] for i in range(_NUM_COLORS)]
      logging.debug('Test 1: RGB ratios: %s', str(rgb_ratios))

      # Assert proper behavior
      for i in range(_NUM_COLORS-1):
        if rgb_ratios[i+1]-rgb_ratios[i] < _MIN_RGB_RATIO_DIFF:
          raise AssertionError(
              f'RGB ratios {i+1}: {rgb_ratios[i+1]:.4f}, {i}: '
              f'{rgb_ratios[i]:.4f}, ATOL: {_MIN_RGB_RATIO_DIFF}')

      # Test 2
      means_2 = []
      for size in [_L_TMAP, 2*_L_TMAP]:
        tonemap_curve = sum([[i/(size-1)]*2 for i in range(size)], [])
        req = capture_request_utils.manual_capture_request(sens, exp)
        req['android.tonemap.mode'] = 0
        req['android.tonemap.curve'] = {'red': tonemap_curve,
                                        'green': tonemap_curve,
                                        'blue': tonemap_curve}
        cap = cam.do_capture(req)
        img_name = f'{name_with_log_path}_size={size:02d}.jpg'
        means_2.append(compute_means_and_save(cap, img_name))

      rgb_diffs = [means_2[1][i] - means_2[0][i] for i in range(_NUM_COLORS)]
      logging.debug('Test 2: RGB diffs: %s', str(rgb_diffs))

      # assert proper behavior
      for i, ch in enumerate(_COLORS):
        if abs(rgb_diffs[i]) > _MAX_RGB_MEANS_DIFF:
          raise AssertionError(f'{ch} rgb_diffs: {rgb_diffs[i]:.4f}, '
                               f'THRESH: {_MAX_RGB_MEANS_DIFF}')

if __name__ == '__main__':
  test_runner.main()
