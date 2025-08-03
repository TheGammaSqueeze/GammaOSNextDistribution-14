#!/usr/bin/env python3
# Copyright 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unittests for the results module."""

import os
import sys
import unittest

_path = os.path.realpath(__file__ + '/../..')
if sys.path[0] != _path:
    sys.path.insert(0, _path)
del _path

# We have to import our local modules after the sys.path tweak.  We can't use
# relative imports because this is an executable program, not a module.
# pylint: disable=wrong-import-position
import rh
import rh.results
import rh.utils


COMPLETED_PROCESS_PASS = rh.utils.CompletedProcess(returncode=0)
COMPLETED_PROCESS_FAIL = rh.utils.CompletedProcess(returncode=1)
COMPLETED_PROCESS_WARN = rh.utils.CompletedProcess(returncode=77)


class HookResultTests(unittest.TestCase):
    """Verify behavior of HookResult object."""

    def test_error_warning(self):
        """Check error & warning handling."""
        # No errors.
        result = rh.results.HookResult('hook', 'project', 'HEAD', False)
        self.assertFalse(result)
        self.assertFalse(result.is_warning())

        # An error.
        result = rh.results.HookResult('hook', 'project', 'HEAD', True)
        self.assertTrue(result)
        self.assertFalse(result.is_warning())


class HookCommandResultTests(unittest.TestCase):
    """Verify behavior of HookCommandResult object."""

    def test_error_warning(self):
        """Check error & warning handling."""
        # No errors.
        result = rh.results.HookCommandResult(
            'hook', 'project', 'HEAD', COMPLETED_PROCESS_PASS)
        self.assertFalse(result)
        self.assertFalse(result.is_warning())

        # An error.
        result = rh.results.HookCommandResult(
            'hook', 'project', 'HEAD', COMPLETED_PROCESS_FAIL)
        self.assertTrue(result)
        self.assertFalse(result.is_warning())

        # A warning.
        result = rh.results.HookCommandResult(
            'hook', 'project', 'HEAD', COMPLETED_PROCESS_WARN)
        self.assertFalse(result)
        self.assertTrue(result.is_warning())


class ProjectResultsTests(unittest.TestCase):
    """Verify behavior of ProjectResults object."""

    def test_error_warning(self):
        """Check error & warning handling."""
        # No errors.
        result = rh.results.ProjectResults('project', 'workdir')
        self.assertFalse(result)

        # Warnings are not errors.
        result.add_results([
            rh.results.HookResult('hook', 'project', 'HEAD', False),
            rh.results.HookCommandResult(
                'hook', 'project', 'HEAD', COMPLETED_PROCESS_WARN),
        ])
        self.assertFalse(result)

        # Errors are errors.
        result.add_results([
            rh.results.HookResult('hook', 'project', 'HEAD', True),
        ])
        self.assertTrue(result)


if __name__ == '__main__':
    unittest.main()
