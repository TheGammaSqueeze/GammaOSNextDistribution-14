#!/usr/bin/env python3
# Copyright (c) 2011 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# This utility allows for easy update to chromium os tree status, with proper
# password protected authorization.
#
# Example usage:
# ./set_tree_status.py [options] "a quoted space separated message."

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import getpass
import optparse
import os
import sys
from six.moves import urllib

CHROMEOS_STATUS_SERVER = 'https://chromiumos-status.appspot.com'


def get_status():
    response = urllib.request.urlopen(
        CHROMEOS_STATUS_SERVER + '/current?format=raw')
    return response.read()


def get_pwd():
    password_file = os.path.join('/home', getpass.getuser(),
                                 '.status_password.txt')
    if os.path.isfile(password_file):
        return open(password_file, 'r').read().strip()
    return getpass.getpass()


def post_status(force, message):
    if not force:
        status = get_status()
        if 'tree is closed' in status.lower():
            print('Tree is already closed for some other reason.',
                  file=sys.stderr)
            print(status, file=sys.stderr)
            return -1
    data = {
        'message': message,
        'username': getpass.getuser(),
        'password': get_pwd(),
    }
    urllib.request.urlopen(CHROMEOS_STATUS_SERVER + '/status',
                           urllib.parse.urlencode(data))
    return 0


if __name__ == '__main__':
    parser = optparse.OptionParser("%prog [options] quoted_message")
    parser.add_option('--noforce',
                      dest='force', action='store_false',
                      default=True,
                      help='Dont force to close tree if it is already closed.')
    options, args = parser.parse_args()
    if not args:
        print('missing tree close message.', file=sys.stderr)
    sys.exit(post_status(options.force, args[0]))
