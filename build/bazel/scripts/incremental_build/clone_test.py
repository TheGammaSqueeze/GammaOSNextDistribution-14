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
from io import StringIO

from clone import ModuleName
from clone import _extract_templates_helper
from clone import module_defs
from clone import name_in
from clone import type_in


class CloneTest(unittest.TestCase):
    def setUp(self) -> None:
        self.bp = textwrap.dedent(
            """\
            //licence text
            alias=blah
            cc_library {
              out: ["dont"],
              name: "a",
              other: 45
            }
            genrule {
              name: "b",
              out: [
                "oph"
              ]
              other:  {
                name: 'not-a-name'
                blah: "nested"
              }
            }
            """
        )

    def test_module_def(self):
        defs = list(module_defs(StringIO(self.bp)))
        self.assertEqual(len(defs), 2)
        name, content = defs[0]
        self.assertEqual(name, "cc_library")
        self.assertEqual(
            content,
            textwrap.dedent(
                """\
                cc_library {
                  out: ["dont"],
                  name: "a",
                  other: 45
                }
                """
            ),
        )
        name, content = defs[1]
        self.assertEqual(name, "genrule")
        self.assertEqual(
            content,
            textwrap.dedent(
                """\
                genrule {
                  name: "b",
                  out: [
                    "oph"
                  ]
                  other:  {
                    name: 'not-a-name'
                    blah: "nested"
                  }
                }
                """
            ),
        )

    def test_non_existent(self):
        cloners = _extract_templates_helper(StringIO(self.bp), name_in("not-a-name"))
        self.assertEqual(len(cloners), 0)

    def test_by_type(self):
        cloners = _extract_templates_helper(StringIO(self.bp), type_in("genrule"))
        self.assertEqual(len(cloners), 1)
        self.assertEqual(
            cloners[ModuleName("b")].substitute(suffix="test"),
            textwrap.dedent(
                """\
                genrule {
                  name: "b-test",
                  out: [
                    "oph-test"
                  ]
                  other:  {
                    name: 'not-a-name'
                    blah: "nested"
                  }
                }
                """
            ),
        )

    def test_by_name(self):
        cloners = _extract_templates_helper(StringIO(self.bp), name_in("a", "b"))
        self.assertEqual(len(cloners), 2)
        self.assertEqual(
            cloners[ModuleName("a")].substitute(suffix="test"),
            textwrap.dedent(
                """\
                cc_library {
                  out: ["dont"],
                  name: "a-test",
                  other: 45
                }
                """
            ),
        )


if __name__ == "__main__":
    unittest.main()