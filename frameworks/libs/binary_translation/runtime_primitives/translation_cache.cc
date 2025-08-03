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

#include "berberis/runtime_primitives/translation_cache.h"

#include <atomic>
#include <map>
#include <mutex>  // std::lock_guard, std::mutex

#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/runtime_library.h"

namespace berberis {

TranslationCache* TranslationCache::GetInstance() {
  static TranslationCache g_translation_cache;
  return &g_translation_cache;
}

GuestCodeEntry* TranslationCache::AddAndLockForTranslation(GuestAddr pc,
                                                           uint32_t counter_threshold) {
  // Make sure host code is updated under the mutex, so that it's in sync with
  // the set of the translating regions (e.g as invalidation observes it).
  std::lock_guard<std::mutex> lock(mutex_);

  auto host_code_ptr = GetHostCodePtr(pc);
  bool added;
  auto entry = AddUnsafe(pc,
                         host_code_ptr,
                         {kEntryNotTranslated, 0},  // TODO(b/232598137): set true host_size?
                         1,                         // Non-zero size simplifies invalidation.
                         GuestCodeEntry::Kind::kInterpreted,
                         &added);
  CHECK(entry);

  // Must not be translated yet.
  if (entry->host_code->load() != kEntryNotTranslated) {
    return nullptr;
  }

  // Check the threshold.
  if (entry->invocation_counter < counter_threshold) {
    ++entry->invocation_counter;
    return nullptr;
  }

  LockForTranslationUnsafe(entry);
  return entry;
}

GuestCodeEntry* TranslationCache::LockForGearUpTranslation(GuestAddr pc) {
  std::lock_guard<std::mutex> lock(mutex_);

  auto* entry = LookupGuestCodeEntryUnsafe(pc);
  if (!entry) {
    // Entry could have been invalidated and erased.
    return nullptr;
  }

  // This method should be called for light-translated region, but we cannot
  // guarantee they stay as such before we lock the mutex.
  if (entry->kind != GuestCodeEntry::Kind::kLightTranslated) {
    return nullptr;
  }

  LockForTranslationUnsafe(entry);
  return entry;
}

void TranslationCache::LockForTranslationUnsafe(GuestCodeEntry* entry) {
  entry->host_code->store(kEntryTranslating);
  entry->kind = GuestCodeEntry::Kind::kUnderProcessing;

  bool inserted = translating_.insert(entry).second;
  CHECK(inserted);
}

void TranslationCache::SetTranslatedAndUnlock(GuestAddr pc,
                                              GuestCodeEntry* entry,
                                              uint32_t guest_size,
                                              GuestCodeEntry::Kind kind,
                                              HostCodePiece code) {
  CHECK(kind != GuestCodeEntry::Kind::kUnderProcessing);
  CHECK(kind != GuestCodeEntry::Kind::kGuestWrapped);
  CHECK(kind != GuestCodeEntry::Kind::kHostWrapped);
  // Make sure host code is updated under the mutex, so that it's in sync with
  // the set of the translating regions (e.g as invalidation observes it).
  std::lock_guard<std::mutex> lock(mutex_);

  auto current = entry->host_code->load();

  // Might have been invalidated while translating.
  if (current == kEntryInvalidating) {
    // ATTENTION: all transitions from kEntryInvalidating are protected by mutex!
    entry->host_code->store(kEntryNotTranslated);
    guest_entries_.erase(pc);
    return;
  }

  // Must be translating
  CHECK_EQ(current, kEntryTranslating);
  CHECK(entry->kind == GuestCodeEntry::Kind::kUnderProcessing);

  // ATTENTION: all transitions from kEntryTranslating are protected by mutex!
  entry->host_code->store(code.code);

  CHECK_GT(guest_size, 0);
  entry->host_size = code.size;
  entry->guest_size = guest_size;
  entry->kind = kind;

  size_t num_erased = translating_.erase(entry);
  CHECK_EQ(num_erased, 1);

  if (max_guest_size_ < guest_size) {
    max_guest_size_ = guest_size;
  }
}

GuestCodeEntry* TranslationCache::AddAndLockForWrapping(GuestAddr pc) {
  // This should be relatively rare, don't need a fast pass.
  std::lock_guard<std::mutex> lock(mutex_);

  // ATTENTION: kEntryWrapping is a locked state, can return the entry.
  bool locked;
  auto entry = AddUnsafe(pc,
                         GetHostCodePtr(pc),
                         {kEntryWrapping, 0},  // TODO(b/232598137): set true host_size?
                         1,                    // Non-zero size simplifies invalidation.
                         GuestCodeEntry::Kind::kUnderProcessing,
                         &locked);
  return locked ? entry : nullptr;
}

void TranslationCache::SetWrappedAndUnlock(GuestAddr pc,
                                           GuestCodeEntry* entry,
                                           bool is_host_func,
                                           HostCodePiece code) {
  std::lock_guard<std::mutex> lock(mutex_);

  auto current = entry->host_code->load();

  // Might have been invalidated while wrapping.
  if (current == kEntryInvalidating) {
    // ATTENTION: all transitions from kEntryInvalidating are protected by mutex!
    entry->host_code->store(kEntryNotTranslated);
    guest_entries_.erase(pc);
    return;
  }

  // Must be wrapping.
  CHECK_EQ(current, kEntryWrapping);
  CHECK(entry->kind == GuestCodeEntry::Kind::kUnderProcessing);

  // ATTENTION: all transitions from kEntryWrapping are protected by mutex!
  entry->host_code->store(code.code);

  entry->host_size = code.size;
  entry->kind =
      is_host_func ? GuestCodeEntry::Kind::kHostWrapped : GuestCodeEntry::Kind::kGuestWrapped;
  // entry->guest_size remains from 'wrapping'.
  CHECK_EQ(entry->guest_size, 1);
}

bool TranslationCache::IsHostFunctionWrapped(GuestAddr pc) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (auto entry = LookupGuestCodeEntryUnsafe(pc)) {
    return entry->kind == GuestCodeEntry::Kind::kHostWrapped;
  }
  return false;
}

