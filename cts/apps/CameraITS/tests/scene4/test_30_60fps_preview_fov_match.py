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
"""Verify 30FPS and 60FPS preview videos have the same FoV."""


import logging
import math
import os

from mobly import test_runner

import camera_properties_utils
import image_fov_utils
import image_processing_utils
import its_base_test
import its_session_utils
import opencv_processing_utils
import video_processing_utils

_ASPECT_RATIO_ATOL = 0.075
_HEIGHT = 'h'
_FPS_ATOL = 0.5

_MAX_AREA = 1920 * 1440  # max mandatory preview stream resolution
_MAX_CENTER_THRESHOLD_PERCENT = 0.075
_MIN_AREA = 640 * 480  # assume VGA to be min preview size
_MIN_CENTER_THRESHOLD_PERCENT = 0.03

_RADIUS = 'r'
_RADIUS_RTOL = 0.04  # 4 percent

_RECORDING_DURATION = 2  # seconds
_WIDTH = 'w'
_X_OFFSET = 'x_offset'
_Y_OFFSET = 'y_offset'


def _calculate_center_offset_threshold(img_np_array):
  """Calculates appropriate center offset threshold.

  This function calculates a viable threshold that centers of two circles can be
  offset by for a given image size. The threshold percent is linearly
  interpolated between _MIN_CENTER_THRESHOLD_PERCENT and
  _MAX_CENTER_THRESHOLD_PERCENT according to the image size passed.

  Args:
    img_np_array: tuples; size of the image for which threshold has to be
                calculated. ex. (1080, 1920, 3)

  Returns:
    threshold value ratio between which the circle centers can differ
  """

  img_area = img_np_array[0] * img_np_array[1]

  normalized_area = (img_area - _MIN_AREA) / (_MAX_AREA - _MIN_AREA)

  if normalized_area > 1 or normalized_area < 0:
    raise AssertionError('normalized area > 1 or < 0! '
                         f'image_area: {img_area}, '
                         f'normalized_area: {normalized_area}')

  # Threshold should be larger for images with smaller resolution
  normalized_threshold_percent = (
      (1 - normalized_area) * (_MAX_CENTER_THRESHOLD_PERCENT -
                               _MIN_CENTER_THRESHOLD_PERCENT))

  return normalized_threshold_percent + _MIN_CENTER_THRESHOLD_PERCENT


