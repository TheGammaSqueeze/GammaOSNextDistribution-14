#
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
"""A commandline tool to check and update packages in external/

Example usage:
updater.sh checkall
updater.sh update kotlinc
updater.sh update --refresh --keep_date rust/crates/libc
"""

import argparse
from collections.abc import Iterable
import enum
import json
import logging
import os
import subprocess
import sys
import textwrap
import time
from typing import Dict, Iterator, List, Union, Tuple, Type
from pathlib import Path

from base_updater import Updater
from crates_updater import CratesUpdater
from git_updater import GitUpdater
from github_archive_updater import GithubArchiveUpdater
import fileutils
import git_utils
# pylint: disable=import-error
import metadata_pb2  # type: ignore
import updater_utils

UPDATERS: List[Type[Updater]] = [
    CratesUpdater,
    GithubArchiveUpdater,
    GitUpdater,
]

TMP_BRANCH_NAME = 'tmp_auto_upgrade'
USE_COLOR = sys.stdout.isatty()


@enum.unique
class Color(enum.Enum):
    """Colors for output to console."""
    FRESH = '\x1b[32m'
    STALE = '\x1b[31;1m'
    ERROR = '\x1b[31m'


END_COLOR = '\033[0m'


def color_string(string: str, color: Color) -> str:
    """Changes the color of a string when print to terminal."""
    if not USE_COLOR:
        return string
    return color.value + string + END_COLOR


def build_updater(proj_path: Path) -> Tuple[Updater, metadata_pb2.MetaData]:
    """Build updater for a project specified by proj_path.

    Reads and parses METADATA file. And builds updater based on the information.

    Args:
      proj_path: Absolute or relative path to the project.

    Returns:
      The updater object built. None if there's any error.
    """

    proj_path = fileutils.get_absolute_project_path(proj_path)
    metadata = fileutils.read_metadata(proj_path)
    metadata = fileutils.convert_url_to_identifier(metadata)
    updater = updater_utils.create_updater(metadata, proj_path, UPDATERS)
    return updater, metadata


def _do_update(args: argparse.Namespace, updater: Updater,
               metadata: metadata_pb2.MetaData) -> None:
    full_path = updater.project_path

    if not args.keep_local_changes:
        git_utils.detach_to_android_head(full_path)
        if TMP_BRANCH_NAME in git_utils.list_local_branches(full_path):
            git_utils.delete_branch(full_path, TMP_BRANCH_NAME)
            git_utils.reset_hard(full_path)
            git_utils.clean(full_path)
        git_utils.start_branch(full_path, TMP_BRANCH_NAME)

    try:
        updater.update()

        updated_metadata = updater.update_metadata(metadata)
        fileutils.write_metadata(full_path, updated_metadata, args.keep_date)
        git_utils.add_file(full_path, 'METADATA')

        try:
            rel_proj_path = str(fileutils.get_relative_project_path(full_path))
        except ValueError:
            # Absolute paths to other trees will not be relative to our tree. There are
            # not portable instructions for upgrading that project, since the path will
            # differ between machines (or checkouts).
            rel_proj_path = "<absolute path to project>"
        msg = textwrap.dedent(f"""\
        Upgrade {metadata.name} to {updater.latest_version}

        This project was upgraded with external_updater.
        Usage: tools/external_updater/updater.sh update external/{rel_proj_path}
        For more info, check https://cs.android.com/android/platform/superproject/+/main:tools/external_updater/README.md

        Test: TreeHugger""")
        git_utils.remove_gitmodules(full_path)
        git_utils.add_file(full_path, '*')
        git_utils.commit(full_path, msg, args.no_verify)

        if not args.skip_post_update:
            updater_utils.run_post_update(full_path, full_path)
            git_utils.add_file(full_path, '*')
            git_utils.commit_amend(full_path)

        if args.build:
            try:
                updater_utils.build(full_path)
            except subprocess.CalledProcessError:
                logging.exception("Build failed, aborting upload")
                return
    except Exception as err:
        if updater.rollback():
            print('Rolled back.')
        raise err

    if not args.no_upload:
        git_utils.push(full_path, args.remote_name, updater.has_errors)


def has_new_version(updater: Updater) -> bool:
    """Checks if a newer version of the project is available."""
    if updater.current_version != updater.latest_version:
        return True
    return False


