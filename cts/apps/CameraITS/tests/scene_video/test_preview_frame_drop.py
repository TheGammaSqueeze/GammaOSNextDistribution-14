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
"""Verify frames are not dropped during preview recording."""


import logging
import math
import os
import time

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import its_session_utils
import video_processing_utils


_FPS_RTOL = 0.1  # Recording FPS must be within 10% of requested FPS
# Consecutive frames averaging >1.5x more than ideal frame rate -> FAIL
_FRAME_DELTA_MAXIMUM_FACTOR = 1.5
_FRAME_DELTA_WINDOW_SIZE = 30  # 0.5 second of 60FPS video -> 30 frames
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SCENE_DISPLAY_WAIT_TIME = 5  # seconds
_VIDEO_DURATION = 10  # seconds


def _get_local_maximum(values, window_size=1):
  output = min(values)
  for i in range(len(values)):
    if i + window_size <= len(values):
      output = max(output, np.average(values[i:i+window_size]))
  return output


class PreviewFrameDropTest(its_base_test.ItsBaseTest):
  """Tests if frames are dropped during preview recording.

  Takes a preview recording of a video scene, with circles moving
  at different simulated frame rates. Verifies that the overall frame rate of
  the recording matches the requested frame rate, and that there are no
  significant groups of elevated frame deltas.
  """

  def test_preview_frame_drop(self):
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)

      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL)

      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          lighting_check=False, log_path=self.log_path)
      time.sleep(_SCENE_DISPLAY_WAIT_TIME)

      # Log ffmpeg version being used
      video_processing_utils.log_ffmpeg_version()

      # Find largest minimum AE target FPS
      fps_ranges = camera_properties_utils.get_ae_target_fps_ranges(props)
      logging.debug('FPS ranges: %s', fps_ranges)
      if not fps_ranges:
        raise AssertionError('No FPS ranges found.')
      video_fps = max(fps_ranges, key=lambda r: r[0])[0]
      logging.debug('Recording FPS: %s', video_fps)

      # Record preview at largest supported size
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)
      supported_video_sizes = cam.get_supported_video_sizes_capped(
          self.camera_id)
      max_video_size = supported_video_sizes[-1]
      logging.debug('Camera supported video sizes: %s',
                    supported_video_sizes)

      # Change preview size depending on video size support
      preview_size = supported_preview_sizes[-1]
      if preview_size <= max_video_size:
        logging.debug('preview_size is supported by video encoder')
      else:
        preview_size = max_video_size

      recording_obj = cam.do_preview_recording(
          preview_size, _VIDEO_DURATION, False,
          ae_target_fps_min=video_fps, ae_target_fps_max=video_fps)

      logging.debug('Recorded output path: %s',
                    recording_obj['recordedOutputPath'])
      logging.debug('Tested quality: %s', recording_obj['quality'])

      # Grab the video from the saved location on DUT
      self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
      file_name = recording_obj['recordedOutputPath'].split('/')[-1]
      logging.debug('Recorded file name: %s', file_name)

      # Calculate average frame rate of recording
      failure_messages = []
      file_name_with_path = os.path.join(
          self.log_path, file_name)
      reported_frame_rate = video_processing_utils.get_average_frame_rate(
          file_name_with_path)
      if not math.isclose(video_fps, reported_frame_rate, rel_tol=_FPS_RTOL):
        failure_messages.append(
            f'Requested FPS {video_fps} does not match '
            f'recording FPS {reported_frame_rate}, RTOL: {_FPS_RTOL}'
        )
      else:
        logging.debug('Reported preview frame rate: %s', reported_frame_rate)

      # Calculate frame deltas, discarding first value
      frame_deltas = np.array(video_processing_utils.get_frame_deltas(
          file_name_with_path))[1:]
      frame_delta_max = np.max(frame_deltas)
      frame_delta_min = np.min(frame_deltas)
      frame_delta_avg = np.average(frame_deltas)
      frame_delta_var = np.var(frame_deltas)
      logging.debug('Frame delta max: %.4f, min: %.4f, avg: %.4f, var: %.4f',
                    frame_delta_max, frame_delta_min,
                    frame_delta_avg, frame_delta_var)
      frame_delta_local_max = _get_local_maximum(
          frame_deltas, window_size=_FRAME_DELTA_WINDOW_SIZE)
      logging.debug('Frame delta local maximum: %.4f', frame_delta_local_max)
      maximum_tolerable_frame_delta = _FRAME_DELTA_MAXIMUM_FACTOR / video_fps
      if frame_delta_local_max > maximum_tolerable_frame_delta:
        failure_messages.append(
            f'Local maximum of frame deltas {frame_delta_local_max} was '
            'greater than maximum tolerable '
            f'frame delta {maximum_tolerable_frame_delta}. '
            f'Window for local maximum: {_FRAME_DELTA_WINDOW_SIZE}. '
        )

      if failure_messages:
        raise AssertionError('\n'.join(failure_messages))

  def teardown_test(self):
    its_session_utils.stop_video_playback(self.tablet)


if __name__ == '__main__':
  test_runner.main()
