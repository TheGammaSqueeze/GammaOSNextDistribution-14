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

"""Unittests for the terminal module."""

import contextlib
import io
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
import rh.terminal


class ColorTests(unittest.TestCase):
    """Verify behavior of Color class."""

    def setUp(self):
        os.environ.pop('NOCOLOR', None)

    def test_enabled_auto_tty(self):
        """Test automatic enable behavior based on tty."""
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            c = rh.terminal.Color()
            self.assertFalse(c.enabled)

            stderr.isatty = lambda: True
            c = rh.terminal.Color()
            self.assertTrue(c.enabled)

    def test_enabled_auto_env(self):
        """Test automatic enable behavior based on $NOCOLOR."""
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            os.environ['NOCOLOR'] = 'yes'
            c = rh.terminal.Color()
            self.assertFalse(c.enabled)

            os.environ['NOCOLOR'] = 'no'
            c = rh.terminal.Color()
            self.assertTrue(c.enabled)

    def test_enabled_override(self):
        """Test explicit enable behavior."""
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            stderr.isatty = lambda: True
            os.environ['NOCOLOR'] = 'no'
            c = rh.terminal.Color()
            self.assertTrue(c.enabled)
            c = rh.terminal.Color(False)
            self.assertFalse(c.enabled)

            stderr.isatty = lambda: False
            os.environ['NOCOLOR'] = 'yes'
            c = rh.terminal.Color()
            self.assertFalse(c.enabled)
            c = rh.terminal.Color(True)
            self.assertTrue(c.enabled)

    def test_output_disabled(self):
        """Test output when coloring is disabled."""
        c = rh.terminal.Color(False)
        self.assertEqual(c.start(rh.terminal.Color.BLACK), '')
        self.assertEqual(c.color(rh.terminal.Color.BLACK, 'foo'), 'foo')
        self.assertEqual(c.stop(), '')

    def test_output_enabled(self):
        """Test output when coloring is enabled."""
        c = rh.terminal.Color(True)
        self.assertEqual(c.start(rh.terminal.Color.BLACK), '\x1b[1;30m')
        self.assertEqual(c.color(rh.terminal.Color.BLACK, 'foo'),
                         '\x1b[1;30mfoo\x1b[m')
        self.assertEqual(c.stop(), '\x1b[m')


class PrintStatusLine(unittest.TestCase):
    """Verify behavior of print_status_line."""

    def test_terminal(self):
        """Check tty behavior."""
        stderr = io.StringIO()
        stderr.isatty = lambda: True
        with contextlib.redirect_stderr(stderr):
            rh.terminal.print_status_line('foo')
            rh.terminal.print_status_line('bar', print_newline=True)
        csi = rh.terminal.CSI_ERASE_LINE_AFTER
        self.assertEqual(stderr.getvalue(), f'\rfoo{csi}\rbar{csi}\n')

    def test_no_terminal(self):
        """Check tty-less behavior."""
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            rh.terminal.print_status_line('foo')
            rh.terminal.print_status_line('bar', print_newline=True)
        self.assertEqual(stderr.getvalue(), 'foo\nbar\n')


@contextlib.contextmanager
def redirect_stdin(new_target):
    """Temporarily switch sys.stdin to |new_target|."""
    old = sys.stdin
    try:
        sys.stdin = new_target
        yield
    finally:
        sys.stdin = old


class StringPromptTests(unittest.TestCase):
    """Verify behavior of str_prompt."""

    def setUp(self):
        self.stdin = io.StringIO()

    def set_stdin(self, value: str) -> None:
        """Set stdin wrapper to a string."""
        self.stdin.seek(0)
        self.stdin.write(value)
        self.stdin.truncate()
        self.stdin.seek(0)

    def test_defaults(self):
        """Test default behavior."""
        stdout = io.StringIO()
        with redirect_stdin(self.stdin), contextlib.redirect_stdout(stdout):
            # Test EOF behavior.
            self.assertIsNone(rh.terminal.str_prompt('foo', ('a', 'b')))

            # Test enter behavior.
            self.set_stdin('\n')
            self.assertEqual(rh.terminal.str_prompt('foo', ('a', 'b')), '')

            # Lowercase inputs.
            self.set_stdin('Ok')
            self.assertEqual(rh.terminal.str_prompt('foo', ('a', 'b')), 'ok')

            # Don't lowercase inputs.
            self.set_stdin('Ok')
            self.assertEqual(
                rh.terminal.str_prompt('foo', ('a', 'b'), lower=False), 'Ok')


class BooleanPromptTests(unittest.TestCase):
    """Verify behavior of boolean_prompt."""

    def setUp(self):
        self.stdin = io.StringIO()

    def set_stdin(self, value: str) -> None:
        """Set stdin wrapper to a string."""
        self.stdin.seek(0)
        self.stdin.write(value)
        self.stdin.truncate()
        self.stdin.seek(0)

    def test_defaults(self):
        """Test default behavior."""
        stdout = io.StringIO()
        with redirect_stdin(self.stdin), contextlib.redirect_stdout(stdout):
            # Default values.  Will loop to EOF when it doesn't match anything.
            for v in ('', '\n', 'oops'):
                self.set_stdin(v)
                self.assertTrue(rh.terminal.boolean_prompt())

            # False values.
            for v in ('n', 'N', 'no', 'NO'):
                self.set_stdin(v)
                self.assertFalse(rh.terminal.boolean_prompt())

            # True values.
            for v in ('y', 'Y', 'ye', 'yes', 'YES'):
                self.set_stdin(v)
                self.assertTrue(rh.terminal.boolean_prompt())


if __name__ == '__main__':
    unittest.main()
