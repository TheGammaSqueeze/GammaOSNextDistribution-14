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

#include "berberis/backend/x86_64/machine_ir_opt.h"

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir_opt.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/backend/x86_64/machine_ir_test_corpus.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

TEST(MachineIRRemoveDeadCodeTest, DefKilledByAnotherDef) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegReg>(vreg1, vreg1);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 1);
  builder.Gen<PseudoBranch>(bb);

  bb->live_out().push_back(vreg1);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);

  auto insn_it = bb->insn_list().begin();
  MachineInsn* insn = *insn_it;
  MachineReg reg_after = insn->RegAt(0);
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpMovqRegImm, opcode_after);
  EXPECT_EQ(vreg1, reg_after);
}

TEST(MachineIRRemoveDeadCodeTest, RegUsedInSameBasicBlockNotErased) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqMemBaseDispReg>(vreg2, 0, vreg1);
  builder.Gen<PseudoBranch>(bb);

  bb->live_out().push_back(vreg1);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 3UL);

  auto insn_it = bb->insn_list().begin();
  MachineInsn* insn = *insn_it;
  MachineReg reg_after = insn->RegAt(0);
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpMovqRegImm, opcode_after);
  EXPECT_EQ(vreg1, reg_after);
}

TEST(MachineIRRemoveDeadCodeTest, LiveOutRegNotErased) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<PseudoBranch>(bb);

  bb->live_out().push_back(vreg1);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);

  auto insn_it = bb->insn_list().begin();
  MachineInsn* insn = *insn_it;
  MachineReg reg_after = insn->RegAt(0);
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpMovqRegImm, opcode_after);
  EXPECT_EQ(vreg1, reg_after);
}

TEST(MachineIRRemoveDeadCodeTest, UseOfRegBeforeDoesNotMakeInsnLive) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqRegReg>(vreg2, vreg1);
  builder.Gen<PseudoBranch>(bb);

  bb->live_out().push_back(vreg1);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);

  auto insn_it = bb->insn_list().rbegin();
  insn_it++;
  MachineInsn* insn = *insn_it++;
  MachineReg reg_after = insn->RegAt(0);
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpMovqRegImm, opcode_after);
  EXPECT_EQ(vreg1, reg_after);
}

TEST(MachineIRRemoveDeadCodeTest, UnusedRegErased) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<PseudoBranch>(bb);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 1UL);

  auto insn_it = bb->insn_list().begin();
  MachineInsn* insn = *insn_it++;
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpPseudoBranch, opcode_after);
}

TEST(MachineIRRemoveDeadCodeTest, DefKilledBySecondResultOfAnotherDef) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::AddbRegImm>(vreg1, 1, vreg3);
  builder.Gen<x86_64::AddbRegImm>(vreg2, 2, vreg3);
  builder.Gen<PseudoBranch>(bb);

  bb->live_out().push_back(vreg2);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);

  auto insn_it = bb->insn_list().begin();
  MachineInsn* insn = *insn_it++;
  MachineReg reg_after = insn->RegAt(0);
  MachineOpcode opcode_after = insn->opcode();
  EXPECT_EQ(kMachineOpAddbRegImm, opcode_after);
  EXPECT_EQ(vreg2, reg_after);
}

TEST(MachineIRRemoveDeadCodeTest, HardRegisterAccess) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::AddbRegImm>(x86_64::kMachineRegRAX, 3, x86_64::kMachineRegFLAGS);
  builder.Gen<PseudoBranch>(bb);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
}

TEST(MachineIRRemoveDeadCodeTest, CallImmArgIsLive) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  auto* bb = machine_ir.NewBasicBlock();
  x86_64::MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb);
  builder.GenCallImm(0,
                     machine_ir.AllocVReg(),
                     std::array<x86_64::CallImm::Arg, 1>{
                         {{machine_ir.AllocVReg(), x86_64::CallImm::kIntRegType}}});
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::RemoveDeadCode(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 4UL);
}

int GetInEdgeIndex(MachineBasicBlock* dst_bb, MachineBasicBlock* src_bb) {
  for (size_t i = 0; i < dst_bb->in_edges().size(); i++) {
    if (dst_bb->in_edges()[i]->src() == src_bb) {
      return i;
    }
  }
  return -1;
}

int GetOutEdgeIndex(MachineBasicBlock* src_bb, MachineBasicBlock* dst_bb) {
  for (size_t i = 0; i < src_bb->out_edges().size(); i++) {
    if (src_bb->out_edges()[i]->dst() == dst_bb) {
      return i;
    }
  }
  return -1;
}

