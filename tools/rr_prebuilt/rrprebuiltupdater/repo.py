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
"""APIs for working with repo."""
import subprocess
from pathlib import Path


class Repo:
    """Wrapper around repo commands."""

    def __init__(self, top: Path) -> None:
        self.top = top

    def _in_pore_tree(self) -> bool:
        """Returns True if the tree is using pore instead of repo."""
        return (self.top / ".pore").exists()

    def start_branch(self, name: str) -> None:
        """Starts a branch in the project."""
        pore = self._in_pore_tree()
        if pore:
            args = ["pore"]
        else:
            args = ["repo"]
        args.extend(["start", name])
        if not pore:
            args.append(".")
        subprocess.run(args, check=True)

    def upload(self) -> None:
        """Uploads the current branch to gerrit."""
        if self._in_pore_tree():
            repo = "pore"
        else:
            repo = "repo"
        subprocess.run([repo, "upload", "--cbr", "."], check=True)
