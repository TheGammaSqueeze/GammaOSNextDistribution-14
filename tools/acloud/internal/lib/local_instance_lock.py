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
"""LocalInstanceLock class."""

import errno
import fcntl
import logging
import os

from acloud import errors
from acloud.internal.lib import utils


logger = logging.getLogger(__name__)

_LOCK_FILE_SIZE = 1
# An empty file is equivalent to NOT_IN_USE.
_IN_USE_STATE = b"I"
_NOT_IN_USE_STATE = b"N"

_DEFAULT_TIMEOUT_SECS = 5


class LocalInstanceLock:
    """The class that controls a lock file for a local instance.

    Acloud acquires the lock file of a local instance before it creates,
    deletes, or queries it. The lock prevents multiple acloud processes from
    accessing an instance simultaneously.

    The lock file records whether the instance is in use. Acloud checks the
    state when it needs an unused id to create a new instance.

    Attributes:
        _file_path: The path to the lock file.
        _file_desc: The file descriptor of the file. It is set to None when
                    this object does not hold the lock.
    """

    def __init__(self, file_path):
        self._file_path = file_path
        self._file_desc = None

    def _Flock(self, timeout_secs):
        """Call fcntl.flock with timeout.

        Args:
            timeout_secs: An integer or a float, the timeout for acquiring the
                          lock file. 0 indicates non-block.

        Returns:
            True if the file is locked successfully. False if timeout.

        Raises:
            OSError: if any file operation fails.
        """
        try:
            if timeout_secs > 0:
                wrapper = utils.TimeoutException(timeout_secs)
                wrapper(fcntl.flock)(self._file_desc, fcntl.LOCK_EX)
            else:
                fcntl.flock(self._file_desc, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except errors.FunctionTimeoutError as e:
            logger.debug("Cannot lock %s within %s seconds",
                         self._file_path, timeout_secs)
            return False
        except (OSError, IOError) as e:
            # flock raises IOError in python2; OSError in python3.
            if e.errno in (errno.EACCES, errno.EAGAIN):
                logger.debug("Cannot lock %s", self._file_path)
                return False
            raise
        return True

    def Lock(self, timeout_secs=_DEFAULT_TIMEOUT_SECS):
        """Acquire the lock file.

        Args:
            timeout_secs: An integer or a float, the timeout for acquiring the
                          lock file. 0 indicates non-block.

        Returns:
            True if the file is locked successfully. False if timeout.

        Raises:
            OSError: if any file operation fails.
        """
        if self._file_desc is not None:
            raise OSError("%s has been locked." % self._file_path)
        parent_dir = os.path.dirname(self._file_path)
        if not os.path.exists(parent_dir):
            os.makedirs(parent_dir)
        successful = False
        self._file_desc = os.open(self._file_path, os.O_CREAT | os.O_RDWR,
                                  0o666)
        os.chmod(self._file_path, 0o666)
        os.chmod(parent_dir, 0o755)
        try:
            successful = self._Flock(timeout_secs)
        finally:
            if not successful:
                os.close(self._file_desc)
                self._file_desc = None
        return successful

    def _CheckFileDescriptor(self):
        """Raise an error if the file is not opened or locked."""
        if self._file_desc is None:
            raise RuntimeError("%s has not been locked." % self._file_path)

    def SetInUse(self, in_use):
        """Write the instance state to the file.

        Args:
            in_use: A boolean, whether to set the instance to be in use.

        Raises:
            OSError: if any file operation fails.
        """
        self._CheckFileDescriptor()
        os.lseek(self._file_desc, 0, os.SEEK_SET)
        state = _IN_USE_STATE if in_use else _NOT_IN_USE_STATE
        if os.write(self._file_desc, state) != _LOCK_FILE_SIZE:
            raise OSError("Cannot write " + self._file_path)

    def Unlock(self):
        """Unlock the file.

        Raises:
            OSError: if any file operation fails.
        """
        self._CheckFileDescriptor()
        fcntl.flock(self._file_desc, fcntl.LOCK_UN)
        os.close(self._file_desc)
        self._file_desc = None

    def LockIfNotInUse(self, timeout_secs=_DEFAULT_TIMEOUT_SECS):
        """Lock the file if the instance is not in use.

        Returns:
            True if the file is locked successfully.
            False if timeout or the instance is in use.

        Raises:
            OSError: if any file operation fails.
        """
        if not self.Lock(timeout_secs):
            return False
        in_use = True
        try:
            in_use = os.read(self._file_desc, _LOCK_FILE_SIZE) == _IN_USE_STATE
        finally:
            if in_use:
                self.Unlock()
        return not in_use
