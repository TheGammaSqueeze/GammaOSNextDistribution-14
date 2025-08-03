# Copyright 2024 The Android Open Source Project
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
"""Verify preview zoom ratio scales circle sizes correctly."""

import logging
import os.path

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import video_processing_utils
import zoom_capture_utils


_CIRCLISH_RTOL = 0.05  # contour area vs ideal circle area pi*((w+h)/4)**2
_IMG_FORMAT = 'png'
_MAX_ZOOM_TOL = 0.1   # add Zoom tolarance to enable capture at max zoom
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10
_SKIP_INITIAL_FRAMES = 15
_VIDEO_DURATION = 400  # milliseconds
_ZOOM_MIN_THRESH = 2.0


def _collect_data(cam, preview_size, zoom_start, zoom_end, step_size):
  """Capture a preview video from the device.

  Captures camera preview frames from the passed device.

  Args:
    cam: camera object
    preview_size: str; preview resolution. ex. '1920x1080'
    zoom_start: (float) is the starting zoom ratio during recording
    zoom_end: (float) is the ending zoom ratio during recording
    step_size: (float) is the step for zoom ratio during recording

  Returns:
    recording object as described by cam.do_preview_recording_with_dynamic_zoom
  """

  recording_obj = cam.do_preview_recording_with_dynamic_zoom(
      preview_size,
      stabilize=False,
      sweep_zoom=(zoom_start, zoom_end, step_size, _VIDEO_DURATION)
  )
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  return recording_obj


class PreviewZoomTest(its_base_test.ItsBaseTest):
  """Verify zoom ratio of preview frames matches values in TotalCaptureResult."""

  def test_preview_zoom(self):
    log_path = self.log_path
    video_processing_utils.log_ffmpeg_version()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      debug = self.debug_mode

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Load scene
      its_session_utils.load_scene(cam, props, self.scene, self.tablet,
                                   its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # List of preview resolutions to test
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      for size in video_processing_utils.LOW_RESOLUTION_SIZES:
        if size in supported_preview_sizes:
          supported_preview_sizes.remove(size)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)

      # Determine test zoom range
      z_range = props['android.control.zoomRatioRange']
      logging.debug('z_range = %s', str(z_range))
      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(z_max >= z_min * _ZOOM_MIN_THRESH)
      z_max = min(z_max, zoom_capture_utils.ZOOM_MAX_THRESH * z_min)
      z_step_size = (z_max-z_min) / (_NUM_STEPS-1)
      logging.debug('zoomRatioRange = %s z_min = %f z_max = %f z_stepSize = %f',
                    str(z_range), z_min, z_max, z_step_size)

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, _ = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)

      # Converge 3A
      cam.do_3a()

      # get max preview size
      preview_size = supported_preview_sizes[-1]
      size = [int(x) for x in preview_size.split('x')]
      logging.debug('preview_size = %s', str(preview_size))
      logging.debug('size = %s', str(size))

      # recording preview
      preview_rec_obj = _collect_data(cam, preview_size,
                                      z_min, z_max + _MAX_ZOOM_TOL, z_step_size)

      # Grab the recording from DUT
      self.dut.adb.pull([preview_rec_obj['recordedOutputPath'], log_path])
      preview_file_name = (
          preview_rec_obj['recordedOutputPath'].split('/')[-1])
      logging.debug('preview_file_name: %s', preview_file_name)
      logging.debug('recorded video size : %s',
                    str(preview_rec_obj['videoSize']))

      # Extract frames as png from mp4 preview recording
      file_list = video_processing_utils.extract_all_frames_from_video(
          log_path, preview_file_name, _IMG_FORMAT
      )

      # Raise error if capture result and frame count doesn't match.
      capture_results = preview_rec_obj['captureMetadata']
      extra_capture_result_count = len(capture_results) - len(file_list)
      logging.debug('Number of frames %d', len(file_list))
      if extra_capture_result_count != 0:
        e_msg = (f'Number of CaptureResult ({len(capture_results)}) '
                 f'vs number of Frames ({len(file_list)}) count mismatch.'
                 ' Retry Test.')
        raise AssertionError(e_msg)

      # skip frames which might not have 3A converged
      capture_results = capture_results[_SKIP_INITIAL_FRAMES:]
      file_list = file_list[_SKIP_INITIAL_FRAMES:]

      test_data = {}
      test_data_index = 0

      for capture_result, img_name in zip(capture_results, file_list):
        z = float(capture_result['android.control.zoomRatio'])

        # read image
        img = image_processing_utils.convert_image_to_numpy_array(
            os.path.join(log_path, img_name)
            )

        # add path to image name
        img_name = f'{os.path.join(self.log_path, img_name)}'

        # determine radius tolerance of capture
        cap_fl = capture_result['android.lens.focalLength']
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
            img, img_name, size, z, z_min, circlish_rtol=circlish_rtol,
            debug=debug)

        # Zoom is too large to find center circle
        if circle is None:
          logging.error('Unable to detect circle in %s', img_name)
          break

        test_data[test_data_index] = {'z': z, 'circle': circle,
                                      'r_tol': radius_tol, 'o_tol': offset_tol,
                                      'fl': cap_fl}

        logging.debug('test_data[%d] = %s', test_data_index,
                      str(test_data[test_data_index]))
        test_data_index = test_data_index + 1

      if not zoom_capture_utils.verify_zoom_results(
          test_data, size, z_max, z_min):
        raise AssertionError(f'{_NAME} failed! Check test_log.DEBUG for errors')

if __name__ == '__main__':
  test_runner.main()
