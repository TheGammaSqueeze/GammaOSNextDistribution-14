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
"""Verifies 3A converges in CONTINUOUS_PICTURE mode."""


import logging
import os.path

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_CONTINUOUS_PICTURE_MODE = 4
_CONVERGED_3A = (2, 2, 2)  # (AE, AF, AWB)
_M_TO_CM = 100
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NS_TO_MS = 1E-6
_NUM_FRAMES = 50
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RGB_G_CH = 1
_VGA_W, _VGA_H = 640, 480


def _capture_frames(cam):
  """Captures frames, logs info, and creates cap_3a_state_list.

  Args:
    cam: a camera capture object.

  Returns:
    cap_3a_state_list: list of 3a states [AE, AF, AWB] during captures.
    imgs: list of images indexed by frame.
  """
  cap_3a_state_list = []
  req = capture_request_utils.auto_capture_request()
  req['android.control.afMode'] = _CONTINUOUS_PICTURE_MODE
  fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
  caps = cam.do_capture([req]*_NUM_FRAMES, fmt)

  # Extract frame metadata and frame.
  imgs = []
  for i, cap in enumerate(caps):
    md = cap['metadata']
    exp = md['android.sensor.exposureTime']
    iso = md['android.sensor.sensitivity']
    fd = md['android.lens.focalLength']
    ae_state = md['android.control.aeState']
    af_state = md['android.control.afState']
    awb_state = md['android.control.awbState']
    fd_str = 'infinity'
    if fd != 0.0:
      fd_str = f'{_M_TO_CM/fd:.2f}cm'
    img = image_processing_utils.convert_capture_to_rgb_image(cap)
    patch = image_processing_utils.get_image_patch(
        img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
    green_mean = image_processing_utils.compute_image_means(patch)[_RGB_G_CH]
    logging.debug(
        '%d, iso: %d, exp: %.2fms, fd: %s, avg: %.3f, [ae,af,awb]'
        ': [%d,%d,%d]', i, iso, exp * _NS_TO_MS, fd_str, green_mean, ae_state,
        af_state, awb_state)
    cap_3a_state_list.append([ae_state, af_state, awb_state])
    imgs.append(img)
  return cap_3a_state_list, imgs


class ContinuousPictureTest(its_base_test.ItsBaseTest):
  """Test 3A converges in CONTINUOUS_PICTURE mode.

  Sets camera into CONTINUOUS_PICTURE mode and does NUM_FRAMES capture.
  By the end of NUM_FRAMES capture, 3A should be in converged state.
  Converged state is [2, 2, 2] for [AE, AF, AWB]

  State information:
    AE_STATES: {0: INACTIVE, 1: SEARCHING, 2: CONVERGED, 3: LOCKED,
                4: FLASH_REQ, 5: PRECAPTURE}
    AF_STATES: {0: INACTIVE, 1: PASSIVE_SCAN, 2: PASSIVE_FOCUSED,
                3: ACTIVE_SCAN, 4: FOCUS_LOCKED, 5: NOT_FOCUSED_LOCKED,
                6: PASSIVE_UNFOCUSED}
    AWB_STATES: {0: INACTIVE, 1: SEARCHING, 2: CONVERGED, 3: LOCKED}
  """

  def test_continuous_picture(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Check SKIP conditions.
      camera_properties_utils.skip_unless(
          camera_properties_utils.continuous_picture(props) and
          camera_properties_utils.read_3a(props))

      # Load chart for scene.
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Do 3A up front.
      cam.do_3a()

      # Ensure 3A settles in CONTINUOUS_PICTURE mode with no scene change.
      cap_3a_state_list, imgs = _capture_frames(cam)
      final_3a = cap_3a_state_list[_NUM_FRAMES-1]
      if final_3a != list(_CONVERGED_3A):
        for i, img in enumerate(imgs):
          image_processing_utils.write_image(
              img, f'{os.path.join(self.log_path, _NAME)}_{i}.jpg')
        raise AssertionError(
            f'Last frame [AE,AF,AWB]: {final_3a}. CONVERGED: {_CONVERGED_3A}.')

if __name__ == '__main__':
  test_runner.main()

