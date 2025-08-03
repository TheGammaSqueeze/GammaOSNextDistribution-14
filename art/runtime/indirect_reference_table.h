/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef ART_RUNTIME_INDIRECT_REFERENCE_TABLE_H_
#define ART_RUNTIME_INDIRECT_REFERENCE_TABLE_H_

#include <stdint.h>

#include <iosfwd>
#include <limits>
#include <string>

#include <android-base/logging.h>

#include "base/bit_utils.h"
#include "base/locks.h"
#include "base/macros.h"
#include "base/mem_map.h"
#include "base/mutex.h"
#include "gc_root.h"
#include "obj_ptr.h"
#include "offsets.h"
#include "read_barrier_option.h"

namespace art HIDDEN {

class IsMarkedVisitor;
class RootInfo;

namespace mirror {
class Object;
}  // namespace mirror

// Indirect reference definition.  This must be interchangeable with JNI's jobject, and it's
// convenient to let null be null, so we use void*.
//
// We need a 2-bit reference kind (global, local, weak global) and the rest of the `IndirectRef`
// is used to locate the actual reference storage.
//
// For global and weak global references, we need a (potentially) large table index and we also
// reserve some bits to be used to detect stale indirect references: we put a serial number in
// the extra bits, and keep a copy of the serial number in the table. This requires more memory
// and additional memory accesses on add/get, but is moving-GC safe. It will catch additional
// problems, e.g.: create iref1 for obj, delete iref1, create iref2 for same obj, lookup iref1.
// A pattern based on object bits will miss this.
//
// Local references use the same bits for the reference kind but the rest of their `IndirectRef`
// encoding is different, see `LocalReferenceTable` for details.
using IndirectRef = void*;

// Indirect reference kind, used as the two low bits of IndirectRef.
//
// For convenience these match up with enum jobjectRefType from jni.h, except that
// we use value 0 for JNI transitions instead of marking invalid reference type.
enum IndirectRefKind {
  kJniTransition = 0,  // <<JNI transition frame reference>>
  kLocal         = 1,  // <<local reference>>
  kGlobal        = 2,  // <<global reference>>
  kWeakGlobal    = 3,  // <<weak global reference>>
  kLastKind      = kWeakGlobal
};
EXPORT std::ostream& operator<<(std::ostream& os, IndirectRefKind rhs);
const char* GetIndirectRefKindString(IndirectRefKind kind);

// Maintain a table of indirect references.  Used for global and weak global JNI references.
//
// The table contains object references, where the strong global references are part of the
// GC root set (but not the weak global references). When an object is added we return an
// `IndirectRef` that is not a valid pointer but can be used to find the original value in O(1)
// time. Conversions to and from indirect references are performed in JNI functions and when
// returning from native methods to managed code, so they need to be very fast.
//
// The GC must be able to scan the entire table quickly.
//
// In summary, these must be very fast:
//  - adding references
//  - removing references
//  - converting an indirect reference back to an Object
// These can be a little slower, but must still be pretty quick:
//  - scanning the entire table straight through

// Table definition.
//
// For the global reference tables, the expected common operations are adding a new entry and
// removing a recently-added entry (usually the most-recently-added entry).
//
// If we delete entries from the middle of the list, we will be left with "holes".  We track the
// number of holes so that, when adding new elements, we can quickly decide to do a trivial append
// or go slot-hunting.
//
// When the top-most entry is removed, any holes immediately below it are also removed. Thus,
// deletion of an entry may reduce "top_index" by more than one.
//
// Common alternative implementation: make IndirectRef a pointer to the actual reference slot.
// Instead of getting a table and doing a lookup, the lookup can be done instantly. Operations like
// determining the type and deleting the reference are more expensive because the table must be
// hunted for (i.e. you have to do a pointer comparison to see which table it's in), you can't move
// the table when expanding it (so realloc() is out), and tricks like serial number checking to
// detect stale references aren't possible (though we may be able to get similar benefits with other
// approaches).
//
// TODO: consider a "lastDeleteIndex" for quick hole-filling when an add immediately follows a
// delete.

// We associate a few bits of serial number with each reference, for error checking.
static constexpr unsigned int kIRTSerialBits = 3;
static constexpr uint32_t kIRTMaxSerial = ((1 << kIRTSerialBits) - 1);

class IrtEntry {
 public:
  void Add(ObjPtr<mirror::Object> obj) REQUIRES_SHARED(Locks::mutator_lock_);

