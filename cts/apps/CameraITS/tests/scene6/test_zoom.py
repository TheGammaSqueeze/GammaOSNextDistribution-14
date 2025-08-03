# Copyright 2020 The Android Open Source Project
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
"""Verify zoom ratio scales circle sizes correctly."""


import logging
import os.path

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
from mobly import test_runner
import numpy as np
import zoom_capture_utils

_CIRCLISH_RTOL = 0.05  # contour area vs ideal circle area pi*((w+h)/4)**2
_JPEG_STR = 'jpg'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10
_TEST_FORMATS = ['yuv']  # list so can be appended for newer Android versions
_TEST_REQUIRED_MPC = 33
_ZOOM_MIN_THRESH = 2.0


class ZoomTest(its_base_test.ItsBaseTest):
  """Test the camera zoom behavior.
  """

  def test_zoom(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Determine test zoom range
      z_range = props['android.control.zoomRatioRange']
      debug = self.debug_mode
      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(z_max >= z_min * _ZOOM_MIN_THRESH)
      z_max = min(z_max, zoom_capture_utils.ZOOM_MAX_THRESH * z_min)
      z_list = np.arange(z_min, z_max, (z_max - z_min) / (_NUM_STEPS - 1))
      z_list = np.append(z_list, z_max)
      logging.debug('Testing zoom range: %s', str(z_list))

      # Check media performance class
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      if (media_performance_class >= _TEST_REQUIRED_MPC and
          cam.is_primary_camera() and
          cam.has_ultrawide_camera(facing=props['android.lens.facing']) and
          int(z_min) >= 1):
        raise AssertionError(
            f'With primary camera {self.camera_id}, '
            f'MPC >= {_TEST_REQUIRED_MPC}, and '
            'an ultrawide camera facing in the same direction as the primary, '
            'zoom_ratio minimum must be less than 1.0. '
            f'Found media performance class {media_performance_class} '
            f'and minimum zoom {z_min}.')

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, size = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
        yuv_size = capture_request_utils.get_largest_yuv_format(props)
        size = [yuv_size['width'], yuv_size['height']]
      logging.debug('capture size: %s', str(size))
      logging.debug('test TOLs: %s', str(test_tols))

      # determine first API level and test_formats to test
      test_formats = _TEST_FORMATS
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      if first_api_level >= its_session_utils.ANDROID14_API_LEVEL:
        test_formats.append(_JPEG_STR)

      # do captures over zoom range and find circles with cv2
      img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
      req = capture_request_utils.auto_capture_request()
      test_failed = False
      for fmt in test_formats:
        logging.debug('testing %s format', fmt)
        test_data = {}
        for i, z in enumerate(z_list):
          req['android.control.zoomRatio'] = z
          logging.debug('zoom ratio: %.3f', z)
          cam.do_3a(
              zoom_ratio=z,
              out_surfaces={
                  'format': fmt,
                  'width': size[0],
                  'height': size[1]
              },
              repeat_request=None,
          )
          cap = cam.do_capture(
              req, {'format': fmt, 'width': size[0], 'height': size[1]},
              reuse_session=True)

          img = image_processing_utils.convert_capture_to_rgb_image(
              cap, props=props)
          img_name = f'{img_name_stem}_{fmt}_{round(z, 2)}.{_JPEG_STR}'
          image_processing_utils.write_image(img, img_name)

          # determine radius tolerance of capture
          cap_fl = cap['metadata']['android.lens.focalLength']
          radius_tol, offset_tol = test_tols.get(
              cap_fl,
              (zoom_capture_utils.RADIUS_RTOL, zoom_capture_utils.OFFSET_RTOL)
          )

          # Scale circlish RTOL for low zoom ratios
          if z < 1:
            circlish_rtol = _CIRCLISH_RTOL / z
          else:
            circlish_rtol = _CIRCLISH_RTOL

          # Find the center circle in img and check if it's cropped
          circle = zoom_capture_utils.find_center_circle(
              img, img_name, size, z, z_list[0], circlish_rtol=circlish_rtol,
              debug=debug)

          # Zoom is too large to find center circle
          if circle is None:
            break
          test_data[i] = {'z': z, 'circle': circle, 'r_tol': radius_tol,
                          'o_tol': offset_tol, 'fl': cap_fl}

        if not zoom_capture_utils.verify_zoom_results(
            test_data, size, z_max, z_min):
          test_failed = True

    if test_failed:
      raise AssertionError(f'{_NAME} failed! Check test_log.DEBUG for errors')

if __name__ == '__main__':
  test_runner.main()
