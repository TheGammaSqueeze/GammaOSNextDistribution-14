# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests for ui_interaction_utils."""

import unittest
import unittest.mock

from snippet_uiautomator import uidevice
from snippet_uiautomator import uiobject2

import its_device_utils
import ui_interaction_utils

_LOG_PATH = '/foo/bar/baz'
_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT = 'prefix'


def _get_mock_ui_object(visibility):
  """Returns a mock snippet-uiautomator UI object with specified visibility."""
  mock_ui_object = unittest.mock.create_autospec(
      uiobject2.UiObject2, instance=True)
  mock_ui_object.wait.exists.return_value = visibility
  return mock_ui_object


def _get_mock_dut(visibility):
  """Returns a mock Android device object with specified UI visibility."""
  # Mock used because accessing the `ui` attribute is unwieldy with autospec.
  mock_dut = unittest.mock.Mock()
  mock_ui = unittest.mock.create_autospec(uidevice.UiDevice, instance=True)
  mock_ui.wait.exists.return_value = visibility
  mock_dut.ui.return_value = mock_ui
  return mock_dut


class UiInteractionUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def setUp(self):
    super().setUp()
    self.mock_visible_ui_object = _get_mock_ui_object(True)
    self.mock_not_visible_ui_object = _get_mock_ui_object(False)
    self.mock_call_on_fail = unittest.mock.Mock()
    self.mock_visible_dut = _get_mock_dut(True)
    self.mock_not_visible_dut = _get_mock_dut(False)
    self.addCleanup(unittest.mock.patch.stopall)
    unittest.mock.patch.object(its_device_utils, 'run', autospec=True).start()

  def test_verify_ui_object_visible_with_visible_object_and_call(self):
    ui_interaction_utils.verify_ui_object_visible(
        self.mock_visible_ui_object, call_on_fail=self.mock_call_on_fail)
    self.mock_call_on_fail.assert_not_called()

  def test_verify_ui_object_visible_with_not_visible_object_no_call(self):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.verify_ui_object_visible(
          self.mock_not_visible_ui_object)

  def test_verify_ui_object_visible_with_not_visible_object_and_call(self):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.verify_ui_object_visible(
          self.mock_not_visible_ui_object, call_on_fail=self.mock_call_on_fail)
    self.mock_call_on_fail.assert_called_once()

  def test_open_jca_viewfinder_success(self):
    ui_interaction_utils.open_jca_viewfinder(self.mock_visible_dut, _LOG_PATH)
    self.mock_visible_dut.take_screenshot.assert_called_once()
    mock_args, mock_kwargs = self.mock_visible_dut.take_screenshot.call_args
    self.assertEqual(mock_args, (_LOG_PATH,))
    self.assertEqual(mock_kwargs[_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT],
                     ui_interaction_utils.VIEWFINDER_VISIBLE_PREFIX)

  @unittest.mock.patch.object(ui_interaction_utils,
                              'verify_ui_object_visible',
                              autospec=True)
  def test_open_jca_viewfinder_fail(self, _):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.open_jca_viewfinder(
          self.mock_not_visible_dut, _LOG_PATH)
    self.mock_not_visible_dut.take_screenshot.assert_called_once()
    mock_args, mock_kwargs = (
        self.mock_not_visible_dut.take_screenshot.call_args
    )
    self.assertEqual(mock_args, (_LOG_PATH,))
    self.assertEqual(mock_kwargs[_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT],
                     ui_interaction_utils.VIEWFINDER_NOT_VISIBLE_PREFIX)
    self.mock_not_visible_dut.ui.dump.assert_called_once()


if __name__ == '__main__':
  unittest.main()