TEST(MachineIR, RemoveCriticalEdge) {
  Arena arena;
  berberis::x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1   bb2
  //   \  /  \
  //   bb3   bb4
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb3);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb2, bb4);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb3, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::RemoveCriticalEdges(&machine_ir);
  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(bb3->in_edges().size(), 2UL);
  int bb1_index_in_bb3 = GetInEdgeIndex(bb3, bb1);
  ASSERT_NE(bb1_index_in_bb3, -1);
  auto new_bb = bb3->in_edges()[1 - bb1_index_in_bb3]->src();

  ASSERT_EQ(bb2->out_edges().size(), 2UL);
  int bb4_index_in_bb2 = GetOutEdgeIndex(bb2, bb4);
  ASSERT_NE(bb4_index_in_bb2, -1);
  EXPECT_EQ(new_bb, bb2->out_edges()[1 - bb4_index_in_bb2]->dst());
}

TEST(MachineIR, RemoveCriticalEdgeLoop) {
  Arena arena;
  berberis::x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1
  //  |
  // bb2 <---
  //  |  \__/
  // bb3
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb2);
  machine_ir.AddEdge(bb2, bb3);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::RemoveCriticalEdges(&machine_ir);
  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(bb2->in_edges().size(), 2UL);
  int bb1_index_in_bb2 = GetInEdgeIndex(bb2, bb1);
  ASSERT_NE(bb1_index_in_bb2, -1);
  auto new_bb = bb2->in_edges()[1 - bb1_index_in_bb2]->src();

  ASSERT_EQ(bb2->out_edges().size(), 2UL);
  int bb3_index_in_bb2 = GetOutEdgeIndex(bb2, bb3);
  ASSERT_NE(bb3_index_in_bb2, -1);
  EXPECT_EQ(new_bb, bb2->out_edges()[1 - bb3_index_in_bb2]->dst());
}

TEST(MachineIR, RemoveCriticalEdgeRecovery) {
  Arena arena;
  berberis::x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1   bb2
  //   \  /  \
  //   bb3  bb4(recovery)
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb3);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb2, bb4);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb4);
  bb4->MarkAsRecovery();
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::RemoveCriticalEdges(&machine_ir);
  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(bb3->in_edges().size(), 2UL);
  int bb1_index_in_bb3 = GetInEdgeIndex(bb3, bb1);
  ASSERT_NE(bb1_index_in_bb3, -1);
  auto new_bb = bb3->in_edges()[1 - bb1_index_in_bb3]->src();

  ASSERT_EQ(bb2->out_edges().size(), 2UL);
  int bb4_index_in_bb2 = GetOutEdgeIndex(bb2, bb4);
  ASSERT_NE(bb4_index_in_bb2, -1);
  EXPECT_EQ(new_bb, bb2->out_edges()[1 - bb4_index_in_bb2]->dst());

  ASSERT_EQ(bb2->insn_list().size(), 1UL);
  ASSERT_EQ(bb2->insn_list().front()->opcode(), kMachineOpPseudoBranch);
  ASSERT_EQ(static_cast<PseudoBranch*>(bb2->insn_list().front())->then_bb(), new_bb);
}

TEST(MachineIR, PutsInSuccessorsKillPut) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveRedundantPut(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(1u, bb1->insn_list().size());
  ASSERT_EQ(2u, bb2->insn_list().size());
  ASSERT_EQ(2u, bb3->insn_list().size());
}

TEST(MachineIR, PutInOneOfTwoSuccessorsDoesNotKillPut) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveRedundantPut(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(2u, bb1->insn_list().size());
  ASSERT_EQ(2u, bb2->insn_list().size());
  ASSERT_EQ(1u, bb3->insn_list().size());
}

