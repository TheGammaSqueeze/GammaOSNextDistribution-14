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

import argparse
import collections
import dataclasses
import datetime
import functools
import os.path
import subprocess
import sys
from typing import DefaultDict, Dict, FrozenSet, List, Optional, Set, Tuple
import xml
from bp2build_metrics_proto.bp2build_metrics_pb2 import Bp2BuildMetrics, UnconvertedReasonType
import bp2build_pb2
import dependency_analysis


@dataclasses.dataclass(frozen=True, order=True)
class GraphFilterInfo:
  module_names: Set[str] = dataclasses.field(default_factory=set)
  module_types: Set[str] = dataclasses.field(default_factory=set)
  package_dir: str = dataclasses.field(default_factory=str)
  recursive: bool = dataclasses.field(default_factory=bool)


@dataclasses.dataclass(frozen=True, order=True)
class ModuleInfo:
  name: str
  kind: str
  dirname: str
  created_by: Optional[str]
  reasons_from_heuristics: FrozenSet[str] = frozenset()
  reason_from_metric: str = ""
  props: FrozenSet[str] = frozenset()
  num_deps: int = 0
  converted: bool = False

  def __str__(self):
    converted = " (converted)" if self.converted else ""
    return f"{self.name} [{self.kind}] [{self.dirname}]{converted}"

  def short_string(self, converted: Set[str]):
    converted = " (c)" if self.is_converted(converted) else ""
    return f"{self.name} [{self.kind}]{converted}"

  def get_reasons_from_heuristics(self):
    if len(self.reasons_from_heuristics) == 0:
      return ""
    return (
        "unconverted reasons from heuristics: {reasons_from_heuristics}"
        .format(reasons_from_heuristics=", ".join(self.reasons_from_heuristics))
    )

  def get_reason_from_metric(self):
    if len(self.reason_from_metric) == 0:
      return ""
    return "unconverted reason from metric: {reason_from_metric}".format(
        reason_from_metric=self.reason_from_metric
    )

  def is_converted(self, converted: Dict[str, Set[str]]):
    return self.name in converted and self.kind in converted[self.name]

  def is_skipped(self):
    # these are implementation details of another module type that can never be
    # created in a BUILD file
    return ".go_android/soong" in self.kind and (
        self.kind.endswith("__loadHookModule")
        or self.kind.endswith("__topDownMutatorModule")
    )

  def is_converted_or_skipped(self, converted: Dict[str, Set[str]]):
    return self.is_converted(converted) or self.is_skipped()


@dataclasses.dataclass(frozen=True, order=True)
class DepInfo:
  direct_deps: Set[ModuleInfo] = dataclasses.field(default_factory=set)
  transitive_deps: Set[ModuleInfo] = dataclasses.field(default_factory=set)

  def all_deps(self):
    return set.union(self.direct_deps, self.transitive_deps)


@dataclasses.dataclass(frozen=True, order=True)
class InputModule:
  module: ModuleInfo
  num_deps: int = 0
  num_unconverted_deps: int = 0

  def __str__(self):
    total = self.num_deps
    converted = self.num_deps - self.num_unconverted_deps
    percent = 100
    if self.num_deps > 0:
      percent = converted / self.num_deps * 100
    return f"{self.module.name}: {percent:.1f}% ({converted}/{total}) converted"


@dataclasses.dataclass(frozen=True)
class ReportData:
  total_deps: Set[ModuleInfo]
  unconverted_deps: Set[str]
  all_unconverted_modules: Dict[str, Set[ModuleInfo]]
  blocked_modules: Dict[ModuleInfo, Set[str]]
  blocked_modules_transitive: Dict[ModuleInfo, Set[str]]
  dirs_with_unconverted_modules: Set[str]
  kind_of_unconverted_modules: Set[str]
  converted: Dict[str, Set[str]]
  show_converted: bool
  hide_unconverted_modules_reasons: bool
  package_dir: str
  input_modules: Set[InputModule] = dataclasses.field(default_factory=set)
  input_types: Set[str] = dataclasses.field(default_factory=set)


