#!/usr/bin/env python3
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

import argparse
import json
import logging
import pathlib
import typing

COPYRIGHT_HEADER = """// Copyright 2020, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

"""

class BoostAndroidBPGenerator:
  """
  A generator of Soong's Android.bp file for boost.
  """
  DEFAULT_MODULE_CONF = "<DEFAULT>"
  PRIORITY_KEYS = [
    "name",
    "defaults",
    "cc_defaults",
    "cc_library_headers",
    "cc_library"
  ]
  def __init__(self, bp_template_file: pathlib.Path, dependency_file: pathlib.Path):
    """
    :param bp_template_file: The JSON template contains definitions that are
                             used to tweak the generated Android.bp file.
                             By default, all the boost modules are transformed
                             into a Soong module (libboost_$name) with default
                             configurations. Nevertheless, certain Boost modules
                             need compiler flags or other specific configuration.
    :param dependency_file: A text file with dependency graph of all boost modules.
                            This file can be generated with the `b2` tool.
    """
    self.bp_template = json.loads(bp_template_file.read_text())
    self.write_level = -1
    self.log = logging.getLogger("BoostBPGenerator")
    self.modules = {}
    self.required_modules = set()
    self.load_dependencies(dependency_file)

  def load_dependencies(self, dependency_file: pathlib.Path):
    """
    Parse the dependency file.
    """
    self.modules.clear()
    with dependency_file.open("r") as fd:
      for line in fd.readlines():
        self.log.debug("Parsing line '%s'", line)
        line = line.rstrip()
        module_dependencies = line.split(" -> ", 1)
        # The module is standalone and doesn't have dependencies
        if len(module_dependencies) == 1:
          module = module_dependencies[0][:-3] # cut the " ->" at the end
        else:
          module = module_dependencies[0]
        try:
          dependencies = module_dependencies[1].split(" ")
        except IndexError:
          dependencies = []
        self.modules[module] = dependencies

  def resolve_dependencies(self, module: str):
    if module not in self.modules:
      raise ValueError("Unknown module", module)

    if module in self.required_modules:
      return

    self.required_modules.add(module)

    for module in self.modules[module]:
      self.resolve_dependencies(module)

  def sorted_dict_items(self, data: dict):
    """
    Equivalent to dict.items() but return items sorted by a certain priority
    and the remaining items sorted alphabetically.
    """
    visited = []
    for key in BoostAndroidBPGenerator.PRIORITY_KEYS:
      if key in data:
        visited.append(key)
        yield (key, data[key])
    for key, value in sorted(data.items(), key=lambda i: i[0]):
      if key not in visited:
        yield (key, value)

  def render(self, obj, stream: typing.TextIO):
    """
    Given an object (dict or list), render a valid Soong version of such
    an object into a text `stream`.
    """
    self.write_level += 1
    self.log.debug("%s %s", " " * self.write_level, type(obj))
    if isinstance(obj, dict):
      for key, value in self.sorted_dict_items(obj):
        stream.write(" " * self.write_level + f"{key}")
        if self.write_level > 1:
          stream.write(": ")
        if isinstance(value, dict):
          self.write_level += 1
          stream.write(" {\n")
          self.render(value, stream)
          self.write_level -= 1
          stream.write(" " * self.write_level + "}")
          if self.write_level > 1:
            stream.write(",")
          stream.write("\n")
        elif isinstance(value, (str, bool, int, float)):
          stream.write(json.dumps(value) + ",\n")
        elif isinstance(value, list):
          self.render(value, stream)
        else:
          raise ValueError("Unsupported type in dict", key, value, type(value))
    elif isinstance(obj, list):
      rendered = json.dumps(obj, indent=self.write_level + 1).splitlines()
      rendered[-2] += ","
      rendered[-1] = " " * (self.write_level - 1) + rendered[-1] + ",\n"
      stream.write("\n".join(rendered))
    else:
      raise ValueError("Unsupported type", obj, type(obj))
    self.write_level -= 1
    if self.write_level == -1:
      stream.write("\n")

  def is_ignored_module(self, module):
    """
    Return whether a boost module is to be ignored (i.e not built).
    """
    return module in self.bp_template["ignored_modules"]

  def get_module_bp(self, module: str, bp: dict):
    module_bp = {}
    # Some boost modules have submodules. Those are marked with ~ (til):
    # <module name>~<submodule name>. e.g numeric~convertion.
    # Let's use <module name>_<submodule name> for the Soong module name.
    module_name = module.replace('~', '_')
    module_dir = module.replace('~', '/')
    bp["cc_library_headers"]["export_include_dirs"].append(f"{module_dir}/include")
    module_bp = {**self.bp_template["modules"][BoostAndroidBPGenerator.DEFAULT_MODULE_CONF]}
    try:
      module_bp.update(**self.bp_template["modules"][module_name])
    except KeyError:
      pass
    module_bp["name"] = f"libboost_{module_name}"
    # Respect the "srcs" entry from the template
    if "srcs" not in module_bp:
      module_bp["srcs"] = [f"{module_dir}/src/**/*.cpp", f"{module_dir}/src/**/*.c"]
    # Respect the "export_include_dirs" entry from the template
    if "export_include_dirs" not in module_bp:
      module_bp["export_include_dirs"] = [f"{module_dir}/include"]
    dependencies = [
      f"libboost_{dep.replace('~', '_')}" for dep in self.modules[module]
      if not self.is_ignored_module(dep)
    ]
    if dependencies:
      module_bp["shared"] = {
        "shared_libs": dependencies
      }
    return module_bp

  def save_android_bp(self, required_modules: list, android_bp_file: pathlib.Path):
    """
    Saves the Soong module configuration.

    :param required_modules: A list of modules (and its dependencies) to include in the
                            Android.bp file.
    :param android_bp_file: the path to the file to write the Soong module config.
    """
    bp = {
      "cc_library": []
    }

    self.required_modules = set()
    for module in required_modules:
      self.resolve_dependencies(module)

    for item, value in self.bp_template["templates"].items():
      bp[item] = value
    for module in sorted(self.required_modules):
      if self.is_ignored_module(module):
        self.log.warning("Ignoring module %s", module)
        continue
      self.log.debug("Preparing module %s", module)
      module_bp = self.get_module_bp(module, bp)
      bp["cc_library"].append(module_bp)
    self.log.info("Generating %s", android_bp_file)
    with android_bp_file.open("w") as fd:
      fd.write(COPYRIGHT_HEADER)
      fd.write("// This file is auto-generated by gen_android_bp.py, do not manually modify.\n")
      fd.write("// The required modules were:\n")
      fd.write("\n".join(f"//   - {module}" for module in sorted(required_modules)))
      fd.write("\n\n")
      try:
        for key, value in self.sorted_dict_items(bp):
          self.log.debug("Rendering section %s", key)
          if isinstance(value, list):
            for i in value:
              self.render({key: i}, fd)
          else:
            self.render({key: value}, fd)
      except Exception:
        self.log.error("Broke in %s", i)
        raise

def parse_args():
  parser = argparse.ArgumentParser(description="AOSP Boost importer")
  parser.add_argument(
    "--dependency-file",
    type=pathlib.Path,
    help="Path to a file with the output of `b2 --list-dependencies`",
    required=True,
  )
  parser.add_argument(
    "--verbose",
    action='store_true',
    default=False,
  )
  parser.add_argument(
    "--bp-template",
    type=pathlib.Path,
    help="Path to the JSON file with the Android.bp template",
    required=True,
  )
  parser.add_argument(
    "--output",
    type=pathlib.Path,
    help="Name of the output Blueprint file. Default: (default: %(default)s)",
    default=pathlib.Path("Android.bp"),
  )
  parser.add_argument(
    "--module",
    action="append",
    help="Name of the module to be included. It may be used multiple times.",
  )
  return parser.parse_args()

def main():
  args = parse_args()
  logging.basicConfig(
    format="%(asctime)s | %(levelname)-10s | %(message)s",
    level=logging.DEBUG if args.verbose else logging.INFO
  )
  bp_generator = BoostAndroidBPGenerator(args.bp_template, args.dependency_file)
  bp_generator.save_android_bp(args.module, args.output)

if __name__ == "__main__":
  main()
