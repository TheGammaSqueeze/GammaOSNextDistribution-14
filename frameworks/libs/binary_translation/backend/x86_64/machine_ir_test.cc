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

namespace berberis {

namespace {

TEST(MachineIR, SplitBasicBlock) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);

  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, 0);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, 0);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, 1);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, 1);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, 1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  std::advance(insn_it, 2);
  auto new_bb = machine_ir.SplitBasicBlock(bb, insn_it);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  EXPECT_TRUE(machine_ir.bb_list().size() == 2);
  EXPECT_EQ(bb->insn_list().size(), static_cast<unsigned int>(3));
  EXPECT_EQ(bb->insn_list().back()->opcode(), kMachineOpPseudoBranch);
  EXPECT_EQ(new_bb->insn_list().size(), static_cast<unsigned int>(4));
}

TEST(MachineIR, SplitBasicBlockWithOutcomingEdges) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  MachineReg vreg = machine_ir.AllocVReg();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb1, bb3);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = std::next(bb1->insn_list().begin());
  MachineBasicBlock* new_bb = machine_ir.SplitBasicBlock(bb1, insn_it);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  EXPECT_EQ(bb1->out_edges().size(), 1UL);
  EXPECT_EQ(bb1->out_edges().front()->src(), bb1);
  EXPECT_EQ(bb1->out_edges().front()->dst(), new_bb);

  EXPECT_EQ(new_bb->in_edges().size(), 1UL);
  EXPECT_EQ(new_bb->in_edges().front()->src(), bb1);
  EXPECT_EQ(new_bb->in_edges().front()->dst(), new_bb);
  EXPECT_EQ(new_bb->out_edges().size(), 2UL);
  EXPECT_EQ(new_bb->out_edges().front()->src(), new_bb);
  EXPECT_EQ(new_bb->out_edges().front()->dst(), bb2);
  EXPECT_EQ(new_bb->out_edges().back()->src(), new_bb);
  EXPECT_EQ(new_bb->out_edges().back()->dst(), bb3);

  EXPECT_EQ(bb2->in_edges().size(), 1UL);
  EXPECT_EQ(bb2->in_edges().front()->src(), new_bb);
  EXPECT_EQ(bb2->in_edges().front()->dst(), bb2);

  EXPECT_EQ(bb3->in_edges().size(), 1UL);
  EXPECT_EQ(bb3->in_edges().front()->src(), new_bb);
  EXPECT_EQ(bb3->in_edges().front()->dst(), bb3);
}

}  // namespace

}  // namespace berberis
