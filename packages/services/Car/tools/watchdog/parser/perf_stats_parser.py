#!/usr/bin/python3

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
#

"""Tool to parse CarWatchdog's performance stats dump.

To build the parser script run:
  m perf_stats_parser

To parse a carwatchdog dump text file run:
  perf_stats_parser -f <cw-dump>.txt -o cw_proto_out.pb

To read a carwatchdog proto file as a json run:
  perf_stats_parser -r <cw-proto-out>.pb -j
"""

import argparse
import json
import os
import sys

from . import carwatchdog_dump_parser
from . import perf_stats_proto_utils


def init_arguments() -> argparse.Namespace:
  """Initializes the program arguments."""
  parser = argparse.ArgumentParser(description="Parses CarWatchdog's dump.")
  parser.add_argument(
      "-f",
      "--file",
      dest="file",
      default="dump.txt",
      help="File with the CarWatchdog dump",
  )
  parser.add_argument(
      "-o",
      "--out",
      dest="out",
      help="protobuf binary with parsed performance stats",
  )
  parser.add_argument(
      "-b",
      "--build",
      dest="build",
      help="File with Android device build information",
  )
  parser.add_argument(
      "-d",
      "--device-out",
      dest="device_out",
      default="device_perf_stats.pb",
      help="protobuf binary with build information",
  )
  parser.add_argument(
      "-p",
      "--print",
      dest="print",
      action="store_true",
      help=(
          "prints the parsed performance data to the console "
          "when out proto defined"
      ),
  )
  parser.add_argument(
      "-r",
      "--read",
      dest="read_proto",
      help=(
          "Protobuf binary to be printed in console. If this "
          "flag is set no other process is executed."
      ),
  )
  parser.add_argument(
      "-D",
      "--device-run",
      dest="device_run",
      action="store_true",
      help=(
          "Specifies that the proto to be read is a "
          "DevicePerformanceStats proto. (Only checked if "
          "-r is set)"
      ),
  )
  parser.add_argument(
      "-j",
      "--json",
      dest="json",
      action="store_true",
      help="Generate a JSON file from the protobuf binary read.",
  )

  return parser.parse_args()


if __name__ == "__main__":
  args = init_arguments()

  if args.read_proto:
    if not os.path.isfile(args.read_proto):
      print("Error: Proto binary '%s' does not exist" % args.read_proto)
      sys.exit(1)
    if args.device_run:
      performance_stats = (
          perf_stats_proto_utils.read_device_performance_stats_pb(
              args.read_proto
          )
      )
    else:
      performance_stats = perf_stats_proto_utils.read_performance_stats_pb(
          args.read_proto
      )
    if performance_stats is None:
      print(f"Error: Could not read '{args.read_proto}'")
      sys.exit(1)
    if args.json:
      print(json.dumps(performance_stats.to_dict()))
    else:
      print(performance_stats)
    sys.exit()

  if not os.path.isfile(args.file):
    print("Error: File '%s' does not exist" % args.file)
    sys.exit(1)

  with open(args.file, "r", encoding="UTF-8", errors="ignore") as f:
    performance_stats = carwatchdog_dump_parser.parse_dump(f.read())

    build_info = None
    if args.build:
      build_info = carwatchdog_dump_parser.parse_build_info(args.build)
      print(build_info)

    if performance_stats.is_empty():
      print(
          "Error: No performance stats were parsed. Make sure dump file"
          " contains carwatchdog's dump text."
      )
      sys.exit(1)

    if (args.out or args.build) and perf_stats_proto_utils.write_pb(
        performance_stats, args.out, build_info, args.device_out
    ):
      out_file = args.out if args.out else args.device_out
      print("Output protobuf binary in:", out_file)

    if args.print or not (args.out or args.build):
      if args.json:
        print(json.dumps(performance_stats.to_dict()))
        sys.exit()
      print(performance_stats)
