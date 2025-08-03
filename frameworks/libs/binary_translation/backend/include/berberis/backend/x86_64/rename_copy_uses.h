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

#ifndef BERBERIS_BACKEND_X86_64_RENAME_COPY_USES_H_
#define BERBERIS_BACKEND_X86_64_RENAME_COPY_USES_H_

#include <stdint.h>

#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/arena_vector.h"

namespace berberis::x86_64 {

class RenameCopyUsesMap {
 public:
  explicit RenameCopyUsesMap(MachineIR* machine_ir)
      : map_(machine_ir->NumVReg(), {kInvalidMachineReg, 0, 0}, machine_ir->arena()) {}

  void RenameUseIfMapped(MachineInsn* insn, int i);
  void ProcessDef(MachineInsn* insn, int i);
  void ProcessCopy(MachineInsn* copy);
  void Tick() { time_++; }
  void StartBasicBlock(MachineBasicBlock* bb);

 private:
  struct RenameData {
    MachineReg renamed;
    uint64_t renaming_time;
    uint64_t last_def_time;
  };

  MachineReg Get(MachineReg reg);
  RenameData& RenameDataForReg(MachineReg reg) { return map_.at(reg.GetVRegIndex()); }

  ArenaVector<RenameData> map_;
  // Since we are not SSA or SSI we keep track of time of definitions to see if
  // mappings are still active.
  uint64_t time_ = 0;
  MachineBasicBlock* bb_;
};

void RenameCopyUses(MachineIR* machine_ir);

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_RENAME_COPY_USES_H_
