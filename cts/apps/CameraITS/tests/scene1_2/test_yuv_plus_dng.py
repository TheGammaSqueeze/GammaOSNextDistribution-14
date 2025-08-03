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
"""Verifies single capture of both DNG and YUV."""


import logging
import os.path
from mobly import test_runner

import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_MAX_IMG_SIZE = (1920, 1080)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SENSOR_CALIBRATION_XFORM_ATOL = 0.1
_UNITY_MATRIX = np.array([1, 0, 0,
                          0, 1, 0,
                          0, 0, 1])


class YuvPlusDngTest(its_base_test.ItsBaseTest):
  """Test capturing a single frame as both DNG and YUV outputs."""

  def test_yuv_plus_dng(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(camera_properties_utils.raw(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Create requests
      mono_camera = camera_properties_utils.mono_camera(props)
      cam.do_3a(mono_camera=mono_camera)
      req = capture_request_utils.auto_capture_request()
      max_dng_size = capture_request_utils.get_available_output_sizes(
          'raw', props)[0]
      if capture_request_utils.is_common_aspect_ratio(max_dng_size):
        w, h = capture_request_utils.get_available_output_sizes(
            'yuv', props, _MAX_IMG_SIZE, max_dng_size)[0]
      else:
        w, h = capture_request_utils.get_available_output_sizes(
            'yuv', props, max_size=_MAX_IMG_SIZE)[0]
      out_surfaces = [{'format': 'dng'},
                      {'format': 'yuv', 'width': w, 'height': h}]
      cap_dng, cap_yuv = cam.do_capture(req, out_surfaces)

      img = image_processing_utils.convert_capture_to_rgb_image(cap_yuv)
      image_processing_utils.write_image(img, f'{name_with_log_path}_yuv.jpg')

      with open(f'{name_with_log_path}.dng', 'wb') as f:
        f.write(cap_dng['data'])

      # Transform/Matrix check: note value can return None
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      e_msg = ''
      if first_api_level >= its_session_utils.ANDROID15_API_LEVEL:
        for i in (1, 2):
          if props.get(f'android.sensor.calibrationTransform{i}'):
            calibration_xform = props[f'android.sensor.calibrationTransform{i}']
            logging.debug('Rational Cal Transform%d: %s',
                          i, str(calibration_xform))
            calibration_xform = capture_request_utils.rational_to_float(
                calibration_xform)
            logging.debug('Float Cal Transform%d: %s',
                          i, str(calibration_xform))
            if not np.allclose(calibration_xform, _UNITY_MATRIX,
                               atol=_SENSOR_CALIBRATION_XFORM_ATOL):
              e_msg += (
                  f'SENSOR_CALIBRATION_TRANSFORM{i} {calibration_xform} !~= '
                  f'{_UNITY_MATRIX}, ATOL: {_SENSOR_CALIBRATION_XFORM_ATOL}\n'
              )
          else:
            logging.debug('Check skipped: no SENSOR_CALIBRATION_TRANSFORM%d', i)
      if e_msg:
        raise AssertionError(e_msg)

if __name__ == '__main__':
  test_runner.main()
