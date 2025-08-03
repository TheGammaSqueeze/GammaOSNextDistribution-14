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
"""repo tree fakes for use in tests."""
import contextlib
import subprocess
from pathlib import Path
from xml.etree import ElementTree

from .fakeproject import FakeProject


class FakeRepo:
    """A repo tree for use in tests.

    This shouldn't be used directly. Use the tree_builder fixture.
    """

    def __init__(self, temp_dir: Path) -> None:
        self.root = temp_dir / "tree"
        self.mirror_dir = temp_dir / "mirrors"
        self.upstream_dir = temp_dir / "upstreams"
        self.manifest_repo = temp_dir / "manifest"
        self.projects: list[FakeProject] = []
        self._used_git_subpaths: set[str] = set()
        self._used_tree_subpaths: set[str] = set()

    def project(self, git_subpath: str, tree_subpath: str) -> FakeProject:
        """Creates a new project in the repo."""
        if git_subpath in self._used_git_subpaths:
            raise KeyError(f"A project with git path {git_subpath} already exists")
        if tree_subpath in self._used_tree_subpaths:
            raise KeyError(f"A project with tree path {tree_subpath} already exists")
        project = FakeProject(
            self.root / tree_subpath,
            self.upstream_dir / tree_subpath,
            self.mirror_dir / git_subpath,
        )
        self.projects.append(project)
        self._used_git_subpaths.add(git_subpath)
        self._used_tree_subpaths.add(tree_subpath)
        return project

    def init_and_sync(self) -> None:
        """Runs repo init and repo sync to clone the repo tree."""
        self.root.mkdir(parents=True)
        with contextlib.chdir(self.root):
            subprocess.run(
                ["repo", "init", "-c", "-u", str(self.manifest_repo), "-b", "main"],
                check=True,
            )
            subprocess.run(["repo", "sync", "-c"], check=True)

    def create_manifest_repo(self) -> None:
        """Creates the git repo for the manifest, commits the manifest XML."""
        self.manifest_repo.mkdir(parents=True)
        with contextlib.chdir(self.manifest_repo):
            subprocess.run(["git", "init"], check=True)
            Path("default.xml").write_bytes(
                ElementTree.tostring(self._create_manifest_xml(), encoding="utf-8")
            )
            subprocess.run(["git", "add", "default.xml"], check=True)
            subprocess.run(["git", "commit", "-m", "Initial commit."], check=True)

    def _create_manifest_xml(self) -> ElementTree.Element:
        # Example manifest:
        #
        # <manifest>
        #   <remote name="aosp" fetch="$URL" />
        #   <default revision="main" remote="aosp" />
        #
        #   <project path="external/project" name="platform/external/project"
        #            revision="master" remote="goog" />
        #   ...
        # </manifest>
        #
        # The revision and remote attributes of project are optional.
        root = ElementTree.Element("manifest")
        ElementTree.SubElement(
            root,
            "remote",
            {"name": "aosp", "fetch": self.mirror_dir.resolve().as_uri()},
        )
        ElementTree.SubElement(root, "default", {"revision": "main", "remote": "aosp"})
        for project in self.projects:
            ElementTree.SubElement(
                root,
                "project",
                {
                    "path": str(project.local.path.relative_to(self.root)),
                    "name": str(
                        project.android_mirror.path.relative_to(self.mirror_dir)
                    ),
                },
            )
        return root
