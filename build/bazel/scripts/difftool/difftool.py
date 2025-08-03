#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
# limitations under the License.
"""Provides useful diff information for build artifacts.

Uses collected build artifacts from two separate build invocations to
compare output artifacts of these builds and/or the commands executed
to generate them.

See the directory-level README for information about full usage, including
the collection step: a preparatory step required before invocation of this
tool.

Use `difftool.py --help` for full usage information of this tool.

Example Usage:
  ./difftool.py [left_dir] [left_output_file] [right_dir] [right_output_file]

Difftool will compare [left_dir]/[left_output_file] and
[right_dir]/[right_output_file] and provide its best insightful analysis on the
differences between these files. The content and depth of this analysis depends
on the types of these files, and also on Difftool"s verbosity mode. Difftool
may also use command data present in the left and right directories as part of
its analysis.
"""

import argparse
import enum
import functools
import json
import os
import pathlib
import re
import subprocess
import sys
from typing import Callable

import clangcompile
import commands
from collect import COLLECTION_INFO_FILENAME

DiffFunction = Callable[[pathlib.Path, pathlib.Path], list[str]]
"""Given two files, produces a list of differences."""


@functools.total_ordering
class DiffLevel(enum.Enum):
  """Defines the level of differences that should trigger a failure.

  E.g. when set to WARNING, differences deemed WARNING or SEVERE are taken into
  account while other differences (INFO, FINE etc.) will be ignored.
  """
  SEVERE = 1
  WARNING = 2
  INFO = 3
  FINE = 4

  def __lt__(self, other):
    if self.__class__ is other.__class__:
      return self.value < other.value
    return NotImplemented


class EnumAction(argparse.Action):
  """Parses command line options into Enum types."""

  def __init__(self, **kwargs):
    enum_type = kwargs.pop("type", None)
    kwargs.setdefault("choices", list(e.name for e in enum_type))
    super(EnumAction, self).__init__(**kwargs)
    self._enum = enum_type

  def __call__(self, parser, namespace, values, option_string=None):
    value = self._enum[values]
    setattr(namespace, self.dest, value)


class ArtifactType(enum.Enum):
  AUTO_INFER_FROM_SUFFIX = 0
  CC_OBJECT = 1
  CC_SHARED_LIBRARY = 2
  CC_OBJECT_WITH_DEBUG_SYMBOLS = 3
  OTHER = 99


FILE_TYPE_CHOICES = {
    "auto": ArtifactType.AUTO_INFER_FROM_SUFFIX,
    "object": ArtifactType.CC_OBJECT,
    "object_with_debug_symbols": ArtifactType.CC_OBJECT_WITH_DEBUG_SYMBOLS,
    "shared_library": ArtifactType.CC_SHARED_LIBRARY,
}


def _artifact_type(file_path):
  ext = file_path.suffix
  if ext in [".o", ".a"]:
    return ArtifactType.CC_OBJECT
  elif ext == ".so":
    return ArtifactType.CC_SHARED_LIBRARY
  else:
    return ArtifactType.OTHER


# TODO(usta) use libdiff
def literal_diff(left_path: pathlib.Path,
                 right_path: pathlib.Path) -> list[str]:
  return subprocess.run(
      ["diff", str(left_path), str(right_path)],
      check=False,
      capture_output=True,
      encoding="utf-8").stdout.splitlines()


@functools.cache
def _diff_fns(artifact_type: ArtifactType,
              level: DiffLevel) -> list[DiffFunction]:
  fns = []

  if artifact_type in [
      ArtifactType.CC_OBJECT, ArtifactType.CC_OBJECT_WITH_DEBUG_SYMBOLS
  ]:
    fns.append(clangcompile.nm_differences)
    if level >= DiffLevel.WARNING:
      fns.append(clangcompile.elf_differences)
      if artifact_type == ArtifactType.CC_OBJECT_WITH_DEBUG_SYMBOLS:
        fns.append(clangcompile.bloaty_differences_compileunits)
      else:
        fns.append(clangcompile.bloaty_differences)
  else:
    fns.append(literal_diff)

  return fns


