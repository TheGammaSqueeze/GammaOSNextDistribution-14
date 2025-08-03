"""Blueberry gRPC device controller.

This is a server to act as a mock device for testing the Blueberry gRPC
interface.
"""

from concurrent import futures
from absl import app
from absl import flags

import grpc

# Internal import
from blueberry.grpc import blueberry_device_controller_service
from blueberry.grpc.proto import blueberry_device_controller_pb2_grpc


_HOST = '[::]'

FLAGS = flags.FLAGS
flags.DEFINE_integer('port', 10000, 'port to listen on')
flags.DEFINE_integer('threads', 10, 'number of worker threads in thread pool')


def main(unused_argv):
  server = grpc.server(
      futures.ThreadPoolExecutor(max_workers=FLAGS.threads),
      ports=(FLAGS.port,))  # pytype: disable=wrong-keyword-args
  servicer = (
      blueberry_device_controller_service.BlueberryDeviceControllerServicer())
  blueberry_device_controller_pb2_grpc.add_BlueberryDeviceControllerServicer_to_server(
      servicer, server)
  server_creds = loas2.loas2_server_credentials()
  server.add_secure_port(f'{_HOST}:{FLAGS.port}', server_creds)
  server.start()
  server.wait_for_termination()


if __name__ == '__main__':
  app.run(main)
