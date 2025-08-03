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
from resource_utils import get_all_resources, get_androidx_resources, Resource
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
REGENERATE USING packages/apps/Car/libs/tools/rro/generate-overlays.py
"""

"""
Script used to update the 'overlayable.xml' file.
"""
def main():
    parser = argparse.ArgumentParser(description="Generate overlayable.xml. This script assumes that all the RRO resources have the exact same name as the app resource they're overlaying.")
    parser.add_argument('-o', '--outputFile', default='', help='Output file path. If empty, output to stdout')
    parser.add_argument('-a', '--appResources', help="Path to the app's resource folder. If given, will be used to exclude any rro-specific resources from overlays.xml.")
    required_args = parser.add_argument_group('Required arguments')
    required_args.add_argument('-r', '--resourcePath', help="Path to the RRO's resource directory", required=True)
    args = parser.parse_args()

    resources = get_all_resources(args.resourcePath)
    try:
        resources.remove(Resource('overlays', 'xml'))
    except KeyError:
        pass

    if args.appResources:
        resources = resources.intersection(
            get_all_resources(args.appResources).union(get_androidx_resources()))
    generate_overlays_file(resources, args.outputFile)

def generate_overlays_file(resources, output_file):
    resources = sorted(resources, key=lambda x: x.type + x.name)
    root = etree.Element('overlay')
    root.addprevious(etree.Comment(COPYRIGHT_STR))
    root.addprevious(etree.Comment(AUTOGENERATION_NOTICE_STR))
    for resource in resources:
        item = etree.SubElement(root, 'item')
        item.set('target', f'{resource.type}/{resource.name}')
        item.set('value', f'@{resource.type}/{resource.name}')
    rootTree = etree.ElementTree(root)
    if not output_file:
        print(etree.tostring(rootTree, pretty_print=True, xml_declaration=True).decode())
    else:
        with open(output_file, 'wb') as f:
            rootTree.write(f, pretty_print=True, xml_declaration=True, encoding='utf-8')

if __name__ == '__main__':
    main()
