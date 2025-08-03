#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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

"""
Dump new HALs that are introduced in each FCM version in a human-readable format.

Example:
hals_for_release.py
  Show changes for each release, including new and deprecated HALs.
hals_for_release.py -dua
  Show changes as well as unchanged HALs for each release.
hals_for_release.py -i
  Show details about instance names and regex patterns as well.
hals_for_release.py -p wifi
  Show changes of Wi-Fi HALs for each release.
"""

import argparse
import collections
import enum
import json
import logging
import os
import subprocess
from collections.abc import Sequence
from typing import Any
from typing import Optional

import sys

logging.basicConfig(format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)


def ParseArgs() -> argparse.Namespace:
  """
  Parse arguments.
  :return: arguments.
  """
  parser = argparse.ArgumentParser(description=__doc__,
                                   formatter_class=argparse.RawTextHelpFormatter)
  parser.add_argument("--analyze-matrix", help="Location of analyze_matrix")
  parser.add_argument("input", metavar="INPUT", nargs="?",
                      help="Directory of compatibility matrices.")
  parser.add_argument("--deprecated", "-d",
                      help="Show deprecated HALs. If none of deprecated, unchanged or introduced "
                           "is specified, default is --deprecated and --introduced",
                      action="store_true")
  parser.add_argument("--unchanged", "-u",
                      help="Show unchanged HALs. If none of deprecated, unchanged or introduced "
                           "is specified, default is --deprecated and --introduced",
                      action="store_true")
  parser.add_argument("--introduced", "-a",
                      help="Show deprecated HALs. If none of deprecated, unchanged or introduced "
                           "is specified, default is --deprecated and --introduced",
                      action="store_true")
  parser.add_argument("--instances", "-i", action="store_true",
                      help="Show instance names and regex patterns as well")
  parser.add_argument("--packages", "-p", nargs="*", metavar="PACKAGE",
                      help="Only print HALs where package contains the given substring. "
                           "E.g. wifi, usb, health. Recommend to use with --unchanged.")
  parser.add_argument("--verbose", "-v", action="store_true", help="Verbose mode")
  parser.add_argument("--json", "-j", action="store_true", help="Print JSON")
  parser.add_argument("--package-only", action="store_true", help="Analyze on the package level.")
  args = parser.parse_args()

  if args.verbose:
    logger.setLevel(logging.DEBUG)

  if not args.deprecated and not args.unchanged and not args.introduced:
    args.deprecated = args.introduced = True

  host_out = os.environ.get("ANDROID_HOST_OUT")
  if host_out and not args.analyze_matrix:
    analyze_matrix = os.path.join(host_out, "bin", "analyze_matrix")
    if os.path.isfile(analyze_matrix):
      args.analyze_matrix = analyze_matrix
  if not args.analyze_matrix:
    args.analyze_matrix = "analyze_matrix"

  top = os.environ.get("ANDROID_BUILD_TOP")
  if top and not args.input:
    args.input = os.path.join(top, "hardware", "interfaces", "compatibility_matrices")
  if not args.input:
    logger.fatal("Unable to determine compatibility matrix dir, lunch or provide one explicitly.")
    return None

  logger.debug("Using analyze_matrix at path: %s", args.analyze_matrix)
  logger.debug("Dumping compatibility matrices at path: %s", args.input)
  logger.debug("Show deprecated HALs? %s", args.deprecated)
  logger.debug("Show unchanged HALs? %s", args.unchanged)
  logger.debug("Show introduced HALs? %s", args.introduced)
  logger.debug("Only showing packages %s", args.packages)

  return args


def Analyze(analyze_matrix: str, file: str, args: Sequence[str],
    ignore_errors=False) -> str:
  """
  Run analyze_matrix with
  :param analyze_matrix: path of analyze_matrix
  :param file: input file
  :param arg: argument to analyze_matrix, e.g. "level"
  :param ignore_errors: Whether errors during execution should be rased
  :return: output of analyze_matrix
  """
  command = [analyze_matrix, "--input", file] + args
  proc = subprocess.run(command,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.DEVNULL if ignore_errors else subprocess.PIPE)
  if not ignore_errors and proc.returncode != 0:
    logger.warning("`%s` exits with code %d with the following error: %s", " ".join(command),
                   proc.returncode, proc.stderr)
    proc.check_returncode()
  return proc.stdout.decode().strip()


