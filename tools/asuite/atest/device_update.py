# Copyright 2023, The Android Open Source Project
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

"""Device update methods used to prepare the device under test."""

from abc import ABC, abstractmethod
from pathlib import Path
import subprocess
from subprocess import CalledProcessError
import time
from typing import List, Set

from atest import atest_utils
from atest import constants


class DeviceUpdateMethod(ABC):
  """A device update method used to update device."""

  @abstractmethod
  def update(self, serials: List[str] = None):
    """Updates the device.

    Args:
        serials: A list of serial numbers.

    Raises:
        Error: If the device update fails.
    """

  @abstractmethod
  def dependencies(self) -> Set[str]:
    """Returns the dependencies required by this device update method."""


class NoopUpdateMethod(DeviceUpdateMethod):

  def update(self, serials: List[str] = None) -> None:
    pass

  def dependencies(self) -> Set[str]:
    return set()


class AdeviceUpdateMethod(DeviceUpdateMethod):
  _TOOL = 'adevice'

  def __init__(self, adevice_path: Path=_TOOL, targets: Set[str]=None):
    self._adevice_path = adevice_path
    self._targets = targets or set()

  def update(self, serials: List[str] = None) -> None:
    try:
      print(atest_utils.mark_cyan('\nUpdating device...'))
      update_start = time.time()

      update_cmd = [self._adevice_path, 'update']
      if serials:
        if len(serials) > 1:
          atest_utils.colorful_print(
              'Warning: Device update feature can only update one '
              'device for now, but this invocation specifies more '
              'than one device. Atest will update the first device '
              'by default.',
              constants.YELLOW,
          )

        update_cmd.extend(['--serial', serials[0]])

      subprocess.check_call(update_cmd)

      print(
          atest_utils.mark_cyan(
              '\nDevice update finished in '
              f'{str(round(time.time() - update_start, 2))}s.'
          )
      )

    except CalledProcessError as e:
      raise Error('Failed to update the device with adevice') from e

  def dependencies(self) -> Set[str]:
    return self._targets.union({self._TOOL})


class Error(Exception):
  pass
