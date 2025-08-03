#!/usr/bin/env python
#
# Copyright 2019 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for acloud.internal.lib.ssh."""

import io
import subprocess
import threading
import time
import unittest

from unittest import mock

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import ssh
from acloud.internal.lib import utils


class SshTest(driver_test_lib.BaseDriverTest):
    """Test ssh class."""

    FAKE_SSH_PRIVATE_KEY_PATH = "/fake/acloud_rea"
    FAKE_SSH_USER = "fake_user"
    FAKE_IP = ssh.IP(external="1.1.1.1", internal="10.1.1.1")
    FAKE_EXTRA_ARGS_SSH = "-o ProxyCommand='ssh fake_user@2.2.2.2 Server 22'"
    FAKE_REPORT_INTERNAL_IP = True

    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.Patch(utils, "FindExecutable",
                   side_effect=lambda name: f"/usr/bin/{name}")
        self.created_subprocess = mock.MagicMock()
        self.created_subprocess.stdout = mock.MagicMock()
        self.created_subprocess.stdout.readline = mock.MagicMock(return_value=b"")
        self.created_subprocess.poll = mock.MagicMock(return_value=0)
        self.created_subprocess.returncode = 0
        self.created_subprocess.communicate = mock.MagicMock(return_value=
                                                             ('', ''))

    def testSSHExecuteWithRetry(self):
        """test SSHExecuteWithRetry method."""
        self.Patch(time, "sleep")
        self.Patch(subprocess, "Popen",
                   side_effect=subprocess.CalledProcessError(
                       None, "ssh command fail."))
        self.assertRaises(subprocess.CalledProcessError,
                          ssh.ShellCmdWithRetry,
                          "fake cmd")

    def testGetBaseCmdWithInternalIP(self):
        """Test get base command with internal ip."""
        ssh_object = ssh.Ssh(ip=self.FAKE_IP,
                             user=self.FAKE_SSH_USER,
                             ssh_private_key_path=self.FAKE_SSH_PRIVATE_KEY_PATH,
                             report_internal_ip=self.FAKE_REPORT_INTERNAL_IP)
        expected_ssh_cmd = (
            "/usr/bin/ssh -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-l fake_user 10.1.1.1")
        self.assertEqual(ssh_object.GetBaseCmd(constants.SSH_BIN), expected_ssh_cmd)

    def testGetBaseCmd(self):
        """Test get base command."""
        ssh_object = ssh.Ssh(self.FAKE_IP, self.FAKE_SSH_USER, self.FAKE_SSH_PRIVATE_KEY_PATH)
        expected_ssh_cmd = (
            "/usr/bin/ssh -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-l fake_user 1.1.1.1")
        self.assertEqual(ssh_object.GetBaseCmd(constants.SSH_BIN), expected_ssh_cmd)

        expected_scp_cmd = (
            "/usr/bin/scp -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no")
        self.assertEqual(ssh_object.GetBaseCmd(constants.SCP_BIN), expected_scp_cmd)

    # pylint: disable=no-member
    def testSshRunCmd(self):
        """Test ssh run command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.created_subprocess.communicate.return_value = ("stdout", "")
        ssh_object = ssh.Ssh(self.FAKE_IP, self.FAKE_SSH_USER, self.FAKE_SSH_PRIVATE_KEY_PATH)
        self.assertEqual("stdout", ssh_object.Run("command"))
        expected_cmd = (
            "exec /usr/bin/ssh -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-l fake_user 1.1.1.1 command")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    def testSshRunCmdwithExtraArgs(self):
        """test ssh rum command with extra command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.created_subprocess.communicate.return_value = ("stdout", "")
        ssh_object = ssh.Ssh(self.FAKE_IP,
                             self.FAKE_SSH_USER,
                             self.FAKE_SSH_PRIVATE_KEY_PATH,
                             self.FAKE_EXTRA_ARGS_SSH)
        self.assertEqual("stdout", ssh_object.Run("command"))
        expected_cmd = (
            "exec /usr/bin/ssh -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-o ProxyCommand='ssh fake_user@2.2.2.2 Server 22' "
            "-l fake_user 1.1.1.1 command")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    def testScpPullFileCmd(self):
        """Test scp pull file command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        ssh_object = ssh.Ssh(self.FAKE_IP, self.FAKE_SSH_USER, self.FAKE_SSH_PRIVATE_KEY_PATH)
        ssh_object.ScpPullFile("/tmp/test", "/tmp/test_1.log")
        expected_cmd = (
            "exec /usr/bin/scp -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "fake_user@1.1.1.1:/tmp/test /tmp/test_1.log")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    def testScpPullFileCmdwithExtraArgs(self):
        """Test scp pull file command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        ssh_object = ssh.Ssh(self.FAKE_IP,
                             self.FAKE_SSH_USER,
                             self.FAKE_SSH_PRIVATE_KEY_PATH,
                             self.FAKE_EXTRA_ARGS_SSH)
        ssh_object.ScpPullFile("/tmp/test", "/tmp/test_1.log")
        expected_cmd = (
            "exec /usr/bin/scp -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-o ProxyCommand='ssh fake_user@2.2.2.2 Server 22' "
            "fake_user@1.1.1.1:/tmp/test /tmp/test_1.log")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    def testScpPushFileCmd(self):
        """Test scp push file command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        ssh_object = ssh.Ssh(self.FAKE_IP, self.FAKE_SSH_USER, self.FAKE_SSH_PRIVATE_KEY_PATH)
        ssh_object.ScpPushFile("/tmp/test", "/tmp/test_1.log")
        expected_cmd = (
            "exec /usr/bin/scp -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "/tmp/test fake_user@1.1.1.1:/tmp/test_1.log")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    def testScpPushFileCmdwithExtraArgs(self):
        """Test scp pull file command."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        ssh_object = ssh.Ssh(self.FAKE_IP,
                             self.FAKE_SSH_USER,
                             self.FAKE_SSH_PRIVATE_KEY_PATH,
                             self.FAKE_EXTRA_ARGS_SSH)
        ssh_object.ScpPushFile("/tmp/test", "/tmp/test_1.log")
        expected_cmd = (
            "exec /usr/bin/scp -i /fake/acloud_rea -o LogLevel=ERROR -o ControlPath=none "
            "-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
            "-o ProxyCommand='ssh fake_user@2.2.2.2 Server 22' "
            "/tmp/test fake_user@1.1.1.1:/tmp/test_1.log")
        subprocess.Popen.assert_called_with(expected_cmd,
                                            shell=True,
                                            stderr=-2,
                                            stdin=None,
                                            stdout=-1,
                                            universal_newlines=True)

    # pylint: disable=protected-access
    def testIPAddress(self):
        """Test IP class to get ip address."""
        # Internal ip case.
        ssh_object = ssh.Ssh(ip=ssh.IP(external="1.1.1.1", internal="10.1.1.1"),
                             user=self.FAKE_SSH_USER,
                             ssh_private_key_path=self.FAKE_SSH_PRIVATE_KEY_PATH,
                             report_internal_ip=True)
        expected_ip = "10.1.1.1"
        self.assertEqual(ssh_object._ip, expected_ip)

        # External ip case.
        ssh_object = ssh.Ssh(ip=ssh.IP(external="1.1.1.1", internal="10.1.1.1"),
                             user=self.FAKE_SSH_USER,
                             ssh_private_key_path=self.FAKE_SSH_PRIVATE_KEY_PATH)
        expected_ip = "1.1.1.1"
        self.assertEqual(ssh_object._ip, expected_ip)

        # Only one ip case.
        ssh_object = ssh.Ssh(ip=ssh.IP(ip="1.1.1.1"),
                             user=self.FAKE_SSH_USER,
                             ssh_private_key_path=self.FAKE_SSH_PRIVATE_KEY_PATH)
        expected_ip = "1.1.1.1"
        self.assertEqual(ssh_object._ip, expected_ip)

    def testWaitForSsh(self):
        """Test WaitForSsh."""
        ssh_object = ssh.Ssh(ip=self.FAKE_IP,
                             user=self.FAKE_SSH_USER,
                             ssh_private_key_path=self.FAKE_SSH_PRIVATE_KEY_PATH,
                             report_internal_ip=self.FAKE_REPORT_INTERNAL_IP)
        self.created_subprocess.returncode = -1
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.assertRaises(subprocess.CalledProcessError,
                          ssh_object.WaitForSsh,
                          timeout=1,
                          max_retry=1)

    def testSshCallWait(self):
        """Test SshCallWait."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        ssh._SshCallWait(fake_cmd)
        threading.Timer.assert_not_called()

    def testSshCallWaitTimeout(self):
        """Test SshCallWait with timeout."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        fake_timeout = 30
        ssh._SshCallWait(fake_cmd, fake_timeout)
        threading.Timer.assert_called_once()

    def testSshCall(self):
        """Test _SshCall."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        ssh._SshCall(fake_cmd)
        threading.Timer.assert_not_called()

    def testSshCallTimeout(self):
        """Test SshCallWait with timeout."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        fake_timeout = 30
        ssh._SshCall(fake_cmd, fake_timeout)
        threading.Timer.assert_called_once()

    def testSshLogOutput(self):
        """Test _SshCall."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        ssh._SshLogOutput(fake_cmd)
        threading.Timer.assert_not_called()

        # Test with all kind of exceptions.
        self.created_subprocess.returncode = 255
        self.assertRaises(
            errors.DeviceConnectionError, ssh._SshLogOutput, fake_cmd)

        self.created_subprocess.returncode = -1
        self.assertRaises(
            subprocess.CalledProcessError, ssh._SshLogOutput, fake_cmd)

        with mock.patch("sys.stderr", new=io.StringIO()):
            self.created_subprocess.communicate = mock.MagicMock(
                return_value=(constants.ERROR_MSG_VNC_NOT_SUPPORT, ''))
            self.assertRaises(
                errors.LaunchCVDFail, ssh._SshLogOutput, fake_cmd)

        with mock.patch("sys.stderr", new=io.StringIO()):
            self.created_subprocess.communicate = mock.MagicMock(
                return_value=(constants.ERROR_MSG_WEBRTC_NOT_SUPPORT, ''))
            self.assertRaises(
                errors.LaunchCVDFail, ssh._SshLogOutput, fake_cmd)

    def testSshLogOutputTimeout(self):
        """Test SshCallWait with timeout."""
        self.Patch(subprocess, "Popen", return_value=self.created_subprocess)
        self.Patch(threading, "Timer")
        fake_cmd = "fake command"
        fake_timeout = 30
        ssh._SshLogOutput(fake_cmd, fake_timeout)
        threading.Timer.assert_called_once()

    def testGetErrorMessage(self):
        """Test _GetErrorMessage."""
        # should return response
        fake_output = """
