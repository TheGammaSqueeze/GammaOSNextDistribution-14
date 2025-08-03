/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "intern_table-inl.h"

#include <memory>

#include "dex/utf.h"
#include "gc/collector/garbage_collector.h"
#include "gc/space/image_space.h"
#include "gc/weak_root_state.h"
#include "gc_root-inl.h"
#include "handle_scope-inl.h"
#include "mirror/dex_cache-inl.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/string-inl.h"
#include "oat/image-inl.h"
#include "object_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "thread.h"
#include "thread-inl.h"

namespace art HIDDEN {

InternTable::InternTable()
    : log_new_roots_(false),
      weak_intern_condition_("New intern condition", *Locks::intern_table_lock_),
      weak_root_state_(gc::kWeakRootStateNormal) {
}

size_t InternTable::Size() const {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  return strong_interns_.Size() + weak_interns_.Size();
}

size_t InternTable::StrongSize() const {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  return strong_interns_.Size();
}

size_t InternTable::WeakSize() const {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  return weak_interns_.Size();
}

void InternTable::DumpForSigQuit(std::ostream& os) const {
  os << "Intern table: " << StrongSize() << " strong; " << WeakSize() << " weak\n";
}

void InternTable::VisitRoots(RootVisitor* visitor, VisitRootFlags flags) {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  if ((flags & kVisitRootFlagAllRoots) != 0) {
    strong_interns_.VisitRoots(visitor);
  } else if ((flags & kVisitRootFlagNewRoots) != 0) {
    for (auto& root : new_strong_intern_roots_) {
      ObjPtr<mirror::String> old_ref = root.Read<kWithoutReadBarrier>();
      root.VisitRoot(visitor, RootInfo(kRootInternedString));
      ObjPtr<mirror::String> new_ref = root.Read<kWithoutReadBarrier>();
      if (new_ref != old_ref) {
        // The GC moved a root in the log. Need to search the strong interns and update the
        // corresponding object. This is slow, but luckily for us, this may only happen with a
        // concurrent moving GC.
        DCHECK(new_ref != nullptr);
        uint32_t hash = static_cast<uint32_t>(old_ref->GetStoredHashCode());
        DCHECK_EQ(hash, static_cast<uint32_t>(new_ref->GetStoredHashCode()));
        DCHECK(new_ref->Equals(old_ref));
        bool found = false;
        for (Table::InternalTable& table : strong_interns_.tables_) {
          auto it = table.set_.FindWithHash(GcRoot<mirror::String>(old_ref), hash);
          if (it != table.set_.end()) {
            *it = GcRoot<mirror::String>(new_ref);
            found = true;
            break;
          }
        }
        DCHECK(found);
      }
    }
  }
  if ((flags & kVisitRootFlagClearRootLog) != 0) {
    new_strong_intern_roots_.clear();
  }
  if ((flags & kVisitRootFlagStartLoggingNewRoots) != 0) {
    log_new_roots_ = true;
  } else if ((flags & kVisitRootFlagStopLoggingNewRoots) != 0) {
    log_new_roots_ = false;
  }
  // Note: we deliberately don't visit the weak_interns_ table and the immutable image roots.
}

ObjPtr<mirror::String> InternTable::LookupWeak(Thread* self, ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  MutexLock mu(self, *Locks::intern_table_lock_);
  return weak_interns_.Find(s, hash);
}

ObjPtr<mirror::String> InternTable::LookupStrong(Thread* self, ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  MutexLock mu(self, *Locks::intern_table_lock_);
  return strong_interns_.Find(s, hash);
}

ObjPtr<mirror::String> InternTable::LookupStrong(Thread* self,
                                                 uint32_t utf16_length,
                                                 const char* utf8_data) {
  uint32_t hash = Utf8String::Hash(utf16_length, utf8_data);
  MutexLock mu(self, *Locks::intern_table_lock_);
  return strong_interns_.Find(Utf8String(utf16_length, utf8_data), hash);
}

ObjPtr<mirror::String> InternTable::LookupWeakLocked(ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  return weak_interns_.Find(s, hash);
}

ObjPtr<mirror::String> InternTable::LookupStrongLocked(ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  return strong_interns_.Find(s, hash);
}

void InternTable::AddNewTable() {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  weak_interns_.AddNewTable();
  strong_interns_.AddNewTable();
}

ObjPtr<mirror::String> InternTable::InsertStrong(ObjPtr<mirror::String> s, uint32_t hash) {
  Runtime* runtime = Runtime::Current();
  if (runtime->IsActiveTransaction()) {
    runtime->RecordStrongStringInsertion(s);
  }
  if (log_new_roots_) {
    new_strong_intern_roots_.push_back(GcRoot<mirror::String>(s));
  }
  strong_interns_.Insert(s, hash);
  return s;
}

ObjPtr<mirror::String> InternTable::InsertWeak(ObjPtr<mirror::String> s, uint32_t hash) {
  Runtime* runtime = Runtime::Current();
  if (runtime->IsActiveTransaction()) {
    runtime->RecordWeakStringInsertion(s);
  }
  weak_interns_.Insert(s, hash);
  return s;
}

void InternTable::RemoveStrong(ObjPtr<mirror::String> s, uint32_t hash) {
  strong_interns_.Remove(s, hash);
}

void InternTable::RemoveWeak(ObjPtr<mirror::String> s, uint32_t hash) {
  Runtime* runtime = Runtime::Current();
  if (runtime->IsActiveTransaction()) {
    runtime->RecordWeakStringRemoval(s);
  }
  weak_interns_.Remove(s, hash);
}

void InternTable::BroadcastForNewInterns() {
  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::intern_table_lock_);
  weak_intern_condition_.Broadcast(self);
}

