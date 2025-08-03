"""Decorator for UWB ranging methods."""

import time
from typing import List
from lib import uwb_ranging_params
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.snippet import errors

CALLBACK_WAIT_TIME_SEC = 3
STOP_CALLBACK_WAIT_TIME_SEC = 6


class UwbRangingDecorator():
  """Decorator for Uwb ranging methods."""

  def __init__(self, ad: android_device.AndroidDevice):
    """Initialize the ranging device.

    Args:
      ad: android device object

    Usage:
      The ranging methods should be called in the following order
      1. open_ranging()
      2. start_ranging()
      3. find peer, distance measurement, aoa measurements.
      4. stop_ranging()
      5. close_ranging()
    """
    self.ad = ad
    self._callback_keys = {}
    self._event_handlers = {}
    self.log = self.ad.log

  def clear_ranging_session_callback_events(self, ranging_session_id: int = 0):
    """Clear 'RangingSessionCallback' events from EventCache.

    Args:
      ranging_session_id: ranging session id.
    """
    handler = self._event_handlers[ranging_session_id]
    handler.getAll("RangingSessionCallback")

  def verify_callback_received(self,
                               ranging_event: str,
                               session: int = 0,
                               timeout: int = CALLBACK_WAIT_TIME_SEC):
    """Verifies if the expected callback is received.

    Args:
      ranging_event: Expected ranging event.
      session: ranging session.
      timeout: callback timeout.

    Raises:
      TimeoutError: if the expected callback event is not received.
    """
    handler = self._event_handlers[session]
    start_time = time.time()
    while time.time() - start_time < timeout:
      try:
        event = handler.waitAndGet("RangingSessionCallback", timeout=timeout)
        event_received = event.data["rangingSessionEvent"]
        self.ad.log.debug("Received event - %s" % event_received)
        if event_received == ranging_event:
          self.ad.log.debug("Received the '%s' callback in %ss" %
                            (ranging_event, round(time.time() - start_time, 2)))
          self.clear_ranging_session_callback_events(session)
          return
      except errors.CallbackHandlerTimeoutError as e:
        self.log.warn("Failed to receive 'RangingSessionCallback' event")
        raise TimeoutError(
            "Failed to receive '%s' event" % ranging_event
        ) from e

  def open_fira_ranging(self,
                        params: uwb_ranging_params.UwbRangingParams,
                        session: int = 0,
                        expect_to_succeed: bool = True):
    """Opens fira ranging session.

    Args:
      params: UWB ranging parameters.
      session: ranging session.
      expect_to_succeed: Whether the session open is expected to succeed.
    """
    callback_key = "fira_session_%s" % session
    handler = self.ad.uwb.openFiraRangingSession(callback_key, params.to_dict())
    self._event_handlers[session] = handler
    if expect_to_succeed:
      self.verify_callback_received("Opened", session)
      self._callback_keys[session] = callback_key
    else:
      self.verify_callback_received("OpenFailed", session)

  def start_fira_ranging(self, session: int = 0):
    """Starts Fira ranging session.

    Args:
      session: ranging session.
    """
    self.ad.uwb.startFiraRangingSession(self._callback_keys[session])
    self.verify_callback_received("Started", session)

  def reconfigure_fira_ranging(
      self,
      params: uwb_ranging_params.UwbRangingReconfigureParams,
      session: int = 0):
    """Reconfigures Fira ranging parameters.

    Args:
      params: UWB reconfigured params.
      session: ranging session.
    """
    self.ad.uwb.reconfigureFiraRangingSession(self._callback_keys[session],
                                              params.to_dict())
    self.verify_callback_received("Reconfigured", session)

  def add_controlee_fira_ranging(
      self,
      params: uwb_ranging_params.UwbRangingControleeParams,
      session: int = 0,
  ):
    """Reconfigures Fira ranging to add controlee.

    Args:
      params: UWB controlee params.
      session: ranging session.
    """
    self.ad.uwb.addControleeFiraRangingSession(
        self._callback_keys[session], params.to_dict()
    )
    self.verify_callback_received("ControleeAdded", session)

  def remove_controlee_fira_ranging(
      self,
      params: uwb_ranging_params.UwbRangingControleeParams,
      session: int = 0,
  ):
    """Reconfigures Fira ranging to add controlee.

    Args:
      params: UWB controlee params.
      session: ranging session.
    """
    self.ad.uwb.removeControleeFiraRangingSession(
        self._callback_keys[session], params.to_dict()
    )
    self.verify_callback_received("ControleeRemoved", session)

  def is_uwb_peer_found(self, addr: List[int], session: int = 0) -> bool:
    """Verifies if the UWB peer is found.

    Args:
      addr: peer address.
      session: ranging session.

    Returns:
      True if peer is found, False if not.
    """
    self.verify_callback_received("ReportReceived", session)
    return self.ad.uwb.isUwbPeerFound(self._callback_keys[session], addr)

  def get_distance_measurement(self,
                               addr: List[int],
                               session: int = 0) -> float:
    """Returns distance measurement from peer.

    Args:
      addr: peer address.
      session: ranging session.

    Returns:
      Distance measurement in float.

    Raises:
      ValueError: if the DistanceMeasurement object is null.
    """
    try:
      return self.ad.uwb.getDistanceMeasurement(self._callback_keys[session],
                                                addr)
    except jsonrpc_client_base.ApiError as api_error:
      raise ValueError("Failed to get distance measurement.") from api_error

  def get_aoa_azimuth_measurement(self,
                                  addr: List[int],
                                  session: int = 0) -> float:
    """Returns AoA azimuth measurement data from peer.

    Args:
      addr: list, peer address.
      session: ranging session.

    Returns:
      AoA azimuth measurement in radians in float.

    Raises:
      ValueError: if the AngleMeasurement object is null.
    """
    try:
      return self.ad.uwb.getAoAAzimuthMeasurement(
          self._callback_keys[session], addr)
    except jsonrpc_client_base.ApiError as api_error:
      raise ValueError("Failed to get azimuth measurement.") from api_error

  def get_aoa_altitude_measurement(self,
                                   addr: List[int],
                                   session: int = 0) -> float:
    """Gets UWB AoA altitude measurement data.

    Args:
      addr: list, peer address.
      session: ranging session.

    Returns:
      AoA altitude measurement in radians in float.

    Raises:
      ValueError: if the AngleMeasurement object is null.
    """
    try:
      return self.ad.uwb.getAoAAltitudeMeasurement(
          self._callback_keys[session], addr)
    except jsonrpc_client_base.ApiError as api_error:
      raise ValueError("Failed to get altitude measurement.") from api_error

  def get_rssi_measurement(self, addr: List[int], session: int = 0) -> int:
    """Returns RSSI measurement from both devices.

    Args:
      addr: peer address.
      session: ranging session.

    Returns:
      RSSI measurement in int.

    Raises:
      ValueError: if the RSSI Measurement object is null.
    """
    try:
      return self.ad.uwb.getRssiDbmMeasurement(self._callback_keys[session],
                                               addr)
    except errors.ApiError as api_error:
      raise ValueError("Failed to get RSSI measurement.") from api_error

  def stop_ranging(self, session: int = 0):
    """Stops UWB ranging session.

    Args:
      session: ranging session.
    """
    self.ad.uwb.stopRangingSession(self._callback_keys[session])
    self.verify_callback_received("Stopped", session, STOP_CALLBACK_WAIT_TIME_SEC)

  def close_ranging(self, session: int = 0):
    """Closes ranging session.

    Args:
      session: ranging session.
    """
    if session not in self._callback_keys:
      return
    self.ad.uwb.closeRangingSession(self._callback_keys[session])
    self.verify_callback_received("Closed", session)
    self._callback_keys.pop(session, None)
    self._event_handlers.pop(session, None)

  def close_all_ranging_sessions(self):
    """Closes all ranging sessions.

    Args:
    """
    for session in self._callback_keys:
      self.ad.uwb.closeRangingSession(self._callback_keys[session])
      self.verify_callback_received("Closed", session)
    self.clear_all_ranging_sessions()

  def clear_all_ranging_sessions(self):
    """Clear all ranging sessions from internal map (for ex: after reboot).

    Args:
    """
    self._callback_keys.clear()
    self._event_handlers.clear()