  GcRoot<mirror::Object>* GetReference() {
    DCHECK_LE(serial_, kIRTMaxSerial);
    return &reference_;
  }

  const GcRoot<mirror::Object>* GetReference() const {
    DCHECK_LE(serial_, kIRTMaxSerial);
    return &reference_;
  }

  uint32_t GetSerial() const {
    return serial_;
  }

  void SetReference(ObjPtr<mirror::Object> obj) REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  uint32_t serial_;  // Incremented for each reuse; checked against reference.
  GcRoot<mirror::Object> reference_;
};
static_assert(sizeof(IrtEntry) == 2 * sizeof(uint32_t), "Unexpected sizeof(IrtEntry)");
static_assert(IsPowerOfTwo(sizeof(IrtEntry)), "Unexpected sizeof(IrtEntry)");

class IndirectReferenceTable {
 public:
  // Constructs an uninitialized indirect reference table. Use `Initialize()` to initialize it.
  explicit IndirectReferenceTable(IndirectRefKind kind);

  // Initialize the indirect reference table.
  //
  // Max_count is the requested total capacity (not resizable). The actual total capacity
  // can be higher to utilize all allocated memory (rounding up to whole pages).
  bool Initialize(size_t max_count, std::string* error_msg);

  ~IndirectReferenceTable();

  // Add a new entry. "obj" must be a valid non-null object reference. This function will
  // return null if an error happened (with an appropriate error message set).
  IndirectRef Add(ObjPtr<mirror::Object> obj, std::string* error_msg)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Given an IndirectRef in the table, return the Object it refers to.
  //
  // This function may abort under error conditions.
  template<ReadBarrierOption kReadBarrierOption = kWithReadBarrier>
  ObjPtr<mirror::Object> Get(IndirectRef iref) const REQUIRES_SHARED(Locks::mutator_lock_)
      ALWAYS_INLINE;

  // Updates an existing indirect reference to point to a new object.
  void Update(IndirectRef iref, ObjPtr<mirror::Object> obj) REQUIRES_SHARED(Locks::mutator_lock_);

  // Remove an existing entry.
  //
  // If the entry is not between the current top index and the bottom index
  // specified by the cookie, we don't remove anything.  This is the behavior
  // required by JNI's DeleteLocalRef function.
  //
  // Returns "false" if nothing was removed.
  bool Remove(IndirectRef iref);

  void Dump(std::ostream& os) const
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::alloc_tracker_lock_);

  IndirectRefKind GetKind() const {
    return kind_;
  }

  // Return the #of entries in the entire table.  This includes holes, and
  // so may be larger than the actual number of "live" entries.
  size_t Capacity() const {
    return top_index_;
  }

  // Return the number of non-null entries in the table. Only reliable for a
  // single segment table.
  int32_t NEntriesForGlobal() {
    return top_index_ - current_num_holes_;
  }

  // We'll only state here how much is trivially free, without recovering holes.
  // Thus this is a conservative estimate.
  size_t FreeCapacity() const;

  void VisitRoots(RootVisitor* visitor, const RootInfo& root_info)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Release pages past the end of the table that may have previously held references.
  void Trim() REQUIRES_SHARED(Locks::mutator_lock_);

  // Determine what kind of indirect reference this is. Opposite of EncodeIndirectRefKind.
  ALWAYS_INLINE static inline IndirectRefKind GetIndirectRefKind(IndirectRef iref) {
    return DecodeIndirectRefKind(reinterpret_cast<uintptr_t>(iref));
  }

  static constexpr uintptr_t GetGlobalOrWeakGlobalMask() {
    constexpr uintptr_t mask = enum_cast<uintptr_t>(kGlobal);
    static_assert(IsPowerOfTwo(mask));
    static_assert((mask & kJniTransition) == 0u);
    static_assert((mask & kLocal) == 0u);
    static_assert((mask & kGlobal) != 0u);
    static_assert((mask & kWeakGlobal) != 0u);
    return mask;
  }

  static bool IsGlobalOrWeakGlobalReference(IndirectRef iref) {
    return (reinterpret_cast<uintptr_t>(iref) & GetGlobalOrWeakGlobalMask()) != 0u;
  }

