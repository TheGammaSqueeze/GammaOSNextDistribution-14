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
"""Builder for creating repo trees for use in testing."""
from pathlib import Path

from .fakerepo import FakeRepo


class TreeBuilder:  # pylint: disable=too-few-public-methods
    """Creates test repo trees in a temporary directory."""

    def __init__(self, temp_dir: Path) -> None:
        self.temp_dir = temp_dir
        self._trees: set[str] = set()

    def repo_tree(self, name: str) -> FakeRepo:
        """Creates a new repo tree with the given name."""
        if name in self._trees:
            raise KeyError(f"A repo tree named {name} already exists")
        self._trees.add(name)
        return FakeRepo(self.temp_dir / name)
