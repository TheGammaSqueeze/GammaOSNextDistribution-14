"""Setup for Android Bluetooth device."""

from mobly import test_runner
from mobly.controllers import android_device
from mobly.controllers.android_device_lib.services import sl4a_service
from blueberry.utils import blueberry_base_test


class BluetoothDeviceSetup(blueberry_base_test.BlueberryBaseTest):
  """A class for Bluetooth device setup.

  This is not a test, just used to do device quick setup for building a testbed.
  """

  def test_setup_device(self):
    """Setup a Bluetooth device.

    Executes logging setup and checks if MBS and SL4A can be used.
    """
    device = self.android_devices[0]
    # Setup logging
    self.set_bt_trc_level_verbose(device)
    self.set_btsnooplogmode_full(device)
    self.set_logger_buffer_size_16m(device)
    device.reboot()
    # Loads MBS and SL4A to make sure they work fine.
    device.load_snippet('mbs', android_device.MBS_PACKAGE)
    device.services.register('sl4a', sl4a_service.Sl4aService)


if __name__ == '__main__':
  test_runner.main()
