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

#include "berberis/backend/x86_64/loop_guest_context_optimizer.h"

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/guest_state/guest_state_opaque.h"

#include "x86_64/loop_guest_context_optimizer_test_checks.h"

namespace berberis::x86_64 {

namespace {

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetAndUpdateMap) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenGet(reg1, GetThreadStateRegOffset(0));
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovq, false);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplacePutAndUpdateMap) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenPut(GetThreadStateRegOffset(1), reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateRegOffset(1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovq, true);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetPutAndUpdateMap) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  auto reg2 = machine_ir.AllocVReg();
  builder.GenGet(reg1, GetThreadStateRegOffset(1));
  builder.GenPut(GetThreadStateRegOffset(1), reg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ReplacePutAndUpdateMap(&machine_ir, std::next(insn_it), mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 3UL);
  auto* get_copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(get_copy_insn, reg1);
  auto* put_copy_insn = *std::next(bb->insn_list().begin());
  auto mapped_reg_in_put = CheckCopyPutInsnAndObtainMappedReg(put_copy_insn, reg2);
  EXPECT_EQ(mapped_reg, mapped_reg_in_put);

  auto offset = GetThreadStateRegOffset(1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovq, true);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetSimdAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedSimdRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have SIMD registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenGetSimd<16>(reg1, GetThreadStateSimdRegOffset(0));
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateSimdRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovdqa, false);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplacePutSimdAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedSimdRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have SIMD registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenSetSimd<16>(GetThreadStateSimdRegOffset(0), reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateSimdRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovdqa, true);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetFAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedFpRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have dedicated Fp registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenGetSimd<8>(reg1, GetThreadStateFRegOffset(0));
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateFRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovsd, false);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplacePutFAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedFpRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have dedicated Fp registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenSetSimd<8>(GetThreadStateFRegOffset(0), reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateFRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovsd, true);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetVAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedVecRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have Vector registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenGetSimd<16>(reg1, GetThreadStateVRegOffset(0));
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateVRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovdqa, false);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplacePutVAndUpdateMap) {
  if (!DoesCpuStateHaveDedicatedVecRegs()) {
    GTEST_SKIP() << "Guest CPU doesn't have Vector registers";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  builder.GenSetSimd<16>(GetThreadStateVRegOffset(0), reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);

  auto offset = GetThreadStateVRegOffset(0);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovdqa, true);
}

TEST(MachineIRLoopGuestContextOptimizerRiscv64, ReplaceGetMovwAndUpdateMap) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  auto offset = 0;
  builder.Gen<MovwRegMemBaseDisp>(reg1, kMachineRegRBP, offset);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovw, false);
}

TEST(MachineIRLoopGuestContextOptimizerRiscv64, ReplacePutMovwAndUpdateMap) {
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  auto offset = 0;
  builder.Gen<MovwMemBaseDispReg>(kMachineRegRBP, offset, reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovw, true);
}

TEST(MachineIRLoopGuestContextOptimizer, GenerateGetInsns) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();

  // Add an out-edge for the CHECK in GenerateGetInsns.
  auto* dst = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb, dst);

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  auto reg2 = machine_ir.AllocVReg();
  auto reg3 = machine_ir.AllocVReg();
  auto reg4 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, false};
  MappedRegInfo mapped_reg2 = {reg2, MovType::kMovdqa, false};
  MappedRegInfo mapped_reg3 = {reg3, MovType::kMovsd, false};
  MappedRegInfo mapped_reg4 = {reg4, MovType::kMovw, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    mem_reg_map[GetThreadStateSimdRegOffset(0)] = mapped_reg2;
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    mem_reg_map[GetThreadStateVRegOffset(0)] = mapped_reg2;
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    mem_reg_map[GetThreadStateFRegOffset(0)] = mapped_reg3;
  }
  if (DoesCpuStateHaveFlags()) {
    mem_reg_map[GetThreadStateFlagOffset()] = mapped_reg4;
  }

  GenerateGetInsns(&machine_ir, bb, mem_reg_map);

  EXPECT_EQ(
      bb->insn_list().size(),
      (DoesCpuStateHaveFlags() ? 2UL : 1UL) + (DoesCpuStateHaveDedicatedFpRegs() ? 1UL : 0UL) +
          ((DoesCpuStateHaveDedicatedSimdRegs() || DoesCpuStateHaveDedicatedVecRegs()) ? 1UL
                                                                                       : 0UL));
  auto insn_it = bb->insn_list().begin();
  CheckGetInsn(*insn_it, kMachineOpMovqRegMemBaseDisp, reg1, GetThreadStateRegOffset(0));
  std::advance(insn_it, 1);
  if (DoesCpuStateHaveFlags()) {
    CheckGetInsn(*insn_it, kMachineOpMovwRegMemBaseDisp, reg4, GetThreadStateFlagOffset());
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovsdXRegMemBaseDisp, reg3, GetThreadStateFRegOffset(0));
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovdqaXRegMemBaseDisp, reg2, GetThreadStateSimdRegOffset(0));
    std::advance(insn_it, 1);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovdqaXRegMemBaseDisp, reg2, GetThreadStateVRegOffset(0));
    std::advance(insn_it, 1);
  }
}

