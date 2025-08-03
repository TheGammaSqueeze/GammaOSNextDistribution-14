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
"""Helpers pertaining to clang compile actions."""

import collections
import pathlib
import subprocess
from commands import CommandInfo
from commands import flag_repr
from commands import is_flag_starts_with
from commands import parse_flag_groups
from diffs.diff import ExtractInfo
from diffs.context import ContextDiff
from diffs.nm import NmSymbolDiff
from diffs.bloaty import BloatyDiff


class ClangCompileInfo(CommandInfo):
  """Contains information about a clang compile action commandline."""

  def __init__(self, tool, args):
    CommandInfo.__init__(self, tool, args)

    flag_groups = parse_flag_groups(args, _custom_flag_group)

    misc = []
    i_includes = []
    iquote_includes = []
    isystem_includes = []
    defines = []
    warnings = []
    features = []
    libraries = []
    linker_args = []
    assembler_args = []
    file_flags = []
    for g in flag_groups:
      if is_flag_starts_with("D", g) or is_flag_starts_with("U", g):
        defines += [g]
      elif is_flag_starts_with("f", g):
        features += [g]
      elif is_flag_starts_with("l", g):
        libraries += [g]
      elif is_flag_starts_with("Wl", g):
        linker_args += [g]
      elif is_flag_starts_with("Wa", g) and not is_flag_starts_with("Wall", g):
        assembler_args += [g]
      elif is_flag_starts_with("W", g) or is_flag_starts_with("w", g):
        warnings += [g]
      elif is_flag_starts_with("I", g):
        i_includes += [g]
      elif is_flag_starts_with("isystem", g):
        isystem_includes += [g]
      elif is_flag_starts_with("iquote", g):
        iquote_includes += [g]
      elif (
          is_flag_starts_with("MF", g)
          or is_flag_starts_with("o", g)
          or _is_src_group(g)
      ):
        file_flags += [g]
      else:
        misc += [g]
    self.features = features
    self.defines = _process_defines(defines)
    self.libraries = libraries
    self.linker_args = linker_args
    self.assembler_args = assembler_args
    self.i_includes = _process_includes(i_includes)
    self.iquote_includes = _process_includes(iquote_includes)
    self.isystem_includes = _process_includes(isystem_includes)
    self.file_flags = file_flags
    self.warnings = warnings
    self.misc_flags = sorted(misc, key=flag_repr)

  def _str_for_field(self, field_name, values):
    s = "  " + field_name + ":\n"
    for x in values:
      s += "    " + flag_repr(x) + "\n"
    return s

  def __str__(self):
    s = "ClangCompileInfo:\n"

    for label, fields in {
        "Features": self.features,
        "Defines": self.defines,
        "Libraries": self.libraries,
        "Linker args": self.linker_args,
        "Assembler args": self.assembler_args,
        "Includes (-I,": self.i_includes,
        "Includes (-iquote,": self.iquote_includes,
        "Includes (-isystem,": self.isystem_includes,
        "Files": self.file_flags,
        "Warnings": self.warnings,
        "Misc": self.misc_flags,
    }.items():
      if len(fields) > 0:
        s += self._str_for_field(label, list(set(fields)))

    return s

  def compare(self, other):
    """computes difference in arguments from another ClangCompileInfo"""
    diffs = ClangCompileInfo(self.tool, [])
    diffs.defines = [i for i in self.defines if i not in other.defines]
    diffs.warnings = [i for i in self.warnings if i not in other.warnings]
    diffs.features = [i for i in self.features if i not in other.features]
    diffs.libraries = [i for i in self.libraries if i not in other.libraries]
    diffs.linker_args = [
        i for i in self.linker_args if i not in other.linker_args
    ]
    diffs.assembler_args = [
        i for i in self.assembler_args if i not in other.assembler_args
    ]
    diffs.i_includes = [i for i in self.i_includes if i not in other.i_includes]
    diffs.iquote_includes = [
        i for i in self.iquote_includes if i not in other.iquote_includes
    ]
    diffs.isystem_includes = [
        i for i in self.isystem_includes if i not in other.isystem_includes
    ]
    diffs.file_flags = [i for i in self.file_flags if i not in other.file_flags]
    diffs.misc_flags = [i for i in self.misc_flags if i not in other.misc_flags]
    return diffs


