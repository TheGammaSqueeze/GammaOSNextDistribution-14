#!/usr/bin/python3

# Copyright (c) 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""This script creates a allowlist of test attributes based on the 'suite' read
from test control files.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse

import common
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.server.cros.dynamic_suite.suite import Suite


def main():
    """main script."""
    # Parse filepath from cmd line.
    parser = argparse.ArgumentParser(description='Create attribute allowlist.')
    parser.add_argument('path',
                        metavar='ALLOWLIST_FILE_PATH',
                        help='Path to the file allowlist is written to. E.g. '
                        './attribute_allowlist.txt')
    args = parser.parse_args()

    # Get all the suites from current test control files, and order them.
    fs_getter = Suite.create_fs_getter(common.autotest_dir)
    devserver = dev_server.ImageServer('')
    suite_list = Suite.list_all_suites('', devserver, fs_getter)
    suite_list.sort(key=str.lower)

    # Parse attributes from suites, and write to a file
    allowlist = ['suite:' + x for x in suite_list]
    _WriteToFile(allowlist, args.path)


def _WriteToFile(allowlist, path):
    """"Write the allowlist to a file under the path.

  The format of the file used here is a list, which can be easily read to a list
  by using ast.literal_eval.

  Args:
    allowlist: a list contains all the allowed attributes.
    path: path to the file.
  """
    with open(path, 'wb') as attr_file:
        attr_file.write('\n'.join(allowlist).encode('utf-8'))


if __name__ == '__main__':
    main()
