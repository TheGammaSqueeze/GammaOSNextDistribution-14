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
"""Helper functions for updaters."""

from collections.abc import Sequence
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import List, Tuple, Type

from base_updater import Updater
import fileutils
# pylint: disable=import-error
import metadata_pb2  # type: ignore


def create_updater(metadata: metadata_pb2.MetaData, proj_path: Path,
                   updaters: List[Type[Updater]]) -> Updater:
    """Creates corresponding updater object for a project.

    Args:
      metadata: Parsed proto for METADATA file.
      proj_path: Absolute path for the project.

    Returns:
      An updater object.

    Raises:
      ValueError: Occurred when there's no updater for all urls.
    """
    for identifier in metadata.third_party.identifier:
        if identifier.type.lower() != 'homepage':
            for updater_cls in updaters:
                updater = updater_cls(proj_path, identifier, metadata.third_party.version)
                if updater.is_supported_url():
                    return updater

    raise ValueError('No supported URL.')


def replace_package(source_dir, target_dir, temp_file=None) -> None:
    """Invokes a shell script to prepare and update a project.

    Args:
      source_dir: Path to the new downloaded and extracted package.
      target_dir: The path to the project in Android source tree.
    """

    print(f'Updating {target_dir} using {source_dir}.')
    script_path = os.path.join(os.path.dirname(sys.argv[0]),
                               'update_package.sh')
    subprocess.check_call(['bash', script_path, source_dir, target_dir,
                           "" if temp_file is None else temp_file])


def run_post_update(source_dir: Path, target_dir: Path) -> None:
    """
      source_dir: Path to the new downloaded and extracted package.
      target_dir: The path to the project in Android source tree.
    """
    post_update_path = os.path.join(source_dir, 'post_update.sh')
    if os.path.isfile(post_update_path):
        cmd: Sequence[str | Path] = ['bash', post_update_path, source_dir, target_dir]
        print(f'Running {post_update_path}')
        subprocess.check_call(cmd)


VERSION_SPLITTER_PATTERN: str = r'[\.\-_]'
VERSION_PATTERN: str = (r'^(?P<prefix>[^\d]*)' + r'(?P<version>\d+(' +
                        VERSION_SPLITTER_PATTERN + r'\d+)*)' +
                        r'(?P<suffix>.*)$')
VERSION_RE: re.Pattern = re.compile(VERSION_PATTERN)
VERSION_SPLITTER_RE: re.Pattern = re.compile(VERSION_SPLITTER_PATTERN)

ParsedVersion = Tuple[List[int], str, str]


def _parse_version(version: str) -> ParsedVersion:
    match = VERSION_RE.match(version)
    if match is None:
        raise ValueError('Invalid version.')
    try:
        prefix, version, suffix = match.group('prefix', 'version', 'suffix')
        versions = [int(v) for v in VERSION_SPLITTER_RE.split(version)]
        return versions, str(prefix), str(suffix)
    except IndexError:
        # pylint: disable=raise-missing-from
        raise ValueError('Invalid version.')


def _match_and_get_version(old_ver: ParsedVersion,
                           version: str) -> Tuple[bool, bool, List[int]]:
    try:
        new_ver = _parse_version(version)
    except ValueError:
        return False, False, []

    right_format = new_ver[1:] == old_ver[1:]
    right_length = len(new_ver[0]) == len(old_ver[0])

    return right_format, right_length, new_ver[0]


def get_latest_version(current_version: str, version_list: List[str]) -> str:
    """Gets the latest version name from a list of versions.

    The new version must have the same prefix and suffix with old version.
    If no matched version is newer, current version name will be returned.
    """
    parsed_current_ver = _parse_version(current_version)

    latest = max(
        version_list,
        key=lambda ver: _match_and_get_version(parsed_current_ver, ver),
        default=None)
    if not latest:
        raise ValueError('No matching version.')
    return latest


def build(proj_path: Path) -> None:
    tree = fileutils.find_tree_containing(proj_path)
    cmd = [
        str(tree / 'build/soong/soong_ui.bash'),
        "--build-mode",
        "--modules-in-a-dir-no-deps",
        f"--dir={str(proj_path)}",
    ]
    print('Building...')
    subprocess.run(cmd, check=True, text=True)
