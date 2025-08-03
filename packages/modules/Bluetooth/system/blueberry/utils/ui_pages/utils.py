"""Utilities used by ui_pages module."""
from typing import Any, Callable, Dict


def dr_wakeup_before_op(op: Callable[..., Any]) -> Callable[..., Any]:
  """Sends keycode 'KEYCODE_WAKEUP' before conducting UI function.

  Args:
    op: UI function (click, swipe etc.)

  Returns:
    Wrapped UI function.
  """
  def _wrapper(*args: Any, **kargs: Dict[str, Any]) -> Callable[..., Any]:
    """Wrapper of UI function.

    Args:
      *args: Argument list passed into UI function.
      **kargs: key/value argument passed into UI function.

    Returns:
      The returned result by calling the wrapped UI operation method.
    """

    ui_page_self = args[0]
    ui_page_self.ctx.ad.adb.shell(
        'input keyevent KEYCODE_WAKEUP')
    return op(*args, **kargs)

  return _wrapper
