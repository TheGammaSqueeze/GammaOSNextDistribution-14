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

#include "assembler_riscv64.h"

#include "base/bit_utils.h"
#include "base/casts.h"
#include "base/logging.h"
#include "base/memory_region.h"

namespace art HIDDEN {
namespace riscv64 {

static_assert(static_cast<size_t>(kRiscv64PointerSize) == kRiscv64DoublewordSize,
              "Unexpected Riscv64 pointer size.");
static_assert(kRiscv64PointerSize == PointerSize::k64, "Unexpected Riscv64 pointer size.");

// Split 32-bit offset into an `imm20` for LUI/AUIPC and
// a signed 12-bit short offset for ADDI/JALR/etc.
ALWAYS_INLINE static inline std::pair<uint32_t, int32_t> SplitOffset(int32_t offset) {
  // The highest 0x800 values are out of range.
  DCHECK_LT(offset, 0x7ffff800);
  // Round `offset` to nearest 4KiB offset because short offset has range [-0x800, 0x800).
  int32_t near_offset = (offset + 0x800) & ~0xfff;
  // Calculate the short offset.
  int32_t short_offset = offset - near_offset;
  DCHECK(IsInt<12>(short_offset));
  // Extract the `imm20`.
  uint32_t imm20 = static_cast<uint32_t>(near_offset) >> 12;
  // Return the result as a pair.
  return std::make_pair(imm20, short_offset);
}

ALWAYS_INLINE static inline int32_t ToInt12(uint32_t uint12) {
  DCHECK(IsUint<12>(uint12));
  return static_cast<int32_t>(uint12 - ((uint12 & 0x800) << 1));
}

void Riscv64Assembler::FinalizeCode() {
  CHECK(!finalized_);
  Assembler::FinalizeCode();
  ReserveJumpTableSpace();
  EmitLiterals();
  PromoteBranches();
  EmitBranches();
  EmitJumpTables();
  PatchCFI();
  finalized_ = true;
}

/////////////////////////////// RV64 VARIANTS extension ///////////////////////////////

//////////////////////////////// RV64 "I" Instructions ////////////////////////////////

// LUI/AUIPC (RV32I, with sign-extension on RV64I), opcode = 0x17, 0x37

void Riscv64Assembler::Lui(XRegister rd, uint32_t imm20) {
  EmitU(imm20, rd, 0x37);
}

void Riscv64Assembler::Auipc(XRegister rd, uint32_t imm20) {
  EmitU(imm20, rd, 0x17);
}

// Jump instructions (RV32I), opcode = 0x67, 0x6f

void Riscv64Assembler::Jal(XRegister rd, int32_t offset) {
  EmitJ(offset, rd, 0x6F);
}

void Riscv64Assembler::Jalr(XRegister rd, XRegister rs1, int32_t offset) {
  EmitI(offset, rs1, 0x0, rd, 0x67);
}

// Branch instructions, opcode = 0x63 (subfunc from 0x0 ~ 0x7), 0x67, 0x6f

void Riscv64Assembler::Beq(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x0, 0x63);
}

void Riscv64Assembler::Bne(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x1, 0x63);
}

void Riscv64Assembler::Blt(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x4, 0x63);
}

void Riscv64Assembler::Bge(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x5, 0x63);
}

void Riscv64Assembler::Bltu(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x6, 0x63);
}

void Riscv64Assembler::Bgeu(XRegister rs1, XRegister rs2, int32_t offset) {
  EmitB(offset, rs2, rs1, 0x7, 0x63);
}

// Load instructions (RV32I+RV64I): opcode = 0x03, funct3 from 0x0 ~ 0x6

void Riscv64Assembler::Lb(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x0, rd, 0x03);
}

void Riscv64Assembler::Lh(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x1, rd, 0x03);
}

void Riscv64Assembler::Lw(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x2, rd, 0x03);
}

void Riscv64Assembler::Ld(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x3, rd, 0x03);
}

void Riscv64Assembler::Lbu(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x4, rd, 0x03);
}

void Riscv64Assembler::Lhu(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x5, rd, 0x03);
}

void Riscv64Assembler::Lwu(XRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitI(offset, rs1, 0x6, rd, 0x3);
}

// Store instructions (RV32I+RV64I): opcode = 0x23, funct3 from 0x0 ~ 0x3

void Riscv64Assembler::Sb(XRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitS(offset, rs2, rs1, 0x0, 0x23);
}

void Riscv64Assembler::Sh(XRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitS(offset, rs2, rs1, 0x1, 0x23);
}

void Riscv64Assembler::Sw(XRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitS(offset, rs2, rs1, 0x2, 0x23);
}

void Riscv64Assembler::Sd(XRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore);
  EmitS(offset, rs2, rs1, 0x3, 0x23);
}

// IMM ALU instructions (RV32I): opcode = 0x13, funct3 from 0x0 ~ 0x7

void Riscv64Assembler::Addi(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x0, rd, 0x13);
}

void Riscv64Assembler::Slti(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x2, rd, 0x13);
}

void Riscv64Assembler::Sltiu(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x3, rd, 0x13);
}

void Riscv64Assembler::Xori(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x4, rd, 0x13);
}

void Riscv64Assembler::Ori(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x6, rd, 0x13);
}

void Riscv64Assembler::Andi(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x7, rd, 0x13);
}

// 0x1 Split: 0x0(6b) + imm12(6b)
void Riscv64Assembler::Slli(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 64u);
  EmitI6(0x0, shamt, rs1, 0x1, rd, 0x13);
}

// 0x5 Split: 0x0(6b) + imm12(6b)
void Riscv64Assembler::Srli(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 64u);
  EmitI6(0x0, shamt, rs1, 0x5, rd, 0x13);
}

// 0x5 Split: 0x10(6b) + imm12(6b)
void Riscv64Assembler::Srai(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 64u);
  EmitI6(0x10, shamt, rs1, 0x5, rd, 0x13);
}

// ALU instructions (RV32I): opcode = 0x33, funct3 from 0x0 ~ 0x7

void Riscv64Assembler::Add(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x0, rd, 0x33);
}

void Riscv64Assembler::Sub(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x20, rs2, rs1, 0x0, rd, 0x33);
}

void Riscv64Assembler::Slt(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x02, rd, 0x33);
}

void Riscv64Assembler::Sltu(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x03, rd, 0x33);
}

void Riscv64Assembler::Xor(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x04, rd, 0x33);
}

void Riscv64Assembler::Or(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x06, rd, 0x33);
}

void Riscv64Assembler::And(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x07, rd, 0x33);
}

void Riscv64Assembler::Sll(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x01, rd, 0x33);
}

void Riscv64Assembler::Srl(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x05, rd, 0x33);
}

void Riscv64Assembler::Sra(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x20, rs2, rs1, 0x05, rd, 0x33);
}

// 32bit Imm ALU instructions (RV64I): opcode = 0x1b, funct3 from 0x0, 0x1, 0x5

void Riscv64Assembler::Addiw(XRegister rd, XRegister rs1, int32_t imm12) {
  EmitI(imm12, rs1, 0x0, rd, 0x1b);
}

void Riscv64Assembler::Slliw(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 32u);
  EmitR(0x0, shamt, rs1, 0x1, rd, 0x1b);
}

void Riscv64Assembler::Srliw(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 32u);
  EmitR(0x0, shamt, rs1, 0x5, rd, 0x1b);
}

void Riscv64Assembler::Sraiw(XRegister rd, XRegister rs1, int32_t shamt) {
  CHECK_LT(static_cast<uint32_t>(shamt), 32u);
  EmitR(0x20, shamt, rs1, 0x5, rd, 0x1b);
}

// 32bit ALU instructions (RV64I): opcode = 0x3b, funct3 from 0x0 ~ 0x7

void Riscv64Assembler::Addw(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x0, rd, 0x3b);
}

void Riscv64Assembler::Subw(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x20, rs2, rs1, 0x0, rd, 0x3b);
}

void Riscv64Assembler::Sllw(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x1, rd, 0x3b);
}

void Riscv64Assembler::Srlw(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x0, rs2, rs1, 0x5, rd, 0x3b);
}

void Riscv64Assembler::Sraw(XRegister rd, XRegister rs1, XRegister rs2) {
  EmitR(0x20, rs2, rs1, 0x5, rd, 0x3b);
}

// Environment call and breakpoint (RV32I), opcode = 0x73

void Riscv64Assembler::Ecall() { EmitI(0x0, 0x0, 0x0, 0x0, 0x73); }

void Riscv64Assembler::Ebreak() { EmitI(0x1, 0x0, 0x0, 0x0, 0x73); }

// Fence instruction (RV32I): opcode = 0xf, funct3 = 0

void Riscv64Assembler::Fence(uint32_t pred, uint32_t succ) {
  DCHECK(IsUint<4>(pred));
  DCHECK(IsUint<4>(succ));
  EmitI(/* normal fence */ 0x0 << 8 | pred << 4 | succ, 0x0, 0x0, 0x0, 0xf);
}

void Riscv64Assembler::FenceTso() {
  static constexpr uint32_t kPred = kFenceWrite | kFenceRead;
  static constexpr uint32_t kSucc = kFenceWrite | kFenceRead;
  EmitI(ToInt12(/* TSO fence */ 0x8 << 8 | kPred << 4 | kSucc), 0x0, 0x0, 0x0, 0xf);
}

//////////////////////////////// RV64 "I" Instructions  END ////////////////////////////////

/////////////////////////// RV64 "Zifencei" Instructions  START ////////////////////////////

// "Zifencei" Standard Extension, opcode = 0xf, funct3 = 1
void Riscv64Assembler::FenceI() {
  AssertExtensionsEnabled(Riscv64Extension::kZifencei);
  EmitI(0x0, 0x0, 0x1, 0x0, 0xf);
}

//////////////////////////// RV64 "Zifencei" Instructions  END /////////////////////////////

/////////////////////////////// RV64 "M" Instructions  START ///////////////////////////////

// RV32M Standard Extension: opcode = 0x33, funct3 from 0x0 ~ 0x7

void Riscv64Assembler::Mul(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x0, rd, 0x33);
}

void Riscv64Assembler::Mulh(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x1, rd, 0x33);
}

void Riscv64Assembler::Mulhsu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x2, rd, 0x33);
}

void Riscv64Assembler::Mulhu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x3, rd, 0x33);
}

void Riscv64Assembler::Div(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x4, rd, 0x33);
}

void Riscv64Assembler::Divu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x5, rd, 0x33);
}

void Riscv64Assembler::Rem(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x6, rd, 0x33);
}

void Riscv64Assembler::Remu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x7, rd, 0x33);
}

// RV64M Standard Extension: opcode = 0x3b, funct3 0x0 and from 0x4 ~ 0x7

void Riscv64Assembler::Mulw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x0, rd, 0x3b);
}

void Riscv64Assembler::Divw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x4, rd, 0x3b);
}

void Riscv64Assembler::Divuw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x5, rd, 0x3b);
}

void Riscv64Assembler::Remw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x6, rd, 0x3b);
}

void Riscv64Assembler::Remuw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kM);
  EmitR(0x1, rs2, rs1, 0x7, rd, 0x3b);
}

//////////////////////////////// RV64 "M" Instructions  END ////////////////////////////////

/////////////////////////////// RV64 "A" Instructions  START ///////////////////////////////

void Riscv64Assembler::LrW(XRegister rd, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  CHECK(aqrl != AqRl::kRelease);
  EmitR4(0x2, enum_cast<uint32_t>(aqrl), 0x0, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::LrD(XRegister rd, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  CHECK(aqrl != AqRl::kRelease);
  EmitR4(0x2, enum_cast<uint32_t>(aqrl), 0x0, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::ScW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  CHECK(aqrl != AqRl::kAcquire);
  EmitR4(0x3, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::ScD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  CHECK(aqrl != AqRl::kAcquire);
  EmitR4(0x3, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoSwapW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x1, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoSwapD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x1, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoAddW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x0, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoAddD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x0, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoXorW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x4, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoXorD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x4, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoAndW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0xc, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoAndD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0xc, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoOrW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x8, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoOrD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x8, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoMinW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x10, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoMinD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x10, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoMaxW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x14, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoMaxD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x14, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoMinuW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x18, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoMinuD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x18, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

void Riscv64Assembler::AmoMaxuW(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x1c, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x2, rd, 0x2f);
}

void Riscv64Assembler::AmoMaxuD(XRegister rd, XRegister rs2, XRegister rs1, AqRl aqrl) {
  AssertExtensionsEnabled(Riscv64Extension::kA);
  EmitR4(0x1c, enum_cast<uint32_t>(aqrl), rs2, rs1, 0x3, rd, 0x2f);
}

/////////////////////////////// RV64 "A" Instructions  END ///////////////////////////////

///////////////////////////// RV64 "Zicsr" Instructions  START /////////////////////////////

// "Zicsr" Standard Extension, opcode = 0x73, funct3 from 0x1 ~ 0x3 and 0x5 ~ 0x7

void Riscv64Assembler::Csrrw(XRegister rd, uint32_t csr, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), rs1, 0x1, rd, 0x73);
}

void Riscv64Assembler::Csrrs(XRegister rd, uint32_t csr, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), rs1, 0x2, rd, 0x73);
}

void Riscv64Assembler::Csrrc(XRegister rd, uint32_t csr, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), rs1, 0x3, rd, 0x73);
}

void Riscv64Assembler::Csrrwi(XRegister rd, uint32_t csr, uint32_t uimm5) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), uimm5, 0x5, rd, 0x73);
}

void Riscv64Assembler::Csrrsi(XRegister rd, uint32_t csr, uint32_t uimm5) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), uimm5, 0x6, rd, 0x73);
}

void Riscv64Assembler::Csrrci(XRegister rd, uint32_t csr, uint32_t uimm5) {
  AssertExtensionsEnabled(Riscv64Extension::kZicsr);
  EmitI(ToInt12(csr), uimm5, 0x7, rd, 0x73);
}

////////////////////////////// RV64 "Zicsr" Instructions  END //////////////////////////////

/////////////////////////////// RV64 "FD" Instructions  START ///////////////////////////////

// FP load/store instructions (RV32F+RV32D): opcode = 0x07, 0x27

void Riscv64Assembler::FLw(FRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kF);
  EmitI(offset, rs1, 0x2, rd, 0x07);
}

void Riscv64Assembler::FLd(FRegister rd, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kD);
  EmitI(offset, rs1, 0x3, rd, 0x07);
}

void Riscv64Assembler::FSw(FRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kF);
  EmitS(offset, rs2, rs1, 0x2, 0x27);
}

