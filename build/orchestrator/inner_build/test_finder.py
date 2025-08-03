#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
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

import unittest

import os
from pathlib import Path
from tempfile import TemporaryDirectory

from finder import FileFinder

FILENAME = "myfile.json"


def _create_file(root: str, rel_dir_path: str, filename: str):
    """Helper function to create an empty file in a test directory"""
    if ".." in rel_dir_path:
        raise Exception(f".. is not allowed in {rel_dir_path}")
    # Create the directory if it is does not exist
    dirpath = os.path.join(root, rel_dir_path)
    os.makedirs(dirpath, exist_ok=True)
    # Create an empty file in the newly created directory
    filepath = os.path.join(dirpath, filename)
    Path(filepath).touch()


class TestFileFinder(unittest.TestCase):
    def test_single_file_match(self):
        finder = FileFinder(FILENAME)
        # root dir
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "", FILENAME)
            _create_file(tmp, "", "some_other_file.json")
            results = list(finder.find(tmp, search_depth=100))
            self.assertEqual(1, len(results))
            self.assertEqual(f"{tmp}/{FILENAME}", results[0])
        # nested dir
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "a/b/c", FILENAME)
            results = list(finder.find(tmp, search_depth=100))
            self.assertEqual(1, len(results))
            self.assertEqual(f"{tmp}/a/b/c/{FILENAME}", results[0])

    def test_multiple_file_matches(self):
        finder = FileFinder(FILENAME)
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "a", FILENAME)
            _create_file(tmp, "a/b", FILENAME)
            all_results = sorted(list(finder.find(tmp, search_depth=100)),
                                 reverse=True)
            self.assertEqual(2, len(all_results))
            self.assertEqual(f"{tmp}/a/{FILENAME}", all_results[0])
            self.assertEqual(f"{tmp}/a/b/{FILENAME}", all_results[1])

    def test_find_does_not_escape_directory(self):
        finder = FileFinder(FILENAME)
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "", FILENAME)
            _create_file(tmp, "a", FILENAME)
            _create_file(tmp, "b", FILENAME)
            search_dir_a_results = list(
                finder.find(f"{tmp}/a", search_depth=100))
            self.assertEqual(1, len(search_dir_a_results))
            self.assertEqual(f"{tmp}/a/{FILENAME}", search_dir_a_results[0])

    def test_depth_pruning(self):
        finder = FileFinder(FILENAME)
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "", FILENAME)
            _create_file(tmp, "a", FILENAME)
            _create_file(tmp, "a/b", FILENAME)
            self.assertEqual(3, len(list(finder.find(tmp, 3))))
            self.assertEqual(3, len(list(finder.find(tmp, 2))))
            self.assertEqual(2, len(list(finder.find(tmp, 1))))
            self.assertEqual(1, len(list(finder.find(tmp, 0))))

    def test_path_pruning(self):
        with TemporaryDirectory() as tmp:
            finder = FileFinder(FILENAME, ignore_paths=[f"{tmp}/out"])
            _create_file(tmp, "", FILENAME)
            _create_file(tmp, "out",
                         FILENAME)  # This should not appear in results
            _create_file(
                tmp, "a/b/out",
                FILENAME)  # This is "source code", should appear in results
            results = sorted(list(finder.find(tmp, search_depth=100)),
                             reverse=True)
            self.assertEqual(2, len(results))
            self.assertEqual(f"{tmp}/{FILENAME}", results[0])
            self.assertEqual(f"{tmp}/a/b/out/{FILENAME}", results[1])

    def test_hidden_dir_pruning(self):
        prune_hidden_dir_finder = FileFinder(FILENAME)
        all_dir_finder = FileFinder(FILENAME, prune_hidden_dirs=False)
        with TemporaryDirectory() as tmp:
            _create_file(tmp, "", FILENAME)
            _create_file(tmp, ".hidden_dir", FILENAME)
            _create_file(tmp, "visible_dir", FILENAME)
            self.assertEqual(
                2,
                len(list(prune_hidden_dir_finder.find(tmp, search_depth=100))))
            self.assertEqual(
                3, len(list(all_dir_finder.find(tmp, search_depth=100))))


if __name__ == "__main__":
    # This unit test assumes that the file separator is /
    # A generic solution would use os.path.join to join path fragments and make
    # this test platform agnostic, but would make the test less readable.
    if os.name != "posix":
        raise Exception(f"This unit test is not supported on {os.name}")
    unittest.main()
