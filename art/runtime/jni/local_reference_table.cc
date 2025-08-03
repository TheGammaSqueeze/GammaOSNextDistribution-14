/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "local_reference_table-inl.h"

#include "base/bit_utils.h"
#include "base/casts.h"
#include "base/globals.h"
#include "base/mutator_locked_dumpable.h"
#include "base/systrace.h"
#include "base/utils.h"
#include "indirect_reference_table.h"
#include "jni/java_vm_ext.h"
#include "jni/jni_internal.h"
#include "mirror/object-inl.h"
#include "nth_caller_visitor.h"
#include "reference_table.h"
#include "runtime-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "thread.h"

#include <cstdlib>

namespace art HIDDEN {
namespace jni {

static constexpr bool kDumpStackOnNonLocalReference = false;

// Mmap an "indirect ref table region. Table_bytes is a multiple of a page size.
static inline MemMap NewLRTMap(size_t table_bytes, std::string* error_msg) {
  return MemMap::MapAnonymous("local ref table",
                              table_bytes,
                              PROT_READ | PROT_WRITE,
                              /*low_4gb=*/ false,
                              error_msg);
}

SmallLrtAllocator::SmallLrtAllocator()
    : free_lists_(num_lrt_slots_, nullptr),
      shared_lrt_maps_(),
      lock_("Small LRT allocator lock", LockLevel::kGenericBottomLock) {
}

inline size_t SmallLrtAllocator::GetIndex(size_t size) {
  DCHECK_GE(size, kSmallLrtEntries);
  DCHECK_LT(size, gPageSize / sizeof(LrtEntry));
  DCHECK(IsPowerOfTwo(size));
  size_t index = WhichPowerOf2(size / kSmallLrtEntries);
  DCHECK_LT(index, num_lrt_slots_);
  return index;
}

LrtEntry* SmallLrtAllocator::Allocate(size_t size, std::string* error_msg) {
  size_t index = GetIndex(size);
  MutexLock lock(Thread::Current(), lock_);
  size_t fill_from = index;
  while (fill_from != num_lrt_slots_ && free_lists_[fill_from] == nullptr) {
    ++fill_from;
  }
  void* result = nullptr;
  if (fill_from != num_lrt_slots_) {
    // We found a slot with enough memory.
    result = free_lists_[fill_from];
    free_lists_[fill_from] = *reinterpret_cast<void**>(result);
  } else {
    // We need to allocate a new page and split it into smaller pieces.
    MemMap map = NewLRTMap(gPageSize, error_msg);
    if (!map.IsValid()) {
      return nullptr;
    }
    result = map.Begin();
    shared_lrt_maps_.emplace_back(std::move(map));
  }
  while (fill_from != index) {
    --fill_from;
    // Store the second half of the current buffer in appropriate free list slot.
    void* mid = reinterpret_cast<uint8_t*>(result) + (kInitialLrtBytes << fill_from);
    DCHECK(free_lists_[fill_from] == nullptr);
    *reinterpret_cast<void**>(mid) = nullptr;
    free_lists_[fill_from] = mid;
  }
  // Clear the memory we return to the caller.
  std::memset(result, 0, kInitialLrtBytes << index);
  return reinterpret_cast<LrtEntry*>(result);
}

void SmallLrtAllocator::Deallocate(LrtEntry* unneeded, size_t size) {
  size_t index = GetIndex(size);
  MutexLock lock(Thread::Current(), lock_);
  while (index < num_lrt_slots_) {
    // Check if we can merge this free block with another block with the same size.
    void** other = reinterpret_cast<void**>(
        reinterpret_cast<uintptr_t>(unneeded) ^ (kInitialLrtBytes << index));
    void** before = &free_lists_[index];
    if (index + 1u == num_lrt_slots_ && *before == other && *other == nullptr) {
      // Do not unmap the page if we do not have other free blocks with index `num_lrt_slots_ - 1`.
      // (Keep at least one free block to avoid a situation where creating and destroying a single
      // thread with no local references would map and unmap a page in the `SmallLrtAllocator`.)
      break;
    }
    while (*before != nullptr && *before != other) {
      before = reinterpret_cast<void**>(*before);
    }
    if (*before == nullptr) {
      break;
    }
    // Remove `other` from the free list and merge it with the `unneeded` block.
    DCHECK(*before == other);
    *before = *reinterpret_cast<void**>(other);
    ++index;
    unneeded = reinterpret_cast<LrtEntry*>(
        reinterpret_cast<uintptr_t>(unneeded) & reinterpret_cast<uintptr_t>(other));
  }
  if (index == num_lrt_slots_) {
    // Free the entire page.
    DCHECK(free_lists_[num_lrt_slots_ - 1u] != nullptr);
    auto match = [=](MemMap& map) { return unneeded == reinterpret_cast<LrtEntry*>(map.Begin()); };
    auto it = std::find_if(shared_lrt_maps_.begin(), shared_lrt_maps_.end(), match);
    DCHECK(it != shared_lrt_maps_.end());
    shared_lrt_maps_.erase(it);
    DCHECK(!shared_lrt_maps_.empty());
    return;
  }
  *reinterpret_cast<void**>(unneeded) = free_lists_[index];
  free_lists_[index] = unneeded;
}

LocalReferenceTable::LocalReferenceTable(bool check_jni)
    : previous_state_(kLRTFirstSegment),
      segment_state_(kLRTFirstSegment),
      max_entries_(0u),
      free_entries_list_(
          FirstFreeField::Update(kFreeListEnd, check_jni ? 1u << kFlagCheckJni : 0u)),
      small_table_(nullptr),
      tables_(),
      table_mem_maps_() {
}

void LocalReferenceTable::SetCheckJniEnabled(bool enabled) {
  free_entries_list_ =
      (free_entries_list_ & ~(1u << kFlagCheckJni)) | (enabled ? 1u << kFlagCheckJni : 0u);
}

bool LocalReferenceTable::Initialize(size_t max_count, std::string* error_msg) {
  CHECK(error_msg != nullptr);

  // Overflow and maximum check.
  CHECK_LE(max_count, kMaxTableSizeInBytes / sizeof(LrtEntry));
  if (IsCheckJniEnabled()) {
    CHECK_LE(max_count, kMaxTableSizeInBytes / sizeof(LrtEntry) / kCheckJniEntriesPerReference);
    max_count *= kCheckJniEntriesPerReference;
  }

  SmallLrtAllocator* small_lrt_allocator = Runtime::Current()->GetSmallLrtAllocator();
  LrtEntry* first_table = small_lrt_allocator->Allocate(kSmallLrtEntries, error_msg);
  if (first_table == nullptr) {
    DCHECK(!error_msg->empty());
    return false;
  }
  DCHECK_ALIGNED(first_table, kCheckJniEntriesPerReference * sizeof(LrtEntry));
  small_table_ = first_table;
  max_entries_ = kSmallLrtEntries;
  return (max_count <= kSmallLrtEntries) || Resize(max_count, error_msg);
}

LocalReferenceTable::~LocalReferenceTable() {
  SmallLrtAllocator* small_lrt_allocator =
      max_entries_ != 0u ? Runtime::Current()->GetSmallLrtAllocator() : nullptr;
  if (small_table_ != nullptr) {
    small_lrt_allocator->Deallocate(small_table_, kSmallLrtEntries);
    DCHECK(tables_.empty());
  } else {
    size_t num_small_tables = std::min(tables_.size(), MaxSmallTables());
    for (size_t i = 0; i != num_small_tables; ++i) {
      small_lrt_allocator->Deallocate(tables_[i], GetTableSize(i));
    }
  }
}

bool LocalReferenceTable::Resize(size_t new_size, std::string* error_msg) {
  DCHECK_GE(max_entries_, kSmallLrtEntries);
  DCHECK(IsPowerOfTwo(max_entries_));
  DCHECK_GT(new_size, max_entries_);
  DCHECK_LE(new_size, kMaxTableSizeInBytes / sizeof(LrtEntry));
  size_t required_size = RoundUpToPowerOfTwo(new_size);
  size_t num_required_tables = NumTablesForSize(required_size);
  DCHECK_GE(num_required_tables, 2u);
  // Delay moving the `small_table_` to `tables_` until after the next table allocation succeeds.
  size_t num_tables = (small_table_ != nullptr) ? 1u : tables_.size();
  DCHECK_EQ(num_tables, NumTablesForSize(max_entries_));
  for (; num_tables != num_required_tables; ++num_tables) {
    size_t new_table_size = GetTableSize(num_tables);
    if (num_tables < MaxSmallTables()) {
      SmallLrtAllocator* small_lrt_allocator = Runtime::Current()->GetSmallLrtAllocator();
      LrtEntry* new_table = small_lrt_allocator->Allocate(new_table_size, error_msg);
      if (new_table == nullptr) {
        DCHECK(!error_msg->empty());
        return false;
      }
      DCHECK_ALIGNED(new_table, kCheckJniEntriesPerReference * sizeof(LrtEntry));
      tables_.push_back(new_table);
    } else {
      MemMap new_map = NewLRTMap(new_table_size * sizeof(LrtEntry), error_msg);
      if (!new_map.IsValid()) {
        DCHECK(!error_msg->empty());
        return false;
      }
      DCHECK_ALIGNED(new_map.Begin(), kCheckJniEntriesPerReference * sizeof(LrtEntry));
      tables_.push_back(reinterpret_cast<LrtEntry*>(new_map.Begin()));
      table_mem_maps_.push_back(std::move(new_map));
    }
    DCHECK_EQ(num_tables == 1u, small_table_ != nullptr);
    if (num_tables == 1u) {
      tables_.insert(tables_.begin(), small_table_);
      small_table_ = nullptr;
    }
    // Record the new available capacity after each successful allocation.
    DCHECK_EQ(max_entries_, new_table_size);
    max_entries_ = 2u * new_table_size;
  }
  DCHECK_EQ(num_required_tables, tables_.size());
  return true;
}

template <typename EntryGetter>
inline void LocalReferenceTable::PrunePoppedFreeEntries(EntryGetter&& get_entry) {
  const uint32_t top_index = segment_state_.top_index;
  uint32_t free_entries_list = free_entries_list_;
  uint32_t free_entry_index = FirstFreeField::Decode(free_entries_list);
  DCHECK_NE(free_entry_index, kFreeListEnd);
  DCHECK_GE(free_entry_index, top_index);
  do {
    free_entry_index = get_entry(free_entry_index)->GetNextFree();
  } while (free_entry_index != kFreeListEnd && free_entry_index >= top_index);
  free_entries_list_ = FirstFreeField::Update(free_entry_index, free_entries_list);
}

inline uint32_t LocalReferenceTable::IncrementSerialNumber(LrtEntry* serial_number_entry) {
  DCHECK_EQ(serial_number_entry, GetCheckJniSerialNumberEntry(serial_number_entry));
  // The old serial number can be 0 if it was not used before. It can also be bits from the
  // representation of an object reference, or a link to the next free entry written in this
  // slot before enabling the CheckJNI. (Some gtests repeatedly enable and disable CheckJNI.)
  uint32_t old_serial_number =
      serial_number_entry->GetSerialNumberUnchecked() % kCheckJniEntriesPerReference;
  uint32_t new_serial_number =
      (old_serial_number + 1u) != kCheckJniEntriesPerReference ? old_serial_number + 1u : 1u;
  DCHECK(IsValidSerialNumber(new_serial_number));
  serial_number_entry->SetSerialNumber(new_serial_number);
  return new_serial_number;
}

IndirectRef LocalReferenceTable::Add(ObjPtr<mirror::Object> obj, std::string* error_msg) {
  if (kDebugLRT) {
    LOG(INFO) << "+++ Add: previous_state=" << previous_state_.top_index
              << " top_index=" << segment_state_.top_index;
  }

  DCHECK(obj != nullptr);
  VerifyObject(obj);

  DCHECK_LE(previous_state_.top_index, segment_state_.top_index);
  DCHECK(max_entries_ == kSmallLrtEntries ? small_table_ != nullptr : !tables_.empty());

  auto store_obj = [obj, this](LrtEntry* free_entry, const char* tag)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    free_entry->SetReference(obj);
    IndirectRef result = ToIndirectRef(free_entry);
    if (kDebugLRT) {
      LOG(INFO) << "+++ " << tag << ": added at index " << GetReferenceEntryIndex(result)
                << ", top=" << segment_state_.top_index;
    }
    return result;
  };

