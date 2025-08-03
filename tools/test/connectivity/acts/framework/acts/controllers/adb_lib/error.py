#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts import error


class AdbError(error.ActsError):
    """Raised when there is an error in adb operations."""

    def __init__(self, cmd, stdout, stderr, ret_code):
        super().__init__()
        self.cmd = cmd
        self.stdout = stdout
        self.stderr = stderr
        self.ret_code = ret_code

    def __str__(self):
        return ("Error executing adb cmd '%s'. ret: %d, stdout: %s, stderr: %s"
                ) % (self.cmd, self.ret_code, self.stdout, self.stderr)


class AdbCommandError(AdbError):
    """Raised when there is an error in the command being run through ADB."""
