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
"""Verifies advertised FPS from device as webcam."""

import logging
import time

import cv2
import device

_FPS_TEST_DURATION = 10  # seconds
_DEVICE_NAME = 'android' # TODO b/277159494
_FPS_TO_TEST = [15, 30, 60]  # Since device or cv2 do not retrieve the
                            # advertised FPS, the test will use these
                            # values which are known to be supported


def initialize_device():
  """Gets the device index of the webcam to be tested.

  Returns:
      Device index of webcam
  """
  res_device_index = -1
  device_name = ''

  device_list = device.getDeviceList()
  for index, camera in enumerate(device_list):
    if _DEVICE_NAME in camera[0].lower():
      res_device_index = index
      device_name = camera[0]
      break

  if res_device_index < 0:
    logging.error('No device found')
  else:
    logging.info('Using webcam: %s', device_name)

  return res_device_index


def initialize_resolutions(device_index):
  """Gets list of supported resolutions from webcam.

  Args:
    device_index: index of device

  Returns:
      List of tuples of supported resolutions
  """
  device_list = device.getDeviceList()

  res = []
  if device_list:
    res = device_list[device_index][1]

  return res


def setup_for_test_fps(dut, supported_resolutions):
  """Sets up and runs fps testing on device.

  Args:
      dut: device under test
      supported_resolutions: supported resolutions on device to be tested

  Returns:
      List of tuples of the fps results, where the first element is the
      expected and the second element is the actual fps from testing
  """
  results = []

  for current_resolution in supported_resolutions:
    dut.set(cv2.CAP_PROP_FRAME_WIDTH, current_resolution[0])
    dut.set(cv2.CAP_PROP_FRAME_HEIGHT, current_resolution[1])

    for current_fps in _FPS_TO_TEST:
      dut.set(cv2.CAP_PROP_FPS, current_fps)

      results.append((current_fps, test_fps(dut)))

  return results


def test_fps(dut):
  """Tests fps on device.

  Args:
      dut: device under test

  Returns:
      fps calculated from test
  """
  num_frames = dut.get(cv2.CAP_PROP_FPS) * _FPS_TEST_DURATION

  start_time = time.time()
  i = num_frames
  while i > 0:
    ret = dut.read()

    if not ret:
      logging.error('Error while reading frame')
      break

    i -= 1

  end_time = time.time()

  fps = num_frames / (end_time - start_time)

  return fps


def main():
  device_index = initialize_device()

  if device_index < 0:
    logging.info('Supported device not found!')
    return []

  supported_resolutions = initialize_resolutions(device_index)

  if not supported_resolutions:
    logging.error('Error retrieving formats and resolutions')
    return []

  dut = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)

  res = setup_for_test_fps(dut, supported_resolutions)

  dut.release()
  print(res)
  return res


if __name__ == '__main__':
  main()