def print_project_status(updater: Updater) -> None:
    """Prints the current status of the project on console."""

    current_version = updater.current_version
    latest_version = updater.latest_version
    alternative_latest_version = updater.alternative_latest_version

    print(f'Current version: {current_version}')
    print(f'Latest version: {latest_version}')
    if alternative_latest_version is not None:
        print(f'Alternative latest version: {alternative_latest_version}')
    if has_new_version(updater):
        print(color_string('Out of date!', Color.STALE))
    else:
        print(color_string('Up to date.', Color.FRESH))


def find_ver_types(current_version: str) -> Tuple[str, str]:
    if git_utils.is_commit(current_version):
        alternative_ver_type = 'tag'
        latest_ver_type = 'sha'
    else:
        alternative_ver_type = 'sha'
        latest_ver_type = 'tag'
    return latest_ver_type, alternative_ver_type


def use_alternative_version(updater: Updater) -> bool:
    """This function only runs when there is an alternative version available."""

    latest_ver_type, alternative_ver_type = find_ver_types(updater.current_version)
    latest_version = updater.latest_version
    alternative_version = updater.alternative_latest_version
    new_version_available = has_new_version(updater)

    out_of_date_question = f'Would you like to upgrade to {alternative_ver_type} {alternative_version} instead of {latest_ver_type} {latest_version}? (yes/no)\n'
    up_to_date_question = f'Would you like to upgrade to {alternative_ver_type} {alternative_version}? (yes/no)\n'
    recom_message = color_string(f'We recommend upgrading to {alternative_ver_type} {alternative_version} instead. ', Color.FRESH)
    not_recom_message = color_string(f'We DO NOT recommend upgrading to {alternative_ver_type} {alternative_version}. ', Color.STALE)

    # If alternative_version is not None, there are ONLY three possible
    # scenarios:
    # Scenario 1, out of date, we recommend switching to tag:
    # Current version: sha1
    # Latest version: sha2
    # Alternative latest version: tag

    # Scenario 2, out of date, we DO NOT recommend switching to sha.
    # Current version: tag1
    # Latest version: tag2
    # Alternative latest version: sha

    # Scenario 3, up to date, we DO NOT recommend switching to sha.
    # Current version: tag1
    # Latest version: tag1
    # Alternative latest version: sha

    if alternative_ver_type == 'tag':
        warning = out_of_date_question + recom_message
    else:
        if not new_version_available:
            warning = up_to_date_question + not_recom_message
        else:
            warning = out_of_date_question + not_recom_message

    answer = input(warning)
    if "yes".startswith(answer.lower()):
        return True
    elif answer.lower().startswith("no"):
        return False
    # If user types something that is not "yes" or "no" or something similar, abort.
    else:
        raise ValueError(f"Invalid input: {answer}")



def check_and_update(args: argparse.Namespace,
                     proj_path: Path,
                     update_lib=False) -> Union[Updater, str]:
    """Checks updates for a project.

    Args:
      args: commandline arguments
      proj_path: Absolute or relative path to the project.
      update_lib: If false, will only check for new version, but not update.
    """

    try:
        canonical_path = fileutils.canonicalize_project_path(proj_path)
        print(f'Checking {canonical_path}...')
        updater, metadata = build_updater(proj_path)
        updater.check()

        alternative_version = updater.alternative_latest_version
        new_version_available = has_new_version(updater)
        print_project_status(updater)

        if update_lib:
            if args.refresh:
                print('Refreshing the current version')
                updater.refresh_without_upgrading()

            answer = False
            if alternative_version is not None:
                answer = use_alternative_version(updater)
                if answer:
                    updater.set_new_version(alternative_version)
            if new_version_available or args.force or args.refresh or answer:
                _do_update(args, updater, metadata)
        return updater
    # pylint: disable=broad-except
    except Exception as err:
        logging.exception("Failed to check or update %s", proj_path)
        return str(err)


def check_and_update_path(args: argparse.Namespace, paths: Iterable[Path],
                          update_lib: bool,
                          delay: int) -> Dict[str, Dict[str, str]]:
    results = {}
    for path in paths:
        res = {}
        updater = check_and_update(args, path, update_lib)
        if isinstance(updater, str):
            res['error'] = updater
        else:
            res['current'] = updater.current_version
            res['latest'] = updater.latest_version
        results[str(fileutils.canonicalize_project_path(path))] = res
        time.sleep(delay)
    return results


def _list_all_metadata() -> Iterator[str]:
    for path, dirs, files in os.walk(fileutils.external_path()):
        if fileutils.METADATA_FILENAME in files:
            # Skip sub directories.
            dirs[:] = []
            yield path
        dirs.sort(key=lambda d: d.lower())


