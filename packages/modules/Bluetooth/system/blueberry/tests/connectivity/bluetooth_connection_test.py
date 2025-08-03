"""Tests for Bluetooth connection with Android device and a Bluetooth device."""

import time

from mobly import test_runner
from mobly import signals
from blueberry.utils import asserts
from blueberry.utils import blueberry_base_test

# Connection state change sleep time in seconds.
CONNECTION_STATE_CHANGE_SLEEP_SEC = 5


class BluetoothConnectionTest(blueberry_base_test.BlueberryBaseTest):
  """Test Class for Bluetooth connection testing.

  Attributes:
    primary_device: A primary device under test.
    derived_bt_device: A Bluetooth device which is used to connected to the
        primary device in the test.
  """

  def setup_class(self):
    super().setup_class()
    self.primary_device = self.android_devices[0]
    self.primary_device.init_setup()

    self.derived_bt_device = self.derived_bt_devices[0]
    self.derived_bt_device.factory_reset_bluetooth()
    self.mac_address = self.derived_bt_device.get_bluetooth_mac_address()
    self.derived_bt_device.activate_pairing_mode()
    self.primary_device.pair_and_connect_bluetooth(self.mac_address)

  def setup_test(self):
    super().setup_test()
    # Checks if A2DP and HSP profiles are connected.
    self.assert_a2dp_and_hsp_connection_state(connected=True)
    # Buffer between tests.
    time.sleep(CONNECTION_STATE_CHANGE_SLEEP_SEC)

  def assert_a2dp_and_hsp_connection_state(self, connected):
    """Asserts that A2DP and HSP connections are in the expected state.

    Args:
      connected: bool, True if the expected state is connected else False.
    """
    with asserts.assert_not_raises(signals.TestError):
      self.primary_device.wait_for_a2dp_connection_state(self.mac_address,
                                                         connected)
      self.primary_device.wait_for_hsp_connection_state(self.mac_address,
                                                        connected)

  def test_disconnect_and_connect(self):
    """Test for DUT disconnecting and then connecting to the remote device."""
    self.primary_device.log.info('Disconnecting the device "%s"...' %
                                 self.mac_address)
    self.primary_device.disconnect_bluetooth(self.mac_address)
    self.assert_a2dp_and_hsp_connection_state(connected=False)
    # Buffer time for connection state change.
    time.sleep(CONNECTION_STATE_CHANGE_SLEEP_SEC)
    self.primary_device.log.info('Connecting the device "%s"...' %
                                 self.mac_address)
    self.primary_device.connect_bluetooth(self.mac_address)
    self.assert_a2dp_and_hsp_connection_state(connected=True)

  def test_reconnect_when_enabling_bluetooth(self):
    """Test for DUT reconnecting to the remote device when Bluetooth enabled."""
    self.primary_device.log.info('Turning off Bluetooth...')
    self.primary_device.sl4a.bluetoothToggleState(False)
    self.primary_device.wait_for_bluetooth_toggle_state(enabled=False)
    self.primary_device.wait_for_disconnection_success(self.mac_address)
    time.sleep(CONNECTION_STATE_CHANGE_SLEEP_SEC)
    self.primary_device.log.info('Turning on Bluetooth...')
    self.primary_device.sl4a.bluetoothToggleState(True)
    self.primary_device.wait_for_bluetooth_toggle_state(enabled=True)
    self.primary_device.wait_for_connection_success(self.mac_address)
    self.assert_a2dp_and_hsp_connection_state(connected=True)

  def test_reconnect_when_connected_device_powered_on(self):
    """Test for the remote device reconnecting to DUT.

    Tests that DUT can be disconnected when the remoted device is powerd off,
    and then reconnected when the remote device is powered on.
    """
    self.primary_device.log.info(
        'The connected device "%s" is being powered off...' % self.mac_address)
    self.derived_bt_device.power_off()
    self.primary_device.wait_for_disconnection_success(self.mac_address)
    self.assert_a2dp_and_hsp_connection_state(connected=False)
    time.sleep(CONNECTION_STATE_CHANGE_SLEEP_SEC)
    self.derived_bt_device.power_on()
    self.primary_device.log.info(
        'The connected device "%s" is being powered on...' % self.mac_address)
    self.primary_device.wait_for_connection_success(self.mac_address)
    self.assert_a2dp_and_hsp_connection_state(connected=True)


if __name__ == '__main__':
  test_runner.main()
