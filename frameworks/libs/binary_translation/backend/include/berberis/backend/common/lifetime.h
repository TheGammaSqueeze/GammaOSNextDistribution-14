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

#ifndef BERBERIS_BACKEND_COMMON_LIFETIME_H_
#define BERBERIS_BACKEND_COMMON_LIFETIME_H_

#include <string>

#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_list.h"
#include "berberis/base/logging.h"
#include "berberis/base/stringprintf.h"

#include "berberis/backend/common/machine_ir.h"

namespace berberis {

// One use of virtual register.
// TODO(b/232598137): should probably go inside machine IR?
// Or make it internal in VRegLifetime?
class VRegUse {
 public:
  VRegUse(const MachineInsnListPosition& pos, int index, int begin, int end)
      : pos_(pos), index_(index), begin_(begin), end_(end) {}

  MachineReg GetVReg() const { return pos_.insn()->RegAt(index_); }

  void RewriteVReg(MachineIR* machine_ir, MachineReg reg, int slot) {
    pos_.insn()->SetRegAt(index_, reg);
    if (slot != -1) {
      int offset = machine_ir->SpillSlotOffset(slot);
      MachineReg spill = MachineReg::CreateSpilledRegFromIndex(offset);
      int size = GetRegClass()->RegSize();
      if (IsUse()) {
        if (pos_.insn()->is_copy() && !pos_.insn()->RegAt(0).IsSpilledReg()) {
          // Rewrite the src of the copy itself, unless the result is mem-to-mem copy.
          CHECK_EQ(1, index_);
          pos_.insn()->SetRegAt(1, spill);
        } else {
          pos_.InsertBefore(machine_ir->NewInsn<PseudoCopy>(reg, spill, size));
        }
      }
      if (IsDef()) {
        if (pos_.insn()->is_copy() && !pos_.insn()->RegAt(1).IsSpilledReg()) {
          // Rewrite the dst of the copy itself, unless the result is mem-to-mem copy.
          CHECK_EQ(0, index_);
          pos_.insn()->SetRegAt(0, spill);
        } else {
          pos_.InsertAfter(machine_ir->NewInsn<PseudoCopy>(spill, reg, size));
        }
      }
    }
  }

  const MachineRegClass* GetRegClass() const { return pos_.insn()->RegKindAt(index_).RegClass(); }

  int begin() const { return begin_; }

  int end() const { return end_; }

  // One line.
  std::string GetDebugString() const {
    return StringPrintf("[%d, %d) %s", begin(), end(), GetMachineRegDebugString(GetVReg()).c_str());
  }

  // One line.
  std::string GetInsnDebugString() const { return pos_.insn()->GetDebugString(); }

  bool IsUse() const { return pos_.insn()->RegKindAt(index_).IsUse(); }

  bool IsDef() const { return pos_.insn()->RegKindAt(index_).IsDef(); }

 private:
  // Insn to rewrite and spill/reload insert position.
  MachineInsnListPosition pos_;
  // Index or register operand.
  int index_;
  // Range for interference test.
  int begin_;
  int end_;
};

using VRegUseList = ArenaList<VRegUse>;

// Continuous live range of virtual register.
class VRegLiveRange {
 public:
  VRegLiveRange(Arena* arena, int begin) : begin_(begin), end_(begin), use_list_(arena) {}

  VRegLiveRange(Arena* arena, const VRegUse& use)
      : begin_(use.begin()), end_(use.end()), use_list_(1, use, arena) {}

  int begin() const { return begin_; }

  // Modify begin only if there are no uses yet.
  void set_begin(int begin) {
    DCHECK_LE(begin_, begin);
    DCHECK_LE(end_, begin);
    DCHECK(use_list_.empty());
    begin_ = begin;
    end_ = begin;
  }

  int end() const { return end_; }

  void set_end(int end) {
    DCHECK_LE(end_, end);
    end_ = end;
  }

  const VRegUseList& use_list() const { return use_list_; }

  // Try to kill this!
  VRegUseList& use_list() { return use_list_; }

  void AppendUse(const VRegUse& use) {
    DCHECK_LE(begin_, use.begin());
    // It can happen that use overlaps previous use.
    // For example, if an insn 'FOO use_def, use' appears as 'FOO x, x',
    // then 'x' uses will come (ordered by begin) as [0, 2), [0, 1).
    // We record each use separately so we can rewrite them.
    // But because of this lame case we have to do extra check for set_end.
    // TODO(b/232598137): another option is to order uses as 'use, use_def, def'
    // in lifetime_analysis::AddInsn, so that uses with equal begin are sorted
    // by end.
    use_list_.push_back(use);
    if (end_ < use.end()) {
      end_ = use.end();
    }
  }

  // Multiline
  std::string GetDebugString() const {
    std::string out(StringPrintf("[%d, %d) {\n", begin(), end()));
    for (const auto& use : use_list_) {
      out += "  ";
      out += use.GetDebugString();
      out += "\n";
    }
    out += "}\n";
    return out;
  }

