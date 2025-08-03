# Copyright (C) 2023 The Android Open Source Project
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
import unittest

from finder import confirm
from finder import is_git_repo
from cuj import src


class UtilTest(unittest.TestCase):
    def test_is_git_repo(self):
        self.assertFalse(is_git_repo(src(".")))
        self.assertTrue(is_git_repo(src("build/soong")))

    def test_any_match(self):
        with self.subTest("required"):
            with self.subTest("literal"):
                confirm(src("build/soong"), "root.bp")
            with self.subTest("wildcarded"):
                confirm(src("build"), "*/root.bp")

        with self.subTest("disallowed"):
            with self.subTest("literal"):
                confirm(src("build/bazel"), "!Android.bp", "!BUILD")
            with self.subTest("wildcarded"):
               confirm(src("bionic"), "!*.bazel", "*")

        with self.subTest("disallowed and required"):
            with self.subTest("literal"):
                confirm(
                    src("build/bazel/scripts/incremental_build"),
                    "BUILD.bazel",
                    "!BUILD",
                )
            with self.subTest("wildcarded"):
                confirm(src("bionic/libm"), "!**/BUILD", "**/*.cpp")



if __name__ == "__main__":
    unittest.main()
