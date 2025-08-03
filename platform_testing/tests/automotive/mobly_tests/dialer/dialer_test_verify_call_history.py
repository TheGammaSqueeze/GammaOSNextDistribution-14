""" Test that call history shows correct calls records

- Requires a testbed with one phones in it that can make calls.


 Steps include:
1. Tap on Phone icon from facet rail or App launcher to launch Dialer app
2. Tap on 'Call History' menu to see recent calls
3. Verify that all Incoming and outgoing calls are showing under call history
4. Verify HU Calls records are matching with paired phone call records

NOTE: The original CUJ requires a check on the number of arrows shown below each call, which
is beyond automation capabilities at this time. This
test will need to be expanded in the future to cover this functionality.

"""

import logging

from utilities import constants
from utilities.main_utils import common_main
from bluetooth_test import bluetooth_base_test

from mobly import asserts

class VerifyCallHistory(bluetooth_base_test.BluetoothBaseTest):
    """Implement verify call history test."""

    # In order to test a number greater than three, this test needs to be rewritten
    # to handle scrolling for multiple unique call entries.
    TEST_CALLS = 3

    def setup_test(self):
        """Setup steps before any test is executed."""
        # Pair the devices
        self.bt_utils.pair_primary_to_secondary()
    def test_verify_call_history(self):
        """
            Assumes a new phone image with no call history.
        """
        # Set up mobile device
        primary_test_number = constants.DIALER_THREE_DIGIT_NUMBER
        secondary_test_number = constants.INFORMATION_THREE_DIGIT_NUMBER


        # Call each test number
        self.call_utils.dial_a_number(primary_test_number)
        self.call_utils.make_call()
        self.call_utils.end_call()

        self.call_utils.dial_a_number(secondary_test_number)
        self.call_utils.make_call()
        self.call_utils.end_call()

        # Open up call history
        self.call_utils.press_home()
        self.call_utils.wait_with_log(constants.WAIT_ONE_SEC)
        self.call_utils.open_phone_app()
        self.call_utils.wait_with_log(constants.WAIT_ONE_SEC)
        self.call_utils.open_call_history()
        self.call_utils.wait_with_log(constants.WAIT_ONE_SEC)

        # TODO: This test may be upgraded to get the number of history entries from the
        # target device for comparison, and to compare call history type (i.e., 'correct arrows')
        asserts.assert_true(
            self.discoverer.mbs.hasUIElementWithText(primary_test_number),
            "Expected number %s in call history, but did not see it on screen"
            % primary_test_number
        )

        asserts.assert_true(
            self.discoverer.mbs.hasUIElementWithText(secondary_test_number),
            "Expected number %s in call history, but did not see it on screen"
            % secondary_test_number
        )

        self.call_utils.press_home()
        logging.info("Both expected numbers found in call history.")
        self.call_utils.wait_with_log(constants.WAIT_TWO_SECONDS)


    def teardown_test(self):
        # End call if test failed
        self.call_utils.end_call_using_adb_command(self.target)
        super().teardown_test()

"""
        asserts.assert_true(
            self.discoverer.mbs.hasElementWithText(str(primary_test_number)),
            "Expected number %i in call history, but did not see it on screen"
            % (self.primary_test_number)
        )
"""



if __name__ == '__main__':
    common_main()