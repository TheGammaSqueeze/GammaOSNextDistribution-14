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

#include "chre/util/system/ref_base.h"

namespace {

class TestBase : public chre::RefBase<TestBase> {
 public:
  ~TestBase() {
    destructorCount++;
  }
  static int destructorCount;
};

int TestBase::destructorCount = 0;

class RefBaseTest : public testing::Test {
 public:
  void SetUp() override {
    TestBase::destructorCount = 0;
    mObject = static_cast<TestBase *>(chre::memoryAlloc(sizeof(TestBase)));
    new (mObject) TestBase();
  }

  TestBase *mObject;
};

}  // namespace

TEST_F(RefBaseTest, DecRef) {
  mObject->decRef();
  EXPECT_EQ(1, TestBase::destructorCount);
}

TEST_F(RefBaseTest, TwoIncRef) {
  mObject->incRef();

  mObject->decRef();
  EXPECT_EQ(0, TestBase::destructorCount);

  mObject->decRef();
  EXPECT_EQ(1, TestBase::destructorCount);
}