void InternTable::WaitUntilAccessible(Thread* self) {
  Locks::intern_table_lock_->ExclusiveUnlock(self);
  {
    ScopedThreadSuspension sts(self, ThreadState::kWaitingWeakGcRootRead);
    MutexLock mu(self, *Locks::intern_table_lock_);
    while ((!gUseReadBarrier && weak_root_state_ == gc::kWeakRootStateNoReadsOrWrites) ||
           (gUseReadBarrier && !self->GetWeakRefAccessEnabled())) {
      weak_intern_condition_.Wait(self);
    }
  }
  Locks::intern_table_lock_->ExclusiveLock(self);
}

ObjPtr<mirror::String> InternTable::Insert(ObjPtr<mirror::String> s,
                                           uint32_t hash,
                                           bool is_strong,
                                           size_t num_searched_strong_frozen_tables) {
  DCHECK(s != nullptr);
  DCHECK_EQ(hash, static_cast<uint32_t>(s->GetStoredHashCode()));
  DCHECK_IMPLIES(hash == 0u, s->ComputeHashCode() == 0);
  Thread* const self = Thread::Current();
  MutexLock mu(self, *Locks::intern_table_lock_);
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(self);
    CHECK_EQ(2u, self->NumberOfHeldMutexes()) << "may only safely hold the mutator lock";
  }
  while (true) {
    // Check the strong table for a match.
    ObjPtr<mirror::String> strong =
        strong_interns_.Find(s, hash, num_searched_strong_frozen_tables);
    if (strong != nullptr) {
      return strong;
    }
    if (gUseReadBarrier ? self->GetWeakRefAccessEnabled()
                        : weak_root_state_ != gc::kWeakRootStateNoReadsOrWrites) {
      break;
    }
    num_searched_strong_frozen_tables = strong_interns_.tables_.size() - 1u;
    // weak_root_state_ is set to gc::kWeakRootStateNoReadsOrWrites in the GC pause but is only
    // cleared after SweepSystemWeaks has completed. This is why we need to wait until it is
    // cleared.
    StackHandleScope<1> hs(self);
    auto h = hs.NewHandleWrapper(&s);
    WaitUntilAccessible(self);
  }
  if (!gUseReadBarrier) {
    CHECK_EQ(weak_root_state_, gc::kWeakRootStateNormal);
  } else {
    CHECK(self->GetWeakRefAccessEnabled());
  }
  // There is no match in the strong table, check the weak table.
  ObjPtr<mirror::String> weak = weak_interns_.Find(s, hash);
  if (weak != nullptr) {
    if (is_strong) {
      // A match was found in the weak table. Promote to the strong table.
      RemoveWeak(weak, hash);
      return InsertStrong(weak, hash);
    }
    return weak;
  }
  // No match in the strong table or the weak table. Insert into the strong / weak table.
  return is_strong ? InsertStrong(s, hash) : InsertWeak(s, hash);
}

