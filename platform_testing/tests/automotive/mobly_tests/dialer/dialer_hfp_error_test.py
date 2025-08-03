"""
Pairing Test
"""

from mobly import asserts
from mobly import test_runner
from bluetooth_test import bluetooth_base_test
from utilities.main_utils import common_main


class DialerHFPError(bluetooth_base_test.BluetoothBaseTest):
  """Enable and Disable Bluetooth from Bluetooth Palette."""

  def setup_test(self):
    """Setup steps before any test is executed."""
    # Pair caller phone with automotive device
    self.bt_utils.pair_primary_to_secondary()


  def test_dialer_hfp_error(self):
    """Disable - Enable Phone-HFP Bluetooth profile"""
    self.call_utils.open_phone_app()
    self.call_utils.wait_with_log(5)
    self.target.mbs.btDisable()
    self.call_utils.wait_with_log(5)
    asserts.assert_true(self.call_utils.is_bluetooth_hfp_error_displayed(),'hfp error is displayed')

if __name__ == '__main__':
    common_main()
