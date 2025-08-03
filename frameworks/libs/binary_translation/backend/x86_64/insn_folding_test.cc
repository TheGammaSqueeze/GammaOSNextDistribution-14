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

#include <tuple>

#include "gtest/gtest.h"

#include "berberis/backend/x86_64/insn_folding.h"

#include "berberis/backend/code_emitter.h"  // for CodeEmitter::Condition
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis::x86_64 {

namespace {

// By default for the successful folding the immediate must be sign-extended from 32-bit to the same
// 64-bit integer number.
template <typename InsnTypeRegReg, typename InsnTypeRegImm, bool kExpectSuccess = true>
void TryRegRegInsnFolding(bool is_64bit_mov_imm, uint64_t imm = 0x7777ffffULL) {
  Arena arena;
  MachineIR machine_ir(&arena);
  auto* bb = machine_ir.NewBasicBlock();

  MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg flags = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  if (is_64bit_mov_imm) {
    builder.Gen<MovqRegImm>(vreg1, imm);
  } else {
    builder.Gen<MovlRegImm>(vreg1, imm);
  }
  builder.Gen<InsnTypeRegReg>(vreg2, vreg1, flags);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg2);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  auto insn_it = bb->insn_list().begin();
  insn_it++;
  const MachineInsn* insn = *insn_it;

  auto [is_folded, folded_insn] = insn_folding.TryFoldInsn(insn);

  if (!is_folded) {
    EXPECT_FALSE(kExpectSuccess);
    return;
  }
  EXPECT_TRUE(kExpectSuccess);
  EXPECT_EQ(InsnTypeRegImm::kInfo.opcode, folded_insn->opcode());
  EXPECT_EQ(vreg2, folded_insn->RegAt(0));
  EXPECT_EQ(flags, folded_insn->RegAt(1));
  EXPECT_EQ(static_cast<uint64_t>(static_cast<int32_t>(imm)),
            AsMachineInsnX86_64(folded_insn)->imm());
}

template <typename InsnTypeRegReg, typename InsnTypeRegImm>
void TryMovInsnFolding(bool is_64bit_mov_imm, uint64_t imm) {
  Arena arena;
  MachineIR machine_ir(&arena);
  auto* bb = machine_ir.NewBasicBlock();

  MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  if (is_64bit_mov_imm) {
    builder.Gen<MovqRegImm>(vreg1, imm);
  } else {
    builder.Gen<MovlRegImm>(vreg1, imm);
  }
  builder.Gen<InsnTypeRegReg>(vreg2, vreg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg2);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  auto insn_it = bb->insn_list().begin();
  insn_it++;
  const MachineInsn* insn = *insn_it;

  auto [is_folded, folded_insn] = insn_folding.TryFoldInsn(insn);

  EXPECT_TRUE(is_folded);
  EXPECT_EQ(InsnTypeRegImm::kInfo.opcode, folded_insn->opcode());
  EXPECT_EQ(vreg2, folded_insn->RegAt(0));
  // MovqRegReg is the only instruction that can take full 64-bit imm.
  if (InsnTypeRegReg::kInfo.opcode == MovqRegReg::kInfo.opcode) {
    // Take into account zero-extension when MOVL.
    EXPECT_EQ(is_64bit_mov_imm ? imm : static_cast<uint32_t>(imm),
              AsMachineInsnX86_64(folded_insn)->imm());
  } else {
    EXPECT_EQ(static_cast<uint64_t>(static_cast<int32_t>(imm)),
              AsMachineInsnX86_64(folded_insn)->imm());
  }
}

TEST(InsnFoldingTest, DefMapGetsLatestDef) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg flags = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<MovqRegImm>(vreg1, 0);
  builder.Gen<MovqRegImm>(vreg2, 0);
  builder.Gen<AddqRegReg>(vreg2, vreg1, flags);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);
  bb->live_out().push_back(vreg2);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  auto [vreg1_def, index1] = def_map.Get(vreg1);
  EXPECT_EQ(kMachineOpMovqRegImm, vreg1_def->opcode());
  EXPECT_EQ(vreg1, vreg1_def->RegAt(0));
  EXPECT_EQ(index1, 0);

  auto [vreg2_def, index2] = def_map.Get(vreg2);
  EXPECT_EQ(kMachineOpAddqRegReg, vreg2_def->opcode());
  EXPECT_EQ(vreg2, vreg2_def->RegAt(0));
  EXPECT_EQ(index2, 2);
}

