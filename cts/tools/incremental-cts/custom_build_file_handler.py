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
"""Implement this class to read your build file."""

from abstract_build_file_handler import *

class CustomBuildFileHandler(AbstractBuildFileHandler):
  """Custom handler."""

  def __init__(self, build_file):
    super().__init__(build_file)

  def get_file_hash(self, file_names, hash_func=None):
    """See base class."""
    raise NotImplementedError('You need to implement get_file_hash function.')

  def get_system_fingerprint(self):
    """See base class."""
    raise NotImplementedError('You need to implement get_system_fingerprint function.')

