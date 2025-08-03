/*
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_ACCOUNTING_SPACE_BITMAP_H_
#define ART_RUNTIME_GC_ACCOUNTING_SPACE_BITMAP_H_

#include <limits.h>
#include <stdint.h>
#include <memory>
#include <set>
#include <vector>

#include "base/locks.h"
#include "base/mem_map.h"
#include "runtime_globals.h"

namespace art HIDDEN {

namespace mirror {
class Class;
class Object;
}  // namespace mirror

namespace gc {
namespace accounting {

template<size_t kAlignment>
class SpaceBitmap {
 public:
  using ScanCallback = void(mirror::Object* obj, void* finger, void* arg);
  using SweepCallback = void(size_t ptr_count, mirror::Object** ptrs, void* arg);

  // Initialize a space bitmap so that it points to a bitmap large enough to cover a heap at
  // heap_begin of heap_capacity bytes, where objects are guaranteed to be kAlignment-aligned.
  EXPORT static SpaceBitmap Create(const std::string& name,
                                   uint8_t* heap_begin,
                                   size_t heap_capacity);

  // Initialize a space bitmap using the provided mem_map as the live bits. Takes ownership of the
  // mem map. The address range covered starts at heap_begin and is of size equal to heap_capacity.
  // Objects are kAlignement-aligned.
  static SpaceBitmap CreateFromMemMap(const std::string& name,
                                      MemMap&& mem_map,
                                      uint8_t* heap_begin,
                                      size_t heap_capacity);

  EXPORT ~SpaceBitmap();

  // Return the bitmap word index corresponding to memory offset (relative to
  // `HeapBegin()`) `offset`.
  // See also SpaceBitmap::OffsetBitIndex.
  //
  // <offset> is the difference from .base to a pointer address.
  // <index> is the index of .bits that contains the bit representing
  //         <offset>.
  static constexpr size_t OffsetToIndex(size_t offset) {
    return offset / kAlignment / kBitsPerIntPtrT;
  }

  // Return the memory offset (relative to `HeapBegin()`) corresponding to
  // bitmap word index `index`.
  template<typename T>
  static constexpr T IndexToOffset(T index) {
    return static_cast<T>(index * kAlignment * kBitsPerIntPtrT);
  }

  // Return the bit within the bitmap word index corresponding to
  // memory offset (relative to `HeapBegin()`) `offset`.
  // See also SpaceBitmap::OffsetToIndex.
  ALWAYS_INLINE static constexpr uintptr_t OffsetBitIndex(uintptr_t offset) {
    return (offset / kAlignment) % kBitsPerIntPtrT;
  }

  // Return the word-wide bit mask corresponding to `OffsetBitIndex(offset)`.
  // Bits are packed in the obvious way.
  static constexpr uintptr_t OffsetToMask(uintptr_t offset) {
    return static_cast<size_t>(1) << OffsetBitIndex(offset);
  }

  // Set the bit corresponding to `obj` in the bitmap and return the previous value of that bit.
  bool Set(const mirror::Object* obj) ALWAYS_INLINE {
    return Modify<true>(obj);
  }

  // Clear the bit corresponding to `obj` in the bitmap and return the previous value of that bit.
  bool Clear(const mirror::Object* obj) ALWAYS_INLINE {
    return Modify<false>(obj);
  }

  // Returns true if the object was previously marked.
  bool AtomicTestAndSet(const mirror::Object* obj);

  // Fill the bitmap with zeroes.  Returns the bitmap's memory to the system as a side-effect.
  // If `release_eagerly` is true, this method will also try to give back the
  // memory to the OS eagerly.
  void Clear(bool release_eagerly = true);

  // Clear a range covered by the bitmap using madvise if possible.
  void ClearRange(const mirror::Object* begin, const mirror::Object* end);

  // Test whether `obj` is part of the bitmap (i.e. return whether the bit
  // corresponding to `obj` has been set in the bitmap).
  //
  // Precondition: `obj` is within the range of pointers that this bitmap could
  // potentially cover (i.e. `this->HasAddress(obj)` is true)
  bool Test(const mirror::Object* obj) const;

  // Return true iff <obj> is within the range of pointers that this bitmap could potentially cover,
  // even if a bit has not been set for it.
  bool HasAddress(const void* obj) const {
    // If obj < heap_begin_ then offset underflows to some very large value past the end of the
    // bitmap.
    const uintptr_t offset = reinterpret_cast<uintptr_t>(obj) - heap_begin_;
    const size_t index = OffsetToIndex(offset);
    return index < bitmap_size_ / sizeof(intptr_t);
  }

  template <typename Visitor>
  void VisitRange(uintptr_t visit_begin, uintptr_t visit_end, const Visitor& visitor) const {
    for (; visit_begin < visit_end; visit_begin += kAlignment) {
      visitor(reinterpret_cast<mirror::Object*>(visit_begin));
    }
  }

  // Find first object while scanning bitmap backwards from visit_begin -> visit_end.
  // Covers [visit_end, visit_begin] range.
  mirror::Object* FindPrecedingObject(uintptr_t visit_begin, uintptr_t visit_end = 0) const;

  // Visit the live objects in the range [visit_begin, visit_end). If kVisitOnce
  // is true, then only the first live object will be visited.
  // TODO: Use lock annotations when clang is fixed.
  // REQUIRES(Locks::heap_bitmap_lock_) REQUIRES_SHARED(Locks::mutator_lock_);
  template <bool kVisitOnce = false, typename Visitor>
  void VisitMarkedRange(uintptr_t visit_begin, uintptr_t visit_end, Visitor&& visitor) const
      NO_THREAD_SAFETY_ANALYSIS;

  // Visit all of the set bits in HeapBegin(), HeapLimit().
  template <typename Visitor>
  void VisitAllMarked(Visitor&& visitor) const {
    VisitMarkedRange(HeapBegin(), HeapLimit(), visitor);
  }

  // Visits set bits in address order.  The callback is not permitted to change the bitmap bits or
  // max during the traversal.
  template <typename Visitor>
  void Walk(Visitor&& visitor)
      REQUIRES_SHARED(Locks::heap_bitmap_lock_, Locks::mutator_lock_);

  // Walk through the bitmaps in increasing address order, and find the object pointers that
  // correspond to garbage objects.  Call <callback> zero or more times with lists of these object
  // pointers. The callback is not permitted to increase the max of either bitmap.
  static void SweepWalk(const SpaceBitmap& live, const SpaceBitmap& mark, uintptr_t base,
                        uintptr_t max, SweepCallback* thunk, void* arg);

  void CopyFrom(SpaceBitmap* source_bitmap);

  // Starting address of our internal storage.
  Atomic<uintptr_t>* Begin() const {
    return bitmap_begin_;
  }

  // Size of our internal storage
  size_t Size() const {
    return bitmap_size_;
  }

  // Size in bytes of the memory that the bitmaps spans.
  uint64_t HeapSize() const {
    return IndexToOffset<uint64_t>(Size() / sizeof(intptr_t));
  }

  void SetHeapSize(size_t bytes) {
    heap_limit_ = heap_begin_ + bytes;
    bitmap_size_ = ComputeBitmapSize(bytes);
    CHECK_EQ(HeapSize(), bytes);
    if (mem_map_.IsValid()) {
      mem_map_.SetSize(bitmap_size_);
    }
  }

  uintptr_t HeapBegin() const {
    return heap_begin_;
  }

  // The maximum address which the bitmap can span. (HeapBegin() <= object < HeapLimit()).
  uint64_t HeapLimit() const {
    return heap_limit_;
  }

  // Set the max address which can covered by the bitmap.
  void SetHeapLimit(uintptr_t new_end);

  std::string GetName() const {
    return name_;
  }

  void SetName(const std::string& name) {
    name_ = name;
  }

  std::string Dump() const;

  // Dump three bitmap words around obj.
  std::string DumpMemAround(mirror::Object* obj) const;

  // Helper function for computing bitmap size based on a 64 bit capacity.
  static size_t ComputeBitmapSize(uint64_t capacity);
  static size_t ComputeHeapSize(uint64_t bitmap_bytes);

  // TODO: heap_end_ is initialized so that the heap bitmap is empty, this doesn't require the -1,
  // however, we document that this is expected on heap_end_

  SpaceBitmap() = default;
  SpaceBitmap(SpaceBitmap&&) noexcept = default;
  SpaceBitmap& operator=(SpaceBitmap&&) noexcept = default;

  bool IsValid() const {
    return bitmap_begin_ != nullptr;
  }

  // Copy a view of the other bitmap without taking ownership of the underlying data.
  void CopyView(SpaceBitmap& other) {
    bitmap_begin_ = other.bitmap_begin_;
    bitmap_size_ = other.bitmap_size_;
    heap_begin_ = other.heap_begin_;
    heap_limit_ = other.heap_limit_;
    name_ = other.name_;
  }

 private:
  // TODO: heap_end_ is initialized so that the heap bitmap is empty, this doesn't require the -1,
  // however, we document that this is expected on heap_end_
  SpaceBitmap(const std::string& name,
              MemMap&& mem_map,
              uintptr_t* bitmap_begin,
              size_t bitmap_size,
              const void* heap_begin,
              size_t heap_capacity);

  // Change the value of the bit corresponding to `obj` in the bitmap
  // to `kSetBit` and return the previous value of that bit.
  template<bool kSetBit>
  bool Modify(const mirror::Object* obj);

  // Backing storage for bitmap.
  MemMap mem_map_;

  // This bitmap itself, word sized for efficiency in scanning.
  Atomic<uintptr_t>* bitmap_begin_ = nullptr;

  // Size of this bitmap.
  size_t bitmap_size_ = 0u;

  // The start address of the memory covered by the bitmap, which corresponds to the word
  // containing the first bit in the bitmap.
  uintptr_t heap_begin_ = 0u;

  // The end address of the memory covered by the bitmap. This may not be on a word boundary.
  uintptr_t heap_limit_ = 0u;

  // Name of this bitmap.
  std::string name_;
};

using ContinuousSpaceBitmap = SpaceBitmap<kObjectAlignment>;

// We pick the lowest supported page size to ensure that it's a constexpr, so
// that we can keep bitmap accesses optimized. However, this means that when the
// large-object alignment is higher than kMinPageSize, then not all bits in the
// bitmap are actually in use.
// In practice, this happens when running with a kernel that uses 16kB as the
// page size, where 1 out of every 4 bits of the bitmap is used.

// TODO: In the future, we should consider alternative fixed alignments for
// large objects, disassociated from the page size. This would allow us to keep
// accesses optimized, while also packing the bitmap efficiently, and reducing
// its size enough that it would no longer make sense to allocate it with
// mmap().
using LargeObjectBitmap = SpaceBitmap<kMinPageSize>;

template<size_t kAlignment>
std::ostream& operator << (std::ostream& stream, const SpaceBitmap<kAlignment>& bitmap);

}  // namespace accounting
}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_ACCOUNTING_SPACE_BITMAP_H_
