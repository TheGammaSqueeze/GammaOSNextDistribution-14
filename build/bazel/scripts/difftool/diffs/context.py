#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License."""

import difflib
import pathlib
from diffs.diff import Diff, ExtractInfo


class ContextDiff(Diff):
  def __init__(self, tool: ExtractInfo, tool_name: str):
    self.tool = tool
    self.tool_name = tool_name

  def diff(self, left_path: pathlib.Path, right_path: pathlib.Path) -> list[str]:
    errors = []

    left = self.tool(left_path)
    right = self.tool(right_path)
    comparator = difflib.context_diff(left, right)
    difflines = list(comparator)
    if difflines:
      err = "\n".join(difflines)
      errors.append(
        f"{left_path}\ndiffers from\n{right_path}\nper {self.tool_name}:\n{err}")
    return errors