# Generate a dot file containing the transitive closure of the module.
def generate_dot_file(
    modules: Dict[ModuleInfo, DepInfo],
    converted: Dict[str, Set[str]],
    show_converted: bool,
):
  # Check that all modules in the argument are in the list of converted modules
  all_converted = lambda modules: all(
      m.is_converted(converted) for m in modules
  )

  dot_entries = []

  for module, dep_info in sorted(modules.items()):
    deps = dep_info.direct_deps
    if module.is_converted(converted):
      if show_converted:
        color = "dodgerblue"
      else:
        continue
    elif all_converted(deps):
      color = "yellow"
    else:
      color = "tomato"

    dot_entries.append(
        f'"{module.name}" [label="{module.name}\\n{module.kind}" color=black,'
        f" style=filled, fillcolor={color}]"
    )
    dot_entries.extend(
        f'"{module.name}" -> "{dep.name}"'
        for dep in sorted(deps)
        if show_converted or not dep.is_converted(converted)
    )

  return """
digraph mygraph {{
  node [shape=box];

  %s
}}
""" % "\n  ".join(dot_entries)


def get_transitive_unconverted_deps(
    cache: Dict[DepInfo, Set[DepInfo]],
    module: ModuleInfo,
    modules: Dict[ModuleInfo, DepInfo],
    converted: Dict[str, Set[str]],
) -> Set[str]:
  if module in cache:
    return cache[module]
  unconverted_deps = set()
  dep = modules[module]
  for d in dep.direct_deps:
    if d.is_converted_or_skipped(converted):
      continue
    unconverted_deps.add(d)
    transitive = get_transitive_unconverted_deps(cache, d, modules, converted)
    unconverted_deps = unconverted_deps.union(transitive)
  cache[module] = unconverted_deps
  return unconverted_deps


# Filter modules based on the module and graph_filter
def module_matches_filter(module, graph_filter):
  dirname = module.dirname + "/"
  if graph_filter.package_dir is not None:
    if graph_filter.recursive:
      return dirname.startswith(graph_filter.package_dir)
    return dirname == graph_filter.package_dir
  return (
      module.name in graph_filter.module_names
      or module.kind in graph_filter.module_types
  )


def unconverted_reasons_from_heuristics(
    module, unconverted_transitive_deps, props_by_converted_module_type
):
  """Heuristics for determining the reason for unconverted module"""
  reasons = []
  if module.converted:
    raise RuntimeError(
        "Heuristics should not be run on converted module %s" % module.name
    )
  if module.kind in props_by_converted_module_type:
    props_diff = module.props.difference(
        props_by_converted_module_type[module.kind]
    )
    if len(props_diff) != 0:
      reasons.append(
          "unconverted properties: [%s]" % ", ".join(sorted(props_diff))
      )
  else:
    reasons.append("type missing converter")
  if len(unconverted_transitive_deps) > 0:
    reasons.append("unconverted dependencies")
  return frozenset(reasons)


