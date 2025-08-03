/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_COLLECTOR_MARK_COMPACT_H_
#define ART_RUNTIME_GC_COLLECTOR_MARK_COMPACT_H_

#include <signal.h>

#include <map>
#include <memory>
#include <unordered_map>
#include <unordered_set>

#include "barrier.h"
#include "base/atomic.h"
#include "base/gc_visited_arena_pool.h"
#include "base/macros.h"
#include "base/mutex.h"
#include "garbage_collector.h"
#include "gc/accounting/atomic_stack.h"
#include "gc/accounting/bitmap-inl.h"
#include "gc/accounting/heap_bitmap.h"
#include "gc_root.h"
#include "immune_spaces.h"
#include "offsets.h"

namespace art HIDDEN {

EXPORT bool KernelSupportsUffd();

namespace mirror {
class DexCache;
}  // namespace mirror

namespace gc {

class Heap;

namespace space {
class BumpPointerSpace;
}  // namespace space

namespace collector {
class MarkCompact final : public GarbageCollector {
 public:
  using SigbusCounterType = uint32_t;

  static constexpr size_t kAlignment = kObjectAlignment;
  static constexpr int kCopyMode = -1;
  static constexpr int kMinorFaultMode = -2;
  // Fake file descriptor for fall back mode (when uffd isn't available)
  static constexpr int kFallbackMode = -3;
  static constexpr int kFdSharedAnon = -1;
  static constexpr int kFdUnused = -2;

  // Bitmask for the compaction-done bit in the sigbus_in_progress_count_.
  static constexpr SigbusCounterType kSigbusCounterCompactionDoneMask =
      1u << (BitSizeOf<SigbusCounterType>() - 1);

  explicit MarkCompact(Heap* heap);

  ~MarkCompact() {}

  void RunPhases() override REQUIRES(!Locks::mutator_lock_, !lock_);

  void ClampGrowthLimit(size_t new_capacity) REQUIRES(Locks::heap_bitmap_lock_);
  // Updated before (or in) pre-compaction pause and is accessed only in the
  // pause or during concurrent compaction. The flag is reset in next GC cycle's
  // InitializePhase(). Therefore, it's safe to update without any memory ordering.
  bool IsCompacting() const { return compacting_; }

  bool IsUsingSigbusFeature() const { return use_uffd_sigbus_; }

  // Called by SIGBUS handler. NO_THREAD_SAFETY_ANALYSIS for mutator-lock, which
  // is asserted in the function.
  bool SigbusHandler(siginfo_t* info) REQUIRES(!lock_) NO_THREAD_SAFETY_ANALYSIS;

  GcType GetGcType() const override {
    return kGcTypeFull;
  }

  CollectorType GetCollectorType() const override {
    return kCollectorTypeCMC;
  }

  Barrier& GetBarrier() {
    return gc_barrier_;
  }