TEST(MachineIRLoopGuestContextOptimizer, GeneratePutInsns) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();
  auto* src = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(src, bb);
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  auto reg2 = machine_ir.AllocVReg();
  auto reg3 = machine_ir.AllocVReg();
  auto reg4 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, true};
  MappedRegInfo mapped_reg2 = {reg2, MovType::kMovdqa, true};
  MappedRegInfo mapped_reg3 = {reg3, MovType::kMovsd, true};
  MappedRegInfo mapped_reg4 = {reg4, MovType::kMovw, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    mem_reg_map[GetThreadStateSimdRegOffset(0)] = mapped_reg2;
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    mem_reg_map[GetThreadStateVRegOffset(0)] = mapped_reg2;
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    mem_reg_map[GetThreadStateFRegOffset(0)] = mapped_reg3;
  }
  if (DoesCpuStateHaveFlags()) {
    mem_reg_map[GetThreadStateFlagOffset()] = mapped_reg4;
  }

  GeneratePutInsns(&machine_ir, bb, mem_reg_map);

  EXPECT_EQ(
      bb->insn_list().size(),
      (DoesCpuStateHaveFlags() ? 2UL : 1UL) + (DoesCpuStateHaveDedicatedFpRegs() ? 1UL : 0UL) +
          ((DoesCpuStateHaveDedicatedSimdRegs() || DoesCpuStateHaveDedicatedVecRegs()) ? 1UL
                                                                                       : 0UL));
  auto insn_it = bb->insn_list().begin();
  CheckPutInsn(*insn_it, kMachineOpMovqMemBaseDispReg, reg1, GetThreadStateRegOffset(0));
  std::advance(insn_it, 1);
  if (DoesCpuStateHaveFlags()) {
    CheckPutInsn(*insn_it, kMachineOpMovwMemBaseDispReg, reg4, GetThreadStateFlagOffset());
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovsdMemBaseDispXReg, reg3, GetThreadStateFRegOffset(0));
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovdqaMemBaseDispXReg, reg2, GetThreadStateSimdRegOffset(0));
    std::advance(insn_it, 1);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovdqaMemBaseDispXReg, reg2, GetThreadStateVRegOffset(0));
    std::advance(insn_it, 1);
  }
}

