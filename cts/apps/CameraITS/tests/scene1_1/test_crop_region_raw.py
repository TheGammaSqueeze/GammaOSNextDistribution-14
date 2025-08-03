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
"""Verifies RAW streams are not croppable."""


import logging
import math
import os.path

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_CROP_FULL_ERROR_THRESHOLD = 3  # pixels
_CROP_REGION_ERROR_THRESHOLD = 0.01  # reltol
_DIFF_THRESH = 0.05  # reltol
_NAME = os.path.splitext(os.path.basename(__file__))[0]


class CropRegionRawTest(its_base_test.ItsBaseTest):
  """Test that RAW streams are not croppable."""

  def test_crop_region_raw(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.raw16(props) and
          camera_properties_utils.per_frame_control(props) and
          not camera_properties_utils.mono_camera(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Calculate the active sensor region for a full (non-cropped) image.
      a = props['android.sensor.info.activeArraySize']
      ax, ay = a['left'], a['top']
      aw, ah = a['right'] - a['left'], a['bottom'] - a['top']
      logging.debug('Active sensor region: (%d,%d %dx%d)', ax, ay, aw, ah)

      full_region = {
          'left': 0,
          'top': 0,
          'right': aw,
          'bottom': ah
      }

      # Calculate a center crop region.
      zoom = min(3.0, camera_properties_utils.get_max_digital_zoom(props))
      if zoom < 1:
        raise AssertionError(f'zoom: {zoom:.2f}')
      crop_w = aw // zoom
      crop_h = ah // zoom

      crop_region = {
          'left': aw // 2 - crop_w // 2,
          'top': ah // 2 - crop_h // 2,
          'right': aw // 2 + crop_w // 2,
          'bottom': ah // 2 + crop_h // 2
      }

      # Capture without a crop region.
      # Use a manual request with a linear tonemap so that the YUV and RAW
      # should look the same (once converted by image_processing_utils).
      e, s = target_exposure_utils.get_target_exposure_combos(log_path, cam)[
          'minSensitivity']
      req = capture_request_utils.manual_capture_request(s, e, 0.0, True, props)
      cap1_raw, cap1_yuv = cam.do_capture(req, cam.CAP_RAW_YUV)

      # Capture with a crop region.
      req['android.scaler.cropRegion'] = crop_region
      cap2_raw, cap2_yuv = cam.do_capture(req, cam.CAP_RAW_YUV)

      # Check the metadata related to crop regions.
      # When both YUV and RAW are requested, the crop region that's
      # applied to YUV should be reported.
      # Note that the crop region returned by the cropped captures doesn't
      # need to perfectly match the one that was requested.
      imgs = {}
      for s, cap, cr_expected, err_delta in [
          ('yuv_full', cap1_yuv, full_region, _CROP_FULL_ERROR_THRESHOLD),
          ('raw_full', cap1_raw, full_region, _CROP_FULL_ERROR_THRESHOLD),
          ('yuv_crop', cap2_yuv, crop_region, _CROP_REGION_ERROR_THRESHOLD),
          ('raw_crop', cap2_raw, crop_region, _CROP_REGION_ERROR_THRESHOLD)]:

        # Convert the capture to RGB and dump to a file.
        img = image_processing_utils.convert_capture_to_rgb_image(cap,
                                                                  props=props)
        image_processing_utils.write_image(img, f'{name_with_log_path}_{s}.jpg')
        imgs[s] = img

        # Get the crop region that is reported in the capture result.
        cr_reported = cap['metadata']['android.scaler.cropRegion']
        x, y = cr_reported['left'], cr_reported['top']
        w = cr_reported['right'] - cr_reported['left']
        h = cr_reported['bottom'] - cr_reported['top']
        logging.debug('Crop reported on %s: (%d,%d %dx%d)', s, x, y, w, h)

        # Test that the reported crop region is the same as the expected
        # one, for a non-cropped capture, and is close to the expected one,
        # for a cropped capture.
        ex = _CROP_FULL_ERROR_THRESHOLD
        ey = _CROP_FULL_ERROR_THRESHOLD
        if math.isclose(err_delta, _CROP_REGION_ERROR_THRESHOLD, rel_tol=0.01):
          ex = aw * err_delta
          ey = ah * err_delta
        logging.debug('error X, Y: %.2f, %.2f', ex, ey)
        if not (
            (abs(cr_expected['left'] - cr_reported['left']) <= ex) and
            (abs(cr_expected['right'] - cr_reported['right']) <= ex) and
            (abs(cr_expected['top'] - cr_reported['top']) <= ey) and
            (abs(cr_expected['bottom'] - cr_reported['bottom']) <= ey)):
          raise AssertionError(f'expected: {cr_expected}, reported: '
                               f'{cr_reported}, ex: {ex:.2f}, ey: {ey:.2f}')

      # Also check the image content; 3 of the 4 shots should match.
      # Note that all the shots are RGB below; the variable names correspond
      # to what was captured.

      # Shrink the YUV images 2x2 -> 1 to account for the size reduction that
      # the raw images went through in the RGB conversion.
      imgs2 = {}
      for s, img in imgs.items():
        h, w, _ = img.shape
        if s in ['yuv_full', 'yuv_crop']:
          img = img.reshape(h//2, 2, w//2, 2, 3).mean(3).mean(1)
          img = img.reshape(h//2, w//2, 3)
        imgs2[s] = img

      # Strip any border pixels from the raw shots (since the raw images may
      # be larger than the YUV images). Assume a symmetric padded border.
      xpad = (imgs2['raw_full'].shape[1] - imgs2['yuv_full'].shape[1]) // 2
      ypad = (imgs2['raw_full'].shape[0] - imgs2['yuv_full'].shape[0]) // 2
      wyuv = imgs2['yuv_full'].shape[1]
      hyuv = imgs2['yuv_full'].shape[0]
      imgs2['raw_full'] = imgs2['raw_full'][ypad:ypad+hyuv:,
                                            xpad:xpad+wyuv:,
                                            ::]
      imgs2['raw_crop'] = imgs2['raw_crop'][ypad:ypad+hyuv:,
                                            xpad:xpad+wyuv:,
                                            ::]
      logging.debug('Stripping padding before comparison: %dx%d', xpad, ypad)

      for s, img in imgs2.items():
        image_processing_utils.write_image(
            img, f'{name_with_log_path}_comp_{s}.jpg')

      # Compute diffs between images of the same type.
      # The raw_crop and raw_full shots should be identical (since the crop
      # doesn't apply to raw images), and the yuv_crop and yuv_full shots
      # should be different.
      diff_yuv = np.fabs((imgs2['yuv_full'] - imgs2['yuv_crop'])).mean()
      diff_raw = np.fabs((imgs2['raw_full'] - imgs2['raw_crop'])).mean()
      logging.debug('YUV diff (crop vs. non-crop): %.3f', diff_yuv)
      logging.debug('RAW diff (crop vs. non-crop): %.3f', diff_raw)

      if diff_yuv <= _DIFF_THRESH:
        raise AssertionError('YUV diff too small! diff_yuv: '
                             f'{diff_yuv:.3f}, THRESH: {_DIFF_THRESH}')
      if diff_raw >= _DIFF_THRESH:
        raise AssertionError('RAW diff too big! diff_raw: '
                             f'{diff_raw:.3f}, THRESH: {_DIFF_THRESH}')

if __name__ == '__main__':
  test_runner.main()