def collect_commands_bazel(expr: str, config: str, mnemonic: str, *args):
  bazel_tool_path = pathlib.Path("build/bazel/bin/bazel").resolve().absolute()
  bazel_proc = subprocess.run(
      [
          bazel_tool_path,
          "aquery",
          "--curses=no",
          "--config=bp2build",
          "--output=jsonproto",
          f"--config={config}",
          *args,
          f"{expr}",
      ],
      capture_output=True,
      encoding="utf-8",
  )
  print(bazel_proc.stderr)
  actions_json = json.loads(bazel_proc.stdout)
  return [a for a in actions_json["actions"] if a["mnemonic"] == mnemonic]


def collect_commands_ninja(ninja_file_path: pathlib.Path,
                           output_file_path: pathlib.Path,
                           ninja_tool_path: pathlib.Path) -> list[str]:
  """Returns a list of all command lines required to build the file at given

  output_file_path_string, as described by the ninja file present at
  ninja_file_path_string.
  """

  result = subprocess.check_output([
      str(ninja_tool_path), "-f", ninja_file_path, "-t", "commands",
      str(output_file_path)
  ]).decode("utf-8")
  return result.splitlines()


def collect_commands(ninja_file_path: pathlib.Path,
                     output_file_path: pathlib.Path) -> list[str]:
  ninja_tool_path = pathlib.Path(
      "prebuilts/build-tools/linux-x86/bin/ninja").resolve()
  wd = os.getcwd()
  try:
    os.chdir(ninja_file_path.parent.absolute())
    return collect_commands_ninja(
        ninja_file_path.name,
        output_file_path,
        ninja_tool_path,
    )
  except Exception as e:
    raise e
  finally:
    os.chdir(wd)


def file_differences(
    left_path: pathlib.Path,
    right_path: pathlib.Path,
    level=DiffLevel.SEVERE,
    file_type=ArtifactType.AUTO_INFER_FROM_SUFFIX) -> list[str]:
  """Returns differences between the two given files.

  Returns the empty list if these files are deemed "similar enough".
  """

  errors = []
  if not left_path.is_file():
    errors += ["%s does not exist" % left_path]
  if not right_path.is_file():
    errors += ["%s does not exist" % right_path]
  if errors:
    return errors

  if file_type is ArtifactType.AUTO_INFER_FROM_SUFFIX:
    file_type = _artifact_type(left_path)
    right_type = _artifact_type(right_path)
    if file_type != right_type:
      errors += ["file types differ: %s and %s" % (file_type, right_type)]
      return errors

  for fn in _diff_fns(file_type, level):
    errors += fn(left_path, right_path)

  return errors


def parse_collection_info(info_file_path: pathlib.Path):
  """Parses the collection info file at the given path and returns details."""
  if not info_file_path.is_file():
    raise Exception("Expected file %s was not found. " % info_file_path +
                    "Did you run collect.py for this directory?")

  info_contents = info_file_path.read_text().splitlines()
  ninja_path = pathlib.Path(info_contents[0])
  target_file = None

  if len(info_contents) > 1 and info_contents[1]:
    target_file = info_contents[1]

  return ninja_path, target_file


# Pattern to parse out env-setting command prefix, for example:
#
# FOO=BAR KEY=VALUE {main_command_args}
env_set_prefix_pattern = re.compile("^(( )*([^ =]+=[^ =]+)( )*)+(.*)$")

# Pattern to parse out command prefixes which cd into the execroot and
# then remove the old output. For example:
#
# cd path/to/execroot && rm old_output && {main_command}
cd_rm_prefix_pattern = re.compile("^cd [^&]* &&( )+rm [^&]* && (.*)$")

# Pattern to parse out any trailing comment suffix. For example:
#
# {main_command} # This comment should be removed.
comment_suffix_pattern = re.compile("(.*) # .*")


def _remove_rbe_tokens(tokens, tool_endings):
  for i in range(len(tokens)):
    for ending in tool_endings:
      if tokens[i].endswith(ending):
        return tokens[i:]
  return None


def rich_command_info(raw_command):
  """Returns a command info object describing the raw command string."""
  cmd = raw_command.strip()
  # Remove things unrelated to the core command.
  m = env_set_prefix_pattern.fullmatch(cmd)
  if m is not None:
    cmd = m.group(5)
  m = cd_rm_prefix_pattern.fullmatch(cmd)
  if m is not None:
    cmd = m.group(2)
  m = comment_suffix_pattern.fullmatch(cmd)
  if m is not None:
    cmd = m.group(1)
  tokens = cmd.split()
  tokens_without_rbe = _remove_rbe_tokens(tokens, ["clang", "clang++"])
  if tokens_without_rbe:
    tokens = tokens_without_rbe
  tool = tokens[0]
  args = tokens[1:]

  if tool.endswith("clang") or tool.endswith("clang++"):
    # TODO(cparsons): Disambiguate between clang compile and other clang
    # commands.
    return clangcompile.ClangCompileInfo(tool=tool, args=args)
  else:
    return commands.CommandInfo(tool=tool, args=args)