TEST(MachineIRLoopGuestContextOptimizer, GeneratePreloop) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* loop_body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, loop_body);
  machine_ir.AddEdge(loop_body, loop_body);
  machine_ir.AddEdge(loop_body, afterloop);

  MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(loop_body);
  builder.StartBasicBlock(loop_body);
  builder.Gen<PseudoCondBranch>(
      CodeEmitter::Condition::kZero, loop_body, afterloop, kMachineRegFLAGS);
  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  Loop loop(machine_ir.arena());
  loop.push_back(loop_body);

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  auto reg2 = machine_ir.AllocVReg();
  auto reg3 = machine_ir.AllocVReg();
  auto reg4 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, false};
  MappedRegInfo mapped_reg2 = {reg2, MovType::kMovdqa, false};
  MappedRegInfo mapped_reg3 = {reg3, MovType::kMovsd, false};
  MappedRegInfo mapped_reg4 = {reg4, MovType::kMovw, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    mem_reg_map[GetThreadStateSimdRegOffset(0)] = mapped_reg2;
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    mem_reg_map[GetThreadStateVRegOffset(0)] = mapped_reg2;
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    mem_reg_map[GetThreadStateFRegOffset(0)] = mapped_reg3;
  }
  if (DoesCpuStateHaveFlags()) {
    mem_reg_map[GetThreadStateFlagOffset()] = mapped_reg4;
  }

  GenerateGetsInPreloop(&machine_ir, &loop, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(
      preloop->insn_list().size(),
      (DoesCpuStateHaveFlags() ? 3UL : 2UL) + (DoesCpuStateHaveDedicatedFpRegs() ? 1UL : 0UL) +
          ((DoesCpuStateHaveDedicatedSimdRegs() || DoesCpuStateHaveDedicatedVecRegs()) ? 1UL
                                                                                       : 0UL));
  auto insn_it = preloop->insn_list().begin();
  CheckGetInsn(*insn_it, kMachineOpMovqRegMemBaseDisp, reg1, GetThreadStateRegOffset(0));
  std::advance(insn_it, 1);
  if (DoesCpuStateHaveFlags()) {
    CheckGetInsn(*insn_it, kMachineOpMovwRegMemBaseDisp, reg4, GetThreadStateFlagOffset());
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovsdXRegMemBaseDisp, reg3, GetThreadStateFRegOffset(0));
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovdqaXRegMemBaseDisp, reg2, GetThreadStateSimdRegOffset(0));
    std::advance(insn_it, 1);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    CheckGetInsn(*insn_it, kMachineOpMovdqaXRegMemBaseDisp, reg2, GetThreadStateVRegOffset(0));
    std::advance(insn_it, 1);
  }
}

TEST(MachineIRLoopGuestContextOptimizer, GenerateAfterloop) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* loop_body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, loop_body);
  machine_ir.AddEdge(loop_body, loop_body);
  machine_ir.AddEdge(loop_body, afterloop);

  MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(loop_body);
  builder.StartBasicBlock(loop_body);
  builder.Gen<PseudoCondBranch>(
      CodeEmitter::Condition::kZero, loop_body, afterloop, kMachineRegFLAGS);
  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  Loop loop(machine_ir.arena());
  loop.push_back(loop_body);

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  auto reg2 = machine_ir.AllocVReg();
  auto reg3 = machine_ir.AllocVReg();
  auto reg4 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, true};
  MappedRegInfo mapped_reg2 = {reg2, MovType::kMovdqa, true};
  MappedRegInfo mapped_reg3 = {reg3, MovType::kMovsd, true};
  MappedRegInfo mapped_reg4 = {reg4, MovType::kMovw, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    mem_reg_map[GetThreadStateSimdRegOffset(0)] = mapped_reg2;
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    mem_reg_map[GetThreadStateVRegOffset(0)] = mapped_reg2;
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    mem_reg_map[GetThreadStateFRegOffset(0)] = mapped_reg3;
  }
  if (DoesCpuStateHaveFlags()) {
    mem_reg_map[GetThreadStateFlagOffset()] = mapped_reg4;
  }

  GeneratePutsInPostloop(&machine_ir, &loop, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(
      afterloop->insn_list().size(),
      (DoesCpuStateHaveFlags() ? 3UL : 2UL) + (DoesCpuStateHaveDedicatedFpRegs() ? 1UL : 0UL) +
          ((DoesCpuStateHaveDedicatedSimdRegs() || DoesCpuStateHaveDedicatedVecRegs()) ? 1UL
                                                                                       : 0UL));
  auto insn_it = afterloop->insn_list().begin();
  CheckPutInsn(*insn_it, kMachineOpMovqMemBaseDispReg, reg1, GetThreadStateRegOffset(0));
  std::advance(insn_it, 1);
  if (DoesCpuStateHaveFlags()) {
    CheckPutInsn(*insn_it, kMachineOpMovwMemBaseDispReg, reg4, GetThreadStateFlagOffset());
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedFpRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovsdMemBaseDispXReg, reg3, GetThreadStateFRegOffset(0));
    std::advance(insn_it, 1);
  }
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovdqaMemBaseDispXReg, reg2, GetThreadStateSimdRegOffset(0));
    std::advance(insn_it, 1);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    CheckPutInsn(*insn_it, kMachineOpMovdqaMemBaseDispXReg, reg2, GetThreadStateVRegOffset(0));
    std::advance(insn_it, 1);
  }
}

