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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_TRANSLATION_CACHE_H_
#define BERBERIS_RUNTIME_PRIMITIVES_TRANSLATION_CACHE_H_

#include <atomic>
#include <cstdint>
#include <mutex>

#include "berberis/base/forever_map.h"
#include "berberis/base/forever_set.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/runtime_primitives/table_of_tables.h"

namespace berberis {

// Translated code entry.
// ATTENTION: associated guest pc and host code pointer never change!
// TODO(b/232598137): consider making TranslationCache-internal!
struct GuestCodeEntry {
  std::atomic<HostCode>* const host_code;

  // Fields below are protected by TranslationCache mutex.

  uint32_t host_size;

  // Must be greater than zero even for special entries, such as wrapped or
  // translation-in-progress.
  uint32_t guest_size;

  enum class Kind {
    kInterpreted,
    kLightTranslated,
    kHeavyOptimized,
    kGuestWrapped,
    kHostWrapped,
    // E.g. translating, wrapping, invalidating.
    kUnderProcessing,
    // E.g. non-executable, unpredictable.
    kSpecialHandler,
  };

  Kind kind;

  // The number of times this entry has been invoked.
  uint32_t invocation_counter;
};

// Cache of translated code regions:
// - Thread-safe: coordinates translation across threads.
// - Provides host code to execute for a given guest pc.
//   This is lock- and wait-free.
// - Tracks translated regions and invalidates them when corresponding guest code is updated.
//   This is protected by mutex.
//
// Each guest code cache entry is not necessarily translated code. Each entry is a _state machine_,
// where only one state actually contains translated guest code. However, each entry always contains
// a pointer to code that should be executed; such code will either be translated code, or an
// berberis function e.g. to call the interpreter or to call a 'trampoline' function which
// calls a host function, or a no-op function for when the entry is to be invalidated, etc.
//
// The possible guest entry states are:
// - Not Translated. Execution should interpret or translate.
//   Nothing to track.
// - Translating. 'Locked' by a translating thread. Execution should interpret or wait for
//   translation to complete.
//   At this point the guest range for a region is still unknown, since we don't know how much of
//   the guest code at the start address can be translated to a continuous region until it is
//   translated.
//   If ANY guest code gets updated, translation should be abandoned (invalidated). Because we don't
//   know the size of the region before translation, any updated code could overlap with a region
//   that is being translated, and thereby invalidates the translation. (Also, in the future
//   translated regions may not be simple linear blocks.)
// - Invalidating. Execution should interpret or wait.
//   'Locked' by a translating thread, which should abandon the translation.
//   Nothing to track.
// - Translated. Execution should run generated code.
//   Guest range is now known. If guest code that overlaps the region gets updated, the entry should
//   be invalidated.
//
// There are more entries that do not correspond to real guest code:
// - wrapping. Execution should wait.
//   'locked' by a wrapper generating thread.
//   Nothing to track.
// - wrapped. Execution should run generated code.
//   Nothing to track.
//
class TranslationCache {
 public:
  TranslationCache() = default;
  TranslationCache(const TranslationCache&) = delete;
  TranslationCache& operator=(const TranslationCache&) = delete;

  static TranslationCache* GetInstance();

  bool SetStop(GuestAddr pc) {
    auto expected = kEntryNotTranslated;  // expect default value.
    auto host_code_ptr = GetHostCodePtr(pc);
    if (host_code_ptr->compare_exchange_strong(expected, kEntryStop)) {
      return true;
    }
    return expected == kEntryStop;
  }

  void TestingClearStop(GuestAddr pc) {
    GetHostCodePtr(pc)->store(kEntryNotTranslated);  // set default value.
  }