TEST(MachineIR, MultiplePutsCanBeKilled) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg1 = machine_ir.AllocVReg();
  auto vreg2 = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.GenPut(GetThreadStateRegOffset(1), vreg2);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.GenPut(GetThreadStateRegOffset(1), vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.GenPut(GetThreadStateRegOffset(1), vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveRedundantPut(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(1u, bb1->insn_list().size());
  ASSERT_EQ(3u, bb2->insn_list().size());
  ASSERT_EQ(3u, bb3->insn_list().size());
}

TEST(MachineIR, GetInOneOfTheSuccessorsMakesPutLive) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.GenGet(vreg, GetThreadStateRegOffset(0));
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveRedundantPut(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  ASSERT_EQ(2u, bb1->insn_list().size());
  ASSERT_EQ(3u, bb2->insn_list().size());
  ASSERT_EQ(2u, bb3->insn_list().size());
}

TEST(MachineIR, ForwardingPseudoBranch) {
  // We create:
  //
  // BB0 -> BB1
  // BB1 (forwarder)
  // BB2
  //
  // We verify that the jump to BB1 is redirected to BB2.

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb2);

  builder.StartBasicBlock(bb0);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRAX, 23);
  builder.Gen<PseudoBranch>(bb1);

  // Create a forwarder block
  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we have exactly two basic blocks.
  EXPECT_EQ(2u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();

  // Verify that BB0 contains exactly two instructions.
  EXPECT_EQ(bb0, *bb_it);
  EXPECT_EQ(2u, bb0->insn_list().size());

  // Verify that the last instruction is PseudoBranch that jumps
  // to BB2.
  MachineInsn* bb0_insn = bb0->insn_list().back();
  EXPECT_EQ(kMachineOpPseudoBranch, bb0_insn->opcode());
  PseudoBranch* bb0_branch_insn = static_cast<PseudoBranch*>(bb0_insn);
  EXPECT_EQ(bb2, bb0_branch_insn->then_bb());

  // Check for BB2.  Note that RemoveForwarderBlocks deletes BB1.
  EXPECT_EQ(bb2, *(++bb_it));
}

TEST(MachineIR, ForwardingPseudoCondBranchThen) {
  // We create:
  //
  // BB0 (cond jump)-> BB1 (then_bb) and BB3 (else_bb)
  // BB1 (forwarder)
  // BB2
  // BB3
  //
  // We verify that the jump to BB1 is redirected to BB2.

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb0, bb3);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb1, bb3, x86_64::kMachineRegFLAGS);

  // Create a forwarder block
  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRAX, 23);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we have exactly three basic blocks.
  EXPECT_EQ(3u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();

  // Verify that BB0 contains exactly one instruction.
  EXPECT_EQ(bb0, *bb_it);
  EXPECT_EQ(1u, bb0->insn_list().size());

  // Verify that the sole instruction is PseudoCondBranch that jumps
  // to BB2 (then_bb) and BB3 (else_bb).
  MachineInsn* bb0_insn = bb0->insn_list().front();
  EXPECT_EQ(kMachineOpPseudoCondBranch, bb0_insn->opcode());
  PseudoCondBranch* bb0_branch_insn = static_cast<PseudoCondBranch*>(bb0_insn);
  EXPECT_EQ(bb2, bb0_branch_insn->then_bb());
  EXPECT_EQ(bb3, bb0_branch_insn->else_bb());

  // Check for BB2.  Note that RemoveForwarderBlocks deletes BB1.
  EXPECT_EQ(bb2, *(++bb_it));

  // Check for BB3.
  EXPECT_EQ(bb3, *(++bb_it));
}

TEST(MachineIR, ForwardingPseudoCondBranchElse) {
  // We create:
  //
  // BB0 (cond jump)-> BB1 (then_bb) and BB2 (else_bb)
  // BB1
  // BB2 (forwarder)
  // BB3
  //
  // We verify that the jump to BB2 is redirected to BB3.

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb0, bb2);
  machine_ir.AddEdge(bb2, bb3);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb1, bb2, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRAX, 23);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  // Create a forwarder block
  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we have exactly three basic blocks.
  EXPECT_EQ(3u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();

  // Verify that BB0 contains exactly one instruction.
  EXPECT_EQ(bb0, *bb_it);
  EXPECT_EQ(1u, bb0->insn_list().size());

  // Verify that the sole instruction is PseudoCondBranch that jumps
  // to BB1 (then_bb) and BB3 (else_bb).
  MachineInsn* bb0_insn = bb0->insn_list().front();
  EXPECT_EQ(kMachineOpPseudoCondBranch, bb0_insn->opcode());
  PseudoCondBranch* bb0_branch_insn = static_cast<PseudoCondBranch*>(bb0_insn);
  EXPECT_EQ(bb1, bb0_branch_insn->then_bb());
  EXPECT_EQ(bb3, bb0_branch_insn->else_bb());

  // Check for BB1.
  EXPECT_EQ(bb1, *(++bb_it));

  // Check for BB3.  Note that RemoveForwarderBlocks deletes BB2.
  EXPECT_EQ(bb3, *(++bb_it));
}

