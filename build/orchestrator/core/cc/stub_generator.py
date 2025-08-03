#!/usr/bin/python3
#
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

"""A module for writing ninja rules that generate C stubs."""

import os

from typing import NamedTuple

from ninja_tools import Ninja
from ninja_syntax import Variable, Rule, BuildAction

# This is the path relative to outer tree root
NDKSTUBGEN = "orchestrator/build/orchestrator/core/cc/ndkstubgen_runner.sh"

class GenCcStubsInput:

    def __init__(self, arch: str, version: str, api: str, version_map: str, additional_args=""):
        self.arch = arch  # target device arch (e.g. x86)
        self.version = version  # numeric API level (e.g. 33)
        self.version_map = version_map # path to API level map (e.g. Q-->29)
        self.api = api  # path to map.txt
        self.additional_args = additional_args  # additional args to ndkstubgen (e.g. --llndk)

class GenCcStubsOutput(NamedTuple):
    stub: str
    version_script: str
    symbol_list: str

class StubGenerator:

    def __init__(self):
        self._stubgen_rule = None

    def _add_stubgen_rule(self, ninja: Ninja):
        """This adds a ninja rule to run ndkstubgen

        Running ndkstubgen creates C stubs from API .map.txt files
        """
        # Create a variable name for the binary.
        ninja.add_variable(Variable("ndkstubgen", NDKSTUBGEN))

        # Add a rule to the ninja file.
        rule = Rule("genCcStubsRule")
        rule.add_variable(
            "description",
            "Generate stub .c files from .map.txt API description files")
        rule.add_variable(
            "command",
            "${ndkstubgen} --arch ${arch} --api ${apiLevel} --api-map ${apiMap} ${additionalArgs} ${in} ${out}"
        )
        ninja.add_rule(rule)
        self._stubgen_rule = rule

    def add_stubgen_action(self, ninja: Ninja, stub_input: GenCcStubsInput,
                           work_dir: str) -> GenCcStubsOutput:
        """This adds a ninja build action to generate stubs using `genCcStubsRule`"""
        # Add stubgen rule if it has not been added yet.
        if not self._stubgen_rule:
            self._add_stubgen_rule(ninja)

        outputs = GenCcStubsOutput(
            stub=os.path.join(work_dir, stub_input.arch, "stub.c"),
            version_script=os.path.join(work_dir, stub_input.arch, "stub.map"),
            symbol_list=os.path.join(work_dir, stub_input.arch,
                                     "abi_symbol_list.txt")
        )

        # Create the ninja build action.
        stubgen_action = BuildAction(
            output=list(outputs),
            inputs=stub_input.api,
            rule=self._stubgen_rule.name,
            implicits=[
                    NDKSTUBGEN,
                    stub_input.version_map,
            ],
        )
        stubgen_action.add_variable("arch", stub_input.arch)
        stubgen_action.add_variable("apiLevel", stub_input.version)
        stubgen_action.add_variable("apiMap", stub_input.version_map)
        stubgen_action.add_variable("additionalArgs",
                                           stub_input.additional_args)

        # Add the build action to the ninja file.
        ninja.add_build_action(stubgen_action)
        return outputs
