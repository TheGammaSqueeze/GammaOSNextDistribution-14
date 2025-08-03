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

#include "berberis/backend/x86_64/loop_guest_context_optimizer.h"

#include <algorithm>
#include <functional>
#include <tuple>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/logging.h"
#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis::x86_64 {

void ReplaceGetAndUpdateMap(MachineIR* ir,
                            const MachineInsnList::iterator insn_it,
                            MemRegMap& mem_reg_map) {
  auto* insn = AsMachineInsnX86_64(*insn_it);
  auto disp = insn->disp();

  MovType regtype;
  switch (insn->opcode()) {
    case kMachineOpMovqRegMemBaseDisp:
      regtype = MovType::kMovq;
      break;
    case kMachineOpMovdqaXRegMemBaseDisp:
      regtype = MovType::kMovdqa;
      break;
    case kMachineOpMovwRegMemBaseDisp:
      regtype = MovType::kMovw;
      break;
    case kMachineOpMovsdXRegMemBaseDisp:
      regtype = MovType::kMovsd;
      break;
    default:
      LOG_ALWAYS_FATAL("Unrecognized Get instruction opcode");
  }

  if (!mem_reg_map[disp].has_value()) {
    auto reg = ir->AllocVReg();
    mem_reg_map[disp] = {reg, regtype, false};
  }

  auto dst = insn->RegAt(0);
  auto copy_size = insn->opcode() == kMachineOpMovdqaXRegMemBaseDisp ? 16 : 8;
  auto* new_insn = ir->NewInsn<PseudoCopy>(dst, mem_reg_map[disp].value().reg, copy_size);
  *insn_it = new_insn;
}

void ReplacePutAndUpdateMap(MachineIR* ir,
                            const MachineInsnList::iterator insn_it,
                            MemRegMap& mem_reg_map) {
  auto* insn = AsMachineInsnX86_64(*insn_it);
  auto disp = insn->disp();

  MovType regtype;
  switch (insn->opcode()) {
    case kMachineOpMovqMemBaseDispReg:
      regtype = MovType::kMovq;
      break;
    case kMachineOpMovdqaMemBaseDispXReg:
      regtype = MovType::kMovdqa;
      break;
    case kMachineOpMovwMemBaseDispReg:
      regtype = MovType::kMovw;
      break;
    case kMachineOpMovsdMemBaseDispXReg:
      regtype = MovType::kMovsd;
      break;
    default:
      LOG_ALWAYS_FATAL("Unrecognized Put instruction opcode");
  }

  if (!mem_reg_map[disp].has_value()) {
    auto reg = ir->AllocVReg();
    mem_reg_map[disp] = {reg, regtype, true};
  } else {
    mem_reg_map[disp].value().is_modified = true;
  }

  auto src = insn->RegAt(1);
  auto copy_size = insn->opcode() == kMachineOpMovdqaMemBaseDispXReg ? 16 : 8;
  auto* new_insn = static_cast<MachineInsn*>(
      ir->NewInsn<PseudoCopy>(mem_reg_map[disp].value().reg, src, copy_size));
  *insn_it = new_insn;
}

void GenerateGetInsns(MachineIR* ir, MachineBasicBlock* bb, const MemRegMap& mem_reg_map) {
  // Check that there is no critical edge.
  CHECK_EQ(bb->out_edges().size(), 1);

  auto insert_it = std::prev(bb->insn_list().end());
  for (unsigned long disp = 0; disp < mem_reg_map.size(); disp++) {
    if (!mem_reg_map[disp].has_value()) {
      continue;
    }

    // It's tempting to generate Get only if there is a Get insn for the guest register in the loop,
    // but it would be incorrect because the loop can exit without updating
    // the mapped register, making the afterloop loading from the uninitialized
    // mapped register.

    // TODO(b/203826752) Do not generate the Get insn if the initialization of the mapped
    // register is not needed.
    auto reg_info = mem_reg_map[disp].value();
    MachineInsn* get_insn;
    switch (reg_info.mov_type) {
      case MovType::kMovq:
        get_insn = ir->NewInsn<MovqRegMemBaseDisp>(reg_info.reg, kMachineRegRBP, disp);
        break;
      case MovType::kMovdqa:
        get_insn = ir->NewInsn<MovdqaXRegMemBaseDisp>(reg_info.reg, kMachineRegRBP, disp);
        break;
      case MovType::kMovw:
        get_insn = ir->NewInsn<MovwRegMemBaseDisp>(reg_info.reg, kMachineRegRBP, disp);
        break;
      case MovType::kMovsd:
        get_insn = ir->NewInsn<MovsdXRegMemBaseDisp>(reg_info.reg, kMachineRegRBP, disp);
        break;
    }

    bb->insn_list().insert(insert_it, get_insn);
  }
}

void GeneratePutInsns(MachineIR* ir, MachineBasicBlock* bb, const MemRegMap& mem_reg_map) {
  // Check that there is no critical edge.
  CHECK_EQ(bb->in_edges().size(), 1);

  auto insert_it = bb->insn_list().begin();
  for (unsigned long disp = 0; disp < mem_reg_map.size(); disp++) {
    if (!mem_reg_map[disp].has_value()) {
      continue;
    }

    auto reg_info = mem_reg_map[disp].value();
    if (!reg_info.is_modified) {
      continue;
    }

    MachineInsn* put_insn;
    switch (reg_info.mov_type) {
      case MovType::kMovq:
        put_insn = ir->NewInsn<MovqMemBaseDispReg>(kMachineRegRBP, disp, reg_info.reg);
        break;
      case MovType::kMovdqa:
        put_insn = ir->NewInsn<MovdqaMemBaseDispXReg>(kMachineRegRBP, disp, reg_info.reg);
        break;
      case MovType::kMovw:
        put_insn = ir->NewInsn<MovwMemBaseDispReg>(kMachineRegRBP, disp, reg_info.reg);
        break;
      case MovType::kMovsd:
        put_insn = ir->NewInsn<MovsdMemBaseDispXReg>(kMachineRegRBP, disp, reg_info.reg);
        break;
    }

    bb->insn_list().insert(insert_it, put_insn);
  }
}

