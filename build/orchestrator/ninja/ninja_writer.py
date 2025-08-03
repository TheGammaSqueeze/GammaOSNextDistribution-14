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
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import functools

from ninja_syntax import Variable, BuildAction, Rule, Pool, Subninja, Line


# TODO: Format the output according to a configurable width variable
# This will ensure that the generated content fits on a screen and does not
# require horizontal scrolling
class Writer:
    def __init__(self, file, builddir: str = None):
        self.file = file
        self.nodes = []  # type Node
        if builddir:
            self.add_variable(Variable('builddir', builddir))

    def add_variable(self, variable: Variable):
        self.nodes.append(variable)

    @functools.lru_cache(maxsize=None)
    def add_rule(self, rule: Rule):
        self.nodes.append(rule)

    @functools.lru_cache(maxsize=None)
    def add_build_action(self, build_action: BuildAction):
        self.nodes.append(build_action)

    def add_pool(self, pool: Pool):
        self.nodes.append(pool)

    def add_comment(self, comment: str):
        self.nodes.append(Line(value=f"# {comment}"))

    def add_default(self, default: str):
        self.nodes.append(Line(value=f"default {default}"))

    def add_newline(self):
        self.nodes.append(Line(value=""))

    def add_subninja(self, subninja: Subninja):
        self.nodes.append(subninja)

    def add_phony(self, name: str, deps: tuple):
        self.add_build_action(BuildAction(output=name, rule="phony", inputs=deps))

    def write(self):
        for node in self.nodes:
            for line in node.stream():
                print(line, file=self.file)
