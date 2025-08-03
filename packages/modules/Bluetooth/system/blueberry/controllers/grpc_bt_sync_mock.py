"""A generic gRPC mock device controller.

Example MH testbed config for Hostside:
- name: GrpcBtSyncStub-1
  devices:
  - type: MiscTestbedSubDevice
    dimensions:
      mobly_type: DerivedBtDevice
    properties:
      ModuleName: grpc_bt_sync_mock
      ClassName: GrpcBtSyncMock
      Params:
        config:
          mac_address: FE:ED:BE:EF:CA:FE
  dimensions:
    device: GrpcBtSyncStub
"""

import subprocess
from typing import Any, Dict, Optional, Tuple

from absl import flags
from absl import logging
import grpc

# Internal import
from blueberry.grpc.proto import blueberry_device_controller_pb2
from blueberry.grpc.proto import blueberry_device_controller_pb2_grpc


FLAGS = flags.FLAGS
flags.DEFINE_string('server', 'dns:///[::1]:10000', 'server address')


class GrpcBtSyncMock(object):
  """Generic GRPC device controller."""

  def __init__(self, config: Dict[str, str]) -> None:
    """Initialize GRPC object."""
    super(GrpcBtSyncMock, self).__init__()
    self.config = config
    self.mac_address = config['mac_address']

  def __del__(self) -> None:
    # pytype: disable=attribute-error
    self.server_proc.terminate()
    del self.channel_creds
    del self.channel
    del self.stub

  def setup(self) -> None:
    """Setup the gRPC server that the sync mock will respond to."""
    # pytype: disable=attribute-error
    server_path = self.get_user_params()['mh_files']['grpc_server'][0]
    logging.info('Start gRPC server: %s', server_path)
    self.server_proc = subprocess.Popen([server_path],
                                        stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE,
                                        universal_newlines=True,
                                        bufsize=0)

    self.channel_creds = loas2.loas2_channel_credentials()
    self.channel = grpc.secure_channel(FLAGS.server, self.channel_creds)
    grpc.channel_ready_future(self.channel).result()
    self.stub = blueberry_device_controller_pb2_grpc.BlueberryDeviceControllerStub(
        self.channel)

  def init_setup(self) -> None:
    logging.info('init setup TO BE IMPLEMENTED')

  def set_target(self, bt_device: Any) -> None:
    self._target_device = bt_device

  def pair_and_connect_bluetooth(
      self, target_mac_address: str) -> Optional[Tuple[float, float]]:
    """Pair and connect to a peripheral Bluetooth device."""
    request = blueberry_device_controller_pb2.TargetMacAddress(
        mac_address=target_mac_address)
    try:
      # pytype: disable=attribute-error
      response = self.stub.PairAndConnectBluetooth(request)
      logging.info('pair and connect bluetooth response: %s', response)
      if response.error:
        print('error handler TO BE IMPLEMENTED')
      else:
        return response.pairing_time_sec, response.connection_time_sec
    except grpc.RpcError as rpc_error:
      print(rpc_error)
