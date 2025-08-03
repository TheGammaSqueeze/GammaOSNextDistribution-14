#!/usr/bin/python
#
# Copyright (C) 2014 The Android Open Source Project
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

"""Generate assembler files out of the definition file."""

import asm_defs
import os
import re
import sys


INDENT = '  '

_imm_types = {
    'Imm2': 'int8_t',
    'Imm8': 'int8_t',
    'Imm16': 'int16_t',
    'Imm32': 'int32_t',
    'Imm64': 'int64_t'
}


def _get_arg_type_name(arg):
  cls = arg.get('class')
  if asm_defs.is_x87reg(cls):
    return 'X87Register'
  if asm_defs.is_greg(cls):
    return 'Register'
  if asm_defs.is_xreg(cls):
    return 'XMMRegister'
  if asm_defs.is_imm(cls):
    return _imm_types[cls]
  if asm_defs.is_disp(cls):
    return 'int32_t'
  if asm_defs.is_label(cls):
    return 'const Label&'
  if asm_defs.is_cond(cls):
    return 'Condition'
  if asm_defs.is_mem_op(cls):
    return 'const Operand&'
  raise Exception('class %s is not supported' % (cls))


def _get_immediate_type(insn):
  imm_type = None
  for arg in insn.get('args'):
    cls = arg.get('class')
    if asm_defs.is_imm(cls):
      assert imm_type is None
      imm_type = _imm_types[cls]
  return imm_type


def _get_params(insn):
  result = []
  arg_count = 0
  for arg in insn.get('args'):
    if asm_defs.is_implicit_reg(arg.get('class')):
      continue
    result.append("%s arg%d" % (_get_arg_type_name(arg), arg_count))
    arg_count += 1
  return ', '.join(result)


def _contains_mem(insn):
  return any(asm_defs.is_mem_op(arg['class']) for arg in insn.get('args'))


def _get_template_name(insn):
  name = insn.get('asm')
  if '<' not in name:
    return None, name
  return 'template <%s>' % ', '.join(
      'bool' if param.strip() in ('true', 'false') else
      'typename' if re.search('[_a-zA-Z]', param) else 'int'
      for param in name.split('<',1)[1][:-1].split(',')), name.split('<')[0]


def _gen_generic_functions_h(f, insns, binary_assembler):
  template_names = set()
  for insn in insns:
    template, name = _get_template_name(insn)
    params = _get_params(insn)
    imm_type = _get_immediate_type(insn)
    if template:
      # We could only describe each template function once, or that would be
      # compilation error.  Yet functions with the same name but different
      # arguments are different (e.g. MacroVTbl<15> and MacroVTbl<23>).
      # But different types of arguments could map to the same C++ type.
      # For example MacroCmpFloat<Float32> and MacroCmpFloat<Float64> have
      # different IR arguments (FpReg32 vs FpReg64), but both map to the
      # same C++ type: XMMRegister.
      #
      # Use function name + parameters (as described by _get_params) to get
      # full description of template function.
      template_name = str({
          'name': name,
          'params': _get_params(insn)
      })
      if template_name in template_names:
        continue
      template_names.add(template_name)
      print(template, file=f)
    # If this is binary assembler then we only generate header and then actual
    # implementation is written manually.
    #
    # Text assembled passes "real" work down to GNU as, this works fine with
    # just a simple generic implementation.
    if binary_assembler:
      if 'opcodes' in insn:
        print('void %s(%s) {' % (name, params), file=f)
        _gen_emit_shortcut(f, insn, insns)
        _gen_emit_instruction(f, insn)
        print('}', file=f)
        # If we have a memory operand (there may be at most one) then we also
        # have a special x86-64 exclusive form which accepts Label (it can be
        # emulated on x86-32, too, if needed).
        if 'const Operand&' in params:
          print("", file=f)
          print('void %s(%s) {' % (
              name, params.replace('const Operand&', 'const LabelOperand')), file=f)
          _gen_emit_shortcut(f, insn, insns)
          _gen_emit_instruction(f, insn, rip_operand=True)
          print('}\n', file=f)
      else:
        print('void %s(%s);' % (name, params), file=f)
      if imm_type is not None:
        if template:
          print(template[:-1] + ", typename ImmType>", file=f)
        else:
          print('template<typename ImmType>', file=f)
        print(('auto %s(%s) -> '
                    'std::enable_if_t<std::is_integral_v<ImmType> && '
                    'sizeof(%s) < sizeof(ImmType)> = delete;') % (
                        name, params.replace(imm_type, 'ImmType'), imm_type), file=f)
    else:
      print('void %s(%s) {' % (name, params), file=f);
      if 'feature' in insn:
        print('  SetRequiredFeature%s();' % insn['feature'], file=f)
      print('  Instruction(%s);' % ', '.join(
          ['"%s"' % name] + list(_gen_instruction_args(insn))), file=f)
      print('}', file=f)


