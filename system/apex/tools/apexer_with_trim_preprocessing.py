#!/usr/bin/env python3

"""This is a wrapper function of apexer. It provides opportunity to do
some artifact preprocessing before calling into apexer. Some of these
artifact preprocessing are difficult or impossible to do in soong or
bazel such as trimming the apex native shared libs. It is better to do
these in a binary so that the preprocessing logic can be reused regardless
of the build system
"""

import argparse
from glob import glob
import os
import re
import shutil
import sys
import tempfile

import apex_manifest_pb2
import apexer_wrapper_utils

def ParseArgs(argv):
  parser = argparse.ArgumentParser(
      description='wrapper to run apexer with native shared lib trimming')
  parser.add_argument(
      '--apexer',
      help='path to apexer binary')
  parser.add_argument(
      '--canned_fs_config',
      help='path to canned_fs_config file')
  parser.add_argument(
      '--manifest',
      help='path to apex_manifest.pb file')
  parser.add_argument(
      '--libs_to_trim',
      help='native shared libraries to trim')
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

def TrimNativeSharedLibs(image_dir: str, canned_fs_config: str,
                         manifest: str, libs_to_trim: list[str]):
  """Place native shared libs for trimmed variant in a special way.

  Traditional apex has native shared libs placed under /lib(64)? inside
  the apex. However, for trimmed variant, for the libs to trim, they will
  be replaced with a sym link to

  /apex/sharedlibs/lib(64)?/foo.so/[sha512 foo.so]/foo.so
  """

  libs_trimmed = set()
  with open(canned_fs_config, 'r') as f:
    lines = f.readlines()
  for line in lines:
    segs = line.split(' ')
    if segs[0].endswith('.so'):
      if any(segs[0].endswith(v) for v in libs_to_trim):
        lib_relative_path = segs[0][1:]
        lib_absolute_path = os.path.join(image_dir, lib_relative_path)
        lib_name = os.path.basename(lib_relative_path)
        libs_trimmed.add(lib_name)
        digest = apexer_wrapper_utils.GetDigest(lib_absolute_path)
        os.remove(lib_absolute_path)
        link = os.path.join('/apex/sharedlibs', lib_relative_path, digest, lib_name)
        os.symlink(link, lib_absolute_path)

  manifest_pb = apex_manifest_pb2.ApexManifest()
  with open(manifest, 'rb') as f:
    manifest_pb.ParseFromString(f.read())

  # bump version code
  # google mainline module logic, for trimmed variant, the variant digit is 2
  manifest_pb.version = 10*(manifest_pb.version // 10) + 2

  # setting requireSharedApexLibs
  del manifest_pb.requireSharedApexLibs[:]
  manifest_pb.requireSharedApexLibs.extend(
    sorted(re.sub(r':.{128}$', ':sha-512', lib)
           for lib in libs_trimmed))

  # write back to root apex_manifest.pb
  with open(manifest, 'wb') as f:
    f.write(manifest_pb.SerializeToString())

def main(argv):
  args = ParseArgs(argv)
  segs = args.libs_to_trim.split(',')
  libs_to_trim = [v+'.so' for v in args.libs_to_trim.split(',')]
  TrimNativeSharedLibs(args.input_dir, args.canned_fs_config, args.manifest,
                       libs_to_trim)
  cmd = [args.apexer, '--canned_fs_config', args.canned_fs_config]
  cmd.extend(['--manifest', args.manifest])
  cmd.extend(args.rest_args)
  cmd.extend([args.input_dir, args.output])

  apexer_wrapper_utils.RunCommand(cmd)

if __name__ == "__main__":
 main(sys.argv[1:])
