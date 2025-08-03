"""Blueberry gRPC Mock Service.

This is simple mock service that is used to verify the implementation of the
Blueberry gRPC device controller interface.
"""

from blueberry.grpc.proto import blueberry_device_controller_pb2
from blueberry.grpc.proto import blueberry_device_controller_pb2_grpc


class BlueberryDeviceControllerServicer(
    blueberry_device_controller_pb2_grpc.BlueberryDeviceControllerServicer):
  """A BlueberryTest gRPC server."""

  def __init__(self, *args, **kwargs):
    super(BlueberryDeviceControllerServicer, self).__init__(*args, **kwargs)
    self._error = "testing 123"

  def SetDiscoverableMode(self, request, servicer_context):
    """Sets the device's discoverable mode.

    Args:
      request: a blueberry_test_server_pb2.DiscoverableMode object containing
        the "mode" to set the device to.
      servicer_context: A grpc.ServicerContext for use during service of the
        RPC.

    Returns:
      A blueberry_test_server_pb2.DiscoverableResult
    """
    return blueberry_device_controller_pb2.DiscoverableResult(
        result=True,
        error=self._error)

  def PairAndConnectBluetooth(self, request, servicer_context):
    return blueberry_device_controller_pb2.PairAndConnectBluetoothResult(
        pairing_time_sec=0.1, connection_time_sec=0.2, error=None)