class ThirtySixtyFpsPreviewFoVMatchTest(its_base_test.ItsBaseTest):
  """Tests if preview FoV is within spec.

  The test captures two videos, one with 30 fps and another with 60 fps.
  A representative frame is selected from each video, and analyzed to
  ensure that the FoV changes in the two videos are within spec.

  Specifically, the test checks for the following parameters with and without
  preview stabilization:
    - The circle's aspect ratio remains constant
    - The center of the circle remains stable
    - The radius of circle remains constant
  """

  def test_30_60fps_preview_fov_match(self):
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      def _do_preview_recording(cam, resolution, stabilize, fps):
        """Record a new set of data from the device.

        Captures camera preview frames.

        Args:
          cam: camera object
          resolution: str; preview resolution (ex. '1920x1080')
          stabilize: bool; True or False
          fps: integer; frames per second capture rate

        Returns:
          preview file name
        """

        # Record stabilized and unstabilized previews
        preview_recording_obj = cam.do_preview_recording(
            resolution, _RECORDING_DURATION, stabilize=stabilize,
            ae_target_fps_min=fps, ae_target_fps_max=fps)
        logging.debug('Preview_recording_obj: %s', preview_recording_obj)
        logging.debug('Recorded output path for preview: %s',
                      preview_recording_obj['recordedOutputPath'])

        # Grab and rename the preview recordings from the save location on DUT
        self.dut.adb.pull(
            [preview_recording_obj['recordedOutputPath'], log_path])
        preview_file_name = (
            preview_recording_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('recorded %s preview name: %s', fps, preview_file_name)

        # Validate preview frame rate
        preview_file_name_with_path = os.path.join(
            self.log_path, preview_file_name)
        preview_frame_rate = video_processing_utils.get_average_frame_rate(
            preview_file_name_with_path)
        if not math.isclose(preview_frame_rate, fps, abs_tol=_FPS_ATOL):
          logging.warning(
              'Preview frame rate: %.1f, expected: %1.f, ATOL: %.2f',
              preview_frame_rate, fps, _FPS_ATOL)

        return preview_file_name

      # Load scene
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)

      # Check skip condition
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)
      camera_properties_utils.skip_unless(
          [30, 30] and [60, 60] in fps_ranges and
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL)

      # Log ffmpeg version being used
      video_processing_utils.log_ffmpeg_version()

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # List preview resolutions and find 720P or above to test
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)
      preview_size = video_processing_utils.get_720p_or_above_size(
          supported_preview_sizes)
      logging.debug('Testing preview resolution: %s', preview_size)

      # Recording preview streams 30/60 fps with stabilization off
      fps30_video = _do_preview_recording(
          cam, preview_size, stabilize=False, fps=30)
      fps60_video = _do_preview_recording(
          cam, preview_size, stabilize=False, fps=60)

      # Get last key frame from the 30/60 fps video with stabilization off
      fps30_frame = (
          video_processing_utils.extract_last_key_frame_from_recording(
              log_path, fps30_video))
      fps60_frame = (
          video_processing_utils.extract_last_key_frame_from_recording(
              log_path, fps60_video))

      # Compare 30/60 fps circles with stabilization off
      key_frame_name_stem = f'preview_{preview_size}_key_frame.png'
      fps30_key_frame_name = 'fps30_' + key_frame_name_stem
      fps30_circle = opencv_processing_utils.find_circle(
          fps30_frame, fps30_key_frame_name,
          image_fov_utils.CIRCLE_MIN_AREA, image_fov_utils.CIRCLE_COLOR)
      fps60_key_frame_name = 'fps60_' + key_frame_name_stem
      fps60_circle = opencv_processing_utils.find_circle(
          fps60_frame, fps60_key_frame_name,
          image_fov_utils.CIRCLE_MIN_AREA, image_fov_utils.CIRCLE_COLOR)

      # Ensure the circles have the same aspect ratio in 30/60 fps recordings
      fps30_aspect_ratio = (
          fps30_circle[_WIDTH] / fps30_circle[_HEIGHT])
      logging.debug('fps30 aspect ratio: %f', fps30_aspect_ratio)
      fps60_aspect_ratio = (
          fps60_circle[_WIDTH] / fps60_circle[_HEIGHT])
      logging.debug('fps60 aspect ratio: %f', fps60_aspect_ratio)

      # Identifying failure
      fail_msg = []
      if not math.isclose(fps30_aspect_ratio, fps60_aspect_ratio,
                          abs_tol=_ASPECT_RATIO_ATOL):
        fail_msg.append('Circle aspect_ratio changed too much: '
                        f'fps30 ratio: {fps30_aspect_ratio}, '
                        f'fps60 ratio: {fps60_aspect_ratio}, '
                        f'RTOL <= {_ASPECT_RATIO_ATOL}. ')

      # Distance between centers, x_offset and y_offset are relative to the
      # radius of the circle, so they're normalized. Not pixel values.
      fps30_center = (
          fps30_circle[_X_OFFSET], fps30_circle[_Y_OFFSET])
      logging.debug('fps30 center: %s', fps30_center)
      fps60_center = (
          fps60_circle[_X_OFFSET], fps60_circle[_Y_OFFSET])
      logging.debug('fps60 center: %s', fps60_center)

      center_offset = image_processing_utils.distance(
          fps30_center, fps60_center)
      img_np_array = fps30_frame.shape
      center_offset_threshold = (
          _calculate_center_offset_threshold(img_np_array))
      if center_offset > center_offset_threshold:
        fail_msg.append('Circle moved too much: fps30 center: '
                        f'{fps30_center}, '
                        f'fps60 center: {fps60_center}, '
                        f'expected distance < {center_offset_threshold}, '
                        f'actual_distance: {center_offset}. ')

        raise AssertionError(fail_msg)
      fps30_radius = fps30_circle[_RADIUS]
      fps60_radius = fps60_circle[_RADIUS]
      if not math.isclose(
          fps30_radius, fps60_radius, rel_tol=_RADIUS_RTOL):
        fail_msg.append('Too much FoV change: '
                        f'fps30 radius: {fps30_radius}, '
                        f'fps60 radius: {fps60_radius}, '
                        f'RTOL: {_RADIUS_RTOL}. ')
        raise AssertionError(fail_msg)

if __name__ == '__main__':
  test_runner.main()
