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
"""Verify preview matches video output during video zoom."""

import logging
import math
import os

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import video_processing_utils
import zoom_capture_utils

_CIRCLE_AR_RTOL = 0.15  # contour width vs height (aspect ratio)
_CIRCLE_COLOR = 0  # [0: black, 255: white]
_CIRCLE_R = 2
_CIRCLE_X = 0
_CIRCLE_Y = 1
_CIRCLISH_RTOL = 0.15  # contour area vs ideal circle area pi*((w+h)/4)**2
_JPEG_STR = 'jpg'
_LINE_COLOR = (255, 0, 0)  # red
_MAX_STR = 'max'
_MIN_STR = 'min'
_MIN_AREA_RATIO = 0.00015  # based on 2000/(4000x3000) pixels
_MIN_CIRCLE_PTS = 10
_MIN_SIZE = 1280*720  # 720P
_MIN_ZOOM_SCALE_CHART = 0.70  # zoom factor to trigger scaled chart
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_OFFSET_TOL = 5  # pixels
_RADIUS_RTOL = 0.1  # 10% tolerance Video/Preview circle size
_RECORDING_DURATION = 2  # seconds
_ZOOM_COMP_MAX_THRESH = 1.15
_ZOOM_MIN_THRESH = 2.0
_ZOOM_RATIO = 2


def _extract_key_frame_from_recording(log_path, file_name):
  """Extract key frames from recordings.

  Args:
    log_path: str; file location
    file_name: str file name for saved video

  Returns:
    dictionary of images
  """
  key_frame_files = []
  key_frame_files = (
      video_processing_utils.extract_key_frames_from_video(
          log_path, file_name)
  )
  logging.debug('key_frame_files: %s', key_frame_files)

  # Get the key frame file to process.
  last_key_frame_file = (
      video_processing_utils.get_key_frame_to_process(
          key_frame_files)
  )
  logging.debug('last_key_frame: %s', last_key_frame_file)
  last_key_frame_path = os.path.join(log_path, last_key_frame_file)

  # Convert lastKeyFrame to numpy array
  np_image = image_processing_utils.convert_image_to_numpy_array(
      last_key_frame_path)
  logging.debug('numpy image shape: %s', np_image.shape)

  return np_image


