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

#ifndef ART_RUNTIME_JNI_LOCAL_REFERENCE_TABLE_H_
#define ART_RUNTIME_JNI_LOCAL_REFERENCE_TABLE_H_

#include <stdint.h>

#include <iosfwd>
#include <limits>
#include <string>

#include <android-base/logging.h>

#include "base/bit_field.h"
#include "base/bit_utils.h"
#include "base/casts.h"
#include "base/dchecked_vector.h"
#include "base/locks.h"
#include "base/macros.h"
#include "base/mem_map.h"
#include "base/mutex.h"
#include "gc_root.h"
#include "indirect_reference_table.h"
#include "mirror/object_reference.h"
#include "obj_ptr.h"
#include "offsets.h"

namespace art HIDDEN {

class RootInfo;

namespace mirror {
class Object;
}  // namespace mirror

namespace jni {

// Maintain a table of local JNI references.
//
// The table contains object references that are part of the GC root set. When an object is
// added we return an `IndirectRef` that is not a valid pointer but can be used to find the
// original value in O(1) time. Conversions to and from local JNI references are performed
// on upcalls and downcalls as well as in JNI functions, so they need to be very fast.
//
// To be efficient for JNI local variable storage, we need to provide operations that allow us to
// operate on segments of the table, where segments are pushed and popped as if on a stack. For
// example, deletion of an entry should only succeed if it appears in the current segment, and we
// want to be able to strip off the current segment quickly when a method returns. Additions to the
// table must be made in the current segment even if space is available in an earlier area.
//
// A new segment is created when we call into native code from managed code, or when we handle
// the JNI PushLocalFrame function.
//
// The GC must be able to scan the entire table quickly.
//
// In summary, these must be very fast:
//  - adding or removing a segment
//  - adding references (always adding to the current segment)
//  - converting a local reference back to an Object
// These can be a little slower, but must still be pretty quick:
//  - removing individual references
//  - scanning the entire table straight through
//
// If there's more than one segment, we don't guarantee that the table will fill completely before
// we fail due to lack of space. We do ensure that the current segment will pack tightly, which
// should satisfy JNI requirements (e.g. EnsureLocalCapacity).

// To get the desired behavior for JNI locals, we need to know the bottom and top of the current
// "segment". When we call a native method or push a local frame, the current top index gets pushed
// on, and serves as the new bottom. When we pop a frame off, the value from the stack becomes the
// new top index, and the value stored in the previous frame becomes the new bottom.
//
// If we delete entries from the middle of the list, we will be left with "holes" which we track
// with a singly-linked list, so that they can be reused quickly. After a segment has been removed,
// we need to prune removed free entries from the front of this singly-linked list before we can
// reuse a free entry from the current segment. This is linear in the number of entries removed
// and may appear as a slow reference addition but this slow down is attributable to the previous
// removals with a constant time per removal.
//
// Without CheckJNI, we aim for the fastest possible implementation, so there is no error checking
// (in release build) and stale references can be erroneously used, especially after the same slot
// has been reused for another reference which we cannot easily detect (even in debug build).
//
// With CheckJNI, we rotate the slots that we use based on a "serial number".
// This increases the memory use but it allows for decent error detection.
//
// We allow switching between CheckJNI enabled and disabled but entries created with CheckJNI
// disabled shall have weaker checking even after enabling CheckJNI and the switch can also
// prevent reusing a hole that held a reference created with a different CheckJNI setting.

// The state of the current segment contains the top index.
struct LRTSegmentState {
  uint32_t top_index;
};

// Use as initial value for "cookie", and when table has only one segment.
static constexpr LRTSegmentState kLRTFirstSegment = { 0 };

// Each entry in the `LocalReferenceTable` can contain a null (initially or after a `Trim()`)
// or reference, or it can be marked as free and hold the index of the next free entry.
// If CheckJNI is (or was) enabled, some entries can contain serial numbers instead and
// only one other entry in a CheckJNI chunk starting with a serial number is active.
//
// Valid bit patterns:
//                   33222222222211111111110000000000
//                   10987654321098765432109876543210
//   null:           00000000000000000000000000000000  // Only above the top index.
//   reference:      <----- reference value ----->000  // See also `kObjectAlignment`.
//   free:           <-------- next free --------->01
//   serial number:  <------ serial number ------->10  // CheckJNI entry.
// Note that serial number entries can appear only as the first entry of a 16-byte aligned
// chunk of four entries and the serial number in the range [1, 3] specifies which of the
// other three entries in the chunk is currently used.
class LrtEntry {
 public:
  void SetReference(ObjPtr<mirror::Object> ref) REQUIRES_SHARED(Locks::mutator_lock_);

