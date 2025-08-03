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

#ifndef BERBERIS_BACKEND_X86_64_LIVENESS_ANALYZER_H_
#define BERBERIS_BACKEND_X86_64_LIVENESS_ANALYZER_H_

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/vreg_bit_set.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"

namespace berberis::x86_64 {

class LivenessAnalyzer {
 public:
  explicit LivenessAnalyzer(const MachineIR* machine_ir)
      : machine_ir_(machine_ir),
        live_in_(machine_ir->NumBasicBlocks(),
                 VRegBitSet(machine_ir->NumVReg(), machine_ir->arena()),
                 machine_ir->arena()) {}

  void Run();

  bool IsLiveIn(const MachineBasicBlock* bb, MachineReg reg) const {
    return live_in_[bb->id()][reg];
  }

  // We provide live-in iterators instead of exposing individual bit-sets
  // because with an efficient VRegBitSet implementation these methods can be made
  // faster.
  MachineReg GetFirstLiveIn(const MachineBasicBlock* bb) const {
    return GetNextLiveIn(bb, kInvalidMachineReg);
  }

  MachineReg GetNextLiveIn(const MachineBasicBlock* bb, MachineReg prev) const {
    CHECK(prev == kInvalidMachineReg || prev.IsVReg());
    CHECK(prev == kInvalidMachineReg || prev.GetVRegIndex() < static_cast<uint32_t>(NumVReg()));

    for (uint32_t vreg_index = (prev == kInvalidMachineReg ? 0 : prev.GetVRegIndex() + 1);
         vreg_index < static_cast<uint32_t>(NumVReg());
         vreg_index++) {
      MachineReg vreg = MachineReg::CreateVRegFromIndex(vreg_index);
      if (IsLiveIn(bb, vreg)) {
        return vreg;
      }
    }
    return kInvalidMachineReg;
  }

 private:
  [[nodiscard]] int NumVReg() const {
    CHECK_GT(live_in_.size(), 0);
    return live_in_[0].Size();
  }

  bool VisitBasicBlock(const MachineBasicBlock* bb);

  const MachineIR* machine_ir_;
  // Contains a bit-set of live registers for each basic block.
  ArenaVector<VRegBitSet> live_in_;
};

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_LIVENESS_ANALYZER_H_
