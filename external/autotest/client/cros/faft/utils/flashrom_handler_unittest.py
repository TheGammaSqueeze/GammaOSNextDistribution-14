#!/usr/bin/python3
"""Unit tests for flashrom_handler.py."""

import unittest
from unittest import mock

from autotest_lib.client.common_lib import autotemp
from autotest_lib.client.cros.faft.utils import (os_interface,
                                                 flashrom_handler)


class TestFlashromHandler(unittest.TestCase):
    """Tests for flashrom_handler.FlashromHandler()"""

    def setUp(self):
        self._tempdir = autotemp.tempdir(unique_id='flashrom_handler_test')
        self.addCleanup(self._tempdir.clean)

        self.os_if = os_interface.OSInterface(
                self._tempdir.name, test_mode=True)
        self.flashrom_handler = flashrom_handler.FlashromHandler(self.os_if)

        self.good_flashrom = mock.Mock()
        attrs = {
                'communicate.return_value':
                (b'working flashrom stdout', b'working flashrom stderr')
        }
        self.good_flashrom.configure_mock(**attrs)
        self.good_flashrom.returncode = 0

        self.bad_flashrom = mock.Mock()
        attrs = {
                'communicate.return_value':
                (b'broken flashrom stdout', b'broken flashrom stderr')
        }
        self.bad_flashrom.configure_mock(**attrs)
        self.bad_flashrom.returncode = 1

    @mock.patch('subprocess.Popen')
    def testHandlerUnavailable(self, mock_subproc_popen):
        """Failing flashrom check should mark the handler unavailable"""

        mock_subproc_popen.return_value = self.bad_flashrom
        self.assertFalse(self.flashrom_handler.is_available())

    @mock.patch('subprocess.Popen')
    def testHandlerUnavailableCached(self, mock_subproc_popen):
        """Status of flashrom handler should be cached, and not rechecked"""

        mock_subproc_popen.return_value = self.bad_flashrom
        self.assertFalse(self.flashrom_handler.is_available())

        mock_subproc_popen.return_value = self.good_flashrom
        self.assertFalse(self.flashrom_handler.is_available())

        mock_subproc_popen.assert_called_once()

    @mock.patch('subprocess.Popen')
    def testHandlerAvailable(self, mock_subproc_popen):
        """Successful flashrom check should mark the handler available"""

        mock_subproc_popen.return_value = self.good_flashrom
        self.assertTrue(self.flashrom_handler.is_available())

    @mock.patch('subprocess.Popen')
    def testHandlerAvailableCached(self, mock_subproc_popen):
        """Status of flashrom handler should be cached, and not rechecked"""

        mock_subproc_popen.return_value = self.good_flashrom
        self.assertTrue(self.flashrom_handler.is_available())

        mock_subproc_popen.return_value = self.bad_flashrom
        self.assertTrue(self.flashrom_handler.is_available())

        mock_subproc_popen.assert_called_once()

if __name__ == '__main__':
    unittest.main()
