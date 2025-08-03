#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for generate_pgo_profile."""

from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock

import generate_pgo_profile
import pgo_tools


class Test(unittest.TestCase):
    """Tests for generate_pgo_profile."""

    @mock.patch.object(pgo_tools, "run")
    def test_find_missing_cross_libs_works_for_empty_results(self, mock_run):
        mock_run.return_value.returncode = 3
        mock_run.return_value.stdout = ""
        self.assertEqual(
            generate_pgo_profile.find_missing_cross_libs(),
            generate_pgo_profile.ALL_NEEDED_CROSS_LIBS,
        )

        mock_run.return_value.returncode = 0
        self.assertEqual(
            generate_pgo_profile.find_missing_cross_libs(),
            generate_pgo_profile.ALL_NEEDED_CROSS_LIBS,
        )

    @mock.patch.object(pgo_tools, "run")
    def test_find_missing_cross_libs_filters_results_properly(self, mock_run):
        mock_run.return_value.returncode = 0
        mock_run.return_value.stdout = "\n".join(
            generate_pgo_profile.ALL_NEEDED_CROSS_LIBS
        )
        self.assertEqual(generate_pgo_profile.find_missing_cross_libs(), set())

        some_cross_libs = sorted(generate_pgo_profile.ALL_NEEDED_CROSS_LIBS)
        del some_cross_libs[len(some_cross_libs) // 3 :]
        mock_run.return_value.stdout = "\n".join(
            some_cross_libs + ["cross-foo/bar"]
        )

        expected_result = generate_pgo_profile.ALL_NEEDED_CROSS_LIBS - set(
            some_cross_libs
        )
        self.assertEqual(
            generate_pgo_profile.find_missing_cross_libs(), expected_result
        )

    def make_tempdir(self) -> Path:
        tempdir = Path(tempfile.mkdtemp(prefix="generate_pgo_profile_test_"))
        self.addCleanup(lambda: shutil.rmtree(tempdir))
        return tempdir

    def test_read_exactly_one_dirent_works(self):
        tempdir = self.make_tempdir()
        ent = tempdir / "one-ent"
        ent.touch()

        self.assertEqual(
            generate_pgo_profile.read_exactly_one_dirent(tempdir), ent
        )

    def test_read_exactly_one_dirent_fails_when_no_ents(self):
        tempdir = self.make_tempdir()
        with self.assertRaisesRegex(ValueError, "^Expected exactly one"):
            generate_pgo_profile.read_exactly_one_dirent(tempdir)

    def test_read_exactly_one_dirent_fails_when_multiple_ents(self):
        tempdir = self.make_tempdir()
        (tempdir / "a").touch()
        (tempdir / "b").touch()
        with self.assertRaisesRegex(ValueError, "^Expected exactly one"):
            generate_pgo_profile.read_exactly_one_dirent(tempdir)

    @mock.patch.object(pgo_tools, "run")
    def test_profraw_conversion_works(self, mock_run):
        tempdir = self.make_tempdir()
        profiles = [
            tempdir / "profile-foo.profraw",
            tempdir / "profile-bar.profraw",
        ]
        not_a_profile = tempdir / "not-a-profile.profraw"
        for f in profiles + [not_a_profile]:
            f.touch()

        result = generate_pgo_profile.convert_profraw_to_pgo_profile(tempdir)
        self.assertNotEqual(result.stem, ".profraw")
        try:
            # is_relative_to was added in Py3.9; until the chroot has that,
            # this code needs to use `relative_to` & check for exceptions.
            result.relative_to(tempdir)
        except ValueError:
            self.fail(f"{result} should be relative to {tempdir}")

        mock_run.assert_called_once()
        run_cmd = mock_run.call_args[0][0]
        for p in profiles:
            self.assertIn(p, run_cmd)
        self.assertNotIn(not_a_profile, run_cmd)
        self.assertIn(f"--output={result}", run_cmd)


if __name__ == "__main__":
    unittest.main()
