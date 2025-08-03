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
"""Tool functions to deal with files."""

import datetime
import enum
import os
from pathlib import Path
import textwrap

# pylint: disable=import-error
from google.protobuf import text_format  # type: ignore

# pylint: disable=import-error
import metadata_pb2  # type: ignore


METADATA_FILENAME = 'METADATA'


@enum.unique
class IdentifierType(enum.Enum):
    """A subset of different Identifier types"""
    GIT = 'Git'
    SVN = 'SVN'
    HG = 'Hg'
    DARCS = 'Darcs'
    ARCHIVE = 'Archive'
    OTHER = 'Other'


def find_tree_containing(project: Path) -> Path:
    """Returns the path to the repo tree parent of the given project.

    The parent tree is found by searching up the directory tree until a
    directory is found that contains a .repo directory. Other methods of
    finding this directory won't necessarily work:

    * Using ANDROID_BUILD_TOP might find the wrong tree (if external_updater
    is used to manage a project that is not in AOSP, as it does for CMake,
    rr, and a few others), since ANDROID_BUILD_TOP will be the one that built
    external_updater rather than the given project.
    * Paths relative to __file__ are no good because we'll run from a "built"
    PAR somewhere in the soong out directory, or possibly somewhere more
    arbitrary when run from CI.
    * Paths relative to the CWD require external_updater to be run from a
    predictable location. Doing so prevents the user from using relative
    paths (and tab complete) from directories other than the expected location.

    The result for one project should not be reused for other projects,
    as it's possible that the user has provided project paths from multiple
    trees.
    """
    if (project / ".repo").exists():
        return project
    if project.parent == project:
        raise FileNotFoundError(
            f"Could not find a .repo directory in any parent of {project}"
        )
    return find_tree_containing(project.parent)


def external_path() -> Path:
    """Returns the path to //external.

    We cannot use the relative path from this file to find the top of the
    tree because this will often be run in a "compiled" form from an
    arbitrary location in the out directory. We can't fully rely on
    ANDROID_BUILD_TOP because not all contexts will have run envsetup/lunch
    either. We use ANDROID_BUILD_TOP whenever it is set, but if it is not set
    we instead rely on the convention that the CWD is the root of the tree (
    updater.sh will cd there before executing).

    There is one other context where this function cannot succeed: CI. Tests
    run in CI do not have a source tree to find, so calling this function in
    that context will fail.
    """
    android_top = Path(os.environ.get("ANDROID_BUILD_TOP", os.getcwd()))
    top = android_top / 'external'

    if not top.exists():
        raise RuntimeError(
            f"{top} does not exist. This program must be run from the "
            f"root of an Android tree (CWD is {os.getcwd()})."
        )
    return top


def get_absolute_project_path(proj_path: Path) -> Path:
    """Gets absolute path of a project.

    Path resolution starts from external/.
    """
    if proj_path.is_absolute():
        return proj_path
    return external_path() / proj_path


def resolve_command_line_paths(paths: list[str]) -> list[Path]:
    """Resolves project paths provided by the command line.

    Both relative and absolute paths are resolved to fully qualified paths
    and returned. If any path does not exist relative to the CWD, a message
    will be printed and that path will be pruned from the list.
    """
    resolved: list[Path] = []
    for path_str in paths:
        path = Path(path_str)
        if not path.exists():
            print(f"Provided path {path} ({path.resolve()}) does not exist. Skipping.")
        else:
            resolved.append(path.resolve())
    return resolved


def get_metadata_path(proj_path: Path) -> Path:
    """Gets the absolute path of METADATA for a project."""
    return get_absolute_project_path(proj_path) / METADATA_FILENAME


def get_relative_project_path(proj_path: Path) -> Path:
    """Gets the relative path of a project starting from external/."""
    return get_absolute_project_path(proj_path).relative_to(external_path())


def canonicalize_project_path(proj_path: Path) -> Path:
    """Returns the canonical representation of the project path.

    For paths that are in the same tree as external_updater (the common
    case), the canonical path is the path of the project relative to //external.

    For paths that are in a different tree (an uncommon case used for
    updating projects in other builds such as the NDK), the canonical path is
    the absolute path.
    """
    try:
        return get_relative_project_path(proj_path)
    except ValueError as ex:
        # A less common use case, but the path might be to a non-local tree,
        # in which case the path will not be relative to our tree. This
        # happens when using external_updater in another project like the NDK
        # or rr.
        if proj_path.is_absolute():
            return proj_path

        # Not relative to //external, and not an absolute path. This case
        # hasn't existed before, so it has no canonical form.
        raise ValueError(
            f"{proj_path} must be either an absolute path or relative to {external_path()}"
        ) from ex


def read_metadata(proj_path: Path) -> metadata_pb2.MetaData:
    """Reads and parses METADATA file for a project.

    Args:
      proj_path: Path to the project.

    Returns:
      Parsed MetaData proto.

    Raises:
      text_format.ParseError: Occurred when the METADATA file is invalid.
      FileNotFoundError: Occurred when METADATA file is not found.
    """

    with get_metadata_path(proj_path).open('r') as metadata_file:
        metadata = metadata_file.read()
        return text_format.Parse(metadata, metadata_pb2.MetaData())

def convert_url_to_identifier(metadata: metadata_pb2.MetaData) -> metadata_pb2.MetaData:
    """Converts the old style METADATA to the new style"""
    for url in metadata.third_party.url:
        if url.type == metadata_pb2.URL.HOMEPAGE:
            metadata.third_party.homepage = url.value
        else:
            identifier = metadata_pb2.Identifier()
            identifier.type = IdentifierType[metadata_pb2.URL.Type.Name(url.type)].value
            identifier.value = url.value
            identifier.version = metadata.third_party.version
            metadata.third_party.ClearField("version")
            metadata.third_party.identifier.append(identifier)
    metadata.third_party.ClearField("url")
    return metadata


def write_metadata(proj_path: Path, metadata: metadata_pb2.MetaData, keep_date: bool) -> None:
    """Writes updated METADATA file for a project.

    This function updates last_upgrade_date in metadata and write to the project
    directory.

    Args:
      proj_path: Path to the project.
      metadata: The MetaData proto to write.
      keep_date: Do not change date.
    """

    if not keep_date:
        date = metadata.third_party.last_upgrade_date
        now = datetime.datetime.now()
        date.year = now.year
        date.month = now.month
        date.day = now.day
    try:
        rel_proj_path = str(get_relative_project_path(proj_path))
    except ValueError:
        # Absolute paths to other trees will not be relative to our tree.
        # There are no portable instructions for upgrading that project,
        # since the path will differ between machines (or checkouts).
        rel_proj_path = "<absolute path to project>"
    usage_hint = textwrap.dedent(f"""\
    # This project was upgraded with external_updater.
    # Usage: tools/external_updater/updater.sh update external/{rel_proj_path}
    # For more info, check https://cs.android.com/android/platform/superproject/+/main:tools/external_updater/README.md

    """)
    text_metadata = usage_hint + text_format.MessageToString(metadata)
    with get_metadata_path(proj_path).open('w') as metadata_file:
        if metadata.third_party.license_type == metadata_pb2.LicenseType.BY_EXCEPTION_ONLY:
            metadata_file.write(textwrap.dedent("""\
            # THIS PACKAGE HAS SPECIAL LICENSING CONDITIONS. PLEASE
            # CONSULT THE OWNERS AND opensource-licensing@google.com BEFORE
            # DEPENDING ON IT IN YOUR PROJECT.

            """))
        metadata_file.write(text_metadata)
