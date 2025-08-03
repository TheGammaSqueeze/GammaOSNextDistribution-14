#!src/build/run_python
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

import itertools
import json
import sys

import gen_asm_x86


# Enable to avoid cycles.  Only use one register combo for tests.
fast_mode = False


def main(argv):
  # Usage: gen_asm.tests_py <assembler_ref.S>
  #                         <assembler_test.cc>
  #                         <def_common>
  #                         <def_arch>
  att_assembler_file_name = argv[1]
  arc_assembler_file_name = argv[2]
  # Generate empty files if we don't have assembler files to test.
  if len(argv) <= 4:
    with open(att_assembler_file_name, 'w') as att_assembler_file:
      pass
    with open(arc_assembler_file_name, 'w') as arc_assembler_file:
      pass
    return 0
  common_defs = gen_asm_x86._load_asm_defs(argv[3])
  arch_defs = gen_asm_x86._load_asm_defs(argv[4])

  fast_mode = globals()["fast_mode"]
  if len(argv) > 5 and argv[5] == '--fast':
    fast_mode = True

  with open(argv[4]) as arch_def:
    obj = json.load(arch_def)
    arch = obj.get('arch')
    assert arch in ('x86_32', 'x86_64')
    _update_arguments(arch == 'x86_64')

  with open(att_assembler_file_name, 'w') as att_assembler_file:
    print('.globl berberis_gnu_as_output_start_%s' % arch,
          file=att_assembler_file)
    print('.globl berberis_gnu_as_output_end_%s' % arch,
          file=att_assembler_file)
    print('.data', file=att_assembler_file)
    print('berberis_gnu_as_output_start_%s:' % arch,
          file=att_assembler_file)
    print('.code%d' % (32 if arch == 'x86_32' else 64), file=att_assembler_file)
    _gen_att_assembler(att_assembler_file, common_defs, fast_mode)
    _gen_att_assembler(att_assembler_file, arch_defs, fast_mode)
    print('berberis_gnu_as_output_end_%s:' % arch, file=att_assembler_file)

  with open(arc_assembler_file_name, 'w') as arc_assembler_file:
    print('#include "berberis/assembler/%s.h"' % (arch), file=arc_assembler_file)
    print('namespace berberis {', file=arc_assembler_file)
    print('namespace %s {' % (arch), file=arc_assembler_file)
    _gen_arc_generators(arc_assembler_file)
    _gen_arc_assembler(arc_assembler_file, 'Common', common_defs, fast_mode)
    _gen_arc_assembler(arc_assembler_file, 'Arch', arch_defs, fast_mode)
    print('}  // namespace %s' % (arch), file=arc_assembler_file)
    print('}  // namespace berberis', file=arc_assembler_file)
  return 0


sample_att_arguments = {
    # Note: st(0) is not tested on purpose: many instructions have ambiguity
    # where st(0) to st(0) version can be encoded in two different ways and both
    # work fine, but assemblers produce different versions!
    # Rather than try to match everything perfectly we limit ourselves to st(1),
    # st(2), and st(4) which ensures that all bits are ancoded correctly.
    'RegX87': ('%st(1)', '%st(2)', '%st(4)'),
    'Imm2': ('$0', '$1', '$2', '$3'),
    'Imm8': ('$-1', '$0', '$1', '$2'),
    'Imm16': ('$-1', '$0', '$1', '$2', '$256'),
    'Imm32': ('$-1', '$0', '$1', '$2', '$256', '$65536')
}

sample_att_arguments_x86_32 = {
    # Note: accumulator comes last to ensure that --fast version doesn't pick it,
    # except as accumulator.  That way we test both instruction where accumulator
    # is encoded specially (such as “add %al,$1”) and instructios where
    # accumulator is not used (such as “add %cl,$1”.
    'GeneralReg8': ('%CL', '%DL', '%BL', '%AL'),
    'GeneralReg16': ('%CX', '%DX', '%BX', '%SP', '%BP', '%SI', '%DI', '%AX'),
    'GeneralReg32': ('%ECX', '%EDX', '%EBX', '%ESP',
                     '%EBP', '%ESI', '%EDI', '%EAX'),
    'VecReg128': tuple('%%XMM%d' % N for N in (0, 4, 7)),
    'XmmReg': tuple('%%XMM%d' % N for N in (0, 4, 7)),
    'FpReg32': tuple('%%XMM%d' % N for N in range(8)),
    'FpReg64': tuple('%%XMM%d' % N for N in range(8)),
    'Label': ('0b', '1b', '2f'),
}

