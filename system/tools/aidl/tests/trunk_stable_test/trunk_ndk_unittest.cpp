/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "gtest/gtest.h"

/* Shared Client/Service includes */
#include <aidl/android/aidl/test/trunk/BnTrunkStableTest.h>
#include <aidl/android/aidl/test/trunk/ITrunkStableTest.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <sys/prctl.h>

/* AIDL Client includes */

/* AIDL Service includes */
#include <android/binder_process.h>

#ifndef AIDL_TEST_TRUNK_VER
#define AIDL_TEST_TRUNK_VER 2
#endif

using ::aidl::android::aidl::test::trunk::ITrunkStableTest;
using ndk::ScopedAStatus;

/* AIDL Client definition */
class MyCallback : public ITrunkStableTest::BnMyCallback {
 public:
  ScopedAStatus repeatParcelable(const ITrunkStableTest::MyParcelable& in_input,
                                 ITrunkStableTest::MyParcelable* _aidl_return) override {
    *_aidl_return = in_input;
    repeatParcelableCalled = true;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatEnum(const ITrunkStableTest::MyEnum in_input,
                           ITrunkStableTest::MyEnum* _aidl_return) override {
    *_aidl_return = in_input;
    repeatEnumCalled = true;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatUnion(const ITrunkStableTest::MyUnion& in_input,
                            ITrunkStableTest::MyUnion* _aidl_return) override {
    *_aidl_return = in_input;
    repeatUnionCalled = true;
    return ScopedAStatus::ok();
  }

#if AIDL_TEST_TRUNK_VER >= 2
  ScopedAStatus repeatOtherParcelable(const ITrunkStableTest::MyOtherParcelable& in_input,
                                      ITrunkStableTest::MyOtherParcelable* _aidl_return) override {
    *_aidl_return = in_input;
    repeatOtherParcelableCalled = true;
    return ScopedAStatus::ok();
  }

  bool repeatOtherParcelableCalled = false;
#endif

  bool repeatParcelableCalled = false;
  bool repeatEnumCalled = false;
  bool repeatUnionCalled = false;
};

class ClientTest : public testing::Test {
 public:
  void SetUp() override {
    mService = ITrunkStableTest::fromBinder(
        ndk::SpAIBinder(AServiceManager_waitForService(ITrunkStableTest::descriptor)));
    ASSERT_NE(nullptr, mService);
  }

  std::shared_ptr<ITrunkStableTest> mService;
};

TEST_F(ClientTest, SanityCheck) {
  ITrunkStableTest::MyParcelable a, b;
  a.a = 12;
  a.b = 13;
#if AIDL_TEST_TRUNK_VER >= 2
  a.c = 14;
#endif
  auto status = mService->repeatParcelable(a, &b);
  EXPECT_TRUE(status.isOk());
  EXPECT_EQ(a, b);
}

TEST_F(ClientTest, Callback) {
  auto cb = ndk::SharedRefBase::make<MyCallback>();
  auto status = mService->callMyCallback(cb);
  EXPECT_TRUE(status.isOk());
  EXPECT_TRUE(cb->repeatParcelableCalled);
  EXPECT_TRUE(cb->repeatEnumCalled);
  EXPECT_TRUE(cb->repeatUnionCalled);
#if AIDL_TEST_TRUNK_VER >= 2
  EXPECT_TRUE(cb->repeatOtherParcelableCalled);
#endif
}

#if AIDL_TEST_TRUNK_VER >= 2
TEST_F(ClientTest, CallV2Method) {
  ITrunkStableTest::MyOtherParcelable a, b;
  a.a = 12;
  a.b = 13;
  auto status = mService->repeatOtherParcelable(a, &b);
  EXPECT_TRUE(status.isOk());
  EXPECT_EQ(a, b);
}
#endif

/* AIDL service definition */
using ::aidl::android::aidl::test::trunk::BnTrunkStableTest;
class TrunkStableTest : public BnTrunkStableTest {
  ScopedAStatus repeatParcelable(const MyParcelable& in_input,
                                 MyParcelable* _aidl_return) override {
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatEnum(const MyEnum in_input, MyEnum* _aidl_return) override {
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus repeatUnion(const MyUnion& in_input, MyUnion* _aidl_return) override {
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
  ScopedAStatus callMyCallback(const std::shared_ptr<IMyCallback>& in_cb) override {
    MyParcelable a, b;
    MyEnum c = MyEnum::ZERO, d = MyEnum::ZERO;
    MyUnion e, f;
    auto status = in_cb->repeatParcelable(a, &b);
    if (!status.isOk()) {
      return status;
    }
    status = in_cb->repeatEnum(c, &d);
    if (!status.isOk()) {
      return status;
    }
    status = in_cb->repeatUnion(e, &f);
#if AIDL_TEST_TRUNK_VER >= 2
    if (!status.isOk()) {
      return status;
    }
    MyOtherParcelable g, h;
    status = in_cb->repeatOtherParcelable(g, &h);
#endif
    return status;
  }
#if AIDL_TEST_TRUNK_VER >= 2
  ScopedAStatus repeatOtherParcelable(const ITrunkStableTest::MyOtherParcelable& in_input,
                                      ITrunkStableTest::MyOtherParcelable* _aidl_return) override {
    *_aidl_return = in_input;
    return ScopedAStatus::ok();
  }
#endif
};

int run_service() {
  auto trunk = ndk::SharedRefBase::make<TrunkStableTest>();
  binder_status_t status =
      AServiceManager_addService(trunk->asBinder().get(), TrunkStableTest::descriptor);
  CHECK_EQ(status, STATUS_OK);

  ABinderProcess_joinThreadPool();
  return EXIT_FAILURE;  // should not reach
}

int main(int argc, char* argv[]) {
  if (fork() == 0) {
    prctl(PR_SET_PDEATHSIG, SIGHUP);
    run_service();
  }
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
