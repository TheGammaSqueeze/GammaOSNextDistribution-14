#
# Copyright (C) 2024 The Android Open Source Project
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
"""Unit tests for base_updater."""

import unittest
from pathlib import Path

import base_updater
# pylint: disable=import-error
import metadata_pb2  # type: ignore
# pylint: enable=import-error


class UpdaterTest(unittest.TestCase):
    """Unit tests for Updater."""

    def test_current_version(self) -> None:
        """Tests that Updater.current_version returns the appropriate value."""
        updater = base_updater.Updater(
            # This is absolute so we get the fast path out of the path canonicalization
            # that would otherwise require us to define ANDROID_BUILD_TOP or run from a
            # temp repo tree.
            Path("/"),
            metadata_pb2.Identifier(),
            "old version",
        )
        self.assertEqual(updater.current_version, "old version")

        identifier = metadata_pb2.Identifier()
        identifier.version = "old version"
        updater = base_updater.Updater(
            # This is absolute so we get the fast path out of the path canonicalization
            # that would otherwise require us to define ANDROID_BUILD_TOP or run from a
            # temp repo tree.
            Path("/"),
            identifier,
            "",
        )
        self.assertEqual(updater.current_version, "old version")


if __name__ == "__main__":
    unittest.main(verbosity=2)