def GetLevel(analyze_matrix: str, file: str) -> Optional[int]:
  """
  :param analyze_matrix: Path of analyze_matrix
  :param file: a file, possibly a compatibility matrix
  :return: If it is a compatibility matrix, return an integer indicating the level.
    If it is not a compatibility matrix, returns None.
    For matrices with empty level, return None.
  """
  output = Analyze(analyze_matrix, file, ["--level"], ignore_errors=True)
  # Ignore empty level matrices and non-matrices
  if not output:
    return None
  try:
    return int(output)
  except ValueError:
    logger.warning("Unknown level '%s' in file: %s", output, file)
    return None


def GetLevelName(analyze_matrix: str, file: str) -> str:
  """
  :param analyze_matrix: Path of analyze_matrix
  :param file: a file, possibly a compatibility matrix
  :return: If it is a compatibility matrix, return the level name.
    If it is not a compatibility matrix, returns None.
    For matrices with empty level, return "Level unspecified".
  """
  return Analyze(analyze_matrix, file, ["--level-name"], ignore_errors=True)


class MatrixData(object):
  def __init__(self, level: str, level_name: str, instances: Sequence[str]):
    self.level = level
    self.level_name = level_name
    self.instances = instances

  def GetInstancesKeyedOnPackage(self) -> dict[str, list[str]]:
    return KeyOnPackage(self.instances)


def ReadMatrices(args: argparse.Namespace) -> dict[int, MatrixData]:
  """
  :param args: parsed arguments from ParseArgs
  :return: A dictionary. Key is an integer indicating the matrix level.
  Value is (level name, a set of instances in that matrix).
  """
  matrices = dict()
  for child in os.listdir(args.input):
    file = os.path.join(args.input, child)
    level, level_name = GetLevel(args.analyze_matrix, file), GetLevelName(args.analyze_matrix, file)
    if level is None:
      logger.debug("Ignoring file %s", file)
      continue
    action = "--instances" if args.instances else "--interfaces"
    instances = Analyze(args.analyze_matrix, file, [action, "--requirement"]).split("\n")
    instances = set(map(str.strip, instances)) - {""}
    if level in matrices:
      logger.warning("Found duplicated matrix for level %s, ignoring: %s", level, file)
      continue
    matrices[level] = MatrixData(level, level_name, instances)

  return matrices


class HalFormat(enum.Enum):
  HIDL = 0
  NATIVE = 1
  AIDL = 2


def GetHalFormat(instance: str) -> HalFormat:
  """
  Guess the HAL format of instance.
  :param instance: two formats:
    android.hardware.health.storage@1.0::IStorage/default optional
    android.hardware.health.storage.IStorage/default (@1) optional
    storage@5.0 optional
  :return: HalFormat.HIDL for the first one, HalFormat.AIDL for the second,
    HalFormat.NATIVE for the third.

  >>> str(GetHalFormat("android.hardware.health.storage@1.0::IStorage/default optional"))
  'HalFormat.HIDL'
  >>> str(GetHalFormat("android.hardware.health.storage.IStorage/default (@1) optional"))
  'HalFormat.AIDL'
  >>> str(GetHalFormat("storage@5.0 optional"))
  'HalFormat.NATIVE'
  """
  if "::" in instance:
    return HalFormat.HIDL
  elif "(@" in instance:
    return HalFormat.AIDL
  else:
    return HalFormat.NATIVE


def SplitInstance(instance: str) -> tuple[str, str, str]:
  """
  Split instance into parts.
  :param instance:
  :param instance: two formats:
    android.hardware.health.storage@1.0::IStorage/default optional
    android.hardware.health.storage.IStorage/default (@1) optional
    storage@5.0 optional
  :return: (package, version+interface+instance, requirement)

  >>> SplitInstance("android.hardware.health.storage@1.0::IStorage/default optional")
  ('android.hardware.health.storage', '@1.0::IStorage/default', 'optional')
  >>> SplitInstance("android.hardware.health.storage.IStorage/default (@1) optional")
  ('android.hardware.health.storage', 'IStorage/default (@1)', 'optional')
  >>> SplitInstance("storage@5.0 optional")
  ('storage', 'storage@5.0', 'optional')
  """
  format = GetHalFormat(instance)
  if format == HalFormat.HIDL:
    atPos = instance.find("@")
    spacePos = instance.rfind(" ")
    return instance[:atPos], instance[atPos:spacePos], instance[spacePos + 1:]
  elif format == HalFormat.AIDL:
    dotPos = instance.rfind(".")
    spacePos = instance.rfind(" ")
    return instance[:dotPos], instance[dotPos + 1:spacePos], instance[spacePos + 1:]
  elif format == HalFormat.NATIVE:
    atPos = instance.find("@")
    spacePos = instance.rfind(" ")
    return instance[:atPos], instance[:spacePos], instance[spacePos + 1:]


