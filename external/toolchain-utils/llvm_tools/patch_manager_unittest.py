#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests when handling patches."""

import json
from pathlib import Path
import tempfile
from typing import Callable
import unittest
from unittest import mock

import atomic_write_file
import patch_manager
import patch_utils


class PatchManagerTest(unittest.TestCase):
    """Test class when handling patches of packages."""

    # Simulate behavior of 'os.path.isdir()' when the path is not a directory.
    @mock.patch.object(Path, "is_dir", return_value=False)
    def testInvalidDirectoryPassedAsCommandLineArgument(self, mock_isdir):
        src_dir = "/some/path/that/is/not/a/directory"
        patch_metadata_file = "/some/path/that/is/not/a/file"

        # Verify the exception is raised when the command line argument for
        # '--filesdir_path' or '--src_path' is not a directory.
        with self.assertRaises(ValueError):
            patch_manager.main(
                [
                    "--src_path",
                    src_dir,
                    "--patch_metadata_file",
                    patch_metadata_file,
                ]
            )
        mock_isdir.assert_called_once()

    # Simulate behavior of 'os.path.isfile()' when the patch metadata file is
    # does not exist.
    @mock.patch.object(Path, "is_file", return_value=False)
    def testInvalidPathToPatchMetadataFilePassedAsCommandLineArgument(
        self, mock_isfile
    ):
        src_dir = "/some/path/that/is/not/a/directory"
        patch_metadata_file = "/some/path/that/is/not/a/file"

        # Verify the exception is raised when the command line argument for
        # '--filesdir_path' or '--src_path' is not a directory.
        with mock.patch.object(Path, "is_dir", return_value=True):
            with self.assertRaises(ValueError):
                patch_manager.main(
                    [
                        "--src_path",
                        src_dir,
                        "--patch_metadata_file",
                        patch_metadata_file,
                    ]
                )
        mock_isfile.assert_called_once()

    @mock.patch("builtins.print")
    @mock.patch.object(patch_utils, "git_clean_context")
    def testCheckPatchApplies(self, _, mock_git_clean_context):
        """Tests whether we can apply a single patch for a given svn_version."""
        mock_git_clean_context.return_value = mock.MagicMock()
        with tempfile.TemporaryDirectory(
            prefix="patch_manager_unittest"
        ) as dirname:
            dirpath = Path(dirname)
            patch_entries = [
                patch_utils.PatchEntry(
                    dirpath,
                    metadata=None,
                    platforms=[],
                    rel_patch_path="another.patch",
                    version_range={
                        "from": 9,
                        "until": 20,
                    },
                ),
                patch_utils.PatchEntry(
                    dirpath,
                    metadata=None,
                    platforms=["chromiumos"],
                    rel_patch_path="example.patch",
                    version_range={
                        "from": 1,
                        "until": 10,
                    },
                ),
                patch_utils.PatchEntry(
                    dirpath,
                    metadata=None,
                    platforms=["chromiumos"],
                    rel_patch_path="patch_after.patch",
                    version_range={
                        "from": 1,
                        "until": 5,
                    },
                ),
            ]
            patches_path = dirpath / "PATCHES.json"
            with atomic_write_file.atomic_write(
                patches_path, encoding="utf-8"
            ) as f:
                json.dump([pe.to_dict() for pe in patch_entries], f)

            def _harness1(
                version: int,
                return_value: patch_utils.PatchResult,
                expected: patch_manager.GitBisectionCode,
            ):
                with mock.patch.object(
                    patch_utils.PatchEntry,
                    "apply",
                    return_value=return_value,
                ) as m:
                    result = patch_manager.CheckPatchApplies(
                        version,
                        dirpath,
                        patches_path,
                        "example.patch",
                        patch_utils.gnu_patch,
                    )
                    self.assertEqual(result, expected)
                    m.assert_called()

            _harness1(
                1,
                patch_utils.PatchResult(True, {}),
                patch_manager.GitBisectionCode.GOOD,
            )
            _harness1(
                2,
                patch_utils.PatchResult(True, {}),
                patch_manager.GitBisectionCode.GOOD,
            )
            _harness1(
                2,
                patch_utils.PatchResult(False, {}),
                patch_manager.GitBisectionCode.BAD,
            )
            _harness1(
                11,
                patch_utils.PatchResult(False, {}),
                patch_manager.GitBisectionCode.BAD,
            )

            def _harness2(
                version: int,
                application_func: Callable,
                expected: patch_manager.GitBisectionCode,
            ):
                with mock.patch.object(
                    patch_utils,
                    "apply_single_patch_entry",
                    application_func,
                ):
                    result = patch_manager.CheckPatchApplies(
                        version,
                        dirpath,
                        patches_path,
                        "example.patch",
                        patch_utils.gnu_patch,
                    )
                    self.assertEqual(result, expected)

            # Check patch can apply and fail with good return codes.
            def _apply_patch_entry_mock1(v, _, patch_entry, _func, **__):
                return patch_entry.can_patch_version(v), None

            _harness2(
                1,
                _apply_patch_entry_mock1,
                patch_manager.GitBisectionCode.GOOD,
            )
            _harness2(
                11,
                _apply_patch_entry_mock1,
                patch_manager.GitBisectionCode.BAD,
            )

            # Early exit check, shouldn't apply later failing patch.
            def _apply_patch_entry_mock2(v, _, patch_entry, _func, **__):
                if (
                    patch_entry.can_patch_version(v)
                    and patch_entry.rel_patch_path == "patch_after.patch"
                ):
                    return False, {"filename": mock.Mock()}
                return True, None

            _harness2(
                1,
                _apply_patch_entry_mock2,
                patch_manager.GitBisectionCode.GOOD,
            )

            # Skip check, should exit early on the first patch.
            def _apply_patch_entry_mock3(v, _, patch_entry, _func, **__):
                if (
                    patch_entry.can_patch_version(v)
                    and patch_entry.rel_patch_path == "another.patch"
                ):
                    return False, {"filename": mock.Mock()}
                return True, None

            _harness2(
                9,
                _apply_patch_entry_mock3,
                patch_manager.GitBisectionCode.SKIP,
            )


if __name__ == "__main__":
    unittest.main()
