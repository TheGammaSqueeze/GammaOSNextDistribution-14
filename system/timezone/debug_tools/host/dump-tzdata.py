#!/usr/bin/python3 -B

# Copyright 2019 The Android Open Source Project
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

"""Dumps the contents of a tzdata file."""

import argparse
import os
import subprocess
import sys

sys.path.append('%s/external/icu/tools' % os.environ.get('ANDROID_BUILD_TOP'))
import i18nutil

sys.path.append('%s/system/timezone' % os.environ.get('ANDROID_BUILD_TOP'))
import tzdatautil


# Calculate the paths that are referred to by multiple functions.
android_build_top = i18nutil.GetAndroidRootOrDie()
timezone_dir = os.path.realpath('%s/system/timezone' % android_build_top)
i18nutil.CheckDirExists(timezone_dir, 'system/timezone')

android_host_out = i18nutil.GetAndroidHostOutOrDie()

debug_tools_dir = os.path.realpath('%s/system/timezone/debug_tools/host' % android_build_top)
i18nutil.CheckDirExists(debug_tools_dir, 'system/timezone/debug_tools/host')


def BuildDebugTools():
  tzdatautil.InvokeSoong(android_build_top, ['zone_splitter', 'tz_file_dumper'])


def SplitTzData(tzdata_file, output_dir):
  jar_file = '%s/framework/zone_splitter.jar' % android_host_out
  subprocess.check_call(['java', '-jar', jar_file, tzdata_file, output_dir])


def CreateCsvFiles(zones_dir, csvs_dir):
  jar_file = '%s/framework/tz_file_dumper.jar' % android_host_out
  subprocess.check_call(['java', '-jar', jar_file, zones_dir, csvs_dir])


def CheckFileExists(file, filename):
  if not os.path.isfile(file):
    print("Couldn't find %s (%s)!" % (filename, file))
    sys.exit(1)


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument('-tzdata', required=True,
      help='The tzdata file to process')
  parser.add_argument('-output', required=True,
      help='The output directory for the dump')
  args = parser.parse_args()

  tzdata_file = args.tzdata
  output_dir = args.output

  CheckFileExists(tzdata_file, '-tzdata')
  if not os.path.exists(output_dir):
    print('Creating dir: %s'  % output_dir)
    os.mkdir(output_dir)
  i18nutil.CheckDirExists(output_dir, '-output')

  BuildDebugTools()

  SplitTzData(tzdata_file, output_dir)

  zones_dir = '%s/zones' % output_dir
  csvs_dir = '%s/csvs' % output_dir

  i18nutil.CheckDirExists(zones_dir, 'zones output dir')
  if not os.path.exists(csvs_dir):
    os.mkdir(csvs_dir)

  CreateCsvFiles(zones_dir, csvs_dir)

  print('Look in %s for all extracted files' % output_dir)
  print('Look in %s for dumped CSVs' % csvs_dir)
  sys.exit(0)


if __name__ == '__main__':
  main()
