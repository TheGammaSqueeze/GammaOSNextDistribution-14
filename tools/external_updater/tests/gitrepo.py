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
"""APIs for interacting with git repositories."""
# TODO: This should be partially merged with the git_utils APIs.
# The bulk of this should be lifted out of the tests and used by the rest of
# external_updater, but we'll want to keep a few of the APIs just in the tests because
# they're not particularly sensible elsewhere (specifically the shorthand for commit
# with the update_files and delete_files arguments). It's probably easiest to do that by
# reworking the git_utils APIs into a class like this and then deriving this one from
# that.
from __future__ import annotations

import subprocess
from pathlib import Path


class GitRepo:
    """A git repository for use in tests."""

    def __init__(self, path: Path) -> None:
        self.path = path

    def run(self, command: list[str]) -> str:
        """Runs the given git command in the repository, returning the output."""
        return subprocess.run(
            ["git", "-C", str(self.path)] + command,
            check=True,
            capture_output=True,
            text=True,
        ).stdout

    def init(self, branch_name: str | None = None) -> None:
        """Initializes a new git repository."""
        self.path.mkdir(parents=True)
        cmd = ["init"]
        if branch_name is not None:
            cmd.extend(["-b", branch_name])
        self.run(cmd)

    def head(self) -> str:
        """Returns the SHA of the current HEAD."""
        return self.run(["rev-parse", "HEAD"]).strip()

    def sha_of_ref(self, ref: str) -> str:
        """Returns the sha of the given ref."""
        return self.run(["rev-list", "-n", "1", ref]).strip()

    def current_branch(self) -> str:
        """Returns the name of the current branch."""
        return self.run(["branch", "--show-current"]).strip()

    def fetch(self, ref_or_repo: str | GitRepo) -> None:
        """Fetches the given ref or repo."""
        if isinstance(ref_or_repo, GitRepo):
            ref_or_repo = str(ref_or_repo.path)
        self.run(["fetch", ref_or_repo])

    def commit(
        self,
        message: str,
        allow_empty: bool = False,
        update_files: dict[str, str] | None = None,
        delete_files: set[str] | None = None,
    ) -> None:
        """Create a commit in the repository."""
        if update_files is None:
            update_files = {}
        if delete_files is None:
            delete_files = set()

        for delete_file in delete_files:
            self.run(["rm", delete_file])

        for update_file, contents in update_files.items():
            (self.path / update_file).write_text(contents, encoding="utf-8")
            self.run(["add", update_file])

        commit_cmd = ["commit", "-m", message]
        if allow_empty:
            commit_cmd.append("--allow-empty")
        self.run(commit_cmd)

    def merge(
        self,
        ref: str,
        allow_fast_forward: bool = True,
        allow_unrelated_histories: bool = False,
    ) -> None:
        """Merges the upstream ref into the repo."""
        cmd = ["merge"]
        if not allow_fast_forward:
            cmd.append("--no-ff")
        if allow_unrelated_histories:
            cmd.append("--allow-unrelated-histories")
        self.run(cmd + [ref])

    def switch_to_new_branch(self, name: str, start_point: str | None = None) -> None:
        """Creates and switches to a new branch."""
        args = ["switch", "--create", name]
        if start_point is not None:
            args.append(start_point)
        self.run(args)

    def tag(self, name: str, ref: str | None = None) -> None:
        """Creates a tag at the given ref, or HEAD if not provided."""
        args = ["tag", name]
        if ref is not None:
            args.append(ref)
        self.run(args)

    def commit_message_at_revision(self, revision: str) -> str:
        """Returns the commit message of the given revision."""
        # %B is the raw commit body
        # %- eats the separator newline
        # Note that commit messages created with `git commit` will always end with a
        # trailing newline.
        return self.run(["log", "--format=%B%-", "-n1", revision])

    def file_contents_at_revision(self, revision: str, path: str) -> str:
        """Returns the commit message of the given revision."""
        # %B is the raw commit body
        # %- eats the separator newline
        return self.run(["show", "--format=%B%-", f"{revision}:{path}"])
