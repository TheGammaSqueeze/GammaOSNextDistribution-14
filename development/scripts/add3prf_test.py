# Copyright 2021 Google Inc. All rights reserved.
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
import os.path
import unittest

from pyfakefs import fake_filesystem_unittest

import add3prf

class LicenseDetectionTestCase(fake_filesystem_unittest.TestCase):

  def setUp(self):
    self.setUpPyfakefs()

  def test_dual_license(self):
    self.fs.create_file("LICENSE-APACHE")
    self.fs.create_file("LICENSE-MIT")
    licenses = add3prf.decide_license_type("MIT OR Apache-2.0")
    self.assertEqual(len(licenses), 2)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.APACHE2)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE-APACHE")

  def test_mit_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("MIT")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.MIT)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_misc_license(self):
    self.fs.create_file("LICENSE.txt")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.BSD_LIKE)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE.txt")

  def test_missing_license_file(self):
    with self.assertRaises(FileNotFoundError):
      add3prf.decide_license_type("MIT OR Apache-2.0")

  def test_mpl_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("MPL")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.MPL)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.RECIPROCAL)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_0bsd_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("0BSD")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.ZERO_BSD)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_0bsd_licensefile(self):
    self.fs.create_file("LICENSE-0BSD")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.ZERO_BSD)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "LICENSE-0BSD")

  def test_0bsd_licensefile_with_extension(self):
    self.fs.create_file("LICENSE-0BSD.md")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.ZERO_BSD)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "LICENSE-0BSD.md")

  def test_unlicense_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("Unlicense")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.UNLICENSE)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_unlicense_licensefile(self):
    self.fs.create_file("UNLICENSE")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.UNLICENSE)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "UNLICENSE")

  def test_unlicense_licensefile_with_extension(self):
    self.fs.create_file("UNLICENSE.txt")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.UNLICENSE)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.PERMISSIVE)
    self.assertEqual(preferred_license.filename, "UNLICENSE.txt")

  def test_zlib_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("Zlib")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.ZLIB)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_zlib_licensefile(self):
    self.fs.create_file("LICENSE-ZLIB")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.ZLIB)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE-ZLIB")

  def test_boost_license(self):
    self.fs.create_file("LICENSE")
    licenses = add3prf.decide_license_type("BSL-1.0")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.BOOST)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE")

  def test_boost_licensefile(self):
    self.fs.create_file("LICENSE-BOOST")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.BOOST)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE-BOOST")

  def test_bsd_licensefile(self):
    self.fs.create_file("LICENSE-BSD")
    licenses = add3prf.decide_license_type("")
    self.assertEqual(len(licenses), 1)
    preferred_license = licenses[0]
    self.assertEqual(preferred_license.type, add3prf.LicenseType.BSD_LIKE)
    self.assertEqual(preferred_license.group, add3prf.LicenseGroup.NOTICE)
    self.assertEqual(preferred_license.filename, "LICENSE-BSD")

class AddModuleLicenseTestCase(fake_filesystem_unittest.TestCase):

  def setUp(self):
    self.setUpPyfakefs()

  def test_no_file(self):
    add3prf.add_module_license(add3prf.LicenseType.APACHE2)
    self.assertTrue(os.path.exists("MODULE_LICENSE_APACHE2"))

  def test_already_exists(self):
    self.fs.create_file("MODULE_LICENSE_APACHE2")
    add3prf.add_module_license(add3prf.LicenseType.APACHE2)

  def test_mit_apache(self):
    self.fs.create_file("MODULE_LICENSE_MIT")
    with self.assertRaises(Exception):
      add3prf.add_module_license(add3prf.LicenseType.APACHE2)


if __name__ == '__main__':
  unittest.main(verbosity=2)
