"""Tests for blueberry.map.bluetooth_map."""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import queue
import time

from mobly import test_runner
from mobly import signals
from mobly import utils

from blueberry.controllers import android_bt_target_device
from blueberry.utils import blueberry_base_test

_SMS_MSG_EVENT = 'SmsReceived'
_MAP_MSG_EVENT = 'MapMessageReceived'

_EVENT_TIMEOUT_SEC = 180
_TEXT_LENGTH = 10
_TEXT_COUNT = 5


class BluetoothMapTest(blueberry_base_test.BlueberryBaseTest):
  """Test Class for Bluetooth MAP Test."""

  def __init__(self, configs):
    super().__init__(configs)
    self.derived_bt_device = None
    self.pri_phone = None
    self.pri_number = None
    self.sec_phone = None

  def setup_class(self):
    """Standard Mobly setup class."""
    super().setup_class()
    for device in self.android_devices:
      device.init_setup()
      device.sl4a_setup()

    # Primary phone which role is Message Server Equipment (MSE).
    self.pri_phone = self.android_devices[0]
    self.pri_phone.sl4a.smsStartTrackingIncomingSmsMessage()
    self.pri_number = self.pri_phone.dimensions['phone_number']

    # Secondary phone which is used to send SMS messages to primary phone.
    self.sec_phone = self.android_devices[1]

    # Bluetooth carkit which role is Message Client Equipment (MCE).
    self.derived_bt_device = self.derived_bt_devices[0]

    mac_address = self.derived_bt_device.get_bluetooth_mac_address()
    self.derived_bt_device.activate_pairing_mode()
    self.pri_phone.pair_and_connect_bluetooth(mac_address)
    # Sleep to make the connection to be steady.
    time.sleep(5)

    if isinstance(
        self.derived_bt_device, android_bt_target_device.AndroidBtTargetDevice):
      # Allow sl4a to receive the intent with ACTION_MESSAGE_RECEIVED.
      self.derived_bt_device.adb.shell(
          'pm grant com.googlecode.android_scripting '
          'android.permission.RECEIVE_SMS')
      # Connect derived bt device to primary phone via MAP MCE profile.
      self.derived_bt_device.add_sec_ad_device(self.pri_phone)

  def teardown_test(self):
    """Standard Mobly teardown test.

    Disconnects the MAP connection after a test completes.
    """
    super().teardown_test()
    self.derived_bt_device.map_disconnect()

  def _wait_for_message_on_mce(self, text):
    """Waits for that MCE gets an event with specific message.

    Args:
      text: String, Text of the message.

    Raises:
      TestFailure: Raised if timed out.
    """
    try:
      self.derived_bt_device.ed.wait_for_event(
          _MAP_MSG_EVENT, lambda e: e['data'] == text, _EVENT_TIMEOUT_SEC)
      self.derived_bt_device.log.info(
          'Successfully got the unread message: %s' % text)
    except queue.Empty:
      raise signals.TestFailure(
          'Timed out after %ds waiting for "%s" event with the message: %s' %
          (_EVENT_TIMEOUT_SEC, _MAP_MSG_EVENT, text))

  def _wait_for_message_on_mse(self, text):
    """Waits for that MSE gets an event with specific message.

    This method is used to make sure that MSE has received the test message.

    Args:
      text: String, Text of the message.

    Raises:
      TestError: Raised if timed out.
    """
    try:
      self.pri_phone.ed.wait_for_event(
          _SMS_MSG_EVENT, lambda e: e['data']['Text'] == text,
          _EVENT_TIMEOUT_SEC)
      self.pri_phone.log.info(
          'Successfully received the incoming message: %s' % text)
    except queue.Empty:
      raise signals.TestError(
          'Timed out after %ds waiting for "%s" event with the message: %s' %
          (_EVENT_TIMEOUT_SEC, _SMS_MSG_EVENT, text))

  def _create_message_on_mse(self, text):
    """Creates a new incoming message on MSE.

    Args:
      text: String, Text of the message.
    """
    self.sec_phone.sl4a.smsSendTextMessage(self.pri_number, text, False)
    self._wait_for_message_on_mse(text)

  def test_get_existing_unread_messages(self):
    """Test for the feature of getting existing unread messages on MCE.

    Tests MCE can list existing messages of MSE.
    """
    text_list = []
    # Creates 5 SMS messages on MSE before establishing connection.
    for _ in range(_TEXT_COUNT):
      text = utils.rand_ascii_str(_TEXT_LENGTH)
      self._create_message_on_mse(text)
      text_list.append(text)
    self.derived_bt_device.map_connect()
    # Gets the unread messages of MSE and checks if they are downloaded
    # successfully on MCE.
    self.derived_bt_device.get_unread_messages()
    for text in text_list:
      self._wait_for_message_on_mce(text)

  def test_receive_unread_message(self):
    """Test for the feature of receiving unread message on MCE.

    Tests MCE can get an unread message when MSE receives an incoming message.
    """
    self.derived_bt_device.map_connect()
    text = utils.rand_ascii_str(_TEXT_LENGTH)
    self._create_message_on_mse(text)
    self._wait_for_message_on_mce(text)


if __name__ == '__main__':
  test_runner.main()
