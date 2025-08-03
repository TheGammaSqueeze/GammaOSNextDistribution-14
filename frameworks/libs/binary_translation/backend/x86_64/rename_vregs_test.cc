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

#include "gtest/gtest.h"

#include "berberis/backend/x86_64/rename_vregs.h"

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_test_corpus.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

TEST(MachineRenameVRegsTest, AssignNewVRegsInSameBasicBlock) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::VRegMap vreg_map(&machine_ir);
  vreg_map.AssignNewVRegs();

  ASSERT_EQ(bb->insn_list().size(), 3U);
  auto it = bb->insn_list().begin();
  MachineReg new_vreg = (*it)->RegAt(0);
  EXPECT_NE(vreg, new_vreg);
  it++;
  EXPECT_EQ(new_vreg, (*it)->RegAt(1));
  // Hard regs remain unrenamed.
  EXPECT_EQ(x86_64::kMachineRegRAX, (*it)->RegAt(0));
}

TEST(MachineRenameVRegsTest, AssignNewVRegsAcrossBasicBlocks) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb2);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::VRegMap vreg_map(&machine_ir);
  vreg_map.AssignNewVRegs();

  ASSERT_EQ(bb1->insn_list().size(), 2U);
  auto it = bb1->insn_list().begin();
  MachineReg vreg_in_bb1 = (*it)->RegAt(0);
  EXPECT_NE(vreg, vreg_in_bb1);

  ASSERT_EQ(bb2->insn_list().size(), 2U);
  it = bb2->insn_list().begin();
  MachineReg vreg_in_bb2 = (*it)->RegAt(1);
  EXPECT_NE(vreg, vreg_in_bb2);
  EXPECT_NE(vreg_in_bb1, vreg_in_bb2);
  // Hard regs remain unrenamed.
  EXPECT_EQ(x86_64::kMachineRegRAX, (*it)->RegAt(0));
}

TEST(MachineRenameVRegsTest, DataFlowAcrossBasicBlocks) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg1, vreg2] = BuildDataFlowAcrossBasicBlocks(&machine_ir);

  x86_64::RenameVRegs(&machine_ir);

  // BB1:
  // MOVQ bb1_v1, 0
  // MOVQ bb1_v2, 0
  // BRANCH BB2
  ASSERT_EQ(bb1->insn_list().size(), 3U);
  auto it = bb1->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  MachineReg vreg1_in_bb1 = (*it)->RegAt(0);
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  it++;
  MachineReg vreg2_in_bb1 = (*it)->RegAt(0);

  // BB2:
  // PSEUDO_COPY bb2_v1, bb1_v1
  // PSEUDO_COPY bb2_v2, bb1_v2
  // MOVQ RAX, bb2_v2
  // BRANCH BB3
  ASSERT_EQ(bb2->insn_list().size(), 4U);
  MachineReg vreg1_in_bb2, vreg2_in_bb2;
  it = bb2->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  // Pseudo-moves order is not guaranteed. So consider both cases.
  if ((*it)->RegAt(1) == vreg1_in_bb1) {
    vreg1_in_bb2 = (*it)->RegAt(0);
    it++;
    EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
    EXPECT_EQ((*it)->RegAt(1), vreg2_in_bb1);
    vreg2_in_bb2 = (*it)->RegAt(0);
  } else {
    EXPECT_EQ((*it)->RegAt(1), vreg2_in_bb1);

    vreg2_in_bb2 = (*it)->RegAt(0);
    it++;
    EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
    EXPECT_EQ((*it)->RegAt(1), vreg1_in_bb1);
    vreg1_in_bb2 = (*it)->RegAt(0);
  }
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ((*it)->RegAt(1), vreg2_in_bb2);

  // BB3:
  // PSEUDO_COPY bb3_v1, bb2_v1
  // MOVQ RAX, bb3_v1
  // JUMP
  ASSERT_EQ(bb3->insn_list().size(), 3U);
  it = bb3->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ((*it)->RegAt(1), vreg1_in_bb2);
  MachineReg vreg1_in_bb3 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ((*it)->RegAt(1), vreg1_in_bb3);
}

