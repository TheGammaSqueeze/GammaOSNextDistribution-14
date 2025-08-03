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
"""Verifies shots with different tonemap curves."""


import logging
import os.path
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_MAX_DELTA_SAME = 0.03  # match number in test_burst_sameness_manual
_MIN_DELTA_DIFF = 0.10
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_CAPTURES = 3
_NUM_FRAMES_PER_CAPTURE = 8
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RGB_R_CH = 0
_TMAP_NO_DELTA_FRAMES = list(range(_NUM_CAPTURES-1)) + list(
    range(_NUM_CAPTURES, 2*_NUM_CAPTURES-1))


def do_captures_and_extract_means(
    cam, req, fmt, num_frames_per_cap, tonemap, log_path):
  """Do captures, save image and extract means from center patch.

  Args:
    cam: camera object.
    req: camera request.
    fmt: capture format.
    num_frames_per_cap: int; number of frames per capture
    tonemap: string to determine 'linear' or 'default' tonemap.
    log_path: location to save images.

  Returns:
    appended R channel means list.
  """
  r_means = []

  for i in range(_NUM_CAPTURES):
    cap = cam.do_capture([req]*num_frames_per_cap, fmt)
    img = image_processing_utils.convert_capture_to_rgb_image(cap[-1])
    image_processing_utils.write_image(
        img, f'{os.path.join(log_path, _NAME)}_{tonemap}_{i}.jpg')
    patch = image_processing_utils.get_image_patch(
        img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
    rgb_means = image_processing_utils.compute_image_means(patch)
    logging.debug('%s frame %d means: %s', tonemap, i, str(rgb_means))
    r_means.append(rgb_means[_RGB_R_CH])
  return r_means


class TonemapSequenceTest(its_base_test.ItsBaseTest):
  """Tests a sequence of shots with different tonemap curves.

  There should be _NUM_CAPTURES with a linear tonemap followed by a second set
  of _NUM_CAPTURES with the default tonemap.

  asserts the frames in each _NUM_CAPTURES bunch are similar
  asserts the frames in the 2 _NUM_CAPTURES bunches are different by >10%
  """

  def test_tonemap_sequence(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          not camera_properties_utils.mono_camera(props) and
          camera_properties_utils.linear_tonemap(props))
      log_path = self.log_path

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # define formats
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      means = []

      # set params based on per_frame_control & read_3a
      manual_and_per_frame_control = (
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.read_3a(props))
      if manual_and_per_frame_control:
        logging.debug('PER_FRAME_CONTROL supported.')
        num_frames_per_cap = 1
        sens, exp, _, _, f_dist = cam.do_3a(do_af=True, get_results=True)
      else:
        logging.debug('PER_FRAME_CONTROL not supported.')
        num_frames_per_cap = _NUM_FRAMES_PER_CAPTURE
        cam.do_3a()

      for tonemap in ['linear', 'default']:
        if tonemap == 'linear':
          if manual_and_per_frame_control:
            req = capture_request_utils.manual_capture_request(
                sens, exp, f_dist, True, props)
          else:
            req = capture_request_utils.auto_capture_request(
                linear_tonemap=True, props=props)
        else:
          if manual_and_per_frame_control:
            req = capture_request_utils.manual_capture_request(
                sens, exp, f_dist, False)
          else:
            req = capture_request_utils.auto_capture_request()

        means.extend(do_captures_and_extract_means(
            cam, req, fmt, num_frames_per_cap, tonemap, log_path))

      # Compute the delta between each consecutive frame pair
      deltas = [np.fabs(means[i+1]-means[i]) for i in range(2*_NUM_CAPTURES-1)]
      logging.debug('Deltas between consecutive frames: %s', str(deltas))

      # assert frames similar with same tonemap
      if not all([deltas[i] < _MAX_DELTA_SAME for i in _TMAP_NO_DELTA_FRAMES]):
        raise AssertionError('Captures too different with same tonemap! '
                             f'deltas: {deltas}, ATOL: {_MAX_DELTA_SAME}')

      # assert frames different with tonemap change
      if deltas[_NUM_CAPTURES-1] <= _MIN_DELTA_DIFF:
        raise AssertionError('Captures too similar with different tonemaps! '
                             f'deltas: {deltas}, THRESH: {_MIN_DELTA_DIFF}')

if __name__ == '__main__':
  test_runner.main()
