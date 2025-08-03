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
"""Git repository fakes for use in tests."""
import textwrap
from pathlib import Path

from tests.gitrepo import GitRepo


class FakeProject:  # pylint: disable=too-few-public-methods
    """A collection of git repositories for use in tests.

    This shouldn't be used directly. Use the tree_builder fixture.

    A project contains the three git repos that are used in the Android upstream
    mirroring process:

    1. The true upstream repository. Where the third-party commits their work.
    2. The Android repository. Where Gerrit submits work.
    3. The local repository. This is where work happens before being uploaded to Gerrit.
    """

    def __init__(
        self, tree_path: Path, upstream_path: Path, android_mirror_path: Path
    ) -> None:
        self.local = GitRepo(tree_path)
        self.upstream = GitRepo(upstream_path)
        self.android_mirror = GitRepo(android_mirror_path)
        self._initialize_repo(self.upstream)

    def _initialize_repo(self, repo: GitRepo) -> None:
        """Create a git repo and initial commit in the upstream repository."""
        repo.init(branch_name="main")
        repo.commit("Initial commit.", allow_empty=True)

    def initial_import(self) -> None:
        """Perform the initial import of the upstream repo into the mirror repo.

        These are an approximation of the steps that would be taken for the initial
        import as part of go/android3p:

        * A new git repo is created with a single empty commit. That commit is **not**
          present in the upstream repo. Android imports begin with unrelated histories.
        * The upstream repository is merged into the local tree.
        * METADATA, NOTICE, MODULE_LICENSE_*, and OWNERS files are added to the local
          tree. We only bother with METADATA for now since that's all the tests need.
        """
        self.android_mirror.init()
        self.android_mirror.commit("Initial commit.", allow_empty=True)

        upstream_sha = self.upstream.head()
        self.android_mirror.fetch(self.upstream)
        self.android_mirror.merge(
            "FETCH_HEAD", allow_fast_forward=False, allow_unrelated_histories=True
        )

        self.android_mirror.commit(
            "Add metadata files.",
            update_files={
                "OWNERS": "nobody",
                "METADATA": textwrap.dedent(
                    f"""\
                    name: "test"
                    description: "It's a test."
                    third_party {{
                      license_type: UNENCUMBERED
                      last_upgrade_date {{
                        year: 2023
                        month: 12
                        day: 1
                      }}
                      identifier {{
                        type: "GIT"
                        value: "{self.upstream.path.as_uri()}"
                        version: "{upstream_sha}"
                      }}
                    }}
                    """
                ),
            },
        )