  ObjPtr<mirror::Object> GetReference() REQUIRES_SHARED(Locks::mutator_lock_);

  bool IsNull() const {
    return root_.IsNull();
  }

  void SetNextFree(uint32_t next_free) REQUIRES_SHARED(Locks::mutator_lock_);

  uint32_t GetNextFree() {
    DCHECK(IsFree());
    DCHECK(!IsSerialNumber());
    return NextFreeField::Decode(GetRawValue());
  }

  bool IsFree() {
    return (GetRawValue() & (1u << kFlagFree)) != 0u;
  }

  void SetSerialNumber(uint32_t serial_number) REQUIRES_SHARED(Locks::mutator_lock_);

  uint32_t GetSerialNumber() {
    DCHECK(IsSerialNumber());
    DCHECK(!IsFree());
    return GetSerialNumberUnchecked();
  }

  uint32_t GetSerialNumberUnchecked() {
    return SerialNumberField::Decode(GetRawValue());
  }

  bool IsSerialNumber() {
    return (GetRawValue() & (1u << kFlagSerialNumber)) != 0u;
  }

  GcRoot<mirror::Object>* GetRootAddress() {
    return &root_;
  }

  static constexpr uint32_t FreeListEnd() {
    return MaxInt<uint32_t>(kFieldNextFreeBits);
  }

 private:
  // Definitions of bit fields and flags.
  static constexpr size_t kFlagFree = 0u;
  static constexpr size_t kFlagSerialNumber = kFlagFree + 1u;
  static constexpr size_t kFieldNextFree = kFlagSerialNumber + 1u;
  static constexpr size_t kFieldNextFreeBits = BitSizeOf<uint32_t>() - kFieldNextFree;

  using NextFreeField = BitField<uint32_t, kFieldNextFree, kFieldNextFreeBits>;
  using SerialNumberField = NextFreeField;

  static_assert(kObjectAlignment > (1u << kFlagFree));
  static_assert(kObjectAlignment > (1u << kFlagSerialNumber));

  void SetVRegValue(uint32_t value) REQUIRES_SHARED(Locks::mutator_lock_);

  uint32_t GetRawValue() {
    return root_.AddressWithoutBarrier()->AsVRegValue();
  }

  // We record the contents as a `GcRoot<>` but it is an actual `GcRoot<>` only if it's below
  // the current segment's top index, it's not a "serial number" or inactive entry in a CheckJNI
  // chunk, and it's not marked as "free". Such entries are never null.
  GcRoot<mirror::Object> root_;
};
static_assert(sizeof(LrtEntry) == sizeof(mirror::CompressedReference<mirror::Object>));
// Assert that the low bits of an `LrtEntry*` are sufficient for encoding the reference kind.
static_assert(enum_cast<uint32_t>(IndirectRefKind::kLastKind) < alignof(LrtEntry));


// We initially allocate local reference tables with a small number of entries, packing
// multiple tables into a single page. If we need to expand, we double the capacity,
// first allocating another chunk with the same number of entries as the first chunk
// and then allocating twice as big chunk on each subsequent expansion.
static constexpr size_t kInitialLrtBytes = 512;  // Number of bytes in an initial local table.
static constexpr size_t kSmallLrtEntries = kInitialLrtBytes / sizeof(LrtEntry);
static_assert(IsPowerOfTwo(kInitialLrtBytes));

static_assert(kMinPageSize % kInitialLrtBytes == 0);
static_assert(kInitialLrtBytes % sizeof(LrtEntry) == 0);

// A minimal stopgap allocator for initial small local LRT tables.
class SmallLrtAllocator {
 public:
  SmallLrtAllocator();

  // Allocate a small block of `LrtEntries` for the `LocalReferenceTable` table. The `size`
  // must be a power of 2, at least `kSmallLrtEntries`, and requiring less than a page of memory.
  LrtEntry* Allocate(size_t size, std::string* error_msg) REQUIRES(!lock_);

