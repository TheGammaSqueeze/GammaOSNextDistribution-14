/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <cstdint>

#include "lite_translator.h"

#include "berberis/base/checks.h"
#include "berberis/base/macros.h"
#include "berberis/code_gen_lib/code_gen_lib.h"
#include "berberis/decoder/riscv64/decoder.h"

namespace berberis {

using Register = LiteTranslator::Register;
using Condition = LiteTranslator::Condition;

//
// Instruction implementations.
//

Register LiteTranslator::Op(Decoder::OpOpcode opcode, Register arg1, Register arg2) {
  using OpOpcode = Decoder::OpOpcode;
  Register res = AllocTempReg();
  switch (opcode) {
    case OpOpcode::kAdd:
      as_.Movq(res, arg1);
      as_.Addq(res, arg2);
      break;
    case OpOpcode::kSub:
      as_.Movq(res, arg1);
      as_.Subq(res, arg2);
      break;
    case OpOpcode::kAnd:
      as_.Movq(res, arg1);
      as_.Andq(res, arg2);
      break;
    case OpOpcode::kOr:
      as_.Movq(res, arg1);
      as_.Orq(res, arg2);
      break;
    case OpOpcode::kXor:
      as_.Movq(res, arg1);
      as_.Xorq(res, arg2);
      break;
    case OpOpcode::kSll:
    case OpOpcode::kSrl:
    case OpOpcode::kSra:
      as_.Movq(res, arg1);
      as_.Movq(as_.rcx, arg2);
      if (opcode == OpOpcode::kSrl) {
        as_.ShrqByCl(res);
      } else if (opcode == OpOpcode::kSll) {
        as_.ShlqByCl(res);
      } else if (opcode == OpOpcode::kSra) {
        as_.SarqByCl(res);
      } else {
        FATAL("Unexpected OpOpcode");
      }
      break;
    case OpOpcode::kSlt:
      as_.Xorq(res, res);
      as_.Cmpq(arg1, arg2);
      as_.Setcc(Condition::kLess, res);
      break;
    case OpOpcode::kSltu:
      as_.Xorq(res, res);
      as_.Cmpq(arg1, arg2);
      as_.Setcc(Condition::kBelow, res);
      break;
    case OpOpcode::kMul:
      as_.Movq(res, arg1);
      as_.Imulq(res, arg2);
      break;
    case OpOpcode::kMulh:
      as_.Movq(res, arg1);
      as_.Movq(as_.rax, arg1);
      as_.Imulq(arg2);
      as_.Movq(res, as_.rdx);
      break;
    case OpOpcode::kMulhsu: {
      as_.Movq(res, arg1);
      as_.Movq(as_.rax, arg2);
      as_.Mulq(res);
      as_.Sarq(res, int8_t{63});
      as_.Imulq(res, arg2);
      as_.Addq(res, as_.rdx);
      break;
    }
    case OpOpcode::kMulhu:
      as_.Movq(as_.rax, arg1);
      as_.Mulq(arg2);
      as_.Movq(res, as_.rdx);
      break;
    case OpOpcode::kDiv:
    case OpOpcode::kRem:
      as_.Movq(as_.rax, arg1);
      as_.Movq(as_.rdx, as_.rax);
      as_.Sarq(as_.rdx, int8_t{63});
      as_.Idivq(arg2);
      as_.Movq(res, opcode == OpOpcode::kDiv ? as_.rax : as_.rdx);
      break;
    case OpOpcode::kDivu:
    case OpOpcode::kRemu:
      as_.Movq(as_.rax, arg1);
      as_.Xorq(as_.rdx, as_.rdx);
      as_.Divq(arg2);
      as_.Movq(res, opcode == OpOpcode::kDivu ? as_.rax : as_.rdx);
      break;
    case Decoder::OpOpcode::kAndn:
      if (host_platform::kHasBMI) {
        as_.Andnq(res, arg2, arg1);
      } else {
        as_.Movq(res, arg2);
        as_.Notq(res);
        as_.Andq(res, arg1);
      }
      break;
    case Decoder::OpOpcode::kOrn:
      as_.Movq(res, arg2);
      as_.Notq(res);
      as_.Orq(res, arg1);
      break;
    case Decoder::OpOpcode::kXnor:
      as_.Movq(res, arg2);
      as_.Xorq(res, arg1);
      as_.Notq(res);
      break;
    default:
      Unimplemented();
      return {};
  }
  return res;
}

Register LiteTranslator::Op32(Decoder::Op32Opcode opcode, Register arg1, Register arg2) {
  using Op32Opcode = Decoder::Op32Opcode;
  Register res = AllocTempReg();
  switch (opcode) {
    case Op32Opcode::kAddw:
      as_.Movl(res, arg1);
      as_.Addl(res, arg2);
      as_.Movsxlq(res, res);
      break;
    case Op32Opcode::kSubw:
      as_.Movl(res, arg1);
      as_.Subl(res, arg2);
      as_.Movsxlq(res, res);
      break;
    case Op32Opcode::kSllw:
    case Op32Opcode::kSrlw:
    case Op32Opcode::kSraw:
      as_.Movl(res, arg1);
      as_.Movl(as_.rcx, arg2);
      if (opcode == Op32Opcode::kSrlw) {
        as_.ShrlByCl(res);
      } else if (opcode == Op32Opcode::kSllw) {
        as_.ShllByCl(res);
      } else if (opcode == Op32Opcode::kSraw) {
        as_.SarlByCl(res);
      } else {
        FATAL("Unexpected Op32Opcode");
      }
      as_.Movsxlq(res, res);
      break;
    case Op32Opcode::kMulw:
      as_.Movl(res, arg1);
      as_.Imull(res, arg2);
      as_.Movsxlq(res, res);
      break;
    case Op32Opcode::kDivw:
    case Op32Opcode::kRemw:
      as_.Movl(as_.rax, arg1);
      as_.Movl(as_.rdx, as_.rax);
      as_.Sarl(as_.rdx, int8_t{31});
      as_.Idivl(arg2);
      as_.Movsxlq(res, opcode == Op32Opcode::kDivw ? as_.rax : as_.rdx);
      break;
    case Op32Opcode::kDivuw:
    case Op32Opcode::kRemuw:
      as_.Movl(as_.rax, arg1);
      as_.Xorl(as_.rdx, as_.rdx);
      as_.Divl(arg2);
      as_.Movsxlq(res, opcode == Op32Opcode::kDivuw ? as_.rax : as_.rdx);
      break;
    default:
      Unimplemented();
      return {};
  }
  return res;
}

Register LiteTranslator::OpImm(Decoder::OpImmOpcode opcode, Register arg, int16_t imm) {
  using OpImmOpcode = Decoder::OpImmOpcode;
  Register res = AllocTempReg();
  switch (opcode) {
    case OpImmOpcode::kAddi:
      as_.Movq(res, arg);
      as_.Addq(res, imm);
      break;
    case OpImmOpcode::kSlti:
      as_.Xorq(res, res);
      as_.Cmpq(arg, imm);
      as_.Setcc(Condition::kLess, res);
      break;
    case OpImmOpcode::kSltiu:
      as_.Xorq(res, res);
      as_.Cmpq(arg, imm);
      as_.Setcc(Condition::kBelow, res);
      break;
    case OpImmOpcode::kXori:
      as_.Movq(res, arg);
      as_.Xorq(res, imm);
      break;
    case OpImmOpcode::kOri:
      as_.Movq(res, arg);
      as_.Orq(res, imm);
      break;
    case OpImmOpcode::kAndi:
      as_.Movq(res, arg);
      as_.Andq(res, imm);
      break;
    default:
      Unimplemented();
      return {};
  }
  return res;
}

Register LiteTranslator::OpImm32(Decoder::OpImm32Opcode opcode, Register arg, int16_t imm) {
  Register res = AllocTempReg();
  switch (opcode) {
    case Decoder::OpImm32Opcode::kAddiw:
      as_.Movl(res, arg);
      as_.Addl(res, imm);
      as_.Movsxlq(res, res);
      break;
    default:
      Unimplemented();
      return {};
  }
  return res;
}

Register LiteTranslator::Slli(Register arg, int8_t imm) {
  Register res = AllocTempReg();
  as_.Movq(res, arg);
  as_.Shlq(res, imm);
  return res;
}

Register LiteTranslator::Srli(Register arg, int8_t imm) {
  Register res = AllocTempReg();
  as_.Movq(res, arg);
  as_.Shrq(res, imm);
  return res;
}

Register LiteTranslator::Srai(Register arg, int8_t imm) {
  Register res = AllocTempReg();
  as_.Movq(res, arg);
  as_.Sarq(res, imm);
  return res;
}

Register LiteTranslator::ShiftImm32(Decoder::ShiftImm32Opcode opcode, Register arg, uint16_t imm) {
  using ShiftImm32Opcode = Decoder::ShiftImm32Opcode;
  Register res = AllocTempReg();
  as_.Movl(res, arg);
  as_.Movl(as_.rcx, imm);
  if (opcode == ShiftImm32Opcode::kSrliw) {
    as_.ShrlByCl(res);
  } else if (opcode == ShiftImm32Opcode::kSlliw) {
    as_.ShllByCl(res);
  } else if (opcode == ShiftImm32Opcode::kSraiw) {
    as_.SarlByCl(res);
  } else {
    Unimplemented();
    return {};
  }
  as_.Movsxlq(res, res);
  return res;
}

Register LiteTranslator::Rori(Register arg, int8_t shamt) {
  Register res = AllocTempReg();
  as_.Movq(res, arg);
  as_.Rorq(res, shamt);
  return res;
}

Register LiteTranslator::Roriw(Register arg, int8_t shamt) {
  Register res = AllocTempReg();
  as_.Movq(res, arg);
  as_.Rorl(res, shamt);
  as_.Movsxlq(res, res);
  return res;
}

Register LiteTranslator::Lui(int32_t imm) {
  Register res = AllocTempReg();
  as_.Movq(res, imm);
  return res;
}

Register LiteTranslator::Auipc(int32_t imm) {
  Register res = GetImm(GetInsnAddr());
  as_.Addq(res, imm);
  return res;
}

void LiteTranslator::CompareAndBranch(Decoder::BranchOpcode opcode,
                                      Register arg1,
                                      Register arg2,
                                      int16_t offset) {
  Assembler::Label* cont = as_.MakeLabel();
  as_.Cmpq(arg1, arg2);
  switch (opcode) {
    case Decoder::BranchOpcode::kBeq:
      as_.Jcc(Condition::kNotEqual, *cont);
      break;
    case Decoder::BranchOpcode::kBne:
      as_.Jcc(Condition::kEqual, *cont);
      break;
    case Decoder::BranchOpcode::kBltu:
      as_.Jcc(Condition::kAboveEqual, *cont);
      break;
    case Decoder::BranchOpcode::kBgeu:
      as_.Jcc(Condition::kBelow, *cont);
      break;
    case Decoder::BranchOpcode::kBlt:
      as_.Jcc(Condition::kGreaterEqual, *cont);
      break;
    case Decoder::BranchOpcode::kBge:
      as_.Jcc(Condition::kLess, *cont);
      break;
    default:
      return Unimplemented();
  }
  ExitRegion(GetInsnAddr() + offset);
  as_.Bind(cont);
}

void LiteTranslator::ExitGeneratedCode(GuestAddr target) {
  StoreMappedRegs();
  // EmitExitGeneratedCode is more efficient if receives target in rax.
  as_.Movq(as_.rax, target);
  EmitExitGeneratedCode(&as_, as_.rax);
}

void LiteTranslator::ExitRegion(GuestAddr target) {
  StoreMappedRegs();
  if (params_.allow_dispatch) {
    EmitDirectDispatch(&as_, target, /* check_pending_signals */ true);
  } else {
    // EmitExitGeneratedCode is more efficient if receives target in rax.
    as_.Movq(as_.rax, target);
    EmitExitGeneratedCode(&as_, as_.rax);
  }
}

void LiteTranslator::ExitRegionIndirect(Register target) {
  StoreMappedRegs();
  if (params_.allow_dispatch) {
    EmitIndirectDispatch(&as_, target);
  } else {
    EmitExitGeneratedCode(&as_, target);
  }
}

void LiteTranslator::Branch(int32_t offset) {
  is_region_end_reached_ = true;
  ExitRegion(GetInsnAddr() + offset);
}

void LiteTranslator::BranchRegister(Register base, int16_t offset) {
  Register res = AllocTempReg();
  as_.Movq(res, base);
  as_.Addq(res, offset);
  // TODO(b/232598137) Maybe move this to translation cache?
  // Zeroing out the last bit.
  as_.Andq(res, ~int32_t{1});
  is_region_end_reached_ = true;
  ExitRegionIndirect(res);
}

Register LiteTranslator::Load(Decoder::LoadOperandType operand_type, Register arg, int16_t offset) {
  AssemblerBase::Label* recovery_label = as_.MakeLabel();
  as_.SetRecoveryPoint(recovery_label);

  Register res = AllocTempReg();
  Assembler::Operand asm_memop{.base = arg, .disp = offset};
  switch (operand_type) {
    case Decoder::LoadOperandType::k8bitUnsigned:
      as_.Movzxbl(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k16bitUnsigned:
      as_.Movzxwl(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k32bitUnsigned:
      as_.Movl(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k64bit:
      as_.Movq(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k8bitSigned:
      as_.Movsxbq(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k16bitSigned:
      as_.Movsxwq(res, asm_memop);
      break;
    case Decoder::LoadOperandType::k32bitSigned:
      as_.Movsxlq(res, asm_memop);
      break;
    default:
      Unimplemented();
      return {};
  }

  // TODO(b/144326673): Emit the recovery code at the end of the region so it doesn't interrupt
  // normal code flow with the jump and doesn't negatively affect instruction cache locality.
  AssemblerBase::Label* cont = as_.MakeLabel();
  as_.Jmp(*cont);
  as_.Bind(recovery_label);
  ExitGeneratedCode(GetInsnAddr());
  as_.Bind(cont);

  return res;
}

void LiteTranslator::Store(Decoder::MemoryDataOperandType operand_type,
                           Register arg,
                           int16_t offset,
                           Register data) {
  AssemblerBase::Label* recovery_label = as_.MakeLabel();
  as_.SetRecoveryPoint(recovery_label);

  Assembler::Operand asm_memop{.base = arg, .disp = offset};
  switch (operand_type) {
    case Decoder::MemoryDataOperandType::k8bit:
      as_.Movb(asm_memop, data);
      break;
    case Decoder::MemoryDataOperandType::k16bit:
      as_.Movw(asm_memop, data);
      break;
    case Decoder::MemoryDataOperandType::k32bit:
      as_.Movl(asm_memop, data);
      break;
    case Decoder::MemoryDataOperandType::k64bit:
      as_.Movq(asm_memop, data);
      break;
    default:
      return Unimplemented();
  }

  // TODO(b/144326673): Emit the recovery code at the end of the region so it doesn't interrupt
  // normal code flow with the jump and doesn't negatively affect instruction cache locality.
  AssemblerBase::Label* cont = as_.MakeLabel();
  as_.Jmp(*cont);
  as_.Bind(recovery_label);
  ExitGeneratedCode(GetInsnAddr());
  as_.Bind(cont);
}

Register LiteTranslator::UpdateCsr(Decoder::CsrOpcode opcode, Register arg, Register csr) {
  Register res = AllocTempReg();
  switch (opcode) {
    case Decoder::CsrOpcode::kCsrrs:
      as_.Movq(res, arg);
      as_.Orq(res, csr);
      break;
    case Decoder::CsrOpcode::kCsrrc:
      if (host_platform::kHasBMI) {
        as_.Andnq(res, arg, csr);
      } else {
        as_.Movq(res, arg);
        as_.Notq(res);
        as_.Andq(res, csr);
      }
      break;
    default:
      Unimplemented();
      return {};
  }
  return arg;
}

Register LiteTranslator::UpdateCsr(Decoder::CsrImmOpcode opcode, uint8_t imm, Register csr) {
  Register res = AllocTempReg();
  switch (opcode) {
    case Decoder::CsrImmOpcode::kCsrrwi:
      as_.Movl(res, imm);
      break;
    case Decoder::CsrImmOpcode::kCsrrsi:
      as_.Movl(res, imm);
      as_.Orq(res, csr);
      break;
    case Decoder::CsrImmOpcode::kCsrrci:
      as_.Movq(res, static_cast<int8_t>(~imm));
      as_.Andq(res, csr);
      break;
    default:
      Unimplemented();
      return {};
  }
  return res;
}

}  // namespace berberis
