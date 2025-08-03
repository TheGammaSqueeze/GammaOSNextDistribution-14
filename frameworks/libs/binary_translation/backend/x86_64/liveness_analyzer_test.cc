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
#include "berberis/backend/x86_64/liveness_analyzer.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_test_corpus.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

template <typename... VRegs>
void ExpectNoLiveIns(const x86_64::LivenessAnalyzer* liveness,
                     const MachineBasicBlock* bb,
                     VRegs... not_live_in_vregs) {
  EXPECT_TRUE((!liveness->IsLiveIn(bb, not_live_in_vregs) && ... && true));
  EXPECT_EQ(liveness->GetFirstLiveIn(bb), kInvalidMachineReg);
}

template <typename... VRegs>
void ExpectSingleLiveIn(const x86_64::LivenessAnalyzer* liveness,
                        const MachineBasicBlock* bb,
                        MachineReg vreg,
                        VRegs... not_live_in_vregs) {
  EXPECT_TRUE((!liveness->IsLiveIn(bb, not_live_in_vregs) && ... && true));
  EXPECT_TRUE(liveness->IsLiveIn(bb, vreg));
  EXPECT_EQ(liveness->GetFirstLiveIn(bb), vreg);
  EXPECT_EQ(liveness->GetNextLiveIn(bb, vreg), kInvalidMachineReg);
}

void ExpectTwoLiveIns(const x86_64::LivenessAnalyzer* liveness,
                      const MachineBasicBlock* bb,
                      MachineReg vreg1,
                      MachineReg vreg2) {
  EXPECT_TRUE(liveness->IsLiveIn(bb, vreg1));
  EXPECT_TRUE(liveness->IsLiveIn(bb, vreg2));

  MachineReg live_in1 = liveness->GetFirstLiveIn(bb);
  ASSERT_TRUE(live_in1 == vreg1 || live_in1 == vreg2);
  MachineReg live_in2 = liveness->GetNextLiveIn(bb, live_in1);
  ASSERT_TRUE(live_in2 == vreg1 || live_in2 == vreg2);
  EXPECT_NE(live_in1, live_in2);
  EXPECT_EQ(liveness->GetNextLiveIn(bb, live_in2), kInvalidMachineReg);
}

TEST(MachineLivenessAnalyzerTest, UseProducesLiveIn) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectSingleLiveIn(&liveness, bb, vreg);
}

class FakeInsnWithDefEarlyClobber : public MachineInsn {
 public:
  explicit FakeInsnWithDefEarlyClobber(MachineReg reg)
      : MachineInsn(kMachineOpUndefined, 1, &reg_kind_, &reg_, kMachineInsnDefault), reg_{reg} {}
  [[nodiscard]] std::string GetDebugString() const override {
    return "FakeInsnWithDefEarlyClobber";
  }
  void Emit(CodeEmitter* /*as*/) const override {}

 private:
  static MachineRegKind reg_kind_;
  MachineReg reg_;
};

MachineRegKind FakeInsnWithDefEarlyClobber::reg_kind_ = {&x86_64::kGeneralReg64,
                                                         MachineRegKind::kDefEarlyClobber};

TEST(MachineLivenessAnalyzerTest, DefEarlyClobberDoesNotProduceLiveIn) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<FakeInsnWithDefEarlyClobber>(vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb, vreg);
}

TEST(MachineLivenessAnalyzerTest, DefKillsUse) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb, vreg);
}

TEST(MachineLivenessAnalyzerTest, DefDoesNotKillUseInSameInstruction) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegReg>(vreg, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectSingleLiveIn(&liveness, bb, vreg);
}

TEST(MachineLivenessAnalyzerTest, DefDoesNotKillAnotherVReg) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  MachineReg vreg1 = machine_ir.AllocVReg();
  MachineReg vreg2 = machine_ir.AllocVReg();

  auto* bb = machine_ir.NewBasicBlock();

  builder.StartBasicBlock(bb);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg2);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectSingleLiveIn(&liveness, bb, vreg2, vreg1);
}

TEST(MachineLivenessAnalyzerTest, DataFlowAcrossBasicBlocks) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg1, vreg2] = BuildDataFlowAcrossBasicBlocks(&machine_ir);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb1, vreg1, vreg2);
  ExpectTwoLiveIns(&liveness, bb2, vreg1, vreg2);
  ExpectSingleLiveIn(&liveness, bb3, vreg1, vreg2);
}

TEST(MachineLivenessAnalyzerTest, DataFlowFromTwoPreds) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg] = BuildDataFlowFromTwoPreds(&machine_ir);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb1, vreg);
  ExpectNoLiveIns(&liveness, bb2, vreg);
  ExpectSingleLiveIn(&liveness, bb3, vreg);
}

TEST(MachineLivenessAnalyzerTest, DataFlowToTwoSuccs) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, vreg] = BuildDataFlowToTwoSuccs(&machine_ir);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb1, vreg);
  ExpectSingleLiveIn(&liveness, bb2, vreg);
  ExpectSingleLiveIn(&liveness, bb3, vreg);
}

TEST(MachineLivenessAnalyzerTest, DataFlowAcrossEmptyLoop) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto [bb1, bb2, bb3, bb4, vreg] = BuildDataFlowAcrossEmptyLoop(&machine_ir);

  x86_64::LivenessAnalyzer liveness(&machine_ir);
  liveness.Run();

  ExpectNoLiveIns(&liveness, bb1, vreg);
  ExpectSingleLiveIn(&liveness, bb2, vreg);
  ExpectSingleLiveIn(&liveness, bb3, vreg);
  ExpectSingleLiveIn(&liveness, bb4, vreg);
}

}  // namespace

}  // namespace berberis