sample_att_arguments_x86_64 = {
    'Imm64': ('$-1', '$0', '$1', '$2', '$256', '$65536', '$4294967296'),
    # Note: accumulator comes last to ensure that --fast version doesn't pick it,
    # except as accumulator.  That way we test both instruction where accumulator
    # is encoded specially (such as “add %al,$1”) and instructios where
    # accumulator is not used (such as “add %cl,$1”.
    'GeneralReg8': ('%CL', '%DL', '%BL', '%SPL',
                    '%BPL', '%SIL', '%DIL', '%R8B',
                    '%R9B', '%R10B', '%R11B', '%R12B',
                    '%R13B', '%R14B', '%R15B', '%AL', ),
    'GeneralReg16': ('%CX', '%DX', '%BX', '%SP',
                     '%BP', '%SI', '%DI', '%R8W',
                     '%R9W', '%R10W', '%R11W', '%R12W',
                     '%R13W', '%R14W', '%R15W', '%AX'),
    'GeneralReg32': ('%ECX', '%EDX', '%EBX', '%ESP',
                     '%EBP', '%ESI', '%EDI', '%R8D',
                     '%R9D', '%R10D', '%R11D', '%R12D',
                     '%R13D', '%R14D', '%R15D', '%EAX'),
    'GeneralReg64': ('%RCX', '%RDX', '%RBX', '%RSP',
                     '%RBP', '%RSI', '%RDI', '%R8',
                     '%R9', '%R10', '%R11', '%R12',
                     '%R13', '%R14', '%R15', '%RAX',),
    'VecReg128': tuple('%%XMM%d' % N for N in range(0, 16, 5)),
    'XmmReg': tuple('%%XMM%d' % N for N in range(0, 16, 5)),
    'FpReg32': tuple('%%XMM%d' % N for N in range(16)),
    'FpReg64': tuple('%%XMM%d' % N for N in range(16)),
    'Label': ('0b', '1b', '2f'),
}

sample_arc_arguments = {
    # Note: st(0) is not tested on purpose: many instructions have ambiguity
    # where st(0) to st(0) version can be encoded in two different ways and both
    # work fine, but assemblers produce different versions!
    # Rather than try to match everything perfectly we limit ourselves to st(1),
    # st(2), and st(4) which ensures that all bits are ancoded correctly.
    'RegX87': ('Assembler::st1', 'Assembler::st2', 'Assembler::st4'),
    'Imm2': ('0', '1', '2', '3'),
    'Imm8': ('-1', '0', '1', '2'),
    'Imm16': ('-1', '0', '1', '2', '256'),
    'Imm32': ('-1', '0', '1', '2', '256', '65536'),
    'Cond': ('Assembler::Condition::kOverflow', 'Assembler::Condition::kNoOverflow',
             'Assembler::Condition::kBelow', 'Assembler::Condition::kAboveEqual',
             'Assembler::Condition::kEqual', 'Assembler::Condition::kNotEqual',
             'Assembler::Condition::kBelowEqual', 'Assembler::Condition::kAbove',
             'Assembler::Condition::kNegative', 'Assembler::Condition::kPositive',
             'Assembler::Condition::kParityEven', 'Assembler::Condition::kParityOdd',
             'Assembler::Condition::kLess', 'Assembler::Condition::kGreaterEqual',
             'Assembler::Condition::kLessEqual', 'Assembler::Condition::kGreater'),
}

# Note: have to match sample_att_arguments_x86_32.
# Read comment there about why accumulator is last.
gp_registers_32 = ('Assembler::ecx', 'Assembler::edx',
                   'Assembler::ebx', 'Assembler::esp',
                   'Assembler::ebp', 'Assembler::esi',
                   'Assembler::edi', 'Assembler::eax', )

