#!/usr/bin/env python3
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
"""A tool to print human-readable metrics information regarding the last build.

By default, the consumed files will be located in $ANDROID_BUILD_TOP/out/. You
may pass in a different directory instead using the metrics_files_dir flag.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tarfile

from bazel_metrics_proto.bazel_metrics_pb2 import BazelMetrics
from bp2build_metrics_proto.bp2build_metrics_pb2 import Bp2BuildMetrics, UnconvertedReasonType
from google.protobuf import json_format
from metrics_proto.metrics_pb2 import MetricsBase, SoongBuildMetrics


class Event(object):
  """Contains nested event data.

  Fields:
    name: The short name of this event e.g. the 'b' in an event called a.b.
    start_time_relative_ns: Time since the epoch that the event started
    duration_ns: Duration of this event, including time spent in children.
  """

  def __init__(self, name, start_time_relative_ns, duration_ns):
    self.name = name
    self.start_time_relative_ns = start_time_relative_ns
    self.duration_ns = duration_ns


def _get_output_file(output_dir, filename):
  file_base = os.path.splitext(filename)[0]
  return os.path.join(output_dir, file_base + ".json")


def _get_default_out_dir(metrics_dir):
  return os.path.join(metrics_dir, "analyze_build_output")


def _get_default_metrics_dir():
  """Returns the filepath for the build output."""
  out_dir = os.getenv("OUT_DIR")
  if out_dir:
    return out_dir
  build_top = os.getenv("ANDROID_BUILD_TOP")
  if not build_top:
    raise Exception(
        "$ANDROID_BUILD_TOP not found in environment. Have you run lunch?"
    )
  return os.path.join(build_top, "out")


def _write_event(out, event):
  """Writes an event. See _write_events for args."""
  out.write(
      "%(start)9s  %(duration)9s  %(name)s\n"
      % {
          "start": _format_ns(event.start_time_relative_ns),
          "duration": _format_ns(event.duration_ns),
          "name": event.name,
      }
  )


def _print_metrics_event_times(description, metrics):
  # Bail if there are no events
  raw_events = metrics.events
  if not raw_events:
    print("%s: No events to display" % description)
    return
  print("-- %s events --" % description)

  # Update the start times to be based on the first event
  first_time_ns = min([event.start_time for event in raw_events])
  events = [
      Event(
          getattr(e, "description", e.name),
          e.start_time - first_time_ns,
          e.real_time,
      )
      for e in raw_events
  ]

  # Sort by start time so the nesting also is sorted by time
  events.sort(key=lambda x: x.start_time_relative_ns)

  # Output the results
  print("    start   duration")

  for event in events:
    _write_event(sys.stdout, event)
  print()


def _format_ns(duration_ns):
  "Pretty print duration in nanoseconds"
  return "%.02fs" % (duration_ns / 1_000_000_000)


def _read_data(filepath, proto):
  with open(filepath, "rb") as f:
    proto.ParseFromString(f.read())
    f.close()


def _maybe_save_data(proto, filename, args):
  if args.skip_metrics:
    return
  json_out = json_format.MessageToJson(proto)
  output_filepath = _get_output_file(args.output_dir, filename)
  _save_file(json_out, output_filepath)


def _save_file(data, file):
  with open(file, "w") as f:
    f.write(data)
    f.close()


def _handle_missing_metrics(args, filename):
  """Handles cleanup for a metrics file that doesn't exist.

  This will delete any output files under the tool's output directory that
  would have been generated as a result of a metrics file from a previous
  build. This prevents stale analysis files from polluting the output dir.
  """
  if args.skip_metrics:
    # If skip_metrics is enabled, then don't write or delete any data.
    return
  output_filepath = _get_output_file(args.output_dir, filename)
  if os.path.exists(output_filepath):
    os.remove(output_filepath)


def process_timing_mode(args):
  metrics_files_dir = args.metrics_files_dir
  if not args.skip_metrics:
    os.makedirs(args.output_dir, exist_ok=True)
    print("Writing build analysis files to " + args.output_dir, file=sys.stderr)

  bp2build_file = os.path.join(metrics_files_dir, "bp2build_metrics.pb")
  if os.path.exists(bp2build_file):
    bp2build_metrics = Bp2BuildMetrics()
    _read_data(bp2build_file, bp2build_metrics)
    _print_metrics_event_times("bp2build", bp2build_metrics)
    _maybe_save_data(bp2build_metrics, "bp2build_metrics.pb", args)
  else:
    _handle_missing_metrics(args, "bp2build_metrics.pb")

  soong_build_file = os.path.join(metrics_files_dir, "soong_build_metrics.pb")
  if os.path.exists(soong_build_file):
    soong_build_metrics = SoongBuildMetrics()
    _read_data(soong_build_file, soong_build_metrics)
    _print_metrics_event_times("soong_build", soong_build_metrics)
    _maybe_save_data(soong_build_metrics, "soong_build_metrics.pb", args)
  else:
    _handle_missing_metrics(args, "soong_build_metrics.pb")

  soong_metrics_file = os.path.join(metrics_files_dir, "soong_metrics")
  if os.path.exists(soong_metrics_file):
    metrics_base = MetricsBase()
    _read_data(soong_metrics_file, metrics_base)
    _maybe_save_data(metrics_base, "soong_metrics", args)
  else:
    _handle_missing_metrics(args, "soong_metrics")

  bazel_metrics_file = os.path.join(metrics_files_dir, "bazel_metrics.pb")
  if os.path.exists(bazel_metrics_file):
    bazel_metrics = BazelMetrics()
    _read_data(bazel_metrics_file, bazel_metrics)
    _maybe_save_data(bazel_metrics, "bazel_metrics.pb", args)
  else:
    _handle_missing_metrics(args, "bazel_metrics.pb")


def process_build_files_mode(args):
  if args.skip_metrics:
    raise Exception("build_files mode incompatible with --skip-metrics")
  os.makedirs(args.output_dir, exist_ok=True)
  tar_out = os.path.join(args.output_dir, "build_files.tar.gz")

  os.chdir(args.metrics_files_dir)

  if os.path.exists(tar_out):
    os.remove(tar_out)
  print("adding build files to", tar_out, "...", file=sys.stderr)

  with tarfile.open(tar_out, "w:gz", dereference=True) as tar:
    for root, dirs, files in os.walk("."):
      for file in files:
        if (
            file.endswith(".bzl")
            or file.endswith("BUILD")
            or file.endswith("BUILD.bazel")
        ):
          tar.add(os.path.join(root, file), arcname=os.path.join(root, file))


def process_bp2build_mode(args):
  metrics_files_dir = args.metrics_files_dir
  if not args.skip_metrics:
    os.makedirs(args.output_dir, exist_ok=True)
    print("Writing build analysis files to " + args.output_dir, file=sys.stderr)

  bp2build_file = os.path.join(metrics_files_dir, "bp2build_metrics.pb")
  if not os.path.exists(bp2build_file):
    raise Exception("bp2build mode requires that the last build ran bp2build")

  bp2build_metrics = Bp2BuildMetrics()
  _read_data(bp2build_file, bp2build_metrics)
  _maybe_save_data(bp2build_metrics, "bp2build_metrics.pb", args)
  converted_modules = {}
  for module in bp2build_metrics.convertedModules:
    converted_modules[module] = True

  if len(args.module_names) > 0:
    modules_to_report = args.module_names
  else:
    all_modules = {}
    for m in converted_modules:
      all_modules[m] = True
    for m in bp2build_metrics.unconvertedModules:
      all_modules[m] = True
    modules_to_report = sorted(all_modules)

  for name in modules_to_report:
    if name in converted_modules:
      print(name, "converted successfully.")
    elif name in bp2build_metrics.unconvertedModules:
      unconverted_summary = name + " not converted: "
      t = bp2build_metrics.unconvertedModules[name].type
      if t > -1 and t < len(UnconvertedReasonType.keys()):
        unconverted_summary += UnconvertedReasonType.keys()[t]
      else:
        unconverted_summary += "UNKNOWN_TYPE"
      if len(bp2build_metrics.unconvertedModules[name].detail) > 0:
        unconverted_summary += (
            " detail: " + bp2build_metrics.unconvertedModules[name].detail
        )
      print(unconverted_summary)
    else:
      print(name, "does not exist.")


def _define_global_flags(parser, suppress_default=False):
  """Adds global flags to the given parser object.

  Global flags should be added to both the global args parser and subcommand
  parsers. This allows global flags to be specified before or after the
  subcommand.

  Subcommand parser binding should pass suppress_default=True. This uses the
  default value specified in the global parser.
  """
  parser.add_argument(
      "--metrics_files_dir",
      default=(
          argparse.SUPPRESS if suppress_default else _get_default_metrics_dir()
      ),
      help="The directory contained metrics files to analyze."
      + " Defaults to $OUT_DIR if set, $ANDROID_BUILD_TOP/out otherwise.",
  )
  parser.add_argument(
      "--skip-metrics",
      action="store_true",
      default=(argparse.SUPPRESS if suppress_default else None),
      help="If set, do not save the output of printproto commands.",
  )
  parser.add_argument(
      "--output_dir",
      default=(argparse.SUPPRESS if suppress_default else None),
      help="The directory to save analyzed proto output to. "
      + "If unspecified, will default to the directory specified with"
      " --metrics_files_dir + '/analyze_build_output/'",
  )


def main():
  # Parse args
  parser = argparse.ArgumentParser(
      description=(
          "Analyzes build artifacts from the user's most recent build. Prints"
          " and/or saves data in a user-friendly format. See"
          " subcommand-specific help for analysis options."
      ),
      prog="analyze_build",
  )
  _define_global_flags(parser)
  subparsers = parser.add_subparsers(
      title="subcommands",
      help='types of analysis to run, "timing" by default.',
      dest="mode",
  )
  timing_parser = subparsers.add_parser(
      "timing", help="print per-phase build timing information"
  )
  _define_global_flags(timing_parser, True)
  build_files_parser = subparsers.add_parser(
      "build_files",
      help="create a tar containing all bazel-related build files",
  )
  _define_global_flags(build_files_parser, True)
  bp2build_parser = subparsers.add_parser(
      "bp2build",
      help="print whether a module was generated by bp2build",
  )
  _define_global_flags(bp2build_parser, True)
  bp2build_parser.add_argument(
      "module_names",
      metavar="module_name",
      nargs="*",
      help="print conversion info about these modules",
  )

  args = parser.parse_args()

  # Use `timing` as the default build mode.
  if not args.mode:
    args.mode = "timing"
  # Check the metrics dir.
  if not os.path.exists(args.metrics_files_dir):
    raise Exception(
        "Directory "
        + arg.metrics_files_dir
        + " not found. Did you run a build?"
    )

  args.output_dir = args.output_dir or _get_default_out_dir(
      args.metrics_files_dir
  )

  if args.mode == "timing":
    process_timing_mode(args)
  elif args.mode == "build_files":
    process_build_files_mode(args)
  elif args.mode == "bp2build":
    process_bp2build_mode(args)


if __name__ == "__main__":
  main()
