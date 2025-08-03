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

#include <vector>

#include "berberis/backend/x86_64/machine_ir_analysis.h"

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/logging.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

void CheckLoopContent(x86_64::Loop* loop, std::vector<MachineBasicBlock*> body) {
  EXPECT_EQ(loop->size(), body.size());

  // Loop head must be the first basic block in the loop.
  EXPECT_EQ(loop->at(0), body[0]);

  for (auto* bb : body) {
    EXPECT_TRUE(Contains(*loop, bb));
  }
}

TEST(MachineIRAnalysis, SelfLoop) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1 -- bb2 -- bb3
  //        | |
  //        ---
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

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loops = x86_64::FindLoops(&machine_ir);
  EXPECT_EQ(loops.size(), 1UL);
  auto loop = loops[0];
  CheckLoopContent(loop, {bb2});
}

TEST(MachineIRAnalysis, SingleLoop) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1 -- bb2 -- bb3 ---- bb4
  //         |      |
  //         --------
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb2);
  machine_ir.AddEdge(bb3, bb4);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loops = x86_64::FindLoops(&machine_ir);
  EXPECT_EQ(loops.size(), 1UL);
  auto loop = loops[0];
  CheckLoopContent(loop, {bb2, bb3});
}

TEST(MachineIRAnalysis, MultipleBackEdges) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  //         -----------------
  //         |               |
  // bb1 -- bb2 -- bb3 ---- bb4
  //         |      |
  //         --------
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb2);
  machine_ir.AddEdge(bb3, bb4);
  machine_ir.AddEdge(bb4, bb2);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoBranch>(bb2);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loops = x86_64::FindLoops(&machine_ir);
  EXPECT_EQ(loops.size(), 1UL);
  auto loop = loops[0];
  CheckLoopContent(loop, {bb2, bb3, bb4});
}

TEST(MachineIRAnalysis, TwoLoops) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  //         ------------------------
  //         |                      |
  // bb0---bb1 -- bb2 -- bb3 ---- bb4
  //               |      |
  //               --------
  auto bb0 = machine_ir.NewBasicBlock();
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb2);
  machine_ir.AddEdge(bb3, bb4);
  machine_ir.AddEdge(bb4, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb1);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoBranch>(bb1);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loops = x86_64::FindLoops(&machine_ir);
  EXPECT_EQ(loops.size(), 2UL);
  auto loop1 = loops[0];
  CheckLoopContent(loop1, {bb1, bb2, bb3, bb4});
  auto loop2 = loops[1];
  CheckLoopContent(loop2, {bb2, bb3});
}

TEST(MachineIRAnalysis, LoopTreeInsertLoop) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  x86_64::Loop loop1(&arena);
  loop1.push_back(bb1);

  x86_64::LoopTree tree(&machine_ir);
  tree.InsertLoop(&loop1);

  EXPECT_EQ(tree.root()->loop(), nullptr);
  EXPECT_EQ(tree.root()->NumInnerloops(), 1UL);

  auto* node = tree.root()->GetInnerloopNode(0);
  CheckLoopContent(node->loop(), {bb1});
  EXPECT_EQ(node->NumInnerloops(), 0UL);
}

TEST(MachineIRAnalysis, LoopTreeInsertParallelLoops) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  auto* bb3 = machine_ir.NewBasicBlock();
  x86_64::Loop loop1(&arena);
  loop1.push_back(bb1);
  loop1.push_back(bb2);
  x86_64::Loop loop2(&arena);
  loop2.push_back(bb3);

  x86_64::LoopTree tree(&machine_ir);
  tree.InsertLoop(&loop1);
  tree.InsertLoop(&loop2);

  EXPECT_EQ(tree.root()->loop(), nullptr);
  EXPECT_EQ(tree.root()->NumInnerloops(), 2UL);

  auto* node1 = tree.root()->GetInnerloopNode(0);
  CheckLoopContent(node1->loop(), {bb1, bb2});
  EXPECT_EQ(node1->NumInnerloops(), 0UL);

  auto* node2 = tree.root()->GetInnerloopNode(1);
  CheckLoopContent(node2->loop(), {bb3});
  EXPECT_EQ(node2->NumInnerloops(), 0UL);
}

