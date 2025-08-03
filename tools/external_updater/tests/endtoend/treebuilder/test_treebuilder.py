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
"""Tests for treebuilder."""
from pathlib import Path

import pytest

from .treebuilder import TreeBuilder


class TestTreeBuilder:
    """Tests for treebuilder.TreeBuilder"""

    def test_repo_tree(self, tmp_path: Path) -> None:
        """Tests that a TreeBuilder is created."""
        builder = TreeBuilder(tmp_path)
        assert builder.temp_dir == tmp_path

    def test_repo_tree_error_if_name_reused(self, tmp_path: Path) -> None:
        """Tests that KeyError is raised if a tree name is reused."""
        builder = TreeBuilder(tmp_path)
        builder.repo_tree("foo")
        builder.repo_tree("bar")
        with pytest.raises(KeyError):
            builder.repo_tree("foo")
