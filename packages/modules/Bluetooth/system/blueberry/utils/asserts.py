"""Assertions for Blueberry package."""

import contextlib
from typing import Iterator, Type

from mobly import signals


@contextlib.contextmanager
def assert_not_raises(
    exception: Type[Exception] = Exception) -> Iterator[None]:
  """Asserts that the exception is not raised.

  This assertion function is used to catch a specified exception
  (or any exceptions) and raise signal.TestFailure instead.

  Usage:
    ```
    with asserts.assert_not_raises(signals.TestError):
      foo()
    ```

  Args:
    exception: Exception to be catched. If not specify, catch any exceptions as
      default.

  Yields:
    A context which may raise the exception.

  Raises:
    signals.TestFailure: raised when the exception is catched in the context.
  """

  try:
    yield
  except exception as e:  # pylint: disable=broad-except
    raise signals.TestFailure(e)
