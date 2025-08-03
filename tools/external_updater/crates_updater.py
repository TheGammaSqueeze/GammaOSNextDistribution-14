# Copyright (C) 2020 The Android Open Source Project
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
"""Module to check updates from crates.io."""

import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import urllib.request
from typing import IO

import archive_utils
from base_updater import Updater
import git_utils
# pylint: disable=import-error
import metadata_pb2  # type: ignore
import updater_utils

LIBRARY_NAME_PATTERN: str = r"([-\w]+)"

ALPHA_BETA_PATTERN: str = r"^.*[0-9]+\.[0-9]+\.[0-9]+-(alpha|beta).*"

ALPHA_BETA_RE: re.Pattern = re.compile(ALPHA_BETA_PATTERN)

"""Match both x.y.z and x.y.z+a.b.c which is used by some Vulkan binding libraries"""
VERSION_PATTERN: str = r"([0-9]+)\.([0-9]+)\.([0-9]+)(\+([0-9]+)\.([0-9]+)\.([0-9]+))?"

VERSION_RE: re.Pattern = re.compile(VERSION_PATTERN)

CRATES_IO_ARCHIVE_URL_PATTERN: str = (r"^https:\/\/static.crates.io\/crates\/" +
                                      LIBRARY_NAME_PATTERN + "/" +
                                      LIBRARY_NAME_PATTERN + "-" +
                                      "(.*?)" + ".crate")

CRATES_IO_ARCHIVE_URL_RE: re.Pattern = re.compile(CRATES_IO_ARCHIVE_URL_PATTERN)

DESCRIPTION_PATTERN: str = r"^description *= *(\".+\")"

DESCRIPTION_RE: re.Pattern = re.compile(DESCRIPTION_PATTERN)


