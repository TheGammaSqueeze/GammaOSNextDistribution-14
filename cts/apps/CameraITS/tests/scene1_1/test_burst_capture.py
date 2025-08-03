# Copyright 2016 The Android Open Source Project
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
"""Verify capture burst of full size images is fast enough to not timeout."""

import logging
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_FRAME_TIME_DELTA_ATOL = 60  # ideal frame delta: 33ms
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NS_TO_MS = 1.0E-6
_NUM_TEST_FRAMES = 15
_PATCH_H = 0.1  # center 10% patch params
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESH_MIN_LEVEL = 0.1  # check images aren't too dark


class BurstCaptureTest(its_base_test.ItsBaseTest):
  """Test capture a burst of full size images is fast enough and doesn't timeout.

  This test verifies that the entire capture pipeline can keep up the speed of
  fullsize capture + CPU read for at least some time.
  """

  def test_burst_capture(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.backward_compatible(props))
      req = capture_request_utils.auto_capture_request()
      cam.do_3a()
      caps = cam.do_capture([req] * _NUM_TEST_FRAMES)
      img = image_processing_utils.convert_capture_to_rgb_image(
          caps[0], props=props)
      name_with_log_path = os.path.join(self.log_path, _NAME)
      image_processing_utils.write_image(img, f'{name_with_log_path}.jpg')
      logging.debug('Image W, H: %d, %d', caps[0]['width'], caps[0]['height'])

      # Confirm center patch brightness
      patch = image_processing_utils.get_image_patch(
          img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
      r, g, b = image_processing_utils.compute_image_means(patch)
      logging.debug('RGB levels %.3f, %.3f, %.3f', r, g, b)
      if g < _THRESH_MIN_LEVEL:
        raise AssertionError(f'Image is too dark! G center patch avg: {g:.3f}, '
                             f'THRESH: {_THRESH_MIN_LEVEL}')

      # Check frames are consecutive
      frame_times = [cap['metadata']['android.sensor.timestamp']
                     for cap in caps]
      for i, time in enumerate(frame_times):
        if i > 1:
          frame_time_delta = (time - frame_times[i-1]) * _NS_TO_MS
          if frame_time_delta > _FRAME_TIME_DELTA_ATOL:
            raise AssertionError(
                f'Frame drop! Frame time delta: {frame_time_delta}ms, '
                f'ATOL: {_FRAME_TIME_DELTA_ATOL}')


if __name__ == '__main__':
  test_runner.main()
