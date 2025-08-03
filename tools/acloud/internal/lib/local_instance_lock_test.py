#!/usr/bin/env python
#
# Copyright 2020 - The Android Open Source Project
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
"""Tests for LocalInstanceLock."""

import fcntl
import os
import shutil
import tempfile
import unittest

from unittest import mock

from acloud import errors
from acloud.internal.lib import local_instance_lock


class LocalInstanceLockTest(unittest.TestCase):
    """Test LocalInstanceLock methods."""

    def setUp(self):
        self._temp_dir = tempfile.mkdtemp()
        self._lock_path = os.path.join(self._temp_dir, "temp.lock")
        self._lock = local_instance_lock.LocalInstanceLock(
            self._lock_path)

    def tearDown(self):
        shutil.rmtree(self._temp_dir, ignore_errors=True)

    def testLock(self):
        """Test the method calls that don't raise errors."""
        self.assertTrue(self._lock.LockIfNotInUse())
        self.assertTrue(os.path.isfile(self._lock_path))
        self._lock.Unlock()

        self.assertTrue(self._lock.LockIfNotInUse(timeout_secs=0))
        self._lock.SetInUse(True)
        self._lock.Unlock()

        self.assertFalse(self._lock.LockIfNotInUse())

        self.assertTrue(self._lock.Lock())
        self._lock.SetInUse(False)
        self._lock.Unlock()

        self.assertTrue(self._lock.Lock(timeout_secs=0))
        self._lock.Unlock()

    def testOperationsWithoutLock(self):
        """Test raising errors when the file is not locked."""
        self.assertRaises(RuntimeError, self._lock.Unlock)
        self.assertRaises(RuntimeError, self._lock.SetInUse, True)
        self.assertRaises(RuntimeError, self._lock.SetInUse, False)

    def testNonBlockingLock(self):
        """Test failing to lock in non-blocking mode."""
        lock = local_instance_lock.LocalInstanceLock(self._lock_path)
        self.assertTrue(lock.Lock(timeout_secs=0))
        try:
            self.assertFalse(self._lock.Lock(timeout_secs=0))
            self.assertFalse(self._lock.LockIfNotInUse(timeout_secs=0))
        finally:
            lock.Unlock()

    @mock.patch("acloud.internal.lib.local_instance_lock."
                "utils.TimeoutException")
    def testLockWithTimeout(self, mock_timeout_exception):
        """Test failing to lock due to timeout."""
        mock_wrapped_flock = mock.Mock(side_effect=errors.FunctionTimeoutError)
        mock_wrapper = mock.Mock(return_value=mock_wrapped_flock)
        mock_timeout_exception.return_value = mock_wrapper

        self.assertFalse(self._lock.Lock(timeout_secs=1))

        mock_wrapper.assert_called_once_with(fcntl.flock)
        mock_wrapped_flock.assert_called_once_with(mock.ANY, fcntl.LOCK_EX)
        mock_wrapper.reset_mock()
        mock_wrapped_flock.reset_mock()

        self.assertFalse(self._lock.LockIfNotInUse(timeout_secs=1))

        mock_wrapper.assert_called_once_with(fcntl.flock)
        mock_wrapped_flock.assert_called_once_with(mock.ANY, fcntl.LOCK_EX)


if __name__ == "__main__":
    unittest.main()
