"""gRPC mock target for testing purposes.

Example MH testbed config for Hostside:
- name: GrpcBtTargetStub-1
  devices:
  - type: MiscTestbedSubDevice
    dimensions:
      mobly_type: DerivedBtDevice
    properties:
      ModuleName: grpc_bt_target_mock
      ClassName: GrpcBtTargetMock
      Params:
        config:
          mac_address: FE:ED:BE:EF:CA:FE
  dimensions:
    device: GrpcBtTargetStub
"""

import subprocess
from typing import Dict

from absl import flags
from absl import logging
import grpc

# Internal import
from blueberry.grpc.proto import blueberry_device_controller_pb2
from blueberry.grpc.proto import blueberry_device_controller_pb2_grpc


FLAGS = flags.FLAGS


class GrpcBtTargetMock(object):
  """BT Mock Target for testing the GRPC interface."""

  def __init__(self, config: Dict[str, str]) -> None:
    """Initialize GRPC object."""
    super(GrpcBtTargetMock, self).__init__()
    self.config = config
    self.mac_address = config['mac_address']

  def __del__(self) -> None:
    # pytype: disable=attribute-error
    self.server_proc.terminate()
    del self.channel_creds
    del self.channel
    del self.stub

  def setup(self) -> None:
    """Setup the gRPC server that the target mock will respond to."""
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

  def activate_pairing_mode(self) -> int:
    """Activates Bluetooth pairing mode."""
    logging.info('activate pairing mode TO BE IMPLEMENTED')
    request = blueberry_device_controller_pb2.DiscoverableMode(mode=True)
    try:
      # pytype: disable=attribute-error
      response = self.stub.SetDiscoverableMode(request)
      logging.info('set discoverageble response: %s', response)
      return 0
    except grpc.RpcError as rpc_error:
      print(rpc_error)
      return -1

  def factory_reset_bluetooth(self) -> None:
    logging.info('factory reset TO BE IMPLEMENTED')

  def get_bluetooth_mac_address(self) -> str:
    logging.info('mac_address: %s', self.mac_address)
    return self.mac_address