  mirror::Object* MarkObject(mirror::Object* obj) override
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  void MarkHeapReference(mirror::HeapReference<mirror::Object>* obj,
                         bool do_atomic_update) override
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  void VisitRoots(mirror::Object*** roots,
                  size_t count,
                  const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  void VisitRoots(mirror::CompressedReference<mirror::Object>** roots,
                  size_t count,
                  const RootInfo& info) override
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  bool IsNullOrMarkedHeapReference(mirror::HeapReference<mirror::Object>* obj,
                                   bool do_atomic_update) override
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  void RevokeAllThreadLocalBuffers() override;

  void DelayReferenceReferent(ObjPtr<mirror::Class> klass,
                              ObjPtr<mirror::Reference> reference) override
      REQUIRES_SHARED(Locks::mutator_lock_, Locks::heap_bitmap_lock_);

  mirror::Object* IsMarked(mirror::Object* obj) override
      REQUIRES_SHARED(Locks::mutator_lock_, Locks::heap_bitmap_lock_);

  mirror::Object* GetFromSpaceAddrFromBarrier(mirror::Object* old_ref) {
    CHECK(compacting_);
    if (HasAddress(old_ref)) {
      return GetFromSpaceAddr(old_ref);
    }
    return old_ref;
  }
  // Called from Heap::PostForkChildAction() for non-zygote processes and from
  // PrepareForCompaction() for zygote processes. Returns true if uffd was
  // created or was already done.
  bool CreateUserfaultfd(bool post_fork);

  // Returns a pair indicating if userfaultfd itself is available (first) and if
  // so then whether its minor-fault feature is available or not (second).
  static std::pair<bool, bool> GetUffdAndMinorFault();

  // Add linear-alloc space data when a new space is added to
  // GcVisitedArenaPool, which mostly happens only once.
  void AddLinearAllocSpaceData(uint8_t* begin, size_t len);

  // In copy-mode of userfaultfd, we don't need to reach a 'processed' state as
  // it's given that processing thread also copies the page, thereby mapping it.
  // The order is important as we may treat them as integers.
  enum class PageState : uint8_t {
    kUnprocessed = 0,           // Not processed yet
    kProcessing = 1,            // Being processed by GC thread and will not be mapped
    kProcessed = 2,             // Processed but not mapped
    kProcessingAndMapping = 3,  // Being processed by GC or mutator and will be mapped
    kMutatorProcessing = 4,     // Being processed by mutator thread
    kProcessedAndMapping = 5,   // Processed and will be mapped
    kProcessedAndMapped = 6     // Processed and mapped. For SIGBUS.
  };

  // Different heap clamping states.
  enum class ClampInfoStatus : uint8_t {
    kClampInfoNotDone,
    kClampInfoPending,
    kClampInfoFinished
  };

 private:
  using ObjReference = mirror::CompressedReference<mirror::Object>;
  // Number of bits (live-words) covered by a single chunk-info (below)
  // entry/word.
  // TODO: Since popcount is performed usomg SIMD instructions, we should
  // consider using 128-bit in order to halve the chunk-info size.
  static constexpr uint32_t kBitsPerVectorWord = kBitsPerIntPtrT;
  static constexpr uint32_t kOffsetChunkSize = kBitsPerVectorWord * kAlignment;
  static_assert(kOffsetChunkSize < kMinPageSize);
  // Bitmap with bits corresponding to every live word set. For an object
  // which is 4 words in size will have the corresponding 4 bits set. This is
  // required for efficient computation of new-address (post-compaction) from
  // the given old-address (pre-compaction).
  template <size_t kAlignment>
  class LiveWordsBitmap : private accounting::MemoryRangeBitmap<kAlignment> {
    using Bitmap = accounting::Bitmap;
    using MemRangeBitmap = accounting::MemoryRangeBitmap<kAlignment>;

   public:
    static_assert(IsPowerOfTwo(kBitsPerVectorWord));
    static_assert(IsPowerOfTwo(Bitmap::kBitsPerBitmapWord));
    static_assert(kBitsPerVectorWord >= Bitmap::kBitsPerBitmapWord);
    static constexpr uint32_t kBitmapWordsPerVectorWord =
            kBitsPerVectorWord / Bitmap::kBitsPerBitmapWord;
    static_assert(IsPowerOfTwo(kBitmapWordsPerVectorWord));
    using MemRangeBitmap::SetBitmapSize;
    static LiveWordsBitmap* Create(uintptr_t begin, uintptr_t end);

    // Return offset (within the indexed chunk-info) of the nth live word.
    uint32_t FindNthLiveWordOffset(size_t chunk_idx, uint32_t n) const;
    // Sets all bits in the bitmap corresponding to the given range. Also
    // returns the bit-index of the first word.
    ALWAYS_INLINE uintptr_t SetLiveWords(uintptr_t begin, size_t size);
    // Count number of live words upto the given bit-index. This is to be used
    // to compute the post-compact address of an old reference.
    ALWAYS_INLINE size_t CountLiveWordsUpto(size_t bit_idx) const;
    // Call 'visitor' for every stride of contiguous marked bits in the live-words
    // bitmap, starting from begin_bit_idx. Only visit 'bytes' live bytes or
    // until 'end', whichever comes first.
    // Visitor is called with index of the first marked bit in the stride,
    // stride size and whether it's the last stride in the given range or not.
    template <typename Visitor>
    ALWAYS_INLINE void VisitLiveStrides(uintptr_t begin_bit_idx,
                                        uint8_t* end,
                                        const size_t bytes,
                                        Visitor&& visitor) const
        REQUIRES_SHARED(Locks::mutator_lock_);
    // Count the number of live bytes in the given vector entry.
    size_t LiveBytesInBitmapWord(size_t chunk_idx) const;
    void ClearBitmap() { Bitmap::Clear(); }
    ALWAYS_INLINE uintptr_t Begin() const { return MemRangeBitmap::CoverBegin(); }
    ALWAYS_INLINE bool HasAddress(mirror::Object* obj) const {
      return MemRangeBitmap::HasAddress(reinterpret_cast<uintptr_t>(obj));
    }
    ALWAYS_INLINE bool Test(uintptr_t bit_index) const {
      return Bitmap::TestBit(bit_index);
    }
    ALWAYS_INLINE bool Test(mirror::Object* obj) const {
      return MemRangeBitmap::Test(reinterpret_cast<uintptr_t>(obj));
    }
    ALWAYS_INLINE uintptr_t GetWord(size_t index) const {
      static_assert(kBitmapWordsPerVectorWord == 1);
      return Bitmap::Begin()[index * kBitmapWordsPerVectorWord];
    }
  };

  static bool HasAddress(mirror::Object* obj, uint8_t* begin, uint8_t* end) {
    uint8_t* ptr = reinterpret_cast<uint8_t*>(obj);
    return ptr >= begin && ptr < end;
  }

  bool HasAddress(mirror::Object* obj) const {
    return HasAddress(obj, moving_space_begin_, moving_space_end_);
  }
  // For a given object address in pre-compact space, return the corresponding
  // address in the from-space, where heap pages are relocated in the compaction
  // pause.
  mirror::Object* GetFromSpaceAddr(mirror::Object* obj) const {
    DCHECK(HasAddress(obj)) << " obj=" << obj;
    return reinterpret_cast<mirror::Object*>(reinterpret_cast<uintptr_t>(obj)
                                             + from_space_slide_diff_);
  }

  // Verifies that that given object reference refers to a valid object.
  // Otherwise fataly dumps logs, including those from callback.
  template <typename Callback>
  void VerifyObject(mirror::Object* ref, Callback& callback) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Check if the obj is within heap and has a klass which is likely to be valid
  // mirror::Class.
  bool IsValidObject(mirror::Object* obj) const REQUIRES_SHARED(Locks::mutator_lock_);
  void InitializePhase();
  void FinishPhase() REQUIRES(!Locks::mutator_lock_, !Locks::heap_bitmap_lock_, !lock_);
  void MarkingPhase() REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Locks::heap_bitmap_lock_);
  void CompactionPhase() REQUIRES_SHARED(Locks::mutator_lock_);

