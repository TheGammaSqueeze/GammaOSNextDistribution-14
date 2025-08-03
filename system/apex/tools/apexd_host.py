#!/usr/bin/env python
#
# Copyright (C) 2023 The Android Open Source Project
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
"""apexd_host simulates apexd on host.

Basically the tool scans .apex/.capex files from given directories
(e.g --system_path) and extracts them under the output directory (--apex_path)
using the deapexer tool. It also generates apex-info-list.xml file because
some tools need that file as well to know the partitions of APEX files.

This can be used when handling APEX files on host at buildtime or with
target-files. For example, check_target_files_vintf tool invokes checkvintf with
target-files, which, in turn, needs to read VINTF fragment files in APEX files.
Hence, check_target_files_vintf can use apexd_host before checkvintf.

Example:
    $ apexd_host --apex_path /path/to/apex --system_path /path/to/system
"""
from __future__ import print_function

import argparse
import glob
import os
import subprocess
import sys
from xml.dom import minidom

import apex_manifest


# This should be in sync with kApexPackageBuiltinDirs in
# system/apex/apexd/apex_constants.h
PARTITIONS = ['system', 'system_ext', 'product', 'vendor']


def DirectoryType(path):
  if not os.path.exists(path):
    return None
  if not os.path.isdir(path):
    raise argparse.ArgumentTypeError(f'{path} is not a directory')
  return os.path.realpath(path)


def ExistentDirectoryType(path):
  if not os.path.exists(path):
    raise argparse.ArgumentTypeError(f'{path} is not found')
  return DirectoryType(path)


def ParseArgs():
  parser = argparse.ArgumentParser()
  parser.add_argument('--tool_path', help='Tools are searched in TOOL_PATH/bin')
  parser.add_argument(
      '--apex_path',
      required=True,
      type=ExistentDirectoryType,
      help='Path to the directory where to activate APEXes',
  )
  for part in PARTITIONS:
    parser.add_argument(
        f'--{part}_path',
        help=f'Path to the directory corresponding /{part} on device',
        type=DirectoryType,
    )
  return parser.parse_args()


class ApexFile(object):
  """Represents an APEX file."""

  def __init__(self, path_on_host, path_on_device):
    self._path_on_host = path_on_host
    self._path_on_device = path_on_device
    self._manifest = apex_manifest.fromApex(path_on_host)

  @property
  def name(self):
    return self._manifest.name

  @property
  def path_on_host(self):
    return self._path_on_host

  @property
  def path_on_device(self):
    return self._path_on_device

  # Helper to create apex-info element
  @property
  def attrs(self):
    return {
        'moduleName': self.name,
        'modulePath': self.path_on_device,
        'preinstalledModulePath': self.path_on_device,
        'versionCode': str(self._manifest.version),
        'versionName': self._manifest.versionName,
        'isFactory': 'true',
        'isActive': 'true',
        'provideSharedApexLibs': (
            'true' if self._manifest.provideSharedApexLibs else 'false'
        ),
    }


def InitTools(tool_path):
  if tool_path is None:
    exec_path = os.path.realpath(sys.argv[0])
    if exec_path.endswith('.py'):
      script_name = os.path.basename(exec_path)[:-3]
      sys.exit(
          f'Do not invoke {exec_path} directly. Instead, use {script_name}'
      )
    tool_path = os.path.dirname(os.path.dirname(exec_path))

  def ToolPath(name):
    path = os.path.join(tool_path, 'bin', name)
    if not os.path.exists(path):
      sys.exit(f'Required tool({name}) not found in {tool_path}')
    return path

  return {
      tool: ToolPath(tool)
      for tool in [
          'deapexer',
          'debugfs_static',
          'fsck.erofs',
      ]
  }


def ScanApexes(partition, real_path) -> list[ApexFile]:
  apexes = []
  for path_on_host in glob.glob(
      os.path.join(real_path, 'apex/*.apex')
  ) + glob.glob(os.path.join(real_path, 'apex/*.capex')):
    path_on_device = f'/{partition}/apex/' + os.path.basename(path_on_host)
    apexes.append(ApexFile(path_on_host, path_on_device))
  # sort list for stability
  return sorted(apexes, key=lambda apex: apex.path_on_device)


def ActivateApexes(partitions, apex_dir, tools):
  # Emit apex-info-list.xml
  impl = minidom.getDOMImplementation()
  doc = impl.createDocument(None, 'apex-info-list', None)
  apex_info_list = doc.documentElement

  # Scan each partition for apexes and activate them
  for partition, real_path in partitions.items():
    apexes = ScanApexes(partition, real_path)

    # Activate each apex with deapexer
    for apex_file in apexes:
      # Multi-apex is ignored
      if os.path.exists(os.path.join(apex_dir, apex_file.name)):
        continue

      cmd = [tools['deapexer']]
      cmd += ['--debugfs_path', tools['debugfs_static']]
      cmd += ['--fsckerofs_path', tools['fsck.erofs']]
      cmd += [
          'extract',
          apex_file.path_on_host,
          os.path.join(apex_dir, apex_file.name),
      ]
      subprocess.check_output(cmd, text=True, stderr=subprocess.STDOUT)

      # Add activated apex_info to apex_info_list
      apex_info = doc.createElement('apex-info')
      for name, value in apex_file.attrs.items():
        apex_info.setAttribute(name, value)
      apex_info_list.appendChild(apex_info)

  apex_info_list_file = os.path.join(apex_dir, 'apex-info-list.xml')
  with open(apex_info_list_file, 'wt', encoding='utf-8') as f:
    doc.writexml(f, encoding='utf-8', addindent='  ', newl='\n')


def main():
  args = ParseArgs()

  partitions = {}
  for part in PARTITIONS:
    if vars(args).get(f'{part}_path'):
      partitions[part] = vars(args).get(f'{part}_path')

  tools = InitTools(args.tool_path)
  ActivateApexes(partitions, args.apex_path, tools)


if __name__ == '__main__':
  main()
