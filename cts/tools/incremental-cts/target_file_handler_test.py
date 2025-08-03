# Lint as: python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for target_file_handler."""

import unittest
from target_file_handler import *

class BuildFileHandlerTest(unittest.TestCase):

  def test_get_hash(self):
    """Test TargetFileHandler could get hash from target file."""
    build_file = './testdata/base_build_target-files.zip'
    handler = TargetFileHandler(build_file)
    deqp_deps = ['/system/deqp_dependency_file_a.so', '/vendor/deqp_dependency_file_b.so',
                 '/vendor/file_not_exists.so']
    hash_dict = handler.get_file_hash(deqp_deps)

    self.assertEqual(hash_dict['/system/deqp_dependency_file_a.so'],
                     hash(b'placeholder\nplaceholder\n'))
    self.assertEqual(hash_dict['/vendor/deqp_dependency_file_b.so'],
                     hash(b'placeholder\nplaceholder\nplaceholder\n\n'))
    self.assertEqual(len(hash_dict), 2)

  def test_get_system_fingerprint(self):
    """Test TargetFileHandler could get SYSTEM fingerprint from target file."""
    build_file = './testdata/base_build_target-files.zip'
    handler = TargetFileHandler(build_file)
    self.assertEqual(('generic/aosp_cf_x86_64_phone/vsoc_x86_64:S/AOSP.MASTER/7363308:'
                      'userdebug/test-keys'), handler.get_system_fingerprint())

  def test_get_system_fingerprint_without_buildprop(self):
    """Test TargetFileHandler get fingerprint raises exception if build.prop doesn't exist."""
    build_file = './testdata/current_build_target-files.zip'
    handler = TargetFileHandler(build_file)
    with self.assertRaises(KeyError):
      handler.get_system_fingerprint()


if __name__ == '__main__':
  unittest.main()
