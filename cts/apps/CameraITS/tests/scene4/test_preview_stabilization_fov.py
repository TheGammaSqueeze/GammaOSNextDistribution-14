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
"""Ensure that FoV reduction with Preview Stabilization is within spec."""

import logging
import math
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_fov_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import video_processing_utils

_VIDEO_DURATION = 3  # seconds

_MAX_STABILIZED_RADIUS_RATIO = 1.25  # An FOV reduction of 20% corresponds to an
                                     # increase in lengths of 25%. So the
                                     # stabilized circle's radius can be at most
                                     # 1.25 times that of an unstabilized circle
_MAX_STABILIZED_RADIUS_ATOL = 1  # 1 pixel tol for radii inaccuracy
_ROUNDESS_DELTA_THRESHOLD = 0.05

_MAX_CENTER_THRESHOLD_PERCENT = 0.075
_MAX_AREA = 1920 * 1440  # max mandatory preview stream resolution
_MIN_CENTER_THRESHOLD_PERCENT = 0.03
_MIN_AREA = 176 * 144  # assume QCIF to be min preview size
_KEY_FRAME_INDEX = -1  # last key frame
_STABILIZED_SCALER_CROP_RTOL = 0.2  # 20% relative tolerance


def _collect_data(cam, preview_size, stabilize):
  """Capture a preview video from the device.

  Captures camera preview frames from the passed device.

  Args:
    cam: camera object
    preview_size: str; preview resolution. ex. '1920x1080'
    stabilize: boolean; whether the preview should be stabilized or not

  Returns:
    recording object as described by cam.do_preview_recording
  """

  recording_obj = cam.do_preview_recording(preview_size, _VIDEO_DURATION,
                                           stabilize)
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  return recording_obj


def _point_distance(p1_x, p1_y, p2_x, p2_y):
  """Calculates the euclidean distance between two points.

  Args:
    p1_x: x coordinate of the first point
    p1_y: y coordinate of the first point
    p2_x: x coordinate of the second point
    p2_y: y coordinate of the second point

  Returns:
    Euclidean distance between two points
  """
  return math.sqrt(pow(p1_x - p2_x, 2) + pow(p1_y - p2_y, 2))


def _calculate_center_offset_threshold(image_size):
  """Calculates appropriate center offset threshold.

  This function calculates a viable threshold that centers of two circles can be
  offset by for a given image size. The threshold percent is linearly
  interpolated between _MIN_CENTER_THRESHOLD_PERCENT and
  _MAX_CENTER_THRESHOLD_PERCENT according to the image size passed.

  Args:
    image_size: pair; size of the image for which threshold has to be
                calculated. ex. (1920, 1080)

  Returns:
    threshold value ratio between which the circle centers can differ
  """

  img_area = image_size[0] * image_size[1]

  normalized_area = (img_area - _MIN_AREA) / (_MAX_AREA - _MIN_AREA)

  if normalized_area > 1 or normalized_area < 0:
    raise AssertionError(f'normalized area > 1 or < 0! '
                         f'image_size[0]: {image_size[0]}, '
                         f'image_size[1]: {image_size[1]}, '
                         f'normalized_area: {normalized_area}')

  # Threshold should be larger for images with smaller resolution
  normalized_threshold_percent = ((1 - normalized_area) *
                                  (_MAX_CENTER_THRESHOLD_PERCENT -
                                   _MIN_CENTER_THRESHOLD_PERCENT))

  return normalized_threshold_percent + _MIN_CENTER_THRESHOLD_PERCENT


