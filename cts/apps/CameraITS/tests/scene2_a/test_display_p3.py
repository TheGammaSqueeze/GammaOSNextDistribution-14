# Copyright 2023 The Android Open Source Project
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
"""Verifies P3 JPEG output is correct."""


import logging
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]


def _check_icc(jpeg_img, color_space, fmt_str, icc_path):
  if not image_processing_utils.jpeg_has_icc_profile(jpeg_img):
    logging.error('FMT: %s/%s has no icc profile', fmt_str, color_space)
    return False
  elif not image_processing_utils.is_jpeg_icc_profile_correct(
      jpeg_img, color_space, icc_path):
    logging.error('FMT: %s/%s has incorrect icc profile', fmt_str, color_space)
    return False
  return True


class DisplayP3Test(its_base_test.ItsBaseTest):
  """Test Display P3 JPEG capture.

  Note the test does not require a specific target but does perform
  both automatic and manual captures so it requires a fixed scene
  where 3A can converge.
  """

  def test_display_p3(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      # check SKIP conditions
      camera_properties_utils.skip_unless(cam.is_p3_capture_supported())

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      cam.do_3a()

      req = capture_request_utils.auto_capture_request()

      display_p3_color_space = camera_properties_utils.color_space_to_int(
          'DISPLAY_P3')

      surface_fmt = {'format': 'jpeg'}

      try:
        p3_fmt = surface_fmt.copy()
        p3_fmt['colorSpace'] = display_p3_color_space

        p3_cap = cam.do_capture(req, p3_fmt)

        name_with_path = os.path.join(self.log_path, _NAME)

        fmt_str = surface_fmt['format']
        p3_img_name = f'{name_with_path}_{fmt_str}_cap_display_p3.jpg'
        p3_img_icc = p3_img_name.strip('.jpg') + '.icc'
        p3_jpeg_img = image_processing_utils.get_img(p3_cap['data'])
        p3_jpeg_img.save(p3_img_name)

        if not _check_icc(p3_jpeg_img, 'DISPLAY_P3', fmt_str, p3_img_icc):
          raise AssertionError('Failure: P3 JPEG does not contain correct '
                               'icc profile')
        if not image_processing_utils.p3_img_has_wide_gamut(p3_jpeg_img):
          raise AssertionError('Failure: P3 JPEG does not contain wide gamut '
                               'pixels outside the SRGB color space.')

      # pylint: disable=broad-except
      except Exception as e:
        raise AssertionError('Failure') from e

if __name__ == '__main__':
  test_runner.main()
