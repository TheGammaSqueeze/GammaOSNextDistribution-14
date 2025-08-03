#!/usr/bin/env python3

#
# Copyright 2022, The Android Open Source Project
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
"""Script to format or check Kotlin files."""

import argparse
import os
import subprocess
import sys


def main():
  parser = argparse.ArgumentParser(
      'Format Kotlin files or check that they are correctly formatted.')
  parser.add_argument(
      '--check',
      '-c',
      action='store_true',
      default=False,
      help='Perform a format check instead of formatting.')
  parser.add_argument(
      '--includes_file',
      '-i',
      default='',
      help='The file containing the Kotlin files and directories that should be included/excluded, generated using generate_includes_file.py.'
  )
  parser.add_argument(
      '--jar',
      default='',
      help='The path to the ktfmt jar.'
  )
  parser.add_argument(
      'files',
      nargs='*',
      help='The files to format or check. If --include_file is specified, only the files at their intersection will be formatted/checked.'
  )
  args = parser.parse_args()

  ktfmt_args = ['--kotlinlang-style']

  check = args.check
  if check:
    ktfmt_args += ['--set-exit-if-changed', '--dry-run']

  kt_files = []
  for file in args.files:
    if os.path.isdir(file):
      for root, dirs, files in os.walk(file):
        for f in files:
          if is_kotlin_file(f):
            kt_files += [os.path.join(root, f)]

    if is_kotlin_file(file):
      kt_files += [file]

  # Only format/check files from the includes list.
  includes_file = args.includes_file
  if kt_files and includes_file:
    f = open(includes_file, 'r')

    lines = f.read().splitlines()
    included = [line[1:] for line in lines if line.startswith('+')]
    included_files = set()
    included_dirs = []
    for line in included:
      if is_kotlin_file(line):
        included_files.add(line)
      else:
        included_dirs += [line]

    excluded_files = [line[1:] for line in lines if line.startswith('-')]

    kt_files = [
        kt_file for kt_file in kt_files if kt_file not in excluded_files and
        (kt_file in included_files or is_included(kt_file, included_dirs))
    ]

  # No need to start ktfmt if there are no files to check/format.
  if not kt_files:
    sys.exit(0)

  ktfmt_args += kt_files

  dir = os.path.normpath(os.path.dirname(__file__))
  ktfmt_jar = args.jar if args.jar else os.path.join(dir, 'ktfmt.jar')

  ktlint_env = os.environ.copy()
  ktlint_env['JAVA_CMD'] = 'java'
  try:
    process = subprocess.Popen(
        ['java', '-jar', ktfmt_jar] + ktfmt_args,
        stdout=subprocess.PIPE,
        env=ktlint_env)
    stdout, _ = process.communicate()
    code = process.returncode
    if check and code != 0:
      print(
          '**********************************************************************'
      )
      print(
          'Some Kotlin files are not properly formatted. Run the command below to format them.\n'
          'Note: If you are using the Android Studio ktfmt plugin, make sure to select the '
          'Kotlinlang style in \'Editor > ktfmt Settings\'.\n')
      script_path = os.path.normpath(__file__)
      incorrect_files = [
          os.path.abspath(file) for file in stdout.decode('utf-8').splitlines()
      ]
      print('$ ' + script_path + ' ' + ' '.join(incorrect_files) + '\n')
      print(
          '**********************************************************************'
      )
      sys.exit(code)
    else:
      sys.exit(0)
  except OSError as e:
    print('Error running ktfmt')
    sys.exit(1)


def is_kotlin_file(name):
  return name.endswith('.kt') or name.endswith('.kts')


def is_included(file, dirs):
  for dir in dirs:
    if file.startswith(dir):
      return True


if __name__ == '__main__':
  main()
