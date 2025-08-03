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

#include <cstdint>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/decoder/riscv64/decoder.h"
#include "berberis/guest_state/guest_addr.h"

#include "frontend.h"

namespace berberis {

namespace {

constexpr static GuestAddr kStartGuestAddr = 0x0000'aaaa'bbbb'ccccULL;
// Assume all instructions are not compressed.
constexpr int32_t kInsnSize = 4;

bool DoesEdgeExist(const MachineBasicBlock* src_bb, const MachineBasicBlock* end_bb) {
  bool out_edge_found = false;
  for (auto out_edge : src_bb->out_edges()) {
    if (out_edge->dst() == end_bb) {
      out_edge_found = true;
      break;
    }
  }

  if (!out_edge_found) {
    return false;
  }

  for (auto in_edge : end_bb->in_edges()) {
    if (in_edge->src() == src_bb) {
      // in edge found
      return true;
    }
  }
  return false;
}

MachineBasicBlock* FindEntryBasicBlock(const MachineIR* machine_ir) {
  for (auto* bb : machine_ir->bb_list()) {
    if (bb->in_edges().size() == 0U) {
      return bb;
    }
  }
  return nullptr;
}

const MachineBasicBlock* FindEntrySuccessor(const MachineIR* machine_ir) {
  auto* entry_bb = FindEntryBasicBlock(machine_ir);
  CHECK_GE(entry_bb->insn_list().size(), 1UL);
  auto* branch_insn = entry_bb->insn_list().back();
  CHECK_EQ(branch_insn->opcode(), kMachineOpPseudoBranch);
  return static_cast<PseudoBranch*>(branch_insn)->then_bb();
}

void CheckBasicBlockEndsWith(const MachineBasicBlock* bb, MachineOpcode opcode) {
  ASSERT_NE(bb, nullptr);
  ASSERT_EQ(bb->insn_list().back()->opcode(), opcode);
}

template <int32_t kOffset>
constexpr GuestAddr insn_at() {
  return kStartGuestAddr + kOffset * kInsnSize;
}

TEST(HeavyOptimizerFrontendTest, BranchTargets) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  auto tmp = frontend.GetImm(0xbeefULL);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.SetReg(3, tmp);
  frontend.SetReg(3, tmp);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.SetReg(3, tmp);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.Finalize(insn_at<3>());

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  auto branch_targets = frontend.branch_targets();

  EXPECT_TRUE(branch_targets[insn_at<0>()].second.has_value());
  auto it = branch_targets[insn_at<0>()].second.value();

  EXPECT_TRUE(branch_targets[insn_at<1>()].second.has_value());
  it = branch_targets[insn_at<1>()].second.value();

  EXPECT_TRUE(branch_targets[insn_at<2>()].second.has_value());
  it = branch_targets[insn_at<2>()].second.value();

  EXPECT_FALSE(branch_targets[insn_at<3>()].second.has_value());

  EXPECT_TRUE(branch_targets.find(kStartGuestAddr - kInsnSize) == branch_targets.end());
  EXPECT_TRUE(branch_targets.find(insn_at<4>()) == branch_targets.end());
}

TEST(HeavyOptimizerFrontendTest, LoopInsideRegion) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  auto tmp = frontend.GetImm(0xbeefULL);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.SetReg(3, tmp);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.Finalize(insn_at<1>());

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  auto* preloop_bb = FindEntrySuccessor(&machine_ir);
  auto* branch_insn = preloop_bb->insn_list().back();
  ASSERT_EQ(branch_insn->opcode(), kMachineOpPseudoBranch);
  auto* loop_bb = static_cast<PseudoBranch*>(branch_insn)->then_bb();
  auto* cmpb = *std::next(loop_bb->insn_list().rbegin());
  ASSERT_EQ(cmpb->opcode(), kMachineOpCmpbMemBaseDispImm);
  branch_insn = loop_bb->insn_list().back();
  ASSERT_EQ(branch_insn->opcode(), kMachineOpPseudoCondBranch);
  auto* signal_exit_bb = static_cast<PseudoCondBranch*>(branch_insn)->then_bb();
  branch_insn = signal_exit_bb->insn_list().back();
  ASSERT_EQ(branch_insn->opcode(), kMachineOpPseudoJump);

  EXPECT_EQ(preloop_bb->in_edges().size(), 1UL);
  EXPECT_EQ(preloop_bb->out_edges().size(), 1UL);
  EXPECT_EQ(loop_bb->in_edges().size(), 2UL);
  EXPECT_EQ(loop_bb->out_edges().size(), 2UL);
  EXPECT_EQ(signal_exit_bb->in_edges().size(), 1UL);
  EXPECT_EQ(signal_exit_bb->out_edges().size(), 0UL);

  EXPECT_TRUE(DoesEdgeExist(preloop_bb, loop_bb));
  EXPECT_TRUE(DoesEdgeExist(loop_bb, loop_bb));
  EXPECT_TRUE(DoesEdgeExist(loop_bb, signal_exit_bb));
}

