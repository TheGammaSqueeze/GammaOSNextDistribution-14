/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "gtest/gtest.h"

#include "chre/util/system/shared_ptr.h"

namespace {

class TestBase : public chre::RefBase<TestBase> {
 public:
  TestBase() {}
  TestBase(int value) : value(value) {}

  ~TestBase() {
    destructorCount++;
  }

  int value = 0;
  static int destructorCount;
};

int TestBase::destructorCount = 0;

struct BigArray : public chre::RefBase<BigArray> {
  int x[2048];
};

class SharedPtrTest : public testing::Test {
 public:
  void SetUp() override {
    TestBase::destructorCount = 0;
  }
};

}  // namespace

TEST_F(SharedPtrTest, IsNull) {
  chre::SharedPtr<TestBase> ptr;

  EXPECT_TRUE(ptr.isNull());
}

TEST_F(SharedPtrTest, IsNotNull) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();

  EXPECT_FALSE(ptr.isNull());
}

TEST_F(SharedPtrTest, MoveConstructor) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();

  chre::SharedPtr<TestBase> movedPtr(std::move(ptr));

  EXPECT_TRUE(ptr.isNull());
  EXPECT_FALSE(movedPtr.isNull());
}

TEST_F(SharedPtrTest, CopyConstructor) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();

  chre::SharedPtr<TestBase> copiedPtr(ptr);

  EXPECT_FALSE(ptr.isNull());
  EXPECT_FALSE(copiedPtr.isNull());
}

TEST_F(SharedPtrTest, MoveAssignment) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();

  chre::SharedPtr<TestBase> movedPtr = std::move(ptr);

  EXPECT_TRUE(ptr.isNull());
  EXPECT_FALSE(movedPtr.isNull());
}

TEST_F(SharedPtrTest, CopiedAssignment) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();

  chre::SharedPtr<TestBase> copiedPtr = ptr;

  EXPECT_FALSE(ptr.isNull());
  EXPECT_FALSE(copiedPtr.isNull());
}

TEST_F(SharedPtrTest, Get) {
  int specialVal = 0xdeadbeef;
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>(specialVal);

  EXPECT_EQ(specialVal, ptr.get()->value);
}

TEST_F(SharedPtrTest, Reset) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();
  chre::SharedPtr<TestBase> ptr2 = chre::MakeShared<TestBase>();

  EXPECT_NE(ptr, ptr2);

  ptr2.reset(ptr.get());
  EXPECT_EQ(ptr, ptr2);

  ptr.reset();
  EXPECT_TRUE(ptr.isNull());
}

TEST_F(SharedPtrTest, MemoryReleased) {
  chre::SharedPtr<TestBase> ptr = chre::MakeShared<TestBase>();
  chre::SharedPtr<TestBase> copiedPtr = ptr;

  ptr.~SharedPtr();
  EXPECT_EQ(0, TestBase::destructorCount);

  copiedPtr.~SharedPtr();
  EXPECT_EQ(1, TestBase::destructorCount);
}

TEST_F(SharedPtrTest, MakeSharedZeroFill) {
  BigArray baseline = {};
  auto myArray = chre::MakeSharedZeroFill<BigArray>();
  ASSERT_FALSE(myArray.isNull());
  // Note that this doesn't actually test things properly, because we don't
  // guarantee that malloc is not already giving us zeroed out memory. To
  // properly do it, we could inject the allocator, but this function is simple
  // enough that it's not really worth the effort.
  EXPECT_EQ(std::memcmp(baseline.x, myArray.get()->x, sizeof(baseline.x)), 0);
}
