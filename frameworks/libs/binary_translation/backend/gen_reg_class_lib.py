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

def gen_machine_reg_class_inc(f, reg_classes):
  for reg_class in reg_classes:
    name = reg_class.get('name')
    regs = reg_class.get('regs')
    print('inline constexpr uint64_t k%sMask =' % (name), file=f)
    for r in regs[: -1]:
      print('    (1ULL << kMachineReg%s.reg()) |' % (r), file=f)
    print('    (1ULL << kMachineReg%s.reg());' % (regs[-1]), file=f)
    print('inline constexpr MachineRegClass k%s = {' % (name), file=f)
    print('    "%s",' % (name), file=f)
    print('    %d,' % (reg_class.get('size')), file=f)
    print('    k%sMask,' % (name), file=f)
    print('    %d,' % (len(regs)), file=f)
    print('    {', file=f)
    for r in regs:
      print('      kMachineReg%s,' % (r), file=f)
    print('    }', file=f)
    print('};', file=f)


def expand_aliases(reg_classes):
  expanded = {}
  for reg_class in reg_classes:
    expanded_regs = []
    for r in reg_class.get('regs'):
      expanded_regs.extend(expanded.get(r, [r]))
    reg_class['regs'] = expanded_regs
    expanded[reg_class.get('name')] = expanded_regs
