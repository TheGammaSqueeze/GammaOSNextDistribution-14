#!/usr/bin/python3 -B

# Copyright 2021 The Android Open Source Project
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
"""ojluni_modify_expectation is a command-line tool for modifying the EXPECTED_UPSTREAM file."""

import argparse
import sys

# pylint: disable=g-importing-member
# pylint: disable=g-multiple-import
from typing import (
    Sequence,
    List,
)

from common_util import (
    ExpectedUpstreamEntry,
    ExpectedUpstreamFile,
    LIBCORE_DIR,
    OpenjdkFinder,
    OjluniFinder,
)

# Import git only after common_util because common_util will
# produce informative error
from git import (Commit, Repo)
from gitdb.exc import BadName

LIBCORE_REPO = Repo(LIBCORE_DIR.as_posix())

AUTOCOMPLETE_TAGS = [
    'jdk7u/jdk7u40-b60',
    'jdk8u/jdk8u121-b13',
    'jdk8u/jdk8u60-b31',
    'jdk9/jdk-9+181',
    'jdk11u/jdk-11.0.22-ga',
    'jdk17u/jdk-17.0.10-ga',
    'jdk21u/jdk-21.0.2-ga',
]


def error_and_exit(msg: str) -> None:
  print(f'Error: {msg}', file=sys.stderr)
  sys.exit(1)


def get_commit_or_exit(git_ref: str) -> Commit:
  try:
    return LIBCORE_REPO.commit(git_ref)
  except BadName as e:
    error_and_exit(f'{e}')


def autocomplete_tag_or_commit(str_tag_or_commit: str) -> List[str]:
  """Returns a list of tags / commits matching the given partial string."""
  if str_tag_or_commit is None:
    str_tag_or_commit = ''
  return list(
      filter(lambda tag: tag.startswith(str_tag_or_commit), AUTOCOMPLETE_TAGS))


COMMAND_ACTIONS = ['add', 'modify', 'remove', 'sort']


def autocomplete_action(partial_str: str) -> None:
  result_list = list(
      filter(lambda action: action.startswith(partial_str), COMMAND_ACTIONS))
  print('\n'.join(result_list))
  exit(0)


