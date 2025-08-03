#!/usr/bin/env python3
#
# Copyright 2022 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
`fsverity_manifest_generator` generates the a manifest file containing digests
of target files.
"""

import argparse
import os
import subprocess
import sys
from fsverity_digests_pb2 import FSVerityDigests

HASH_ALGORITHM = 'sha256'

def _digest(fsverity_path, input_file):
  cmd = [fsverity_path, 'digest', input_file]
  cmd.extend(['--compact'])
  cmd.extend(['--hash-alg', HASH_ALGORITHM])
  out = subprocess.check_output(cmd, universal_newlines=True).strip()
  return bytes(bytearray.fromhex(out))

if __name__ == '__main__':
  p = argparse.ArgumentParser()
  p.add_argument(
      '--output',
      help='Path to the output manifest',
      required=True)
  p.add_argument(
      '--fsverity-path',
      help='path to the fsverity program',
      required=True)
  p.add_argument(
      '--base-dir',
      help='directory to use as a relative root for the inputs',
      required=True)
  p.add_argument(
      'inputs',
      nargs='*',
      help='input file for the build manifest')
  args = p.parse_args(sys.argv[1:])

  digests = FSVerityDigests()
  for f in sorted(args.inputs):
    # f is a full path for now; make it relative so it starts with {mount_point}/
    digest = digests.digests[os.path.relpath(f, args.base_dir)]
    digest.digest = _digest(args.fsverity_path, f)
    digest.hash_alg = HASH_ALGORITHM

  manifest = digests.SerializeToString()

  with open(args.output, "wb") as f:
    f.write(manifest)
