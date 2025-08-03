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

#ifndef BERBERIS_BACKEND_X86_64_RENAME_VREGS_H_
#define BERBERIS_BACKEND_X86_64_RENAME_VREGS_H_

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"

namespace berberis::x86_64 {

// Exported for testing only.
class VRegMap {
 public:
  explicit VRegMap(MachineIR* machine_ir)
      : machine_ir_(machine_ir),
        map_(
            machine_ir->NumBasicBlocks(),
            ArenaVector<MachineReg>(machine_ir->NumVReg(), kInvalidMachineReg, machine_ir->arena()),
            machine_ir->arena()),
        max_size_(machine_ir->NumVReg(), 0, machine_ir->arena()) {}

  // Rename vregs so that they have different numbers in different basic blocks.
  // Remember the mapping, so that it can be retrieved by Get().
  void AssignNewVRegs();

  MachineReg Get(MachineReg reg, const MachineBasicBlock* bb);
  [[nodiscard]] int GetMaxSize(MachineReg reg) const { return max_size_.at(reg.GetVRegIndex()); }

 private:
  MachineIR* machine_ir_;
  ArenaVector<ArenaVector<MachineReg>> map_;
  ArenaVector<int> max_size_;
};

void RenameVRegs(MachineIR* machine_ir);

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_RENAME_VREGS_H_
