#!/usr/bin/env python3
# Copyright 2016 The Android Open Source Project
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

"""Wrapper to run git-clang-format and parse its output."""

import argparse
import os
import sys

_path = os.path.realpath(__file__ + '/../..')
if sys.path[0] != _path:
    sys.path.insert(0, _path)
del _path

# We have to import our local modules after the sys.path tweak.  We can't use
# relative imports because this is an executable program, not a module.
# pylint: disable=wrong-import-position,import-error
import rh.shell
import rh.utils


# Since we're asking git-clang-format to print a diff, all modified filenames
# that have formatting errors are printed with this prefix.
DIFF_MARKER_PREFIX = '+++ b/'


def get_parser():
    """Return a command line parser."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--clang-format', default='clang-format',
                        help='The path of the clang-format executable.')
    parser.add_argument('--git-clang-format', default='git-clang-format',
                        help='The path of the git-clang-format executable.')
    parser.add_argument('--style', metavar='STYLE', type=str,
                        help='The style that clang-format will use.')
    parser.add_argument('--extensions', metavar='EXTENSIONS', type=str,
                        help='Comma-separated list of file extensions to '
                             'format.')
    parser.add_argument('--fix', action='store_true',
                        help='Fix any formatting errors automatically.')

    scope = parser.add_mutually_exclusive_group(required=True)
    scope.add_argument('--commit', type=str, default='HEAD',
                       help='Specify the commit to validate.')
    scope.add_argument('--working-tree', action='store_true',
                       help='Validates the files that have changed from '
                            'HEAD in the working directory.')

    parser.add_argument('files', type=str, nargs='*',
                        help='If specified, only consider differences in '
                             'these files.')
    return parser


def main(argv):
    """The main entry."""
    parser = get_parser()
    opts = parser.parse_args(argv)

    cmd = [opts.git_clang_format, '--binary', opts.clang_format, '--diff']
    if opts.style:
        cmd.extend(['--style', opts.style])
    if opts.extensions:
        cmd.extend(['--extensions', opts.extensions])
    if not opts.working_tree:
        cmd.extend([f'{opts.commit}^', opts.commit])
    cmd.extend(['--'] + opts.files)

    # Fail gracefully if clang-format itself aborts/fails.
    result = rh.utils.run(cmd, capture_output=True, check=False)
    # Newer versions of git-clang-format will exit 1 when it worked.  Assume a
    # real failure is any exit code above 1, or any time stderr is used, or if
    # it exited 1 and produce useful format diffs to stdout.  If it exited 0,
    # then assume all is well and we'll attempt to parse its output below.
    ret_code = None
    if (result.returncode > 1 or result.stderr or
        (result.stdout and result.returncode)):
        # Apply fix if the flag is set and clang-format shows it is fixible.
        if opts.fix and result.stdout and result.returncode:
            result = rh.utils.run(['git', 'apply'], input=result.stdout,
                                   check=False)
            ret_code = result.returncode
            if ret_code:
                print('Error: Unable to automatically fix things.\n'
                      '  Make sure your checkout is clean first.\n'
                      '  If you have multiple commits, you might have to '
                      'manually rebase your tree first.',
                      file=sys.stderr)

        else:  # Regular clang-format aborts/fails.
            print(f'clang-format failed:\ncmd: {result.cmdstr}\n'
                  f'stdout:\n{result.stdout}\n', file=sys.stderr)
            if result.returncode > 1 or result.stderr:
                print('\nPlease report this to the clang team.\n',
                      f'stderr:\n{result.stderr}', file=sys.stderr)
            ret_code = 1

        return ret_code

    stdout = result.stdout
    if stdout.rstrip('\n') == 'no modified files to format':
        # This is always printed when only files that clang-format does not
        # understand were modified.
        return 0

    diff_filenames = []
    for line in stdout.splitlines():
        if line.startswith(DIFF_MARKER_PREFIX):
            diff_filenames.append(line[len(DIFF_MARKER_PREFIX):].rstrip())

    if diff_filenames:
        if opts.fix:
            result = rh.utils.run(['git', 'apply'], input=stdout, check=False)
            if result.returncode:
                print('Error: Unable to automatically fix things.\n'
                      '  Make sure your checkout is clean first.\n'
                      '  If you have multiple commits, you might have to '
                      'manually rebase your tree first.',
                      file=sys.stderr)
                return result.returncode
        else:
            print('The following files have formatting errors:')
            for filename in diff_filenames:
                print(f'\t{filename}')
            print('You can try to fix this by running:\n'
                  f'{sys.argv[0]} --fix {rh.shell.cmd_to_str(argv)}')
            return 1

    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