void Riscv64Assembler::FSd(FRegister rs2, XRegister rs1, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kD);
  EmitS(offset, rs2, rs1, 0x3, 0x27);
}

// FP FMA instructions (RV32F+RV32D): opcode = 0x43, 0x47, 0x4b, 0x4f

void Riscv64Assembler::FMAddS(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR4(rs3, 0x0, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x43);
}

void Riscv64Assembler::FMAddD(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR4(rs3, 0x1, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x43);
}

void Riscv64Assembler::FMSubS(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR4(rs3, 0x0, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x47);
}

void Riscv64Assembler::FMSubD(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR4(rs3, 0x1, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x47);
}

void Riscv64Assembler::FNMSubS(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR4(rs3, 0x0, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x4b);
}

void Riscv64Assembler::FNMSubD(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR4(rs3, 0x1, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x4b);
}

void Riscv64Assembler::FNMAddS(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR4(rs3, 0x0, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x4f);
}

void Riscv64Assembler::FNMAddD(
    FRegister rd, FRegister rs1, FRegister rs2, FRegister rs3, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR4(rs3, 0x1, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x4f);
}

// Simple FP instructions (RV32F+RV32D): opcode = 0x53, funct7 = 0b0XXXX0D

void Riscv64Assembler::FAddS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x0, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FAddD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x1, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FSubS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x4, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FSubD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x5, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FMulS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x8, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FMulD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x9, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FDivS(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0xc, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FDivD(FRegister rd, FRegister rs1, FRegister rs2, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0xd, rs2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FSqrtS(FRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x2c, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FSqrtD(FRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x2d, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FSgnjS(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x10, rs2, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FSgnjD(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x11, rs2, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FSgnjnS(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x10, rs2, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FSgnjnD(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x11, rs2, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FSgnjxS(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x10, rs2, rs1, 0x2, rd, 0x53);
}

void Riscv64Assembler::FSgnjxD(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x11, rs2, rs1, 0x2, rd, 0x53);
}

void Riscv64Assembler::FMinS(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x14, rs2, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FMinD(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x15, rs2, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FMaxS(FRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x14, rs2, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FMaxD(FRegister rd, FRegister rs1, FRegister rs2) {
  EmitR(0x15, rs2, rs1, 0x1, rd, 0x53);
  AssertExtensionsEnabled(Riscv64Extension::kD);
}

void Riscv64Assembler::FCvtSD(FRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF, Riscv64Extension::kD);
  EmitR(0x20, 0x1, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtDS(FRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF, Riscv64Extension::kD);
  // Note: The `frm` is useless, the result can represent every value of the source exactly.
  EmitR(0x21, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

// FP compare instructions (RV32F+RV32D): opcode = 0x53, funct7 = 0b101000D

void Riscv64Assembler::FEqS(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x50, rs2, rs1, 0x2, rd, 0x53);
}

void Riscv64Assembler::FEqD(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x51, rs2, rs1, 0x2, rd, 0x53);
}

void Riscv64Assembler::FLtS(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x50, rs2, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FLtD(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x51, rs2, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FLeS(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x50, rs2, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FLeD(XRegister rd, FRegister rs1, FRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x51, rs2, rs1, 0x0, rd, 0x53);
}

// FP conversion instructions (RV32F+RV32D+RV64F+RV64D): opcode = 0x53, funct7 = 0b110X00D

void Riscv64Assembler::FCvtWS(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x60, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtWD(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x61, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtWuS(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x60, 0x1, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtWuD(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x61, 0x1, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtLS(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x60, 0x2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtLD(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x61, 0x2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtLuS(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x60, 0x3, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtLuD(XRegister rd, FRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x61, 0x3, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtSW(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x68, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtDW(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  // Note: The `frm` is useless, the result can represent every value of the source exactly.
  EmitR(0x69, 0x0, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtSWu(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x68, 0x1, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtDWu(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  // Note: The `frm` is useless, the result can represent every value of the source exactly.
  EmitR(0x69, 0x1, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtSL(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x68, 0x2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtDL(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x69, 0x2, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtSLu(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x68, 0x3, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

void Riscv64Assembler::FCvtDLu(FRegister rd, XRegister rs1, FPRoundingMode frm) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x69, 0x3, rs1, enum_cast<uint32_t>(frm), rd, 0x53);
}

// FP move instructions (RV32F+RV32D): opcode = 0x53, funct3 = 0x0, funct7 = 0b111X00D

void Riscv64Assembler::FMvXW(XRegister rd, FRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x70, 0x0, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FMvXD(XRegister rd, FRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x71, 0x0, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FMvWX(FRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x78, 0x0, rs1, 0x0, rd, 0x53);
}

void Riscv64Assembler::FMvDX(FRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x79, 0x0, rs1, 0x0, rd, 0x53);
}

// FP classify instructions (RV32F+RV32D): opcode = 0x53, funct3 = 0x1, funct7 = 0b111X00D

void Riscv64Assembler::FClassS(XRegister rd, FRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kF);
  EmitR(0x70, 0x0, rs1, 0x1, rd, 0x53);
}

void Riscv64Assembler::FClassD(XRegister rd, FRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kD);
  EmitR(0x71, 0x0, rs1, 0x1, rd, 0x53);
}

/////////////////////////////// RV64 "FD" Instructions  END ///////////////////////////////

/////////////////////////////// RV64 "C" Instructions  START /////////////////////////////

void Riscv64Assembler::CLwsp(XRegister rd, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  EmitCI(0b010u, rd, ExtractOffset52_76(offset), 0b10u);
}

void Riscv64Assembler::CLdsp(XRegister rd, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  EmitCI(0b011u, rd, ExtractOffset53_86(offset), 0b10u);
}

void Riscv64Assembler::CFLdsp(FRegister rd, int32_t offset) {
  AssertExtensionsEnabled(
      Riscv64Extension::kLoadStore, Riscv64Extension::kZcd, Riscv64Extension::kD);
  EmitCI(0b001u, rd, ExtractOffset53_86(offset), 0b10u);
}

void Riscv64Assembler::CSwsp(XRegister rs2, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCSS(0b110u, ExtractOffset52_76(offset), rs2, 0b10u);
}

void Riscv64Assembler::CSdsp(XRegister rs2, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCSS(0b111u, ExtractOffset53_86(offset), rs2, 0b10u);
}

void Riscv64Assembler::CFSdsp(FRegister rs2, int32_t offset) {
  AssertExtensionsEnabled(
      Riscv64Extension::kLoadStore, Riscv64Extension::kZcd, Riscv64Extension::kD);
  EmitCSS(0b101u, ExtractOffset53_86(offset), rs2, 0b10u);
}

void Riscv64Assembler::CLw(XRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCM(0b010u, ExtractOffset52_6(offset), rs1_s, rd_s, 0b00u);
}

void Riscv64Assembler::CLd(XRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCM(0b011u, ExtractOffset53_76(offset), rs1_s, rd_s, 0b00u);
}

void Riscv64Assembler::CFLd(FRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(
      Riscv64Extension::kLoadStore, Riscv64Extension::kZcd, Riscv64Extension::kD);
  EmitCM(0b001u, ExtractOffset53_76(offset), rs1_s, rd_s, 0b00u);
}

void Riscv64Assembler::CSw(XRegister rs2_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCM(0b110u, ExtractOffset52_6(offset), rs1_s, rs2_s, 0b00u);
}

void Riscv64Assembler::CSd(XRegister rs2_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZca);
  EmitCM(0b111u, ExtractOffset53_76(offset), rs1_s, rs2_s, 0b00u);
}

void Riscv64Assembler::CFSd(FRegister rs2_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(
      Riscv64Extension::kLoadStore, Riscv64Extension::kZcd, Riscv64Extension::kD);
  EmitCM(0b101u, ExtractOffset53_76(offset), rs1_s, rs2_s, 0b00u);
}

void Riscv64Assembler::CLi(XRegister rd, int32_t imm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  DCHECK(IsInt<6>(imm));
  EmitCI(0b010u, rd, EncodeInt6(imm), 0b01u);
}

void Riscv64Assembler::CLui(XRegister rd, uint32_t nzimm6) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  DCHECK_NE(rd, SP);
  DCHECK(IsImmCLuiEncodable(nzimm6));
  EmitCI(0b011u, rd, nzimm6 & MaskLeastSignificant<uint32_t>(6), 0b01u);
}

void Riscv64Assembler::CAddi(XRegister rd, int32_t nzimm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  DCHECK_NE(nzimm, 0);
  EmitCI(0b000u, rd, EncodeInt6(nzimm), 0b01u);
}

void Riscv64Assembler::CAddiw(XRegister rd, int32_t imm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  EmitCI(0b001u, rd, EncodeInt6(imm), 0b01u);
}

void Riscv64Assembler::CAddi16Sp(int32_t nzimm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(nzimm, 0);
  DCHECK(IsAligned<16>(nzimm));

  uint32_t unzimm = static_cast<uint32_t>(nzimm);

  // nzimm[9]
  uint32_t imms1 =  BitFieldExtract(unzimm, 9, 1);
  // nzimm[4|6|8:7|5]
  uint32_t imms0 = (BitFieldExtract(unzimm, 4, 1) << 4) |
                   (BitFieldExtract(unzimm, 6, 1) << 3) |
                   (BitFieldExtract(unzimm, 7, 2) << 1) |
                    BitFieldExtract(unzimm, 5, 1);

  EmitCI(0b011u, SP, BitFieldInsert(imms0, imms1, 5, 1), 0b01u);
}

void Riscv64Assembler::CAddi4Spn(XRegister rd_s, uint32_t nzuimm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(nzuimm, 0u);
  DCHECK(IsAligned<4>(nzuimm));
  DCHECK(IsUint<10>(nzuimm));

  // nzuimm[5:4|9:6|2|3]
  uint32_t uimm = (BitFieldExtract(nzuimm, 4, 2) << 6) |
                  (BitFieldExtract(nzuimm, 6, 4) << 2) |
                  (BitFieldExtract(nzuimm, 2, 1) << 1) |
                   BitFieldExtract(nzuimm, 3, 1);

  EmitCIW(0b000u, uimm, rd_s, 0b00u);
}

void Riscv64Assembler::CSlli(XRegister rd, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(shamt, 0);
  DCHECK_NE(rd, Zero);
  EmitCI(0b000u, rd, shamt, 0b10u);
}

void Riscv64Assembler::CSrli(XRegister rd_s, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(shamt, 0);
  DCHECK(IsUint<6>(shamt));
  EmitCBArithmetic(0b100u, 0b00u, shamt, rd_s, 0b01u);
}

void Riscv64Assembler::CSrai(XRegister rd_s, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(shamt, 0);
  DCHECK(IsUint<6>(shamt));
  EmitCBArithmetic(0b100u, 0b01u, shamt, rd_s, 0b01u);
}

void Riscv64Assembler::CAndi(XRegister rd_s, int32_t imm) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK(IsInt<6>(imm));
  EmitCBArithmetic(0b100u, 0b10u, imm, rd_s, 0b01u);
}

void Riscv64Assembler::CMv(XRegister rd, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  DCHECK_NE(rs2, Zero);
  EmitCR(0b1000u, rd, rs2, 0b10u);
}

void Riscv64Assembler::CAdd(XRegister rd, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rd, Zero);
  DCHECK_NE(rs2, Zero);
  EmitCR(0b1001u, rd, rs2, 0b10u);
}

void Riscv64Assembler::CAnd(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100011u, rd_s, 0b11u, rs2_s, 0b01u);
}

void Riscv64Assembler::COr(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100011u, rd_s, 0b10u, rs2_s, 0b01u);
}

void Riscv64Assembler::CXor(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100011u, rd_s, 0b01u, rs2_s, 0b01u);
}

void Riscv64Assembler::CSub(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100011u, rd_s, 0b00u, rs2_s, 0b01u);
}

void Riscv64Assembler::CAddw(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100111u, rd_s, 0b01u, rs2_s, 0b01u);
}

void Riscv64Assembler::CSubw(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCAReg(0b100111u, rd_s, 0b00u, rs2_s, 0b01u);
}

// "Zcb" Standard Extension, part of "C", opcode = 0b00, 0b01, funct3 = 0b100.

void Riscv64Assembler::CLbu(XRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZcb);
  EmitCAReg(0b100000u, rs1_s, EncodeOffset0_1(offset), rd_s, 0b00u);
}

void Riscv64Assembler::CLhu(XRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZcb);
  DCHECK(IsUint<2>(offset));
  DCHECK_ALIGNED(offset, 2);
  EmitCAReg(0b100001u, rs1_s, BitFieldExtract<uint32_t>(offset, 1, 1), rd_s, 0b00u);
}

void Riscv64Assembler::CLh(XRegister rd_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZcb);
  DCHECK(IsUint<2>(offset));
  DCHECK_ALIGNED(offset, 2);
  EmitCAReg(0b100001u, rs1_s, 0b10 | BitFieldExtract<uint32_t>(offset, 1, 1), rd_s, 0b00u);
}

void Riscv64Assembler::CSb(XRegister rs2_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZcb);
  EmitCAReg(0b100010u, rs1_s, EncodeOffset0_1(offset), rs2_s, 0b00u);
}

void Riscv64Assembler::CSh(XRegister rs2_s, XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kZcb);
  DCHECK(IsUint<2>(offset));
  DCHECK_ALIGNED(offset, 2);
  EmitCAReg(0b100011u, rs1_s, BitFieldExtract<uint32_t>(offset, 1, 1), rs2_s, 0b00u);
}

void Riscv64Assembler::CZextB(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b000u, 0b01u);
}

void Riscv64Assembler::CSextB(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb, Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b001u, 0b01u);
}

void Riscv64Assembler::CZextH(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb, Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b010u, 0b01u);
}

void Riscv64Assembler::CSextH(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb, Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b011u, 0b01u);
}

void Riscv64Assembler::CZextW(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZba, Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b100u, 0b01u);
}

void Riscv64Assembler::CNot(XRegister rd_rs1_s) {
  AssertExtensionsEnabled(Riscv64Extension::kZcb);
  EmitCAImm(0b100111u, rd_rs1_s, 0b11u, 0b101u, 0b01u);
}

void Riscv64Assembler::CMul(XRegister rd_s, XRegister rs2_s) {
  AssertExtensionsEnabled(Riscv64Extension::kM, Riscv64Extension::kZcb);
  EmitCAReg(0b100111u, rd_s, 0b10u, rs2_s, 0b01u);
}

void Riscv64Assembler::CJ(int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCJ(0b101u, offset, 0b01u);
}

void Riscv64Assembler::CJr(XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rs1, Zero);
  EmitCR(0b1000u, rs1, Zero, 0b10u);
}

void Riscv64Assembler::CJalr(XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  DCHECK_NE(rs1, Zero);
  EmitCR(0b1001u, rs1, Zero, 0b10u);
}

void Riscv64Assembler::CBeqz(XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCBBranch(0b110u, offset, rs1_s, 0b01u);
}

void Riscv64Assembler::CBnez(XRegister rs1_s, int32_t offset) {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCBBranch(0b111u, offset, rs1_s, 0b01u);
}

void Riscv64Assembler::CEbreak() {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCR(0b1001u, Zero, Zero, 0b10u);
}

void Riscv64Assembler::CNop() {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  EmitCI(0b000u, Zero, 0u, 0b01u);
}

void Riscv64Assembler::CUnimp() {
  AssertExtensionsEnabled(Riscv64Extension::kZca);
  Emit16(0x0u);
}

/////////////////////////////// RV64 "C" Instructions  END ///////////////////////////////

////////////////////////////// RV64 "Zba" Instructions  START /////////////////////////////

void Riscv64Assembler::AddUw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x4, rs2, rs1, 0x0, rd, 0x3b);
}

void Riscv64Assembler::Sh1Add(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x2, rd, 0x33);
}

void Riscv64Assembler::Sh1AddUw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x2, rd, 0x3b);
}

