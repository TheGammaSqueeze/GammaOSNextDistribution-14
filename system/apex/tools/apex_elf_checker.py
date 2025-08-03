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
"""apex_elf_checker checks if ELF files in the APEX

Usage: apex_elf_checker [--unwanted <names>] <apex>

  --unwanted <names>

    Fail if any of ELF files in APEX has any of unwanted names in NEEDED `
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile


_DYNAMIC_SECTION_NEEDED_PATTERN = re.compile(
    '^  0x[0-9a-fA-F]+\\s+NEEDED\\s+Shared library: \\[(.*)\\]$'
)


_ELF_MAGIC = b'\x7fELF'


def ParseArgs():
  parser = argparse.ArgumentParser()
  parser.add_argument('apex', help='Path to the APEX')
  parser.add_argument(
      '--tool_path',
      help='Tools are searched in TOOL_PATH/bin. Colon-separated list of paths',
  )
  parser.add_argument(
      '--unwanted',
      help='Names not allowed in DT_NEEDED. Colon-separated list of names',
  )
  return parser.parse_args()


def InitTools(tool_path):
  if tool_path is None:
    exec_path = os.path.realpath(sys.argv[0])
    if exec_path.endswith('.py'):
      script_name = os.path.basename(exec_path)[:-3]
      sys.exit(
          f'Do not invoke {exec_path} directly. Instead, use {script_name}'
      )
    tool_path = os.environ['PATH']

  def ToolPath(name):
    for p in tool_path.split(':'):
      path = os.path.join(p, name)
      if os.path.exists(path):
        return path
    sys.exit(f'Required tool({name}) not found in {tool_path}')

  return {
      tool: ToolPath(tool)
      for tool in [
          'deapexer',
          'debugfs_static',
          'fsck.erofs',
          'llvm-readelf',
      ]
  }


def IsElfFile(path):
  with open(path, 'rb') as f:
    buf = bytearray(len(_ELF_MAGIC))
    f.readinto(buf)
    return buf == _ELF_MAGIC


def ParseElfNeeded(path, tools):
  output = subprocess.check_output(
      [tools['llvm-readelf'], '-d', '--elf-output-style', 'LLVM', path],
      text=True,
      stderr=subprocess.PIPE,
  )

  needed = []
  for line in output.splitlines():
    match = _DYNAMIC_SECTION_NEEDED_PATTERN.match(line)
    if match:
      needed.append(match.group(1))
  return needed


def ScanElfFiles(work_dir):
  for parent, _, files in os.walk(work_dir):
    for file in files:
      path = os.path.join(parent, file)
      # Skip symlinks for APEXes with symlink optimization
      if os.path.islink(path):
        continue
      if IsElfFile(path):
        yield path


def CheckElfFiles(args, tools):
  with tempfile.TemporaryDirectory() as work_dir:
    subprocess.check_output(
        [
            tools['deapexer'],
            '--debugfs_path',
            tools['debugfs_static'],
            '--fsckerofs_path',
            tools['fsck.erofs'],
            'extract',
            args.apex,
            work_dir,
        ],
        text=True,
        stderr=subprocess.PIPE,
    )

    if args.unwanted:
      unwanted = set(args.unwanted.split(':'))
      for file in ScanElfFiles(work_dir):
        needed = set(ParseElfNeeded(file, tools))
        if unwanted & needed:
          sys.exit(
              f'{os.path.relpath(file, work_dir)} has unwanted NEEDED:'
              f' {",".join(unwanted & needed)}'
          )


def main():
  args = ParseArgs()
  tools = InitTools(args.tool_path)
  try:
    CheckElfFiles(args, tools)
  except subprocess.CalledProcessError as e:
    sys.exit('Result:' + str(e.stderr))


if __name__ == '__main__':
  main()
