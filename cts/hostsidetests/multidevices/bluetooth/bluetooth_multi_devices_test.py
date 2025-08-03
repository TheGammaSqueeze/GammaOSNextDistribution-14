# Lint as: python3
"""
Bluetooth multi devices tests.
"""
import sys
import os.path
import time
import re

import logging
logging.basicConfig(filename="/tmp/bluetooth_multi_devices_test_log.txt", level=logging.INFO)

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

BLUETOOTH_MULTI_DEVICES_SNIPPET_PACKAGE = 'com.google.snippet.bluetooth'

SERVICE_UUID_1 = "0000fffb-0000-1000-8000-00805f9b34fc"
SERVICE_UUID_2 = "0000fffb-0000-1000-8000-00805f9b34fd"

class BluetoothMultiDevicesTest(base_test.BaseTestClass):

  def setup_class(self):
    # Declare that two Android devices are needed.
    self.client, self.server = self.register_controller(
      android_device, min_number=2)

    def setup_device(device):
      device.load_snippet('bluetooth_multi_devices_snippet', BLUETOOTH_MULTI_DEVICES_SNIPPET_PACKAGE)

    # Set up devices in parallel to save time.
    utils.concurrent_exec(
        setup_device, ((self.client,), (self.server,)),
        max_workers=2,
        raise_on_exception=True)

  def setup_test(self):
    self.server.bluetooth_multi_devices_snippet.disableBluetooth()
    self.client.bluetooth_multi_devices_snippet.disableBluetooth()

    # TODO(b/266635827) implement callback
    time.sleep(3)

    asserts.assert_false(self.server.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Server Bluetooth did not stop')
    asserts.assert_false(self.client.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Client Bluetooth did not stop')

    self.server.bluetooth_multi_devices_snippet.enableBluetooth()
    self.client.bluetooth_multi_devices_snippet.enableBluetooth()

    # TODO(b/266635827) implement callback
    time.sleep(3)

    asserts.assert_true(self.server.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Server Bluetooth did not start')
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Client Bluetooth did not start')

    self.server.bluetooth_multi_devices_snippet.reset()
    self.client.bluetooth_multi_devices_snippet.reset()

  def test_normal_gatt_server(self):
    """
    Tests the android.bluetooth.le.BluetoothLeAdvertiser#startAdvertisingSet API.
    """
    # Start and advertise two regular servers
    self.server.bluetooth_multi_devices_snippet.createAndAdvertiseServer(SERVICE_UUID_1)
    self.server.bluetooth_multi_devices_snippet.createAndAdvertiseServer(SERVICE_UUID_2)

    # Connect to the first advertisement
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.connectGatt(SERVICE_UUID_1), "Server not discovered")

    # Check the target UUID is present
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.containsService(SERVICE_UUID_1), "Service not found")
    # Check that the UUID from the other server is *also* present
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.containsService(SERVICE_UUID_2), "Service not found")

  def test_isolated_gatt_server(self):
    """
    Tests the android.bluetooth.le.BluetoothLeAdvertiser#startAdvertisingSet API.
    """
    # Start a server tied to its advertisement
    self.server.bluetooth_multi_devices_snippet.createAndAdvertiseIsolatedServer(SERVICE_UUID_1)
    # Start a second regular server
    self.server.bluetooth_multi_devices_snippet.createAndAdvertiseServer(SERVICE_UUID_2)
    # Connect to the first server
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.connectGatt(SERVICE_UUID_1), "Server not discovered")

    # Check the target UUID is present
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.containsService(SERVICE_UUID_1), "Service not found")
    # Check that the UUID from the other server is NOT present
    asserts.assert_false(self.client.bluetooth_multi_devices_snippet.containsService(SERVICE_UUID_2), "Service unexpectedly found")


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()
