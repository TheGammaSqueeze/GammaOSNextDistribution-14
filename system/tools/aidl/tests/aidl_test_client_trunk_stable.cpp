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

#include <android/aidl/test/trunk/BnTrunkStableTest.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <gtest/gtest.h>
#include <utils/String16.h>

#include "aidl_test_client.h"

#ifdef AIDL_USE_UNFROZEN
constexpr bool kUseUnfrozen = true;
#else
constexpr bool kUseUnfrozen = false;
#endif

using android::OK;
using android::sp;
using android::String16;
using android::aidl::test::trunk::ITrunkStableTest;
using android::binder::Status;
using MyParcelable = android::aidl::test::trunk::ITrunkStableTest::MyParcelable;
#ifdef AIDL_USE_V2_INTERFACE
using MyOtherParcelable = android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable;
#endif  // AIDL_USE_V2_INTERFACE
using MyEnum = android::aidl::test::trunk::ITrunkStableTest::MyEnum;
using MyUnion = android::aidl::test::trunk::ITrunkStableTest::MyUnion;

class TrunkInterfaceTest : public AidlTest {
 public:
  void SetUp() override {
    android::ProcessState::self()->setThreadPoolMaxThreadCount(1);
    android::ProcessState::self()->startThreadPool();
    service = android::waitForService<ITrunkStableTest>(ITrunkStableTest::descriptor);
    ASSERT_NE(nullptr, service);

    AidlTest::SetUp();
  }

  sp<ITrunkStableTest> service;
};

TEST_F(TrunkInterfaceTest, getInterfaceVersion) {
  // TODO(b/292539129) this should be done with an annotation instead of ifdefs
  // We have to match on a single char with #if, even though it is
  // really "enabled"/"disabled"
  if (kUseUnfrozen) {
    EXPECT_EQ(2, service->getInterfaceVersion());
    // Check the local version as well
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_EQ(2, ITrunkStableTest::VERSION);
#else
    // linked against V1 explicitly
    EXPECT_EQ(1, ITrunkStableTest::VERSION);
#endif
  } else {
    EXPECT_EQ(1, service->getInterfaceVersion());
    // Check the local version as well
    EXPECT_EQ(1, ITrunkStableTest::VERSION);
  }
}

TEST_F(TrunkInterfaceTest, getInterfaceHash) {
  if (kUseUnfrozen) {
    EXPECT_EQ("notfrozen", service->getInterfaceHash());
    // Check the local hash as well
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_EQ("notfrozen", ITrunkStableTest::HASH);
#else
    // linked against V1 explicitly
    EXPECT_EQ("88311b9118fb6fe9eff4a2ca19121de0587f6d5f", ITrunkStableTest::HASH);
#endif
  } else {
    EXPECT_EQ("88311b9118fb6fe9eff4a2ca19121de0587f6d5f", service->getInterfaceHash());
    // Check the local hash as well
    EXPECT_EQ("88311b9118fb6fe9eff4a2ca19121de0587f6d5f", ITrunkStableTest::HASH);
  }
}

// `c` is a new field that isn't read from the reply parcel
TEST_F(TrunkInterfaceTest, repeatParcelable) {
  MyParcelable in, out;
  in.a = 14;
  in.b = 15;
#ifdef AIDL_USE_V2_INTERFACE
  in.c = 16;
#endif  // AIDL_USE_V2_INTERFACE
  auto status = service->repeatParcelable(in, &out);
  ASSERT_TRUE(status.isOk()) << status;
  if (kUseUnfrozen) {
    EXPECT_EQ(in.a, out.a);
    EXPECT_EQ(in.b, out.b);
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_EQ(in.c, out.c);
#endif  // AIDL_USE_V2_INTERFACE
  } else {
    EXPECT_EQ(in.a, out.a);
    EXPECT_EQ(in.b, out.b);
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_NE(in.c, out.c);
    EXPECT_EQ(0, out.c);
#endif  // AIDL_USE_V2_INTERFACE
  }
}

