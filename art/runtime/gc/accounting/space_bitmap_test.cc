/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "space_bitmap.h"

#include <stdint.h>
#include <memory>

#include "base/mutex.h"
#include "common_runtime_test.h"
#include "gc/space/large_object_space.h"
#include "runtime_globals.h"
#include "space_bitmap-inl.h"

namespace art HIDDEN {
namespace gc {
namespace accounting {

// SpaceBitmapTest is a CommonRuntimeTest as the test requires runtime to be initialized to enable
// access to space::LargeObjectSpace::ObjectAlignment().
template <typename T>
class SpaceBitmapTest : public CommonRuntimeTest {};

// Main test parameters. For each test case, we pair together a SpaceBitmap
// implementation with an object alignment. The object alignment may be larger
// than the underlying SpaceBitmap alignment.
template <typename T, size_t kAlignment>
struct SpaceBitmapTestType {
  using SpaceBitmap = T;
  static constexpr size_t GetObjectAlignment() {
    return kAlignment;
  }
};

// This is a special case where object alignment is chosen to be the large-object
// alignment determined at runtime.
template <typename T>
struct SpaceBitmapTestPageSizeType {
  using SpaceBitmap = T;
  static size_t GetObjectAlignment() {
    return space::LargeObjectSpace::ObjectAlignment();
  }
};

using SpaceBitmapTestTypes =
    ::testing::Types<SpaceBitmapTestType<ContinuousSpaceBitmap, kObjectAlignment>,
                     // Large objects are aligned to the OS page size, try
                     // different supported values, including the current
                     // runtime page size.
                     SpaceBitmapTestType<LargeObjectBitmap, kMinPageSize>,
                     SpaceBitmapTestPageSizeType<LargeObjectBitmap>,
                     SpaceBitmapTestType<LargeObjectBitmap, kMaxPageSize>>;

TYPED_TEST_CASE(SpaceBitmapTest, SpaceBitmapTestTypes);

TYPED_TEST(SpaceBitmapTest, Init) {
  uint8_t* heap_begin = reinterpret_cast<uint8_t*>(0x10000000);
  size_t heap_capacity = 16 * MB;
  auto space_bitmap(TypeParam::SpaceBitmap::Create("test bitmap", heap_begin, heap_capacity));
  EXPECT_TRUE(space_bitmap.IsValid());
}

template <typename SpaceBitmap>
class BitmapVerify {
 public:
  BitmapVerify(SpaceBitmap* bitmap, const mirror::Object* begin,
               const mirror::Object* end)
    : bitmap_(bitmap),
      begin_(begin),
      end_(end) {}

  void operator()(const mirror::Object* obj) {
    EXPECT_TRUE(obj >= begin_);
    EXPECT_TRUE(obj <= end_);
    EXPECT_EQ(bitmap_->Test(obj), ((reinterpret_cast<uintptr_t>(obj) & 0xF) != 0));
  }

  SpaceBitmap* const bitmap_;
  const mirror::Object* begin_;
  const mirror::Object* end_;
};

TYPED_TEST(SpaceBitmapTest, ScanRange) {
  uint8_t* heap_begin = reinterpret_cast<uint8_t*>(0x10000000);
  size_t heap_capacity = 16 * MB;
  const size_t gObjectAlignment = TypeParam::GetObjectAlignment();

  auto space_bitmap(TypeParam::SpaceBitmap::Create("test bitmap", heap_begin, heap_capacity));
  EXPECT_TRUE(space_bitmap.IsValid());

  // Set all the odd bits in the first BitsPerIntPtrT * 3 to one.
  for (size_t j = 0; j < kBitsPerIntPtrT * 3; ++j) {
    const mirror::Object* obj =
        reinterpret_cast<mirror::Object*>(heap_begin + j * gObjectAlignment);
    if (reinterpret_cast<uintptr_t>(obj) & 0xF) {
      space_bitmap.Set(obj);
    }
  }
  // Try every possible starting bit in the first word. Then for each starting bit, try each
  // possible length up to a maximum of `kBitsPerIntPtrT * 2 - 1` bits.
  // This handles all the cases, having runs which start and end on the same word, and different
  // words.
  for (size_t i = 0; i < static_cast<size_t>(kBitsPerIntPtrT); ++i) {
    mirror::Object* start =
        reinterpret_cast<mirror::Object*>(heap_begin + i * gObjectAlignment);
    for (size_t j = 0; j < static_cast<size_t>(kBitsPerIntPtrT * 2); ++j) {
      mirror::Object* end =
          reinterpret_cast<mirror::Object*>(heap_begin + (i + j) * gObjectAlignment);
      BitmapVerify(&space_bitmap, start, end);
    }
  }
}

TYPED_TEST(SpaceBitmapTest, ClearRange) {
  const size_t page_size = MemMap::GetPageSize();
  uint8_t* heap_begin = reinterpret_cast<uint8_t*>(0x10000000);
  size_t heap_capacity = 16 * MB;
  const size_t gObjectAlignment = TypeParam::GetObjectAlignment();

  auto bitmap(TypeParam::SpaceBitmap::Create("test bitmap", heap_begin, heap_capacity));
  EXPECT_TRUE(bitmap.IsValid());

  // Set all of the bits in the bitmap.
  for (size_t j = 0; j < heap_capacity; j += gObjectAlignment) {
    const mirror::Object* obj = reinterpret_cast<mirror::Object*>(heap_begin + j);
    bitmap.Set(obj);
  }

  std::vector<std::pair<uintptr_t, uintptr_t>> ranges = {
      {0, RoundUp(10 * KB, gObjectAlignment) + gObjectAlignment},
      {gObjectAlignment, gObjectAlignment},
      {gObjectAlignment, 2 * gObjectAlignment},
      {gObjectAlignment, 5 * gObjectAlignment},
      {RoundUp(1 * KB, gObjectAlignment) + gObjectAlignment,
       RoundUp(2 * KB, gObjectAlignment) + 5 * gObjectAlignment},
  };
  // Try clearing a few ranges.
  for (const std::pair<uintptr_t, uintptr_t>& range : ranges) {
    const mirror::Object* obj_begin = reinterpret_cast<mirror::Object*>(heap_begin + range.first);
    const mirror::Object* obj_end = reinterpret_cast<mirror::Object*>(heap_begin + range.second);
    bitmap.ClearRange(obj_begin, obj_end);
    // Boundaries should still be marked.
    for (uintptr_t i = 0; i < range.first; i += gObjectAlignment) {
      EXPECT_TRUE(bitmap.Test(reinterpret_cast<mirror::Object*>(heap_begin + i)));
    }
    for (uintptr_t i = range.second; i < range.second + page_size; i += gObjectAlignment) {
      EXPECT_TRUE(bitmap.Test(reinterpret_cast<mirror::Object*>(heap_begin + i)));
    }
    // Everything inside should be cleared.
    for (uintptr_t i = range.first; i < range.second; i += gObjectAlignment) {
      EXPECT_FALSE(bitmap.Test(reinterpret_cast<mirror::Object*>(heap_begin + i)));
      bitmap.Set(reinterpret_cast<mirror::Object*>(heap_begin + i));
    }
  }
}


class SimpleCounter {
 public:
  explicit SimpleCounter(size_t* counter) : count_(counter) {}

