"""
Bluetooth Base Test

This test class serves as a base class for others to inherit from.
It also serves as a device-cleaner to help reset devices between tests.

"""

import logging

from mobly import base_test
from mobly.controllers import android_device

from utilities import spectatio_utils
from utilities import bt_utils
from utilities.main_utils import common_main


class BluetoothBaseTest(base_test.BaseTestClass):

    def setup_class(self):
        # Registering android_device controller module, and declaring that the test
        # requires at least two Android devices.
        # This setup will need to be overwritten or extended if a test uses three devices.
        logging.info("Running basic class setup.")
        self.ads = self.register_controller(android_device, min_number=2)
        # The device used to discover Bluetooth devices.
        self.discoverer = android_device.get_device(
            self.ads, label='auto')
        # Sets the tag that represents this device in logs.
        self.discoverer.debug_tag = 'discoverer'
        # The device that is expected to be discovered
        self.target = android_device.get_device(self.ads, label='phone')
        self.target.debug_tag = 'target'
        logging.info("\tLoading Snippets.")
        self.target.load_snippet('mbs', android_device.MBS_PACKAGE)
        self.discoverer.load_snippet('mbs', android_device.MBS_PACKAGE)
        logging.info("\tInitializing Utilities")
        self.call_utils = (spectatio_utils.CallUtils(self.discoverer))
        self.bt_utils = (bt_utils.BTUtils(self.discoverer, self.target))

    def setup_test(self):
        # Make sure bluetooth is on.
        logging.info("Running basic test setup.")
        logging.info("\tEnabling bluetooth on target and discoverer.")
        self.target.mbs.btEnable()
        self.discoverer.mbs.btEnable()

    def teardown_test(self):
        # Turn Bluetooth off on both devices.
        logging.info("Running basic test teardown.")
        self.bt_utils.unpair()
        logging.info("Disable Bluetooth on Discoverer device")
        self.discoverer.mbs.btDisable()
        logging.info("Disable Bluetooth on Target device")
        self.target.mbs.btDisable()


if __name__ == '__main__':
    common_main()
