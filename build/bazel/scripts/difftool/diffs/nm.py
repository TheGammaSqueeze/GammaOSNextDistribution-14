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

import pathlib
import re
import subprocess
from diffs.diff import Diff, ExtractInfo

class _Symbol:
    """Data structure to hold a symbol as specified by nm

    Equality of symbols is based on their name and attributes.

    The self._addr property is excluded from comparisons in this class
    because the location of a symbol in one binary is not a useful
    difference from another binary.
    """
    def __init__(self, name, addr, attr):
        self.name = name
        self._addr = addr
        self.attr = attr
    def __hash__(self):
        return (self.name + self.attr).__hash__()
    def __eq__(self, other):
        return self.name == other.name and self.attr == other.attr
    def __repr__(self):
        return f"{self.name}{{{self.attr}}}"


class NmSymbolDiff(Diff):
  """Compares symbols the symbol table output by nm

  Example nm output:
  0000000000000140 t GetExceptionSummary
                   U ExpandableStringInitialize
                   U ExpandableStringRelease
  0000000000000cf0 T jniCreateString

  The first column is the address of the symbol in the binary, the second
  column is an attribute associated with the symbol (see man nm for details),
  and the last column is the demangled symbol name.
  """
  _nm_re = re.compile(r"^(\w+)?\s+([a-zA-Z])\s(\S+)$")

  def __init__(self, tool: ExtractInfo, tool_name: str):
    self.tool = tool
    self.tool_name = tool_name

  def _read_symbols(nm_output):
      symbols = set()
      for line in nm_output:
          match = NmSymbolDiff._nm_re.match(line)
          if match:
              symbols.add(_Symbol(match.group(3), match.group(1), match.group(2)))
      return symbols

  def diff(self, left_path: pathlib.Path, right_path: pathlib.Path) -> list[str]:
    left_nm = subprocess.run(["nm", left_path], capture_output=True, encoding="utf-8").stdout.splitlines()
    right_nm = subprocess.run(["nm", right_path], capture_output=True, encoding="utf-8").stdout.splitlines()
    left_symbols = NmSymbolDiff._read_symbols(left_nm)
    right_symbols = NmSymbolDiff._read_symbols(right_nm)

    left_only = []
    for s in left_symbols:
        if s not in right_symbols:
          left_only.append(s)
    right_only = []
    for s in right_symbols:
        if s not in left_symbols:
          right_only.append(s)

    errors = []
    if left_only:
      errors.append(f"symbols in {left_path} not in {right_path}:")
      errors.extend("\t" + str(s) for s in left_only)
    if right_only:
      errors.append(f"symbols in {right_path} not in {left_path}:")
      errors.extend("\t" + str(s) for s in right_only)

    return errors
