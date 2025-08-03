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
"""Verifies that preview FPS reaches minimum under low light conditions."""


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
import lighting_control_utils
import opencv_processing_utils
import video_processing_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PREVIEW_RECORDING_DURATION_SECONDS = 10
_MAX_VAR_FRAME_DELTA = 0.001  # variance of frame deltas, units: seconds^2
_FPS_ATOL = 0.8
_DARKNESS_ATOL = 0.1 * 255  # openCV uses [0:255] images


class PreviewMinFrameRateTest(its_base_test.ItsBaseTest):
  """Tests preview frame rate under dark lighting conditions.

  Takes preview recording under dark conditions while setting
  CONTROL_AE_TARGET_FPS_RANGE, and checks that the
  recording's frame rate is at the minimum of the requested FPS range.
  """

  def test_preview_min_frame_rate(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID14_API_LEVEL)

      # determine acceptable ranges
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)
      ae_target_fps_range = camera_properties_utils.get_fps_range_to_test(
          fps_ranges)

      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      # Validate lighting
      cam.do_3a(do_af=False)
      cap = cam.do_capture(
          capture_request_utils.auto_capture_request(), cam.CAP_YUV)
      y_plane, _, _ = image_processing_utils.convert_capture_to_planes(cap)
      its_session_utils.validate_lighting(
          y_plane, self.scene, state='OFF', log_path=self.log_path)

      logging.debug('Taking preview recording in darkened scene.')
      # determine camera capabilities for preview
      preview_sizes = cam.get_supported_preview_sizes(
          self.camera_id)
      supported_video_sizes = cam.get_supported_video_sizes_capped(
          self.camera_id)
      max_video_size = supported_video_sizes[-1]  # largest available size
      logging.debug('Camera supported preview sizes: %s', preview_sizes)
      logging.debug('Camera supported video sizes: %s', supported_video_sizes)

      preview_size = preview_sizes[-1]  # choose largest available size
      if preview_size <= max_video_size:
        logging.debug('preview_size is supported by video encoder')
      else:
        preview_size = max_video_size
      logging.debug('Doing 3A to ensure AE convergence')
      cam.do_3a(do_af=False)
      logging.debug('Testing preview recording FPS for size: %s', preview_size)
      preview_recording_obj = cam.do_preview_recording(
          preview_size, _PREVIEW_RECORDING_DURATION_SECONDS, stabilize=False,
          zoom_ratio=None,
          ae_target_fps_min=ae_target_fps_range[0],
          ae_target_fps_max=ae_target_fps_range[1])
      logging.debug('preview_recording_obj: %s', preview_recording_obj)

      # turn lights back ON
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'ON')

      # pull the video recording file from the device.
      self.dut.adb.pull([preview_recording_obj['recordedOutputPath'],
                         self.log_path])
      logging.debug('Recorded preview video is available at: %s',
                    self.log_path)
      preview_file_name = preview_recording_obj[
          'recordedOutputPath'].split('/')[-1]
      logging.debug('preview_file_name: %s', preview_file_name)
      preview_file_name_with_path = os.path.join(
          self.log_path, preview_file_name)
      preview_frame_rate = video_processing_utils.get_average_frame_rate(
          preview_file_name_with_path)
      errors = []
      if not math.isclose(
          preview_frame_rate, ae_target_fps_range[0], abs_tol=_FPS_ATOL):
        errors.append(
            f'Preview frame rate was {preview_frame_rate:.3f}. '
            f'Expected to be {ae_target_fps_range[0]}, ATOL: {_FPS_ATOL}.'
        )
      frame_deltas = np.array(video_processing_utils.get_frame_deltas(
          preview_file_name_with_path))
      frame_delta_avg = np.average(frame_deltas)
      frame_delta_var = np.var(frame_deltas)
      logging.debug('Delta avg: %.4f, delta var: %.4f',
                    frame_delta_avg, frame_delta_var)
      if frame_delta_var > _MAX_VAR_FRAME_DELTA:
        errors.append(
            f'Preview frame delta variance {frame_delta_var:.3f} too large, '
            f'maximum allowed: {_MAX_VAR_FRAME_DELTA}.'
        )
      if errors:
        raise AssertionError('\n'.join(errors))

      last_key_frame = video_processing_utils.extract_key_frames_from_video(
          self.log_path, preview_file_name)[-1]
      logging.debug('Confirming video brightness in frame %s is low enough.',
                    last_key_frame)
      last_image = image_processing_utils.convert_image_to_numpy_array(
          os.path.join(self.log_path, last_key_frame))
      last_image_bgr = last_image[:, :, ::-1]
      y_avg = np.average(opencv_processing_utils.convert_to_y(last_image_bgr))
      logging.debug('Last frame y avg: %.4f', y_avg)
      if not math.isclose(y_avg, 0, abs_tol=_DARKNESS_ATOL):
        raise AssertionError(f'Last frame y average: {y_avg}, expected: 0, '
                             f'ATOL: {_DARKNESS_ATOL}')

if __name__ == '__main__':
  test_runner.main()