  // Fast-path for small table with CheckJNI disabled.
  uint32_t top_index = segment_state_.top_index;
  LrtEntry* const small_table = small_table_;
  if (LIKELY(small_table != nullptr)) {
    DCHECK_EQ(max_entries_, kSmallLrtEntries);
    DCHECK_LE(segment_state_.top_index, kSmallLrtEntries);
    auto get_entry = [small_table](uint32_t index) ALWAYS_INLINE {
      DCHECK_LT(index, kSmallLrtEntries);
      return &small_table[index];
    };
    if (LIKELY(free_entries_list_ == kEmptyFreeListAndCheckJniDisabled)) {
      if (LIKELY(top_index != kSmallLrtEntries)) {
        LrtEntry* free_entry = get_entry(top_index);
        segment_state_.top_index = top_index + 1u;
        return store_obj(free_entry, "small_table/empty-free-list");
      }
    } else if (LIKELY(!IsCheckJniEnabled())) {
      uint32_t first_free_index = GetFirstFreeIndex();
      DCHECK_NE(first_free_index, kFreeListEnd);
      if (UNLIKELY(first_free_index >= top_index)) {
        PrunePoppedFreeEntries(get_entry);
        first_free_index = GetFirstFreeIndex();
      }
      if (first_free_index != kFreeListEnd && first_free_index >= previous_state_.top_index) {
        DCHECK_LT(first_free_index, segment_state_.top_index);  // Popped entries pruned above.
        LrtEntry* free_entry = get_entry(first_free_index);
        // Use the `free_entry` only if it was created with CheckJNI disabled.
        LrtEntry* serial_number_entry = GetCheckJniSerialNumberEntry(free_entry);
        if (!serial_number_entry->IsSerialNumber()) {
          free_entries_list_ = FirstFreeField::Update(free_entry->GetNextFree(), 0u);
          return store_obj(free_entry, "small_table/reuse-empty-slot");
        }
      }
      if (top_index != kSmallLrtEntries) {
        LrtEntry* free_entry = get_entry(top_index);
        segment_state_.top_index = top_index + 1u;
        return store_obj(free_entry, "small_table/pruned-free-list");
      }
    }
  }
  DCHECK(IsCheckJniEnabled() || small_table == nullptr || top_index == kSmallLrtEntries);

