#!/usr/bin/env python3

# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for mass_seccomp_editor.py"""

import unittest
from unittest import mock

import mass_seccomp_editor

BASE_SECCOMP_CONTENTS = """
fstat: 1
poll: 1
foobar: 1
"""

TEST_FP = "foo"


class TestMassSeccompEditor(unittest.TestCase):
    """Test the mass_seccomp_editor."""

    def test_check_missing_sycalls(self):
        """Test we can find missing syscalls."""
        with mock.patch(
            "builtins.open", mock.mock_open(read_data=BASE_SECCOMP_CONTENTS)
        ):
            out = mass_seccomp_editor.check_missing_syscalls(
                ["fstat", "dup", "fizzbuzz"], TEST_FP
            )
        self.assertEqual(out, set(["dup", "fizzbuzz"]))


if __name__ == "__main__":
    unittest.main()
