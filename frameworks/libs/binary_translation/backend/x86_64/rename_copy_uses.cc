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

#include "berberis/backend/x86_64/rename_copy_uses.h"

#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"

namespace berberis::x86_64 {

MachineReg RenameCopyUsesMap::Get(MachineReg reg) {
  MachineReg renamed = RenameDataForReg(reg).renamed;
  if (renamed == kInvalidMachineReg) {
    return kInvalidMachineReg;
  }
  if (RenameDataForReg(renamed).last_def_time > RenameDataForReg(reg).renaming_time) {
    return kInvalidMachineReg;
  }
  return renamed;
}

void RenameCopyUsesMap::RenameUseIfMapped(MachineInsn* insn, int i) {
  // Narrow type uses may require a copy for register allocator to successfully handle them.
  // TODO(b/200327919): It'd better to make CallImmArg specify the exact narrow class for
  // the corresponding call argument. Then we wouldn't need to special case it.
  if (insn->opcode() == kMachineOpCallImmArg || insn->RegKindAt(i).RegClass()->NumRegs() == 1) {
    return;
  }
  MachineReg reg = insn->RegAt(i);
  if (!reg.IsVReg()) {
    return;
  }
  // Renaming is only possible for USE without DEF.
  MachineReg mapped = Get(reg);
  if (mapped != kInvalidMachineReg) {
    insn->SetRegAt(i, mapped);
  }
}

void RenameCopyUsesMap::ProcessDef(MachineInsn* insn, int i) {
  MachineReg reg = insn->RegAt(i);

  if (!reg.IsVReg()) {
    return;
  }

  RenameDataForReg(reg) = {kInvalidMachineReg, 0, time_};
}

void RenameCopyUsesMap::ProcessCopy(MachineInsn* copy) {
  auto dst = copy->RegAt(0);
  auto src = copy->RegAt(1);
  if (!dst.IsVReg() || !src.IsVReg()) {
    return;
  }

  if (Contains(bb_->live_out(), dst)) {
    // If dst is a live-out then renaming it to src won't help eliminate the copy. So instead we
    // rename src to dst. In an unlikely event when src is also a live-out it doesn't matter
    // which one we rename.
    RenameDataForReg(src).renamed = dst;
    RenameDataForReg(src).renaming_time = time_;
  } else {
    RenameDataForReg(dst) = {src, time_, time_};
  }
}

void RenameCopyUsesMap::StartBasicBlock(MachineBasicBlock* bb) {
  bb_ = bb;
  for (auto& data : map_) {
    data = {kInvalidMachineReg, 0, 0};
  }
}

void RenameCopyUses(MachineIR* machine_ir) {
  RenameCopyUsesMap map(machine_ir);

  for (auto* bb : machine_ir->bb_list()) {
    map.StartBasicBlock(bb);

    for (MachineInsn* insn : bb->insn_list()) {
      for (int i = 0; i < insn->NumRegOperands(); ++i) {
        // Note that Def-Use operands cannot be renamed, so we handle them as Defs.
        if (insn->RegKindAt(i).IsDef()) {
          map.ProcessDef(insn, i);
        } else {
          map.RenameUseIfMapped(insn, i);
        }
      }  // for operand in insn

      // Note that we intentionally rename copy's use before attempting to create a mapping, so that
      // the existing mappings are applied and propagated further.
      if (insn->is_copy()) {
        map.ProcessCopy(insn);
      }
      map.Tick();
    }  // For insn in bb
  }    // For bb in IR
}

}  // namespace berberis::x86_64