TEST(InsnFoldingTest, MovFolding) {
  constexpr uint64_t kSignExtendableImm = 0xffff'ffff'8000'0000ULL;
  constexpr uint64_t kNotSignExtendableImm = 0xffff'ffff'0000'0000ULL;
  for (bool is_64bit_mov_imm : {true, false}) {
    // MovqRegReg is the only instruction that allow 64-bit immediates.
    TryMovInsnFolding<MovqRegReg, MovqRegImm>(is_64bit_mov_imm, kSignExtendableImm);
    TryMovInsnFolding<MovqRegReg, MovqRegImm>(is_64bit_mov_imm, kNotSignExtendableImm);
    // Movl isn't sensetive to upper immediate bits.
    TryMovInsnFolding<MovlRegReg, MovlRegImm>(is_64bit_mov_imm, kSignExtendableImm);
    TryMovInsnFolding<MovlRegReg, MovlRegImm>(is_64bit_mov_imm, kNotSignExtendableImm);
  }
}

TEST(InsnFoldingTest, SingleMovqMemBaseDispImm32Folding) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();
  auto* recovery_bb = machine_ir.NewBasicBlock();

  MachineReg vreg1 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<MovlRegImm>(vreg1, 2);
  builder.Gen<MovqMemBaseDispReg>(kMachineRegRAX, 4, vreg1);
  builder.SetRecoveryPointAtLastInsn(recovery_bb);
  builder.SetRecoveryWithGuestPCAtLastInsn(42);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  auto insn_it = bb->insn_list().begin();
  insn_it++;
  const MachineInsn* insn = *insn_it;

  auto [_, folded_insn] = insn_folding.TryFoldInsn(insn);
  EXPECT_EQ(kMachineOpMovqMemBaseDispImm, folded_insn->opcode());
  EXPECT_EQ(kMachineRegRAX, folded_insn->RegAt(0));
  EXPECT_EQ(2UL, AsMachineInsnX86_64(folded_insn)->imm());
  EXPECT_EQ(4UL, AsMachineInsnX86_64(folded_insn)->disp());
  EXPECT_EQ(folded_insn->recovery_pc(), 42UL);
  EXPECT_EQ(folded_insn->recovery_bb(), recovery_bb);
}

TEST(InsnFoldingTest, SingleMovlMemBaseDispImm32Folding) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();
  auto* recovery_bb = machine_ir.NewBasicBlock();

  MachineReg vreg1 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<MovqRegImm>(vreg1, 0x3'0000'0003);
  builder.Gen<MovlMemBaseDispReg>(kMachineRegRAX, 4, vreg1);
  builder.SetRecoveryPointAtLastInsn(recovery_bb);
  builder.SetRecoveryWithGuestPCAtLastInsn(42);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  auto insn_it = bb->insn_list().begin();
  insn_it++;
  const MachineInsn* insn = *insn_it;

  auto [_, folded_insn] = insn_folding.TryFoldInsn(insn);
  EXPECT_EQ(kMachineOpMovlMemBaseDispImm, folded_insn->opcode());
  EXPECT_EQ(kMachineRegRAX, folded_insn->RegAt(0));
  EXPECT_EQ(3UL, AsMachineInsnX86_64(folded_insn)->imm());
  EXPECT_EQ(4UL, AsMachineInsnX86_64(folded_insn)->disp());
  EXPECT_EQ(folded_insn->recovery_pc(), 42UL);
  EXPECT_EQ(folded_insn->recovery_bb(), recovery_bb);
}

TEST(InsnFoldingTest, RedundantMovlFolding) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();
  MachineReg flags = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<AddlRegReg>(vreg2, vreg3, flags);
  builder.Gen<MovlRegReg>(vreg1, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  auto insn_it = bb->insn_list().begin();
  const MachineInsn* insn = *std::next(insn_it);

  auto [_, folded_insn] = insn_folding.TryFoldInsn(insn);
  EXPECT_EQ(kMachineOpPseudoCopy, folded_insn->opcode());
  EXPECT_EQ(vreg1, folded_insn->RegAt(0));
  EXPECT_EQ(vreg2, folded_insn->RegAt(1));
}