void GenerateGetsInPreloop(MachineIR* ir, const Loop* loop, const MemRegMap& mem_reg_map) {
  auto* header = (*loop)[0];
  CHECK_GE(header->in_edges().size(), 2);
  for (auto in_edge : header->in_edges()) {
    if (Contains(*loop, in_edge->src())) {
      // The source of the edge is inside the loop.
      continue;
    }

    GenerateGetInsns(ir, in_edge->src(), mem_reg_map);
  }
}

void GeneratePutsInPostloop(MachineIR* ir, const Loop* loop, const MemRegMap& mem_reg_map) {
  for (auto bb : *loop) {
    for (auto* out_edge : bb->out_edges()) {
      if (Contains(*loop, out_edge->dst())) {
        continue;
      }

      GeneratePutInsns(ir, out_edge->dst(), mem_reg_map);
    }
  }
}

ArenaVector<int> CountGuestRegAccesses(const MachineIR* ir, const Loop* loop) {
  ArenaVector<int> guest_access_count(sizeof(CPUState), 0, ir->arena());
  for (auto* bb : *loop) {
    for (auto* base_insn : bb->insn_list()) {
      auto insn = AsMachineInsnX86_64(base_insn);
      if (insn->IsCPUStateGet() || insn->IsCPUStatePut()) {
        guest_access_count.at(insn->disp())++;
      }
    }
  }
  return guest_access_count;
}

OffsetCounterMap GetSortedOffsetCounters(MachineIR* ir, Loop* loop) {
  auto guest_access_count = CountGuestRegAccesses(ir, loop);

  OffsetCounterMap offset_counter_map(ir->arena());
  for (size_t offset = 0; offset < sizeof(CPUState); offset++) {
    int cnt = guest_access_count.at(offset);
    if (cnt > 0) {
      offset_counter_map.push_back({offset, cnt});
    }
  }

  std::sort(offset_counter_map.begin(), offset_counter_map.end(), [](auto pair1, auto pair2) {
    return std::get<1>(pair1) > std::get<1>(pair2);
  });

  return offset_counter_map;
}

void OptimizeLoop(MachineIR* machine_ir, Loop* loop, const OptimizeLoopParams& params) {
  OffsetCounterMap sorted_offsets = GetSortedOffsetCounters(machine_ir, loop);
  ArenaVector<bool> optimized_offsets(sizeof(CPUState), false, machine_ir->arena());

  size_t general_reg_count = 0;
  size_t simd_reg_count = 0;
  for (auto [offset, unused_counter] : sorted_offsets) {
    // TODO(b/232598137) Account for f and v register classes.
    // Simd regs.
    if (IsSimdOffset(offset)) {
      if (simd_reg_count++ < params.simd_reg_limit) {
        optimized_offsets[offset] = true;
      }
      continue;
    }
    // General regs and flags.
    if (general_reg_count++ < params.general_reg_limit) {
      optimized_offsets[offset] = true;
    }
  }

  MemRegMap mem_reg_map(sizeof(CPUState), std::nullopt, machine_ir->arena());
  // Replace gets and puts with Pseudocopy and update mem_reg_map.
  for (auto* bb : *loop) {
    for (auto insn_it = bb->insn_list().begin(); insn_it != bb->insn_list().end(); insn_it++) {
      auto insn = AsMachineInsnX86_64(*insn_it);

      // Skip insn if it accesses regs with low priority
      if (insn->IsCPUStateGet() || insn->IsCPUStatePut()) {
        if (!optimized_offsets.at(insn->disp())) {
          continue;
        }
      }

      if (insn->IsCPUStateGet()) {
        ReplaceGetAndUpdateMap(machine_ir, insn_it, mem_reg_map);
      } else if (insn->IsCPUStatePut()) {
        ReplacePutAndUpdateMap(machine_ir, insn_it, mem_reg_map);
      }
    }
  }

  GenerateGetsInPreloop(machine_ir, loop, mem_reg_map);
  GeneratePutsInPostloop(machine_ir, loop, mem_reg_map);
}

bool ContainsCall(Loop* loop) {
  for (auto* bb : *loop) {
    for (auto* insn : bb->insn_list()) {
      if (AsMachineInsnX86_64(insn)->opcode() == kMachineOpCallImm) {
        return true;
      }
    }
  }
  return false;
}

template <typename PredicateFunction>
void OptimizeLoopTree(MachineIR* machine_ir, LoopTreeNode* node, PredicateFunction predicate) {
  if (node->loop() && predicate(node)) {
    OptimizeLoop(machine_ir, node->loop());
    return;
  }

  for (size_t i = 0; i < node->NumInnerloops(); i++) {
    OptimizeLoopTree(machine_ir, node->GetInnerloopNode(i), predicate);
  }
}

void RemoveLoopGuestContextAccesses(MachineIR* machine_ir) {
  // TODO(b/203826752): Provide a better heuristic for deciding which loop to optimize.
  auto loop_tree = BuildLoopTree(machine_ir);

  auto predicate = [](LoopTreeNode* node) -> bool {
    // TODO(b/203826752): Avoid repeating invoking ContainsCall for innerloops.
    return !ContainsCall(node->loop());
  };

  OptimizeLoopTree(machine_ir, loop_tree.root(), predicate);
}

}  // namespace berberis::x86_64
