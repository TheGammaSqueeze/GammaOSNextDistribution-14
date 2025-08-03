#!/usr/bin/env python3
# Copyright 2022 The Android Open Source Project
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

"""Unittests for clang-format."""

import contextlib
from pathlib import Path
import sys
import tempfile
import unittest


DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(DIR.parent))

# We have to import our local modules after the sys.path tweak.  We can't use
# relative imports because this is an executable program, not a module.
# pylint: disable=wrong-import-position,import-error
import rh.utils


CLANG_FORMAT = DIR / 'clang-format.py'


@contextlib.contextmanager
def git_clang_format(data: str):
    """Create a fake git-clang-format script."""
    with tempfile.TemporaryDirectory(prefix='repohooks-tests') as tempdir:
        tempdir = Path(tempdir)
        script = tempdir / 'git-clang-format-fake.sh'
        script.write_text(f'#!/bin/sh\n{data}', encoding='utf-8')
        script.chmod(0o755)
        yield script


def run_clang_format(script, args, **kwargs):
    """Helper to run clang-format.py with fake git-clang-format script."""
    kwargs.setdefault('capture_output', True)
    return rh.utils.run(
        [CLANG_FORMAT, '--git-clang-format', script] + args, **kwargs)


class GitClangFormatExit(unittest.TestCase):
    """Test git-clang-format parsing."""

    def test_diff_exit_0_no_output(self):
        """Test exit 0 w/no output."""
        with git_clang_format('exit 0') as script:
            result = run_clang_format(script, ['--working-tree'])
            self.assertEqual(result.stdout, '')

    def test_diff_exit_0_stderr(self):
        """Test exit 0 w/stderr output."""
        with git_clang_format('echo bad >&2; exit 0') as script:
            with self.assertRaises(rh.utils.CalledProcessError) as e:
                run_clang_format(script, ['--working-tree'])
            self.assertIn('clang-format failed', e.exception.stderr)

    def test_diff_exit_1_no_output(self):
        """Test exit 1 w/no output."""
        with git_clang_format('exit 1') as script:
            result = run_clang_format(script, ['--working-tree'])
            self.assertEqual(result.stdout, '')

    def test_diff_exit_1_output(self):
        """Test exit 1 with output."""
        with git_clang_format('echo bad; exit 1') as script:
            with self.assertRaises(rh.utils.CalledProcessError) as e:
                run_clang_format(script, ['--working-tree'])
            self.assertIn('clang-format failed', e.exception.stderr)

    def test_diff_exit_1_stderr(self):
        """Test exit 1 w/stderr."""
        with git_clang_format('echo bad >&2; exit 1') as script:
            with self.assertRaises(rh.utils.CalledProcessError) as e:
                run_clang_format(script, ['--working-tree'])
            self.assertIn('clang-format failed', e.exception.stderr)

    def test_diff_exit_2(self):
        """Test exit 2."""
        with git_clang_format('exit 2') as script:
            with self.assertRaises(rh.utils.CalledProcessError) as e:
                run_clang_format(script, ['--working-tree'])
            self.assertIn('clang-format failed', e.exception.stderr)

    def test_fix_exit_1_output(self):
        """Test fix with incorrect patch syntax."""
        with git_clang_format('echo bad patch; exit 1') as script:
            with self.assertRaises(rh.utils.CalledProcessError) as e:
                run_clang_format(script, ['--working-tree', '--fix'])
            self.assertIn('Error: Unable to automatically fix things',
                          e.exception.stderr)


if __name__ == '__main__':
    unittest.main()
