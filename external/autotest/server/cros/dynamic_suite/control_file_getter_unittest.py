#!/usr/bin/python3
#
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for client/common_lib/cros/control_file_getter.py."""

import unittest
from unittest.mock import patch

import common

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server.cros.dynamic_suite import control_file_getter


class DevServerGetterTest(unittest.TestCase):
    """Unit tests for control_file_getter.DevServerGetter.

    @var _HOST: fake dev server host address.
    """

    _BUILD = 'fake/build'
    _FILES = ['a/b/control', 'b/c/control']
    _CONTENTS = 'Multi-line\nControl File Contents\n'
    _403 = dev_server.DevServerException('HTTP 403 Forbidden!')

    def setUp(self):
        super(DevServerGetterTest, self).setUp()
        patcher = patch.object(dev_server, 'ImageServer')
        self.dev_server = patcher.start()
        self.addCleanup(patcher.stop)

        self.getter = control_file_getter.DevServerGetter(self._BUILD,
                                                          self.dev_server)

    def tearDown(self):
        if self.dev_server.resolve.call_count > 0:
            self.dev_server.resolve.assert_called_with(self._BUILD,
                                                       None,
                                                       ban_list=None)

    def testListControlFiles(self):
        """Should successfully list control files from the dev server."""
        self.dev_server.list_control_files.return_value = self._FILES
        self.assertEquals(self.getter.get_control_file_list(), self._FILES)
        self.assertEquals(self.getter._files, self._FILES)
        self.dev_server.list_control_files.assert_called_with(self._BUILD,
                                                              suite_name='')

    def testListControlFilesFail(self):
        """Should fail to list control files from the dev server."""
        self.dev_server.list_control_files.return_value = None

        self.dev_server.list_control_files.side_effect = self._403
        self.assertRaises(error.NoControlFileList,
                          self.getter.get_control_file_list)
        self.dev_server.list_control_files.assert_called_with(self._BUILD,
                                                              suite_name='')

    def testGetControlFile(self):
        """Should successfully get a control file from the dev server."""
        path = self._FILES[0]
        self.dev_server.get_control_file.return_value = self._CONTENTS
        self.assertEquals(self.getter.get_control_file_contents(path),
                          self._CONTENTS)
        self.dev_server.get_control_file.assert_called_with(self._BUILD, path)

    def testGetSuiteInfo(self):
        """
        Should successfully list control files' path and contents from the
        dev server.
        """
        file_contents = {f:self._CONTENTS for f in self._FILES}
        self.dev_server.list_suite_controls.return_value = file_contents

        suite_info = self.getter.get_suite_info()
        for k in suite_info.keys():
            self.assertEquals(suite_info[k], file_contents[k])
        self.assertEquals(sorted(self.getter._files), sorted(self._FILES))
        self.dev_server.list_suite_controls.assert_called_with(self._BUILD,
                                                               suite_name='')

    def testListSuiteControlisFail(self):
        """
        Should fail to list all control file's contents from the dev server.
        """
        self.dev_server.list_suite_controls.side_effect = self._403
        self.assertRaises(error.SuiteControlFileException,
                          self.getter.get_suite_info,
                          '')
        self.dev_server.list_suite_controls.assert_called_with(self._BUILD,
                                                               suite_name='')

    def testGetControlFileFail(self):
        """Should fail to get a control file from the dev server."""
        path = self._FILES[0]
        self.dev_server.get_control_file.side_effect = self._403

        self.assertRaises(error.ControlFileNotFound,
                          self.getter.get_control_file_contents,
                          path)
        self.dev_server.get_control_file.assert_called_with(self._BUILD, path)

    def testGetControlFileByNameCached(self):
        """\
        Should successfully get a cf by name from the dev server, using a cache.
        """
        name = 'one'
        path = "file/%s/control" % name

        self.getter._files = self._FILES + [path]
        self.dev_server.get_control_file.return_value = self._CONTENTS
        self.assertEquals(self.getter.get_control_file_contents_by_name(name),
                          self._CONTENTS)
        self.dev_server.get_control_file.assert_called_with(self._BUILD, path)

    def testGetControlFileByName(self):
        """\
        Should successfully get a control file from the dev server by name.
        """
        name = 'one'
        path = "file/%s/control" % name

        files = self._FILES + [path]
        self.dev_server.list_control_files.return_value = files
        self.dev_server.get_control_file.return_value = self._CONTENTS
        self.assertEquals(self.getter.get_control_file_contents_by_name(name),
                          self._CONTENTS)
        self.dev_server.list_control_files.assert_called_with(self._BUILD,
                                                              suite_name='')
        self.dev_server.get_control_file.assert_called_with(self._BUILD, path)

    def testGetSuiteControlFileByName(self):
        """\
        Should successfully get a suite control file from the devserver by name.
        """
        name = 'control.bvt'
        path = "file/" + name

        files = self._FILES + [path]
        self.dev_server.list_control_files.return_value = files
        self.dev_server.get_control_file.return_value = self._CONTENTS
        self.assertEquals(self.getter.get_control_file_contents_by_name(name),
                          self._CONTENTS)
        self.dev_server.list_control_files.assert_called_with(self._BUILD,
                                                              suite_name='')
        self.dev_server.get_control_file.assert_called_with(self._BUILD, path)

    def testGetControlFileByNameFail(self):
        """Should fail to get a control file from the dev server by name."""
        name = 'one'

        self.dev_server.list_control_files.return_value = self._FILES
        self.assertRaises(error.ControlFileNotFound,
                          self.getter.get_control_file_contents_by_name,
                          name)


if __name__ == '__main__':
    unittest.main()
