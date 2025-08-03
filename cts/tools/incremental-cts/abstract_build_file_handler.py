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

"""Abstract class that reads build file."""

class AbstractBuildFileHandler(object):
  """Build file handler interface."""

  def __init__(self, build_file):
    self.build_file = build_file

  def get_file_hash(self, file_names, hash_func=None):
    """Get hash value of file's content.

    Args:
      file_names: list of file names inside build file, the format should be
        consistent with the file in device, e.g. /system/build.prop.
      hash_func: optional hash function.
    Returns:
      A dictionary where key is file name and value is hash value of its content.
    """
    raise NotImplementedError('You need to implement get_file_hash function.')

  def get_system_fingerprint(self):
    """Get build fingerprint in SYSTEM partition.

    Fingerprint format: $(BRAND)/$(PRODUCT)/$(DEVICE)/$(BOARD):
    $(VERSION.RELEASE)/$(ID)/$(VERSION.INCREMENTAL):$(TYPE)/$(TAGS)

    Returns:
      String of build fingerprint.
    """
    raise NotImplementedError('You need to implement get_system_fingerprint function.')