TEST(MachineIRLoopGuestContextOptimizer, GenerateMultiplePreloops) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop1 = machine_ir.NewBasicBlock();
  auto* preloop2 = machine_ir.NewBasicBlock();
  auto* loop_body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop1, loop_body);
  machine_ir.AddEdge(preloop2, loop_body);
  machine_ir.AddEdge(loop_body, loop_body);
  machine_ir.AddEdge(loop_body, afterloop);

  MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(preloop1);
  builder.Gen<PseudoBranch>(loop_body);
  builder.StartBasicBlock(preloop2);
  builder.Gen<PseudoBranch>(loop_body);
  builder.StartBasicBlock(loop_body);
  builder.Gen<PseudoCondBranch>(
      CodeEmitter::Condition::kZero, loop_body, afterloop, kMachineRegFLAGS);
  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  Loop loop(machine_ir.arena());
  loop.push_back(loop_body);

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;

  GenerateGetsInPreloop(&machine_ir, &loop, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(preloop1->insn_list().size(), 2UL);
  auto insn_it = preloop1->insn_list().begin();
  CheckGetInsn(*insn_it, kMachineOpMovqRegMemBaseDisp, reg1, GetThreadStateRegOffset(0));

  EXPECT_EQ(preloop2->insn_list().size(), 2UL);
  insn_it = preloop2->insn_list().begin();
  CheckGetInsn(*insn_it, kMachineOpMovqRegMemBaseDisp, reg1, GetThreadStateRegOffset(0));
}

TEST(MachineIRLoopGuestContextOptimizer, GenerateMultiplePostloops) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* loop_body1 = machine_ir.NewBasicBlock();
  auto* loop_body2 = machine_ir.NewBasicBlock();
  auto* postloop1 = machine_ir.NewBasicBlock();
  auto* postloop2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, loop_body1);
  machine_ir.AddEdge(loop_body1, loop_body2);
  machine_ir.AddEdge(loop_body1, postloop1);
  machine_ir.AddEdge(loop_body2, loop_body1);
  machine_ir.AddEdge(loop_body2, postloop2);

  MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(loop_body1);
  builder.StartBasicBlock(loop_body1);
  builder.Gen<PseudoCondBranch>(
      CodeEmitter::Condition::kZero, loop_body2, postloop1, kMachineRegFLAGS);
  builder.StartBasicBlock(loop_body2);
  builder.Gen<PseudoCondBranch>(
      CodeEmitter::Condition::kZero, loop_body1, postloop2, kMachineRegFLAGS);
  builder.StartBasicBlock(postloop1);
  builder.Gen<PseudoJump>(kNullGuestAddr);
  builder.StartBasicBlock(postloop2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  Loop loop(machine_ir.arena());
  loop.push_back(loop_body1);
  loop.push_back(loop_body2);

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  auto reg1 = machine_ir.AllocVReg();
  MappedRegInfo mapped_reg1 = {reg1, MovType::kMovq, true};
  mem_reg_map[GetThreadStateRegOffset(0)] = mapped_reg1;

  GeneratePutsInPostloop(&machine_ir, &loop, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(postloop1->insn_list().size(), 2UL);
  auto insn_it = postloop1->insn_list().begin();
  CheckPutInsn(*insn_it, kMachineOpMovqMemBaseDispReg, reg1, GetThreadStateRegOffset(0));

  EXPECT_EQ(postloop2->insn_list().size(), 2UL);
  insn_it = postloop2->insn_list().begin();
  CheckPutInsn(*insn_it, kMachineOpMovqMemBaseDispReg, reg1, GetThreadStateRegOffset(0));
}

TEST(MachineIRLoopGuestContextOptimizer, RemovePutInSelfLoop) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body);
  machine_ir.AddEdge(body, body);
  machine_ir.AddEdge(body, afterloop);

  MachineReg vreg1 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body);

  builder.StartBasicBlock(body);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body, afterloop, kMachineRegFLAGS);

  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  RemoveLoopGuestContextAccesses(&machine_ir);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(preloop->insn_list().size(), 2UL);
  auto* get_insn = preloop->insn_list().front();
  EXPECT_EQ(get_insn->opcode(), kMachineOpMovqRegMemBaseDisp);
  auto mapped_reg = get_insn->RegAt(0);
  auto disp = AsMachineInsnX86_64(get_insn)->disp();
  EXPECT_EQ(disp, GetThreadStateRegOffset(0));

  EXPECT_EQ(body->insn_list().size(), 2UL);
  auto* copy_insn = body->insn_list().front();
  EXPECT_EQ(CheckCopyPutInsnAndObtainMappedReg(copy_insn, vreg1), mapped_reg);

  EXPECT_EQ(afterloop->insn_list().size(), 2UL);
  auto* put_insn = afterloop->insn_list().front();
  CheckPutInsn(put_insn, kMachineOpMovqMemBaseDispReg, mapped_reg, GetThreadStateRegOffset(0));
}