  // Process free list: prune, reuse free entry or pad for CheckJNI.
  uint32_t first_free_index = GetFirstFreeIndex();
  if (first_free_index != kFreeListEnd && first_free_index >= top_index) {
    PrunePoppedFreeEntries([&](size_t index) { return GetEntry(index); });
    first_free_index = GetFirstFreeIndex();
  }
  if (first_free_index != kFreeListEnd && first_free_index >= previous_state_.top_index) {
    // Reuse the free entry if it was created with the same CheckJNI setting.
    DCHECK_LT(first_free_index, top_index);  // Popped entries have been pruned above.
    LrtEntry* free_entry = GetEntry(first_free_index);
    LrtEntry* serial_number_entry = GetCheckJniSerialNumberEntry(free_entry);
    if (serial_number_entry->IsSerialNumber() == IsCheckJniEnabled()) {
      free_entries_list_ = FirstFreeField::Update(free_entry->GetNextFree(), free_entries_list_);
      if (UNLIKELY(IsCheckJniEnabled())) {
        DCHECK_NE(free_entry, serial_number_entry);
        uint32_t serial_number = IncrementSerialNumber(serial_number_entry);
        free_entry = serial_number_entry + serial_number;
        DCHECK_EQ(
            free_entry,
            GetEntry(RoundDown(first_free_index, kCheckJniEntriesPerReference) + serial_number));
      }
      return store_obj(free_entry, "reuse-empty-slot");
    }
  }
  if (UNLIKELY(IsCheckJniEnabled()) && !IsAligned<kCheckJniEntriesPerReference>(top_index)) {
    // Add non-CheckJNI holes up to the next serial number entry.
    for (; !IsAligned<kCheckJniEntriesPerReference>(top_index); ++top_index) {
      GetEntry(top_index)->SetNextFree(first_free_index);
      first_free_index = top_index;
    }
    free_entries_list_ = FirstFreeField::Update(first_free_index, 1u << kFlagCheckJni);
    segment_state_.top_index = top_index;
  }

