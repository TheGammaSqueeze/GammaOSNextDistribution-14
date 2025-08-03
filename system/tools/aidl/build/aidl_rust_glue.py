#!/usr/bin/env python3

#
# Copyright (C) 2020 The Android Open Source Project
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
import collections
import os
import pathlib
import sys

class RustModule:
    def __init__(self):
        self.files = []
        self.nested = collections.defaultdict(RustModule)

    def emit(self, output_file, indent=""):
        for (input_name, input_path) in self.files:
            output_file.write(indent)
            output_file.write("pub mod %s {\n" % input_name)
            # Copy the contents of the input file into the output
            with open(input_path, "r") as input_file:
                for l in input_file:
                    output_file.write(indent)
                    output_file.write("  ")
                    output_file.write(l)

            output_file.write(indent)
            output_file.write("}\n")

        for name, mod in self.nested.items():
            output_file.write(indent)
            output_file.write("pub mod %s {\n" % name)
            mod.emit(output_file, indent + "  ")
            output_file.write(indent)
            output_file.write("}\n")

    def emit_mangled(self, output_file, indent="", prefix=""):
        for (input_name, _) in self.files:
            output_file.write(indent)
            output_file.write("pub use %s::%s::mangled::*;\n" % (prefix, input_name))
        for name, mod in self.nested.items():
            new_prefix = prefix + "::" + name
            mod.emit_mangled(output_file, indent, prefix=new_prefix)

def main(output, root, inputs, imports):
  root_module = RustModule()
  for inp in inputs:
    in_rel = os.path.relpath(inp, root)
    in_path = pathlib.PurePath(in_rel)

    node = root_module
    for part in in_path.parts[:-1]:
        node = node.nested[part]

    if os.path.isfile(inp):
        in_name, in_ext = os.path.splitext(in_path.parts[-1])
        node.files.append((in_name, inp))

  with open(output, "w") as lib_rs_file:
    lib_rs_file.write("#![allow(non_snake_case)]\n")
    lib_rs_file.write("#![allow(missing_docs)]\n")
    lib_rs_file.write("#[deprecated(note = \"Please access via libbinder_rs binder::\")]\n")
    lib_rs_file.write("pub use binder;\n")

    lib_rs_file.write("pub mod aidl {\n")
    root_module.emit(lib_rs_file, indent="  ")
    lib_rs_file.write("}\n")

    lib_rs_file.write("pub mod mangled {\n")
    root_module.emit_mangled(lib_rs_file, indent="  ", prefix="super::aidl")
    for imp in imports:
      lib_rs_file.write("  pub(crate) use %s::mangled::*;\n" % imp)
    lib_rs_file.write("}\n")

def execute():
  parser = argparse.ArgumentParser(description='Generate the top-level lib.rs.',
                                   fromfile_prefix_chars='@')
  parser.add_argument('output', help='Path to output .rs file')
  parser.add_argument('root', help='Common ancestor of all input files')
  parser.add_argument('inputs', nargs='+', help='Input .rs files')
  parser.add_argument('-I', '--import', action='append', dest='imports',
                      default=[], help='Crates to import')

  args = parser.parse_args()
  if args is None:
    sys.exit(1)

  sys.exit(main(args.output, args.root, args.inputs, args.imports))

if __name__ == "__main__":
  execute()