# Note: have to match sample_att_arguments_x86_32.
# Read comment there about why accumulator is last.
gp_registers_64 = ('Assembler::rcx', 'Assembler::rdx',
                   'Assembler::rbx', 'Assembler::rsp',
                   'Assembler::rbp', 'Assembler::rsi',
                   'Assembler::rdi', 'Assembler::r8',
                   'Assembler::r9', 'Assembler::r10',
                   'Assembler::r11', 'Assembler::r12',
                   'Assembler::r13', 'Assembler::r14',
                   'Assembler::r15', 'Assembler::rax',)

sample_arc_arguments_x86_32 = {
    # Note: have to match sample_att_arguments_x86_32.
    # Read comment there about why accumulator is last.
    'GeneralReg8': ('Assembler::ecx', 'Assembler::edx',
                    'Assembler::ebx', 'Assembler::eax'),
    'GeneralReg16': gp_registers_32,
    'GeneralReg32': gp_registers_32,
    'VecReg128': tuple('Assembler::xmm%d' % N for N in (0, 4, 7)),
    'XmmReg': tuple('Assembler::xmm%d' % N for N in (0, 4, 7)),
    'FpReg32': tuple('Assembler::xmm%d' % N for N in range(8)),
    'FpReg64': tuple('Assembler::xmm%d' % N for N in range(8)),
}

sample_arc_arguments_x86_64 = {
    'Imm64': ('-1', '0', '1', '2', '256', '65536', '4294967296LL'),
    'GeneralReg8': gp_registers_64,
    'GeneralReg16': gp_registers_64,
    'GeneralReg32': gp_registers_64,
    'GeneralReg64': gp_registers_64,
    'VecReg128': tuple('Assembler::xmm%d' % N for N in range(0, 16, 5)),
    'XmmReg': tuple('Assembler::xmm%d' % N for N in range(0, 16, 5)),
    'FpReg32': tuple('Assembler::xmm%d' % N for N in range(16)),
    'FpReg64': tuple('Assembler::xmm%d' % N for N in range(16)),
}

MNEMO_TO_ASM = {
    'MOVDQ': 'MOVAPS',
    'MOVSXBL': 'MOVSBL',
    'MOVSXBQ': 'MOVSBQ',
    'MOVSXWL': 'MOVSWL',
    'MOVSXWQ': 'MOVSWQ',
    'MOVSXLQ': 'MOVSLQ',
    'MOVZXBL': 'MOVZBL',
    'MOVZXBQ': 'MOVZBQ',
    'MOVZXWL': 'MOVZWL',
    'MOVZXWQ': 'MOVZWQ',
    'VCVTPD2DQ': 'VCVTPD2DQX',
    'VCVTPD2PS': 'VCVTPD2PSX',
    'VCVTTPD2DQ': 'VCVTTPD2DQX'
}

FIXED_REGISTER_CLASSES = (
    'AL', 'AX', 'EAX', 'RAX',
    'CL', 'ECX', 'RCX', 'ST', 'ST1',
    'DX', 'EDX', 'RDX', 'CC',
    'BX', 'EBX', 'RBX', 'SW',
    'EBP', 'RSP', 'FLAGS'
)