TEST(MachineIRLoopGuestContextOptimizer, RemoveGetInSelfLoop) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body);
  machine_ir.AddEdge(body, body);
  machine_ir.AddEdge(body, afterloop);

  MachineReg vreg1 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body);

  builder.StartBasicBlock(body);
  builder.GenGet(vreg1, GetThreadStateRegOffset(0));
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body, afterloop, kMachineRegFLAGS);

  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  RemoveLoopGuestContextAccesses(&machine_ir);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(preloop->insn_list().size(), 2UL);
  auto* get_insn = preloop->insn_list().front();
  EXPECT_EQ(get_insn->opcode(), kMachineOpMovqRegMemBaseDisp);
  auto mapped_reg = get_insn->RegAt(0);
  auto disp = AsMachineInsnX86_64(get_insn)->disp();
  EXPECT_EQ(disp, GetThreadStateRegOffset(0));

  EXPECT_EQ(body->insn_list().size(), 2UL);
  auto* copy_insn = body->insn_list().front();
  EXPECT_EQ(mapped_reg, CheckCopyGetInsnAndObtainMappedReg(copy_insn, vreg1));

  EXPECT_EQ(afterloop->insn_list().size(), 1UL);
}

TEST(MachineIRLoopGuestContextOptimizer, RemoveGetPutInSelfLoop) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body);
  machine_ir.AddEdge(body, body);
  machine_ir.AddEdge(body, afterloop);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body);

  builder.StartBasicBlock(body);
  builder.GenGet(vreg1, GetThreadStateRegOffset(0));
  builder.GenPut(GetThreadStateRegOffset(0), vreg2);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body, afterloop, kMachineRegFLAGS);

  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  RemoveLoopGuestContextAccesses(&machine_ir);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(preloop->insn_list().size(), 2UL);
  auto* get_insn = preloop->insn_list().front();
  EXPECT_EQ(get_insn->opcode(), kMachineOpMovqRegMemBaseDisp);
  auto mapped_reg = get_insn->RegAt(0);
  auto disp = AsMachineInsnX86_64(get_insn)->disp();
  EXPECT_EQ(disp, GetThreadStateRegOffset(0));

  EXPECT_EQ(body->insn_list().size(), 3UL);
  auto* copy_insn1 = body->insn_list().front();
  EXPECT_EQ(mapped_reg, CheckCopyGetInsnAndObtainMappedReg(copy_insn1, vreg1));
  auto* copy_insn2 = *(std::next(body->insn_list().begin()));
  EXPECT_EQ(mapped_reg, CheckCopyPutInsnAndObtainMappedReg(copy_insn2, vreg2));

  EXPECT_EQ(afterloop->insn_list().size(), 2UL);
  auto* put_insn = afterloop->insn_list().front();
  CheckPutInsn(put_insn, kMachineOpMovqMemBaseDispReg, mapped_reg, GetThreadStateRegOffset(0));
}