#ifdef AIDL_USE_V2_INTERFACE
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
    EXPECT_EQ(::android::UNKNOWN_TRANSACTION, status.transactionError()) << status;
  }
}
#endif  // AIDL_USE_V2_INTERFACE

// enums aren't handled differently.
TEST_F(TrunkInterfaceTest, repeatEnum) {
  MyEnum in = MyEnum::TWO;
  MyEnum out = MyEnum::ZERO;

  auto status = service->repeatEnum(in, &out);
  ASSERT_TRUE(status.isOk()) << status;
  EXPECT_EQ(in, out);
}

// `c` is a new field that causes a failure if used
// `b` is from V1 and will cause no failure
TEST_F(TrunkInterfaceTest, repeatUnion) {
  MyUnion in_ok = MyUnion::make<MyUnion::b>(13);
  ;
#ifdef AIDL_USE_V2_INTERFACE
  MyUnion in_test = MyUnion::make<MyUnion::c>(12);
  ;
#endif  // AIDL_USE_V2_INTERFACE
  MyUnion out;

  auto status = service->repeatUnion(in_ok, &out);
  ASSERT_TRUE(status.isOk()) << status;
  EXPECT_EQ(in_ok, out);

#ifdef AIDL_USE_V2_INTERFACE
  status = service->repeatUnion(in_test, &out);
  if (kUseUnfrozen) {
    ASSERT_TRUE(status.isOk()) << status;
    EXPECT_EQ(in_test, out);
  } else {
    ASSERT_FALSE(status.isOk()) << status;
    EXPECT_NE(in_test, out);
  }
#endif  // AIDL_USE_V2_INTERFACE
}

class MyCallback : public ITrunkStableTest::BnMyCallback {
 public:
  MyCallback() {}
  virtual ~MyCallback() = default;

  Status repeatParcelable(const MyParcelable& input, MyParcelable* _aidl_return) override {
    *_aidl_return = input;
    repeatParcelableCalled = true;
    return Status::ok();
  }
  Status repeatEnum(MyEnum input, MyEnum* _aidl_return) override {
    *_aidl_return = input;
    repeatEnumCalled = true;
    return Status::ok();
  }
  Status repeatUnion(const MyUnion& input, MyUnion* _aidl_return) override {
    *_aidl_return = input;
    repeatUnionCalled = true;
    return Status::ok();
  }
#ifdef AIDL_USE_V2_INTERFACE
  Status repeatOtherParcelable(const MyOtherParcelable& input,
                               MyOtherParcelable* _aidl_return) override {
    *_aidl_return = input;
    repeatOtherParcelableCalled = true;
    return Status::ok();
  }
#endif  // AIDL_USE_V2_INTERFACE
  bool repeatParcelableCalled = false;
  bool repeatEnumCalled = false;
  bool repeatUnionCalled = false;
#ifdef AIDL_USE_V2_INTERFACE
  bool repeatOtherParcelableCalled = false;
#endif  // AIDL_USE_V2_INTERFACE
};

// repeatOtherParcelable is new in V2, so it won't be called
TEST_F(TrunkInterfaceTest, callMyCallback) {
  sp<MyCallback> cb = sp<MyCallback>::make();

  auto status = service->callMyCallback(cb);
  ASSERT_TRUE(status.isOk()) << status;
  if (kUseUnfrozen) {
    EXPECT_TRUE(cb->repeatParcelableCalled);
    EXPECT_TRUE(cb->repeatEnumCalled);
    EXPECT_TRUE(cb->repeatUnionCalled);
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_TRUE(cb->repeatOtherParcelableCalled);
#endif  // AIDL_USE_V2_INTERFACE
  } else {
    EXPECT_TRUE(cb->repeatParcelableCalled);
    EXPECT_TRUE(cb->repeatEnumCalled);
    EXPECT_TRUE(cb->repeatUnionCalled);
#ifdef AIDL_USE_V2_INTERFACE
    EXPECT_FALSE(cb->repeatOtherParcelableCalled);
#endif  // AIDL_USE_V2_INTERFACE
  }
}
