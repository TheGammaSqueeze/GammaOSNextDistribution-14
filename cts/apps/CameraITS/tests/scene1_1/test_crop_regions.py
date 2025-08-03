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
"""Verifies android.scaler.cropRegion param works."""


import logging
import os.path

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

# 5 regions specified in normalized (x, y, w, h) coords.
_CROP_REGIONS = [(0.0, 0.0, 0.5, 0.5),  # top-left
                 (0.5, 0.0, 0.5, 0.5),  # top-right
                 (0.0, 0.5, 0.5, 0.5),  # bottom-left
                 (0.5, 0.5, 0.5, 0.5),  # bottom-right
                 (0.25, 0.25, 0.5, 0.5)]  # center
_MIN_DIGITAL_ZOOM_THRESH = 2
_NAME = os.path.splitext(os.path.basename(__file__))[0]


class CropRegionsTest(its_base_test.ItsBaseTest):
  """Test that crop regions works."""

  def test_crop_regions(self):
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
          camera_properties_utils.freeform_crop(props) and
          camera_properties_utils.per_frame_control(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      a = props['android.sensor.info.activeArraySize']
      ax, ay = a['left'], a['top']
      aw, ah = a['right'] - a['left'], a['bottom'] - a['top']
      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['minSensitivity']
      logging.debug('Active sensor region (%d,%d %dx%d)', ax, ay, aw, ah)

      # Uses a 2x digital zoom.
      max_digital_zoom = capture_request_utils.get_max_digital_zoom(props)
      if max_digital_zoom < _MIN_DIGITAL_ZOOM_THRESH:
        raise AssertionError(f'Max digital zoom: {max_digital_zoom}, '
                             f'THRESH: {_MIN_DIGITAL_ZOOM_THRESH}')

      # Capture a full frame.
      req = capture_request_utils.manual_capture_request(s, e)
      cap_full = cam.do_capture(req)
      img_full = image_processing_utils.convert_capture_to_rgb_image(cap_full)
      wfull, hfull = cap_full['width'], cap_full['height']
      image_processing_utils.write_image(
          img_full, f'{name_with_log_path}_full_{wfull}x{hfull}.jpg')

      # Capture a burst of crop region frames.
      # Note that each region is 1/2x1/2 of the full frame, and is digitally
      # zoomed into the full size output image, so must be downscaled (below)
      # by 2x when compared to a tile of the full image.
      reqs = []
      for x, y, w, h in _CROP_REGIONS:
        req = capture_request_utils.manual_capture_request(s, e)
        req['android.scaler.cropRegion'] = {
            'top': int(ah * y),
            'left': int(aw * x),
            'right': int(aw * (x + w)),
            'bottom': int(ah * (y + h))}
        reqs.append(req)
      caps_regions = cam.do_capture(reqs)
      match_failed = False
      e_msg = []
      for i, cap in enumerate(caps_regions):
        a = cap['metadata']['android.scaler.cropRegion']
        ax, ay = a['left'], a['top']
        aw, ah = a['right'] - a['left'], a['bottom'] - a['top']

        # Match this crop image against each of the five regions of
        # the full image, to find the best match (which should be
        # the region that corresponds to this crop image).
        img_crop = image_processing_utils.convert_capture_to_rgb_image(cap)
        img_crop = image_processing_utils.downscale_image(img_crop, 2)
        image_processing_utils.write_image(
            img_crop, f'{name_with_log_path}_crop{i}.jpg')
        min_diff = None
        min_diff_region = None
        for j, (x, y, w, h) in enumerate(_CROP_REGIONS):
          tile_full = image_processing_utils.get_image_patch(
              img_full, x, y, w, h)
          wtest = min(tile_full.shape[1], aw)
          htest = min(tile_full.shape[0], ah)
          tile_full = tile_full[0:htest:, 0:wtest:, ::]
          tile_crop = img_crop[0:htest:, 0:wtest:, ::]
          image_processing_utils.write_image(
              tile_full, f'{name_with_log_path}_fullregion{j}.jpg')
          diff = np.fabs(tile_full - tile_crop).mean()
          if min_diff is None or diff < min_diff:
            min_diff = diff
            min_diff_region = j
        if i != min_diff_region:
          match_failed = True
          e_msg.append(f'i != min_diff_region. i: {i}, '
                       f'min_diff_region: {min_diff_region}. ')
        logging.debug('Crop image %d (%d,%d %dx%d) best match with region %d',
                      i, ax, ay, aw, ah, min_diff_region)

    if match_failed:
      raise AssertionError(f'Match failed: {e_msg}')

if __name__ == '__main__':
  test_runner.main()
