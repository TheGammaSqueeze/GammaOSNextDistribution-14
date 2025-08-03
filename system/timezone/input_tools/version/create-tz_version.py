#!/usr/bin/python3 -B

# Copyright 2017 The Android Open Source Project
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

"""Generates a time zone version file"""

import argparse
import os
import shutil
import subprocess
import sys

sys.path.append('%s/external/icu/tools' % os.environ.get('ANDROID_BUILD_TOP'))
import i18nutil

sys.path.append('%s/system/timezone' % os.environ.get('ANDROID_BUILD_TOP'))
import tzdatautil

android_build_top = i18nutil.GetAndroidRootOrDie()
android_host_out_dir = i18nutil.GetAndroidHostOutOrDie()
timezone_dir = os.path.realpath('%s/system/timezone' % android_build_top)
i18nutil.CheckDirExists(timezone_dir, 'system/timezone')

def RunCreateTzVersion(properties_file):
  # Build the libraries needed.
  tzdatautil.InvokeSoong(android_build_top, ['create_tz_version'])

  # Run the CreateTzVersion tool
  command = '%s/bin/create_tz_version' % android_host_out_dir
  subprocess.check_call([command, properties_file])


def CreateTzVersion(
    iana_version, revision, output_version_file):
  original_cwd = os.getcwd()

  i18nutil.SwitchToNewTemporaryDirectory()
  working_dir = os.getcwd()

  # Generate the properties file.
  properties_file = '%s/tzversion.properties' % working_dir
  with open(properties_file, "w") as properties:
    properties.write('rules.version=%s\n' % iana_version)
    properties.write('revision=%s\n' % revision)
    properties.write('output.version.file=%s\n' % output_version_file)

  RunCreateTzVersion(properties_file)

  os.chdir(original_cwd)


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument('-iana_version', required=True,
      help='The IANA time zone rules release version, e.g. 2017b')
  parser.add_argument('-revision', type=int, required = True,
      help='Revision of the current IANA version')
  parser.add_argument('-output_version_file', required=True,
      help='The output path for the version file')
  args = parser.parse_args()

  iana_version = args.iana_version
  revision = args.revision
  output_version_file = os.path.abspath(args.output_version_file)

  CreateTzVersion(
      iana_version=iana_version,
      revision=revision,
      output_version_file=output_version_file)

  print('Version file created as %s' % output_version_file)
  sys.exit(0)


if __name__ == '__main__':
  main()
