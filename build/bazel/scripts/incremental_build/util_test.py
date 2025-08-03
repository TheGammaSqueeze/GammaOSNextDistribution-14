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
import datetime
import unittest
from pathlib import Path

from util import groupby
from util import hhmmss
from util import next_path
from util import period_to_seconds


class UtilTest(unittest.TestCase):
    def test_groupby(self):
        x1 = {"g": "b", "id": 1}
        x2 = {"g": "a", "id": 2}
        x3 = {"g": "b", "id": 3}
        grouped = groupby([x1, x2, x3], lambda x: x["g"])
        self.assertEqual(grouped, {"b": [x1, x3], "a": [x2]})
        self.assertEqual(list(grouped.keys()), ["b", "a"], "insertion order maintained")

    def test_next_path(self):
        examples = [
            ("output", "output-001"),
            ("output-1", "output-002"),
            ("output-09", "output-010"),
            ("output-010", "output-011"),
            ("output-999", "output-1000"),
        ]
        for pattern, expected in examples:
            with self.subTest(msg=pattern, pattern=pattern, expected=expected):
                generator = next_path(Path(pattern))
                n = next(generator)
                self.assertEqual(n, Path(expected))

    def test_hhmmss(self):
        decimal_precision_examples = [
            (datetime.timedelta(seconds=(2 * 60 + 5)), "02:05.000"),
            (datetime.timedelta(seconds=(3600 + 23 * 60 + 45.897898)), "1:23:45.898"),
        ]
        non_decimal_precision_examples = [
            (datetime.timedelta(seconds=(2 * 60 + 5)), "02:05"),
            (datetime.timedelta(seconds=(3600 + 23 * 60 + 45.897898)), "1:23:46"),
        ]
        for ts, expected in decimal_precision_examples:
            with self.subTest(ts=ts, expected=expected):
                self.assertEqual(hhmmss(ts, decimal_precision=True), expected)
        for ts, expected in non_decimal_precision_examples:
            with self.subTest(ts=ts, expected=expected):
                self.assertEqual(hhmmss(ts, decimal_precision=False), expected)

    def test_period_to_seconds(self):
        examples = [
            ("02:05.000", 2 * 60 + 5),
            ("1:23:45.898", 3600 + 23 * 60 + 45.898),
            ("1.898", 1.898),
            ("0.3", 0.3),
            ("0", 0),
            ("0:00", 0),
            ("0:00:00", 0),
            ("", 0),
        ]
        for ts, expected in examples:
            with self.subTest(ts=ts, expected=expected):
                self.assertEqual(period_to_seconds(ts), expected)


if __name__ == "__main__":
    unittest.main()