GuestCodeEntry* TranslationCache::AddUnsafe(GuestAddr pc,
                                            std::atomic<HostCode>* host_code_ptr,
                                            HostCodePiece host_code_piece,
                                            uint32_t guest_size,
                                            GuestCodeEntry::Kind kind,
                                            bool* added) {
  auto [it, result] = guest_entries_.emplace(
      std::pair{pc, GuestCodeEntry{host_code_ptr, host_code_piece.size, guest_size, kind, 0}});

  if (result) {
    host_code_ptr->store(host_code_piece.code);
  }

  *added = result;
  return &it->second;
}

GuestCodeEntry* TranslationCache::ProfilerLookupGuestCodeEntryByGuestPC(GuestAddr pc) {
  std::lock_guard<std::mutex> lock(mutex_);
  return LookupGuestCodeEntryUnsafe(pc);
}

uint32_t TranslationCache::GetInvocationCounter(GuestAddr pc) const {
  std::lock_guard<std::mutex> lock(mutex_);
  auto* entry = LookupGuestCodeEntryUnsafe(pc);
  if (entry == nullptr) {
    return 0;
  }
  return entry->invocation_counter;
}

GuestCodeEntry* TranslationCache::LookupGuestCodeEntryUnsafe(GuestAddr pc) {
  auto it = guest_entries_.find(pc);
  if (it != end(guest_entries_)) {
    return &it->second;
  }

  return nullptr;
}

const GuestCodeEntry* TranslationCache::LookupGuestCodeEntryUnsafe(GuestAddr pc) const {
  return const_cast<TranslationCache*>(this)->LookupGuestCodeEntryUnsafe(pc);
}

GuestAddr TranslationCache::SlowLookupGuestCodeEntryPCByHostPC(HostCode pc) {
  std::lock_guard<std::mutex> lock(mutex_);

  for (auto& it : guest_entries_) {
    auto* entry = &it.second;
    auto host_code = entry->host_code->load();
    if (host_code <= pc &&
        pc < AsHostCode(reinterpret_cast<uintptr_t>(host_code) + entry->host_size)) {
      return it.first;
    }
  }
  return 0;
}

void TranslationCache::InvalidateEntriesBeingTranslatedUnsafe() {
  for (GuestCodeEntry* entry : translating_) {
    CHECK(entry->kind == GuestCodeEntry::Kind::kUnderProcessing);
    CHECK_EQ(entry->host_code->load(), kEntryTranslating);
    entry->host_code->store(kEntryInvalidating);
    entry->host_size = 0;  // TODO(b/232598137): set true host_size?
    // entry->guest_size and entry->kind remain from 'translating'.
    // The entry will be erased on SetTranslatedAndUnlock.
  }
  translating_.clear();
}

void TranslationCache::InvalidateGuestRange(GuestAddr start, GuestAddr end) {
  std::lock_guard<std::mutex> lock(mutex_);

  // Also invalidate all entries being translated, since they may possibly overlap with the
  // start/end invalidation range. Technically, in the current implementation where we only
  // translate regions that are a linear range of addresses, we would not need to invalidate the
  // Translating entries that come after the end of the region being invalidated. But whether this
  // would be beneficial is unclear and unlikely, and furthermore we may change the "linear" aspect
  // later e.g. to follow static jumps.
  InvalidateEntriesBeingTranslatedUnsafe();

  std::map<GuestAddr, GuestCodeEntry>::iterator first;
  if (start <= max_guest_size_) {
    first = guest_entries_.begin();
  } else {
    first = guest_entries_.upper_bound(start - max_guest_size_);
  }

  while (first != guest_entries_.end()) {
    auto curr = first++;
    auto guest_pc = curr->first;
    GuestCodeEntry* entry = &curr->second;

    CHECK_GT(entry->guest_size, 0);
    if (guest_pc + entry->guest_size <= start) {
      continue;
    }
    if (guest_pc >= end) {
      break;
    }

    HostCode current = entry->host_code->load();

    if (current == kEntryInvalidating) {
      // Translating but invalidated entry is handled in SetTranslatedAndUnlock.
    } else if (current == kEntryWrapping) {
      // Wrapping entry range is known in advance, so we don't have it in translating_.
      entry->host_code->store(kEntryInvalidating);
      // Wrapping but invalidated entry is handled in SetWrappedAndUnlock.
    } else {
      entry->host_code->store(kEntryNotTranslated);
      guest_entries_.erase(curr);
    }
  }
}

}  // namespace berberis
