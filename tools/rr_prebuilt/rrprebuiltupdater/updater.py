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
"""Updater for rr prebuilts."""
import contextlib
import logging
import subprocess
import textwrap
from collections.abc import AsyncIterator
from pathlib import Path
from tempfile import TemporaryDirectory

import fetchartifact
from aiohttp import ClientSession

from .buildapi import BUILD_API_URL
from .repo import Repo

THIS_DIR = Path(__file__).parent
TOP = THIS_DIR.parent.parent.parent
INSTALL_PATH = THIS_DIR / "rr/android/x86_64"


BRANCH = "aosp-rr-dev"
BUILD_TARGET = "linux"
ARTIFACT_NAME = "rr-5.6.0-Android-x86_64.tar.gz"


class Updater:
    """Tool for updating the rr prebuilts stored in this directory."""

    def __init__(
        self,
        build_id: str,
        use_current_branch: bool = False,
        should_upload: bool = True,
        build_api_url: str = BUILD_API_URL,
        install_path: Path = INSTALL_PATH,
        repo_root: Path = TOP,
    ) -> None:
        self.build_id = build_id
        self._use_current_branch = use_current_branch
        self._should_upload = should_upload
        self.repo = Repo(repo_root)
        self.build_api_url = build_api_url
        self.install_path = install_path

    async def run(self) -> None:
        """Performs the update.

        The updater will:

        1. Download the new prebuilt
        2. Create a repo branch
        3. Delete the old prebuilt
        4. Install the new prebuilt to the repo
        5. Create a commit for the update
        6. Upload the CL
        """
        async with self.extracted_artifact() as extracted_path:
            if not self._use_current_branch:
                self.repo.start_branch(f"update-rr-prebuilt-{self.build_id}")
            self.replace_current_prebuilt_with(extracted_path)
            self.commit()
            self.upload()

    def replace_current_prebuilt_with(self, extracted_path: Path) -> None:
        """Replaces the currently installed prebuilts with those from the given path.

        The removal of old prebuilts and installation of new prebuilts will be tracked
        by git (i.e., `git rm` and `git add` will be called).
        """
        if self.install_path.exists():
            logging.info("Removing old prebuilts from %s", self.install_path)
            subprocess.run(["git", "rm", "-rf", self.install_path], check=True)
        self.install_path.parent.mkdir(exist_ok=True, parents=True)
        logging.info("Installing prebuilts to %s", self.install_path)
        extracted_path.rename(self.install_path)
        logging.info("Adding %s to git index", self.install_path)
        subprocess.run(["git", "add", self.install_path], check=True)

    @contextlib.asynccontextmanager
    async def extracted_artifact(self) -> AsyncIterator[Path]:
        """Context manager for the extracted artifact tarball.

        On entry, the artifact will be downloaded and extracted to a temporary
        directory. On exit, the temporary directory will be deleted.

        The archive is assumed to be have common tarball layout. That is, the top level
        of the tarball is expected to be a single directory which contains the contents.
        The yielded path will be the path to that single top level directory to avoid
        the caller needing to care about the name of the top level directory.

        Yields:
            The path to the contents of the archive.
        """
        with TemporaryDirectory() as temp_dir:
            async with self._downloaded_artifact() as path:
                extract_path = Path(temp_dir)
                logging.info("Extracting %s to %s", path, extract_path)
                # This is using check_call rather than run as a hack to avoid having
                # this mocked out in tests that just want to mock git.
                subprocess.check_call(
                    ["tar", "xf", path, "--strip-components=1", "-C", extract_path]
                )
                yield extract_path

    @contextlib.asynccontextmanager
    async def _downloaded_artifact(self) -> AsyncIterator[Path]:
        """Downloads the artifact to a temp directory and returns its path."""
        async with ClientSession() as session:
            with TemporaryDirectory() as temp_dir:
                path = Path(temp_dir) / "artifact.tar.gz"
                logging.info(
                    "Downloading %s from target %s of build %s to %s",
                    ARTIFACT_NAME,
                    BUILD_TARGET,
                    self.build_id,
                    path,
                )
                with path.open("wb") as output:
                    async for chunk in fetchartifact.fetch_artifact_chunked(
                        BUILD_TARGET,
                        self.build_id,
                        ARTIFACT_NAME,
                        session,
                        query_url_base=self.build_api_url,
                    ):
                        output.write(chunk)
                yield path

    def commit(self) -> None:
        """Commits the changes to the git repo."""
        logging.info("Committing changes")
        message = textwrap.dedent(
            f"""\
            Update rr prebuilts to build {self.build_id}.

            Bug: None
            Test: treehugger
            """
        )
        subprocess.run(["git", "commit", "-m", message], check=True)

    def upload(self) -> None:
        """Uploads the changes to gerrit.

        If should_upload=False was passed to the constructor, the upload will be
        skipped.
        """
        if not self._should_upload:
            logging.info("Skipping upload because --no-upload was passed")
            return

        logging.info("Uploading CL")
        self.repo.upload()
