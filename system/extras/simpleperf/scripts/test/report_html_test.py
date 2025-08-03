#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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

import collections
import json
import os
import tempfile
from typing import Any, Dict, List, Optional, Set

from binary_cache_builder import BinaryCacheBuilder
from . test_utils import TestBase, TestHelper


class TestReportHtml(TestBase):
    def test_long_callchain(self):
        self.run_cmd(['report_html.py', '-i',
                      TestHelper.testdata_path('perf_with_long_callchain.data')])

    def test_aggregated_by_thread_name(self):
        # Calculate event_count for each thread name before aggregation.
        event_count_for_thread_name = collections.defaultdict(lambda: 0)
        # use "--min_func_percent 0" to avoid cutting any thread.
        record_data = self.get_record_data(['--min_func_percent', '0', '-i',
                                            TestHelper.testdata_path('aggregatable_perf1.data'),
                                            TestHelper.testdata_path('aggregatable_perf2.data')])
        event = record_data['sampleInfo'][0]
        for process in event['processes']:
            for thread in process['threads']:
                thread_name = record_data['threadNames'][str(thread['tid'])]
                event_count_for_thread_name[thread_name] += thread['eventCount']

        # Check event count for each thread after aggregation.
        record_data = self.get_record_data(['--aggregate-by-thread-name',
                                            '--min_func_percent', '0', '-i',
                                            TestHelper.testdata_path('aggregatable_perf1.data'),
                                            TestHelper.testdata_path('aggregatable_perf2.data')])
        event = record_data['sampleInfo'][0]
        hit_count = 0
        for process in event['processes']:
            for thread in process['threads']:
                thread_name = record_data['threadNames'][str(thread['tid'])]
                self.assertEqual(thread['eventCount'],
                                 event_count_for_thread_name[thread_name])
                hit_count += 1
        self.assertEqual(hit_count, len(event_count_for_thread_name))

    def test_no_empty_process(self):
        """ Test not showing a process having no threads. """
        perf_data = TestHelper.testdata_path('two_process_perf.data')
        record_data = self.get_record_data(['-i', perf_data])
        processes = record_data['sampleInfo'][0]['processes']
        self.assertEqual(len(processes), 2)

        # One process is removed because all its threads are removed for not
        # reaching the min_func_percent limit.
        record_data = self.get_record_data(['-i', perf_data, '--min_func_percent', '20'])
        processes = record_data['sampleInfo'][0]['processes']
        self.assertEqual(len(processes), 1)

    def test_proguard_mapping_file(self):
        """ Test --proguard-mapping-file option. """
        testdata_file = TestHelper.testdata_path('perf_need_proguard_mapping.data')
        proguard_mapping_file = TestHelper.testdata_path('proguard_mapping.txt')
        original_methodname = 'androidx.fragment.app.FragmentActivity.startActivityForResult'
        # Can't show original method name without proguard mapping file.
        record_data = self.get_record_data(['-i', testdata_file])
        self.assertNotIn(original_methodname, json.dumps(record_data))
        # Show original method name with proguard mapping file.
        record_data = self.get_record_data(
            ['-i', testdata_file, '--proguard-mapping-file', proguard_mapping_file])
        self.assertIn(original_methodname, json.dumps(record_data))

    def get_record_data(self, options: List[str]) -> Dict[str, Any]:
        json_data = self.get_record_data_string(options)
        return json.loads(json_data)

    def get_record_data_string(self, options: List[str]) -> str:
        args = ['report_html.py'] + options
        if TestHelper.ndk_path:
            args += ['--ndk_path', TestHelper.ndk_path]
        self.run_cmd(args)
        with open('report.html', 'r') as fh:
            data = fh.read()
        start_str = 'type="application/json"'
        end_str = '</script>'
        start_pos = data.find(start_str)
        self.assertNotEqual(start_pos, -1)
        start_pos = data.find('>', start_pos)
        self.assertNotEqual(start_pos, -1)
        start_pos += 1
        end_pos = data.find(end_str, start_pos)
        self.assertNotEqual(end_pos, -1)
        return data[start_pos:end_pos]

    def test_add_source_code(self):
        """ Test --add_source_code option. """
        testdata_file = TestHelper.testdata_path('runtest_two_functions_arm64_perf.data')

        # Build binary_cache.
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.build_binary_cache(testdata_file, [TestHelper.testdata_dir])

        # Generate report.html.
        source_dir = TestHelper.testdata_dir
        record_data = self.get_record_data(
            ['-i', testdata_file, '--add_source_code', '--source_dirs', str(source_dir)])

        # Check source code info in samples.
        source_code_list = []
        thread = record_data['sampleInfo'][0]['processes'][0]['threads'][0]
        for lib in thread['libs']:
            for function in lib['functions']:
                for source_code_info in function.get('s') or []:
                    source_file = record_data['sourceFiles'][source_code_info['f']]
                    file_path = source_file['path']
                    line_number = source_code_info['l']
                    line_content = source_file['code'][str(line_number)]
                    event_count = source_code_info['e']
                    subtree_event_count = source_code_info['s']
                    s = (f'{file_path}:{line_number}:{line_content}:' +
                         f'{event_count}:{subtree_event_count}')
                    source_code_list.append(s)
        check_items = ['two_functions.cpp:9:    *p = i;\n:590184:590184',
                       'two_functions.cpp:16:    *p = i;\n:591577:591577',
                       'two_functions.cpp:22:    Function1();\n:0:590184',
                       'two_functions.cpp:23:    Function2();\n:0:591577']
        for item in check_items:
            found = False
            for source_code in source_code_list:
                if item in source_code:
                    found = True
                    break
            self.assertTrue(found, item)

    def test_add_disassembly(self):
        """ Test --add_disassembly option. """
        testdata_file = TestHelper.testdata_path('runtest_two_functions_arm64_perf.data')

        # Build binary_cache.
        binary_cache_builder = BinaryCacheBuilder(TestHelper.ndk_path, False)
        binary_cache_builder.build_binary_cache(testdata_file, [TestHelper.testdata_dir])

        # Generate report.html.
        record_data = self.get_record_data(['-i', testdata_file, '--add_disassembly'])

        # Check disassembly in samples.
        disassembly_list = []
        thread = record_data['sampleInfo'][0]['processes'][0]['threads'][0]
        for lib in thread['libs']:
            lib_name = record_data['libList'][lib['libId']]
            for function in lib['functions']:
                for addr_info in function.get('a') or []:
                    addr = addr_info['a']
                    event_count = addr_info['e']
                    subtree_event_count = addr_info['s']
                    function_data = record_data['functionMap'][str(function['f'])]
                    function_name = function_data['f']
                    for dis_line, dis_addr in function_data.get('d') or []:
                        if addr == dis_addr:
                            s = (f'{lib_name}:{function_name}:{addr}:' +
                                 f'{event_count}:{subtree_event_count}')
                            disassembly_list.append(s)

        check_items = ['simpleperf_runtest_two_functions_arm64:Function1():0x1094:590184:590184',
                       'simpleperf_runtest_two_functions_arm64:Function2():0x1104:591577:591577',
                       'simpleperf_runtest_two_functions_arm64:main:0x113c:0:590184',
                       'simpleperf_runtest_two_functions_arm64:main:0x1140:0:591577']
        for item in check_items:
            found = False
            for disassembly in disassembly_list:
                if item in disassembly:
                    found = True
                    break
            self.assertTrue(found, item)

    def test_trace_offcpu(self):
        """ Test --trace-offcpu option. """
        testdata_file = TestHelper.testdata_path('perf_with_trace_offcpu_v2.data')
        record_data = self.get_record_data(['-i', testdata_file, '--trace-offcpu', 'on-cpu'])
        self.assertEqual(len(record_data['sampleInfo']), 1)
        self.assertEqual(record_data['sampleInfo'][0]['eventName'], 'cpu-clock:u')
        self.assertEqual(record_data['sampleInfo'][0]['eventCount'], 52000000)

        record_data = self.get_record_data(['-i', testdata_file, '--trace-offcpu', 'off-cpu'])
        self.assertEqual(len(record_data['sampleInfo']), 1)
        self.assertEqual(record_data['sampleInfo'][0]['eventName'], 'sched:sched_switch')
        self.assertEqual(record_data['sampleInfo'][0]['eventCount'], 344124304)

        record_data = self.get_record_data(['-i', testdata_file, '--trace-offcpu', 'on-off-cpu'])
        self.assertEqual(len(record_data['sampleInfo']), 2)
        self.assertEqual(record_data['sampleInfo'][0]['eventName'], 'cpu-clock:u')
        self.assertEqual(record_data['sampleInfo'][0]['eventCount'], 52000000)
        self.assertEqual(record_data['sampleInfo'][1]['eventName'], 'sched:sched_switch')
        self.assertEqual(record_data['sampleInfo'][1]['eventCount'], 344124304)

        record_data = self.get_record_data(
            ['-i', testdata_file, '--trace-offcpu', 'mixed-on-off-cpu'])
        self.assertEqual(len(record_data['sampleInfo']), 1)
        self.assertEqual(record_data['sampleInfo'][0]['eventName'], 'cpu-clock:u')
        self.assertEqual(record_data['sampleInfo'][0]['eventCount'], 396124304)

    def test_sample_filters(self):
        def get_threads_for_filter(filter: str) -> Set[int]:
            record_data = self.get_record_data(
                ['-i', TestHelper.testdata_path('perf_display_bitmaps.data')] + filter.split())
            threads = set()
            try:
                for thread in record_data['sampleInfo'][0]['processes'][0]['threads']:
                    threads.add(thread['tid'])
            except IndexError:
                pass
            return threads

        self.assertNotIn(31850, get_threads_for_filter('--exclude-pid 31850'))
        self.assertIn(31850, get_threads_for_filter('--include-pid 31850'))
        self.assertIn(31850, get_threads_for_filter('--pid 31850'))
        self.assertNotIn(31881, get_threads_for_filter('--exclude-tid 31881'))
        self.assertIn(31881, get_threads_for_filter('--include-tid 31881'))
        self.assertIn(31881, get_threads_for_filter('--tid 31881'))
        self.assertNotIn(31881, get_threads_for_filter(
            '--exclude-process-name com.example.android.displayingbitmaps'))
        self.assertIn(31881, get_threads_for_filter(
            '--include-process-name com.example.android.displayingbitmaps'))
        self.assertNotIn(31850, get_threads_for_filter(
            '--exclude-thread-name com.example.android.displayingbitmaps'))
        self.assertIn(31850, get_threads_for_filter(
            '--include-thread-name com.example.android.displayingbitmaps'))

        with tempfile.NamedTemporaryFile('w', delete=False) as filter_file:
            filter_file.write('GLOBAL_BEGIN 684943449406175\nGLOBAL_END 684943449406176')
            filter_file.flush()
            threads = get_threads_for_filter('--filter-file ' + filter_file.name)
            self.assertIn(31881, threads)
            self.assertNotIn(31850, threads)
        os.unlink(filter_file.name)

    def test_show_art_frames(self):
        art_frame_str = 'art::interpreter::DoCall'
        options = ['-i', TestHelper.testdata_path('perf_with_interpreter_frames.data')]
        report = self.get_record_data_string(options)
        self.assertNotIn(art_frame_str, report)
        report = self.get_record_data_string(options + ['--show-art-frames'])
        self.assertIn(art_frame_str, report)

    def test_aggregate_threads(self):
        def get_thread_names(aggregate_threads_option: Optional[List[str]]) -> Dict[str, int]:
            options = ['-i', TestHelper.testdata_path('perf_display_bitmaps.data')]
            if aggregate_threads_option:
                options += ['--aggregate-threads'] + aggregate_threads_option
            record_data = self.get_record_data(options)
            thread_names = {}
            try:
                for thread in record_data['sampleInfo'][0]['processes'][0]['threads']:
                    tid = str(thread['tid'])
                    thread_names[record_data['threadNames'][tid]] = thread['sampleCount']
            except IndexError:
                pass
            return thread_names
        thread_names = get_thread_names(None)
        self.assertEqual(thread_names['AsyncTask #3'], 6)
        self.assertEqual(thread_names['AsyncTask #4'], 13)
        thread_names = get_thread_names(['AsyncTask.*'])
        self.assertEqual(thread_names['AsyncTask.*'], 19)
        self.assertNotIn('AsyncTask #3', thread_names)
        self.assertNotIn('AsyncTask #4', thread_names)

    def test_sort_call_graph_by_function_name(self):
        record_data = self.get_record_data(
            ['-i', TestHelper.testdata_path('perf_display_bitmaps.data'),
             '--aggregate-threads', '.*'])

        def get_func_name(func_id: int) -> str:
            return record_data['functionMap'][str(func_id)]['f']

        # Test if the top functions are sorted by function names.
        thread = record_data['sampleInfo'][0]['processes'][0]['threads'][0]
        top_functions = [get_func_name(c['f']) for c in thread['g']['c']]
        self.assertIn('__libc_init', top_functions)
        self.assertIn('__start_thread', top_functions)
        self.assertEqual(top_functions, sorted(top_functions))
