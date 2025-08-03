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

// At the moment, this implements more-or-less traditional linear scan register
// allocation.
//
// Input is virtual register lifetimes list, sorted by begin. Each lifetime is
// a list of continuous live ranges with lifetime holes in between. Each live
// range tracks insns that actually use the virtual register.
//
// Allocator walks sorted lifetime list and allocates lifetimes to hard
// registers. When lifetimes do not interfere, so live ranges of one lifetime
// fit into holes of another lifetime, both lifetimes can be allocated to the
// same hard register.
//
// If there is no available hard register, allocator selects hard register to
// free. All lifetimes allocated to that hard register that interfere with
// lifetime being allocated are spilled.
//
// Lifetime being spilled is split into tiny lifetimes, each for one insn
// where that virtual register is used. If register is read by insn, reload
// insn is added before, and if register is written by insn, spill insn is
// added after.
//
// Tiny lifetimes originated from spilling still need to be allocated. For tiny
// lifetimes that end before lifetime in favor of which they were spilled,
// previously allocated hard register is used. Tiny lifetimes that begin after
// are merged into a list of not yet allocated lifetimes.
//
// The problematic case is when tiny lifetime overlaps with begin of lifetime
// in favor of which it was spilled. In this case previously allocated hard
// register can't be used (otherwise it doesn't become free) and tiny lifetime
// can't be allocated later according to order by begin. In this case spill is
// considered impossible.
//
// The above is the most significant difference from classic linear scan
// register allocation algorithms, which usually solve tiny lifetimes
// allocation either by backtracking or using reserved registers. Hopefully
// this new approach works if there are more suitable hard registers than can
// be used in one insn, so there is always a suitable register that is not used
// at the point of spill.
//
// TODO(b/232598137): this might blow up when lifetimes compete for some single
// specific register (ecx for x86 shift insn)! Can be solved by generating code
// that minimizes lifetimes of such registers - moves in right before and moves
// out right after the insn using such register.
//
// TODO(b/232598137): investigate how code quality is affected when there are few
// available hard registers (x86_32)!

#include "berberis/backend/common/reg_alloc.h"

#include <iterator>  // std::next()
#include <string>

#include "berberis/backend/common/lifetime.h"
#include "berberis/backend/common/lifetime_analysis.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_list.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/config.h"
#include "berberis/base/logging.h"

// #define LOG_REG_ALLOC(...) ALOGE(__VA_ARGS__)
#define LOG_REG_ALLOC(...) ((void)0)

namespace berberis {

namespace {

// Lifetimes themselves are owned by lifetime list populated by lifetime
// analysis. Use list of pointers to track lifetimes currently allocated
// to particular hard register.
using VRegLifetimePtrList = ArenaList<VRegLifetime*>;

// How to spill one virtual register.
struct VRegLifetimeSpill {
  VRegLifetimePtrList::iterator lifetime;
  SplitPos realloc_pos;

  VRegLifetimeSpill(VRegLifetimePtrList::iterator i, const SplitPos& p)
      : lifetime(i), realloc_pos(p) {}
};

// Every possible spill should have some smaller weight (CHECKed).
const int kInfiniteSpillWeight = 99999;

// Track what virtual registers are currently allocated to this particular
// hard register, and how to spill them.
class HardRegAllocation {
 public:
  explicit HardRegAllocation(Arena* arena)
      : arena_(arena), lifetimes_(arena), new_lifetime_(nullptr), spills_(arena) {}

  HardRegAllocation(const HardRegAllocation& other) = default;

  // If new_lifetime doesn't interfere with lifetimes currently allocated
  // to this hard register, allocate new_lifetime to this register as well.
  bool TryAssign(VRegLifetime* new_lifetime);

  // If TryAssign returned false:
  // Check if it is possible to spill all lifetimes allocated to this hard
  // register that interfere with new_lifetime, and return spill weight.
  int ConsiderSpill(VRegLifetime* new_lifetime);

  // If ConsiderSpill returned non-infinite weight:
  // Given spill is possible, actually spill lifetimes that interfere with
  // new_lifetime to spill_slot. Insert newly created tiny lifetimes into
  // 'lifetimes' list, starting at position 'pos'.
  void SpillAndAssign(VRegLifetime* new_lifetime,
                      int spill_slot,
                      VRegLifetimeList* lifetimes,
                      VRegLifetimeList::iterator pos);

 private:
  // Arena for allocations.
  Arena* arena_;
  // Lifetimes currently allocated to this hard register.
  VRegLifetimePtrList lifetimes_;

  // Last lifetime being allocated, for CHECKing.
  // TODO(b/232598137): probably use this for ConsiderSpill and SpillAndAssign?
  // This looks more natural...
  VRegLifetime* new_lifetime_;

