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
import io
import textwrap
import unittest

from perf2cfg import analyze
from perf2cfg import edit


def empty_analyzer():
    return analyze.RecordAnalyzer()


def populated_analyzer():
    analyzer = analyze.RecordAnalyzer()
    analyzer.target_arch = 'aarch64'
    samples = [
        ('void hcf()', 4, 'cpu-cycles', 90),
        ('void hcf()', 8, 'cpu-cycles', 10),
        ('void hcf()', 8, 'cache-misses', 100),
    ]

    for sample in samples:
        analyzer.record_sample(*sample)

    return analyzer


def edit_string(analyzer, input_string):
    input_stream = io.StringIO(input_string)
    output_stream = io.StringIO()

    editor = edit.CfgEditor(analyzer, input_stream, output_stream)
    editor.edit()

    return output_stream


class TestEdit(unittest.TestCase):

    def test_empty_file(self):
        output_stream = edit_string(empty_analyzer(), '')
        self.assertEqual(output_stream.getvalue(), '')

    def test_wrong_filetype(self):
        with self.assertLogs() as ctx:
            edit_string(
                empty_analyzer(), """<!DOCTYPE html>
                <html>
                <head>
                  <title>I'm not a CFG file</title>
                </head>
                </html>""")

        self.assertEqual(
            ctx.output,
            ['ERROR:root:Line 1: Expected a `begin_compilation` directive'])

    def test_no_architecture(self):
        with self.assertLogs() as ctx:
            edit_string(
                populated_analyzer(), """begin_compilation
                  name "void noMetadata()"
                end_compilation""")

        self.assertEqual(ctx.output, [
            'WARNING:root:Could not deduce the CFG file ISA, assuming it is '
            'compatible with the target architecture aarch64'
        ])

    def test_wrong_architecture(self):
        with self.assertLogs() as ctx:
            edit_string(
                populated_analyzer(), """begin_compilation
                  name "isa:x86_64"
                end_compilation""")

        self.assertEqual(ctx.output, [
            'ERROR:root:The CFG file ISA x86_64 is incompatible with the '
            'target architecture aarch64'
        ])

    def test_annotate_method(self):
        with self.assertLogs() as ctx:
            output_stream = edit_string(
                populated_analyzer(),
                textwrap.dedent("""\
                    begin_compilation
                      name "isa:arm64 isa_features:a53,crc,-lse,-fp16,-dotprod,-sve"
                    end_compilation
                    begin_compilation
                      name "void hcf()"
                    end_compilation
                    begin_cfg
                      name "disassembly (after)"
                      begin_block
                        flags
                        begin_HIR
                          0 0 NOPSlide dex_pc:0 loop:none
                          0x00000000: d503201f nop
                          0x00000004: d503201f nop
                          0x00000008: d503201f nop
                          <|@
                        end_HIR
                      end_block
                    end_cfg"""))

        self.assertEqual(ctx.output, ['INFO:root:Annotated void hcf()'])
        self.assertEqual(
            output_stream.getvalue(),
            textwrap.dedent("""\
                begin_compilation
                  name "isa:arm64 isa_features:a53,crc,-lse,-fp16,-dotprod,-sve"
                end_compilation
                begin_compilation
                  name "[cpu-cycles: 100.00%, cache-misses: 100.00%] void hcf()"
                end_compilation
                begin_cfg
                  name "disassembly (after)"
                  begin_block
                    flags "cpu-cycles: 100.00%" "cache-misses: 100.00%" "HI"
                    begin_HIR
                      0 0 NOPSlide dex_pc:0 loop:none
                _                           0x00000000: d503201f nop
                cpu-cycles:     90 (90.00%) 0x00000004: d503201f nop
                cache-misses:     0 (0.00%)
                cpu-cycles:     10 (10.00%) 0x00000008: d503201f nop
                cache-misses: 100 (100.00%)
                      <|@
                    end_HIR
                  end_block
                end_cfg"""))
