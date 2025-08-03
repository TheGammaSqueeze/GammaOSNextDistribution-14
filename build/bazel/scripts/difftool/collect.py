#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Copies ninja file information to another directory for processing.

Usage:
  ./collect.py [ninja_file] [dest_directory]

This script should be used as in preparation for further analysis
using difftool.py. See directory-level README for details.
"""

import argparse
import os
import pathlib
import shutil


COLLECTION_INFO_FILENAME = "collection_info"


def subninja_files(ninja_file_path):
  result = []
  with ninja_file_path.open() as f:
    for line in f:
      if line.startswith("subninja "):
        result += [line[len("subninja "):].strip()]
  return result


def main():
  parser = argparse.ArgumentParser(description="")
  parser.add_argument("ninja_file",
                      help="the path to the root ninja file of the build " +
                           "to be analyzed. Ex: out/combined-aosp_flame.ninja")
  parser.add_argument("dest_directory",
                      help="directory to copy build-related information for " +
                           "later difftool comparison. Ex: /tmp/buildArtifacts")
  # TODO(usta): enable multiple files or even a glob to be specified
  parser.add_argument("--file", dest="output_file", default=None,
                      help="the path to the output artifact to be analyzed. " +
                           "Ex: out/path/to/foo.so")
  args = parser.parse_args()
  dest = args.dest_directory

  if not os.path.isdir(dest):
    raise Exception("invalid destination directory " + dest)

  collection_info_filepath = ""
  if args.output_file is not None:
    output_file = pathlib.Path(args.output_file)
    if not output_file.is_file():
      raise Exception("Expected file %s was not found. " % output_file)
    output_file_dest = pathlib.Path(dest).joinpath(output_file)
    output_file_dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(output_file, output_file_dest)
    collection_info_filepath = str(output_file)

  ninja_file = pathlib.Path(args.ninja_file)
  main_ninja_basename = ninja_file.name
  shutil.copy2(args.ninja_file, os.path.join(dest, main_ninja_basename))

  for subninja_file in subninja_files(ninja_file):
    parent_dir = pathlib.Path(subninja_file).parent
    dest_dir = os.path.join(dest, parent_dir)
    pathlib.Path(dest_dir).mkdir(parents=True, exist_ok=True)
    shutil.copy2(subninja_file, os.path.join(dest, subninja_file))

  collection_info = main_ninja_basename + "\n" + collection_info_filepath
  pathlib.Path(dest).joinpath(COLLECTION_INFO_FILENAME).write_text(collection_info)


if __name__ == "__main__":
  main()
