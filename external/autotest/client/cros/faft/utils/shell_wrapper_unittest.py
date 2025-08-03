#!/usr/bin/python3
"""Unit tests for shell_wrapper.py."""

import unittest
from unittest import mock

from autotest_lib.client.cros.faft.utils import shell_wrapper


class TestLocalShell(unittest.TestCase):
    """Tests for shell_wrapper.LocalShell()."""

    @mock.patch('subprocess.Popen')
    def testSuccessTokenAbsent(self, mock_subproc_popen):
        cmd = 'foo'
        success_token = 'unexpected'
        mock_process = mock.Mock()
        mock_subproc_popen.return_value = mock_process
        attrs = {
                'communicate.return_value': (b'sucessfully executed foo', b'')
        }
        mock_process.configure_mock(**attrs)
        os_if = mock.Mock()
        local_shell = shell_wrapper.LocalShell(os_if)
        self.assertFalse(
                local_shell.run_command_check_output(cmd, success_token))

    @mock.patch('subprocess.Popen')
    def testSuccessTokenPresent(self, mock_subproc_popen):
        cmd = 'bar'
        success_token = 'expected'
        mock_process = mock.Mock()
        mock_subproc_popen.return_value = mock_process
        attrs = {
                'communicate.return_value':
                (b'successfully executed bar. expected is expected.', b'')
        }
        mock_process.configure_mock(**attrs)
        os_if = mock.Mock()
        local_shell = shell_wrapper.LocalShell(os_if)
        self.assertTrue(
                local_shell.run_command_check_output(cmd, success_token))

    @mock.patch('subprocess.Popen')
    def testSuccessTokenMalformed(self, mock_subproc_popen):
        cmd = 'baz'
        success_token = 'malformed token \n'
        mock_process = mock.Mock()
        mock_subproc_popen.return_value = mock_process
        attrs = {
                'communicate.return_value': (b'successfully executed baz', b'')
        }
        mock_process.configure_mock(**attrs)
        os_if = mock.Mock()
        local_shell = shell_wrapper.LocalShell(os_if)
        self.assertRaises(shell_wrapper.UnsupportedSuccessToken,
                          local_shell.run_command_check_output, cmd,
                          success_token)


if __name__ == '__main__':
    unittest.main()
