#!/usr/bin/python3
#
# Copyright (C) 2024 The Android Open Source Project
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
import os
import shlex
import subprocess
import tempfile

from build_chd_utils import copy_files, unzip_otatools

"""Builds a vendor_boot-chd_debug.img.

The vendor_boot-chd_debug.img is built by adding those CHD specific debugging
files to a Cuttlefish's vendor_boot-debug.img, using a new ramdisk fragment.

Test command:
python3 tools/treble/cuttlefish/build_chd_debug_ramdisk.py \
    $ANDROID_PRODUCT_OUT/vendor_boot-debug.img \
    -o $ANDROID_PRODUCT_OUT/vendor_boot-chd_debug.img \
    --otatools_zip $ANDROID_PRODUCT_OUT/otatools.zip \
    --add_file chd_debug.prop:adb_debug.prop
"""

# The value of ramdisk type needs to be synchronized with
# `system/tools/mkbootimg/mkbootimg.py`. We choose `_PLATFORM` here because the
# CHD debug ramdisk will be used in normal boot (not for _RECOVERY or _DLKM).
_VENDOR_RAMDISK_TYPE_PLATFORM = '1'


def _parse_args():
  """Parse the arguments for building the chd debug ramdisk.

  Returns:
    An object of argparse.Namespace.
  """
  parser = argparse.ArgumentParser()
  parser.add_argument('input_img',
                      help='The input Cuttlefish vendor boot debug image.')
  parser.add_argument('--output_img', '-o', required=True,
                      help='The output CHD vendor boot debug image.')
  parser.add_argument('--otatools_zip', required=True,
                      help='Path to the otatools.zip.')
  parser.add_argument('--add_file', action='append', default=[],
                      help='The file to be added to the CHD debug ramdisk. '
                           'The format is <src path>:<dst path>.')
  return parser.parse_args()


class BootImage:
  """A class that supports adding a new ramdisk fragment into a boot.img."""

  def __init__(self, bootimg, bootimg_dir, unpack_bootimg_bin, mkbootfs_bin,
               mkbootimg_bin, lz4_bin):
    self._bootimg = bootimg
    self._bootimg_dir = bootimg_dir
    self._unpack_bootimg_bin = unpack_bootimg_bin
    self._mkbootfs_bin = mkbootfs_bin
    self._mkbootimg_bin = mkbootimg_bin
    self._lz4_bin = lz4_bin
    self._bootimg_args = []

  def unpack(self):
    """Unpacks the boot.img and capture the bootimg arguments."""
    if self._bootimg_args:
      raise RuntimeError(f'cannot unpack {self._bootimg} twice')
    print(f'Unpacking {self._bootimg} to {self._bootimg_dir}')
    unpack_cmd = [
        self._unpack_bootimg_bin,
        '--boot_img', self._bootimg,
        '--out', self._bootimg_dir,
        '--format', 'mkbootimg'
    ]
    unpack_result = subprocess.run(unpack_cmd, check=True,
                                   capture_output=True, encoding='utf-8')
    self._bootimg_args = shlex.split(unpack_result.stdout)

  def add_ramdisk(self, ramdisk_root):
    """Adds a new ramdisk fragment and update the bootimg arguments."""
    # Name the new ramdisk using the smallest unused index.
    ramdisk_files = [file for file in os.listdir(self._bootimg_dir)
                     if file.startswith('vendor_ramdisk')]
    new_ramdisk_name = f'vendor_ramdisk{len(ramdisk_files):02d}'
    new_ramdisk_file = os.path.join(self._bootimg_dir, new_ramdisk_name)
    if os.path.exists(new_ramdisk_file):
      raise FileExistsError(f'{new_ramdisk_file} already exists')

    print(f'Adding a new vendor ramdisk fragment {new_ramdisk_file}')
    mkbootfs_cmd = [self._mkbootfs_bin, ramdisk_root]
    mkbootfs_result = subprocess.run(mkbootfs_cmd, check=True,
                                     capture_output=True)

    compress_cmd = [self._lz4_bin, '-l', '-12', '--favor-decSpeed']
    with open(new_ramdisk_file, 'w') as o:
      subprocess.run(compress_cmd, check=True,
                     input=mkbootfs_result.stdout, stdout=o)

    # Update the bootimg arguments to include the new ramdisk file.
    self._bootimg_args.extend([
        '--ramdisk_type', _VENDOR_RAMDISK_TYPE_PLATFORM,
        '--ramdisk_name', 'chd',
        '--vendor_ramdisk_fragment', new_ramdisk_file
    ])

  def pack(self, output_img):
    """Packs the boot.img."""
    print(f'Packing {output_img} with args: {self._bootimg_args}')
    mkbootimg_cmd = [
        self._mkbootimg_bin, '--vendor_boot', output_img
    ] + self._bootimg_args
    subprocess.check_call(mkbootimg_cmd)


def _prepare_env(otatools_dir):
  """Get the executable path of the required otatools.

  We need `unpack_bootimg`, `mkbootfs`, `mkbootimg` and `lz4` for building CHD
  debug ramdisk. This function returns the path to the above tools in order.

  Args:
    otatools_dir: The path to the otatools directory.

  Raises:
    FileNotFoundError if any required otatool does not exist.
  """
  tools_path = []
  for tool_name in ['unpack_bootimg', 'mkbootfs', 'mkbootimg', 'lz4']:
    tool_path = os.path.join(otatools_dir, 'bin', tool_name)
    if not os.path.exists(tool_path):
      raise FileNotFoundError(f'otatool {tool_path} does not exist')
    tools_path.append(tool_path)
  return tools_path


def add_debug_ramdisk_files(input_image, files_to_add, otatools_dir, temp_dir,
                            output_image):
  """Add files to a vendor boot debug image.

  This function creates a new ramdisk fragment, add this fragment into the
  input vendor boot debug image, and generate an output image.

  Args:
    input_image: The path to the input vendor boot debug image.
    files_to_add: A list of files to be added in the debug ramdisk, where a
                  pair defines the src and dst path of each file.
    otatools_dir: The path to the otatools directory.
    temp_dir: The path to the temporary directory for ramdisk filesystem.
    output_img: The path to the output vendor boot debug image.

  Raises:
    FileExistsError if having duplicated ramdisk fragments.
    FileNotFoundError if any required otatool does not exist.
  """
  print(f'Adding {files_to_add} to {input_image}')
  ramdisk_root = os.path.join(temp_dir, 'ramdisk_root')
  os.mkdir(ramdisk_root)
  copy_files(files_to_add, ramdisk_root)

  bootimg_dir = os.path.join(temp_dir, 'bootimg')
  unpack_bootimg, mkbootfs, mkbootimg, lz4 = _prepare_env(otatools_dir)
  bootimg = BootImage(input_image, bootimg_dir, unpack_bootimg, mkbootfs,
                      mkbootimg, lz4)
  bootimg.unpack()
  bootimg.add_ramdisk(ramdisk_root)
  bootimg.pack(output_image)


def main(temp_dir):
  args = _parse_args()
  otatools_dir = os.path.join(temp_dir, 'otatools')
  unzip_otatools(args.otatools_zip, otatools_dir, [
      'bin/unpack_bootimg', 'bin/mkbootfs', 'bin/mkbootimg', 'bin/lz4',
      'lib64/*'
  ])
  add_debug_ramdisk_files(args.input_img, args.add_file, otatools_dir,
                          temp_dir, args.output_img)


if __name__ == '__main__':
  with tempfile.TemporaryDirectory() as temp_dir:
    main(temp_dir)
