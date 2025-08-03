"""A module to trigger device into doze mode."""
import enum
from retry import retry


_UNPLUG_POWER = "dumpsys battery unplug"
_RESET_POWER = "dumpsys battery reset"
_GET_DOZE_STATUS = "dumpsys deviceidle get {status}"
_FORCE_IDLE = "dumpsys deviceidle force-idle {status}"
_LEAVE_IDLE = "dumpsys deviceidle disable"


class DozeState(enum.Enum):
  """Doze state."""
  INACTIVE = "INACTIVE"
  ACTIVE = "ACTIVE"
  IDLE = "IDLE"


class DozeType(enum.Enum):
  DEEP = "deep"
  LIGHT = "light"


def _check_doze_status(
    dut,
    doze_type: DozeType,
    doze_state: DozeState):
  command = _GET_DOZE_STATUS.format(status=doze_type.value)
  doze_status = dut.adb.shell(command).strip()
  dut.log.info("%s doze status is %s" % (doze_type.value, doze_status))
  if doze_status != doze_state.value:
    raise ValueError("Unexpected doze status.")


@retry(exceptions=ValueError, tries=3, delay=1)
def enter_doze_mode(
    dut,
    doze_type: DozeType):
  """Sets device into doze mode according to given doze type.
  Args:
    dut: The device under test.
    doze_type: The desired doze type.
  Raises:
    ValueError: When fail to sets device into doze mode.
  """
  dut.adb.shell(_UNPLUG_POWER)
  dut.adb.shell(
      _FORCE_IDLE.format(status=doze_type.value),
  )
  _check_doze_status(dut, doze_type, DozeState.IDLE)


@retry(exceptions=ValueError, tries=3, delay=1)
def leave_doze_mode(
    dut,
    doze_type: DozeType):
  """Sets device out of doze mode.
  Args:
    dut: The device under test.
    doze_type: The desired doze type.
  Raises:
    ValueError: When fail to sets device out of doze mode.
  """
  dut.adb.shell(_RESET_POWER)
  dut.adb.shell(_LEAVE_IDLE)
  _check_doze_status(dut, doze_type, DozeState.ACTIVE)