  void SweepSystemWeaks(Thread* self, Runtime* runtime, const bool paused)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::heap_bitmap_lock_);
  // Update the reference at given offset in the given object with post-compact
  // address. [begin, end) is moving-space range.
  ALWAYS_INLINE void UpdateRef(mirror::Object* obj,
                               MemberOffset offset,
                               uint8_t* begin,
                               uint8_t* end) REQUIRES_SHARED(Locks::mutator_lock_);

  // Verify that the gc-root is updated only once. Returns false if the update
  // shouldn't be done.
  ALWAYS_INLINE bool VerifyRootSingleUpdate(void* root,
                                            mirror::Object* old_ref,
                                            const RootInfo& info)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Update the given root with post-compact address. [begin, end) is
  // moving-space range.
  ALWAYS_INLINE void UpdateRoot(mirror::CompressedReference<mirror::Object>* root,
                                uint8_t* begin,
                                uint8_t* end,
                                const RootInfo& info = RootInfo(RootType::kRootUnknown))
      REQUIRES_SHARED(Locks::mutator_lock_);
  ALWAYS_INLINE void UpdateRoot(mirror::Object** root,
                                uint8_t* begin,
                                uint8_t* end,
                                const RootInfo& info = RootInfo(RootType::kRootUnknown))
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Given the pre-compact address, the function returns the post-compact
  // address of the given object. [begin, end) is moving-space range.
  ALWAYS_INLINE mirror::Object* PostCompactAddress(mirror::Object* old_ref,
                                                   uint8_t* begin,
                                                   uint8_t* end) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Compute post-compact address of an object in moving space. This function
  // assumes that old_ref is in moving space.
  ALWAYS_INLINE mirror::Object* PostCompactAddressUnchecked(mirror::Object* old_ref) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Compute the new address for an object which was allocated prior to starting
  // this GC cycle.
  ALWAYS_INLINE mirror::Object* PostCompactOldObjAddr(mirror::Object* old_ref) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Compute the new address for an object which was black allocated during this
  // GC cycle.
  ALWAYS_INLINE mirror::Object* PostCompactBlackObjAddr(mirror::Object* old_ref) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Clears (for alloc spaces in the beginning of marking phase) or ages the
  // card table. Also, identifies immune spaces and mark bitmap.
  void PrepareCardTableForMarking(bool clear_alloc_space_cards)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_);

  // Perform one last round of marking, identifying roots from dirty cards
  // during a stop-the-world (STW) pause.
  void MarkingPause() REQUIRES(Locks::mutator_lock_, !Locks::heap_bitmap_lock_);
  // Perform stop-the-world pause prior to concurrent compaction.
  // Updates GC-roots and protects heap so that during the concurrent
  // compaction phase we can receive faults and compact the corresponding pages
  // on the fly.
  void CompactionPause() REQUIRES(Locks::mutator_lock_);
  // Compute offsets (in chunk_info_vec_) and other data structures required
  // during concurrent compaction.
  void PrepareForCompaction() REQUIRES_SHARED(Locks::mutator_lock_);

