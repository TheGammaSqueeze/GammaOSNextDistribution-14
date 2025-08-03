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

#ifndef BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_H_
#define BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_H_

#include <iterator>
#include <optional>
#include <utility>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_analysis.h"
#include "berberis/base/arena_vector.h"

namespace berberis::x86_64 {

// Types exported for testing:
enum class MovType { kMovq, kMovdqa, kMovw, kMovsd };

struct MappedRegInfo {
  MachineReg reg;
  MovType mov_type;
  bool is_modified;
};

using MemRegMap = ArenaVector<std::optional<MappedRegInfo>>;

// Functions exported for testing:
void ReplaceGetAndUpdateMap(MachineIR* ir,
                            const MachineInsnList::iterator insn_it,
                            MemRegMap& mem_reg_map);
void ReplacePutAndUpdateMap(MachineIR* ir,
                            const MachineInsnList::iterator insn_it,
                            MemRegMap& mem_reg_map);
void GenerateGetInsns(MachineIR* ir, MachineBasicBlock* bb, const MemRegMap& mem_reg_map);
void GeneratePutInsns(MachineIR* ir, MachineBasicBlock* bb, const MemRegMap& mem_reg_map);
void GenerateGetsInPreloop(MachineIR* ir, const Loop* loop, const MemRegMap& mem_reg_map);
void GeneratePutsInPostloop(MachineIR* ir, const Loop* loop, const MemRegMap& mem_reg_map);
ArenaVector<int> CountGuestRegAccesses(const MachineIR* ir, const Loop* loop);

using OffsetCounterMap = ArenaVector<std::pair<size_t, int>>;
OffsetCounterMap GetSortedOffsetCounters(MachineIR* ir, Loop* loop);

struct OptimizeLoopParams {
  size_t general_reg_limit = 12;
  size_t simd_reg_limit = 12;
};

void OptimizeLoop(MachineIR* machine_ir,
                  Loop* loop,
                  const OptimizeLoopParams& params = OptimizeLoopParams());

// Loop optimization interface:
void RemoveLoopGuestContextAccesses(MachineIR* machine_ir);

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_LOOP_GUEST_CONTEXT_OPTIMIZER_H_
