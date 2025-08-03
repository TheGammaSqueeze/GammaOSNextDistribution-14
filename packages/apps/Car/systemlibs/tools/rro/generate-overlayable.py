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
from resource_utils import get_all_resources, Resource
from datetime import datetime
import lxml.etree as etree
if sys.version_info[0] != 3:
    print("Must use python 3")
    sys.exit(1)

COPYRIGHT_STR = """ Copyright (C) %s The Android Open Source Project
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.""" % (datetime.today().strftime("%Y"))

AUTOGENERATION_NOTICE_STR = """
THIS FILE WAS AUTO GENERATED, DO NOT EDIT MANUALLY.
REGENERATE USING packages/apps/Car/libs/tools/rro/generate-overlayable.py
"""

"""
Script used to update the 'overlayable.xml' file.
"""
def main():
    parser = argparse.ArgumentParser(description='Generate overlayable.xml.')
    optional_args = parser.add_argument_group('optional arguments')
    optional_args.add_argument('-t', '--policyType', default='system|product|signature', help='Policy type for the overlay - delimited by |')
    optional_args.add_argument('-e', '--excludeFiles', nargs='*', help='File paths (absolute or relative to cwd) that should be excluded when generating overlayable.xml')
    optional_args.add_argument('-o', '--outputFile', default='', help='Output file path (absolute or relative to cwd). If empty, output to stdout')
    required_args = parser.add_argument_group('required arguments')
    required_args.add_argument('-n', '--targetName', help='Overlayable name for the overlay.', required=True)
    required_args.add_argument('-r', '--resourcePath', help='Path to resource directory (absolute or relative to cwd)', required=True, action='append')
    args = parser.parse_args()

    resources = set()
    for path in args.resourcePath:
        resources |= get_all_resources(path, args.excludeFiles)
    generate_overlayable_file(resources, args.targetName, args.policyType, args.outputFile)

def generate_overlayable_file(resources, target_name, policy_type, output_file):
    resources = sorted(resources, key=lambda x: x.type + x.name)
    root = etree.Element('resources')
    root.addprevious(etree.Comment(COPYRIGHT_STR))
    root.addprevious(etree.Comment(AUTOGENERATION_NOTICE_STR))
    overlayable = etree.SubElement(root, 'overlayable')
    overlayable.set('name', target_name)
    policy = etree.SubElement(overlayable, 'policy')
    policy.set('type', policy_type)
    for resource in resources:
        item = etree.SubElement(policy, 'item')
        item.set('type', resource.type)
        item.set('name', resource.name)
    data = etree.ElementTree(root)
    if not output_file:
        print(etree.tostring(data, pretty_print=True, xml_declaration=True).decode())
    else:
        with open(output_file, 'wb') as f:
            data.write(f, pretty_print=True, xml_declaration=True, encoding='utf-8')

if __name__ == '__main__':
    main()
