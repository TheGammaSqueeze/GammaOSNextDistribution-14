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
"""Verify video is stable during phone movement."""

import fnmatch
import logging
import os
import threading
import time

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import sensor_fusion_utils
import video_processing_utils

_ASPECT_RATIO_16_9 = 16/9  # determine if video fmt > 16:9
_IMG_FORMAT = 'png'
_MIN_PHONE_MOVEMENT_ANGLE = 5  # degrees
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_ROTATIONS = 24
_START_FRAME = 30  # give 3A 1s to warm up
_VIDEO_DELAY_TIME = 5.5  # seconds
_VIDEO_DURATION = 5.5  # seconds
_VIDEO_QUALITIES_TESTED = ('CIF:3', '480P:4', '720P:5', '1080P:6', 'QVGA:7',
                           'VGA:9')
_VIDEO_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed
_VIDEO_STABILIZATION_MODE = 1
_SIZE_TO_PROFILE = {'176x144': 'QCIF:2', '352x288': 'CIF:3',
                    '320x240': 'QVGA:7'}


def _collect_data(cam, tablet_device, video_profile, video_quality, rot_rig):
  """Capture a new set of data from the device.

  Captures camera frames while the user is moving the device in the prescribed
  manner.

  Args:
    cam: camera object
    tablet_device: boolean; based on config.yml
    video_profile: str; number of video profile
    video_quality: str; key string for video quality. ie. 1080P
    rot_rig: dict with 'cntl' and 'ch' defined

  Returns:
    recording object
  """
  logging.debug('Starting sensor event collection')
  props = cam.get_camera_properties()
  props = cam.override_with_hidden_physical_camera_props(props)

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

  # Record video and return recording object
  time.sleep(_VIDEO_DELAY_TIME)  # allow time for rig to start moving
  recording_obj = cam.do_basic_recording(
      video_profile, video_quality, _VIDEO_DURATION, _VIDEO_STABILIZATION_MODE)
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  # Wait for vibration to stop
  p.join()

  return recording_obj


