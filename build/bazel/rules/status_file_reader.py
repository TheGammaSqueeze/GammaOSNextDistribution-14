#!/usr/bin/env python3
# Copyright (C) 2023 The Android Open Source Project
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

import argparse
import os
import re
import sys

def read_status_file(filepath):
    result = {}
    with open(filepath) as f:
        for line in f:
            if not line.strip():
                continue
            if line.endswith('\r\n'):
                line = line.removesuffix('\r\n')
            else:
                line = line.removesuffix('\n')
            var, value = line.split(' ', maxsplit=1)
            result[var] = value
    return result


def cat(args):
    status_file = read_status_file(args.file)
    if args.variable not in status_file:
        sys.exit(f'error: {args.variable} was not found in {args.file}')
    print(status_file[args.variable])


def replace(args):
    status_file = read_status_file(args.file)

    if args.var:
        trimmed_status_file = {}
        for var in args.var:
            if var not in status_file:
                sys.exit(f'error: {var} was not found in {args.file}')
            trimmed_status_file[var] = status_file[var]
        status_file = trimmed_status_file

    pattern = re.compile("|".join([re.escape("{" + v + "}") for v in status_file.keys()]))

    with open(args.input) as inf, open(args.output, 'w') as outf:
        contents = inf.read()
        contents = pattern.sub(lambda m: status_file[m.group(0)[1:-1]], contents)
        outf.write(contents)


def main():
    parser = argparse.ArgumentParser(description = 'A utility tool for reading the bazel version file. (ctx.version_file, aka volatile-status.txt)')
    subparsers = parser.add_subparsers(required=True)
    cat_parser = subparsers.add_parser('cat', description = 'print the value of a single variable in the version file')
    cat_parser.add_argument('file', help = 'path to the volatile-status.txt file')
    cat_parser.add_argument('variable', help = 'the variable to print')
    cat_parser.set_defaults(func=cat)
    replace_parser = subparsers.add_parser('replace', description = 'Replace strings like {VAR_NAME} in an input file')
    replace_parser.add_argument('file', help = 'path to the volatile-status.txt file')
    replace_parser.add_argument('input', help = 'path to the input file with {VAR_NAME} strings')
    replace_parser.add_argument('output', help = 'path to the output file')
    replace_parser.add_argument('--var', nargs='*', help = 'If given, only replace these variables')
    replace_parser.set_defaults(func=replace)
    args = parser.parse_args()

    args.func(args)


if __name__ == '__main__':
    try:
        main()
    except FileNotFoundError as e:
        # Don't show a backtrace for FileNotFoundErrors
        sys.exit(str(e))