def _gen_instruction_args(insn):
  arg_count = 0
  for arg in insn.get('args'):
    if asm_defs.is_implicit_reg(arg.get('class')):
      continue
    if _get_arg_type_name(arg) == 'Register':
      yield 'typename Assembler::%s(arg%d)' % (
          _ARGUMENT_FORMATS_TO_SIZES[arg['class']], arg_count)
    else:
      yield 'arg%d' % arg_count
    arg_count += 1


def _gen_emit_shortcut(f, insn, insns):
  # If we have one 'Imm8' argument then it could be shift, try too see if
  # ShiftByOne with the same arguments exist.
  if asm_defs.exactly_one_of(arg['class'] == 'Imm8' for arg in insn['args']):
    _gen_emit_shortcut_shift(f, insn, insns)
  if asm_defs.exactly_one_of(arg['class'] in ('Imm16', 'Imm32') for arg in insn['args']):
    if insn['asm'].endswith('Accumulator'):
      _gen_emit_shortcut_accumulator_imm8(f, insn, insns)
    else:
      _gen_emit_shortcut_generic_imm8(f, insn, insns)
  if len(insn['args']) > 1 and insn['args'][0]['class'].startswith('GeneralReg'):
    _gen_emit_shortcut_accumulator(f, insn, insns)


def _gen_emit_shortcut_shift(f, insn, insns):
  # Replace Imm8 argument with '1' argument.
  non_imm_args = [arg for arg in insn['args'] if arg['class'] != 'Imm8']
  imm_arg_index = insn['args'].index({'class': 'Imm8'})
  for maybe_shift_by_1_insn in insns:
    if not _is_insn_match(maybe_shift_by_1_insn,
                          insn['asm'] + 'ByOne',
                          non_imm_args):
      continue
    # Now call that version if immediate is 1.
    args = []
    arg_count = 0
    for arg in non_imm_args:
      if asm_defs.is_implicit_reg(arg['class']):
        continue
      args.append('arg%d' % arg_count)
      arg_count += 1
    print('  if (arg%d == 1) return %sByOne(%s);' % (
        imm_arg_index, insn['asm'], ', '.join(args)), file=f)


def _gen_emit_shortcut_accumulator_imm8(f, insn, insns):
  insn_name = insn['asm'][:-11]
  args = insn['args']
  assert len(args) == 3 and args[2]['class'] == 'FLAGS'
  acc_class = args[0]['class']
  # Note: AL is accumulator, too, but but imm is always 8-bit for it which means
  # it shouldn't be encountered here and if it *does* appear here - it's an error
  # and we should fail.
  assert acc_class in ('AX', 'EAX', 'RAX')
  greg_class = {
      'AX': 'GeneralReg16',
      'EAX': 'GeneralReg32',
      'RAX': 'GeneralReg64'
  }[acc_class]
  maybe_8bit_imm_args = [
    { 'class': greg_class, 'usage': args[0]['usage'] },
    { 'class': 'Imm8' },
    { 'class': 'FLAGS', 'usage': insn['args'][2]['usage'] }
  ]
  for maybe_imm8_insn in insns:
    if not _is_insn_match(maybe_imm8_insn,
                          insn_name + 'Imm8',
                          maybe_8bit_imm_args):
      continue
    print('  if (IsInRange<int8_t>(arg0)) {', file=f)
    print(('    return %s(Assembler::Accumulator(), '
                 'static_cast<int8_t>(arg0));') % (
                     maybe_imm8_insn['asm'],), file=f)
    print('  }', file=f)

def _gen_emit_shortcut_generic_imm8(f, insn, insns):
  maybe_8bit_imm_args = [{ 'class': 'Imm8' } if arg['class'].startswith('Imm') else arg
                         for arg in insn['args']]
  imm_arg_index = maybe_8bit_imm_args.index({'class': 'Imm8'})
  for maybe_imm8_insn in insns:
    if not _is_insn_match(maybe_imm8_insn,
                          insn['asm'] + 'Imm8',
                          maybe_8bit_imm_args):
      continue
    # Now call that version if immediate fits into 8-bit.
    arg_count = len(_get_params(insn).split(','))
    print('  if (IsInRange<int8_t>(arg%d)) {' % (arg_count - 1), file=f)
    print('   return %s(%s);' % (maybe_imm8_insn['asm'], ', '.join(
        ('static_cast<int8_t>(arg%d)' if n == arg_count - 1 else 'arg%d') % n
        for n in range(arg_count))), file=f)
    print('  }', file=f)


