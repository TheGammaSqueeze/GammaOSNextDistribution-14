"""Functions to interact with modem log.

Different modem logging profile can be found here:
cs/vendor/google/apps/PixelLogger/log_profile/GFT_Call_Performance.xml
"""
import enum
import logging
import time

from mobly.controllers import android_device  # type: ignore

_LOG = logging.getLogger(__name__)


class ModemLogAction(enum.Enum):
  """All possible valid PILOT logging actions."""

  START = 'ACTION_START_LOGGING'
  STOP = 'ACTION_STOP_LOGGING'
  CLEAR = 'ACTION_CLEAR_LOG'


class ModemLogProfile(enum.Enum):
  """All possible modem logging profiles."""

  LASSEN_AUDIO_TCP_DSP = 'Call_Performance.xml'
  LASSEN_TCP_DSP = 'Data_Performance.xml'


_MODEM_PILOT_ENABLE_PROP_NAME = 'vendor.pixellogger.pilot.logging_enable'

_MODEM_LOG_PATH = '/sdcard/Android/data/com.android.pixellogger/files/logs'

_ADB_SET_LOG_PROFILE_TEMPLATE = (
    'am broadcast '
    '-a com.android.pixellogger.experiment.ACTION_LOAD_PROFILE '
    '-n com.android.pixellogger/.receiver.ExperimentLoggingReceiver '
    '--es name "{log_profile_name}"'
)

_ADB_LOG_ACTION = (
    'am broadcast '
    '-a com.android.pixellogger.experiment.{log_action} '
    '-n com.android.pixellogger/.receiver.ExperimentLoggingReceiver'
)

_MODEM_LOGGING_PROFILE_PROP_NAME = (
    'persist.vendor.pixellogger.pilot.profile_name'
)


def start_modem_logging(
    dut: android_device.AndroidDevice,
    timeout: int = 20,
    polling_interval: int = 1,
) -> bool:
  """Starts modem PILOT logging.

  Args:
    dut: A mobly AndroidDevice controller object.
    timeout: Seconds to try to confirm logging before giving up.
    polling_interval: Seconds to wait between confirmation attempts.

  Raises:
    RuntimeError: If unable to enable PILOT modem logging within timeout.
  """
  dut.adb.root()
  cmd = _ADB_LOG_ACTION.format(log_action=ModemLogAction.START.value)
  dut.adb.shell(cmd)
  end_time = time.time() + timeout
  while time.time() < end_time:
    time.sleep(polling_interval)
    res = dut.adb.getprop(_MODEM_PILOT_ENABLE_PROP_NAME).strip()
    _LOG.debug('PILOT modem logging enable: %s', res)
    if res == 'true':
      return
  raise RuntimeError('Fail to start modem logging in PILOT mode.')


def stop_modem_logging(
    dut: android_device.AndroidDevice,
    timeout: int = 20,
    polling_interval: int = 1,
) -> bool:
  """Stops modem PILOT logging.

  Args:
    dut: A mobly AndroidDevice controller object.
    timeout: An integer of time in second to wait for modem log to stop.
    polling_interval: Interval in second to check if modem logging stopped.

  Raises:
    RuntimeError: If unable to disable PILOT modem logging within timeout.
  """
  dut.adb.root()
  cmd = _ADB_LOG_ACTION.format(log_action=ModemLogAction.STOP.value)
  dut.adb.shell(cmd)
  end_time = time.time() + timeout
  while time.time() < end_time:
    time.sleep(polling_interval)
    res = dut.adb.getprop(_MODEM_PILOT_ENABLE_PROP_NAME).strip()
    _LOG.debug('PILOT modem logging enable: %s', res)
    if res == 'false' or not res:
      return
  raise RuntimeError('Fail to stop modem logging in PILOT mode.')


def clear_modem_logging(dut: android_device.AndroidDevice) -> None:
  """Stops modem PILOT logging.

  Args:
    dut: A mobly AndroidDevice controller object.
  """
  dut.adb.root()
  cmd = _ADB_LOG_ACTION.format(log_action=ModemLogAction.CLEAR.value)
  dut.adb.shell(cmd)
  _LOG.debug('Cleared modem logs.')


def set_modem_log_profle(
    dut: android_device.AndroidDevice,
    profile: ModemLogProfile,
    timeout: int = 10,
    polling_interval: int = 1,
) -> bool:
  """Set modem log profile.

  Args:
    dut: An mobly AndroidDevice controller object.
    profile: An ModemLogProfile enum represent modem logging profile.
    timeout: Time waiting for modem log profile to be set.
    polling_interval: Interval in second to check if log profile change.

  Returns:
    True if successfully set modem log profile within timeout. Fail otherwise.
  """
  dut.adb.root()
  cmd = _ADB_SET_LOG_PROFILE_TEMPLATE.format(log_profile_name=profile.value)
  dut.adb.shell(cmd)
  end_time = time.time() + timeout
  while time.time() < end_time:
    time.sleep(polling_interval)
    if profile.value in get_modem_log_profile(dut):
      return True
  return False


def get_modem_log_profile(dut: android_device.AndroidDevice) -> str:
  """Get modem log profile.

  Args:
    dut: An mobly AndroidDevice controller object.

  Returns:
    String value of modem logging profile name.

  Raises:
    RuntimeError: If get empty response from adb shell.
  """
  dut.adb.root()
  res = dut.adb.getprop(_MODEM_LOGGING_PROFILE_PROP_NAME)
  if not res:
    raise RuntimeError('Fail to get modem logging profile from device.')
  return res


def pull_logs(dut: android_device.AndroidDevice, out_path: str, pull_timeout = 300) -> None:
  """Pulls logs on device.

  Args:
    dut: An mobly AndroidDevice controller object.
    out_path: A path to extract logs to.
    pull_timeout: Seconds to wait for pulling complete.
  """
  dut.adb.root()
  dut.adb.pull(
    "%s %s" % (_MODEM_LOG_PATH, out_path), timeout=pull_timeout)
  _LOG.debug('Modem logs exported to %s', out_path)