void Riscv64Assembler::Sh2Add(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x4, rd, 0x33);
}

void Riscv64Assembler::Sh2AddUw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x4, rd, 0x3b);
}

void Riscv64Assembler::Sh3Add(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x6, rd, 0x33);
}

void Riscv64Assembler::Sh3AddUw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitR(0x10, rs2, rs1, 0x6, rd, 0x3b);
}

void Riscv64Assembler::SlliUw(XRegister rd, XRegister rs1, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZba);
  EmitI6(0x2, shamt, rs1, 0x1, rd, 0x1b);
}

/////////////////////////////// RV64 "Zba" Instructions  END //////////////////////////////

////////////////////////////// RV64 "Zbb" Instructions  START /////////////////////////////

void Riscv64Assembler::Andn(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x20, rs2, rs1, 0x7, rd, 0x33);
}

void Riscv64Assembler::Orn(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x20, rs2, rs1, 0x6, rd, 0x33);
}

void Riscv64Assembler::Xnor(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x20, rs2, rs1, 0x4, rd, 0x33);
}

void Riscv64Assembler::Clz(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x0, rs1, 0x1, rd, 0x13);
}

void Riscv64Assembler::Clzw(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x0, rs1, 0x1, rd, 0x1b);
}

void Riscv64Assembler::Ctz(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x1, rs1, 0x1, rd, 0x13);
}

void Riscv64Assembler::Ctzw(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x1, rs1, 0x1, rd, 0x1b);
}

void Riscv64Assembler::Cpop(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x2, rs1, 0x1, rd, 0x13);
}

void Riscv64Assembler::Cpopw(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x2, rs1, 0x1, rd, 0x1b);
}

void Riscv64Assembler::Min(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x5, rs2, rs1, 0x4, rd, 0x33);
}

void Riscv64Assembler::Minu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x5, rs2, rs1, 0x5, rd, 0x33);
}

void Riscv64Assembler::Max(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x5, rs2, rs1, 0x6, rd, 0x33);
}

void Riscv64Assembler::Maxu(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x5, rs2, rs1, 0x7, rd, 0x33);
}

void Riscv64Assembler::Rol(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, rs2, rs1, 0x1, rd, 0x33);
}

void Riscv64Assembler::Rolw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, rs2, rs1, 0x1, rd, 0x3b);
}

void Riscv64Assembler::Ror(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, rs2, rs1, 0x5, rd, 0x33);
}

void Riscv64Assembler::Rorw(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, rs2, rs1, 0x5, rd, 0x3b);
}

void Riscv64Assembler::Rori(XRegister rd, XRegister rs1, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  CHECK_LT(static_cast<uint32_t>(shamt), 64u);
  EmitI6(0x18, shamt, rs1, 0x5, rd, 0x13);
}

void Riscv64Assembler::Roriw(XRegister rd, XRegister rs1, int32_t shamt) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  CHECK_LT(static_cast<uint32_t>(shamt), 32u);
  EmitI6(0x18, shamt, rs1, 0x5, rd, 0x1b);
}

void Riscv64Assembler::OrcB(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x14, 0x7, rs1, 0x5, rd, 0x13);
}

void Riscv64Assembler::Rev8(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x35, 0x18, rs1, 0x5, rd, 0x13);
}

void Riscv64Assembler::ZbbSextB(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x4, rs1, 0x1, rd, 0x13);
}

void Riscv64Assembler::ZbbSextH(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x30, 0x5, rs1, 0x1, rd, 0x13);
}

void Riscv64Assembler::ZbbZextH(XRegister rd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kZbb);
  EmitR(0x4, 0x0, rs1, 0x4, rd, 0x3b);
}

/////////////////////////////// RV64 "Zbb" Instructions  END //////////////////////////////

/////////////////////////////// RVV "VSet" Instructions  START ////////////////////////////

void Riscv64Assembler::VSetvli(XRegister rd, XRegister rs1, uint32_t vtypei) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(IsUint<11>(vtypei));
  EmitI(vtypei, rs1, enum_cast<uint32_t>(VAIEncoding::kOPCFG), rd, 0x57);
}

void Riscv64Assembler::VSetivli(XRegister rd, uint32_t uimm, uint32_t vtypei) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(IsUint<10>(vtypei));
  DCHECK(IsUint<5>(uimm));
  EmitI((~0U << 10 | vtypei), uimm, enum_cast<uint32_t>(VAIEncoding::kOPCFG), rd, 0x57);
}

void Riscv64Assembler::VSetvl(XRegister rd, XRegister rs1, XRegister rs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  EmitR(0x40, rs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPCFG), rd, 0x57);
}

/////////////////////////////// RVV "VSet" Instructions  END //////////////////////////////

/////////////////////////////// RVV Load/Store Instructions  START ////////////////////////////

void Riscv64Assembler::VLe8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLe16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLe32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLe64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSe8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSe16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSe32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSe64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLm(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01011, rs1, enum_cast<uint32_t>(VectorWidth::kMask), vd, 0x7);
}

void Riscv64Assembler::VSm(VRegister vs3, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01011, rs1, enum_cast<uint32_t>(VectorWidth::kMask), vs3, 0x27);
}

void Riscv64Assembler::VLe8ff(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b10000, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLe16ff(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b10000, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLe32ff(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b10000, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLe64ff(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b10000, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLse8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLse16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLse32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLse64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSse8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSse16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSse32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSse64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLoxei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSoxei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLseg2e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg2e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg2e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg2e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg3e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg3e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg3e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg3e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg4e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg4e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg4e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg4e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg5e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg5e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg5e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg5e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg6e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg6e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg6e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg6e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg7e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg7e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg7e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg7e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg8e8(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg8e16(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg8e32(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg8e64(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSseg2e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg2e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg2e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg2e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg3e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg3e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg3e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg3e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg4e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg4e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg4e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg4e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg5e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg5e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg5e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg5e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg6e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg6e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg6e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg6e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg7e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg7e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg7e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg7e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSseg8e8(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSseg8e16(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSseg8e32(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSseg8e64(VRegister vs3, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b00000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLseg2e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg2e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg2e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg2e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg3e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg3e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg3e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg3e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg4e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg4e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg4e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg4e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg5e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg5e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg5e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg5e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg6e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg6e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg6e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg6e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg7e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg7e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg7e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg7e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLseg8e8ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLseg8e16ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLseg8e32ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLseg8e64ff(VRegister vd, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, vm);
  EmitR(funct7, 0b10000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg2e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg2e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg2e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg2e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg3e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg3e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg3e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg3e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg4e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg4e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg4e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg4e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg5e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg5e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg5e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg5e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg6e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg6e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg6e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg6e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg7e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg7e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg7e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg7e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLsseg8e8(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLsseg8e16(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLsseg8e32(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLsseg8e64(VRegister vd, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSsseg2e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg2e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg2e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg2e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg3e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg3e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg3e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg3e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg4e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg4e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg4e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg4e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg5e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg5e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg5e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg5e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg6e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg6e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg6e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg6e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg7e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg7e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg7e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg7e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSsseg8e8(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSsseg8e16(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSsseg8e32(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSsseg8e64(VRegister vs3, XRegister rs1, XRegister rs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kStrided, vm);
  EmitR(funct7, rs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLuxseg2ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg2ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg2ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg2ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg3ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg3ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg3ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg3ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg4ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg4ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg4ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg4ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg5ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg5ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg5ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg5ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg6ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg6ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg6ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg6ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg7ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg7ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg7ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg7ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLuxseg8ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLuxseg8ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLuxseg8ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLuxseg8ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSuxseg2ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg2ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg2ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg2ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg3ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg3ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg3ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg3ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg4ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg4ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg4ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg4ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg5ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg5ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg5ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg5ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg6ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg6ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg6ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg6ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg7ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg7ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg7ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg7ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg8ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg8ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg8ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSuxseg8ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedUnordered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VLoxseg2ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg2ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg2ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg2ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg3ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg3ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg3ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg3ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg4ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg4ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg4ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg4ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg5ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg5ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg5ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg5ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg6ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg6ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg6ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg6ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg7ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg7ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg7ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg7ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VLoxseg8ei8(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VLoxseg8ei16(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VLoxseg8ei32(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VLoxseg8ei64(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VSoxseg2ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg2ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg2ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg2ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg3ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg3ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg3ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg3ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k3, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg4ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg4ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg4ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg4ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg5ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg5ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg5ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg5ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k5, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg6ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg6ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg6ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg6ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k6, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg7ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg7ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg7ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg7ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k7, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg8ei8(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k8), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg8ei16(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k16), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg8ei32(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k32), vs3, 0x27);
}

void Riscv64Assembler::VSoxseg8ei64(VRegister vs3, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kIndexedOrdered, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VectorWidth::k64), vs3, 0x27);
}

void Riscv64Assembler::VL1re8(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VL1re16(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VL1re32(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VL1re64(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VL2re8(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 2), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VL2re16(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 2), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VL2re32(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 2), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VL2re64(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 2), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VL4re8(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 4), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VL4re16(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 4), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VL4re32(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 4), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VL4re64(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 4), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VL8re8(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 8), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k8), vd, 0x7);
}

void Riscv64Assembler::VL8re16(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 8), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k16), vd, 0x7);
}

void Riscv64Assembler::VL8re32(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 8), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k32), vd, 0x7);
}

void Riscv64Assembler::VL8re64(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  DCHECK_EQ((enum_cast<uint32_t>(vd) % 8), 0U);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::k64), vd, 0x7);
}

void Riscv64Assembler::VL1r(VRegister vd, XRegister rs1) { VL1re8(vd, rs1); }

void Riscv64Assembler::VL2r(VRegister vd, XRegister rs1) { VL2re8(vd, rs1); }

void Riscv64Assembler::VL4r(VRegister vd, XRegister rs1) { VL4re8(vd, rs1); }

void Riscv64Assembler::VL8r(VRegister vd, XRegister rs1) { VL8re8(vd, rs1); }

void Riscv64Assembler::VS1r(VRegister vs3, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k1, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::kWholeR), vs3, 0x27);
}

void Riscv64Assembler::VS2r(VRegister vs3, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k2, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::kWholeR), vs3, 0x27);
}

void Riscv64Assembler::VS4r(VRegister vs3, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k4, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::kWholeR), vs3, 0x27);
}

void Riscv64Assembler::VS8r(VRegister vs3, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kLoadStore, Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVMemF7(Nf::k8, 0x0, MemAddressMode::kUnitStride, VM::kUnmasked);
  EmitR(funct7, 0b01000u, rs1, enum_cast<uint32_t>(VectorWidth::kWholeR), vs3, 0x27);
}

/////////////////////////////// RVV Load/Store Instructions  END //////////////////////////////

/////////////////////////////// RVV Arithmetic Instructions  START ////////////////////////////

void Riscv64Assembler::VAdd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VAdd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VAdd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VRsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VRsub_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000011, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNeg_v(VRegister vd, VRegister vs2) { VRsub_vx(vd, vs2, Zero); }

void Riscv64Assembler::VMinu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMinu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMin_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMin_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMaxu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMaxu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMax_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMax_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VAnd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VAnd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VAnd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VOr_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VOr_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VOr_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VXor_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VXor_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VXor_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001011, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNot_v(VRegister vd, VRegister vs2, VM vm) { VXor_vi(vd, vs2, -1, vm); }

void Riscv64Assembler::VRgather_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VRgather_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VRgather_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001100, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSlideup_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSlideup_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001110, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VRgatherei16_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSlidedown_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSlidedown_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001111, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VAdc_vvm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kV0_t);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VAdc_vxm(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kV0_t);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VAdc_vim(VRegister vd, VRegister vs2, int32_t imm5) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kV0_t);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMadc_vvm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kV0_t);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMadc_vxm(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kV0_t);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMadc_vim(VRegister vd, VRegister vs2, int32_t imm5) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kV0_t);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMadc_vv(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMadc_vx(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kUnmasked);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMadc_vi(VRegister vd, VRegister vs2, int32_t imm5) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010001, VM::kUnmasked);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSbc_vvm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, VM::kV0_t);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSbc_vxm(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, VM::kV0_t);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsbc_vvm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010011, VM::kV0_t);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsbc_vxm(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010011, VM::kV0_t);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsbc_vv(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010011, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsbc_vx(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010011, VM::kUnmasked);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMerge_vvm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kV0_t);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMerge_vxm(VRegister vd, VRegister vs2, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kV0_t);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMerge_vim(VRegister vd, VRegister vs2, int32_t imm5) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kV0_t);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMv_vv(VRegister vd, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kUnmasked);
  EmitR(funct7, V0, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMv_vx(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kUnmasked);
  EmitR(funct7, V0, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMv_vi(VRegister vd, int32_t imm5) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kUnmasked);
  EmitR(funct7, V0, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMseq_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMseq_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMseq_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011000, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsne_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsne_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsne_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011001, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsltu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsltu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsgtu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  VMsltu_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMslt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMslt_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsgt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  VMslt_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMsleu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsleu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsleu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011100, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsgeu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  VMsleu_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMsltu_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm) {
  CHECK(IsUint<4>(aimm5 - 1)) << "Should be between [1, 16]" << aimm5;
  VMsleu_vi(vd, vs2, aimm5 - 1, vm);
}

void Riscv64Assembler::VMsle_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VMsle_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsle_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011101, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsge_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  VMsle_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMslt_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm) {
  VMsle_vi(vd, vs2, aimm5 - 1, vm);
}

void Riscv64Assembler::VMsgtu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsgtu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011110, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsgeu_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  CHECK(IsUint<4>(aimm5 - 1)) << "Should be between [1, 16]" << aimm5;
  VMsgtu_vi(vd, vs2, aimm5 - 1, vm);
}