def main(argv: Sequence[str]) -> None:
  is_auto_complete = len(argv) >= 2 and argv[0] == '--autocomplete'
  # argparse can't help autocomplete subcommand. We implement this without
  # argparse here.
  if is_auto_complete and argv[1] == '1':
    action = argv[2] if len(argv) >= 3 else ''
    autocomplete_action(action)

  # If it's for autocompletion, then all arguments are optional.
  parser_nargs = '?' if is_auto_complete else 1

  main_parser = argparse.ArgumentParser(
      description='A command line tool modifying the EXPECTED_UPSTREAM file.')
  # --autocomplete <int> is an 'int' argument because the value represents
  # the raw index of the argument to be autocompleted received in the Shell,
  # and this number is not always the same as the number of arguments
  # received here, i.e. len(argv), for examples of empty value in the
  # argument or autocompleting the middle argument, not last argument.
  main_parser.add_argument(
      '--autocomplete', type=int, help='flag when tabbing in command line')
  subparsers = main_parser.add_subparsers(
      dest='command', help='sub-command help')

  add_parser = subparsers.add_parser(
      'add', help='Add a new entry into the EXPECTED_UPSTREAM '
      'file')
  add_parser.add_argument(
      'tag_or_commit',
      nargs=parser_nargs,
      help='A git tag or commit in the upstream-openjdkXXX branch')
  add_parser.add_argument(
      'class_or_source_file',
      nargs=parser_nargs,
      help='Fully qualified class name or upstream source path')
  add_parser.add_argument(
      'ojluni_path', nargs='?', help='Destination path in ojluni/')

  modify_parser = subparsers.add_parser(
      'modify', help='Modify an entry in the EXPECTED_UPSTREAM file')
  modify_parser.add_argument(
      'class_or_ojluni_path', nargs=parser_nargs, help='File path in ojluni/')
  modify_parser.add_argument(
      'tag_or_commit',
      nargs=parser_nargs,
      help='A git tag or commit in the upstream-openjdkXXX branch')
  modify_parser.add_argument(
      'source_file', nargs='?', help='A upstream source path')

  modify_parser = subparsers.add_parser(
      'remove', help='Remove an entry in the EXPECTED_UPSTREAM file')
  modify_parser.add_argument(
      'class_or_ojluni_path', nargs=parser_nargs, help='File path in ojluni/')

  subparsers.add_parser(
      'sort', help='Sort the entries in the EXPECTED_UPSTREAM file')

  args = main_parser.parse_args(argv)

  expected_upstream_file = ExpectedUpstreamFile()
  expected_entries = expected_upstream_file.read_all_entries()

  if is_auto_complete:
    no_args = args.autocomplete

    autocomp_result = []
    if args.command == 'modify' or args.command == 'remove':
      if no_args == 2:
        input_class_or_ojluni_path = args.class_or_ojluni_path
        if input_class_or_ojluni_path is None:
          input_class_or_ojluni_path = ''

        existing_dst_paths = list(
            map(lambda entry: entry.dst_path, expected_entries))
        ojluni_finder: OjluniFinder = OjluniFinder(existing_dst_paths)
        # Case 1: Treat the input as file path
        autocomp_result += ojluni_finder.match_path_prefix(
            input_class_or_ojluni_path)

        # Case 2: Treat the input as java package / class name
        autocomp_result += ojluni_finder.match_classname_prefix(
            input_class_or_ojluni_path)
      elif no_args == 3 and args.command == 'modify':
        autocomp_result += autocomplete_tag_or_commit(args.tag_or_commit)
    elif args.command == 'add':
      if no_args == 2:
        autocomp_result += autocomplete_tag_or_commit(args.tag_or_commit)
      elif no_args == 3:
        commit = get_commit_or_exit(args.tag_or_commit)
        class_or_src_path = args.class_or_source_file
        if class_or_src_path is None:
          class_or_src_path = ''

        openjdk_finder: OpenjdkFinder = OpenjdkFinder(commit)

        matches = openjdk_finder.match_path_prefix(
            class_or_src_path)

        matches += openjdk_finder.match_classname_prefix(
            class_or_src_path)

        existing_dst_paths = set(map(lambda e: e.dst_path, expected_entries))

        # Translate the class names or source paths to dst paths and exclude
        # such matches from the auto-completion result
        def source_not_exists(src_path_or_class: str) -> bool:
          nonlocal existing_dst_paths, openjdk_finder
          t_src_path = openjdk_finder.find_src_path_from_classname(
              src_path_or_class)
          if t_src_path is None:
            # t_src_path is a java package. It must not in existing_dst_paths.
            return True
          t_dst_path = OpenjdkFinder.translate_src_path_to_ojluni_path(
              t_src_path)
          return t_dst_path not in existing_dst_paths

        autocomp_result += list(filter(source_not_exists, matches))

    print('\n'.join(autocomp_result))
    exit(0)

  if args.command == 'modify':
    dst_class_or_file = args.class_or_ojluni_path[0]
    dst_path = OjluniFinder.translate_from_class_name_to_ojluni_path(
        dst_class_or_file)
    matches = list(filter(lambda e: dst_path == e.dst_path, expected_entries))
    if not matches:
      error_and_exit(f'{dst_path} is not found in the EXPECTED_UPSTREAM.')
    entry: ExpectedUpstreamEntry = matches[0]
    str_tag_or_commit = args.tag_or_commit[0]
    is_src_given = args.source_file is not None
    src_path = args.source_file if is_src_given else entry.src_path
    commit = get_commit_or_exit(str_tag_or_commit)
    openjdk_finder: OpenjdkFinder = OpenjdkFinder(commit)
    if openjdk_finder.has_file(src_path):
      pass
    elif not is_src_given:
      guessed_src_path = openjdk_finder.find_src_path_from_ojluni_path(dst_path)
      if guessed_src_path is None:
        error_and_exit('[source_file] argument is required.')
      src_path = guessed_src_path
    else:
      error_and_exit(f'{src_path} is not found in the {str_tag_or_commit}')
    entry.git_ref = str_tag_or_commit
    entry.src_path = src_path
    expected_upstream_file.write_all_entries(expected_entries)
    print(f'Modified the entry {entry}')
  elif args.command == 'remove':
    dst_class_or_file = args.class_or_ojluni_path[0]
    dst_path = OjluniFinder.translate_from_class_name_to_ojluni_path(
        dst_class_or_file)
    matches = list(filter(lambda e: dst_path == e.dst_path, expected_entries))
    if not matches:
      error_and_exit(f'{dst_path} is not found in the EXPECTED_UPSTREAM.')

    entry: ExpectedUpstreamEntry = matches[0]
    expected_entries.remove(entry)
    expected_upstream_file.write_all_entries(expected_entries)
    print(f'Removed the entry {entry}')
  elif args.command == 'add':
    class_or_src_path = args.class_or_source_file[0]
    str_tag_or_commit = args.tag_or_commit[0]
    commit = get_commit_or_exit(str_tag_or_commit)
    openjdk_finder = OpenjdkFinder(commit)
    src_path = openjdk_finder.find_src_path_from_classname(class_or_src_path)
    if src_path is None:
      search_paths = openjdk_finder.get_search_paths()
      error_and_exit(f'{class_or_src_path} is not found in {commit}. '
                     f'The search paths are:\n{search_paths}')
    ojluni_path = args.ojluni_path
    # Guess the source path if it's not given in the argument
    if ojluni_path is None:
      ojluni_path = OpenjdkFinder.translate_src_path_to_ojluni_path(src_path)
    if ojluni_path is None:
      error_and_exit('The ojluni destination path is not given.')

    matches = list(
        filter(lambda e: ojluni_path == e.dst_path, expected_entries))
    if matches:
      error_and_exit(f"Can't add the file {ojluni_path} because "
                     f'{class_or_src_path} exists in the EXPECTED_UPSTREAM')

    new_entry = ExpectedUpstreamEntry(ojluni_path, str_tag_or_commit, src_path)
    expected_upstream_file.write_new_entry(new_entry, expected_entries)
  elif args.command == 'sort':
    expected_upstream_file.sort_and_write_all_entries(expected_entries)
  else:
    error_and_exit(f'Unknown subcommand: {args.command}')


if __name__ == '__main__':
  main(sys.argv[1:])
