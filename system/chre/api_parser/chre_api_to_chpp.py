#!/usr/bin/python3
#
# Copyright (C) 2020 The Android Open Source Project
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
#

import argparse
import os
import json
import subprocess

from api_parser import ApiParser
from chpp_code_generator import CodeGenerator
from utils import system_chre_abs_path


def run(args):
    with open(system_chre_abs_path() + '/api_parser/chre_api_annotations.json') as f:
        js = json.load(f)

    commit_hash = subprocess.getoutput(
        "git describe --always --long --dirty --exclude '*'")
    for file in js:
        if args.file_filter:
            matched = False
            for matcher in args.file_filter:
                if matcher in file['filename']:
                    matched = True
                    break
            if not matched:
                print("Skipping {} - doesn't match filter(s) {}".format(file['filename'],
                                                                        args.file_filter))
                continue
        print('Parsing {} ... '.format(file['filename']), end='', flush=True)
        api_parser = ApiParser(file)
        code_gen = CodeGenerator(api_parser, commit_hash)
        print('done')
        code_gen.generate_header_file(args.dry_run, args.skip_clang_format)
        code_gen.generate_conversion_file(args.dry_run, args.skip_clang_format)


def main():
    parser = argparse.ArgumentParser(
        description='Generate CHPP serialization code from CHRE APIs.')
    parser.add_argument('-n', dest='dry_run', action='store_true',
                        help='Print the output instead of writing to a file')
    parser.add_argument('--skip-clang-format', dest='skip_clang_format', action='store_true',
                        help='Skip running clang-format on the output files (doesn\'t apply to dry '
                             'runs)')
    parser.add_argument('file_filter', nargs='*',
                        help='Filters the input files (filename field in the JSON) to generate a '
                             'subset of the typical output, e.g. "wifi" to just generate conversion'
                             ' routines for wifi.h')
    args = parser.parse_args()
    run(args)


if __name__ == '__main__':
    main()
