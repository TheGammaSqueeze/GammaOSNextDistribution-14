# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Test rbe."""

import unittest
from . import rbe

class RBETest(unittest.TestCase):
  """unittest for RBE."""

  def testExtraNsjailArgs(self):
    self.assertEqual(rbe.get_extra_nsjail_args()[0], '--disable_clone_newnet')

  def testEnv(self):
    env = rbe.prepare_env(env=["RBE_instance=test_instance", "RBE_service=test_service"])
    self.assertIn('USE_RBE=true', env)
    self.assertIn('RBE_instance=test_instance', env)
    self.assertIn('RBE_JAVAC=true', env)
    self.assertIn('RBE_D8=true', env)
    self.assertIn('RBE_R8=true', env)


if __name__ == '__main__':
  unittest.main()
