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
"""Script to generate an include file that can be passed to ktfmt.py.

The include file is generated from one or more folders containing one or more
Kotlin files. The generated include file will exclude all Kotlin files that
currently don't follow ktfmt format. This way, we can easily start formatting
all new files in a project/directory.
"""

import argparse
import os
import subprocess
import sys


def main():
  parser = argparse.ArgumentParser(
      'Generate an include file that can be passed to ktfmt.py')
  parser.add_argument(
      '--output', '-o', required=True, help='The output include file.')
  parser.add_argument(
      'files',
      nargs='*',
      help='The files or directories that should be included.')
  args = parser.parse_args()

  # Add a line preprended with '+' for included files/folders.
  with open(args.output, 'w+') as out:
    includes = args.files
    includes.sort()
    for include in includes:
      out.write('+' + include + '\n')

    # Retrieve all Kotlin files.
    kt_files = []
    for file in args.files:
      if os.path.isdir(file):
        for root, dirs, files in os.walk(file):
          for f in files:
            if is_kotlin_file(f):
              kt_files += [os.path.join(root, f)]

      if is_kotlin_file(file):
        kt_files += [file]

    # Check all files with ktfmt.
    ktfmt_args = ['--kotlinlang-style', '--set-exit-if-changed', '--dry-run'
                 ] + kt_files
    dir = os.path.dirname(__file__)
    ktfmt_jar = os.path.normpath(
        os.path.join(dir,
                     '../../prebuilts/build-tools/common/framework/ktfmt.jar'))

    ktlint_env = os.environ.copy()
    ktlint_env['JAVA_CMD'] = 'java'
    try:
      process = subprocess.Popen(
          ['java', '-jar', ktfmt_jar] + ktfmt_args,
          stdout=subprocess.PIPE,
          env=ktlint_env)

      # Add a line prepended with '-' for all files that are not correctly.
      # formatted.
      stdout, _ = process.communicate()
      incorrect_files = stdout.decode('utf-8').splitlines()
      incorrect_files.sort()
      for file in incorrect_files:
        out.write('-' + file + '\n')
    except OSError as e:
      print('Error running ktfmt')
      sys.exit(1)


def is_kotlin_file(name):
  return name.endswith('.kt') or name.endswith('.kts')


if __name__ == '__main__':
  main()