TEST(InsnFoldingTest, GracefulHandlingOfVRegDefinedInPreviousBasicBlock) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  bb->live_in().push_back(vreg2);

  builder.StartBasicBlock(bb);
  builder.Gen<MovlRegReg>(vreg1, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  DefMap def_map(machine_ir.NumVReg(), machine_ir.arena());
  for (const auto* insn : bb->insn_list()) {
    def_map.ProcessInsn(insn);
  }

  InsnFolding insn_folding(def_map, &machine_ir);

  const MachineInsn* insn = *(bb->insn_list().begin());

  auto [success, _] = insn_folding.TryFoldInsn(insn);
  EXPECT_FALSE(success);
}

TEST(InsnFoldingTest, RegRegInsnTypeFolding) {
  for (bool is_64bit_mov_imm : {true, false}) {
    TryRegRegInsnFolding<AddqRegReg, AddqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<SubqRegReg, SubqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<CmpqRegReg, CmpqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<OrqRegReg, OrqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<XorqRegReg, XorqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<AndqRegReg, AndqRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<TestqRegReg, TestqRegImm>(is_64bit_mov_imm);

    TryRegRegInsnFolding<AddlRegReg, AddlRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<SublRegReg, SublRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<CmplRegReg, CmplRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<OrlRegReg, OrlRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<XorlRegReg, XorlRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<AndlRegReg, AndlRegImm>(is_64bit_mov_imm);
    TryRegRegInsnFolding<TestlRegReg, TestlRegImm>(is_64bit_mov_imm);
  }
}

TEST(InsnFoldingTest, 32To64SignExtendableImm) {
  // The signed immediate is 32->64 sign-extend to the same integer value.
  constexpr uint64_t kImm = 0xffff'ffff'8000'0000ULL;
  // Can fold into 64-bit instruction.
  TryRegRegInsnFolding<AddqRegReg,
                       AddqRegImm,
                       /* kExpectSuccess */ true>(/* is_64bit_mov_imm */ true, kImm);
  // But cannot fold if the upper bits are cleared out by MOVL, since it's not sign-extable anymore.
  TryRegRegInsnFolding<AddqRegReg,
                       AddqRegImm,
                       /* kExpectSuccess */ false>(/* is_64bit_mov_imm */ false, kImm);

  for (bool is_64bit_mov_imm : {true, false}) {
    // Can fold into 32-bit instruction since the upper bits are not used.
    TryRegRegInsnFolding<AddlRegReg,
                         AddlRegImm,
                         /* kExpectSuccess */ true>(is_64bit_mov_imm, kImm);
  }
}

TEST(InsnFoldingTest, Not32To64SignExtendableImm) {
  // The immediate doesn't 32->64 sign-extend to the same integer value.
  constexpr uint64_t kImm = 0xffff'ffff'0000'0000ULL;
  // Cannot fold into 64-bit instruction.
  TryRegRegInsnFolding<AddqRegReg,
                       AddqRegImm,
                       /* kExpectSuccess */ false>(/* is_64bit_mov_imm */ true, kImm);
  // But can fold if the upper bits are cleared out by MOVL.
  TryRegRegInsnFolding<AddqRegReg,
                       AddqRegImm,
                       /* kExpectSuccess */ true>(/* is_64bit_mov_imm */ false, kImm);

  for (bool is_64bit_mov_imm : {true, false}) {
    // Can fold into 32-bit instruction since the upper bits are not used.
    TryRegRegInsnFolding<AddlRegReg,
                         AddlRegImm,
                         /* kExpectSuccess */ true>(is_64bit_mov_imm, kImm);
  }
}

TEST(InsnFoldingTest, HardRegsAreSafe) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb);
  builder.Gen<AddqRegReg>(kMachineRegRAX, kMachineRegRDI, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  FoldInsns(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
}

TEST(InsnFoldingTest, PseudoWriteFlagsErased) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();

  MachineReg flag = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();
  MachineReg vreg4 = machine_ir.AllocVReg();
  MachineReg vreg5 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<AddqRegReg>(vreg4, vreg5, flag);
  builder.Gen<PseudoReadFlags>(PseudoReadFlags::kWithOverflow, vreg2, flag);
  builder.Gen<PseudoCopy>(vreg3, vreg2, 8);
  builder.Gen<PseudoWriteFlags>(vreg3, flag);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  FoldInsns(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 4UL);

  auto insn_it = bb->insn_list().rbegin();
  insn_it++;
  const MachineInsn* insn = *insn_it;

  EXPECT_EQ(kMachineOpPseudoCopy, insn->opcode());
}