TEST(MachineIR, EntryForwarderIsNotRemoved) {
  // BB0 (entry forwarder) -> BB2
  // BB1
  // BB2

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb2);
  machine_ir.AddEdge(bb1, bb2);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb2);

  // Create a forwarder block
  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRAX, 29);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we still have exactly three basic blocks.
  EXPECT_EQ(3u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();

  // Check for BB0.
  EXPECT_EQ(bb0, *bb_it);

  // Check for BB1.
  EXPECT_EQ(bb1, *(++bb_it));

  // Check for BB2.
  EXPECT_EQ(bb2, *(++bb_it));
}

TEST(MachineIR, SelfForwarderIsNotRemoved) {
  // We add entry block BB0 so that BB1 is skipped because it's self-forwarding,
  // and not because it's the entry block
  //
  // BB0
  // BB1 -> BB1 (self-forwarder)

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb1);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb1);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(machine_ir.bb_list().size(), 2u);

  auto bb_it = machine_ir.bb_list().begin();

  // Check for BB0.
  EXPECT_EQ(bb0, *bb_it);

  // Check for BB1.
  EXPECT_EQ(bb1, *(++bb_it));
}

TEST(MachineIR, ForwarderLoopIsNotRemoved) {
  // We add entry block BB0 so that entry exception doesn't apply to loop nodes.
  //
  // BB0
  // BB1 (forwarder)
  // BB2 -> BB1 (forwarder)
  //
  // After BB1 is removed, BB2 becomes self-forwarder and should not be removed.

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb1);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb1);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(machine_ir.bb_list().size(), 2u);

  auto bb_it = machine_ir.bb_list().begin();

  EXPECT_EQ(bb0, *bb_it);
  EXPECT_EQ(bb2, *(++bb_it));
}

