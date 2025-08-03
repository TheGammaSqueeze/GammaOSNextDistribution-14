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
#include <optional>
#include <unordered_map>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "flatbuffers/flatbuffers.h"

#include "common/bind.h"
#include "os/handler.h"
#include "os/thread.h"
#include "os/wakelock_manager.h"
#include "wakelock_manager_generated.h"

namespace testing {

using bluetooth::os::FinishWakelockManagerDataBuffer;
using bluetooth::os::GetWakelockManagerData;
using bluetooth::os::Handler;
using bluetooth::os::Thread;
using bluetooth::os::WakelockManager;
using bluetooth::os::WakelockManagerData;
using bluetooth::os::WakelockManagerDataBuilder;

class TestOsCallouts : public WakelockManager::OsCallouts {
 public:
  void AcquireCallout(const std::string& lock_name) override {
    auto iter = acquired_lock_counts.find(lock_name);
    if (iter == acquired_lock_counts.end()) {
      acquired_lock_counts[lock_name] = 0;
    }
    acquired_lock_counts[lock_name] += 1;
  }

  void ReleaseCallout(const std::string& lock_name) override {
    auto iter = acquired_lock_counts.find(lock_name);
    if (iter == acquired_lock_counts.end()) {
      acquired_lock_counts[lock_name] = 0;
    }
    acquired_lock_counts[lock_name] -= 1;
  }

  std::optional<int> GetNetAcquiredCount(const std::string& lock_name) const {
    auto iter = acquired_lock_counts.find(lock_name);
    if (iter == acquired_lock_counts.end()) {
      return std::nullopt;
    }
    return iter->second;
  }

  // how many times each lock is acquired, net, can go negative
  std::unordered_map<std::string, int> acquired_lock_counts;
};

class WakelockManagerTest : public Test {
 protected:
  void SetUp() override {
    thread_ = new Thread("test_thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
  }
  void TearDown() override {
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void SyncHandler() {
    std::promise<void> promise;
    auto future = promise.get_future();
    handler_->Post(
        bluetooth::common::BindOnce(&std::promise<void>::set_value, bluetooth::common::Unretained(&promise)));
    auto future_status = future.wait_for(std::chrono::seconds(1));
    ASSERT_EQ(future_status, std::future_status::ready);
  }

  Handler* handler_;
  Thread* thread_;
};

TEST_F(WakelockManagerTest, test_set_os_callouts_repeated_acquire) {
  TestOsCallouts os_callouts;
  WakelockManager::Get().SetOsCallouts(&os_callouts, handler_);

  // Initially, no wakelock is acquired
  ASSERT_TRUE(os_callouts.acquired_lock_counts.empty());
  ASSERT_FALSE(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId));

  WakelockManager::Get().Acquire();
  SyncHandler();
  ASSERT_EQ(os_callouts.acquired_lock_counts.size(), (size_t)1);
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(1)));

  WakelockManager::Get().Acquire();
  SyncHandler();
  ASSERT_EQ(os_callouts.acquired_lock_counts.size(), (size_t)1);
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(2)));

  WakelockManager::Get().Release();
  SyncHandler();
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(1)));

  WakelockManager::Get().CleanUp();
  SyncHandler();
}

TEST_F(WakelockManagerTest, test_set_os_callouts_repeated_release) {
  TestOsCallouts os_callouts;
  WakelockManager::Get().SetOsCallouts(&os_callouts, handler_);

  // Initially, no wakelock is acquired
  ASSERT_TRUE(os_callouts.acquired_lock_counts.empty());
  ASSERT_FALSE(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId));

  WakelockManager::Get().Acquire();
  SyncHandler();
  ASSERT_EQ(os_callouts.acquired_lock_counts.size(), (size_t)1);
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(1)));

  WakelockManager::Get().Release();
  SyncHandler();
  ASSERT_EQ(os_callouts.acquired_lock_counts.size(), (size_t)1);
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(0)));

  // OS callouts allow pass through for repeated release calls
  WakelockManager::Get().Release();
  SyncHandler();
  ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(-1)));

  WakelockManager::Get().CleanUp();
  SyncHandler();
}

TEST_F(WakelockManagerTest, test_with_os_callouts_in_a_loop_and_dump) {
  TestOsCallouts os_callouts;
  WakelockManager::Get().SetOsCallouts(&os_callouts, handler_);

  // Initially, no wakelock is acquired
  ASSERT_TRUE(os_callouts.acquired_lock_counts.empty());
  ASSERT_FALSE(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId));

  for (size_t i = 0; i < 1000; i++) {
    WakelockManager::Get().Acquire();
    SyncHandler();
    ASSERT_EQ(os_callouts.acquired_lock_counts.size(), (size_t)1);
    ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(1)));
    WakelockManager::Get().Release();
    SyncHandler();
    ASSERT_THAT(os_callouts.GetNetAcquiredCount(WakelockManager::kBtWakelockId), Optional(Eq(0)));
  }

  {
    flatbuffers::FlatBufferBuilder builder(1024);
    auto offset = WakelockManager::Get().GetDumpsysData(&builder);
    FinishWakelockManagerDataBuffer(builder, offset);
    auto data = GetWakelockManagerData(builder.GetBufferPointer());

    ASSERT_EQ(data->acquired_count(), 1000);
    ASSERT_EQ(data->released_count(), 1000);
  }

  WakelockManager::Get().CleanUp();
  SyncHandler();

  {
    flatbuffers::FlatBufferBuilder builder(1024);
    auto offset = WakelockManager::Get().GetDumpsysData(&builder);
    FinishWakelockManagerDataBuffer(builder, offset);
    auto data = GetWakelockManagerData(builder.GetBufferPointer());

    ASSERT_EQ(data->acquired_count(), 0);
    ASSERT_EQ(data->released_count(), 0);
  }
}

}  // namespace testing