 private:
  // Actual live range, might start before first use and end after last use.
  int begin_;
  int end_;
  // Use list might be empty if register is live but not used :)
  VRegUseList use_list_;
};

using VRegLiveRangeList = ArenaList<VRegLiveRange>;

// We might consider spilling 'lifetime' to free its hard register 'reg'
// after 'begin' position to be used by 'new_lifetime'.
//
// We assume all lifetimes that start before 'begin' are already allocated.
// Here is what we do:
// - We assign spill slot to 'lifetime', virtual register of this lifetime
//   now lives in that spill slot;
// - If instructions needs virtual register to be in hard register, we create
//   a 'tiny' lifetime that only describes use of that virtual register in
//   that instruction. Such tiny lifetime can't be spilled;
// - Tiny lifetimes that start before 'begin' are allocated to 'reg'. This
//   doesn't create any conflicts with other lifetimes that start before
//   'begin';
// - Remaining tiny lifetimes start at or after 'begin', so will be allocated
//   in order, after 'new_lifetime';
// - 'reg' is now free at 'begin', so 'new_lifetime' can use it;
//
// If some tiny lifetime starts before but ends after 'begin', spilling is
// impossible. As that tiny lifetime starts before 'begin', it has to be
// allocated to 'reg', otherwise there might be conflicts (and we don't
// backtrack to resolve them), so 'reg' is not free at 'begin'.
//
// If some tiny lifetime starts right at 'begin', it means 'lifetime' and
// 'new_lifetime' virtual registers are used in the same instruction.
// Spilling is still possible, as that tiny lifetime will be allocated after
// 'new_lifetime', so 'new_lifetime' will use 'reg', and tiny lifetime will
// get some other hard register. However, that makes sense only if tiny
// lifetime will not compete with 'new_lifetime' for the same hard register,
// so we mark this case explicitly.

enum SplitKind { SPLIT_IMPOSSIBLE = 0, SPLIT_CONFLICT, SPLIT_OK };

struct SplitPos {
  VRegLiveRangeList::iterator range_it;
  VRegUseList::iterator use_it;
};

// Lifetime of virtual register.
// Basically, list of live ranges.
class VRegLifetime {
 public:
  using List = ArenaList<VRegLifetime>;

  VRegLifetime(Arena* arena, int begin)
      : arena_(arena),
        range_list_(1, VRegLiveRange(arena, begin), arena),
        reg_class_(nullptr),
        hard_reg_(0),
        spill_slot_(-1),
        spill_weight_(0),
        move_hint_(nullptr) {}

  VRegLifetime(Arena* arena, const VRegUse& use)
      : arena_(arena),
        range_list_(1, VRegLiveRange(arena, use), arena),
        reg_class_(use.GetRegClass()),
        hard_reg_(0),
        spill_slot_(-1),
        spill_weight_(1),
        move_hint_(nullptr) {}

  void StartLiveRange(int begin) {
    DCHECK_LE(end(), begin);
    range_list_.push_back(VRegLiveRange(arena_, begin));
  }

  void AppendUse(const VRegUse& use) {
    if (use.IsDef() && !use.IsUse() && end() < use.begin()) {
      // This is write-only use and there is a gap between it and previous use.
      // Can insert lifetime hole.
      if (range_list_.back().use_list().empty()) {
        // If current live range is still empty, this might be live-in
        // register that gets overwritten, so remove live-in.
        range_list_.back().set_begin(use.begin());
      } else {
        range_list_.push_back(VRegLiveRange(arena_, use.begin()));
      }
    }
    range_list_.back().AppendUse(use);
    // We assume reg classes are either nested or unrelated (so have no
    // common registers).
    if (reg_class_) {
      reg_class_ = reg_class_->GetIntersection(use.GetRegClass());
      CHECK(reg_class_);
    } else {
      reg_class_ = use.GetRegClass();
    }
    ++spill_weight_;
  }

  void set_hard_reg(MachineReg reg) { hard_reg_ = reg; }

  MachineReg hard_reg() const { return hard_reg_; }

  int GetSpill() const { return spill_slot_; }

  void SetSpill(int slot) {
    DCHECK_EQ(spill_slot_, -1);
    spill_slot_ = slot;
  }

  int spill_weight() const { return spill_weight_; }

  // If lifetimes are connected with reg to reg move, try allocating both on the
  // same register.
  // Implement move hints as disjoint set of lifetimes, with representative that
  // is allocated first (so no union by rank, only path compression).

  VRegLifetime* FindMoveHint() {
    if (move_hint_) {
      move_hint_ = move_hint_->FindMoveHint();
      return move_hint_;
    }
    return this;
  }

