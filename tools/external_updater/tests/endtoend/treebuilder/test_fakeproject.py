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
"""Tests for fakeproject."""
from pathlib import Path

from .fakeproject import FakeProject


class TestFakeProject:
    """Tests for fakeproject.FakeProject."""

    def test_constructor_initializes_upstream_repo(self, tmp_path: Path) -> None:
        """Tests that the constructor initializes the "upstream" git repo."""
        project = FakeProject(
            tmp_path / "local", tmp_path / "upstream", tmp_path / "mirror"
        )
        assert (
            project.upstream.commit_message_at_revision("HEAD") == "Initial commit.\n"
        )

    def test_initial_import(self, tmp_path: Path) -> None:
        """Tests that initial_import merges and creates metadata files."""
        project = FakeProject(
            tmp_path / "local", tmp_path / "upstream", tmp_path / "mirror"
        )
        project.upstream.commit(
            "Add README.md.", update_files={"README.md": "Hello, world!"}
        )

        upstream_sha = project.upstream.head()
        project.initial_import()

        # The import is done in the mirror repository. The cloned repository in the repo
        # tree should not be created until init_and_sync() is called.
        assert not project.local.path.exists()

        assert (
            project.android_mirror.commit_message_at_revision("HEAD^")
            == f"Merge {project.upstream.path}\n"
        )
        assert (
            project.android_mirror.commit_message_at_revision("HEAD")
            == "Add metadata files.\n"
        )
        metadata = project.android_mirror.file_contents_at_revision("HEAD", "METADATA")
        assert 'type: "GIT"' in metadata
        assert f'value: "{project.upstream.path.as_uri()}"' in metadata
        assert f'version: "{upstream_sha}"' in metadata
