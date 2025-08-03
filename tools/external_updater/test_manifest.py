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
"""Tests for manifest.py."""
import textwrap
from pathlib import Path

import pytest

from manifest import Manifest, ManifestParser, find_manifest_xml_for_tree


class TestFindManifestXmlForTree:
    """Tests for find_manifest_xml_for_tree."""

    def test_repo_tree(self, repo_tree: Path) -> None:
        """Tests that the correct manifest file is found in a repo tree."""
        manifest_dir = Path(repo_tree / ".repo/manifests")
        manifest_dir.mkdir()
        manifest_path = manifest_dir / "default.xml"
        manifest_path.touch()
        assert find_manifest_xml_for_tree(repo_tree) == manifest_path

    def test_no_manifest(self, tmp_path: Path) -> None:
        """Tests that an error is raised when no manifest is found."""
        with pytest.raises(FileNotFoundError):
            find_manifest_xml_for_tree(tmp_path)


class TestManifestParser:
    """Tests for ManifestParser."""

    def test_default_missing(self, tmp_path: Path) -> None:
        """Tests that an error is raised when the default node is missing."""
        manifest_path = tmp_path / "manifest.xml"
        manifest_path.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <project path="external/project" revision="master" />
                </manifest>
                """
            )
        )
        with pytest.raises(RuntimeError):
            ManifestParser(manifest_path).parse()

    def test_multiple_default(self, tmp_path: Path) -> None:
        """Tests that an error is raised when there is more than one default node."""
        manifest = tmp_path / "manifest.xml"
        manifest.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default revision="main" remote="aosp" />
                    <default revision="main" remote="aosp" />

                    <project path="external/project" revision="master" />
                </manifest>
                """
            )
        )
        with pytest.raises(RuntimeError):
            ManifestParser(manifest).parse()

    def test_remote_default(self, tmp_path: Path) -> None:
        """Tests that the default remote is used when not defined by the project."""
        manifest_path = tmp_path / "manifest.xml"
        manifest_path.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <remote name="aosp" />
                    <default revision="main" remote="aosp" />

                    <project path="external/project" />
                </manifest>
                """
            )
        )
        manifest = ManifestParser(manifest_path).parse()
        assert manifest.project_with_path("external/project").remote == "aosp"

    def test_revision_default(self, tmp_path: Path) -> None:
        """Tests that the default revision is used when not defined by the project."""
        manifest_path = tmp_path / "manifest.xml"
        manifest_path.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default revision="main" remote="aosp" />

                    <project path="external/project" />
                </manifest>
                """
            )
        )
        manifest = ManifestParser(manifest_path).parse()
        assert manifest.project_with_path("external/project").revision == "main"

    def test_remote_explicit(self, tmp_path: Path) -> None:
        """Tests that the project remote is used when defined."""
        manifest_path = tmp_path / "manifest.xml"
        manifest_path.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default revision="main" remote="aosp" />

                    <project path="external/project" remote="origin" />
                </manifest>
                """
            )
        )
        manifest = ManifestParser(manifest_path).parse()
        assert manifest.project_with_path("external/project").remote == "origin"

    def test_revision_explicit(self, tmp_path: Path) -> None:
        """Tests that the project revision is used when defined."""
        manifest_path = tmp_path / "manifest.xml"
        manifest_path.write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default revision="main" remote="aosp" />

                    <project path="external/project" revision="master" />
                </manifest>
                """
            )
        )
        manifest = ManifestParser(manifest_path).parse()
        assert manifest.project_with_path("external/project").revision == "master"


class TestManifest:
    """Tests for Manifest."""

    def test_for_tree(self, repo_tree: Path) -> None:
        """Tests the Manifest.for_tree constructor."""
        manifest_dir = Path(repo_tree / ".repo/manifests")
        manifest_dir.mkdir()
        (manifest_dir / "default.xml").write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default remote="aosp" revision="main" />

                    <project path="external/a" />
                    <project path="external/b" />
                    <project path="external/c" />
                </manifest>
                """
            )
        )
        manifest = Manifest.for_tree(repo_tree)
        assert len(manifest.projects_by_path) == 3

    def test_project_with_path(self, repo_tree: Path) -> None:
        """Tests that Manifest.project_with_path returns the correct project."""
        manifest_dir = Path(repo_tree / ".repo/manifests")
        manifest_dir.mkdir()
        (manifest_dir / "default.xml").write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default remote="aosp" revision="main" />

                    <project path="external/a" />
                    <project path="external/b" />
                    <project path="external/c" />
                </manifest>
                """
            )
        )
        manifest = Manifest.for_tree(repo_tree)
        assert manifest.project_with_path("external/b").path == "external/b"

    def test_project_with_path_missing(self, repo_tree: Path) -> None:
        """Tests that Manifest.project_with_path raises an error when not found."""
        manifest_dir = Path(repo_tree / ".repo/manifests")
        manifest_dir.mkdir()
        (manifest_dir / "default.xml").write_text(
            textwrap.dedent(
                """\
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest>
                    <default remote="aosp" revision="main" />

                    <project path="external/a" />
                    <project path="external/b" />
                    <project path="external/c" />
                </manifest>
                """
            )
        )
        manifest = Manifest.for_tree(repo_tree)
        with pytest.raises(KeyError):
            manifest.project_with_path("external/d")
