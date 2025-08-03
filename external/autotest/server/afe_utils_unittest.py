#!/usr/bin/python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import common
from autotest_lib.server import afe_utils


class FakeHostInfo(object):
    def __init__(self, board, cros_stable_version, servo_cros_stable_version):
        self._board = board
        self._cros_stable_version = cros_stable_version
        self._servo_cros_stable_version = servo_cros_stable_version

    @property
    def board(self):
        return self._board

    @property
    def cros_stable_version(self):
        return self._cros_stable_version

    @property
    def servo_cros_stable_version(self):
        return self._servo_cros_stable_version


class AfeUtilsTestCase(unittest.TestCase):
    def test_get_stable_cros_image_name_v2(self):
        board = "xxx-board"
        host_info = FakeHostInfo(
            board=board,
            servo_cros_stable_version="some garbage",
            cros_stable_version="R1-2.3.4"
        )
        expected = "xxx-board-release/R1-2.3.4"
        out = afe_utils.get_stable_cros_image_name_v2(host_info=host_info)
        self.assertEqual(out, expected)


if __name__ == '__main__':
    unittest.main()
