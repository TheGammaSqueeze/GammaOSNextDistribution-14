#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

"""File handler that reads target files."""

from abstract_build_file_handler import *
from zipfile import ZipFile

class TargetFileHandler(AbstractBuildFileHandler):
  """Reads file's content from target files."""

  def __init__(self, build_file):
    super().__init__(build_file)

  def get_file_hash(self, file_names, hash_func=None):
    """See base class."""
    hash_dict = dict()
    with ZipFile(self.build_file) as zip_file:
      for file_name in file_names:
        # Convert top directory's name to upper case.
        idx = file_name[1:].find('/')+1
        real_file_name = file_name[1:idx].upper()+file_name[idx:]
        if real_file_name not in zip_file.namelist():
          continue
        with zip_file.open(real_file_name) as f:
          if hash_func:
            hash_dict[file_name] = hash_func(f.read())
          else:
            hash_dict[file_name] = hash(f.read())
    return hash_dict

  def get_system_fingerprint(self):
    """See base class."""
    fingerprint = ''
    with ZipFile(self.build_file) as zip_file:
      with zip_file.open('SYSTEM/build.prop') as build_prop:
        for line in build_prop:
          line = line.decode('UTF-8')
          if 'build.fingerprint' not in line:
            continue
          fingerprint = line.split('=')[1].strip()
          break
    return fingerprint
