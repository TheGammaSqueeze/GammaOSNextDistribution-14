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
"""Utility functions for sensor_fusion hardware rig."""


import bisect
import codecs
import logging
import math
import os
import struct
import time

import cv2
from matplotlib import pylab
import matplotlib.pyplot
import numpy as np
import scipy.spatial
import serial
from serial.tools import list_ports

import camera_properties_utils
import image_processing_utils

# Constants for Rotation Rig
ARDUINO_ANGLE_MAX = 180.0  # degrees
ARDUINO_ANGLES_SENSOR_FUSION = (0, 90)  # degrees
ARDUINO_ANGLES_STABILIZATION = (10, 25)  # degrees
ARDUINO_BAUDRATE = 9600
ARDUINO_CMD_LENGTH = 3
ARDUINO_CMD_TIME = 2.0 * ARDUINO_CMD_LENGTH / ARDUINO_BAUDRATE  # round trip
ARDUINO_MOVE_TIME_SENSOR_FUSION = 2  # seconds
ARDUINO_MOVE_TIME_STABILIZATION = 0.3  # seconds
ARDUINO_PID = 0x0043
ARDUINO_SERVO_SPEED_MAX = 255
ARDUINO_SERVO_SPEED_MIN = 1
ARDUINO_SERVO_SPEED_SENSOR_FUSION = 20
ARDUINO_SERVO_SPEED_STABILIZATION = 10
ARDUINO_SERVO_SPEED_STABILIZATION_TABLET = 20
ARDUINO_SPEED_START_BYTE = 253
ARDUINO_START_BYTE = 255
ARDUINO_START_NUM_TRYS = 5
ARDUINO_START_TIMEOUT = 300  # seconds
ARDUINO_STRING = 'Arduino'
ARDUINO_TEST_CMD = (b'\x01', b'\x02', b'\x03')
ARDUINO_VALID_CH = ('1', '2', '3', '4', '5', '6')
ARDUINO_VIDS = (0x2341, 0x2a03)

CANAKIT_BAUDRATE = 115200
CANAKIT_CMD_TIME = 0.05  # seconds (found experimentally)
CANAKIT_DATA_DELIMITER = '\r\n'
CANAKIT_PID = 0xfc73
CANAKIT_SEND_TIMEOUT = 0.02  # seconds
CANAKIT_SET_CMD = 'REL'
CANAKIT_SLEEP_TIME = 2  # seconds (for full 90 degree rotation)
CANAKIT_VALID_CMD = ('ON', 'OFF')
CANAKIT_VALID_CH = ('1', '2', '3', '4')
CANAKIT_VID = 0x04d8

HS755HB_ANGLE_MAX = 202.0  # throw for rotation motor in degrees

# From test_sensor_fusion
_FEATURE_MARGIN = 0.20  # Only take feature points from center 20% so that
                        # rotation measured has less rolling shutter effect.
_FEATURE_PTS_MIN = 30  # Min number of feature pts to perform rotation analysis.
# cv2.goodFeatures to track.
# 'POSTMASK' is the measurement method in all previous versions of Android.
# 'POSTMASK' finds best features on entire frame and then masks the features
# to the vertical center FEATURE_MARGIN for the measurement.
# 'PREMASK' is a new measurement that is used when FEATURE_PTS_MIN is not
# found in frame. This finds the best 2*FEATURE_PTS_MIN in the FEATURE_MARGIN
# part of the frame.
_CV2_FEATURE_PARAMS_POSTMASK = dict(maxCorners=240,
                                    qualityLevel=0.3,
                                    minDistance=7,
                                    blockSize=7)
_CV2_FEATURE_PARAMS_PREMASK = dict(maxCorners=2*_FEATURE_PTS_MIN,
                                   qualityLevel=0.3,
                                   minDistance=7,
                                   blockSize=7)
_GYRO_SAMP_RATE_MIN = 100.0  # Samples/second: min gyro sample rate.
_CV2_LK_PARAMS = dict(winSize=(15, 15),
                      maxLevel=2,
                      criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT,
                                10, 0.03))  # cv2.calcOpticalFlowPyrLK params.
_ROTATION_PER_FRAME_MIN = 0.001  # rads/s
_GYRO_ROTATION_PER_SEC_MAX = 2.0  # rads/s
_R_SQUARED_TOLERANCE = 0.01  # tolerance for polynomial fitting r^2
_SHIFT_DOMAIN_RADIUS = 5  # limited domain centered around best shift

