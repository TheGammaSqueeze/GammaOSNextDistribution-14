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

#include "berberis/backend/x86_64/liveness_analyzer.h"

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/vreg_bit_set.h"
#include "berberis/base/algorithm.h"

namespace berberis {

namespace x86_64 {

void LivenessAnalyzer::Run() {
  // IR must not change between analyzer construction and run.
  CHECK_EQ(machine_ir_->NumBasicBlocks(), live_in_.size());
  CHECK_EQ(machine_ir_->NumVReg(), NumVReg());
  // Copy the original list in reverse order to foster faster liveness
  // propagation from successors to predecessors.
  // TODO(b/179708579): For better post order approximation need to implement an
  // analog of BackwardDataFlowWorkList from High-level IR.
  MachineBasicBlockList worklist(machine_ir_->bb_list().rbegin(),
                                 machine_ir_->bb_list().rend(),
                                 ArenaAllocator<MachineBasicBlock*>(machine_ir_->arena()));
  while (!worklist.empty()) {
    auto* bb = worklist.back();
    worklist.pop_back();
    if (VisitBasicBlock(bb)) {
      // Since there is a change we need to process preds again.
      for (auto edge : bb->in_edges()) {
        auto* pred_bb = edge->src();
        if (!Contains(worklist, pred_bb)) {
          worklist.push_back(pred_bb);
        }
      }
    }
  }
}

// Updates live-ins for the basic block. Returns whether there was any change.
bool LivenessAnalyzer::VisitBasicBlock(const MachineBasicBlock* bb) {
  bool changed = false;

  // Compute liveness at the end of basic block.
  // Exit blocks have all regs dead - the default value for Liveness.
  VRegBitSet running_liveness(NumVReg(), machine_ir_->arena());
  for (auto edge : bb->out_edges()) {
    running_liveness |= live_in_.at(edge->dst()->id());
  }

  // Traverse instructions backward, updating liveness.
  for (auto insn_it = bb->insn_list().rbegin(); insn_it != bb->insn_list().rend(); ++insn_it) {
    const MachineInsn* insn = *insn_it;
    // Same reg can be def and use, so process all defs first.
    for (int i = 0; i < insn->NumRegOperands(); ++i) {
      if (insn->RegAt(i).IsVReg() && insn->RegKindAt(i).IsDef()) {
        running_liveness.Reset(insn->RegAt(i));
      }
    }
    for (int i = 0; i < insn->NumRegOperands(); ++i) {
      if (insn->RegAt(i).IsVReg() && insn->RegKindAt(i).IsInput()) {
        running_liveness.Set(insn->RegAt(i));
      }
    }
  }

  if (live_in_[bb->id()] != running_liveness) {
    live_in_[bb->id()] = running_liveness;
    changed = true;
  }

  return changed;
}

}  // namespace x86_64

}  // namespace berberis