class VideoStabilizationTest(its_base_test.ItsBaseTest):
  """Tests if video is stabilized.

  Camera is moved in sensor fusion rig on an arc of 15 degrees.
  Speed is set to mimic hand movement (and not be too fast.)
  Video is captured after rotation rig starts moving, and the
  gyroscope data is dumped.

  Video is processed to dump all of the frames to PNG files.
  Camera movement is extracted from frames by determining max
  angle of deflection in video movement vs max angle of deflection
  in gyroscope movement. Test is a PASS if rotation is reduced in video.
  """

  def test_video_stabilization(self):
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      supported_stabilization_modes = props[
          'android.control.availableVideoStabilizationModes']

      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL and
          _VIDEO_STABILIZATION_MODE in supported_stabilization_modes)

      # Log ffmpeg version being used
      video_processing_utils.log_ffmpeg_version()

      # Raise error if not FRONT or REAR facing camera
      facing = props['android.lens.facing']
      camera_properties_utils.check_front_or_rear_camera(props)

      # Initialize rotation rig
      rot_rig['cntl'] = self.rotator_cntl
      rot_rig['ch'] = self.rotator_ch
      if rot_rig['cntl'].lower() != 'arduino':
        raise AssertionError(f'You must use an arduino controller for {_NAME}.')

      # Create list of video qualities to test
      excluded_sizes = video_processing_utils.LOW_RESOLUTION_SIZES
      excluded_qualities = [
          _SIZE_TO_PROFILE[s] for s in excluded_sizes if s in _SIZE_TO_PROFILE
      ]
      supported_video_qualities = cam.get_supported_video_qualities(
          self.camera_id)
      logging.debug('Supported video qualities: %s', supported_video_qualities)
      tested_video_qualities = list(set(_VIDEO_QUALITIES_TESTED) &
                                    set(supported_video_qualities) -
                                    set(excluded_qualities))

      # Raise error if no video qualities to test
      if not tested_video_qualities:
        raise AssertionError(
            f'QUALITY_LOW not supported: {supported_video_qualities}')
      else:
        logging.debug('video qualities tested: %s', str(tested_video_qualities))

      max_cam_gyro_angles = {}

      for video_tested in tested_video_qualities:
        video_profile = video_tested.split(':')[1]
        video_quality = video_tested.split(':')[0]

        # Record video
        recording_obj = _collect_data(
            cam, self.tablet_device, video_profile, video_quality, rot_rig)

        # Grab the video from the save location on DUT
        self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
        file_name = recording_obj['recordedOutputPath'].split('/')[-1]
        logging.debug('file_name: %s', file_name)

        # Get gyro events
        logging.debug('Reading out inertial sensor events')
        gyro_events = cam.get_sensor_events()['gyro']
        logging.debug('Number of gyro samples %d', len(gyro_events))

        # Extract all frames from video
        file_list = video_processing_utils.extract_all_frames_from_video(
            log_path, file_name, _IMG_FORMAT)
        frames = []
        logging.debug('Number of frames %d', len(file_list))
        for file in file_list:
          img = image_processing_utils.convert_image_to_numpy_array(
              os.path.join(log_path, file))
          frames.append(img/255)
        frame_shape = frames[0].shape
        logging.debug('Frame size %d x %d', frame_shape[1], frame_shape[0])

        # Extract camera rotations
        img_h = frames[0].shape[0]
        file_name_stem = f'{os.path.join(log_path, _NAME)}_{video_quality}'
        cam_rots = sensor_fusion_utils.get_cam_rotations(
            frames[_START_FRAME:], facing, img_h,
            file_name_stem, _START_FRAME, stabilized_video=True)
        sensor_fusion_utils.plot_camera_rotations(
            cam_rots, _START_FRAME, video_quality, file_name_stem)
        max_camera_angle = sensor_fusion_utils.calc_max_rotation_angle(
            cam_rots, 'Camera')

        # Extract gyro rotations
        sensor_fusion_utils.plot_gyro_events(
            gyro_events, f'{_NAME}_{video_quality}', log_path)
        gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
            gyro_events, _VIDEO_DELAY_TIME)
        max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
            gyro_rots, 'Gyro')
        logging.debug(
            'Max deflection (degrees) %s: video: %.3f, gyro: %.3f, ratio: %.4f',
            video_quality, max_camera_angle, max_gyro_angle,
            max_camera_angle / max_gyro_angle)
        max_cam_gyro_angles[video_quality] = {'gyro': max_gyro_angle,
                                              'cam': max_camera_angle,
                                              'frame_shape': frame_shape}

        # Assert phone is moved enough during test
        if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
          raise AssertionError(
              f'Phone not moved enough! Movement: {max_gyro_angle}, '
              f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

      # Assert PASS/FAIL criteria
      test_failures = []
      for video_quality, max_angles in max_cam_gyro_angles.items():
        aspect_ratio = (max_angles['frame_shape'][1] /
                        max_angles['frame_shape'][0])
        if aspect_ratio > _ASPECT_RATIO_16_9:
          video_stabilization_factor = _VIDEO_STABILIZATION_FACTOR * 1.1
        else:
          video_stabilization_factor = _VIDEO_STABILIZATION_FACTOR
        if max_angles['cam'] >= max_angles['gyro']*video_stabilization_factor:
          test_failures.append(
              f'{video_quality} video not stabilized enough! '
              f"Max video angle:  {max_angles['cam']:.3f}, "
              f"Max gyro angle: {max_angles['gyro']:.3f}, "
              f"ratio: {max_angles['cam']/max_angles['gyro']:.3f} "
              f'THRESH: {video_stabilization_factor}.')
        else:  # remove frames if PASS
          temp_files = []
          try:
            temp_files = os.listdir(log_path)
          except FileNotFoundError:
            logging.debug('/tmp directory: %s not found', log_path)
          for file in temp_files:
            if fnmatch.fnmatch(
                file, f'*_{video_quality}_*_stabilized_frame_*.png'):
              file_to_remove = os.path.join(log_path, file)
              try:
                os.remove(file_to_remove)
              except FileNotFoundError:
                logging.debug('File not found: %s', str(file))
          logging.debug('Quality %s passes, frame images have been removed',
                        video_quality)
      if test_failures:
        raise AssertionError(test_failures)


if __name__ == '__main__':
  test_runner.main()
