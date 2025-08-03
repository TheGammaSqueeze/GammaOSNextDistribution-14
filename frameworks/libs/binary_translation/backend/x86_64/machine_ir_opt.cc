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

#include <bitset>
#include <iterator>

#include "berberis/backend/x86_64/context_liveness_analyzer.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_analysis.h"
#include "berberis/backend/x86_64/machine_ir_opt.h"
#include "berberis/backend/x86_64/vreg_bit_set.h"
#include "berberis/base/arena_vector.h"

namespace berberis::x86_64 {

// TODO(b/232598137): Move more code in this file into the anonymous namespace.
namespace {

// Wrapper function for VRegBitSet, to ensure safe behavior of hardware registers - in particular,
// all hardware registers are considered to be used (and thus not dead). Hardware registers are not
// optimized, for efficiency's sake.
class RegUsageBitSet {
 public:
  RegUsageBitSet(const MachineIR* machine_ir)
      : reg_set_(machine_ir->NumVReg(), machine_ir->arena()) {}

  void Set(MachineReg reg) {
    if (reg.IsVReg()) {
      reg_set_.Set(reg);
    }
  }

  void Reset(MachineReg reg) {
    if (reg.IsVReg()) {
      reg_set_.Reset(reg);
    }
  }

  bool operator[](MachineReg reg) const {
    if (reg.IsVReg()) {
      return reg_set_[reg];
    } else {
      return true;
    }
  }

  void Clear() { reg_set_.Clear(); }

