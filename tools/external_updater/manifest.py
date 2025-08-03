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
"""Manifest discovery and parsing.

The repo manifest format is documented at
https://gerrit.googlesource.com/git-repo/+/master/docs/manifest-format.md. This module
doesn't implement the full spec, since we only need a few properties.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree


def find_manifest_xml_for_tree(root: Path) -> Path:
    """Returns the path to the manifest XML file for the tree."""
    repo_path = root / ".repo/manifests/default.xml"
    if repo_path.exists():
        return repo_path
    raise FileNotFoundError(f"Could not find manifest at {repo_path}")


@dataclass(frozen=True)
class Project:
    """Data for a manifest <project /> field.

    https://gerrit.googlesource.com/git-repo/+/master/docs/manifest-format.md#element-project
    """

    path: str
    remote: str
    revision: str

    @staticmethod
    def from_xml_node(
        node: ElementTree.Element, default_remote: str, default_revision: str
    ) -> Project:
        """Parses a Project from the given XML node."""
        try:
            path = node.attrib["path"]
        except KeyError as ex:
            raise RuntimeError(
                f"<project /> element missing required path attribute: {node}"
            ) from ex

        return Project(
            path,
            node.attrib.get("remote", default_remote),
            node.attrib.get("revision", default_revision),
        )


class ManifestParser:  # pylint: disable=too-few-public-methods
    """Parser for the repo manifest.xml."""

    def __init__(self, xml_path: Path) -> None:
        self.xml_path = xml_path

    def parse(self) -> Manifest:
        """Parses the manifest.xml file and returns a Manifest."""
        root = ElementTree.parse(self.xml_path)
        defaults = root.findall("./default")
        if len(defaults) != 1:
            raise RuntimeError(
                f"Expected exactly one <default /> element, found {len(defaults)}"
            )
        default_node = defaults[0]
        try:
            default_revision = default_node.attrib["revision"]
            default_remote = default_node.attrib["remote"]
        except KeyError as ex:
            raise RuntimeError("<default /> element missing required attribute") from ex

        return Manifest(
            self.xml_path,
            [
                Project.from_xml_node(p, default_remote, default_revision)
                for p in root.findall("./project")
            ],
        )


class Manifest:
    """The manifest data for a repo tree.

    https://gerrit.googlesource.com/git-repo/+/master/docs/manifest-format.md
    """

    def __init__(self, path: Path, projects: list[Project]) -> None:
        self.path = path
        self.projects_by_path = {p.path: p for p in projects}

    @staticmethod
    def for_tree(root: Path) -> Manifest:
        """Constructs a Manifest for the tree at `root`."""
        return ManifestParser(find_manifest_xml_for_tree(root)).parse()

    def project_with_path(self, path: str) -> Project:
        """Returns the Project with the given path, or raises KeyError."""
        return self.projects_by_path[path]