# unittest constants
_COARSE_FIT_RANGE = 20  # Range area around coarse fit to do optimization.
_CORR_TIME_OFFSET_MAX = 50  # ms max shift to try and match camera/gyro times.
_CORR_TIME_OFFSET_STEP = 0.5  # ms step for shifts.

# Unit translators
_MSEC_TO_NSEC = 1000000
_NSEC_TO_SEC = 1E-9
_SEC_TO_NSEC = int(1/_NSEC_TO_SEC)
_RADS_TO_DEGS = 180/math.pi

_NUM_GYRO_PTS_TO_AVG = 20


def polynomial_from_coefficients(coefficients):
  """Return a polynomial function from a coefficient list, highest power first.

  Args:
    coefficients: list of coefficients (float)
  Returns:
    Function in the form of a*x^n + b*x^(n - 1) + ... + constant
  """
  def polynomial(x):
    n = len(coefficients)
    return sum(coefficients[i] * x ** (n - i - 1) for i in range(n))
  return polynomial


def smallest_absolute_minimum_of_polynomial(coefficients):
  """Return the smallest minimum by absolute value from a coefficient list.

  Args:
    coefficients: list of coefficients (float)
  Returns:
    Smallest local minimum (by absolute value) on the function (float)
  """
  first_derivative = np.polyder(coefficients, m=1)
  second_derivative = np.polyder(coefficients, m=2)
  extrema = np.roots(first_derivative)
  smallest_absolute_minimum = None
  for extremum in extrema:
    if np.polyval(second_derivative, extremum) > 0:
      if smallest_absolute_minimum is None or abs(extremum) < abs(
          smallest_absolute_minimum):
        smallest_absolute_minimum = extremum
  if smallest_absolute_minimum is None:
    raise AssertionError(
        f'No minima were found on function described by {coefficients}.')
  return smallest_absolute_minimum


def serial_port_def(name):
  """Determine the serial port and open.

  Args:
    name: string of device to locate (ie. 'Arduino', 'Canakit' or 'Default')
  Returns:
    serial port object
  """
  serial_port = None
  devices = list_ports.comports()
  for device in devices:
    if not (device.vid and device.pid):  # Not all comm ports have vid and pid
      continue
    if name.lower() == 'arduino':
      if (device.vid in ARDUINO_VIDS and device.pid == ARDUINO_PID):
        logging.debug('Arduino: %s', str(device))
        serial_port = device.device
        return serial.Serial(serial_port, ARDUINO_BAUDRATE, timeout=1)

    elif name.lower() in ('canakit', 'default'):
      if (device.vid == CANAKIT_VID and device.pid == CANAKIT_PID):
        logging.debug('Canakit: %s', str(device))
        serial_port = device.device
        return serial.Serial(serial_port, CANAKIT_BAUDRATE,
                             timeout=CANAKIT_SEND_TIMEOUT,
                             parity=serial.PARITY_EVEN,
                             stopbits=serial.STOPBITS_ONE,
                             bytesize=serial.EIGHTBITS)
  raise ValueError(f'{name} device not connected.')


def canakit_cmd_send(canakit_serial_port, cmd_str):
  """Wrapper for sending serial command to Canakit.

  Args:
    canakit_serial_port: port to write for canakit
    cmd_str: str; value to send to device.
  """
  try:
    logging.debug('writing port...')
    canakit_serial_port.write(CANAKIT_DATA_DELIMITER.encode())
    time.sleep(CANAKIT_CMD_TIME)  # This is critical for relay.
    canakit_serial_port.write(cmd_str.encode())

  except IOError as io_error:
    raise IOError(
        f'Port {CANAKIT_VID}:{CANAKIT_PID} is not open!') from io_error


def canakit_set_relay_channel_state(canakit_port, ch, state):
  """Set Canakit relay channel and state.

  Waits CANAKIT_SLEEP_TIME for rotation to occur.

  Args:
    canakit_port: serial port object for the Canakit port.
    ch: string for channel number of relay to set. '1', '2', '3', or '4'
    state: string of either 'ON' or 'OFF'
  """
  logging.debug('Setting relay state %s', state)
  if ch in CANAKIT_VALID_CH and state in CANAKIT_VALID_CMD:
    canakit_cmd_send(canakit_port, CANAKIT_SET_CMD + ch + '.' + state + '\r\n')
    time.sleep(CANAKIT_SLEEP_TIME)
  else:
    logging.debug('Invalid ch (%s) or state (%s), no command sent.', ch, state)


