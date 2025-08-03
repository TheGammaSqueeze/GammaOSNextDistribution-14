#!/usr/bin/python3 -B
# Copyright 2018 The Android Open Source Project
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

"""Regenerates (just) ICU data source files used to build ICU data files."""

from __future__ import print_function

import os
import shutil
import subprocess
import sys
from pathlib import Path

import i18nutil
import icuutil


# Run with no arguments from any directory, with no special setup required.
# See icu4c/source/data/cldr-icu-readme.txt for the upstream ICU instructions.
def main():
  if subprocess.call(["which", "mvn"]) != 0 or subprocess.call(["which", "ant"]) != 0:
    print("Can't find the required tools. Run `sudo apt-get install maven ant` to install")
    exit(1)

  if not os.path.exists(os.path.join(Path.home(), ".m2/settings.xml")):
    print("Can\'t find `~/.m2/settings.xml`. Please follow the instructions at "
          "http://cldr.unicode.org/development/maven to create one and the github token.")
    exit(1)

  cldr_dir = icuutil.cldrDir()
  print('Found cldr in %s ...' % cldr_dir)
  icu_dir = icuutil.icuDir()
  print('Found icu in %s ...' % icu_dir)

  # Ant doesn't have any mechanism for using a build directory separate from the
  # source directory so this build script creates a temporary directory and then
  # copies all necessary ICU4J and CLDR source code to here before building it:
  i18nutil.SwitchToNewTemporaryDirectory()
  build_dir = os.getcwd()
  cldr_build_dir = os.path.join(build_dir, 'cldr')
  icu4c_build_dir = os.path.join(build_dir, 'icu4c')
  icu4j_build_dir = os.path.join(build_dir, 'icu4j')
  icu_tools_build_dir = os.path.join(build_dir, 'icu_tools')

  print('Copying CLDR source code ...')
  shutil.copytree(cldr_dir, cldr_build_dir, symlinks=True)
  print('Copying ICU4C source code ...')
  shutil.copytree(os.path.join(icu_dir, 'icu4c'), icu4c_build_dir, symlinks=True)
  print('Copying ICU4J source code ...')
  shutil.copytree(os.path.join(icu_dir, 'icu4j'), icu4j_build_dir, symlinks=True)
  print('Copying ICU tools source code ...')
  shutil.copytree(os.path.join(icu_dir, 'tools'), icu_tools_build_dir, symlinks=True)

  # Setup environment variables for all subshell
  os.environ['ANT_OPTS'] = '-Xmx4096m'

  # This is the location of the original CLDR source tree (not the temporary
  # copy of the tools source code) from where the data files are to be read:
  os.environ['CLDR_DIR'] = cldr_build_dir  # os.path.join(os.getcwd(), 'cldr')

  os.environ['ICU4C_ROOT'] = icu4c_build_dir
  os.environ['ICU4J_ROOT'] = icu4j_build_dir
  os.environ['TOOLS_ROOT'] = icu_tools_build_dir
  cldr_tmp_dir = os.path.join(build_dir, 'cldr-staging')
  os.environ['CLDR_TMP_DIR'] = cldr_tmp_dir

  icu_tools_cldr_dir = os.path.join(icu_tools_build_dir, 'cldr')
  print('Installing CLDR tools ...')
  os.chdir(icu_tools_cldr_dir)
  subprocess.check_call(['ant', 'install-cldr-libs'])

  print('Building ICU data...')
  icu4c_data_build_dir = os.path.join(icu4c_build_dir, 'source/data')
  os.chdir(icu4c_data_build_dir)
  subprocess.check_call(['ant', 'cleanprod'])
  subprocess.check_call(['ant', 'setup'])
  subprocess.check_call(['ant', 'proddata'])

  # Finally we "compile" CLDR-data to a "production" form and place it in ICU
  os.chdir(os.path.join(icu_tools_build_dir, 'cldr', 'cldr-to-icu'))
  cldr_production_tmp_dir = os.path.join(cldr_tmp_dir, 'production')
  subprocess.check_call([
    'ant',
    '-f',
    'build-icu-data.xml',
    '-DcldrDataDir=' + cldr_production_tmp_dir,
    '-DforceDelete=true',
    '-DincludePseudoLocales=true'
  ])

  os.chdir(icu_tools_cldr_dir)
  subprocess.check_call([
    'ant',
    'copy-cldr-testdata',
  ])

  # Copy the generated data files from the temporary directory into AOSP.
  icu4c_data_source_dir = os.path.join(icu_dir, 'icu4c/source/data')
  rmAndCopyTree(icu4c_data_build_dir, icu4c_data_source_dir)
  rmAndCopyTree(
    os.path.join(icu4c_build_dir, 'source/test/testdata/cldr'),
    os.path.join(icu_dir, 'icu4c/source/test/testdata/cldr'))
  rmAndCopyTree(
    os.path.join(icu4j_build_dir, 'main/tests/core/src/com/ibm/icu/dev/data/cldr'),
    os.path.join(icu_dir, 'icu4j/main/tests/core/src/com/ibm/icu/dev/data/cldr'))

  localeCanonicalization_src = os.path.join(
    cldr_production_tmp_dir, 'common/testData/localeIdentifiers/localeCanonicalization.txt')
  shutil.copy(localeCanonicalization_src, os.path.join(
    icu_dir, 'icu4c/source/test/testdata/localeCanonicalization.txt'))
  shutil.copy(localeCanonicalization_src, os.path.join(
    icu_dir, 'icu4j/main/tests/core/src/com/ibm/icu/dev/data/unicode/localeCanonicalization.txt'))

  print('Look in %s for new data source files' % icu4c_data_source_dir)
  sys.exit(0)


def rmAndCopyTree(src, dst):
  if os.path.exists(dst):
    shutil.rmtree(dst)
  shutil.copytree(src, dst)


if __name__ == '__main__':
  main()
