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
"""Base class for all updaters."""

from pathlib import Path

import git_utils
import fileutils
# pylint: disable=import-error
import metadata_pb2  # type: ignore


class Updater:
    """Base Updater that defines methods common for all updaters."""
    def __init__(self, proj_path: Path, old_identifier: metadata_pb2.Identifier,
                 old_ver: str) -> None:
        self._proj_path = fileutils.get_absolute_project_path(proj_path)
        self._old_identifier = old_identifier
        self._old_identifier.version = old_identifier.version if old_identifier.version else old_ver

        self._new_identifier = metadata_pb2.Identifier()
        self._new_identifier.CopyFrom(old_identifier)

        self._alternative_new_ver: str | None = None

        self._has_errors = False

    def is_supported_url(self) -> bool:
        """Returns whether the url is supported."""
        raise NotImplementedError()

    def setup_remote(self) -> None:
        raise NotImplementedError()

    def validate(self) -> str:
        """Checks whether aosp version is what it claims to be."""
        self.setup_remote()
        return git_utils.diff(self._proj_path, self._old_identifier.version)

    def check(self) -> None:
        """Checks whether a new version is available."""
        raise NotImplementedError()

    def update(self) -> None:
        """Updates the package.

        Has to call check() before this function.
        """
        raise NotImplementedError()

    def rollback(self) -> bool:
        """Rolls the current update back.

        This is an optional operation.  Returns whether the rollback succeeded.
        """
        return False

    def update_metadata(self, metadata: metadata_pb2.MetaData) -> metadata_pb2:
        """Rewrites the metadata file."""
        updated_metadata = metadata_pb2.MetaData()
        updated_metadata.CopyFrom(metadata)
        updated_metadata.third_party.ClearField("version")
        for identifier in updated_metadata.third_party.identifier:
            if identifier == self.current_identifier:
                identifier.CopyFrom(self.latest_identifier)
        return updated_metadata

    @property
    def project_path(self) -> Path:
        """Gets absolute path to the project."""
        return self._proj_path

    @property
    def current_version(self) -> str:
        """Gets the current version."""
        return self._old_identifier.version

    @property
    def current_identifier(self) -> metadata_pb2.Identifier:
        """Gets the current identifier."""
        return self._old_identifier

    @property
    def latest_version(self) -> str:
        """Gets latest version."""
        return self._new_identifier.version

    @property
    def latest_identifier(self) -> metadata_pb2.Identifier:
        """Gets identifier for latest version."""
        return self._new_identifier

    @property
    def has_errors(self) -> bool:
        """Gets whether this update had an error."""
        return self._has_errors

    @property
    def alternative_latest_version(self) -> str | None:
        """Gets alternative latest version."""
        return self._alternative_new_ver

    def refresh_without_upgrading(self) -> None:
        """Uses current version and url as the latest to refresh project."""
        self._new_identifier.version = self._old_identifier.version
        self._new_identifier.value = self._old_identifier.value

    def set_new_version(self, version: str) -> None:
        """Uses the passed version as the latest to upgrade project."""
        self._new_identifier.version = version
