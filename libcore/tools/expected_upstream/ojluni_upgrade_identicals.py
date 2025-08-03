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
from typing import Sequence

from common_util import ExpectedUpstreamFile
from common_util import LIBCORE_DIR
from common_util import OpenjdkFinder

# Import git only after common_util because common_util will
# produce informative error
from git import Commit
from git import Repo
from gitdb.exc import BadName

LIBCORE_REPO = Repo(LIBCORE_DIR.as_posix())


def error_and_exit(msg: str) -> None:
  print(f'Error: {msg}', file=sys.stderr)
  sys.exit(1)


def get_commit_or_exit(git_ref: str) -> Commit:
  try:
    return LIBCORE_REPO.commit(git_ref)
  except BadName as e:
    error_and_exit(f'{e}')


def main(argv: Sequence[str]) -> None:
  arg_parser = argparse.ArgumentParser(
      description='Set an entry in EXCPETED_UPSTREAM to the given version if'
                  ' the current and given version are identical.')
  arg_parser.add_argument(
      'target_ref', nargs=1,
      help='A git tag or commit in the upstream-openjdkXXX branch')
  arg_parser.add_argument(
      'source_ref', nargs='?',
      help='A git tag or commit in the upstream-openjdkXXX branch')

  args = arg_parser.parse_args(argv)

  target_ref = args.target_ref[0]
  source_ref = args.source_ref
  commit = get_commit_or_exit(target_ref)

  expected_upstream_file = ExpectedUpstreamFile()
  expected_entries = expected_upstream_file.read_all_entries()

  new_finder = OpenjdkFinder(commit)

  for expected_entry in expected_entries:
    if expected_entry.git_ref == target_ref:
      continue

    # If the source_ref is specified, skip any existing different source refs.
    if source_ref is not None and expected_entry.git_ref != source_ref:
      continue

    current_commit = LIBCORE_REPO.commit(expected_entry.git_ref)
    current_finder = OpenjdkFinder(current_commit)
    if not current_finder.has_file(expected_entry.src_path):
      error_and_exit(f'{expected_entry.src_path} is not found in '
                     f'{expected_entry.git_ref}')

    current_blob = current_commit.tree[expected_entry.src_path]

    # Try to guess the new source path in the new version
    new_src_path = expected_entry.src_path
    if new_finder.has_file(new_src_path):
      pass
    else:
      new_src_path = new_finder.find_src_path_from_ojluni_path(
          expected_entry.dst_path)
      if new_src_path is None:
        print(f"Warning: can't find the upstream path for "
              f"{expected_entry.dst_path}", file=sys.stderr)
        continue

    new_blob = commit.tree[new_src_path]
    if current_blob.data_stream.read() == new_blob.data_stream.read():
      expected_entry.git_ref = target_ref
      expected_entry.src_path = new_src_path

  expected_upstream_file.write_all_entries(expected_entries)


if __name__ == '__main__':
  main(sys.argv[1:])