void Riscv64Assembler::VMsgt_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VMsgt_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011111, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VMsge_vi(VRegister vd, VRegister vs2, int32_t aimm5, VM vm) {
  VMsgt_vi(vd, vs2, aimm5 - 1, vm);
}

void Riscv64Assembler::VSaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSaddu_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSadd_vi(VRegister vd, VRegister vs2, int32_t imm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, EncodeInt5(imm5), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSll_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSll_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSll_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100101, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSmul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::Vmv1r_v(VRegister vd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b100111, VM::kUnmasked);
  EmitR(
      funct7, vs2, enum_cast<uint32_t>(Nf::k1), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::Vmv2r_v(VRegister vd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_EQ(enum_cast<uint32_t>(vd) % 2, 0u);
  DCHECK_EQ(enum_cast<uint32_t>(vs2) % 2, 0u);
  const uint32_t funct7 = EncodeRVVF7(0b100111, VM::kUnmasked);
  EmitR(
      funct7, vs2, enum_cast<uint32_t>(Nf::k2), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::Vmv4r_v(VRegister vd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_EQ(enum_cast<uint32_t>(vd) % 4, 0u);
  DCHECK_EQ(enum_cast<uint32_t>(vs2) % 4, 0u);
  const uint32_t funct7 = EncodeRVVF7(0b100111, VM::kUnmasked);
  EmitR(
      funct7, vs2, enum_cast<uint32_t>(Nf::k4), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::Vmv8r_v(VRegister vd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_EQ(enum_cast<uint32_t>(vd) % 8, 0u);
  DCHECK_EQ(enum_cast<uint32_t>(vs2) % 8, 0u);
  const uint32_t funct7 = EncodeRVVF7(0b100111, VM::kUnmasked);
  EmitR(
      funct7, vs2, enum_cast<uint32_t>(Nf::k8), enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSrl_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSrl_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSrl_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101000, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSra_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSra_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSra_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSsrl_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSsrl_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSsrl_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101010, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VSsra_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VSsra_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VSsra_vi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNsrl_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VNsrl_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VNsrl_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101100, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNcvt_x_x_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  VNsrl_wx(vd, vs2, Zero, vm);
}

void Riscv64Assembler::VNsra_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VNsra_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VNsra_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNclipu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VNclipu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VNclipu_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101110, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VNclip_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VNclip_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPIVX), vd, 0x57);
}

void Riscv64Assembler::VNclip_wi(VRegister vd, VRegister vs2, uint32_t uimm5, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, uimm5, enum_cast<uint32_t>(VAIEncoding::kOPIVI), vd, 0x57);
}

void Riscv64Assembler::VWredsumu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b110000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VWredsum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b110001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPIVV), vd, 0x57);
}

void Riscv64Assembler::VRedsum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedand_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedor_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedxor_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedminu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedmin_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedmaxu_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRedmax_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VAaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VAaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VAadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VAadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VAsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VAsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VAsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VAsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VSlide1up_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VSlide1down_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VCompress_vm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMandn_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011000, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMand_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011001, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMmv_m(VRegister vd, VRegister vs2) { VMand_mm(vd, vs2, vs2); }

void Riscv64Assembler::VMor_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011010, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMxor_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011011, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMclr_m(VRegister vd) { VMxor_mm(vd, vd, vd); }

void Riscv64Assembler::VMorn_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011100, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMnand_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011101, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMnot_m(VRegister vd, VRegister vs2) { VMnand_mm(vd, vs2, vs2); }

void Riscv64Assembler::VMnor_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011110, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMxnor_mm(VRegister vd, VRegister vs2, VRegister vs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b011111, VM::kUnmasked);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMset_m(VRegister vd) { VMxnor_mm(vd, vd, vd); }

void Riscv64Assembler::VDivu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VDivu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VDiv_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VDiv_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VRemu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRemu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VRem_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VRem_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMulhu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMulhu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMulhsu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMulhsu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMulh_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMulh_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMadd_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VNmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VNmsub_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMacc_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VNmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VNmsac_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWaddu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWaddu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWcvtu_x_x_v(VRegister vd, VRegister vs, VM vm) {
  VWaddu_vx(vd, vs, Zero, vm);
}

void Riscv64Assembler::VWadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWadd_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110001, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWcvt_x_x_v(VRegister vd, VRegister vs, VM vm) {
  VWadd_vx(vd, vs, Zero, vm);
}

void Riscv64Assembler::VWsubu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWsubu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWsub_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWaddu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWaddu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWadd_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWadd_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWsubu_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWsubu_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWsub_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWsub_wx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmulu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmulu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111000, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmulsu_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmulsu_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111010, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmul_vx(VRegister vd, VRegister vs2, XRegister rs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111011, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmaccu_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmaccu_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111100, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmacc_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111101, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmaccus_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111110, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VWmaccsu_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VWmaccsu_vx(VRegister vd, XRegister rs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111111, vm);
  EmitR(funct7, vs2, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VFadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFadd_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFredusum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000010, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFredosum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmin_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmin_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFredmin_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmax_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmax_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b000110, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFredmax_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b000111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsgnj_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsgnj_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFsgnjn_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsgnjn_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001001, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFneg_v(VRegister vd, VRegister vs) { VFsgnjn_vv(vd, vs, vs); }

void Riscv64Assembler::VFsgnjx_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsgnjx_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001010, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFabs_v(VRegister vd, VRegister vs) { VFsgnjx_vv(vd, vs, vs); }

void Riscv64Assembler::VFslide1up_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b001110, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFslide1down_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b001111, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmerge_vfm(VRegister vd, VRegister vs2, FRegister fs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK(vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kV0_t);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmv_v_f(VRegister vd, FRegister fs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010111, VM::kUnmasked);
  EmitR(funct7, V0, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfeq_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VMfeq_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfle_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VMfle_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011001, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfge_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  VMfle_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMflt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VMflt_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011011, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfgt_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  VMflt_vv(vd, vs1, vs2, vm);
}

void Riscv64Assembler::VMfne_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VMfne_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfgt_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011101, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMfge_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b011111, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFdiv_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFdiv_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFrdiv_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100001, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmul_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFrsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b100111, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmadd_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFnmadd_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFnmadd_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101001, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmsub_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101010, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFnmsub_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFnmsub_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101011, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFnmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFnmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101101, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101110, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFnmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFnmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b101111, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwadd_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwadd_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwredusum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110001, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwsub_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110010, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwsub_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b110010, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwredosum_vs(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b110011, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwadd_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwadd_wf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwsub_wv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  const uint32_t funct7 = EncodeRVVF7(0b110110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwsub_wf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b110110, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwmul_vv(VRegister vd, VRegister vs2, VRegister vs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111000, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwmul_vf(VRegister vd, VRegister vs2, FRegister fs1, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111000, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111100, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111100, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwnmacc_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111101, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwnmacc_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111101, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111110, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111110, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFwnmsac_vv(VRegister vd, VRegister vs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs1);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111111, vm);
  EmitR(funct7, vs2, vs1, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwnmsac_vf(VRegister vd, FRegister fs1, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b111111, vm);
  EmitR(funct7, vs2, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VMv_s_x(VRegister vd, XRegister rs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kUnmasked);
  EmitR(funct7, 0b00000, rs1, enum_cast<uint32_t>(VAIEncoding::kOPMVX), vd, 0x57);
}

void Riscv64Assembler::VMv_x_s(XRegister rd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kUnmasked);
  EmitR(funct7, vs2, 0b00000, enum_cast<uint32_t>(VAIEncoding::kOPMVV), rd, 0x57);
}

void Riscv64Assembler::VCpop_m(XRegister rd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, vm);
  EmitR(funct7, vs2, 0b10000, enum_cast<uint32_t>(VAIEncoding::kOPMVV), rd, 0x57);
}

void Riscv64Assembler::VFirst_m(XRegister rd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, vm);
  EmitR(funct7, vs2, 0b10001, enum_cast<uint32_t>(VAIEncoding::kOPMVV), rd, 0x57);
}

void Riscv64Assembler::VZext_vf8(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00010, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VSext_vf8(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00011, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VZext_vf4(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00100, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VSext_vf4(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00101, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VZext_vf2(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00110, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VSext_vf2(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00111, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VFmv_s_f(VRegister vd, FRegister fs1) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kUnmasked);
  EmitR(funct7, 0b00000, fs1, enum_cast<uint32_t>(VAIEncoding::kOPFVF), vd, 0x57);
}

void Riscv64Assembler::VFmv_f_s(FRegister fd, VRegister vs2) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  const uint32_t funct7 = EncodeRVVF7(0b010000, VM::kUnmasked);
  EmitR(funct7, vs2, 0b00000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), fd, 0x57);
}

void Riscv64Assembler::VFcvt_xu_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFcvt_x_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00001, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFcvt_f_xu_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00010, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFcvt_f_x_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00011, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFcvt_rtz_xu_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00110, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFcvt_rtz_x_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b00111, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_xu_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_x_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01001, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_f_xu_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01010, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_f_x_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01011, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_f_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01100, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_rtz_xu_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01110, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFwcvt_rtz_x_f_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b01111, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_xu_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_x_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10001, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_f_xu_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10010, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_f_x_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10011, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_f_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10100, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_rod_f_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10101, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_rtz_xu_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10110, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFncvt_rtz_x_f_w(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010010, vm);
  EmitR(funct7, vs2, 0b10111, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFsqrt_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010011, vm);
  EmitR(funct7, vs2, 0b00000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFrsqrt7_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010011, vm);
  EmitR(funct7, vs2, 0b00100, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFrec7_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010011, vm);
  EmitR(funct7, vs2, 0b00101, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VFclass_v(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010011, vm);
  EmitR(funct7, vs2, 0b10000, enum_cast<uint32_t>(VAIEncoding::kOPFVV), vd, 0x57);
}

void Riscv64Assembler::VMsbf_m(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010100, vm);
  EmitR(funct7, vs2, 0b00001, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMsof_m(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010100, vm);
  EmitR(funct7, vs2, 0b00010, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VMsif_m(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010100, vm);
  EmitR(funct7, vs2, 0b00011, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VIota_m(VRegister vd, VRegister vs2, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  DCHECK(vd != vs2);
  const uint32_t funct7 = EncodeRVVF7(0b010100, vm);
  EmitR(funct7, vs2, 0b10000, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

void Riscv64Assembler::VId_v(VRegister vd, VM vm) {
  AssertExtensionsEnabled(Riscv64Extension::kV);
  DCHECK_IMPLIES(vm == VM::kV0_t, vd != V0);
  const uint32_t funct7 = EncodeRVVF7(0b010100, vm);
  EmitR(funct7, V0, 0b10001, enum_cast<uint32_t>(VAIEncoding::kOPMVV), vd, 0x57);
}

/////////////////////////////// RVV Arithmetic Instructions  END   /////////////////////////////

////////////////////////////// RV64 MACRO Instructions  START ///////////////////////////////

// Pseudo instructions

void Riscv64Assembler::Nop() { Addi(Zero, Zero, 0); }

void Riscv64Assembler::Li(XRegister rd, int64_t imm) {
  LoadImmediate(rd, imm, /*can_use_tmp=*/ false);
}

void Riscv64Assembler::Mv(XRegister rd, XRegister rs) { Addi(rd, rs, 0); }

void Riscv64Assembler::Not(XRegister rd, XRegister rs) { Xori(rd, rs, -1); }

void Riscv64Assembler::Neg(XRegister rd, XRegister rs) { Sub(rd, Zero, rs); }

void Riscv64Assembler::NegW(XRegister rd, XRegister rs) { Subw(rd, Zero, rs); }

void Riscv64Assembler::SextB(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZbb)) {
    if (IsExtensionEnabled(Riscv64Extension::kZcb) && rd == rs && IsShortReg(rd)) {
      CSextB(rd);
    } else {
      ZbbSextB(rd, rs);
    }
  } else {
    Slli(rd, rs, kXlen - 8u);
    Srai(rd, rd, kXlen - 8u);
  }
}

void Riscv64Assembler::SextH(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZbb)) {
    if (IsExtensionEnabled(Riscv64Extension::kZcb) && rd == rs && IsShortReg(rd)) {
      CSextH(rd);
    } else {
      ZbbSextH(rd, rs);
    }
  } else {
    Slli(rd, rs, kXlen - 16u);
    Srai(rd, rd, kXlen - 16u);
  }
}

void Riscv64Assembler::SextW(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZca) && rd != Zero && (rd == rs || rs == Zero)) {
    if (rd == rs) {
      CAddiw(rd, 0);
    } else {
      CLi(rd, 0);
    }
  } else {
    Addiw(rd, rs, 0);
  }
}

void Riscv64Assembler::ZextB(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZcb) && rd == rs && IsShortReg(rd)) {
    CZextB(rd);
  } else {
    Andi(rd, rs, 0xff);
  }
}

void Riscv64Assembler::ZextH(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZbb)) {
    if (IsExtensionEnabled(Riscv64Extension::kZcb) && rd == rs && IsShortReg(rd)) {
      CZextH(rd);
    } else {
      ZbbZextH(rd, rs);
    }
  } else {
    Slli(rd, rs, kXlen - 16u);
    Srli(rd, rd, kXlen - 16u);
  }
}

void Riscv64Assembler::ZextW(XRegister rd, XRegister rs) {
  if (IsExtensionEnabled(Riscv64Extension::kZba)) {
    if (IsExtensionEnabled(Riscv64Extension::kZcb) && rd == rs && IsShortReg(rd)) {
      CZextW(rd);
    } else {
      AddUw(rd, rs, Zero);
    }
  } else {
    Slli(rd, rs, kXlen - 32u);
    Srli(rd, rd, kXlen - 32u);
  }
}

void Riscv64Assembler::Seqz(XRegister rd, XRegister rs) { Sltiu(rd, rs, 1); }

void Riscv64Assembler::Snez(XRegister rd, XRegister rs) { Sltu(rd, Zero, rs); }

void Riscv64Assembler::Sltz(XRegister rd, XRegister rs) { Slt(rd, rs, Zero); }

void Riscv64Assembler::Sgtz(XRegister rd, XRegister rs) { Slt(rd, Zero, rs); }

void Riscv64Assembler::FMvS(FRegister rd, FRegister rs) { FSgnjS(rd, rs, rs); }

void Riscv64Assembler::FAbsS(FRegister rd, FRegister rs) { FSgnjxS(rd, rs, rs); }

void Riscv64Assembler::FNegS(FRegister rd, FRegister rs) { FSgnjnS(rd, rs, rs); }

void Riscv64Assembler::FMvD(FRegister rd, FRegister rs) { FSgnjD(rd, rs, rs); }

void Riscv64Assembler::FAbsD(FRegister rd, FRegister rs) { FSgnjxD(rd, rs, rs); }

void Riscv64Assembler::FNegD(FRegister rd, FRegister rs) { FSgnjnD(rd, rs, rs); }

void Riscv64Assembler::Beqz(XRegister rs, int32_t offset) {
  Beq(rs, Zero, offset);
}

void Riscv64Assembler::Bnez(XRegister rs, int32_t offset) {
  Bne(rs, Zero, offset);
}

void Riscv64Assembler::Blez(XRegister rt, int32_t offset) {
  Bge(Zero, rt, offset);
}

void Riscv64Assembler::Bgez(XRegister rt, int32_t offset) {
  Bge(rt, Zero, offset);
}

void Riscv64Assembler::Bltz(XRegister rt, int32_t offset) {
  Blt(rt, Zero, offset);
}

void Riscv64Assembler::Bgtz(XRegister rt, int32_t offset) {
  Blt(Zero, rt, offset);
}

void Riscv64Assembler::Bgt(XRegister rs, XRegister rt, int32_t offset) {
  Blt(rt, rs, offset);
}

void Riscv64Assembler::Ble(XRegister rs, XRegister rt, int32_t offset) {
  Bge(rt, rs, offset);
}

void Riscv64Assembler::Bgtu(XRegister rs, XRegister rt, int32_t offset) {
  Bltu(rt, rs, offset);
}

void Riscv64Assembler::Bleu(XRegister rs, XRegister rt, int32_t offset) {
  Bgeu(rt, rs, offset);
}

void Riscv64Assembler::J(int32_t offset) { Jal(Zero, offset); }

void Riscv64Assembler::Jal(int32_t offset) { Jal(RA, offset); }

void Riscv64Assembler::Jr(XRegister rs) { Jalr(Zero, rs, 0); }

void Riscv64Assembler::Jalr(XRegister rs) { Jalr(RA, rs, 0); }

void Riscv64Assembler::Jalr(XRegister rd, XRegister rs) { Jalr(rd, rs, 0); }

void Riscv64Assembler::Ret() { Jalr(Zero, RA, 0); }

void Riscv64Assembler::RdCycle(XRegister rd) {
  Csrrs(rd, 0xc00, Zero);
}

void Riscv64Assembler::RdTime(XRegister rd) {
  Csrrs(rd, 0xc01, Zero);
}

void Riscv64Assembler::RdInstret(XRegister rd) {
  Csrrs(rd, 0xc02, Zero);
}

void Riscv64Assembler::Csrr(XRegister rd, uint32_t csr) {
  Csrrs(rd, csr, Zero);
}

void Riscv64Assembler::Csrw(uint32_t csr, XRegister rs) {
  Csrrw(Zero, csr, rs);
}

void Riscv64Assembler::Csrs(uint32_t csr, XRegister rs) {
  Csrrs(Zero, csr, rs);
}

void Riscv64Assembler::Csrc(uint32_t csr, XRegister rs) {
  Csrrc(Zero, csr, rs);
}

void Riscv64Assembler::Csrwi(uint32_t csr, uint32_t uimm5) {
  Csrrwi(Zero, csr, uimm5);
}

void Riscv64Assembler::Csrsi(uint32_t csr, uint32_t uimm5) {
  Csrrsi(Zero, csr, uimm5);
}

void Riscv64Assembler::Csrci(uint32_t csr, uint32_t uimm5) {
  Csrrci(Zero, csr, uimm5);
}

void Riscv64Assembler::Loadb(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lb>(rd, rs1, offset);
}

void Riscv64Assembler::Loadh(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lh>(rd, rs1, offset);
}

void Riscv64Assembler::Loadw(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lw>(rd, rs1, offset);
}

void Riscv64Assembler::Loadd(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Ld>(rd, rs1, offset);
}

void Riscv64Assembler::Loadbu(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lbu>(rd, rs1, offset);
}

void Riscv64Assembler::Loadhu(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lhu>(rd, rs1, offset);
}

void Riscv64Assembler::Loadwu(XRegister rd, XRegister rs1, int32_t offset) {
  LoadFromOffset<&Riscv64Assembler::Lwu>(rd, rs1, offset);
}

void Riscv64Assembler::Storeb(XRegister rs2, XRegister rs1, int32_t offset) {
  StoreToOffset<&Riscv64Assembler::Sb>(rs2, rs1, offset);
}

void Riscv64Assembler::Storeh(XRegister rs2, XRegister rs1, int32_t offset) {
  StoreToOffset<&Riscv64Assembler::Sh>(rs2, rs1, offset);
}

void Riscv64Assembler::Storew(XRegister rs2, XRegister rs1, int32_t offset) {
  StoreToOffset<&Riscv64Assembler::Sw>(rs2, rs1, offset);
}

void Riscv64Assembler::Stored(XRegister rs2, XRegister rs1, int32_t offset) {
  StoreToOffset<&Riscv64Assembler::Sd>(rs2, rs1, offset);
}

void Riscv64Assembler::FLoadw(FRegister rd, XRegister rs1, int32_t offset) {
  FLoadFromOffset<&Riscv64Assembler::FLw>(rd, rs1, offset);
}

void Riscv64Assembler::FLoadd(FRegister rd, XRegister rs1, int32_t offset) {
  FLoadFromOffset<&Riscv64Assembler::FLd>(rd, rs1, offset);
}

void Riscv64Assembler::FStorew(FRegister rs2, XRegister rs1, int32_t offset) {
  FStoreToOffset<&Riscv64Assembler::FSw>(rs2, rs1, offset);
}

void Riscv64Assembler::FStored(FRegister rs2, XRegister rs1, int32_t offset) {
  FStoreToOffset<&Riscv64Assembler::FSd>(rs2, rs1, offset);
}

void Riscv64Assembler::LoadConst32(XRegister rd, int32_t value) {
  // No need to use a temporary register for 32-bit values.
  LoadImmediate(rd, value, /*can_use_tmp=*/ false);
}

void Riscv64Assembler::LoadConst64(XRegister rd, int64_t value) {
  LoadImmediate(rd, value, /*can_use_tmp=*/ true);
}

template <typename ValueType, typename Addi, typename AddLarge>
void AddConstImpl(Riscv64Assembler* assembler,
                  XRegister rd,
                  XRegister rs1,
                  ValueType value,
                  Addi&& addi,
                  AddLarge&& add_large) {
  ScratchRegisterScope srs(assembler);
  // A temporary must be available for adjustment even if it's not needed.
  // However, `rd` can be used as the temporary unless it's the same as `rs1` or SP.
  DCHECK_IMPLIES(rd == rs1 || rd == SP, srs.AvailableXRegisters() != 0u);

  if (IsInt<12>(value)) {
    addi(rd, rs1, value);
    return;
  }

  constexpr int32_t kPositiveValueSimpleAdjustment = 0x7ff;
  constexpr int32_t kHighestValueForSimpleAdjustment = 2 * kPositiveValueSimpleAdjustment;
  constexpr int32_t kNegativeValueSimpleAdjustment = -0x800;
  constexpr int32_t kLowestValueForSimpleAdjustment = 2 * kNegativeValueSimpleAdjustment;

  if (rd != rs1 && rd != SP) {
    srs.IncludeXRegister(rd);
  }
  XRegister tmp = srs.AllocateXRegister();
  if (value >= 0 && value <= kHighestValueForSimpleAdjustment) {
    addi(tmp, rs1, kPositiveValueSimpleAdjustment);
    addi(rd, tmp, value - kPositiveValueSimpleAdjustment);
  } else if (value < 0 && value >= kLowestValueForSimpleAdjustment) {
    addi(tmp, rs1, kNegativeValueSimpleAdjustment);
    addi(rd, tmp, value - kNegativeValueSimpleAdjustment);
  } else {
    add_large(rd, rs1, value, tmp);
  }
}

void Riscv64Assembler::AddConst32(XRegister rd, XRegister rs1, int32_t value) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  CHECK_EQ((1u << rd) & available_scratch_core_registers_, 0u);
  auto addiw = [&](XRegister rd, XRegister rs1, int32_t value) { Addiw(rd, rs1, value); };
  auto add_large = [&](XRegister rd, XRegister rs1, int32_t value, XRegister tmp) {
    LoadConst32(tmp, value);
    Addw(rd, rs1, tmp);
  };
  AddConstImpl(this, rd, rs1, value, addiw, add_large);
}

