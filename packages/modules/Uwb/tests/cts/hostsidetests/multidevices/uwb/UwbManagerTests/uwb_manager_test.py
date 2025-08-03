"""Tests for UwbManager APIs."""

import random
import sys
from lib import uwb_base_test
from mobly import asserts
from mobly import config_parser
from mobly import suite_runner
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from test_utils import uwb_test_utils

_TEST_CASES = (
    "test_default_uwb_state",
    "test_disable_uwb_state",
    "test_enable_uwb_state",
    "test_uwb_state_with_airplane_mode_on",
    "test_toggle_uwb_state_with_airplane_mode_on",
    "test_uwb_state_with_airplane_mode_off",
    "test_uwb_state_off_with_airplane_mode_toggle",
)


class UwbManagerTest(uwb_base_test.UwbBaseTest):
  """Tests for UwbManager platform APIs.

  Attributes:
    android_devices: list of android device objects.
  """

  def __init__(self, configs: config_parser.TestRunConfig):
    """Init method for the test class.

    Args:
      configs: A config_parser.TestRunConfig object.
    """
    super().__init__(configs)
    self.tests = _TEST_CASES

  def setup_class(self):
    super().setup_class()
    self.dut = self.android_devices[0]

  def setup_test(self):
    super().setup_test()
    self.callback = "uwb_state_%s" % random.randint(1, 100)
    self.handler = self.dut.uwb.registerUwbAdapterStateCallback(self.callback)

  def teardown_test(self):
    super().teardown_test()
    self.dut.uwb.unregisterUwbAdapterStateCallback(self.callback)

  ### Helper methods ###

  def _test_uwb_state_after_reboot(
      self,
      dut: android_device.AndroidDevice,
      state: bool,
      handler: callback_handler_v2.CallbackHandlerV2,
  ):
    """Sets UWB state and verifies it is persistent after reboot.

    Args:
      dut: android device object.
      state: bool, True for UWB mode on, False for off.
      handler: callback handler.
    """
    uwb_test_utils.set_uwb_state_and_verify(dut, state)
    dut.reboot()
    uwb_test_utils.initialize_uwb_country_code_if_not_set(dut)
    state_after_reboot = uwb_test_utils.get_uwb_state(dut)
    asserts.assert_equal(
        state, state_after_reboot,
        "Uwb state before reboot: %s;  after reboot: %s" %
        (state, state_after_reboot))

  def _verify_uwb_state_with_airplane_mode(
      self,
      dut: android_device.AndroidDevice,
      prev_uwb_state: bool,
      expected_uwb_state: bool,
      event_str: str,
      handler: callback_handler_v2.CallbackHandlerV2,
  ):
    """Verifies UWB state with airplane mode.

    Args:
      dut: android device object.
      prev_uwb_state: previous UWB state.
      expected_uwb_state: expected UWB state.
      event_str: callback event string.
      handler: callback handler.

    Returns:
      True if test results in expected UWB state, False if not.
    """
    callback_received = uwb_test_utils.verify_uwb_state_callback(
        dut, event_str, handler
    )
    if prev_uwb_state == expected_uwb_state:
      return uwb_test_utils.get_uwb_state(dut) == expected_uwb_state
    return callback_received

  ### Test Cases ###

  def test_default_uwb_state(self):
    """Verifies default UWB state is On after flashing the device."""
    asserts.assert_true(uwb_test_utils.get_uwb_state(self.dut),
                        "UWB state: Off; Expected: On.")

  def test_disable_uwb_state(self):
    """Disables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, False, self.handler)

  def test_enable_uwb_state(self):
    """Enables and verifies UWB state."""
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True, self.handler)

  def test_uwb_state_after_reboot_with_uwb_off(self):
    """Sets UWB state to off and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, False, self.handler)

  def test_uwb_state_after_reboot_with_uwb_on(self):
    """Sets UWB state to on and verifies it is persistent after reboot."""
    self._test_uwb_state_after_reboot(self.dut, True, self.handler)

  def test_uwb_state_with_airplane_mode_on(self):
    """Verifies UWB is disabled with airplane mode on."""
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, False, "Disabled", self.handler
        ),
        "UWB is not disabled with airplane mode On.",
    )

  def test_toggle_uwb_state_with_airplane_mode_on(self):
    """Verifies UWB cannot be turned on with airplane mode On."""
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, False, "Disabled", self.handler
        ),
        "UWB is not disabled with airplane mode On.",
    )

    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    self.dut.uwb.setUwbEnabled(True)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, False, "Disabled", self.handler
        ),
        "Enabling UWB with airplane mode On should not work.",
    )

  def test_uwb_state_with_airplane_mode_off(self):
    """Verifies UWB is disabled with airplane mode off."""
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    uwb_test_utils.set_airplane_mode(self.dut, False)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, True, "Inactive", self.handler
        ),
        "UWB is not enabled with airplane mode Off.",
    )

  def test_uwb_state_off_with_airplane_mode_toggle(self):
    """Verifies UWB disabled state is persistent with airplane mode toggle."""

    # disable UWB
    uwb_test_utils.set_uwb_state_and_verify(self.dut, False, self.handler)

    # enable airplane mode and verify UWB is disabled.
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    uwb_test_utils.set_airplane_mode(self.dut, True)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, False, "Disabled", self.handler
        ),
        "UWB is not disabled with airplane mode On.",
    )

    # disable airplane mode and verify UWB is disabled.
    uwb_state = uwb_test_utils.get_uwb_state(self.dut)
    uwb_test_utils.set_airplane_mode(self.dut, False)
    asserts.assert_true(
        self._verify_uwb_state_with_airplane_mode(
            self.dut, uwb_state, False, "Disabled", self.handler
        ),
        "UWB state: On, Expected state: Off",
    )

    # enable UWB
    uwb_test_utils.set_uwb_state_and_verify(self.dut, True)

if __name__ == "__main__":
  if "--" in sys.argv:
    index = sys.argv.index("--")
    sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  suite_runner.run_suite([UwbManagerTest])
