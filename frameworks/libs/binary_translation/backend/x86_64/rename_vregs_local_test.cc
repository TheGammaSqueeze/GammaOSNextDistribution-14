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

#include "berberis/backend/x86_64/rename_vregs_local.h"

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

TEST(RenameVRegsLocalTest, NothingRenamed) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegImm>(vreg2, 0);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);
  bb->live_out().push_back(vreg2);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 3UL);

  auto insn_it = bb->insn_list().begin();

  MachineInsn* insn = *insn_it;
  EXPECT_EQ(vreg1, insn->RegAt(0));

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg2, insn->RegAt(0));
}

TEST(RenameVRegsLocalTest, LiveInRenamed) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegReg>(vreg2, MachineReg{4});
  builder.Gen<x86_64::MovqRegReg>(vreg1, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_in().push_back(vreg2);

  bb->live_out().push_back(vreg1);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 3UL);

  auto insn_it = bb->insn_list().begin();

  MachineInsn* insn = *insn_it;
  MachineReg vreg2_renamed = insn->RegAt(0);
  EXPECT_NE(vreg2, vreg2_renamed);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg2_renamed, insn->RegAt(1));
}

TEST(RenameVRegsLocalTest, SecondDefRenamed) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegReg>(vreg2, vreg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);
  bb->live_out().push_back(vreg2);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 4UL);

  auto insn_it = bb->insn_list().begin();

  MachineInsn* insn = *insn_it;
  EXPECT_EQ(vreg1, insn->RegAt(0));

  insn_it++;
  insn = *insn_it;
  MachineReg vreg1_renamed = insn->RegAt(0);
  EXPECT_NE(vreg1, vreg1_renamed);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg1_renamed, insn->RegAt(1));
}

TEST(RenameVRegsLocalTest, ThirdDefRenamed) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegReg>(vreg2, vreg1);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 3);
  builder.Gen<x86_64::MovqRegReg>(vreg2, vreg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 6UL);

  auto insn_it = bb->insn_list().begin();

  insn_it++;
  insn_it++;
  MachineInsn* insn = *insn_it;
  MachineReg vreg1_renamed1 = insn->RegAt(1);
  insn_it++;
  insn = *insn_it;
  MachineReg vreg1_renamed2 = insn->RegAt(0);
  EXPECT_NE(vreg1_renamed1, vreg1_renamed2);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg1_renamed2, insn->RegAt(1));
}

TEST(RenameVRegsLocalTest, SecondDefOfDefAndUseReg) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::AddqRegReg>(vreg1, vreg2, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 4UL);

  auto insn_it = bb->insn_list().begin();

  insn_it++;
  MachineInsn* insn = *insn_it;
  EXPECT_EQ(insn->opcode(), kMachineOpMovqRegReg);
  MachineReg vreg1_renamed = insn->RegAt(0);
  MachineReg vreg1_original = insn->RegAt(1);
  EXPECT_EQ(vreg1_original, vreg1);
  EXPECT_NE(vreg1_original, vreg1_renamed);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg1_renamed, insn->RegAt(0));
}

TEST(RenameVRegsLocalTest, ThirdDefOfDefAndUseReg) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 3);
  builder.Gen<x86_64::AddqRegReg>(vreg1, vreg2, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb->live_out().push_back(vreg1);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb->insn_list().size(), 5UL);

  auto insn_it = bb->insn_list().begin();

  insn_it++;
  MachineInsn* insn = *insn_it;
  MachineReg vreg_renamed1 = insn->RegAt(0);
  EXPECT_NE(vreg_renamed1, vreg1);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(insn->opcode(), kMachineOpMovqRegReg);
  MachineReg vreg_renamed1_use = insn->RegAt(1);
  MachineReg vreg_renamed2 = insn->RegAt(0);
  EXPECT_EQ(vreg_renamed1, vreg_renamed1_use);
  EXPECT_NE(vreg_renamed2, vreg_renamed1_use);

  insn_it++;
  insn = *insn_it;
  EXPECT_EQ(vreg_renamed2, insn->RegAt(0));
}

TEST(RenameVRegsLocalTest, LiveOutsAndLiveInsRenamed) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 4);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegReg>(vreg2, vreg1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  bb1->live_out().push_back(vreg1);
  bb1->live_out().push_back(vreg2);

  bb2->live_in().push_back(vreg1);
  bb2->live_in().push_back(vreg2);

  machine_ir.AddEdge(bb1, bb2);

  x86_64::RenameVRegsLocal(&machine_ir);

  EXPECT_EQ(bb1->insn_list().size(), 4UL);
  EXPECT_EQ(bb2->insn_list().size(), 2UL);

  MachineReg new_vreg1 = bb1->live_out()[0];
  MachineReg new_vreg2 = bb1->live_out()[1];

  EXPECT_NE(new_vreg1, vreg1);
  EXPECT_EQ(new_vreg2, vreg2);

  auto insn_it = bb2->insn_list().begin();

  EXPECT_EQ(new_vreg1, bb2->live_in()[0]);
  EXPECT_EQ(new_vreg2, bb2->live_in()[1]);

  MachineInsn* insn = *insn_it;
  EXPECT_EQ(insn->opcode(), kMachineOpMovqRegReg);
  EXPECT_EQ(vreg1, insn->RegAt(0));
  EXPECT_EQ(new_vreg1, insn->RegAt(1));
}

}  // namespace

}  // namespace berberis