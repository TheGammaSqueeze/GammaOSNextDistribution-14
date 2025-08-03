#!/usr/bin/env python
#
# Copyright 2016 - The Android Open Source Project
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
"""Tests for acloud.internal.lib.utils."""

import collections
import errno
import getpass
import grp
import os
import shutil
import subprocess
import tempfile
import time
import webbrowser

import unittest

from unittest import mock

from acloud import errors
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import utils


GroupInfo = collections.namedtuple("GroupInfo", [
    "gr_name",
    "gr_passwd",
    "gr_gid",
    "gr_mem"])

# Tkinter may not be supported so mock it out.
try:
    import Tkinter
except ImportError:
    Tkinter = mock.Mock()


class FakeTkinter:
    """Fake implementation of Tkinter.Tk()"""

    def __init__(self, width=None, height=None):
        self.width = width
        self.height = height

    # pylint: disable=invalid-name
    def winfo_screenheight(self):
        """Return the screen height."""
        return self.height

    # pylint: disable=invalid-name
    def winfo_screenwidth(self):
        """Return the screen width."""
        return self.width


# pylint: disable=too-many-public-methods
class UtilsTest(driver_test_lib.BaseDriverTest):
    """Test Utils."""

    def TestTempDirSuccess(self):
        """Test create a temp dir."""
        self.Patch(os, "chmod")
        self.Patch(tempfile, "mkdtemp", return_value="/tmp/tempdir")
        self.Patch(shutil, "rmtree")
        with utils.TempDir():
            pass
        # Verify.
        tempfile.mkdtemp.assert_called_once()  # pylint: disable=no-member
        shutil.rmtree.assert_called_with("/tmp/tempdir")  # pylint: disable=no-member

    def TestTempDirExceptionRaised(self):
        """Test create a temp dir and exception is raised within with-clause."""
        self.Patch(os, "chmod")
        self.Patch(tempfile, "mkdtemp", return_value="/tmp/tempdir")
        self.Patch(shutil, "rmtree")

        class ExpectedException(Exception):
            """Expected exception."""

        def _Call():
            with utils.TempDir():
                raise ExpectedException("Expected exception.")

        # Verify. ExpectedException should be raised.
        self.assertRaises(ExpectedException, _Call)
        tempfile.mkdtemp.assert_called_once()  # pylint: disable=no-member
        shutil.rmtree.assert_called_with("/tmp/tempdir")  #pylint: disable=no-member

    def testTempDirWhenDeleteTempDirNoLongerExist(self):  # pylint: disable=invalid-name
        """Test create a temp dir and dir no longer exists during deletion."""
        self.Patch(os, "chmod")
        self.Patch(tempfile, "mkdtemp", return_value="/tmp/tempdir")
        expected_error = EnvironmentError()
        expected_error.errno = errno.ENOENT
        self.Patch(shutil, "rmtree", side_effect=expected_error)

        def _Call():
            with utils.TempDir():
                pass

        # Verify no exception should be raised when rmtree raises
        # EnvironmentError with errno.ENOENT, i.e.
        # directory no longer exists.
        _Call()
        tempfile.mkdtemp.assert_called_once()  #pylint: disable=no-member
        shutil.rmtree.assert_called_with("/tmp/tempdir")  #pylint: disable=no-member

    def testTempDirWhenDeleteEncounterError(self):
        """Test create a temp dir and encoutered error during deletion."""
        self.Patch(os, "chmod")
        self.Patch(tempfile, "mkdtemp", return_value="/tmp/tempdir")
        expected_error = OSError("Expected OS Error")
        self.Patch(shutil, "rmtree", side_effect=expected_error)

        def _Call():
            with utils.TempDir():
                pass

        # Verify OSError should be raised.
        self.assertRaises(OSError, _Call)
        tempfile.mkdtemp.assert_called_once()  #pylint: disable=no-member
        shutil.rmtree.assert_called_with("/tmp/tempdir")  #pylint: disable=no-member

    def testTempDirOrininalErrorRaised(self):
        """Test original error is raised even if tmp dir deletion failed."""
        self.Patch(os, "chmod")
        self.Patch(tempfile, "mkdtemp", return_value="/tmp/tempdir")
        expected_error = OSError("Expected OS Error")
        self.Patch(shutil, "rmtree", side_effect=expected_error)

        class ExpectedException(Exception):
            """Expected exception."""

        def _Call():
            with utils.TempDir():
                raise ExpectedException("Expected Exception")

        # Verify.
        # ExpectedException should be raised, and OSError
        # should not be raised.
        self.assertRaises(ExpectedException, _Call)
        tempfile.mkdtemp.assert_called_once()  #pylint: disable=no-member
        shutil.rmtree.assert_called_with("/tmp/tempdir")  #pylint: disable=no-member

    def testCreateSshKeyPairKeyAlreadyExists(self):  #pylint: disable=invalid-name
        """Test when the key pair already exists."""
        public_key = "/fake/public_key"
        private_key = "/fake/private_key"
        self.Patch(os.path, "exists", side_effect=[True, True])
        self.Patch(subprocess, "check_call")
        self.Patch(os, "makedirs", return_value=True)
        utils.CreateSshKeyPairIfNotExist(private_key, public_key)
        self.assertEqual(subprocess.check_call.call_count, 0)  #pylint: disable=no-member

    def testCreateSshKeyPairKeyAreCreated(self):
        """Test when the key pair created."""
        public_key = "/fake/public_key"
        private_key = "/fake/private_key"
        self.Patch(os.path, "exists", return_value=False)
        self.Patch(os, "makedirs", return_value=True)
        self.Patch(subprocess, "check_call")
        self.Patch(os, "rename")
        utils.CreateSshKeyPairIfNotExist(private_key, public_key)
        self.assertEqual(subprocess.check_call.call_count, 1)  #pylint: disable=no-member
        subprocess.check_call.assert_called_with(  #pylint: disable=no-member
            utils.SSH_KEYGEN_CMD +
            ["-C", getpass.getuser(), "-f", private_key],
            stdout=mock.ANY,
            stderr=mock.ANY)

    def testCreatePublicKeyAreCreated(self):
        """Test when the PublicKey created."""
        public_key = "/fake/public_key"
        private_key = "/fake/private_key"
        self.Patch(os.path, "exists", side_effect=[False, True, True])
        self.Patch(os, "makedirs", return_value=True)
        mock_open = mock.mock_open(read_data=public_key)
        self.Patch(subprocess, "check_output")
        self.Patch(os, "rename")
        with mock.patch("builtins.open", mock_open):
            utils.CreateSshKeyPairIfNotExist(private_key, public_key)
        self.assertEqual(subprocess.check_output.call_count, 1)  #pylint: disable=no-member
        subprocess.check_output.assert_called_with(  #pylint: disable=no-member
            utils.SSH_KEYGEN_PUB_CMD +["-f", private_key])

    def TestRetryOnException(self):
        """Test Retry."""

        def _IsValueError(exc):
            return isinstance(exc, ValueError)

        num_retry = 5

        @utils.RetryOnException(_IsValueError, num_retry)
        def _RaiseAndRetry(sentinel):
            sentinel.alert()
            raise ValueError("Fake error.")

        sentinel = mock.MagicMock()
        self.assertRaises(ValueError, _RaiseAndRetry, sentinel)
        self.assertEqual(1 + num_retry, sentinel.alert.call_count)

    def testRetryExceptionType(self):
        """Test RetryExceptionType function."""

        def _RaiseAndRetry(sentinel):
            sentinel.alert()
            raise ValueError("Fake error.")

        num_retry = 5
        sentinel = mock.MagicMock()
        self.assertRaises(
            ValueError,
            utils.RetryExceptionType, (KeyError, ValueError),
            num_retry,
            _RaiseAndRetry,
            0, # sleep_multiplier
            1, # retry_backoff_factor
            sentinel=sentinel)
        self.assertEqual(1 + num_retry, sentinel.alert.call_count)

    def testRetry(self):
        """Test Retry."""
        mock_sleep = self.Patch(time, "sleep")

        def _RaiseAndRetry(sentinel):
            sentinel.alert()
            raise ValueError("Fake error.")

        num_retry = 5
        sentinel = mock.MagicMock()
        self.assertRaises(
            ValueError,
            utils.RetryExceptionType, (ValueError, KeyError),
            num_retry,
            _RaiseAndRetry,
            1, # sleep_multiplier
            2, # retry_backoff_factor
            sentinel=sentinel)

        self.assertEqual(1 + num_retry, sentinel.alert.call_count)
        mock_sleep.assert_has_calls(
            [
                mock.call(1),
                mock.call(2),
                mock.call(4),
                mock.call(8),
                mock.call(16)
            ])

    @mock.patch("builtins.input")
    def testGetAnswerFromList(self, mock_raw_input):
        """Test GetAnswerFromList."""
        answer_list = ["image1.zip", "image2.zip", "image3.zip"]
        mock_raw_input.return_value = 0
        with self.assertRaises(SystemExit):
            utils.GetAnswerFromList(answer_list)
        mock_raw_input.side_effect = [1, 2, 3, 4]
        self.assertEqual(utils.GetAnswerFromList(answer_list),
                         ["image1.zip"])
        self.assertEqual(utils.GetAnswerFromList(answer_list),
                         ["image2.zip"])
        self.assertEqual(utils.GetAnswerFromList(answer_list),
                         ["image3.zip"])
        self.assertEqual(utils.GetAnswerFromList(answer_list,
                                                 enable_choose_all=True),
                         answer_list)

    @unittest.skipIf(isinstance(Tkinter, mock.Mock), "Tkinter mocked out, test case not needed.")
    @mock.patch.object(Tkinter, "Tk")
    def testCalculateVNCScreenRatio(self, mock_tk):
        """Test Calculating the scale ratio of VNC display."""
        # Get scale-down ratio if screen height is smaller than AVD height.
        mock_tk.return_value = FakeTkinter(height=800, width=1200)
        avd_h = 1920
        avd_w = 1080
        self.assertEqual(utils.CalculateVNCScreenRatio(avd_w, avd_h), 0.4)

        # Get scale-down ratio if screen width is smaller than AVD width.
        mock_tk.return_value = FakeTkinter(height=800, width=1200)
        avd_h = 900
        avd_w = 1920
        self.assertEqual(utils.CalculateVNCScreenRatio(avd_w, avd_h), 0.6)

        # Scale ratio = 1 if screen is larger than AVD.
        mock_tk.return_value = FakeTkinter(height=1080, width=1920)
        avd_h = 800
        avd_w = 1280
        self.assertEqual(utils.CalculateVNCScreenRatio(avd_w, avd_h), 1)

        # Get the scale if ratio of width is smaller than the
        # ratio of height.
        mock_tk.return_value = FakeTkinter(height=1200, width=800)
        avd_h = 1920
        avd_w = 1080
        self.assertEqual(utils.CalculateVNCScreenRatio(avd_w, avd_h), 0.6)

    def testCheckUserInGroups(self):
        """Test CheckUserInGroups."""
        self.Patch(getpass, "getuser", return_value="user_0")
        self.Patch(grp, "getgrall", return_value=[
            GroupInfo("fake_group1", "passwd_1", 0, ["user_1", "user_2"]),
            GroupInfo("fake_group2", "passwd_2", 1, ["user_1", "user_2"])])
        self.Patch(grp, "getgrnam", return_value=GroupInfo(
            "fake_group1", "passwd_1", 0, ["user_1", "user_2"]))
        # Test Group name doesn't exist.
        self.assertFalse(utils.CheckUserInGroups(["Non_exist_group"]))

        # Test User isn't in group.
        self.assertFalse(utils.CheckUserInGroups(["fake_group1"]))

        # Test User is in group.
        self.Patch(getpass, "getuser", return_value="user_1")
        self.assertTrue(utils.CheckUserInGroups(["fake_group1"]))

    @mock.patch.object(utils, "CheckUserInGroups")
    def testAddUserGroupsToCmd(self, mock_user_group):
        """Test AddUserGroupsToCmd."""
        command = "test_command"
        groups = ["group1", "group2"]
        # Don't add user group in command
        mock_user_group.return_value = True
        expected_value = "test_command"
        self.assertEqual(expected_value, utils.AddUserGroupsToCmd(command,
                                                                  groups))

        # Add user group in command
        mock_user_group.return_value = False
        expected_value = "sg group1 <<EOF\nsg group2\ntest_command\nEOF"
        self.assertEqual(expected_value, utils.AddUserGroupsToCmd(command,
                                                                  groups))

    # pylint: disable=invalid-name
    def testTimeoutException(self):
        """Test TimeoutException."""
        @utils.TimeoutException(1, "should time out")
        def functionThatWillTimeOut():
            """Test decorator of @utils.TimeoutException should timeout."""
            time.sleep(5)

        self.assertRaises(errors.FunctionTimeoutError,
                          functionThatWillTimeOut)


    def testTimeoutExceptionNoTimeout(self):
        """Test No TimeoutException."""
        @utils.TimeoutException(5, "shouldn't time out")
        def functionThatShouldNotTimeout():
            """Test decorator of @utils.TimeoutException shouldn't timeout."""
            return None
        try:
            functionThatShouldNotTimeout()
        except errors.FunctionTimeoutError:
            self.fail("shouldn't timeout")

    def testEstablishSshTunnel(self):
        """Test EstablishSshTunnel."""
        ip_addr = "1.1.1.1"
        rsa_key_file = "/tmp/rsa_file"
        port_mapping = [(1111, 2222), (8888, 9999)]
        ssh_user = "fake_user"
        mock_execute_command = self.Patch(utils, "_ExecuteCommand")
        utils.EstablishSshTunnel(ip_addr, rsa_key_file, ssh_user,
                                 port_mapping, "-o command='shell %s %h'")
        arg_list = ["-i", rsa_key_file, "-o", "ControlPath=none",
                    "-o", "UserKnownHostsFile=/dev/null",
                    "-o", "StrictHostKeyChecking=no",
                    "-L", "1111:127.0.0.1:2222",
                    "-L", "8888:127.0.0.1:9999",
                    "-N", "-f",
                    "-l", ssh_user, ip_addr,
                    "-o", "command=shell %s %h"]
        mock_execute_command.assert_called_with("ssh", arg_list)

    def testAutoConnectCreateSSHTunnelFail(self):
        """Test auto connect."""
        fake_ip_addr = "1.1.1.1"
        fake_rsa_key_file = "/tmp/rsa_file"
        fake_target_vnc_port = 8888
        target_adb_port = 9999
        ssh_user = "fake_user"
        call_side_effect = subprocess.CalledProcessError(123, "fake",
                                                         "fake error")
        result = utils.ForwardedPorts(vnc_port=None, adb_port=None)
        self.Patch(utils, "EstablishSshTunnel", side_effect=call_side_effect)
        self.assertEqual(result, utils.AutoConnect(fake_ip_addr,
                                                   fake_rsa_key_file,
                                                   fake_target_vnc_port,
                                                   target_adb_port,
                                                   ssh_user))

    def testAutoConnectWithExtraArgs(self):
        """Test extra args will be the same with expanded args."""
        fake_ip_addr = "1.1.1.1"
        fake_rsa_key_file = "/tmp/rsa_file"
        fake_target_vnc_port = 8888
        target_adb_port = 9999
        ssh_user = "fake_user"
        fake_port = 12345
        self.Patch(utils, "PickFreePort", return_value=fake_port)
        mock_execute_command = self.Patch(utils, "_ExecuteCommand")
        mock_establish_ssh_tunnel = self.Patch(utils, "EstablishSshTunnel")
        extra_args_ssh_tunnel = "-o command='shell %s %h' -o command1='ls -la'"
        utils.AutoConnect(ip_addr=fake_ip_addr,
                          rsa_key_file=fake_rsa_key_file,
                          target_vnc_port=fake_target_vnc_port,
                          target_adb_port=target_adb_port,
                          ssh_user=ssh_user,
                          client_adb_port=fake_port,
                          extra_args_ssh_tunnel=extra_args_ssh_tunnel)
        mock_establish_ssh_tunnel.assert_called_with(
            fake_ip_addr,
            fake_rsa_key_file,
            ssh_user,
            [utils.PortMapping(fake_port, target_adb_port),
             utils.PortMapping(fake_port, fake_target_vnc_port)],
            extra_args_ssh_tunnel)
        mock_execute_command.assert_called_with(
            "adb", ["connect", "127.0.0.1:12345"])

    def testEstablishWebRTCSshTunnel(self):
        """Test establish WebRTC ssh tunnel."""
        fake_ip_addr = "1.1.1.1"
        fake_rsa_key_file = "/tmp/rsa_file"
        ssh_user = "fake_user"
        fake_webrtc_local_port = 12345
        self.Patch(utils, "GetWebRTCServerPort", return_value=8443)
        mock_establish_ssh_tunnel = self.Patch(utils, "EstablishSshTunnel")
        fake_port_mapping = [utils.PortMapping(port, port) for port in range(15555, 15579 + 1)] + [utils.PortMapping(12345, 8443)]

        utils.EstablishWebRTCSshTunnel(
            ip_addr=fake_ip_addr, rsa_key_file=fake_rsa_key_file,
            ssh_user=ssh_user, webrtc_local_port=fake_webrtc_local_port)
        mock_establish_ssh_tunnel.assert_called_with(
            fake_ip_addr,
            fake_rsa_key_file,
            ssh_user,
            fake_port_mapping,
            None)

        mock_establish_ssh_tunnel.reset_mock()
        extra_args_ssh_tunnel = "-o command='shell %s %h'"
        utils.EstablishWebRTCSshTunnel(
            ip_addr=fake_ip_addr, rsa_key_file=fake_rsa_key_file,
            ssh_user=ssh_user, extra_args_ssh_tunnel=extra_args_ssh_tunnel,
            webrtc_local_port=fake_webrtc_local_port)
        mock_establish_ssh_tunnel.assert_called_with(
            fake_ip_addr,
            fake_rsa_key_file,
            ssh_user,
            fake_port_mapping,
            extra_args_ssh_tunnel)

    def testGetWebRTCServerPort(self):
        """test GetWebRTCServerPort."""
        fake_ip_addr = "1.1.1.1"
        fake_rsa_key_file = "/tmp/rsa_file"
        ssh_user = "fake_user"
        extra_args_ssh_tunnel = "-o command='shell %s %h'"
        fake_subprocess = mock.MagicMock()
        fake_subprocess.returncode = 0
        fake_subprocess.communicate = mock.MagicMock(
            return_value=('', ''))
        self.Patch(subprocess, "Popen", return_value=fake_subprocess)
        self.assertEqual(1443, utils.GetWebRTCServerPort(
            fake_ip_addr, fake_rsa_key_file,ssh_user,extra_args_ssh_tunnel))

        # Test the case that find "webrtc_operator" process.
        webrtc_operator_process = "11:45 bin/webrtc_operator -assets_dir=assets"
        fake_subprocess.communicate = mock.MagicMock(
            return_value=(webrtc_operator_process, ''))
        self.assertEqual(8443, utils.GetWebRTCServerPort(
            fake_ip_addr, fake_rsa_key_file,ssh_user,extra_args_ssh_tunnel))

    def testGetWebrtcPortFromSSHTunnel(self):
        """"Test Get forwarding webrtc port from ssh tunnel."""
        fake_ps_output = ("/fake_ps_1 --fake arg \n"
                          "/fake_ps_2 --fake arg \n"
                          "/usr/bin/ssh -i ~/.ssh/acloud_rsa -o ControlPath=none "
                          "-o UserKnownHostsFile=/dev/null "
                          "-o StrictHostKeyChecking=no -L 15551:127.0.0.1:15551 "
                          "-L 12345:127.0.0.1:8443 -N -f -l user 1.1.1.1").encode()
        self.Patch(subprocess, "check_output", return_value=fake_ps_output)
        webrtc_ports = utils.GetWebrtcPortFromSSHTunnel("1.1.1.1")
        self.assertEqual(12345, webrtc_ports)

    @mock.patch("acloud.internal.lib.utils.subprocess")
    def testFindRemoteFiles(self, mock_subprocess):
        """Test FindRemoteFiles."""
        mock_ssh = mock.Mock()

        paths = utils.FindRemoteFiles(mock_ssh, [])
        mock_subprocess.run.assert_not_called()
        self.assertEqual([], paths)

        mock_ssh.GetBaseCmd.return_value = "mock_ssh"
        mock_subprocess.run.return_value = mock.Mock(
            returncode=0, stderr=b'stderr', stdout=b'file1\nfile2\n')
        paths = utils.FindRemoteFiles(mock_ssh, ["dir1", "dir2"])
        self.assertEqual(["file1", "file2"], paths)
        mock_subprocess.run.assert_called_with(
            'mock_ssh find -H dir1 dir2 -type f',
            shell=True, capture_output=True, check=False)

        mock_subprocess.run.return_value = mock.Mock(
            returncode=0, stderr=b'', stdout=b'')
        paths = utils.FindRemoteFiles(mock_ssh, ["dir1", "dir2"])
        self.assertEqual([], paths)

        mock_subprocess.run.return_value = mock.Mock(
            returncode=1, stderr=b'', stdout=b'')
        with self.assertRaises(errors.SubprocessFail):
            utils.FindRemoteFiles(mock_ssh, ["dir1", "dir2"])

    # pylint: disable=protected-access, no-member
    def testCleanupSSVncviwer(self):
        """test cleanup ssvnc viewer."""
        fake_vnc_port = 9999
        fake_ss_vncviewer_pattern = utils._SSVNC_VIEWER_PATTERN % {
            "vnc_port": fake_vnc_port}
        self.Patch(utils, "IsCommandRunning", return_value=True)
        self.Patch(subprocess, "check_call", return_value=True)
        utils.CleanupSSVncviewer(fake_vnc_port)
        subprocess.check_call.assert_called_with(["pkill", "-9", "-f", fake_ss_vncviewer_pattern])

        subprocess.check_call.call_count = 0
        self.Patch(utils, "IsCommandRunning", return_value=False)
        utils.CleanupSSVncviewer(fake_vnc_port)
        subprocess.check_call.assert_not_called()

    def testLaunchBrowserFromReport(self):
        """test launch browser from report."""
        self.Patch(webbrowser, "open_new_tab")
        fake_report = mock.MagicMock(data={})

        # test remote instance
        self.Patch(os.environ, "get", return_value=True)
        fake_report.data = {
            "devices": [{"instance_name": "remote_cf_instance_name",
                         "ip": "192.168.1.1",},],}

        utils.LaunchBrowserFromReport(fake_report)
        webbrowser.open_new_tab.assert_called_once_with("https://localhost:8443")
        webbrowser.open_new_tab.call_count = 0

        # test local instance
        fake_report.data = {
            "devices": [{"instance_name": "local-instance1",
                         "ip": "127.0.0.1:6250",},],}
        utils.LaunchBrowserFromReport(fake_report)
        webbrowser.open_new_tab.assert_called_once_with("https://localhost:8443")
        webbrowser.open_new_tab.call_count = 0

        # verify terminal can't support launch webbrowser.
        self.Patch(os.environ, "get", return_value=False)
        utils.LaunchBrowserFromReport(fake_report)
        self.assertEqual(webbrowser.open_new_tab.call_count, 0)

    def testSetExecutable(self):
        """test setting a file to be executable."""
        with tempfile.NamedTemporaryFile(delete=True) as temp_file:
            utils.SetExecutable(temp_file.name)
            self.assertEqual(os.stat(temp_file.name).st_mode & 0o777, 0o755)

    def testSetDirectoryTreeExecutable(self):
        """test setting a file in a directory to be executable."""
        with tempfile.TemporaryDirectory() as temp_dir:
            subdir = os.path.join(temp_dir, "subdir")
            file_path = os.path.join(subdir, "file")
            os.makedirs(subdir)
            with open(file_path, "w"):
                pass
            utils.SetDirectoryTreeExecutable(temp_dir)
            self.assertEqual(os.stat(file_path).st_mode & 0o777, 0o755)

    def testSetCvdPort(self):
        """test base_instance_num."""
        utils.SetCvdPorts(2)
        self.assertEqual(utils.GetCvdPorts().adb_port, 6521)
        self.assertEqual(utils.GetCvdPorts().vnc_port, 6445)
        utils.SetCvdPorts(None)


    @mock.patch.object(utils, "PrintColorString")
    def testPrintDeviceSummary(self, mock_print):
        """test PrintDeviceSummary."""
        fake_report = mock.MagicMock(data={})
        fake_report.data = {
            "devices": [{"instance_name": "remote_cf_instance_name",
                         "ip": "192.168.1.1",
                         "device_serial": "127.0.0.1:399"},],}
        utils.PrintDeviceSummary(fake_report)
        self.assertEqual(mock_print.call_count, 7)

        # Test for OpenWrt device case.
        fake_report.data = {
            "devices": [{"instance_name": "remote_cf_instance_name",
                         "ip": "192.168.1.1",
                         "ssh_command": "fake_ssh_cmd",
                         "screen_command": "fake_screen_cmd"},],}
        mock_print.reset_mock()
        utils.PrintDeviceSummary(fake_report)
        self.assertEqual(mock_print.call_count, 13)

        # Test for fail case
        fake_report.data = {
            "errors": "Fail to create devices"}
        mock_print.reset_mock()
        utils.PrintDeviceSummary(fake_report)
        self.assertEqual(mock_print.call_count, 3)

    # pylint: disable=protected-access
    def testIsSupportedKvm(self):
        """Test IsSupportedKvm."""
        self.Patch(os.path, "exists", return_value=True)
        self.assertTrue(utils.IsSupportedKvm())

        self.Patch(os.path, "exists", return_value=False)
        self.assertFalse(utils.IsSupportedKvm())


if __name__ == "__main__":
    unittest.main()