TEST(MachineRenameVRegsTest, DataFlowFromTwoPreds) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg] = BuildDataFlowFromTwoPreds(&machine_ir);

  x86_64::RenameVRegs(&machine_ir);

  // BB1:
  // MOVQ v1, 0
  // PSEUDO_COPY v3, v1
  // BRANCH BB3
  ASSERT_EQ(bb1->insn_list().size(), 3U);
  auto it = bb1->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  auto vreg_in_bb1 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb1, (*it)->RegAt(1));
  auto vreg_in_bb3 = (*it)->RegAt(0);

  // BB2:
  // MOVQ v2, 1
  // PSEUDO_COPY v3, v2
  // BRANCH BB3
  ASSERT_EQ(bb2->insn_list().size(), 3U);
  it = bb2->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  auto vreg_in_bb2 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb2, (*it)->RegAt(1));
  EXPECT_EQ(vreg_in_bb3, (*it)->RegAt(0));

  // BB3:
  // MOVQ RAX, v3
  // JUMP
  ASSERT_EQ(bb3->insn_list().size(), 2U);
  it = bb3->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ(vreg_in_bb3, (*it)->RegAt(1));
}

TEST(MachineRenameVRegsTest, DataFlowToTwoSuccs) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg] = BuildDataFlowToTwoSuccs(&machine_ir);

  x86_64::RenameVRegs(&machine_ir);

  // BB1:
  // MOVQ v1, 0
  // COND_BRANCH Z, BB2, BB3
  ASSERT_EQ(bb1->insn_list().size(), 2U);
  auto it = bb1->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  auto vreg_in_bb1 = (*it)->RegAt(0);

  // BB2:
  // PSEUDO_COPY v2, v1
  // MOVQ RAX, v2
  // JUMP
  ASSERT_EQ(bb2->insn_list().size(), 3U);
  it = bb2->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb1, (*it)->RegAt(1));
  auto vreg_in_bb2 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ(vreg_in_bb2, (*it)->RegAt(1));

  // BB3:
  // PSEUDO_COPY v3, v1
  // MOVQ RAX, v3
  // JUMP
  ASSERT_EQ(bb3->insn_list().size(), 3U);
  it = bb3->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb1, (*it)->RegAt(1));
  auto vreg_in_bb3 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ(vreg_in_bb3, (*it)->RegAt(1));
}

TEST(MachineRenameVRegsTest, DataFlowAcrossEmptyLoop) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, bb4, vreg] = BuildDataFlowAcrossEmptyLoop(&machine_ir);

  x86_64::RenameVRegs(&machine_ir);

  // BB1:
  // MOVQ v1, 0
  // PSEUDO_COPY v2, v1
  // BRANCH BB2
  ASSERT_EQ(bb1->insn_list().size(), 3U);
  auto it = bb1->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegImm);
  auto vreg_in_bb1 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb1, (*it)->RegAt(1));
  auto vreg_in_bb2 = (*it)->RegAt(0);

  // BB2:
  // COND_BRANCH Z, BB3, BB4
  ASSERT_EQ(bb2->insn_list().size(), 1U);

  // BB3:
  // PSEUDO_COPY v3, v2
  // PSEUDO_COPY v2, v3
  // BRAND BB2
  ASSERT_EQ(bb4->insn_list().size(), 3U);
  it = bb3->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb2, (*it)->RegAt(1));
  auto vreg_in_bb3 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb3, (*it)->RegAt(1));
  EXPECT_EQ(vreg_in_bb2, (*it)->RegAt(0));

  // BB4:
  // PSEUDO_COPY v4, v2
  // MOVQ RAX, v4
  // JUMP
  ASSERT_EQ(bb4->insn_list().size(), 3U);
  it = bb4->insn_list().begin();
  EXPECT_EQ((*it)->opcode(), kMachineOpPseudoCopy);
  EXPECT_EQ(vreg_in_bb2, (*it)->RegAt(1));
  auto vreg_in_bb4 = (*it)->RegAt(0);
  it++;
  EXPECT_EQ((*it)->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ(vreg_in_bb4, (*it)->RegAt(1));
}

}  // namespace

}  // namespace berberis
