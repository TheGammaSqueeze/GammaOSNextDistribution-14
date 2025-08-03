# Copyright 2021 The Android Open Source Project
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


import logging
import select
import struct
import sys
import time
import sensor_fusion_utils

# Constants for Arduino
ARDUINO_BRIGHTNESS_MAX = 255
ARDUINO_BRIGHTNESS_MIN = 0
ARDUINO_LIGHT_START_BYTE = 254

KEYBOARD_ENTRY_WAIT_TIME = 20  # seconds to wait for keyboard entry


def _toggle_screen_state(device, desired_state):
  """Trigger device power key to toggle screen."""
  current_state = 'ON'
  if desired_state == 'ON':
    current_state = 'OFF'
  output = device.adb.shell('dumpsys display | grep mScreenState=')
  output_val = str(output.decode('utf-8')).strip()
  if current_state in output_val:
    device.adb.shell(['input', 'keyevent', 'KEYCODE_POWER'])


def turn_off_device(device):
  """Turn off a device via power key if screen is on."""
  _toggle_screen_state(device, 'OFF')


def turn_on_device(device):
  """Turn on a device via power key if screen is on."""
  _toggle_screen_state(device, 'ON')


def set_light_brightness(ch, brightness, serial_port, delay=0):
  """Turn on light to specified brightness.

  Args:
    ch: str; light to turn on in ARDUINO_VALID_CH
    brightness: int value of brightness between 0 and 255.
    serial_port: object; serial port
    delay: int; time in seconds
  """
  if brightness < ARDUINO_BRIGHTNESS_MIN:
    logging.debug('Brightness must be >= %d.', ARDUINO_BRIGHTNESS_MIN)
    brightness = ARDUINO_BRIGHTNESS_MIN
  elif brightness > ARDUINO_BRIGHTNESS_MAX:
    logging.debug('Brightness must be <= %d.', ARDUINO_BRIGHTNESS_MAX)
    brightness = ARDUINO_BRIGHTNESS_MAX

  cmd = [struct.pack('B', i) for i in [
      ARDUINO_LIGHT_START_BYTE, int(ch), brightness]]
  sensor_fusion_utils.arduino_send_cmd(serial_port, cmd)
  time.sleep(delay)


def lighting_control(lighting_cntl, lighting_ch):
  """Establish communication with lighting controller.

  lighting_ch is hard wired and must be determined from physical setup.

  First initialize the port and send a test string defined by ARDUINO_TEST_CMD
  to establish communications.

  Args:
    lighting_cntl: str to identify 'arduino' controller.
    lighting_ch: str to identify lighting channel number.
  Returns:
    serial port pointer
  """

  logging.debug('Controller: %s, ch: %s', lighting_cntl, lighting_ch)
  if lighting_cntl.lower() == 'arduino':
    # identify port
    arduino_serial_port = sensor_fusion_utils.serial_port_def('arduino')

    # send test cmd to Arduino until cmd returns properly
    sensor_fusion_utils.establish_serial_comm(arduino_serial_port)

    # return serial port
    return arduino_serial_port

  else:
    logging.debug('No lighting control: need to control lights manually.')
    return None


def set_lighting_state(arduino_serial_port, lighting_ch, state):
  """Turn lights ON in test rig.

  Args:
    arduino_serial_port: serial port object
    lighting_ch: str for lighting channel
    state: str 'ON/OFF'
  """
  if state == 'ON':
    level = 255
  elif state == 'OFF':
    level = 0
  else:
    raise AssertionError(f'Lighting state not defined correctly: {state}')

  if arduino_serial_port:
    set_light_brightness(lighting_ch, level, arduino_serial_port, delay=1)
  else:
    print(f'Turn {state} lights in rig and hit <ENTER> to continue.')
    _, _, _ = select.select([sys.stdin], [], [], KEYBOARD_ENTRY_WAIT_TIME)