def write_json(json_file: str, results: Dict[str, Dict[str, str]]) -> None:
    """Output a JSON report."""
    with Path(json_file).open('w', encoding='utf-8') as res_file:
        json.dump(results, res_file, sort_keys=True, indent=4)


def validate(args: argparse.Namespace) -> None:
    """Handler for validate command."""
    paths = fileutils.resolve_command_line_paths(args.paths)
    try:
        canonical_path = fileutils.canonicalize_project_path(paths[0])
        print(f'Validating {canonical_path}')
        updater, _ = build_updater(paths[0])
        print(updater.validate())
    except Exception:  # pylint: disable=broad-exception-caught
        logging.exception("Failed to check or update %s", paths)


def check(args: argparse.Namespace) -> None:
    """Handler for check command."""
    if args.all:
        paths = [Path(p) for p in _list_all_metadata()]
    else:
        paths = fileutils.resolve_command_line_paths(args.paths)
    results = check_and_update_path(args, paths, False, args.delay)

    if args.json_output is not None:
        write_json(args.json_output, results)


def update(args: argparse.Namespace) -> None:
    """Handler for update command."""
    all_paths = fileutils.resolve_command_line_paths(args.paths)
    # Remove excluded paths.
    excludes = set() if args.exclude is None else set(args.exclude)
    filtered_paths = [path for path in all_paths
                      if not path.name in excludes]
    # Now we can update each path.
    results = check_and_update_path(args, filtered_paths, True, 0)

    if args.json_output is not None:
        write_json(args.json_output, results)


def parse_args() -> argparse.Namespace:
    """Parses commandline arguments."""

    parser = argparse.ArgumentParser(
        description='Check updates for third party projects in external/.')
    subparsers = parser.add_subparsers(dest='cmd')
    subparsers.required = True

    diff_parser = subparsers.add_parser('validate',
                                        help='Check if aosp version is what it claims to be.')
    diff_parser.add_argument(
        'paths',
        nargs='*',
        help='Paths of the project. '
             'Relative paths will be resolved from external/.')
    diff_parser.set_defaults(func=validate)

    # Creates parser for check command.
    check_parser = subparsers.add_parser('check',
                                         help='Check update for one project.')
    check_parser.add_argument(
        'paths',
        nargs='*',
        help='Paths of the project. '
        'Relative paths will be resolved from external/.')
    check_parser.add_argument('--json-output',
                              help='Path of a json file to write result to.')
    check_parser.add_argument(
        '--all',
        action='store_true',
        help='If set, check updates for all supported projects.')
    check_parser.add_argument(
        '--delay',
        default=0,
        type=int,
        help='Time in seconds to wait between checking two projects.')
    check_parser.set_defaults(func=check)

    # Creates parser for update command.
    update_parser = subparsers.add_parser('update', help='Update one project.')
    update_parser.add_argument(
        'paths',
        nargs='*',
        help='Paths of the project as globs. '
        'Relative paths will be resolved from external/.')
    update_parser.add_argument('--json-output',
                               help='Path of a json file to write result to.')
    update_parser.add_argument(
        '--force',
        help='Run update even if there\'s no new version.',
        action='store_true')
    update_parser.add_argument(
        '--refresh',
        help='Run update and refresh to the current version.',
        action='store_true')
    update_parser.add_argument(
        '--keep-date',
        help='Run update and do not change date in METADATA.',
        action='store_true')
    update_parser.add_argument('--no-upload',
                               action='store_true',
                               help='Does not upload to Gerrit after upgrade')
    update_parser.add_argument('--keep-local-changes',
                               action='store_true',
                               help='Updates the current branch')
    update_parser.add_argument('--skip-post-update',
                               action='store_true',
                               help='Skip post_update script')
    update_parser.add_argument('--no-build',
                               action='store_false',
                               dest='build',
                               help='Skip building')
    update_parser.add_argument('--no-verify',
                               action='store_true',
                               help='Pass --no-verify to git commit')
    update_parser.add_argument('--remote-name',
                               default='aosp',
                               required=False,
                               help='Upstream remote name.')
    update_parser.add_argument('--exclude',
                               action='append',
                               help='Names of projects to exclude. '
                               'These are just the final part of the path '
                               'with no directories.')
    update_parser.set_defaults(func=update)

    return parser.parse_args()


def main() -> None:
    """The main entry."""

    args = parse_args()
    args.func(args)


if __name__ == '__main__':
    main()
