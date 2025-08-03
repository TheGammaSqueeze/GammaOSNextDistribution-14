"""Tests for blueberry.tests.bluetooth_pairing."""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging

from mobly import test_runner
from mobly import signals
from blueberry.utils import asserts
from blueberry.utils import blueberry_base_test


class BluetoothPairingTest(blueberry_base_test.BlueberryBaseTest):
  """Test Class for Bluetooth Pairing Test.

  Test will reset the bluetooth settings on the phone and attempt to pair
  with the derived_bt_device specified in the configuration file.
  """

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothPairingTest, self).setup_class()
    # Adds a use case that derived_bt_device initiates a pairing request to
    # primary_device. Enable this case if allow_pairing_reverse is 1.
    self.allow_pairing_reverse = int(self.user_params.get(
        'allow_pairing_reverse', 0))

    if self.android_devices:
      self.primary_device = self.android_devices[0]
      self.primary_device.init_setup()
      self.primary_device.sl4a_setup()

      if len(self.android_devices) > 1 and not self.derived_bt_devices:
        # In the case of pairing phone to phone, we need to treat the
        # secondary phone as a derived_bt_device in order for the generic script
        # to work with this android phone properly.
        self.derived_bt_device = self.android_devices[1]
        self.derived_bt_devices.append(self.derived_bt_device)
        self.derived_bt_device.init_setup()
        self.derived_bt_device.sl4a_setup()
      else:
        self.derived_bt_device = self.derived_bt_devices[0]
        self.derived_bt_device.factory_reset_bluetooth()
    else:
      # In the case of pairing mock to mock, at least 2 derived_bt_device is
      # required. The first derived_bt_device is treated as primary_device.
      self.primary_device = self.derived_bt_devices[0]
      self.primary_device.init_setup()
      self.derived_bt_device = self.derived_bt_devices[1]
      self.derived_bt_device.factory_reset_bluetooth()

  def setup_test(self):
    """Setup for pairing test."""
    logging.info('Setup Test for test_pair_and_bond')
    super(BluetoothPairingTest, self).setup_test()

  def test_pair_and_bond(self):
    """Test for pairing and bonding a phone with a bluetooth device.

    Initiates pairing from the phone and checks for 20 seconds that
    the device is connected.
    """
    device_list = [(self.primary_device, self.derived_bt_device)]
    if self.allow_pairing_reverse:
      device_list.append((self.derived_bt_device, self.primary_device))
    for initiator, receiver in device_list:
      # get mac address of device to pair with
      mac_address = receiver.get_bluetooth_mac_address()
      logging.info('Receiver BT MAC Address: %s', mac_address)
      # put device into pairing mode
      receiver.activate_pairing_mode()
      # initiate pairing from initiator
      initiator.set_target(receiver)
      with asserts.assert_not_raises(signals.ControllerError):
        initiator.pair_and_connect_bluetooth(mac_address)
      if self.allow_pairing_reverse and initiator != self.derived_bt_device:
        logging.info('===== Reversing Pairing =====')
        # Resets Bluetooth status for two sides.
        initiator.factory_reset_bluetooth()
        receiver.factory_reset_bluetooth()


if __name__ == '__main__':
  test_runner.main()