def GetPackage(instance: str) -> str:
  """
  Guess the package of instance.
  :param instance: two formats:
    android.hardware.health.storage@1.0::IStorage/default
    android.hardware.health.storage.IStorage/default (@1)
  :return: The package. In the above example, return android.hardware.health.storage

  >>> GetPackage("android.hardware.health.storage@1.0::IStorage/default")
  'android.hardware.health.storage'
  >>> GetPackage("android.hardware.health.storage.IStorage/default (@1)")
  'android.hardware.health.storage'
  """
  return SplitInstance(instance)[0]


def GetPackageAndHidlVersion(instance: str) -> str:
  """
  Guess the package and version of instance.
  :param instance: two formats:
    android.hardware.health.storage@1.0::IStorage/default
    android.hardware.health.storage.IStorage/default (@1)
    storage@5.0
  :return: The package and HIDL version. In the above example, return
    android.hardware.health.storage@1.0 for HIDL, storage@5.0 for NATIVE,
    and android.hardware.health.storage for AIDL.

  >>> GetPackageAndHidlVersion("android.hardware.health.storage@1.0::IStorage/default")
  'android.hardware.health.storage@1.0'
  >>> GetPackageAndHidlVersion("android.hardware.health.storage.IStorage/default (@1)")
  'android.hardware.health.storage'
  >>> GetPackageAndHidlVersion("storage@5.0")
  'storage@5.0'
  """
  format = GetHalFormat(instance)
  if format == HalFormat.HIDL:
    colonPos = instance.find("::")
    return instance[:colonPos]
  elif format == HalFormat.AIDL:
    dotPos = instance.rfind(".")
    return instance[:dotPos]
  elif format == HalFormat.NATIVE:
    return instance



def KeyOnPackage(instances: Sequence[str]) -> dict[str, list[str]]:
  """
  :param instances: A list of instances.
  :return: A dictionary, where key is the package (see GetPackage), and
    value is a list of instances in the provided list, where
    GetPackage(instance) is the corresponding key.
  """
  d = collections.defaultdict(list)
  for instance in instances:
    package = GetPackage(instance)
    d[package].append(instance)
  return d


class Report(object):
  """
  Base class for generating a report.
  """
  def __init__(self, matrixData1: MatrixData, matrixData2: MatrixData, args: argparse.Namespace):
    """
    Initialize the report with two matrices.
    :param matrixData1: Data of the old matrix
    :param matrixData2: Data of the new matrix
    :param args: command-line arguments
    """
    self.args = args
    self.matrixData1 = matrixData1
    self.matrixData2 = matrixData2
    self.instances_by_package1 = matrixData1.GetInstancesKeyedOnPackage()
    self.instances_by_package2 = matrixData2.GetInstancesKeyedOnPackage()
    self.all_packages = set(self.instances_by_package1.keys()) | set(
      self.instances_by_package2.keys())

  def GetReport(self) -> Any:
    """
    Generate the report
    :return: An object representing the report. Type is implementation defined.
    """
    packages_report: dict[str, Any] = dict()
    for package in self.all_packages:
      package_instances1 = set(self.instances_by_package1.get(package, []))
      package_instances2 = set(self.instances_by_package2.get(package, []))

      if self.args.package_only:
        package_instances1 = set(GetPackageAndHidlVersion(inst) for inst in package_instances1)
        package_instances2 = set(GetPackageAndHidlVersion(inst) for inst in package_instances2)

      deprecated = sorted(package_instances1 - package_instances2)
      unchanged = sorted(package_instances1 & package_instances2)
      introduced = sorted(package_instances2 - package_instances1)
      package_report = self.DescribePackage(deprecated=deprecated,
                                            unchanged=unchanged,
                                            introduced=introduced)
      if package_report:
        packages_report[package] = package_report
    return self.CombineReport(packages_report)

  def DescribePackage(self, deprecated: Sequence[str], unchanged: Sequence[str],
      introduced: Sequence[str]) -> Any:
    """
    Describe a package in a implementation-defined format, with the given
    set of changes.
    :param deprecated: set of deprecated HALs
    :param unchanged:  set of unchanged HALs
    :param introduced: set of new HALs
    :return: An object that will later be passed into the values of the
      packages_report argument of CombineReport
    """
    raise NotImplementedError

  def CombineReport(self, packages_report: dict[str, Any]) -> Any:
    """
    Combine a set of reports for a package in an implementation-defined way.
    :param packages_report: A dictionary, where key is the package
      name, and value is the object generated by DescribePackage
    :return: the report object
    """
    raise NotImplementedError


