#!/usr/bin/env python3

#
# Copyright 2021, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Repohook script to run checks on TODOs in CHRE.

This script runs the following checks on TODOs in a commit:

1: Prints a warning if a TODO references the bug ID in the commit message.
This is mainly intended to minimize TODOs in the CHRE codebase, and to be
an active reminder to remove a TODO once a commit addresses the debt
mentioned.

2: Fails the repo upload if the current commit adds a TODO, but fails to
associate it with a bug-ID in the (usual) expected format of
'TODO(b/13371337).

A bug ID field in the commit message is REQUIRED for this script to work.
This can be ensured by adding a 'commit_msg_bug_field = true' hook to the
project's PREUPLOAD.cfg file. It is also recommended to add the
'ignore_merged_commits' option to avoid unexpected script behavior.

This script will work with any number of commits in the current repo
checkout.
"""

import os
import re
import subprocess
import sys

COMMIT_HASH = os.environ['PREUPLOAD_COMMIT']

# According to the repohooks documentation, only the warning and success IDs
# are mentioned - we use a random non-zero value (that's high enough to
# avoid confusion with errno values) as our error code.
REPO_ERROR_RETURN_CODE = 1337
REPO_WARNING_RETURN_CODE = 77
REPO_SUCCESS_RETURN_CODE = 0

def check_for_unassociated_todos() -> int:
  """Check if a TODO has a bug ID associated with it.

  Check if a TODO has a bug ID, in the usual 'TODO(b/13371337): {desc}'
  format. Also prints the line where said TODO was found.

  Returns:
    An error code if a TODO has no bugs associated with it.
  """
  rc = REPO_SUCCESS_RETURN_CODE
  commit_contents_cmd = 'git diff ' + COMMIT_HASH + '~ ' + COMMIT_HASH
  diff_result_lines = subprocess.check_output(commit_contents_cmd,
                                              shell=True,
                                              encoding='UTF-8') \
                                              .split('\n')
  regex = r'TODO\(b\/([0-9]+)'

  for line in diff_result_lines:
    if line.startswith('+') and not line.startswith('+++') and \
        'TODO' in line and not re.findall(regex, line):
      print('Found a TODO in the following line in the commit without an \
            associated bug-ID!')
      print(line)
      print('Please include a bug ID in the format TODO(b/13371337)')
      rc = REPO_ERROR_RETURN_CODE

  return rc

def grep_for_todos(bug_id : str) -> int:
  """Searches for TODOs associated with the BUG ID referenced in the commit.

  Args:
    bug_id: Bug ID referenced in the commit.

  Returns:
    A warning code if current bug ID references any TODOs.
  """
  grep_result = None
  rc = REPO_SUCCESS_RETURN_CODE
  git_repo_path_cmd = 'git rev-parse --show-toplevel'
  repo_path = ' ' + subprocess.check_output(git_repo_path_cmd, shell=True,
                                            encoding='UTF-8')

  grep_base_cmd = 'grep -nri '
  grep_file_filters = '--include \*.h --include \*.cc --include \*.cpp --include \*.c '
  grep_shell_cmd = grep_base_cmd + grep_file_filters + bug_id + repo_path
  try:
    grep_result = subprocess.check_output(grep_shell_cmd, shell=True,
                                          encoding='UTF-8')
  except subprocess.CalledProcessError as e:
    if e.returncode != 1:
      # A return code of 1 means that grep returned a 'NOT_FOUND', which is
      # our ideal scenario! A return code of > 1 means something went very
      # wrong with grep. We still return a success here, since there's
      # nothing much else we can do (and this tool is intended to be mostly
      # informational).
      print('ERROR: grep failed with err code {}'.format(e.returncode),
            file=sys.stderr)
      print('The grep command that was run was:\n{}'.format(grep_shell_cmd),
            file=sys.stderr)

  if grep_result is not None:
    print('Matching TODOs found for the Bug-ID in the commit message..')
    print('Hash of the current commit being checked: {}'
          .format(COMMIT_HASH))
    grep_result = grep_result.replace(repo_path + '/', '')
    print(grep_result)
    rc = REPO_WARNING_RETURN_CODE

  return rc

def get_bug_id_for_current_commit() -> str:
  """Get the Bug ID for the current commit

  Returns:
    The bug ID for the current commit.
  """
  git_current_commit_msg_cmd = 'git log --format=%B -n 1 '
  commit_msg_lines_cmd = git_current_commit_msg_cmd + COMMIT_HASH
  commit_msg_lines_list = subprocess.check_output(commit_msg_lines_cmd,
                                                  shell=True,
                                                  encoding='UTF-8') \
                                                  .split('\n')
  try:
    bug_id_line = \
      [line for line in commit_msg_lines_list if \
        any(word in line.lower() for word in ['bug:', 'fixes:'])][0]
  except IndexError:
    print('Please include a Bug or Fixes field in the commit message')
    sys.exit(-1);
  return bug_id_line.split(':')[1].strip()

def is_file_in_diff(filename : str) -> bool:
  """Check if a given filename is part of the commit.

  Args:
    filename: filename to check in the git diff.

  Returns:
    True if the file is part of the commit.
  """
  commit_contents_cmd = 'git diff ' + COMMIT_HASH + '~ ' + COMMIT_HASH
  diff_result = subprocess.check_output(commit_contents_cmd, shell=True,
                                        encoding='UTF-8')
  return filename in diff_result

def main():
  # This script has a bunch of TODOs peppered around, though not with the
  # same intention as the checks that are being performed. Skip the checks
  # if we're committing changes to this script! One caveat is that we
  # should avoid pushing in changes to other code if we're committing
  # changes to this script.
  rc = REPO_SUCCESS_RETURN_CODE
  if not is_file_in_diff(os.path.basename(__file__)):
    bug_id = get_bug_id_for_current_commit()
    grep_rc = grep_for_todos(bug_id)
    check_rc = check_for_unassociated_todos()
    rc = max(grep_rc, check_rc)
  sys.exit(rc)

if __name__ == '__main__':
  main()
