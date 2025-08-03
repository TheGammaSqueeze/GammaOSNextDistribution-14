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
import textwrap
import unittest

from go_allowlists import GoAllowlistManipulator


class GoListLocatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.allowlists = GoAllowlistManipulator(
            textwrap.dedent(
                """\
                import blah
                package blue
                type X
                var (
                  empty = []string{
                  }
                  more = []string{
                    "a",
                    "b", // comment
                  }
                  Bp2buildDefaultConfig = Bp2BuildConfig{
                    "some_dir": Bp2BuildDefaultFalse
                  }
                )
                """
            ).splitlines(keepends=True)
        )
        self.original_size = len(self.allowlists.lines)

    def test_no_existing(self):
        with self.assertRaises(RuntimeError):
            self.allowlists.locate("non-existing")

    def test_empty_list(self):
        empty = self.allowlists.locate("empty")
        self.assertNotEqual(-1, empty.begin)
        self.assertNotEqual(-1, empty.end)
        self.assertEqual(empty.begin, empty.end)
        self.assertFalse("a" in empty)

    def test_add_to_empty_list(self):
        empty = self.allowlists.locate("empty")
        more = self.allowlists.locate("more")
        begin = more.begin
        end = more.end
        empty.prepend(["a-1", "a-2"])
        self.assertEqual(2, empty.end - empty.begin)
        self.assertTrue("a-1" in empty)
        self.assertTrue("a-2" in empty)
        self.assertEqual(self.original_size + 2, len(self.allowlists.lines))
        with self.subTest("subsequent sibling lists are re-indexed"):
            self.assertEqual(self.original_size + 2, len(self.allowlists.lines))
            self.assertEqual(begin + 2, more.begin)
            self.assertEqual(end + 2, more.end)

    def test_non_empty_list(self):
        empty = self.allowlists.locate("empty")
        begin = empty.begin
        end = empty.end
        more = self.allowlists.locate("more")
        self.assertEqual(2, more.end - more.begin)
        self.assertTrue("a" in more)
        self.assertTrue("b" in more)
        with self.subTest("preceding sibling lists are NOT re-indexed"):
            self.assertEqual(begin, empty.begin)
            self.assertEqual(end, empty.end)


if __name__ == "__main__":
    unittest.main()