class PreviewVideoZoomMatchTest(its_base_test.ItsBaseTest):
  """Tests if preview matches video output when zooming.

  Preview and video are recorded while do_3a() iterate through
  different cameras with minimal zoom to zoom factor 1.5x.

  The recorded preview and video output are processed to dump all
  of the frames to PNG files. Camera movement in zoom is extracted
  from frames by determining if the size of the circle being recorded
  increases as zoom factor increases. Test is a PASS if both recordings
  match in zoom factors.
  """

  def test_preview_video_zoom_match(self):
    video_test_data = {}
    preview_test_data = {}
    log_path = self.log_path
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      debug = self.debug_mode

      def _do_preview_recording(cam, resolution, zoom_ratio):
        """Record a new set of data from the device.

        Captures camera preview frames while the camera is zooming.

        Args:
          cam: camera object
          resolution: str; preview resolution (ex. '1920x1080')
          zoom_ratio: float; zoom ratio

        Returns:
          preview recording object as described by cam.do_basic_recording
        """

        # Record previews
        preview_recording_obj = cam.do_preview_recording(
            resolution, _RECORDING_DURATION, False, zoom_ratio=zoom_ratio)
        logging.debug('Preview_recording_obj: %s', preview_recording_obj)
        logging.debug('Recorded output path for preview: %s',
                      preview_recording_obj['recordedOutputPath'])

        # Grab and rename the preview recordings from the save location on DUT
        self.dut.adb.pull(
            [preview_recording_obj['recordedOutputPath'], log_path])
        preview_file_name = (
            preview_recording_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('recorded preview name: %s', preview_file_name)

        return preview_file_name

      def _do_video_recording(cam, profile_id, quality, zoom_ratio):
        """Record a new set of data from the device.

        Captures camera video frames while the camera is zooming per zoom_ratio.

        Args:
          cam: camera object
          profile_id: int; profile id corresponding to the quality level
          quality: str; video recording quality such as High, Low, 480P
          zoom_ratio: float; zoom ratio.

        Returns:
          video recording object as described by cam.do_basic_recording
        """

        # Record videos
        video_recording_obj = cam.do_basic_recording(
            profile_id, quality, _RECORDING_DURATION, 0, zoom_ratio=zoom_ratio)
        logging.debug('Video_recording_obj: %s', video_recording_obj)
        logging.debug('Recorded output path for video: %s',
                      video_recording_obj['recordedOutputPath'])

        # Grab and rename the video recordings from the save location on DUT
        self.dut.adb.pull(
            [video_recording_obj['recordedOutputPath'], log_path])
        video_file_name = (
            video_recording_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('recorded video name: %s', video_file_name)

        return video_file_name

      # Find zoom range
      z_range = props['android.control.zoomRatioRange']

      # Skip unless camera has zoom ability
      first_api_level = its_session_utils.get_first_api_level(
          self.dut.serial)
      camera_properties_utils.skip_unless(
          z_range and first_api_level >= its_session_utils.ANDROID14_API_LEVEL
      )
      logging.debug('Testing zoomRatioRange: %s', str(z_range))

      # Determine zoom factors
      z_min = z_range[0]
      camera_properties_utils.skip_unless(
          float(z_range[-1]) >= z_min * _ZOOM_MIN_THRESH)
      zoom_ratios_to_be_tested = [z_min]
      if z_min < 1.0:
        zoom_ratios_to_be_tested.append(float(_ZOOM_RATIO))
      else:
        zoom_ratios_to_be_tested.append(float(z_min * 2))
      logging.debug('Testing zoom ratios: %s', str(zoom_ratios_to_be_tested))

      # Load chart for scene
      if z_min > _MIN_ZOOM_SCALE_CHART:
        its_session_utils.load_scene(
            cam, props, self.scene, self.tablet, self.chart_distance)
      else:  # Load full-scale chart for small zoom factor
        its_session_utils.load_scene(
            cam, props, self.scene, self.tablet,
            its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Find supported preview/video sizes, and their smallest and common size
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('supported_preview_sizes: %s', supported_preview_sizes)
      supported_video_qualities = cam.get_supported_video_qualities(
          self.camera_id)
      logging.debug(
          'Supported video profiles and ID: %s', supported_video_qualities)
      common_size, common_video_quality = (
          video_processing_utils.get_lowest_preview_video_size(
              supported_preview_sizes, supported_video_qualities, _MIN_SIZE))

      # Start video recording over minZoom and 2x Zoom
      for quality_profile_id_pair in supported_video_qualities:
        quality = quality_profile_id_pair.split(':')[0]
        profile_id = quality_profile_id_pair.split(':')[-1]
        if quality == common_video_quality:
          for i, z in enumerate(zoom_ratios_to_be_tested):
            logging.debug('Testing video recording for quality: %s', quality)
            req = capture_request_utils.auto_capture_request()
            req['android.control.zoomRatio'] = z
            cam.do_3a(zoom_ratio=z)
            logging.debug('Zoom ratio: %.2f', z)

            # Determine focal length of camera through capture
            cap = cam.do_capture(
                req, {'format': 'yuv'})
            cap_fl = cap['metadata']['android.lens.focalLength']
            logging.debug('Camera focal length: %.2f', cap_fl)

            # Determine width and height of video
            size = common_size.split('x')
            width = int(size[0])
            height = int(size[1])

            # Start video recording
            video_file_name = _do_video_recording(
                cam, profile_id, quality, zoom_ratio=z)

            # Get key frames from the video recording
            video_img = (
                video_processing_utils.extract_last_key_frame_from_recording(
                    log_path, video_file_name))

            # Find the center circle in video img
            img_name_stem = os.path.join(log_path, 'video_zoomRatio')
            video_img_name = (
                f'{img_name_stem}_{z:.2f}_{quality}_circle.png')
            circle = zoom_capture_utils.find_center_circle(
                video_img, video_img_name, [width, height],
                z, z_min, min_circle_pts=_MIN_CIRCLE_PTS, debug=debug)
            logging.debug('Recorded video name: %s', video_file_name)

            video_test_data[i] = {'z': z, 'circle': circle}

      # Start preview recording over minZoom and maxZoom
      for size in supported_preview_sizes:
        if size == common_size:
          for i, z in enumerate(zoom_ratios_to_be_tested):
            cam.do_3a(zoom_ratio=z)
            preview_file_name = _do_preview_recording(
                cam, size, zoom_ratio=z)

            # Define width and height from size
            width = int(size.split('x')[0])
            height = int(size.split('x')[1])

            # Get key frames from the preview recording
            preview_img = (
                video_processing_utils.extract_last_key_frame_from_recording(
                    log_path, preview_file_name))

            # If testing front camera, mirror preview image
            # Opencv expects a numpy array but np.flip generates a 'view' which
            # doesn't work with opencv. ndarray.copy forces copy instead of view
            if (props['android.lens.facing'] ==
                camera_properties_utils.LENS_FACING['FRONT']):
              # Preview are flipped on device's natural orientation
              # so for sensor orientation 90 or 270, it is up or down
              # Sensor orientation 0 or 180 is left or right
              img_name_stem = os.path.join(log_path, 'flipped_preview')
              img_name = f'{img_name_stem}_zoomRatio_{z:.2f}.{_JPEG_STR}'
              if props['android.sensor.orientation'] in (90, 270):
                preview_img = np.ndarray.copy(np.flipud(preview_img))
                logging.debug(
                    'Found sensor orientation %d, flipping up down',
                    props['android.sensor.orientation'])
              else:
                preview_img = np.ndarray.copy(np.fliplr(preview_img))
                logging.debug(
                    'Found sensor orientation %d, flipping left right',
                    props['android.sensor.orientation'])
              image_processing_utils.write_image(preview_img / 255, img_name)
            else:
              img_name_stem = os.path.join(log_path, 'rear_preview')

            # Find the center circle in preview img
            preview_img_name = (
                f'{img_name_stem}_zoomRatio_{z:.2f}_{size}_circle.png')
            circle = zoom_capture_utils.find_center_circle(
                preview_img, preview_img_name, [width, height],
                z, z_min, min_circle_pts=_MIN_CIRCLE_PTS, debug=debug)

            preview_test_data[i] = {'z': z, 'circle': circle}

      # compare size and center of preview's circle to video's circle
      preview_radius = {}
      video_radius = {}
      z_idx = {}
      zoom_factor = {}
      preview_radius[_MIN_STR] = (preview_test_data[0]['circle'][_CIRCLE_R])
      video_radius[_MIN_STR] = (video_test_data[0]['circle'][_CIRCLE_R])
      preview_radius[_MAX_STR] = (preview_test_data[1]['circle'][_CIRCLE_R])
      video_radius[_MAX_STR] = (video_test_data[1]['circle'][_CIRCLE_R])
      z_idx[_MIN_STR] = (
          preview_radius[_MIN_STR] / video_radius[_MIN_STR])
      z_idx[_MAX_STR] = (
          preview_radius[_MAX_STR] / video_radius[_MAX_STR])
      z_comparison = z_idx[_MAX_STR] / z_idx[_MIN_STR]
      zoom_factor[_MIN_STR] = preview_test_data[0]['z']
      zoom_factor[_MAX_STR] = preview_test_data[1]['z']

      # compare preview circle's center with video circle's center
      preview_circle_x = preview_test_data[1]['circle'][_CIRCLE_X]
      video_circle_x = video_test_data[1]['circle'][_CIRCLE_X]
      preview_circle_y = preview_test_data[1]['circle'][_CIRCLE_Y]
      video_circle_y = video_test_data[1]['circle'][_CIRCLE_Y]
      circles_offset_x = math.isclose(preview_circle_x, video_circle_x,
                                      abs_tol=_OFFSET_TOL)
      circles_offset_y = math.isclose(preview_circle_y, video_circle_y,
                                      abs_tol=_OFFSET_TOL)
      logging.debug('Preview circle x: %.2f, Video circle x: %.2f'
                    ' Preview circle y: %.2f, Video circle y: %.2f',
                    preview_circle_x, video_circle_x,
                    preview_circle_y, video_circle_y)
      logging.debug('Preview circle r: %.2f, Preview circle r zoom: %.2f'
                    ' Video circle r: %.2f, Video circle r zoom: %.2f'
                    ' centers offset x: %s, centers offset y: %s',
                    preview_radius[_MIN_STR], preview_radius[_MAX_STR],
                    video_radius[_MIN_STR], video_radius[_MAX_STR],
                    circles_offset_x, circles_offset_y)
      if not circles_offset_x or not circles_offset_y:
        raise AssertionError('Preview and video output do not match! '
                             'Preview and video circles offset is too great')

      # check zoom ratio by size of circles before and after zoom
      for radius_ratio in z_idx.values():
        if not math.isclose(radius_ratio, 1, rel_tol=_RADIUS_RTOL):
          raise AssertionError('Preview and video output do not match! '
                               f'Radius ratio: {radius_ratio:.2f}')

      if z_comparison > _ZOOM_COMP_MAX_THRESH:
        raise AssertionError('Preview and video output do not match! '
                             f'Zoom ratio difference: {z_comparison:.2f}')

if __name__ == '__main__':
  test_runner.main()

