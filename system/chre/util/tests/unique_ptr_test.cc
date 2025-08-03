/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <cstring>

#include "gtest/gtest.h"

#include "chre/util/unique_ptr.h"

using chre::MakeUnique;
using chre::MakeUniqueArray;
using chre::MakeUniqueZeroFill;
using chre::UniquePtr;
using chre::util::internal::is_unbounded_array_v;

struct Value {
  Value(int value) : value(value) {
    constructionCounter++;
  }

  ~Value() {
    constructionCounter--;
  }

  Value &operator=(Value &&other) {
    value = other.value;
    return *this;
  }

  int value;
  static int constructionCounter;
};

int Value::constructionCounter = 0;

TEST(UniquePtr, NullInit) {
  // Put something on the stack to help catch uninitialized memory errors
  {
    UniquePtr<int> p1 = MakeUnique<int>();
    // Verify that the typical null checks are implemented correctly
    ASSERT_FALSE(p1.isNull());
    EXPECT_TRUE(p1);
    EXPECT_NE(p1, nullptr);
  }
  {
    UniquePtr<int> p1;
    EXPECT_FALSE(p1);
    EXPECT_TRUE(p1.isNull());
    EXPECT_EQ(p1, nullptr);
  }
  UniquePtr<int> p2(nullptr);
  EXPECT_TRUE(p2.isNull());
}

TEST(UniquePtr, Construct) {
  UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
  ASSERT_TRUE(myInt);
  EXPECT_EQ(myInt.get()->value, 0xcafe);
  EXPECT_EQ(myInt->value, 0xcafe);
  EXPECT_EQ((*myInt).value, 0xcafe);

  int *realInt = chre::memoryAlloc<int>();
  ASSERT_NE(realInt, nullptr);
  UniquePtr<int> wrapped(realInt);
  ASSERT_TRUE(realInt);
}

struct BigArray {
  int x[2048];
};

// Check the is_unbounded_array_v backport used in memoryAllocArray to help
// constrain usage of MakeUniqueArray to only the proper type category
static_assert(is_unbounded_array_v<int> == false &&
                  is_unbounded_array_v<char[2]> == false &&
                  is_unbounded_array_v<char[]> == true,
              "is_unbounded_array_v implemented incorrectly");

TEST(UniquePtr, MakeUniqueArray) {
  // For these tests, we are just allocating and writing to the array - the main
  // thing we are looking for is that the allocation is of an appropriate size,
  // which should be checked when running this test with sanitizers enabled
  {
    constexpr size_t kSize = 32;
    UniquePtr<char[]> ptr = MakeUniqueArray<char[]>(kSize);
    ASSERT_FALSE(ptr.isNull());
    std::memset(ptr.get(), 0x98, kSize);
    ptr[0] = 0x11;
    EXPECT_EQ(*ptr.get(), 0x11);
  }
  {
    constexpr size_t kSize = 4;
    auto ptr = MakeUniqueArray<BigArray[]>(kSize);
    ASSERT_FALSE(ptr.isNull());
    std::memset(ptr.get(), 0x37, sizeof(BigArray) * kSize);
  }
}

TEST(UniquePtr, MakeUniqueZeroFill) {
  BigArray baseline = {};
  auto myArray = MakeUniqueZeroFill<BigArray>();
  ASSERT_FALSE(myArray.isNull());
  // Note that this doesn't actually test things properly, because we don't
  // guarantee that malloc is not already giving us zeroed out memory. To
  // properly do it, we could inject the allocator, but this function is simple
  // enough that it's not really worth the effort.
  EXPECT_EQ(std::memcmp(&baseline, myArray.get(), sizeof(baseline)), 0);
}

TEST(UniquePtr, MoveConstruct) {
  UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
  ASSERT_FALSE(myInt.isNull());
  Value *value = myInt.get();

  UniquePtr<Value> moved(std::move(myInt));
  EXPECT_EQ(moved.get(), value);
  EXPECT_EQ(myInt.get(), nullptr);
}

TEST(UniquePtr, Move) {
  Value::constructionCounter = 0;

  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    ASSERT_FALSE(myInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 1);

    UniquePtr<Value> myMovedInt = MakeUnique<Value>(0);
    ASSERT_FALSE(myMovedInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 2);
    myMovedInt = std::move(myInt);
    ASSERT_FALSE(myMovedInt.isNull());
    ASSERT_TRUE(myInt.isNull());
    EXPECT_EQ(myMovedInt.get()->value, 0xcafe);
  }

  EXPECT_EQ(Value::constructionCounter, 0);
}

TEST(UniquePtr, Release) {
  Value::constructionCounter = 0;

  Value *value1, *value2;
  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    ASSERT_FALSE(myInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 1);
    value1 = myInt.get();
    EXPECT_NE(value1, nullptr);
    value2 = myInt.release();
    EXPECT_EQ(value1, value2);
    EXPECT_EQ(myInt.get(), nullptr);
    EXPECT_TRUE(myInt.isNull());
  }

  EXPECT_EQ(Value::constructionCounter, 1);
  EXPECT_EQ(value2->value, 0xcafe);
  value2->~Value();
  chre::memoryFree(value2);
}

TEST(UniquePtr, Reset) {
  Value::constructionCounter = 0;

  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    EXPECT_EQ(myInt.get()->value, 0xcafe);
    EXPECT_EQ(Value::constructionCounter, 1);
    myInt.reset(nullptr);
    EXPECT_EQ(myInt.get(), nullptr);
    EXPECT_EQ(Value::constructionCounter, 0);

    myInt = MakeUnique<Value>(0xcafe);
    UniquePtr<Value> myInt2 = MakeUnique<Value>(0xface);
    EXPECT_EQ(Value::constructionCounter, 2);
    myInt.reset(myInt2.release());
    EXPECT_EQ(Value::constructionCounter, 1);
    EXPECT_EQ(myInt.get()->value, 0xface);
    EXPECT_EQ(myInt2.get(), nullptr);

    myInt.reset();
    EXPECT_EQ(myInt.get(), nullptr);
  }

  EXPECT_EQ(Value::constructionCounter, 0);
}

TEST(UniquePtr, EqualityOperator) {
  Value::constructionCounter = 0;

  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    EXPECT_TRUE(myInt != nullptr);

    myInt.reset();
    EXPECT_TRUE(myInt == nullptr);
  }

  EXPECT_EQ(Value::constructionCounter, 0);
}

TEST(UniquePtr, OverAlignedTest) {
  // Explicitly overaligned structure larger than std::max_align_t.
  struct alignas(32) OverAlignedStruct {
    uint32_t x[32];
  };
  static_assert(alignof(OverAlignedStruct) > alignof(std::max_align_t));

  UniquePtr<OverAlignedStruct> ptr = MakeUnique<OverAlignedStruct>();
  ASSERT_EQ(reinterpret_cast<uintptr_t>(ptr.get()) % alignof(OverAlignedStruct),
            0);
}