TEST(InsnFoldingTest, FlagModifiedAfterPseudoRead) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();

  MachineReg flag = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();
  MachineReg vreg4 = machine_ir.AllocVReg();
  MachineReg vreg5 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoReadFlags>(PseudoReadFlags::kWithOverflow, vreg2, flag);
  builder.Gen<PseudoCopy>(vreg3, vreg2, 8);
  builder.Gen<AddqRegReg>(vreg4, vreg5, flag);
  builder.Gen<PseudoWriteFlags>(vreg3, flag);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  FoldInsns(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 5UL);
}

TEST(InsnFoldingTest, WriteFlagsNotDeletedBecauseDefinitionIsAfterUse) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto* bb = machine_ir.NewBasicBlock();

  MachineReg flag = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoReadFlags>(PseudoReadFlags::kWithOverflow, vreg2, flag);
  builder.Gen<PseudoCopy>(vreg3, vreg2, 8);
  builder.Gen<MovqRegImm>(vreg2, 3);
  builder.Gen<PseudoWriteFlags>(vreg3, flag);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  FoldInsns(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 5UL);
}

TEST(InsnFoldingTest, FoldInsnsSmoke) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg flags = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<MovqRegImm>(vreg1, 2);
  builder.Gen<AddqRegReg>(vreg2, vreg1, flags);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg2);
  bb->live_in().push_back(vreg2);

  FoldInsns(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 3UL);

  auto insn_it = bb->insn_list().begin();
  insn_it++;
  MachineInsn* insn = *insn_it;

  EXPECT_EQ(insn->opcode(), kMachineOpAddqRegImm);
  EXPECT_EQ(vreg2, insn->RegAt(0));
  EXPECT_EQ(2UL, AsMachineInsnX86_64(insn)->imm());
}

using Cond = CodeEmitter::Condition;

void TestFoldCond(Cond input_cond, Cond expected_new_cond, uint16_t expected_flags_mask) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();
  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoWriteFlags>(kMachineRegRAX, kMachineRegFLAGS);
  builder.Gen<PseudoCondBranch>(input_cond, nullptr, nullptr, kMachineRegFLAGS);

  MachineReg flags_src = (*bb->insn_list().begin())->RegAt(0);

  FoldWriteFlags(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);

  auto insn_it = bb->insn_list().begin();
  const auto* insn = AsMachineInsnX86_64(*insn_it);
  EXPECT_EQ(insn->opcode(), kMachineOpTestwRegImm);
  EXPECT_EQ(flags_src, insn->RegAt(0));
  EXPECT_EQ(expected_flags_mask, static_cast<uint16_t>(insn->imm()));
  MachineReg flags = insn->RegAt(1);

  const auto* branch = static_cast<const PseudoCondBranch*>(*(++insn_it));
  EXPECT_EQ(branch->opcode(), kMachineOpPseudoCondBranch);
  EXPECT_EQ(flags, branch->RegAt(0));
  EXPECT_EQ(expected_new_cond, branch->cond());
}

TEST(InsnFoldingTest, FoldWriteFlags) {
  TestFoldCond(Cond::kEqual, Cond::kNotEqual, PseudoWriteFlags::Flags::kZero);
  TestFoldCond(Cond::kNotEqual, Cond::kEqual, PseudoWriteFlags::Flags::kZero);
  TestFoldCond(Cond::kCarry, Cond::kNotEqual, PseudoWriteFlags::Flags::kCarry);
  TestFoldCond(Cond::kNotCarry, Cond::kEqual, PseudoWriteFlags::Flags::kCarry);
  TestFoldCond(Cond::kNegative, Cond::kNotEqual, PseudoWriteFlags::Flags::kNegative);
  TestFoldCond(Cond::kNotSign, Cond::kEqual, PseudoWriteFlags::Flags::kNegative);
  TestFoldCond(Cond::kOverflow, Cond::kNotEqual, PseudoWriteFlags::Flags::kOverflow);
  TestFoldCond(Cond::kNoOverflow, Cond::kEqual, PseudoWriteFlags::Flags::kOverflow);
}

}  // namespace

}  // namespace berberis::x86_64
