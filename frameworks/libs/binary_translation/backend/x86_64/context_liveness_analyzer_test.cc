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
#include <list>

#include "gtest/gtest.h"

#include "berberis/backend/x86_64/context_liveness_analyzer.h"

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_state_arch.h"

namespace berberis {

namespace {

void CheckBBLiveIn(const x86_64::ContextLivenessAnalyzer* analyzer,
                   const MachineBasicBlock* bb,
                   const std::list<uint32_t> dead_guest_regs) {
  for (auto reg : dead_guest_regs) {
    CHECK_LE(reg, kNumGuestRegs);
  }

  for (unsigned int reg = 0; reg < kNumGuestRegs; reg++) {
    if (Contains(dead_guest_regs, reg)) {
      EXPECT_FALSE(analyzer->IsLiveIn(bb, offsetof(ProcessState, cpu.x[reg])));
    } else {
      EXPECT_TRUE(analyzer->IsLiveIn(bb, offsetof(ProcessState, cpu.x[reg])));
    }
  }
}

TEST(MachineIRContextLivenessAnalyzerTest, PutKillsLiveIn) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb = machine_ir.NewBasicBlock();
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ContextLivenessAnalyzer analyzer(&machine_ir);
  analyzer.Init();

  CheckBBLiveIn(&analyzer, bb, {0});
}

TEST(MachineIRContextLivenessAnalyzerTest, GetRevivesLiveInKilledByPut) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenGet(vreg, GetThreadStateRegOffset(0));
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ContextLivenessAnalyzer analyzer(&machine_ir);
  analyzer.Init();

  CheckBBLiveIn(&analyzer, bb1, {});
  CheckBBLiveIn(&analyzer, bb2, {0});
}

TEST(MachineIRContextLivenessAnalyzerTest,
     GetRevivesLiveInKilledByPutButNotOtherLiveInsAcrossBasicBlocks) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  x86_64::MachineIRBuilder builder(&machine_ir);

  auto vreg = machine_ir.AllocVReg();
  builder.StartBasicBlock(bb1);
  builder.GenGet(vreg, GetThreadStateRegOffset(1));
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.GenPut(GetThreadStateRegOffset(1), vreg);
  builder.GenPut(GetThreadStateRegOffset(2), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ContextLivenessAnalyzer analyzer(&machine_ir);
  analyzer.Init();

  CheckBBLiveIn(&analyzer, bb1, {0, 2});
  CheckBBLiveIn(&analyzer, bb2, {0, 1, 2});
}

TEST(MachineIRContextLivenessAnalyzerTest, ContextWritesOnlyKillLiveInIfHappenInBothSuccessors) {
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
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.GenPut(GetThreadStateRegOffset(1), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.GenPut(GetThreadStateRegOffset(0), vreg);
  builder.GenPut(GetThreadStateRegOffset(2), vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  x86_64::ContextLivenessAnalyzer analyzer(&machine_ir);
  analyzer.Init();

  CheckBBLiveIn(&analyzer, bb1, {0});
  CheckBBLiveIn(&analyzer, bb2, {0, 1});
  CheckBBLiveIn(&analyzer, bb3, {0, 2});
}

}  // namespace

}  // namespace berberis