def arduino_read_cmd(port):
  """Read back Arduino command from serial port."""
  cmd = []
  for _ in range(ARDUINO_CMD_LENGTH):
    cmd.append(port.read())
  return cmd


def arduino_send_cmd(port, cmd):
  """Send command to serial port."""
  for i in range(ARDUINO_CMD_LENGTH):
    port.write(cmd[i])


def arduino_loopback_cmd(port, cmd):
  """Send command to serial port."""
  arduino_send_cmd(port, cmd)
  time.sleep(ARDUINO_CMD_TIME)
  return arduino_read_cmd(port)


def establish_serial_comm(port):
  """Establish connection with serial port."""
  logging.debug('Establishing communication with %s', port.name)
  trys = 1
  hex_test = convert_to_hex(ARDUINO_TEST_CMD)
  logging.debug(' test tx: %s %s %s', hex_test[0], hex_test[1], hex_test[2])
  start = time.time()
  while time.time() < start + ARDUINO_START_TIMEOUT:
    try:
      cmd_read = arduino_loopback_cmd(port, ARDUINO_TEST_CMD)
    except serial.serialutil.SerialException as _:
      logging.debug('Port in use, trying again...')
      continue
    hex_read = convert_to_hex(cmd_read)
    logging.debug(' test rx: %s %s %s', hex_read[0], hex_read[1], hex_read[2])
    if cmd_read != list(ARDUINO_TEST_CMD):
      trys += 1
    else:
      logging.debug(' Arduino comm established after %d try(s)', trys)
      break
  else:
    raise AssertionError(f'Arduino comm not established after {trys} tries '
                         f'and {ARDUINO_START_TIMEOUT} seconds')


def convert_to_hex(cmd):
  return [('%0.2x' % int(codecs.encode(x, 'hex_codec'), 16) if x else '--')
          for x in cmd]


def arduino_rotate_servo_to_angle(ch, angle, serial_port, move_time):
  """Rotate servo to the specified angle.

  Args:
    ch: str; servo to rotate in ARDUINO_VALID_CH
    angle: int; servo angle to move to
    serial_port: object; serial port
    move_time: int; time in seconds
  """
  if angle < 0 or angle > ARDUINO_ANGLE_MAX:
    logging.debug('Angle must be between 0 and %d.', ARDUINO_ANGLE_MAX)
    angle = 0
    if angle > ARDUINO_ANGLE_MAX:
      angle = ARDUINO_ANGLE_MAX

  cmd = [struct.pack('B', i) for i in [ARDUINO_START_BYTE, int(ch), angle]]
  arduino_send_cmd(serial_port, cmd)
  time.sleep(move_time)


def arduino_rotate_servo(ch, angles, move_time, serial_port):
  """Rotate servo through 'angles'.

  Args:
    ch: str; servo to rotate
    angles: list of ints; servo angles to move to
    move_time: int; time required to allow for arduino movement
    serial_port: object; serial port
  """

  for angle in angles:
    angle_norm = int(round(angle*ARDUINO_ANGLE_MAX/HS755HB_ANGLE_MAX, 0))
    arduino_rotate_servo_to_angle(ch, angle_norm, serial_port, move_time)


