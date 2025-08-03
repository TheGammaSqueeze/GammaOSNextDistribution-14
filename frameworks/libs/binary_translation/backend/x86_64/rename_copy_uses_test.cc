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

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/guest_state/guest_addr.h"

#include "berberis/backend/x86_64/rename_copy_uses.h"

namespace berberis::x86_64 {

namespace {

TEST(MachineIRRenameCopyUsesMapTest, Basic) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  auto* copy_insn = builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUsesMap map(&machine_ir);
  map.StartBasicBlock(bb);

  // Renaming doesn't do anything for not mapped registers.
  map.RenameUseIfMapped(copy_insn, 1);
  EXPECT_EQ(copy_insn->RegAt(1), vreg2);

  // This should map vreg1 -> vreg2.
  map.ProcessCopy(copy_insn);

  // Now it should rename vreg1.
  map.RenameUseIfMapped(add_insn, 1);
  EXPECT_EQ(add_insn->RegAt(1), vreg2);
}

TEST(MachineIRRenameCopyUsesTest, Basic) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);
  EXPECT_EQ(add_insn->RegAt(1), vreg2);
}

TEST(MachineIRRenameCopyUsesTest, RenameCopyChain) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();
  MachineReg vreg4 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  builder.Gen<PseudoCopy>(vreg3, vreg1, 8);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg4, vreg3, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);
  EXPECT_EQ(add_insn->RegAt(1), vreg2);
}

TEST(MachineIRRenameCopyUsesTest, DoNotRenameIfCopySourceRedefined) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  builder.Gen<x86_64::SubqRegImm>(vreg2, 1, kMachineRegFLAGS);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);

  // vreg1 is not renamed since vreg2 is redefined after copy.
  EXPECT_EQ(add_insn->RegAt(1), vreg1);
}

TEST(MachineIRRenameCopyUsesTest, DoNotRenameIfCopyResultRedefined) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  builder.Gen<x86_64::SubqRegImm>(vreg1, 1, kMachineRegFLAGS);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);
  // vreg1 is not renamed since it is redefined after copy.
  EXPECT_EQ(add_insn->RegAt(1), vreg1);
}

TEST(MachineIRRenameCopyUsesTest, DoNotRenameNarrowRegClass) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  auto* shift_insn = builder.Gen<x86_64::ShrqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  // Builder normally doesn't allow constructing CallImmArg without CallImm, so we construct in IR
  // directly.
  auto* call_arg_insn = builder.ir()->NewInsn<CallImmArg>(vreg1, CallImm::RegType::kIntType);
  bb->insn_list().push_back(call_arg_insn);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(bb->insn_list().size(), 4u);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);
  // vreg1 is not renamed since Shrq second operand is CL register - narrow class.
  EXPECT_EQ(shift_insn->RegAt(1), vreg1);
  // vreg1 is not renamed since CallImmArg implicitly has narrow class.
  EXPECT_EQ(call_arg_insn->RegAt(0), vreg1);
}

TEST(MachineIRRenameCopyUsesTest, GracefullyIgnoreHardwareRegs) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(kMachineRegRAX, kMachineRegRBX, 8);
  auto* add_insn =
      builder.Gen<x86_64::AddqRegReg>(kMachineRegRCX, kMachineRegRAX, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  RenameCopyUses(&machine_ir);
  // Nothing is renamed.
  EXPECT_EQ(add_insn->RegAt(1), kMachineRegRAX);
}

TEST(MachineIRRenameCopyUsesTest, RenameCopySourceIfResultIsLiveout) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();
  MachineReg vreg3 = machine_ir.AllocVReg();
  MachineReg vreg4 = machine_ir.AllocVReg();

  builder.StartBasicBlock(bb);
  builder.Gen<PseudoCopy>(vreg1, vreg2, 8);
  auto* add_insn = builder.Gen<x86_64::AddqRegReg>(vreg3, vreg1, kMachineRegFLAGS);
  auto* sub_insn = builder.Gen<x86_64::SubqRegReg>(vreg4, vreg2, kMachineRegFLAGS);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), kMachineIRCheckSuccess);

  bb->live_out().push_back(vreg1);

  RenameCopyUses(&machine_ir);
  // Should not rename vreg1.
  EXPECT_EQ(add_insn->RegAt(1), vreg1);
  // Should rename vreg2.
  EXPECT_EQ(sub_insn->RegAt(1), vreg1);
}

}  // namespace

}  // namespace berberis::x86_64
