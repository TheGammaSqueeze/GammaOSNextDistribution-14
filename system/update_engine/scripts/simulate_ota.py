#!/usr/bin/env python3
#
# Copyright (C) 2020 The Android Open Source Project
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
#

"""Tools for running host side simulation of an OTA update."""


import argparse
import filecmp
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

import update_payload


def extract_file(zip_file_path, entry_name, target_file_path):
  """Extract a file from zip archive into |target_file_path|"""
  with open(target_file_path, 'wb') as out_fp:
    if isinstance(zip_file_path, zipfile.ZipFile):
      with zip_file_path.open(entry_name) as fp:
        shutil.copyfileobj(fp, out_fp)
    elif os.path.isdir(zip_file_path):
      with open(os.path.join(zip_file_path, entry_name), "rb") as fp:
        shutil.copyfileobj(fp, out_fp)


def is_sparse_image(filepath):
  with open(filepath, 'rb') as fp:
    # Magic for android sparse image format
    # https://source.android.com/devices/bootloader/images
    return fp.read(4) == b'\x3A\xFF\x26\xED'


def extract_img(zip_archive: zipfile.ZipFile, img_name, output_path, is_source):
  """ Extract and unsparse partition image from zip archive """
  entry_name = "IMAGES/" + img_name + ".img"
  try:
    extract_file(zip_archive, entry_name, output_path)
  except (KeyError, FileNotFoundError) as e:
    print("Faild to extract", img_name, "from IMAGES/ dir, trying RADIO/", e)
    extract_file(zip_archive, "RADIO/" + img_name + ".img", output_path)
  if is_sparse_image(output_path):
    raw_img_path = output_path + ".raw"
    subprocess.check_output(["simg2img", output_path, raw_img_path])
    os.rename(raw_img_path, output_path)

  # delta_generator only supports images multiple of 4 KiB. For target images
  # we pad the data with zeros if needed, but for source images we truncate
  # down the data since the last block of the old image could be padded on
  # disk with unknown data.
  file_size = os.path.getsize(output_path)
  if file_size % 4096 != 0:
    if is_source:
      print("Rounding DOWN partition {} to a multiple of 4 KiB."
            .format(output_path))
      file_size = file_size & -4096
    else:
      print("Rounding UP partition {} to a multiple of 4 KiB."
            .format(output_path))
      file_size = (file_size + 4095) & -4096
    with open(output_path, 'a') as f:
      f.truncate(file_size)

def run_ota(source, target, payload_path, tempdir, output_dir):
  """Run an OTA on host side"""
  payload = update_payload.Payload(payload_path)
  payload.Init()
  if source and zipfile.is_zipfile(source):
    source = zipfile.ZipFile(source)
  if target and zipfile.is_zipfile(target):
    target = zipfile.ZipFile(target)
  source_exist = source and (isinstance(
      source, zipfile.ZipFile) or os.path.exists(source))
  target_exist = target and (isinstance(
      target, zipfile.ZipFile) or os.path.exists(target))

  old_partitions = []
  new_partitions = []
  expected_new_partitions = []
  for part in payload.manifest.partitions:
    name = part.partition_name
    old_image = os.path.join(tempdir, "source_" + name + ".img")
    new_image = os.path.join(tempdir, "target_" + name + ".img")
    if part.HasField("old_partition_info"):
      assert source_exist, \
          "source target file must point to a valid zipfile or directory " + \
          source
      print("Extracting source image for", name)
      extract_img(source, name, old_image, True)
    if target_exist:
      print("Extracting target image for", name)
      extract_img(target, name, new_image, False)

    old_partitions.append(old_image)
    scratch_image_name = new_image + ".actual"
    new_partitions.append(scratch_image_name)
    with open(scratch_image_name, "wb") as fp:
      fp.truncate(part.new_partition_info.size)
    expected_new_partitions.append(new_image)

  delta_generator_args = ["delta_generator", "--in_file=" + payload_path]
  partition_names = [
      part.partition_name for part in payload.manifest.partitions
  ]
  if payload.manifest.partial_update:
    delta_generator_args.append("--is_partial_update")
  if payload.is_incremental:
    delta_generator_args.append("--old_partitions=" + ":".join(old_partitions))
  delta_generator_args.append("--partition_names=" + ":".join(partition_names))
  delta_generator_args.append("--new_partitions=" + ":".join(new_partitions))

  print("Running ", " ".join(delta_generator_args))
  subprocess.check_output(delta_generator_args)

  valid = True
  if not target_exist:
    for part in new_partitions:
      print("Output written to", part)
      shutil.copy(part, output_dir)
    return
  for (expected_part, actual_part, part_name) in \
          zip(expected_new_partitions, new_partitions, partition_names):
    if filecmp.cmp(expected_part, actual_part):
      print("Partition `{}` is valid".format(part_name))
    else:
      valid = False
      print(
          "Partition `{}` is INVALID expected image: {} actual image: {}"
          .format(part_name, expected_part, actual_part))

  if not valid and sys.stdout.isatty():
    input("Paused to investigate invalid partitions, press any key to exit.")


def main():
  parser = argparse.ArgumentParser(
      description="Run host side simulation of OTA package")
  parser.add_argument(
      "--source",
      help="Target file zip for the source build",
      required=False)
  parser.add_argument(
      "--target",
      help="Target file zip for the target build",
      required=False)
  parser.add_argument(
      "-o",
      dest="output_dir",
      help="Output directory to put all images, current directory by default"
  )
  parser.add_argument(
      "payload",
      help="payload.bin for the OTA package, or a zip of OTA package itself",
      nargs=1)
  args = parser.parse_args()
  print(args)

  # pylint: disable=no-member
  with tempfile.TemporaryDirectory() as tempdir:
    payload_path = args.payload[0]
    if zipfile.is_zipfile(payload_path):
      with zipfile.ZipFile(payload_path, "r") as zfp:
        payload_entry_name = 'payload.bin'
        zfp.extract(payload_entry_name, tempdir)
        payload_path = os.path.join(tempdir, payload_entry_name)
    if args.output_dir is None:
      args.output_dir = "."
    if not os.path.exists(args.output_dir):
      os.makedirs(args.output_dir, exist_ok=True)
    assert os.path.isdir(args.output_dir)
    run_ota(args.source, args.target, payload_path, tempdir, args.output_dir)


if __name__ == '__main__':
  main()