  void Deallocate(LrtEntry* unneeded, size_t size) REQUIRES(!lock_);

 private:
  // Number of free lists in the allocator.
#ifdef ART_PAGE_SIZE_AGNOSTIC
  const size_t num_lrt_slots_ = (WhichPowerOf2(gPageSize / kInitialLrtBytes));
#else
  static constexpr size_t num_lrt_slots_ = (WhichPowerOf2(gPageSize / kInitialLrtBytes));
#endif

  size_t GetIndex(size_t size);

  // Free lists of small chunks linked through the first word.
  dchecked_vector<void*> free_lists_;

  // Repository of MemMaps used for small LRT tables.
  dchecked_vector<MemMap> shared_lrt_maps_;

  Mutex lock_;  // Level kGenericBottomLock; acquired before mem_map_lock_, which is a C++ mutex.
};

class LocalReferenceTable {
 public:
  explicit LocalReferenceTable(bool check_jni);
  ~LocalReferenceTable();

  // Set the CheckJNI enabled status.
  // Called only from the Zygote post-fork callback while the process is single-threaded.
  // Enabling CheckJNI reduces the number of entries that can be stored, thus invalidating
  // guarantees provided by a previous call to `EnsureFreeCapacity()`.
  void SetCheckJniEnabled(bool enabled);

  // Returns whether the CheckJNI is enabled for this `LocalReferenceTable`.
  bool IsCheckJniEnabled() const {
    return (free_entries_list_ & (1u << kFlagCheckJni)) != 0u;
  }

  // Initialize the `LocalReferenceTable`.
  //
  // Max_count is the requested minimum initial capacity (resizable). The actual initial
  // capacity can be higher to utilize all allocated memory.
  //
  // Returns true on success.
  // On failure, returns false and reports error in `*error_msg`.
  bool Initialize(size_t max_count, std::string* error_msg);

  // Add a new entry. The `obj` must be a valid non-null object reference. This function
  // will return null if an error happened (with an appropriate error message set).
  EXPORT IndirectRef Add(ObjPtr<mirror::Object> obj, std::string* error_msg)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Given an `IndirectRef` in the table, return the `Object` it refers to.
  //
  // This function may abort under error conditions in debug build.
  // In release builds, error conditions are unchecked and the function can
  // return old or invalid references from popped segments and deleted entries.
  ObjPtr<mirror::Object> Get(IndirectRef iref) const
      REQUIRES_SHARED(Locks::mutator_lock_) ALWAYS_INLINE;

  // Updates an existing indirect reference to point to a new object.
  // Used exclusively for updating `String` references after calling a `String` constructor.
  void Update(IndirectRef iref, ObjPtr<mirror::Object> obj) REQUIRES_SHARED(Locks::mutator_lock_);

  // Remove an existing entry.
  //
  // If the entry is not between the current top index and the bottom index
  // specified by the cookie, we don't remove anything.  This is the behavior
  // required by JNI's DeleteLocalRef function.
  //
  // Returns "false" if nothing was removed.
  bool Remove(IndirectRef iref)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void AssertEmpty();

  void Dump(std::ostream& os) const
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::alloc_tracker_lock_);

  IndirectRefKind GetKind() const {
    return kLocal;
  }

  // Return the number of entries in the entire table. This includes holes,
  // and so may be larger than the actual number of "live" entries.
  // The value corresponds to the number of entries for the current CheckJNI setting
  // and may be wrong if there are entries created with a different CheckJNI setting.
  size_t Capacity() const {
    if (IsCheckJniEnabled()) {
      DCHECK_ALIGNED(segment_state_.top_index, kCheckJniEntriesPerReference);
      return segment_state_.top_index / kCheckJniEntriesPerReference;
    } else {
      return segment_state_.top_index;
    }
  }

  // Ensure that at least free_capacity elements are available, or return false.
  // Caller ensures free_capacity > 0.
  bool EnsureFreeCapacity(size_t free_capacity, std::string* error_msg)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // See implementation of EnsureFreeCapacity. We'll only state here how much is trivially free,
  // without recovering holes. Thus this is a conservative estimate.
  size_t FreeCapacity() const;

  EXPORT void VisitRoots(RootVisitor* visitor, const RootInfo& root_info)
      REQUIRES_SHARED(Locks::mutator_lock_);

