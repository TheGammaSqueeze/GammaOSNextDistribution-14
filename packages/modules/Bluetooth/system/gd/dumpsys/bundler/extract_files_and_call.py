#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
#  Copyright 2021 Google, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
""" Get contents of given files and pass as args to remaining params.

Example:
  a.files = [ "foo", "bar" ]
  b.files = [ "fizz", "buzz" ]

  extract_files_and_call.py a.files b.files -- somebin -a --set foo -c -o

  will result in this call:

  somebin -a --set foo -c -o foo bar fizz buzz

"""

from __future__ import print_function

import subprocess
import sys


def file_to_args(filename):
    """ Read file and return lines with empties removed.
    """
    with open(filename, 'r') as f:
        return [x.strip() for x in f.readlines() if x.strip()]


def main():
    file_contents = []
    args = []
    for i in range(1, len(sys.argv) - 1):
        if sys.argv[i] == '--':
            args = sys.argv[i + 1:] + file_contents
            break
        else:
            file_contents.extend(file_to_args(sys.argv[i]))

    subprocess.check_call(args)


if __name__ == "__main__":
    main()
