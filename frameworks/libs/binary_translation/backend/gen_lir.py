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

"""Generate LIR files out of the definition file.

* Operand usage

Register allocator needs operand usage to learn which operands can share the
same register.

To understand register sharing options, register allocator assumes insn works
in these steps:
- read input operands
- do the job
- write output operands

So, input-output operands should have dedicated registers, while input-only
operands can share registers with output-only operands.

There might be an exception when output-only operand is written before all
input-only operands are read, so its register can't be shared. Such operands
are usually referred as output-only-early-clobber operands.

For register sharing, output-only-early-clobber operand is the same as
input-output operand, but it is unnatural to describe output-only as
input-output, so we use a special keyword for it.

Finally, keywords are:
use - input-only
def - output-only
def_early_clobber - output-only-early-clobber
use_def - input-output

* Scratch operands

Scratch operands are actually output operands - indeed, their original value
is not used and they get some new value after the insn is done. However, they
are usually written before all input operands are read, so it makes sense to
describe scratch operands as output-only-early-clobber.
"""

import gen_lir_lib
import sys

def main(argv):
  # Usage:
  #   gen_lir.py --headers <insn-inl.h>
  #                        <machine_info-inl.h>
  #                        <machine_opcode-inl.h>
  #                        <machine_ir-inl.h>
  #                        <lir_instructions.json>
  #                        ...
  #                        <machine_ir_intrinsic_binding.json>
  #                        ...
  #                        <def>
  #                        ...
  #   gen_lir.py --sources <code_emit.cc>
  #                        <code_debug.cc>
  #                        <lir_instructions.json>
  #                        ...
  #                        <machine_ir_intrinsic_binding.json>
  #                        ...
  #                        <def>
  #                        ...

  mode = argv[1]
  lir_def_files_begin = 6 if mode == '--headers' else 4
  lir_def_files_end = lir_def_files_begin
  while argv[lir_def_files_end].endswith('lir_instructions.json'):
    lir_def_files_end += 1
  arch_def_files_end = lir_def_files_end
  while argv[arch_def_files_end].endswith('machine_ir_intrinsic_binding.json'):
    arch_def_files_end += 1

  if mode == '--headers':
    arch, insns = gen_lir_lib.load_all_lir_defs(
      argv[lir_def_files_begin:lir_def_files_end],
      argv[lir_def_files_end:arch_def_files_end],
      argv[arch_def_files_end:])
    gen_lir_lib.gen_code_2_cc(argv[2], arch, insns)
    gen_lir_lib.gen_machine_info_h(argv[3], arch, insns)
    gen_lir_lib.gen_machine_opcode_h(argv[4], arch, insns)
    gen_lir_lib.gen_machine_ir_h(argv[5], arch, insns)
  elif mode == '--sources':
    arch, insns = gen_lir_lib.load_all_lir_defs(
      argv[lir_def_files_begin:lir_def_files_end],
      argv[lir_def_files_end:arch_def_files_end],
      argv[arch_def_files_end:])
    gen_lir_lib.gen_code_emit_cc(argv[2], arch, insns)
    gen_lir_lib.gen_code_debug_cc(argv[3], arch, insns)
  else:
    assert False, 'unknown option %s' % (mode)

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