def _gen_emit_shortcut_accumulator(f, insn, insns):
  accumulator_name = {
      'GeneralReg8': 'AL',
      'GeneralReg16': 'AX',
      'GeneralReg32': 'EAX',
      'GeneralReg64': 'RAX'
  }[insn['args'][0]['class']]
  maybe_accumulator_args = [
      { 'class': accumulator_name, 'usage': insn['args'][0]['usage']}
  ] + insn['args'][1:]
  for maybe_accumulator_insn in insns:
    if not _is_insn_match(maybe_accumulator_insn,
                          insn['asm'] + 'Accumulator',
                          maybe_accumulator_args):
      continue
    # Now call that version if register is an Accumulator.
    arg_count = len(_get_params(insn).split(','))
    print('  if (Assembler::IsAccumulator(arg0)) {', file=f)
    print('  return %s(%s);' % (
      maybe_accumulator_insn['asm'],
      ', '.join('arg%d' % n for n in range(1, arg_count))), file=f)
    print('}', file=f)


def _is_insn_match(insn, expected_name, expected_args):
  # Note: usually there are more than one instruction with the same name
  # but different arguments because they could accept either GeneralReg
  # or Memory or Immediate argument.
  #   Instructions:
  #     Addl %eax, $1
  #     Addl (%eax), $1
  #     Addl %eax, %eax
  #     Addl (%eax), %eax
  #   are all valid.
  #
  # Yet not all instruction have all kinds of optimizations: TEST only have
  # version with accumulator (which is shorter than usual) - but does not
  # have while version with short immediate. Imul have version with short
  # immediate - but not version with accumulator.
  #
  # We want to ensure that we have the exact match - expected name plus
  # expected arguments.

  return insn['asm'] == expected_name and insn['args'] == expected_args


_ARGUMENT_FORMATS_TO_SIZES = {
  'X87Reg' : 'RegisterDefaultBit',
  'Cond': '',
  'FpReg32' : 'VectorRegister128Bit',
  'FpReg64' : 'VectorRegister128Bit',
  'GeneralReg' : 'RegisterDefaultBit',
  'GeneralReg8' : 'Register8Bit',
  'GeneralReg16' : 'Register16Bit',
  'GeneralReg32' : 'Register32Bit',
  'GeneralReg64' : 'Register64Bit',
  'Imm2': '',
  'Imm8': '',
  'Imm16': '',
  'Imm32': '',
  'Imm64': '',
  'Mem8' : 'Memory8Bit',
  'Mem16' : 'Memory16Bit',
  'Mem32' : 'Memory32Bit',
  'Mem64' : 'Memory64Bit',
  'Mem128' : 'Memory128Bit',
  'MemX87': 'MemoryX87',
  'MemX8716': 'MemoryX8716Bit',
  'MemX8732': 'MemoryX8732Bit',
  'MemX8764': 'MemoryX8764Bit',
  'MemX8780': 'MemoryX8780Bit',
  'RegX87': 'X87Register',
  'XmmReg' : 'VectorRegister128Bit',
  'VecMem32': 'VectorMemory32Bit',
  'VecMem64': 'VectorMemory64Bit',
  'VecMem128': 'VectorMemory128Bit',
  'VecReg128' : 'VectorRegister128Bit'
}


# On x86-64 each instruction which accepts explicit memory operant (there may at most be one)
# can also accept $rip-relative addressing (where distance to operand is specified by 32-bit
# difference between end of instruction and operand address).
#
# We use it to support Label operands - only name of class is changed from MemoryXXX to LabelXXX,
# e.g. VectorMemory32Bit becomes VectorLabel32Bit.
#
# Note: on x86-32 that mode can also be emulated using regular instruction form, if needed.
def _gen_emit_instruction(f, insn, rip_operand=False):
  result = []
  arg_count = 0
  for arg in insn['args']:
    if asm_defs.is_implicit_reg(arg['class']):
      continue
    result.append('%s(arg%d)' % (_ARGUMENT_FORMATS_TO_SIZES[arg['class']], arg_count))
    arg_count += 1
  if insn.get('reg_to_rm', False):
    result[0], result[1] = result[1], result[0]
  if insn.get('rm_to_vex', False):
    result[0], result[1] = result[1], result[0]
  if insn.get('vex_imm_rm_to_reg', False):
    result[0], result[1], result[2], result[3] = result[0], result[3], result[1], result[2]
  if insn.get('vex_rm_imm_to_reg', False):
    result[0], result[1], result[2], result[3] = result[0], result[2], result[1], result[3]
  # If we want %rip--operand then we need to replace 'Memory' with 'Labal'
  if rip_operand:
    result = [arg.replace('Memory', 'Label') for arg in result]
  # If vex operand is one of first 8 registers and rm operand is not then swapping these two
  # operands produces more compact encoding.
  # This only works with commutative instructions from first opcode map.
  if ((insn.get('is_optimizable_using_commutation', False) and
    # Note: we may only swap arguments if they have the same type.
    # E.g. if one is memory and the other is register then we couldn't swap them.
    result[0].split('(')[0] == result[2].split('(')[0])):
    assert insn.get('vex_rm_to_reg', False)
    print('  if (Assembler::IsSwapProfitable(%s, %s)) {' % (result[2], result[1]), file=f)
    print('    return EmitInstruction<Opcodes<%s>>(%s);' % (
        ', '.join('0x%02x' % int(opcode, 16) for opcode in insn['opcodes']),
        ', '.join(result)), file=f)
    print('  }', file=f)
  if insn.get('vex_rm_to_reg', False):
    result[0], result[1], result[2] = result[0], result[2], result[1]
  print('  EmitInstruction<Opcodes<%s>>(%s);' % (
      ', '.join('0x%02x' % int(opcode, 16) for opcode in insn['opcodes']),
      ', '.join(result)), file=f)


