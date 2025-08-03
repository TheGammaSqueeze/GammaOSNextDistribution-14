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

#include "berberis/backend/common/lifetime_analysis.h"

namespace berberis {

VRegLifetime* VRegLifetimeAnalysis::GetVRegLifetime(MachineReg r, int begin) {
  uint32_t i = r.GetVRegIndex();
  if (vreg_lifetimes_.size() < i + 1) {
    vreg_lifetimes_.resize(i + 1, nullptr);
  }
  VRegLifetime*& lifetime = vreg_lifetimes_[i];
  if (lifetime) {
    // Ensure the lifetime has live range for current basic block.
    // Use last live range begin to check that, as lifetime end might be equal
    // to bb_tick_ both when register lives out of prev basic block and when
    // register lives into current basic block but yet has no uses (so last
    // live range is [bb_tick_, bb_tick_)).
    if (lifetime->LastLiveRangeBegin() < bb_tick_) {
      lifetime->StartLiveRange(begin);
    }
  } else {
    // Newly created lifetime last live range will start at 'begin'.
    lifetimes_->push_back(VRegLifetime(arena_, begin));
    lifetime = &lifetimes_->back();
  }
  return lifetime;
}

void VRegLifetimeAnalysis::AppendUse(const VRegUse& use) {
  VRegLifetime* lifetime = GetVRegLifetime(use.GetVReg(), use.begin());
  lifetime->AppendUse(use);
}

// Set move hint for vreg to vreg move.
void VRegLifetimeAnalysis::TrySetMoveHint(const MachineInsn* insn) {
  if (!insn->is_copy()) {
    return;
  }

  // Copy should have 2 vreg operands.
  DCHECK_EQ(insn->NumRegOperands(), 2);
  MachineReg dst = insn->RegAt(0);
  if (!dst.IsVReg()) {
    return;
  }
  MachineReg src = insn->RegAt(1);
  if (!src.IsVReg()) {
    return;
  }

  // Lifetimes must exist.
  vreg_lifetimes_[dst.GetVRegIndex()]->SetMoveHint(vreg_lifetimes_[src.GetVRegIndex()]);
}

void VRegLifetimeAnalysis::AddInsn(const MachineInsnListPosition& pos) {
  const MachineInsn* insn = pos.insn();

  // To get lifetimes sorted by begin, first add use and use-def operands,
  // then def-only operands.

  // Walk use and use-def register operands.
  for (int i = 0; i < insn->NumRegOperands(); ++i) {
    // Skip non-virtual registers.
    MachineReg r = insn->RegAt(i);
    if (!r.IsVReg()) {
      continue;
    }

    // Skip def-only operands.
    const MachineRegKind& reg_kind = insn->RegKindAt(i);
    if (!reg_kind.IsUse()) {
      continue;
    }

    // Get range.
    int begin = tick_;
    int end = tick_ + (reg_kind.IsDef() ? 2 : 1);

    AppendUse(VRegUse(pos, i, begin, end));
  }

  // Walk def-only register operands.
  for (int i = 0; i < insn->NumRegOperands(); ++i) {
    // Skip non-virtual registers.
    MachineReg r = insn->RegAt(i);
    if (!r.IsVReg()) {
      continue;
    }

    // Skip use and use-def operands.
    const MachineRegKind& reg_kind = insn->RegKindAt(i);
    if (reg_kind.IsUse()) {
      continue;
    }

    // Get range.
    int begin = tick_ + 1;
    int end = tick_ + 2;

    // Append use.
    AppendUse(VRegUse(pos, i, begin, end));
  }

  TrySetMoveHint(insn);

  // Instruction have got 2 ticks:
  // - read inputs ('use' operands)
  // - write outputs ('def' operands)
  tick_ += 2;
}

}  // namespace berberis
