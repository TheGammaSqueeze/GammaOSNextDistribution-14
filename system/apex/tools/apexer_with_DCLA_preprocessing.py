#!/usr/bin/env python3

"""This is a wrapper function of apexer. It provides opportunity to do
some artifact preprocessing before calling into apexer. Some of these
artifact preprocessing are difficult or impossible to do in soong or
bazel such as placing native shared libs in DCLA. It is better to do
these in a binary so that the DCLA preprocessing logic can be reused
regardless of the build system
"""

import argparse
from glob import glob
import os
import shutil
import sys
import tempfile

import apexer_wrapper_utils

def ParseArgs(argv):
  parser = argparse.ArgumentParser(
      description='wrapper to run apexer for DCLA')
  parser.add_argument(
      '--apexer',
      help='path to apexer binary')
  parser.add_argument(
      '--canned_fs_config',
      help='path to canned_fs_config file')
  parser.add_argument(
      'input_dir',
      metavar='INPUT_DIR',
      help='the directory having files to be packaged')
  parser.add_argument(
      'output',
      metavar='OUTPUT',
      help='name of the APEX file')
  parser.add_argument(
      'rest_args',
      nargs='*',
      help='remaining flags that will be passed as-is to apexer')
  return parser.parse_args(argv)

def PlaceDCLANativeSharedLibs(image_dir: str, canned_fs_config: str) -> str:
  """Place native shared libs for DCLA in a special way.

  Traditional apex has native shared libs placed under /lib(64)? inside
  the apex. However, for DCLA, it needs to be placed in a special way:

  /lib(64)?/foo.so/<sha512 foo.so>/foo.so

  This function moves the shared libs to desired location and update
  canned_fs_config file accordingly
  """

  # remove all .so entries from canned_fs_config
  parent_dir = os.path.dirname(canned_fs_config)
  updated_canned_fs_config = os.path.join(parent_dir, 'updated_canned_fs_config')
  with open(canned_fs_config, 'r') as f:
    lines = f.readlines()
  with open(updated_canned_fs_config, 'w') as f:
    for line in lines:
      segs = line.split(' ')
      if not segs[0].endswith('.so'):
        f.write(line)
      else:
        with tempfile.TemporaryDirectory() as tmp_dir:
          # move native libs
          lib_file = os.path.join(image_dir, segs[0][1:])
          digest = apexer_wrapper_utils.GetDigest(lib_file)
          lib_name = os.path.basename(lib_file)
          dest_dir = os.path.join(lib_file, digest)

          shutil.move(lib_file, os.path.join(tmp_dir, lib_name))
          os.makedirs(dest_dir, exist_ok=True)
          shutil.move(os.path.join(tmp_dir, lib_name),
                      os.path.join(dest_dir, lib_name))

          # add canned_fs_config entries
          f.write(f'{segs[0]} 0 2000 0755\n')
          f.write(f'{os.path.join(segs[0], digest)} 0 2000 0755\n')
          f.write(f'{os.path.join(segs[0], digest, lib_name)} 1000 1000 0644\n')

  # return the modified canned_fs_config
  return updated_canned_fs_config

def main(argv):
  args = ParseArgs(argv)
  args.canned_fs_config = PlaceDCLANativeSharedLibs(
      args.input_dir, args.canned_fs_config)

  cmd = [args.apexer, '--canned_fs_config', args.canned_fs_config]
  cmd.extend(args.rest_args)
  cmd.extend([args.input_dir, args.output])

  apexer_wrapper_utils.RunCommand(cmd)

if __name__ == "__main__":
 main(sys.argv[1:])