class CratesUpdater(Updater):
    """Updater for crates.io packages."""

    UPSTREAM_REMOTE_NAME: str = "update_origin"
    download_url: str
    package: str
    package_dir: str
    temp_file: IO

    def is_supported_url(self) -> bool:
        match = CRATES_IO_ARCHIVE_URL_RE.match(self._old_identifier.value)
        if match is None:
            return False
        self.package = match.group(1)
        return True

    def setup_remote(self) -> None:
        url = "https://crates.io/api/v1/crates/" + self.package
        with urllib.request.urlopen(url) as request:
            data = json.loads(request.read().decode())
        homepage = data["crate"]["repository"]
        remotes = git_utils.list_remotes(self._proj_path)
        current_remote_url = None
        for name, url in remotes.items():
            if name == self.UPSTREAM_REMOTE_NAME:
                current_remote_url = url

        if current_remote_url is not None and current_remote_url != homepage:
            git_utils.remove_remote(self._proj_path, self.UPSTREAM_REMOTE_NAME)
            current_remote_url = None

        if current_remote_url is None:
            git_utils.add_remote(self._proj_path, self.UPSTREAM_REMOTE_NAME, homepage)

        branch = git_utils.detect_default_branch(self._proj_path,
                                                 self.UPSTREAM_REMOTE_NAME)
        git_utils.fetch(self._proj_path, self.UPSTREAM_REMOTE_NAME, branch)

    def _get_version_numbers(self, version: str) -> tuple[int, int, int]:
        match = VERSION_RE.match(version)
        if match is not None:
            return (
                int(match.group(1)),
                int(match.group(2)),
                int(match.group(3)),
            )
        return (0, 0, 0)

    def _is_newer_version(self, prev_version: str, prev_id: int,
                          check_version: str, check_id: int):
        """Return true if check_version+id is newer than prev_version+id."""
        return ((self._get_version_numbers(check_version), check_id) >
                (self._get_version_numbers(prev_version), prev_id))

    def _find_latest_non_test_version(self) -> None:
        url = f"https://crates.io/api/v1/crates/{self.package}/versions"
        with urllib.request.urlopen(url) as request:
            data = json.loads(request.read().decode())
        last_id = 0
        self._new_identifier.version = ""
        for v in data["versions"]:
            version = v["num"]
            if (not v["yanked"] and not ALPHA_BETA_RE.match(version) and
                self._is_newer_version(
                    self._new_identifier.version, last_id, version, int(v["id"]))):
                last_id = int(v["id"])
                self._new_identifier.version = version
                self.download_url = "https://crates.io" + v["dl_path"]

    def check(self) -> None:
        """Checks crates.io and returns whether a new version is available."""
        url = "https://crates.io/api/v1/crates/" + self.package
        with urllib.request.urlopen(url) as request:
            data = json.loads(request.read().decode())
            self._new_identifier.version = data["crate"]["max_version"]
        # Skip d.d.d-{alpha,beta}* versions
        if ALPHA_BETA_RE.match(self._new_identifier.version):
            print(f"Ignore alpha or beta release:{self.package}-{self._new_identifier.version}.")
            self._find_latest_non_test_version()
        else:
            url = url + "/" + self._new_identifier.version
            with urllib.request.urlopen(url) as request:
                data = json.loads(request.read().decode())
                self.download_url = "https://crates.io" + data["version"]["dl_path"]

    def set_new_version_to_old(self):
        super().refresh_without_upgrading()
        # A shortcut to use the static download path.
        self.download_url = f"https://static.crates.io/crates/{self.package}/" \
                            f"{self.package}-{self._new_identifier.version}.crate"

    def update(self) -> None:
        """Updates the package.

        Has to call check() before this function.
        """
        try:
            temporary_dir = archive_utils.download_and_extract(self.download_url)
            self.package_dir = archive_utils.find_archive_root(temporary_dir)
            self.temp_file = tempfile.NamedTemporaryFile()
            updater_utils.replace_package(self.package_dir, self._proj_path,
                                          self.temp_file.name)
            self.check_for_errors()
        finally:
            urllib.request.urlcleanup()

    def rollback(self) -> bool:
        # Only rollback if we have already swapped,
        # which we denote by writing to this file.
        if os.fstat(self.temp_file.fileno()).st_size > 0:
            tmp_dir = tempfile.TemporaryDirectory()
            shutil.move(self._proj_path, tmp_dir.name)
            shutil.move(self.package_dir, self._proj_path)
            shutil.move(Path(tmp_dir.name) / self.package, self.package_dir)
            return True
        return False

    def update_metadata(self, metadata: metadata_pb2.MetaData) -> metadata_pb2:
        """Updates METADATA content."""
        # copy only HOMEPAGE url, and then add new ARCHIVE url.
        updated_metadata = super().update_metadata(metadata)
        for identifier in updated_metadata.third_party.identifier:
            if identifier.version:
                identifier.value = f"https://static.crates.io/crates/" \
                                   f"{updated_metadata.name}/"\
                                   f"{updated_metadata.name}" \
                                   f"-{self.latest_identifier.version}.crate"
                break
        # copy description from Cargo.toml to METADATA
        cargo_toml = os.path.join(self.project_path, "Cargo.toml")
        description = self._get_cargo_description(cargo_toml)
        if description and description != updated_metadata.description:
            print("New METADATA description:", description)
            updated_metadata.description = description
        return updated_metadata

    def check_for_errors(self) -> None:
        # Check for .rej patches from failing to apply patches.
        # If this has too many false positives, we could either
        # check if the files are modified by patches or somehow
        # track which files existed before the patching.
        rejects = list(self._proj_path.glob('**/*.rej'))
        if len(rejects) > 0:
            print(f"Error: Found patch reject files: {str(rejects)}")
            self._has_errors = True

    def _toml2str(self, line: str) -> str:
        """Convert a quoted toml string to a Python str without quotes."""
        if line.startswith("\"\"\""):
            return ""  # cannot handle broken multi-line description
        # TOML string escapes: \b \t \n \f \r \" \\ (no unicode escape)
        line = line[1:-1].replace("\\\\", "\n").replace("\\b", "")
        line = line.replace("\\t", " ").replace("\\n", " ").replace("\\f", " ")
        line = line.replace("\\r", "").replace("\\\"", "\"").replace("\n", "\\")
        # replace a unicode quotation mark, used in the libloading crate
        return line.replace("’", "'").strip()

    def _get_cargo_description(self, cargo_toml: str) -> str:
        """Return the description in Cargo.toml or empty string."""
        if os.path.isfile(cargo_toml) and os.access(cargo_toml, os.R_OK):
            with open(cargo_toml, "r", encoding="utf-8") as toml_file:
                for line in toml_file:
                    match = DESCRIPTION_RE.match(line)
                    if match:
                        return self._toml2str(match.group(1))
        return ""
