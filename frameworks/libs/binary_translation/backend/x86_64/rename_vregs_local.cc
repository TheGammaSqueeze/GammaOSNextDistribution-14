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

#include "berberis/backend/x86_64/rename_vregs_local.h"

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/vreg_bit_set.h"

namespace berberis::x86_64 {

namespace {

class VRegMap {
 public:
  VRegMap(size_t size, Arena* arena) : vreg_set_(size, kInvalidMachineReg, arena) {}

  void Set(MachineReg from_reg, MachineReg to_reg) { vreg_set_[from_reg.GetVRegIndex()] = to_reg; }
  [[nodiscard]] MachineReg Get(MachineReg reg) const { return vreg_set_[reg.GetVRegIndex()]; }

  [[nodiscard]] bool WasRenamed(MachineReg reg) const {
    return vreg_set_[reg.GetVRegIndex()] != kInvalidMachineReg &&
           vreg_set_[reg.GetVRegIndex()] != reg;
  }

  bool WasSeen(MachineReg reg) { return vreg_set_[reg.GetVRegIndex()] != kInvalidMachineReg; }

 private:
  ArenaVector<MachineReg> vreg_set_;
};

void MarkLiveInsAsSeen(VRegMap& vreg_map, MachineBasicBlock* basic_block) {
  for (auto in_reg : basic_block->live_in()) {
    vreg_map.Set(in_reg, in_reg);
  }
}

void TryRenameRegOperand(int operand_index,
                         VRegMap& vreg_map,
                         MachineInsnList::const_iterator insn_it,
                         MachineIR* machine_ir,
                         MachineInsnList& insn_list) {
  MachineInsn* insn = *insn_it;
  MachineReg reg = insn->RegAt(operand_index);

  if (!reg.IsVReg()) {
    return;
  }

  if (insn->RegKindAt(operand_index).IsDef()) {
    if (!vreg_map.WasSeen(reg)) {
      vreg_map.Set(reg, reg);
      return;
    }

    MachineReg new_reg = machine_ir->AllocVReg();

    // If instruction is also a use, insert a MOV definition before current one.
    if (insn->RegKindAt(operand_index).IsUse()) {
      insn_list.insert(insn_it, machine_ir->NewInsn<MovqRegReg>(new_reg, vreg_map.Get(reg)));
    }

    insn->SetRegAt(operand_index, new_reg);
    // Map or remap renamed register.
    vreg_map.Set(reg, new_reg);
  } else {
    // Register is a USE, and not a DEF.
    CHECK(insn->RegKindAt(operand_index).IsUse());
    // If it's in the map, use it.
    if (vreg_map.WasSeen(reg)) {
      insn->SetRegAt(operand_index, vreg_map.Get(reg));
    }
  }
}

void RenameInsnListRegs(VRegMap& vreg_map, MachineInsnList& insn_list, MachineIR* machine_ir) {
  for (auto insn_it = insn_list.begin(); insn_it != insn_list.end(); ++insn_it) {
    MachineInsn* insn = *insn_it;
    for (int i = 0; i < insn->NumRegOperands(); ++i) {
      // Renames current register, if necessary - has various criteria depending on the type of the
      // register (i.e., register is a USE and/or DEF).
      TryRenameRegOperand(i, vreg_map, insn_it, machine_ir, insn_list);
    }  // for register in instruction
  }    // for instruction in instruction list
}

void RenameLiveOuts(VRegMap& vreg_map, MachineBasicBlock* basic_block) {
  for (auto& out_reg : basic_block->live_out()) {
    if (vreg_map.WasRenamed(out_reg)) {
      out_reg = vreg_map.Get(out_reg);
    }
  }
}

void RenameSuccessorsLiveIns(VRegMap& vreg_map,
                             MachineBasicBlock* basic_block,
                             MachineIR* machine_ir) {
  for (auto* out_edge : basic_block->out_edges()) {
    for (auto& in_reg : out_edge->dst()->live_in()) {
      MachineReg old_reg = in_reg;
      if (vreg_map.WasRenamed(in_reg)) {
        in_reg = vreg_map.Get(in_reg);
        // Add a MOV instruction to preserve the existing register in the next basic block.
        auto& insn_list = out_edge->dst()->insn_list();
        insn_list.push_front(machine_ir->NewInsn<MovqRegReg>(old_reg, in_reg));
      }
    }  // for register in edge destination
  }    // for edge in out edges
}

}  // namespace

void RenameVRegsLocal(MachineIR* machine_ir) {
  for (auto* basic_block : machine_ir->bb_list()) {
    // Instead of using a boolean vector to track seen registers, the VReg is instead mapped to
    // itself; since the initial value of all mapped VRegs is 0, we can track registers DEFs never
    // seen, seen once, and seen two or more times, all in a single vector (as demonstrated by the
    // above functions).
    VRegMap vreg_map(machine_ir->NumVReg(), machine_ir->arena());
    MachineInsnList& insn_list = basic_block->insn_list();

    MarkLiveInsAsSeen(vreg_map, basic_block);
    RenameInsnListRegs(vreg_map, insn_list, machine_ir);

    RenameLiveOuts(vreg_map, basic_block);
    RenameSuccessorsLiveIns(vreg_map, basic_block, machine_ir);
  }
}

}  // namespace berberis::x86_64