TEST(HeavyOptimizerFrontendTest, BranchBuildsJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  frontend.Branch(kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  // Branch builds Jump.
  CheckBasicBlockEndsWith(FindEntrySuccessor(&machine_ir), kMachineOpPseudoJump);
}

TEST(HeavyOptimizerFrontendTest, ResolveJumps) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  frontend.Branch(kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  // NOP, just to include this address in the region.
  frontend.StartInsn();
  frontend.IncrementInsnAddr(kInsnSize);

  // ResolveJumps happens here.
  frontend.Finalize(insn_at<2>());

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Jump is replaced by Branch.
  CheckBasicBlockEndsWith(FindEntrySuccessor(&machine_ir), kMachineOpPseudoBranch);
}

TEST(HeavyOptimizerFrontendTest, ResolveJumpToAlreadyReplacedJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  frontend.Branch(kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.Branch(-kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  // ResolveJumps happens here.
  // We are testing that after one of the jumps is resolved the internal data
  // structures are still valid for resolution of the second jump.
  frontend.Finalize(insn_at<2>());

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Both Jumps are replaced by Branches
  auto* bb = FindEntrySuccessor(&machine_ir);
  CheckBasicBlockEndsWith(bb, kMachineOpPseudoBranch);

  auto* next_bb = bb->out_edges()[0]->dst();
  // This one is CondBranch because we also insert pending signals check.
  CheckBasicBlockEndsWith(next_bb, kMachineOpPseudoCondBranch);
  ASSERT_EQ(next_bb->out_edges()[1]->dst(), bb);
}

TEST(HeavyOptimizerFrontendTest, ResolveJumpToAlreadyReplacedBackJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  frontend.StartInsn();
  frontend.CompareAndBranch(HeavyOptimizerFrontend::Decoder::BranchOpcode::kBeq,
                            MachineReg(1),
                            MachineReg(2),
                            2 * kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.Branch(-kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.StartInsn();
  frontend.Branch(-kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  // ResolveJumps happens here.
  // We are testing that after a back jump is resolved the internal data
  // structures are still valid for resolution of another jump to it.
  // Note, there is a possible order of resolutions where all back jumps are
  // resolved after jumps that target them. But we assume that the resolution
  // happens either top-down or down-top, in which case this test is useful.
  frontend.Finalize(insn_at<3>());

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Both back Jumps are replaced by CondBranches because we also insert pending signals check.
  //
  // Expect
  // ->          BB1
  // |        COND_BRANCH
  // |     /                  \
  // |   BB2          <-      BB3
  // COND_BRANCH BB1   |      BRANCH
  //                   |       |
  //                   |       BB4
  //                   ------- COND_BRANCH_BB2
  auto* bb1 = FindEntrySuccessor(&machine_ir);
  CheckBasicBlockEndsWith(bb1, kMachineOpPseudoCondBranch);

  auto* bb2 = bb1->out_edges()[1]->dst();
  CheckBasicBlockEndsWith(bb2, kMachineOpPseudoCondBranch);
  ASSERT_EQ(bb2->out_edges()[1]->dst(), bb1);

  auto* bb3 = bb1->out_edges()[0]->dst();
  CheckBasicBlockEndsWith(bb3, kMachineOpPseudoBranch);

  auto* bb4 = bb3->out_edges()[0]->dst();
  CheckBasicBlockEndsWith(bb4, kMachineOpPseudoCondBranch);
  ASSERT_EQ(bb4->out_edges()[1]->dst(), bb2);
}

TEST(HeavyOptimizerFrontendTest, ResolveJumpToAnotherJump) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  HeavyOptimizerFrontend frontend(&machine_ir, kStartGuestAddr);

  // A conditional branch results is two basic blocks.
  // BB0, BB1: kStartGuestAddr.
  frontend.StartInsn();
  frontend.CompareAndBranch(
      HeavyOptimizerFrontend::Decoder::BranchOpcode::kBeq, MachineReg(1), MachineReg(2), 8);
  frontend.IncrementInsnAddr(kInsnSize);

  // Make sure the next Branch doesn't start a basic block, so that we'll
  // need to split it in ResolveJumps.
  // BB2: kStartGuestAddr + 4.
  frontend.StartInsn();
  (void)frontend.GetImm(0xbeefULL);
  frontend.IncrementInsnAddr(kInsnSize);

  // BB2: kStartGuestAddr + 8.
  frontend.StartInsn();
  frontend.Branch(kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  // BB3: kStartGuestAddr + 12.
  frontend.StartInsn();
  frontend.Branch(kInsnSize);
  frontend.IncrementInsnAddr(kInsnSize);

  frontend.Finalize(insn_at<4>());

  // The main check of this test - the IR is integral.
  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

  // Expected control-flow:
  // BB0 -> (BB2 -> BB4) -> BB3
  //     \___BB1____^
  //
  // When resolving BB1->BB4 jump we split BB2 into BB2 and BB4.
  // Then we must resolve BB4->BB3 jump, otherwise BB3 will be unlinked from IR.
  auto* bb0 = FindEntrySuccessor(&machine_ir);
  CheckBasicBlockEndsWith(bb0, kMachineOpPseudoCondBranch);

  auto* bb1 = bb0->out_edges()[1]->dst();
  CheckBasicBlockEndsWith(bb1, kMachineOpPseudoBranch);

  auto* bb5 = bb0->out_edges()[0]->dst();
  CheckBasicBlockEndsWith(bb5, kMachineOpPseudoBranch);

  auto* bb4 = bb5->out_edges()[0]->dst();
  CheckBasicBlockEndsWith(bb4, kMachineOpPseudoBranch);

  EXPECT_EQ(bb1->out_edges()[0]->dst(), bb4);

  auto* bb2 = bb4->out_edges()[0]->dst();
  CheckBasicBlockEndsWith(bb2, kMachineOpPseudoJump);
  EXPECT_EQ(bb2->out_edges().size(), 0u);
}

}  // namespace

}  // namespace berberis