  // How to free this register for last considered new lifetime.
  // This is here for the following reasons:
  // - it is highly coupled with lifetimes_
  // - to avoid reallocating this for every spill consideration
  ArenaVector<VRegLifetimeSpill> spills_;
};

bool HardRegAllocation::TryAssign(VRegLifetime* new_lifetime) {
  // TODO(b/232598137): had to disable the check below! The problem is that when
  // new_lifetime_ is split so that there remains no live ranges, we can't
  // call begin() for it. Seems this place requires some rethinking, as such
  // case means we can simply reorder lifetimes instead of actually splitting...
  // Check lifetimes are processed in order by increasing begin.
  // CHECK(!new_lifetime_ || new_lifetime_->begin() <= new_lifetime->begin());
  new_lifetime_ = new_lifetime;

  for (auto curr = lifetimes_.begin(); curr != lifetimes_.end();) {
    VRegLifetime* curr_lifetime = *curr;

    if (curr_lifetime->end() <= new_lifetime->begin()) {
      // Curr lifetime ends before new lifetime starts, expire it.
      curr = lifetimes_.erase(curr);
    } else if (curr_lifetime->TestInterference(*new_lifetime)) {
      // Lifetimes interfere, can't assign.
      return false;
    } else {
      ++curr;
    }
  }

  // No lifetimes interfere with new, can assign.
  lifetimes_.push_back(new_lifetime);
  return true;
}

int HardRegAllocation::ConsiderSpill(VRegLifetime* new_lifetime) {
  CHECK_EQ(new_lifetime_, new_lifetime);

  spills_.clear();
  int weight = 0;

  for (auto curr = lifetimes_.begin(); curr != lifetimes_.end(); ++curr) {
    VRegLifetime* curr_lifetime = *curr;

    if (!curr_lifetime->TestInterference(*new_lifetime)) {
      // No interference, no need to spill.
      continue;
    }

    SplitPos split_pos;
    SplitKind split_kind = curr_lifetime->FindSplitPos(new_lifetime->begin(), &split_pos);
    if (split_kind == SPLIT_IMPOSSIBLE) {
      // Lifetimes interfere in such a way that spill is not possible.
      return kInfiniteSpillWeight;
    } else if (split_kind == SPLIT_CONFLICT) {
      // A use within this lifetime conflicts with first use in 'new_lifetime'.
      // If we spill it, it will compete with 'new_lifetime' at reallocation,
      // and if it can only use register suitable for 'new_lifetime' as well,
      // 'new_lifetime' can be evicted back, resulting in double spill.
      if (curr_lifetime->GetRegClass()->IsSubsetOf(new_lifetime->GetRegClass())) {
        return kInfiniteSpillWeight;
      }
    }

    // Record spill.
    spills_.push_back(VRegLifetimeSpill(curr, split_pos));
    // Evicting tiny lifetime is free.
    if (curr_lifetime->GetSpill() == -1) {
      weight += curr_lifetime->spill_weight();
    }
  }

  CHECK_LT(weight, kInfiniteSpillWeight);
  return weight;
}

// Same as std::list::merge, but starting from a 'pos' position.
void MergeVRegLifetimeList(VRegLifetimeList* dst,
                           VRegLifetimeList::iterator dst_pos,
                           VRegLifetimeList* src) {
  while (!src->empty()) {
    auto curr = src->begin();
    for (; dst_pos != dst->end(); ++dst_pos) {
      if (curr->begin() < dst_pos->begin()) {
        break;
      }
    }
    dst->splice(dst_pos, *src, curr);
  }
}

void HardRegAllocation::SpillAndAssign(VRegLifetime* new_lifetime,
                                       int spill_slot,
                                       VRegLifetimeList* lifetimes,
                                       VRegLifetimeList::iterator pos) {
  CHECK_EQ(new_lifetime_, new_lifetime);
  CHECK(!spills_.empty());

  for (const auto& spill : spills_) {
    VRegLifetime* spill_lifetime = *spill.lifetime;

    // Assign spill slot.
    // Lifetimes being spilled do not interfere, can share spill slot!
    // TODO(b/232598137): evicted tiny lifetime have spill slot already.
    // If we only evict tiny lifetimes, we might not need new spill_slot!
    // Allocate spill slot here when needed.
    if (spill_lifetime->GetSpill() == -1) {
      spill_lifetime->SetSpill(spill_slot);
    }

    // Split spilled lifetime into tiny lifetimes, enqueue them for allocation.
    VRegLifetimeList split(arena_);
    spill_lifetime->Split(spill.realloc_pos, &split);
    MergeVRegLifetimeList(lifetimes, pos, &split);

    // Expire spilled lifetime.
    lifetimes_.erase(spill.lifetime);
  }

  // Spilled all interfering lifetimes, can assign.
  lifetimes_.push_back(new_lifetime);
}

// Simple register allocator.
// Walk list of lifetimes sorted by begin and allocates in order.
// Modifies lifetimes that have been spilled and adds tiny lifetimes split
// from spilled lifetimes to the same list.
class VRegLifetimeAllocator {
 public:
  VRegLifetimeAllocator(MachineIR* machine_ir, VRegLifetimeList* lifetimes)
      : machine_ir_(machine_ir),
        lifetimes_(lifetimes),
        allocations_(config::kMaxHardRegs,
                     HardRegAllocation(machine_ir->arena()),
                     machine_ir->arena()) {}

  void Allocate();

