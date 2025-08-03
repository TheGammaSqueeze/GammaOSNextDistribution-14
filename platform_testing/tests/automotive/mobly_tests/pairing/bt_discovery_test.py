"""
Pairing Test
"""

from bluetooth_test import bluetooth_base_test
from utilities.main_utils import common_main

# Number of seconds for the target to stay discoverable on Bluetooth.
DISCOVERABLE_TIME = 120


class MultiDeviceTest(bluetooth_base_test.BluetoothBaseTest):

    def setup_test(self):
        super().setup_test()
        # Set Bluetooth name on target device.
        self.target.mbs.btSetName('LookForMe!')

    def test_bluetooth_discovery(self):
        self.bt_utils.discover_secondary_from_primary()

    def test_bluetooth_pair(self):
        self.bt_utils.pair_primary_to_secondary()


if __name__ == '__main__':
    common_main()