TEST(MachineIRLoopGuestContextOptimizer, RemovePutInLoopWithMultipleExits) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body1 = machine_ir.NewBasicBlock();
  auto* body2 = machine_ir.NewBasicBlock();
  auto* afterloop1 = machine_ir.NewBasicBlock();
  auto* afterloop2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body1);
  machine_ir.AddEdge(body1, body2);
  machine_ir.AddEdge(body1, afterloop1);
  machine_ir.AddEdge(body2, body1);
  machine_ir.AddEdge(body2, afterloop2);

  MachineReg vreg1 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body1);

  builder.StartBasicBlock(body1);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body2, afterloop1, kMachineRegFLAGS);

  builder.StartBasicBlock(body2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body1, afterloop2, kMachineRegFLAGS);

  builder.StartBasicBlock(afterloop1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(afterloop2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  RemoveLoopGuestContextAccesses(&machine_ir);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(preloop->insn_list().size(), 2UL);
  auto* get_insn = preloop->insn_list().front();
  EXPECT_EQ(get_insn->opcode(), kMachineOpMovqRegMemBaseDisp);
  auto mapped_reg = get_insn->RegAt(0);
  auto disp = AsMachineInsnX86_64(get_insn)->disp();
  EXPECT_EQ(disp, GetThreadStateRegOffset(0));

  EXPECT_EQ(body1->insn_list().size(), 1UL);
  EXPECT_EQ(body2->insn_list().size(), 2UL);
  auto* copy_insn = body2->insn_list().front();
  EXPECT_EQ(CheckCopyPutInsnAndObtainMappedReg(copy_insn, vreg1), mapped_reg);

  EXPECT_EQ(afterloop1->insn_list().size(), 2UL);
  auto* put_insn = afterloop1->insn_list().front();
  CheckPutInsn(put_insn, kMachineOpMovqMemBaseDispReg, mapped_reg, GetThreadStateRegOffset(0));

  EXPECT_EQ(afterloop2->insn_list().size(), 2UL);
  put_insn = afterloop2->insn_list().front();
  CheckPutInsn(put_insn, kMachineOpMovqMemBaseDispReg, mapped_reg, GetThreadStateRegOffset(0));
}

TEST(MachineIRLoopGuestContextOptimizer, CountGuestRegAccesses) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body1 = machine_ir.NewBasicBlock();
  auto* body2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body1);
  machine_ir.AddEdge(body1, body2);
  machine_ir.AddEdge(body2, body1);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body1);

  builder.StartBasicBlock(body1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    builder.GenGetSimd<16>(vreg2, GetThreadStateSimdRegOffset(0));
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    builder.GenGetSimd<16>(vreg2, GetThreadStateVRegOffset(0));
  }
  builder.Gen<PseudoBranch>(body2);

  builder.StartBasicBlock(body2);
  builder.GenGet(vreg1, GetThreadStateRegOffset(1));
  builder.GenPut(GetThreadStateRegOffset(1), vreg1);
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    builder.GenSetSimd<16>(GetThreadStateSimdRegOffset(0), vreg2);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    builder.GenSetSimd<16>(GetThreadStateVRegOffset(0), vreg2);
  }
  builder.Gen<PseudoBranch>(body1);

  Loop loop({body1, body2}, machine_ir.arena());
  auto guest_access_count = CountGuestRegAccesses(&machine_ir, &loop);
  EXPECT_EQ(guest_access_count[GetThreadStateRegOffset(0)], 1);
  EXPECT_EQ(guest_access_count[GetThreadStateRegOffset(1)], 2);
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    EXPECT_EQ(guest_access_count[GetThreadStateSimdRegOffset(0)], 2);
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    EXPECT_EQ(guest_access_count[GetThreadStateVRegOffset(0)], 2);
  }
}

