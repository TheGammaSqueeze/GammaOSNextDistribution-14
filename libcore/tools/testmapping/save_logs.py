#!/usr/bin/env python2
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

"""Tool to save logs files for use by gen_smoke_tests.py."""

from __future__ import print_function

import glob
import os
import shutil
import sys

import util

ANDROID_REPOSITORY_ROOT = util.android_repository_root()
LOGS_ROOT = os.path.join(ANDROID_REPOSITORY_ROOT, 'out', 'host', 'linux-x86',
                         'cts', 'android-cts', 'logs')
CTS_LOGS_PATTERN = os.path.join(LOGS_ROOT, '*', '*',
                                'device_logcat_test_*.txt.gz')
LIBCORE_DIR = os.path.join(ANDROID_REPOSITORY_ROOT, 'libcore')
DESTINATION_DIR = os.path.join(LIBCORE_DIR, 'smoketest')


def check_destination():
  """Ensures the destination directory to copy the files to exists."""
  if not os.path.isdir(LIBCORE_DIR):
    raise Exception('Could not find directory ' + LIBCORE_DIR)
  if not os.path.isdir(DESTINATION_DIR):
    print('Making destination directory ' +
          util.printable_path(DESTINATION_DIR))
    os.mkdir(DESTINATION_DIR, 0o755)
  else:
    print('Found destination directory ' + util.printable_path(DESTINATION_DIR))


def is_real_log_file(filename):
  """Returns whether the filename is one we should use or not."""
  return 'latest' not in filename


def find_all_log_files():
  """Finds all CTS log files in the expected directory.

  Returns:
    A list of filenames, sorted by mtime, most recent first.
  Raises:
    Exception: Not enough log files found.
  """
  print('Looking for logs in %s' % util.printable_path(LOGS_ROOT))
  print('+++ ' + CTS_LOGS_PATTERN)
  for f in glob.glob(CTS_LOGS_PATTERN):
    print('*** ' + f)
  sources = [f for f in glob.glob(CTS_LOGS_PATTERN) if is_real_log_file(f)]
  if not sources:
    raise Exception('Found no logs files!')
  return sorted(sources, key=os.path.getmtime, reverse=True)


def relative_source_name(source):
  """Returns the name of a source file, relative to the logs root."""
  return os.path.relpath(source, LOGS_ROOT)


def flatten_name(name):
  """Returns a flattened version of a path, using _ to separate."""
  parts = []
  while name:
    (head, tail) = os.path.split(name)
    parts.insert(0, tail)
    name = head
  return '_'.join(parts)


def destination_path(name):
  """Returns the destination path for a given name (which must be flattened)."""
  assert not os.path.dirname(name)
  return os.path.join(DESTINATION_DIR, name)


def get_indexes_from_user(prompt, options):
  """Gets a sequence of indexes between 1 and max from the user.

  Args:
    prompt: A prompt to show to the user.
    options: The options to show to the user.

  Yields:
    The indexes.
  """
  for (index, option) in enumerate(options):
    print('%d: %s' % (index + 1, option))
  while True:
    print(prompt)
    instr = sys.stdin.readline().strip()
    if instr.lower() == 'q':
      break
    try:
      index = int(instr)
    except ValueError:
      print('Not a valid index! Please try again')
      continue
    if index < 1 or index > len(options):
      print('Not a valid index! Please try again')
      continue
    yield index - 1


def do_copy(source):
  """Copies the given source into the destination directory."""
  destination = destination_path(flatten_name(relative_source_name(source)))
  if os.path.exists(destination):
    print('File already exists: ' + util.printable_path(destination))
  else:
    print('Copying %s to %s' %
          (util.printable_path(source), util.printable_path(destination)))
    shutil.copyfile(source, destination)


def main():
  check_destination()
  sources = find_all_log_files()
  for index in get_indexes_from_user(
      'Enter the number of the file to save, or Q to quit',
      map(relative_source_name, sources)):
    do_copy(sources[index])
  print('Bye!')


main()