def main():
  parser = argparse.ArgumentParser(description="")
  parser.add_argument(
      "--level",
      action=EnumAction,
      default=DiffLevel.SEVERE,
      type=DiffLevel,
      help="the level of differences to be considered." +
      "Diffs below the specified level are ignored.")
  parser.add_argument(
      "--verbose",
      "-v",
      action=argparse.BooleanOptionalAction,
      default=False,
      help="log verbosely.")
  parser.add_argument(
      "left_dir",
      help="the 'left' directory to compare build outputs " +
      "from. This must be the target of an invocation of collect.py.")
  parser.add_argument(
      "--left_file",
      "-l",
      dest="left_file",
      default=None,
      help="the output file (relative to execution root) for " +
      "the 'left' build invocation.")
  parser.add_argument(
      "right_dir",
      help="the 'right' directory to compare build outputs " +
      "from. This must be the target of an invocation of collect.py.")
  parser.add_argument(
      "--right_file",
      "-r",
      dest="right_file",
      default=None,
      help="the output file (relative to execution root) " +
      "for the 'right' build invocation.")
  parser.add_argument(
      "--file_type",
      dest="file_type",
      default="auto",
      choices=FILE_TYPE_CHOICES.keys(),
      help="the type of file being diffed (overrides automatic " +
      "filetype resolution)")
  parser.add_argument(
      "--allow_missing_file",
      action=argparse.BooleanOptionalAction,
      default=False,
      help="allow a missing output file; this is useful to " +
      "compare actions even in the absence of an output file.")
  args = parser.parse_args()

  level = args.level
  left_diffinfo = pathlib.Path(args.left_dir).joinpath(COLLECTION_INFO_FILENAME)
  right_diffinfo = pathlib.Path(
      args.right_dir).joinpath(COLLECTION_INFO_FILENAME)

  left_ninja_name, left_file = parse_collection_info(left_diffinfo)
  right_ninja_name, right_file = parse_collection_info(right_diffinfo)
  if args.left_file:
    left_file = pathlib.Path(args.left_file)
  if args.right_file:
    right_file = pathlib.Path(args.right_file)

  if left_file is None:
    raise Exception("No left file specified. Either run collect.py with a " +
                    "target file, or specify --left_file.")
  if right_file is None:
    raise Exception("No right file specified. Either run collect.py with a " +
                    "target file, or specify --right_file.")

  left_path = pathlib.Path(args.left_dir).joinpath(left_file)
  right_path = pathlib.Path(args.right_dir).joinpath(right_file)
  if not args.allow_missing_file:
    if not left_path.is_file():
      raise RuntimeError("Expected file %s was not found. " % left_path)
    if not right_path.is_file():
      raise RuntimeError("Expected file %s was not found. " % right_path)

  file_diff_errors = file_differences(left_path, right_path, level,
                                      FILE_TYPE_CHOICES[args.file_type])

  if file_diff_errors:
    for err in file_diff_errors:
      print(err)
    if args.verbose:
      left_ninja_path = pathlib.Path(args.left_dir).joinpath(left_ninja_name)
      left_commands = collect_commands(left_ninja_path, left_file)
      left_command_info = rich_command_info(left_commands[-1])
      right_ninja_path = pathlib.Path(args.right_dir).joinpath(right_ninja_name)
      right_commands = collect_commands(right_ninja_path, right_file)
      right_command_info = rich_command_info(right_commands[-1])
      print("======== ACTION COMPARISON: ========")
      print("=== LEFT ONLY:\n")
      print(left_command_info.compare(right_command_info))
      print()
      print("=== RIGHT ONLY:\n")
      print(right_command_info.compare(left_command_info))
      print()
    sys.exit(1)
  else:
    print(f"{left_file} matches\n{right_file}")
  sys.exit(0)


if __name__ == "__main__":
  main()