TEST(MachineIRAnalysis, LoopTreeInsertNestedLoops) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  auto* bb1 = machine_ir.NewBasicBlock();
  auto* bb2 = machine_ir.NewBasicBlock();
  x86_64::Loop loop1(&arena);
  loop1.push_back(bb1);
  loop1.push_back(bb2);
  x86_64::Loop loop2(&arena);
  loop2.push_back(bb2);

  x86_64::LoopTree tree(&machine_ir);
  tree.InsertLoop(&loop1);
  tree.InsertLoop(&loop2);

  EXPECT_EQ(tree.root()->loop(), nullptr);
  EXPECT_EQ(tree.root()->NumInnerloops(), 1UL);

  auto* node1 = tree.root()->GetInnerloopNode(0);
  CheckLoopContent(node1->loop(), {bb1, bb2});
  EXPECT_EQ(node1->NumInnerloops(), 1UL);

  auto* node2 = node1->GetInnerloopNode(0);
  CheckLoopContent(node2->loop(), {bb2});
  EXPECT_EQ(node2->NumInnerloops(), 0UL);
}

TEST(MachineIRAnalysis, FindSingleLoopTree) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  // bb1 -- bb2 -- bb3
  //        | |
  //        ---
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

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loop_tree = x86_64::BuildLoopTree(&machine_ir);
  auto* root = loop_tree.root();

  EXPECT_EQ(root->NumInnerloops(), 1UL);
  auto* loop_node = root->GetInnerloopNode(0);
  CheckLoopContent(loop_node->loop(), {bb2});
}

TEST(MachineIRAnalysis, FindNestedLoopTree) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  //         ------------------------
  //         |                      |
  // bb0---bb1 -- bb2 -- bb3 ---- bb4
  //               |      |
  //               --------
  auto bb0 = machine_ir.NewBasicBlock();
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb2);
  machine_ir.AddEdge(bb3, bb4);
  machine_ir.AddEdge(bb4, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb1);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoBranch>(bb1);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loop_tree = x86_64::BuildLoopTree(&machine_ir);
  auto* root = loop_tree.root();

  EXPECT_EQ(root->NumInnerloops(), 1UL);
  auto* outerloop_node = root->GetInnerloopNode(0);
  CheckLoopContent(outerloop_node->loop(), {bb1, bb2, bb3, bb4});

  EXPECT_EQ(outerloop_node->NumInnerloops(), 1UL);
  auto* innerloop_node = outerloop_node->GetInnerloopNode(0);
  CheckLoopContent(innerloop_node->loop(), {bb2, bb3});
}

TEST(MachineIRAnalysis, FindLoopTreeWithMultipleInnerloops) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);

  //         -------------------------------
  //         |                     |       |
  // bb0---bb1 -- bb2 -- bb3 ---- bb4-----bb5
  //               |      |
  //               --------
  auto bb0 = machine_ir.NewBasicBlock();
  auto bb1 = machine_ir.NewBasicBlock();
  auto bb2 = machine_ir.NewBasicBlock();
  auto bb3 = machine_ir.NewBasicBlock();
  auto bb4 = machine_ir.NewBasicBlock();
  auto bb5 = machine_ir.NewBasicBlock();
  machine_ir.AddEdge(bb0, bb1);
  machine_ir.AddEdge(bb1, bb2);
  machine_ir.AddEdge(bb2, bb3);
  machine_ir.AddEdge(bb3, bb2);
  machine_ir.AddEdge(bb3, bb4);
  machine_ir.AddEdge(bb4, bb5);
  machine_ir.AddEdge(bb5, bb4);
  machine_ir.AddEdge(bb5, bb1);

  builder.StartBasicBlock(bb0);
  builder.Gen<PseudoBranch>(bb1);

  builder.StartBasicBlock(bb1);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoBranch>(bb5);

  builder.StartBasicBlock(bb5);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb1, bb4, x86_64::kMachineRegFLAGS);

  ASSERT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);
  auto loop_tree = x86_64::BuildLoopTree(&machine_ir);
  auto* root = loop_tree.root();

  EXPECT_EQ(root->NumInnerloops(), 1UL);
  auto* outerloop_node = root->GetInnerloopNode(0);
  CheckLoopContent(outerloop_node->loop(), {bb1, bb2, bb3, bb4, bb5});

  EXPECT_EQ(outerloop_node->NumInnerloops(), 2UL);
  auto* innerloop_node1 = outerloop_node->GetInnerloopNode(0);
  CheckLoopContent(innerloop_node1->loop(), {bb2, bb3});
  auto* innerloop_node2 = outerloop_node->GetInnerloopNode(1);
  CheckLoopContent(innerloop_node2->loop(), {bb4, bb5});
}

}  // namespace

}  // namespace berberis
