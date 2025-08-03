"""Test of delete dialed using Mobly and Mobile Harness.

- Requires a testbed with one phones in it that can make calls.
- Requites sl4a be installed on Android devices. See README.md for details.
- Optional to use --define="dialer_test_phone_number={$PHONE_NUMBER_VALUE}"
- Default dialer_test_phone_number=511

 Steps include:
        1) Pre-call state check on IVI and phone devices. (OK)
        2) Dial any digits number using Dial Pad
        3) Delete dialed number
        4) Assert placeholder on Dial Pad is 'Dial a number'
"""

from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test



class DeleteDialedNumberTest(bluetooth_base_test.BluetoothBaseTest):
  """Implement calling to three digits number test."""


  def setup_test(self):
    """Setup steps before any test is executed."""
    # Pair the devices
    self.bt_utils.pair_primary_to_secondary()
  def test_delete_dialed_number(self):
    """Test the calling three digits number functionality."""

    #Variable
    dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER
    #Tests the calling three digits number functionality

    self.call_utils.wait_with_log(2)
    self.call_utils.dial_a_number(dialer_test_phone_number);
    self.call_utils.delete_dialed_number_on_dial_pad()
    self.call_utils.verify_dialed_number_on_dial_pad(
        constants.DIAL_A_NUMBER
    )


if __name__ == '__main__':
  common_main()