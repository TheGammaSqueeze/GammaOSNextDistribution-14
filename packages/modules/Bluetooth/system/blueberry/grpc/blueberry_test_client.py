"""Blueberry Test Client.

Simple gRPC client to test the Blueberry Mock server.
"""

from absl import app
from absl import flags

import grpc

# Internal import
from blueberry.grpc.proto import blueberry_device_controller_pb2
from blueberry.grpc.proto import blueberry_device_controller_pb2_grpc

FLAGS = flags.FLAGS
flags.DEFINE_string('server', 'dns:///[::1]:10000', 'server address')


def _UpdateDiscoveryMode(stub, request):
  try:
    print('try SetDiscoverableMode')
    response = stub.SetDiscoverableMode(request)
    print('complete response')
    print(response)
    return 0
  except grpc.RpcError as rpc_error:
    print(rpc_error)
    return -1


def main(unused_argv):
  channel_creds = loas2.loas2_channel_credentials()
  with grpc.secure_channel(FLAGS.server, channel_creds) as channel:
    grpc.channel_ready_future(channel).result()
    stub = blueberry_device_controller_pb2_grpc.BlueberryDeviceControllerStub(
        channel)

    print('request grpc')
    request = blueberry_device_controller_pb2.DiscoverableMode(
        mode=True)
    print('Call _UpdateDiscoveryMode')
    return _UpdateDiscoveryMode(stub, request)


if __name__ == '__main__':
  app.run(main)
