"""Suite collecting bluetooth test classes for acceptance testing."""

import logging

from mobly import test_runner_suite

from blueberry.tests.a2dp import bluetooth_a2dp_test
from blueberry.tests.avrcp import bluetooth_avrcp_test
from blueberry.tests.connectivity import ble_pairing_test
from blueberry.tests.connectivity import bluetooth_pairing_test
from blueberry.tests.hfp import bluetooth_hfp_test
from blueberry.tests.map import bluetooth_map_test
from blueberry.tests.opp import bluetooth_opp_test
from blueberry.tests.pan import bluetooth_pan_test
from blueberry.tests.pbap import bluetooth_pbap_test

# Test classes for the Bluetooth acceptance suite.
TEST_CLASSES = [
    bluetooth_pairing_test.BluetoothPairingTest,
    ble_pairing_test.BlePairingTest,
    bluetooth_a2dp_test.BluetoothA2dpTest,
    bluetooth_avrcp_test.BluetoothAvrcpTest,
    bluetooth_hfp_test.BluetoothHfpTest,
    bluetooth_map_test.BluetoothMapTest,
    bluetooth_pbap_test.BluetoothPbapTest,
    bluetooth_opp_test.BluetoothOppTest,
    bluetooth_pan_test.BluetoothPanTest
]


class BluetoothAcceptanceSuite(mobly_g3_suite.BaseSuite):
  """Bluetooth Acceptance Suite.

  Usage of Test selector:
  Add the parameter "acceptance_test_selector" in the Mobly configuration, it's
  value is like "test_method_1,test_method_2,...". If this parameter is not
  used, all tests will be running.
  """

  def setup_suite(self, config):
    selected_tests = None
    selector = config.user_params.get('acceptance_test_selector')
    if selector:
      selected_tests = selector.split(',')
      logging.info('Selected tests: %s', ' '.join(selected_tests))
    # Enable all Bluetooth logging in the first test.
    first_test_config = config.copy()
    first_test_config.user_params.update({
        'enable_hci_snoop_logging': 1,
    })
    for index, clazz in enumerate(TEST_CLASSES):
      if selected_tests:
        matched_tests = None
        # Gets the same elements between selected_tests and dir(clazz).
        matched_tests = list(set(selected_tests) & set(dir(clazz)))
        # Adds the test class if it contains the selected tests.
        if matched_tests:
          self.add_test_class(
              clazz=clazz,
              config=first_test_config if index == 0 else config,
              tests=matched_tests)
          logging.info('Added the tests of "%s": %s', clazz.__name__,
                       ' '.join(matched_tests))
      else:
        self.add_test_class(
            clazz=clazz,
            config=first_test_config if index == 0 else config)


if __name__ == '__main__':
  mobly_g3_suite.main()
