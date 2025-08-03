"""Network simulator Python gRPC client."""

import logging
import os
import platform
from typing import Dict, Optional

from google.protobuf import empty_pb2
import grpc
from netsim_grpc.proto.netsim import (
    common_pb2 as common,
    frontend_pb2_grpc as frontend_grpc,
    frontend_pb2 as frontend,
    model_pb2 as model,
)

_Empty = empty_pb2.Empty
_Channel = grpc.Channel

NETSIM_INI = 'netsim.ini'


class SetupError(Exception):
  """Class for exceptions related to netsim setup."""


class NetsimClient(object):
  """Network simulator client."""

  def __init__(self):
    """Create a NetsimClient.

    Args:
      local_creds: Use local credentials for gRPC channel.
    """
    self._server_addr = _get_grpc_server_addr()
    self._channel = _create_frontend_grpc_channel(self._server_addr)
    self._stub = frontend_grpc.FrontendServiceStub(self._channel)

  def get_version(self) -> str:
    """Get the version of the netsim daemon.

    Returns:
      The netsim daemon version.
    """
    return self._stub.GetVersion(_Empty()).version

  def get_devices(self) -> Dict[str, model.Device]:
    """Get info for all devices connected to netsim.

    Returns:
      A dict mapping each connected device to its netsim properties.
    """
    response = self._stub.ListDevice(_Empty())
    return {device.name: device for device in response.devices}

  def set_position(
      self,
      device_name: str,
      position: Optional[model.Position] = None,
      orientation: Optional[model.Orientation] = None,
  ) -> bool:
    """Set the position and/or orientation of the specified device.

    NOTE: Leaving the position/orientation unset would reset the device's
      position/orientation to zero.

    Args:
      device_name: The avd name of the specified device.
      position: The desired (x, y, z) position of the device.
      orientation: The desired (yaw, pitch, roll) orientation of the device.

    Returns:
      bool indicating whether device position was successfully set.
    """
    request = frontend.PatchDeviceRequest()
    request.device.name = device_name
    if position:
      logging.info(
          'Setting new position for device %s: %s', device_name, position
      )
      request.device.position.x = position.x
      request.device.position.y = position.y
      request.device.position.z = position.z
    if orientation:
      logging.info(
          'Setting new orientation for device %s: %s', device_name, orientation
      )
      request.device.orientation.yaw = orientation.yaw
      request.device.orientation.pitch = orientation.pitch
      request.device.orientation.roll = orientation.roll
    self._stub.PatchDevice(request)
    device_info = self.get_devices()[device_name]
    success = True
    if position and device_info.position != position:
      logging.error(
          'Device %s position not set as expected. Current position: %s',
          device_name,
          device_info.position,
      )
      success = False
    if orientation and device_info.orientation != orientation:
      logging.error(
          'Device %s orientation not set as expected. Current orientation: %s',
          device_name,
          device_info.orientation,
      )
      success = False
    return success

  def set_radio(
      self, device_name: str, radio: model.PhyKind, state: bool
  ) -> None:
    """Set the radio state of the specified device.

    Args:
      device_name: The avd name of the specified device.
      radio: The specified radio, e.g. BLUETOOTH_LOW_ENERGY, WIFI
      state: Set radio state UP if True, DOWN if False.
    """
    chip = model.Chip()
    state = model.State.ON if state else model.State.OFF

    if radio == model.PhyKind.WIFI:
      chip.wifi.state = state
      chip.kind = common.ChipKind.WIFI
    elif radio == model.PhyKind.UWB:
      chip.uwb.state = state
      chip.kind = common.ChipKind.UWB
    else:
      if radio == model.PhyKind.BLUETOOTH_LOW_ENERGY:
        chip.bt.low_energy.state = state
      elif radio == model.PhyKind.BLUETOOTH_CLASSIC:
        chip.bt.classic.state = state
      chip.kind = common.ChipKind.BLUETOOTH

    request = frontend.PatchDeviceRequest()
    request.device.name = device_name
    request.device.chips.append(chip)
    self._stub.PatchDevice(request)

  def reset(self) -> None:
    """Reset all devices."""
    self._stub.Reset(_Empty())

  def close(self) -> None:
    """Close the netsim client connection."""
    if hasattr(self, '_channel'):
      self._channel.close()

  def __del__(self) -> None:
    self.close()


def _get_grpc_server_addr() -> str:
  """Locate the grpc server address from netsim's .ini file."""
  # TMPDIR is set on buildbots
  file_path = os.path.join('/tmp', NETSIM_INI)
  if 'TMPDIR' in os.environ and os.path.exists(
      os.path.join(os.environ['TMPDIR'], NETSIM_INI)
  ):
    file_path = os.path.join(os.environ['TMPDIR'], NETSIM_INI)
  # XDG_RUNTIME_DIR for Linux local discovery env
  elif platform.system() == 'Linux' and 'XDG_RUNTIME_DIR' in os.environ:
    file_path = os.path.join(os.environ['XDG_RUNTIME_DIR'], NETSIM_INI)
  # HOME for Mac local discovery
  elif platform.system() == 'Darwin' and 'HOME' in os.environ:
    file_path = os.path.join(
        os.environ['HOME'], 'Library/Caches/TemporaryItems', NETSIM_INI
    )
  # LOCALAPPDATA for Windows local discovery
  elif platform.system() == 'Windows' and 'LOCALAPPDATA' in os.environ:
    file_path = os.path.join(os.environ['LOCALAPPDATA'], 'Temp', NETSIM_INI)
  else:
    logging.warning(
        'TMPDIR, XDG_RUNTIME_DIR, HOME, or LOCALAPPDATA environment variable'
        ' not set.Using /tmp. Is netsimd running?'
    )
  if not os.path.exists(file_path):
    raise SetupError(
        f'Unable to find the netsim.ini file at {file_path}. Is netsimd'
        ' running?',
    )
  with open(file_path, 'r') as f:
    for line in f:
      key, value = line.strip().split('=')
      if key == 'grpc.port':
        logging.info('Found netsim server gRPC port: %s.', value)
        return f'localhost:{value}'
  raise SetupError(
      'Unable to find the netsim server address from the .ini file.'
  )


def _create_frontend_grpc_channel(
    server_addr: str,
) -> _Channel:
  """Creates a gRPC channel to communicate with netsim FE service.

  Args:
    server_addr: Endpoint address of the netsim server.

  Returns:
    gRPC channel
  """
  logging.info(
      'Creating gRPC channel for netsim frontend service at %s.', server_addr
  )
  return grpc.insecure_channel(server_addr)
