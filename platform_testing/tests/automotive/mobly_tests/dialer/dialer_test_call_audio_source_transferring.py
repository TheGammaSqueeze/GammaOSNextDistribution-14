"""Test of audio source transferring using Mobly and Mobile Harness.

- Requires a testbed with one phones in it that can make calls.
- Requites sl4a be installed on Android devices. See README.md for details.
- Optional to use --define="dialer_test_phone_number={$PHONE_NUMBER_VALUE}"
- Default dialer_test_phone_number=511

 Steps include:
        1) Pre-call state check on IVI and phone devices. (OK)
        2) Dial any digits number using Dial Pad
        3) Change audio source to phone
        4) Change audio source to car speaker
"""
from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test


class ChangeAudioSourceTest(bluetooth_base_test.BluetoothBaseTest):

  """Implement audio source transferring test."""
  def setup_test(self):
    """Setup steps before any test is executed."""
    # Pair the devices
    self.bt_utils.pair_primary_to_secondary()
  def test_switch_audio_sources_while_in_call(self):
    """Test audio source transferring functionality."""

    dialer_test_phone_number = constants.DIALER_THREE_DIGIT_NUMBER
    #Tests the calling three digits number functionality

    self.call_utils.dial_a_number(dialer_test_phone_number)
    self.call_utils.make_call()
    self.call_utils.wait_with_log(5)
    self.call_utils.change_audio_source_to_phone()
    self.call_utils.change_audio_source_to_car_speakers()

  def teardown_test(self):
    # End call
    self.call_utils.end_call_using_adb_command(self.target)


if __name__ == '__main__':
  common_main()