def _is_src_group(x):
  """Returns true if the given flag group describes a source file."""
  return isinstance(x, str) and x.endswith(".cpp")


def _custom_flag_group(x):
  """Identifies single-arg flag groups for clang compiles.

  Returns a flag group if the given argument corresponds to a single-argument
  flag group for clang compile. (For example, `-c` is a single-arg flag for
  clang compiles, but may not be for other tools.)

  See commands.parse_flag_groups documentation for signature details.
  """
  if x.startswith("-I") and len(x) > 2:
    return ("I", x[2:])
  if x.startswith("-W") and len(x) > 2:
    return x
  elif x == "-c":
    return x
  return None


def _process_defines(defs):
  """Processes and returns deduplicated define flags from all define args."""
  # TODO(cparsons): Determine and return effective defines (returning the last
  # set value).
  defines_by_var = collections.defaultdict(list)
  for x in defs:
    if isinstance(x, tuple):
      var_name = x[0][2:]
    else:
      var_name = x[2:]
    defines_by_var[var_name].append(x)
  result = []
  for k in sorted(defines_by_var):
    d = defines_by_var[k]
    for x in d:
      result += [x]
  return result


def _process_includes(includes):
  # Drop genfiles directories; makes diffing easier.
  result = []
  for x in includes:
    if isinstance(x, tuple):
      if not x[1].startswith("bazel-out"):
        result += [x]
    else:
      result += [x]
  return result


def _external_tool(*args) -> ExtractInfo:
  return lambda file: subprocess.run(
      [*args, str(file)], check=True, capture_output=True, encoding="utf-8"
  ).stdout.splitlines()


# TODO(usta) use nm as a data dependency
def nm_differences(
    left_path: pathlib.Path, right_path: pathlib.Path
) -> list[str]:
  """Returns differences in symbol tables.

  Returns the empty list if these files are deemed "similar enough".
  """
  return NmSymbolDiff(_external_tool("nm"), "symbol tables").diff(
      left_path, right_path
  )


# TODO(usta) use readelf as a data dependency
def elf_differences(
    left_path: pathlib.Path, right_path: pathlib.Path
) -> list[str]:
  """Returns differences in elf headers.

  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files.
  """
  return ContextDiff(_external_tool("readelf", "-h"), "elf headers").diff(
      left_path, right_path
  )


# TODO(usta) use bloaty as a data dependency
def bloaty_differences(
    left_path: pathlib.Path, right_path: pathlib.Path
) -> list[str]:
  """Returns differences in symbol and section tables.

  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files.
  """
  return _bloaty_differences(left_path, right_path)


# TODO(usta) use bloaty as a data dependency
def bloaty_differences_compileunits(
    left_path: pathlib.Path, right_path: pathlib.Path
) -> list[str]:
  """Returns differences in symbol and section tables.

  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files.
  """
  return _bloaty_differences(left_path, right_path, True)


# TODO(usta) use bloaty as a data dependency
def _bloaty_differences(
    left_path: pathlib.Path, right_path: pathlib.Path, debug=False
) -> list[str]:
  symbols = BloatyDiff(
      "symbol tables", "symbols", has_debug_symbols=debug
  ).diff(left_path, right_path)
  sections = BloatyDiff(
      "section tables", "sections", has_debug_symbols=debug
  ).diff(left_path, right_path)
  segments = BloatyDiff(
      "segment tables", "segments", has_debug_symbols=debug
  ).diff(left_path, right_path)
  return symbols + sections + segments