# Generate a report for each module in the transitive closure, and the blockers for each module
def generate_report_data(
    modules: Dict[ModuleInfo, DepInfo],
    converted: Set[str],
    graph_filter: GraphFilterInfo,
    props_by_converted_module_type: DefaultDict[str, Set[str]],
    use_queryview: bool,
    bp2build_metrics: Bp2BuildMetrics,
    hide_unconverted_modules_reasons: bool = False,
    show_converted: bool = False,
) -> ReportData:
  # Map of [number of unconverted deps] to list of entries,
  # with each entry being the string: "<module>: <comma separated list of unconverted modules>"
  blocked_modules = collections.defaultdict(set)
  blocked_modules_transitive = collections.defaultdict(set)

  # Map of unconverted modules to the modules they're blocking
  # (i.e. reverse deps)
  all_unconverted_modules = collections.defaultdict(set)

  dirs_with_unconverted_modules = set()
  kind_of_unconverted_modules = collections.defaultdict(int)

  input_all_deps = set()
  input_unconverted_deps = set()
  input_modules = set()

  transitive_deps_by_dep_info = {}

  for module, dep_info in sorted(modules.items()):
    deps = dep_info.direct_deps
    unconverted_deps = set(
        dep for dep in deps if not dep.is_converted_or_skipped(converted)
    )

    unconverted_transitive_deps = get_transitive_unconverted_deps(
        transitive_deps_by_dep_info, module, modules, converted
    )

    # ModuleInfo.reason_from_metric will be an empty string if the module is converted or --use-queryview flag is passed
    unconverted_module_reason_from_metrics = ""
    if (
        module.name in bp2build_metrics.unconvertedModules
        and not use_queryview
        and not hide_unconverted_modules_reasons
    ):
      # TODO(b/291642059): Concatenate the value of UnconvertedReason.detail field with unconverted_module_reason_from_metrics.
      unconverted_module_reason_from_metrics = UnconvertedReasonType.Name(
          bp2build_metrics.unconvertedModules[module.name].type
      )

    unconverted_module_reasons_from_heuristics = (
        unconverted_reasons_from_heuristics(
            module, unconverted_transitive_deps, props_by_converted_module_type
        )
        if not (
            module.is_skipped()
            or module.is_converted(converted)
            or use_queryview
            or hide_unconverted_modules_reasons
        )
        else frozenset()
    )

    # replace deps count with transitive deps rather than direct deps count
    module = ModuleInfo(
        module.name,
        module.kind,
        module.dirname,
        module.created_by,
        unconverted_module_reasons_from_heuristics,
        unconverted_module_reason_from_metrics,
        module.props,
        len(dep_info.all_deps()),
        module.is_converted(converted),
    )

    for dep in unconverted_transitive_deps:
      all_unconverted_modules[dep].add(module)

    if not module.is_skipped() and (
        not module.is_converted(converted) or show_converted
    ):
      if show_converted:
        full_deps = set(dep for dep in deps)
        blocked_modules[module].update(full_deps)
        full_deps = set(dep for dep in dep_info.all_deps())
        blocked_modules_transitive[module].update(full_deps)
      else:
        blocked_modules[module].update(unconverted_deps)
        blocked_modules_transitive[module].update(unconverted_transitive_deps)

    if not module.is_converted_or_skipped(converted):
      dirs_with_unconverted_modules.add(module.dirname)
      kind_of_unconverted_modules[module.kind] += 1

    if module_matches_filter(module, graph_filter):
      transitive_deps = dep_info.all_deps()
      input_modules.add(
          InputModule(
              module, len(transitive_deps), len(unconverted_transitive_deps)
          )
      )
      input_all_deps.update(transitive_deps)
      input_unconverted_deps.update(unconverted_transitive_deps)

  kinds = set(
      f"{k}: {kind_of_unconverted_modules[k]}"
      for k in kind_of_unconverted_modules.keys()
  )

  return ReportData(
      input_modules=input_modules,
      input_types=graph_filter.module_types,
      total_deps=input_all_deps,
      unconverted_deps=input_unconverted_deps,
      all_unconverted_modules=all_unconverted_modules,
      blocked_modules=blocked_modules,
      blocked_modules_transitive=blocked_modules_transitive,
      dirs_with_unconverted_modules=dirs_with_unconverted_modules,
      kind_of_unconverted_modules=kinds,
      converted=converted,
      show_converted=show_converted,
      hide_unconverted_modules_reasons=hide_unconverted_modules_reasons,
      package_dir=graph_filter.package_dir,
  )


def generate_proto(report_data):
  message = bp2build_pb2.Bp2buildConversionProgress(
      root_modules=[m.module.name for m in report_data.input_modules],
      num_deps=len(report_data.total_deps),
  )
  for (
      module,
      unconverted_deps,
  ) in report_data.blocked_modules_transitive.items():
    message.unconverted.add(
        name=module.name,
        directory=module.dirname,
        type=module.kind,
        unconverted_deps={d.name for d in unconverted_deps},
        num_deps=module.num_deps,
        # when the module is converted or queryview is being used, an empty list will be assigned
        unconverted_reasons_from_heuristics=list(
            module.reasons_from_heuristics
        ),
    )
  return message