void Riscv64Assembler::AddConst64(XRegister rd, XRegister rs1, int64_t value) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  CHECK_EQ((1u << rd) & available_scratch_core_registers_, 0u);
  auto addi = [&](XRegister rd, XRegister rs1, int32_t value) { Addi(rd, rs1, value); };
  auto add_large = [&](XRegister rd, XRegister rs1, int64_t value, XRegister tmp) {
    // We may not have another scratch register for `LoadConst64()`, so use `Li()`.
    // TODO(riscv64): Refactor `LoadImmediate()` so that we can reuse the code to detect
    // when the code path using the scratch reg is beneficial, and use that path with a
    // small modification - instead of adding the two parts togeter, add them individually
    // to the input `rs1`. (This works as long as `rd` is not the same as `tmp`.)
    Li(tmp, value);
    Add(rd, rs1, tmp);
  };
  AddConstImpl(this, rd, rs1, value, addi, add_large);
}

void Riscv64Assembler::Beqz(XRegister rs, Riscv64Label* label, bool is_bare) {
  Beq(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Bnez(XRegister rs, Riscv64Label* label, bool is_bare) {
  Bne(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Blez(XRegister rs, Riscv64Label* label, bool is_bare) {
  Ble(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Bgez(XRegister rs, Riscv64Label* label, bool is_bare) {
  Bge(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Bltz(XRegister rs, Riscv64Label* label, bool is_bare) {
  Blt(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Bgtz(XRegister rs, Riscv64Label* label, bool is_bare) {
  Bgt(rs, Zero, label, is_bare);
}

void Riscv64Assembler::Beq(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondEQ, rs, rt);
}

void Riscv64Assembler::Bne(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondNE, rs, rt);
}

void Riscv64Assembler::Ble(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondLE, rs, rt);
}

void Riscv64Assembler::Bge(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondGE, rs, rt);
}

void Riscv64Assembler::Blt(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondLT, rs, rt);
}

void Riscv64Assembler::Bgt(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondGT, rs, rt);
}

void Riscv64Assembler::Bleu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondLEU, rs, rt);
}

void Riscv64Assembler::Bgeu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondGEU, rs, rt);
}

void Riscv64Assembler::Bltu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondLTU, rs, rt);
}

void Riscv64Assembler::Bgtu(XRegister rs, XRegister rt, Riscv64Label* label, bool is_bare) {
  Bcond(label, is_bare, kCondGTU, rs, rt);
}

void Riscv64Assembler::Jal(XRegister rd, Riscv64Label* label, bool is_bare) {
  Buncond(label, rd, is_bare);
}

void Riscv64Assembler::J(Riscv64Label* label, bool is_bare) {
  Jal(Zero, label, is_bare);
}

void Riscv64Assembler::Jal(Riscv64Label* label, bool is_bare) {
  Jal(RA, label, is_bare);
}

void Riscv64Assembler::Loadw(XRegister rd, Literal* literal) {
  DCHECK_EQ(literal->GetSize(), 4u);
  LoadLiteral(literal, rd, Branch::kLiteral);
}

void Riscv64Assembler::Loadwu(XRegister rd, Literal* literal) {
  DCHECK_EQ(literal->GetSize(), 4u);
  LoadLiteral(literal, rd, Branch::kLiteralUnsigned);
}

void Riscv64Assembler::Loadd(XRegister rd, Literal* literal) {
  DCHECK_EQ(literal->GetSize(), 8u);
  LoadLiteral(literal, rd, Branch::kLiteralLong);
}

void Riscv64Assembler::FLoadw(FRegister rd, Literal* literal) {
  DCHECK_EQ(literal->GetSize(), 4u);
  LoadLiteral(literal, rd, Branch::kLiteralFloat);
}

void Riscv64Assembler::FLoadd(FRegister rd, Literal* literal) {
  DCHECK_EQ(literal->GetSize(), 8u);
  LoadLiteral(literal, rd, Branch::kLiteralDouble);
}

void Riscv64Assembler::Unimp() {
  // TODO(riscv64): use 16-bit zero C.UNIMP once we support compression
  Emit32(0xC0001073);
}

/////////////////////////////// RV64 MACRO Instructions END ///////////////////////////////

const Riscv64Assembler::Branch::BranchInfo Riscv64Assembler::Branch::branch_info_[] = {
    // Short branches (can be promoted to longer).
    {4, 0, Riscv64Assembler::Branch::kOffset13},  // kCondBranch
    {4, 0, Riscv64Assembler::Branch::kOffset21},  // kUncondBranch
    {4, 0, Riscv64Assembler::Branch::kOffset21},  // kCall
    // Short branches (can't be promoted to longer).
    {4, 0, Riscv64Assembler::Branch::kOffset13},  // kBareCondBranch
    {4, 0, Riscv64Assembler::Branch::kOffset21},  // kBareUncondBranch
    {4, 0, Riscv64Assembler::Branch::kOffset21},  // kBareCall

    // Medium branch.
    {8, 4, Riscv64Assembler::Branch::kOffset21},  // kCondBranch21

    // Long branches.
    {12, 4, Riscv64Assembler::Branch::kOffset32},  // kLongCondBranch
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLongUncondBranch
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLongCall

    // label.
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLabel

    // literals.
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLiteral
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLiteralUnsigned
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLiteralLong
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLiteralFloat
    {8, 0, Riscv64Assembler::Branch::kOffset32},  // kLiteralDouble
};

void Riscv64Assembler::Branch::InitShortOrLong(Riscv64Assembler::Branch::OffsetBits offset_size,
                                               Riscv64Assembler::Branch::Type short_type,
                                               Riscv64Assembler::Branch::Type long_type,
                                               Riscv64Assembler::Branch::Type longest_type) {
  Riscv64Assembler::Branch::Type type = short_type;
  if (offset_size > branch_info_[type].offset_size) {
    type = long_type;
    if (offset_size > branch_info_[type].offset_size) {
      type = longest_type;
    }
  }
  type_ = type;
}

