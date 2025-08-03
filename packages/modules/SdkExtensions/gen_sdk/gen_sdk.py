#!/usr/bin/env python
#
# Copyright (C) 2021 The Android Open Source Project
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
"""gen_sdk is a command line tool for managing the sdk extension proto db.

Example usages:
# Print a binary representation of the proto database.
$ gen_sdk --action print_binary

# Validate the database
$ gen_sdk --action validate

# Create a new SDK
$ gen_sdk --action new_sdk --sdk 1 --modules=IPSEC,SDK_EXTENSIONS
"""

import argparse
import google.protobuf.text_format
import pathlib
import sys

from sdk_pb2 import ExtensionVersion
from sdk_pb2 import ExtensionDatabase
from sdk_pb2 import SdkModule
from sdk_pb2 import SdkVersion


def ParseArgs(argv):
  parser = argparse.ArgumentParser('Manage the extension SDK database')
  parser.add_argument(
    '--database',
    type=pathlib.Path,
    metavar='PATH',
    default='extensions_db.textpb',
    help='The existing text-proto database to use. (default: extensions_db.textpb)'
  )
  parser.add_argument(
    '--action',
    choices=['print_binary', 'new_sdk', 'validate'],
    metavar='ACTION',
    required=True,
    help='Which action to take (print_binary|new_sdk|validate).'
  )
  parser.add_argument(
    '--sdk',
    type=int,
    metavar='SDK',
    help='The extension SDK level to deal with (int)'
  )
  parser.add_argument(
    '--modules',
    metavar='MODULES',
    help='Comma-separated list of modules providing new APIs. Used for action=new_sdk to create a '
         'new SDK that only requires new versions of some modules.'
  )
  return parser.parse_args(argv)


"""Print the binary representation of the db proto to stdout."""
def PrintBinary(database):
  sys.stdout.buffer.write(database.SerializeToString())


def ValidateDatabase(database, dbname):
  def find_duplicate(l):
    s = set()
    for i in l:
      if i in s:
        return i
      s.add(i)
    return None

  def find_bug():
    dupe = find_duplicate([v.version for v in database.versions])
    if dupe:
      return 'Found duplicate extension version: %d' % dupe

    for version in database.versions:
      dupe = find_duplicate([r.module for r in version.requirements])
      if dupe:
        return 'Found duplicate module requirement for %s in single version %s' % (dupe, version)

    prev_requirements = {}
    for version in sorted(database.versions, key=lambda v: v.version):
      for requirement in version.requirements:
        if requirement.module in prev_requirements:
          prev = prev_requirements[requirement.module]
          if prev.version > requirement.version.version:
            return 'Found module requirement moving backwards: %s in %s' % (requirement, version)
        prev_requirements[requirement.module] = requirement.version

    for version in database.versions:
      required_modules = [r.module for r in version.requirements]
      if SdkModule.UNKNOWN in required_modules:
        return 'SDK %d has a requirement on the UNKNOWN module' % version.version
      if not all([m in SdkModule.values() for m in required_modules]):
        return 'SDK %d has a requirement on an undefined module value' % version.version
      has_adservices = SdkModule.AD_SERVICES in required_modules
      has_extservices = SdkModule.EXT_SERVICES in required_modules
      if version.version >= 9 and (has_adservices ^ has_extservices):
        return 'AD_SERVICES and EXT_SERVICES must be finalized together as of version 9'

    return None

  err = find_bug()
  if err is not None:
    print('%s not valid, aborting:\n  %s' % (dbname, err))
    sys.exit(1)


def NewSdk(database, args):
  if not args.sdk:
    print('Missing required argument --sdk for action new_sdk')
    sys.exit(1)

  new_version = args.sdk
  if args.modules:
    module_names = args.modules.split(',')
  else:
    # Default: require all modules
    module_names = [m for m in SdkModule.keys() if not m == 'UNKNOWN']

  module_values = [SdkModule.Value(m) for m in module_names]
  new_requirements = {}

  # Gather the previous highest requirement of each module
  for prev_version in sorted(database.versions, key=lambda v: v.version):
    for prev_requirement in prev_version.requirements:
      new_requirements[prev_requirement.module] = prev_requirement.version

  # Add new requirements of this version
  for module in module_values:
    new_requirements[module] = SdkVersion(version=new_version)

  to_proto = lambda m : ExtensionVersion.ModuleRequirement(module=m, version=new_requirements[m])
  module_requirements = [to_proto(m) for m in new_requirements]
  extension_version = ExtensionVersion(version=new_version, requirements=module_requirements)
  database.versions.append(extension_version)

  print('Created a new extension SDK level %d with modules %s' % (new_version, ','.join(module_names)))


def main(argv):
  args = ParseArgs(argv)
  with args.database.open('r') as f:
    database = google.protobuf.text_format.Parse(f.read(), ExtensionDatabase())

  ValidateDatabase(database, 'Input database')

  {
    'validate': lambda : print('Validated database'),
    'print_binary': lambda : PrintBinary(database),
    'new_sdk': lambda : NewSdk(database, args)
  }[args.action]()

  if args.action in ['new_sdk']:
    ValidateDatabase(database, 'Post-modification database')
    with args.database.open('w') as f:
      f.write(google.protobuf.text_format.MessageToString(database))

if __name__ == '__main__':
  main(sys.argv[1:])