def _update_arguments(x86_64):
  if x86_64:
    addr = 'GeneralReg64'
    sample_att_arguments.update(sample_att_arguments_x86_64)
    sample_arc_arguments_add = sample_arc_arguments_x86_64
  else:
    addr = 'GeneralReg32'
    sample_att_arguments.update(sample_att_arguments_x86_32)
    sample_arc_arguments_add = sample_arc_arguments_x86_32
  for key, values in sample_arc_arguments_add.items():
    sample_arc_arguments[key] = values
  addrs = ['0']
  addrs += ['(%s)' % reg for reg in sample_att_arguments[addr]]
  addrs += ['%s(,%s%s)' % (offset, index, scale)
            for offset in ('', '64', '32768')
            for index in sample_att_arguments[addr]
            for scale in ('', ',2', ',4', ',8')
            if index not in ('%ESP', '%RSP')]
  addrs += ['%s(%s,%s%s)' % (offset, base, index, scale)
            for offset in ('', '64', '32768')
            for base in sample_att_arguments[addr]
            for index in sample_att_arguments[addr]
            for scale in ('', ',2', ',4', ',8')
            if index not in ('%ESP', '%RSP')]
  for mem_arg in ('Mem8', 'Mem16', 'Mem32', 'Mem64', 'Mem128',
                  'MemX87', 'MemX8716', 'MemX8732', 'MemX8764', 'MemX8780',
                  'VecMem32', 'VecMem64', 'VecMem128'):
    sample_att_arguments[mem_arg] = tuple(addrs)

  sample_att_arguments['GeneralReg'] = sample_att_arguments[addr]

  def peel_constructor(s):
    return s.split('(', 1)[1][:-1] if '(' in s else s

  addrs = ['Assembler::Operand()']
  addrs += ['{.base = %s}' % peel_constructor(reg)
            for reg in sample_arc_arguments[addr]]
  addrs += ['{.index = %s, .scale = Assembler::kTimes%s, .disp = %d}' %
            (peel_constructor(index), scale, disp)
            for disp in (0, 64, 32768)
            for index in sample_arc_arguments[addr]
            for scale in ('One', 'Two', 'Four', 'Eight')
            if 'Assembler::esp' not in index and 'Assembler::rsp' not in index]
  addrs += ['{.base = %s, .index = %s, .scale = Assembler::kTimes%s, .disp = %d}' %
            (peel_constructor(base), peel_constructor(index), scale, disp)
            for disp in (0, 64, 32768)
            for base in sample_arc_arguments[addr]
            for index in sample_arc_arguments[addr]
            for scale in ('One', 'Two', 'Four', 'Eight')
            if 'Assembler::esp' not in index and 'Assembler::rsp' not in index]
  for mem_arg in ('Mem8', 'Mem16', 'Mem32', 'Mem64', 'Mem128',
                  'MemX87', 'MemX8716', 'MemX8732', 'MemX8764', 'MemX8780',
                  'VecMem32', 'VecMem64', 'VecMem128'):
    sample_arc_arguments[mem_arg] = tuple(addrs)

  sample_arc_arguments['GeneralReg'] = sample_arc_arguments[addr]


def _gen_att_assembler(file, insns, fast_mode):
  for insn in insns:
    arc_name = insn['asm']
    insn_name = insn['mnemo']
    if len(insn['args']) and insn['args'][0]['class'] == 'Cond':
      if insn_name in ('CMOVW', 'CMOVL', 'CMOVQ'):
        insn_name = 'CMOV'
      else:
        assert insn_name.endswith('CC')
        insn_name = insn_name[:-2]
      for insn_suffix in ('O', 'NO', 'B', 'AE', 'E', 'NE', 'BE', 'A',
                          'S', 'NS', 'P', 'NP', 'L', 'GE', 'LE', 'G'):
        _gen_att_instruction_variants(
            file, arc_name, insn_name + insn_suffix, insn['args'], fast_mode)
    elif arc_name == 'Call' and insn['args'][1]['class'] != 'Label':
      _gen_att_call_variants(file, insn['args'], fast_mode)
    else:
      _gen_att_instruction_variants(
          file, arc_name, insn_name, insn['args'], fast_mode)


