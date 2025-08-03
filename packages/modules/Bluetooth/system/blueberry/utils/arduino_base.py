"""Base class for Blueberry controllers using Arduino board.

This module uses pyserial library to communicate with Arduino UNO board.

About Arduino code, please refer to the code of following Arduino project:
Internal link
"""

import time
from typing import Dict
from mobly.signals import ControllerError
import serial


class ArduinoBase(object):
  """Implements an Arduino base class.

  Attributes:
    config: A device configuration.
    serial: serial object, a serial object which is used to communicate with
      Arduino board.
  """

  def __init__(self, config: Dict[str, str]):
    """Initializes an Arduino base class."""
    self._verify_config(config)
    self.config = config
    self.serial = serial.Serial(config['arduino_port'], 9600)
    self.serial.timeout = 30
    # Buffer between calling serial.Serial() and serial.Serial.write().
    time.sleep(2)

  def _verify_config(self, config):
    """Checks the device config's required config parameters.

    Args:
      config: dict, Mobly controller config for ArduinoBass. The config should
        include the key "arduino_port" whose value is a string representing
        Arduino board name. e.g. /dev/ttyACM0.
    """
    if 'arduino_port' not in config:
      raise ControllerError('Please provide an Arduino board port for the'
                            ' ArduinoBase in Mobile Harness config')

  def _send_string_to_arduino(self, tx_string):
    """Sends a particular string to communicate with Arduino.

    The method requires that Arduino code can read string which is received from
    a python serial object and then send the same string to the serial object.

    An example of Arduino code:
      String kRxString = "";
      void setup() {
        ...
      }
      void loop() {
        if (Serial.available() > 0) {
          kRxString = Serial.readString();
          ...
          Serial.write(kRxString.c_str());
        }
      }

    Args:
      tx_string: string, is used to be sent to Arduino port for making the
        controlled device perform action. After Arduino receives the string, it
        will send a response which is the same string.

    Returns:
      The time it takes for waiting a response, in seconds.

    Raises:
      ControllerError: raised if not received a response from Arduino.
    """
    self.serial.write(str.encode(tx_string))
    start_time = time.time()
    rx_string = self.serial.read_until(tx_string, len(tx_string)).decode()
    if rx_string == tx_string:
      return time.time() - start_time
    raise ControllerError('Timed out after %ds waiting for the string "%s" from'
                          ' Arduino.' % (self.serial.timeout, tx_string))
