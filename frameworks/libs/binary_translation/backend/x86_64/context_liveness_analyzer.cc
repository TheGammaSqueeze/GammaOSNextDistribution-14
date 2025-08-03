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

#include "berberis/backend/x86_64/context_liveness_analyzer.h"

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/arena_alloc.h"

namespace berberis::x86_64 {

void ContextLivenessAnalyzer::Init() {
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

bool ContextLivenessAnalyzer::IsLiveIn(const MachineBasicBlock* bb, uint32_t offset) const {
  return context_live_in_[bb->id()].test(offset);
}

bool ContextLivenessAnalyzer::VisitBasicBlock(const MachineBasicBlock* bb) {
  ContextLiveness running_liveness;
  if (bb->out_edges().size() == 0) {
    running_liveness.set();
  } else {
    for (auto* out_edge : bb->out_edges()) {
      running_liveness |= context_live_in_[out_edge->dst()->id()];
    }
  }

  for (auto insn_it = bb->insn_list().rbegin(); insn_it != bb->insn_list().rend(); insn_it++) {
    auto* insn = AsMachineInsnX86_64(*insn_it);
    if (insn->IsCPUStatePut()) {
      running_liveness.reset(insn->disp());
    } else if (insn->IsCPUStateGet()) {
      running_liveness.set(insn->disp());
    }
  }

  if (context_live_in_[bb->id()] != running_liveness) {
    context_live_in_[bb->id()] = running_liveness;
    return true;
  }

  return false;
}

}  // namespace berberis::x86_64
