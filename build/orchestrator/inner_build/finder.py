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
"""Module for finding files in a tree"""

from collections.abc import Iterator

import os
import sys


class FileFinder:
    def __init__(self, filename: str, ignore_paths=(), prune_hidden_dirs=True):
        """Lightweight Single-threaded Non-cached file finder

        Arguments:
            filename: filename, should not contain directory prefixes
            ignore_paths: filepath of dirs to ignore (e.g.
                          $ANDROID_BUILD_TOP/out)
            prune_hidden_dirs: whether hidden dirs (beginning with a dot
                          character) should be ignored
        """
        self.filename = filename
        self.ignore_paths = ignore_paths
        if prune_hidden_dirs and os.name != "posix":
            raise Exception(
                "Skipping hidden directories is not supported on this platform"
            )
        self.prune_hidden_dirs = prune_hidden_dirs

    def _is_hidden_dir(self, dirname: str) -> bool:
        """Return true if directory is a hidden directory"""
        return self.prune_hidden_dirs and dirname.startswith(".")

    def find(self, path: str, search_depth: int) -> Iterator[str]:
        """Search directory rooted at <path>

        Arguments:
            path: Search root
            search_depth: Search maxdepth (relative to root). Search will be
                          pruned beyond this level

        Returns:
            An iterator of filepaths that match <filename>
        """
        if search_depth < 0:
            return

        subdirs = []
        with os.scandir(path) as it:
            for dirent in sorted(it, key=lambda x: x.name):
                try:
                    if dirent.is_file():
                        if dirent.name == self.filename:
                            yield dirent.path
                    elif dirent.is_dir():
                        if (dirent.path not in self.ignore_paths
                                and not self._is_hidden_dir(dirent.name)):
                            subdirs.append(dirent.path)
                except OSError as ex:
                    # Log the exception, but continue traversing
                    print(ex, file=sys.stderr)
        for subdir in subdirs:
            yield from self.find(subdir, search_depth - 1)
