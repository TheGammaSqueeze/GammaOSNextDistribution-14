#!/usr/bin/python3
#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import argparse
import glob
import os
import subprocess
import tempfile

from build_chd_debug_ramdisk import add_debug_ramdisk_files
from build_chd_utils import copy_files, merge_chd_sepolicy, unzip_otatools

"""Test command:

WORKSPACE=out/dist && \
python3 tools/treble/cuttlefish/build_cf_hybrid_device.py \
    --build_id 123456 \
    --otatools_zip $WORKSPACE/otatools.zip \
    --target chd-target \
    --output_dir $WORKSPACE \
    --framework_target_files_zip $WORKSPACE/device-target_files-*.zip \
    --vendor_target_files_zip $WORKSPACE/cf_arm64_only_phone-target_files-*.zip
"""


def _parse_args():
  """Parse the arguments for building cuttlefish hybrid devices.

  Returns:
    An object of argparse.Namespace.
  """
  parser = argparse.ArgumentParser()

  parser.add_argument('--build_id', required=True,
                      help='Build id.')
  parser.add_argument('--target', required=True,
                      help='Target name of the cuttlefish hybrid build.')
  parser.add_argument('--otatools_zip', required=True,
                      help='Path to the otatools.zip.')
  parser.add_argument('--output_dir', required=True,
                      help='Path to the output directory of the hybrid build.')
  parser.add_argument('--framework_target_files_zip', required=True,
                      help='glob pattern of framework target_files zip.')
  parser.add_argument('--vendor_target_files_zip', required=True,
                      help='glob pattern of vendor target_files zip.')
  parser.add_argument('--copy_file', action='append', default=[],
                      help='The file to be copied to output directory. '
                           'The format is <src glob pattern>:<dst path>.')
  return parser.parse_args()


def run(temp_dir):
  args = _parse_args()

  # unzip otatools
  otatools = os.path.join(temp_dir, 'otatools')
  unzip_otatools(args.otatools_zip, otatools)

  # get framework and vendor target files
  matched_framework_target_files = glob.glob(args.framework_target_files_zip)
  if not matched_framework_target_files:
    raise ValueError('framework target files zip '
                     f'{args.framework_target_files_zip} not found.')
  matched_vendor_target_files = glob.glob(args.vendor_target_files_zip)
  if not matched_vendor_target_files:
    raise ValueError('vendor target files zip '
                     f'{args.vendor_target_files_zip} not found.')

  # merge target files
  framework_target_files = matched_framework_target_files[0]
  vendor_target_files = matched_vendor_target_files[0]
  merged_target_files = os.path.join(
      args.output_dir,
      f'{args.target}-target_files-{args.build_id}.zip')
  command = [
      os.path.join(otatools, 'bin', 'merge_target_files'),
      '--path', otatools,
      '--framework-target-files', framework_target_files,
      '--vendor-target-files', vendor_target_files,
      '--output-target-files', merged_target_files,
      '--avb-resolve-rollback-index-location-conflict'
  ]
  subprocess.run(command, check=True)

  # create images from the merged target files
  img_zip_path = os.path.join(args.output_dir,
                              f'{args.target}-img-{args.build_id}.zip')
  command = [
      os.path.join(otatools, 'bin', 'img_from_target_files'),
      merged_target_files,
      img_zip_path]
  subprocess.run(command, check=True)

  # merge CHD debug sepolicy
  # TODO (b/315474132): remove this when the CHD sepolicy issue is resolved.
  chd_sepolicy = None
  try:
    chd_sepolicy = merge_chd_sepolicy(
        framework_target_files, vendor_target_files, otatools, args.output_dir)
  except Exception as error:
    print(f'Warning - cannot generate chd_merged_sepolicy: {error}')

  # copy files
  copy_files(args.copy_file, args.output_dir)

  # build the CHD vendor boot debug image by adding chd_sepolicy and
  # chd_debug_prop (if present) into the Cuttlefish's vendor_boot-debug.img.
  files_to_add = []
  if chd_sepolicy and os.path.exists(chd_sepolicy):
    files_to_add.append(f'{chd_sepolicy}:precompiled_sepolicy')
  chd_debug_prop = os.path.join(args.output_dir, 'chd_debug.prop')
  if os.path.exists(chd_debug_prop):
    # rename the debug prop file as `adb_debug.prop` because this is the
    # file name that property init expects.
    files_to_add.append(f'{chd_debug_prop}:adb_debug.prop')

  cf_debug_img = os.path.join(args.output_dir, 'vendor_boot-debug.img')
  if files_to_add and os.path.exists(cf_debug_img):
    chd_debug_img = os.path.join(args.output_dir, 'vendor_boot-chd_debug.img')
    try:
      add_debug_ramdisk_files(
          cf_debug_img, files_to_add, otatools, temp_dir, chd_debug_img)
    except Exception as error:
      print(f'Warning - cannot build {chd_debug_img}: {error}')


if __name__ == '__main__':
  with tempfile.TemporaryDirectory() as temp_dir:
    run(temp_dir)
