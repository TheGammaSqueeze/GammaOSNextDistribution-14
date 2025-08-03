/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <aidl/android/aidl/test/trunk/BnTrunkStableTest.h>
#include <aidl/android/aidl/test/trunk/ITrunkStableTest.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <binder/ProcessState.h>
#include <gtest/gtest.h>

#ifdef AIDL_USE_UNFROZEN
constexpr bool kUseUnfrozen = true;
#else
constexpr bool kUseUnfrozen = false;
#endif

using aidl::android::aidl::test::trunk::ITrunkStableTest;
using ndk::ScopedAStatus;
using MyParcelable = aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable;
using MyOtherParcelable = aidl::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable;
using MyEnum = aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum;
using MyUnion = aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion;

class TrunkInterfaceTest : public testing::Test {
 public:
  void SetUp() override {
    android::ProcessState::self()->setThreadPoolMaxThreadCount(1);
    android::ProcessState::self()->startThreadPool();
    ndk::SpAIBinder binder =
        ndk::SpAIBinder(AServiceManager_waitForService(ITrunkStableTest::descriptor));
    service = ITrunkStableTest::fromBinder(binder);
    ASSERT_NE(nullptr, service);
  }

  std::shared_ptr<ITrunkStableTest> service;
};

TEST_F(TrunkInterfaceTest, getInterfaceVersion) {
  // TODO(b/292539129) this should be done with an annotation instead of ifdefs
  // We have to match on a single char with #if, even though it is
  // really "enabled"/"disabled"
  int32_t ver = 0;
  auto status = service->getInterfaceVersion(&ver);
  ASSERT_TRUE(status.isOk());
  if (kUseUnfrozen) {
    EXPECT_EQ(2, ver);
    // Check the local version as well
    EXPECT_EQ(2, ITrunkStableTest::version);
  } else {
    EXPECT_EQ(1, ver);
    // Check the local version as well
    EXPECT_EQ(1, ITrunkStableTest::version);
  }
}

TEST_F(TrunkInterfaceTest, getInterfaceHash) {
  std::string hash;
  std::string localHash;
  auto status = service->getInterfaceHash(&hash);
  ASSERT_TRUE(status.isOk());
  if (kUseUnfrozen) {
    EXPECT_EQ("notfrozen", hash);
    // Check the local hash as well
    EXPECT_EQ("notfrozen", ITrunkStableTest::hash);
  } else {
    EXPECT_EQ("88311b9118fb6fe9eff4a2ca19121de0587f6d5f", hash);
    // Check the local hash as well
    EXPECT_EQ("88311b9118fb6fe9eff4a2ca19121de0587f6d5f", ITrunkStableTest::hash);
  }
}

// `c` is a new field that isn't read from the reply parcel
TEST_F(TrunkInterfaceTest, repeatParcelable) {
  MyParcelable in, out;
  in.a = 14;
  in.b = 15;
  in.c = 16;

  auto status = service->repeatParcelable(in, &out);
  ASSERT_TRUE(status.isOk()) << status;
  if (kUseUnfrozen) {
    EXPECT_EQ(in.a, out.a);
    EXPECT_EQ(in.b, out.b);
    EXPECT_EQ(in.c, out.c);
  } else {
    EXPECT_EQ(in.a, out.a);
    EXPECT_EQ(in.b, out.b);
    EXPECT_NE(in.c, out.c);
    EXPECT_EQ(0, out.c);
  }
}

// repeatOtherParcelable is a new API that isn't implemented
TEST_F(TrunkInterfaceTest, repeatOtherParcelable) {
  MyOtherParcelable in, out;
  in.a = 14;
  in.b = 15;

  auto status = service->repeatOtherParcelable(in, &out);
  if (kUseUnfrozen) {
    ASSERT_TRUE(status.isOk()) << status;
    EXPECT_EQ(in.a, out.a);
    EXPECT_EQ(in.b, out.b);
  } else {
    EXPECT_FALSE(status.isOk()) << status;
    EXPECT_EQ(STATUS_UNKNOWN_TRANSACTION, status.getStatus()) << status;
  }
}

// enums aren't handled differently.
TEST_F(TrunkInterfaceTest, repeatEnum) {
  MyEnum in = MyEnum::THREE;
  MyEnum out = MyEnum::ZERO;

  auto status = service->repeatEnum(in, &out);
  ASSERT_TRUE(status.isOk()) << status;
  EXPECT_EQ(in, out);
}

// `c` is a new field that causes a failure if used
// `b` is from V1 and will cause no failure
TEST_F(TrunkInterfaceTest, repeatUnion) {
  MyUnion in_ok = MyUnion::make<MyUnion::b>(13);
  MyUnion in_test = MyUnion::make<MyUnion::c>(12);
  MyUnion out;

  auto status = service->repeatUnion(in_ok, &out);
  ASSERT_TRUE(status.isOk()) << status;
  EXPECT_EQ(in_ok, out);

  status = service->repeatUnion(in_test, &out);
  if (kUseUnfrozen) {
    ASSERT_TRUE(status.isOk()) << status;
    EXPECT_EQ(in_test, out);
  } else {
    ASSERT_FALSE(status.isOk()) << status;
    EXPECT_NE(in_test, out);
  }
}

class MyCallback : public ITrunkStableTest::BnMyCallback {
 public:
  MyCallback() {}
  virtual ~MyCallback() = default;

  ScopedAStatus repeatParcelable(const MyParcelable& input, MyParcelable* _aidl_return) override {
    *_aidl_return = input;
    repeatParcelableCalled = true;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatEnum(MyEnum input, MyEnum* _aidl_return) override {
    *_aidl_return = input;
    repeatEnumCalled = true;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatUnion(const MyUnion& input, MyUnion* _aidl_return) override {
    *_aidl_return = input;
    repeatUnionCalled = true;
    return ScopedAStatus::ok();
  }

  ScopedAStatus repeatOtherParcelable(const MyOtherParcelable& input,
                                      MyOtherParcelable* _aidl_return) override {
    *_aidl_return = input;
    repeatOtherParcelableCalled = true;
    return ScopedAStatus::ok();
  }

  bool repeatParcelableCalled = false;
  bool repeatEnumCalled = false;
  bool repeatUnionCalled = false;
  bool repeatOtherParcelableCalled = false;
};

// repeatOtherParcelable is new in V2, so it won't be called
TEST_F(TrunkInterfaceTest, callMyCallback) {
  std::shared_ptr<MyCallback> cb = ndk::SharedRefBase::make<MyCallback>();

  auto status = service->callMyCallback(cb);
  ASSERT_TRUE(status.isOk()) << status;
  if (kUseUnfrozen) {
    EXPECT_TRUE(cb->repeatParcelableCalled);
    EXPECT_TRUE(cb->repeatEnumCalled);
    EXPECT_TRUE(cb->repeatUnionCalled);
    EXPECT_TRUE(cb->repeatOtherParcelableCalled);
  } else {
    EXPECT_TRUE(cb->repeatParcelableCalled);
    EXPECT_TRUE(cb->repeatEnumCalled);
    EXPECT_TRUE(cb->repeatUnionCalled);
    EXPECT_FALSE(cb->repeatOtherParcelableCalled);
  }
}
