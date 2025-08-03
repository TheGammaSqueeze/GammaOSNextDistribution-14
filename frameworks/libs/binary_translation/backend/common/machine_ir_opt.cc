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

#include "berberis/backend/common/machine_ir_opt.h"

#include <utility>  // std::swap

#include "berberis/backend/common/machine_ir.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"

namespace berberis {

// Remove those PSEUDO_COPY instructions with identical source and
// destination operands.
void RemoveNopPseudoCopy(MachineIR* machine_ir) {
  for (auto* machine_bb : machine_ir->bb_list()) {
    machine_bb->insn_list().remove_if([](MachineInsn* machine_insn) {
      return machine_insn->opcode() == PseudoCopy::kOpcode &&
             machine_insn->RegAt(0) == machine_insn->RegAt(1);
    });
  }
}

// Remove forwarder blocks, those basic blocks that contain nothing
// but unconditional jumps.  Jumps to those forwarder blocks are
// redirected to their respective final destinations.
void RemoveForwarderBlocks(MachineIR* machine_ir) {
  // A map from a MachineBasicBlock ID to MachineBasicBlock* to
  // describe where forwarder blocks take us to.  Specifically, if
  // basic block BB is a forwarder block, then forwarder_map[BB]
  // points to the MachineBasicBlock that the forwarder block jumps
  // to.  Otherwise, forwarder_map[BB] is nullptr.
  ArenaVector<const MachineBasicBlock*> forwarder_map(
      machine_ir->NumBasicBlocks(), nullptr, machine_ir->arena());

  // Identify forwarder blocks.  We store in forwarder_map mappings
  // from source blocks to destination blocks.
  for (const auto* machine_bb : machine_ir->bb_list()) {
    if (machine_bb->insn_list().size() != 1) continue;

    const MachineInsn* last_insn = machine_bb->insn_list().back();
    if (last_insn->opcode() == PseudoBranch::kOpcode) {
      const PseudoBranch* branch_insn = static_cast<const PseudoBranch*>(last_insn);
      forwarder_map[machine_bb->id()] = branch_insn->then_bb();
    }
  }

  // We might have a forwarder block that jumps to another forwarder
  // block.  Go through the entire map and determine the final
  // destination for each entry.
  //
  // Note that we *must* do this for correctness.  If we did not, then
  // a jump to a forwarder block that in turn jumps to another
  // forwarder block would get re-written as a jump to a deleted basic
  // block, which would be a disaster.
  for (size_t i = 0; i < forwarder_map.size(); i++) {
    auto* final_dest = forwarder_map[i];
    if (final_dest == nullptr) {
      continue;
    }

    unsigned steps = 0;
    while (auto* bb = forwarder_map[final_dest->id()]) {
      final_dest = bb;

      // Assert that we don't have a loop composed of forwarder
      // blocks only.
      ++steps;
      CHECK_LT(steps, forwarder_map.size());
    }
    forwarder_map[i] = final_dest;
  }

  // Redirect jumps to forwarder blocks.
  for (const auto* machine_bb : machine_ir->bb_list()) {
    const MachineInsnList& insn_list = machine_bb->insn_list();
    if (insn_list.empty()) {
      continue;
    }

    MachineInsn* last_insn = insn_list.back();
    if (last_insn->opcode() == PseudoBranch::kOpcode) {
      PseudoBranch* branch_insn = static_cast<PseudoBranch*>(last_insn);
      if (auto* new_dest = forwarder_map[branch_insn->then_bb()->id()]) {
        branch_insn->set_then_bb(new_dest);
      }
    } else if (last_insn->opcode() == PseudoCondBranch::kOpcode) {
      PseudoCondBranch* branch_insn = static_cast<PseudoCondBranch*>(last_insn);
      if (auto* new_then_bb = forwarder_map[branch_insn->then_bb()->id()]) {
        branch_insn->set_then_bb(new_then_bb);
      }
      if (auto* new_else_bb = forwarder_map[branch_insn->else_bb()->id()]) {
        branch_insn->set_else_bb(new_else_bb);
      }
    }
  }

  // Don't remove the first basic block even if it is a forwarder
  // block.  Since it is the entry point into the region, removing it
  // could change the semantics of the region if it jumps to a basic
  // block other than the second one, in which case the entry point is
  // actually changed.
  forwarder_map[machine_ir->bb_list().front()->id()] = nullptr;

  // Remove forwarder blocks.
  machine_ir->bb_list().remove_if([&forwarder_map](const MachineBasicBlock* machine_bb) {
    return forwarder_map[machine_bb->id()] != nullptr;
  });
}

// Reorder basic blocks so that recovery basic blocks come at the end
// of the basic block chain.
//
// By moving recovery basic blocks to the end of the basic block
// chain, we solve two problems -- improving cache locality while
// avoiding unconditional jumps around recovery basic blocks.  In
// future, we might generalize this function to handle other cold
// blocks and such.
//
// Moving exit blocks to the end doesn't break MachineIR::BasicBlocksOrder::kReversePostOrder
// properties we are interested in. So we intentionally keep this order valid if it was set.
void MoveColdBlocksToEnd(MachineIR* machine_ir) {
  // Since the first bb is region entry, we must keep it in
  // place. Fortunately, a recovery block cannot be the first one,
  // since it must follow a faulty instruction.
  CHECK(!machine_ir->bb_list().front()->is_recovery());

  // We are going to partition bb_list() into normal and recovery
  // basic blocks. We preserve the order of normal basic blocks so
  // that they will more likely fall through (that is, without
  // unconditional jumps around recovery basic blocks). We do not
  // preserve the order of recovery basic blocks.
  //
  // We've chosen not to use std::stable_partition because we don't
  // like the fact that it allocates a temporary buffer in the regular
  // heap.
  auto* bb_list = &(machine_ir->bb_list());
  auto normal_it = bb_list->begin();
  for (auto*& bb : *bb_list) {
    if (!bb->is_recovery()) {
      std::swap(*normal_it++, bb);  // can be the same
    }
  }
}

}  // namespace berberis
