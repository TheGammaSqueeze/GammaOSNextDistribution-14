# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import unittest

from perf2cfg import exceptions
from perf2cfg import parse


class TestParse(unittest.TestCase):

    def test_build_flags_without_arguments(self):
        got = parse.build_flags([])
        self.assertEqual(got.strip(), 'flags')

    def test_build_flags_with_arguments(self):
        got = parse.build_flags(['catch_block', 'critical'])
        self.assertEqual(got.strip(), 'flags "catch_block" "critical"')

    def test_build_name(self):
        got = parse.build_name('void hcf()')
        self.assertEqual(got.strip(), 'name "void hcf()"')

    def test_parse_invalid_address_line(self):
        with self.assertRaises(exceptions.ParseError) as ctx:
            parse.parse_address(':)')

        self.assertEqual(str(ctx.exception), 'Expected an address')

    def test_parse_valid_address_line(self):
        got = parse.parse_address('0x0000001c: d503201f nop')
        self.assertEqual(got, 0x1c)

    def test_parse_flags_wrong_directive(self):
        with self.assertRaises(exceptions.ParseError) as ctx:
            parse.parse_flags('name "void hcf()"')

        self.assertEqual(str(ctx.exception), 'Expected a `flags` directive')

    def test_parse_flags_without_arguments(self):
        got = parse.parse_flags('flags')
        self.assertEqual(got, [])

    def test_parse_flags_with_arguments(self):
        got = parse.parse_flags('flags "catch_block" "critical"')
        self.assertEqual(got, ['catch_block', 'critical'])

    def test_parse_name_wrong_directive(self):
        with self.assertRaises(exceptions.ParseError) as ctx:
            parse.parse_name('flags "catch_block" "critical"')

        self.assertEqual(str(ctx.exception), 'Expected a `name` directive')

    def test_parse_name_without_argument(self):
        with self.assertRaises(exceptions.ParseError) as ctx:
            parse.parse_name('name')

        self.assertEqual(str(ctx.exception),
                         'Expected an argument to the `name` directive')

    def test_parse_name_with_argument(self):
        got = parse.parse_name('name "void hcf()"')
        self.assertEqual(got, 'void hcf()')