  void SetMoveHint(VRegLifetime* other) {
    VRegLifetime* hint = FindMoveHint();
    VRegLifetime* other_hint = other->FindMoveHint();
    // Select lifetime that begins first.
    if (hint->begin() > other_hint->begin()) {
      hint->move_hint_ = other_hint;
    } else if (other_hint != hint) {
      other_hint->move_hint_ = hint;
    }
  }

  int begin() const {
    DCHECK(!range_list_.empty());
    return range_list_.front().begin();
  }

  int LastLiveRangeBegin() const {
    DCHECK(!range_list_.empty());
    return range_list_.back().begin();
  }

  int end() const {
    DCHECK(!range_list_.empty());
    return range_list_.back().end();
  }

  void set_end(int end) {
    DCHECK(!range_list_.empty());
    range_list_.back().set_end(end);
  }

  // Multiline.
  std::string GetDebugString() const {
    std::string out("lifetime {\n");
    for (const auto& range : range_list_) {
      out += range.GetDebugString();
    }
    out += "}\n";
    return out;
  }

  const MachineRegClass* GetRegClass() const {
    DCHECK(reg_class_);
    return reg_class_;
  }

  // Return true if lifetimes interfere.
  bool TestInterference(const VRegLifetime& other) const {
    VRegLiveRangeList::const_iterator j = other.range_list_.begin();
    for (VRegLiveRangeList::const_iterator i = range_list_.begin();
         i != range_list_.end() && j != other.range_list_.end();) {
      if (i->end() <= j->begin()) {
        ++i;
      } else if (j->end() <= i->begin()) {
        ++j;
      } else {
        return true;
      }
    }
    return false;
  }

  // Consider splitting into tiny lifetimes after 'begin'.
  // Non-const, as we return non-const iterator?!
  SplitKind FindSplitPos(int begin, SplitPos* pos) {
    for (auto range_it = range_list_.begin(); range_it != range_list_.end(); ++range_it) {
      if (range_it->end() <= begin) {
        continue;
      }

      for (auto use_it = range_it->use_list().begin(); use_it != range_it->use_list().end();
           ++use_it) {
        if (use_it->end() <= begin) {
          // Future tiny lifetime ends before 'begin'.
          continue;
        }

        if (use_it->begin() < begin) {
          // Future tiny lifetime starts before but ends after 'begin'.
          // Problematic case we don't allow.
          return SPLIT_IMPOSSIBLE;
        }

        // Future tiny lifetime starts at or after 'begin'.
        pos->range_it = range_it;
        pos->use_it = use_it;
        return use_it->begin() == begin ? SPLIT_CONFLICT : SPLIT_OK;
      }
    }

    // If we got here, lifetime spans after begin but has no uses there.
    // It can happen with live-out virtual registers.
    pos->range_it = range_list_.end();
    return SPLIT_OK;
  }

  void Split(const SplitPos& split_pos, ArenaList<VRegLifetime>* out) {
    VRegLiveRangeList::iterator range_it = split_pos.range_it;
    if (range_it == range_list_.end()) {
      return;
    }

    // Create tiny lifetime from each use after split pos.
    VRegUseList::iterator use_it = split_pos.use_it;
    for (;;) {
      for (; use_it != range_it->use_list().end(); ++use_it) {
        out->push_back(VRegLifetime(arena_, *use_it));
        out->back().SetSpill(GetSpill());
      }
      if (++range_it == range_list_.end()) {
        break;
      }
      use_it = range_it->use_list().begin();
    }

    // Erase transferred uses (so they are not rewritten twice).
    VRegLiveRangeList::iterator first_range_to_erase = split_pos.range_it;
    if (split_pos.use_it != first_range_to_erase->use_list().begin()) {
      // Erase only tail of the first range.
      split_pos.range_it->use_list().erase(split_pos.use_it, split_pos.range_it->use_list().end());
      ++first_range_to_erase;
    }
    range_list_.erase(first_range_to_erase, range_list_.end());
  }

  // Walk reg uses and replace vreg with assigned hard reg.
  void Rewrite(MachineIR* machine_ir) {
    for (auto& range : range_list_) {
      for (auto& use : range.use_list()) {
        use.RewriteVReg(machine_ir, hard_reg_, spill_slot_);
      }
    }
  }

 private:
  // Arena for allocations.
  Arena* arena_;
  // List of live ranges, must be non-empty after lifetime is populated!
  VRegLiveRangeList range_list_;
  // Register class that fits all uses.
  const MachineRegClass* reg_class_;
  // Assigned hard register.
  MachineReg hard_reg_;
  // Where to spill previous value of assigned hard register.
  int spill_slot_;
  // Spill weight, roughly the number of spill/reload insns to add.
  int spill_weight_;
  // Lifetime that starts before and is connected by move with this one.
  VRegLifetime* move_hint_;
};

using VRegLifetimeList = VRegLifetime::List;

}  // namespace berberis

#endif  // BERBERIS_BACKEND_COMMON_LIFETIME_H_
