#!/usr/bin/python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from unittest import mock

import common
from autotest_lib.site_utils.admin_audit import constants
from autotest_lib.site_utils.admin_audit import verifiers
from autotest_lib.server.cros.storage import storage_validate as storage

# pylint: disable=missing-docstring


class StubHost(object):
    """Simple host"""
    def is_up(self, **args):
        return True

    def run(self, **args):
        return ''


class StubStorageStateValidator(object):
    def get_type(self):
        return "some_type"

    def get_state(self, run_badblocks=None):
        return storage.STORAGE_STATE_WARNING


class VerifyDutStorageTests(unittest.TestCase):
    """Unit tests for ServoTypeLabel"""
    def test_state_is_none_till_run_verify(self):
        host = StubHost()
        verify = verifiers.VerifyDutStorage(host)
        self.assertEqual(verify.get_state(), None)

    @mock.patch('autotest_lib.server.cros.storage.'
                'storage_validate.StorageStateValidator')
    def test_get_state_after_runverify(self, validator):
        validator.return_value = StubStorageStateValidator()
        host = StubHost()
        verify = verifiers.VerifyDutStorage(host)
        verify.verify(set_label=False)
        self.assertEqual(verify.get_state(), constants.HW_STATE_ACCEPTABLE)


if __name__ == '__main__':
    unittest.main()