void Riscv64Assembler::Branch::InitializeType(Type initial_type) {
  OffsetBits offset_size_needed = GetOffsetSizeNeeded(location_, target_);

  switch (initial_type) {
    case kCondBranch:
      if (condition_ != kUncond) {
        InitShortOrLong(offset_size_needed, kCondBranch, kCondBranch21, kLongCondBranch);
        break;
      }
      FALLTHROUGH_INTENDED;
    case kUncondBranch:
      InitShortOrLong(offset_size_needed, kUncondBranch, kLongUncondBranch, kLongUncondBranch);
      break;
    case kCall:
      InitShortOrLong(offset_size_needed, kCall, kLongCall, kLongCall);
      break;
    case kBareCondBranch:
      if (condition_ != kUncond) {
        type_ = kBareCondBranch;
        CHECK_LE(offset_size_needed, GetOffsetSize());
        break;
      }
      FALLTHROUGH_INTENDED;
    case kBareUncondBranch:
      type_ = kBareUncondBranch;
      CHECK_LE(offset_size_needed, GetOffsetSize());
      break;
    case kBareCall:
      type_ = kBareCall;
      CHECK_LE(offset_size_needed, GetOffsetSize());
      break;
    case kLabel:
      type_ = initial_type;
      break;
    case kLiteral:
    case kLiteralUnsigned:
    case kLiteralLong:
    case kLiteralFloat:
    case kLiteralDouble:
      CHECK(!IsResolved());
      type_ = initial_type;
      break;
    default:
      LOG(FATAL) << "Unexpected branch type " << enum_cast<uint32_t>(initial_type);
      UNREACHABLE();
  }

  old_type_ = type_;
}

bool Riscv64Assembler::Branch::IsNop(BranchCondition condition, XRegister lhs, XRegister rhs) {
  switch (condition) {
    case kCondNE:
    case kCondLT:
    case kCondGT:
    case kCondLTU:
    case kCondGTU:
      return lhs == rhs;
    default:
      return false;
  }
}

bool Riscv64Assembler::Branch::IsUncond(BranchCondition condition, XRegister lhs, XRegister rhs) {
  switch (condition) {
    case kUncond:
      return true;
    case kCondEQ:
    case kCondGE:
    case kCondLE:
    case kCondLEU:
    case kCondGEU:
      return lhs == rhs;
    default:
      return false;
  }
}

Riscv64Assembler::Branch::Branch(uint32_t location, uint32_t target, XRegister rd, bool is_bare)
    : old_location_(location),
      location_(location),
      target_(target),
      lhs_reg_(rd),
      rhs_reg_(Zero),
      freg_(kNoFRegister),
      condition_(kUncond) {
  InitializeType(
      (rd != Zero ? (is_bare ? kBareCall : kCall) : (is_bare ? kBareUncondBranch : kUncondBranch)));
}

Riscv64Assembler::Branch::Branch(uint32_t location,
                                 uint32_t target,
                                 Riscv64Assembler::BranchCondition condition,
                                 XRegister lhs_reg,
                                 XRegister rhs_reg,
                                 bool is_bare)
    : old_location_(location),
      location_(location),
      target_(target),
      lhs_reg_(lhs_reg),
      rhs_reg_(rhs_reg),
      freg_(kNoFRegister),
      condition_(condition) {
  DCHECK_NE(condition, kUncond);
  DCHECK(!IsNop(condition, lhs_reg, rhs_reg));
  DCHECK(!IsUncond(condition, lhs_reg, rhs_reg));
  InitializeType(is_bare ? kBareCondBranch : kCondBranch);
}

Riscv64Assembler::Branch::Branch(uint32_t location,
                                 uint32_t target,
                                 XRegister rd,
                                 Type label_or_literal_type)
    : old_location_(location),
      location_(location),
      target_(target),
      lhs_reg_(rd),
      rhs_reg_(Zero),
      freg_(kNoFRegister),
      condition_(kUncond) {
  CHECK_NE(rd , Zero);
  InitializeType(label_or_literal_type);
}

Riscv64Assembler::Branch::Branch(uint32_t location,
                                 uint32_t target,
                                 FRegister rd,
                                 Type literal_type)
    : old_location_(location),
      location_(location),
      target_(target),
      lhs_reg_(Zero),
      rhs_reg_(Zero),
      freg_(rd),
      condition_(kUncond) {
  InitializeType(literal_type);
}

Riscv64Assembler::BranchCondition Riscv64Assembler::Branch::OppositeCondition(
    Riscv64Assembler::BranchCondition cond) {
  switch (cond) {
    case kCondEQ:
      return kCondNE;
    case kCondNE:
      return kCondEQ;
    case kCondLT:
      return kCondGE;
    case kCondGE:
      return kCondLT;
    case kCondLE:
      return kCondGT;
    case kCondGT:
      return kCondLE;
    case kCondLTU:
      return kCondGEU;
    case kCondGEU:
      return kCondLTU;
    case kCondLEU:
      return kCondGTU;
    case kCondGTU:
      return kCondLEU;
    case kUncond:
      LOG(FATAL) << "Unexpected branch condition " << enum_cast<uint32_t>(cond);
      UNREACHABLE();
  }
}

Riscv64Assembler::Branch::Type Riscv64Assembler::Branch::GetType() const { return type_; }

Riscv64Assembler::BranchCondition Riscv64Assembler::Branch::GetCondition() const {
    return condition_;
}

XRegister Riscv64Assembler::Branch::GetLeftRegister() const { return lhs_reg_; }

XRegister Riscv64Assembler::Branch::GetRightRegister() const { return rhs_reg_; }

FRegister Riscv64Assembler::Branch::GetFRegister() const { return freg_; }

uint32_t Riscv64Assembler::Branch::GetTarget() const { return target_; }

uint32_t Riscv64Assembler::Branch::GetLocation() const { return location_; }

uint32_t Riscv64Assembler::Branch::GetOldLocation() const { return old_location_; }

uint32_t Riscv64Assembler::Branch::GetLength() const { return branch_info_[type_].length; }

uint32_t Riscv64Assembler::Branch::GetOldLength() const { return branch_info_[old_type_].length; }

uint32_t Riscv64Assembler::Branch::GetEndLocation() const { return GetLocation() + GetLength(); }

uint32_t Riscv64Assembler::Branch::GetOldEndLocation() const {
  return GetOldLocation() + GetOldLength();
}

bool Riscv64Assembler::Branch::IsBare() const {
  switch (type_) {
    case kBareUncondBranch:
    case kBareCondBranch:
    case kBareCall:
      return true;
    default:
      return false;
  }
}

bool Riscv64Assembler::Branch::IsResolved() const { return target_ != kUnresolved; }

Riscv64Assembler::Branch::OffsetBits Riscv64Assembler::Branch::GetOffsetSize() const {
  return branch_info_[type_].offset_size;
}

Riscv64Assembler::Branch::OffsetBits Riscv64Assembler::Branch::GetOffsetSizeNeeded(
    uint32_t location, uint32_t target) {
  // For unresolved targets assume the shortest encoding
  // (later it will be made longer if needed).
  if (target == kUnresolved) {
    return kOffset13;
  }
  int64_t distance = static_cast<int64_t>(target) - location;
  if (IsInt<kOffset13>(distance)) {
    return kOffset13;
  } else if (IsInt<kOffset21>(distance)) {
    return kOffset21;
  } else {
    return kOffset32;
  }
}

void Riscv64Assembler::Branch::Resolve(uint32_t target) { target_ = target; }

void Riscv64Assembler::Branch::Relocate(uint32_t expand_location, uint32_t delta) {
  // All targets should be resolved before we start promoting branches.
  DCHECK(IsResolved());
  if (location_ > expand_location) {
    location_ += delta;
  }
  if (target_ > expand_location) {
    target_ += delta;
  }
}

uint32_t Riscv64Assembler::Branch::PromoteIfNeeded() {
  // All targets should be resolved before we start promoting branches.
  DCHECK(IsResolved());
  Type old_type = type_;
  switch (type_) {
    // Short branches (can be promoted to longer).
    case kCondBranch: {
      OffsetBits needed_size = GetOffsetSizeNeeded(GetOffsetLocation(), target_);
      if (needed_size <= GetOffsetSize()) {
        return 0u;
      }
      // The offset remains the same for `kCondBranch21` for forward branches.
      DCHECK_EQ(branch_info_[kCondBranch21].length - branch_info_[kCondBranch21].pc_offset,
                branch_info_[kCondBranch].length - branch_info_[kCondBranch].pc_offset);
      if (target_ <= location_) {
        // Calculate the needed size for kCondBranch21.
        needed_size =
            GetOffsetSizeNeeded(location_ + branch_info_[kCondBranch21].pc_offset, target_);
      }
      type_ = (needed_size <= branch_info_[kCondBranch21].offset_size)
          ? kCondBranch21
          : kLongCondBranch;
      break;
    }
    case kUncondBranch:
      if (GetOffsetSizeNeeded(GetOffsetLocation(), target_) <= GetOffsetSize()) {
        return 0u;
      }
      type_ = kLongUncondBranch;
      break;
    case kCall:
      if (GetOffsetSizeNeeded(GetOffsetLocation(), target_) <= GetOffsetSize()) {
        return 0u;
      }
      type_ = kLongCall;
      break;
    // Medium branch (can be promoted to long).
    case kCondBranch21:
      if (GetOffsetSizeNeeded(GetOffsetLocation(), target_) <= GetOffsetSize()) {
        return 0u;
      }
      type_ = kLongCondBranch;
      break;
    default:
      // Other branch types cannot be promoted.
      DCHECK_LE(GetOffsetSizeNeeded(GetOffsetLocation(), target_), GetOffsetSize()) << type_;
      return 0u;
  }
  DCHECK(type_ != old_type);
  DCHECK_GT(branch_info_[type_].length, branch_info_[old_type].length);
  return branch_info_[type_].length - branch_info_[old_type].length;
}

uint32_t Riscv64Assembler::Branch::GetOffsetLocation() const {
  return location_ + branch_info_[type_].pc_offset;
}

int32_t Riscv64Assembler::Branch::GetOffset() const {
  CHECK(IsResolved());
  // Calculate the byte distance between instructions and also account for
  // different PC-relative origins.
  uint32_t offset_location = GetOffsetLocation();
  int32_t offset = static_cast<int32_t>(target_ - offset_location);
  DCHECK_EQ(offset, static_cast<int64_t>(target_) - static_cast<int64_t>(offset_location));
  return offset;
}

void Riscv64Assembler::EmitBcond(BranchCondition cond,
                                 XRegister rs,
                                 XRegister rt,
                                 int32_t offset) {
  switch (cond) {
#define DEFINE_CASE(COND, cond) \
    case kCond##COND:           \
      B##cond(rs, rt, offset);  \
      break;
    DEFINE_CASE(EQ, eq)
    DEFINE_CASE(NE, ne)
    DEFINE_CASE(LT, lt)
    DEFINE_CASE(GE, ge)
    DEFINE_CASE(LE, le)
    DEFINE_CASE(GT, gt)
    DEFINE_CASE(LTU, ltu)
    DEFINE_CASE(GEU, geu)
    DEFINE_CASE(LEU, leu)
    DEFINE_CASE(GTU, gtu)
#undef DEFINE_CASE
    case kUncond:
      LOG(FATAL) << "Unexpected branch condition " << enum_cast<uint32_t>(cond);
      UNREACHABLE();
  }
}

void Riscv64Assembler::EmitBranch(Riscv64Assembler::Branch* branch) {
  CHECK(overwriting_);
  overwrite_location_ = branch->GetLocation();
  const int32_t offset = branch->GetOffset();
  BranchCondition condition = branch->GetCondition();
  XRegister lhs = branch->GetLeftRegister();
  XRegister rhs = branch->GetRightRegister();

  auto emit_auipc_and_next = [&](XRegister reg, auto next) {
    CHECK_EQ(overwrite_location_, branch->GetOffsetLocation());
    auto [imm20, short_offset] = SplitOffset(offset);
    Auipc(reg, imm20);
    next(short_offset);
  };

  switch (branch->GetType()) {
    // Short branches.
    case Branch::kUncondBranch:
    case Branch::kBareUncondBranch:
      CHECK_EQ(overwrite_location_, branch->GetOffsetLocation());
      J(offset);
      break;
    case Branch::kCondBranch:
    case Branch::kBareCondBranch:
      CHECK_EQ(overwrite_location_, branch->GetOffsetLocation());
      EmitBcond(condition, lhs, rhs, offset);
      break;
    case Branch::kCall:
    case Branch::kBareCall:
      CHECK_EQ(overwrite_location_, branch->GetOffsetLocation());
      DCHECK(lhs != Zero);
      Jal(lhs, offset);
      break;

    // Medium branch.
    case Branch::kCondBranch21:
      EmitBcond(Branch::OppositeCondition(condition), lhs, rhs, branch->GetLength());
      CHECK_EQ(overwrite_location_, branch->GetOffsetLocation());
      J(offset);
      break;

    // Long branches.
    case Branch::kLongCondBranch:
      EmitBcond(Branch::OppositeCondition(condition), lhs, rhs, branch->GetLength());
      FALLTHROUGH_INTENDED;
    case Branch::kLongUncondBranch:
      emit_auipc_and_next(TMP, [&](int32_t short_offset) { Jalr(Zero, TMP, short_offset); });
      break;
    case Branch::kLongCall:
      DCHECK(lhs != Zero);
      emit_auipc_and_next(lhs, [&](int32_t short_offset) { Jalr(lhs, lhs, short_offset); });
      break;

    // label.
    case Branch::kLabel:
      emit_auipc_and_next(lhs, [&](int32_t short_offset) { Addi(lhs, lhs, short_offset); });
      break;
    // literals.
    case Branch::kLiteral:
      emit_auipc_and_next(lhs, [&](int32_t short_offset) { Lw(lhs, lhs, short_offset); });
      break;
    case Branch::kLiteralUnsigned:
      emit_auipc_and_next(lhs, [&](int32_t short_offset) { Lwu(lhs, lhs, short_offset); });
      break;
    case Branch::kLiteralLong:
      emit_auipc_and_next(lhs, [&](int32_t short_offset) { Ld(lhs, lhs, short_offset); });
      break;
    case Branch::kLiteralFloat:
      emit_auipc_and_next(
          TMP, [&](int32_t short_offset) { FLw(branch->GetFRegister(), TMP, short_offset); });
      break;
    case Branch::kLiteralDouble:
      emit_auipc_and_next(
          TMP, [&](int32_t short_offset) { FLd(branch->GetFRegister(), TMP, short_offset); });
      break;
  }
  CHECK_EQ(overwrite_location_, branch->GetEndLocation());
  CHECK_LE(branch->GetLength(), static_cast<uint32_t>(Branch::kMaxBranchLength));
}