ObjPtr<mirror::String> InternTable::InternStrong(uint32_t utf16_length, const char* utf8_data) {
  DCHECK(utf8_data != nullptr);
  uint32_t hash = Utf8String::Hash(utf16_length, utf8_data);
  Thread* self = Thread::Current();
  ObjPtr<mirror::String> s;
  size_t num_searched_strong_frozen_tables;
  {
    // Try to avoid allocation. If we need to allocate, release the mutex before the allocation.
    MutexLock mu(self, *Locks::intern_table_lock_);
    DCHECK(!strong_interns_.tables_.empty());
    num_searched_strong_frozen_tables = strong_interns_.tables_.size() - 1u;
    s = strong_interns_.Find(Utf8String(utf16_length, utf8_data), hash);
  }
  if (s != nullptr) {
    return s;
  }
  bool is_ascii = (utf8_data[utf16_length] == 0);
  int32_t utf8_length = utf16_length + (LIKELY(is_ascii) ? 0 : strlen(utf8_data + utf16_length));
  DCHECK_EQ(static_cast<size_t>(utf8_length), strlen(utf8_data));
  s = mirror::String::AllocFromModifiedUtf8(self, utf16_length, utf8_data, utf8_length);
  if (UNLIKELY(s == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  s->SetHashCode(static_cast<int32_t>(hash));
  return Insert(s, hash, /*is_strong=*/ true, num_searched_strong_frozen_tables);
}

ObjPtr<mirror::String> InternTable::InternStrong(const char* utf8_data) {
  DCHECK(utf8_data != nullptr);
  Thread* self = Thread::Current();
  ObjPtr<mirror::String> s = mirror::String::AllocFromModifiedUtf8(self, utf8_data);
  if (UNLIKELY(s == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  return InternStrong(s);
}

ObjPtr<mirror::String> InternTable::InternStrong(ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  return Insert(s, hash, /*is_strong=*/ true);
}

ObjPtr<mirror::String> InternTable::InternWeak(const char* utf8_data) {
  DCHECK(utf8_data != nullptr);
  Thread* self = Thread::Current();
  ObjPtr<mirror::String> s = mirror::String::AllocFromModifiedUtf8(self, utf8_data);
  if (UNLIKELY(s == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  return InternWeak(s);
}

ObjPtr<mirror::String> InternTable::InternWeak(ObjPtr<mirror::String> s) {
  DCHECK(s != nullptr);
  // `String::GetHashCode()` ensures that the stored hash is calculated.
  uint32_t hash = static_cast<uint32_t>(s->GetHashCode());
  return Insert(s, hash, /*is_strong=*/ false);
}

void InternTable::SweepInternTableWeaks(IsMarkedVisitor* visitor) {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  weak_interns_.SweepWeaks(visitor);
}

void InternTable::Table::Remove(ObjPtr<mirror::String> s, uint32_t hash) {
  // Note: We can remove weak interns even from frozen tables when promoting to strong interns.
  // We can remove strong interns only for a transaction rollback.
  for (InternalTable& table : tables_) {
    auto it = table.set_.FindWithHash(GcRoot<mirror::String>(s), hash);
    if (it != table.set_.end()) {
      table.set_.erase(it);
      return;
    }
  }
  LOG(FATAL) << "Attempting to remove non-interned string " << s->ToModifiedUtf8();
}

FLATTEN
ObjPtr<mirror::String> InternTable::Table::Find(ObjPtr<mirror::String> s,
                                                uint32_t hash,
                                                size_t num_searched_frozen_tables) {
  Locks::intern_table_lock_->AssertHeld(Thread::Current());
  auto mid = tables_.begin() + num_searched_frozen_tables;
  for (Table::InternalTable& table : MakeIterationRange(tables_.begin(), mid)) {
    DCHECK(table.set_.FindWithHash(GcRoot<mirror::String>(s), hash) == table.set_.end());
  }
  // Search from the last table, assuming that apps shall search for their own
  // strings more often than for boot image strings.
  for (Table::InternalTable& table : ReverseRange(MakeIterationRange(mid, tables_.end()))) {
    auto it = table.set_.FindWithHash(GcRoot<mirror::String>(s), hash);
    if (it != table.set_.end()) {
      return it->Read();
    }
  }
  return nullptr;
}

FLATTEN
ObjPtr<mirror::String> InternTable::Table::Find(const Utf8String& string, uint32_t hash) {
  Locks::intern_table_lock_->AssertHeld(Thread::Current());
  // Search from the last table, assuming that apps shall search for their own
  // strings more often than for boot image strings.
  for (InternalTable& table : ReverseRange(tables_)) {
    auto it = table.set_.FindWithHash(string, hash);
    if (it != table.set_.end()) {
      return it->Read();
    }
  }
  return nullptr;
}

void InternTable::Table::AddNewTable() {
  // Propagate the min/max load factor from the old active set.
  DCHECK(!tables_.empty());
  const UnorderedSet& last_set = tables_.back().set_;
  InternalTable new_table;
  new_table.set_.SetLoadFactor(last_set.GetMinLoadFactor(), last_set.GetMaxLoadFactor());
  tables_.push_back(std::move(new_table));
}

void InternTable::Table::Insert(ObjPtr<mirror::String> s, uint32_t hash) {
  // Always insert the last table, the image tables are before and we avoid inserting into these
  // to prevent dirty pages.
  DCHECK(!tables_.empty());
  tables_.back().set_.PutWithHash(GcRoot<mirror::String>(s), hash);
}

void InternTable::Table::VisitRoots(RootVisitor* visitor) {
  BufferedRootVisitor<kDefaultBufferedRootCount> buffered_visitor(
      visitor, RootInfo(kRootInternedString));
  for (InternalTable& table : tables_) {
    for (auto& intern : table.set_) {
      buffered_visitor.VisitRoot(intern);
    }
  }
}

void InternTable::Table::SweepWeaks(IsMarkedVisitor* visitor) {
  for (InternalTable& table : tables_) {
    SweepWeaks(&table.set_, visitor);
  }
}

void InternTable::Table::SweepWeaks(UnorderedSet* set, IsMarkedVisitor* visitor) {
  for (auto it = set->begin(), end = set->end(); it != end;) {
    // This does not need a read barrier because this is called by GC.
    mirror::Object* object = it->Read<kWithoutReadBarrier>();
    mirror::Object* new_object = visitor->IsMarked(object);
    if (new_object == nullptr) {
      it = set->erase(it);
    } else {
      // Don't use AsString as it does IsString check in debug builds which, in
      // case of userfaultfd GC, is called when the object's content isn't
      // thereyet.
      *it = GcRoot<mirror::String>(ObjPtr<mirror::String>::DownCast(new_object));
      ++it;
    }
  }
}

size_t InternTable::Table::Size() const {
  return std::accumulate(tables_.begin(),
                         tables_.end(),
                         0U,
                         [](size_t sum, const InternalTable& table) {
                           return sum + table.Size();
                         });
}

void InternTable::ChangeWeakRootState(gc::WeakRootState new_state) {
  MutexLock mu(Thread::Current(), *Locks::intern_table_lock_);
  ChangeWeakRootStateLocked(new_state);
}

void InternTable::ChangeWeakRootStateLocked(gc::WeakRootState new_state) {
  CHECK(!gUseReadBarrier);
  weak_root_state_ = new_state;
  if (new_state != gc::kWeakRootStateNoReadsOrWrites) {
    weak_intern_condition_.Broadcast(Thread::Current());
  }
}

InternTable::Table::Table() {
  Runtime* const runtime = Runtime::Current();
  InternalTable initial_table;
  initial_table.set_.SetLoadFactor(runtime->GetHashTableMinLoadFactor(),
                                   runtime->GetHashTableMaxLoadFactor());
  tables_.push_back(std::move(initial_table));
}

}  // namespace art
