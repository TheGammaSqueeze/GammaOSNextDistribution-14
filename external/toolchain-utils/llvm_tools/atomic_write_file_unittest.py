#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for atomic_write_file.py."""

from pathlib import Path
import tempfile
import unittest

import atomic_write_file


class TestAtomicWrite(unittest.TestCase):
    """Test atomic_write."""

    def test_atomic_write(self):
        """Test that atomic write safely writes."""
        prior_contents = "This is a test written by patch_utils_unittest.py\n"
        new_contents = "I am a test written by patch_utils_unittest.py\n"
        with tempfile.TemporaryDirectory(
            prefix="patch_utils_unittest"
        ) as dirname:
            dirpath = Path(dirname)
            filepath = dirpath / "test_atomic_write.txt"
            with filepath.open("w", encoding="utf-8") as f:
                f.write(prior_contents)

            def _t():
                with atomic_write_file.atomic_write(
                    filepath, encoding="utf-8"
                ) as f:
                    f.write(new_contents)
                    raise Exception("Expected failure")

            self.assertRaises(Exception, _t)
            with filepath.open(encoding="utf-8") as f:
                lines = f.readlines()
            self.assertEqual(lines[0], prior_contents)
            with atomic_write_file.atomic_write(
                filepath, encoding="utf-8"
            ) as f:
                f.write(new_contents)
            with filepath.open(encoding="utf-8") as f:
                lines = f.readlines()
            self.assertEqual(lines[0], new_contents)


if __name__ == "__main__":
    unittest.main()
