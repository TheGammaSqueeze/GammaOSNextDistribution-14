#!/usr/bin/python3
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

"""Generate machine IR register class definitions from data file."""

import gen_reg_class_lib
import json
import sys


def main(argv):
  # Usage: gen_reg_class.py <machine_reg_class-inl.h> <def> ... <def>
  machine_reg_class_inl_name = argv[1]
  defs = argv[2:]

  reg_classes = []
  for d in defs:
    with open(d) as f:
      j = json.load(f)
      reg_classes.extend(j.get('reg_classes'))

  gen_reg_class_lib.expand_aliases(reg_classes)

  with open(machine_reg_class_inl_name, 'w') as f:
    gen_reg_class_lib.gen_machine_reg_class_inc(f, reg_classes)


if __name__ == '__main__':
  sys.exit(main(sys.argv))
