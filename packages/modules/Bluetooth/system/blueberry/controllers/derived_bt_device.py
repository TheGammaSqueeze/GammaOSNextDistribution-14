# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Controller class for a Bluetooth Device.

This controller will instantiate derived classes from BtDevice and the
module/class specified via strings in configs dictionary.

The idea is to allow vendors to run blueberry tests with their controller class
through this controller module, eliminating the need to edit the test classes
themselves.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import importlib
import logging
from typing import Any, Dict, List, Sequence

import yaml


MOBLY_CONTROLLER_CONFIG_NAME = 'DerivedBtDevice'
MOBLY_CONTROLLER_CONFIG_MODULE_KEY = 'ModuleName'
MOBLY_CONTROLLER_CONFIG_CLASS_KEY = 'ClassName'
MOBLY_CONTROLLER_CONFIG_PARAMS_KEY = 'Params'


def create(configs: List[Dict[str, Any]]) -> List[Any]:
  """Creates DerivedBtDevice controller objects.

  For each config dict in configs:
    Import desired controller class from config, compose DerivedBtDevice class
    from that class and BtDevice, instantiate with params from config.

  Args:
    configs (list): A list of dicts, each representing a configuration for a
        Bluetooth device. Each dict should be of the format:
          {"ModuleName": <name of module in blueberry.controllers>,
           "ClassName": <name of class to derive controller from>,
           "Params": <kwargs in dict form to instantiate class with>}

  Returns:
    A list with DerivedBtDevice objects.
  """
  return [_create_bt_device_class(config) for config in configs]


def _create_bt_device_class(config: Dict[str, Any]) -> Any:
  """Created new device class from associated device controller from config."""
  module = importlib.import_module(
      'blueberry.controllers.%s' %
      config[MOBLY_CONTROLLER_CONFIG_MODULE_KEY])
  logging.info('Creating DerivedBtDevice from %r', config)
  cls = getattr(module, config[MOBLY_CONTROLLER_CONFIG_CLASS_KEY])
  params = yaml.safe_load('%s' %
                          config.get(MOBLY_CONTROLLER_CONFIG_PARAMS_KEY, {}))
  new_class = type(MOBLY_CONTROLLER_CONFIG_NAME, (cls, BtDevice), params)
  return new_class(**params)


def destroy(derived_bt_devices: Sequence[Any])-> None:
  """Cleans up DerivedBtDevice objects."""
  for device in derived_bt_devices:
    # Execute cleanup if the controller class has the method "clean_up".
    if hasattr(device, 'clean_up'):
      device.clean_up()
  del derived_bt_devices


class BtDevice(object):
  """Base class for all Bluetooth Devices.

  Provides additional necessary functionality for use within blueberry.
  """

  def __init__(self) -> None:
    """Initializes a derived bt base class."""
    self._user_params = {}

  def setup(self) -> None:
    """For devices that need extra setup."""

  def set_user_params(self, params: Dict[str, str]) -> None:
    """Intended for passing mobly user_params into a derived device class.

    Args:
      params: Mobly user params.
    """
    self._user_params = params

  def get_user_params(self) -> Dict[str, str]:
    """Return saved user_params.

    Returns:
      user_params.
    """
    return self._user_params

  def factory_reset_bluetooth(self) -> None:
    """Factory resets Bluetooth on an BT Device."""
    raise NotImplementedError

  def activate_pairing_mode(self) -> None:
    """Activates pairing mode on an AndroidDevice."""
    raise NotImplementedError

  def get_bluetooth_mac_address(self) -> None:
    """Get bluetooth mac address of an BT Device."""
    pass