def _gen_att_instruction_variants(
    file, arc_name, insn_name, insn_args, fast_mode):
  if insn_name in MNEMO_TO_ASM:
    insn_name = MNEMO_TO_ASM[insn_name]
  insn_sample_args = []
  label_present = False
  if arc_name.endswith('ByOne'):
    assert insn_name.endswith('BYONE')
    insn_name = insn_name[:-5]
  elif arc_name.endswith('Imm2'):
    assert insn_name.endswith('IMM2')
    insn_name = insn_name[:-4]
  elif arc_name.endswith('Imm8'):
    assert insn_name.endswith('IMM8')
    insn_name = insn_name[:-4]
  elif arc_name.endswith('Accumulator'):
    assert insn_name.endswith('ACCUMULATOR')
    insn_name = insn_name[:-11]
  elif arc_name.endswith('ByCl'):
    assert insn_name.endswith('BYCL')
    insn_name = insn_name[:-4]
  elif arc_name.endswith('FromSt'):
    assert insn_name.endswith('FROMST')
    insn_name = insn_name[:-6]
  elif arc_name.endswith('ToSt'):
    assert insn_name.endswith('TOST')
    insn_name = insn_name[:-4]
  for arg_nr, insn_arg in enumerate(insn_args):
    arg_class = insn_arg['class']
    if arg_class == 'Cond':
      # This argument was already embedded into the name of instruction.
      continue
    if arg_class == 'Label':
      label_present = True
    if arg_class in ('AL', 'AX', 'EAX', 'RAX') and (
       arc_name.endswith('Accumulator') or arc_name == "Fnstsw"):
      arg_variants = ('%%%s' % arg_class,)
    elif arg_class == 'CL' and arc_name.endswith('ByCl'):
      arg_variants = ('%CL',)
    elif arg_class == 'ST' and (arc_name.endswith('FromSt') or arc_name.endswith('ToSt')):
      arg_variants = ('%ST',)
    elif arg_class == 'GeneralReg' and insn_name not in ('PUSH', 'POP'):
      arg_variants = tuple('*%s' % reg for reg in sample_att_arguments['GeneralReg'])
    elif arg_class[:3] == 'Imm' and insn_name in (
        'PSLLW', 'PSRAW', 'PSRLW', 'PSLLD', 'PSRAD', 'PSRLD',
        'PSLLQ', 'PSRLQ', 'PSLLDQ', 'PSRLDQ',
        'RCLB', 'RCLW', 'RCLL', 'RCLQ', 'RCRB', 'RCRW', 'RCRL', 'RCRQ',
        'ROLB', 'ROLW', 'ROLL', 'ROLQ', 'RORB', 'RORW', 'RORL', 'RORQ',
        'SHLB', 'SHLW', 'SHLL', 'SHLQ', 'SHRB', 'SHRW', 'SHRL', 'SHRQ',
        'SARB', 'SARW', 'SARL', 'SARQ', 'BTCB', 'BTCW', 'BTCL', 'BTCQ'):
      arg_variants = sample_att_arguments[insn_arg['class']][1:]
    elif ((arg_class == 'Mem32' and insn_name in ('JMPL', 'CALLL')) or
          (arg_class == 'VecMem64' and insn_name in ('JMPQ', 'CALLQ'))):
      arg_variants = tuple('*%s' % reg for reg in sample_att_arguments[insn_arg['class']])
    elif arg_class in FIXED_REGISTER_CLASSES:
      # Note: arguments from FIXED_REGISTER_CLASSES are implicit in AT&T assembler
      # (except for accumulator in some instructions).  Skip them.
      continue
    else:
      arg_variants = sample_att_arguments[insn_arg['class']]
    # Some instructions have special encodings with certain immediates
    # (e.g. shifts by 1, introduced in 8086, are encoded differently than
    # shifts by 2, introduced in 80186).
    # Keep all variants even in --fast mode.
    if fast_mode and not arg_class.startswith('Imm'):
      # In --fast mode we want to keep only one variant, but it's important for
      # us to pick different registers for diffetent position.
      # This way we are testting “vmfaddps %xmm0, %xmm1, %xmm2, %xmm3” instead
      # of “vmfaddps %xmm0, %xmm0, %xmm0, %xmm0” which is important to detect
      # cases where operands are specified in a wrong order in JSON.
      arg_variants = (arg_variants[arg_nr % len(arg_variants)],)
    insn_sample_args.append(arg_variants)
  for insn_args in itertools.product(*insn_sample_args):
    fixed_name = insn_name
    if insn_name == 'MOVQ' and not '(' in insn_args[0] and '%' in insn_args[0]:
      # This is rare case where ARC code emitter produces code more optimal than
      # GNU assembler.  Reproduce that optimization here.
      if insn_args[1][0] == '$' and insn_args[1] not in ('$-1', '$4294967296'):
        fixed_name = 'MOVL'
        # Make 32-bit version of register name.  High registers need to add
        # 'D' suffix, for low ones we need to replace 'R' with 'E'.
        if insn_args[0] in ('%R8', '%R9', '%R10', '%R11',
                            '%R12', '%R13', '%R14', '%R15'):
          insn_args = (insn_args[0] + 'D',) + insn_args[1:]
        else:
          insn_args = ('%E' + insn_args[0][2:],) + insn_args[1:]
    if insn_name[0:4] == 'LOCK':
     # TODO(b/161986409): replace '\n' with ' ' when clang would be fixed.
     fixed_name = '%s\n%s' % (insn_name[0:4], insn_name[4:])
    fixed_name = {
      # GNU disassembler accepts these instructions, but not Clang assembler.
      'FNDISI': '.byte 0xdb, 0xe1',
      'FNENI': '.byte 0xdb, 0xe0',
      'FNSETPM': '.byte 0xdb, 0xe4',
    }.get(fixed_name, fixed_name)
    if label_present:
      print('.p2align 5, 0x90', file=file)
      print('0:', file=file)
      for _ in range(256):
        print('nop', file=file)
      print('1:', file=file)
    print('%s %s' % (fixed_name, ', '.join(reversed(insn_args))), file=file)
    if label_present:
      for _ in range(256):
        print('nop', file=file)
      print('2:', file=file)


