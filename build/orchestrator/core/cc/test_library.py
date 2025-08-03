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

"""Unit tests for library.py."""

import unittest

from ninja_tools import Ninja
from ninja_syntax import Rule, BuildAction

from cc.library import CompileContext, Compiler, LinkContext, Linker
from utils import ContextTest

class TestCompiler(unittest.TestCase):

    def setUp(self):
        self.ninja_context = ContextTest("test_dir", self.id())

    def test_clang_is_implicit_dep(self):
        compiler = Compiler()
        compile_context = CompileContext("src", "flags", "out", frontend="my/clang")
        ninja = Ninja(context=self.ninja_context, file=None)
        compiler.compile(ninja, compile_context)
        compile_action_nodes = [node for node in ninja.nodes if isinstance(node, BuildAction)]
        self.assertTrue(all(["my/clang" in node.implicits for node in compile_action_nodes]))

    def test_compile_flags_are_added(self):
        compiler = Compiler()
        compile_context = CompileContext("src", "myflag1 myflag2 myflag3", "out", frontend="my/clang")
        ninja = Ninja(context=self.ninja_context, file=None)
        compiler.compile(ninja, compile_context)
        compile_action_nodes = [node for node in ninja.nodes if isinstance(node, BuildAction)]
        self.assertEqual(len(compile_action_nodes), 1)
        compile_action_node = compile_action_nodes[0]
        variables = sorted(compile_action_node.variables, key=lambda x: x.name)
        self.assertEqual(len(variables), 2)
        self.assertEqual(variables[0].name, "cFlags")
        self.assertEqual(variables[0].value, compile_context.flags)
        self.assertEqual(variables[1].name, "cFrontend")
        self.assertEqual(variables[1].value, compile_context.frontend)

class TestLinker(unittest.TestCase):

    def setUp(self):
        self.ninja_context = ContextTest("test_dir", self.id())

    def test_clang_is_implicit_dep(self):
        linker = Linker()
        link_context = LinkContext("objs", "flags", "out", frontend="my/clang")
        ninja = Ninja(context=self.ninja_context, file=None)
        linker.link(ninja, link_context)
        link_action_nodes = [node for node in ninja.nodes if isinstance(node, BuildAction)]
        self.assertTrue(all(["my/clang" in node.implicits for node in link_action_nodes]))

    def test_link_flags_are_added(self):
        linker = Linker()
        link_context = LinkContext("src", "myflag1 myflag2 myflag3", "out", frontend="my/clang")
        ninja = Ninja(context=self.ninja_context, file=None)
        linker.link(ninja, link_context)
        link_action_nodes = [node for node in ninja.nodes if isinstance(node, BuildAction)]
        self.assertEqual(len(link_action_nodes), 1)
        link_action_node = link_action_nodes[0]
        variables = sorted(link_action_node.variables, key=lambda x: x.name)
        self.assertEqual(len(variables), 2)
        self.assertEqual(variables[0].name, "ldFlags")
        self.assertEqual(variables[0].value, link_context.flags)
        self.assertEqual(variables[1].name, "ldFrontend")
        self.assertEqual(variables[1].value, link_context.frontend)


if __name__ == "__main__":
    unittest.main()
