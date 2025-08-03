"""Test ability to call a phone number from search results.

- Requires a testbed with one phone in it that can make calls.

 Steps include:
1. Tap on Phone icon from facet rail or App launcher to launch Dialer app
2. Go to 'Contacts' and verify that all Contacts from Mobile Device displayed in Carkit
3. Click on Search icon and enter any name or alphabet on it
4. Verify that contacts matching with entered alphabet, name or number is showing in the list
5. Select the contact and a make a call
"""
from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test


class MakeCallFromHistotyTest(bluetooth_base_test.BluetoothBaseTest):
  """Implement calling to ten digits number test."""
  def setup_test(self):

    # Pair the devices
    self.bt_utils.pair_primary_to_secondary()
  def test_dial_large_digits_number(self):
    """Tests the calling tten digits number functionality."""
    #Variable
    dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER
    #Tests the calling three digits number functionality

    self.call_utils.dial_a_number(dialer_test_phone_number)
    self.call_utils.make_call()
    self.call_utils.wait_with_log(5)
    self.call_utils.verify_dialing_number(dialer_test_phone_number)
    self.call_utils.end_call()
    self.call_utils.wait_with_log(5)
    self.call_utils.open_call_history()
    self.call_utils.call_most_recent_call_history()
    self.call_utils.wait_with_log(5)
    self.call_utils.verify_dialing_number(dialer_test_phone_number)

  def teardown_test(self):
    # End call if test failed
    self.call_utils.end_call_using_adb_command(self.target)

if __name__ == '__main__':
  common_main()