  LRTSegmentState PushFrame() {
    if (kDebugLRT) {
      LOG(INFO) << "+++ Push frame: previous state " << previous_state_.top_index << " -> "
                << segment_state_.top_index;
    }
    LRTSegmentState result = previous_state_;
    previous_state_ = segment_state_;
    return result;
  }

  void PopFrame(LRTSegmentState previous_state) {
    if (kDebugLRT) {
      LOG(INFO) << "+++ Pop frame: current state " << segment_state_.top_index << " -> "
                << previous_state_.top_index << ", previous state -> " << previous_state.top_index;
    }
    segment_state_ = previous_state_;
    previous_state_ = previous_state;
  }

  static MemberOffset PreviousStateOffset() {
    // Note: The `previous_state_` must be before any pointer-size-dependent members, so that
    // `MEMBER_OFFSET()` gives the correct value even for cross-compilation.
    return MemberOffset(OFFSETOF_MEMBER(LocalReferenceTable, previous_state_));
  }

  static MemberOffset SegmentStateOffset() {
    // Note: The `segment_state_` must be before any pointer-size-dependent members, so that
    // `MEMBER_OFFSET()` gives the correct value even for cross-compilation.
    return MemberOffset(OFFSETOF_MEMBER(LocalReferenceTable, segment_state_));
  }

  // Release pages past the end of the table that may have previously held references.
  void Trim() REQUIRES_SHARED(Locks::mutator_lock_);

  /* Reference validation for CheckJNI and debug build. */
  bool IsValidReference(IndirectRef, /*out*/std::string* error_msg) const
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  static constexpr bool kDebugLRT = false;

  // Flags and fields in the `free_entries_list_`.
  static constexpr size_t kFlagCheckJni = 0u;
  // Skip a bit to have the same value range for the "first free" as the "next free" in `LrtEntry`.
  static constexpr size_t kFlagPadding = kFlagCheckJni + 1u;
  static constexpr size_t kFieldFirstFree = kFlagPadding + 1u;
  static constexpr size_t kFieldFirstFreeSize = BitSizeOf<uint32_t>() - kFieldFirstFree;

  using FirstFreeField = BitField<uint32_t, kFieldFirstFree, kFieldFirstFreeSize>;

  // The value of `FirstFreeField` in `free_entries_list_` indicating the end of the free list.
  static constexpr uint32_t kFreeListEnd = LrtEntry::FreeListEnd();
  static_assert(kFreeListEnd == MaxInt<uint32_t>(kFieldFirstFreeSize));

  // The value of `free_entries_list_` indicating empty free list and disabled CheckJNI.
  static constexpr uint32_t kEmptyFreeListAndCheckJniDisabled =
      FirstFreeField::Update(kFreeListEnd, 0u);  // kFlagCheckJni not set.

  // The number of entries per reference to detect obsolete reference uses with CheckJNI enabled.
  // The first entry serves as a serial number, one of the remaining entries can hold the actual
  // reference or the next free index.
  static constexpr size_t kCheckJniEntriesPerReference = 4u;
  static_assert(IsPowerOfTwo(kCheckJniEntriesPerReference));

  // The maximum total table size we allow.
  static constexpr size_t kMaxTableSizeInBytes = 128 * MB;
  static_assert(IsPowerOfTwo(kMaxTableSizeInBytes));
  static_assert(IsPowerOfTwo(sizeof(LrtEntry)));
  static constexpr size_t kMaxTableSize = kMaxTableSizeInBytes / sizeof(LrtEntry);

  static IndirectRef ToIndirectRef(LrtEntry* entry) {
    // The `IndirectRef` can be used to directly access the underlying `GcRoot<>`.
    DCHECK_EQ(reinterpret_cast<GcRoot<mirror::Object>*>(entry), entry->GetRootAddress());
    return reinterpret_cast<IndirectRef>(
        reinterpret_cast<uintptr_t>(entry) | static_cast<uintptr_t>(kLocal));
  }

  static LrtEntry* ToLrtEntry(IndirectRef iref) {
    DCHECK_EQ(IndirectReferenceTable::GetIndirectRefKind(iref), kLocal);
    return IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(iref);
  }

