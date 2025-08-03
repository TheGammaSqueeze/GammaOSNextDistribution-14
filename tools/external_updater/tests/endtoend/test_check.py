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
"""End-to-end tests for external_updater."""
import subprocess
from pathlib import Path

from .treebuilder import TreeBuilder


class TestCheck:
    """Tests for `external_updater check`."""

    def check(self, updater_cmd: list[str], paths: list[Path]) -> str:
        """Runs `external_updater check` with the given arguments.

        Returns:
            The output of the command.
        """
        return subprocess.run(
            updater_cmd + ["check"] + [str(p) for p in paths],
            check=True,
            capture_output=True,
            text=True,
        ).stdout

    def test_git_up_to_date(
        self, tree_builder: TreeBuilder, updater_cmd: list[str]
    ) -> None:
        """Tests that up-to-date projects are identified."""
        tree = tree_builder.repo_tree("tree")
        a = tree.project("platform/external/foo", "external/foo")
        a.upstream.commit(
            "Add README.md.",
            update_files={
                "README.md": "Hello, world!\n",
            },
        )
        tree.create_manifest_repo()
        a.initial_import()
        tree.init_and_sync()
        output = self.check(updater_cmd, [a.local.path])
        current_version = a.upstream.head()
        assert output == (
            f"Checking {a.local.path}...\n"
            f"Current version: {current_version}\n"
            f"Latest version: {current_version}\n"
            "Up to date.\n"
        )

    def test_git_out_of_date(
        self, tree_builder: TreeBuilder, updater_cmd: list[str]
    ) -> None:
        """Tests that out-of-date projects are identified."""
        tree = tree_builder.repo_tree("tree")
        a = tree.project("platform/external/foo", "external/foo")
        a.upstream.commit(
            "Add README.md.",
            update_files={
                "README.md": "Hello, world!\n",
            },
        )
        tree.create_manifest_repo()
        a.initial_import()
        current_version = a.upstream.head()
        tree.init_and_sync()
        a.upstream.commit(
            "Update the project.",
            update_files={"README.md": "This project is deprecated.\n"},
        )
        output = self.check(updater_cmd, [a.local.path])
        latest_version = a.upstream.head()
        assert output == (
            f"Checking {a.local.path}...\n"
            f"Current version: {current_version}\n"
            f"Latest version: {latest_version}\n"
            "Out of date!\n"
        )
