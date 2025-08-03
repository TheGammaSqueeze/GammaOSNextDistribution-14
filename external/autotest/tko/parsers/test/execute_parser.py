#!/usr/bin/python3
"""Reexecute parser in scenario and store the result at specified tag.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import optparse, sys
from os import path
import common
from autotest_lib.tko.parsers.test import scenario_base

usage = 'usage: %prog [options] scenario_dirpath parser_result_tag'
parser = optparse.OptionParser(usage=usage)


def main():
    (options, args) = parser.parse_args()
    if len(args) < 2:
        parser.print_help()
        sys.exit(1)

    scenario_dirpath = path.normpath(args[0])
    parser_result_tag = args[1]

    if not path.exists(scenario_dirpath) or not path.isdir(scenario_dirpath):
        print('Invalid scenarios_dirpath:', scenario_dirpath)
        parser.print_help()
        sys.exit(1)

    tempdir, results_dirpath = scenario_base.load_results_dir(scenario_dirpath)
    harness = scenario_base.new_parser_harness(results_dirpath)
    try:
        parser_result = harness.execute()
    except Exception as e:
        parser_result = e
    scenario_base.store_parser_result(
        scenario_dirpath, parser_result, parser_result_tag)
    tempdir.clean()


if __name__ == '__main__':
    main()
