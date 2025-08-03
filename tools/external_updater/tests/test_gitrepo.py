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
"""Tests for gitrepo."""
import os
import subprocess
import unittest
from contextlib import ExitStack
from pathlib import Path
from tempfile import TemporaryDirectory

from .gitrepo import GitRepo


class GitRepoTest(unittest.TestCase):
    """Tests for gitrepo.GitRepo."""

    def setUp(self) -> None:
        # Local test runs will probably pass without this since the caller
        # almost certainly has git configured, but the bots that run the tests
        # may not. **Do not** use `git config --global` for this, since that
        # will modify the caller's config during local testing.
        self._original_env = os.environ.copy()
        os.environ["GIT_AUTHOR_NAME"] = "Testy McTestFace"
        os.environ["GIT_AUTHOR_EMAIL"] = "test@example.com"
        os.environ["GIT_COMMITTER_NAME"] = os.environ["GIT_AUTHOR_NAME"]
        os.environ["GIT_COMMITTER_EMAIL"] = os.environ["GIT_AUTHOR_EMAIL"]

        with ExitStack() as stack:
            temp_dir = TemporaryDirectory()  # pylint: disable=consider-using-with
            stack.enter_context(temp_dir)
            self.addCleanup(stack.pop_all().close)
            self.tmp_path = Path(temp_dir.name)

    def tearDown(self) -> None:
        # This isn't trivially `os.environ = self._original_env` because
        # os.environ isn't actually a dict, it's an os._Environ, and there isn't
        # a good way to construct a new one of those.
        os.environ.clear()
        os.environ.update(self._original_env)

    def test_commit_adds_files(self) -> None:
        """Tests that new files in commit are added to the repo."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init()
        repo.commit("Add README.md.", update_files={"README.md": "Hello, world!"})
        self.assertEqual(repo.commit_message_at_revision("HEAD"), "Add README.md.\n")
        self.assertEqual(
            repo.file_contents_at_revision("HEAD", "README.md"), "Hello, world!"
        )

    def test_commit_updates_files(self) -> None:
        """Tests that updated files in commit are modified."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init()
        repo.commit("Add README.md.", update_files={"README.md": "Hello, world!"})
        repo.commit("Update README.md.", update_files={"README.md": "Goodbye, world!"})
        self.assertEqual(repo.commit_message_at_revision("HEAD^"), "Add README.md.\n")
        self.assertEqual(
            repo.file_contents_at_revision("HEAD^", "README.md"), "Hello, world!"
        )
        self.assertEqual(repo.commit_message_at_revision("HEAD"), "Update README.md.\n")
        self.assertEqual(
            repo.file_contents_at_revision("HEAD", "README.md"), "Goodbye, world!"
        )

    def test_commit_deletes_files(self) -> None:
        """Tests that files deleted by commit are removed from the repo."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init()
        repo.commit("Add README.md.", update_files={"README.md": "Hello, world!"})
        repo.commit("Remove README.md.", delete_files={"README.md"})
        self.assertEqual(repo.commit_message_at_revision("HEAD^"), "Add README.md.\n")
        self.assertEqual(
            repo.file_contents_at_revision("HEAD^", "README.md"), "Hello, world!"
        )
        self.assertEqual(repo.commit_message_at_revision("HEAD"), "Remove README.md.\n")
        self.assertNotEqual(
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(repo.path),
                    "ls-files",
                    "--error-unmatch",
                    "README.md",
                ],
                # The atest runner cannot parse test lines that have output. Hide the
                # descriptive error from git (README.md does not exist, exactly what
                # we're testing) so the test result can be parsed.
                stderr=subprocess.DEVNULL,
                check=False,
            ).returncode,
            0,
        )

    def test_current_branch(self) -> None:
        """Tests that current branch returns the current branch name."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init("main")
        self.assertEqual(repo.current_branch(), "main")

    def test_current_branch_fails_if_not_init(self) -> None:
        """Tests that current branch fails when there is no git repo."""
        with self.assertRaises(subprocess.CalledProcessError):
            GitRepo(self.tmp_path / "repo").current_branch()

    def test_switch_to_new_branch(self) -> None:
        """Tests that switch_to_new_branch creates a new branch and switches to it."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init("main")
        repo.switch_to_new_branch("feature")
        self.assertEqual(repo.current_branch(), "feature")

    def test_switch_to_new_branch_does_not_clobber_existing_branches(self) -> None:
        """Tests that switch_to_new_branch raises an error for extant branches."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init("main")
        repo.commit("Initial commit.", allow_empty=True)
        with self.assertRaises(subprocess.CalledProcessError):
            repo.switch_to_new_branch("main")

    def test_switch_to_new_branch_with_start_point(self) -> None:
        """Tests that switch_to_new_branch uses the provided start point."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init("main")
        repo.commit("Initial commit.", allow_empty=True)
        initial_commit = repo.head()
        repo.commit("Second commit.", allow_empty=True)
        repo.switch_to_new_branch("feature", start_point=initial_commit)
        self.assertEqual(repo.current_branch(), "feature")
        self.assertEqual(repo.head(), initial_commit)

    def test_sha_of_ref(self) -> None:
        """Tests that sha_of_ref returns the SHA of the given ref."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init("main")
        repo.commit("Initial commit.", allow_empty=True)
        self.assertEqual(repo.sha_of_ref("heads/main"), repo.head())

    def test_tag_head(self) -> None:
        """Tests that tag creates a tag at HEAD."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init()
        repo.commit("Initial commit.", allow_empty=True)
        repo.commit("Second commit.", allow_empty=True)
        repo.tag("v1.0.0")
        self.assertEqual(repo.sha_of_ref("tags/v1.0.0"), repo.head())

    def test_tag_ref(self) -> None:
        """Tests that tag creates a tag at the given ref."""
        repo = GitRepo(self.tmp_path / "repo")
        repo.init()
        repo.commit("Initial commit.", allow_empty=True)
        first_commit = repo.head()
        repo.commit("Second commit.", allow_empty=True)
        repo.tag("v1.0.0", first_commit)
        self.assertEqual(repo.sha_of_ref("tags/v1.0.0"), first_commit)


if __name__ == "__main__":
    unittest.main(verbosity=2)
