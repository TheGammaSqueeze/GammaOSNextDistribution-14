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

#include <algorithm>

#include "base/metrics/metrics.h"
#include "class_linker-inl.h"
#include "common_runtime_test.h"
#include "gc/accounting/card_table-inl.h"
#include "gc/accounting/space_bitmap-inl.h"
#include "handle_scope-inl.h"
#include "mirror/class-inl.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "mirror/object_array-inl.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {
namespace gc {

class HeapTest : public CommonRuntimeTest {
 public:
  HeapTest() {
    use_boot_image_ = true;  // Make the Runtime creation cheaper.
  }

  void SetUp() override {
    MemMap::Init();
    std::string error_msg;
    // Reserve the preferred address to force the heap to use another one for testing.
    reserved_ = MemMap::MapAnonymous("ReserveMap",
                                     gc::Heap::kPreferredAllocSpaceBegin,
                                     16 * KB,
                                     PROT_READ,
                                     /*low_4gb=*/ true,
                                     /*reuse=*/ false,
                                     /*reservation=*/ nullptr,
                                     &error_msg);
    // There is no guarantee that reserved_ will be valid (due to ASLR). See b/175018342.
    CommonRuntimeTest::SetUp();
  }

 private:
  MemMap reserved_;
};

TEST_F(HeapTest, ClearGrowthLimit) {
  Heap* heap = Runtime::Current()->GetHeap();
  int64_t max_memory_before = heap->GetMaxMemory();
  int64_t total_memory_before = heap->GetTotalMemory();
  heap->ClearGrowthLimit();
  int64_t max_memory_after = heap->GetMaxMemory();
  int64_t total_memory_after = heap->GetTotalMemory();
  EXPECT_GE(max_memory_after, max_memory_before);
  EXPECT_GE(total_memory_after, total_memory_before);
}

TEST_F(HeapTest, GarbageCollectClassLinkerInit) {
  {
    ScopedObjectAccess soa(Thread::Current());
    // garbage is created during ClassLinker::Init

    StackHandleScope<1> hs(soa.Self());
    Handle<mirror::Class> c(
        hs.NewHandle(class_linker_->FindSystemClass(soa.Self(), "[Ljava/lang/Object;")));
    for (size_t i = 0; i < 1024; ++i) {
      StackHandleScope<1> hs2(soa.Self());
      Handle<mirror::ObjectArray<mirror::Object>> array(hs2.NewHandle(
          mirror::ObjectArray<mirror::Object>::Alloc(soa.Self(), c.Get(), 2048)));
      for (size_t j = 0; j < 2048; ++j) {
        ObjPtr<mirror::String> string =
            mirror::String::AllocFromModifiedUtf8(soa.Self(), "hello, world!");
        // handle scope operator -> deferences the handle scope before running the method.
        array->Set<false>(j, string);
      }
    }
  }
  Runtime::Current()->GetHeap()->CollectGarbage(/* clear_soft_references= */ false);
}

TEST_F(HeapTest, HeapBitmapCapacityTest) {
  uint8_t* heap_begin = reinterpret_cast<uint8_t*>(0x1000);
  const size_t heap_capacity = kObjectAlignment * (sizeof(intptr_t) * 8 + 1);
  accounting::ContinuousSpaceBitmap bitmap(
      accounting::ContinuousSpaceBitmap::Create("test bitmap", heap_begin, heap_capacity));
  mirror::Object* fake_end_of_heap_object =
      reinterpret_cast<mirror::Object*>(&heap_begin[heap_capacity - kObjectAlignment]);
  bitmap.Set(fake_end_of_heap_object);
}

TEST_F(HeapTest, DumpGCPerformanceOnShutdown) {
  Runtime::Current()->GetHeap()->CollectGarbage(/* clear_soft_references= */ false);
  Runtime::Current()->SetDumpGCPerformanceOnShutdown(true);
}

bool AnyIsFalse(bool x, bool y) { return !x || !y; }

TEST_F(HeapTest, GCMetrics) {
  // Allocate a few string objects (to be collected), then trigger garbage
  // collection, and check that GC metrics are updated (where applicable).
  Heap* heap = Runtime::Current()->GetHeap();
  {
    constexpr const size_t kNumObj = 128;
    ScopedObjectAccess soa(Thread::Current());
    StackHandleScope<kNumObj> hs(soa.Self());
    for (size_t i = 0u; i < kNumObj; ++i) {
      Handle<mirror::String> string [[maybe_unused]] (
          hs.NewHandle(mirror::String::AllocFromModifiedUtf8(soa.Self(), "test")));
    }
    // Do one GC while the temporary objects are reachable, forcing the GC to scan something.
    // The subsequent GC at line 127 may not scan anything but will certainly free some bytes.
    // Together the two GCs ensure success of the test.
    heap->CollectGarbage(/* clear_soft_references= */ false);
  }
  heap->CollectGarbage(/* clear_soft_references= */ false);

  // ART Metrics.
  metrics::ArtMetrics* metrics = Runtime::Current()->GetMetrics();
  // ART full-heap GC metrics.
  metrics::MetricsBase<int64_t>* full_gc_collection_time = metrics->FullGcCollectionTime();
  metrics::MetricsBase<uint64_t>* full_gc_count = metrics->FullGcCount();
  metrics::MetricsBase<uint64_t>* full_gc_count_delta = metrics->FullGcCountDelta();
  metrics::MetricsBase<int64_t>* full_gc_throughput = metrics->FullGcThroughput();
  metrics::MetricsBase<int64_t>* full_gc_tracing_throughput = metrics->FullGcTracingThroughput();
  metrics::MetricsBase<uint64_t>* full_gc_throughput_avg = metrics->FullGcThroughputAvg();
  metrics::MetricsBase<uint64_t>* full_gc_tracing_throughput_avg =
      metrics->FullGcTracingThroughputAvg();
  metrics::MetricsBase<uint64_t>* full_gc_scanned_bytes = metrics->FullGcScannedBytes();
  metrics::MetricsBase<uint64_t>* full_gc_scanned_bytes_delta = metrics->FullGcScannedBytesDelta();
  metrics::MetricsBase<uint64_t>* full_gc_freed_bytes = metrics->FullGcFreedBytes();
  metrics::MetricsBase<uint64_t>* full_gc_freed_bytes_delta = metrics->FullGcFreedBytesDelta();
  metrics::MetricsBase<uint64_t>* full_gc_duration = metrics->FullGcDuration();
  metrics::MetricsBase<uint64_t>* full_gc_duration_delta = metrics->FullGcDurationDelta();
  // ART young-generation GC metrics.
  metrics::MetricsBase<int64_t>* young_gc_collection_time = metrics->YoungGcCollectionTime();
  metrics::MetricsBase<uint64_t>* young_gc_count = metrics->YoungGcCount();
  metrics::MetricsBase<uint64_t>* young_gc_count_delta = metrics->YoungGcCountDelta();
  metrics::MetricsBase<int64_t>* young_gc_throughput = metrics->YoungGcThroughput();
  metrics::MetricsBase<int64_t>* young_gc_tracing_throughput = metrics->YoungGcTracingThroughput();
  metrics::MetricsBase<uint64_t>* young_gc_throughput_avg = metrics->YoungGcThroughputAvg();
  metrics::MetricsBase<uint64_t>* young_gc_tracing_throughput_avg =
      metrics->YoungGcTracingThroughputAvg();
  metrics::MetricsBase<uint64_t>* young_gc_scanned_bytes = metrics->YoungGcScannedBytes();
  metrics::MetricsBase<uint64_t>* young_gc_scanned_bytes_delta =
      metrics->YoungGcScannedBytesDelta();
  metrics::MetricsBase<uint64_t>* young_gc_freed_bytes = metrics->YoungGcFreedBytes();
  metrics::MetricsBase<uint64_t>* young_gc_freed_bytes_delta = metrics->YoungGcFreedBytesDelta();
  metrics::MetricsBase<uint64_t>* young_gc_duration = metrics->YoungGcDuration();
  metrics::MetricsBase<uint64_t>* young_gc_duration_delta = metrics->YoungGcDurationDelta();

  CollectorType fg_collector_type = heap->GetForegroundCollectorType();
  if (fg_collector_type == kCollectorTypeCC || fg_collector_type == kCollectorTypeCMC) {
    // Only the Concurrent Copying and Concurrent Mark-Compact collectors enable
    // GC metrics at the moment.
    if (heap->GetUseGenerationalCC()) {
      // Check that full-heap and/or young-generation GC metrics are non-null
      // after trigerring the collection.
      EXPECT_PRED2(
          AnyIsFalse, full_gc_collection_time->IsNull(), young_gc_collection_time->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_count->IsNull(), young_gc_count->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_count_delta->IsNull(), young_gc_count_delta->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_throughput->IsNull(), young_gc_throughput->IsNull());
      EXPECT_PRED2(
          AnyIsFalse, full_gc_tracing_throughput->IsNull(), young_gc_tracing_throughput->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_throughput_avg->IsNull(), young_gc_throughput_avg->IsNull());
      EXPECT_PRED2(AnyIsFalse,
                   full_gc_tracing_throughput_avg->IsNull(),
                   young_gc_tracing_throughput_avg->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_scanned_bytes->IsNull(), young_gc_scanned_bytes->IsNull());
      EXPECT_PRED2(AnyIsFalse,
                   full_gc_scanned_bytes_delta->IsNull(),
                   young_gc_scanned_bytes_delta->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_freed_bytes->IsNull(), young_gc_freed_bytes->IsNull());
      EXPECT_PRED2(
          AnyIsFalse, full_gc_freed_bytes_delta->IsNull(), young_gc_freed_bytes_delta->IsNull());
      // We have observed that sometimes the GC duration (both for full-heap and
      // young-generation collections) is null (b/271112044). Temporarily
      // suspend the following checks while we investigate.
      //
      // TODO(b/271112044): Investigate and adjust these expectations and/or the
      // corresponding metric logic.
#if 0
      EXPECT_PRED2(AnyIsFalse, full_gc_duration->IsNull(), young_gc_duration->IsNull());
      EXPECT_PRED2(AnyIsFalse, full_gc_duration_delta->IsNull(), young_gc_duration_delta->IsNull());
#endif
    } else {
      // Check that only full-heap GC metrics are non-null after trigerring the collection.
      EXPECT_FALSE(full_gc_collection_time->IsNull());
      EXPECT_FALSE(full_gc_count->IsNull());
      EXPECT_FALSE(full_gc_count_delta->IsNull());
      EXPECT_FALSE(full_gc_throughput->IsNull());
      EXPECT_FALSE(full_gc_tracing_throughput->IsNull());
      EXPECT_FALSE(full_gc_throughput_avg->IsNull());
      EXPECT_FALSE(full_gc_tracing_throughput_avg->IsNull());
      EXPECT_FALSE(full_gc_scanned_bytes->IsNull());
      EXPECT_FALSE(full_gc_scanned_bytes_delta->IsNull());
      EXPECT_FALSE(full_gc_freed_bytes->IsNull());
      EXPECT_FALSE(full_gc_freed_bytes_delta->IsNull());
      EXPECT_FALSE(full_gc_duration->IsNull());
      EXPECT_FALSE(full_gc_duration_delta->IsNull());

      EXPECT_TRUE(young_gc_collection_time->IsNull());
      EXPECT_TRUE(young_gc_count->IsNull());
      EXPECT_TRUE(young_gc_count_delta->IsNull());
      EXPECT_TRUE(young_gc_throughput->IsNull());
      EXPECT_TRUE(young_gc_tracing_throughput->IsNull());
      EXPECT_TRUE(young_gc_throughput_avg->IsNull());
      EXPECT_TRUE(young_gc_tracing_throughput_avg->IsNull());
      EXPECT_TRUE(young_gc_scanned_bytes->IsNull());
      EXPECT_TRUE(young_gc_scanned_bytes_delta->IsNull());
      EXPECT_TRUE(young_gc_freed_bytes->IsNull());
      EXPECT_TRUE(young_gc_freed_bytes_delta->IsNull());
      EXPECT_TRUE(young_gc_duration->IsNull());
      EXPECT_TRUE(young_gc_duration_delta->IsNull());
    }
  } else {
    // Check that all metrics are null after trigerring the collection.
    EXPECT_TRUE(full_gc_collection_time->IsNull());
    EXPECT_TRUE(full_gc_count->IsNull());
    EXPECT_TRUE(full_gc_count_delta->IsNull());
    EXPECT_TRUE(full_gc_throughput->IsNull());
    EXPECT_TRUE(full_gc_tracing_throughput->IsNull());
    EXPECT_TRUE(full_gc_throughput_avg->IsNull());
    EXPECT_TRUE(full_gc_tracing_throughput_avg->IsNull());
    EXPECT_TRUE(full_gc_scanned_bytes->IsNull());
    EXPECT_TRUE(full_gc_scanned_bytes_delta->IsNull());
    EXPECT_TRUE(full_gc_freed_bytes->IsNull());
    EXPECT_TRUE(full_gc_freed_bytes_delta->IsNull());
    EXPECT_TRUE(full_gc_duration->IsNull());
    EXPECT_TRUE(full_gc_duration_delta->IsNull());

    EXPECT_TRUE(young_gc_collection_time->IsNull());
    EXPECT_TRUE(young_gc_count->IsNull());
    EXPECT_TRUE(young_gc_count_delta->IsNull());
    EXPECT_TRUE(young_gc_throughput->IsNull());
    EXPECT_TRUE(young_gc_tracing_throughput->IsNull());
    EXPECT_TRUE(young_gc_throughput_avg->IsNull());
    EXPECT_TRUE(young_gc_tracing_throughput_avg->IsNull());
    EXPECT_TRUE(young_gc_scanned_bytes->IsNull());
    EXPECT_TRUE(young_gc_scanned_bytes_delta->IsNull());
    EXPECT_TRUE(young_gc_freed_bytes->IsNull());
    EXPECT_TRUE(young_gc_freed_bytes_delta->IsNull());
    EXPECT_TRUE(young_gc_duration->IsNull());
    EXPECT_TRUE(young_gc_duration_delta->IsNull());
  }
}

class ZygoteHeapTest : public CommonRuntimeTest {
 public:
  ZygoteHeapTest() {
    use_boot_image_ = true;  // Make the Runtime creation cheaper.
  }

  void SetUpRuntimeOptions(RuntimeOptions* options) override {
    CommonRuntimeTest::SetUpRuntimeOptions(options);
    options->push_back(std::make_pair("-Xzygote", nullptr));
  }
};

TEST_F(ZygoteHeapTest, PreZygoteFork) {
  // Exercise Heap::PreZygoteFork() to check it does not crash.
  Runtime::Current()->GetHeap()->PreZygoteFork();
}

}  // namespace gc
}  // namespace art
