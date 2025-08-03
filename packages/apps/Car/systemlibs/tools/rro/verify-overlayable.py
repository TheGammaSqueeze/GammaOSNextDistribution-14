#!/usr/bin/env python3
#  Copyright (C) 2021 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
import sys
from resource_utils import get_all_resources, get_resources_from_single_file, Resource

if sys.version_info[0] != 3:
    print("Must use python 3")
    sys.exit(1)

"""
Script used to verify the 'overlayable.xml' file.
"""
def main():
    parser = argparse.ArgumentParser(description='Verify overlayable.xml.')
    optional_args = parser.add_argument_group('optional arguments')
    optional_args.add_argument('-e', '--excludeFiles', nargs='*', help='File paths (absolute or relative to cwd) that should be excluded when generating overlayable.xml')
    optional_args.add_argument('-m', '--errorMessage', nargs='*', help='Custom error message if resources are added or removed.')
    required_args = parser.add_argument_group('required arguments')
    required_args.add_argument('-r', '--resourcePath', help='Path to resource directory (absolute or relative to cwd)', required=True, action='append')
    required_args.add_argument('-o', '--overlayableFilePath', help='Filepath to overlayable.xml (absolute or relative to cwd).', required=True)
    args = parser.parse_args()

    resources = set()
    for path in args.resourcePath:
        resources |= get_all_resources(path, args.excludeFiles)
    old_mapping = get_resources_from_single_file(args.overlayableFilePath)
    compare_resources(old_mapping, resources, args.overlayableFilePath, args.errorMessage)

def compare_resources(old_mapping, new_mapping, res_public_file, errorMsg):
    removed = old_mapping.difference(new_mapping)
    added = new_mapping.difference(old_mapping)
    if len(removed) > 0:
        print('Resources removed:\n' + '\n'.join(map(lambda x: str(x), removed)))
    if len(added) > 0:
        print('Resources added:\n' + '\n'.join(map(lambda x: str(x), added)))
    if len(added) + len(removed) > 0:
        if errorMsg is not None:
            print(errorMsg)
        else:
            print("Some resource have been modified. If this is intentional please " +
                  "run 'python3 generate-overlayable.py' again and submit the new %s" % res_public_file)
            print("Some projects may include $PROJECT_TOP/tools/generate-overlayable.sh which calls " +
                  "the above command with the appropriate command line arguments")
        sys.exit(1)

if __name__ == '__main__':
    main()