TEST(MachineIRLoopGuestContextOptimizer, GetOffsetCounters) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body1 = machine_ir.NewBasicBlock();
  auto* body2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body1);
  machine_ir.AddEdge(body1, body2);
  machine_ir.AddEdge(body2, body1);

  MachineReg vreg1 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body1);

  builder.StartBasicBlock(body1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.GenGet(vreg1, GetThreadStateRegOffset(0));
  builder.GenGet(vreg1, GetThreadStateRegOffset(1));
  builder.Gen<PseudoBranch>(body2);

  builder.StartBasicBlock(body2);
  builder.GenGet(vreg1, GetThreadStateRegOffset(2));
  builder.GenPut(GetThreadStateRegOffset(2), vreg1);
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.Gen<PseudoBranch>(body1);

  Loop loop({body1, body2}, machine_ir.arena());
  auto counters = GetSortedOffsetCounters(&machine_ir, &loop);
  EXPECT_EQ(counters.size(), 3UL);
  EXPECT_EQ(std::get<0>(counters[0]), GetThreadStateRegOffset(0));
  EXPECT_EQ(std::get<1>(counters[0]), 3);

  EXPECT_EQ(std::get<0>(counters[1]), GetThreadStateRegOffset(2));
  EXPECT_EQ(std::get<1>(counters[1]), 2);

  EXPECT_EQ(std::get<0>(counters[2]), GetThreadStateRegOffset(1));
  EXPECT_EQ(std::get<1>(counters[2]), 1);
}

TEST(MachineIRLoopGuestContextOptimizer, OptimizeLoopWithPriority) {
  Arena arena;
  MachineIR machine_ir(&arena);

  auto* preloop = machine_ir.NewBasicBlock();
  auto* body = machine_ir.NewBasicBlock();
  auto* afterloop = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(preloop, body);
  machine_ir.AddEdge(body, body);
  machine_ir.AddEdge(body, afterloop);

  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  MachineIRBuilder builder(&machine_ir);

  builder.StartBasicBlock(preloop);
  builder.Gen<PseudoBranch>(body);

  // Regular reg 0 has 3 uses.
  // Regular reg 1 has 1 use.
  builder.StartBasicBlock(body);
  builder.GenGet(vreg1, GetThreadStateRegOffset(0));
  builder.GenPut(GetThreadStateRegOffset(0), vreg1);
  builder.GenGet(vreg1, GetThreadStateRegOffset(0));
  builder.GenGet(vreg1, GetThreadStateRegOffset(1));

  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    // Simd reg 0 has 2 uses.
    // Simd reg 1 has 1 use.
    builder.GenGetSimd<16>(vreg2, GetThreadStateSimdRegOffset(0));
    builder.GenSetSimd<16>(GetThreadStateSimdRegOffset(0), vreg2);
    builder.GenGetSimd<16>(vreg2, GetThreadStateSimdRegOffset(1));
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    // Vector reg 0 has 2 uses.
    // Vector reg 1 has 1 use.
    builder.GenGetSimd<16>(vreg2, GetThreadStateVRegOffset(0));
    builder.GenSetSimd<16>(GetThreadStateVRegOffset(0), vreg2);
    builder.GenGetSimd<16>(vreg2, GetThreadStateVRegOffset(1));
  }
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, body, afterloop, kMachineRegFLAGS);

  builder.StartBasicBlock(afterloop);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  Loop loop({body}, machine_ir.arena());
  OptimizeLoop(&machine_ir,
               &loop,
               OptimizeLoopParams{
                   .general_reg_limit = 1,
                   .simd_reg_limit = 1,
               });

  EXPECT_EQ(preloop->insn_list().size(), 3UL);
  auto* get_insn_1 = preloop->insn_list().front();
  EXPECT_EQ(get_insn_1->opcode(), kMachineOpMovqRegMemBaseDisp);
  auto mapped_reg_1 = get_insn_1->RegAt(0);
  auto disp_1 = AsMachineInsnX86_64(get_insn_1)->disp();
  EXPECT_EQ(disp_1, GetThreadStateRegOffset(0));

  auto* get_insn_2 = *std::next(preloop->insn_list().begin());
  EXPECT_EQ(get_insn_2->opcode(), kMachineOpMovdqaXRegMemBaseDisp);
  auto mapped_reg_2 = get_insn_2->RegAt(0);
  auto disp_2 = AsMachineInsnX86_64(get_insn_2)->disp();
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    EXPECT_EQ(disp_2, GetThreadStateSimdRegOffset(0));
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    EXPECT_EQ(disp_2, GetThreadStateVRegOffset(0));
  }

  // Since regular reg limit is 1 only reg 0 is optimized. Same for simd/vector regs.
  EXPECT_EQ(body->insn_list().size(), 8UL);
  auto insn_it = body->insn_list().begin();
  EXPECT_EQ(mapped_reg_1, CheckCopyGetInsnAndObtainMappedReg(*insn_it++, vreg1));
  EXPECT_EQ(mapped_reg_1, CheckCopyPutInsnAndObtainMappedReg(*insn_it++, vreg1));
  EXPECT_EQ(mapped_reg_1, CheckCopyGetInsnAndObtainMappedReg(*insn_it++, vreg1));
  EXPECT_EQ((*insn_it++)->opcode(), kMachineOpMovqRegMemBaseDisp);
  EXPECT_EQ(mapped_reg_2, CheckCopyGetInsnAndObtainMappedReg(*insn_it++, vreg2));
  EXPECT_EQ(mapped_reg_2, CheckCopyPutInsnAndObtainMappedReg(*insn_it++, vreg2));
  EXPECT_EQ((*insn_it++)->opcode(), kMachineOpMovdqaXRegMemBaseDisp);

  EXPECT_EQ(afterloop->insn_list().size(), 3UL);
  auto* put_insn_1 = afterloop->insn_list().front();
  CheckPutInsn(put_insn_1, kMachineOpMovqMemBaseDispReg, mapped_reg_1, GetThreadStateRegOffset(0));
  auto* put_insn_2 = *std::next(afterloop->insn_list().begin());
  if (DoesCpuStateHaveDedicatedSimdRegs()) {
    CheckPutInsn(
        put_insn_2, kMachineOpMovdqaMemBaseDispXReg, mapped_reg_2, GetThreadStateSimdRegOffset(0));
  } else if (DoesCpuStateHaveDedicatedVecRegs()) {
    CheckPutInsn(
        put_insn_2, kMachineOpMovdqaMemBaseDispXReg, mapped_reg_2, GetThreadStateVRegOffset(0));
  }
}