TEST(MachineIR, RemoveConsecutiveForwarderBlocks) {
  // We create:
  //
  // BB0 (cond jump)->  BB3
  // BB1
  // BB2 (forwarder)
  // BB3 (forwarder)
  // BB4
  // BB5
  //
  // Tested cases:
  //   1) regular -> forwarder -> forwarder
  //   2) cond else -> forwarder -> regular
  //
  // Not tested: cond then -> forwarder, loops, forwarder is the first bb in list
  //
  // Attention: forwarder loops are not allowed

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  auto* bb4 = machine_ir.NewBasicBlock();
  auto* bb5 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb0, bb3);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb4);
  machine_ir.AddEdge(bb4, bb5);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb1, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRAX, 23);
  builder.Gen<PseudoBranch>(bb2);

  // Create a forwarder block.
  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoCopy>(x86_64::kMachineRegRAX, x86_64::kMachineRegRAX, 4);
  builder.Gen<PseudoCopy>(x86_64::kMachineRegRBX, x86_64::kMachineRegRBX, 4);
  builder.Gen<PseudoBranch>(bb3);

  // Create another forwarder block.
  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoBranch>(bb4);

  builder.StartBasicBlock(bb4);
  builder.Gen<x86_64::MovlRegImm>(x86_64::kMachineRegRBX, 7);
  builder.Gen<PseudoBranch>(bb5);

  builder.StartBasicBlock(bb5);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  RemoveNopPseudoCopy(&machine_ir);
  x86_64::RemoveForwarderBlocks(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we have exactly four basic blocks left after two
  // forwarder blocks are removed.
  //
  // BB0 (cond jump)->  BB4 (target changed)
  // BB1 (target changed)
  // BB4
  // BB5
  EXPECT_EQ(4u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();

  // Verify that BB0 jumps to BB1 (then_bb) and BB4 (else_bb).
  EXPECT_EQ(bb0, *bb_it);
  MachineInsn* bb0_last_insn = bb0->insn_list().back();
  EXPECT_EQ(kMachineOpPseudoCondBranch, bb0_last_insn->opcode());
  PseudoCondBranch* bb0_branch_insn = static_cast<PseudoCondBranch*>(bb0_last_insn);
  EXPECT_EQ(bb1, bb0_branch_insn->then_bb());
  EXPECT_EQ(bb4, bb0_branch_insn->else_bb());

  // Verify that BB1 jumps to BB4.
  EXPECT_EQ(bb1, *(++bb_it));
  MachineInsn* bb1_last_insn = bb1->insn_list().back();
  EXPECT_EQ(kMachineOpPseudoBranch, bb1_last_insn->opcode());
  PseudoBranch* bb1_branch_insn = static_cast<PseudoBranch*>(bb1_last_insn);
  EXPECT_EQ(bb4, bb1_branch_insn->then_bb());

  // Check for BB4.  Note that RemoveForwarderBlocks deletes BB2 and
  // BB3.
  EXPECT_EQ(bb4, *(++bb_it));

  // Check for BB5.
  EXPECT_EQ(bb5, *(++bb_it));
}

TEST(MachineIR, RemoveNopPseudoCopy) {
  // Verify that RemoveNopPseudoCopy removes PseudoCopy instructions
  // with identical source and destination operands while retaining
  // all other instructions.

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  auto* bb0 = machine_ir.NewBasicBlock();
  x86_64::MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoCopy>(x86_64::kMachineRegRAX, x86_64::kMachineRegRAX, 4);
  builder.Gen<PseudoCopy>(x86_64::kMachineRegRBX, x86_64::kMachineRegRCX, 4);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  RemoveNopPseudoCopy(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Verify that we have exactly one basic block.
  EXPECT_EQ(1u, machine_ir.bb_list().size());

  // Verify that bb0 contains exactly two instructions.
  EXPECT_EQ(bb0, machine_ir.bb_list().front());
  EXPECT_EQ(2u, bb0->insn_list().size());

  auto insn_it = bb0->insn_list().begin();

  // Verify that the first instruction is PseudoCopy that copies ECX
  // to EBX.
  MachineInsn* insn0 = *insn_it;
  EXPECT_EQ(kMachineOpPseudoCopy, insn0->opcode());
  EXPECT_EQ(x86_64::kMachineRegRBX, insn0->RegAt(0));
  EXPECT_EQ(x86_64::kMachineRegRCX, insn0->RegAt(1));

  // Verify that the next instruction is PseudoJump.
  MachineInsn* insn1 = *(++insn_it);
  EXPECT_EQ(kMachineOpPseudoJump, insn1->opcode());
}

TEST(MachineIR, ReorderBasicBlocksInReversePostOrder) {
  //       |----|
  //       v    |
  // BB0  BB1  BB2
  //  |         ^
  //  |---------|
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb0 = machine_ir.NewBasicBlock();
  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  machine_ir.AddEdge(bb0, bb2);
  machine_ir.AddEdge(bb2, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb1);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ReorderBasicBlocksInReversePostOrder(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(3u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();
  EXPECT_EQ(bb0, *bb_it);
  EXPECT_EQ(bb2, *(++bb_it));
  EXPECT_EQ(bb1, *(++bb_it));
}

TEST(MachineIR, ReorderDiamondControlFlowInReversePostOrder) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, bb4] = BuildDiamondControlFlow(&machine_ir);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ReorderBasicBlocksInReversePostOrder(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(4u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();
  auto* enter_bb = *bb_it++;
  auto* then_bb = *bb_it++;
  auto* else_bb = *bb_it++;
  auto* merge_bb = *bb_it++;
  EXPECT_EQ(enter_bb, bb1);
  // `Then` and `else` are not strictly ordered by RPO.
  if (then_bb == bb2) {
    EXPECT_EQ(else_bb, bb3);
  } else {
    EXPECT_EQ(then_bb, bb3);
    EXPECT_EQ(else_bb, bb2);
  }
  EXPECT_EQ(merge_bb, bb4);
}

TEST(MachineIR, ReorderControlFlowWithLoopInReversePostOrder) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, bb4, unused_vreg] = BuildDataFlowAcrossEmptyLoop(&machine_ir);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ReorderBasicBlocksInReversePostOrder(&machine_ir);
  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(4u, machine_ir.bb_list().size());

  auto bb_it = machine_ir.bb_list().begin();
  auto* enter_bb = *bb_it++;
  auto* loop_head_bb = *bb_it++;
  auto* then_bb = *bb_it++;
  auto* else_bb = *bb_it++;
  EXPECT_EQ(enter_bb, bb1);
  EXPECT_EQ(loop_head_bb, bb2);
  // `Then` and `else` are not strictly ordered by RPO.
  // Note that loop may be separated by the post loop code.
  if (then_bb == bb3) {
    EXPECT_EQ(else_bb, bb4);
  } else {
    EXPECT_EQ(then_bb, bb4);
    EXPECT_EQ(else_bb, bb3);
  }
}

}  // namespace

}  // namespace berberis