  static bool IsJniTransitionOrLocalReference(IndirectRef iref) {
    return !IsGlobalOrWeakGlobalReference(iref);
  }

  template <typename T>
  static T ClearIndirectRefKind(IndirectRef iref) {
    static_assert(std::is_pointer_v<T>);
    return reinterpret_cast<T>(
        reinterpret_cast<uintptr_t>(iref) & ~static_cast<uintptr_t>(kKindMask));
  }

  static constexpr uintptr_t GetIndirectRefKindMask() {
    return kKindMask;
  }

  /* Reference validation for CheckJNI. */
  bool IsValidReference(IndirectRef, /*out*/std::string* error_msg) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT void SweepJniWeakGlobals(IsMarkedVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::jni_weak_globals_lock_);

 private:
  static constexpr uint32_t kShiftedSerialMask = (1u << kIRTSerialBits) - 1;

  static constexpr size_t kKindBits = MinimumBitsToStore(
      static_cast<uint32_t>(IndirectRefKind::kLastKind));
  static constexpr uint32_t kKindMask = (1u << kKindBits) - 1;

  static constexpr uintptr_t EncodeIndex(uint32_t table_index) {
    static_assert(sizeof(IndirectRef) == sizeof(uintptr_t), "Unexpected IndirectRef size");
    DCHECK_LE(MinimumBitsToStore(table_index), BitSizeOf<uintptr_t>() - kIRTSerialBits - kKindBits);
    return (static_cast<uintptr_t>(table_index) << kKindBits << kIRTSerialBits);
  }
  static constexpr uint32_t DecodeIndex(uintptr_t uref) {
    return static_cast<uint32_t>((uref >> kKindBits) >> kIRTSerialBits);
  }

  static constexpr uintptr_t EncodeIndirectRefKind(IndirectRefKind kind) {
    return static_cast<uintptr_t>(kind);
  }
  static constexpr IndirectRefKind DecodeIndirectRefKind(uintptr_t uref) {
    return static_cast<IndirectRefKind>(uref & kKindMask);
  }

  static constexpr uintptr_t EncodeSerial(uint32_t serial) {
    DCHECK_LE(MinimumBitsToStore(serial), kIRTSerialBits);
    return serial << kKindBits;
  }
  static constexpr uint32_t DecodeSerial(uintptr_t uref) {
    return static_cast<uint32_t>(uref >> kKindBits) & kShiftedSerialMask;
  }

  constexpr uintptr_t EncodeIndirectRef(uint32_t table_index, uint32_t serial) const {
    DCHECK_LT(table_index, max_entries_);
    return EncodeIndex(table_index) | EncodeSerial(serial) | EncodeIndirectRefKind(kind_);
  }

  static void ConstexprChecks();

  // Extract the table index from an indirect reference.
  ALWAYS_INLINE static uint32_t ExtractIndex(IndirectRef iref) {
    return DecodeIndex(reinterpret_cast<uintptr_t>(iref));
  }

  IndirectRef ToIndirectRef(uint32_t table_index) const {
    DCHECK_LT(table_index, max_entries_);
    uint32_t serial = table_[table_index].GetSerial();
    return reinterpret_cast<IndirectRef>(EncodeIndirectRef(table_index, serial));
  }

  // Abort if check_jni is not enabled. Otherwise, just log as an error.
  static void AbortIfNoCheckJNI(const std::string& msg);

  /* extra debugging checks */
  bool CheckEntry(const char*, IndirectRef, uint32_t) const;

  // Mem map where we store the indirect refs.
  MemMap table_mem_map_;
  // Bottom of the stack. Do not directly access the object references
  // in this as they are roots. Use Get() that has a read barrier.
  IrtEntry* table_;
  // Bit mask, ORed into all irefs.
  const IndirectRefKind kind_;

  // The "top of stack" index where new references are added.
  size_t top_index_;

  // Maximum number of entries allowed.
  size_t max_entries_;

  // Some values to retain old behavior with holes.
  // Description of the algorithm is in the .cc file.
  // TODO: Consider other data structures for compact tables, e.g., free lists.
  size_t current_num_holes_;  // Number of holes in the current / top segment.
};

}  // namespace art

#endif  // ART_RUNTIME_INDIRECT_REFERENCE_TABLE_H_