  // Copy gPageSize live bytes starting from 'offset' (within the moving space),
  // which must be within 'obj', into the gPageSize sized memory pointed by 'addr'.
  // Then update the references within the copied objects. The boundary objects are
  // partially updated such that only the references that lie in the page are updated.
  // This is necessary to avoid cascading userfaults.
  void CompactPage(mirror::Object* obj, uint32_t offset, uint8_t* addr, bool needs_memset_zero)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Compact the bump-pointer space. Pass page that should be used as buffer for
  // userfaultfd.
  template <int kMode>
  void CompactMovingSpace(uint8_t* page) REQUIRES_SHARED(Locks::mutator_lock_);

  // Compact the given page as per func and change its state. Also map/copy the
  // page, if required.
  template <int kMode, typename CompactionFn>
  ALWAYS_INLINE void DoPageCompactionWithStateChange(size_t page_idx,
                                                     size_t status_arr_len,
                                                     uint8_t* to_space_page,
                                                     uint8_t* page,
                                                     CompactionFn func)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Update all the objects in the given non-moving space page. 'first' object
  // could have started in some preceding page.
  void UpdateNonMovingPage(mirror::Object* first, uint8_t* page)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Update all the references in the non-moving space.
  void UpdateNonMovingSpace() REQUIRES_SHARED(Locks::mutator_lock_);

  // For all the pages in non-moving space, find the first object that overlaps
  // with the pages' start address, and store in first_objs_non_moving_space_ array.
  void InitNonMovingSpaceFirstObjects() REQUIRES_SHARED(Locks::mutator_lock_);
  // In addition to the first-objects for every post-compact moving space page,
  // also find offsets within those objects from where the contents should be
  // copied to the page. The offsets are relative to the moving-space's
  // beginning. Store the computed first-object and offset in first_objs_moving_space_
  // and pre_compact_offset_moving_space_ respectively.
  void InitMovingSpaceFirstObjects(const size_t vec_len) REQUIRES_SHARED(Locks::mutator_lock_);

  // Gather the info related to black allocations from bump-pointer space to
  // enable concurrent sliding of these pages.
  void UpdateMovingSpaceBlackAllocations() REQUIRES(Locks::mutator_lock_, Locks::heap_bitmap_lock_);
  // Update first-object info from allocation-stack for non-moving space black
  // allocations.
  void UpdateNonMovingSpaceBlackAllocations() REQUIRES(Locks::mutator_lock_, Locks::heap_bitmap_lock_);

  // Slides (retain the empty holes, which are usually part of some in-use TLAB)
  // black page in the moving space. 'first_obj' is the object that overlaps with
  // the first byte of the page being slid. pre_compact_page is the pre-compact
  // address of the page being slid. 'dest' is the gPageSize sized memory where
  // the contents would be copied.
  void SlideBlackPage(mirror::Object* first_obj,
                      mirror::Object* next_page_first_obj,
                      uint32_t first_chunk_size,
                      uint8_t* const pre_compact_page,
                      uint8_t* dest,
                      bool needs_memset_zero) REQUIRES_SHARED(Locks::mutator_lock_);

  // Perform reference-processing and the likes before sweeping the non-movable
  // spaces.
  void ReclaimPhase() REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Locks::heap_bitmap_lock_);

