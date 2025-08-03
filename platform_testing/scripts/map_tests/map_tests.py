#!/usr/bin/python3

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

"""Script to automatically populate TEST_MAPPING"""

if __name__ != "__main__":
    print("This is a script, not a library.")
    exit(1)

import argparse
import json
import os
import textwrap

from pathlib import Path

# some other things we might consider adding:
# - support for other classes of tests, besides 'presubmit'
# - use bpmodify to add tests to 'general-tests' or 'device-tests' automatically

parser = argparse.ArgumentParser(
     epilog=textwrap.dedent("""For example,
             `map_tests --dir system/libhidl --tests-in system/libhidl/transport -w` would add the
             tests in the `transport` subdirectory into system/libhidl/TEST_MAPPING`. In general,
             it's expected to be used by `map_tests.py -w` in the directory where you want to add
             tests."""))
parser.add_argument("-i", "--module-info", action="store", help="Default is $ANDROID_PRODUCT_OUT/module-info.json. If this is out of date, run `refreshmod`.")
parser.add_argument("-w", "--write", action="store_true", help="Write over the TEST_MAPPING file.")
parser.add_argument("-d", "--dir", action="store", help="Directory where TEST_MAPPING file should exist, defaults to current directory.")
parser.add_argument("-t", "--tests-in", action="store", help="Directory to pull tests from, defaults to test mapping '--dir'")
parser.add_argument("-p", "--print", action="store", help="Also print the module-info.json entry for this module, or '-' to print everything")
args = parser.parse_args()

INFO_PATH = args.module_info or (os.environ["ANDROID_PRODUCT_OUT"] + "/module-info.json")
MAP_DIR = args.dir or os.getcwd()
TESTS_IN_DIR = args.tests_in or MAP_DIR
PRINT = args.print
WRITE = args.write
del args

MAP_PATH = MAP_DIR + "/TEST_MAPPING"
TOP = os.environ["ANDROID_BUILD_TOP"]

################################################################################
# READ THE CURRENT TEST MAPPING
################################################################################
if os.path.exists(MAP_PATH):
    with open(MAP_PATH, "r", encoding="utf-8") as f:
        test_mapping = json.loads("".join(f.readlines()))
else:
    test_mapping = {}

################################################################################
# READ THE MODULE INFO
################################################################################
with open(INFO_PATH, "r", encoding="utf-8") as module_info_file:
    info = json.load(module_info_file)

################################################################################
# UPDATE TEST MAPPING BASED ON MODULE INFO
################################################################################
tests_dir = os.path.relpath(TESTS_IN_DIR, TOP)

for name,k in info.items():
    if PRINT == '-' or name == PRINT: print(name, k)

    # skip 32-bit tests, which show up in module-info.json, but they aren't present
    # at the atest level
    if name.endswith("_32"): continue

    is_in_path = any(Path(p).is_relative_to(tests_dir) for p in k['path'])
    if not is_in_path: continue

    # these are the test_suites that TEST_MAPPING can currently pull tests from
    is_built = any(ts in k['compatibility_suites'] for ts in ['device-tests', 'general-tests'])
    if not is_built: continue

    # automatically runs using other infrastructure
    if k['is_unit_test'] == 'true': continue

    has_test_config = len(k['test_config'])
    is_native_test = 'NATIVE_TESTS' in k['class']
    if not has_test_config and not is_native_test: continue

    if "presubmit" not in test_mapping: test_mapping["presubmit"] = []

    already_there = any(i["name"] == name for i in test_mapping["presubmit"])
    if already_there: continue

    test_mapping["presubmit"] += [{ "name": name }]

out = json.dumps(test_mapping, indent=2)

################################################################################
# WRITE THE OUTPUT
################################################################################
if WRITE:
    with open(MAP_PATH, "w+", encoding="utf-8") as f:
        f.write(out + "\n")
else:
    print(out)
