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
"""Verify that the switch from UW to W has similar RGB values."""

import glob
import logging
import os.path
import pathlib

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import video_processing_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]
_ZOOM_SEARCH_DEPTH = 12
_ZOOM_RATIO_ROUND_VAL = 3
_ZOOM_STEP = 0.01
_ZOOM_RANGE_UW_W = (0.95, 2.05)  # UW/W crossover range
_CAP_FMT = [{'format': 'yuv', 'width': 640, 'height': 480}]
_RECORDING_DURATION = 400  # milliseconds
_SKIP_INITIAL_FRAMES = 15
_IMG_FORMAT = 'png'
_HIGH_RES_SIZE = '3840x2160'  # Resolution for 4K quality
_HIGH_RES_QUALITY_STR = '4K'


def _get_preview_test_size(cam, camera_id):
  """Finds preview size to be tested.

  Args:
    cam: camera object
    camera_id: str; camera device id under test

  Returns:
    preview_test_size: str; wxh resolution of the size to be tested
  """
  supported_preview_sizes = cam.get_supported_preview_sizes(camera_id)
  logging.debug('supported_preview_sizes: %s', supported_preview_sizes)
  supported_video_qualities = cam.get_supported_video_qualities(camera_id)
  logging.debug('Supported video profiles and ID: %s',
                supported_video_qualities)
  if _HIGH_RES_QUALITY_STR not in supported_video_qualities:
    preview_test_size = supported_preview_sizes[-1]
  else:
    preview_test_size = _HIGH_RES_SIZE
  return preview_test_size


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
      sweep_zoom=(zoom_start, zoom_end, step_size, _RECORDING_DURATION)
  )
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  return recording_obj


def _remove_frame_files(dir_name, save_files_list):
  """Removes the generated frame files from test dir.
  Args:
    dir_name: test directory name
    save_files_list: list of files not to be removed
  """
  if os.path.exists(dir_name):
    for image in glob.glob('%s/*.png' % dir_name):
      if image not in save_files_list:
        os.remove(image)


class MultiCameraSwitchTest(its_base_test.ItsBaseTest):
  """Test that the switch from UW to W lens has similar RGB values.

  This test uses various zoom ratios within range android.control.zoomRatioRange
  to capture images and find the point when the physical camera changes
  to determine the crossover point of change from UW to W.
  It does preview recording at before and after crossover point to verify that
  the AE, AWB behavior remains the same.
  """

  def test_multi_camera_switch(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      chart_distance = self.chart_distance

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          camera_properties_utils.zoom_ratio_range(props) and
          camera_properties_utils.logical_multi_camera(props))

      # Check the zoom range
      zoom_range = props['android.control.zoomRatioRange']
      logging.debug('zoomRatioRange: %s', str(zoom_range))
      camera_properties_utils.skip_unless(len(zoom_range) > 1)

      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, chart_distance)

      preview_test_size = _get_preview_test_size(cam, self.camera_id)
      cam.do_3a()

      # dynamic preview recording
      recording_obj = _collect_data(cam, preview_test_size,
                                    _ZOOM_RANGE_UW_W[0],
                                    _ZOOM_RANGE_UW_W[1],
                                    _ZOOM_STEP)

      # Grab the recording from DUT
      self.dut.adb.pull([recording_obj['recordedOutputPath'], self.log_path])
      preview_file_name = (
          recording_obj['recordedOutputPath'].split('/')[-1])
      logging.debug('preview_file_name: %s', preview_file_name)
      logging.debug('recorded video size : %s',
                    str(recording_obj['videoSize']))

      # Extract frames as png from mp4 preview recording
      file_list = video_processing_utils.extract_all_frames_from_video(
          self.log_path, preview_file_name, _IMG_FORMAT
      )

      # TODO(ruchamk): Raise error if capture result and
      # frame count doesn't match.
      capture_results = recording_obj['captureMetadata']

      # skip frames which might not have 3A converged
      capture_results = capture_results[_SKIP_INITIAL_FRAMES:]
      file_list = file_list[_SKIP_INITIAL_FRAMES:]

      physical_id_before = None
      crossover_counter = 0  # counter for the index of crossover point result
      lens_changed = False

      for capture_result in capture_results:
        crossover_counter += 1
        physical_id = capture_result[
            'android.logicalMultiCamera.activePhysicalId']
        if not physical_id_before:
          physical_id_before = physical_id
        zoom_ratio = float(capture_result['android.control.zoomRatio'])
        if physical_id_before == physical_id:
          continue
        else:
          logging.debug('Active physical id changed')
          logging.debug('Crossover zoom ratio point: %f', zoom_ratio)
          physical_id_before = physical_id
          lens_changed = True
          break

      # Raise error is lens did not switch within the range
      # _ZOOM_RANGE_UW_W
      # TODO(ruchamk): Add lens_changed to the CameraITS metrics
      if not lens_changed:
        e_msg = 'Crossover point not found. Try running the test again!'
        raise AssertionError(e_msg)

      img_before_crossover_file = file_list[crossover_counter-2]
      capture_result_before_crossover = capture_results[crossover_counter-2]
      logging.debug('Capture results before crossover: %s',
                    capture_result_before_crossover)
      img_after_crossover_file = file_list[crossover_counter-1]
      capture_result_after_crossover = capture_results[crossover_counter-1]
      logging.debug('Capture results after crossover: %s',
                    capture_result_after_crossover)

      # Remove unwanted frames and only save the before and
      # after crossover point frames along with mp4 recording
      _remove_frame_files(self.log_path, [
          os.path.join(self.log_path, img_before_crossover_file),
          os.path.join(self.log_path, img_after_crossover_file)])

      # Add suffix to the before and after crossover files
      before_path = pathlib.Path(os.path.join(self.log_path,
                                              img_before_crossover_file))
      before_crossover_name = before_path.with_name(
          f'{before_path.stem}_before_crossover{before_path.suffix}')
      os.rename(os.path.join(self.log_path,
                             img_before_crossover_file), before_crossover_name)

      after_path = pathlib.Path(os.path.join(self.log_path,
                                             img_after_crossover_file))
      after_crossover_name = after_path.with_name(
          f'{after_path.stem}_after_crossover{after_path.suffix}')
      os.rename(os.path.join(self.log_path, img_after_crossover_file),
                after_crossover_name)

      # TODO(ruchamk): AE,AWB checks


if __name__ == '__main__':
  test_runner.main()
