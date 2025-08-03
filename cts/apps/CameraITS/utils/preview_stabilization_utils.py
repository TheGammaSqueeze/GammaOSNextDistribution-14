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
"""Utility functions for verifying preview stabilization.
"""

import fnmatch
import logging
import os
import threading
import time

import image_processing_utils
import sensor_fusion_utils
import video_processing_utils

_ASPECT_RATIO_16_9 = 16/9  # determine if preview fmt > 16:9
_IMG_FORMAT = 'png'
_MIN_PHONE_MOVEMENT_ANGLE = 5  # degrees
_NUM_ROTATIONS = 24
_PREVIEW_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed
_START_FRAME = 30  # give 3A some frames to warm up
_VIDEO_DELAY_TIME = 5.5  # seconds
_VIDEO_DURATION = 5.5  # seconds


def collect_data(cam, tablet_device, preview_size, stabilize,
                 rot_rig, fps_range=None, hlg10=False, ois=False):
  """Capture a new set of data from the device.

  Captures camera preview frames while the user is moving the device in
  the prescribed manner.

  Args:
    cam: camera object
    tablet_device: boolean; based on config file
    preview_size: str; preview stream resolution. ex. '1920x1080'
    stabilize: boolean; whether preview stabilization is ON
    rot_rig: dict with 'cntl' and 'ch' defined
    fps_range: list; target fps range.
    hlg10: boolean; whether to capture hlg10 output
    ois: boolean; whether optical image stabilization is ON

  Returns:
    recording object; a dictionary containing output path, video size, etc.
  """

  logging.debug('Starting sensor event collection')
  serial_port = None
  if rot_rig['cntl'].lower() == sensor_fusion_utils.ARDUINO_STRING.lower():
    # identify port
    serial_port = sensor_fusion_utils.serial_port_def(
        sensor_fusion_utils.ARDUINO_STRING)
    # send test cmd to Arduino until cmd returns properly
    sensor_fusion_utils.establish_serial_comm(serial_port)
  # Start camera vibration
  if tablet_device:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION_TABLET
  else:
    servo_speed = sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION
  p = threading.Thread(
      target=sensor_fusion_utils.rotation_rig,
      args=(
          rot_rig['cntl'],
          rot_rig['ch'],
          _NUM_ROTATIONS,
          sensor_fusion_utils.ARDUINO_ANGLES_STABILIZATION,
          servo_speed,
          sensor_fusion_utils.ARDUINO_MOVE_TIME_STABILIZATION,
          serial_port,
      ),
  )
  p.start()

  cam.start_sensor_events()
  # Allow time for rig to start moving
  time.sleep(_VIDEO_DELAY_TIME)

  # Record video and return recording object
  min_fps = fps_range[0] if (fps_range is not None) else None
  max_fps = fps_range[1] if (fps_range is not None) else None
  recording_obj = cam.do_preview_recording(
      preview_size, _VIDEO_DURATION, stabilize, ois, ae_target_fps_min=min_fps,
      ae_target_fps_max=max_fps, hlg10_enabled=hlg10)
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  # Wait for vibration to stop
  p.join()

  return recording_obj


def verify_preview_stabilization(recording_obj, gyro_events,
                                 test_name, log_path, facing):
  """Verify the returned recording is properly stabilized.

  Args:
    recording_obj: Camcorder recording object.
    gyro_events: Gyroscope events collected while recording.
    test_name: Name of the test
    log_path: Path for the log file
    facing: Facing of the camera device

  Returns:
    A dictionary containing the maximum gyro angle, the maximum camera angle,
    and a failure message if the recorded video isn't properly stablilized.
  """

  file_name = recording_obj['recordedOutputPath'].split('/')[-1]
  logging.debug('recorded file name: %s', file_name)
  video_size = recording_obj['videoSize']
  logging.debug('video size: %s', video_size)

  # Get all frames from the video
  file_list = video_processing_utils.extract_all_frames_from_video(
      log_path, file_name, _IMG_FORMAT
  )
  frames = []

  logging.debug('Number of frames %d', len(file_list))
  for file in file_list:
    img = image_processing_utils.convert_image_to_numpy_array(
        os.path.join(log_path, file)
    )
    frames.append(img / 255)
  frame_shape = frames[0].shape
  logging.debug('Frame size %d x %d', frame_shape[1], frame_shape[0])

  # Extract camera rotations
  img_h = frames[0].shape[0]
  file_name_stem = f'{os.path.join(log_path, test_name)}_{video_size}'
  cam_rots = sensor_fusion_utils.get_cam_rotations(
      frames[_START_FRAME:],
      facing,
      img_h,
      file_name_stem,
      _START_FRAME,
      stabilized_video=True
  )
  sensor_fusion_utils.plot_camera_rotations(cam_rots, _START_FRAME,
                                            video_size, file_name_stem)
  max_camera_angle = sensor_fusion_utils.calc_max_rotation_angle(
      cam_rots, 'Camera')

  # Extract gyro rotations
  sensor_fusion_utils.plot_gyro_events(
      gyro_events, f'{test_name}_{video_size}', log_path)
  gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
      gyro_events, _VIDEO_DELAY_TIME)
  max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
      gyro_rots, 'Gyro')
  logging.debug(
      'Max deflection (degrees) %s: video: %.3f, gyro: %.3f ratio: %.4f',
      video_size, max_camera_angle, max_gyro_angle,
      max_camera_angle / max_gyro_angle)

  # Assert phone is moved enough during test
  if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
    raise AssertionError(
        f'Phone not moved enough! Movement: {max_gyro_angle}, '
        f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

  w_x_h = video_size.split('x')
  if int(w_x_h[0])/int(w_x_h[1]) > _ASPECT_RATIO_16_9:
    preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR * 1.1
  else:
    preview_stabilization_factor = _PREVIEW_STABILIZATION_FACTOR

  failure_msg = None
  if max_camera_angle >= max_gyro_angle * preview_stabilization_factor:
    failure_msg = (
        f'{video_size} preview not stabilized enough! '
        f'Max preview angle:  {max_camera_angle:.3f}, '
        f'Max gyro angle: {max_gyro_angle:.3f}, '
        f'ratio: {max_camera_angle/max_gyro_angle:.3f} '
        f'THRESH: {preview_stabilization_factor}.')
  # Delete saved frames if the format is a PASS
  else:
    try:
      tmpdir = os.listdir(log_path)
    except FileNotFoundError:
      logging.debug('Tmp directory: %s not found', log_path)
    for file in tmpdir:
      if fnmatch.fnmatch(file, f'*_{video_size}_stabilized_frame_*'):
        file_to_remove = os.path.join(log_path, file)
        try:
          os.remove(file_to_remove)
        except FileNotFoundError:
          logging.debug('File Not Found: %s', str(file))
    logging.debug('Format %s passes, frame images removed', video_size)

  return {'gyro': max_gyro_angle, 'cam': max_camera_angle,
          'failure': failure_msg}
