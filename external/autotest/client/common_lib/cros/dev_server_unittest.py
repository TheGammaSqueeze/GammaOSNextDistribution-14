#!/usr/bin/python3
#
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for client/common_lib/cros/dev_server.py."""

import six.moves.http_client
import json
import os
import six
from six.moves import urllib
import time
import unittest
from unittest import mock
from unittest.mock import patch, call

import common
from autotest_lib.client.bin import utils as bin_utils
from autotest_lib.client.common_lib import android_utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib.test_utils import comparators

from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import retry


def retry_mock(ExceptionToCheck, timeout_min, exception_to_raise=None,
               label=None):
    """A mock retry decorator to use in place of the actual one for testing.

    @param ExceptionToCheck: the exception to check.
    @param timeout_mins: Amount of time in mins to wait before timing out.
    @param exception_to_raise: the exception to raise in retry.retry
    @param label: used in debug messages

    """
    def inner_retry(func):
        """The actual decorator.

        @param func: Function to be called in decorator.

        """
        return func

    return inner_retry


class MockSshResponse(object):
    """An ssh response mocked for testing."""

    def __init__(self, output, exit_status=0):
        self.stdout = output
        self.exit_status = exit_status
        self.stderr = 'SSH connection error occurred.'


class MockSshError(error.CmdError):
    """An ssh error response mocked for testing."""

    def __init__(self):
        self.result_obj = MockSshResponse('error', exit_status=255)


E403 = urllib.error.HTTPError(url='',
                              code=six.moves.http_client.FORBIDDEN,
                              msg='Error 403',
                              hdrs=None,
                              fp=six.StringIO('Expected.'))
E500 = urllib.error.HTTPError(url='',
                              code=six.moves.http_client.INTERNAL_SERVER_ERROR,
                              msg='Error 500',
                              hdrs=None,
                              fp=six.StringIO('Expected.'))
CMD_ERROR = error.CmdError('error_cmd', MockSshError().result_obj)


class RunCallTest(unittest.TestCase):
    """Unit tests for ImageServerBase.run_call or DevServer.run_call."""

    def setUp(self):
        """Set up the test"""
        self.test_call = 'http://nothing/test'
        self.hostname = 'nothing'
        self.contents = 'true'
        self.contents_readline = ['file/one', 'file/two']
        self.save_ssh_config = dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER
        super(RunCallTest, self).setUp()

        run_patcher = patch.object(utils, 'run', spec=True)
        self.utils_run_mock = run_patcher.start()
        self.addCleanup(run_patcher.stop)

        urlopen_patcher = patch.object(urllib.request, 'urlopen', spec=True)
        self.urlopen_mock = urlopen_patcher.start()
        self.addCleanup(urlopen_patcher.stop)

        sleep = mock.patch('time.sleep', autospec=True)
        sleep.start()
        self.addCleanup(sleep.stop)


    def tearDown(self):
        """Tear down the test"""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = self.save_ssh_config
        super(RunCallTest, self).tearDown()


    def testRunCallHTTPWithDownDevserver(self):
        """Test dev_server.ImageServerBase.run_call using http with arg:
        (call)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False

        urllib.request.urlopen.side_effect = [
                six.StringIO(dev_server.ERR_MSG_FOR_DOWN_DEVSERVER),
                six.StringIO(self.contents)
        ]

        response = dev_server.ImageServerBase.run_call(self.test_call)
        self.assertEquals(self.contents, response)
        self.urlopen_mock.assert_called_with(
                comparators.Substring(self.test_call))

    def testRunCallSSHWithDownDevserver(self):
        """Test dev_server.ImageServerBase.run_call using http with arg:
        (call)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = True
        with patch.object(utils, 'get_restricted_subnet') as subnet_patch:
            utils.get_restricted_subnet.return_value = self.hostname

            to_return1 = MockSshResponse(dev_server.ERR_MSG_FOR_DOWN_DEVSERVER)
            to_return2 = MockSshResponse(self.contents)
            utils.run.side_effect = [to_return1, to_return2]

            response = dev_server.ImageServerBase.run_call(self.test_call)
            self.assertEquals(self.contents, response)
            dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False

            self.utils_run_mock.assert_has_calls([
                    call(comparators.Substring(self.test_call),
                         timeout=mock.ANY),
                    call(comparators.Substring(self.test_call),
                         timeout=mock.ANY)
            ])

            subnet_patch.assert_called_with(self.hostname,
                                            utils.get_all_restricted_subnets())

    def testRunCallWithSingleCallHTTP(self):
        """Test dev_server.ImageServerBase.run_call using http with arg:
        (call)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False

        urllib.request.urlopen.return_value = six.StringIO(self.contents)
        response = dev_server.ImageServerBase.run_call(self.test_call)
        self.assertEquals(self.contents, response)
        self.urlopen_mock.assert_called_with(
                comparators.Substring(self.test_call))

    def testRunCallWithCallAndReadlineHTTP(self):
        """Test dev_server.ImageServerBase.run_call using http with arg:
        (call, readline=True)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False

        urllib.request.urlopen.return_value = (six.StringIO('\n'.join(
                self.contents_readline)))
        response = dev_server.ImageServerBase.run_call(
                self.test_call, readline=True)
        self.assertEquals(self.contents_readline, response)
        self.urlopen_mock.assert_called_with(
                comparators.Substring(self.test_call))


    def testRunCallWithCallAndTimeoutHTTP(self):
        """Test dev_server.ImageServerBase.run_call using http with args:
        (call, timeout=xxx)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False

        urllib.request.urlopen.return_value = six.StringIO(self.contents)
        response = dev_server.ImageServerBase.run_call(
                self.test_call, timeout=60)
        self.assertEquals(self.contents, response)
        self.urlopen_mock.assert_called_with(comparators.Substring(
                self.test_call),
                                             data=None)


    def testRunCallWithSingleCallSSH(self):
        """Test dev_server.ImageServerBase.run_call using ssh with arg:
        (call)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = True
        with patch.object(utils, 'get_restricted_subnet') as subnet_patch:
            utils.get_restricted_subnet.return_value = self.hostname

            to_return = MockSshResponse(self.contents)
            utils.run.return_value = to_return
            response = dev_server.ImageServerBase.run_call(self.test_call)
            self.assertEquals(self.contents, response)
            subnet_patch.assert_called_with(self.hostname,
                                            utils.get_all_restricted_subnets())
            expected_str = comparators.Substring(self.test_call)
            self.utils_run_mock.assert_called_with(expected_str,
                                                   timeout=mock.ANY)

    def testRunCallWithCallAndReadlineSSH(self):
        """Test dev_server.ImageServerBase.run_call using ssh with args:
        (call, readline=True)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = True
        with patch.object(utils, 'get_restricted_subnet') as subnet_patch:
            utils.get_restricted_subnet.return_value = self.hostname

            to_return = MockSshResponse('\n'.join(self.contents_readline))
            utils.run.return_value = to_return

            response = dev_server.ImageServerBase.run_call(self.test_call,
                                                           readline=True)

            self.assertEquals(self.contents_readline, response)
            subnet_patch.assert_called_with(self.hostname,
                                            utils.get_all_restricted_subnets())

            expected_str = comparators.Substring(self.test_call)
            self.utils_run_mock.assert_called_with(expected_str,
                                                   timeout=mock.ANY)


    def testRunCallWithCallAndTimeoutSSH(self):
        """Test dev_server.ImageServerBase.run_call using ssh with args:
        (call, timeout=xxx)."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = True
        with patch.object(utils, 'get_restricted_subnet') as subnet_patch:
            utils.get_restricted_subnet.return_value = self.hostname

            to_return = MockSshResponse(self.contents)
            utils.run.return_value = to_return
            response = dev_server.ImageServerBase.run_call(self.test_call,
                                                           timeout=60)

            self.assertEquals(self.contents, response)
            subnet_patch.assert_called_with(self.hostname,
                                            utils.get_all_restricted_subnets())

            expected_str = comparators.Substring(self.test_call)
            self.utils_run_mock.assert_called_with(expected_str,
                                                   timeout=mock.ANY)


    def testRunCallWithExceptionHTTP(self):
        """Test dev_server.ImageServerBase.run_call using http with raising
        exception."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = False
        urllib.request.urlopen.side_effect = E500
        self.assertRaises(urllib.error.HTTPError,
                          dev_server.ImageServerBase.run_call,
                          self.test_call)
        self.urlopen_mock.assert_called_with(
                comparators.Substring(self.test_call))


    def testRunCallWithExceptionSSH(self):
        """Test dev_server.ImageServerBase.run_call using ssh with raising
        exception."""
        dev_server.ENABLE_SSH_CONNECTION_FOR_DEVSERVER = True
        with patch.object(utils, 'get_restricted_subnet') as subnet_patch:
            utils.get_restricted_subnet.return_value = self.hostname

            utils.run.side_effect = MockSshError()

            self.assertRaises(error.CmdError,
                              dev_server.ImageServerBase.run_call,
                              self.test_call)
            subnet_patch.assert_called_with(self.hostname,
                                            utils.get_all_restricted_subnets())
            self.utils_run_mock.assert_called_with(comparators.Substring(
                    self.test_call),
                                                   timeout=mock.ANY)

    def testRunCallByDevServerHTTP(self):
        """Test dev_server.DevServer.run_call, which uses http, and can be
        directly called by CrashServer."""
        urllib.request.urlopen.return_value = six.StringIO(self.contents)
        response = dev_server.DevServer.run_call(
               self.test_call, timeout=60)
        self.assertEquals(self.contents, response)
        self.urlopen_mock.assert_called_with(comparators.Substring(
                self.test_call),
                                             data=None)