class HumanReadableReport(Report):
  def DescribePackage(self, deprecated: Sequence[str], unchanged: Sequence[str],
      introduced: Sequence[str]) -> Any:
    package_report = []
    desc = lambda fmt, instance: fmt.format(GetHalFormat(instance).name,
                                            *SplitInstance(instance))
    if self.args.deprecated:
      package_report += [desc("- {0} {2} can no longer be used", instance)
                         for instance in deprecated]
    if self.args.unchanged:
      package_report += [desc("  {0} {2}", instance) for instance in
                         unchanged]
    if self.args.introduced:
      package_report += [desc("+ {0} {2}", instance) for instance in
                         introduced]

    return package_report

  def CombineReport(self, packages_report: dict[str, Any]) -> str:
    report = ["============",
              "Level %s (%s) (against Level %s (%s))" % (
              self.matrixData2.level, self.matrixData2.level_name,
              self.matrixData1.level, self.matrixData1.level_name),
              "============"]
    for package, lines in sorted(packages_report.items()):
      report.append(package)
      report += [("    " + e) for e in lines]

    return "\n".join(report)


class JsonReport(Report):
  def DescribePackage(self, deprecated: Sequence[str], unchanged: Sequence[str],
      introduced: Sequence[str]) -> Any:
    package_report = collections.defaultdict(list)
    if self.args.deprecated and deprecated:
      package_report["deprecated"] += deprecated
    if self.args.unchanged and unchanged:
      package_report["unchanged"] += unchanged
    if self.args.introduced and introduced:
      package_report["introduced"] += introduced

    return package_report

  def CombineReport(self, packages_report: dict[str, Any]) -> dict[str, Any]:
    final = collections.defaultdict(list)
    for package_report in packages_report.values():
      for key, lst in package_report.items():
        final[key] += lst
    for key in final:
      final[key] = sorted(final[key])
    final["__meta__"] = {
        "old": {"level": self.matrixData1.level,
                "level_name": self.matrixData1.level_name},
        "new": {"level": self.matrixData2.level,
                "level_name": self.matrixData2.level_name},
    }
    return final


def PrintReport(matrices: dict[int, MatrixData], args: argparse.Namespace):
  """
  :param matrixData1: data of first matrix
  :param matrixData2: data of second matrix
  :return: A report of their difference.
  """
  sorted_matrices = sorted(matrices.items())
  if not sorted_matrices:
    logger.warning("Nothing to show, because no matrices found in '%s'.", args.input)

  if args.json:
    reports = []
    for (level1, matrixData1), (level2, matrixData2) in zip(sorted_matrices, sorted_matrices[1:]):
      reports.append(JsonReport(matrixData1, matrixData2, args).GetReport())
    print(json.dumps(reports))
    return

  for (level1, matrixData1), (level2, matrixData2) in zip(sorted_matrices, sorted_matrices[1:]):
    report = HumanReadableReport(matrixData1, matrixData2, args)
    print(report.GetReport())


def main():
  sys.stderr.write("Generated with %s\n" % " ".join(sys.argv))
  args = ParseArgs()
  if args is None:
    return 1
  matrices = ReadMatrices(args)
  PrintReport(matrices, args)
  return 0


if __name__ == "__main__":
  sys.exit(main())
