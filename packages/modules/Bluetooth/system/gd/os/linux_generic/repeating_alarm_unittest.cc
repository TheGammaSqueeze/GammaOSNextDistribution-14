/*
 * Copyright 2019 The Android Open Source Project
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

#include "os/repeating_alarm.h"

#include <future>

#include "common/bind.h"
#include "gtest/gtest.h"
#include "os/fake_timer/fake_timerfd.h"

namespace bluetooth {
namespace os {
namespace {

using fake_timer::fake_timerfd_advance;
using fake_timer::fake_timerfd_reset;

class RepeatingAlarmTest : public ::testing::Test {
 protected:
  void SetUp() override {
    thread_ = new Thread("test_thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    alarm_ = new RepeatingAlarm(handler_);
  }

  void TearDown() override {
    delete alarm_;
    handler_->Clear();
    delete handler_;
    delete thread_;
    fake_timerfd_reset();
  }

  void VerifyMultipleDelayedTasks(int scheduled_tasks, int task_length_ms, int interval_between_tasks_ms) {
    std::promise<void> promise;
    auto future = promise.get_future();
    auto start_time = std::chrono::steady_clock::now();
    int counter = 0;
    alarm_->Schedule(
        common::Bind(
            &RepeatingAlarmTest::verify_delayed_tasks,
            common::Unretained(this),
            common::Unretained(&counter),
            start_time,
            scheduled_tasks,
            common::Unretained(&promise),
            task_length_ms,
            interval_between_tasks_ms),
        std::chrono::milliseconds(interval_between_tasks_ms));
    fake_timer_advance(interval_between_tasks_ms * scheduled_tasks);
    future.get();
    alarm_->Cancel();
  }

  void verify_delayed_tasks(
      int* counter,
      std::chrono::steady_clock::time_point /* start_time */,
      int scheduled_tasks,
      std::promise<void>* promise,
      int /* task_length_ms */,
      int /* interval_between_tasks_ms */) {
    *counter = *counter + 1;
    if (*counter == scheduled_tasks) {
      promise->set_value();
    }
  }

  void fake_timer_advance(uint64_t ms) {
    handler_->Post(common::BindOnce(fake_timerfd_advance, ms));
  }

  RepeatingAlarm* alarm_;

  common::Closure should_not_happen_ = common::Bind([] { ASSERT_TRUE(false); });

 private:
  Thread* thread_;
  Handler* handler_;
};

TEST_F(RepeatingAlarmTest, cancel_while_not_armed) {
  alarm_->Cancel();
}

TEST_F(RepeatingAlarmTest, schedule) {
  std::promise<void> promise;
  auto future = promise.get_future();
  int period_ms = 10;
  alarm_->Schedule(
      common::Bind(&std::promise<void>::set_value, common::Unretained(&promise)), std::chrono::milliseconds(period_ms));
  fake_timer_advance(period_ms);
  future.get();
  alarm_->Cancel();
  ASSERT_FALSE(future.valid());
}

TEST_F(RepeatingAlarmTest, cancel_alarm) {
  alarm_->Schedule(should_not_happen_, std::chrono::milliseconds(10));
  alarm_->Cancel();
  std::this_thread::sleep_for(std::chrono::milliseconds(50));
}

TEST_F(RepeatingAlarmTest, cancel_alarm_from_callback) {
  alarm_->Schedule(
      common::Bind(&RepeatingAlarm::Cancel, common::Unretained(this->alarm_)), std::chrono::milliseconds(1));
  std::this_thread::sleep_for(std::chrono::milliseconds(5));
}

TEST_F(RepeatingAlarmTest, schedule_while_alarm_armed) {
  alarm_->Schedule(should_not_happen_, std::chrono::milliseconds(1));
  std::promise<void> promise;
  auto future = promise.get_future();
  alarm_->Schedule(
      common::Bind(&std::promise<void>::set_value, common::Unretained(&promise)), std::chrono::milliseconds(10));
  fake_timer_advance(10);
  future.get();
  alarm_->Cancel();
}

TEST_F(RepeatingAlarmTest, delete_while_alarm_armed) {
  alarm_->Schedule(should_not_happen_, std::chrono::milliseconds(1));
  delete alarm_;
  alarm_ = nullptr;
  std::this_thread::sleep_for(std::chrono::milliseconds(1));
}

TEST_F(RepeatingAlarmTest, verify_small) {
  VerifyMultipleDelayedTasks(100, 1, 10);
}

TEST_F(RepeatingAlarmTest, verify_large) {
  VerifyMultipleDelayedTasks(100, 3, 10);
}

}  // namespace
}  // namespace os
}  // namespace bluetooth