def generate_report(report_data):
  report_lines = []
  if len(report_data.input_types) > 0:
    input_module_str = ", ".join(
        str(i) for i in sorted(report_data.input_types)
    )
  else:
    input_module_str = ", ".join(
        str(i) for i in sorted(report_data.input_modules)
    )

  report_lines.append("# bp2build progress report for: %s\n" % input_module_str)

  if report_data.show_converted:
    report_lines.append(
        "# progress report includes data both for converted and unconverted"
        " modules"
    )

  total = len(report_data.total_deps)
  unconverted = len(report_data.unconverted_deps)
  converted = total - unconverted
  if total > 0:
    percent = converted / total * 100
  else:
    percent = 100
  report_lines.append(f"Percent converted: {percent:.2f} ({converted}/{total})")
  report_lines.append(f"Total unique unconverted dependencies: {unconverted}")

  report_lines.append(
      "Ignored module types: %s\n" % sorted(dependency_analysis.IGNORED_KINDS)
  )
  report_lines.append("# Transitive dependency closure:")

  current_count = -1
  for module, unconverted_transitive_deps in sorted(
      report_data.blocked_modules_transitive.items(), key=lambda x: len(x[1])
  ):
    count = len(unconverted_transitive_deps)
    if current_count != count:
      report_lines.append(f"\n{count} unconverted transitive deps remaining:")
      current_count = count
    unconverted_deps = report_data.blocked_modules.get(module, set())
    unconverted_deps = set(
        d.short_string(report_data.converted) for d in unconverted_deps
    )
    report_lines.append(f"{module}")
    if not report_data.hide_unconverted_modules_reasons:
      report_lines.append("\tunconverted due to:")
      reason_from_metric = module.get_reason_from_metric()
      reasons_from_heuristics = module.get_reasons_from_heuristics()
      if reason_from_metric != "":
        report_lines.append(f"\t\t{reason_from_metric}")
      if reasons_from_heuristics != "":
        report_lines.append(f"\t\t{reasons_from_heuristics}")
    if len(unconverted_deps) == 0:
      report_lines.append('\tdirect deps:')
    else:
      report_lines.append(
        "\tdirect deps: {deps}".format(deps=", ".join(sorted(unconverted_deps)))
      )

  report_lines.append("\n")
  report_lines.append("# Unconverted deps of {}:\n".format(input_module_str))
  for count, dep in sorted(
      (
          (len(unconverted), dep)
          for dep, unconverted in report_data.all_unconverted_modules.items()
      ),
      reverse=True,
  ):
    report_lines.append(
        "%s: blocking %d modules"
        % (dep.short_string(report_data.converted), count)
    )

  report_lines.append("\n")
  report_lines.append(
      "# Dirs with unconverted modules:\n\n{}".format(
          "\n".join(sorted(report_data.dirs_with_unconverted_modules))
      )
  )

  report_lines.append("\n")
  report_lines.append(
      "# Kinds with unconverted modules:\n\n{}".format(
          "\n".join(sorted(report_data.kind_of_unconverted_modules))
      )
  )

  report_lines.append("\n")
  if report_data.show_converted:
    report_lines.append(
      "# Converted modules:\n\n%s" % "\n".join(sorted(report_data.converted))
    )
  else:
    report_lines.append("# Converted modules not shown")

  report_lines.append("\n")
  report_lines.append(
      "Generated by:"
      " https://cs.android.com/android/platform/superproject/+/master:build/bazel/scripts/bp2build_progress/bp2build_progress.py"
  )
  report_lines.append(
      "Generated at: %s"
      % datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S %z")
  )

  return "\n".join(report_lines)


