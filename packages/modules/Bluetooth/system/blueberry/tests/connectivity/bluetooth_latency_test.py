# Lint as: python3
"""Tests for blueberry.tests.bluetooth.bluetooth_latency."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import math
import random
import string
import time

from mobly import asserts
from mobly import test_runner
from mobly.signals import TestAbortClass
# Internal import
from blueberry.utils import blueberry_base_test
from blueberry.utils import bt_test_utils
from blueberry.utils import metrics_utils
# Internal import


class BluetoothLatencyTest(blueberry_base_test.BlueberryBaseTest):

  @retry.logged_retry_on_exception(
      retry_intervals=retry.FuzzedExponentialIntervals(
          initial_delay_sec=2, factor=5, num_retries=5, max_delay_sec=300))
  def _measure_latency(self):
    """Measures the latency of data transfer over RFCOMM.

    Sends data from the client device that is read by the server device.
    Calculates the latency of the transfer.

    Returns:
        The latency of the transfer milliseconds.
    """

    # Generates a random message to transfer
    message = (''.join(
        random.choice(string.ascii_letters + string.digits) for _ in range(6)))
    start_time = time.time()
    write_read_successful = bt_test_utils.write_read_verify_data_sl4a(
        self.phone, self.derived_bt_device, message, False)
    end_time = time.time()
    asserts.assert_true(write_read_successful, 'Failed to send/receive message')
    return (end_time - start_time) * 1000

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothLatencyTest, self).setup_class()
    if len(self.android_devices) < 2:
      raise TestAbortClass(
          'Not enough android phones detected (need at least two)')
    self.phone = self.android_devices[0]
    self.phone.init_setup()
    self.phone.sl4a_setup()

    # We treat the secondary phone as a derived_bt_device in order for the
    # generic script to work with this android phone properly. Data will be sent
    # from first phone to the second phone.
    self.derived_bt_device = self.android_devices[1]
    self.derived_bt_device.init_setup()
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
    logging.info('Running Bluetooth latency test %s times.', self.iterations)
    logging.info('Successfully found required devices.')

  def setup_test(self):
    """Setup for bluetooth latency test."""
    logging.info('Setup Test for test_bluetooth_latency')
    super(BluetoothLatencyTest, self).setup_test()
    asserts.assert_true(self.phone.connect_with_rfcomm(self.derived_bt_device),
                        'Failed to establish RFCOMM connection')

  def test_bluetooth_latency(self):
    """Tests the latency for a data transfer over RFCOMM."""

    metrics = {}
    latency_list = []

    for _ in range(self.iterations):
      latency_list.append(self._measure_latency())

    metrics['data_transfer_protocol'] = self.data_transfer_type
    metrics['data_latency_min_millis'] = int(min(latency_list))
    metrics['data_latency_max_millis'] = int(max(latency_list))
    metrics['data_latency_avg_millis'] = int(
        math.fsum(latency_list) / float(len(latency_list)))
    logging.info('Latency: %s', metrics)

    asserts.assert_true(metrics['data_latency_min_millis'] > 0,
                        'Minimum latency must be greater than 0!')
    self.metrics.add_test_metrics(metrics)
    for metric in metrics:
      self.record_data({
          'Test Name': 'test_bluetooth_latency',
          'sponge_properties': {
              metric: metrics[metric],
          }
      })

  def teardown_class(self):
    logging.info('Factory resetting Bluetooth on devices.')
    self.phone.sl4a.bluetoothSocketConnStop()
    self.derived_bt_device.sl4a.bluetoothSocketConnStop()
    self.phone.factory_reset_bluetooth()
    self.derived_bt_device.factory_reset_bluetooth()
    super(BluetoothLatencyTest, self).teardown_class()
    self.record_data({
        'Test Name': 'test_bluetooth_latency',
        'sponge_properties': {
            'proto_ascii':
                self.metrics.proto_message_to_ascii(),
            'primary_device_build':
                self.phone.get_device_info()['android_release_id']
        }
    })


if __name__ == '__main__':
  test_runner.main()