  // Mark GC-roots (except from immune spaces and thread-stacks) during a STW pause.
  void ReMarkRoots(Runtime* runtime) REQUIRES(Locks::mutator_lock_, Locks::heap_bitmap_lock_);
  // Concurrently mark GC-roots, except from immune spaces.
  void MarkRoots(VisitRootFlags flags) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Collect thread stack roots via a checkpoint.
  void MarkRootsCheckpoint(Thread* self, Runtime* runtime) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Second round of concurrent marking. Mark all gray objects that got dirtied
  // since the first round.
  void PreCleanCards() REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_);

  void MarkNonThreadRoots(Runtime* runtime) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  void MarkConcurrentRoots(VisitRootFlags flags, Runtime* runtime)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_);

  // Traverse through the reachable objects and mark them.
  void MarkReachableObjects() REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Scan (only) immune spaces looking for references into the garbage collected
  // spaces.
  void UpdateAndMarkModUnion() REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Scan mod-union and card tables, covering all the spaces, to identify dirty objects.
  // These are in 'minimum age' cards, which is 'kCardAged' in case of concurrent (second round)
  // marking and kCardDirty during the STW pause.
  void ScanDirtyObjects(bool paused, uint8_t minimum_age) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Recursively mark dirty objects. Invoked both concurrently as well in a STW
  // pause in PausePhase().
  void RecursiveMarkDirtyObjects(bool paused, uint8_t minimum_age)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Go through all the objects in the mark-stack until it's empty.
  void ProcessMarkStack() override REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  void ExpandMarkStack() REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  // Scan object for references. If kUpdateLivewords is true then set bits in
  // the live-words bitmap and add size to chunk-info.
  template <bool kUpdateLiveWords>
  void ScanObject(mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  // Push objects to the mark-stack right after successfully marking objects.
  void PushOnMarkStack(mirror::Object* obj)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  // Update the live-words bitmap as well as add the object size to the
  // chunk-info vector. Both are required for computation of post-compact addresses.
  // Also updates freed_objects_ counter.
  void UpdateLivenessInfo(mirror::Object* obj, size_t obj_size)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void ProcessReferences(Thread* self)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::heap_bitmap_lock_);

  void MarkObjectNonNull(mirror::Object* obj,
                         mirror::Object* holder = nullptr,
                         MemberOffset offset = MemberOffset(0))
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  void MarkObject(mirror::Object* obj, mirror::Object* holder, MemberOffset offset)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  template <bool kParallel>
  bool MarkObjectNonNullNoPush(mirror::Object* obj,
                               mirror::Object* holder = nullptr,
                               MemberOffset offset = MemberOffset(0))
      REQUIRES(Locks::heap_bitmap_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void Sweep(bool swap_bitmaps) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);
  void SweepLargeObjects(bool swap_bitmaps) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  // Perform all kernel operations required for concurrent compaction. Includes
  // mremap to move pre-compact pages to from-space, followed by userfaultfd
  // registration on the moving space and linear-alloc.
  void KernelPreparation();
  // Called by KernelPreparation() for every memory range being prepared for
  // userfaultfd registration.
  void KernelPrepareRangeForUffd(uint8_t* to_addr,
                                 uint8_t* from_addr,
                                 size_t map_size,
                                 int fd,
                                 uint8_t* shadow_addr = nullptr);

  void RegisterUffd(void* addr, size_t size, int mode);
  void UnregisterUffd(uint8_t* start, size_t len);

  // Called by thread-pool workers to read uffd_ and process fault events.
  template <int kMode>
  void ConcurrentCompaction(uint8_t* buf) REQUIRES_SHARED(Locks::mutator_lock_);
  // Called by thread-pool workers to compact and copy/map the fault page in
  // moving space.
  template <int kMode>
  void ConcurrentlyProcessMovingPage(uint8_t* fault_page,
                                     uint8_t* buf,
                                     size_t nr_moving_space_used_pages)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // Called by thread-pool workers to process and copy/map the fault page in
  // linear-alloc.
  template <int kMode>
  void ConcurrentlyProcessLinearAllocPage(uint8_t* fault_page, bool is_minor_fault)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Process concurrently all the pages in linear-alloc. Called by gc-thread.
  void ProcessLinearAlloc() REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns true if the moving space can be compacted using uffd's minor-fault
  // feature.
  bool CanCompactMovingSpaceWithMinorFault();

  void FreeFromSpacePages(size_t cur_page_idx, int mode) REQUIRES_SHARED(Locks::mutator_lock_);

  // Maps processed pages (from moving space and linear-alloc) for uffd's
  // minor-fault feature. We try to 'claim' all processed (and unmapped) pages
  // contiguous to 'to_space_start'.
  // kFirstPageMapping indicates if the first page is already claimed or not. It
  // also indicates that the ioctl must succeed in mapping the first page.
  template <bool kFirstPageMapping>
  void MapProcessedPages(uint8_t* to_space_start,
                         Atomic<PageState>* state_arr,
                         size_t arr_idx,
                         size_t arr_len) REQUIRES_SHARED(Locks::mutator_lock_);

  bool IsValidFd(int fd) const { return fd >= 0; }
  // Add/update <class, obj> pair if class > obj and obj is the lowest address
  // object of class.
  ALWAYS_INLINE void UpdateClassAfterObjectMap(mirror::Object* obj)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Updates 'class_after_obj_map_' map by updating the keys (class) with its
  // highest-address super-class (obtained from 'super_class_after_class_map_'),
  // if there is any. This is to ensure we don't free from-space pages before
  // the lowest-address obj is compacted.
  void UpdateClassAfterObjMap();

  void MarkZygoteLargeObjects() REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::heap_bitmap_lock_);

  void ZeropageIoctl(void* addr, bool tolerate_eexist, bool tolerate_enoent);
  void CopyIoctl(void* dst, void* buffer);
  // Called after updating a linear-alloc page to either map a zero-page if the
  // page wasn't touched during updation, or map the page via copy-ioctl. And
  // then updates the page's state to indicate the page is mapped.
  void MapUpdatedLinearAllocPage(uint8_t* page,
                                 uint8_t* shadow_page,
                                 Atomic<PageState>& state,
                                 bool page_touched);
  // Called for clamping of 'info_map_' and other GC data structures, which are
  // small and/or in >4GB address space. There is no real benefit of clamping
  // them synchronously during app forking. It clamps only if clamp_info_map_status_
  // is set to kClampInfoPending, which is done by ClampGrowthLimit().
  void MaybeClampGcStructures() REQUIRES(Locks::heap_bitmap_lock_);
  // Initialize all the info-map related fields of this GC. Returns total size
  // of all the structures in info-map.
  size_t InitializeInfoMap(uint8_t* p, size_t moving_space_sz);
  // Update class-table classes in compaction pause if we are running in debuggable
  // mode. Only visit class-table in image spaces if 'immune_class_table_only'
  // is true.
  void UpdateClassTableClasses(Runtime* runtime, bool immune_class_table_only)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // For checkpoints
  Barrier gc_barrier_;
  // Every object inside the immune spaces is assumed to be marked.
  ImmuneSpaces immune_spaces_;
  // Required only when mark-stack is accessed in shared mode, which happens
  // when collecting thread-stack roots using checkpoint. Otherwise, we use it
  // to synchronize on updated_roots_ in debug-builds.
  Mutex lock_;
  accounting::ObjectStack* mark_stack_;
  // Special bitmap wherein all the bits corresponding to an object are set.
  // TODO: make LiveWordsBitmap encapsulated in this class rather than a
  // pointer. We tend to access its members in performance-sensitive
  // code-path. Also, use a single MemMap for all the GC's data structures,
  // which we will clear in the end. This would help in limiting the number of
  // VMAs that get created in the kernel.
  std::unique_ptr<LiveWordsBitmap<kAlignment>> live_words_bitmap_;
  // Track GC-roots updated so far in a GC-cycle. This is to confirm that no
  // GC-root is updated twice.
  // TODO: Must be replaced with an efficient mechanism eventually. Or ensure
  // that double updation doesn't happen in the first place.
  std::unique_ptr<std::unordered_set<void*>> updated_roots_ GUARDED_BY(lock_);
  MemMap from_space_map_;
  MemMap shadow_to_space_map_;
  // Any array of live-bytes in logical chunks of kOffsetChunkSize size
  // in the 'to-be-compacted' space.
  MemMap info_map_;
  // Set of page-sized buffers used for compaction. The first page is used by
  // the GC thread. Subdequent pages are used by mutator threads in case of
  // SIGBUS feature, and by uffd-worker threads otherwise. In the latter case
  // the first page is also used for termination of concurrent compaction by
  // making worker threads terminate the userfaultfd read loop.
  MemMap compaction_buffers_map_;

  class LessByArenaAddr {
   public:
    bool operator()(const TrackedArena* a, const TrackedArena* b) const {
      return std::less<uint8_t*>{}(a->Begin(), b->Begin());
    }
  };

  // Map of arenas allocated in LinearAlloc arena-pool and last non-zero page,
  // captured during compaction pause for concurrent updates.
  std::map<const TrackedArena*, uint8_t*, LessByArenaAddr> linear_alloc_arenas_;
  // Set of PageStatus arrays, one per arena-pool space. It's extremely rare to
  // have more than one, but this is to be ready for the worst case.
  class LinearAllocSpaceData {
   public:
    LinearAllocSpaceData(MemMap&& shadow,
                         MemMap&& page_status_map,
                         uint8_t* begin,
                         uint8_t* end,
                         bool already_shared)
        : shadow_(std::move(shadow)),
          page_status_map_(std::move(page_status_map)),
          begin_(begin),
          end_(end),
          already_shared_(already_shared) {}

    MemMap shadow_;
    MemMap page_status_map_;
    uint8_t* begin_;
    uint8_t* end_;
    // Indicates if the linear-alloc is already MAP_SHARED.
    bool already_shared_;
  };

  std::vector<LinearAllocSpaceData> linear_alloc_spaces_data_;

  class ObjReferenceHash {
   public:
    uint32_t operator()(const ObjReference& ref) const {
      return ref.AsVRegValue() >> kObjectAlignmentShift;
    }
  };

  class ObjReferenceEqualFn {
   public:
    bool operator()(const ObjReference& a, const ObjReference& b) const {
      return a.AsMirrorPtr() == b.AsMirrorPtr();
    }
  };

  class LessByObjReference {
   public:
    bool operator()(const ObjReference& a, const ObjReference& b) const {
      return std::less<mirror::Object*>{}(a.AsMirrorPtr(), b.AsMirrorPtr());
    }
  };

  // Data structures used to track objects whose layout information is stored in later
  // allocated classes (at higher addresses). We must be careful not to free the
  // corresponding from-space pages prematurely.
  using ObjObjOrderedMap = std::map<ObjReference, ObjReference, LessByObjReference>;
  using ObjObjUnorderedMap =
      std::unordered_map<ObjReference, ObjReference, ObjReferenceHash, ObjReferenceEqualFn>;
  // Unordered map of <K, S> such that the class K (in moving space) has kClassWalkSuper
  // in reference bitmap and S is its highest address super class.
  ObjObjUnorderedMap super_class_after_class_hash_map_;
  // Unordered map of <K, V> such that the class K (in moving space) is after its objects
  // or would require iterating super-class hierarchy when visiting references. And V is
  // its lowest address object (in moving space).
  ObjObjUnorderedMap class_after_obj_hash_map_;
  // Ordered map constructed before starting compaction using the above two maps. Key is a
  // class (or super-class) which is higher in address order than some of its object(s) and
  // value is the corresponding object with lowest address.
  ObjObjOrderedMap class_after_obj_ordered_map_;
  // Since the compaction is done in reverse, we use a reverse iterator. It is maintained
  // either at the pair whose class is lower than the first page to be freed, or at the
  // pair whose object is not yet compacted.
  ObjObjOrderedMap::const_reverse_iterator class_after_obj_iter_;
  // Cached reference to the last class which has kClassWalkSuper in reference
  // bitmap but has all its super classes lower address order than itself.
  mirror::Class* walk_super_class_cache_;
  // Used by FreeFromSpacePages() for maintaining markers in the moving space for
  // how far the pages have been reclaimed/checked.
  size_t last_checked_reclaim_page_idx_;
  uint8_t* last_reclaimed_page_;

  space::ContinuousSpace* non_moving_space_;
  space::BumpPointerSpace* const bump_pointer_space_;
  // The main space bitmap
  accounting::ContinuousSpaceBitmap* const moving_space_bitmap_;
  accounting::ContinuousSpaceBitmap* non_moving_space_bitmap_;
  Thread* thread_running_gc_;
  // Array of moving-space's pages' compaction status.
  Atomic<PageState>* moving_pages_status_;
  size_t vector_length_;
  size_t live_stack_freeze_size_;

  uint64_t bytes_scanned_;

  // For every page in the to-space (post-compact heap) we need to know the
  // first object from which we must compact and/or update references. This is
  // for both non-moving and moving space. Additionally, for the moving-space,
  // we also need the offset within the object from where we need to start
  // copying.
  // chunk_info_vec_ holds live bytes for chunks during marking phase. After
  // marking we perform an exclusive scan to compute offset for every chunk.
  uint32_t* chunk_info_vec_;
  // For pages before black allocations, pre_compact_offset_moving_space_[i]
  // holds offset within the space from where the objects need to be copied in
  // the ith post-compact page.
  // Otherwise, black_alloc_pages_first_chunk_size_[i] holds the size of first
  // non-empty chunk in the ith black-allocations page.
  union {
    uint32_t* pre_compact_offset_moving_space_;
    uint32_t* black_alloc_pages_first_chunk_size_;
  };
  // first_objs_moving_space_[i] is the pre-compact address of the object which
  // would overlap with the starting boundary of the ith post-compact page.
  ObjReference* first_objs_moving_space_;
  // First object for every page. It could be greater than the page's start
  // address, or null if the page is empty.
  ObjReference* first_objs_non_moving_space_;
  size_t non_moving_first_objs_count_;
  // Length of first_objs_moving_space_ and pre_compact_offset_moving_space_
  // arrays. Also the number of pages which are to be compacted.
  size_t moving_first_objs_count_;
  // Number of pages containing black-allocated objects, indicating number of
  // pages to be slid.
  size_t black_page_count_;

  uint8_t* from_space_begin_;
  // Cached values of moving-space range to optimize checking if reference
  // belongs to moving-space or not. May get updated if and when heap is
  // clamped.
  uint8_t* const moving_space_begin_;
  uint8_t* moving_space_end_;
  // moving-space's end pointer at the marking pause. All allocations beyond
  // this will be considered black in the current GC cycle. Aligned up to page
  // size.
  uint8_t* black_allocations_begin_;
  // End of compacted space. Use for computing post-compact addr of black
  // allocated objects. Aligned up to page size.
  uint8_t* post_compact_end_;
  // Cache (black_allocations_begin_ - post_compact_end_) for post-compact
  // address computations.
  ptrdiff_t black_objs_slide_diff_;
  // Cache (from_space_begin_ - bump_pointer_space_->Begin()) so that we can
  // compute from-space address of a given pre-comapct addr efficiently.
  ptrdiff_t from_space_slide_diff_;

  // TODO: Remove once an efficient mechanism to deal with double root updation
  // is incorporated.
  void* stack_high_addr_;
  void* stack_low_addr_;

  uint8_t* conc_compaction_termination_page_;

  PointerSize pointer_size_;
  // Number of objects freed during this GC in moving space. It is decremented
  // every time an object is discovered. And total-object count is added to it
  // in MarkingPause(). It reaches the correct count only once the marking phase
  // is completed.
  int32_t freed_objects_;
  // memfds for moving space for using userfaultfd's minor-fault feature.
  // Initialized to kFdUnused to indicate that mmap should be MAP_PRIVATE in
  // KernelPrepareRange().
  int moving_to_space_fd_;
  int moving_from_space_fd_;
  // Userfault file descriptor, accessed only by the GC itself.
  // kFallbackMode value indicates that we are in the fallback mode.
  int uffd_;
  // Number of mutator-threads currently executing SIGBUS handler. When the
  // GC-thread is done with compaction, it set the most significant bit to
  // indicate that. Mutator threads check for the flag when incrementing in the
  // handler.
  std::atomic<SigbusCounterType> sigbus_in_progress_count_;
  // Number of mutator-threads/uffd-workers working on moving-space page. It
  // must be 0 before gc-thread can unregister the space after it's done
  // sequentially compacting all pages of the space.
  std::atomic<uint16_t> compaction_in_progress_count_;
  // When using SIGBUS feature, this counter is used by mutators to claim a page
  // out of compaction buffers to be used for the entire compaction cycle.
  std::atomic<uint16_t> compaction_buffer_counter_;
  // Used to exit from compaction loop at the end of concurrent compaction
  uint8_t thread_pool_counter_;
  // True while compacting.
  bool compacting_;
  // Flag indicating whether one-time uffd initialization has been done. It will
  // be false on the first GC for non-zygote processes, and always for zygote.
  // Its purpose is to minimize the userfaultfd overhead to the minimal in
  // Heap::PostForkChildAction() as it's invoked in app startup path. With
  // this, we register the compaction-termination page on the first GC.
  bool uffd_initialized_;
  // Flag indicating if userfaultfd supports minor-faults. Set appropriately in
  // CreateUserfaultfd(), where we get this information from the kernel.
  const bool uffd_minor_fault_supported_;
  // Flag indicating if we should use sigbus signals instead of threads to
  // handle userfaults.
  const bool use_uffd_sigbus_;
  // For non-zygote processes this flag indicates if the spaces are ready to
  // start using userfaultfd's minor-fault feature. This initialization involves
  // starting to use shmem (memfd_create) for the userfaultfd protected spaces.
  bool minor_fault_initialized_;
  // Set to true when linear-alloc can start mapping with MAP_SHARED. Set on
  // non-zygote processes during first GC, which sets up everyting for using
  // minor-fault from next GC.
  bool map_linear_alloc_shared_;
  // Clamping statue of `info_map_`. Initialized with 'NotDone'. Once heap is
  // clamped but info_map_ is delayed, we set it to 'Pending'. Once 'info_map_'
  // is also clamped, then we set it to 'Finished'.
  ClampInfoStatus clamp_info_map_status_;

  class FlipCallback;
  class ThreadFlipVisitor;
  class VerifyRootMarkedVisitor;
  class ScanObjectVisitor;
  class CheckpointMarkThreadRoots;
  template <size_t kBufferSize>
  class ThreadRootsVisitor;
  class RefFieldsVisitor;
  template <bool kCheckBegin, bool kCheckEnd> class RefsUpdateVisitor;
  class ArenaPoolPageUpdater;
  class ClassLoaderRootsUpdater;
  class LinearAllocPageUpdater;
  class ImmuneSpaceUpdateObjVisitor;
  class ConcurrentCompactionGcTask;

  DISALLOW_IMPLICIT_CONSTRUCTORS(MarkCompact);
};

std::ostream& operator<<(std::ostream& os, MarkCompact::PageState value);
std::ostream& operator<<(std::ostream& os, MarkCompact::ClampInfoStatus value);

}  // namespace collector
}  // namespace gc
}  // namespace art

#endif  // ART_RUNTIME_GC_COLLECTOR_MARK_COMPACT_H_