def adjacency_list_from_json(
    module_graph: ...,
    ignore_by_name: List[str],
    ignore_java_auto_deps: bool,
    graph_filter: GraphFilterInfo,
    collect_transitive_dependencies: bool = True,
) -> Dict[ModuleInfo, Set[ModuleInfo]]:
  def filtering(json):
    module = ModuleInfo(
        name=json["Name"],
        created_by=json["CreatedBy"],
        kind=json["Type"],
        dirname=os.path.dirname(json["Blueprint"]),
    )
    return module_matches_filter(module, graph_filter)

  module_adjacency_list = {}
  name_to_info = {}

  def collect_dependencies(module, deps_names):
    module_info = None
    name = module["Name"]
    props = dependency_analysis.get_properties(module)
    converted = (
        props.get("Bazel_module.Bp2build_available", "false") == "true"
        or props.get("Bazel_module.Label", "") != ""
    )
    name_to_info.setdefault(
        name,
        ModuleInfo(
            name=name,
            created_by=module["CreatedBy"],
            kind=module["Type"],
            props=frozenset(
                prop for prop in dependency_analysis.get_property_names(module)
            ),
            dirname=os.path.dirname(module["Blueprint"]),
            num_deps=len(deps_names),
            converted=converted,
        ),
    )

    module_info = name_to_info[name]

    # ensure module_info added to adjacency list even with no deps
    module_adjacency_list.setdefault(module_info, DepInfo())
    for dep in deps_names:
      # this may occur if there is a cycle between a module and created_by
      # module
      if not dep in name_to_info:
        continue
      dep_module_info = name_to_info[dep]
      module_adjacency_list[module_info].direct_deps.add(dep_module_info)
      if collect_transitive_dependencies:
        transitive_dep_info = module_adjacency_list.get(
            dep_module_info, DepInfo()
        )
        module_adjacency_list[module_info].transitive_deps.update(
            transitive_dep_info.all_deps()
        )

  dependency_analysis.visit_json_module_graph_post_order(
      module_graph,
      ignore_by_name,
      ignore_java_auto_deps,
      filtering,
      collect_dependencies,
  )

  return module_adjacency_list


def adjacency_list_from_queryview_xml(
    module_graph: xml.etree.ElementTree,
    graph_filter: GraphFilterInfo,
    ignore_by_name: List[str],
    collect_transitive_dependencies: bool = True,
) -> Dict[ModuleInfo, DepInfo]:
  def filtering(module):
    return (
        module.name in graph_filter.module_names
        or module.kind in graph_filter.module_types
    )

  module_adjacency_list = collections.defaultdict(set)
  name_to_info = {}

  def collect_dependencies(module, deps_names):
    module_info = None
    name_to_info.setdefault(
        module.name,
        ModuleInfo(
            name=module.name,
            kind=module.kind,
            dirname=module.dirname,
            # required so that it cannot be forgotten when updating num_deps
            created_by=None,
            num_deps=len(deps_names),
        ),
    )
    module_info = name_to_info[module.name]

    # ensure module_info added to adjacency list even with no deps
    module_adjacency_list.setdefault(module_info, DepInfo())
    for dep in deps_names:
      dep_module_info = name_to_info[dep]
      module_adjacency_list[module_info].direct_deps.add(dep_module_info)
      if collect_transitive_dependencies:
        transitive_dep_info = module_adjacency_list.get(
            dep_module_info, DepInfo()
        )
        module_adjacency_list[module_info].transitive_deps.update(
            transitive_dep_info.all_deps()
        )

  dependency_analysis.visit_queryview_xml_module_graph_post_order(
      module_graph, ignore_by_name, filtering, collect_dependencies
  )

  return module_adjacency_list


# this function gets map of converted module types to set of properties for heuristics
def get_props_by_converted_module_type(module_graph, converted, ignore_by_name):
  props_by_converted_module_type = collections.defaultdict(set)

  def collect_module_props(module):
    props = set(prop for prop in dependency_analysis.get_property_names(module))
    if module["Type"] not in props_by_converted_module_type:
      props_by_converted_module_type[module["Type"]] = props
    else:
      props_by_converted_module_type[module["Type"]].update(props)

  for module in module_graph:
    if module[
        "Name"
    ] not in converted or dependency_analysis.ignore_json_module(
        module, ignore_by_name
    ):
      continue
    collect_module_props(module)

  return props_by_converted_module_type