 private:
  void AllocateLifetime(VRegLifetimeList::iterator lifetime_it);

  bool TryAssignHardReg(VRegLifetime* lifetime, MachineReg hard_reg);

  int ConsiderSpillHardReg(MachineReg hard_reg, VRegLifetime* lifetime);

  void SpillAndAssignHardReg(MachineReg hard_reg, VRegLifetimeList::iterator curr);

  void RewriteAllocatedLifetimes();

  MachineIR* machine_ir_;

  VRegLifetimeList* lifetimes_;

  ArenaVector<HardRegAllocation> allocations_;
};

int VRegLifetimeAllocator::ConsiderSpillHardReg(MachineReg hard_reg, VRegLifetime* lifetime) {
  return allocations_[hard_reg.reg()].ConsiderSpill(lifetime);
}

bool VRegLifetimeAllocator::TryAssignHardReg(VRegLifetime* curr_lifetime, MachineReg hard_reg) {
  if (allocations_[hard_reg.reg()].TryAssign(curr_lifetime)) {
    curr_lifetime->set_hard_reg(hard_reg);
    LOG_REG_ALLOC(".. to %s\n", GetMachineHardRegDebugName(hard_reg));
    return true;
  }
  return false;
}

void VRegLifetimeAllocator::SpillAndAssignHardReg(MachineReg hard_reg,
                                                  VRegLifetimeList::iterator curr) {
  auto next = std::next(curr);
  allocations_[hard_reg.reg()].SpillAndAssign(&*curr, machine_ir_->AllocSpill(), lifetimes_, next);
  curr->set_hard_reg(hard_reg);
  LOG_REG_ALLOC(".. to %s (after spill)\n", GetMachineHardRegDebugName(hard_reg));
}

void VRegLifetimeAllocator::AllocateLifetime(VRegLifetimeList::iterator lifetime_it) {
  VRegLifetime* lifetime = &*lifetime_it;
  const MachineRegClass* reg_class = lifetime->GetRegClass();

  LOG_REG_ALLOC(
      "allocating lifetime %s:\n%s", reg_class->GetDebugName(), lifetime->GetDebugString().c_str());

  // First try preferred register.
  MachineReg pref_reg = lifetime->FindMoveHint()->hard_reg();
  if (reg_class->HasReg(pref_reg) && TryAssignHardReg(lifetime, pref_reg)) {
    return;
  }

  // Walk registers from reg class.
  for (MachineReg hard_reg : *reg_class) {
    if (hard_reg != pref_reg && TryAssignHardReg(lifetime, hard_reg)) {
      return;
    }
  }

  LOG_REG_ALLOC("... failed to find free hard reg, will try spilling");

  // Walk registers again, consider each for spilling.
  int best_spill_weight = kInfiniteSpillWeight;
  MachineReg best_reg{0};
  for (MachineReg hard_reg : *reg_class) {
    int spill_weight = ConsiderSpillHardReg(hard_reg, lifetime);
    LOG_REG_ALLOC(
        "... consider spilling %s, weight %d", GetMachineHardRegDebugName(hard_reg), spill_weight);
    if (best_spill_weight > spill_weight) {
      best_spill_weight = spill_weight;
      best_reg = hard_reg;
    }
  }

  // Spill register with best spill weight.
  CHECK_LT(best_spill_weight, kInfiniteSpillWeight);
  SpillAndAssignHardReg(best_reg, lifetime_it);
}

void VRegLifetimeAllocator::RewriteAllocatedLifetimes() {
  for (auto& lifetime : *lifetimes_) {
    lifetime.Rewrite(machine_ir_);
  }
}

void VRegLifetimeAllocator::Allocate() {
  for (auto lifetime_it = lifetimes_->begin(); lifetime_it != lifetimes_->end(); ++lifetime_it) {
    AllocateLifetime(lifetime_it);
  }
  RewriteAllocatedLifetimes();
}

void CollectLifetimes(const MachineIR* machine_ir, VRegLifetimeList* lifetimes) {
  VRegLifetimeAnalysis lifetime_analysis(machine_ir->arena(), 2 * machine_ir->NumVReg(), lifetimes);

  // Not 'const' because we need pointer to modifiable bb->insn_list().
  for (auto* bb : machine_ir->bb_list()) {
    for (const auto reg : bb->live_in()) {
      lifetime_analysis.SetLiveIn(reg);
    }

    for (auto insn_it = bb->insn_list().begin(); insn_it != bb->insn_list().end(); ++insn_it) {
      lifetime_analysis.AddInsn(MachineInsnListPosition(&(bb->insn_list()), insn_it));
    }

    for (const auto reg : bb->live_out()) {
      lifetime_analysis.SetLiveOut(reg);
    }
    lifetime_analysis.EndBasicBlock();
  }
}

}  // namespace

void AllocRegs(MachineIR* machine_ir) {
  VRegLifetimeList lifetimes(machine_ir->arena());
  CollectLifetimes(machine_ir, &lifetimes);

  VRegLifetimeAllocator allocator(machine_ir, &lifetimes);
  allocator.Allocate();
}

}  // namespace berberis
