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

#include "berberis/backend/x86_64/rename_vregs.h"

#include <algorithm>  // std::max

#include "berberis/backend/x86_64/liveness_analyzer.h"
#include "berberis/backend/x86_64/machine_ir.h"

namespace berberis::x86_64 {

void VRegMap::AssignNewVRegs() {
  for (auto* bb : machine_ir_->bb_list()) {
    for (auto* insn : bb->insn_list()) {
      for (int i = 0; i < insn->NumRegOperands(); ++i) {
        auto reg = insn->RegAt(i);
        if (reg.IsVReg()) {
          insn->SetRegAt(i, Get(reg, bb));
          auto& max_size = max_size_.at(reg.GetVRegIndex());
          max_size = std::max(max_size, insn->RegKindAt(i).RegClass()->RegSize());
        }
      }
    }
  }
}

MachineReg VRegMap::Get(MachineReg reg, const MachineBasicBlock* bb) {
  CHECK(reg.IsVReg());
  MachineReg& mapped_reg = map_.at(bb->id()).at(reg.GetVRegIndex());
  if (mapped_reg == kInvalidMachineReg) {
    mapped_reg = machine_ir_->AllocVReg();
  }
  return mapped_reg;
}

void GenInterBasicBlockMove(MachineIR* machine_ir,
                            VRegMap* vreg_map,
                            MachineBasicBlock* pred_bb,
                            MachineBasicBlock* succ_bb,
                            MachineReg vreg) {
  MachineReg pred_vreg = vreg_map->Get(vreg, pred_bb);
  MachineReg succ_vreg = vreg_map->Get(vreg, succ_bb);
  PseudoCopy* insn =
      machine_ir->NewInsn<PseudoCopy>(succ_vreg, pred_vreg, vreg_map->GetMaxSize(vreg));

  if (succ_bb->in_edges().size() == 1) {
    // Successor has single pred.
    // Build move at the beginning of succ.
    succ_bb->insn_list().insert(succ_bb->insn_list().begin(), insn);
    succ_bb->live_in().push_back(pred_vreg);
    pred_bb->live_out().push_back(pred_vreg);
  } else {
    // Successor has multiple preds.
    // Assume no critical edges, so pred has just one succ.
    // Build move at the end of pred.
    CHECK_EQ(pred_bb->out_edges().size(), 1);
    pred_bb->insn_list().insert(--pred_bb->insn_list().end(), insn);
    succ_bb->live_in().push_back(succ_vreg);
    pred_bb->live_out().push_back(succ_vreg);
  }
}

// Rename vregs so that they have different names in different basic blocks
// and build the data-flow connecting moves.
void RenameVRegs(MachineIR* machine_ir) {
  LivenessAnalyzer liveness(machine_ir);
  VRegMap vreg_map(machine_ir);

  // We want to analyze liveness before assigning new vregs.
  liveness.Run();
  vreg_map.AssignNewVRegs();

  // Build moves connecting the data-flow.
  for (auto bb : machine_ir->bb_list()) {
    for (auto edge : bb->out_edges()) {
      auto succ_bb = edge->dst();
      for (auto vreg = liveness.GetFirstLiveIn(succ_bb); vreg != kInvalidMachineReg;
           vreg = liveness.GetNextLiveIn(succ_bb, vreg)) {
        GenInterBasicBlockMove(machine_ir, &vreg_map, bb, succ_bb, vreg);
      }
    }
  }
}

}  // namespace berberis::x86_64