def get_module_adjacency_list_and_props_by_converted_module_type(
    graph_filter: GraphFilterInfo,
    use_queryview: bool,
    ignore_by_name: List[str],
    converted: Set[str],
    target_product: dependency_analysis.TargetProduct,
    ignore_java_auto_deps: bool = False,
    collect_transitive_dependencies: bool = True,
) -> Tuple[Dict[ModuleInfo, DepInfo], DefaultDict[str, Set[str]]]:
  # The main module graph containing _all_ modules in the Soong build,
  # and the list of converted modules.

  # Map of converted modules types to the set of properties.
  # This is only used in heuristics implementation.
  props_by_converted_module_type = collections.defaultdict(set)

  try:
    if use_queryview:
      if len(graph_filter.module_names) > 0:
        module_graph = dependency_analysis.get_queryview_module_info(
            graph_filter.module_names, target_product
        )
      else:
        module_graph = dependency_analysis.get_queryview_module_info_by_type(
            graph_filter.module_types, target_product
        )

      module_adjacency_list = adjacency_list_from_queryview_xml(
          module_graph,
          graph_filter,
          ignore_by_name,
          collect_transitive_dependencies,
      )
    else:
      module_graph = dependency_analysis.get_json_module_info(target_product)
      module_adjacency_list = adjacency_list_from_json(
          module_graph,
          ignore_by_name,
          ignore_java_auto_deps,
          graph_filter,
          collect_transitive_dependencies,
      )
      props_by_converted_module_type = get_props_by_converted_module_type(
          module_graph, converted, ignore_by_name
      )
  except subprocess.CalledProcessError as err:
    sys.exit(f"""Error running: '{' '.join(err.cmd)}':"
Stdout:
{err.stdout.decode('utf-8') if err.stdout else ''}
Stderr:
{err.stderr.decode('utf-8') if err.stderr else ''}""")

  return module_adjacency_list, props_by_converted_module_type