class DevServerTest(unittest.TestCase):
    """Unit tests for dev_server.DevServer.

    @var _HOST: fake dev server host address.
    """

    _HOST = 'http://nothing'
    _CRASH_HOST = 'http://nothing-crashed'
    _CONFIG = global_config.global_config


    def setUp(self):
        """Set up the test"""
        super(DevServerTest, self).setUp()
        self.crash_server = dev_server.CrashServer(DevServerTest._CRASH_HOST)
        self.dev_server = dev_server.ImageServer(DevServerTest._HOST)
        self.android_dev_server = dev_server.AndroidBuildServer(
                DevServerTest._HOST)
        patcher = patch.object(utils, 'run', spec=True)
        self.utils_run_mock = patcher.start()
        self.addCleanup(patcher.stop)

        patcher2 = patch.object(urllib.request, 'urlopen', spec=True)
        self.urlopen_mock = patcher2.start()
        self.addCleanup(patcher2.stop)

        patcher3 = patch.object(dev_server.ImageServerBase, 'run_call')
        self.run_call_mock = patcher3.start()
        self.addCleanup(patcher3.stop)

        patcher4 = patch.object(os.path, 'exists', spec=True)
        self.os_exists_mock = patcher4.start()
        self.addCleanup(patcher4.stop)

        # Hide local restricted_subnets setting.
        dev_server.RESTRICTED_SUBNETS = []

        _read_json_response_from_devserver = patch.object(
                dev_server.ImageServer, '_read_json_response_from_devserver')
        self._read_json_mock = _read_json_response_from_devserver.start()
        self.addCleanup(_read_json_response_from_devserver.stop)

        sleep = mock.patch('time.sleep', autospec=True)
        sleep.start()
        self.addCleanup(sleep.stop)

        self.image_name = 'fake/image'
        first_staged = comparators.Substrings(
                [self._HOST, self.image_name, 'stage?'])
        second_staged = comparators.Substrings(
                [self._HOST, self.image_name, 'is_staged'])
        self.staged_calls = [call(first_staged), call(second_staged)]

    def _standard_assert_calls(self):
        """Assert the standard calls are made."""
        bad_host, good_host = 'http://bad_host:99', 'http://good_host:8080'

        argument1 = comparators.Substring(bad_host)
        argument2 = comparators.Substring(good_host)
        calls = [
                call(argument1, timeout=mock.ANY),
                call(argument2, timeout=mock.ANY)
        ]
        self.run_call_mock.assert_has_calls(calls)

    def testSimpleResolve(self):
        """One devserver, verify we resolve to it."""
        with patch.object(dev_server,
             '_get_dev_server_list') as server_list_patch, \
                patch.object(dev_server.ImageServer,
                'devserver_healthy') as devserver_healthy_patch:

            dev_server._get_dev_server_list.return_value = ([
                    DevServerTest._HOST
            ])

            dev_server.ImageServer.devserver_healthy.return_value = True
            devserver = dev_server.ImageServer.resolve('my_build')
            self.assertEquals(devserver.url(), DevServerTest._HOST)

            server_list_patch.assert_called_with()
            devserver_healthy_patch.assert_called_with(DevServerTest._HOST)


    def testResolveWithFailure(self):
        """Ensure we rehash on a failed ping on a bad_host."""

        with patch.object(dev_server, '_get_dev_server_list'):
            bad_host, good_host = 'http://bad_host:99', 'http://good_host:8080'
            dev_server._get_dev_server_list.return_value = ([
                    bad_host, good_host
            ])

            # Mock out bad ping failure by raising devserver exception.
            dev_server.ImageServerBase.run_call.side_effect = [
                    dev_server.DevServerException(), '{"free_disk": 1024}'
            ]

            host = dev_server.ImageServer.resolve(
                    0)  # Using 0 as it'll hash to 0.
            self.assertEquals(host.url(), good_host)
            self._standard_assert_calls()


    def testResolveWithFailureURLError(self):
        """Ensure we rehash on a failed ping using http on a bad_host after
        urlerror."""
        # Set retry.retry to retry_mock for just returning the original
        # method for this test. This is to save waiting time for real retry,
        # which is defined by dev_server.DEVSERVER_SSH_TIMEOUT_MINS.
        # Will reset retry.retry to real retry at the end of this test.
        real_retry = retry.retry
        retry.retry = retry_mock

        with patch.object(dev_server, '_get_dev_server_list'):

            bad_host, good_host = 'http://bad_host:99', 'http://good_host:8080'
            dev_server._get_dev_server_list.return_value = ([
                    bad_host, good_host
            ])

            # Mock out bad ping failure by raising devserver exception.
            dev_server.ImageServerBase.run_call.side_effect = [
                    urllib.error.URLError('urlopen connection timeout'),
                    '{"free_disk": 1024}'
            ]

            host = dev_server.ImageServer.resolve(
                    0)  # Using 0 as it'll hash to 0.
            self.assertEquals(host.url(), good_host)

            retry.retry = real_retry
            self._standard_assert_calls()


    def testResolveWithManyDevservers(self):
        """Should be able to return different urls with multiple devservers."""

        with patch.object(dev_server.ImageServer, 'servers'), \
                patch.object(dev_server.DevServer,
                 'devserver_healthy') as devserver_healthy_patch:

            host0_expected = 'http://host0:8080'
            host1_expected = 'http://host1:8082'

            dev_server.ImageServer.servers.return_value = ([
                    host0_expected, host1_expected
            ])
            dev_server.ImageServer.devserver_healthy.return_value = True
            dev_server.ImageServer.devserver_healthy.return_value = True

            host0 = dev_server.ImageServer.resolve(0)
            host1 = dev_server.ImageServer.resolve(1)

            self.assertEqual(host0.url(), host0_expected)
            self.assertEqual(host1.url(), host1_expected)

            calls = [call(host0_expected), call(host1_expected)]
            devserver_healthy_patch.assert_has_calls(calls)


    def testSuccessfulTriggerDownloadSync(self):
        """Call the dev server's download method with synchronous=True."""
        with patch.object(dev_server.ImageServer,
                          '_finish_download') as download_patch:

            dev_server.ImageServerBase.run_call.side_effect = [
                    'Success', 'True'
            ]
            self.dev_server._finish_download.return_value = None

            # Synchronous case requires a call to finish download.
            self.dev_server.trigger_download(self.image_name, synchronous=True)

            download_patch.assert_called_with(self.image_name, mock.ANY,
                                              mock.ANY)

            self.run_call_mock.assert_has_calls(self.staged_calls)


    def testSuccessfulTriggerDownloadASync(self):
        """Call the dev server's download method with synchronous=False."""
        dev_server.ImageServerBase.run_call.side_effect = ['Success', 'True']
        self.dev_server.trigger_download(self.image_name, synchronous=False)

        self.run_call_mock.assert_has_calls(self.staged_calls)

    def testURLErrorRetryTriggerDownload(self):
        """Should retry on URLError, but pass through real exception."""
        with patch.object(time, 'sleep'):

            refused = urllib.error.URLError('[Errno 111] Connection refused')
            dev_server.ImageServerBase.run_call.side_effect = refused
            time.sleep(mock.ANY)

            dev_server.ImageServerBase.run_call.side_effect = E403
            self.assertRaises(dev_server.DevServerException,
                              self.dev_server.trigger_download, '')
            self.run_call_mock.assert_called()


    def testErrorTriggerDownload(self):
        """Should call the dev server's download method using http, fail
        gracefully."""
        dev_server.ImageServerBase.run_call.side_effect = E500
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.trigger_download,
                          '')
        self.run_call_mock.assert_called()


    def testForbiddenTriggerDownload(self):
        """Should call the dev server's download method using http,
        get exception."""
        dev_server.ImageServerBase.run_call.side_effect = E500
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.trigger_download,
                          '')
        self.run_call_mock.assert_called()


    def testCmdErrorTriggerDownload(self):
        """Should call the dev server's download method using ssh, retry
        trigger_download when getting error.CmdError, raise exception for
        urllib2.HTTPError."""

        dev_server.ImageServerBase.run_call.side_effect = [CMD_ERROR, E500]
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.trigger_download,
                          '')
        self.run_call_mock.assert_has_calls([call(mock.ANY), call(mock.ANY)])


    def testSuccessfulFinishDownload(self):
        """Should successfully call the dev server's finish download method."""
        dev_server.ImageServerBase.run_call.side_effect = ['Success', 'True']

        # Synchronous case requires a call to finish download.
        self.dev_server.finish_download(self.image_name)  # Raises on failure.

        self.run_call_mock.assert_has_calls(self.staged_calls)

    def testErrorFinishDownload(self):
        """Should call the dev server's finish download method using http, fail
        gracefully."""
        dev_server.ImageServerBase.run_call.side_effect = E500
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.finish_download,
                          '')
        self.run_call_mock.assert_called()

    def testCmdErrorFinishDownload(self):
        """Should call the dev server's finish download method using ssh,
        retry finish_download when getting error.CmdError, raise exception
        for urllib2.HTTPError."""
        dev_server.ImageServerBase.run_call.side_effect = [CMD_ERROR, E500]

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.finish_download,
                          '')
        self.run_call_mock.assert_has_calls([call(mock.ANY), call(mock.ANY)])

    def testListControlFiles(self):
        """Should successfully list control files from the dev server."""
        control_files = ['file/one', 'file/two']
        argument = comparators.Substrings([self._HOST, self.image_name])
        dev_server.ImageServerBase.run_call.return_value = control_files

        paths = self.dev_server.list_control_files(self.image_name)
        self.assertEquals(len(paths), 2)
        for f in control_files:
            self.assertTrue(f in paths)

        self.run_call_mock.assert_called_with(argument, readline=True)

    def testFailedListControlFiles(self):
        """Should call the dev server's list-files method using http, get
        exception."""
        dev_server.ImageServerBase.run_call.side_effect = E500
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_control_files,
                          '')
        self.run_call_mock.assert_called_with(mock.ANY, readline=True)


    def testExplodingListControlFiles(self):
        """Should call the dev server's list-files method using http, get
        exception."""
        dev_server.ImageServerBase.run_call.side_effect = E403
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_control_files, '')
        self.run_call_mock.assert_called_with(mock.ANY, readline=True)

    def testCmdErrorListControlFiles(self):
        """Should call the dev server's list-files method using ssh, retry
        list_control_files when getting error.CmdError, raise exception for
        urllib2.HTTPError."""
        dev_server.ImageServerBase.run_call.side_effect = [CMD_ERROR, E500]
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_control_files,
                          '')
        self.run_call_mock.assert_called_with(mock.ANY, readline=True)

    def testListSuiteControls(self):
        """Should successfully list all contents of control files from the dev
        server."""
        control_contents = ['control file one', 'control file two']
        argument = comparators.Substrings([self._HOST, self.image_name])

        dev_server.ImageServerBase.run_call.return_value = (
                json.dumps(control_contents))

        file_contents = self.dev_server.list_suite_controls(self.image_name)
        self.assertEquals(len(file_contents), 2)
        for f in control_contents:
            self.assertTrue(f in file_contents)

        self.run_call_mock.assert_called_with(argument)

    def testFailedListSuiteControls(self):
        """Should call the dev server's list_suite_controls method using http,
        get exception."""
        dev_server.ImageServerBase.run_call.side_effect = E500

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_suite_controls,
                          '')
        self.run_call_mock.assert_called()


    def testExplodingListSuiteControls(self):
        """Should call the dev server's list_suite_controls method using http,
        get exception."""
        dev_server.ImageServerBase.run_call.side_effect = E403

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_suite_controls,
                          '')
        self.run_call_mock.assert_called()

    def testCmdErrorListSuiteControls(self):
        """Should call the dev server's list_suite_controls method using ssh,
        retry list_suite_controls when getting error.CmdError, raise exception
        for urllib2.HTTPError."""
        dev_server.ImageServerBase.run_call.side_effect = [CMD_ERROR, E500]

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.list_suite_controls,
                          '')
        self.run_call_mock.assert_has_calls([call(mock.ANY), call(mock.ANY)])

    def testGetControlFile(self):
        """Should successfully get a control file from the dev server."""
        file = 'file/one'
        contents = 'Multi-line\nControl File Contents\n'
        argument = comparators.Substrings([self._HOST, self.image_name, file])

        dev_server.ImageServerBase.run_call.return_value = contents

        self.assertEquals(
                self.dev_server.get_control_file(self.image_name, file),
                contents)

        self.run_call_mock.assert_called_with(argument)

    def testErrorGetControlFile(self):
        """Should try to get the contents of a control file using http, get
        exception."""
        dev_server.ImageServerBase.run_call.side_effect = E500
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.get_control_file,
                          '', '')
        self.run_call_mock.assert_called()

    def testForbiddenGetControlFile(self):
        """Should try to get the contents of a control file using http, get
        exception."""
        dev_server.ImageServerBase.run_call.side_effect = E403
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.get_control_file,
                          '', '')
        self.run_call_mock.assert_called()


    def testCmdErrorGetControlFile(self):
        """Should try to get the contents of a control file using ssh, retry
        get_control_file when getting error.CmdError, raise exception for
        urllib2.HTTPError."""
        dev_server.ImageServerBase.run_call.side_effect = [CMD_ERROR, E500]

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.get_control_file, '', '')
        self.run_call_mock.assert_has_calls([call(mock.ANY), call(mock.ANY)])


    def testGetLatestBuild(self):
        """Should successfully return a build for a given target."""
        with patch.object(dev_server.ImageServer, 'servers'), \
            patch.object(dev_server.ImageServer,
                         'devserver_healthy') as devserver_patch:

            dev_server.ImageServer.servers.return_value = [self._HOST]
            dev_server.ImageServer.devserver_healthy.return_value = True

            target = 'x86-generic-release'
            build_string = 'R18-1586.0.0-a1-b1514'
            argument = comparators.Substrings([self._HOST, target])

            dev_server.ImageServerBase.run_call.return_value = build_string

            build = dev_server.ImageServer.get_latest_build(target)
            self.assertEquals(build_string, build)

            devserver_patch.assert_called_with(self._HOST)
            self.run_call_mock.assert_called_with(argument)


    def testGetLatestBuildWithManyDevservers(self):
        """Should successfully return newest build with multiple devservers."""
        with patch.object(dev_server.ImageServer, 'servers'), \
            patch.object(dev_server.ImageServer,
                         'devserver_healthy') as devserver_patch:

            host0_expected = 'http://host0:8080'
            host1_expected = 'http://host1:8082'

            dev_server.ImageServer.servers.return_value = ([
                    host0_expected, host1_expected
            ])

            dev_server.ImageServer.devserver_healthy.return_value = True

            dev_server.ImageServer.devserver_healthy.return_value = True

            target = 'x86-generic-release'
            build_string1 = 'R9-1586.0.0-a1-b1514'
            build_string2 = 'R19-1586.0.0-a1-b3514'
            argument1 = comparators.Substrings([host0_expected, target])
            argument2 = comparators.Substrings([host1_expected, target])

            dev_server.ImageServerBase.run_call.side_effect = ([
                    build_string1, build_string2
            ])

            build = dev_server.ImageServer.get_latest_build(target)
            self.assertEquals(build_string2, build)
            devserver_patch.assert_has_calls(
                    [call(host0_expected),
                     call(host1_expected)])

            self.run_call_mock.assert_has_calls(
                    [call(argument1), call(argument2)])


    def testCrashesAreSetToTheCrashServer(self):
        """Should send symbolicate dump rpc calls to crash_server."""
        call = self.crash_server.build_call('symbolicate_dump')
        self.assertTrue(call.startswith(self._CRASH_HOST))


    def _stageTestHelper(self, artifacts=[], files=[], archive_url=None):
        """Helper to test combos of files/artifacts/urls with stage call."""
        expected_archive_url = archive_url
        if not archive_url:
            expected_archive_url = 'gs://my_default_url'
            image_patch = patch.object(dev_server, '_get_image_storage_server')
            self.image_server_mock = image_patch.start()
            self.addCleanup(image_patch.stop)
            dev_server._get_image_storage_server.return_value = (
                    'gs://my_default_url')
            name = 'fake/image'
        else:
            # This is embedded in the archive_url. Not needed.
            name = ''

        argument1 = comparators.Substrings([
                expected_archive_url, name,
                'artifacts=%s' % ','.join(artifacts),
                'files=%s' % ','.join(files), 'stage?'
        ])
        argument2 = comparators.Substrings([
                expected_archive_url, name,
                'artifacts=%s' % ','.join(artifacts),
                'files=%s' % ','.join(files), 'is_staged?'
        ])

        dev_server.ImageServerBase.run_call.side_effect = ['Success', 'True']

        self.dev_server.stage_artifacts(name, artifacts, files, archive_url)
        self.run_call_mock.assert_has_calls([call(argument1), call(argument2)])


    def testStageArtifactsBasic(self):
        """Basic functionality to stage artifacts (similar to
        trigger_download)."""
        self._stageTestHelper(artifacts=['full_payload', 'stateful'])


    def testStageArtifactsBasicWithFiles(self):
        """Basic functionality to stage artifacts (similar to
        trigger_download)."""
        self._stageTestHelper(artifacts=['full_payload', 'stateful'],
                              files=['taco_bell.coupon'])


    def testStageArtifactsOnlyFiles(self):
        """Test staging of only file artifacts."""
        self._stageTestHelper(files=['tasty_taco_bell.coupon'])


    def testStageWithArchiveURL(self):
        """Basic functionality to stage artifacts (similar to
        trigger_download)."""
        self._stageTestHelper(files=['tasty_taco_bell.coupon'],
                              archive_url='gs://tacos_galore/my/dir')


    def testStagedFileUrl(self):
        """Tests that the staged file url looks right."""
        devserver_label = 'x86-mario-release/R30-1234.0.0'
        url = self.dev_server.get_staged_file_url('stateful.tgz',
                                                  devserver_label)
        expected_url = '/'.join([self._HOST, 'static', devserver_label,
                                 'stateful.tgz'])
        self.assertEquals(url, expected_url)

        devserver_label = 'something_complex/that/you_MIGHT/hate'
        url = self.dev_server.get_staged_file_url('chromiumos_image.bin',
                                                  devserver_label)
        expected_url = '/'.join([self._HOST, 'static', devserver_label,
                                 'chromiumos_image.bin'])
        self.assertEquals(url, expected_url)


    def _StageTimeoutHelper(self):
        """Helper class for testing staging timeout."""
        call_patch = patch.object(dev_server.ImageServer, 'call_and_wait')
        self.call_mock = call_patch.start()
        self.addCleanup(call_patch.stop)
        dev_server.ImageServer.call_and_wait.side_effect = (
                bin_utils.TimeoutError())

    def _VerifyTimeoutHelper(self):
        self.call_mock.assert_called_with(call_name='stage',
                                          artifacts=mock.ANY,
                                          files=mock.ANY,
                                          archive_url=mock.ANY,
                                          error_message=mock.ANY)


    def test_StageArtifactsTimeout(self):
        """Test DevServerException is raised when stage_artifacts timed out."""
        self._StageTimeoutHelper()

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.stage_artifacts,
                          image='fake/image', artifacts=['full_payload'])
        self._VerifyTimeoutHelper()


    def test_TriggerDownloadTimeout(self):
        """Test DevServerException is raised when trigger_download timed out."""
        self._StageTimeoutHelper()
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.trigger_download,
                          image='fake/image')
        self._VerifyTimeoutHelper()

    def test_FinishDownloadTimeout(self):
        """Test DevServerException is raised when finish_download timed out."""
        self._StageTimeoutHelper()
        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.finish_download,
                          image='fake/image')
        self._VerifyTimeoutHelper()


    def test_compare_load(self):
        """Test load comparison logic.
        """
        load_high_cpu = {'devserver': 'http://devserver_1:8082',
                         dev_server.DevServer.CPU_LOAD: 100.0,
                         dev_server.DevServer.NETWORK_IO: 1024*1024*1.0,
                         dev_server.DevServer.DISK_IO: 1024*1024.0}
        load_high_network = {'devserver': 'http://devserver_1:8082',
                             dev_server.DevServer.CPU_LOAD: 1.0,
                             dev_server.DevServer.NETWORK_IO: 1024*1024*100.0,
                             dev_server.DevServer.DISK_IO: 1024*1024*1.0}
        load_1 = {'devserver': 'http://devserver_1:8082',
                  dev_server.DevServer.CPU_LOAD: 1.0,
                  dev_server.DevServer.NETWORK_IO: 1024*1024*1.0,
                  dev_server.DevServer.DISK_IO: 1024*1024*2.0}
        load_2 = {'devserver': 'http://devserver_1:8082',
                  dev_server.DevServer.CPU_LOAD: 1.0,
                  dev_server.DevServer.NETWORK_IO: 1024*1024*1.0,
                  dev_server.DevServer.DISK_IO: 1024*1024*1.0}
        self.assertFalse(dev_server._is_load_healthy(load_high_cpu))
        self.assertFalse(dev_server._is_load_healthy(load_high_network))
        self.assertTrue(dev_server._compare_load(load_1, load_2) > 0)


    def _testSuccessfulTriggerDownloadAndroid(self, synchronous=True):
        """Call the dev server's download method with given synchronous
        setting.

        @param synchronous: True to call the download method synchronously.
        """
        target = 'test_target'
        branch = 'test_branch'
        build_id = '123456'
        artifacts = android_utils.AndroidArtifacts.get_artifacts_for_reimage(
                None)
        with patch.object(dev_server.AndroidBuildServer, '_finish_download'):

            argument1 = comparators.Substrings(
                    [self._HOST, target, branch, build_id, 'stage?'])
            argument2 = comparators.Substrings(
                    [self._HOST, target, branch, build_id, 'is_staged?'])

            dev_server.ImageServerBase.run_call.side_effect = [
                    'Success', 'True'
            ]

            if synchronous:
                android_build_info = {
                        'target': target,
                        'build_id': build_id,
                        'branch': branch
                }
                build = (dev_server.ANDROID_BUILD_NAME_PATTERN %
                         android_build_info)
                self.android_dev_server._finish_download(build,
                                                         artifacts,
                                                         '',
                                                         target=target,
                                                         build_id=build_id,
                                                         branch=branch)

            # Synchronous case requires a call to finish download.
            self.android_dev_server.trigger_download(synchronous=synchronous,
                                                     target=target,
                                                     build_id=build_id,
                                                     branch=branch)
            self.run_call_mock.assert_has_calls(
                    [call(argument1), call(argument2)])


    def testSuccessfulTriggerDownloadAndroidSync(self):
        """Call the dev server's download method with synchronous=True."""
        self._testSuccessfulTriggerDownloadAndroid(synchronous=True)


    def testSuccessfulTriggerDownloadAndroidAsync(self):
        """Call the dev server's download method with synchronous=False."""
        self._testSuccessfulTriggerDownloadAndroid(synchronous=False)


    @unittest.expectedFailure
    def testGetUnrestrictedDevservers(self):
        """Test method get_unrestricted_devservers works as expected."""
        restricted_devserver = 'http://192.168.0.100:8080'
        unrestricted_devserver = 'http://172.1.1.3:8080'
        with patch.object(dev_server.ImageServer, 'servers') as servers_patch:
            dev_server.ImageServer.servers.return_value = ([
                    restricted_devserver, unrestricted_devserver
            ])
            # crbug.com/1027277: get_unrestricted_devservers() now returns all
            # servers.
            self.assertEqual(
                    dev_server.ImageServer.get_unrestricted_devservers([
                            ('192.168.0.0', 24)
                    ]), [unrestricted_devserver])

            servers_patch.assert_called_once()

    def testGetUnrestrictedDevserversReturnsAll(self):
        """Test method get_unrestricted_devservers works as expected."""
        restricted_devserver = 'http://192.168.0.100:8080'
        unrestricted_devserver = 'http://172.1.1.3:8080'
        with patch.object(dev_server.ImageServer, 'servers') as servers_patch:
            dev_server.ImageServer.servers.return_value = ([
                    restricted_devserver, unrestricted_devserver
            ])
            # crbug.com/1027277: get_unrestricted_devservers() now returns all
            # servers.
            self.assertEqual(
                    dev_server.ImageServer.get_unrestricted_devservers([
                            ('192.168.0.0', 24)
                    ]), [restricted_devserver, unrestricted_devserver])

            servers_patch.assert_called_once()

    def testDevserverHealthy(self):
        """Test which types of connections that method devserver_healthy uses
        for different types of DevServer.

        CrashServer always adopts DevServer.run_call.
        ImageServer and AndroidBuildServer use ImageServerBase.run_call.
        """
        argument = comparators.Substring(self._HOST)

        # for testing CrashServer

        with patch.object(dev_server.DevServer, 'run_call'):
            # for testing CrashServer
            dev_server.DevServer.run_call.return_value = '{"free_disk": 1024}'

            # for testing ImageServer
            dev_server.ImageServer.run_call.return_value = (
                    '{"free_disk": 1024}')

            # for testing AndroidBuildServer
            dev_server.AndroidBuildServer.run_call.return_value = (
                    '{"free_disk": 1024}')

            self.assertTrue(
                    dev_server.CrashServer.devserver_healthy(self._HOST))
            self.assertTrue(
                    dev_server.ImageServer.devserver_healthy(self._HOST))
            self.assertTrue(
                    dev_server.AndroidBuildServer.devserver_healthy(
                            self._HOST))

            dev_server.DevServer.run_call.assert_called_with(argument,
                                                             timeout=mock.ANY)
            dev_server.ImageServer.run_call.assert_called_with(
                    argument, timeout=mock.ANY)
            dev_server.AndroidBuildServer.run_call.assert_called_with(
                    argument, timeout=mock.ANY)


    def testLocateFile(self):
        """Test locating files for AndriodBuildServer."""
        file_name = 'fake_file'
        artifacts = ['full_payload', 'stateful']
        build = 'fake_build'

        argument = comparators.Substrings([file_name, build, 'locate_file'])
        dev_server.ImageServerBase.run_call.return_value = 'file_path'

        file_location = 'http://nothing/static/fake_build/file_path'
        self.assertEqual(self.android_dev_server.locate_file(
                file_name, artifacts, build, None), file_location)
        self.run_call_mock.assert_called_with(argument)

    def testCmdErrorLocateFile(self):
        """Test locating files for AndriodBuildServer for retry
        error.CmdError, and raise urllib2.URLError."""
        dev_server.ImageServerBase.run_call.side_effect = CMD_ERROR
        dev_server.ImageServerBase.run_call.side_effect = E500

        self.assertRaises(dev_server.DevServerException,
                          self.dev_server.trigger_download,
                          '')


    def testGetAvailableDevserversForCrashServer(self):
        """Test method get_available_devservers for CrashServer."""
        crash_servers = ['http://crash_servers1:8080']
        host = '127.0.0.1'
        with patch.object(dev_server.CrashServer, 'servers'):
            dev_server.CrashServer.servers.return_value = crash_servers
            self.assertEqual(
                    dev_server.CrashServer.get_available_devservers(host),
                    (crash_servers, False))


    def testGetAvailableDevserversForImageServer(self):
        """Test method get_available_devservers for ImageServer."""
        unrestricted_host = '100.0.0.99'
        unrestricted_servers = ['http://100.0.0.10:8080',
                                'http://128.0.0.10:8080']
        same_subnet_unrestricted_servers = ['http://100.0.0.10:8080']
        restricted_host = '127.0.0.99'
        restricted_servers = ['http://127.0.0.10:8080']
        all_servers = unrestricted_servers + restricted_servers
        # Set restricted subnets
        restricted_subnets = [('127.0.0.0', 24)]

        with patch.object(dev_server.ImageServerBase, 'servers'):
            dev_server.ImageServerBase.servers.return_value = (all_servers)

            # dut in unrestricted subnet shall be offered devserver in the same
            # subnet first, and allow retry.
            self.assertEqual(
                    dev_server.ImageServer.get_available_devservers(
                            unrestricted_host, True, restricted_subnets),
                    (same_subnet_unrestricted_servers, True))

            # crbug.com/1027277: If prefer_local_devserver is set to False,
            # allow any devserver, and retry is not allowed.
            self.assertEqual(
                    dev_server.ImageServer.get_available_devservers(
                            unrestricted_host, False, restricted_subnets),
                    (all_servers, False))

            # crbug.com/1027277: When no hostname is specified, all devservers
            # should be considered, and retry is not allowed.
            self.assertEqual(
                    dev_server.ImageServer.get_available_devservers(
                            None, True, restricted_subnets),
                    (all_servers, False))

            # dut in restricted subnet should only be offered devserver in the
            # same restricted subnet, and retry is not allowed.
            self.assertEqual(
                    dev_server.ImageServer.get_available_devservers(
                            restricted_host, True, restricted_subnets),
                    (restricted_servers, False))


if __name__ == "__main__":
    unittest.main()