def rotation_rig(rotate_cntl, rotate_ch, num_rotations, angles, servo_speed,
                 move_time, arduino_serial_port):
  """Rotate the phone n times using rotate_cntl and rotate_ch defined.

  rotate_ch is hard wired and must be determined from physical setup.
  If using Arduino, serial port must be initialized and communication must be
  established before rotation.

  Args:
    rotate_cntl: str to identify 'arduino', 'canakit' or 'external' controller.
    rotate_ch: str to identify rotation channel number.
    num_rotations: int number of rotations.
    angles: list of ints; servo angle to move to.
    servo_speed: int number of move speed between [1, 255].
    move_time: int time required to allow for arduino movement.
    arduino_serial_port: optional initialized serial port object
  """

  logging.debug('Controller: %s, ch: %s', rotate_cntl, rotate_ch)
  if arduino_serial_port:
    # initialize servo at origin
    logging.debug('Moving servo to origin')
    arduino_rotate_servo_to_angle(rotate_ch, 0, arduino_serial_port, 1)

    # set servo speed
    set_servo_speed(rotate_ch, servo_speed, arduino_serial_port, delay=0)
  elif rotate_cntl.lower() == 'canakit':
    canakit_serial_port = serial_port_def('Canakit')
  elif rotate_cntl.lower() == 'external':
    logging.info('External rotation control.')
  else:
    logging.info('No rotation rig defined. Manual test: rotate phone by hand.')

  # rotate phone
  logging.debug('Rotating phone %dx', num_rotations)
  for _ in range(num_rotations):
    if rotate_cntl == 'arduino':
      arduino_rotate_servo(rotate_ch, angles, move_time, arduino_serial_port)
    elif rotate_cntl == 'canakit':
      canakit_set_relay_channel_state(canakit_serial_port, rotate_ch, 'ON')
      canakit_set_relay_channel_state(canakit_serial_port, rotate_ch, 'OFF')
  logging.debug('Finished rotations')
  if rotate_cntl == 'arduino':
    logging.debug('Moving servo to origin')
    arduino_rotate_servo_to_angle(rotate_ch, 0, arduino_serial_port, 1)


def set_servo_speed(ch, servo_speed, serial_port, delay=0):
  """Set servo to specified speed.

  Args:
    ch: str; servo to turn on in ARDUINO_VALID_CH
    servo_speed: int; value of speed between 1 and 255
    serial_port: object; serial port
    delay: int; time in seconds
  """
  logging.debug('Servo speed: %d', servo_speed)
  if servo_speed < ARDUINO_SERVO_SPEED_MIN:
    logging.debug('Servo speed must be >= %d.', ARDUINO_SERVO_SPEED_MIN)
    servo_speed = ARDUINO_SERVO_SPEED_MIN
  elif servo_speed > ARDUINO_SERVO_SPEED_MAX:
    logging.debug('Servo speed must be <= %d.', ARDUINO_SERVO_SPEED_MAX)
    servo_speed = ARDUINO_SERVO_SPEED_MAX

  cmd = [struct.pack('B', i) for i in [ARDUINO_SPEED_START_BYTE,
                                       int(ch), servo_speed]]
  arduino_send_cmd(serial_port, cmd)
  time.sleep(delay)


def calc_max_rotation_angle(rotations, sensor_type):
  """Calculates the max angle of deflection from rotations.

  Args:
    rotations: numpy array of rotation per event
    sensor_type: string 'Camera' or 'Gyro'

  Returns:
    maximum angle of rotation for the given rotations
  """
  rotations *= _RADS_TO_DEGS
  rotations_sum = np.cumsum(rotations)
  rotation_max = max(rotations_sum)
  rotation_min = min(rotations_sum)
  logging.debug('%s min: %.2f, max %.2f rotation (degrees)',
                sensor_type, rotation_min, rotation_max)
  logging.debug('%s max rotation: %.2f degrees',
                sensor_type, (rotation_max-rotation_min))
  return rotation_max-rotation_min


def get_gyro_rotations(gyro_events, cam_times):
  """Get the rotation values of the gyro.

  Integrates the gyro data between each camera frame to compute an angular
  displacement.

  Args:
    gyro_events: List of gyro event objects.
    cam_times: Array of N camera times, one for each frame.

  Returns:
    Array of N-1 gyro rotation measurements (rads/s).
  """
  gyro_times = np.array([e['time'] for e in gyro_events])
  all_gyro_rots = np.array([e['z'] for e in gyro_events])
  gyro_rots = []
  if gyro_times[0] > cam_times[0] or gyro_times[-1] < cam_times[-1]:
    raise AssertionError('Gyro times do not bound camera times! '
                         f'gyro: {gyro_times[0]:.0f} -> {gyro_times[-1]:.0f} '
                         f'cam: {cam_times[0]} -> {cam_times[-1]} (ns).')

  # Integrate the gyro data between each pair of camera frame times.
  for i_cam in range(len(cam_times)-1):
    # Get the window of gyro samples within the current pair of frames.
    # Note: bisect always picks first gyro index after camera time.
    t_cam0 = cam_times[i_cam]
    t_cam1 = cam_times[i_cam+1]
    i_gyro_window0 = bisect.bisect(gyro_times, t_cam0)
    i_gyro_window1 = bisect.bisect(gyro_times, t_cam1)
    gyro_sum = 0

    # Integrate samples within the window.
    for i_gyro in range(i_gyro_window0, i_gyro_window1):
      gyro_val = all_gyro_rots[i_gyro+1]
      t_gyro0 = gyro_times[i_gyro]
      t_gyro1 = gyro_times[i_gyro+1]
      t_gyro_delta = (t_gyro1 - t_gyro0) * _NSEC_TO_SEC
      gyro_sum += gyro_val * t_gyro_delta

    # Handle the fractional intervals at the sides of the window.
    for side, i_gyro in enumerate([i_gyro_window0-1, i_gyro_window1]):
      gyro_val = all_gyro_rots[i_gyro+1]
      t_gyro0 = gyro_times[i_gyro]
      t_gyro1 = gyro_times[i_gyro+1]
      t_gyro_delta = (t_gyro1 - t_gyro0) * _NSEC_TO_SEC
      if side == 0:
        f = (t_cam0 - t_gyro0) / (t_gyro1 - t_gyro0)
        frac_correction = gyro_val * t_gyro_delta * (1.0 - f)
        gyro_sum += frac_correction
      else:
        f = (t_cam1 - t_gyro0) / (t_gyro1 - t_gyro0)
        frac_correction = gyro_val * t_gyro_delta * f
        gyro_sum += frac_correction
    gyro_rots.append(gyro_sum)
  gyro_rots = np.array(gyro_rots)
  return gyro_rots


