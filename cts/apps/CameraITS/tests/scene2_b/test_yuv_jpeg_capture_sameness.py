# Copyright 2022 The Android Open Source Project
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
"""Verifies JPEG and YUV still capture images are pixel-wise matching."""


import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_MAX_IMG_SIZE = (1920, 1080)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_TEST_REQUIRED_MPC = 33
_THRESHOLD_MAX_RMS_DIFF_YUV_JPEG = 0.01  # YUV/JPEG bit exactness threshold
_THRESHOLD_MAX_RMS_DIFF_USE_CASE = 0.1  # Catch swapped color channels
_USE_CASE_PREVIEW = 1
_USE_CASE_STILL_CAPTURE = 2
_USE_CASE_VIDEO_RECORD = 3
_USE_CASE_PREVIEW_VIDEO_STILL = 4
_USE_CASE_VIDEO_CALL = 5
_USE_CASE_NAME_MAP = {
    _USE_CASE_PREVIEW: 'preview',
    _USE_CASE_STILL_CAPTURE: 'still_capture',
    _USE_CASE_VIDEO_RECORD: 'video_record',
    _USE_CASE_PREVIEW_VIDEO_STILL: 'preview_video_still',
    _USE_CASE_VIDEO_CALL: 'video_call'
}


class YuvJpegCaptureSamenessTest(its_base_test.ItsBaseTest):
  """Test capturing a single frame as both YUV and JPEG outputs."""

  def test_yuv_jpeg_capture_sameness(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path

      # check media performance class
      should_run = camera_properties_utils.stream_use_case(props)
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      if media_performance_class >= _TEST_REQUIRED_MPC and not should_run:
        its_session_utils.raise_mpc_assertion_error(
            _TEST_REQUIRED_MPC, _NAME, media_performance_class)

      # check SKIP conditions
      camera_properties_utils.skip_unless(should_run)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Find the maximum mandatory size supported by all use cases
      display_size = cam.get_display_size()
      max_camcorder_profile_size = cam.get_max_camcorder_profile_size(
          self.camera_id)
      size_bound = min([_MAX_IMG_SIZE, display_size,
                        max_camcorder_profile_size],
                       key=lambda t: int(t[0])*int(t[1]))

      logging.debug('display_size %s, max_camcorder_profile_size %s, '
                    'size_bound %s', display_size, max_camcorder_profile_size,
                    size_bound)
      w, h = capture_request_utils.get_available_output_sizes(
          'yuv', props, max_size=size_bound)[0]

      # Create requests
      fmt_yuv = {'format': 'yuv', 'width': w, 'height': h,
                 'useCase': _USE_CASE_STILL_CAPTURE}
      fmt_jpg = {'format': 'jpeg', 'width': w, 'height': h,
                 'useCase': _USE_CASE_STILL_CAPTURE}
      logging.debug('YUV & JPEG stream width: %d, height: %d', w, h)

      cam.do_3a()
      req = capture_request_utils.auto_capture_request()
      req['android.jpeg.quality'] = 100

      cap_yuv, cap_jpg = cam.do_capture(req, [fmt_yuv, fmt_jpg])
      rgb_yuv = image_processing_utils.convert_capture_to_rgb_image(
          cap_yuv, True)
      file_stem = os.path.join(log_path, _NAME)
      image_processing_utils.write_image(rgb_yuv, f'{file_stem}_yuv.jpg')
      rgb_jpg = image_processing_utils.convert_capture_to_rgb_image(
          cap_jpg, True)
      image_processing_utils.write_image(rgb_jpg, f'{file_stem}_jpg.jpg')

      rms_diff = image_processing_utils.compute_image_rms_difference_3d(
          rgb_yuv, rgb_jpg)
      msg = f'RMS diff: {rms_diff:.4f}'
      logging.debug('%s', msg)
      if rms_diff >= _THRESHOLD_MAX_RMS_DIFF_YUV_JPEG:
        raise AssertionError(msg + f', TOL: {_THRESHOLD_MAX_RMS_DIFF_YUV_JPEG}')

      # Create requests for all use cases, and make sure they are at least
      # similar enough with the STILL_CAPTURE YUV. For example, the color
      # channels must be valid.
      num_tests = 0
      num_fail = 0
      for use_case in _USE_CASE_NAME_MAP:
        num_tests += 1
        cam.do_3a()
        fmt_yuv_use_case = {'format': 'yuv', 'width': w, 'height': h,
                            'useCase': use_case}
        cap_yuv_use_case = cam.do_capture(req, [fmt_yuv_use_case])
        rgb_yuv_use_case = image_processing_utils.convert_capture_to_rgb_image(
            cap_yuv_use_case, True)
        use_case_name = _USE_CASE_NAME_MAP[use_case]
        image_processing_utils.write_image(
            rgb_yuv_use_case, f'{file_stem}_yuv_{use_case_name}.jpg')
        rms_diff = image_processing_utils.compute_image_rms_difference_3d(
            rgb_yuv, rgb_yuv_use_case)
        msg = (f'RMS diff for single {use_case_name} use case & still capture '
               f'YUV: {rms_diff:.4f}')
        logging.debug('%s', msg)
        if rms_diff >= _THRESHOLD_MAX_RMS_DIFF_USE_CASE:
          logging.error(msg + f', TOL: {_THRESHOLD_MAX_RMS_DIFF_USE_CASE}')
          num_fail += 1

      if num_fail > 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_tests}')

if __name__ == '__main__':
  test_runner.main()