def add_manual_conversion_to_converted(
    converted: Dict[str, Set[str]], module_adjacency_list: Dict[ModuleInfo, DepInfo]
) -> Set[str]:
  modules_by_name = {m.name: m for m in module_adjacency_list.keys()}

  converted_modules = collections.defaultdict(set)
  converted_modules.update(converted)

  def _update_converted(module_name):
    if module_name in converted_modules:
      return True
    if module_name not in modules_by_name:
      return False
    module = modules_by_name[module_name]
    if module.converted:
      converted_modules[module_name].add(module.kind)
      return True
    return False

  for module in modules_by_name.keys():
    _update_converted(module)

  return converted_modules


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument("mode", help="mode: graph or report")
  parser.add_argument(
      "--product",
      help="Product to collect module graph for. (Optional)",
      required=False,
      default="aosp_cf_arm64_phone",
  )
  parser.add_argument(
      "--module",
      "-m",
      action="append",
      help=(
          "name(s) of Soong module(s). Multiple modules only supported for"
          " report"
      ),
  )
  parser.add_argument(
      "--type",
      "-t",
      action="append",
      help=(
          "type(s) of Soong module(s). Multiple modules only supported for"
          " report"
      ),
  )
  parser.add_argument(
      "--package-dir",
      "-p",
      action="store",
      help=(
          "package directory for Soong modules. Single package directory only"
          " supported for report."
      ),
  )
  parser.add_argument(
      "--recursive",
      "-r",
      action="store_true",
      help=(
          "whether to perform recursive search when --package-dir flag is"
          " passed."
      ),
  )
  parser.add_argument(
      "--use-queryview",
      action="store_true",
      help="whether to use queryview or module_info",
  )
  parser.add_argument(
      "--ignore-by-name",
      default="",
      help=(
          "Comma-separated list. When building the tree of transitive"
          " dependencies, will not follow dependency edges pointing to module"
          " names listed by this flag."
      ),
  )
  parser.add_argument(
      "--ignore-java-auto-deps",
      action="store_true",
      default=True,
      help="whether to ignore automatically added java deps",
  )
  parser.add_argument(
      "--banchan",
      action="store_true",
      help="whether to run Soong in a banchan configuration rather than lunch",
  )
  # TODO(b/283512659): Fix the relative path bug and update the README file
  parser.add_argument(
      "--proto-file",
      help="Path to write proto output",
  )
  # TODO(b/283512659): Fix the relative path bug and update the README file
  parser.add_argument(
      "--out-file",
      "-o",
      type=argparse.FileType("w"),
      default="-",
      help="Path to write output, if omitted, writes to stdout",
  )
  parser.add_argument(
      "--show-converted",
      "-s",
      action="store_true",
      help=(
          "Show bp2build-converted modules in addition to the unconverted"
          " dependencies to see full dependencies post-migration. By default"
          " converted dependencies are not shown"
      ),
  )
  parser.add_argument(
      # This flag is only relevant when used by the CI script. Don't use it when running b command independently.
      "--bp2build-metrics-location",
      default=os.path.join(dependency_analysis.SRC_ROOT_DIR, "out"),
      help=(
          "Path to get bp2build_metrics, if omitted, gets bp2build_metrics from"
          " the SRC_ROOT_DIR/out directory"
      ),
  )
  parser.add_argument(
      "--hide-unconverted-modules-reasons",
      action="store_true",
      help=(
          "Hide unconverted modules reasons of heuristics and"
          " bp2build_metrics.pb. By default unconverted modules reasons are"
          " shown"
      ),
  )
  args = parser.parse_args()

  if args.proto_file and args.mode == "graph":
    sys.exit(f"Proto file only supported for report mode, not {args.mode}")

  mode = args.mode
  use_queryview = args.use_queryview
  ignore_by_name = args.ignore_by_name.split(",")
  ignore_java_auto_deps = args.ignore_java_auto_deps
  target_product = dependency_analysis.TargetProduct(
      banchan_mode=args.banchan,
      product=args.product,
  )
  modules = set(args.module) if args.module is not None else set()
  types = set(args.type) if args.type is not None else set()
  recursive = args.recursive
  package_dir = (
      os.path.normpath(args.package_dir) + "/"
      if args.package_dir
      else args.package_dir
  )
  bp2build_metrics_location = args.bp2build_metrics_location
  graph_filter = GraphFilterInfo(modules, types, package_dir, recursive)

  if package_dir is None:
    if len(modules) == 0 and len(types) == 0:
      sys.exit("Must specify at least one module, type or package directory")
    if recursive:
      sys.exit("Cannot support --recursive with modules or types")
  if package_dir is not None:
    if args.use_queryview:
      sys.exit("Can only support the package directory with json module graph")
    if args.mode == "graph":
      sys.exit(f"Cannot support --package-dir with mode graph")
    if len(modules) > 0 or len(types) > 0:
      sys.exit("Can only support either modules, types or package directory")
  if len(modules) > 0 and len(types) > 0 and args.use_queryview:
    sys.exit("Can only support either of modules or types with --use-queryview")
  if len(modules) > 1 and args.mode == "graph":
    sys.exit(f"Can only support one module with mode graph")
  if len(types) and args.mode == "graph":
    sys.exit(f"Cannot support --type with mode graph")
  if args.hide_unconverted_modules_reasons:
    if args.use_queryview:
      sys.exit(
          "Cannot support --hide-unconverted-modules-reasons with"
          " --use-queryview"
      )
    if args.mode == "graph":
      sys.exit(
          f"Cannot support --hide-unconverted-modules-reasons with mode graph"
      )

  converted = dependency_analysis.get_bp2build_converted_modules(target_product)
  bp2build_metrics = dependency_analysis.get_bp2build_metrics(
      bp2build_metrics_location
  )

  module_adjacency_list, props_by_converted_module_type = (
      get_module_adjacency_list_and_props_by_converted_module_type(
          graph_filter,
          use_queryview,
          ignore_by_name,
          converted,
          target_product,
          ignore_java_auto_deps,
          collect_transitive_dependencies=mode != "graph",
      )
  )

  if len(module_adjacency_list) == 0:
    sys.exit(
        f"Found no modules, verify that the module ({args.module}), type"
        f" ({args.type}) or package {args.package_dir} you requested are valid."
    )

  converted = add_manual_conversion_to_converted(converted, module_adjacency_list)

  output_file = args.out_file
  if mode == "graph":
    dot_file = generate_dot_file(
        module_adjacency_list, converted, args.show_converted
    )
    output_file.write(dot_file)
  elif mode == "report":
    report_data = generate_report_data(
        module_adjacency_list,
        converted,
        graph_filter,
        props_by_converted_module_type,
        args.use_queryview,
        bp2build_metrics,
        args.hide_unconverted_modules_reasons,
        args.show_converted,
    )
    report = generate_report(report_data)
    output_file.write(report)
    if args.proto_file:
      bp2build_conversion_progress_message = generate_proto(report_data)
      with open(args.proto_file, "wb") as f:
        f.write(bp2build_conversion_progress_message.SerializeToString())
  else:
    raise RuntimeError("unknown mode: %s" % mode)


if __name__ == "__main__":
  main()