def procrustes_rotation(x, y):
  """Performs a Procrustes analysis to conform points in x to y.

  Procrustes analysis determines a linear transformation (translation,
  reflection, orthogonal rotation and scaling) of the points in y to best
  conform them to the points in matrix x, using the sum of squared errors
  as the metric for fit criterion.

  Args:
    x: Target coordinate matrix
    y: Input coordinate matrix

  Returns:
    The rotation component of the transformation that maps x to y.
  """
  x0 = (x-x.mean(0)) / np.sqrt(((x-x.mean(0))**2.0).sum())
  y0 = (y-y.mean(0)) / np.sqrt(((y-y.mean(0))**2.0).sum())
  u, _, vt = np.linalg.svd(np.dot(x0.T, y0), full_matrices=False)
  return np.dot(vt.T, u.T)


def get_cam_rotations(frames, facing, h, file_name_stem,
                      start_frame, stabilized_video=False):
  """Get the rotations of the camera between each pair of frames.

  Takes N frames and returns N-1 angular displacements corresponding to the
  rotations between adjacent pairs of frames, in radians.
  Only takes feature points from center so that rotation measured has less
  rolling shutter effect.
  Requires FEATURE_PTS_MIN to have enough data points for accurate measurements.
  Uses FEATURE_PARAMS for cv2 to identify features in checkerboard images.
  Ensures camera rotates enough if not calling with stabilized video.

  Args:
    frames: List of N images (as RGB numpy arrays).
    facing: Direction camera is facing.
    h: Pixel height of each frame.
    file_name_stem: file name stem including location for data.
    start_frame: int; index to start at
    stabilized_video: Boolean; if called with stabilized video

  Returns:
    numpy array of N-1 camera rotation measurements (rad).
  """
  gframes = []
  for frame in frames:
    frame = (frame * 255.0).astype(np.uint8)  # cv2 uses [0, 255]
    gframes.append(cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY))
  num_frames = len(gframes)
  logging.debug('num_frames: %d', num_frames)
  # create mask
  ymin = int(h * (1 - _FEATURE_MARGIN) / 2)
  ymax = int(h * (1 + _FEATURE_MARGIN) / 2)
  pre_mask = np.zeros_like(gframes[0])
  pre_mask[ymin:ymax, :] = 255

  for masking in ['post', 'pre']:  # Do post-masking (original) method 1st
    logging.debug('Using %s masking method', masking)
    rotations = []
    for i in range(1, num_frames):
      j = i - 1
      gframe0 = gframes[j]
      gframe1 = gframes[i]
      if masking == 'post':
        p0 = cv2.goodFeaturesToTrack(
            gframe0, mask=None, **_CV2_FEATURE_PARAMS_POSTMASK)
        post_mask = (p0[:, 0, 1] >= ymin) & (p0[:, 0, 1] <= ymax)
        p0_filtered = p0[post_mask]
      else:
        p0_filtered = cv2.goodFeaturesToTrack(
            gframe0, mask=pre_mask, **_CV2_FEATURE_PARAMS_PREMASK)
      num_features = len(p0_filtered)
      if num_features < _FEATURE_PTS_MIN:
        for pt in np.rint(p0_filtered).astype(int):
          x, y = pt[0][0], pt[0][1]
          cv2.circle(frames[j], (x, y), 3, (100, 255, 255), -1)
        image_processing_utils.write_image(
            frames[j], f'{file_name_stem}_features{j+start_frame:03d}.png')
        msg = (f'Not enough features in frame {j+start_frame}. Need at least '
               f'{_FEATURE_PTS_MIN} features, got {num_features}.')
        if masking == 'pre':
          raise AssertionError(msg)
        else:
          logging.debug(msg)
          break
      else:
        logging.debug('Number of features in frame %s is %d',
                      str(j+start_frame).zfill(3), num_features)
      p1, st, _ = cv2.calcOpticalFlowPyrLK(gframe0, gframe1, p0_filtered, None,
                                           **_CV2_LK_PARAMS)
      tform = procrustes_rotation(p0_filtered[st == 1], p1[st == 1])
      if facing == camera_properties_utils.LENS_FACING['BACK']:
        rotation = -math.atan2(tform[0, 1], tform[0, 0])
      elif facing == camera_properties_utils.LENS_FACING['FRONT']:
        rotation = math.atan2(tform[0, 1], tform[0, 0])
      else:
        raise AssertionError(f'Unknown lens facing: {facing}.')
      rotations.append(rotation)
      if i == 1:
        # Save debug visualization of features that are being
        # tracked in the first frame.
        frame = frames[j]
        for x, y in np.rint(p0_filtered[st == 1]).astype(int):
          cv2.circle(frame, (x, y), 3, (100, 255, 255), -1)
        image_processing_utils.write_image(
            frame, f'{file_name_stem}_features{j+start_frame:03d}.png')
    if i == num_frames-1:
      logging.debug('Correct num of frames found: %d', i)
      break  # exit if enough features in all frames
  if i != num_frames-1:
    raise AssertionError('Neither method found enough features in all frames')

  rotations = np.array(rotations)
  rot_per_frame_max = max(abs(rotations))
  logging.debug('Max rotation in frame: %.2f degrees',
                rot_per_frame_max*_RADS_TO_DEGS)
  if stabilized_video:
    logging.debug('Skipped camera rotation check due to stabilized video.')
  else:
    if rot_per_frame_max < _ROTATION_PER_FRAME_MIN:
      raise AssertionError(f'Device not moved enough: {rot_per_frame_max:.3f} '
                           f'movement. THRESH: {_ROTATION_PER_FRAME_MIN} rads.')
    else:
      logging.debug('Device movement exceeds %.2f degrees',
                    _ROTATION_PER_FRAME_MIN*_RADS_TO_DEGS)
  return rotations


