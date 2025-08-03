"""Test utils for UWB."""

import logging
import random
import time
from typing import List, Optional
from lib import uwb_ranging_decorator
from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb
from mobly.controllers.android_device_lib import callback_handler_v2

WAIT_TIME_SEC = 3


def verify_uwb_state_callback(
    ad: android_device.AndroidDevice,
    uwb_event: str,
    handler: Optional[callback_handler_v2.CallbackHandlerV2] = None,
    timeout: int = WAIT_TIME_SEC,
) -> bool:
  """Verifies expected UWB callback is received.

  Args:
    ad: android device object.
    uwb_event: expected callback event.
    handler: callback handler.
    timeout: timeout for callback event.

  Returns:
    True if expected callback is received, False if not.
  """
  callback_status = False
  callback_key = None
  start_time = time.time()
  if handler is None:
    callback_key = "uwb_state_%s" % random.randint(1, 100)
    handler = ad.uwb.registerUwbAdapterStateCallback(callback_key)
  # wait until expected callback is received.
  while time.time() - start_time < timeout and not callback_status:
    time.sleep(0.1)
    events = handler.getAll("UwbAdapterStateCallback")
    for event in events:
      event_received = event.data["uwbAdapterStateEvent"]
      logging.debug("Received event - %s", event_received)
      if event_received == uwb_event:
        logging.debug("Received the '%s' callback in %ss", uwb_event,
                      round(time.time() - start_time, 2))
        callback_status = True
        break
  if callback_key is not None:
    ad.uwb.unregisterUwbAdapterStateCallback(callback_key)
  return callback_status


def get_uwb_state(ad: android_device.AndroidDevice) -> bool:
  """Gets the current UWB state.

  Args:
    ad: android device object.

  Returns:
    UWB state, True if enabled, False if not.
  """
  if ad.build_info["build_id"].startswith("S"):
    uwb_state = bool(ad.uwb.getAdapterState())
  else:
    uwb_state = ad.uwb.isUwbEnabled()
  return uwb_state


def set_uwb_state_and_verify(
    ad: android_device.AndroidDevice,
    state: bool,
    handler: Optional[callback_handler_v2.CallbackHandlerV2] = None,
):
  """Sets UWB state to on or off and verifies it.

  Args:
    ad: android device object.
    state: bool, True for UWB on, False for off.
    handler: callback_handler.
  """
  failure_msg = "enabled" if state else "disabled"
  ad.uwb.setUwbEnabled(state)
  event_str = "Inactive" if state else "Disabled"
  asserts.assert_true(verify_uwb_state_callback(ad, event_str, handler),
                      "Uwb is not %s" % failure_msg)


def verify_peer_found(ranging_dut: uwb_ranging_decorator.UwbRangingDecorator,
                      peer_addr: List[int], session: int = 0):
  """Verifies if the UWB peer is found.

  Args:
    ranging_dut: uwb ranging device.
    peer_addr: uwb peer device address.
    session: session id.
  """
  ranging_dut.ad.log.info("Look for peer: %s" % peer_addr)
  start_time = time.time()
  while not ranging_dut.is_uwb_peer_found(peer_addr, session):
    if time.time() - start_time > WAIT_TIME_SEC:
      asserts.fail("UWB peer with address %s not found" % peer_addr)
  logging.info("Peer %s found in %s seconds", peer_addr,
               round(time.time() - start_time, 2))


def set_airplane_mode(ad: android_device.AndroidDevice, state: bool):
  """Sets the airplane mode to the given state.

  Args:
    ad: android device object.
    state: bool, True for Airplane mode on, False for off.
  """
  ad.uwb.setAirplaneMode(state)
  start_time = time.time()
  while get_airplane_mode(ad) != state:
    time.sleep(0.5)
    if time.time() - start_time > WAIT_TIME_SEC:
      asserts.fail("Failed to set airplane mode to: %s" % state)


def get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
  """Gets the airplane mode.

  Args:
    ad: android device object.

  Returns:
    True if airplane mode On, False for Off.
  """
  state = ad.adb.shell(["settings", "get", "global", "airplane_mode_on"])
  return bool(int(state.decode().strip()))


def set_screen_rotation(ad: android_device.AndroidDevice, val: int):
  """Sets screen orientation to landscape or portrait mode.

  Args:
    ad: android device object.
    val: False for potrait, True 1 for landscape mode.
  """
  ad.adb.shell(["settings", "put", "system", "accelerometer_rotation", "0"])
  ad.adb.shell(["settings", "put", "system", "user_rotation", str(val)])


def initialize_uwb_country_code_if_not_set(
    ad: android_device.AndroidDevice,
    handler: Optional[callback_handler_v2.CallbackHandlerV2] = None,
):
  """Sets UWB country code to US if the device does not have it set.

  Note: This intentionally relies on an unstable API (shell command) since we
  don't want to expose an API that allows users to circumvent the UWB
  regulatory requirements.

  Args:
    ad: android device object.
    handler: callback handler.
  """
  # Wait to see if UWB state is reported as enabled. If not, this could be
  # because the country code is not set. Try forcing the country code in that
  # case.
  state = verify_uwb_state_callback(
      ad=ad, uwb_event="Inactive", handler=handler, timeout=120
  )

  # Country code already available, nothing to do.
  if state:
    return
  try:
    ad.adb.shell(["cmd", "uwb", "force-country-code", "enabled", "US"])
  except adb.AdbError:
    logging.warning("Unable to force country code")

  # Unable to get UWB enabled even after setting country code, abort!
  asserts.fail(
      not verify_uwb_state_callback(
          ad=ad, uwb_event="Inactive", handler=handler, timeout=120
      ),
      "Uwb is not enabled",
  )
