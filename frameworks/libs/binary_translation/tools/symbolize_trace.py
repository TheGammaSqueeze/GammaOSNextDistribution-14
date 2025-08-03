#!/usr/bin/python
#
# Copyright (C) 2023 The Android Open Source Project
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
#

import argparse
import bisect
import os
import subprocess
import sys

PROT_READ = 1
PROT_WRITE = 2
PROT_EXEC = 4


class MapEntry:
  start = -1
  end = -1
  prot = 0
  offset = 0
  file = ""
  base_addr = -1


class TraceParser:

  def __init__(self, tombstone, trace, symbols_dir):
    self.maps = {}
    self.tombstone = tombstone
    self.trace = trace
    self.symbols_dir = symbols_dir

  def parse_prot(self, prot_str):
    prot = 0
    if prot_str[0] == "r":
      prot |= PROT_READ
    if prot_str[1] == "w":
      prot |= PROT_WRITE
    if prot_str[2] == "x":
      prot |= PROT_EXEC

    return prot

  def parse_tombstone_map_entry(self, line, line_number):
    if not line.startswith("    ") and not line.startswith("--->"):
      raise Exception(
          "Unexpected line (" + line_number + ") in maps section: " + line
      )

    if line.startswith("--->Fault address"):
      return

    line = line[3:]  # throw away indent/prefix

    entries = line.split(maxsplit=5)

    addrs = entries[0].split("-")
    map_entry = MapEntry()
    map_entry.start = int(addrs[0].replace("'", ""), 16)
    map_entry.end = int(addrs[1].replace("'", ""), 16)
    map_entry.prot = self.parse_prot(entries[1])
    map_entry.offset = int(entries[2], 16)
    map_entry.size = int(entries[3], 16)
    if len(entries) >= 5:
      map_entry.file = entries[4]

    # The default base address is start
    map_entry.base_addr = map_entry.start

    # Skip PROT_NONE mappings so they do not interfere with
    # file mappings
    if map_entry.prot == 0:
      return

    self.maps[map_entry.start] = map_entry

  def read_maps_from_tombstone(self):
    with open(self.tombstone, "r") as f:
      maps_section_started = False
      line_number = 0
      for line in f:
        line_number += 1
        if maps_section_started:
          # Maps section ends when we hit either '---------' or end of file
          if line.startswith("---------"):
            break
          self.parse_tombstone_map_entry(line, line_number)
        else:
          maps_section_started = line.startswith("memory map")

  def calculate_base_addr_for_map_entries(self):
    # Ascending order of start_addr (key) is important here
    last_file = None
    current_base_addr = -1
    for key in sorted(self.maps):
      # For now we are assuming load_bias is 0, revisit once proved otherwise
      # note that load_bias printed in tombstone is incorrect atm
      map = self.maps[key]
      if not map.file:
        continue

      # treat /memfd as if it was anon mapping
      if map.file.startswith("/memfd:"):
        continue

      if map.file != last_file:
        last_file = map.file
        current_base_addr = map.start

      map.base_addr = current_base_addr

  def addr2line(self, address, file):
    if not file:
      print("error: no file")
      return None

    p = subprocess.run(
        ["addr2line", "-e", self.symbols_dir + file, hex(address)],
        capture_output=True,
        text=True,
    )
    if p.returncode != 0:
      # print("error: ", p.stderr)
      return None
    return p.stdout.strip()

  def symbolize_trace(self):
    with open(self.trace, "r") as f:
      sorted_start_addresses = sorted(self.maps.keys())

      for line in f:
        tokens = line.split(maxsplit=2)
        if len(tokens) <= 2:
          continue
        msg = tokens[2]
        if not msg.startswith("RunGeneratedCode @"):
          continue

        address = int(msg.split("@")[1].strip(), 16)

        pos = bisect.bisect_right(sorted_start_addresses, address)
        map = self.maps[sorted_start_addresses[pos]]

        if address > map.end:
          print("%x (not maped)" % address)
          continue

        relative_addr = address - map.base_addr

        file_and_line = self.addr2line(relative_addr, map.file)
        if file_and_line:
          print(
              "%x (%s+%x) %s" % (address, map.file, relative_addr, file_and_line)
          )
        else:
          print("%x (%s+%x)" % (address, map.file, relative_addr))

  def parse(self):
    self.read_maps_from_tombstone()
    self.calculate_base_addr_for_map_entries()
    self.symbolize_trace()


def get_symbol_dir(args):
  if args.symbols_dir:
    return symbols_dir

  product_out = os.environ.get("ANDROID_PRODUCT_OUT")
  if not product_out:
    raise Error(
        "--symbols_dir is not set and unable to resolve ANDROID_PRODUCT_OUT via"
        " environment variable"
    )

  return product_out + "/symbols"


def main():
  argument_parser = argparse.ArgumentParser()
  argument_parser.add_argument(
      "trace", help="file containing berberis trace output"
  )
  # TODO(b/232598137): Make it possible to read maps from /proc/pid/maps format as an
  # alternative option
  argument_parser.add_argument(
      "tombstone", help="Tombstone of the corresponding crash"
  )
  argument_parser.add_argument(
      "--symbols_dir",
      help="Symbols dir (default is '$ANDROID_PRODUCT_OUT/symbols')",
  )

  args = argument_parser.parse_args()

  parser = TraceParser(args.tombstone, args.trace, get_symbol_dir(args))
  parser.parse()


if __name__ == "__main__":
  sys.exit(main())

