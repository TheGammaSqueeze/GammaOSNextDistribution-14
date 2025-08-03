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
"""Tests for fakerepo."""
from pathlib import Path
from xml.etree import ElementTree

import pytest

from .fakerepo import FakeRepo


class TestFakeRepo:
    """Tests for fakerepo.FakeRepo."""

    def test_project_created(self, tmp_path: Path) -> None:
        """Tests that FakeRepo.project creates a new FakeProject."""
        repo = FakeRepo(tmp_path)
        project = repo.project("platform/foo", "foo")
        assert project.local.path == repo.root / "foo"
        assert project.android_mirror.path == repo.mirror_dir / "platform/foo"
        assert project.upstream.path == repo.upstream_dir / "foo"

    def test_project_error_if_path_reused(self, tmp_path: Path) -> None:
        """Tests that KeyError is raised if a project path is reused."""
        repo = FakeRepo(tmp_path)
        repo.project("platform/foo", "foo")
        repo.project("platform/bar", "bar")
        with pytest.raises(KeyError):
            repo.project("platform/baz", "foo")
        with pytest.raises(KeyError):
            repo.project("platform/foo", "baz")

    def test_create_manifest_repo_xml_structure(self, tmp_path: Path) -> None:
        """Tests that the correct manifest XML is created."""
        repo = FakeRepo(tmp_path)
        repo.project("platform/foo", "foo")
        repo.project("platform/external/bar", "external/bar")
        repo.create_manifest_repo()

        manifest_path = repo.manifest_repo / "default.xml"
        assert manifest_path.exists()
        root = ElementTree.parse(manifest_path)
        remotes = root.findall("./remote")
        assert len(remotes) == 1
        remote = remotes[0]
        assert remote.attrib["name"] == "aosp"
        assert remote.attrib["fetch"] == repo.mirror_dir.as_uri()

        defaults = root.findall("./default")
        assert len(defaults) == 1
        default = defaults[0]
        assert default.attrib["remote"] == "aosp"
        assert default.attrib["revision"] == "main"

        projects = root.findall("./project")
        assert len(projects) == 2

        assert projects[0].attrib["name"] == "platform/foo"
        assert projects[0].attrib["path"] == "foo"

        assert projects[1].attrib["name"] == "platform/external/bar"
        assert projects[1].attrib["path"] == "external/bar"

    def test_init_and_sync(self, tmp_path: Path) -> None:
        """Tests that init_and_sync initializes and syncs the tree."""
        repo = FakeRepo(tmp_path)
        project = repo.project("platform/foo", "foo")
        repo.create_manifest_repo()
        project.initial_import()
        repo.init_and_sync()

        assert project.local.head() == project.android_mirror.head()
