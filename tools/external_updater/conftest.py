#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Pytest fixtures common across multiple test modules."""
from pathlib import Path

import pytest


def make_repo_tree(root: Path) -> Path:
    """Creates a fake repo tree in the given root."""
    (root / ".repo").mkdir(parents=True)
    (root / "external/foobar").mkdir(parents=True)
    return root


@pytest.fixture(name="repo_tree")
def fixture_repo_tree(tmp_path: Path) -> Path:
    """Fixture for a repo tree."""
    return make_repo_tree(tmp_path)
