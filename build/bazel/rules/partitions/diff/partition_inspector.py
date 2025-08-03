#!/usr/bin/env python3
# Copyright (C) 2023 The Android Open Source Project
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

'''
Partition inspector creates a text file that describes a partition, for diffing it against another
partition. Currently it just lists the files in the partition, but should be expanded to include
selinux information and avb information.
'''

import argparse
import difflib
import hashlib
import os
import subprocess
import sys

def run_debugfs(command):
    p = subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if len(p.stderr.splitlines()) > 1:
        # debugfs unfortunately doesn't exit with a nonzero status code on most errors.
        # Instead, check if it had more than 1 line of stderr output.
        # It always outputs its version number as the first line.
        sys.exit(p.stderr)
    return p.stdout

def tree(debugfs, image, path='/', depth=0):
    result = ''
    for line in run_debugfs([debugfs, '-R', 'ls -p '+path, image]).splitlines():
        line = line.strip()
        if not line:
            continue
        _, _ino, mode, uid, gid, name, size, _ = line.split('/')
        if name == '.' or name == '..':
            continue
        is_dir = not size

        child = os.path.join(path, name)

        # The selinux contexts are stored in the extended attributes
        extended_attributes = ' '.join(run_debugfs([debugfs, '-R' 'ea_list '+child, image]).strip().split())

        result += '  '*depth
        result += f'{mode} {uid} {gid} {name} ({extended_attributes}){":" if is_dir else ""}\n'
        if not size:
            result += tree(debugfs, image, child, depth+1)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--debugfs-path', default='debugfs')
    parser.add_argument('image')
    args = parser.parse_args()

    # debugfs doesn't exit with an error if the image doesn't exist
    if not os.path.isfile(args.image):
        sys.exit(f"{args.image} was not found or was a directory")

    print(tree(args.debugfs_path, args.image))



if __name__ == "__main__":
    main()
