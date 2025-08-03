#!/usr/bin/env python
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
# ibuted under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unit tests for stub_generator.py."""

import unittest

import os
import sys

from ninja_tools import Ninja
from ninja_syntax import Variable, BuildAction
from cc.stub_generator import StubGenerator, GenCcStubsInput, NDKSTUBGEN
from utils import ContextTest

class TestStubGenerator(unittest.TestCase):

    def setUp(self):
        self.ninja_context = ContextTest("test_dir", self.id())

    def _get_stub_inputs(self):
        return GenCcStubsInput("x86", "33", "libfoo.map.txt", "api_levels.json")

    def test_implicit_deps(self):
        """Test that all impicit deps of stub generation are added.

        ndkstubgen and api_levels.json are implicit deps.
        ninja should recomplie stubs if there is a change in either of these.
        """
        ninja = Ninja(context=self.ninja_context, file=None)
        stub_generator = StubGenerator()
        stub_generator.add_stubgen_action(ninja, self._get_stub_inputs(), "out")
        build_actions = [node for node in ninja.nodes if isinstance(node,
                                                                    BuildAction)]
        self.assertIsNotNone(build_actions)
        self.assertTrue(all([NDKSTUBGEN in x.implicits for x in build_actions]))
        self.assertTrue(["api_levels.json" in x.implicits for x in build_actions])

    def test_output_contains_c_stubs(self):
        ninja = Ninja(context=self.ninja_context, file=None)
        stub_generator = StubGenerator()
        outputs = stub_generator.add_stubgen_action(ninja, self._get_stub_inputs(), "out")
        self.assertGreater(len(outputs), 0)
        self.assertTrue("stub.c" in outputs.stub)
        self.assertTrue("stub.map" in outputs.version_script)

if __name__ == "__main__":
    unittest.main()
