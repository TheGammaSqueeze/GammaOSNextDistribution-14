#!/usr/bin/python3 -B

# Copyright 2020 The Android Open Source Project
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

"""Allows visualization of zone trees (the thing that works out if zones are distinct)."""

import argparse
import glob
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


def BuildAndRunTool(country_zones_txt, country_code, output_dir):
  tzdatautil.InvokeSoong(android_build_top, ['unique_zones_visualizer'])
  jar_file = '%s/framework/unique_zones_visualizer.jar' % android_host_out
  subprocess.check_call(['java', '-jar', jar_file, country_zones_txt, country_code, output_dir])

def CreatePngs(output_dir):
  gv_files = glob.glob('%s/*.gv' % output_dir)
  for gv_file in gv_files:
    png_file = gv_file.replace('.gv', '.png')
    print('Generating %s...' % png_file)
    with open(png_file, 'w') as out_file:
      subprocess.check_call(['dot', '-Tpng', gv_file], stdout=out_file)

def CheckFileExists(file, filename):
  if not os.path.isfile(file):
    print("Couldn't find %s (%s)!" % (filename, file))
    sys.exit(1)


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument('-input', required=True,
      help='The country_zones.txt file to process')
  parser.add_argument('-country_code', required=True,
      help='The country code (e.g. "us") to process')
  parser.add_argument('-output', required=True,
      help='The output directory for the dump')
  args = parser.parse_args()

  country_zones_txt = args.input
  country_code = args.country_code
  output_dir = args.output

  CheckFileExists(country_zones_txt, '-input')
  if not os.path.exists(output_dir):
    print('Creating dir: %s'  % output_dir)
    os.mkdir(output_dir)
  i18nutil.CheckDirExists(output_dir, '-output')

  BuildAndRunTool(country_zones_txt, country_code, output_dir)
  CreatePngs(output_dir)

  print('Look in %s for all generated files' % output_dir)
  sys.exit(0)


if __name__ == '__main__':
  main()