def _gen_memory_function_specializations_h(f, insns):
  for insn in insns:
    # Only build additional definitions needed for memory access in LIR if there
    # are memory arguments and instruction is intended for use in LIR
    if not _contains_mem(insn) or insn.get('skip_lir'):
      continue
    template, _ = _get_template_name(insn)
    params = _get_params(insn)
    for addr_mode in ('Absolute', 'BaseDisp', 'IndexDisp', 'BaseIndexDisp'):
      # Generate a function to expand a macro and emit a corresponding
      # assembly instruction with a memory operand.
      macro_name = asm_defs.get_mem_macro_name(insn, addr_mode)
      incoming_args = []
      outgoing_args = []
      for i, arg in enumerate(insn.get('args')):
        if asm_defs.is_implicit_reg(arg.get('class')):
          continue
        arg_name = 'arg%d' % (i)
        if asm_defs.is_mem_op(arg.get('class')):
          if addr_mode == 'Absolute':
            incoming_args.append('int32_t %s' % (arg_name))
            outgoing_args.append('{.disp = %s}' % (arg_name))
            continue
          mem_args = []
          if addr_mode in ('BaseDisp', 'BaseIndexDisp'):
            mem_args.append(['Register', 'base', arg_name + '_base'])
          if addr_mode in ('IndexDisp', 'BaseIndexDisp'):
            mem_args.append(['Register', 'index', arg_name + '_index'])
            mem_args.append(['ScaleFactor', 'scale', arg_name + '_scale'])
          mem_args.append(['int32_t', 'disp', arg_name + '_disp'])
          incoming_args.extend(['%s %s' % (pair[0], pair[2]) for pair in mem_args])
          outgoing_args.append('{%s}' % (
              ', '.join(['.%s = %s' % (pair[1], pair[2]) for pair in mem_args])))
        else:
          incoming_args.append('%s %s' % (_get_arg_type_name(arg), arg_name))
          outgoing_args.append(arg_name)
      if template:
        print(template, file=f)
      print('void %s(%s) {' % (macro_name, ', '.join(incoming_args)), file=f)
      print('  %s(%s);' % (insn.get('asm'), ', '.join(outgoing_args)), file=f)
      print('}', file=f)


def _is_for_asm(insn):
  if insn.get('skip_asm'):
    return False
  return True


def _load_asm_defs(asm_def):
  _, insns = asm_defs.load_asm_defs(asm_def)
  # Filter out explicitly disabled instructions.
  return [i for i in insns if _is_for_asm(i)]


def main(argv):
  # Usage: gen_asm.py --binary-assembler|--text_assembler
  #                   <assembler_common-inl.h>
  #                   <assembler_<arch>-inl.h>
  #                   ...
  #                   <def_common>
  #                   <def_arch>
  #                   ...

  mode = argv[1]
  assert len(argv) % 2 == 0
  filenames = argv[2:]
  filename_pairs = ((filenames[i], filenames[len(filenames)//2 + i])
                    for i in range(0, len(filenames)//2))

  if mode == '--binary-assembler':
    binary_assembler = True
  elif mode == '--text-assembler':
    binary_assembler = False
  else:
    assert False, 'unknown option %s' % (mode)

  for out_filename, input_filename in filename_pairs:
    loaded_defs = _load_asm_defs(input_filename)
    with open(out_filename, 'w') as out_file:
      _gen_generic_functions_h(out_file, loaded_defs, binary_assembler)
      if binary_assembler:
        _gen_memory_function_specializations_h(out_file, loaded_defs)

if __name__ == '__main__':
  sys.exit(main(sys.argv))
