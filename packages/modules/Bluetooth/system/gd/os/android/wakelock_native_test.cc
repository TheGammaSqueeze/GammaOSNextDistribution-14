/******************************************************************************
 *
 *  Copyright 2020 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "os/internal/wakelock_native.h"

#include <aidl/android/system/suspend/BnSuspendCallback.h>
#include <aidl/android/system/suspend/BnWakelockCallback.h>
#include <aidl/android/system/suspend/ISuspendControlService.h>
#include <android/binder_auto_utils.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <gtest/gtest.h>

#include <chrono>
#include <future>
#include <memory>
#include <mutex>

namespace testing {

using aidl::android::system::suspend::BnSuspendCallback;
using aidl::android::system::suspend::BnWakelockCallback;
using aidl::android::system::suspend::ISuspendControlService;
using bluetooth::os::internal::WakelockNative;
using ndk::ScopedAStatus;
using ndk::SharedRefBase;
using ndk::SpAIBinder;

static const std::string kTestWakelockName = "BtWakelockNativeTestLock";

static std::recursive_mutex mutex;
static std::unique_ptr<std::promise<void>> acquire_promise = nullptr;
static std::unique_ptr<std::promise<void>> release_promise = nullptr;

class PromiseFutureContext {
 public:
  static void FulfilPromise(std::unique_ptr<std::promise<void>>& promise) {
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    if (promise != nullptr) {
      std::promise<void>* prom = promise.release();
      prom->set_value();
      delete prom;
    }
  }

  explicit PromiseFutureContext(std::unique_ptr<std::promise<void>>& promise, bool expect_fulfillment)
      : promise_(promise), expect_fulfillment_(expect_fulfillment) {
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    EXPECT_EQ(promise_, nullptr);
    promise_ = std::make_unique<std::promise<void>>();
    future_ = promise->get_future();
  }

  ~PromiseFutureContext() {
    auto future_status = future_.wait_for(std::chrono::seconds(2));
    if (expect_fulfillment_) {
      EXPECT_EQ(future_status, std::future_status::ready);
    } else {
      EXPECT_NE(future_status, std::future_status::ready);
    }
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    promise_ = nullptr;
  }

 private:
  std::unique_ptr<std::promise<void>>& promise_;
  bool expect_fulfillment_ = true;
  std::future<void> future_;
};

class WakelockCallback : public BnWakelockCallback {
 public:
  ScopedAStatus notifyAcquired() override {
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    net_acquired_count++;
    fprintf(stderr, "notifyAcquired, count = %d\n", net_acquired_count);
    PromiseFutureContext::FulfilPromise(acquire_promise);
    return ScopedAStatus::ok();
  }
  ScopedAStatus notifyReleased() override {
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    net_acquired_count--;
    fprintf(stderr, "notifyReleased, count = %d\n", net_acquired_count);
    PromiseFutureContext::FulfilPromise(release_promise);
    return ScopedAStatus::ok();
  }

  int net_acquired_count = 0;
};

class SuspendCallback : public BnSuspendCallback {
 public:
  ScopedAStatus notifyWakeup(
      bool /* success */, const std::vector<std::string>& /* wakeup_reasons */) override {
    std::lock_guard<std::recursive_mutex> lock_guard(mutex);
    fprintf(stderr, "notifyWakeup\n");
    return ScopedAStatus::ok();
  }
};

// There is no way to unregister these callbacks besides when this process dies
// Hence, we want to have only one copy of these callbacks per process
static std::shared_ptr<SuspendCallback> suspend_callback = nullptr;
static std::shared_ptr<WakelockCallback> control_callback = nullptr;

class WakelockNativeTest : public Test {
 protected:
  void SetUp() override {
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    ABinderProcess_startThreadPool();

    WakelockNative::Get().Initialize();

    auto binder_raw = AServiceManager_waitForService("suspend_control");
    ASSERT_NE(binder_raw, nullptr);
    binder.set(binder_raw);
    control_service_ = ISuspendControlService::fromBinder(binder);
    if (control_service_ == nullptr) {
      FAIL() << "Fail to obtain suspend_control";
    }

    if (suspend_callback == nullptr) {
      suspend_callback = SharedRefBase::make<SuspendCallback>();
      bool is_registered = false;
      ScopedAStatus status = control_service_->registerCallback(suspend_callback, &is_registered);
      if (!is_registered || !status.isOk()) {
        FAIL() << "Fail to register suspend callback";
      }
    }

    if (control_callback == nullptr) {
      control_callback = SharedRefBase::make<WakelockCallback>();
      bool is_registered = false;
      ScopedAStatus status =
          control_service_->registerWakelockCallback(control_callback, kTestWakelockName, &is_registered);
      if (!is_registered || !status.isOk()) {
        FAIL() << "Fail to register wakeup callback";
      }
    }
    control_callback->net_acquired_count = 0;
  }

  void TearDown() override {
    control_service_ = nullptr;
    binder.set(nullptr);
    WakelockNative::Get().CleanUp();
  }

  SpAIBinder binder;
  std::shared_ptr<ISuspendControlService> control_service_ = nullptr;
};

TEST_F(WakelockNativeTest, test_acquire_and_release_wakelocks) {
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  {
    PromiseFutureContext context(acquire_promise, true);
    auto status = WakelockNative::Get().Acquire(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 1);

  {
    PromiseFutureContext context(release_promise, true);
    auto status = WakelockNative::Get().Release(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 0);
}

TEST_F(WakelockNativeTest, test_acquire_and_release_wakelocks_repeated_acquire) {
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  {
    PromiseFutureContext context(acquire_promise, true);
    auto status = WakelockNative::Get().Acquire(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 1);

  {
    PromiseFutureContext context(acquire_promise, false);
    auto status = WakelockNative::Get().Acquire(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 1);

  {
    PromiseFutureContext context(release_promise, true);
    auto status = WakelockNative::Get().Release(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 0);
}

TEST_F(WakelockNativeTest, test_acquire_and_release_wakelocks_repeated_release) {
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  {
    PromiseFutureContext context(acquire_promise, true);
    auto status = WakelockNative::Get().Acquire(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 1);

  {
    PromiseFutureContext context(release_promise, true);
    auto status = WakelockNative::Get().Release(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  {
    PromiseFutureContext context(release_promise, false);
    auto status = WakelockNative::Get().Release(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 0);
}

TEST_F(WakelockNativeTest, test_acquire_and_release_wakelocks_in_a_loop) {
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  for (int i = 0; i < 10; ++i) {
    {
      PromiseFutureContext context(acquire_promise, true);
      auto status = WakelockNative::Get().Acquire(kTestWakelockName);
      ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
    }
    ASSERT_EQ(control_callback->net_acquired_count, 1);

    {
      PromiseFutureContext context(release_promise, true);
      auto status = WakelockNative::Get().Release(kTestWakelockName);
      ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
    }
    ASSERT_EQ(control_callback->net_acquired_count, 0);
  }
}

TEST_F(WakelockNativeTest, test_clean_up) {
  WakelockNative::Get().Initialize();
  ASSERT_EQ(control_callback->net_acquired_count, 0);

  {
    PromiseFutureContext context(acquire_promise, true);
    auto status = WakelockNative::Get().Acquire(kTestWakelockName);
    ASSERT_EQ(status, WakelockNative::StatusCode::SUCCESS);
  }
  ASSERT_EQ(control_callback->net_acquired_count, 1);

  {
    PromiseFutureContext context(release_promise, true);
    WakelockNative::Get().CleanUp();
  }
  ASSERT_EQ(control_callback->net_acquired_count, 0);
}

}  // namespace testing
