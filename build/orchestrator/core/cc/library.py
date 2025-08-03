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

"""A module for compiling and linking cc artifacts."""

from ninja_tools import Ninja
from ninja_syntax import BuildAction, Rule

from functools import lru_cache
from typing import List

class CompileContext():

    def __init__(self, src: str, flags:str, out: str, frontend: str):
        self.src = src
        self.flags = flags
        self.out = out
        self.frontend = frontend

class Compiler():

    @lru_cache(maxsize=None)
    def _create_compile_rule(self, ninja: Ninja) -> Rule:
        rule = Rule("cc")
        rule.add_variable("description", "compile source to object file using clang/clang++")
        rule.add_variable("command", "${cFrontend} -c ${cFlags} -o ${out} ${in}")
        ninja.add_rule(rule)
        return rule

    def compile(self, ninja: Ninja, compile_context: CompileContext) -> None:
        compile_rule = self._create_compile_rule(ninja)

        compile_action = BuildAction(output=compile_context.out,
                    inputs=compile_context.src,
                    rule=compile_rule.name,
                    implicits=[compile_context.frontend]
                    )
        compile_action.add_variable("cFrontend", compile_context.frontend)
        compile_action.add_variable("cFlags", compile_context.flags)
        ninja.add_build_action(compile_action)

class LinkContext():

    def __init__(self, objs: List[str], flags: str, out: str, frontend: str):
        self.objs = objs
        self.flags = flags
        self.out = out
        self.frontend = frontend
        self.implicits = [frontend]

    def add_implicits(self, implicits: List[str]):
        self.implicits.extend(implicits)

class Linker():

    @lru_cache(maxsize=None)
    def _create_link_rule(self, ninja: Ninja) -> Rule:
        rule = Rule("ld")
        rule.add_variable("description", "link object files using clang/clang++")
        rule.add_variable("command", "${ldFrontend} ${ldFlags} -o ${out} ${in}")
        ninja.add_rule(rule)
        return rule

    def link(self, ninja: Ninja, link_context: LinkContext) -> None:
        link_rule = self._create_link_rule(ninja)

        link_action = BuildAction(output=link_context.out,
                inputs=link_context.objs,
                rule=link_rule.name,
                implicits=link_context.implicits,
                )
        link_action.add_variable("ldFrontend", link_context.frontend)
        link_action.add_variable("ldFlags", link_context.flags)
        ninja.add_build_action(link_action)
