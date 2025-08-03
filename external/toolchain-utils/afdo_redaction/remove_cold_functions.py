#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to remove cold functions in an textual AFDO profile.

The script will look through the AFDO profile to find all the function
records. Then it'll start with the functions with lowest sample count and
remove it from the profile, until the total remaining functions in the
profile meets the given number. When there are many functions having the
same sample count, we need to remove all of them in order to meet the
target, so the result profile will always have less than or equal to the
given number of functions.

The script is intended to be used on production ChromeOS profiles, after
other redaction/trimming scripts. It can be used with given textual CWP
and benchmark profiles, in order to analyze how many removed functions are
from which profile (or both), which can be used an indicator of fairness
during the removal.

This is part of the effort to stablize the impact of AFDO profile on
Chrome binary size. See crbug.com/1062014 for more context.
"""


import argparse
import collections
import re
import sys


_function_line_re = re.compile(r"^([\w\$\.@]+):(\d+)(?::\d+)?$")
ProfileRecord = collections.namedtuple(
    "ProfileRecord", ["function_count", "function_body", "function_name"]
)


def _read_sample_count(line):
    m = _function_line_re.match(line)
    assert m, "Failed to interpret function line %s" % line
    return m.group(1), int(m.group(2))


def _read_textual_afdo_profile(stream):
    """Parses an AFDO profile from a line stream into ProfileRecords."""
    # ProfileRecords are actually nested, due to inlining. For the purpose of
    # this script, that doesn't matter.
    lines = (line.rstrip() for line in stream)
    function_line = None
    samples = []
    ret = []
    for line in lines:
        if not line:
            continue

        if line[0].isspace():
            assert (
                function_line is not None
            ), "sample exists outside of a function?"
            samples.append(line)
            continue

        if function_line is not None:
            name, count = _read_sample_count(function_line)
            body = [function_line] + samples
            ret.append(
                ProfileRecord(
                    function_count=count, function_body=body, function_name=name
                )
            )
        function_line = line
        samples = []

    if function_line is not None:
        name, count = _read_sample_count(function_line)
        body = [function_line] + samples
        ret.append(
            ProfileRecord(
                function_count=count, function_body=body, function_name=name
            )
        )
    return ret


def write_textual_afdo_profile(stream, records):
    for r in records:
        print("\n".join(r.function_body), file=stream)


def analyze_functions(records, cwp, benchmark):
    cwp_functions = {x.function_name for x in cwp}
    benchmark_functions = {x.function_name for x in benchmark}
    all_functions = {x.function_name for x in records}
    cwp_only_functions = len(
        (all_functions & cwp_functions) - benchmark_functions
    )
    benchmark_only_functions = len(
        (all_functions & benchmark_functions) - cwp_functions
    )
    common_functions = len(all_functions & benchmark_functions & cwp_functions)
    none_functions = len(all_functions - benchmark_functions - cwp_functions)

    assert not none_functions
    return cwp_only_functions, benchmark_only_functions, common_functions


def run(input_stream, output_stream, goal, cwp=None, benchmark=None):
    records = _read_textual_afdo_profile(input_stream)
    num_functions = len(records)
    if not num_functions:
        return
    assert goal, "It's invalid to remove all functions in the profile"

    if cwp and benchmark:
        cwp_records = _read_textual_afdo_profile(cwp)
        benchmark_records = _read_textual_afdo_profile(benchmark)
        cwp_num, benchmark_num, common_num = analyze_functions(
            records, cwp_records, benchmark_records
        )

    records.sort(key=lambda x: (-x.function_count, x.function_name))
    records = records[:goal]

    print(
        "Retained %d/%d (%.1f%%) functions in the profile"
        % (len(records), num_functions, 100.0 * len(records) / num_functions),
        file=sys.stderr,
    )
    write_textual_afdo_profile(output_stream, records)

    if cwp and benchmark:
        (
            cwp_num_after,
            benchmark_num_after,
            common_num_after,
        ) = analyze_functions(records, cwp_records, benchmark_records)
        print(
            "Retained %d/%d (%.1f%%) functions only appear in the CWP profile"
            % (cwp_num_after, cwp_num, 100.0 * cwp_num_after / cwp_num),
            file=sys.stderr,
        )
        print(
            "Retained %d/%d (%.1f%%) functions only appear in the benchmark profile"
            % (
                benchmark_num_after,
                benchmark_num,
                100.0 * benchmark_num_after / benchmark_num,
            ),
            file=sys.stderr,
        )
        print(
            "Retained %d/%d (%.1f%%) functions appear in both CWP and benchmark"
            " profiles"
            % (
                common_num_after,
                common_num,
                100.0 * common_num_after / common_num,
            ),
            file=sys.stderr,
        )


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--input",
        default="/dev/stdin",
        help="File to read from. Defaults to stdin.",
    )
    parser.add_argument(
        "--output",
        default="/dev/stdout",
        help="File to write to. Defaults to stdout.",
    )
    parser.add_argument(
        "--number",
        type=int,
        required=True,
        help="Number of functions to retain in the profile.",
    )
    parser.add_argument(
        "--cwp", help="Textualized CWP profiles, used for further analysis"
    )
    parser.add_argument(
        "--benchmark",
        help="Textualized benchmark profile, used for further analysis",
    )
    args = parser.parse_args()

    if not args.number:
        parser.error("It's invalid to remove the number of functions to 0.")

    if (args.cwp and not args.benchmark) or (not args.cwp and args.benchmark):
        parser.error("Please specify both --cwp and --benchmark")

    with open(args.input) as stdin:
        with open(args.output, "w") as stdout:
            # When user specify textualized cwp and benchmark profiles, perform
            # the analysis. Otherwise, just trim the cold functions from profile.
            if args.cwp and args.benchmark:
                with open(args.cwp) as cwp:
                    with open(args.benchmark) as benchmark:
                        run(stdin, stdout, args.number, cwp, benchmark)
            else:
                run(stdin, stdout, args.number)


if __name__ == "__main__":
    main()