void Riscv64Assembler::EmitBranches() {
  CHECK(!overwriting_);
  // Switch from appending instructions at the end of the buffer to overwriting
  // existing instructions (branch placeholders) in the buffer.
  overwriting_ = true;
  for (auto& branch : branches_) {
    EmitBranch(&branch);
  }
  overwriting_ = false;
}

void Riscv64Assembler::FinalizeLabeledBranch(Riscv64Label* label) {
  // TODO(riscv64): Support "C" Standard Extension - length may not be a multiple of 4.
  DCHECK_ALIGNED(branches_.back().GetLength(), sizeof(uint32_t));
  uint32_t length = branches_.back().GetLength() / sizeof(uint32_t);
  if (!label->IsBound()) {
    // Branch forward (to a following label), distance is unknown.
    // The first branch forward will contain 0, serving as the terminator of
    // the list of forward-reaching branches.
    Emit32(label->position_);
    length--;
    // Now make the label object point to this branch
    // (this forms a linked list of branches preceding this label).
    uint32_t branch_id = branches_.size() - 1;
    label->LinkTo(branch_id);
  }
  // Reserve space for the branch.
  for (; length != 0u; --length) {
    Nop();
  }
}

void Riscv64Assembler::Bcond(
    Riscv64Label* label, bool is_bare, BranchCondition condition, XRegister lhs, XRegister rhs) {
  // TODO(riscv64): Should an assembler perform these optimizations, or should we remove them?
  // If lhs = rhs, this can be a NOP.
  if (Branch::IsNop(condition, lhs, rhs)) {
    return;
  }
  if (Branch::IsUncond(condition, lhs, rhs)) {
    Buncond(label, Zero, is_bare);
    return;
  }

  uint32_t target = label->IsBound() ? GetLabelLocation(label) : Branch::kUnresolved;
  branches_.emplace_back(buffer_.Size(), target, condition, lhs, rhs, is_bare);
  FinalizeLabeledBranch(label);
}

void Riscv64Assembler::Buncond(Riscv64Label* label, XRegister rd, bool is_bare) {
  uint32_t target = label->IsBound() ? GetLabelLocation(label) : Branch::kUnresolved;
  branches_.emplace_back(buffer_.Size(), target, rd, is_bare);
  FinalizeLabeledBranch(label);
}

template <typename XRegisterOrFRegister>
void Riscv64Assembler::LoadLiteral(Literal* literal,
                                   XRegisterOrFRegister rd,
                                   Branch::Type literal_type) {
  Riscv64Label* label = literal->GetLabel();
  DCHECK(!label->IsBound());
  branches_.emplace_back(buffer_.Size(), Branch::kUnresolved, rd, literal_type);
  FinalizeLabeledBranch(label);
}

Riscv64Assembler::Branch* Riscv64Assembler::GetBranch(uint32_t branch_id) {
  CHECK_LT(branch_id, branches_.size());
  return &branches_[branch_id];
}

const Riscv64Assembler::Branch* Riscv64Assembler::GetBranch(uint32_t branch_id) const {
  CHECK_LT(branch_id, branches_.size());
  return &branches_[branch_id];
}

void Riscv64Assembler::Bind(Riscv64Label* label) {
  CHECK(!label->IsBound());
  uint32_t bound_pc = buffer_.Size();

  // Walk the list of branches referring to and preceding this label.
  // Store the previously unknown target addresses in them.
  while (label->IsLinked()) {
    uint32_t branch_id = label->Position();
    Branch* branch = GetBranch(branch_id);
    branch->Resolve(bound_pc);

    uint32_t branch_location = branch->GetLocation();
    // Extract the location of the previous branch in the list (walking the list backwards;
    // the previous branch ID was stored in the space reserved for this branch).
    uint32_t prev = buffer_.Load<uint32_t>(branch_location);

    // On to the previous branch in the list...
    label->position_ = prev;
  }

  // Now make the label object contain its own location (relative to the end of the preceding
  // branch, if any; it will be used by the branches referring to and following this label).
  uint32_t prev_branch_id = Riscv64Label::kNoPrevBranchId;
  if (!branches_.empty()) {
    prev_branch_id = branches_.size() - 1u;
    const Branch* prev_branch = GetBranch(prev_branch_id);
    bound_pc -= prev_branch->GetEndLocation();
  }
  label->prev_branch_id_ = prev_branch_id;
  label->BindTo(bound_pc);
}

void Riscv64Assembler::LoadLabelAddress(XRegister rd, Riscv64Label* label) {
  DCHECK_NE(rd, Zero);
  uint32_t target = label->IsBound() ? GetLabelLocation(label) : Branch::kUnresolved;
  branches_.emplace_back(buffer_.Size(), target, rd, Branch::kLabel);
  FinalizeLabeledBranch(label);
}

Literal* Riscv64Assembler::NewLiteral(size_t size, const uint8_t* data) {
  // We don't support byte and half-word literals.
  if (size == 4u) {
    literals_.emplace_back(size, data);
    return &literals_.back();
  } else {
    DCHECK_EQ(size, 8u);
    long_literals_.emplace_back(size, data);
    return &long_literals_.back();
  }
}

JumpTable* Riscv64Assembler::CreateJumpTable(ArenaVector<Riscv64Label*>&& labels) {
  jump_tables_.emplace_back(std::move(labels));
  JumpTable* table = &jump_tables_.back();
  DCHECK(!table->GetLabel()->IsBound());
  return table;
}

uint32_t Riscv64Assembler::GetLabelLocation(const Riscv64Label* label) const {
  CHECK(label->IsBound());
  uint32_t target = label->Position();
  if (label->prev_branch_id_ != Riscv64Label::kNoPrevBranchId) {
    // Get label location based on the branch preceding it.
    const Branch* prev_branch = GetBranch(label->prev_branch_id_);
    target += prev_branch->GetEndLocation();
  }
  return target;
}

uint32_t Riscv64Assembler::GetAdjustedPosition(uint32_t old_position) {
  // We can reconstruct the adjustment by going through all the branches from the beginning
  // up to the `old_position`. Since we expect `GetAdjustedPosition()` to be called in a loop
  // with increasing `old_position`, we can use the data from last `GetAdjustedPosition()` to
  // continue where we left off and the whole loop should be O(m+n) where m is the number
  // of positions to adjust and n is the number of branches.
  if (old_position < last_old_position_) {
    last_position_adjustment_ = 0;
    last_old_position_ = 0;
    last_branch_id_ = 0;
  }
  while (last_branch_id_ != branches_.size()) {
    const Branch* branch = GetBranch(last_branch_id_);
    if (branch->GetLocation() >= old_position + last_position_adjustment_) {
      break;
    }
    last_position_adjustment_ += branch->GetLength() - branch->GetOldLength();
    ++last_branch_id_;
  }
  last_old_position_ = old_position;
  return old_position + last_position_adjustment_;
}

void Riscv64Assembler::ReserveJumpTableSpace() {
  if (!jump_tables_.empty()) {
    for (JumpTable& table : jump_tables_) {
      Riscv64Label* label = table.GetLabel();
      Bind(label);

      // Bulk ensure capacity, as this may be large.
      size_t orig_size = buffer_.Size();
      size_t required_capacity = orig_size + table.GetSize();
      if (required_capacity > buffer_.Capacity()) {
        buffer_.ExtendCapacity(required_capacity);
      }
#ifndef NDEBUG
      buffer_.has_ensured_capacity_ = true;
#endif

      // Fill the space with placeholder data as the data is not final
      // until the branches have been promoted. And we shouldn't
      // be moving uninitialized data during branch promotion.
      for (size_t cnt = table.GetData().size(), i = 0; i < cnt; ++i) {
        buffer_.Emit<uint32_t>(0x1abe1234u);
      }

#ifndef NDEBUG
      buffer_.has_ensured_capacity_ = false;
#endif
    }
  }
}

void Riscv64Assembler::PromoteBranches() {
  // Promote short branches to long as necessary.
  bool changed;
  do {
    changed = false;
    for (auto& branch : branches_) {
      CHECK(branch.IsResolved());
      uint32_t delta = branch.PromoteIfNeeded();
      // If this branch has been promoted and needs to expand in size,
      // relocate all branches by the expansion size.
      if (delta != 0u) {
        changed = true;
        uint32_t expand_location = branch.GetLocation();
        for (auto& branch2 : branches_) {
          branch2.Relocate(expand_location, delta);
        }
      }
    }
  } while (changed);

  // Account for branch expansion by resizing the code buffer
  // and moving the code in it to its final location.
  size_t branch_count = branches_.size();
  if (branch_count > 0) {
    // Resize.
    Branch& last_branch = branches_[branch_count - 1];
    uint32_t size_delta = last_branch.GetEndLocation() - last_branch.GetOldEndLocation();
    uint32_t old_size = buffer_.Size();
    buffer_.Resize(old_size + size_delta);
    // Move the code residing between branch placeholders.
    uint32_t end = old_size;
    for (size_t i = branch_count; i > 0;) {
      Branch& branch = branches_[--i];
      uint32_t size = end - branch.GetOldEndLocation();
      buffer_.Move(branch.GetEndLocation(), branch.GetOldEndLocation(), size);
      end = branch.GetOldLocation();
    }
  }

  // Align 64-bit literals by moving them up by 4 bytes if needed.
  // This can increase the PC-relative distance but all literals are accessed with AUIPC+Load(imm12)
  // without branch promotion, so this late adjustment cannot take them out of instruction range.
  if (!long_literals_.empty()) {
    uint32_t first_literal_location = GetLabelLocation(long_literals_.front().GetLabel());
    size_t lit_size = long_literals_.size() * sizeof(uint64_t);
    size_t buf_size = buffer_.Size();
    // 64-bit literals must be at the very end of the buffer.
    CHECK_EQ(first_literal_location + lit_size, buf_size);
    if (!IsAligned<sizeof(uint64_t)>(first_literal_location)) {
      // Insert the padding.
      buffer_.Resize(buf_size + sizeof(uint32_t));
      buffer_.Move(first_literal_location + sizeof(uint32_t), first_literal_location, lit_size);
      DCHECK(!overwriting_);
      overwriting_ = true;
      overwrite_location_ = first_literal_location;
      Emit32(0);  // Illegal instruction.
      overwriting_ = false;
      // Increase target addresses in literal and address loads by 4 bytes in order for correct
      // offsets from PC to be generated.
      for (auto& branch : branches_) {
        uint32_t target = branch.GetTarget();
        if (target >= first_literal_location) {
          branch.Resolve(target + sizeof(uint32_t));
        }
      }
      // If after this we ever call GetLabelLocation() to get the location of a 64-bit literal,
      // we need to adjust the location of the literal's label as well.
      for (Literal& literal : long_literals_) {
        // Bound label's position is negative, hence decrementing it instead of incrementing.
        literal.GetLabel()->position_ -= sizeof(uint32_t);
      }
    }
  }
}

void Riscv64Assembler::PatchCFI() {
  if (cfi().NumberOfDelayedAdvancePCs() == 0u) {
    return;
  }

  using DelayedAdvancePC = DebugFrameOpCodeWriterForAssembler::DelayedAdvancePC;
  const auto data = cfi().ReleaseStreamAndPrepareForDelayedAdvancePC();
  const std::vector<uint8_t>& old_stream = data.first;
  const std::vector<DelayedAdvancePC>& advances = data.second;

  // Refill our data buffer with patched opcodes.
  static constexpr size_t kExtraSpace = 16;  // Not every PC advance can be encoded in one byte.
  cfi().ReserveCFIStream(old_stream.size() + advances.size() + kExtraSpace);
  size_t stream_pos = 0;
  for (const DelayedAdvancePC& advance : advances) {
    DCHECK_GE(advance.stream_pos, stream_pos);
    // Copy old data up to the point where advance was issued.
    cfi().AppendRawData(old_stream, stream_pos, advance.stream_pos);
    stream_pos = advance.stream_pos;
    // Insert the advance command with its final offset.
    size_t final_pc = GetAdjustedPosition(advance.pc);
    cfi().AdvancePC(final_pc);
  }
  // Copy the final segment if any.
  cfi().AppendRawData(old_stream, stream_pos, old_stream.size());
}

void Riscv64Assembler::EmitJumpTables() {
  if (!jump_tables_.empty()) {
    CHECK(!overwriting_);
    // Switch from appending instructions at the end of the buffer to overwriting
    // existing instructions (here, jump tables) in the buffer.
    overwriting_ = true;

    for (JumpTable& table : jump_tables_) {
      Riscv64Label* table_label = table.GetLabel();
      uint32_t start = GetLabelLocation(table_label);
      overwrite_location_ = start;

      for (Riscv64Label* target : table.GetData()) {
        CHECK_EQ(buffer_.Load<uint32_t>(overwrite_location_), 0x1abe1234u);
        // The table will contain target addresses relative to the table start.
        uint32_t offset = GetLabelLocation(target) - start;
        Emit32(offset);
      }
    }

    overwriting_ = false;
  }
}

void Riscv64Assembler::EmitLiterals() {
  if (!literals_.empty()) {
    for (Literal& literal : literals_) {
      Riscv64Label* label = literal.GetLabel();
      Bind(label);
      AssemblerBuffer::EnsureCapacity ensured(&buffer_);
      DCHECK_EQ(literal.GetSize(), 4u);
      for (size_t i = 0, size = literal.GetSize(); i != size; ++i) {
        buffer_.Emit<uint8_t>(literal.GetData()[i]);
      }
    }
  }
  if (!long_literals_.empty()) {
    // These need to be 8-byte-aligned but we shall add the alignment padding after the branch
    // promotion, if needed. Since all literals are accessed with AUIPC+Load(imm12) without branch
    // promotion, this late adjustment cannot take long literals out of instruction range.
    for (Literal& literal : long_literals_) {
      Riscv64Label* label = literal.GetLabel();
      Bind(label);
      AssemblerBuffer::EnsureCapacity ensured(&buffer_);
      DCHECK_EQ(literal.GetSize(), 8u);
      for (size_t i = 0, size = literal.GetSize(); i != size; ++i) {
        buffer_.Emit<uint8_t>(literal.GetData()[i]);
      }
    }
  }
}

