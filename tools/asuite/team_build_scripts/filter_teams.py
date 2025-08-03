#!/usr/bin/env python3

# Copyright 2024, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
Combines out/soong/ownership_teams with module-info.json to create a
a new proto that only lists 'test' modules (and their teams).
Uses heuristcis on module-info.json to decide if a module is a test.
   i.e. tests have at least one these:
           test_config property
           NATIVE_TESTS class
           tests tag
           compatibility_suite

   This implicitly covers these soong module types:
        android_test
        art_cc_test
        cc_test
        cc_test_host
        csuite_test
        java_test
        java_test_host
        python_test
        python_test_host
        rust_test
        rust_test_host
        sh_test
        sh_test_host
        android_robolectric_test
        cc_benchmark
   (not bootclasspath_fragment_test or cc_fuzz)

Writes output back to out/soong/ownership_teams/all_test_specs.pb file.
Requires: 'm all_teams' already ran and also module-info.json was created.
          env variables ANDROID_BUILD_TOP, ANDROID_PRODUCT_OUT set.
Also converts between identical serialized proto formats. teams -> test_spec
"""
# pylint: disable=import-error
# pylint: disable=missing-function-docstring
# pylint: disable=line-too-long

import argparse
import json
import os
import sys

from teams import team_pb2
from test_spec import test_spec_pb2

# Parse arg and return Namespace
def parse_args(argv) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Filter teams proto file to only test modules')
    parser.add_argument(
        '--filter_teams', action='store_true',
        help='combine all_teams.bp with module-info for a smaller teams file filtered to tests')
    # parser.add_argument(
    # '--add_files',
    # help='combines all_teams.bp with jdeps and ccdeps to write files owned by each module')
    return parser.parse_args(argv)

#
def main(argv):
    args = parse_args(argv)

    all_modules_proto_file = "%s/out/soong/ownership/all_teams.pb" % os.environ['ANDROID_BUILD_TOP']
    all_teams = read_team_proto_file(all_modules_proto_file)

    if args.filter_teams:
        test_modules = read_module_info("%s/module-info.json" % os.environ['ANDROID_PRODUCT_OUT'])
        filtered_teams = filter_teams(all_teams, test_modules)

        out_file = "%s/out/soong/ownership/all_test_specs.pb" % os.environ['ANDROID_BUILD_TOP']
        with open(out_file, "wb") as f:
            f.write(filtered_teams.SerializeToString())
#
def read_team_proto_file(proto_file_path) -> team_pb2.AllTeams:
    all_teams = team_pb2.AllTeams()
    try:
        # TODO(rbraunstein): Try parsing as textproto if binary fails (for udc-mainline-prod)
        with open(proto_file_path, "rb") as f:
            all_teams.ParseFromString(f.read())
    except IOError:
        print(proto_file_path + ": Could not open file")
        sys.exit(2)

    return all_teams

# Given a proto file that lists the team for _all_modules and a set of test_modules,
# Return a filtered proto (as test_spec proto) that only contains modules that are tests.
# test_modules: dictionary of module names
def filter_teams(all_teams: team_pb2.AllTeams, test_modules: dict[str, int]):
    filtered_teams = test_spec_pb2.TestSpec()

    for team in all_teams.teams:
        if test_modules.get(team.target_name):
            # Only keep module if it has trendy_team_id.
            if team.HasField('trendy_team_id'):
                owner = test_spec_pb2.TestSpec.OwnershipMetadata()
                owner.target_name = team.target_name
                owner.path = team.path
                owner.trendy_team_id = team.trendy_team_id
                filtered_teams.ownership_metadata_list.append(owner)

    return filtered_teams


# Read module-info.json and return a dict of module names that are tests.
def read_module_info(path) -> dict[str, int]:
    test_modules = {}
    with open(path, 'r', encoding="utf-8") as f:
        for mod_name, mod_value in json.load(f).items():
            # Skip android_test_helper_app
            # They don't seem to have test_config and use installed: .apk, not .jar?
            # Fixes .32 problem for CC tests too. (FuseUtilsTest)
            if mod_value.get("test_config", []) or mod_value.get("auto_test_config", []):
                test_modules[mod_name] = 1
                continue

            tags = mod_value.get("tags")
            if tags and  "tests" in tags:
                test_modules[mod_name] = 1
                continue

            clazz = mod_value.get("class", [])
            if "NATIVE_TESTS" in clazz:
                # Fixup names liks net_test_bta_32  back to net_test_bta
                # Is this bad for some modules, only do for NATIVE_TESTS?
                # mod_name = mod_value.get("module_name")
                test_modules[mod_name] = 1
                continue
            # Android_robolectric_test creates an extra runner module that has this class.
            # Technically, it isn't a test and thing it runs is the test and that thing
            # will have a test_config and probably auto_test_config
            # See EmergencyInfoRoboTests in module-info.json
            if "ROBOLECTRIC" in clazz:
                test_modules[mod_name] = 1
                continue

            if mod_value.get("compatibility_suites"):
                test_modules[mod_name] = 1
                continue

    return test_modules


if __name__ == "__main__":
    main(sys.argv[1:])
