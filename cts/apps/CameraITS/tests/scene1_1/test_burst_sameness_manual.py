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
"""Verifies manual burst capture consistency."""


import logging
import os.path
from matplotlib import pylab
import matplotlib.pyplot
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_API_LEVEL_30 = 30
_BURST_LEN = 50
_COLORS = ('R', 'G', 'B')
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_BURSTS = 2
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_SPREAD_THRESH = 0.03
_SPREAD_THRESH_API_LEVEL_30 = 0.02

_NUM_FRAMES = _BURST_LEN * _NUM_BURSTS


class BurstSamenessManualTest(its_base_test.ItsBaseTest):
  """Take long bursts of images and check that they're all identical.

  Assumes a static scene. Can be used to idenfity if there are sporadic
  frames that are processed differently or have artifacts. Uses manual
  capture settings.
  """

  def test_burst_sameness_manual(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_path = os.path.join(log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Capture at the smallest resolution
      _, fmt = capture_request_utils.get_fastest_manual_capture_settings(props)
      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['minSensitivity']
      req = capture_request_utils.manual_capture_request(s, e)
      w, h = fmt['width'], fmt['height']

      # Capture bursts of YUV shots.
      # Get the mean values of a center patch for each.
      # Also build a 4D array, imgs, which is an array of all RGB images.
      r_means = []
      g_means = []
      b_means = []
      imgs = np.empty([_NUM_FRAMES, h, w, 3])
      for j in range(_NUM_BURSTS):
        caps = cam.do_capture([req]*_BURST_LEN, [fmt])
        for i, cap in enumerate(caps):
          n = j*_BURST_LEN + i
          imgs[n] = image_processing_utils.convert_capture_to_rgb_image(cap)
          patch = image_processing_utils.get_image_patch(
              imgs[n], _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
          means = image_processing_utils.compute_image_means(patch)
          r_means.append(means[0])
          g_means.append(means[1])
          b_means.append(means[2])

      # Save first frame for setup debug
      image_processing_utils.write_image(
          imgs[0], f'{name_with_path}_frame000.jpg')

      # Plot RGB means vs frames
      frames = range(_NUM_FRAMES)
      pylab.figure(_NAME)
      pylab.title(_NAME)
      pylab.plot(frames, r_means, '-ro')
      pylab.plot(frames, g_means, '-go')
      pylab.plot(frames, b_means, '-bo')
      pylab.ylim([0, 1])
      pylab.xlabel('frame number')
      pylab.ylabel('RGB avg [0, 1]')
      matplotlib.pyplot.savefig(f'{name_with_path}_plot_means.png')

      # determine spread_thresh
      spread_thresh = _SPREAD_THRESH
      if its_session_utils.get_first_api_level(
          self.dut.serial) >= _API_LEVEL_30:
        spread_thresh = _SPREAD_THRESH_API_LEVEL_30

      # PASS/FAIL based on center patch similarity
      for plane, means in enumerate([r_means, g_means, b_means]):
        spread = max(means) - min(means)
        logging.debug('%s spread: %.5f', _COLORS[plane], spread)
        if spread > spread_thresh:
          # Save all frames if FAIL
          logging.debug('Dumping all images')
          for i in range(1, _NUM_FRAMES):
            image_processing_utils.write_image(
                imgs[i], f'{name_with_path}_frame{i:03d}.jpg')
          raise AssertionError(f'{_COLORS[plane]} spread > THRESH. spread: '
                               f'{spread}, THRESH: {spread_thresh:.2f}')

if __name__ == '__main__':
  test_runner.main()
