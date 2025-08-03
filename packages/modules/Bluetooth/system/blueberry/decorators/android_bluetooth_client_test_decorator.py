"""An example Bluetooth Client Decorator.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from mobly.controllers.android_device import AndroidDevice


class AndroidBluetoothClientTestDecorator(AndroidDevice):
  """A class used to test Blueberry's BT Client Profile decoration."""

  def __init__(self, ad):
    self._ad = ad
    if not isinstance(self._ad, AndroidDevice):
      raise TypeError('Must apply AndroidBluetoothClientTestDecorator to an '
                      'AndroidDevice')

  def __getattr__(self, name):
    return getattr(self._ad, name)

  def test_decoration(self):
    return 'I make this device fancy!'
