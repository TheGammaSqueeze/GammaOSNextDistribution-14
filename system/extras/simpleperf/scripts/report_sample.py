#!/usr/bin/env python3
#
# Copyright (C) 2016 The Android Open Source Project
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
#

"""report_sample.py: report samples in the same format as `perf script`.
"""

import sys
from simpleperf_report_lib import GetReportLib
from simpleperf_utils import BaseArgumentParser, flatten_arg_list, ReportLibOptions
from typing import List, Set, Optional


def report_sample(
        record_file: str,
        symfs_dir: str,
        kallsyms_file: str,
        show_tracing_data: bool,
        header: bool,
        report_lib_options: ReportLibOptions):
    """ read record_file, and print each sample"""
    lib = GetReportLib(record_file)

    lib.ShowIpForUnknownSymbol()
    if symfs_dir is not None:
        lib.SetSymfs(symfs_dir)
    if kallsyms_file is not None:
        lib.SetKallsymsFile(kallsyms_file)
    lib.SetReportOptions(report_lib_options)

    if header:
        print("# ========")
        print("# cmdline : %s" % lib.GetRecordCmd())
        print("# arch : %s" % lib.GetArch())
        for k, v in lib.MetaInfo().items():
            print('# %s : %s' % (k, v.replace('\n', ' ')))
        print("# ========")
        print("#")

    while True:
        sample = lib.GetNextSample()
        if sample is None:
            lib.Close()
            break
        event = lib.GetEventOfCurrentSample()
        symbol = lib.GetSymbolOfCurrentSample()
        callchain = lib.GetCallChainOfCurrentSample()

        sec = sample.time // 1000000000
        usec = (sample.time - sec * 1000000000) // 1000
        print('%s\t%d/%d [%03d] %d.%06d: %d %s:' % (sample.thread_comm,
                                                    sample.pid, sample.tid, sample.cpu, sec,
                                                    usec, sample.period, event.name))
        print('\t%16x %s (%s)' % (sample.ip, symbol.symbol_name, symbol.dso_name))
        for i in range(callchain.nr):
            entry = callchain.entries[i]
            print('\t%16x %s (%s)' % (entry.ip, entry.symbol.symbol_name, entry.symbol.dso_name))
        if show_tracing_data:
            data = lib.GetTracingDataOfCurrentSample()
            if data:
                print('\ttracing data:')
                for key, value in data.items():
                    print('\t\t%s : %s' % (key, value))
        print('')


def main():
    parser = BaseArgumentParser(description='Report samples in perf.data.')
    parser.add_argument('--symfs',
                        help='Set the path to find binaries with symbols and debug info.')
    parser.add_argument('--kallsyms', help='Set the path to find kernel symbols.')
    parser.add_argument('-i', '--record_file', nargs='?', default='perf.data',
                        help='Default is perf.data.')
    parser.add_argument('--show_tracing_data', action='store_true', help='print tracing data.')
    parser.add_argument('--header', action='store_true',
                        help='Show metadata header, like perf script --header')
    parser.add_argument('-o', '--output_file', default='', help="""
        The path of the generated report.  Default is stdout.""")
    parser.add_report_lib_options()
    args = parser.parse_args()
    # If the output file has been set, redirect stdout.
    if args.output_file != '' and args.output_file != '-':
        sys.stdout = open(file=args.output_file, mode='w')
    report_sample(
        record_file=args.record_file,
        symfs_dir=args.symfs,
        kallsyms_file=args.kallsyms,
        show_tracing_data=args.show_tracing_data,
        header=args.header,
        report_lib_options=args.report_lib_options)


if __name__ == '__main__':
    main()
