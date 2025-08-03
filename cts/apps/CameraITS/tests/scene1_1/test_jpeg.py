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
"""Verifies converted YUV images & device JPEG images look the same."""


import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESHOLD_MAX_RMS_DIFF = 0.01


def compute_img_means_and_save(img, fmt_name, log_path):
  """Extract center patch, compute means, and save image.

  Args:
    img: image array
    fmt_name: text to identify image
    log_path: location to save image

  Returns:
    means of image patch
  """
  name_with_log_path = os.path.join(log_path, _NAME)
  image_processing_utils.write_image(
      img, f'{name_with_log_path}_fmt={fmt_name}.jpg')
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  rgb_means = image_processing_utils.compute_image_means(patch)
  logging.debug('%s rgb_means: %s', fmt_name, str(rgb_means))
  return rgb_means


class JpegTest(its_base_test.ItsBaseTest):
  """Test that converted YUV images and device JPEG images look the same."""

  def test_jpeg(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path

      sync_latency = camera_properties_utils.sync_latency(props)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.linear_tonemap(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Initialize common request parameters
      if camera_properties_utils.compute_target_exposure(props):
        e, s = target_exposure_utils.get_target_exposure_combos(
            log_path, cam)['midExposureTime']
        req = capture_request_utils.manual_capture_request(
            s, e, 0.0, True, props)
      else:
        cam.do_3a(do_af=False)
        req = capture_request_utils.auto_capture_request(
            linear_tonemap=True, props=props, do_af=False)

      # YUV
      size = capture_request_utils.get_available_output_sizes('yuv', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'yuv'}
      cap = its_session_utils.do_capture_with_latency(
          cam, req, sync_latency, out_surface)
      img = image_processing_utils.convert_capture_to_rgb_image(cap)
      rgb_means_yuv = compute_img_means_and_save(img, 'yuv', log_path)

      # JPEG
      size = capture_request_utils.get_available_output_sizes('jpg', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'jpg'}
      cap = its_session_utils.do_capture_with_latency(
          cam, req, sync_latency, out_surface)
      img = image_processing_utils.decompress_jpeg_to_rgb_image(cap['data'])
      rgb_means_jpg = compute_img_means_and_save(img, 'jpg', log_path)

      # Assert images are similar
      rms_diff = image_processing_utils.compute_image_rms_difference_1d(
          rgb_means_yuv, rgb_means_jpg)
      logging.debug('RMS difference: %.3f', rms_diff)
      if rms_diff >= _THRESHOLD_MAX_RMS_DIFF:
        raise AssertionError(
            f'RMS diff: {rms_diff:.3f}, spec: {_THRESHOLD_MAX_RMS_DIFF}')

if __name__ == '__main__':
  test_runner.main()