  static constexpr size_t GetTableSize(size_t table_index) {
    // First two tables have size `kSmallLrtEntries`, then it doubles for subsequent tables.
    return kSmallLrtEntries << (table_index != 0u ? table_index - 1u : 0u);
  }

  static constexpr size_t NumTablesForSize(size_t size) {
    DCHECK_GE(size, kSmallLrtEntries);
    DCHECK(IsPowerOfTwo(size));
    return 1u + WhichPowerOf2(size / kSmallLrtEntries);
  }

  static size_t MaxSmallTables() {
    return NumTablesForSize(gPageSize / sizeof(LrtEntry));
  }

  LrtEntry* GetEntry(size_t entry_index) const {
    DCHECK_LT(entry_index, max_entries_);
    if (LIKELY(small_table_ != nullptr)) {
      DCHECK_LT(entry_index, kSmallLrtEntries);
      DCHECK_EQ(max_entries_, kSmallLrtEntries);
      return &small_table_[entry_index];
    }
    size_t table_start_index =
        (entry_index < kSmallLrtEntries) ? 0u : TruncToPowerOfTwo(entry_index);
    size_t table_index =
        (entry_index < kSmallLrtEntries) ? 0u : NumTablesForSize(table_start_index);
    LrtEntry* table = tables_[table_index];
    return &table[entry_index - table_start_index];
  }

  // Get the entry index for a local reference. Note that this may be higher than
  // the current segment state. Returns maximum uint32 value if the reference does not
  // point to one of the internal tables.
  uint32_t GetReferenceEntryIndex(IndirectRef iref) const;

  static LrtEntry* GetCheckJniSerialNumberEntry(LrtEntry* entry) {
    return AlignDown(entry, kCheckJniEntriesPerReference * sizeof(LrtEntry));
  }

  static uint32_t IncrementSerialNumber(LrtEntry* serial_number_entry)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static bool IsValidSerialNumber(uint32_t serial_number) {
    return serial_number != 0u && serial_number < kCheckJniEntriesPerReference;
  }

  // Debug mode check that the reference is valid.
  void DCheckValidReference(IndirectRef iref) const REQUIRES_SHARED(Locks::mutator_lock_);

  // Resize the backing table to be at least `new_size` elements long. The `new_size`
  // must be larger than the current size. After return max_entries_ >= new_size.
  bool Resize(size_t new_size, std::string* error_msg);

  // Extract the first free index from `free_entries_list_`.
  uint32_t GetFirstFreeIndex() const {
    return FirstFreeField::Decode(free_entries_list_);
  }

  // Remove popped free entries from the list.
  // Called only if `free_entries_list_` points to a popped entry.
  template <typename EntryGetter>
  void PrunePoppedFreeEntries(EntryGetter&& get_entry);

  // Helper template function for visiting roots.
  template <typename Visitor>
  void VisitRootsInternal(Visitor&& visitor) const REQUIRES_SHARED(Locks::mutator_lock_);

  /// semi-public - read/write by jni down calls.
  // These two members need to be before any pointer-size-dependent members, so that
  // `MEMBER_OFFSET()` gives the correct value even for cross-compilation.
  LRTSegmentState previous_state_;
  LRTSegmentState segment_state_;

  // The maximum number of entries (modulo resizing).
  uint32_t max_entries_;

  // The singly-linked list of free nodes.
  // We use entry indexes instead of pointers and `kFreeListEnd` instead of null indicates
  // the end of the list. See `LocalReferenceTable::GetEntry()` and `LrtEntry::GetNextFree().
  //
  // We use the lowest bit to record whether CheckJNI is enabled. This helps us
  // check that the list is empty and CheckJNI is disabled in a single comparison.
  uint32_t free_entries_list_;

  // Individual tables.
  // As long as we have only one small table, we use `small_table_` to avoid an extra load
  // from another heap allocated location, otherwise we set it to null and use `tables_`.
  LrtEntry* small_table_;  // For optimizing the fast-path.
  dchecked_vector<LrtEntry*> tables_;

  // Mem maps where we store tables allocated directly with `MemMap`
  // rather than the `SmallLrtAllocator`.
  dchecked_vector<MemMap> table_mem_maps_;
};

}  // namespace jni
}  // namespace art

#endif  // ART_RUNTIME_JNI_LOCAL_REFERENCE_TABLE_H_