  void operator()([[maybe_unused]] mirror::Object* obj) const { (*count_)++; }

  size_t* const count_;
};

class RandGen {
 public:
  explicit RandGen(uint32_t seed) : val_(seed) {}

  uint32_t next() {
    val_ = val_ * 48271 % 2147483647 + 13;
    return val_;
  }

  uint32_t val_;
};

template <typename SpaceBitmap, typename TestFn>
static void RunTest(size_t alignment, TestFn&& fn) NO_THREAD_SAFETY_ANALYSIS {
  uint8_t* heap_begin = reinterpret_cast<uint8_t*>(0x10000000);
  size_t heap_capacity = 16 * MB;

  // Seed with 0x1234 for reproducability.
  RandGen r(0x1234);

  for (int i = 0; i < 5 ; ++i) {
    SpaceBitmap space_bitmap(SpaceBitmap::Create("test bitmap", heap_begin, heap_capacity));

    for (int j = 0; j < 10000; ++j) {
      size_t offset = RoundDown(r.next() % heap_capacity, alignment);
      bool set = r.next() % 2 == 1;

      if (set) {
        space_bitmap.Set(reinterpret_cast<mirror::Object*>(heap_begin + offset));
      } else {
        space_bitmap.Clear(reinterpret_cast<mirror::Object*>(heap_begin + offset));
      }
    }

    for (int j = 0; j < 50; ++j) {
      const size_t offset = RoundDown(r.next() % heap_capacity, alignment);
      const size_t remain = heap_capacity - offset;
      const size_t end = offset + RoundDown(r.next() % (remain + 1), alignment);

      size_t manual = 0;
      for (uintptr_t k = offset; k < end; k += alignment) {
        if (space_bitmap.Test(reinterpret_cast<mirror::Object*>(heap_begin + k))) {
          manual++;
        }
      }

      uintptr_t range_begin = reinterpret_cast<uintptr_t>(heap_begin) + offset;
      uintptr_t range_end = reinterpret_cast<uintptr_t>(heap_begin) + end;

      fn(&space_bitmap, range_begin, range_end, manual);
    }
  }
}

TYPED_TEST(SpaceBitmapTest, VisitorAlignment) {
  using SpaceBitmap = typename TypeParam::SpaceBitmap;
  auto count_test_fn = [](SpaceBitmap* space_bitmap,
                          uintptr_t range_begin,
                          uintptr_t range_end,
                          size_t manual_count) {
    size_t count = 0;
    auto count_fn = [&count]([[maybe_unused]] mirror::Object* obj) { count++; };
    space_bitmap->VisitMarkedRange(range_begin, range_end, count_fn);
    EXPECT_EQ(count, manual_count);
  };
  RunTest<SpaceBitmap>(TypeParam::GetObjectAlignment(), count_test_fn);
}

TYPED_TEST(SpaceBitmapTest, OrderAlignment) {
  using SpaceBitmap = typename TypeParam::SpaceBitmap;
  auto order_test_fn = [](SpaceBitmap* space_bitmap,
                          uintptr_t range_begin,
                          uintptr_t range_end,
                          size_t manual_count)
      REQUIRES_SHARED(Locks::heap_bitmap_lock_, Locks::mutator_lock_) {
    mirror::Object* last_ptr = nullptr;
    auto order_check = [&last_ptr](mirror::Object* obj) {
      EXPECT_LT(last_ptr, obj);
      last_ptr = obj;
    };

    // Test complete walk.
    space_bitmap->Walk(order_check);
    if (manual_count > 0) {
      EXPECT_NE(nullptr, last_ptr);
    }

    // Test range.
    last_ptr = nullptr;
    space_bitmap->VisitMarkedRange(range_begin, range_end, order_check);
    if (manual_count > 0) {
      EXPECT_NE(nullptr, last_ptr);
    }
  };
  RunTest<SpaceBitmap>(TypeParam::GetObjectAlignment(), order_test_fn);
}

}  // namespace accounting
}  // namespace gc
}  // namespace art
