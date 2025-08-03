#
# Copyright (C) 2023 The Android Open Source Project
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
#
"""Unit tests for fileutils."""

import contextlib
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import fileutils


class ResolveCommandLinePathsTest(unittest.TestCase):
    """Unit tests for resolve_command_line_paths."""

    def test_empty_paths(self) -> None:
        """Tests that an empty argument returns an empty list."""
        self.assertListEqual([], fileutils.resolve_command_line_paths([]))

    def test_absolute_paths(self) -> None:
        """Tests that absolute paths are resolved correctly."""
        with TemporaryDirectory() as temp_dir_str:
            temp_dir = Path(temp_dir_str)
            a = temp_dir / "a"
            b = temp_dir / "external" / "b"
            a.mkdir()
            b.mkdir(parents=True)
            self.assertListEqual(
                [a, b],
                fileutils.resolve_command_line_paths(
                    [str(a), str(b), "/does/not/exist"]
                ),
            )

    def test_relative_paths(self) -> None:
        """Tests that relative paths are resolved correctly."""
        with TemporaryDirectory() as temp_dir_str:
            # Make this absolute so the CWD change later doesn't break it.
            temp_dir = Path(temp_dir_str).resolve()
            external = temp_dir / "external"
            external.mkdir()
            a = external / "a"
            a.mkdir()

            working_dir = temp_dir / "cwd"
            working_dir.mkdir()
            b = working_dir / "b"
            b.mkdir()
            with contextlib.chdir(working_dir):
                self.assertListEqual(
                    [a, working_dir, b],
                    fileutils.resolve_command_line_paths(
                        [
                            # These will all be resolved as absolute paths and returned.
                            "../external/a",
                            ".",
                            "b",
                            # This one doesn't exist. It will be pruned from the result.
                            "c",
                        ]
                    ),
                )


class FindTreeContainingTest(unittest.TestCase):
    """Unit tests for find_tree_containing."""

    def setUp(self) -> None:
        self._temp_dir = TemporaryDirectory()
        self.temp_dir = Path(self._temp_dir.name)
        self.repo_tree = self.temp_dir / "tree"
        (self.repo_tree / ".repo").mkdir(parents=True)

    def tearDown(self) -> None:
        self._temp_dir.cleanup()

    def test_cwd_is_in_tree(self) -> None:
        """Tests that the root is found when the CWD is in the same tree."""
        (self.repo_tree / "external/a").mkdir(parents=True)
        (self.repo_tree / "external/b").mkdir(parents=True)

        with contextlib.chdir(self.repo_tree / "external/a"):
            self.assertEqual(
                fileutils.find_tree_containing(self.repo_tree / "external/b"),
                self.repo_tree,
            )

    def test_cwd_is_in_other_tree(self) -> None:
        """Tests that the root is found when the CWD is in another tree."""
        tree_a = self.temp_dir / "a"
        (tree_a / ".repo").mkdir(parents=True)
        (tree_a / "external/a").mkdir(parents=True)

        tree_b = self.temp_dir / "b"
        (tree_b / ".repo").mkdir(parents=True)
        (tree_b / "external/b").mkdir(parents=True)

        with contextlib.chdir(tree_a / "external/a"):
            self.assertEqual(
                fileutils.find_tree_containing(tree_b / "external/b"), tree_b
            )

    def test_no_root(self) -> None:
        """Tests that an error is raised when no tree is found."""
        with self.assertRaises(FileNotFoundError):
            fileutils.find_tree_containing(self.temp_dir)


if __name__ == "__main__":
    unittest.main(verbosity=2)