class PreviewStabilizationFoVTest(its_base_test.ItsBaseTest):
  """Tests if stabilized preview FoV is within spec.

  The test captures two videos, one with preview stabilization on, and another
  with preview stabilization off. A representative frame is selected from each
  video, and analyzed to ensure that the FoV changes in the two videos are
  within spec.

  Specifically, the test checks for the following parameters with and without
  preview stabilization:
    - The circle roundness remains about constant
    - The center of the circle remains relatively stable
    - The size of circle changes no more that 20% i.e. the FOV changes at most
      20%
  """

  def test_preview_stabilization_fov(self):
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load scene.
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)

      # Check skip condition
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL,
          'First API level should be {} or higher. Found {}.'.format(
              its_session_utils.ANDROID13_API_LEVEL, first_api_level))

      # Log ffmpeg version being used
      video_processing_utils.log_ffmpeg_version()

      supported_stabilization_modes = props[
          'android.control.availableVideoStabilizationModes'
      ]

      camera_properties_utils.skip_unless(
          supported_stabilization_modes is not None
          and camera_properties_utils.STABILIZATION_MODE_PREVIEW
          in supported_stabilization_modes,
          'Preview Stabilization not supported',
      )

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # List of preview resolutions to test
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      for size in video_processing_utils.LOW_RESOLUTION_SIZES:
        if size in supported_preview_sizes:
          supported_preview_sizes.remove(size)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)

      test_failures = []

      for preview_size in supported_preview_sizes:

        # recording with stabilization off
        ustab_rec_obj = _collect_data(cam, preview_size, False)
        # recording with stabilization on
        stab_rec_obj = _collect_data(cam, preview_size, True)

        # Grab the unstabilized video from DUT
        self.dut.adb.pull([ustab_rec_obj['recordedOutputPath'], log_path])
        ustab_file_name = (ustab_rec_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('ustab_file_name: %s', ustab_file_name)

        # Grab the stabilized video from DUT
        self.dut.adb.pull([stab_rec_obj['recordedOutputPath'], log_path])
        stab_file_name = (stab_rec_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('stab_file_name: %s', stab_file_name)

        # Get all frames from the videos
        ustab_file_list = video_processing_utils.extract_key_frames_from_video(
            log_path, ustab_file_name)
        logging.debug('Number of unstabilized iframes %d', len(ustab_file_list))

        stab_file_list = video_processing_utils.extract_key_frames_from_video(
            log_path, stab_file_name)
        logging.debug('Number of stabilized iframes %d', len(stab_file_list))

        # Extract last key frame to test from each video
        ustab_frame = os.path.join(log_path,
                                   video_processing_utils
                                   .get_key_frame_to_process(ustab_file_list))
        logging.debug('unstabilized frame: %s', ustab_frame)
        stab_frame = os.path.join(log_path,
                                  video_processing_utils
                                  .get_key_frame_to_process(stab_file_list))
        logging.debug('stabilized frame: %s', stab_frame)

        # Convert to numpy matrix for analysis
        ustab_np_image = image_processing_utils.convert_image_to_numpy_array(
            ustab_frame)
        logging.debug('unstabilized frame size: %s', ustab_np_image.shape)
        stab_np_image = image_processing_utils.convert_image_to_numpy_array(
            stab_frame)
        logging.debug('stabilized frame size: %s', stab_np_image.shape)

        image_size = stab_np_image.shape

        # Get circles to compare
        ustab_circle = opencv_processing_utils.find_circle(
            ustab_np_image,
            ustab_frame,
            image_fov_utils.CIRCLE_MIN_AREA,
            image_fov_utils.CIRCLE_COLOR)

        stab_circle = opencv_processing_utils.find_circle(
            stab_np_image,
            stab_frame,
            image_fov_utils.CIRCLE_MIN_AREA,
            image_fov_utils.CIRCLE_COLOR)

        failure_string = ''

        # Ensure the circles are equally round w/ and w/o stabilization
        ustab_roundness = ustab_circle['w'] / ustab_circle['h']
        logging.debug('unstabilized roundness: %f', ustab_roundness)
        stab_roundness = stab_circle['w'] / stab_circle['h']
        logging.debug('stabilized roundness: %f', stab_roundness)

        roundness_diff = abs(stab_roundness - ustab_roundness)
        if roundness_diff > _ROUNDESS_DELTA_THRESHOLD:
          failure_string += (f'Circle roundness changed too much: '
                             f'unstabilized ratio: {ustab_roundness}, '
                             f'stabilized ratio: {stab_roundness}, '
                             f'Expected ratio difference <= '
                             f'{_ROUNDESS_DELTA_THRESHOLD}, '
                             f'actual ratio difference: {roundness_diff}. ')

        # Distance between centers, x_offset and y_offset are relative to the
        # radius of the circle, so they're normalized. Not pixel values.
        unstab_center = (ustab_circle['x_offset'], ustab_circle['y_offset'])
        logging.debug('unstabilized center: %s', unstab_center)
        stab_center = (stab_circle['x_offset'], stab_circle['y_offset'])
        logging.debug('stabilized center: %s', stab_center)

        dist_centers = _point_distance(unstab_center[0], unstab_center[1],
                                       stab_center[0], stab_center[1])
        center_offset_threshold = _calculate_center_offset_threshold(image_size)
        if dist_centers > center_offset_threshold:
          failure_string += (f'Circle moved too much: '
                             f'unstabilized center: ('
                             f'{unstab_center[0]}, {unstab_center[1]}), '
                             f'stabilized center: '
                             f'({stab_center[0]}, {stab_center[1]}), '
                             f'expected distance < {center_offset_threshold}, '
                             f'actual_distance {dist_centers}. ')

        # ensure radius of stabilized frame is within 120% of radius within
        # unstabilized frame
        ustab_radius = ustab_circle['r']
        logging.debug('unstabilized radius: %f', ustab_radius)
        stab_radius = stab_circle['r']
        logging.debug('stabilized radius: %f', stab_radius)

        max_stab_radius = (ustab_radius * _MAX_STABILIZED_RADIUS_RATIO +
                           _MAX_STABILIZED_RADIUS_ATOL)
        if stab_radius > max_stab_radius:
          failure_string += (f'Too much FoV reduction: '
                             f'unstabilized radius: {ustab_radius}, '
                             f'stabilized radius: {stab_radius}, '
                             f'expected max stabilized radius: '
                             f'{max_stab_radius}. ')

        # Calculate ratio of stabilized image's scaler crop region over
        # active array size and compare it against the ratio of stabilized
        # circle's radius over unstabilized circle
        if stab_radius > ustab_radius:
          stab_scaler_crop = (stab_rec_obj['captureMetadata']
                              [_KEY_FRAME_INDEX]['android.scaler.cropRegion'])
          scaler_crop_ratio = image_fov_utils.calc_scaler_crop_region_ratio(
              stab_scaler_crop, props)
          radius_ratio = ustab_radius / stab_radius
          if math.isclose(scaler_crop_ratio, radius_ratio,
                          rel_tol=_STABILIZED_SCALER_CROP_RTOL):
            logging.debug('Crop region/active array: %f', scaler_crop_ratio)
            logging.debug('Stabilized/unstabilized circle: %f', radius_ratio)
            continue
          else:
            failure_string += (f'Too much FoV reduction: '
                               f'Crop region: {stab_scaler_crop}, '
                               f'Crop region ratio: {scaler_crop_ratio:.2%}, '
                               f'Circle ratio: {radius_ratio:.2%}, '
                               f'Tolerance: {_STABILIZED_SCALER_CROP_RTOL:.2%}')

        if failure_string:
          failure_string = f'{preview_size} fails FoV test. ' + failure_string
          test_failures.append(failure_string)

      if test_failures:
        raise AssertionError(test_failures)


if __name__ == '__main__':
  test_runner.main()

