#!/usr/bin/env python3
#
# Copyright (C) 2024 The Android Open Source Project
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

"""sample_filter.py: generate sample filter files, which can be passed in the
    --filter-file option when reporting.

Example:
  ./sample_filter.py -i perf.data --split-time-range 2 -o sample_filter
  ./gecko_profile_generator.py -i perf.data --filter-file sample_filter_part1 \
    | gzip >profile-part1.json.gz
  ./gecko_profile_generator.py -i perf.data --filter-file sample_filter_part2 \
    | gzip >profile-part2.json.gz
"""

import logging
from simpleperf_report_lib import ReportLib
from simpleperf_utils import BaseArgumentParser
from typing import Tuple


class RecordFileReader:
    def __init__(self, record_file: str):
        self.record_file = record_file

    def get_time_range(self) -> Tuple[int, int]:
        """ Return a tuple of (min_timestamp, max_timestamp). """
        min_timestamp = 0
        max_timestamp = 0
        lib = ReportLib()
        lib.SetRecordFile(self.record_file)
        while True:
            sample = lib.GetNextSample()
            if not sample:
                break
            if not min_timestamp or sample.time < min_timestamp:
                min_timestamp = sample.time
            if not max_timestamp or sample.time > max_timestamp:
                max_timestamp = sample.time
        lib.Close()
        return (min_timestamp, max_timestamp)


def show_time_range(record_file: str) -> None:
    reader = RecordFileReader(record_file)
    time_range = reader.get_time_range()
    print('time range of samples is %.3f s' % ((time_range[1] - time_range[0]) / 1e9))


def filter_samples(
        record_file: str, split_time_range: int, exclude_first_seconds: int,
        exclude_last_seconds: int, output_file_prefix: str) -> None:
    reader = RecordFileReader(record_file)
    min_timestamp, max_timestamp = reader.get_time_range()
    comment = 'total time range: %d seconds' % ((max_timestamp - min_timestamp) // 1e9)
    if exclude_first_seconds:
        min_timestamp += int(exclude_first_seconds * 1e9)
        comment += ', exclude first %d seconds' % exclude_first_seconds
    if exclude_last_seconds:
        max_timestamp -= int(exclude_last_seconds * 1e9)
        comment += ', exclude last %d seconds' % exclude_last_seconds
    if min_timestamp > max_timestamp:
        logging.error('All samples are filtered out')
        return
    if not split_time_range:
        output_file = output_file_prefix
        with open(output_file, 'w') as fh:
            fh.write('// %s\n' % comment)
            fh.write('GLOBAL_BEGIN %d\n' % min_timestamp)
            fh.write('GLOBAL_END %d\n' % max_timestamp)
        print('Generate sample filter file: %s' % output_file)
    else:
        step = (max_timestamp - min_timestamp) // split_time_range
        cur_timestamp = min_timestamp
        for i in range(split_time_range):
            output_file = output_file_prefix + '_part%s' % (i + 1)
            with open(output_file, 'w') as fh:
                time_range_comment = 'current range: %d to %d seconds' % (
                    (cur_timestamp - min_timestamp) // 1e9,
                    (cur_timestamp + step - min_timestamp) // 1e9)
                fh.write('// %s, %s\n' % (comment, time_range_comment))
                fh.write('GLOBAL_BEGIN %d\n' % cur_timestamp)
                if i == split_time_range - 1:
                    cur_timestamp = max_timestamp
                else:
                    cur_timestamp += step
                fh.write('GLOBAL_END %d\n' % (cur_timestamp + 1))
                cur_timestamp += 1
            print('Generate sample filter file: %s' % output_file)


def main():
    parser = BaseArgumentParser(description=__doc__)
    parser.add_argument('-i', '--record-file', nargs='?', default='perf.data',
                        help='Default is perf.data.')
    parser.add_argument('--show-time-range', action='store_true', help='show time range of samples')
    parser.add_argument('--split-time-range', type=int,
                        help='split time ranges of samples into several parts')
    parser.add_argument('--exclude-first-seconds', type=int,
                        help='exclude samples recorded in the first seconds')
    parser.add_argument('--exclude-last-seconds', type=int,
                        help='exclude samples recorded in the last seconds')
    parser.add_argument(
        '-o', '--output-file-prefix', default='sample_filter',
        help='prefix for the generated sample filter files')
    args = parser.parse_args()

    if args.show_time_range:
        show_time_range(args.record_file)

    if args.split_time_range or args.exclude_first_seconds or args.exclude_last_seconds:
        filter_samples(args.record_file, args.split_time_range, args.exclude_first_seconds,
                       args.exclude_last_seconds, args.output_file_prefix)


if __name__ == '__main__':
    main()