def _gen_att_call_variants(file, call_args, fast_mode):
  assert len(call_args) == 2
  assert call_args[0]['class'] == 'RSP'
  assert call_args[1]['class'] == 'GeneralReg'
  arg_variants = sample_att_arguments['GeneralReg']
  if fast_mode:
    arg_variants = (arg_variants[1],)
  for call_arg in arg_variants:
    print('CALL *%s' % call_arg, file=file)


def _gen_arc_generators(file):
  for arg_class, arc_args in sample_arc_arguments.items():
    if arg_class == 'Label':
      continue
    arg_type = _argument_class_to_arc_type(arg_class)
    print('%s* %sArgs() {' % (arg_type, arg_class), file=file)
    print('  static %s* arg_list;' % arg_type, file=file)
    print('  if (!arg_list) {', file=file)
    print('    arg_list = reinterpret_cast<%s*>(malloc(%d * sizeof(%s)));' %
          (arg_type, len(arc_args), arg_type), file=file)
    for arg_nr, arg in enumerate(arc_args):
      print('    arg_list[%d] = %s;' % (arg_nr, arg), file=file)
    print('  }', file=file)
    print('  return arg_list;', file=file)
    print('}', file=file)
    print('', file=file)


def _gen_arc_assembler(file, insn_kind, insns, fast_mode):
  for insn in insns:
    _gen_arc_instruction_variants(file, insn['asm'], insn['args'], fast_mode)
  print('void GenInsns%s(Assembler* as) {' %
        insn_kind, file=file)
  for insn in insns:
    classes = [insn_arg['class'] for insn_arg in insn['args']]
    print('  %s_%s(as);' % (insn['asm'], '_'.join(classes)), file=file)
  if not insns:
    print('  UNUSED(as);', file=file)
  print('}', file=file)
  print('', file=file)


