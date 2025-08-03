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
import io
import textwrap
import unittest
from typing import TextIO

from pretty import Aggregation
from pretty import summarize_helper


class PrettyTest(unittest.TestCase):
    metrics: TextIO

    def setUp(self) -> None:
        self.metrics = io.StringIO(
            textwrap.dedent(
                """\
                build_result,build_type,description,targets,a,ab,ac
                SUCCESS,B1,WARMUP,nothing,1,10,1:00
                SUCCESS,B1,do it,something,10,200,
                SUCCESS,B1,rebuild-1,something,4,,1:04
                SUCCESS,B1,rebuild-2,something,6,55,1:07
                TEST_FAILURE,B2,do it,something,601,,
                TEST_FAILURE,B2,do it,nothing,3600,,
                TEST_FAILURE,B2,undo it,something,240,,
                FAILED,B3,,,70000,70000,7:00:00
                """
            )
        )

    def test_summarize_single_prop(self):
        result = summarize_helper(self.metrics, "a$", Aggregation.MAX)
        self.assertEqual(len(result), 1)
        self.assertEqual(
            textwrap.dedent(
                """\
                cuj,targets,B1,B2
                WARMUP,nothing,1,
                do it,something,10,601
                do it,nothing,,3600
                rebuild,something,6[N=2],
                undo it,something,,240
                """
            ),
            result["a"],
        )

    def test_summarize_multiple_props(self):
        result = summarize_helper(self.metrics, "a.$", Aggregation.MEDIAN)
        self.assertEqual(len(result), 2)
        self.assertEqual(
            textwrap.dedent(
                """\
                cuj,targets,B1,B2
                WARMUP,nothing,10,
                do it,something,200,
                do it,nothing,,
                rebuild,something,55,
                undo it,something,,
                """
            ),
            result["ab"],
        )
        self.assertEqual(
            textwrap.dedent(
                """\
                cuj,targets,B1,B2
                WARMUP,nothing,01:00,
                do it,something,,
                do it,nothing,,
                rebuild,something,01:06[N=2],
                undo it,something,,
                """
            ),
            result["ac"],
        )

    def test_summarize_loose_pattern(self):
        result = summarize_helper(self.metrics, "^a", Aggregation.MEDIAN)
        self.assertEqual(len(result), 3)


if __name__ == "__main__":
    unittest.main()
