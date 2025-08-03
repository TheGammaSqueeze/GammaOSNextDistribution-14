#!/usr/bin/python
#
# Copyright (C) 2018 The Android Open Source Project
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

"""Parse assembler definition file.

Definition JSON file for this script have following form:
{
  "arch": "XXX",
  "insns": [
    {
      "name": "ShlbRegReg",
      "args": [
         {"class": "GeneralReg8", "usage": "use_def"},
         {"class": "RCX", "usage": "use"},
         {"class": "FLAGS", "usage": "def"}
       ],
       "asm": "ShlbByCl",
       "mnemo": "SHLB"
    },
    ...
  ]
'arch' is primarily used for C++ namespace in LIR generator, and is ignored by
this script.

'insn' is array of objects, each describing single instruction variant.
Each instruction is an object with following fields:
  'name' - instruction unique name, used in LIR generator, typical name is
           InsnOp1Op2, where 'Insn' is instruction name, 'Op1', 'Op2' -
           operand types, such as Imm, Reg, Mem(Op), Base, Disp.
  'args' - described ordered list of instruction arguments.
           for each argument 'class' (any GP register, fixed GP register,
           any XMM register, immediate, memory operand, flags register)
           and how it is treated by an instruction (used, defined,
           both used and defined)
  'asm' - which internal assembler's mnemonic is used
  'opcodes' - optional flag for autogeneration: if opcode bytes are specified
              then implementation would be automatically generated
  'reg_to_rm' - optional flag to make RM field in ModRegRM byte destination
                (most instructions with two registers use reg as destination)
  'mnemo' - how instruction shall be named in LIR dumps (ignored here)

Memory operand for assembler instructions can be described as either opaque
Operand class, which provides full power of x86 addressing modes, or as
explicit BaseDisp format, which translates to reg+disp form.

For some instructions (such as pop, push, jmp reg) exact register width is not
specified, and 'GeneralReg' class is used, as same encoding is used for 32 and
64 bit operands, depending on current CPU mode.

This script produces inline file for internal assembler's header, such as for
above example it would yield single line

  void ShlbByCl(Register);

Fixed arguments (such as 'RCX') and flags ('FLAGS') are ignored when generating
assembler's header, while for others emitted an argument of type depending on
argument's class.
"""

import copy
import json


def is_imm(arg_type):
  return arg_type in ('Imm2', 'Imm8', 'Imm16', 'Imm32', 'Imm64')


def is_disp(arg_type):
  return arg_type == 'Disp'


def is_mem_op(arg_type):
  return arg_type in ('Mem8', 'Mem16', 'Mem32', 'Mem64', 'Mem128',
                      'MemX87', 'MemX8716', 'MemX8732', 'MemX8764', 'MemX8780',
                      'VecMem32', 'VecMem64', 'VecMem128')


def is_cond(arg_type):
  return arg_type == 'Cond'


def is_label(arg_type):
  return arg_type == 'Label'


def is_x87reg(arg_type):
  return arg_type == 'RegX87'


def is_greg(arg_type):
  return arg_type in ('GeneralReg',
                      'GeneralReg8', 'GeneralReg16',
                      'GeneralReg32', 'GeneralReg64')


def is_xreg(arg_type):
  return arg_type in ('XmmReg',
                      'VecReg64', 'VecReg128',
                      'FpReg32', 'FpReg64')


# Operands of this type are NOT passed to assembler
def is_implicit_reg(arg_type):
  return arg_type in ('RAX', 'EAX', 'AX', 'AL',
                      'RCX', 'ECX', 'CL', 'ST', 'ST1',
                      'RDX', 'EDX', 'DX', 'CC',
                      'RBX', 'EBX', 'BX', 'SW',
                      'RDI', 'RSI', 'RSP', 'FLAGS')


def exactly_one_of(iterable):
  return sum(1 for elem in iterable if elem) == 1


def get_mem_macro_name(insn, addr_mode = None):
  macro_name = insn.get('asm')
  if macro_name.endswith('ByCl'):
    macro_name = macro_name[:-4]
  for arg in insn['args']:
    clazz = arg['class']
    # Don't reflect FLAGS or Conditions or Labels in the name - we don't ever
    # have two different instructions where these cause the difference.
    if clazz == 'FLAGS' or is_cond(clazz) or is_label(clazz):
      pass
    elif is_x87reg(clazz) or is_greg(clazz) or is_implicit_reg(clazz):
      macro_name += 'Reg'
    elif is_xreg(clazz):
      macro_name += 'XReg'
    elif is_imm(clazz):
      macro_name += 'Imm'
    elif is_mem_op(clazz):
      if addr_mode is not None:
        macro_name += 'Mem' + addr_mode
      else:
        macro_name += 'Op'
    else:
      raise Exception('arg type %s is not supported' % clazz)
  return macro_name


def _expand_name(insn, stem, encoding = {}):
  # Make deep copy of the instruction to make sure consumers could treat them
  # as independent entities and add/remove marks freely.
  #
  # JSON never have "merged" objects thus having them in result violates
  # expectations.
  expanded_insn = copy.deepcopy(insn)
  expanded_insn['asm'] = stem
  expanded_insn['name'] = get_mem_macro_name(expanded_insn)
  expanded_insn['mnemo'] = stem.upper()
  expanded_insn.update(encoding)
  return expanded_insn


def _expand_insn_by_encodings(insns):
  expanded_insns = []
  for insn in insns:
    if insn.get('encodings'):
      assert all((f not in insn) for f in ['stems', 'name', 'asm', 'mnemo'])
      # If we have encoding then we must have at least opcodes
      assert all('opcodes' in encoding for _, encoding in insn['encodings'].items())
      expanded_insns.extend([_expand_name(insn, stem, encoding)
                            for stem, encoding in insn['encodings'].items()])
    elif insn.get('stems'):
      assert all((f not in insn) for f in ['encoding', 'name', 'asm', 'mnemo'])
      expanded_insns.extend([_expand_name(insn, stem)
                            for stem in insn['stems']])
    else:
      assert all((f in insn) for f in ['name', 'asm', 'mnemo'])
      expanded_insns.append(insn)
  return expanded_insns


def _expand_insns_by_operands(insns):
  expanded_insns = []
  for insn in insns:
    split_done = False
    for arg in insn['args']:
      if '/' in arg['class']:
        assert not split_done
        operand_classes = arg['class'].split('/')
        for subclass in operand_classes:
          arg['class'] = subclass
          expanded_insn = copy.deepcopy(insn)
          expanded_insns.append(expanded_insn)
        split_done = True
    if not split_done:
      expanded_insns.append(insn)
  return expanded_insns


def load_asm_defs(asm_def):
  result = []
  with open(asm_def) as asm:
    obj = json.load(asm)
    insns = obj.get('insns')
    insns = _expand_insns_by_operands(insns)
    insns = _expand_insn_by_encodings(insns)
    insns = sorted(insns, key=lambda i: i.get('asm'))
    result.extend(insns)
  return obj.get('arch'), result
