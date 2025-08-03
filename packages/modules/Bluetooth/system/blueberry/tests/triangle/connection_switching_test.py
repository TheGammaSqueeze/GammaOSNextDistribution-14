"""Tests for Connection switching feature of Triangle."""

import logging
import time

from mobly import test_runner
from blueberry.utils import triangle_base_test as base_test
from blueberry.utils import triangle_constants


class ConnectionSwitchingTest(base_test.TriangleBaseTest):
  """Connection Switching Test."""

  def setup_class(self):
    """Executes Connection switching setups.

    Pairs Phone to headset and Watch, then pairs and connect Watch to Headset,
    let Watch be last connected device of Headset.
    """
    super().setup_class()
    self.headset.power_on()
    self.pair_and_connect_phone_to_headset()
    self.pair_and_connect_phone_to_watch()
    self.pair_and_connect_watch_to_headset()

  def setup_test(self):
    """Makes sure that Headset is connected to Watch instead of Phone."""
    super().setup_test()
    self.phone.disconnect_bluetooth(self.headset.mac_address)
    self.watch.connect_bluetooth(self.headset.mac_address)
    self.assert_headset_a2dp_connection(connected=False, device=self.phone)
    self.assert_headset_hsp_connection(connected=False, device=self.phone)

  def test_trigger_connection_switching_when_headset_powered_on(self):
    """Test for triggering connection switching when Headset is powered on.

    Steps:
      1. Power off Headset.
      2. Wait 1 minute.
      3. Power on Headset, and then it will be reconnect.

    Verifications:
      The Headset connection is switched from Watch to Phone.
    """
    logging.info('Power off Headset and wait 1 minute.')
    self.headset.power_off()
    time.sleep(triangle_constants.WAITING_TIME_SEC)
    logging.info('Power on Headset.')
    self.headset.power_on()
    self.assert_headset_a2dp_connection(connected=True, device=self.phone)
    self.assert_headset_hsp_connection(connected=True, device=self.phone)

  def test_trigger_connection_switching_when_phone_tethered_watch(self):
    """Test for triggering connection switching when Phone is tethered to Watch.

    Steps:
      1. Disable Bluetooth on Phone.
      2. Wait 1 minute.
      3. Enable Bluetooth on Phone, and then Phone will be tethered to Watch.

    Verifications:
      The Headset connection is switched from Watch to Phone.
    """
    self.phone.log.info('Disable Bluetooth and wait 1 minute.')
    self.phone.mbs.btDisable()
    time.sleep(triangle_constants.WAITING_TIME_SEC)
    self.phone.log.info('Enable Bluetooth.')
    self.phone.mbs.btEnable()
    self.assert_headset_a2dp_connection(connected=True, device=self.phone)
    self.assert_headset_hsp_connection(connected=True, device=self.phone)


if __name__ == '__main__':
  test_runner.main()
