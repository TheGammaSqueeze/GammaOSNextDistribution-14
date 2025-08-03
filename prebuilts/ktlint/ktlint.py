#!/usr/bin/env python3

#
# Copyright 2017, The Android Open Source Project
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

"""Script that is used by developers to run style checks on Kotlin files."""

from __future__ import print_function

import argparse
import errno
import os
import subprocess
import sys

MAIN_DIRECTORY = os.path.normpath(os.path.dirname(__file__))
KTLINT_JAR = os.path.join(MAIN_DIRECTORY, 'ktlint-android-all.jar')
EDITOR_CONFIG = os.path.join(MAIN_DIRECTORY, '.editorconfig')
FORMAT_MESSAGE = """
**********************************************************************
To format run:
{}/ktlint.py --format --file {}
**********************************************************************
"""


def main(args=None):
  parser = argparse.ArgumentParser()
  parser.add_argument('--file', '-f', nargs='*')
  parser.add_argument('--format', '-F', dest='format', action='store_true')
  parser.add_argument('--noformat', dest='format', action='store_false')
  parser.add_argument(
      '--no-verify-format', dest='verify_format', action='store_false'
  )
  parser.add_argument('--editorconfig', default=EDITOR_CONFIG)
  parser.set_defaults(format=False, verify_format=True)
  args = parser.parse_args()
  kt_files = [f for f in args.file if f.endswith('.kt') or f.endswith('.kts')]
  if not kt_files:
    sys.exit(0)

  disabled_rules = [
      'indent',
      'paren-spacing',
      'curly-spacing',
      'wrapping',
      # trailing-comma requires wrapping
      'trailing-comma-on-call-site',
      'trailing-comma-on-declaration-site',
      # annotations requires wrapping
      'spacing-between-declarations-with-annotations',
      'annotation',
  ]

  # Disable more format-related rules if we shouldn't verify the format. This is usually
  # the case if files we are checking are already checked by ktfmt.
  if not args.verify_format:
    disabled_rules += [
        'final-newline',
        'no-consecutive-blank-lines',
        'import-ordering',
        'comment-wrapping',
        'argument-list-wrapping',
        'spacing-between-declarations-with-comments',
        'annotation-spacing',
        'multiline-if-else',
    ]

  ktlint_args = kt_files[:]
  ktlint_args += ['--disabled_rules=' + ','.join(disabled_rules)]

  # Setup editor config explicitly if defined - else will inherit from tree
  if args.editorconfig is not None:
    ktlint_args += ['--editorconfig', args.editorconfig]

  # Automatically format files if requested.
  if args.format:
    ktlint_args += ['-F']

  ktlint_env = os.environ.copy()
  ktlint_env['JAVA_CMD'] = 'java'
  try:
    check = subprocess.Popen(
        [
            'java',
            '--add-opens=java.base/java.lang=ALL-UNNAMED',
            '-jar',
            KTLINT_JAR,
        ]
        + ktlint_args,
        stdout=subprocess.PIPE,
        env=ktlint_env,
    )
    stdout, _ = check.communicate()
    if stdout:
      print('prebuilts/ktlint found errors in files you changed:')
      print(stdout.decode('utf-8'))
      if args.verify_format:
        print(FORMAT_MESSAGE.format(MAIN_DIRECTORY, ' '.join(kt_files)))
      sys.exit(1)
    else:
      sys.exit(0)
  except OSError as e:
    if e.errno == errno.ENOENT:
      print('Error running ktlint!')
      sys.exit(1)


if __name__ == '__main__':
  main()
