"""
Bluetooth ThreeDeviceTestBed Base Test

This test class serves as a base class for tests which needs three devices

"""

import sys

from mobly import test_runner
from mobly.controllers import android_device

from utilities import spectatio_utils
from utilities import bt_utils
from bluetooth_test import bluetooth_base_test

class BluetoothSMSBaseTest(bluetooth_base_test.BluetoothBaseTest):


    def setup_class(self):
        # Registering android_device controller module and declaring the three devices

        self.ads = self.register_controller(android_device, min_number=3)
        # The device used to discover Bluetooth devices.
        self.discoverer = android_device.get_device(
            self.ads, label='auto')
        # Sets the tag that represents this device in logs.
        self.discoverer.debug_tag = 'discoverer'
        # The device that is expected to be discovered
        self.target = android_device.get_device(self.ads, label='phone')
        self.target.debug_tag = 'target'
        self.target.load_snippet('mbs', android_device.MBS_PACKAGE)
        self.discoverer.load_snippet('mbs', android_device.MBS_PACKAGE)

        self.phone_notpaired = android_device.get_device(self.ads, label='phone_notpaired')
        self.phone_notpaired.debug_tag = 'phone_notpaired'
        self.phone_notpaired.load_snippet('mbs', android_device.MBS_PACKAGE)

        self.call_utils = (spectatio_utils.CallUtils(self.discoverer))
        self.bt_utils = (bt_utils.BTUtils(self.discoverer, self.target))


if __name__ == '__main__':
    common_main()