fetch_cvd E 10-25 09:45:44  1337  1337 build_api.cc:184] URL endpoint did not have json path: {
fetch_cvd E 10-25 09:45:44  1337  1337 build_api.cc:184] 	"error" : "Failed to parse json.",
fetch_cvd E 10-25 09:45:44  1337  1337 build_api.cc:184] 	"response" : "fake_error_response"
fetch_cvd E 10-25 09:45:44  1337  1337 build_api.cc:184] }
fetch_cvd E 10-25 09:45:44  1337  1337 fetch_cvd.cc:102] Unable to download."""
        self.assertEqual(ssh._GetErrorMessage(fake_output), "fake_error_response")

        # should return message only
        fake_output = """
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] Error fetching the artifacts
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	"error" :
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	{
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"code" : 500,
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"errors" :
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		[
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 			{}
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		],
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"message" : "Unknown Error.",
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"status" : "UNKNOWN"
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	}
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] }", and code was 500Fail! (320s)"""
        self.assertEqual(ssh._GetErrorMessage(fake_output), "Unknown Error.")

        # should output last 10 line
        fake_output = """
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] Error fetching the artifacts of {
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	"error" :
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	{
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"code" : 500,
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"errors" :
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		[
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 			{}
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		],
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 		"status" : "UNKNOWN"
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] 	}
fetch_cvd F 11-15 07:34:13  2169  2169 build_api.cc:164] }", and code was 500Fail! (320s)"""
        self.assertEqual(ssh._GetErrorMessage(fake_output), "\n".join(
            fake_output.splitlines()[-10::]))

    def testFilterUnusedContent(self):
        """Test _FilterUnusedContent."""
        # should remove html, !, title, span, a, p, b, style, ins, code, \n
        fake_content = ("<!DOCTYPE html><html lang=en>\\n<meta charset=utf-8>"
                        "<title>Error</title>\\n<style>*{padding:0}html}</style>"
                        "<a href=//www.google.com/><span id=logo></span></a>"
                        "<p><b>404.</b> <ins>That\u2019s an error.</ins><p>"
                        "The requested URL was not found on this server <code>"
                        "url/id</code> <ins>That\u2019s all we know.</ins>\\n")
        expected = (" Error 404. That’s an error.The requested URL was not"
                    " found on this server url/id That’s all we know. ")
        self.assertEqual(ssh._FilterUnusedContent(fake_content), expected)


if __name__ == "__main__":
    unittest.main()
