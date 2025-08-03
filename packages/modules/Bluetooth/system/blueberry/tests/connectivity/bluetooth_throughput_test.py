# Lint as: python3
"""Tests for blueberry.tests.bluetooth.bluetooth_throughput."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import math

from mobly import asserts
from mobly import test_runner
from mobly.controllers.android_device_lib.jsonrpc_client_base import ApiError
from mobly.signals import TestAbortClass
# Internal import
from blueberry.utils import blueberry_base_test
from blueberry.utils import metrics_utils
# Internal import


class BluetoothThroughputTest(blueberry_base_test.BlueberryBaseTest):

  @retry.logged_retry_on_exception(
      retry_intervals=retry.FuzzedExponentialIntervals(
          initial_delay_sec=2, factor=5, num_retries=5, max_delay_sec=300))
  def _measure_throughput(self, num_of_buffers, buffer_size):
    """Measures the throughput of a data transfer.

    Sends data from the client device that is read by the server device.
    Calculates the throughput for the transfer.

    Args:
        num_of_buffers: An integer value designating the number of buffers
                      to be sent.
        buffer_size: An integer value designating the size of each buffer,
                   in bytes.

    Returns:
        The throughput of the transfer in bytes per second.
    """

    # TODO(user): Need to fix throughput send/receive methods
    (self.phone.sl4a
     .bluetoothConnectionThroughputSend(num_of_buffers, buffer_size))

    throughput = (self.derived_bt_device.sl4a
                  .bluetoothConnectionThroughputRead(num_of_buffers,
                                                     buffer_size))
    return throughput

  def _throughput_test(self, buffer_size, test_name):
    logging.info('throughput test with buffer_size: %d and testname: %s',
                 buffer_size, test_name)
    metrics = {}
    throughput_list = []
    num_of_buffers = 1
    for _ in range(self.iterations):
      throughput = self._measure_throughput(num_of_buffers, buffer_size)
      logging.info('Throughput: %d bytes-per-sec', throughput)
      throughput_list.append(throughput)

    metrics['data_transfer_protocol'] = self.data_transfer_type
    metrics['data_packet_size'] = buffer_size
    metrics['data_throughput_min_bytes_per_second'] = int(
        min(throughput_list))
    metrics['data_throughput_max_bytes_per_second'] = int(
        max(throughput_list))
    metrics['data_throughput_avg_bytes_per_second'] = int(
        math.fsum(throughput_list) / float(len(throughput_list)))

    logging.info('Throughput at large buffer: %s', metrics)

    asserts.assert_true(metrics['data_throughput_min_bytes_per_second'] > 0,
                        'Minimum throughput must be greater than 0!')

    self.metrics.add_test_metrics(metrics)
    for metric in metrics:
      self.record_data({
          'Test Name': test_name,
          'sponge_properties': {
              metric: metrics[metric],
          }
      })
    self.record_data({
        'Test Name': test_name,
        'sponge_properties': {
            'proto_ascii':
                self.metrics.proto_message_to_ascii(),
            'primary_device_build':
                self.phone.get_device_info()['android_release_id']
        }
    })

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothThroughputTest, self).setup_class()
    if len(self.android_devices) < 2:
      raise TestAbortClass(
          'Not enough android phones detected (need at least two)')
    self.phone = self.android_devices[0]

    # We treat the secondary phone as a derived_bt_device in order for the
    # generic script to work with this android phone properly. Data will be sent
    # from first phone to the second phone.
    self.derived_bt_device = self.android_devices[1]
    self.phone.init_setup()
    self.derived_bt_device.init_setup()
    self.phone.sl4a_setup()
    self.derived_bt_device.sl4a_setup()
    self.set_btsnooplogmode_full(self.phone)
    self.set_btsnooplogmode_full(self.derived_bt_device)

    self.metrics = (
        metrics_utils.BluetoothMetricLogger(
            metrics_pb2.BluetoothDataTestResult()))
    self.metrics.add_primary_device_metrics(self.phone)
    self.metrics.add_connected_device_metrics(self.derived_bt_device)

    self.data_transfer_type = metrics_pb2.BluetoothDataTestResult.RFCOMM
    self.iterations = int(self.user_params.get('iterations', 300))
    logging.info('Running Bluetooth throughput test %s times.', self.iterations)
    logging.info('Successfully found required devices.')

  def setup_test(self):
    """Setup for bluetooth latency test."""
    logging.info('Setup Test for test_bluetooth_throughput')
    super(BluetoothThroughputTest, self).setup_test()
    asserts.assert_true(self.phone.connect_with_rfcomm(self.derived_bt_device),
                        'Failed to establish RFCOMM connection')

  def test_bluetooth_throughput_large_buffer(self):
    """Tests the throughput with large buffer size.

    Tests the throughput over a series of data transfers with large buffer size.
    """
    large_buffer_size = 300
    test_name = 'test_bluetooth_throughput_large_buffer'
    self._throughput_test(large_buffer_size, test_name)

  def test_bluetooth_throughput_medium_buffer(self):
    """Tests the throughput with medium buffer size.

    Tests the throughput over a series of data transfers with medium buffer
    size.
    """
    medium_buffer_size = 100
    test_name = 'test_bluetooth_throughput_medium_buffer'
    self._throughput_test(medium_buffer_size, test_name)

  def test_bluetooth_throughput_small_buffer(self):
    """Tests the throughput with small buffer size.

    Tests the throughput over a series of data transfers with small buffer size.
    """
    small_buffer_size = 10
    test_name = 'test_bluetooth_throughput_small_buffer'
    self._throughput_test(small_buffer_size, test_name)

  def test_maximum_buffer_size(self):
    """Calculates the maximum allowed buffer size for one packet."""
    current_buffer_size = 300
    throughput = -1
    num_of_buffers = 1
    while True:
      logging.info('Trying buffer size %d', current_buffer_size)
      try:
        throughput = self._measure_throughput(
            num_of_buffers, current_buffer_size)
        logging.info('The throughput is %d at buffer size of %d', throughput,
                     current_buffer_size)
      except ApiError:
        maximum_buffer_size = current_buffer_size - 1
        logging.info('Max buffer size: %d bytes', maximum_buffer_size)
        logging.info('Max throughput: %d bytes-per-second', throughput)
        self.record_data({
            'Test Name': 'test_maximum_buffer_size',
            'sponge_properties': {
                'maximum_buffer_size': maximum_buffer_size
            }
        })
        return True
      current_buffer_size += 1

  def teardown_test(self):
    self.phone.sl4a.bluetoothSocketConnStop()
    self.derived_bt_device.sl4a.bluetoothSocketConnStop()

  def teardown_class(self):
    self.phone.factory_reset_bluetooth()
    self.derived_bt_device.factory_reset_bluetooth()
    logging.info('Factory resetting Bluetooth on devices.')
    super(BluetoothThroughputTest, self).teardown_class()


if __name__ == '__main__':
  test_runner.main()