  // Resize (double the space) if needed.
  if (UNLIKELY(top_index == max_entries_)) {
    static_assert(IsPowerOfTwo(kMaxTableSizeInBytes));
    static_assert(IsPowerOfTwo(sizeof(LrtEntry)));
    DCHECK(IsPowerOfTwo(max_entries_));
    if (kMaxTableSizeInBytes == max_entries_ * sizeof(LrtEntry)) {
      std::ostringstream oss;
      oss << "JNI ERROR (app bug): " << kLocal << " table overflow "
          << "(max=" << max_entries_ << ")" << std::endl
          << MutatorLockedDumpable<LocalReferenceTable>(*this)
          << " Resizing failed: Cannot resize over the maximum permitted size.";
      *error_msg = oss.str();
      return nullptr;
    }

    std::string inner_error_msg;
    if (!Resize(max_entries_ * 2u, &inner_error_msg)) {
      std::ostringstream oss;
      oss << "JNI ERROR (app bug): " << kLocal << " table overflow "
          << "(max=" << max_entries_ << ")" << std::endl
          << MutatorLockedDumpable<LocalReferenceTable>(*this)
          << " Resizing failed: " << inner_error_msg;
      *error_msg = oss.str();
      return nullptr;
    }
  }

  // Use the next entry.
  if (UNLIKELY(IsCheckJniEnabled())) {
    DCHECK_ALIGNED(top_index, kCheckJniEntriesPerReference);
    DCHECK_ALIGNED(previous_state_.top_index, kCheckJniEntriesPerReference);
    DCHECK_ALIGNED(max_entries_, kCheckJniEntriesPerReference);
    LrtEntry* serial_number_entry = GetEntry(top_index);
    uint32_t serial_number = IncrementSerialNumber(serial_number_entry);
    LrtEntry* free_entry = serial_number_entry + serial_number;
    DCHECK_EQ(free_entry, GetEntry(top_index + serial_number));
    segment_state_.top_index = top_index + kCheckJniEntriesPerReference;
    return store_obj(free_entry, "slow-path/check-jni");
  }
  LrtEntry* free_entry = GetEntry(top_index);
  segment_state_.top_index = top_index + 1u;
  return store_obj(free_entry, "slow-path");
}

// Removes an object.
//
// This method is not called when a local frame is popped; this is only used
// for explicit single removals.
//
// If the entry is not at the top, we just add it to the free entry list.
// If the entry is at the top, we pop it from the top and check if there are
// free entries under it to remove in order to reduce the size of the table.
//
// Returns "false" if nothing was removed.
bool LocalReferenceTable::Remove(IndirectRef iref) {
  if (kDebugLRT) {
    LOG(INFO) << "+++ Remove: previous_state=" << previous_state_.top_index
              << " top_index=" << segment_state_.top_index;
  }

  IndirectRefKind kind = IndirectReferenceTable::GetIndirectRefKind(iref);
  if (UNLIKELY(kind != kLocal)) {
    Thread* self = Thread::Current();
    if (kind == kJniTransition) {
      if (self->IsJniTransitionReference(reinterpret_cast<jobject>(iref))) {
        // Transition references count as local but they cannot be deleted.
        // TODO: They could actually be cleared on the stack, except for the `jclass`
        // reference for static methods that points to the method's declaring class.
        JNIEnvExt* env = self->GetJniEnv();
        DCHECK(env != nullptr);
        if (env->IsCheckJniEnabled()) {
          const char* msg = kDumpStackOnNonLocalReference
              ? "Attempt to remove non-JNI local reference, dumping thread"
              : "Attempt to remove non-JNI local reference";
          LOG(WARNING) << msg;
          if (kDumpStackOnNonLocalReference) {
            self->Dump(LOG_STREAM(WARNING));
          }
        }
        return true;
      }
    }
    if (kDumpStackOnNonLocalReference && IsCheckJniEnabled()) {
      // Log the error message and stack. Repeat the message as FATAL later.
      LOG(ERROR) << "Attempt to delete " << kind
                 << " reference as local JNI reference, dumping stack";
      self->Dump(LOG_STREAM(ERROR));
    }
    LOG(IsCheckJniEnabled() ? ERROR : FATAL)
        << "Attempt to delete " << kind << " reference as local JNI reference";
    return false;
  }

  DCHECK_LE(previous_state_.top_index, segment_state_.top_index);
  DCHECK(max_entries_ == kSmallLrtEntries ? small_table_ != nullptr : !tables_.empty());
  DCheckValidReference(iref);

  LrtEntry* entry = ToLrtEntry(iref);
  uint32_t entry_index = GetReferenceEntryIndex(iref);
  uint32_t top_index = segment_state_.top_index;
  const uint32_t bottom_index = previous_state_.top_index;

  if (entry_index < bottom_index) {
    // Wrong segment.
    LOG(WARNING) << "Attempt to remove index outside index area (" << entry_index
                 << " vs " << bottom_index << "-" << top_index << ")";
    return false;
  }

  if (UNLIKELY(IsCheckJniEnabled())) {
    // Ignore invalid references. CheckJNI should have aborted before passing this reference
    // to `LocalReferenceTable::Remove()` but gtests intercept the abort and proceed anyway.
    std::string error_msg;
    if (!IsValidReference(iref, &error_msg)) {
      LOG(WARNING) << "Attempt to remove invalid reference: " << error_msg;
      return false;
    }
  }
  DCHECK_LT(entry_index, top_index);

  // Workaround for double `DeleteLocalRef` bug. b/298297411
  if (entry->IsFree()) {
    // In debug build or with CheckJNI enabled, we would have detected this above.
    LOG(ERROR) << "App error: `DeleteLocalRef()` on already deleted local ref. b/298297411";
    return false;
  }

  // Prune the free entry list if a segment with holes was popped before the `Remove()` call.
  uint32_t first_free_index = GetFirstFreeIndex();
  if (first_free_index != kFreeListEnd && first_free_index >= top_index) {
    PrunePoppedFreeEntries([&](size_t index) { return GetEntry(index); });
  }

  // Check if we're removing the top entry (created with any CheckJNI setting).
  bool is_top_entry = false;
  uint32_t prune_end = entry_index;
  if (GetCheckJniSerialNumberEntry(entry)->IsSerialNumber()) {
    LrtEntry* serial_number_entry = GetCheckJniSerialNumberEntry(entry);
    uint32_t serial_number = dchecked_integral_cast<uint32_t>(entry - serial_number_entry);
    DCHECK_EQ(serial_number, serial_number_entry->GetSerialNumber());
    prune_end = entry_index - serial_number;
    is_top_entry = (prune_end == top_index - kCheckJniEntriesPerReference);
  } else {
    is_top_entry = (entry_index == top_index - 1u);
  }
  if (is_top_entry) {
    // Top-most entry. Scan up and consume holes created with the current CheckJNI setting.
    constexpr uint32_t kDeadLocalValue = 0xdead10c0;
    entry->SetReference(reinterpret_cast32<mirror::Object*>(kDeadLocalValue));

    // TODO: Maybe we should not prune free entries from the top of the segment
    // because it has quadratic worst-case complexity. We could still prune while
    // the first free list entry is at the top.
    uint32_t prune_start = prune_end;
    size_t prune_count;
    auto find_prune_range = [&](size_t chunk_size, auto is_prev_entry_free) {
      while (prune_start > bottom_index && is_prev_entry_free(prune_start)) {
        prune_start -= chunk_size;
      }
      prune_count = (prune_end - prune_start) / chunk_size;
    };

    if (UNLIKELY(IsCheckJniEnabled())) {
      auto is_prev_entry_free = [&](size_t index) {
        DCHECK_ALIGNED(index, kCheckJniEntriesPerReference);
        LrtEntry* serial_number_entry = GetEntry(index - kCheckJniEntriesPerReference);
        DCHECK_ALIGNED(serial_number_entry, kCheckJniEntriesPerReference * sizeof(LrtEntry));
        if (!serial_number_entry->IsSerialNumber()) {
          return false;
        }
        uint32_t serial_number = serial_number_entry->GetSerialNumber();
        DCHECK(IsValidSerialNumber(serial_number));
        LrtEntry* entry = serial_number_entry + serial_number;
        DCHECK_EQ(entry, GetEntry(prune_start - kCheckJniEntriesPerReference + serial_number));
        return entry->IsFree();
      };
      find_prune_range(kCheckJniEntriesPerReference, is_prev_entry_free);
    } else {
      auto is_prev_entry_free = [&](size_t index) {
        LrtEntry* entry = GetEntry(index - 1u);
        return entry->IsFree() && !GetCheckJniSerialNumberEntry(entry)->IsSerialNumber();
      };
      find_prune_range(1u, is_prev_entry_free);
    }

    if (prune_count != 0u) {
      // Remove pruned entries from the free list.
      size_t remaining = prune_count;
      uint32_t free_index = GetFirstFreeIndex();
      while (remaining != 0u && free_index >= prune_start) {
        DCHECK_NE(free_index, kFreeListEnd);
        LrtEntry* pruned_entry = GetEntry(free_index);
        free_index = pruned_entry->GetNextFree();
        pruned_entry->SetReference(reinterpret_cast32<mirror::Object*>(kDeadLocalValue));
        --remaining;
      }
      free_entries_list_ = FirstFreeField::Update(free_index, free_entries_list_);
      while (remaining != 0u) {
        DCHECK_NE(free_index, kFreeListEnd);
        DCHECK_LT(free_index, prune_start);
        DCHECK_GE(free_index, bottom_index);
        LrtEntry* free_entry = GetEntry(free_index);
        while (free_entry->GetNextFree() < prune_start) {
          free_index = free_entry->GetNextFree();
          DCHECK_GE(free_index, bottom_index);
          free_entry = GetEntry(free_index);
        }
        LrtEntry* pruned_entry = GetEntry(free_entry->GetNextFree());
        free_entry->SetNextFree(pruned_entry->GetNextFree());
        pruned_entry->SetReference(reinterpret_cast32<mirror::Object*>(kDeadLocalValue));
        --remaining;
      }
      DCHECK(free_index == kFreeListEnd || free_index < prune_start)
          << "free_index=" << free_index << ", prune_start=" << prune_start;
    }
    segment_state_.top_index = prune_start;
    if (kDebugLRT) {
      LOG(INFO) << "+++ removed last entry, pruned " << prune_count
                << ", new top= " << segment_state_.top_index;
    }
  } else {
    // Not the top-most entry. This creates a hole.
    entry->SetNextFree(GetFirstFreeIndex());
    free_entries_list_ = FirstFreeField::Update(entry_index, free_entries_list_);
    if (kDebugLRT) {
      LOG(INFO) << "+++ removed entry and left hole at " << entry_index;
    }
  }

  return true;
}

void LocalReferenceTable::AssertEmpty() {
  CHECK_EQ(Capacity(), 0u) << "Internal Error: non-empty local reference table.";
}

void LocalReferenceTable::Trim() {
  ScopedTrace trace(__PRETTY_FUNCTION__);
  const size_t num_mem_maps = table_mem_maps_.size();
  if (num_mem_maps == 0u) {
    // Only small tables; nothing to do here. (Do not unnecessarily prune popped free entries.)
    return;
  }
  DCHECK_EQ(tables_.size(), num_mem_maps + MaxSmallTables());
  const size_t top_index = segment_state_.top_index;
  // Prune popped free entries before potentially losing their memory.
  if (UNLIKELY(GetFirstFreeIndex() != kFreeListEnd) &&
      UNLIKELY(GetFirstFreeIndex() >= segment_state_.top_index)) {
    PrunePoppedFreeEntries([&](size_t index) { return GetEntry(index); });
  }
  // Small tables can hold as many entries as the next table.
  const size_t small_tables_capacity = GetTableSize(MaxSmallTables());
  size_t mem_map_index = 0u;
  if (top_index > small_tables_capacity) {
    const size_t table_size = TruncToPowerOfTwo(top_index);
    const size_t table_index = NumTablesForSize(table_size);
    const size_t start_index = top_index - table_size;
    mem_map_index = table_index - MaxSmallTables();
    if (start_index != 0u) {
      ++mem_map_index;
      LrtEntry* table = tables_[table_index];
      uint8_t* release_start = AlignUp(reinterpret_cast<uint8_t*>(&table[start_index]), gPageSize);
      uint8_t* release_end = reinterpret_cast<uint8_t*>(&table[table_size]);
      DCHECK_GE(reinterpret_cast<uintptr_t>(release_end),
                reinterpret_cast<uintptr_t>(release_start));
      DCHECK_ALIGNED_PARAM(release_end, gPageSize);
      DCHECK_ALIGNED_PARAM(release_end - release_start, gPageSize);
      if (release_start != release_end) {
        madvise(release_start, release_end - release_start, MADV_DONTNEED);
      }
    }
  }
  for (MemMap& mem_map : ArrayRef<MemMap>(table_mem_maps_).SubArray(mem_map_index)) {
    madvise(mem_map.Begin(), mem_map.Size(), MADV_DONTNEED);
  }
}

template <typename Visitor>
void LocalReferenceTable::VisitRootsInternal(Visitor&& visitor) const {
  auto visit_table = [&](LrtEntry* table, size_t count) REQUIRES_SHARED(Locks::mutator_lock_) {
    for (size_t i = 0; i != count; ) {
      LrtEntry* entry;
      if (i % kCheckJniEntriesPerReference == 0u && table[i].IsSerialNumber()) {
        entry = &table[i + table[i].GetSerialNumber()];
        i += kCheckJniEntriesPerReference;
        DCHECK_LE(i, count);
      } else {
        entry = &table[i];
        i += 1u;
      }
      DCHECK(!entry->IsSerialNumber());
      if (!entry->IsFree()) {
        GcRoot<mirror::Object>* root = entry->GetRootAddress();
        DCHECK(!root->IsNull());
        visitor(root);
      }
    }
  };

  if (small_table_ != nullptr) {
    visit_table(small_table_, segment_state_.top_index);
  } else {
    uint32_t remaining = segment_state_.top_index;
    size_t table_index = 0u;
    while (remaining != 0u) {
      size_t count = std::min<size_t>(remaining, GetTableSize(table_index));
      visit_table(tables_[table_index], count);
      ++table_index;
      remaining -= count;
    }
  }
}

void LocalReferenceTable::VisitRoots(RootVisitor* visitor, const RootInfo& root_info) {
  BufferedRootVisitor<kDefaultBufferedRootCount> root_visitor(visitor, root_info);
  VisitRootsInternal([&](GcRoot<mirror::Object>* root) REQUIRES_SHARED(Locks::mutator_lock_) {
                       root_visitor.VisitRoot(*root);
                     });
}

void LocalReferenceTable::Dump(std::ostream& os) const {
  os << kLocal << " table dump:\n";
  ReferenceTable::Table entries;
  VisitRootsInternal([&](GcRoot<mirror::Object>* root) REQUIRES_SHARED(Locks::mutator_lock_) {
                       entries.push_back(*root);
                     });
  ReferenceTable::Dump(os, entries);
}

bool LocalReferenceTable::EnsureFreeCapacity(size_t free_capacity, std::string* error_msg) {
  // TODO: Pass `previous_state` so that we can check holes.
  DCHECK_GE(free_capacity, static_cast<size_t>(1));
  size_t top_index = segment_state_.top_index;
  DCHECK_LE(top_index, max_entries_);

  if (IsCheckJniEnabled()) {
    // High values lead to the maximum size check failing below.
    if (free_capacity >= std::numeric_limits<size_t>::max() / kCheckJniEntriesPerReference) {
      free_capacity = std::numeric_limits<size_t>::max();
    } else {
      free_capacity *= kCheckJniEntriesPerReference;
    }
  }

  // TODO: Include holes from the current segment in the calculation.
  if (free_capacity <= max_entries_ - top_index) {
    return true;
  }

  if (free_capacity > kMaxTableSize - top_index) {
    *error_msg = android::base::StringPrintf(
        "Requested size exceeds maximum: %zu > %zu (%zu used)",
        free_capacity,
        kMaxTableSize - top_index,
        top_index);
    return false;
  }

  // Try to increase the table size.
  if (!Resize(top_index + free_capacity, error_msg)) {
    LOG(WARNING) << "JNI ERROR: Unable to reserve space in EnsureFreeCapacity (" << free_capacity
                 << "): " << std::endl
                 << MutatorLockedDumpable<LocalReferenceTable>(*this)
                 << " Resizing failed: " << *error_msg;
    return false;
  }
  return true;
}

size_t LocalReferenceTable::FreeCapacity() const {
  // TODO: Include holes in current segment.
  if (IsCheckJniEnabled()) {
    DCHECK_ALIGNED(max_entries_, kCheckJniEntriesPerReference);
    // The `segment_state_.top_index` is not necessarily aligned; rounding down.
    return (max_entries_ - segment_state_.top_index) / kCheckJniEntriesPerReference;
  } else {
    return max_entries_ - segment_state_.top_index;
  }
}

}  // namespace jni
}  // namespace art
