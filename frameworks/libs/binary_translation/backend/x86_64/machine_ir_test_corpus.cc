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

#include "berberis/backend/x86_64/machine_ir_test_corpus.h"

#include <tuple>

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           MachineReg,
           MachineReg>
BuildDataFlowAcrossBasicBlocks(x86_64::MachineIR* machine_ir) {
  x86_64::MachineIRBuilder builder(machine_ir);
  MachineReg vreg1 = machine_ir->AllocVReg();
  MachineReg vreg2 = machine_ir->AllocVReg();

  auto* bb1 = machine_ir->NewBasicBlock();
  auto* bb2 = machine_ir->NewBasicBlock();
  auto* bb3 = machine_ir->NewBasicBlock();

  machine_ir->AddEdge(bb1, bb2);
  machine_ir->AddEdge(bb2, bb3);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg1, 0);
  builder.Gen<x86_64::MovqRegImm>(vreg2, 0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg2);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg1);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  return {bb1, bb2, bb3, vreg1, vreg2};
}

std::tuple<const MachineBasicBlock*, const MachineBasicBlock*, const MachineBasicBlock*, MachineReg>
BuildDataFlowFromTwoPreds(x86_64::MachineIR* machine_ir) {
  x86_64::MachineIRBuilder builder(machine_ir);
  MachineReg vreg = machine_ir->AllocVReg();

  // BB1   BB2
  //   \   /
  //    BB3
  //
  auto* bb1 = machine_ir->NewBasicBlock();
  auto* bb2 = machine_ir->NewBasicBlock();
  auto* bb3 = machine_ir->NewBasicBlock();

  machine_ir->AddEdge(bb1, bb3);
  machine_ir->AddEdge(bb2, bb3);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegImm>(vreg, 1);
  builder.Gen<PseudoBranch>(bb3);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  return {bb1, bb2, bb3, vreg};
}

std::tuple<const MachineBasicBlock*, const MachineBasicBlock*, const MachineBasicBlock*, MachineReg>
BuildDataFlowToTwoSuccs(x86_64::MachineIR* machine_ir) {
  x86_64::MachineIRBuilder builder(machine_ir);
  MachineReg vreg = machine_ir->AllocVReg();

  //     BB1
  //    /  \
  // BB2    BB3
  //
  auto* bb1 = machine_ir->NewBasicBlock();
  auto* bb2 = machine_ir->NewBasicBlock();
  auto* bb3 = machine_ir->NewBasicBlock();

  machine_ir->AddEdge(bb1, bb2);
  machine_ir->AddEdge(bb1, bb3);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  builder.StartBasicBlock(bb3);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  return {bb1, bb2, bb3, vreg};
}

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*>
BuildDiamondControlFlow(x86_64::MachineIR* machine_ir) {
  x86_64::MachineIRBuilder builder(machine_ir);
  MachineReg vreg = machine_ir->AllocVReg();

  //
  //     BB1
  //    /  \
  // BB2    BB3
  //   \    /
  //     BB4
  //
  auto* bb1 = machine_ir->NewBasicBlock();
  auto* bb2 = machine_ir->NewBasicBlock();
  auto* bb3 = machine_ir->NewBasicBlock();
  auto* bb4 = machine_ir->NewBasicBlock();

  machine_ir->AddEdge(bb1, bb2);
  machine_ir->AddEdge(bb1, bb3);
  machine_ir->AddEdge(bb2, bb4);
  machine_ir->AddEdge(bb3, bb4);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb2, bb3, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoBranch>(bb4);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoBranch>(bb4);

  builder.StartBasicBlock(bb4);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  return {bb1, bb2, bb3, bb4};
}

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           MachineReg>
BuildDataFlowAcrossEmptyLoop(x86_64::MachineIR* machine_ir) {
  x86_64::MachineIRBuilder builder(machine_ir);
  MachineReg vreg = machine_ir->AllocVReg();

  // BB1
  //  |
  // BB2  <-
  //  |  \ |
  // BB4  BB3
  //
  // Moves must be built for all loop blocks (BB2 and BB3).
  auto* bb1 = machine_ir->NewBasicBlock();
  auto* bb2 = machine_ir->NewBasicBlock();
  auto* bb3 = machine_ir->NewBasicBlock();
  auto* bb4 = machine_ir->NewBasicBlock();

  machine_ir->AddEdge(bb1, bb2);
  machine_ir->AddEdge(bb2, bb3);
  machine_ir->AddEdge(bb3, bb2);
  machine_ir->AddEdge(bb2, bb4);

  builder.StartBasicBlock(bb1);
  builder.Gen<x86_64::MovqRegImm>(vreg, 0);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb2);
  builder.Gen<PseudoCondBranch>(CodeEmitter::Condition::kZero, bb3, bb4, x86_64::kMachineRegFLAGS);

  builder.StartBasicBlock(bb3);
  builder.Gen<PseudoBranch>(bb2);

  builder.StartBasicBlock(bb4);
  builder.Gen<x86_64::MovqRegReg>(x86_64::kMachineRegRAX, vreg);
  builder.Gen<PseudoJump>(kNullGuestAddr);

  return {bb1, bb2, bb3, bb4, vreg};
}

}  // namespace berberis