def get_best_alignment_offset(cam_times, cam_rots, gyro_events, degree=2):
  """Find the best offset to align the camera and gyro motion traces.

  This function integrates the shifted gyro data between camera samples
  for a range of candidate shift values, and returns the shift that
  result in the best correlation.

  Uses a correlation distance metric between the curves, where a smaller
  value means that the curves are better-correlated.

  Fits a curve to the correlation distance data to measure the minima more
  accurately, by looking at the correlation distances within a range of
  +/- 10ms from the measured best score; note that this will use fewer
  than the full +/- 10 range for the curve fit if the measured score
  (which is used as the center of the fit) is within 10ms of the edge of
  the +/- 50ms candidate range.

  Args:
    cam_times: Array of N camera times, one for each frame.
    cam_rots: Array of N-1 camera rotation displacements (rad).
    gyro_events: List of gyro event objects.
    degree: Degree of polynomial

  Returns:
    Best alignment offset(ms), fit coefficients, candidates, and distances.
  """
  # Measure the correlation distance over defined shift
  shift_candidates = np.arange(-_CORR_TIME_OFFSET_MAX,
                               _CORR_TIME_OFFSET_MAX+_CORR_TIME_OFFSET_STEP,
                               _CORR_TIME_OFFSET_STEP).tolist()
  spatial_distances = []
  for shift in shift_candidates:
    shifted_cam_times = cam_times + shift*_MSEC_TO_NSEC
    gyro_rots = get_gyro_rotations(gyro_events, shifted_cam_times)
    spatial_distance = scipy.spatial.distance.correlation(cam_rots, gyro_rots)
    logging.debug('shift %.1fms spatial distance: %.5f', shift,
                  spatial_distance)
    spatial_distances.append(spatial_distance)

  best_corr_dist = min(spatial_distances)
  coarse_best_shift = shift_candidates[spatial_distances.index(best_corr_dist)]
  logging.debug('Best shift without fitting is %.4f ms', coarse_best_shift)

  # Fit a polynomial around coarse_best_shift to extract best fit
  i = spatial_distances.index(best_corr_dist)
  i_poly_fit_min = i - _COARSE_FIT_RANGE
  i_poly_fit_max = i + _COARSE_FIT_RANGE + 1
  shift_candidates = shift_candidates[i_poly_fit_min:i_poly_fit_max]
  spatial_distances = spatial_distances[i_poly_fit_min:i_poly_fit_max]
  logging.debug('Polynomial degree: %d', degree)
  fit_coeffs, residuals, _, _, _ = np.polyfit(
      shift_candidates, spatial_distances, degree, full=True
  )
  logging.debug('Fit coefficients: %s', fit_coeffs)
  logging.debug('Residuals: %s', residuals)
  total_sum_of_squares = np.sum(
      (spatial_distances - np.mean(spatial_distances)) ** 2
  )
  # Calculate r-squared on the entire domain for debugging
  r_squared = 1 - residuals[0] / total_sum_of_squares
  logging.debug('r^2 on the entire domain: %f', r_squared)

  # Calculate r-squared near the best shift
  domain_around_best_shift = [coarse_best_shift - _SHIFT_DOMAIN_RADIUS,
                              coarse_best_shift + _SHIFT_DOMAIN_RADIUS]
  logging.debug('Calculating r^2 on the limited domain of [%f, %f]',
                domain_around_best_shift[0], domain_around_best_shift[1])
  small_shifts_and_distances = [
      (x, y)
      for x, y in zip(shift_candidates, spatial_distances)
      if domain_around_best_shift[0] <= x <= domain_around_best_shift[1]
  ]
  small_shift_candidates, small_spatial_distances = zip(
      *small_shifts_and_distances
  )
  logging.debug('Shift candidates on limited domain: %s',
                small_shift_candidates)
  logging.debug('Spatial distances on limited domain: %s',
                small_spatial_distances)
  limited_residuals = np.sum(
      (np.polyval(fit_coeffs, small_shift_candidates) - small_spatial_distances)
      ** 2
  )
  logging.debug('Residuals on limited domain: %s', limited_residuals)
  limited_total_sum_of_squares = np.sum(
      (small_spatial_distances - np.mean(small_spatial_distances)) ** 2
  )
  limited_r_squared = 1 - limited_residuals / limited_total_sum_of_squares
  logging.debug('r^2 on limited domain: %f', limited_r_squared)

  # Calculate exact_best_shift (x where y is minimum of parabola)
  exact_best_shift = smallest_absolute_minimum_of_polynomial(fit_coeffs)

  if abs(coarse_best_shift - exact_best_shift) > 2.0:
    raise AssertionError(
        f'Test failed. Bad fit to time-shift curve. Coarse best shift: '
        f'{coarse_best_shift}, Exact best shift: {exact_best_shift}.')

  # Check fit of polynomial near the best shift
  if not math.isclose(limited_r_squared, 1, abs_tol=_R_SQUARED_TOLERANCE):
    logging.debug('r-squared on domain [%f, %f] was %f, expected 1.0, '
                  'ATOL: %f',
                  domain_around_best_shift[0], domain_around_best_shift[1],
                  limited_r_squared, _R_SQUARED_TOLERANCE)
    return None

  return exact_best_shift, fit_coeffs, shift_candidates, spatial_distances


