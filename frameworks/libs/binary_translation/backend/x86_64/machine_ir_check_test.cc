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

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"

#include "berberis/backend/x86_64/machine_ir_test_corpus.h"

namespace berberis {

namespace {

TEST(MachineIRCheckTest, BasicBlockNotDstOfInEdgeLists) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.bb_list().push_back(bb1);
  machine_ir.bb_list().push_back(bb2);

  auto* bad_edge = NewInArena<MachineEdge>(&arena, &arena, bb1, bb2);
  auto* good_edge = NewInArena<MachineEdge>(&arena, &arena, bb2, bb1);
  bb1->in_edges().push_back(bad_edge);
  bb2->out_edges().push_back(good_edge);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, BasicBlockNotSrcOfItsOutEdgeLists) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.bb_list().push_back(bb1);
  machine_ir.bb_list().push_back(bb2);

  auto* bad_edge = NewInArena<MachineEdge>(&arena, &arena, bb2, bb1);
  auto* good_edge = NewInArena<MachineEdge>(&arena, &arena, bb1, bb2);
  bb1->out_edges().push_back(bad_edge);
  bb2->in_edges().push_back(good_edge);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, EdgeIsNotIncomingForItsDst) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  auto* bb1_to_bb2_edge = NewInArena<MachineEdge>(&arena, &arena, bb1, bb2);
  bb1->out_edges().push_back(bb1_to_bb2_edge);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);
  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckDanglingEdge);
}

TEST(MachineIRCheckTest, EdgeIsNotOutgoingForItsSrc) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  // Create two edges so that we don't encounter dongling basic block error.
  auto* bb1_to_bb2_edge = NewInArena<MachineEdge>(&arena, &arena, bb1, bb2);
  auto* bb2_to_bb1_edge = NewInArena<MachineEdge>(&arena, &arena, bb2, bb1);
  bb2->in_edges().push_back(bb1_to_bb2_edge);
  bb1->in_edges().push_back(bb2_to_bb1_edge);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoJump>(kNullGuestAddr);
  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckDanglingEdge);
}

TEST(MachineIRCheckTest, DanglingBasicBlock) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  // bb1 is on IR's list and links to bb2, so that the checker can find bb2.
  // But bb2 isn't on IR's list.
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.bb_list().push_back(bb1);

  machine_ir.AddEdge(bb1, bb2);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckDanglingBasicBlock);
}

TEST(MachineIRCheckTest, SimpleWellFormedMachineIR) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb2);

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegImm>(vreg2, 0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
}

TEST(MachineIRCheckTest, CorpusWellFormedMachineIRs) {
  Arena arena;

  x86_64::MachineIR machine_ir1(&arena);
  BuildDataFlowAcrossBasicBlocks(&machine_ir1);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir1), x86_64::kMachineIRCheckSuccess);

  x86_64::MachineIR machine_ir2(&arena);
  BuildDataFlowFromTwoPreds(&machine_ir2);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir2), x86_64::kMachineIRCheckSuccess);

  x86_64::MachineIR machine_ir3(&arena);
  BuildDataFlowToTwoSuccs(&machine_ir3);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir3), x86_64::kMachineIRCheckSuccess);

  x86_64::MachineIR machine_ir4(&arena);
  BuildDataFlowToTwoSuccs(&machine_ir4);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir4), x86_64::kMachineIRCheckSuccess);
}

TEST(MachineIRCheckTest, NoControlFlow) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();
  machine_ir.bb_list().push_back(bb);

  MachineReg reg1 = machine_ir.AllocVReg();
  MachineReg reg2 = machine_ir.AllocVReg();
  PseudoCopy* insn = machine_ir.NewInsn<PseudoCopy>(reg1, reg2, 8);

  bb->insn_list().push_back(insn);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, MisplacedJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb = machine_ir.NewBasicBlock();

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoJump>(kNullGuestAddr);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, MisplacedIndirectJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb = machine_ir.NewBasicBlock();

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoIndirectJump>(vreg);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, MisplacedPseudoBranch) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb2);

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, MisplacedPseudoCondBranch) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegImm>(vreg, 1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, NoThenEdgePseudoBranch) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckDanglingBasicBlock);
}

TEST(MachineIRCheckTest, NoThenEdgePseudoCondBranch) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb3);

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegImm>(vreg, 1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

TEST(MachineIRCheckTest, NoElseEdgePseudoCondBranch) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb1, bb2);

  MachineReg vreg = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegImm>(vreg, 1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckFail);
}

}  // namespace

}  // namespace berberis
