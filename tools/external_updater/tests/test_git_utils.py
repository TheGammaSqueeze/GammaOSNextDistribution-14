#
# Copyright (C) 2024 The Android Open Source Project
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
"""Tests for git_utils."""
import os
import unittest
from contextlib import ExitStack
from pathlib import Path
from subprocess import CalledProcessError
from tempfile import TemporaryDirectory

import git_utils

from .gitrepo import GitRepo


class GitRepoTestCase(unittest.TestCase):
    """Common base for tests that operate on a git repo."""

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
            self.repo = GitRepo(Path(temp_dir.name) / "repo")

    def tearDown(self) -> None:
        # This isn't trivially `os.environ = self._original_env` because
        # os.environ isn't actually a dict, it's an os._Environ, and there isn't
        # a good way to construct a new one of those.
        os.environ.clear()
        os.environ.update(self._original_env)

class IsAncestorTest(GitRepoTestCase):
    """Tests for git_utils.is_ancestor."""

    def test_if_commit_is_its_own_ancestor(self) -> None:
        """Tests that False is returned when both commits are the same."""
        self.repo.init()
        self.repo.commit("Initial commit.", allow_empty=True)
        initial_commit = self.repo.head()
        assert not git_utils.is_ancestor(self.repo.path, initial_commit, initial_commit)

    def test_is_ancestor(self) -> None:
        """Tests that True is returned when the ref is an ancestor."""
        self.repo.init()
        self.repo.commit("Initial commit.", allow_empty=True)
        initial_commit = self.repo.head()
        self.repo.commit("Second commit.", allow_empty=True)
        second_commit = self.repo.head()
        git_utils.is_ancestor(self.repo.path, initial_commit, second_commit)

    def test_is_not_ancestor(self) -> None:
        """Tests that False is returned when the ref is not an ancestor."""
        self.repo.init()
        self.repo.commit("Initial commit.", allow_empty=True)
        initial_commit = self.repo.head()
        self.repo.commit("Second commit.", allow_empty=True)
        second_commit = self.repo.head()
        assert not git_utils.is_ancestor(self.repo.path, second_commit, initial_commit)

    def test_error(self) -> None:
        """Tests that an error is raised when git encounters an error."""
        self.repo.init()
        with self.assertRaises(CalledProcessError):
            git_utils.is_ancestor(self.repo.path, "not-a-ref", "not-a-ref")


class GetMostRecentTagTest(GitRepoTestCase):
    """Tests for git_utils.get_most_recent_tag."""

    def test_find_tag_on_correct_branch(self) -> None:
        """Tests that only tags on the given branch are found."""
        self.repo.init("main")
        self.repo.commit("Initial commit.", allow_empty=True)
        self.repo.tag("v1.0.0")
        self.repo.switch_to_new_branch("release-2.0")
        self.repo.commit("Second commit.", allow_empty=True)
        self.repo.tag("v2.0.0")
        self.assertEqual(
            git_utils.get_most_recent_tag(self.repo.path, "main"), "v1.0.0"
        )

    def test_no_tags(self) -> None:
        """Tests that None is returned when the repo has no tags."""
        self.repo.init("main")
        self.repo.commit("Initial commit.", allow_empty=True)
        self.assertIsNone(git_utils.get_most_recent_tag(self.repo.path, "main"))

    def test_no_describing_tags(self) -> None:
        """Tests that None is returned when no tags describe the ref."""
        self.repo.init("main")
        self.repo.commit("Initial commit.", allow_empty=True)
        self.repo.switch_to_new_branch("release-2.0")
        self.repo.commit("Second commit.", allow_empty=True)
        self.repo.tag("v2.0.0")
        self.assertIsNone(git_utils.get_most_recent_tag(self.repo.path, "main"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
