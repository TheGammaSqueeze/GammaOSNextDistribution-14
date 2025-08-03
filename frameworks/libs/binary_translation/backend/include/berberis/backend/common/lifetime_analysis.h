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

#ifndef BERBERIS_BACKEND_COMMON_LIFETIME_ANALYSIS_H_
#define BERBERIS_BACKEND_COMMON_LIFETIME_ANALYSIS_H_

#include "berberis/backend/common/lifetime.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"

namespace berberis {

// Helper class for building sorted list of vreg lifetimes.
class VRegLifetimeAnalysis {
 public:
  VRegLifetimeAnalysis(Arena* arena, int num_vreg, VRegLifetimeList* lifetimes)
      : arena_(arena),
        lifetimes_(lifetimes),
        tick_(0),
        bb_tick_(0),
        vreg_lifetimes_(num_vreg, nullptr, arena) {}

  void AddInsn(const MachineInsnListPosition& pos);

  void SetLiveIn(MachineReg r) {
    // Ensure lifetime exists and includes current tick.
    CHECK_EQ(tick_, bb_tick_);
    GetVRegLifetime(r, tick_);
  }

  void SetLiveOut(MachineReg r) {
    // Lifetime must exist, extend to current tick.
    auto* lifetime = vreg_lifetimes_[r.GetVRegIndex()];
    CHECK(lifetime);
    lifetime->set_end(tick_);
  }

  void EndBasicBlock() { bb_tick_ = tick_; }

 private:
  // Ensure lifetime exists and includes 'begin'.
  VRegLifetime* GetVRegLifetime(MachineReg r, int begin);

  void AppendUse(const VRegUse& use);

  void TrySetMoveHint(const MachineInsn* insn);

  Arena* arena_;

  VRegLifetimeList* lifetimes_;

  int tick_;

  int bb_tick_;

  // Map vreg index -> lifetime.
  ArenaVector<VRegLifetime*> vreg_lifetimes_;
};

}  // namespace berberis

#endif  // BERBERIS_BACKEND_COMMON_LIFETIME_ANALYSIS_H_