TEST(MachineIRLoopGuestContextOptimizer, ReplaceGetFlagsAndUpdateMap) {
  if (!DoesCpuStateHaveFlags()) {
    GTEST_SKIP() << "Guest CPU doesn't support flags";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  auto offset = GetThreadStateFlagOffset();
  builder.Gen<MovwRegMemBaseDisp>(reg1, kMachineRegRBP, offset);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplaceGetAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyGetInsnAndObtainMappedReg(copy_insn, reg1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovw, false);
}

TEST(MachineIRLoopGuestContextOptimizer, ReplacePutFlagsAndUpdateMap) {
  if (!DoesCpuStateHaveFlags()) {
    GTEST_SKIP() << "Guest CPU doesn't support flags";
  }
  Arena arena;
  MachineIR machine_ir(&arena);

  MachineIRBuilder builder(&machine_ir);

  auto bb = machine_ir.NewBasicBlock();
  builder.StartBasicBlock(bb);
  auto reg1 = machine_ir.AllocVReg();
  auto offset = GetThreadStateFlagOffset();
  builder.Gen<MovwMemBaseDispReg>(kMachineRegRBP, offset, reg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  auto insn_it = bb->insn_list().begin();
  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir.arena());
  ReplacePutAndUpdateMap(&machine_ir, insn_it, mem_reg_map);
  ASSERT_EQ(CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  EXPECT_EQ(bb->insn_list().size(), 2UL);
  auto* copy_insn = *bb->insn_list().begin();
  auto mapped_reg = CheckCopyPutInsnAndObtainMappedReg(copy_insn, reg1);
  CheckMemRegMap(mem_reg_map, offset, mapped_reg, MovType::kMovw, true);
}

}  // namespace

}  // namespace berberis::x86_64