def _gen_arc_instruction_variants(file, arc_name, insn_args, fast_mode):
  classes = [insn_arg['class'] for insn_arg in insn_args]
  print('void %s_%s(Assembler* as) {' %
        (arc_name, '_'.join(classes)), file=file)
  label_present = False
  arg_type_count = 0
  indent = ''
  for arg_nr, insn_arg in enumerate(insn_args):
    arg_class = insn_arg['class']
    arg_ref = '*'
    indent = ' ' * (2 * arg_type_count)
    if arg_class == 'Label':
      label_present = True
      print('%s  Assembler::Label* labels[3];' % indent, file=file)
      args_generator = 'labels'
      arg_ref = '**'
    else:
      args_generator = '%sArgs()' % arg_class
    if arg_class not in FIXED_REGISTER_CLASSES:
      arg_type = _argument_class_to_arc_type(arg_class)
      if arg_class == 'Label':
        arg_choices = 3
      else:
        arg_choices = len(sample_arc_arguments[arg_class])
      arg_shift = ''
      if arg_class[:3] == 'Imm' and arc_name in (
          'Psllw', 'Psraw', 'Psrlw', 'Pslld', 'Psrad', 'Psrld',
          'Psllq', 'Psrlq', 'Pslldq', 'Psrldq',
          'Rclb', 'Rclw', 'Rcll', 'Rclq', 'Rcrb', 'Rcrw', 'Rcrl', 'Rcrq',
          'Rolb', 'Rolw', 'Roll', 'Rolq', 'Rorb', 'Rorw', 'Rorl', 'Rorq',
          'Shlb', 'Shlw', 'Shll', 'Shlq', 'Shrb', 'Shrw', 'Shrl', 'Shrq',
          'Sarb', 'Sarw', 'Sarl', 'Sarq', 'Btcb', 'Btcw', 'Btcl', 'Btcq'):
        arg_choices = arg_choices - 1
        arg_shift = ' + 1'
      # See notes about fast_mode handing in _gen_att_instruction_variants.
      if fast_mode and not arg_class.startswith('Imm') and not arg_class == 'Cond':
        print('%s  %s %sarg%d = %s%s + %d;' %
              (indent, arg_type, arg_ref, arg_nr, args_generator, arg_shift,
               arg_nr % arg_choices),
              file=file)
      else:
        arg_type_count += 1
        print('%s  for (%s %sarg%d = %s%s, %sarg%d_list = arg%d;'
              ' arg%d != arg%d_list + %d; ++arg%d) {' %
              (indent, arg_type, arg_ref, arg_nr, args_generator, arg_shift,
               arg_ref, arg_nr, arg_nr, arg_nr, arg_nr, arg_choices, arg_nr),
              file=file)
  indent = ' ' * (2 * arg_type_count)
  if label_present:
    print('%s  labels[0] = as->MakeLabel();' % indent, file=file)
    print('%s  labels[1] = as->MakeLabel();' % indent, file=file)
    print('%s  labels[2] = as->MakeLabel();' % indent, file=file)
    print('%s  // Don\'t use as->Align(32) for compatibility with GNU as'
          % indent, file=file)
    print('%s  while (as->pc()%%32) as->Nop();' % indent, file=file)
    print('%s  as->Bind(labels[0]);' % indent, file=file)
    print('%s  for (int nr=0; nr < 256; ++nr)' % indent, file=file)
    print('%s    as->Nop();' % indent, file=file)
    print('%s  as->Bind(labels[1]);' % indent, file=file)
  print('%s  as->%s(%s);' %
        (indent,
         arc_name,
         ', '.join((
             '%sarg%d' % ('*' if arg['class'] != 'Label' else '**', nr)
             for nr, arg in enumerate(insn_args)
             if arg['class'] not in FIXED_REGISTER_CLASSES))),
        file=file)
  if label_present:
    print('%s  for (int nr=0; nr < 256; ++nr)' % indent, file=file)
    print('%s    as->Nop();' % indent, file=file)
    print('%s  as->Bind(labels[2]);' % indent, file=file)
  for arg_nr in range(arg_type_count, 0, -1):
    print('%s}' % (' ' * (2 * arg_nr)), file=file)
  print('}', file=file)
  print('', file=file)


def _argument_class_to_arc_type(arg_class):
  if arg_class == 'Imm2':
    return 'int8_t'
  elif arg_class[:3] == 'Imm':
    return 'int%s_t' % arg_class[3:]
  elif arg_class == 'Cond':
    return 'Assembler::Condition'
  elif arg_class == 'Label':
    return 'Assembler::Label'
  elif sample_arc_arguments[arg_class][0] in gp_registers_32 + gp_registers_64:
    return 'Assembler::Register'
  elif sample_arc_arguments[arg_class][0].startswith('Assembler::st'):
    return 'Assembler::X87Register'
  elif sample_arc_arguments[arg_class][0].startswith('Assembler::xmm'):
    return 'Assembler::XMMRegister'
  else:
    return sample_arc_arguments[arg_class][0].split('(')[0]


if __name__ == '__main__':
  sys.exit(main(sys.argv))