  // Inserts NotTranslated entry for the given PC if not inserted already. Then transitions from
  // NotTranslated state to Translating, or returns nullptr if the entry has not yet been
  // interpreted at least counter_threshold times, in which case the entry's interpretation counter
  // will be incremented. Also returns nullptr if the state was not Translating, or if somehow there
  // is no entry for the given PC.
  GuestCodeEntry* AddAndLockForTranslation(GuestAddr pc, uint32_t counter_threshold);

  // Locks entry for the given PC for translation if it's currently in LightTranslated state.
  // If successful returns the locked entry, otherwise returns nullptr.
  GuestCodeEntry* LockForGearUpTranslation(GuestAddr pc);

  // Transitions the entry for the given guest address from Translating to Translated, making it
  // available to other threads.
  void SetTranslatedAndUnlock(GuestAddr pc,
                              GuestCodeEntry* entry,
                              uint32_t guest_size,
                              GuestCodeEntry::Kind kind,
                              HostCodePiece code);

  // ATTENTION: interpreter doesn't handle code that is being wrapped!
  GuestCodeEntry* AddAndLockForWrapping(GuestAddr pc);

  void SetWrappedAndUnlock(GuestAddr pc,
                           GuestCodeEntry* entry,
                           bool is_host_func,
                           HostCodePiece code);

  bool IsHostFunctionWrapped(GuestAddr pc);

  // TODO(b/232598137): flawed, used only in profiler - replace or remove!
  GuestCodeEntry* ProfilerLookupGuestCodeEntryByGuestPC(GuestAddr pc);

  [[nodiscard]] uint32_t GetInvocationCounter(GuestAddr pc) const;

  // Find guest entry pc by a host pc.
  // WARNING: SUPER SLOW! Use it with caution.
  GuestAddr SlowLookupGuestCodeEntryPCByHostPC(HostCode pc);

  // Invalidate region of entries.
  void InvalidateGuestRange(GuestAddr start, GuestAddr end);

  const std::atomic<std::atomic<HostCode>*>* main_table_ptr() const {
    return address_map_.main_table();
  }

  std::atomic<HostCode>* GetHostCodePtr(GuestAddr pc) { return address_map_.GetPointer(pc); }

  void PreZygoteForkUnsafe() {
    // Zygote's fork doesn't allow unrecognized open file descriptors, so we close them.
    address_map_.CloseDefaultMemfdUnsafe();
  }

  GuestCodeEntry* LookupGuestCodeEntryUnsafeForTesting(GuestAddr pc) {
    return LookupGuestCodeEntryUnsafe(pc);
  }

 private:
  [[nodiscard]] GuestCodeEntry* LookupGuestCodeEntryUnsafe(GuestAddr pc);
  [[nodiscard]] const GuestCodeEntry* LookupGuestCodeEntryUnsafe(GuestAddr pc) const;

  // Add call record for an address, reuse if already here.
  GuestCodeEntry* AddUnsafe(GuestAddr pc,
                            std::atomic<HostCode>* host_code_ptr,
                            HostCodePiece host_code_piece,
                            uint32_t guest_size,
                            GuestCodeEntry::Kind kind,
                            bool* added);

  void LockForTranslationUnsafe(GuestCodeEntry* entry);

  void InvalidateEntriesBeingTranslatedUnsafe();

  // ATTENTION: all GuestCodeEntry state transitions must be protected by mutex!
  mutable std::mutex mutex_;

  // Stores guest entries that are in Translating state. These will also be in guest_entries_.
  ForeverSet<GuestCodeEntry*> translating_;

  // Guest code entries for all guest PCs ever looked up.
  ForeverMap<GuestAddr, GuestCodeEntry> guest_entries_;

  // Maps guest code addresses to the host address of the translated code.
  TableOfTables<GuestAddr, HostCode> address_map_{kEntryNotTranslated};

  // The size of the largest entry.
  // Wrapped entries do not update it, so if we only have wrapped the size
  // should be 1 at least. This is practically only important for tests.
  size_t max_guest_size_{1};
};

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_TRANSLATION_CACHE_H_