 private:
  VRegBitSet reg_set_;
};

bool AreResultsUsed(const MachineInsn* insn, const RegUsageBitSet& is_reg_used) {
  for (int i = 0; i < insn->NumRegOperands(); ++i) {
    if (insn->RegKindAt(i).IsDef() && is_reg_used[insn->RegAt(i)]) {
      return true;
    }
  }
  return false;
}

void SetInsnResultsUnused(const MachineInsn* insn, RegUsageBitSet& is_reg_used) {
  for (int i = 0; i < insn->NumRegOperands(); ++i) {
    if (insn->RegKindAt(i).IsDef()) {
      is_reg_used.Reset(insn->RegAt(i));
    }
  }
}

void SetInsnArgumentsUsed(const MachineInsn* insn, RegUsageBitSet& is_reg_used) {
  for (int i = 0; i < insn->NumRegOperands(); ++i) {
    if (insn->RegKindAt(i).IsUse()) {
      is_reg_used.Set(insn->RegAt(i));
    }
  }
}

}  // namespace

void RemoveDeadCode(MachineIR* machine_ir) {
  RegUsageBitSet is_reg_used(machine_ir);

  for (auto* bb : machine_ir->bb_list()) {
    is_reg_used.Clear();

    for (auto vreg : bb->live_out()) {
      is_reg_used.Set(vreg);
    }

    // Go from end to begin removing all unused instructions.
    for (auto insn_it = bb->insn_list().rbegin(); insn_it != bb->insn_list().rend();) {
      MachineInsn* insn = *insn_it++;

      if (!insn->has_side_effects() && !AreResultsUsed(insn, is_reg_used)) {
        // Note non trivial way in which reverse_iterator is erased.
        insn_it = MachineInsnList::reverse_iterator(bb->insn_list().erase(insn_it.base()));
        SetInsnResultsUnused(insn, is_reg_used);
        continue;
      }

      SetInsnResultsUnused(insn, is_reg_used);
      SetInsnArgumentsUsed(insn, is_reg_used);
    }  // For insn in bb
  }    // For bb in IR
}

void ChangeBranchTarget(MachineBasicBlock* bb,
                        MachineBasicBlock* old_dst,
                        MachineBasicBlock* new_dst) {
  CHECK_GT(bb->insn_list().size(), 0);
  auto last_insn = bb->insn_list().back();

  // The branch instruction can either be PseudoCondBranch or PseudoBranch.
  // When removing critical edges, the branch instruction is PseudoBranch if
  // and only if bb has an outedge to a recovery block.
  if (last_insn->opcode() == kMachineOpPseudoBranch) {
    auto insn = static_cast<PseudoBranch*>(last_insn);
    CHECK_EQ(insn->then_bb(), old_dst);
    insn->set_then_bb(new_dst);
    return;
  }

  CHECK(last_insn->opcode() == kMachineOpPseudoCondBranch);
  auto insn = static_cast<PseudoCondBranch*>(last_insn);
  if (insn->then_bb() == old_dst) {
    insn->set_then_bb(new_dst);
  } else if (insn->else_bb() == old_dst) {
    insn->set_else_bb(new_dst);
  }
}

void InsertNodeOnEdge(MachineIR* ir, MachineEdge* edge, int in_edge_index) {
  MachineBasicBlock* pred_bb = edge->src();
  MachineBasicBlock* succ_bb = edge->dst();
  MachineBasicBlock* new_bb = ir->NewBasicBlock();
  ir->bb_list().push_back(new_bb);

  // Create a new edge between new_bb and bb.
  MachineEdge* new_edge = NewInArena<MachineEdge>(ir->arena(), ir->arena(), new_bb, succ_bb);
  new_bb->out_edges().push_back(new_edge);
  succ_bb->in_edges()[in_edge_index] = new_edge;

  // Reuse edge but change dst to new_bb.
  edge->set_dst(new_bb);
  new_bb->in_edges().push_back(edge);

  ChangeBranchTarget(pred_bb, succ_bb, new_bb);
  new_bb->insn_list().push_back(ir->NewInsn<PseudoBranch>(succ_bb));
}

void RemoveCriticalEdges(MachineIR* machine_ir) {
  for (auto bb : machine_ir->bb_list()) {
    if (bb->in_edges().size() < 2) {
      continue;
    }
    for (size_t i = 0; i < bb->in_edges().size(); i++) {
      MachineEdge* edge = bb->in_edges()[i];
      MachineBasicBlock* pred_bb = edge->src();
      if (pred_bb->out_edges().size() >= 2) {
        // This is a critical edge!
        InsertNodeOnEdge(machine_ir, edge, i);
      }
    }
  }
}

MachineInsnList::reverse_iterator RemovePutIfDead(const ContextLivenessAnalyzer* analyzer,
                                                  MachineBasicBlock* bb,
                                                  MachineInsnList::reverse_iterator insn_it,
                                                  const std::bitset<sizeof(CPUState)> seen_get) {
  CHECK_NE(bb->out_edges().size(), 0);
  auto* insn = AsMachineInsnX86_64(*insn_it);
  auto disp = insn->disp();

  if (seen_get.test(disp)) {
    return ++insn_it;
  }

  for (auto out_edge : bb->out_edges()) {
    auto dst = out_edge->dst();
    if (analyzer->IsLiveIn(dst, disp)) {
      return ++insn_it;
    }
  }

  // The instruction writes to dead context.
  auto forward_it = --(insn_it.base());
  auto next_it = bb->insn_list().erase(forward_it);
  std::reverse_iterator<MachineInsnList::iterator> rev_it(next_it);
  return rev_it;
}

void RemoveRedundantPut(MachineIR* ir) {
  ContextLivenessAnalyzer analyzer(ir);
  analyzer.Init();
  std::bitset<sizeof(CPUState)> seen_get;
  for (auto* bb : ir->bb_list()) {
    if (bb->out_edges().size() == 0) {
      // We are only looking for PUTs that are dead due to other PUTs in
      // sucessor basic blocks, because RemoveLocalGuestContextAccesses ensures
      // that there is at most one PUT to each guest reg in a basic block.
      continue;
    }

    seen_get.reset();
    for (auto insn_it = bb->insn_list().rbegin(); insn_it != bb->insn_list().rend();) {
      auto* insn = AsMachineInsnX86_64(*insn_it);
      if (insn->IsCPUStatePut()) {
        insn_it = RemovePutIfDead(&analyzer, bb, insn_it, seen_get);
      } else {
        if (insn->IsCPUStateGet()) {
          seen_get.set(insn->disp(), true);
        }
        insn_it++;
      }
    }
  }
}

bool IsForwarderBlock(MachineBasicBlock* bb) {
  if (bb->insn_list().size() != 1) {
    return false;
  }

  // Don't remove the entry block.
  if (bb->in_edges().size() == 0) {
    return false;
  }

  // Don't remove self-loop. We don't need to check for loops formed by
  // a sequence of forwarders since we can remove all of them but the
  // last one, which will be excluded by this condition.
  if (bb->out_edges().size() == 1 && bb->out_edges()[0]->dst() == bb) {
    return false;
  }

  const MachineInsn* last_insn = bb->insn_list().back();
  return last_insn->opcode() == PseudoBranch::kOpcode;
}

void UnlinkForwarderBlock(MachineBasicBlock* bb) {
  CHECK_EQ(bb->out_edges().size(), 1);
  auto dst = bb->out_edges()[0]->dst();
  for (auto edge : bb->in_edges()) {
    edge->set_dst(dst);
    dst->in_edges().push_back(edge);
    ChangeBranchTarget(edge->src(), bb, dst);
  }

  auto* edge = bb->out_edges()[0];
  auto dst_in_edge_it = std::find(dst->in_edges().begin(), dst->in_edges().end(), edge);
  CHECK(dst_in_edge_it != dst->in_edges().end());
  dst->in_edges().erase(dst_in_edge_it);
}

void RemoveForwarderBlocks(MachineIR* ir) {
  for (auto bb_it = ir->bb_list().begin(); bb_it != ir->bb_list().end();) {
    auto* bb = *bb_it;
    if (!IsForwarderBlock(bb)) {
      bb_it++;
      continue;
    }

    UnlinkForwarderBlock(bb);
    bb_it = ir->bb_list().erase(bb_it);
  }
}

void ReorderBasicBlocksInReversePostOrder(MachineIR* ir) {
  ir->bb_list() = GetReversePostOrderBBList(ir);
  ir->set_bb_order(MachineIR::BasicBlockOrder::kReversePostOrder);
}

}  // namespace berberis::x86_64