def plot_camera_rotations(cam_rots, start_frame, video_quality,
                          plot_name_stem):
  """Plot the camera rotations.

  Args:
   cam_rots: np array of camera rotations angle per frame
   start_frame: int value of start frame
   video_quality: str for video quality identifier
   plot_name_stem: str (with path) of what to call plot
  """

  pylab.figure(video_quality)
  frames = range(start_frame, len(cam_rots)+start_frame)
  pylab.title(f'Camera rotation vs frame {video_quality}')
  pylab.plot(frames, cam_rots*_RADS_TO_DEGS, '-ro', label='x')
  pylab.xlabel('frame #')
  pylab.ylabel('camera rotation (degrees)')
  matplotlib.pyplot.savefig(f'{plot_name_stem}_cam_rots.png')
  pylab.close(video_quality)


def plot_gyro_events(gyro_events, plot_name, log_path):
  """Plot x, y, and z on the gyro events.

  Samples are grouped into NUM_GYRO_PTS_TO_AVG groups and averaged to minimize
  random spikes in data.

  Args:
    gyro_events: List of gyroscope events.
    plot_name:  name of plot(s).
    log_path: location to save data.
  """

  nevents = (len(gyro_events) // _NUM_GYRO_PTS_TO_AVG) * _NUM_GYRO_PTS_TO_AVG
  gyro_events = gyro_events[:nevents]
  times = np.array([(e['time'] - gyro_events[0]['time']) * _NSEC_TO_SEC
                    for e in gyro_events])
  x = np.array([e['x'] for e in gyro_events])
  y = np.array([e['y'] for e in gyro_events])
  z = np.array([e['z'] for e in gyro_events])

  # Group samples into size-N groups & average each together to minimize random
  # spikes in data.
  times = times[_NUM_GYRO_PTS_TO_AVG//2::_NUM_GYRO_PTS_TO_AVG]
  x = x.reshape(nevents//_NUM_GYRO_PTS_TO_AVG, _NUM_GYRO_PTS_TO_AVG).mean(1)
  y = y.reshape(nevents//_NUM_GYRO_PTS_TO_AVG, _NUM_GYRO_PTS_TO_AVG).mean(1)
  z = z.reshape(nevents//_NUM_GYRO_PTS_TO_AVG, _NUM_GYRO_PTS_TO_AVG).mean(1)

  pylab.figure(plot_name)
  # x & y on same axes
  pylab.subplot(2, 1, 1)
  pylab.title(f'{plot_name}(mean of {_NUM_GYRO_PTS_TO_AVG} pts)')
  pylab.plot(times, x, 'r', label='x')
  pylab.plot(times, y, 'g', label='y')
  pylab.ylim([np.amin(z)/4, np.amax(z)/4])  # zoom in 4x from z axis
  pylab.ylabel('gyro x,y movement (rads/s)')
  pylab.legend()

  # z on separate axes
  pylab.subplot(2, 1, 2)
  pylab.plot(times, z, 'b', label='z')
  pylab.ylim([np.amin(z), np.amax(z)])
  pylab.xlabel('time (seconds)')
  pylab.ylabel('gyro z movement (rads/s)')
  pylab.legend()
  file_name = os.path.join(log_path, plot_name)
  matplotlib.pyplot.savefig(f'{file_name}_gyro_events.png')
  pylab.close(plot_name)

  z_max = max(abs(z))
  logging.debug('z_max: %.3f', z_max)
  if z_max > _GYRO_ROTATION_PER_SEC_MAX:
    raise AssertionError(
        f'Phone moved too rapidly! Please confirm controller firmware. '
        f'Max: {z_max:.3f}, TOL: {_GYRO_ROTATION_PER_SEC_MAX} rads/s')


def conv_acceleration_to_movement(gyro_events, video_delay_time):
  """Convert gyro_events time and speed to movement during video time.

  Args:
    gyro_events: sorted dict of entries with 'time', 'x', 'y', and 'z'
    video_delay_time: time at which video starts (and the video's duration)

  Returns:
    'z' acceleration converted to movement for times around VIDEO playing.
  """
  gyro_times = np.array([e['time'] for e in gyro_events])
  gyro_speed = np.array([e['z'] for e in gyro_events])
  gyro_time_min = gyro_times[0]
  logging.debug('gyro start time: %dns', gyro_time_min)
  logging.debug('gyro stop time: %dns', gyro_times[-1])
  gyro_rotations = []
  video_time_start = gyro_time_min + video_delay_time *_SEC_TO_NSEC
  video_time_stop = video_time_start + video_delay_time *_SEC_TO_NSEC
  logging.debug('video start time: %dns', video_time_start)
  logging.debug('video stop time: %dns', video_time_stop)

  for i, t in enumerate(gyro_times):
    if video_time_start <= t <= video_time_stop:
      gyro_rotations.append((gyro_times[i]-gyro_times[i-1])/_SEC_TO_NSEC *
                            gyro_speed[i])
  return np.array(gyro_rotations)
