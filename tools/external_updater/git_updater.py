# Copyright (C) 2018 The Android Open Source Project
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
"""Module to check updates from Git upstream."""

import base_updater
import fileutils
import git_utils
# pylint: disable=import-error
from manifest import Manifest


class GitUpdater(base_updater.Updater):
    """Updater for Git upstream."""
    UPSTREAM_REMOTE_NAME: str = "update_origin"

    def is_supported_url(self) -> bool:
        return git_utils.is_valid_url(self._proj_path, self._old_identifier.value)

    @staticmethod
    def _is_likely_android_remote(url: str) -> bool:
        """Returns True if the URL is likely to be the project's Android remote."""
        # There isn't a strict rule for finding the correct remote for
        # upstream-master/main, so we have to guess. Be careful to filter out
        # things that look almost right but aren't. Here's an example of a
        # project that has a lot of false positives:
        #
        # aosp    /usr/local/google/home/danalbert/src/mirrors/android/refs/aosp/toolchain/rr.git (fetch)
        # aosp    persistent-https://android.git.corp.google.com/toolchain/rr (push)
        # origin  https://github.com/DanAlbert/rr.git (fetch)
        # origin  https://github.com/DanAlbert/rr.git (push)
        # unmirrored      persistent-https://android.git.corp.google.com/toolchain/rr (fetch)
        # unmirrored      persistent-https://android.git.corp.google.com/toolchain/rr (push)
        # update_origin   https://github.com/rr-debugger/rr (fetch)
        # update_origin   https://github.com/rr-debugger/rr (push)
        # upstream        https://github.com/rr-debugger/rr.git (fetch)
        # upstream        https://github.com/rr-debugger/rr.git (push)
        #
        # unmirrored is the correct remote here. It's not a local path,
        # and contains either /platform/external/ or /toolchain/ (the two
        # common roots for third- party Android imports).
        if '://' not in url:
            # Skip anything that's likely a local GoB mirror.
            return False
        if '/platform/external/' in url:
            return True
        if '/toolchain/' in url:
            return True
        return False

    def setup_remote(self) -> None:
        remotes = git_utils.list_remotes(self._proj_path)
        current_remote_url = None
        android_remote_name: str | None = None
        for name, url in remotes.items():
            if name == self.UPSTREAM_REMOTE_NAME:
                current_remote_url = url

            if self._is_likely_android_remote(url):
                android_remote_name = name

        if android_remote_name is None:
            remotes_formatted = "\n".join(f"{k} {v}" for k, v in remotes.items())
            raise RuntimeError(
                f"Could not determine android remote for {self._proj_path}. Tried:\n"
                f"{remotes_formatted}")

        if current_remote_url is not None and current_remote_url != self._old_identifier.value:
            git_utils.remove_remote(self._proj_path, self.UPSTREAM_REMOTE_NAME)
            current_remote_url = None

        if current_remote_url is None:
            git_utils.add_remote(self._proj_path, self.UPSTREAM_REMOTE_NAME,
                                 self._old_identifier.value)

        branch = git_utils.detect_default_branch(self._proj_path,
                                                 self.UPSTREAM_REMOTE_NAME)

        git_utils.fetch(self._proj_path, self.UPSTREAM_REMOTE_NAME, branch)
        git_utils.fetch(self._proj_path, android_remote_name,
                        self._determine_android_fetch_ref())

    def check(self) -> None:
        """Checks upstream and returns whether a new version is available."""
        self.setup_remote()
        possible_alternative_new_ver: str | None = None
        if git_utils.is_commit(self._old_identifier.version):
            # Update to remote head.
            self._new_identifier.version = self.current_head_of_upstream_default_branch()
            # Some libraries don't have a tag. We only populate
            # _alternative_new_ver if there is a tag newer than _old_ver.
            # Checks if there is a tag newer than AOSP's SHA
            if (tag := self.latest_tag_of_upstream_default_branch()) is not None:
                possible_alternative_new_ver = tag
        else:
            # Update to the latest version tag.
            tag = self.latest_tag_of_upstream_default_branch()
            if tag is None:
                project = fileutils.canonicalize_project_path(self.project_path)
                raise RuntimeError(
                    f"{project} is currently tracking upstream tags but no tags were "
                    "found in the upstream repository"
                )
            self._new_identifier.version = tag
            # Checks if there is a SHA newer than AOSP's tag
            possible_alternative_new_ver = self.current_head_of_upstream_default_branch()
        if possible_alternative_new_ver is not None and git_utils.is_ancestor(
            self._proj_path,
            self._old_identifier.version,
            possible_alternative_new_ver
        ):
            self._alternative_new_ver = possible_alternative_new_ver

    def latest_tag_of_upstream_default_branch(self) -> str | None:
        branch = git_utils.detect_default_branch(self._proj_path,
                                                 self.UPSTREAM_REMOTE_NAME)
        return git_utils.get_most_recent_tag(
            self._proj_path, self.UPSTREAM_REMOTE_NAME + '/' + branch)

    def current_head_of_upstream_default_branch(self) -> str:
        branch = git_utils.detect_default_branch(self._proj_path,
                                                 self.UPSTREAM_REMOTE_NAME)
        return git_utils.get_sha_for_branch(
            self._proj_path, self.UPSTREAM_REMOTE_NAME + '/' + branch)

    def update(self) -> None:
        """Updates the package.
        Has to call check() before this function.
        """
        print(f"Running `git merge {self._new_identifier.version}`...")
        git_utils.merge(self._proj_path, self._new_identifier.version)

    def _determine_android_fetch_ref(self) -> str:
        """Returns the ref that should be fetched from the android remote."""
        # It isn't particularly efficient to reparse the tree for every
        # project, but we don't guarantee that all paths passed to updater.sh
        # are actually in the same tree so it wouldn't necessarily be correct
        # to do this once at the top level. This isn't the slow part anyway,
        # so it can be dealt with if that ever changes.
        root = fileutils.find_tree_containing(self._proj_path)
        manifest = Manifest.for_tree(root)
        manifest_path = str(self._proj_path.relative_to(root))
        try:
            project = manifest.project_with_path(manifest_path)
        except KeyError as ex:
            raise RuntimeError(
                f"Did not find {manifest_path} in {manifest.path} (tree root is {root})"
            ) from ex
        return project.revision
