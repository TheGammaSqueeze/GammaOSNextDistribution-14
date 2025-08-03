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

import csv
import pathlib
import subprocess
from diffs.diff import Diff, ExtractInfo


class BloatyDiff(Diff):
  """BloatyDiff compares the sizes of symbols present in cc objects

  Bloaty McBloatface (bloaty) is used to discover size differences in object
  files or cc binaries. This diff returns a list of symbols which are new or
  larger in one file than the other.

  The output does not distinguish between new symbols and ones that are simply
  larger, so this output is best combined with the NmSymbolDiff to see which
  symbols are new.

  Example bloaty output (note: compileunits may not always be available):
  $ bloaty --csv -d compileunits,symbols $BAZEL_OBJ -- $LEGACY_OBJ
  compileunits,symbols,vmsize,filesize
  external/zstd/lib/compress/zstd_fast.c,ZSTD_compressBlock_doubleFast_extDict_generic,6240,6344
  external/zstd/lib/compress/zstd_fast.c,ZSTD_compressBlock_lazy_dictMatchState,-3428,-3551

  The first entry is a symbol that is larger in the Bazel version of the binary,
  and the second entry is a symbol that is larger in the Soong version of the
  binary.
  """
  def __init__(self, tool_name, data_source, has_debug_symbols=False):
    self.tool_name = tool_name
    self.data_source = data_source
    self.has_debug_symbols = has_debug_symbols

  def _print_diff_row(self, row, ignore_keys):
    attrs = sorted({
      k: v
      for k, v in row.items()
      if k not in ignore_keys
    }.items())
    return row[self.data_source] + ": { " + ", ".join(f"{a[0]}: {a[1]}" for a in attrs) + " }"

  def _collect_diff_compileunits(self, diffreader: csv.DictReader):
    # maps from compileunit to list of diff rows
    left_bigger = collections.defaultdict(list)
    right_bigger = collections.defaultdict(list)

    for row in diffreader:
      compileunit = row["compileunits"]
      if len(compileunit) > 0 and compileunit[0] == "[":
        continue
      filesize = row["filesize"]
      if int(filesize) < 0:
        left_bigger[compileunit].append(row)
      elif int(filesize) > 0:
        right_bigger[compileunit].append(row)

    def print_diff_dict(dict):
      lines = []
      for compileunit, data in sorted(dict.items()):
        lines.append("\t" + compileunit + ":")
        rows = []
        for row in data:
          if row[self.data_source] and row[self.data_source][0] == "[":
            continue
          rows.append("\t\t" + self.print_diff_row(row, ignore_keys=[self.data_source, "compileunits"]))
        lines.extend(sorted(rows))
      return "\n".join(lines)

    return print_diff_dict(left_bigger), print_diff_dict(right_bigger)

  def _collect_diff(self, diffreader):
    left_bigger = []
    right_bigger = []

    for row in diffreader:
      filesize = row["filesize"]
      if int(filesize) > 0:
        left_bigger.append(row)
      elif int(filesize) < 0:
        right_bigger.append(row)

    left_errors = "\n".join(["\t" + self._print_diff_row(row, ignore_keys=[self.data_source]) for row in left_bigger])
    right_errors = "\n".join(["\t" + self._print_diff_row(row, ignore_keys=[self.data_source]) for row in right_bigger])
    return left_errors, right_errors

  def diff(self, left_path: pathlib.Path, right_path: pathlib.Path) -> list[str]:
    try:
      diff_csv = subprocess.run(["bloaty",
                                  "--csv",
                                  "-n", "0",
                                  "-w",
                                  "-d",
                                  self.data_source + (",compileunits" if self.has_debug_symbols else ""),
                                  str(left_path),
                                  "--",
                                  str(right_path)],
                                 check=True, capture_output=True,
                                 encoding="utf-8").stdout.splitlines()
    except subprocess.CalledProcessError as e:
      print("ERROR: bloaty tool returned non-zero exit status")
      if self.has_debug_symbols:
        print("ERROR: do objects contain debug symbols?")
      raise e

    diffreader = csv.DictReader(diff_csv)

    if self.has_debug_symbols:
      left_bigger, right_bigger = self._collect_diff_compileunits(diffreader)
    else:
      left_bigger, right_bigger = self._collect_diff(diffreader)

    errors = []
    if left_bigger:
      errors.append(f"the following {self.data_source} are either unique or larger in\n{left_path}\n than those in\n{right_path}:\n{left_bigger}")
    if right_bigger:
      errors.append(f"the following {self.data_source} are either unique or larger in\n{right_path}\n than those in\n{left_path}:\n{right_bigger}")

    return errors
