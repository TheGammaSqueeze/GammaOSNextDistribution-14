#!/usr/bin/env python3

# Copyright (C) 2022 The Android Open Source Project
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
import dataclasses
import unittest

from perf_metrics import _get_column_headers


def to_row(concatenated_keys: str) -> dict:
    return {c: None for c in concatenated_keys}


class PerfMetricsTest(unittest.TestCase):
    """Tests utility functions. This is not Perf Test itself."""

    def test_get_column_headers(self):
        @dataclasses.dataclass
        class Example:
            # each string = concatenated keys of the row object
            row_keysets: list[str]
            # concatenated headers
            expected_headers: str

        examples: list[Example] = [
            Example(["a"], "a"),
            Example(["ac", "bd"], "abcd"),
            Example(["abe", "cde"], "abcde"),
            Example(["ab", "ba"], "ab"),
            Example(["abcde", "edcba"], "abcde"),
            Example(["ac", "abc"], "abc"),
        ]
        for e in examples:
            rows = [to_row(kz) for kz in e.row_keysets]
            expected_headers = [*e.expected_headers]
            with self.subTest(rows=rows, expected_headers=expected_headers):
                self.assertEqual(
                    _get_column_headers(rows, allow_cycles=True), expected_headers
                )

    def test_cycles(self):
        examples = [(["ab", "ba"], "a->b->a"), (["abcd", "db"], "b->c->d->b")]
        for e, cycle in examples:
            rows = [to_row(kz) for kz in e]
            with self.subTest(rows=rows, cycle=cycle):
                with self.assertRaisesRegex(
                    ValueError, f"event ordering has a cycle {cycle}"
                ):
                    _get_column_headers(rows, allow_cycles=False)


if __name__ == "__main__":
    unittest.main()