// This method is used to adjust the base register and offset pair for
// a load/store when the offset doesn't fit into 12-bit signed integer.
void Riscv64Assembler::AdjustBaseAndOffset(XRegister& base,
                                           int32_t& offset,
                                           ScratchRegisterScope& srs) {
  // A scratch register must be available for adjustment even if it's not needed.
  CHECK_NE(srs.AvailableXRegisters(), 0u);
  if (IsInt<12>(offset)) {
    return;
  }

  constexpr int32_t kPositiveOffsetMaxSimpleAdjustment = 0x7ff;
  constexpr int32_t kHighestOffsetForSimpleAdjustment = 2 * kPositiveOffsetMaxSimpleAdjustment;
  constexpr int32_t kPositiveOffsetSimpleAdjustmentAligned8 =
      RoundDown(kPositiveOffsetMaxSimpleAdjustment, 8);
  constexpr int32_t kPositiveOffsetSimpleAdjustmentAligned4 =
      RoundDown(kPositiveOffsetMaxSimpleAdjustment, 4);
  constexpr int32_t kNegativeOffsetSimpleAdjustment = -0x800;
  constexpr int32_t kLowestOffsetForSimpleAdjustment = 2 * kNegativeOffsetSimpleAdjustment;

  XRegister tmp = srs.AllocateXRegister();
  if (offset >= 0 && offset <= kHighestOffsetForSimpleAdjustment) {
    // Make the adjustment 8-byte aligned (0x7f8) except for offsets that cannot be reached
    // with this adjustment, then try 4-byte alignment, then just half of the offset.
    int32_t adjustment = IsInt<12>(offset - kPositiveOffsetSimpleAdjustmentAligned8)
        ? kPositiveOffsetSimpleAdjustmentAligned8
        : IsInt<12>(offset - kPositiveOffsetSimpleAdjustmentAligned4)
            ? kPositiveOffsetSimpleAdjustmentAligned4
            : offset / 2;
    DCHECK(IsInt<12>(adjustment));
    Addi(tmp, base, adjustment);
    offset -= adjustment;
  } else if (offset < 0 && offset >= kLowestOffsetForSimpleAdjustment) {
    Addi(tmp, base, kNegativeOffsetSimpleAdjustment);
    offset -= kNegativeOffsetSimpleAdjustment;
  } else if (offset >= 0x7ffff800) {
    // Support even large offsets outside the range supported by `SplitOffset()`.
    LoadConst32(tmp, offset);
    Add(tmp, tmp, base);
    offset = 0;
  } else {
    auto [imm20, short_offset] = SplitOffset(offset);
    Lui(tmp, imm20);
    Add(tmp, tmp, base);
    offset = short_offset;
  }
  base = tmp;
}

template <void (Riscv64Assembler::*insn)(XRegister, XRegister, int32_t)>
void Riscv64Assembler::LoadFromOffset(XRegister rd, XRegister rs1, int32_t offset) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  CHECK_EQ((1u << rd) & available_scratch_core_registers_, 0u);
  ScratchRegisterScope srs(this);
  // If `rd` differs from `rs1`, allow using it as a temporary if needed.
  if (rd != rs1) {
    srs.IncludeXRegister(rd);
  }
  AdjustBaseAndOffset(rs1, offset, srs);
  (this->*insn)(rd, rs1, offset);
}

template <void (Riscv64Assembler::*insn)(XRegister, XRegister, int32_t)>
void Riscv64Assembler::StoreToOffset(XRegister rs2, XRegister rs1, int32_t offset) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  CHECK_EQ((1u << rs2) & available_scratch_core_registers_, 0u);
  ScratchRegisterScope srs(this);
  AdjustBaseAndOffset(rs1, offset, srs);
  (this->*insn)(rs2, rs1, offset);
}

template <void (Riscv64Assembler::*insn)(FRegister, XRegister, int32_t)>
void Riscv64Assembler::FLoadFromOffset(FRegister rd, XRegister rs1, int32_t offset) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  ScratchRegisterScope srs(this);
  AdjustBaseAndOffset(rs1, offset, srs);
  (this->*insn)(rd, rs1, offset);
}

template <void (Riscv64Assembler::*insn)(FRegister, XRegister, int32_t)>
void Riscv64Assembler::FStoreToOffset(FRegister rs2, XRegister rs1, int32_t offset) {
  CHECK_EQ((1u << rs1) & available_scratch_core_registers_, 0u);
  ScratchRegisterScope srs(this);
  AdjustBaseAndOffset(rs1, offset, srs);
  (this->*insn)(rs2, rs1, offset);
}

void Riscv64Assembler::LoadImmediate(XRegister rd, int64_t imm, bool can_use_tmp) {
  CHECK_EQ((1u << rd) & available_scratch_core_registers_, 0u);
  ScratchRegisterScope srs(this);
  CHECK_IMPLIES(can_use_tmp, srs.AvailableXRegisters() != 0u);

  // Helper lambdas.
  auto addi = [&](XRegister rd, XRegister rs, int32_t imm) { Addi(rd, rs, imm); };
  auto addiw = [&](XRegister rd, XRegister rs, int32_t imm) { Addiw(rd, rs, imm); };
  auto slli = [&](XRegister rd, XRegister rs, int32_t imm) { Slli(rd, rs, imm); };
  auto lui = [&](XRegister rd, uint32_t imm20) { Lui(rd, imm20); };

  // Simple LUI+ADDI/W can handle value range [-0x80000800, 0x7fffffff].
  auto is_simple_li_value = [](int64_t value) {
    return value >= INT64_C(-0x80000800) && value <= INT64_C(0x7fffffff);
  };
  auto emit_simple_li_helper = [&](XRegister rd,
                                   int64_t value,
                                   auto&& addi,
                                   auto&& addiw,
                                   auto&& slli,
                                   auto&& lui) {
    DCHECK(is_simple_li_value(value)) << "0x" << std::hex << value;
    if (IsInt<12>(value)) {
      addi(rd, Zero, value);
    } else if (CTZ(value) < 12 && IsInt(6 + CTZ(value), value)) {
      // This path yields two 16-bit instructions with the "C" Standard Extension.
      addi(rd, Zero, value >> CTZ(value));
      slli(rd, rd, CTZ(value));
    } else if (value < INT64_C(-0x80000000)) {
      int32_t small_value = dchecked_integral_cast<int32_t>(value - INT64_C(-0x80000000));
      DCHECK(IsInt<12>(small_value));
      DCHECK_LT(small_value, 0);
      lui(rd, 1u << 19);
      addi(rd, rd, small_value);
    } else {
      DCHECK(IsInt<32>(value));
      // Note: Similar to `SplitOffset()` but we can target the full 32-bit range with ADDIW.
      int64_t near_value = (value + 0x800) & ~0xfff;
      int32_t small_value = value - near_value;
      DCHECK(IsInt<12>(small_value));
      uint32_t imm20 = static_cast<uint32_t>(near_value) >> 12;
      DCHECK_NE(imm20, 0u);  // Small values are handled above.
      lui(rd, imm20);
      if (small_value != 0) {
        addiw(rd, rd, small_value);
      }
    }
  };
  auto emit_simple_li = [&](XRegister rd, int64_t value) {
    emit_simple_li_helper(rd, value, addi, addiw, slli, lui);
  };
  auto count_simple_li_instructions = [&](int64_t value) {
    size_t num_instructions = 0u;
    auto count_rri = [&](XRegister, XRegister, int32_t) { ++num_instructions; };
    auto count_ru = [&](XRegister, uint32_t) { ++num_instructions; };
    emit_simple_li_helper(Zero, value, count_rri, count_rri, count_rri, count_ru);
    return num_instructions;
  };

  // If LUI+ADDI/W is not enough, we can generate up to 3 SLLI+ADDI afterwards (up to 8 instructions
  // total). The ADDI from the first SLLI+ADDI pair can be a no-op.
  auto emit_with_slli_addi_helper = [&](XRegister rd,
                                        int64_t value,
                                        auto&& addi,
                                        auto&& addiw,
                                        auto&& slli,
                                        auto&& lui) {
    static constexpr size_t kMaxNumSllAddi = 3u;
    int32_t addi_values[kMaxNumSllAddi];
    size_t sll_shamts[kMaxNumSllAddi];
    size_t num_sll_addi = 0u;
    while (!is_simple_li_value(value)) {
      DCHECK_LT(num_sll_addi, kMaxNumSllAddi);
      // Prepare sign-extended low 12 bits for ADDI.
      int64_t addi_value = (value & 0xfff) - ((value & 0x800) << 1);
      DCHECK(IsInt<12>(addi_value));
      int64_t remaining = value - addi_value;
      size_t shamt = CTZ(remaining);
      DCHECK_GE(shamt, 12u);
      addi_values[num_sll_addi] = addi_value;
      sll_shamts[num_sll_addi] = shamt;
      value = remaining >> shamt;
      ++num_sll_addi;
    }
    if (num_sll_addi != 0u && IsInt<20>(value) && !IsInt<12>(value)) {
      // If `sll_shamts[num_sll_addi - 1u]` was only 12, we would have stopped
      // the decomposition a step earlier with smaller `num_sll_addi`.
      DCHECK_GT(sll_shamts[num_sll_addi - 1u], 12u);
      // Emit the signed 20-bit value with LUI and reduce the SLLI shamt by 12 to compensate.
      sll_shamts[num_sll_addi - 1u] -= 12u;
      lui(rd, dchecked_integral_cast<uint32_t>(value & 0xfffff));
    } else {
      emit_simple_li_helper(rd, value, addi, addiw, slli, lui);
    }
    for (size_t i = num_sll_addi; i != 0u; ) {
      --i;
      slli(rd, rd, sll_shamts[i]);
      if (addi_values[i] != 0) {
        addi(rd, rd, addi_values[i]);
      }
    }
  };
  auto emit_with_slli_addi = [&](XRegister rd, int64_t value) {
    emit_with_slli_addi_helper(rd, value, addi, addiw, slli, lui);
  };
  auto count_instructions_with_slli_addi = [&](int64_t value) {
    size_t num_instructions = 0u;
    auto count_rri = [&](XRegister, XRegister, int32_t) { ++num_instructions; };
    auto count_ru = [&](XRegister, uint32_t) { ++num_instructions; };
    emit_with_slli_addi_helper(Zero, value, count_rri, count_rri, count_rri, count_ru);
    return num_instructions;
  };

  size_t insns_needed = count_instructions_with_slli_addi(imm);
  size_t trailing_slli_shamt = 0u;
  if (insns_needed > 2u) {
    // Sometimes it's better to end with a SLLI even when the above code would end with ADDI.
    if ((imm & 1) == 0 && (imm & 0xfff) != 0) {
      int64_t value = imm >> CTZ(imm);
      size_t new_insns_needed = count_instructions_with_slli_addi(value) + /*SLLI*/ 1u;
      DCHECK_GT(new_insns_needed, 2u);
      if (insns_needed > new_insns_needed) {
        insns_needed = new_insns_needed;
        trailing_slli_shamt = CTZ(imm);
      }
    }

    // Sometimes we can emit a shorter sequence that ends with SRLI.
    if (imm > 0) {
      size_t shamt = CLZ(static_cast<uint64_t>(imm));
      DCHECK_LE(shamt, 32u);  // Otherwise we would not get here as `insns_needed` would be <= 2.
      if (imm == dchecked_integral_cast<int64_t>(MaxInt<uint64_t>(64 - shamt))) {
        Addi(rd, Zero, -1);
        Srli(rd, rd, shamt);
        return;
      }

      int64_t value = static_cast<int64_t>(static_cast<uint64_t>(imm) << shamt);
      DCHECK_LT(value, 0);
      if (is_simple_li_value(value)){
        size_t new_insns_needed = count_simple_li_instructions(value) + /*SRLI*/ 1u;
        // In case of equal number of instructions, clang prefers the sequence without SRLI.
        if (new_insns_needed < insns_needed) {
          // If we emit ADDI, we set low bits that shall be shifted out to one in line with clang,
          // effectively choosing to emit the negative constant closest to zero.
          int32_t shifted_out = dchecked_integral_cast<int32_t>(MaxInt<uint32_t>(shamt));
          DCHECK_EQ(value & shifted_out, 0);
          emit_simple_li(rd, (value & 0xfff) == 0 ? value : value + shifted_out);
          Srli(rd, rd, shamt);
          return;
        }
      }

      size_t ctz = CTZ(static_cast<uint64_t>(value));
      if (IsInt(ctz + 20, value)) {
        size_t new_insns_needed = /*ADDI or LUI*/ 1u + /*SLLI*/ 1u + /*SRLI*/ 1u;
        if (new_insns_needed < insns_needed) {
          // Clang prefers ADDI+SLLI+SRLI over LUI+SLLI+SRLI.
          if (IsInt(ctz + 12, value)) {
            Addi(rd, Zero, value >> ctz);
            Slli(rd, rd, ctz);
          } else {
            Lui(rd, (static_cast<uint64_t>(value) >> ctz) & 0xfffffu);
            Slli(rd, rd, ctz - 12);
          }
          Srli(rd, rd, shamt);
          return;
        }
      }
    }

    // If we can use a scratch register, try using it to emit a shorter sequence. Without a
    // scratch reg, the sequence is up to 8 instructions, with a scratch reg only up to 6.
    if (can_use_tmp) {
      int64_t low = (imm & 0xffffffff) - ((imm & 0x80000000) << 1);
      int64_t remainder = imm - low;
      size_t slli_shamt = CTZ(remainder);
      DCHECK_GE(slli_shamt, 32u);
      int64_t high = remainder >> slli_shamt;
      size_t new_insns_needed =
          ((IsInt<20>(high) || (high & 0xfff) == 0u) ? 1u : 2u) +
          count_simple_li_instructions(low) +
          /*SLLI+ADD*/ 2u;
      if (new_insns_needed < insns_needed) {
        DCHECK_NE(low & 0xfffff000, 0);
        XRegister tmp = srs.AllocateXRegister();
        if (IsInt<20>(high) && !IsInt<12>(high)) {
          // Emit the signed 20-bit value with LUI and reduce the SLLI shamt by 12 to compensate.
          Lui(rd, static_cast<uint32_t>(high & 0xfffff));
          slli_shamt -= 12;
        } else {
          emit_simple_li(rd, high);
        }
        emit_simple_li(tmp, low);
        Slli(rd, rd, slli_shamt);
        Add(rd, rd, tmp);
        return;
      }
    }
  }
  emit_with_slli_addi(rd, trailing_slli_shamt != 0u ? imm >> trailing_slli_shamt : imm);
  if (trailing_slli_shamt != 0u) {
    Slli(rd, rd, trailing_slli_shamt);
  }
}

/////////////////////////////// RV64 VARIANTS extension end ////////////

}  // namespace riscv64
}  // namespace art
